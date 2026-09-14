"use strict";

const crypto = require("node:crypto");
const {FieldPath, FieldValue, Timestamp} = require("firebase-admin/firestore");
const {ServerValue} = require("firebase-admin/database");
const {roomRetention, realtimeRetention, RETENTION_MS} = require("./onlineRoomCleanupPolicy");

const ORPHANS = "onlineRoomCleanupOrphans";
const MAINTENANCE = "onlineMaintenance/roomCleanup";
const QUEUE = "onlineRoomCleanupQueue";
const RETRY_MS = 60 * 60 * 1000;

// Trigger delivery order is not trusted: read current metadata inside the transaction.
async function enqueueRoomCleanup({firestore, roomId, nowMs = Date.now()}) {
  const roomRef = firestore.collection("partidas").doc(roomId);
  const queueRef = firestore.collection(QUEUE).doc(roomId);
  await firestore.runTransaction(async (transaction) => {
    const [room, orphan, queued] = await Promise.all([
      transaction.get(roomRef), transaction.get(firestore.collection(ORPHANS).doc(roomId)),
      transaction.get(queueRef),
    ]);
    const data = room.exists ? room.data() : orphan.data();
    if (!data || data.cleanupCompleted === true) {
      if (queued.exists) transaction.delete(queueRef);
      return;
    }
    // Keep the first check: extending it on every heartbeat would create needless writes.
    if (queued.exists) return;
    const at = data.actualizadaEn ?? data.creadaEn;
    const timestamp = at && typeof at.toMillis === "function" ? at.toMillis() : null;
    const retention = RETENTION_MS[data.estado];
    const dueMs = timestamp && retention ? timestamp + retention : nowMs + RETRY_MS;
    transaction.set(queueRef, {dueAt: Timestamp.fromMillis(Math.max(1, dueMs))});
  });
}

// One-time, resumable discovery of rooms created before the trigger was deployed.
async function backfillCleanupQueue({firestore, pageSize, nowMs}) {
  const cursorRef = firestore.doc(MAINTENANCE);
  const cursor = (await cursorRef.get()).data() || {};
  for (const [collection, key] of [["partidas", "roomsV2"], [ORPHANS, "orphansV2"]]) {
    if (cursor[`${key}Complete`] === true) continue;
    let query = firestore.collection(collection).orderBy(FieldPath.documentId()).limit(pageSize);
    if (cursor[key]) query = query.startAfter(cursor[key]);
    const page = await query.get();
    for (const document of page.docs) {
      if (document.data().cleanupCompleted !== true) await enqueueRoomCleanup({firestore, roomId: document.id, nowMs});
    }
    await cursorRef.set({[key]: page.docs.at(-1)?.id || null,
      [`${key}Complete`]: page.size < pageSize}, {merge: true});
  }
}

async function observeDeletedRoom({firestore, roomId, room, observedAtMs = Date.now()}) {
  // The event id need not be stored: create-once preserves the original deadline
  // when Eventarc retries delivery, without trusting the deleted client's clock.
  try {
    await firestore.collection(ORPHANS).doc(roomId).create({
      estado: Object.hasOwn(RETENTION_MS, room?.estado) ? room.estado : "abandonada",
      codigoSala: typeof room?.codigoSala === "string" ? room.codigoSala : "",
      actualizadaEn: Timestamp.fromMillis(observedAtMs),
    });
  } catch (error) {
    if (error.code !== 6 && error.code !== "already-exists") throw error;
  }
  await enqueueRoomCleanup({firestore, roomId, nowMs: observedAtMs});
}

async function cleanupRoom({firestore, database, roomId, nowMs = Date.now(), orphan = null}) {
  const roomRef = firestore.collection("partidas").doc(roomId);
  const realtimeRef = database.ref(`salas/${roomId}`);
  const realtime = (await realtimeRef.get()).val(); // A failed read aborts; never infer absence.
  const newToken = crypto.randomUUID();
  const claim = await firestore.runTransaction(async (transaction) => {
    const roomSnapshot = await transaction.get(roomRef);
    if (!roomSnapshot.exists && !orphan) return {eligible: false, reason: "missing-room"};
    if (roomSnapshot.exists && orphan) return {eligible: false, reason: "orphan-recreated"};
    const room = roomSnapshot.exists ? roomSnapshot.data() : orphan;
    const [players, checkpoint] = await Promise.all([
      transaction.get(roomRef.collection("jugadores")),
      transaction.get(roomRef.collection("runtime").doc("authoritative")),
    ]);
    const decision = roomRetention({
      room, players: players.docs.map((doc) => doc.data()), checkpoint: checkpoint.data(), realtime, nowMs,
    });
    if (!decision.eligible) {
      // Recover a crash between the Firestore claim and the RTDB lock. If a
      // reconnect won meanwhile, release the non-destructive Firestore claim.
      if (roomSnapshot.exists && room.cleanupState === "deleting" && !room.cleanupCompleted &&
          realtime?.control?.cleanupToken !== room.cleanupToken) {
        transaction.update(roomRef, {
          cleanupState: FieldValue.delete(), cleanupToken: FieldValue.delete(),
          cleanupClaimedAt: FieldValue.delete(),
        });
      }
      return decision;
    }
    const token = room.cleanupToken || newToken;
    transaction.set(roomRef, {
      ...room,
      ...(orphan ? {estado: "abandonada"} : {}),
      cleanupState: "deleting",
      cleanupToken: token,
      cleanupClaimedAt: FieldValue.serverTimestamp(),
    });
    return {...decision, token, room};
  });
  if (!claim.eligible) return {status: "retained", reason: claim.reason};

  // RTDB transaction serializes the final presence check with reconnects, chat,
  // state publication and the lock. Rules freeze every client write at this point.
  const locked = await realtimeRef.transaction((current) => {
    const control = current?.control || {};
    if (control.cleanupState === "deleting") {
      return control.cleanupToken === claim.token ? current : undefined;
    }
    if (!realtimeRetention(current, nowMs, claim.retentionMs).eligible) return;
    return {...(current || {}), control: {
      ...control, cleanupState: "deleting", cleanupToken: claim.token,
    }};
  }, undefined, false);
  if (!locked.committed) {
    await firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(roomRef);
      if (snapshot.data()?.cleanupToken !== claim.token || snapshot.data()?.cleanupCompleted) return;
      transaction.update(roomRef, {
        cleanupState: FieldValue.delete(), cleanupToken: FieldValue.delete(),
        cleanupClaimedAt: FieldValue.delete(),
      });
    });
    return {status: "retained", reason: "realtime-changed-before-lock"};
  }

  // Keep the parent lock while traversing ALL collections, including runtime,
  // votes and future nested collections. recursiveDelete(parent) would open a
  // window for malicious recreation before the final marker is installed.
  const collections = await roomRef.listCollections();
  for (const collection of collections) await firestore.recursiveDelete(collection);
  const code = claim.room.codigoSala;
  if (typeof code === "string" && /^[A-Z0-9]{6}$/.test(code)) {
    const codeRef = firestore.collection("codigosSala").doc(code);
    await firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(codeRef);
      if (snapshot.data()?.partidaId === roomId) transaction.delete(codeRef);
    });
  }
  await realtimeRef.set({control: {
    cleanupState: "deleting", cleanupToken: claim.token, cleanupDeletedAt: ServerValue.TIMESTAMP,
  }});
  await roomRef.set({
    estado: "abandonada", cleanupState: "deleting", cleanupToken: claim.token,
    cleanupCompleted: true, cleanupDeletedAt: FieldValue.serverTimestamp(),
  });
  await firestore.collection(ORPHANS).doc(roomId).delete();
  return {status: "cleaned"};
}

// Only due entries are repeatedly read. Tombstones remain to deny recreation, outside this queue.
async function sweepRooms({firestore, database, nowMs = Date.now(), pageSize = 100, logger = console}) {
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 500) throw new Error("Invalid page size");
  await backfillCleanupQueue({firestore, pageSize, nowMs});
  const page = await firestore.collection(QUEUE).where("dueAt", "<=", Timestamp.fromMillis(nowMs))
    .orderBy("dueAt").limit(pageSize).get();
  const counters = {examined: 0, cleaned: 0, retained: 0, failed: 0};
  for (const queued of page.docs) {
    counters.examined++;
    try {
      const room = await firestore.collection("partidas").doc(queued.id).get();
      const orphan = room.exists ? null : (await firestore.collection(ORPHANS).doc(queued.id).get()).data();
      if ((!room.exists && !orphan) || room.data()?.cleanupCompleted === true) {
        await queued.ref.delete();
        counters.retained++;
        continue;
      }
      const result = await cleanupRoom({firestore, database, roomId: queued.id, nowMs, orphan});
      counters[result.status]++;
      if (result.status === "cleaned") {
        await queued.ref.delete();
        logger.info("online_room_cleanup_done", {roomId: queued.id});
      } else {
        await queued.ref.set({dueAt: Timestamp.fromMillis(nowMs + RETRY_MS)});
      }
    } catch (error) {
      counters.failed++;
      await queued.ref.set({dueAt: Timestamp.fromMillis(nowMs + RETRY_MS)});
      logger.error("online_room_cleanup_failed", {roomId: queued.id, code: error.code || "unknown"});
    }
  }
  logger.info("online_room_cleanup_sweep", counters);
  return counters;
}

module.exports = {cleanupRoom, observeDeletedRoom, sweepRooms, enqueueRoomCleanup, ORPHANS, QUEUE};
