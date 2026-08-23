const crypto = require("crypto");
const fs = require("fs");
const { spawnSync } = require("child_process");
const { initializeApp, deleteApp } = require("firebase/app");
const {
  createUserWithEmailAndPassword,
  deleteUser,
  getAuth,
} = require("firebase/auth");
const {
  collection,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} = require("firebase/firestore");
const {
  get: getDatabaseValue,
  getDatabase,
  ref,
  serverTimestamp: databaseServerTimestamp,
  set,
  update,
} = require("firebase/database");

const args = process.argv.slice(2);
const valueAfter = (flag, fallback) => {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : fallback;
};
const runCount = Number(valueAfter("--runs", "10"));
const projectId = valueAfter("--project", "");
const confirmation = process.env.TRAIDORES_REAL_FIREBASE_CONFIRM || "";
const sizes = [3, 6, 9, 12, 15];
const verbose = args.includes("--verbose");
const detail = (message) => {
  if (verbose) process.stdout.write(`  ${message}\n`);
};

if (projectId !== "traidores" || confirmation !== projectId) {
  throw new Error(
    "Proteccion activa: define TRAIDORES_REAL_FIREBASE_CONFIRM=traidores para usar el proyecto real."
  );
}
if (!Number.isInteger(runCount) || runCount < 1 || runCount > 10) {
  throw new Error("--runs debe ser un entero entre 1 y 10 para Firebase real");
}

function firebaseConfig() {
  const googleServices = JSON.parse(fs.readFileSync("app/google-services.json", "utf8"));
  const client = googleServices.client.find(
    (candidate) => candidate.client_info.android_client_info.package_name === "com.traidores.juego"
  );
  if (!client) throw new Error("No se encontro la configuracion Android de Traidores");
  return {
    apiKey: client.api_key[0].current_key,
    appId: client.client_info.mobilesdk_app_id,
    projectId: googleServices.project_info.project_id,
    databaseURL: googleServices.project_info.firebase_url,
    storageBucket: googleServices.project_info.storage_bucket,
  };
}

function validRoomCode(runIndex) {
  const digits = String(runIndex + 222).replace(/[01]/g, "2").padStart(3, "2").slice(-3);
  return `TST${digits}`;
}

function waitingRoom(hostUid, size, runIndex) {
  return {
    nombre: `Prueba automatica ${runIndex + 1}`,
    codigoSala: validRoomCode(runIndex),
    estado: "esperando",
    mapa: "pampa",
    mapaNombre: "Pampa",
    hostId: hostUid,
    hostNombre: "Tester 1",
    hostActivoId: hostUid,
    hostVersion: 0,
    partidaInicialCreada: false,
    limpiezaPendiente: false,
    jugadoresEsperados: size,
    maxJugadores: size,
    jugadoresActuales: 1,
    modoPrueba: size === 3,
    visibilidad: "privada",
    origen: "codex-sim-real",
    configLobby: {
      transicionSeg: 3,
      nocheSeg: 30,
      discusionSeg: 60,
      votacionSeg: 30,
      revelarRolesAlMorir: false,
      votosIndividuales: true,
      presetRoles: "RECOMMENDED",
      roles: "1,1,1,1,0,0,0,0,0,0,0",
    },
    creadaEn: serverTimestamp(),
    actualizadaEn: serverTimestamp(),
  };
}

function playerDocument(uid, index) {
  return {
    nombre: `Tester ${index + 1}`,
    nombrePerfil: `Tester ${index + 1}`,
    nombreSala: `Tester ${index + 1}`,
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

function member(index) {
  return {
    nombre: `Tester ${index + 1}`,
    activo: true,
    enLobby: false,
    vivo: true,
    traidor: index === 0,
    actualizadaEn: databaseServerTimestamp(),
  };
}

function syncState(uid, index, size, matchId, phase = "REPARTO", phaseIndex = 0) {
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
    actualizadaEn: databaseServerTimestamp(),
  };
}

async function createTestUsers(config, batchId, count) {
  const clients = [];
  for (let index = 0; index < count; index += 1) {
    const app = initializeApp(config, `real-sim-${batchId}-${index}`);
    const auth = getAuth(app);
    const email = `traidores-sim-${batchId}-${index}@example.com`;
    const password = `Ts!${crypto.randomBytes(18).toString("hex")}`;
    const credential = await createUserWithEmailAndPassword(auth, email, password);
    await credential.user.getIdToken(true);
    clients.push({
      app,
      auth,
      uid: credential.user.uid,
      firestore: getFirestore(app),
      database: getDatabase(app),
    });
  }
  return clients;
}

async function createAndStartRoom(clients, roomId, matchId, size, runIndex) {
  const active = clients.slice(0, size);
  const host = active[0];
  const roomRef = doc(host.firestore, "partidas", roomId);
  await setDoc(roomRef, waitingRoom(host.uid, size, runIndex));
  await setDoc(doc(host.firestore, "partidas", roomId, "jugadores", host.uid), playerDocument(host.uid, 0));

  for (let index = 1; index < size; index += 1) {
    const client = active[index];
    await setDoc(
      doc(client.firestore, "partidas", roomId, "jugadores", client.uid),
      playerDocument(client.uid, index)
    );
    await updateDoc(roomRef, {
      jugadoresActuales: index + 1,
      actualizadaEn: serverTimestamp(),
    });
  }

  const roster = active.map((client, index) => ({
    uidTemporal: client.uid,
    nombre: `Tester ${index + 1}`,
    orden: index,
  }));
  const batch = writeBatch(host.firestore);
  active.forEach((client, index) => {
    batch.set(doc(host.firestore, "partidas", roomId, "repartos", client.uid), {
      matchId,
      uidTemporal: client.uid,
      rolesVisibles: [{
        orden: index,
        rolKey: index === 0 ? "asesino" : "aldeano",
      }],
      creadaEn: serverTimestamp(),
    });
  });
  batch.update(roomRef, {
    estado: "en_juego",
    hostVersion: 1,
    partidaInicialCreada: true,
    partidaInicial: {
      matchId,
      mapa: "pampa",
      mapaNombre: "Pampa",
      jugadores: roster,
    },
    estadoPartida: {
      matchId,
      fase: "REPARTO",
      faseIndice: 0,
      ronda: 1,
      ganador: "",
    },
    actualizadaEn: serverTimestamp(),
    ultimaActividadOnline: serverTimestamp(),
  });
  await batch.commit();
  return active;
}

async function setupRealtime(active, roomId, matchId) {
  const host = active[0];
  await set(ref(host.database, `salas/${roomId}/control/hostUid`), host.uid);
  detail("RTDB host registrado");
  await set(ref(host.database, `salas/${roomId}/control/creatorUid`), host.uid);
  detail("RTDB creador registrado");
  await update(ref(host.database, `salas/${roomId}`), {
    "control/matchId": matchId,
    "control/jugadoresVivos": active.length,
    "control/actualizadaEn": databaseServerTimestamp(),
  });
  detail("RTDB control preparado");
  for (let index = 0; index < active.length; index += 1) {
    const client = active[index];
    await set(ref(host.database, `salas/${roomId}/miembros/${client.uid}`), member(index));
  }
  detail(`RTDB miembros preparados (${active.length})`);

  for (let index = 0; index < active.length; index += 1) {
    const client = active[index];
    await set(ref(client.database, `salas/${roomId}/presencia/${client.uid}`), {
      estado: "conectado",
      ts: databaseServerTimestamp(),
    });
    detail(`RTDB presencia ${index + 1}/${active.length}`);
  }
  for (let index = 0; index < active.length; index += 1) {
    const client = active[index];
    await set(
      ref(client.database, `salas/${roomId}/sincronizacion/clientes/${client.uid}`),
      syncState(client.uid, index, active.length, matchId)
    );
    detail(`RTDB ACK ${index + 1}/${active.length}`);
  }
}

async function verifyScenario(active, roomId, matchId) {
  const host = active[0];
  const startup = await Promise.all(active.map((client) => getDatabaseValue(
    ref(client.database, `salas/${roomId}/sincronizacion/clientes`)
  )));
  detail("RTDB ACK leidos por todos");
  if (startup.some((snapshot) => snapshot.size !== active.length)) {
    throw new Error("Los ACK no llegaron a todos los clientes reales");
  }

  for (let index = 0; index < active.length; index += 1) {
    const client = active[index];
    await set(ref(client.database, `salas/${roomId}/sincronizacion/listosVotacion/${client.uid}`), {
      matchId,
      nombre: `Tester ${index + 1}`,
      listo: true,
      ronda: 1,
      phaseIndex: 4,
      actualizadaEn: databaseServerTimestamp(),
    });
    detail(`RTDB voto listo ${index + 1}/${active.length}`);
  }
  const votes = await getDatabaseValue(
    ref(host.database, `salas/${roomId}/sincronizacion/listosVotacion`)
  );
  detail("RTDB votos leidos por host");
  if (votes.size !== active.length) throw new Error("Los listos de voto quedaron incompletos");

  await Promise.all(active.map((client, index) => set(
    ref(client.database, `salas/${roomId}/sincronizacion/clientes/${client.uid}`),
    syncState(client.uid, index, active.length, matchId, "DIA_DEBATE", 4)
  )));
  const reconnecting = active[active.length - 1];
  await set(ref(reconnecting.database, `salas/${roomId}/presencia/${reconnecting.uid}`), {
    estado: "desconectado",
    ts: databaseServerTimestamp(),
  });
  const presence = await getDatabaseValue(ref(host.database, `salas/${roomId}/presencia`));
  const connected = Object.values(presence.val() || {})
    .filter((entry) => entry.estado === "conectado").length;
  if (connected !== active.length - 1) throw new Error("La desconexion no se reflejo correctamente");
  await set(ref(reconnecting.database, `salas/${roomId}/presencia/${reconnecting.uid}`), {
    estado: "conectado",
    ts: databaseServerTimestamp(),
  });

  await Promise.all(active.map(async (client, index) => {
    const room = await getDoc(doc(client.firestore, "partidas", roomId));
    const ownDeal = await getDoc(doc(client.firestore, "partidas", roomId, "repartos", client.uid));
    if (!room.exists() || room.data().partidaInicial.matchId !== matchId) {
      throw new Error(`Cliente ${index + 1} no recibio el estado autoritativo`);
    }
    if (!ownDeal.exists() || ownDeal.data().matchId !== matchId) {
      throw new Error(`Cliente ${index + 1} no recibio su reparto privado`);
    }
  }));
  const roster = await getDocs(collection(host.firestore, "partidas", roomId, "jugadores"));
  if (roster.size !== active.length) throw new Error("El host recibio un roster incompleto");

  await updateDoc(doc(host.firestore, "partidas", roomId), {
    entradaLiberadaMatchId: matchId,
    estadoPartida: {
      matchId,
      fase: "DIA_DEBATE",
      faseIndice: 4,
      ronda: 1,
      ganador: "",
    },
    actualizadaEn: serverTimestamp(),
    ultimaActividadOnline: serverTimestamp(),
  });
  const advancedStates = await Promise.all(active.map((client) => getDoc(
    doc(client.firestore, "partidas", roomId)
  )));
  if (advancedStates.some((snapshot) => snapshot.data().estadoPartida.fase !== "DIA_DEBATE")) {
    throw new Error("La fase autoritativa no llego a todos los clientes");
  }

  return { acknowledgements: startup[0].size, votesReady: votes.size };
}

function privilegedCleanup(projectIdToClean, roomId) {
  if (!/^codex_sim_[a-z0-9_]+$/i.test(roomId)) {
    throw new Error(`Limpieza rechazada para identificador no seguro: ${roomId}`);
  }
  const firebaseCommand = process.platform === "win32" ? "firebase.cmd" : "firebase";
  const commands = [
    ["database:remove", `/salas/${roomId}`, "--project", projectIdToClean, "--force"],
    ["firestore:delete", `partidas/${roomId}`, "--project", projectIdToClean, "--recursive", "--force"],
  ];
  for (const command of commands) {
    const result = spawnSync(firebaseCommand, command, {
      encoding: "utf8",
      shell: process.platform === "win32",
    });
    if (result.error || result.status !== 0) {
      throw new Error(
        `La limpieza privilegiada fallo: ${result.error?.message || result.stderr || result.stdout}`
      );
    }
  }
}

async function cleanupRoom(host, roomId, projectIdToClean) {
  // Estas salas no representan el flujo de salida de un jugador: son fixtures sintéticos
  // que ya atravesaron una partida completa. Se eliminan con la CLI administrativa para no
  // debilitar las reglas de producción ni dejar que una cuenta cliente borre datos arbitrarios.
  privilegedCleanup(projectIdToClean, roomId);
}

async function main() {
  const config = firebaseConfig();
  if (config.projectId !== projectId) throw new Error("El google-services.json no coincide con el proyecto confirmado");
  const batchId = `${Date.now()}-${crypto.randomBytes(3).toString("hex")}`;
  const clients = [];
  const roomIds = [];
  const results = [];
  try {
    const neededUsers = Math.max(...Array.from({ length: runCount }, (_, index) => sizes[index % sizes.length]));
    process.stdout.write(`Creando ${neededUsers} cuentas temporales aisladas...\n`);
    clients.push(...await createTestUsers(config, batchId, neededUsers));
    for (let index = 0; index < runCount; index += 1) {
      const size = sizes[index % sizes.length];
      const roomId = `codex_sim_${batchId.replace(/[^a-z0-9]/gi, "_")}_${index + 1}`;
      const matchId = `match-real-${batchId}-${index + 1}`;
      roomIds.push(roomId);
      const startedAt = Date.now();
      const active = await createAndStartRoom(clients, roomId, matchId, size, index);
      await setupRealtime(active, roomId, matchId);
      const verified = await verifyScenario(active, roomId, matchId);
      const milliseconds = Date.now() - startedAt;
      results.push({ players: size, milliseconds, ...verified });
      await cleanupRoom(active[0], roomId, projectId);
      roomIds.pop();
      process.stdout.write(
        `REAL ${index + 1}/${runCount} OK players=${size} acks=${verified.acknowledgements} ` +
        `votes=${verified.votesReady} ms=${milliseconds}\n`
      );
    }
  } finally {
    for (const roomId of [...roomIds].reverse()) {
      if (clients[0]) await cleanupRoom(clients[0], roomId, projectId).catch((error) => {
        process.stderr.write(`WARN pendiente ${roomId}: ${error.code || error.message}\n`);
      });
    }
    for (const client of clients.reverse()) {
      if (client.auth.currentUser) {
        await deleteUser(client.auth.currentUser).catch((error) => {
          process.stderr.write(`WARN auth cleanup ${client.uid}: ${error.code || error.message}\n`);
        });
      }
      await deleteApp(client.app).catch(() => {});
    }
  }

  const average = Math.round(results.reduce((sum, result) => sum + result.milliseconds, 0) / results.length);
  const maximum = Math.max(...results.map((result) => result.milliseconds));
  process.stdout.write(
    `ONLINE_FIREBASE_REAL_OK runs=${results.length} sizes=${[...new Set(results.map((result) => result.players))]} ` +
    `avgMs=${average} maxMs=${maximum} cleanup=complete\n`
  );
}

main().catch((error) => {
  console.error("ONLINE_FIREBASE_REAL_FAILED", error.code || error.message, error.stack || "");
  process.exitCode = 1;
});
