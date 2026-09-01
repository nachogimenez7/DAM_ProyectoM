"use strict";

const assert = require("node:assert/strict");
const {after, before, test} = require("node:test");
const {deleteApp, initializeApp} = require("firebase-admin/app");
const {getDatabase} = require("firebase-admin/database");
const {getFirestore} = require("firebase-admin/firestore");
const {startOnlineMatch} = require("../src/onlineStartService");

const projectId = "traidores-local";
const roomIds = [];
let app;
let database;
let firestore;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Falta FIRESTORE_EMULATOR_HOST");
  assert.ok(process.env.FIREBASE_DATABASE_EMULATOR_HOST, "Falta FIREBASE_DATABASE_EMULATOR_HOST");
  app = initializeApp({
    projectId,
    databaseURL: `https://${projectId}-default-rtdb.firebaseio.com`,
  }, "online-start-integration");
  firestore = getFirestore(app);
  database = getDatabase(app);
});

after(async () => {
  for (const roomId of roomIds) {
    await firestore.recursiveDelete(firestore.collection("partidas").doc(roomId));
    await database.ref(`salas/${roomId}`).remove();
  }
  await deleteApp(app);
});

test("el backend inicia, reparte en privado y sincroniza RTDB", async () => {
  const roomId = await seedRoom({playerCount: 5});
  const result = await startOnlineMatch({
    firestore,
    database,
    requesterId: "host",
    roomId,
    matchId: "match-integration-123",
    nowMs: 123456,
    randomInt: () => 0,
  });
  assert.deepEqual(result, {
    status: "started",
    matchId: "match-integration-123",
    mapKey: "pampa",
  });

  const roomSnapshot = await firestore.collection("partidas").doc(roomId).get();
  const room = roomSnapshot.data();
  assert.equal(room.estado, "en_juego");
  assert.equal(room.partidaInicialCreada, true);
  assert.equal(room.partidaInicial.matchId, "match-integration-123");
  const publicKeys = collectKeys(room.partidaInicial);
  for (const privateKey of ["rolKey", "rolNombre", "rolEquipo", "rolImagen", "rolesVisibles"]) {
    assert.equal(publicKeys.has(privateKey), false);
  }

  const roles = await firestore.collection("partidas").doc(roomId).collection("repartos").get();
  assert.equal(roles.size, 5);
  roles.docs.forEach((document) => {
    const data = document.data();
    assert.equal(data.uidTemporal, document.id);
    assert.equal(data.matchId, "match-integration-123");
    assert.ok(Array.isArray(data.rolesVisibles));
    assert.ok(data.rolesVisibles.length >= 1);
  });

  const realtime = (await database.ref(`salas/${roomId}`).get()).val();
  assert.equal(realtime.control.hostUid, "host");
  assert.equal(realtime.control.matchId, "match-integration-123");
  assert.equal(Object.keys(realtime.miembros).length, 5);

  await assert.rejects(
    startOnlineMatch({
      firestore,
      database,
      requesterId: "intruso",
      roomId,
      matchId: "ignored-intruder-retry",
      randomInt: () => 0,
    }),
    (error) => error.code === "host-required",
  );

  await database.ref(`salas/${roomId}`).remove();

  const retry = await startOnlineMatch({
    firestore,
    database,
    requesterId: "host",
    roomId,
    matchId: "ignored-retry-id",
    randomInt: () => 0,
  });
  assert.equal(retry.status, "already_started");
  assert.equal(retry.matchId, "match-integration-123");
  const repairedRealtime = (await database.ref(`salas/${roomId}`).get()).val();
  assert.equal(repairedRealtime.control.matchId, "match-integration-123");
  assert.equal(Object.keys(repairedRealtime.miembros).length, 5);
});

test("un intruso no puede iniciar", async () => {
  const roomId = await seedRoom({playerCount: 5});
  await assert.rejects(
    startOnlineMatch({
      firestore,
      database,
      requesterId: "intruso",
      roomId,
      matchId: "match-intruder",
      randomInt: () => 0,
    }),
    (error) => error.code === "host-required",
  );
  const room = (await firestore.collection("partidas").doc(roomId).get()).data();
  assert.equal(room.estado, "esperando");
  assert.equal(room.partidaInicialCreada, false);
});

test("un empate devuelve opciones y no escribe la partida", async () => {
  const roomId = await seedRoom({
    playerCount: 3,
    votes: ["pampa", "grecia", "medieval"],
  });
  const result = await startOnlineMatch({
    firestore,
    database,
    requesterId: "host",
    roomId,
    matchId: "match-tie",
    randomInt: () => 0,
  });
  assert.deepEqual(result, {
    status: "tie_break_required",
    mapKeys: ["pampa", "grecia", "medieval"],
  });
  const room = (await firestore.collection("partidas").doc(roomId).get()).data();
  assert.equal(room.estado, "esperando");
  assert.equal(room.partidaInicial, undefined);
});

async function seedRoom({playerCount, votes = []}) {
  const roomId = `backend-${Date.now()}-${roomIds.length}`;
  roomIds.push(roomId);
  const roomReference = firestore.collection("partidas").doc(roomId);
  await roomReference.set({
    estado: "esperando",
    limpiezaPendiente: false,
    hostId: "host",
    hostActivoId: "host",
    hostVersion: 0,
    partidaInicialCreada: false,
    jugadoresEsperados: playerCount,
    jugadoresActuales: playerCount,
    modoPrueba: playerCount < 5,
    mapa: "pampa",
    mapaNombre: "Pampa",
    codigoSala: "ABC234",
    configLobby: {
      transicionSeg: 4,
      nocheSeg: 40,
      discusionSeg: 120,
      votacionSeg: 20,
      revelarRolesAlMorir: false,
      votosIndividuales: true,
      presetRoles: "RECOMMENDED",
      roles: "0,1,1,1,0,0,0,0,0,0,0",
    },
  });
  const batch = firestore.batch();
  for (let index = 0; index < playerCount; index += 1) {
    const uid = index === 0 ? "host" : `p${index}`;
    batch.set(roomReference.collection("jugadores").doc(uid), {
      nombre: index === 0 ? "Host" : `Jugador ${index}`,
      nombreSala: index === 0 ? "Host #1" : `Jugador ${index} #${index + 1}`,
      publicId: `#${index + 1}`,
      listo: true,
      orden: index,
      activoEnPartida: true,
      votoMapa: votes[index] || null,
    });
  }
  await batch.commit();
  return roomId;
}

function collectKeys(value) {
  const keys = new Set();
  if (Array.isArray(value)) {
    value.forEach((nested) => collectKeys(nested).forEach((key) => keys.add(key)));
  } else if (value && typeof value === "object") {
    Object.entries(value).forEach(([key, nested]) => {
      keys.add(key);
      collectKeys(nested).forEach((nestedKey) => keys.add(nestedKey));
    });
  }
  return keys;
}
