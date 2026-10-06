"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp, deleteApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {startServerMatch, submitServerAction, expireServerPhase, recoverServerPhase, publishServerOutbox, prepareServerRematch, leaveServerMatch,
  AUTHORITY_GATE, RECOVERY_GRACE_MS} = require("../src/onlineGameService");
const {deadlineToken, projectServerGame} = require("../src/onlineGameCore");
const {archiveFinishedRoom, historyId} = require("../src/accountHistoryService");
const {createServerEndpoints} = require("../src/onlineGameFunctions");
const {cleanupRoom} = require("../src/onlineRoomCleanupService");
const {DAY_MS} = require("../src/onlineRoomCleanupPolicy");
const {requestLimitId, CAPACITY, REFILL_MS} = require("../src/onlineRequestLimiter");
const projectId = "traidores-local";
let app, firestore, database;
const rooms = [], accounts = [];
const limitedUids = new Set(["p1"]);
const now = 1000000;
let ids = 0;
test.before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST?.startsWith("127.0.0.1:"), "Use isolated local Firestore");
  assert.ok(process.env.FIREBASE_DATABASE_EMULATOR_HOST?.startsWith("127.0.0.1:"), "Use isolated local RTDB");
  app = initializeApp({projectId, databaseURL: `https://${projectId}-default-rtdb.firebaseio.com`}, "server-game-integration");
  firestore = getFirestore(app); database = getDatabase(app);
});
test.after(async () => {
  for (const room of rooms) { await firestore.recursiveDelete(firestore.doc(`partidas/${room}`));
    await database.ref(`onlineV3/${room}`).remove(); await database.ref(`salas/${room}`).remove(); }
  for (const uid of accounts) { await firestore.doc(`perfiles_publicos/${uid}`).delete(); await firestore.recursiveDelete(firestore.doc(`cuentas/${uid}`)); }
  await firestore.doc(AUTHORITY_GATE).delete();
  for (const uid of limitedUids) await firestore.doc(`onlineRequestLimits/${requestLimitId(uid)}`).delete();
  await deleteApp(app);
});
async function seed(count = 5, protocol = 3) {
  const roomId = `server-v3-${Date.now()}-${++ids}`; rooms.push(roomId);
  const batch = firestore.batch();
  batch.set(firestore.doc(AUTHORITY_GATE), {enabled: true});
  batch.set(firestore.doc(`partidas/${roomId}`), {estado: "esperando", hostId: "host", hostActivoId: "host",
    jugadoresEsperados: count, mapa: "pampa", codigoSala: "ABC234", configLobby: {presetRoles: "RECOMMENDED"}});
  for (let i = 0; i < count; i++) batch.set(firestore.doc(`partidas/${roomId}/jugadores/${i ? `p${i}` : "host"}`), {
    nombre: `J${i}`, listo: true, activoEnPartida: true, orden: i, protocolVersion: protocol});
  await batch.commit(); return roomId;
}
async function state(roomId) { return (await firestore.doc(`partidas/${roomId}/servidor/current`).get()).data(); }
async function begin(roomId) { return startServerMatch({firestore, roomId, requesterId: "host", nowMs: now,
  matchId: `match-${roomId}`, chooseRandomInt: () => 0}); }
async function expire(roomId, current) { return expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: current.deadlineMs}); }
async function action(roomId, actor, current, type, extra = {}) {
  return submitServerAction({firestore, roomId, requesterId: actor, nowMs: current.phaseStartedAtMs + 1,
    action: {matchId: current.matchId, phaseIndex: current.phaseIndex, requestId: `request_${++ids}`, action: type, ...extra}});
}

test("V3 requires compatible clients, hides secrets from lobby and preserves start retries", async () => {
  const old = await seed(5, 2);
  await assert.rejects(begin(old), (e) => e.code === "incompatible-client");
  const roomId = await seed(5);
  await firestore.doc(`partidas/${roomId}`).update({hostActivoId: "p1"});
  await assert.rejects(startServerMatch({firestore, roomId, requesterId: "intruder"}), (e) => e.code === "host-required");
  await assert.rejects(startServerMatch({firestore, roomId, requesterId: "p1"}), (e) => e.code === "host-required");
  const initial = await begin(roomId), retry = await begin(roomId);
  assert.equal(initial.matchId, retry.matchId); assert.equal(retry.status, "already_started");
  const room = (await firestore.doc(`partidas/${roomId}`).get()).data();
  assert.equal(room.authorityMode, "server");
  assert.ok(!JSON.stringify(room).includes("rolKey"));
  assert.equal((await firestore.collection(`partidas/${roomId}/repartos`).get()).size, 0);
});

test("durable outbox survives enqueue failure and updates only acting player's private projection", async () => {
  const roomId = await seed(8); await begin(roomId);
  const queued = new Map();
  let fail = true;
  const enqueueDeadline = async (task) => {
    if (fail) throw new Error("temporary-task-outage");
    if (queued.has(task.id)) { const e = new Error("exists"); e.code = 6; throw e; }
    queued.set(task.id, task);
  };
  await assert.rejects(publishServerOutbox({firestore, database, roomId, enqueueDeadline}), /temporary-task-outage/);
  assert.ok(await state(roomId)); fail = false;
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline});
  assert.equal(queued.size, 1);
  await expire(roomId, await state(roomId));
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline}); assert.equal(queued.size, 2);
  const before = (await database.ref(`onlineV3/${roomId}/snapshot/public`).get()).val();
  const current = await state(roomId), killer = current.players.find((p) => p.role.key === "asesino"), target = current.players.find((p) => p.role.key === "aldeano");
  await action(roomId, killer.uid, current, "matar", {targetUid: target.uid});
  const publication = await publishServerOutbox({firestore, database, roomId, enqueueDeadline});
  assert.equal(publication.changedPublic, false); assert.equal(publication.changedPrivate, 1);
  assert.equal(queued.size, 2); // Accepted secret action does not enqueue another timer.
  assert.deepEqual((await database.ref(`onlineV3/${roomId}/snapshot/public`).get()).val(), before);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private/${killer.uid}/accionesConfirmadas/0/targetUid`).get()).val(), target.uid);
  assert.equal((await publishServerOutbox({firestore, database, roomId, enqueueDeadline})).published, false);
});

test("concurrent same request, task retries and old deadlines resolve only once", async () => {
  const roomId = await seed(8); await begin(roomId); await expire(roomId, await state(roomId));
  const current = await state(roomId), killer = current.players.find((p) => p.role.key === "asesino"), target = current.players.find((p) => p.role.key === "aldeano");
  const data = {firestore, roomId, requesterId: killer.uid, nowMs: current.phaseStartedAtMs + 1,
    action: {matchId: current.matchId, phaseIndex: current.phaseIndex, requestId: "concurrent_request", action: "matar", targetUid: target.uid}};
  const results = await Promise.all([submitServerAction(data), submitServerAction(data)]);
  assert.equal(results.filter((r) => r.changed).length, 1);
  const ended = await Promise.all([expire(roomId, current), expire(roomId, current)]);
  assert.equal(ended.filter((r) => r.changed).length, 1);
  assert.equal((await state(roomId)).phaseIndex, current.phaseIndex + 1);
  assert.equal((await expire(roomId, current)).changed, false);
});

test("server deadlines finish without host advancement and final history is idempotent", async () => {
  const roomId = await seed(5); await begin(roomId);
  let current = await state(roomId);
  for (const p of current.players) {
    accounts.push(p.uid);
    await firestore.doc(`perfiles_publicos/${p.uid}`).set({publicId: `${p.order + 1}`});
  }
  // Supply valid nightly intentions once, then exercise terminal vote AFK. No host advancement.
  for (let i = 0; i < 60 && !current.winner; i++) {
    if (current.phase === "NOCHE") for (const p of current.players.filter((p) => p.alive)) {
      const target = current.players.find((t) => t.alive && t.uid !== p.uid && t.role.team === "Pueblo");
      if (p.role.key === "asesino" && target) await action(roomId, p.uid, current, "matar", {targetUid: target.uid});
      if (p.role.key === "medico") await action(roomId, p.uid, current, "salvar", {targetUid: p.uid});
      if (p.role.key === "policia" && target) await action(roomId, p.uid, current, "investigar", {targetUid: target.uid});
    }
    await expire(roomId, current); current = await state(roomId);
  }
  assert.ok(current.winner);
  const room = (await firestore.doc(`partidas/${roomId}`).get()).data();
  assert.equal(room.estadoPartida.ganador, current.winner);
  const saved = await archiveFinishedRoom({firestore, roomId, room, finishedAtMs: current.phaseStartedAtMs});
  if (current.winner === "Cancelada") assert.equal(saved, 0);
  else { assert.equal(saved, 5); assert.equal(await archiveFinishedRoom({firestore, roomId, room, finishedAtMs: current.phaseStartedAtMs}), 0); }
});

test("action racing a deadline is committed once before resolution or rejected as stale", async () => {
  const roomId = await seed(8); await begin(roomId); await expire(roomId, await state(roomId));
  const current = await state(roomId), killer = current.players.find((p) => p.role.key === "asesino"),
    target = current.players.find((p) => p.role.key === "aldeano");
  const results = await Promise.allSettled([
    action(roomId, killer.uid, current, "matar", {targetUid: target.uid}), expire(roomId, current),
  ]);
  assert.equal(results[1].status, "fulfilled"); assert.equal(results[1].value.changed, true);
  const final = await state(roomId);
  assert.equal(final.phaseIndex, current.phaseIndex + 1);
  if (results[0].status === "fulfilled") assert.equal(final.players.find((p) => p.uid === target.uid).deathCause, "NIGHT");
  else assert.equal(results[0].reason.code, "stale-phase");
});

test("outbox coalesces unprocessed actions and repairs a crash after task enqueue", async () => {
  const roomId = await seed(8); await begin(roomId);
  const tasks = new Set();
  const enqueueDeadline = async ({id}) => {
    if (tasks.has(id)) { const error = new Error("duplicate"); error.code = "functions/task-already-exists"; throw error; }
    tasks.add(id);
  };
  const interruptedDatabase = {ref: (path) => {
    const real = database.ref(path);
    return {get: () => real.get(), child: (key) => real.child(key),
      parent: {transaction: () => Promise.reject(new Error("write-outage"))},
      transaction: () => Promise.reject(new Error("write-outage"))};
  }};
  await assert.rejects(publishServerOutbox({firestore, database: interruptedDatabase, roomId, enqueueDeadline}), /write-outage/);
  assert.equal(tasks.size, 1);
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline}); assert.equal(tasks.size, 1);
  await expire(roomId, await state(roomId));
  const current = await state(roomId), killer = current.players.find((p) => p.role.key === "asesino"),
    doctor = current.players.find((p) => p.role.key === "medico"), target = current.players.find((p) => p.role.key === "aldeano");
  await action(roomId, killer.uid, current, "matar", {targetUid: target.uid});
  await action(roomId, doctor.uid, current, "salvar", {targetUid: doctor.uid});
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline});
  const published = (await database.ref(`onlineV3/${roomId}/snapshot/private`).get()).val();
  assert.equal(published[killer.uid].accionesConfirmadas[0].action, "matar");
  assert.equal(published[doctor.uid].accionesConfirmadas[0].action, "salvar");
  assert.equal(tasks.size, 2);
});

test("admin rollout gate blocks new starts without interrupting existing games", async () => {
  const active = await seed(5); await begin(active);
  const waiting = await seed(5);
  await firestore.doc(AUTHORITY_GATE).delete();
  await assert.rejects(begin(waiting), (e) => e.code === "server-mode-unavailable");
  assert.equal((await begin(active)).status, "already_started");
  assert.equal((await expire(active, await state(active))).changed, true);
});

test("any member can recover after grace, never an outsider or a client-supplied deadline", async () => {
  const roomId = await seed(5); await begin(roomId);
  const current = await state(roomId);
  const data = {firestore, roomId, requesterId: "p1", matchId: current.matchId, phaseIndex: current.phaseIndex};
  await assert.rejects(recoverServerPhase({...data, requesterId: "outsider"}), (e) => e.code === "not-a-member");
  await assert.rejects(recoverServerPhase({...data, matchId: "old"}), (e) => e.code === "stale-match");
  const early = await expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: current.deadlineMs - 1});
  assert.equal(early.retryAfterMs, 1); assert.equal(early.changed, false);
  assert.equal((await recoverServerPhase({...data, nowMs: current.deadlineMs + RECOVERY_GRACE_MS - 1})).status, "waiting");
  const concurrent = await Promise.all([recoverServerPhase({...data, nowMs: current.deadlineMs + RECOVERY_GRACE_MS}),
    expireServerPhase({firestore, roomId, token: deadlineToken(current), nowMs: current.deadlineMs + RECOVERY_GRACE_MS})]);
  assert.equal((await state(roomId)).phaseIndex, current.phaseIndex + 1);
  assert.equal(concurrent.filter((r) => r.status === "advanced" || r.changed === true).length, 1);
  assert.equal((await recoverServerPhase({...data, nowMs: current.deadlineMs + RECOVERY_GRACE_MS})).status, "current");
});

test("task worker retries an early delivery and repairs publication after its successful commit", async () => {
  const roomId = await seed(5); await begin(roomId);
  const original = await state(roomId), logs = [], tasks = [];
  let clock = original.deadlineMs - 1, fail = true;
  const worker = createServerEndpoints({getFirestore: () => firestore, getDatabase: () => database,
    now: () => clock, logger: {info: (...data) => logs.push(data), warn: (...data) => logs.push(data)},
    enqueueDeadline: async (task) => {if (fail) throw new Error("queue-offline"); tasks.push(task);}}).resolverFaseV3;
  const request = {data: {roomId, token: deadlineToken(original)}};
  await assert.rejects(worker.run(request), (e) => e.code === "unavailable");
  assert.equal((await state(roomId)).phaseIndex, original.phaseIndex);
  clock++;
  await assert.rejects(worker.run(request), /queue-offline/);
  assert.equal((await state(roomId)).phaseIndex, original.phaseIndex + 1);
  assert.equal((await firestore.doc(`partidas/${roomId}/serverOutbox/current`).get()).data().deliveredRevision, 0);
  fail = false;
  await worker.run(request);
  const publicState = (await database.ref(`onlineV3/${roomId}/snapshot/public`).get()).val();
  assert.equal(publicState.fase, "NOCHE"); assert.equal(tasks.length, 1);
  await worker.run(request); assert.equal(tasks.length, 1);
  assert.ok(logs.some(([name, fields]) => name === "online_v3_operation" && fields.published === true && fields.changed === false));
  assert.ok(!JSON.stringify(logs).includes("rolKey"));
});

test("recovery callable repairs a committed phase after a publication outage, without host or a second advance", async () => {
  const roomId = await seed(5); await begin(roomId);
  const current = await state(roomId);
  let fail = true, clock = current.deadlineMs + RECOVERY_GRACE_MS - 1;
  const callable = createServerEndpoints({getFirestore: () => firestore, getDatabase: () => database,
    now: () => clock, logger: {info() {}, warn() {}},
    enqueueDeadline: async () => {if (fail) throw new Error("queue-offline");}}).recuperarFaseV3;
  const request = {auth: {uid: "p1"}, data: {roomId, matchId: current.matchId, phaseIndex: current.phaseIndex}};
  assert.equal((await callable.run(request)).status, "waiting");
  clock++;
  await assert.rejects(callable.run(request), (e) => e.code === "unavailable");
  assert.equal((await state(roomId)).phaseIndex, current.phaseIndex + 1);
  fail = false;
  const retry = await callable.run(request);
  assert.equal(retry.status, "current"); assert.ok(!Object.hasOwn(retry, "transactionAttempts"));
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/public/fase`).get()).val(), "NOCHE");
});

test("V3 cleanup retains active matches, pending publications and connected members; then removes secrets safely", async () => {
  const roomId = await seed(5); await begin(roomId);
  const old = Date.now() - 8 * DAY_MS;
  await firestore.doc(`partidas/${roomId}`).update({actualizadaEn: Timestamp.fromMillis(old)});
  assert.equal((await cleanupRoom({firestore, database, roomId})).reason, "server-match-active");
  await firestore.doc(`partidas/${roomId}/servidor/current`).update({winner: "Cancelada", phase: "FINALIZADA",
    deadlineMs: null, phaseStartedAtMs: old});
  await firestore.doc(`partidas/${roomId}`).update({estado: "finalizada", "estadoPartida.ganador": "Cancelada"});
  assert.equal((await cleanupRoom({firestore, database, roomId})).reason, "server-publication-pending");
  await firestore.doc(`partidas/${roomId}/serverOutbox/current`).update({deliveredRevision: 1});
  await database.ref(`onlineV3/${roomId}`).set({snapshot: {public: {fase: "FINALIZADA"},
    private: {host: {rolKey: "alcalde"}}, permissions: {host: {member: true}}},
    presence: {host: {estado: "conectado", actualizadaEn: old}}});
  assert.equal((await cleanupRoom({firestore, database, roomId})).reason, "connected-presence");
  await database.ref(`onlineV3/${roomId}/presence/host`).update({estado: "desconectado"});
  assert.equal((await cleanupRoom({firestore, database, roomId})).status, "cleaned");
  const tombstone = (await database.ref(`onlineV3/${roomId}`).get()).val();
  assert.equal(tombstone.snapshot.cleanupState, "deleting");
  assert.equal(tombstone.snapshot.private, undefined);
  assert.equal((await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {throw new Error("must-not-enqueue");}})).published, false);
});

test("invalid gameplay attempts consume a shared UID bucket across concurrent callable instances", async () => {
  const roomId = await seed(5); await begin(roomId);
  const current = await state(roomId), uid = `outsider-${roomId}`; limitedUids.add(uid);
  let clock = current.phaseStartedAtMs + 1;
  const build = () => createServerEndpoints({getFirestore: () => firestore, getDatabase: () => database,
    now: () => clock, enqueueDeadline: async () => {}, logger: {info() {}, warn() {}}}).accionPartidaV3;
  const a = build(), b = build();
  const request = {auth: {uid}, data: {roomId, matchId: current.matchId, phaseIndex: current.phaseIndex,
    requestId: "invalid_attempt", action: "role_ack"}};
  const results = await Promise.allSettled(Array.from({length: CAPACITY}, (_, i) => (i % 2 ? a : b).run(request)));
  assert.ok(results.every((r) => r.status === "rejected" && r.reason.code === "permission-denied"));
  await assert.rejects(a.run(request), (e) => e.code === "resource-exhausted" && e.details.reason === "request-rate-limit");
  assert.deepEqual(await state(roomId), current);
  clock += REFILL_MS;
  await assert.rejects(b.run(request), (e) => e.code === "permission-denied");
  await assert.rejects(a.run(request), (e) => e.code === "resource-exhausted");
});

test("leaving is idempotent, revokes secrets, and cannot overwrite ABANDONO when a night target departs", async () => {
  const roomId = await seed(8); await begin(roomId); await expire(roomId, await state(roomId));
  const current = await state(roomId), killer = current.players.find((p) => p.role.key === "asesino"),
    victim = current.players.find((p) => p.role.key === "aldeano");
  await action(roomId, killer.uid, current, "matar", {targetUid: victim.uid});
  const request = {firestore, roomId, requesterId: victim.uid, matchId: current.matchId, nowMs: current.phaseStartedAtMs + 2};
  const results = await Promise.all([leaveServerMatch(request), leaveServerMatch(request)]);
  assert.deepEqual(results.map((r) => r.status).sort(), ["already_left", "left"]);
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {}});
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private/${victim.uid}`).get()).exists(), false);
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/permissions/${victim.uid}/member`).get()).val(), false);
  await expire(roomId, await state(roomId));
  assert.equal((await state(roomId)).players.find((p) => p.uid === victim.uid).deathCause, "ABANDONO");
  await assert.rejects(action(roomId, victim.uid, await state(roomId), "role_ack"), (e) => e.code === "not-a-member");
});

test("rematch archives before reset, relocks until publication and makes old tasks/actions harmless", async () => {
  const roomId = await seed(5); await begin(roomId);
  const old = await state(roomId), oldToken = deadlineToken(old);
  old.winner = "Cancelada"; old.phase = "FINALIZADA"; old.deadlineMs = null;
  await firestore.doc(`partidas/${roomId}/servidor/current`).set(old);
  await firestore.doc(`partidas/${roomId}`).update({estado: "finalizada", "estadoPartida.ganador": "Cancelada"});
  const data = {firestore, roomId, requesterId: "host", matchId: old.matchId, nextMatchId: "next-real-match", nowMs: now + 50000};
  await assert.rejects(prepareServerRematch({...data, requesterId: "p1"}), (e) => e.code === "host-required");
  await assert.rejects(prepareServerRematch(data), (e) => e.code === "publication-pending");
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {}});
  await database.ref(`onlineV3/${roomId}`).update({
    "chat/traidores/host_0": {actorUid: "host", matchId: old.matchId, text: "old team secret", ts: now},
    "chatRate/host": {messageId: "host_0", slot: 0, channel: "traidores", ts: now},
  });
  const prepared = await prepareServerRematch(data);
  assert.equal(prepared.matchId, "next-real-match"); assert.equal(prepared.generation, 2);
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().authorityMode, "server");
  await assert.rejects(begin(roomId), (e) => e.code === "publication-pending");
  assert.equal((await prepareServerRematch(data)).status, "already_prepared");
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {throw new Error("no-lobby-task");}});
  assert.equal((await firestore.doc(`partidas/${roomId}`).get()).data().authorityMode, "lobby");
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/private`).get()).exists(), false);
  assert.equal((await database.ref(`onlineV3/${roomId}/chat`).get()).exists(), false);
  assert.equal((await database.ref(`onlineV3/${roomId}/chatRate`).get()).exists(), false);
  const members = await firestore.collection(`partidas/${roomId}/jugadores`).get();
  assert.ok(members.docs.every((p) => p.data().listo === false));
  await leaveServerMatch({...data, matchId: prepared.matchId, requesterId: "p1"});
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {}});
  assert.equal((await database.ref(`onlineV3/${roomId}/snapshot/permissions/p1`).get()).exists(), false);
  // Rejoin to keep the expected player count, then start a clean match.
  const batch = firestore.batch();
  for (const member of members.docs) batch.update(member.ref, {listo: true, activoEnPartida: true});
  await batch.commit();
  const next = await begin(roomId), newState = await state(roomId);
  assert.equal(next.matchId, "next-real-match"); assert.equal(newState.round, 1);
  assert.equal(newState.deserterUsed, false); assert.equal(newState.oracleUsed, false);
  assert.ok(newState.players.every((p) => p.nightAfk === 0 && p.lastSilencedRound === null));
  assert.equal((await expireServerPhase({firestore, roomId, token: oldToken, nowMs: now + 50001})).changed, false);
  await assert.rejects(submitServerAction({firestore, roomId, requesterId: "host", action: {
    matchId: old.matchId, phaseIndex: 0, requestId: "old_request", action: "role_ack"}, nowMs: now + 1}), (e) => e.code === "stale-match");
});

test("a failed history archive prevents rematch reset and successful retry preserves all account records", async () => {
  const roomId = await seed(5); await begin(roomId);
  const final = await state(roomId);
  final.winner = "Pueblo"; final.phase = "FINALIZADA"; final.deadlineMs = null; final.revision++;
  const projection = projectServerGame(final);
  const batch = firestore.batch();
  batch.set(firestore.doc(`partidas/${roomId}/servidor/current`), final);
  batch.set(firestore.doc(`partidas/${roomId}/serverOutbox/current`), {generation: final.generation,
    revision: final.revision, deliveredRevision: 0, projection, token: deadlineToken(final)});
  batch.update(firestore.doc(`partidas/${roomId}`), {estado: "finalizada", estadoPartida: projection.public});
  for (const p of final.players) {
    accounts.push(p.uid);
    batch.set(firestore.doc(`perfiles_publicos/${p.uid}`), {publicId: `${p.order + 1}`});
  }
  await batch.commit();
  await publishServerOutbox({firestore, database, roomId, enqueueDeadline: async () => {}});
  const request = {firestore, roomId, requesterId: "host", matchId: final.matchId, nowMs: now + 60000};
  await assert.rejects(prepareServerRematch({...request, archiveResult: async () => {throw new Error("history-offline");}}), /history-offline/);
  assert.equal((await state(roomId)).phase, "FINALIZADA");
  await prepareServerRematch(request);
  for (const p of final.players) {
    const record = (await firestore.doc(`cuentas/${p.uid}/historial/${historyId(`online:${final.matchId}`)}`).get()).data();
    assert.equal(record.matchId, final.matchId); assert.equal(record.contabilizada, true);
  }
});
