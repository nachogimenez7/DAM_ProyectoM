"use strict";

const {getApps, initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {getStorage} = require("firebase-admin/storage");
const {getFunctions} = require("firebase-admin/functions");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onDocumentDeleted, onDocumentWritten, onDocumentCreated} = require("firebase-functions/v2/firestore");
const {archiveFinishedRoom, saveRecord} = require("./accountHistoryService");
const logger = require("firebase-functions/logger");
const {sweepRooms, observeDeletedRoom, enqueueRoomCleanup} = require("./onlineRoomCleanupService");
const {OnlineStartError} = require("./onlineStartCore");
const {startOnlineMatch} = require("./onlineStartService");
const {createServerEndpoints, createDeadlineEnqueuer} = require("./onlineGameFunctions");
const {requestLimitId} = require("./onlineRequestLimiter");
const {createServerRecoveryFunction} = require("./onlineGameRecovery");

function adminAppOptions() {
  if (process.env.FUNCTIONS_EMULATOR !== "true") return undefined;
  const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) return undefined;
  return {
    projectId,
    databaseURL: `https://${projectId}-default-rtdb.firebaseio.com`,
  };
}

if (getApps().length === 0) initializeApp(adminAppOptions());

Object.assign(exports, createServerEndpoints({getFirestore, getDatabase, logger,
  enqueueDeadline: (task) => createDeadlineEnqueuer(getFunctions())(task)}));
exports.repararPartidasV3 = createServerRecoveryFunction({getFirestore, getDatabase, logger,
  enqueueDeadline: (task) => createDeadlineEnqueuer(getFunctions())(task)});

function callableError(error) {
  if (!(error instanceof OnlineStartError)) {
    return new HttpsError("internal", "No se pudo iniciar la partida.");
  }
  if (error.code === "host-required") {
    return new HttpsError("permission-denied", error.message, {reason: error.code});
  }
  if (error.code === "invalid-room-id") {
    return new HttpsError("invalid-argument", error.message, {reason: error.code});
  }
  if (error.code === "room-not-found") {
    return new HttpsError("not-found", error.message, {reason: error.code});
  }
  return new HttpsError("failed-precondition", error.message, {reason: error.code});
}

exports.iniciarPartidaV2 = onCall(
  {
    region: "southamerica-west1",
    enforceAppCheck: true,
    timeoutSeconds: 30,
    memory: "256MiB",
    maxInstances: 10,
  },
  async (request) => {
    const requesterId = request.auth && request.auth.uid;
    if (!requesterId) {
      throw new HttpsError("unauthenticated", "Necesitas iniciar sesion para comenzar.");
    }
    const data = request.data && typeof request.data === "object" ? request.data : {};
    try {
      return await startOnlineMatch({
        firestore: getFirestore(),
        database: getDatabase(),
        requesterId,
        roomId: data.roomId,
        hostTieBreakChoice: typeof data.hostTieBreakChoice === "string" ?
          data.hostTieBreakChoice : null,
      });
    } catch (error) {
      throw callableError(error);
    }
  },
);

exports.limpiarSalasAbandonadasV1 = onSchedule({
  // Firebase places the Scheduler job in the function's region. Scheduler has no Santiago location.
  region: "southamerica-east1",
  schedule: "every 15 minutes",
  timeZone: "Etc/UTC",
  timeoutSeconds: 540,
  memory: "256MiB",
  maxInstances: 1,
  retryCount: 3,
}, () => sweepRooms({firestore: getFirestore(), database: getDatabase(), logger}));

exports.registrarSalaHuerfanaV1 = onDocumentDeleted({
  document: "partidas/{roomId}",
  region: "southamerica-west1",
  retry: true,
  maxInstances: 2,
}, (event) => observeDeletedRoom({
  firestore: getFirestore(),
  roomId: event.params.roomId,
  room: event.data?.data(),
  observedAtMs: Date.parse(event.time),
}));

exports.programarLimpiezaSalaV2 = onDocumentWritten({
  document: "partidas/{roomId}",
  region: "southamerica-west1",
  retry: true,
  maxInstances: 2,
}, (event) => {
  if (!event.data?.after.exists) return;
  return enqueueRoomCleanup({firestore: getFirestore(), roomId: event.params.roomId});
});

exports.guardarHistorialOnlineV1 = onDocumentWritten({
  document: "partidas/{roomId}", region: "southamerica-west1", retry: true, maxInstances: 2,
}, (event) => {
  if (!event.data?.after.exists) return;
  const room = event.data.after.data();
  if (!["Pueblo", "Traidores"].includes(room.estadoPartida?.ganador)) return;
  const before = event.data.before.data();
  if (before?.partidaInicial?.matchId === room.partidaInicial?.matchId &&
      ["Pueblo", "Traidores"].includes(before?.estadoPartida?.ganador)) return;
  return archiveFinishedRoom({firestore: getFirestore(), roomId: event.params.roomId,
    room, finishedAtMs: event.data.after.updateTime.toMillis()});
});

exports.contarPartidaLocalV1 = onDocumentCreated({
  document: "cuentas/{uid}/historial/{recordId}", region: "southamerica-west1", retry: true, maxInstances: 2,
}, (event) => {
  const record = event.data?.data();
  if (record?.origen !== "local") return;
  return saveRecord({firestore: getFirestore(), uid: event.params.uid, record,
    recordId: event.params.recordId, existingLocal: true});
});

// Auth deletion also removes private history; delayed room events require an existing profile.
// Auth onDelete is a 1st-gen trigger; Santiago only supports 2nd-gen functions.
exports.borrarHistorialCuentaV1 = require("firebase-functions/v1").region("southamerica-east1")
  .runWith({failurePolicy: true, maxInstances: 2}).auth.user().onDelete(async (user) => {
    await getFirestore().doc(`perfiles_publicos/${user.uid}`).delete();
    await getFirestore().recursiveDelete(getFirestore().doc(`cuentas/${user.uid}`));
    await getFirestore().doc(`onlineRequestLimits/${requestLimitId(user.uid)}`).delete();
    // Never let an emulator without Storage fall through to a real bucket.
    if (process.env.FUNCTIONS_EMULATOR === "true" && !process.env.FIREBASE_STORAGE_EMULATOR_HOST) return;
    const options = getApps()[0].options;
    const bucket = options.storageBucket || `${options.projectId || process.env.GCLOUD_PROJECT}.firebasestorage.app`;
    try { await getStorage().bucket(bucket).deleteFiles({prefix: `profilePhotos/${user.uid}/`, force: true}); }
    catch (error) { if (Number(error.code) !== 404) throw error; }
  });

// Permanent cosmetic purchases. The phone sends the Play token; Google confirms it here.
const {FieldValue} = require("firebase-admin/firestore");
const {GoogleAuth} = require("google-auth-library");
const {PurchaseError, verifyAndGrant, revokeVoided, createPlayApi} = require("./purchaseService");
let playApi;
function purchasesApi() {
  playApi ||= createPlayApi({auth: new GoogleAuth({scopes: ["https://www.googleapis.com/auth/androidpublisher"]})});
  return playApi;
}

exports.validarCompraV1 = onCall({
  region: "southamerica-west1", enforceAppCheck: true, timeoutSeconds: 30, memory: "256MiB", maxInstances: 5,
}, async (request) => {
  const token = request.auth && request.auth.token;
  if (!token || (token.firebase?.sign_in_provider === "anonymous" && !token.email)) {
    throw new HttpsError("unauthenticated", "Iniciá sesión con tu cuenta para comprar.");
  }
  const data = request.data && typeof request.data === "object" ? request.data : {};
  try {
    return await verifyAndGrant({firestore: getFirestore(), playApi: purchasesApi(), uid: request.auth.uid,
      productId: data.productId, purchaseToken: data.purchaseToken, nowMs: Date.now(), FieldValue});
  } catch (error) {
    if (!(error instanceof PurchaseError)) {
      logger.error("purchase_validation_failed", {message: error.message});
      throw new HttpsError("internal", "No pudimos validar la compra. Probá de nuevo.");
    }
    const code = {"unauthenticated": "unauthenticated", "pending": "failed-precondition",
      "play-unavailable": "unavailable"}[error.code] || "permission-denied";
    throw new HttpsError(code, error.message, {reason: error.code});
  }
});

// Refunds and chargebacks: Google lists voided purchases for 30 days; check the last 3 daily.
exports.revisarComprasAnuladasV1 = onSchedule({
  region: "southamerica-east1", schedule: "every day 06:00", timeZone: "America/Argentina/Buenos_Aires",
  timeoutSeconds: 120, memory: "256MiB", maxInstances: 1, retryCount: 1,
}, async () => {
  const result = await revokeVoided({firestore: getFirestore(), playApi: purchasesApi(),
    sinceMs: Date.now() - 3 * 24 * 3600 * 1000, FieldValue});
  logger.info("purchases_voided_checked", result);
});
