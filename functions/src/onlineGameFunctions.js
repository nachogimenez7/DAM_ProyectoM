"use strict";

const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onTaskDispatched} = require("firebase-functions/v2/tasks");
const {OnlineStartError} = require("./onlineStartCore");
const {GameActionError} = require("./onlineGameCore");
const {enforceRequestLimit} = require("./onlineRequestLimiter");
const {startServerMatch, submitServerAction, expireServerPhase, recoverServerPhase,
  publishServerOutbox, prepareServerRematch, leaveServerMatch} = require("./onlineGameService");

const CALLABLE_REGION = "southamerica-west1";
const TASK_REGION = "southamerica-east1";
const DEADLINE_FUNCTION = "resolverFaseV3";
const CALLABLE_OPTIONS = Object.freeze({region: CALLABLE_REGION, enforceAppCheck: true,
  timeoutSeconds: 30, memory: "256MiB", minInstances: 0, maxInstances: 4});

function objectData(data, fields) {
  if (!data || typeof data !== "object" || Array.isArray(data) ||
      Object.keys(data).some((key) => !fields.includes(key)) ||
      Buffer.byteLength(JSON.stringify(data)) > 4096) {
    throw new HttpsError("invalid-argument", "La solicitud no es válida.", {reason: "invalid-request"});
  }
  return data;
}
function authenticated(request) {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Necesitás iniciar sesión.");
  return request.auth.uid;
}
function matchAndPhase(data) {
  if (typeof data.matchId !== "string" || !data.matchId.length || data.matchId.length > 128 ||
      !Number.isSafeInteger(data.phaseIndex) || data.phaseIndex < 0) {
    throw new HttpsError("invalid-argument", "La partida o fase no es válida.", {reason: "invalid-request"});
  }
}
function gameCallableError(error) {
  if (error instanceof HttpsError) return error;
  if (!(error instanceof GameActionError) && !(error instanceof OnlineStartError)) {
    return new HttpsError("unavailable", "El servidor no pudo completar la acción. Probá otra vez.");
  }
  const reason = error.code;
  let code = "failed-precondition";
  if (reason.startsWith("invalid-") || reason === "unknown-action") code = "invalid-argument";
  if (["host-required", "not-a-member"].includes(reason)) code = "permission-denied";
  if (reason === "room-not-found") code = "not-found";
  if (["too-many-actions", "request-rate-limit"].includes(reason)) code = "resource-exhausted";
  const message = reason === "server-mode-unavailable" ? "Este modo online todavía está en preparación." :
    "La acción no está disponible en esta partida o fase.";
  return new HttpsError(code, message, {reason});
}

function createDeadlineEnqueuer(functions) {
  const queue = functions.taskQueue(`locations/${TASK_REGION}/functions/${DEADLINE_FUNCTION}`);
  return ({id, roomId, token, scheduleTime}) => queue.enqueue({roomId, token}, {
    id, scheduleTime, dispatchDeadlineSeconds: 60,
  });
}

// Dependencies are resolved on invocation, not on deploy-time function discovery.
function createServerEndpoints({getFirestore, getDatabase, enqueueDeadline, logger, now = Date.now}) {
  async function limitedRequest(request) {
    const uid = authenticated(request);
    try {
      await enforceRequestLimit({firestore: getFirestore(), uid, nowMs: now()});
    } catch (error) { throw gameCallableError(error); }
    return uid;
  }
  async function measured(operation, roomId, work, callable = false) {
    const startedAt = now();
    try {
      const result = await work();
      logger.info("online_v3_operation", {operation, roomId, durationMs: now() - startedAt,
        transactionAttempts: result.transactionAttempts || 0, changed: result.changed === true,
        published: result.published === true, changedPublic: result.changedPublic === true,
        changedPrivate: result.changedPrivate || 0, projectionBytes: result.projectionBytes || 0});
      return result;
    } catch (error) {
      // Never log intentions, names, roles, tokens or private projections.
      logger.warn("online_v3_operation_failed", {operation, durationMs: now() - startedAt,
        code: error.code || "unknown"});
      throw callable ? gameCallableError(error) : error;
    }
  }
  const publish = (roomId) => publishServerOutbox({firestore: getFirestore(), database: getDatabase(),
    roomId, enqueueDeadline});

  const iniciarPartidaV3 = onCall(CALLABLE_OPTIONS, async (request) => {
    // Auth and envelope shape reject without reads; even game-rule rejections consume the UID bucket.
    authenticated(request);
    const {roomId} = objectData(request.data, ["roomId"]);
    const requesterId = await limitedRequest(request);
    const result = await measured("start", roomId, () => startServerMatch({firestore: getFirestore(),
      roomId, requesterId, nowMs: now()}), true);
    const {transactionAttempts, ...response} = result;
    return response;
  });
  const accionPartidaV3 = onCall(CALLABLE_OPTIONS, async (request) => {
    authenticated(request);
    const {roomId, ...action} = objectData(request.data,
      ["roomId", "matchId", "phaseIndex", "requestId", "action", "targetUid", "team"]);
    const requesterId = await limitedRequest(request);
    const result = await measured("action", roomId, () => submitServerAction({firestore: getFirestore(),
      roomId, requesterId, action, nowMs: now()}), true);
    return result.receipt;
  });
  const recuperarFaseV3 = onCall({...CALLABLE_OPTIONS, maxInstances: 2}, async (request) => {
    authenticated(request);
    const data = objectData(request.data, ["roomId", "matchId", "phaseIndex"]);
    matchAndPhase(data);
    const requesterId = await limitedRequest(request);
    const result = await measured("recover", data.roomId, async () => {
      const result = await recoverServerPhase({firestore: getFirestore(), ...data, requesterId, nowMs: now()});
      if (result.status !== "waiting") await publish(data.roomId);
      return result;
    }, true);
    const {transactionAttempts, ...response} = result;
    return response;
  });
  const prepararRevanchaV3 = onCall({...CALLABLE_OPTIONS, maxInstances: 2}, async (request) => {
    authenticated(request);
    const data = objectData(request.data, ["roomId", "matchId"]);
    matchAndPhase({...data, phaseIndex: 0});
    const requesterId = await limitedRequest(request);
    return measured("rematch", data.roomId, () => prepareServerRematch({firestore: getFirestore(),
      ...data, requesterId, nowMs: now()}), true);
  });
  const abandonarPartidaV3 = onCall({...CALLABLE_OPTIONS, maxInstances: 2}, async (request) => {
    authenticated(request);
    const data = objectData(request.data, ["roomId", "matchId"]);
    matchAndPhase({...data, phaseIndex: 0});
    const requesterId = await limitedRequest(request);
    return measured("leave", data.roomId, () => leaveServerMatch({firestore: getFirestore(),
      ...data, requesterId, nowMs: now()}), true);
  });
  const publicarPartidaV3 = onDocumentWritten({document: "partidas/{roomId}/serverOutbox/current",
    region: CALLABLE_REGION, retry: true, timeoutSeconds: 60, memory: "256MiB", minInstances: 0, maxInstances: 2},
  async (event) => {
    if (!event.data?.after.exists) return;
    const before = event.data.before.data(), after = event.data.after.data();
    // Marking an outbox delivered is not another publication event.
    if (before?.generation === after.generation && before?.revision === after.revision) return;
    return measured("publish", event.params.roomId, () => publish(event.params.roomId));
  });
  const resolverFaseV3 = onTaskDispatched({region: TASK_REGION, invoker: "private",
    timeoutSeconds: 60, memory: "256MiB", minInstances: 0, maxInstances: 2,
    retryConfig: {maxAttempts: 8, minBackoffSeconds: 1, maxBackoffSeconds: 10, maxDoublings: 4},
    rateLimits: {maxConcurrentDispatches: 20, maxDispatchesPerSecond: 20}}, async (request) => {
    const {roomId, token} = objectData(request.data, ["roomId", "token"]);
    objectData(token, ["matchId", "phaseIndex", "deadlineMs"]); matchAndPhase(token);
    if (!Number.isSafeInteger(token.deadlineMs) || token.deadlineMs <= 0) {
      throw new HttpsError("invalid-argument", "El vencimiento no es válido.");
    }
    return measured("deadline", roomId, async () => {
      const result = await expireServerPhase({firestore: getFirestore(), roomId, token, nowMs: now()});
      if (result.retryAfterMs) throw new HttpsError("unavailable", "La fase todavía no venció.");
      // Repair after a previous attempt committed but its publication/next enqueue failed.
      const publication = await publish(roomId);
      return {...result, ...publication};
    });
  });
  return {iniciarPartidaV3, accionPartidaV3, recuperarFaseV3, prepararRevanchaV3, abandonarPartidaV3, publicarPartidaV3, resolverFaseV3};
}

module.exports = {createServerEndpoints, createDeadlineEnqueuer, gameCallableError,
  CALLABLE_REGION, TASK_REGION, DEADLINE_FUNCTION};
