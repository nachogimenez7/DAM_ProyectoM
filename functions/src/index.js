"use strict";

const {getApps, initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {getStorage} = require("firebase-admin/storage");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onDocumentDeleted, onDocumentWritten, onDocumentCreated} = require("firebase-functions/v2/firestore");
const {archiveFinishedRoom, saveRecord} = require("./accountHistoryService");
const logger = require("firebase-functions/logger");
const {sweepRooms, observeDeletedRoom, enqueueRoomCleanup} = require("./onlineRoomCleanupService");
const {OnlineStartError} = require("./onlineStartCore");
const {startOnlineMatch} = require("./onlineStartService");

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
    // Never let an emulator without Storage fall through to a real bucket.
    if (process.env.FUNCTIONS_EMULATOR === "true" && !process.env.FIREBASE_STORAGE_EMULATOR_HOST) return;
    const options = getApps()[0].options;
    const bucket = options.storageBucket || `${options.projectId || process.env.GCLOUD_PROJECT}.firebasestorage.app`;
    try { await getStorage().bucket(bucket).deleteFiles({prefix: `profilePhotos/${user.uid}/`, force: true}); }
    catch (error) { if (Number(error.code) !== 404) throw error; }
  });
