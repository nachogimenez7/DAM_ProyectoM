"use strict";

const {randomUUID, randomInt, createHash} = require("node:crypto");
const {isDeepStrictEqual} = require("node:util");
const {FieldValue} = require("firebase-admin/firestore");
const {archiveFinishedRoom} = require("./accountHistoryService");
const {prepareOnlineMatch, playerFromDocument} = require("./onlineStartCore");
const {validRoomId} = require("./onlineStartService");
const {createServerGame, acceptAction, leaveServerGame, expirePhase, deadlineToken, projectServerGame, GameActionError} = require("./onlineGameCore");
const AUTHORITY_GATE = "onlineMaintenance/serverAuthority";
const RECOVERY_GRACE_MS = 5000;
const DEADLINE_MARGIN_MS = 1500;

function references(firestore, roomId) {
  if (!validRoomId(roomId) || /[.#$\[\]\u0000-\u001f\u007f]/.test(roomId)) throw new GameActionError("invalid-room-id");
  const room = firestore.doc(`partidas/${roomId}`);
  return {room, state: room.collection("servidor").doc("current"), outbox: room.collection("serverOutbox").doc("current")};
}
function writeState(tx, refs, state) {
  tx.set(refs.state, state);
  tx.set(refs.outbox, {generation: state.generation, revision: state.revision,
    projection: projectServerGame(state), token: deadlineToken(state),
    deliveredRevision: 0, updatedAt: FieldValue.serverTimestamp()});
  if (state.winner) tx.update(refs.room, {estado: "finalizada", estadoPartida: projectServerGame(state).public,
    actualizadaEn: FieldValue.serverTimestamp()});
}

// Deliberately separate from iniciarPartidaV2. No existing room is implicitly migrated.
async function startServerMatch({firestore, roomId, requesterId, nowMs = Date.now(),
  matchId = randomUUID(), chooseRandomInt = randomInt}) {
  const refs = references(firestore, roomId);
  let transactionAttempts = 0;
  const result = await firestore.runTransaction(async (tx) => {
    transactionAttempts++;
    const [roomSnap, stateSnap] = await Promise.all([tx.get(refs.room), tx.get(refs.state)]);
    if (!roomSnap.exists) throw new GameActionError("room-not-found");
    const room = roomSnap.data();
    if (room.cleanupState === "deleting") throw new GameActionError("room-cleaning");
    if (requesterId !== room.hostId) throw new GameActionError("host-required");
    if (room.partidaInicialCreada || room.partidaInicial) {
      if (!stateSnap.exists || room.authorityMode !== "server") throw new GameActionError("legacy-match-already-started");
      return {status: "already_started", matchId: stateSnap.data().matchId, mapKey: stateSnap.data().mapKey};
    }
    if (stateSnap.data()?.phase === "LOBBY" && room.authorityMode === "server") throw new GameActionError("publication-pending");
    // Admin-only rollout switch. Disabling new starts never stops an existing match.
    const gate = await tx.get(firestore.doc(AUTHORITY_GATE));
    if (gate.data()?.enabled !== true) throw new GameActionError("server-mode-unavailable");
    const roster = await tx.get(refs.room.collection("jugadores"));
    if (roster.docs.some((p) => p.data().activoEnPartida !== false && p.data().protocolVersion !== 3)) {
      throw new GameActionError("incompatible-client");
    }
    const players = roster.docs.map((p) => playerFromDocument(p.id, p.data())).filter(Boolean);
    const fromRematch = stateSnap.data()?.phase === "LOBBY" && stateSnap.data().matchId === room.preparedMatchId;
    const activeMatchId = fromRematch ? stateSnap.data().matchId : matchId;
    const prepared = prepareOnlineMatch({requesterId, room: {...room, hostActivoId: room.hostId},
      players, matchId: activeMatchId, nowMs, randomInt: chooseRandomInt});
    if (prepared.status !== "ready") throw new GameActionError("not-ready");
    const state = createServerGame({roomId, prepared, nowMs});
    state.generation = fromRematch ? stateSnap.data().generation + 1 : 1;
    writeState(tx, refs, state);
    tx.update(refs.room, {estado: "en_juego", protocolVersion: 3, authorityMode: "server",
      serverGeneration: state.generation, partidaInicialCreada: true,
      partidaInicial: {...prepared.payloads.initialMatch, protocolVersion: 3, authorityMode: "server"},
      estadoPartida: projectServerGame(state).public, actualizadaEn: FieldValue.serverTimestamp()});
    for (const p of state.players) tx.update(refs.room.collection("jugadores").doc(p.uid), {orden: p.order, puedeArbitrar: false});
    return {status: "started", matchId: activeMatchId, mapKey: state.mapKey};
  });
  return {...result, transactionAttempts};
}

async function submitServerAction({firestore, roomId, requesterId, action, nowMs = Date.now()}) {
  const refs = references(firestore, roomId);
  let transactionAttempts = 0;
  const result = await firestore.runTransaction(async (tx) => {
    transactionAttempts++;
    const [room, snapshot] = await Promise.all([tx.get(refs.room), tx.get(refs.state)]);
    if (!snapshot.exists || !room.exists) throw new GameActionError("room-not-found");
    if (room.data().cleanupState === "deleting") throw new GameActionError("room-cleaning");
    if (room.data().authorityMode !== "server") throw new GameActionError("incompatible-client");
    if (snapshot.data().phase === "LOBBY") throw new GameActionError("phase-closed");
    const decision = acceptAction(snapshot.data(), requesterId, action, nowMs);
    if (decision.changed) writeState(tx, refs, decision.state);
    return {receipt: decision.receipt, changed: decision.changed};
  });
  return {...result, transactionAttempts};
}

async function prepareServerRematch({firestore, roomId, requesterId, matchId, nowMs = Date.now(),
  nextMatchId = randomUUID(), archiveResult = archiveFinishedRoom}) {
  const refs = references(firestore, roomId);
  const original = await refs.room.get();
  if (!original.exists) throw new GameActionError("room-not-found");
  const room = original.data();
  if (room.hostId !== requesterId) throw new GameActionError("host-required");
  if (room.rematchOf !== matchId) {
    if (room.partidaInicial?.matchId !== matchId || room.estado !== "finalizada") throw new GameActionError("match-not-finished");
    await archiveResult({firestore, roomId, room, finishedAtMs: room.actualizadaEn?.toMillis?.() || nowMs});
  }
  return firestore.runTransaction(async (tx) => {
    const [current, stateSnap, outbox] = await Promise.all([tx.get(refs.room), tx.get(refs.state), tx.get(refs.outbox)]);
    if (!current.exists) throw new GameActionError("room-not-found");
    const latest = current.data();
    if (latest.hostId !== requesterId) throw new GameActionError("host-required");
    if (latest.cleanupState === "deleting") throw new GameActionError("room-cleaning");
    if (latest.rematchOf === matchId) return {status: "already_prepared", matchId: latest.preparedMatchId,
      generation: latest.rematchGeneration};
    if (!stateSnap.exists || stateSnap.data().matchId !== matchId || !stateSnap.data().winner ||
        latest.estado !== "finalizada") throw new GameActionError("match-not-finished");
    if (outbox.data()?.deliveredRevision !== stateSnap.data().revision) throw new GameActionError("publication-pending");
    const roster = await tx.get(refs.room.collection("jugadores"));
    const generation = stateSnap.data().generation + 1;
    const members = roster.docs.filter((p) => p.data().activoEnPartida !== false);
    const players = members.map((p) => ({uid: p.id, name: p.data().nombre || "Jugador"}));
    tx.set(refs.state, {roomId, protocolVersion: 3, phase: "LOBBY", matchId: nextMatchId, generation,
      phaseIndex: 0, revision: 1, winner: null, deadlineMs: null, players});
    tx.set(refs.outbox, {generation, revision: 1, deliveredRevision: 0,
      token: {matchId: nextMatchId, phaseIndex: 0, deadlineMs: null}, updatedAt: FieldValue.serverTimestamp(),
      projection: {public: {protocolVersion: 3, authorityMode: "lobby", matchId: nextMatchId,
        phaseIndex: 0, revision: 1, fase: "LOBBY"}, private: {},
      permissions: Object.fromEntries(players.map((p) => [p.uid, {member: true, matchId: nextMatchId, phaseIndex: 0,
        alive: true, name: p.name, traitor: false, publicChat: false, traitorChat: false, deadChat: false}]))}});
    // Unlock the lobby only after publication revoked old secrets and permissions.
    tx.update(refs.room, {estado: "esperando", authorityMode: "server", serverGeneration: generation,
      partidaInicialCreada: false, partidaInicial: FieldValue.delete(), estadoPartida: FieldValue.delete(),
      hostActivoId: latest.hostId, hostVersion: (latest.hostVersion || 0) + 1, limpiezaPendiente: false,
      rematchOf: matchId, preparedMatchId: nextMatchId, rematchGeneration: generation,
      actualizadaEn: FieldValue.serverTimestamp()});
    for (const p of members) tx.update(p.ref, {listo: false, puedeArbitrar: false});
    return {status: "prepared", matchId: nextMatchId, generation};
  });
}

async function leaveServerMatch({firestore, roomId, requesterId, matchId, nowMs = Date.now()}) {
  const refs = references(firestore, roomId), memberRef = refs.room.collection("jugadores").doc(requesterId);
  return firestore.runTransaction(async (tx) => {
    const [roomSnap, stateSnap, memberSnap, outboxSnap] = await Promise.all([
      tx.get(refs.room), tx.get(refs.state), tx.get(memberRef), tx.get(refs.outbox)]);
    if (!roomSnap.exists || !stateSnap.exists) throw new GameActionError("room-not-found");
    if (roomSnap.data().cleanupState === "deleting") throw new GameActionError("room-cleaning");
    const current = stateSnap.data();
    if (current.matchId !== matchId) throw new GameActionError("stale-match");
    if (current.phase === "LOBBY") {
      if (!memberSnap.exists) throw new GameActionError("not-a-member");
      if (memberSnap.data().activoEnPartida === false) return {status: "already_left"};
      const outbox = outboxSnap.data();
      if (!outbox) throw new GameActionError("publication-pending");
      // Relock until the old participant's RTDB access is revoked.
      delete outbox.projection.permissions[requesterId];
      tx.update(refs.room, {authorityMode: "server"});
      tx.set(refs.state, {...current, revision: current.revision + 1,
        players: current.players.filter((p) => p.uid !== requesterId)});
      tx.set(refs.outbox, {...outbox, revision: current.revision + 1, deliveredRevision: 0,
        updatedAt: FieldValue.serverTimestamp()});
    } else {
      const decision = leaveServerGame(current, requesterId, nowMs);
      if (!decision.changed) return {status: "already_left"};
      writeState(tx, refs, decision.state);
    }
    if (memberSnap.exists) tx.update(memberRef, {activoEnPartida: false, listo: false});
    return {status: "left"};
  });
}
async function expireServerPhase({firestore, roomId, token, nowMs = Date.now()}) {
  const refs = references(firestore, roomId);
  let transactionAttempts = 0;
  const result = await firestore.runTransaction(async (tx) => {
    transactionAttempts++;
    const [room, snapshot] = await Promise.all([tx.get(refs.room), tx.get(refs.state)]);
    if (!room.exists || !snapshot.exists || room.data().cleanupState === "deleting" ||
        room.data().authorityMode !== "server") return {changed: false};
    const decision = expirePhase(snapshot.data(), token, nowMs);
    if (decision.changed) writeState(tx, refs, decision.state);
    const current = decision.state;
    const early = !decision.changed && current.deadlineMs !== null &&
      token?.matchId === current.matchId && token.phaseIndex === current.phaseIndex &&
      token.deadlineMs === current.deadlineMs && nowMs < current.deadlineMs;
    return {changed: decision.changed, matchId: current.matchId, phaseIndex: current.phaseIndex,
      ...(early ? {retryAfterMs: current.deadlineMs - nowMs} : {})};
  });
  return {...result, transactionAttempts};
}

// Recovery is an authenticated nudge, not client authority. It uses the persisted deadline.
async function recoverServerPhase({firestore, roomId, requesterId, matchId, phaseIndex, nowMs = Date.now()}) {
  const refs = references(firestore, roomId);
  let transactionAttempts = 0;
  const result = await firestore.runTransaction(async (tx) => {
    transactionAttempts++;
    const [room, snapshot] = await Promise.all([tx.get(refs.room), tx.get(refs.state)]);
    if (!snapshot.exists || !room.exists) throw new GameActionError("room-not-found");
    if (room.data().cleanupState === "deleting") throw new GameActionError("room-cleaning");
    if (room.data().authorityMode !== "server") throw new GameActionError("incompatible-client");
    const current = snapshot.data();
    if (!current.players.some((p) => p.uid === requesterId && !p.left)) throw new GameActionError("not-a-member");
    if (current.matchId !== matchId) throw new GameActionError("stale-match");
    if (phaseIndex > current.phaseIndex) throw new GameActionError("stale-phase");
    if (current.phase === "LOBBY") return {status: "current", matchId, phaseIndex: current.phaseIndex};
    if (!current.winner && phaseIndex === current.phaseIndex && nowMs < current.deadlineMs + RECOVERY_GRACE_MS) {
      return {status: "waiting", matchId, phaseIndex, retryAfterMs: current.deadlineMs + RECOVERY_GRACE_MS - nowMs};
    }
    // A retry after advancing still repairs the latest outbox if publication failed.
    const decision = phaseIndex === current.phaseIndex ? expirePhase(current, deadlineToken(current), nowMs) :
      {state: current, changed: false};
    if (decision.changed) writeState(tx, refs, decision.state);
    return {status: decision.changed ? "advanced" : "current", matchId,
      phaseIndex: decision.state.phaseIndex};
  });
  return {...result, transactionAttempts};
}
function withoutRevision(value) {
  if (!value) return value;
  const {revision: ignored, ...material} = value;
  return material;
}
function taskId(roomId, generation, token) {
  return "phase-" + createHash("sha256").update(JSON.stringify([roomId, generation, token])).digest("hex");
}
// RTDB drops null fields. Compare the JSON shape that RTDB actually stores.
function realtimeShape(value) {
  if (Array.isArray(value)) return value.length ? value.map(realtimeShape) : null;
  if (value && typeof value === "object") {
    const result = Object.fromEntries(Object.entries(value).map(([k, v]) => [k, realtimeShape(v)]).filter(([, v]) => v !== null));
    return Object.keys(result).length ? result : null;
  }
  return value === undefined ? null : value;
}
async function publishServerOutbox({firestore, database, roomId, enqueueDeadline}) {
  const refs = references(firestore, roomId);
  // Always load the latest durable projection, never trust an old event snapshot.
  const [snapshot, room] = await Promise.all([refs.outbox.get(), refs.room.get()]);
  if (!snapshot.exists || !room.exists || room.data().authorityMode !== "server" ||
      room.data().cleanupState === "deleting") return {published: false};
  const outbox = snapshot.data();
  if (outbox.deliveredRevision === outbox.revision) return {published: false};
  const realtime = database.ref(`onlineV3/${roomId}/snapshot`);
  const queuedTaskId = (await realtime.child("queuedTaskId").get()).val();
  const key = outbox.token.deadlineMs === null ? null : taskId(roomId, outbox.generation, outbox.token);
  if (key && queuedTaskId !== key) {
    // Crash after enqueue is safe: deterministic ID suppresses another task.
    try { await enqueueDeadline({id: key, roomId, token: outbox.token,
      scheduleTime: new Date(outbox.token.deadlineMs + DEADLINE_MARGIN_MS)}); }
    catch (error) {
      if (![6, "already-exists", "functions/task-already-exists"].includes(error.code)) throw error;
    }
  }
  let changedPublic = false, changedPrivate = 0;
  const mergeSnapshot = (current) => {
    changedPublic = false; changedPrivate = 0;
    if (current?.cleanupState === "deleting") return;
    if ((current?.generation || 0) > outbox.generation ||
        current?.generation === outbox.generation && (current?.syncRevision || 0) > outbox.revision) return;
    const projection = realtimeShape(outbox.projection);
    const next = {...(current || {}), generation: outbox.generation, syncRevision: outbox.revision, queuedTaskId: key};
    changedPublic = !isDeepStrictEqual(withoutRevision(current?.public), withoutRevision(projection.public));
    if (changedPublic) next.public = projection.public;
    next.private = {...(current?.private || {})};
    for (const [uid, value] of Object.entries(projection.private || {})) {
      if (!isDeepStrictEqual(withoutRevision(next.private[uid]), withoutRevision(value))) {
        const privateRevision = current?.generation === outbox.generation ? (next.private[uid]?.revision || 0) + 1 : 1;
        Object.defineProperty(next.private, uid, {value: {...value, revision: privateRevision}, enumerable: true, configurable: true, writable: true});
        changedPrivate++;
      }
    }
    // A rematch's new membership must not retain secrets or permissions for departed players.
    for (const uid of Object.keys(next.private)) if (!Object.hasOwn(projection.private || {}, uid)) delete next.private[uid];
    next.permissions = projection.permissions;
    return next;
  };
  // New membership and removal of old team conversations must become visible atomically.
  // Normal phases transact only the snapshot, without downloading the bounded chat.
  const changesMembership = ["LOBBY", "REPARTO"].includes(outbox.projection.public.fase);
  const result = changesMembership ? await realtime.parent.transaction((currentRoom) => {
    const next = mergeSnapshot(currentRoom?.snapshot);
    if (!next) return;
    const nextRoom = {...(currentRoom || {}), snapshot: next};
    if (currentRoom?.snapshot?.public?.matchId !== outbox.token.matchId) {
      delete nextRoom.chat; delete nextRoom.chatRate; delete nextRoom.presence;
    }
    return nextRoom;
  }, undefined, false) : await realtime.transaction(mergeSnapshot, undefined, false);
  if (!result.committed) return {published: false, stale: true};
  await firestore.runTransaction(async (tx) => {
    const latest = await tx.get(refs.outbox);
    const roomSnap = outbox.projection.public.fase === "LOBBY" ? await tx.get(refs.room) : null;
    if (latest.data()?.generation === outbox.generation && latest.data()?.revision === outbox.revision) {
      tx.update(refs.outbox, {deliveredRevision: outbox.revision});
      if (roomSnap?.data()?.preparedMatchId === outbox.token.matchId && roomSnap.data().cleanupState !== "deleting") {
        tx.update(refs.room, {authorityMode: "lobby"});
      }
    }
  });
  return {published: true, changedPublic, changedPrivate,
    projectionBytes: Buffer.byteLength(JSON.stringify(changesMembership ? result.snapshot.child("snapshot").val() : result.snapshot.val()))};
}
module.exports = {startServerMatch, submitServerAction, expireServerPhase, recoverServerPhase, prepareServerRematch, leaveServerMatch,
  publishServerOutbox, taskId, AUTHORITY_GATE, RECOVERY_GRACE_MS, DEADLINE_MARGIN_MS};
