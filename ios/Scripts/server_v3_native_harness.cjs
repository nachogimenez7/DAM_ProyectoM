#!/usr/bin/env node
// Local fixture controller for native XCTest. It never connects to Cloud: endpoints and
// project are fixed, emulator environment is set before loading the Admin SDK, and the
// HTTP listener binds only loopback. The iOS app still uses the real SDK, rules and callables.
// Run with the isolated server_v3_emulators.firebase.json emulator set, then run the
// OnlineFlowUITests/testServer* tests with TRAIDORES_IOS_V3_NATIVE_TEST=1.
"use strict";
const path = require("node:path");
const http = require("node:http");
const {randomUUID} = require("node:crypto");
const root = path.resolve(__dirname, "../..");
process.env.GCLOUD_PROJECT = "traidores";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:29099";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:28081";
process.env.FIREBASE_DATABASE_EMULATOR_HOST = "127.0.0.1:29000";
const req = require("node:module").createRequire(path.join(root, "functions/package.json"));
const {initializeApp} = req("firebase-admin/app");
const {getAuth} = req("firebase-admin/auth");
const {getFirestore, FieldValue} = req("firebase-admin/firestore");
const {getDatabase} = req("firebase-admin/database");
const core = require(path.join(root, "functions/src/onlineGameCore"));
const {assignRoles, ROLE_KEYS} = require(path.join(root, "functions/src/onlineStartCore"));
const {publishServerOutbox} = require(path.join(root, "functions/src/onlineGameService"));
const app = initializeApp({projectId: "traidores", databaseURL: "http://127.0.0.1:29000?ns=traidores-default-rtdb"}, "ios-native-qa");
const db = getFirestore(app), auth = getAuth(app), realtime = getDatabase(app);
let current;
const rooms = [];
const enqueueDeadline = async () => {}; // XCTest explicitly advances its fixture phases.

async function setup() {
  const id = `ios-native-${randomUUID()}`;
  const code = Array.from({length: 6}, () => "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Math.floor(Math.random() * 32)]).join("");
  const password = "local-emulator-only-123";
  const users = [];
  for (let i = 0; i < 8; i++) users.push(await auth.createUser({uid: `${id}-${i}`, email: `${id}-${i}@traidores.test`, password}));
  const room = db.doc(`partidas/${id}`), batch = db.batch();
  const config = {presetRoles: "RECOMMENDED", transicionSeg: 8, nocheSeg: 120, discusionSeg: 120,
    votacionSeg: 60, revelarRolesAlMorir: true, votosIndividuales: false};
  batch.set(room, {hostId: users[0].uid, hostActivoId: users[0].uid, hostNombre: "iOS QA", hostVersion: 0,
    nombre: "Prueba nativa V3", codigoSala: code, estado: "esperando", mapa: "pampa", mapaNombre: "Pampa",
    protocolVersion: 3, jugadoresEsperados: 8, maxJugadores: 8, jugadoresActuales: 8,
    partidaInicialCreada: false, limpiezaPendiente: false, visibilidad: "privada", soloCuentas: false,
    configLobby: config, creadaEn: FieldValue.serverTimestamp(), actualizadaEn: FieldValue.serverTimestamp()});
  for (let i = 0; i < users.length; i++) {
    const uid = users[i].uid, name = i === 0 ? "iOS QA" : `Jugador ${i}`;
    batch.set(db.doc(`perfiles_publicos/${uid}`), {uidTemporal: uid, publicId: `${1000 + i}`, nombrePerfil: name,
      bioPerfil: "Prueba local", avatarPerfil: "aldeano", bannerPerfil: "pampa", rolFavoritoPerfil: "aldeano"});
    batch.set(room.collection("jugadores").doc(uid), {uidTemporal: uid, publicId: `${1000 + i}`,
      nombre: name, nombreSala: name, nombrePerfil: name, avatarPerfil: "aldeano", bannerPerfil: "pampa",
      bioPerfil: "Prueba local", rolFavoritoPerfil: "aldeano", esHost: i === 0, listo: i !== 0,
      protocolVersion: 3, puedeArbitrar: false, activoEnPartida: true, orden: i, estado: "conectado",
      ultimaConexionLocal: Date.now(), ultimaConexion: FieldValue.serverTimestamp(), unidoEn: FieldValue.serverTimestamp()});
  }
  batch.set(db.doc(`codigosSala/${code}`), {partidaId: id, codigoSala: code, hostId: users[0].uid, creadaEn: FieldValue.serverTimestamp()});
  batch.set(db.doc("onlineMaintenance/serverAuthority"), {enabled: true, allowedHostUids: [users[0].uid], allowedRoomIds: [id]});
  await batch.commit();
  current = {roomId: id, code, uid: users[0].uid, email: users[0].email, password, users};
  rooms.push(current);
  return {...current, users: undefined};
}

async function state() {
  if (!current) throw new Error("Call setup first");
  const data = (await db.doc(`partidas/${current.roomId}/servidor/current`).get()).data();
  if (!data) throw new Error("Start the room in the app first");
  return data;
}

async function publish(s) {
  const room = db.doc(`partidas/${current.roomId}`), batch = db.batch();
  batch.set(room.collection("servidor").doc("current"), s);
  batch.set(room.collection("serverOutbox").doc("current"), {generation: s.generation, revision: s.revision,
    deliveredRevision: 0, projection: core.projectServerGame(s), token: core.deadlineToken(s),
    recoveryAtMs: s.deadlineMs ? s.deadlineMs + 30000 : null, updatedAt: FieldValue.serverTimestamp()});
  batch.update(room, {estado: s.winner ? "finalizada" : "en_juego", estadoPartida: core.projectServerGame(s).public,
    actualizadaEn: FieldValue.serverTimestamp()});
  await batch.commit();
  await publishServerOutbox({firestore: db, database: realtime, roomId: current.roomId, enqueueDeadline});
  return {phase: s.phase, matchId: s.matchId, uid: current.uid};
}

async function phase(input) {
  const s = await state(), me = s.players.find((p) => p.uid === current.uid);
  const allowed = new Set(["REPARTO", "NOCHE", "AMANECER", "DIA_DEBATE", "VOTACION", "DESERTOR_RECONSIDERACION", "FINALIZADA"]);
  if (!allowed.has(input.phase)) throw new Error("Unknown fixture phase");
  if (input.role) {
    if (!Object.values(ROLE_KEYS).includes(input.role)) throw new Error("Unknown fixture role");
    const other = s.players.find((p) => p.uid !== me.uid && p.role.key === input.role);
    if (other) other.role = me.role;
    const assigned = assignRoles(Array.from({length: 8}, (_, i) => ({id: `${i}`, name: `J${i}`, order: i})),
      "pampa", {roleCounts: {[input.role]: 1, aldeano: input.role === "aldeano" ? 8 : 7}}, () => 0);
    me.role = assigned.find((p) => p.role.key === input.role).role;
  }
  s.phase = input.phase; s.phaseIndex++; s.revision++; s.publicRevision = (s.publicRevision || 1) + 1;
  s.phaseStartedAtMs = Date.now(); s.deadlineMs = input.phase === "FINALIZADA" ? null : Date.now() + 600000;
  s.round = input.round || 1; s.actions = {}; s.phaseActionCounts = {}; s.voteTotals = {}; s.voteRound = 1;
  s.tieCandidates = []; s.eliminationUid = null; s.winner = input.phase === "FINALIZADA" ? "Pueblo" : null;
  me.alive = !input.dead; me.muted = !!input.muted; me.deathCause = input.dead ? "NIGHT" : "NONE";
  if (input.role === "desertor") { s.deserterTeam = input.team || null; s.deserterUsed = false; }
  if (input.phase === "DESERTOR_RECONSIDERACION") {
    // Actual traitor parity, so a submitted reconsideration closes through the engine.
    const assassin = s.players.find((p) => p.role.key === "asesino" && p.uid !== me.uid);
    for (const p of s.players) { p.alive = p.uid === me.uid || p.uid === assassin.uid; p.deathCause = p.alive ? "NONE" : "NIGHT"; }
    s.deserterReturn = {phase: "AMANECER", remainingMs: 5000, actions: {}, phaseActionCounts: {}};
  }
  s.eventSeq = (s.eventSeq || 0) + 1;
  s.events = [{seq: s.eventSeq, codigo: input.phase === "NOCHE" ? "NIGHT_START" : "INFO",
    ronda: s.round, jugadores: [], texto: input.phase === "NOCHE" ? `Noche ${s.round}` : `Prueba ${input.phase}`}];
  if (input.event === "NIGHT_DEATH") {
    const victim = s.players.find((p) => p.order === 1);
    victim.alive = false; victim.deathCause = "NIGHT";
    s.config.revelarRolesAlMorir = input.revealRole === true;
    s.events[0] = {seq: s.eventSeq, codigo: "NIGHT_DEATH", ronda: s.round,
      jugadores: [victim.uid], texto: `${victim.name} murió durante la noche.`};
  } else if (input.event === "ORACLE_INVITATION") {
    const guest = s.players.find((p) => p.order === 1);
    guest.alive = false; guest.deathCause = "NIGHT"; s.oracleGuestUid = guest.uid;
    s.events[0] = {seq: s.eventSeq, codigo: "ORACLE_INVITATION", ronda: s.round,
      jugadores: [guest.uid], texto: `${guest.name} vuelve a hablar por invitación del Oráculo.`};
  }
  s.announcement = s.events[0].texto;
  return publish(s);
}

async function status() {
  const s = await state();
  const member = (await db.doc(`partidas/${current.roomId}/jugadores/${current.uid}`).get()).data();
  return {phase: s.phase, matchId: s.matchId, team: s.deserterTeam, used: s.deserterUsed,
    active: member?.activoEnPartida, deathCause: s.players?.find((p) => p.uid === current.uid)?.deathCause,
    confirmed: Object.values(s.actions || {}).filter((a) => a.actorOrder === s.players?.find((p) => p.uid === current.uid)?.order),
    chat: (await realtime.ref(`onlineV3/${current.roomId}/chat`).get()).val()};
}

// Sequential deletes avoid BulkWriter/recursiveDelete failures in the local emulator.
// Only references owned by this controller instance are traversed.
async function deleteFixtureTree(reference) {
  for (const collection of await reference.listCollections()) {
    for (const document of (await collection.get()).docs) await deleteFixtureTree(document.ref);
  }
  await reference.delete();
}

async function cleanup() {
  for (const fixture of rooms) {
    await deleteFixtureTree(db.doc(`partidas/${fixture.roomId}`));
    await db.doc(`codigosSala/${fixture.code}`).delete();
    await realtime.ref(`onlineV3/${fixture.roomId}`).remove();
    for (const user of fixture.users) {
      await db.doc(`perfiles_publicos/${user.uid}`).delete();
      try { await auth.deleteUser(user.uid); }
      catch (error) { if (error.code !== "auth/user-not-found") throw error; }
    }
  }
  rooms.length = 0;
  current = undefined;
}

http.createServer(async (request, response) => {
  try {
    if (request.method !== "POST") throw new Error("POST only");
    let body = "";
    for await (const part of request) { body += part; if (body.length > 4096) throw new Error("Too large"); }
    const input = JSON.parse(body || "{}");
    const result = request.url === "/health" ? {emulatorsOnly: true, projectId: "traidores"}
      : request.url === "/setup" ? await setup() : request.url === "/phase" ? await phase(input)
      : request.url === "/status" ? await status() : request.url === "/cleanup" ? await cleanup() : (() => { throw new Error("Unknown operation"); })();
    response.writeHead(200, {"Content-Type": "application/json"}); response.end(JSON.stringify(result || {}));
  } catch (error) { response.writeHead(500); response.end(JSON.stringify({error: error.message})); }
}).listen(29888, "127.0.0.1", () => console.log("Native iOS V3 fixture controller: loopback 29888, isolated emulators only"));
