"use strict";

const {FieldPath, Timestamp} = require("firebase-admin/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {expireServerPhase, publishServerOutbox} = require("./onlineGameService");
const {TASK_REGION} = require("./onlineGameFunctions");

const RECOVERY_CONTROL = "onlineMaintenance/serverRecovery";
const OUTBOX_PATH = /^partidas\/([^/]+)\/serverOutbox\/current$/;
const DEFAULT_PAGE_SIZE = 25;
const DEFAULT_MAX_PAGES = 2;
const WORK_BUDGET_MS = 40000;
function cursorPathValid(value) {
  if (typeof value !== "string" || value.length > 1500) return false;
  const parts = value.split("/");
  return parts.length >= 4 && parts.length % 2 === 0 && parts.every(Boolean) && parts.at(-2) === "serverOutbox";
}

// A bounded safety net for exhausted Tasks/Eventarc retries, including final results
// and rematches whose pending publication has no next deadline. No client is needed.
async function repairOverdueServerGames({firestore, database, enqueueDeadline, logger,
  nowMs = Date.now(), pageSize = DEFAULT_PAGE_SIZE, maxPages = DEFAULT_MAX_PAGES,
  monotonicNow = () => performance.now()}) {
  if (!Number.isSafeInteger(nowMs) || nowMs <= 0 || !Number.isInteger(pageSize) ||
      pageSize < 1 || pageSize > 50 || !Number.isInteger(maxPages) || maxPages < 1 || maxPages > 4) {
    throw new Error("invalid-recovery-options");
  }
  const startedAt = monotonicNow();
  const control = firestore.doc(RECOVERY_CONTROL);
  const previous = (await control.get()).data()?.cursor;
  let cursor = previous && Number.isSafeInteger(previous.atMs) && previous.atMs > 0 &&
    previous.atMs <= nowMs && cursorPathValid(previous.path) ? previous : null;
  const result = {scanned: 0, advanced: 0, published: 0, failed: 0, hasMore: false};
  for (let pageIndex = 0; pageIndex < maxPages; pageIndex++) {
    let query = firestore.collectionGroup("serverOutbox")
      .where("recoveryAtMs", ">", 0).where("recoveryAtMs", "<=", nowMs)
      .orderBy("recoveryAtMs").orderBy(FieldPath.documentId()).limit(pageSize);
    if (cursor) query = query.startAfter(cursor.atMs, firestore.doc(cursor.path));
    const page = await query.get();
    let processed = 0;
    for (const document of page.docs) {
      if (monotonicNow() - startedAt >= WORK_BUDGET_MS) break;
      const match = OUTBOX_PATH.exec(document.ref.path);
      cursor = {atMs: document.data().recoveryAtMs, path: document.ref.path};
      processed++; result.scanned++;
      if (!match) { result.failed++; logger.error("online_v3_recovery_invalid_path"); continue; }
      const roomId = match[1];
      try {
        const token = document.data().token;
        if (!token || typeof token.matchId !== "string" || !Number.isSafeInteger(token.phaseIndex) ||
            token.phaseIndex < 0 || !(token.deadlineMs === null || Number.isSafeInteger(token.deadlineMs) && token.deadlineMs > 0)) {
          throw new Error("invalid-recovery-token");
        }
        // Later rooms in a batch receive a fresh phase clock, not the scheduler's
        // old start time; processing a backlog must not shorten their next phase.
        const operationNowMs = nowMs + Math.max(0, Math.floor(monotonicNow() - startedAt));
        const expired = await expireServerPhase({firestore, roomId, token, nowMs: operationNowMs});
        if (expired.changed) result.advanced++;
        const publication = await publishServerOutbox({firestore, database, roomId, enqueueDeadline});
        if (publication.published) result.published++;
      } catch (error) {
        result.failed++;
        // Only diagnostic identifiers; never log the outbox, roles or intentions.
        logger.error(error.code === "phase-stuck" ? "online_v3_recovery_stuck" : "online_v3_recovery_failed",
          {roomId, code: error.code || "unknown"});
      }
    }
    result.hasMore = processed < page.docs.length || page.docs.length === pageSize;
    if (!result.hasMore) { cursor = null; break; }
    if (monotonicNow() - startedAt >= WORK_BUDGET_MS) break;
  }
  // Resume after this batch on the next run. Failed old rooms cannot indefinitely
  // starve the later ones; after reaching the end the next run starts a new pass.
  if (result.scanned || previous) await control.set({cursor, updatedAt: Timestamp.fromMillis(nowMs), lastRun: result});
  logger.info("online_v3_recovery", result);
  if (result.hasMore) logger.warn("online_v3_recovery_backlog", {scanned: result.scanned});
  return result;
}

function createServerRecoveryFunction({getFirestore, getDatabase, enqueueDeadline, logger, now = Date.now}) {
  // Cloud job remains paused during the host-authority beta. This slower fallback
  // is staged only; any future deployment must preserve/reapply the pause.
  return onSchedule({region: TASK_REGION, schedule: "every 15 minutes", timeZone: "Etc/UTC",
    timeoutSeconds: 60, memory: "256MiB", minInstances: 0, maxInstances: 1, concurrency: 1, retryCount: 0},
  () => repairOverdueServerGames({firestore: getFirestore(), database: getDatabase(), enqueueDeadline, logger, nowMs: now()}));
}

module.exports = {repairOverdueServerGames, createServerRecoveryFunction, RECOVERY_CONTROL,
  DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGES, WORK_BUDGET_MS};
