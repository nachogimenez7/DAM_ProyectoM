const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  serverTimestamp,
} = require("firebase/firestore");
const {
  get: getDatabaseValue,
  ref,
  set,
  update,
} = require("firebase/database");

const args = process.argv.slice(2);
const runCount = Number(args[args.indexOf("--runs") + 1] || 10);
const localMode = args.includes("--local");
const sizes = [3, 6, 9, 12, 15];

if (!localMode) {
  throw new Error("Este ejecutor requiere --local. El ensayo real usa credenciales aisladas.");
}
if (!Number.isInteger(runCount) || runCount < 1 || runCount > 50) {
  throw new Error("--runs debe ser un entero entre 1 y 50");
}

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

function playerDocument(uid, index) {
  return {
    nombre: `Tester ${index + 1}`,
    nombrePerfil: `Tester ${index + 1}`,
    nombreSala: `Tester ${index + 1} #${index + 1}`,
    publicId: String(index + 1),
    bioPerfil: "",
    avatarPerfil: "aldeano",
    fotoPlayGames: "",
    bannerPerfil: "pampa",
    rolFavoritoPerfil: "aldeano",
    esHost: index === 0,
    estado: "conectado",
    uidTemporal: uid,
    unidoEn: serverTimestamp(),
    ultimaConexion: serverTimestamp(),
    ultimaConexionLocal: Date.now(),
    orden: index,
    activoEnPartida: true,
    listo: true,
  };
}

function roomDocument(roomId, matchId, uids) {
  return {
    nombre: `Simulacion ${roomId}`,
    codigoSala: `S${roomId.slice(-5).toUpperCase()}`,
    estado: "en_juego",
    mapa: "pampa",
    mapaNombre: "Pampa",
    hostId: uids[0],
    hostNombre: "Tester 1",
    hostActivoId: uids[0],
    hostVersion: 1,
    partidaInicialCreada: true,
    limpiezaPendiente: false,
    jugadoresEsperados: uids.length,
    maxJugadores: uids.length,
    jugadoresActuales: uids.length,
    modoPrueba: uids.length === 3,
    visibilidad: "publica",
    origen: "online-firebase-integration-simulation",
    partidaInicial: {
      matchId,
      mapa: "pampa",
      mapaNombre: "Pampa",
      jugadores: uids.map((uid, index) => ({ uidTemporal: uid, nombre: `Tester ${index + 1}`, orden: index })),
    },
    estadoPartida: {
      matchId,
      fase: "REPARTO",
      faseIndice: 0,
      ronda: 1,
      ganador: "",
    },
    configLobby: {
      transicionSeg: 3,
      nocheSeg: 30,
      discusionSeg: 60,
      votacionSeg: 30,
      revelarRolesAlMorir: false,
      votosIndividuales: true,
      presetRoles: "RECOMMENDED",
      roles: "1,1,1,0,0,0,0,0,0,0,0",
    },
    creadaEn: serverTimestamp(),
    actualizadaEn: serverTimestamp(),
  };
}

function member(uid, index) {
  return {
    nombre: `Tester ${index + 1}`,
    activo: true,
    enLobby: false,
    vivo: true,
    traidor: index === 0,
    actualizadaEn: Date.now(),
  };
}

function clientState(uid, index, size, matchId, phase = "REPARTO", phaseIndex = 0) {
  return {
    matchId,
    fase: phase,
    ronda: 1,
    phaseIndex,
    enGameplay: true,
    jugadoresVistos: size,
    jugadoresEsperados: size,
    uidTemporal: uid,
    orden: index,
    rolLeido: true,
    estadoArranque: phase === "REPARTO" ? "rol_leido" : "en_partida",
    aplicoEstadoPartida: true,
    sincronizando: false,
    entradaLobbyLista: true,
    actualizadaEnLocal: Date.now(),
    actualizadaEn: Date.now(),
  };
}

async function seedFirestore(testEnv, roomId, matchId, uids) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "partidas", roomId), roomDocument(roomId, matchId, uids));
    await Promise.all(uids.map(async (uid, index) => {
      await setDoc(doc(db, "partidas", roomId, "jugadores", uid), playerDocument(uid, index));
      await setDoc(doc(db, "partidas", roomId, "repartos", uid), {
        matchId,
        uidTemporal: uid,
        rolesVisibles: [{ orden: index, rolKey: index === 0 ? "asesino" : "aldeano" }],
        creadaEn: serverTimestamp(),
      });
    }));
  });
}

async function runScenario(testEnv, runIndex, size) {
  const startedAt = Date.now();
  const roomId = `sim_online_${String(runIndex + 1).padStart(2, "0")}_${size}`;
  const matchId = `match-simulation-${runIndex + 1}-${size}`;
  const uids = Array.from({ length: size }, (_, index) => `sim_${runIndex + 1}_p${index}`);
  const contexts = uids.map((uid) => testEnv.authenticatedContext(uid, {
    firebase: { sign_in_provider: "password" },
  }));
  const firestoreClients = contexts.map((context) => context.firestore());
  const databaseClients = contexts.map((context) => context.database());

  await seedFirestore(testEnv, roomId, matchId, uids);

  const hostDatabase = databaseClients[0];
  await assertSucceeds(set(ref(hostDatabase, `salas/${roomId}/control/hostUid`), uids[0]));
  await assertSucceeds(set(ref(hostDatabase, `salas/${roomId}/control/creatorUid`), uids[0]));
  const roomSetup = {
    "control/matchId": matchId,
    "control/jugadoresVivos": size,
    "control/actualizadaEn": Date.now(),
  };
  uids.forEach((uid, index) => {
    roomSetup[`miembros/${uid}`] = member(uid, index);
  });
  await assertSucceeds(update(ref(hostDatabase, `salas/${roomId}`), roomSetup));

  await Promise.all(databaseClients.map((db, index) => delay((runIndex * 17 + index * 11) % 45)
    .then(() => assertSucceeds(set(ref(db, `salas/${roomId}/presencia/${uids[index]}`), {
      estado: "conectado",
      ts: Date.now(),
    })))
    .then(() => assertSucceeds(set(
      ref(db, `salas/${roomId}/sincronizacion/clientes/${uids[index]}`),
      clientState(uids[index], index, size, matchId)
    )))));

  await assertFails(set(
    ref(databaseClients[size - 1], `salas/${roomId}/sincronizacion/clientes/${uids[size - 1]}`),
    clientState(uids[size - 1], size - 1, size, `old-${matchId}`)
  ));

  const startupSnapshots = await Promise.all(databaseClients.map((db) => assertSucceeds(
    getDatabaseValue(ref(db, `salas/${roomId}/sincronizacion/clientes`))
  )));
  startupSnapshots.forEach((snapshot) => {
    if (snapshot.size !== size) {
      throw new Error(`ACK incompletos: ${snapshot.size}/${size}`);
    }
  });

  await Promise.all(databaseClients.map((db, index) => assertSucceeds(set(
    ref(db, `salas/${roomId}/sincronizacion/listosVotacion/${uids[index]}`),
    {
      matchId,
      nombre: `Tester ${index + 1}`,
      listo: true,
      ronda: 1,
      phaseIndex: 4,
      actualizadaEn: Date.now(),
    }
  ))));
  const voteReady = await assertSucceeds(getDatabaseValue(
    ref(hostDatabase, `salas/${roomId}/sincronizacion/listosVotacion`)
  ));
  if (voteReady.size !== size) {
    throw new Error(`Listos de voto incompletos: ${voteReady.size}/${size}`);
  }

  await Promise.all(databaseClients.map((db, index) => assertSucceeds(set(
    ref(db, `salas/${roomId}/sincronizacion/clientes/${uids[index]}`),
    clientState(uids[index], index, size, matchId, "DIA_DEBATE", 4)
  ))));

  const disconnectedIndex = size - 1;
  await assertSucceeds(set(
    ref(databaseClients[disconnectedIndex], `salas/${roomId}/presencia/${uids[disconnectedIndex]}`),
    { estado: "desconectado", ts: Date.now() }
  ));
  const disconnectedPresence = await assertSucceeds(getDatabaseValue(
    ref(hostDatabase, `salas/${roomId}/presencia`)
  ));
  const connectedCount = Object.values(disconnectedPresence.val() || {})
    .filter((presence) => presence.estado === "conectado").length;
  if (connectedCount !== size - 1) {
    throw new Error(`Presencia incorrecta: ${connectedCount}/${size - 1}`);
  }
  await assertSucceeds(set(
    ref(databaseClients[disconnectedIndex], `salas/${roomId}/presencia/${uids[disconnectedIndex]}`),
    { estado: "conectado", ts: Date.now() }
  ));

  const firestoreReads = await Promise.all(firestoreClients.map(async (db, index) => {
    const room = await assertSucceeds(getDoc(doc(db, "partidas", roomId)));
    const ownDeal = await assertSucceeds(getDoc(doc(db, "partidas", roomId, "repartos", uids[index])));
    if (!room.exists() || room.data().partidaInicial.matchId !== matchId) {
      throw new Error("Estado autoritativo de Firestore ausente o mezclado");
    }
    if (!ownDeal.exists() || ownDeal.data().matchId !== matchId) {
      throw new Error("Reparto privado ausente o mezclado");
    }
    return true;
  }));
  if (firestoreReads.length !== size) throw new Error("Lecturas Firestore incompletas");
  const roster = await assertSucceeds(getDocs(
    collection(firestoreClients[0], "partidas", roomId, "jugadores")
  ));
  if (roster.size !== size) throw new Error(`Roster incorrecto: ${roster.size}/${size}`);

  return {
    run: runIndex + 1,
    players: size,
    milliseconds: Date.now() - startedAt,
    acknowledgements: startupSnapshots[0].size,
    votesReady: voteReady.size,
  };
}

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId: "traidores-local",
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8081,
    },
    database: {
      rules: fs.readFileSync("database.rules.json", "utf8"),
      host: "127.0.0.1",
      port: 9000,
    },
  });

  const results = [];
  try {
    for (let index = 0; index < runCount; index += 1) {
      await testEnv.clearFirestore();
      await testEnv.clearDatabase();
      const result = await runScenario(testEnv, index, sizes[index % sizes.length]);
      results.push(result);
      process.stdout.write(
        `SIM ${result.run}/${runCount} OK players=${result.players} ` +
        `acks=${result.acknowledgements} votes=${result.votesReady} ms=${result.milliseconds}\n`
      );
    }
  } finally {
    await testEnv.clearFirestore();
    await testEnv.clearDatabase();
    await testEnv.cleanup();
  }

  const totalMs = results.reduce((sum, result) => sum + result.milliseconds, 0);
  const maximumMs = Math.max(...results.map((result) => result.milliseconds));
  process.stdout.write(
    `ONLINE_FIREBASE_LOCAL_OK runs=${results.length} ` +
    `sizes=${[...new Set(results.map((result) => result.players))].join(",")} ` +
    `avgMs=${Math.round(totalMs / results.length)} maxMs=${maximumMs}\n`
  );
}

main().catch((error) => {
  console.error("ONLINE_FIREBASE_LOCAL_FAILED", error);
  process.exitCode = 1;
});
