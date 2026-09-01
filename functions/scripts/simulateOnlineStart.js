"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {performance} = require("node:perf_hooks");
const {deleteApp, initializeApp} = require("firebase-admin/app");
const {getDatabase} = require("firebase-admin/database");
const {getFirestore} = require("firebase-admin/firestore");
const {startOnlineMatch} = require("../src/onlineStartService");

const args = process.argv.slice(2);
const runsValue = args[args.indexOf("--runs") + 1] || "5";
const runs = Number.parseInt(runsValue, 10);
const projectId = process.env.GCLOUD_PROJECT || "traidores-local";
const sizes = [3, 5, 10, 15];

if (!Number.isInteger(runs) || runs < 1 || runs > 20) {
  throw new Error("--runs debe ser un entero entre 1 y 20");
}
assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Falta FIRESTORE_EMULATOR_HOST");
assert.ok(process.env.FIREBASE_DATABASE_EMULATOR_HOST, "Falta FIREBASE_DATABASE_EMULATOR_HOST");

const app = initializeApp({
  projectId,
  databaseURL: `https://${projectId}-default-rtdb.firebaseio.com`,
}, `online-start-simulation-${process.pid}`);
const firestore = getFirestore(app);
const database = getDatabase(app);
const roomIds = new Set();
const functionsHost = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
const callableUrl = `http://${functionsHost}/${projectId}/southamerica-west1/iniciarPartidaV2`;

function unsignedToken(subject, extraPayload = {}) {
  const header = Buffer.from(JSON.stringify({alg: "none", typ: "JWT"})).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    sub: subject,
    aud: projectId,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
    ...extraPayload,
  })).toString("base64url");
  return `${header}.${payload}.emulator`;
}

const authToken = unsignedToken("host", {user_id: "host"});
const appCheckToken = unsignedToken("1:local:android:traidores", {
  app_id: "1:local:android:traidores",
});

function millisecondsSince(startedAt) {
  return Number((performance.now() - startedAt).toFixed(1));
}

function percentile(sortedValues, fraction) {
  const index = Math.min(Math.ceil(sortedValues.length * fraction) - 1, sortedValues.length - 1);
  return sortedValues[Math.max(index, 0)];
}

function summary(values) {
  const sorted = [...values].sort((left, right) => left - right);
  const average = sorted.reduce((sum, value) => sum + value, 0) / sorted.length;
  return {
    average: Number(average.toFixed(1)),
    p50: percentile(sorted, 0.5),
    p95: percentile(sorted, 0.95),
    maximum: sorted[sorted.length - 1],
  };
}

function printSummary(label, values) {
  const result = summary(values);
  process.stdout.write(
    `${label} avgMs=${result.average} p50Ms=${result.p50} ` +
    `p95Ms=${result.p95} maxMs=${result.maximum}\n`,
  );
}

async function seedRoom(playerCount, label) {
  const roomId = `perf-${label}-${crypto.randomUUID().slice(0, 8)}`;
  roomIds.add(roomId);
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
    codigoSala: "PERF01",
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
      votoMapa: "pampa",
    });
  }
  await batch.commit();
  return roomId;
}

async function invokeStart(roomId, matchId) {
  return startOnlineMatch({
    firestore,
    database,
    requesterId: "host",
    roomId,
    matchId,
  });
}

async function measuredStart(roomId, matchId) {
  const startedAt = performance.now();
  const result = await invokeStart(roomId, matchId);
  return {result, milliseconds: millisecondsSince(startedAt)};
}

async function measuredCallableStart(roomId) {
  const startedAt = performance.now();
  const response = await fetch(callableUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${authToken}`,
      "Content-Type": "application/json",
      "X-Firebase-AppCheck": appCheckToken,
    },
    body: JSON.stringify({data: {roomId}}),
  });
  const body = await response.json();
  if (!response.ok || body.error) {
    throw new Error(`Callable ${response.status}: ${JSON.stringify(body.error || body)}`);
  }
  return {result: body.result, milliseconds: millisecondsSince(startedAt)};
}

async function verifyStartedRoom(roomId, playerCount) {
  const roomReference = firestore.collection("partidas").doc(roomId);
  const [roomSnapshot, roleSnapshot, realtimeSnapshot] = await Promise.all([
    roomReference.get(),
    roomReference.collection("repartos").get(),
    database.ref(`salas/${roomId}`).get(),
  ]);
  assert.equal(roomSnapshot.data().partidaInicialCreada, true);
  assert.equal(roleSnapshot.size, playerCount);
  assert.equal(Object.keys(realtimeSnapshot.val().miembros).length, playerCount);
}

async function runSequentialScenarios() {
  const records = new Map(sizes.map((size) => [size, {starts: [], retries: []}]));
  for (const size of sizes) {
    for (let run = 0; run < runs; run += 1) {
      const roomId = await seedRoom(size, `seq-${size}-${run}`);
      const startedAt = performance.now();
      const result = await invokeStart(roomId, `match-seq-${size}-${run}`);
      const startMs = millisecondsSince(startedAt);
      assert.equal(result.status, "started");

      const retryStartedAt = performance.now();
      const retry = await invokeStart(roomId, `ignored-retry-${size}-${run}`);
      const retryMs = millisecondsSince(retryStartedAt);
      assert.equal(retry.status, "already_started");
      assert.equal(retry.matchId, result.matchId);
      await verifyStartedRoom(roomId, size);

      records.get(size).starts.push(startMs);
      records.get(size).retries.push(retryMs);
    }
  }
  for (const size of sizes) {
    printSummary(`START players=${size} runs=${runs}`, records.get(size).starts);
    printSummary(`RETRY players=${size} runs=${runs}`, records.get(size).retries);
  }
}

async function runSameRoomRace() {
  const calls = 5;
  const roomId = await seedRoom(10, "same-room-race");
  const startedAt = performance.now();
  const measurements = await Promise.all(Array.from({length: calls}, (_, index) =>
    measuredStart(roomId, `match-race-${index}`)));
  const elapsedMs = millisecondsSince(startedAt);
  const startedMeasurements = measurements.filter(({result}) => result.status === "started");
  const repeatedMeasurements = measurements.filter(({result}) => result.status === "already_started");
  const started = startedMeasurements.length;
  const repeated = repeatedMeasurements.length;
  assert.equal(started, 1);
  assert.equal(repeated, calls - 1);
  await verifyStartedRoom(roomId, 10);
  process.stdout.write(
    `RACE sameRoom calls=${calls} started=${started} idempotent=${repeated} ` +
    `firstUsefulMs=${startedMeasurements[0].milliseconds} ` +
    `duplicateMaxMs=${Math.max(...repeatedMeasurements.map(({milliseconds}) => milliseconds))} ` +
    `wallMs=${elapsedMs}\n`,
  );
}

async function runIndependentRoomBurst() {
  const roomCount = 8;
  const roomIdsForBurst = await Promise.all(Array.from({length: roomCount}, (_, index) =>
    seedRoom(10, `burst-${index}`)));
  const startedAt = performance.now();
  const measurements = await Promise.all(roomIdsForBurst.map((roomId, index) =>
    measuredStart(roomId, `match-burst-${index}`)));
  const elapsedMs = millisecondsSince(startedAt);
  assert.equal(measurements.every(({result}) => result.status === "started"), true);
  await Promise.all(roomIdsForBurst.map((roomId) => verifyStartedRoom(roomId, 10)));
  const times = measurements.map(({milliseconds}) => milliseconds);
  const result = summary(times);
  process.stdout.write(
    `BURST independentRooms=${roomCount} playersEach=10 avgMs=${result.average} ` +
    `p50Ms=${result.p50} p95Ms=${result.p95} maxMs=${result.maximum} wallMs=${elapsedMs}\n`,
  );
}

async function runCallableScenarios() {
  const roomIdForFirstCall = await seedRoom(10, "callable-first");
  const first = await measuredCallableStart(roomIdForFirstCall);
  assert.equal(first.result.status, "started");
  await verifyStartedRoom(roomIdForFirstCall, 10);

  const warmTimes = [];
  for (let index = 0; index < runs; index += 1) {
    const roomId = await seedRoom(10, `callable-warm-${index}`);
    const measurement = await measuredCallableStart(roomId);
    assert.equal(measurement.result.status, "started");
    warmTimes.push(measurement.milliseconds);
    await verifyStartedRoom(roomId, 10);
  }
  const warm = summary(warmTimes);
  process.stdout.write(
    `CALLABLE players=10 firstMs=${first.milliseconds} warmRuns=${runs} ` +
    `warmAvgMs=${warm.average} warmP50Ms=${warm.p50} warmP95Ms=${warm.p95} ` +
    `warmMaxMs=${warm.maximum}\n`,
  );
}

async function cleanup() {
  for (const roomId of roomIds) {
    await Promise.all([
      firestore.recursiveDelete(firestore.collection("partidas").doc(roomId)),
      database.ref(`salas/${roomId}`).remove(),
    ]);
  }
  await deleteApp(app);
}

async function main() {
  try {
    const warmupRoomId = await seedRoom(5, "warmup");
    const warmupStartedAt = performance.now();
    await invokeStart(warmupRoomId, "match-warmup");
    process.stdout.write(`WARMUP excludedMs=${millisecondsSince(warmupStartedAt)}\n`);

    await runSequentialScenarios();
    await runCallableScenarios();
    await runSameRoomRace();
    await runIndependentRoomBurst();
    process.stdout.write("ONLINE_START_SIMULATION_OK\n");
  } finally {
    await cleanup();
  }
}

main().catch((error) => {
  console.error("ONLINE_START_SIMULATION_FAILED", error);
  process.exitCode = 1;
});
