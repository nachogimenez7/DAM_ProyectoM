"use strict";
const assert = require("node:assert/strict");
const {before, after, test} = require("node:test");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {cleanupRoom, observeDeletedRoom, sweepRooms, enqueueRoomCleanup, ORPHANS, QUEUE} = require("../src/onlineRoomCleanupService");
const {DAY_MS} = require("../src/onlineRoomCleanupPolicy");
let app, firestore, database;
const ids = [];
const nowMs = Date.now();
before(() => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST || "", /^(127\.0\.0\.1|localhost):/);
  assert.match(process.env.FIREBASE_DATABASE_EMULATOR_HOST || "", /^(127\.0\.0\.1|localhost):/);
  app = initializeApp({projectId: "traidores-local", databaseURL: "https://traidores-local-default-rtdb.firebaseio.com"}, "cleanup-tests");
  firestore = getFirestore(app); database = getDatabase(app);
});
after(async () => {
  for (const id of ids) {
    await firestore.recursiveDelete(firestore.doc(`partidas/${id}`));
    await firestore.doc(`${ORPHANS}/${id}`).delete();
    await firestore.doc(`${QUEUE}/${id}`).delete();
    await database.ref(`salas/${id}`).remove();
  }
  await firestore.doc("codigosSala/CLN234").delete();
  await firestore.doc("onlineMaintenance/roomCleanup").delete();
  await deleteApp(app);
});
async function seed(overrides = {}) {
  const id = `cleanup-${Date.now()}-${ids.length}`; ids.push(id);
  const at = nowMs - 8 * DAY_MS;
  const ref = firestore.doc(`partidas/${id}`);
  await ref.set({estado: "esperando", hostId: "host", codigoSala: "CLN234", actualizadaEn: Timestamp.fromMillis(at), ...overrides});
  await ref.collection("jugadores").doc("host").set({ultimaConexion: Timestamp.fromMillis(at)});
  await ref.collection("runtime").doc("authoritative").set({actualizadaEn: Timestamp.fromMillis(at)});
  await ref.collection("acciones").doc("a").collection("nested").doc("b").set({secret: "test"});
  await database.ref(`salas/${id}`).set({control: {hostUid: "host", actualizadaEn: at}, presencia: {host: {estado: "desconectado", ts: at}}});
  return id;
}
test("purga subcolecciones anidadas y espejo, conserva tombstones y es idempotente", async () => {
  const roomId = await seed();
  await firestore.doc("codigosSala/CLN234").set({partidaId: roomId});
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs})).status, "cleaned");
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().cleanupCompleted, true);
  assert.deepEqual(await firestore.doc(`partidas/${roomId}`).listCollections(), []);
  const mirror = (await database.ref(`salas/${roomId}`).get()).val();
  assert.deepEqual(Object.keys(mirror), ["control"]);
  assert.equal(mirror.control.cleanupState, "deleting");
  assert.equal((await firestore.doc("codigosSala/CLN234").get()).exists, false);
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs})).status, "retained");
});
test("reconexión gana carrera antes del lock RTDB; revierte claim sin borrar", async () => {
  const roomId = await seed();
  const wrapped = {ref(path) {
    const ref = database.ref(path);
    return {get: () => ref.get(), transaction: async (...args) => {
      await ref.child("presencia/late").set({estado: "conectado", ts: nowMs});
      return ref.transaction(...args);
    }};
  }};
  assert.equal((await cleanupRoom({firestore, database: wrapped, roomId, nowMs})).reason, "realtime-changed-before-lock");
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().cleanupState, undefined);
  assert.equal((await firestore.doc(`partidas/${roomId}/acciones/a/nested/b`).get()).exists, true);
});
test("fallo parcial de purga se recupera sin abrir los locks", async () => {
  const roomId = await seed();
  const broken = new Proxy(firestore, {get(target, name) {
    if (name === "recursiveDelete") return async () => {throw new Error("injected-purge-failure");};
    const value = target[name]; return typeof value === "function" ? value.bind(target) : value;
  }});
  await assert.rejects(cleanupRoom({firestore: broken, database, roomId, nowMs}), /injected-purge/);
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().cleanupState, "deleting");
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs})).status, "cleaned");
});
test("checkpoint confirmado reciente y presencia activa conservan partida", async () => {
  const roomId = await seed({estado: "en_juego"});
  await firestore.doc(`partidas/${roomId}/runtime/authoritative`).update({actualizadaEn: Timestamp.fromMillis(nowMs)});
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs})).reason, "recent-firestore");
  await database.ref(`salas/${roomId}/presencia/host`).set({estado: "conectado", ts: 1});
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs: nowMs + 10 * DAY_MS})).reason, "connected-presence");
});
test("código reasignado no se borra; dos trabajadores pueden reintentar", async () => {
  const roomId = await seed();
  await firestore.doc("codigosSala/CLN234").set({partidaId: "another-room"});
  const results = await Promise.all([1, 2].map(() => cleanupRoom({firestore, database, roomId, nowMs})));
  assert.ok(results.some((r) => r.status === "cleaned"));
  assert.equal((await firestore.doc("codigosSala/CLN234").get()).data().partidaId, "another-room");
});
test("huérfana observada se limpia tras retención y entrega de evento duplicada no extiende plazo", async () => {
  const roomId = await seed();
  const room = (await firestore.doc(`partidas/${roomId}`).get()).data();
  await firestore.doc(`partidas/${roomId}`).delete();
  await observeDeletedRoom({firestore, roomId, room, observedAtMs: nowMs - 2 * DAY_MS});
  await observeDeletedRoom({firestore, roomId, room, observedAtMs: nowMs});
  const orphan = (await firestore.doc(`${ORPHANS}/${roomId}`).get()).data();
  assert.equal(orphan.actualizadaEn.toMillis(), nowMs - 2 * DAY_MS);
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs, orphan})).status, "cleaned");
});
test("barrido acotado avanza cursor y rechaza tamaños sin límite", async () => {
  await assert.rejects(sweepRooms({firestore, database, pageSize: 0}), /Invalid page size/);
  const result = await sweepRooms({firestore, database, pageSize: 1, nowMs, logger: {info() {}, error() {}}});
  assert.ok(result.examined <= 2);
  assert.equal((await firestore.doc("onlineMaintenance/roomCleanup").get()).exists, true);
});

test("cola conserva primer vencimiento sin escribir con cada actualización", async () => {
  const roomId = await seed();
  await enqueueRoomCleanup({firestore, roomId, nowMs});
  const queueRef = firestore.doc(`${QUEUE}/${roomId}`);
  const before = await queueRef.get();
  await firestore.doc(`partidas/${roomId}`).update({actualizadaEn: Timestamp.fromMillis(nowMs)});
  await enqueueRoomCleanup({firestore, roomId, nowMs});
  const after = await queueRef.get();
  assert.equal(after.updateTime.toMillis(), before.updateTime.toMillis());
  assert.equal((await cleanupRoom({firestore, database, roomId, nowMs})).status, "retained");
});

test("evento atrasado de una sala ya limpiada no vuelve a encolarla", async () => {
  const roomId = await seed();
  await enqueueRoomCleanup({firestore, roomId, nowMs});
  await cleanupRoom({firestore, database, roomId, nowMs});
  await enqueueRoomCleanup({firestore, roomId, nowMs});
  assert.equal((await firestore.doc(`${QUEUE}/${roomId}`).get()).exists, false);
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().cleanupCompleted, true);
});
