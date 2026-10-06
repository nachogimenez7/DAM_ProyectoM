"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {getAuth} = require("firebase-admin/auth");
const {AUTHORITY_GATE} = require("../src/onlineGameService");
const {requestLimitId} = require("../src/onlineRequestLimiter");

// Run only with firebase.authority-qa.json: no project or host can point at production.
const projectId = "traidores-local";
const functionsOrigin = "http://127.0.0.1:15001";
const appCheck = [Buffer.from(JSON.stringify({alg: "none", typ: "JWT"})).toString("base64url"),
  Buffer.from(JSON.stringify({app_id: "local-v3-test"})).toString("base64url"), "local"].join(".");
let app, firestore, database;
const accounts = [], rooms = [];
test.before(() => {
  for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_DATABASE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"])
    assert.match(process.env[key] || "", /^127\.0\.0\.1:/, key);
  assert.equal(process.env.GCLOUD_PROJECT, projectId);
  app = initializeApp({projectId, databaseURL: `https://${projectId}-default-rtdb.firebaseio.com`}, "v3-http-test");
  firestore = getFirestore(app); database = getDatabase(app);
});
test.after(async () => {
  if (!app) return;
  for (const id of rooms) {
    await firestore.recursiveDelete(firestore.doc(`partidas/${id}`));
    await database.ref(`onlineV3/${id}`).remove();
  }
  await firestore.doc(AUTHORITY_GATE).delete();
  for (const user of accounts) {
    await getAuth(app).deleteUser(user.uid);
    await firestore.doc(`onlineRequestLimits/${requestLimitId(user.uid)}`).delete();
  }
  await deleteApp(app);
});
async function account() {
  const response = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-only`, {
    method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({returnSecureToken: true})});
  const data = await response.json(); assert.equal(response.status, 200);
  const user = {uid: data.localId, token: data.idToken}; accounts.push(user); return user;
}
async function call(name, data, user, withAppCheck = true) {
  const headers = {"Content-Type": "application/json"};
  if (user) headers.Authorization = `Bearer ${user.token}`;
  if (withAppCheck) headers["X-Firebase-AppCheck"] = appCheck;
  const response = await fetch(`${functionsOrigin}/${projectId}/southamerica-west1/${name}`, {
    method: "POST", headers, body: JSON.stringify({data})});
  return {status: response.status, body: await response.json()};
}
async function eventually(read, accepts, label, timeoutMs = 15000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    const value = await read(); if (accepts(value)) return value;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  assert.fail(`Timed out: ${label}`);
}
test("HTTP Auth/App Check, outbox trigger and Tasks advance without any host request", {timeout: 100000}, async () => {
  const members = await Promise.all(Array.from({length: 5}, account));
  const outsider = await account(), host = members[0];
  const roomId = `v3-http-${Date.now()}`; rooms.push(roomId);
  const batch = firestore.batch();
  batch.set(firestore.doc(`partidas/${roomId}`), {estado: "esperando", hostId: host.uid, hostActivoId: host.uid,
    jugadoresEsperados: 5, mapa: "pampa", codigoSala: "HTTP23", configLobby: {presetRoles: "RECOMMENDED"}});
  members.forEach((user, i) => batch.set(firestore.doc(`partidas/${roomId}/jugadores/${user.uid}`), {
    nombre: `J${i}`, listo: true, activoEnPartida: true, orden: i, protocolVersion: 3, puedeArbitrar: false}));
  await batch.commit();
  assert.equal((await call("iniciarPartidaV3", {roomId})).status, 401);
  assert.equal((await call("iniciarPartidaV3", {roomId}, host, false)).status, 401);
  const blocked = await call("iniciarPartidaV3", {roomId}, host);
  assert.equal(blocked.body.error?.details?.reason, "server-mode-unavailable");
  await firestore.doc(AUTHORITY_GATE).set({enabled: true});
  const started = await call("iniciarPartidaV3", {roomId}, host);
  assert.equal(started.status, 200, JSON.stringify(started.body));
  const matchId = started.body.result.matchId;
  assert.equal((await call("iniciarPartidaV3", {roomId}, host)).body.result.status, "already_started");
  const publicRef = database.ref(`onlineV3/${roomId}/snapshot/public`);
  const initial = await eventually(async () => (await publicRef.get()).val(), (p) => p?.fase === "REPARTO", "outbox publication");
  assert.equal(initial.matchId, matchId);
  assert.ok((await database.ref(`onlineV3/${roomId}/snapshot/queuedTaskId`).get()).val());
  const data = {roomId, matchId, phaseIndex: 0, requestId: "http_ack_retry", action: "role_ack"};
  const accepted = await call("accionPartidaV3", data, host);
  assert.equal(accepted.status, 200, JSON.stringify(accepted.body));
  assert.deepEqual((await call("accionPartidaV3", data, host)).body, accepted.body);
  assert.deepEqual((await publicRef.get()).val(), initial); // Secret receipts cannot leak their count.
  const foreign = await call("accionPartidaV3", {...data, requestId: "foreign_request"}, outsider);
  assert.equal(foreign.status, 403); assert.equal(foreign.body.error.details.reason, "not-a-member");
  const waiting = await call("recuperarFaseV3", {roomId, matchId, phaseIndex: 0}, members[1]);
  assert.equal(waiting.body.result.status, "waiting");
  console.log("HTTP checks passed; waiting for the Tasks emulator, with all clients idle.");
  const advanced = await eventually(async () => (await publicRef.get()).val(), (p) => p?.phaseIndex > 0,
    "automatic deadline delivery", 70000);
  assert.equal(advanced.fase, "NOCHE"); assert.equal(advanced.phaseIndex, 1);
  const state = (await firestore.doc(`partidas/${roomId}/servidor/current`).get()).data();
  assert.equal(state.phaseIndex, 1);
  const left = await call("abandonarPartidaV3", {roomId, matchId}, members[2]);
  assert.equal(left.status, 200, JSON.stringify(left.body));
  await eventually(async () => (await database.ref(`onlineV3/${roomId}/snapshot/permissions/${members[2].uid}/member`).get()).val(),
    (member) => member === false, "permission revocation");
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private/${members[2].uid}`).get()).exists(), false);
});
