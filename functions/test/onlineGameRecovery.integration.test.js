"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {startServerMatch, publishServerOutbox, expireServerPhase, prepareServerRematch,
  leaveServerMatch, AUTHORITY_GATE, SERVER_RECOVERY_GRACE_MS} = require("../src/onlineGameService");
const {deadlineToken, projectServerGame, expirePhase} = require("../src/onlineGameCore");
const {archiveFinishedRoom, historyId} = require("../src/accountHistoryService");
const {repairOverdueServerGames, RECOVERY_CONTROL} = require("../src/onlineGameRecovery");
const {meterFirestore} = require("../../scripts/measure-server-v3.cjs");
let app, firestore, database, sequence = 0;
const rooms = [], accounts = new Set(), NOW = 1000000;
const logger = {info() {}, warn() {}, error() {}};
test.before(() => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:18081");
  assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, "127.0.0.1:19000");
  assert.equal(process.env.GCLOUD_PROJECT, "traidores-local");
  app = initializeApp({projectId: "traidores-local", databaseURL: "https://traidores-local-default-rtdb.firebaseio.com"}, "v3-recovery");
  firestore = getFirestore(app); database = getDatabase(app);
});
test.beforeEach(async () => { await firestore.doc(RECOVERY_CONTROL).delete(); });
test.afterEach(async () => {
  for (const uid of accounts) {
    await firestore.doc(`perfiles_publicos/${uid}`).delete();
    await firestore.recursiveDelete(firestore.doc(`cuentas/${uid}`));
  }
  accounts.clear();
  for (const roomId of rooms.splice(0)) {
    await firestore.recursiveDelete(firestore.doc(`partidas/${roomId}`));
    await database.ref(`onlineV3/${roomId}`).remove();
  }
});
test.after(async () => {
  await firestore.doc(AUTHORITY_GATE).delete(); await firestore.doc(RECOVERY_CONTROL).delete(); await deleteApp(app);
});
async function seed(prefix = "room", count = 5, mapKey = "pampa") {
  const roomId = `recovery-${prefix}-${++sequence}`; rooms.push(roomId);
  const room = firestore.doc(`partidas/${roomId}`), batch = firestore.batch();
  batch.set(firestore.doc(AUTHORITY_GATE), {enabled: true});
  batch.set(room, {estado: "esperando", hostId: "p0", hostActivoId: "p0", jugadoresEsperados: count,
    mapa: mapKey, codigoSala: "ABC234", configLobby: {presetRoles: "RECOMMENDED"}});
  for (let i = 0; i < count; i++) batch.set(room.collection("jugadores").doc(`p${i}`), {
    nombre: `J${i}`, listo: true, activoEnPartida: true, orden: i, protocolVersion: 3});
  await batch.commit();
  await startServerMatch({firestore, roomId, requesterId: "p0", nowMs: NOW, matchId: `match-${roomId}`, chooseRandomInt: () => 0});
  return roomId;
}
const state = async (roomId) => (await firestore.doc(`partidas/${roomId}/servidor/current`).get()).data();
const publish = (roomId, enqueueDeadline = async () => {}) => publishServerOutbox({firestore, database, roomId, enqueueDeadline});
const repair = (nowMs, options = {}) => repairOverdueServerGames({firestore, database,
  enqueueDeadline: async () => {}, logger, nowMs, ...options});
async function final(roomId) {
  const current = await state(roomId);
  Object.assign(current, {winner: "Cancelada", phase: "FINALIZADA", deadlineMs: null, phaseStartedAtMs: NOW + 50000, revision: current.revision + 1});
  const batch = firestore.batch();
  batch.set(firestore.doc(`partidas/${roomId}/servidor/current`), current);
  batch.update(firestore.doc(`partidas/${roomId}`), {estado: "finalizada", estadoPartida: projectServerGame(current).public});
  batch.set(firestore.doc(`partidas/${roomId}/serverOutbox/current`), {generation: current.generation,
    revision: current.revision, deliveredRevision: 0, token: deadlineToken(current),
    projection: projectServerGame(current), recoveryAtMs: current.phaseStartedAtMs + SERVER_RECOVERY_GRACE_MS});
  await batch.commit(); return current;
}

test("already-delivered publication costs one Firestore read and does not touch Tasks/RTDB", async () => {
  const roomId = await seed(); await publish(roomId);
  const {firestore: metered, measure, rows} = meterFirestore(firestore);
  const result = await measure("duplicate", () => publishServerOutbox({firestore: metered, roomId,
    database: {ref() {throw new Error("must-not-access-realtime");}}, enqueueDeadline() {throw new Error("must-not-enqueue");}}));
  assert.equal(result.published, false); assert.equal(rows.duplicate.reads, 1); assert.equal(rows.duplicate.writes, 0);
});

test("overdue server phase advances with all clients closed and the new-start gate disabled", async () => {
  const roomId = await seed(), current = await state(roomId);
  await publish(roomId); await firestore.doc(AUTHORITY_GATE).set({enabled: false});
  const result = await repair(current.deadlineMs + SERVER_RECOVERY_GRACE_MS);
  assert.equal(result.scanned, 1); assert.equal(result.advanced, 1); assert.equal(result.published, 1);
  assert.equal((await state(roomId)).phaseIndex, 1);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/public/fase`).get()).val(), "NOCHE");
  assert.equal((await repair(current.deadlineMs + SERVER_RECOVERY_GRACE_MS)).scanned, 0);
});

test("publication failure leaves the advanced state durable and retry publishes without a second advance", async () => {
  const roomId = await seed(), current = await state(roomId);
  const nowMs = current.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  const result = await repair(nowMs, {enqueueDeadline: async () => {throw new Error("queue-offline");}});
  assert.equal(result.advanced, 1);
  assert.equal(result.failed, 1); assert.equal((await state(roomId)).phaseIndex, 1);
  await publish(roomId);
  assert.equal((await state(roomId)).phaseIndex, 1);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/public/phaseIndex`).get()).val(), 1);
});

test("pending final publication without a deadline is repaired and removed from the overdue query", async () => {
  const roomId = await seed(); await publish(roomId); const finished = await final(roomId);
  const result = await repair(finished.phaseStartedAtMs + SERVER_RECOVERY_GRACE_MS);
  assert.equal(result.advanced, 0); assert.equal(result.published, 1);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/public/fase`).get()).val(), "FINALIZADA");
  assert.equal((await firestore.doc(`partidas/${roomId}/serverOutbox/current`).get()).data().recoveryAtMs, null);
  assert.equal((await repair(finished.phaseStartedAtMs + SERVER_RECOVERY_GRACE_MS)).scanned, 0);
});

test("pending rematch publication unlocks the lobby and clears old secrets without any connected player", async () => {
  const roomId = await seed(); await publish(roomId); const finished = await final(roomId); await publish(roomId);
  const nowMs = finished.phaseStartedAtMs + 1;
  await prepareServerRematch({firestore, roomId, requesterId: "p0", matchId: finished.matchId,
    nextMatchId: "recovery-rematch", nowMs});
  const result = await repair(nowMs + SERVER_RECOVERY_GRACE_MS);
  assert.equal(result.published, 1); assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().authorityMode, "lobby");
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private`).get()).exists(), false);
});

test("bounded batches resume beyond a failed older room instead of starving later rooms", async () => {
  const bad = await seed("00-bad"), good1 = await seed("01-good"), good2 = await seed("02-good");
  await firestore.doc(`partidas/${bad}/serverOutbox/current`).update({"token.phaseIndex": -1});
  const nowMs = (await state(bad)).deadlineMs + SERVER_RECOVERY_GRACE_MS;
  const first = await repair(nowMs, {pageSize: 1, maxPages: 1});
  assert.equal(first.failed, 1); assert.equal(first.hasMore, true);
  assert.equal((await repair(nowMs, {pageSize: 1, maxPages: 1})).advanced, 1);
  assert.equal((await repair(nowMs, {pageSize: 1, maxPages: 1})).advanced, 1);
  assert.equal((await state(good1)).phaseIndex, 1); assert.equal((await state(good2)).phaseIndex, 1);
  assert.equal((await repair(nowMs, {pageSize: 1, maxPages: 1})).scanned, 0);
  assert.equal((await firestore.doc(RECOVERY_CONTROL).get()).data().cursor, null);
});

test("a departure from a rematch lobby remains recoverable after its prior publication cleared the timer", async () => {
  const roomId = await seed(); await publish(roomId); const finished = await final(roomId); await publish(roomId);
  const nowMs = finished.phaseStartedAtMs + 1;
  const rematch = await prepareServerRematch({firestore, roomId, requesterId: "p0", matchId: finished.matchId,
    nextMatchId: "leave-rematch", nowMs});
  await publish(roomId);
  await leaveServerMatch({firestore, roomId, requesterId: "p1", matchId: rematch.matchId, nowMs: nowMs + 1});
  const result = await repair(nowMs + 1 + SERVER_RECOVERY_GRACE_MS);
  assert.equal(result.published, 1); assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().authorityMode, "lobby");
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/permissions/p1`).get()).exists(), false);
});

test("watchdog racing a deadline task advances exactly once", async () => {
  const roomId = await seed(), current = await state(roomId); await publish(roomId);
  const nowMs = current.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  await Promise.all([repair(nowMs), (async () => {
    await expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs}); await publish(roomId);
  })()]);
  assert.equal((await state(roomId)).phaseIndex, 1);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/public/phaseIndex`).get()).val(), 1);
});

test("time spent processing a recovery batch does not shorten the next phase", async () => {
  const roomId = await seed(), current = await state(roomId);
  const nowMs = current.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  let elapsed = 0;
  await repair(nowMs, {monotonicNow: () => {elapsed += 1000; return elapsed;}});
  const updated = await state(roomId);
  assert.ok(updated.phaseStartedAtMs > nowMs);
  assert.equal(updated.deadlineMs - updated.phaseStartedAtMs, updated.timing.nightSeconds * 1000);
});

test("an unexpected outbox path is logged and cannot starve the valid namespace", async () => {
  const roomId = await seed(), current = await state(roomId);
  const invalid = firestore.doc("otherRooms/invalid/serverOutbox/current");
  const nowMs = current.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  await invalid.set({recoveryAtMs: nowMs});
  try {
    assert.equal((await repair(nowMs, {pageSize: 1, maxPages: 1})).failed, 1);
    assert.equal((await repair(nowMs, {pageSize: 1, maxPages: 1})).advanced, 1);
    assert.equal((await state(roomId)).phaseIndex, 1);
  } finally { await invalid.delete(); }
});

test("future deadlines are excluded; idle polling has a bounded document cost", async () => {
  await seed();
  const {firestore: metered, measure, rows} = meterFirestore(firestore);
  assert.equal((await measure("idle", () => repair(NOW, {firestore: metered}))).scanned, 0);
  // Control document + empty query. No write or whole-room scan while idle.
  assert.equal(rows.idle.reads, 2); assert.equal(rows.idle.writes, 0);
});


async function installState(roomId, current) {
  const batch = firestore.batch();
  batch.set(firestore.doc(`partidas/${roomId}/servidor/current`), current);
  batch.set(firestore.doc(`partidas/${roomId}/serverOutbox/current`), {
    generation: current.generation, revision: current.revision, deliveredRevision: 0,
    token: deadlineToken(current), projection: projectServerGame(current),
    recoveryAtMs: current.deadlineMs + SERVER_RECOVERY_GRACE_MS});
  await batch.commit();
}
async function seedDeserterWindow() {
  const roomId = await seed("deserter", 14), current = await state(roomId);
  let towns = 0;
  for (const p of current.players) p.alive = ["desertor", "asesino", "mercenario"].includes(p.role.key) ||
    p.role.key === "aldeano" && towns++ < 3;
  Object.assign(current, {deserterTeam: "Pueblo", round: 4, phase: "NOCHE", phaseIndex: 1, afkEnabled: false});
  const window = expirePhase(current, deadlineToken(current), current.deadlineMs).state;
  assert.equal(window.phase, "DESERTOR_RECONSIDERACION");
  await installState(roomId, window); return {roomId, window};
}

test("Desertor parity-breaking departure cannot reenter the overdue scheduler indefinitely", async () => {
  const {roomId, window} = await seedDeserterWindow();
  await leaveServerMatch({firestore, roomId, matchId: window.matchId,
    requesterId: window.players.find((p) => p.role.key === "mercenario").uid, nowMs: window.phaseStartedAtMs + 1});
  const resumed = await state(roomId);
  assert.equal(resumed.phase, "DIA_DEBATE"); assert.equal(resumed.deserterUsed, false);
  assert.ok(resumed.phaseIndex > window.phaseIndex); await publish(roomId);
  const nowMs = window.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  const {firestore: metered, measure, rows} = meterFirestore(firestore);
  for (let i = 0; i < 2; i++) {
    const result = await measure("idle", () => repair(nowMs, {firestore: metered}));
    assert.equal(result.scanned, 0); assert.equal(result.advanced, 0);
  }
  assert.equal(rows.idle.writes, 0);
});

test("recovery advances a parity-broken window once and old Tasks cannot advance the new phase", async () => {
  const {roomId, window} = await seedDeserterWindow();
  window.players.find((p) => p.role.key === "mercenario").alive = false;
  await installState(roomId, window);
  const nowMs = window.deadlineMs + SERVER_RECOVERY_GRACE_MS;
  const result = await repair(nowMs); assert.equal(result.advanced, 1); assert.equal(result.published, 1);
  const resumed = await state(roomId);
  assert.equal(resumed.phase, "DIA_DEBATE"); assert.equal(resumed.deserterUsed, false);
  assert.equal(resumed.deadlineMs - resumed.phaseStartedAtMs, resumed.timing.discussionSeconds * 1000);
  assert.equal((await expireServerPhase({firestore, roomId, token: deadlineToken(window), nowMs})).changed, false);
  assert.equal((await repair(nowMs)).scanned, 0);
});

test("malformed stuck recovery logs the specific alarm and never rewrites game or outbox", async () => {
  const {roomId, window} = await seedDeserterWindow();
  window.players.find((p) => p.role.key === "mercenario").alive = false;
  window.deserterReturn = null; await installState(roomId, window);
  const before = (await firestore.doc(`partidas/${roomId}/serverOutbox/current`).get()).data();
  const errors = [], {firestore: metered, measure, rows} = meterFirestore(firestore);
  const result = await measure("stuck", () => repair(window.deadlineMs + SERVER_RECOVERY_GRACE_MS, {
    firestore: metered, logger: {...logger, error: (...args) => errors.push(args)},
    database: {ref() {throw new Error("stuck must not publish");}}}));
  assert.equal(result.advanced, 0); assert.equal(result.published, 0); assert.equal(result.failed, 1);
  assert.equal(errors[0][0], "online_v3_recovery_stuck");
  assert.equal(rows.stuck.writes, 1); // Only the batch cursor; zero game/outbox writes.
  assert.deepEqual(await state(roomId), window);
  assert.deepEqual((await firestore.doc(`partidas/${roomId}/serverOutbox/current`).get()).data(), before);
});

test("REPARTO random fallback persists once and is reused across Firestore transaction retries", async () => {
  const roomId = await seed("random", 14), current = await state(roomId); let draws = 0;
  // The aborted read-only attempt and the real attempt see the same persisted snapshot.
  const retryingFirestore = {
    doc: (...args) => firestore.doc(...args),
    runTransaction: async (callback) => {
      await firestore.runTransaction(async (tx) => callback({get: (ref) => tx.get(ref), set() {}, update() {}}));
      return firestore.runTransaction(callback);
    },
  };
  const result = await expireServerPhase({firestore: retryingFirestore, roomId, token: deadlineToken(current),
    nowMs: current.deadlineMs, chooseRandomInt: (max) => {assert.equal(max, 2); draws++; return 1;}});
  assert.equal(result.transactionAttempts, 2); assert.equal(draws, 1);
  const next = await state(roomId); assert.equal(next.deserterTeam, "Traidores"); assert.equal(next.phase, "NOCHE");
  await publish(roomId);
  const pub = (await database.ref(`onlineV3/${roomId}/snapshot/public`).get()).val();
  assert.equal(pub.desertorBando, undefined);
  const desertor = next.players.find((p) => p.role.key === "desertor");
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private/${desertor.uid}/desertorBando`).get()).val(), "Traidores");
  assert.equal((await expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: next.deadlineMs,
    chooseRandomInt() {throw new Error("duplicate must not draw");}})).changed, false);
});


test("dead Villager and already expelled Jester departures persist one defeat per account with zero wins", async () => {
  const roomId = await seed("departure-history", 8, "medieval"), initial = await state(roomId);
  const jester = initial.players.find((p) => p.role.key === "bufon"), villager = initial.players.find((p) => p.role.key === "aldeano");
  Object.assign(initial, {phase: "RECUENTO_VOTOS", afkEnabled: false, eliminationUid: jester.uid});
  let current = expirePhase(initial, deadlineToken(initial), initial.deadlineMs).state;
  const dead = current.players.find((p) => p.uid === villager.uid); dead.alive = false; dead.deathCause = "NIGHT";
  assert.ok(current.specialVictories.some((v) => v.uid === jester.uid));
  await installState(roomId, current);
  for (const p of [villager, jester]) {
    accounts.add(p.uid); await firestore.doc(`perfiles_publicos/${p.uid}`).set({publicId: `${p.order + 1}`});
    await leaveServerMatch({firestore, roomId, requesterId: p.uid, matchId: current.matchId, nowMs: current.phaseStartedAtMs + 1});
  }
  current = await state(roomId);
  current.players.filter((p) => ["asesino", "espia"].includes(p.role.key)).forEach((p) => {p.alive = false;});
  current.phase = "RESULTADO"; current.eliminationUid = null; await installState(roomId, current);
  await expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: current.deadlineMs});
  const room = (await firestore.doc(`partidas/${roomId}`).get()).data();
  assert.equal(room.estadoPartida.ganador, "Pueblo");
  const args = {firestore, roomId, room, finishedAtMs: current.deadlineMs};
  assert.equal(await archiveFinishedRoom(args), 2); assert.equal(await archiveFinishedRoom(args), 0);
  for (const uid of accounts) {
    const record = (await firestore.doc(`cuentas/${uid}/historial/${historyId(`online:${current.matchId}`)}`).get()).data();
    assert.equal(record.won, false); assert.equal(record.contabilizada, true);
    const summary = (await firestore.doc(`cuentas/${uid}`).get()).data();
    assert.equal(summary.partidas, 1); assert.equal(summary.victorias, 0);
    assert.deepEqual((await firestore.doc(`perfiles_publicos/${uid}`).get()).data().estadisticasPerfil, {partidas: 1, victorias: 0});
  }
});
