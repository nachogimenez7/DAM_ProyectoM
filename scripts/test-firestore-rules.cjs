const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  serverTimestamp,
  increment,
  collection,
  addDoc,
  query,
  where,
  orderBy,
  limit,
  getDocs,
  deleteField,
  deleteDoc,
  runTransaction,
} = require("firebase/firestore");

const projectId = "traidores-local";

const roomData = (
  hostUid = "host_uid",
  expectedPlayers = 5,
  modePrueba = false
) => ({
  nombre: "Sala de Nacho 0001",
  codigoSala: "ABC234",
  estado: "esperando",
  mapa: "pampa",
  mapaNombre: "Pampa",
  hostId: hostUid,
  hostNombre: "Nacho",
  hostActivoId: hostUid,
  hostVersion: 0,
  partidaInicialCreada: false,
  limpiezaPendiente: false,
  jugadoresEsperados: expectedPlayers,
  maxJugadores: expectedPlayers,
  jugadoresActuales: 1,
  modoPrueba: modePrueba,
  configLobby: {
    transicionSeg: 3,
    nocheSeg: 30,
    discusionSeg: 60,
    votacionSeg: 30,
    revelarRolesAlMorir: false,
    votosIndividuales: true,
  },
  origen: "rules-test",
  creadaEn: serverTimestamp(),
  actualizadaEn: serverTimestamp(),
});

const playerData = (uid, name = "Nacho", order = 0, isHost = false) => ({
  nombre: name,
  nombrePerfil: name,
  nombreSala: `${name} #1`,
  publicId: "1",
  bioPerfil: "No fui yo.",
  avatarPerfil: "grecia_oraculo",
  bannerPerfil: "pampa",
  rolFavoritoPerfil: "pampa_payador",
  esHost: isHost,
  estado: "conectado",
  uidTemporal: uid,
  unidoEn: serverTimestamp(),
  ultimaConexion: serverTimestamp(),
  ultimaConexionLocal: Date.now(),
  orden: order,
  activoEnPartida: true,
  listo: true,
});

async function seedRoom(testEnv, roomId = "room_auth", hostUid = "host_uid") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "partidas", roomId), roomData(hostUid));
    await setDoc(doc(db, "partidas", roomId, "jugadores", hostUid), playerData(hostUid, "Host", 0, true));
  });
}

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8081,
    },
  });

  try {
    const host = testEnv.authenticatedContext("host_uid").firestore();
    const guest = testEnv.authenticatedContext("guest_uid").firestore();
    const intruder = testEnv.authenticatedContext("intruder_uid").firestore();
    const anon = testEnv.unauthenticatedContext().firestore();

    await assertFails(setDoc(doc(anon, "partidas", "room_no_auth"), roomData("host_uid")));
    await assertSucceeds(setDoc(doc(host, "partidas", "room_create"), roomData("host_uid")));
    const legacyCreate = roomData("host_uid");
    delete legacyCreate.limpiezaPendiente;
    await assertSucceeds(setDoc(doc(host, "partidas", "room_create_legacy"), legacyCreate));
    await assertFails(setDoc(doc(guest, "partidas", "room_spoof"), roomData("host_uid")));
    await assertSucceeds(setDoc(
      doc(host, "partidas", "room_test_three"),
      roomData("host_uid", 3, true)
    ));
    await assertFails(setDoc(
      doc(host, "partidas", "room_normal_three"),
      roomData("host_uid", 3, false)
    ));

    await seedRoom(testEnv);

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const legacyRoom = roomData("host_uid");
      delete legacyRoom.limpiezaPendiente;
      await setDoc(doc(db, "partidas", "room_legacy"), legacyRoom);
      await setDoc(
        doc(db, "partidas", "room_legacy", "jugadores", "host_uid"),
        playerData("host_uid", "Host", 0, true)
      );
      const startRoom = roomData("host_uid", 3, true);
      startRoom.jugadoresActuales = 3;
      await setDoc(doc(db, "partidas", "room_atomic_start"), startRoom);
      await setDoc(
        doc(db, "partidas", "room_atomic_start", "jugadores", "host_uid"),
        playerData("host_uid", "Host", 0, true)
      );
    });
    await assertFails(updateDoc(doc(host, "partidas", "room_legacy"), {
      estado: "en_juego",
      partidaInicialCreada: true,
      partidaInicial: { matchId: "legacy_match_1", fase: "REPARTO" },
      actualizadaEn: serverTimestamp(),
    }));

    await assertFails(updateDoc(doc(host, "partidas", "room_atomic_start"), {
      estado: "en_juego",
      mapa: "medieval",
      mapaNombre: "Medieval",
      hostVersion: increment(1),
      partidaInicialCreada: true,
      partidaInicial: {
        matchId: "atomic_match_1",
        mapa: "pampa",
        mapaNombre: "Pampa",
      },
      estadoPartida: { fase: "REPARTO" },
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_atomic_start"), {
      estado: "en_juego",
      mapa: "medieval",
      mapaNombre: "Medieval",
      hostVersion: increment(1),
      partidaInicialCreada: true,
      partidaInicial: {
        matchId: "atomic_match_1",
        mapa: "medieval",
        mapaNombre: "Medieval",
      },
      estadoPartida: { fase: "REPARTO" },
      actualizadaEn: serverTimestamp(),
    }));

    await assertSucceeds(setDoc(doc(guest, "partidas", "room_auth", "jugadores", "guest_uid"), playerData("guest_uid", "Guest", 1)));
    await assertFails(setDoc(doc(guest, "partidas", "room_auth", "jugadores", "other_uid"), playerData("other_uid", "Other", 2)));
    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_auth", "jugadores", "guest_uid"), {
      votoMapa: "medieval",
      ultimaConexion: serverTimestamp(),
      ultimaConexionLocal: Date.now(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_auth", "jugadores", "guest_uid"), {
      votoMapa: "atlántida",
    }));

    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      configLobby: {
        transicionSeg: 5,
        nocheSeg: 40,
        discusionSeg: 90,
        votacionSeg: 40,
        revelarRolesAlMorir: false,
        votosIndividuales: true,
      },
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_auth"), {
      configLobby: {
        transicionSeg: 4,
        nocheSeg: 35,
        discusionSeg: 75,
        votacionSeg: 35,
        revelarRolesAlMorir: true,
        votosIndividuales: false,
      },
      actualizadaEn: serverTimestamp(),
    }));

    const lobbyMessage = await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "chat_lobby"), {
      actorId: "guest_uid",
      speaker: "Guest",
      mensaje: "hola lobby",
      tipo: "texto",
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "chat_lobby"), {
      actorId: "guest_uid",
      speaker: "Guest",
      mensaje: "Contento",
      emoteId: "griego_contento",
      tipo: "emote",
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(intruder, "partidas", "room_auth", "chat_lobby"), {
      actorId: "intruder_uid",
      speaker: "Intruder",
      mensaje: "no pertenezco",
      tipo: "texto",
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertSucceeds(deleteDoc(doc(host, lobbyMessage.path)));

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_auth"), {
        estado: "en_juego",
        partidaInicialCreada: true,
        partidaInicial: {
          matchId: "match_rules_1",
          fase: "REPARTO",
          mapa: "pampa",
          mapaNombre: "Pampa",
        },
      });
    });

    await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "accion_jugador",
      actorId: "guest_uid",
      actorNombre: "Guest",
      actorEsHost: false,
      objetivoNombre: "Host",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { accion: "votar" },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "accion_jugador",
      actorId: "host_uid",
      actorNombre: "Host",
      actorEsHost: false,
      objetivoNombre: "Guest",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { accion: "votar" },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));

    const oldChatRef = await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "chat"), {
      matchId: "match_rules_1",
      actorId: "guest_uid",
      speaker: "Guest",
      mensaje: "hola pueblo",
      fase: "DIA_DEBATE",
      ronda: 1,
      isGod: false,
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(guest, "partidas", "room_auth", "chat"), {
      matchId: "match_rules_1",
      actorId: "guest_uid",
      speaker: "Dios",
      mensaje: "evento falso",
      fase: "DIA_DEBATE",
      ronda: 1,
      isGod: true,
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));

    await assertFails(updateDoc(doc(intruder, "partidas", "room_auth"), {
      estado: "en_juego",
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      estado: "en_juego",
      partidaInicialCreada: true,
      actualizadaEn: serverTimestamp(),
    }));

    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_auth"), {
      "estadoClientes.guest_uid": {
        fase: "REPARTO",
        phaseIndex: 0,
        enGameplay: true,
        jugadoresVistos: 5,
        rolLeido: false,
      },
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_auth"), {
      "estadoClientes.guest_uid.rolLeido": true,
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_auth"), {
      "estadoClientes.host_uid": {
        fase: "REPARTO",
        phaseIndex: 0,
        enGameplay: true,
        jugadoresVistos: 5,
        rolLeido: true,
      },
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(intruder, "partidas", "room_auth"), {
      "estadoClientes.intruder_uid": {
        fase: "REPARTO",
        phaseIndex: 0,
        enGameplay: true,
        jugadoresVistos: 5,
        rolLeido: true,
      },
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_auth"), {
      "estadoClientes.guest_uid.rolLeido": false,
      estado: "finalizada",
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      estado: "finalizada",
      partidaInicialCreada: true,
      partidaInicial: { fase: "REPARTO", mapa: "pampa", mapaNombre: "Pampa" },
      estadoPartida: { fase: "RESULTADO" },
      ultimoResultado: {
        ganador: "Pueblo",
        ronda: 2,
        mapa: "pampa",
        matchId: "match_rules_1",
        finalizadaEnLocal: Date.now(),
      },
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      estado: "esperando",
      hostActivoId: "host_uid",
      partidaInicialCreada: false,
      limpiezaPendiente: true,
      partidaInicial: deleteField(),
      estadoPartida: deleteField(),
      estadoClientes: deleteField(),
      ultimaActividadOnline: serverTimestamp(),
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(deleteDoc(doc(host, oldChatRef.path)));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      limpiezaPendiente: false,
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      jugadoresEsperados: 6,
      maxJugadores: 6,
      actualizadaEn: serverTimestamp(),
    }));

    await seedRoom(testEnv, "room_handoff", "old_host_uid");
    await assertSucceeds(setDoc(doc(guest, "partidas", "room_handoff", "jugadores", "guest_uid"), playerData("guest_uid", "Guest", 1)));
    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_handoff"), {
      hostActivoId: "guest_uid",
      hostVersion: increment(1),
      actualizadaEn: serverTimestamp(),
    }));

    await seedRoom(testEnv, "room_stable_transfer", "host_uid");
    await assertSucceeds(setDoc(
      doc(guest, "partidas", "room_stable_transfer", "jugadores", "guest_uid"),
      playerData("guest_uid", "Guest", 1)
    ));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_stable_transfer"), {
        jugadoresActuales: 2,
      });
    });
    await assertSucceeds(runTransaction(host, async (transaction) => {
      transaction.update(doc(host, "partidas", "room_stable_transfer"), {
        hostId: "guest_uid",
        hostNombre: "Guest",
        hostActivoId: "guest_uid",
        hostVersion: increment(1),
        jugadoresActuales: 1,
        actualizadaEn: serverTimestamp(),
      });
      transaction.update(doc(host, "partidas", "room_stable_transfer", "jugadores", "host_uid"), {
        esHost: false,
        activoEnPartida: false,
        listo: false,
        estado: "desconectado",
      });
      transaction.update(doc(host, "partidas", "room_stable_transfer", "jugadores", "guest_uid"), {
        esHost: true,
      });
    }));

    await assertSucceeds(setDoc(doc(guest, "perfiles_publicos", "guest_uid"), {
      uidTemporal: "guest_uid",
      publicId: "2",
      nombrePerfil: "Guest",
      nombreSala: "Guest #2",
      bioPerfil: "Vine a sospechar.",
      avatarPerfil: "grecia_oraculo",
      bannerPerfil: "pampa",
      rolFavoritoPerfil: "pampa_payador",
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(guest, "perfiles_publicos", "other_uid"), {
      uidTemporal: "other_uid",
      publicId: "3",
      nombrePerfil: "Other",
      actualizadaEn: serverTimestamp(),
    }));

    await assertSucceeds(setDoc(doc(host, "meta", "public_ids"), {
      nextId: 2,
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "meta", "public_ids"), {
      nextId: 3,
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(host, "meta", "public_ids"), {
      nextId: 2,
      actualizadaEn: serverTimestamp(),
    }));

    const browserQuery = query(
      collection(guest, "partidas"),
      where("estado", "==", "esperando"),
      orderBy("actualizadaEn", "desc"),
      limit(30)
    );
    await assertSucceeds(getDocs(browserQuery));
    await assertSucceeds(getDoc(doc(guest, "partidas", "room_auth")));
  } finally {
    await testEnv.cleanup();
  }
}

main()
  .then(() => {
    console.log("Firestore rules tests passed.");
  })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
