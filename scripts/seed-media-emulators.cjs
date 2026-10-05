"use strict";
// Test fixtures only. Refuse to initialize Admin without all three local endpoints.
const fs = require("node:fs");
const {randomUUID} = require("node:crypto");
for (const [key, expected] of Object.entries({FIRESTORE_EMULATOR_HOST: "127.0.0.1:8081",
  FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099", FIREBASE_STORAGE_EMULATOR_HOST: "127.0.0.1:9199"})) {
  if (process.env[key] !== expected) throw new Error(`Se requiere ${key}=${expected}; no se accede a producción.`);
}
const admin = require("../functions/node_modules/firebase-admin");
const {archiveFinishedRoom} = require("../functions/src/accountHistoryService");
const app = admin.initializeApp({projectId: "traidores", storageBucket: "traidores.firebasestorage.app"});
(async () => {
  const suffix = randomUUID();
  const password = "media-emulator-test-42";
  const users = [];
  const firstNumber = await app.firestore().runTransaction(async tx => {
    const ref = app.firestore().doc("meta/public_ids");
    const old = await tx.get(ref);
    const next = Math.max(1, old.data()?.nextId || 1);
    tx.set(ref, {nextId: next + 2, actualizadaEn: admin.firestore.FieldValue.serverTimestamp()}, {merge: true});
    return next;
  });
  for (const [index, letter] of ["a", "b"].entries()) {
    const uid = `media-${suffix}-${letter}`;
    const email = `${uid}@traidores.test`;
    await app.auth().createUser({uid, email, password});
    await app.firestore().doc(`perfiles_publicos/${uid}`).set({uidTemporal: uid,
      publicId: String(firstNumber + index), nombrePerfil: `Media QA ${letter.toUpperCase()}`,
      bioPerfil: "Cuenta de prueba", actualizadaEn: admin.firestore.FieldValue.serverTimestamp()});
    users.push({uid, email});
  }
  const room = {partidaInicial: {matchId: `media-${suffix}`, mapa: "pampa", mapaNombre: "Pampa",
    jugadores: users.map((user, orden) => ({orden, uidTemporal: user.uid, nombre: `Media QA ${orden}`}))},
  estadoPartida: {ganador: "Pueblo", jugadores: users.map((user, orden) => ({orden, nombre: `Media QA ${orden}`,
    rolKey: orden ? "asesino" : "aldeano", rolNombre: orden ? "Bandido" : "Aldeano", rolEquipo: orden ? "Traidores" : "Pueblo"}))}};
  if (await archiveFinishedRoom({firestore: app.firestore(), roomId: `media-${suffix}`, room, finishedAtMs: Date.now()}) !== 2)
    throw new Error("El backend no guardó los dos historiales.");
  const output = process.argv[2];
  if (!output) throw new Error("Indicar un archivo JSON de salida para las credenciales ficticias.");
  fs.writeFileSync(output, JSON.stringify({users, password}), {mode: 0o600});
  console.log("Dos cuentas locales y sus resultados guardados por el backend; fixtures preparados.");
  await app.delete();
})().catch(error => { console.error(error.message); process.exitCode = 1; });
