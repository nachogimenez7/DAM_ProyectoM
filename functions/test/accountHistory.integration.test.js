"use strict";
const {test, before, after} = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getAuth} = require("firebase-admin/auth");
const {archiveFinishedRoom, saveRecord, historyId} = require("../src/accountHistoryService");
const {room} = require("./fixtures/accountHistoryRoom");
let app, firestore;
const prefix = "history-" + Date.now();
const uids = [];
const rooms = [];
before(() => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST || "", /^127\.0\.0\.1:/);
  assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST || "", /^127\.0\.0\.1:/);
  app = initializeApp({projectId: process.env.HISTORY_TEST_PROJECT || "traidores-local"}, "history-tests");
  firestore = getFirestore(app);
});
after(async () => {
  for (const id of rooms) await firestore.doc("partidas/" + id).delete();
  for (const uid of uids) {
    await firestore.doc("perfiles_publicos/" + uid).delete();
    await firestore.recursiveDelete(firestore.doc("cuentas/" + uid));
    await getAuth(app).deleteUser(uid).catch(() => {});
  }
  await deleteApp(app);
});
async function profile(label) {
  const uid = prefix + "-" + label; uids.push(uid);
  await getAuth(app).createUser({uid, email: uid + "@example.test", password: "Test-password-42"});
  await firestore.doc("perfiles_publicos/" + uid).set({
    uidTemporal: uid, publicId: "42", nombrePerfil: "Jugador", actualizadaEn: Timestamp.now(),
  });
  return uid;
}
async function eventually(read, check) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const value = await read();
    if (check(value)) return value;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.fail("El trigger no confirmó el resultado dentro del plazo");
}
test("entrega concurrente cuenta una vez y conserva historial de desconectados", async () => {
  const first = await profile("first"), second = await profile("second");
  const value = room();
  value.partidaInicial.jugadores = value.partidaInicial.jugadores.map((p, index) => ({
    ...p, uidTemporal: index === 0 ? first : index === 1 ? second : prefix + "-guest-" + index,
  }));
  const args = {firestore, roomId: prefix, room: value, finishedAtMs: 1000};
  const counts = await Promise.all([archiveFinishedRoom(args), archiveFinishedRoom(args), archiveFinishedRoom(args)]);
  assert.equal(counts.reduce((a, b) => a + b), 2);
  assert.equal((await firestore.doc("cuentas/" + first).get()).data().partidas, 1);
  assert.equal((await firestore.doc("cuentas/" + second).get()).data().victorias, 0);
  assert.equal((await firestore.collection("cuentas/" + second + "/historial").get()).size, 1);
  assert.equal((await firestore.doc("cuentas/" + prefix + "-guest-2").get()).exists, false);
});
test("evento viejo no retrocede la fecha y una cuenta borrada no revive", async () => {
  const uid = await profile("ordering");
  const value = room();
  value.partidaInicial.jugadores = value.partidaInicial.jugadores.map((p, index) => ({...p,
    uidTemporal: index === 0 ? uid : prefix + "-missing-" + index}));
  await archiveFinishedRoom({firestore, roomId: prefix, room: value, finishedAtMs: 5000});
  value.partidaInicial.matchId += "-old";
  await archiveFinishedRoom({firestore, roomId: prefix, room: value, finishedAtMs: 2000});
  const summary = (await firestore.doc("cuentas/" + uid).get()).data();
  assert.equal(summary.partidas, 2); assert.equal(summary.ultimaPartidaEn.toMillis(), 5000);
  await firestore.doc("perfiles_publicos/" + uid).delete();
  await firestore.recursiveDelete(firestore.doc("cuentas/" + uid));
  value.partidaInicial.matchId += "-late";
  assert.equal(await archiveFinishedRoom({firestore, roomId: prefix, room: value, finishedAtMs: 6000}), 0);
  assert.equal((await firestore.doc("cuentas/" + uid).get()).exists, false);
});
test("trigger desplegado en emulador guarda online y sobrevive al borrado de sala", async () => {
  const uid = await profile("trigger");
  const value = room(), id = prefix + "-trigger"; rooms.push(id);
  value.partidaInicial.matchId = id;
  value.partidaInicial.jugadores = value.partidaInicial.jugadores.map((p, index) => ({...p,
    uidTemporal: index === 0 ? uid : prefix + "-missing-trigger-" + index}));
  await firestore.doc("partidas/" + id).set(value);
  await eventually(() => firestore.doc("cuentas/" + uid).get(), (snap) => snap.data()?.partidas === 1);
  await firestore.doc("partidas/" + id).update({ultimaActividadOnline: Timestamp.now()});
  await firestore.doc("partidas/" + id).delete();
  const recovered = await firestore.collection("cuentas/" + uid + "/historial").get();
  assert.equal(recovered.size, 1); assert.equal(recovered.docs[0].data().won, true);
  assert.equal((await firestore.doc("cuentas/" + uid).get()).data().partidas, 1);
});
test("local: trigger, reintentos e ID no canónico no inflan contadores", async () => {
  const uid = await profile("local");
  const record = {schemaVersion: 1, uid, origen: "local", matchKey: "local:" + prefix,
    won: true, finalizadaEn: Timestamp.now()};
  const recordId = historyId(record.matchKey);
  await firestore.doc("cuentas/" + uid + "/historial/" + recordId).set(record);
  await eventually(() => firestore.doc("cuentas/" + uid).get(), (snap) => snap.data()?.partidas === 1);
  assert.equal(await saveRecord({firestore, uid, record, recordId, existingLocal: true}), false);
  await firestore.doc("cuentas/" + uid + "/historial/local_" + "a".repeat(64)).set(record);
  assert.equal(await saveRecord({firestore, uid, record, recordId: "local_" + "a".repeat(64), existingLocal: true}), false);
  assert.equal((await firestore.doc("cuentas/" + uid).get()).data().partidas, 1);
});
test("eliminar Auth purga el historial privado y cierra futuros resultados", async () => {
  const uid = await profile("delete");
  await firestore.doc("cuentas/" + uid).set({partidas: 1});
  await firestore.doc("cuentas/" + uid + "/historial/old").set({won: true});
  await getAuth(app).deleteUser(uid);
  await eventually(() => firestore.doc("perfiles_publicos/" + uid).get(), (snap) => !snap.exists);
  await eventually(() => firestore.collection("cuentas/" + uid + "/historial").get(), (snap) => snap.empty);
  assert.equal((await firestore.doc("cuentas/" + uid).get()).exists, false);
});
