"use strict";

const {getApps, initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getDatabase} = require("firebase-admin/database");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
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
