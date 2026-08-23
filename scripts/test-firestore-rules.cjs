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
  Timestamp,
  writeBatch,
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
  visibilidad: "publica",
  configLobby: {
    transicionSeg: 3,
    nocheSeg: 30,
    discusionSeg: 60,
    votacionSeg: 30,
    revelarRolesAlMorir: false,
    votosIndividuales: true,
    presetRoles: "RECOMMENDED",
    roles: "2,1,1,1,0,0,0,0,0,0,0",
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
  fotoPlayGames: "https://lh3.googleusercontent.com/traidores-avatar",
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

const guestPlayerData = (uid, name = "Mala Onda 4821", order = 1) => ({
  nombre: name,
  nombrePerfil: name,
  nombreSala: name,
  bioPerfil: "",
  avatarPerfil: "aldeano",
  bannerPerfil: "pampa",
  rolFavoritoPerfil: "aldeano",
  esHost: false,
  estado: "conectado",
  uidTemporal: uid,
  unidoEn: serverTimestamp(),
  ultimaConexion: serverTimestamp(),
  ultimaConexionLocal: Date.now(),
  orden: order,
  activoEnPartida: true,
  listo: false,
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
    const oldHost = testEnv.authenticatedContext("old_host_uid").firestore();
    const guest = testEnv.authenticatedContext("guest_uid").firestore();
    const legacyGuest = testEnv.authenticatedContext("legacy_guest_uid", {
      firebase: { sign_in_provider: "anonymous" },
    }).firestore();
    const intruder = testEnv.authenticatedContext("intruder_uid").firestore();
    const blocked = testEnv.authenticatedContext("blocked_uid").firestore();
    const anon = testEnv.unauthenticatedContext().firestore();

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "bans", "blocked_uid"), {
        motivo: "abuso",
        creadaEn: serverTimestamp(),
      });
    });

    await assertFails(setDoc(doc(anon, "partidas", "room_no_auth"), roomData("host_uid")));
    await assertSucceeds(setDoc(doc(host, "partidas", "room_create"), roomData("host_uid")));
    const invalidVisibility = roomData("host_uid");
    invalidVisibility.visibilidad = "secreta";
    await assertFails(setDoc(doc(host, "partidas", "room_invalid_visibility"), invalidVisibility));
    await assertFails(setDoc(doc(blocked, "partidas", "room_blocked"), roomData("blocked_uid")));

    // Una instalacion vieja puede conservar un publicId de cuando tambien se asignaba a
    // invitados. El primer patch debe borrarlo: sin ese delete, las reglas lo rechazan.
    await seedRoom(testEnv, "room_legacy_guest", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "partidas", "room_legacy_guest", "jugadores", "legacy_guest_uid"),
        { ...guestPlayerData("legacy_guest_uid"), publicId: "27" }
      );
    });
    const legacyGuestPlayer = doc(
      legacyGuest,
      "partidas",
      "room_legacy_guest",
      "jugadores",
      "legacy_guest_uid"
    );
    await assertFails(updateDoc(legacyGuestPlayer, { listo: true }));
    await assertSucceeds(updateDoc(legacyGuestPlayer, {
      publicId: deleteField(),
      listo: true,
      ultimaConexion: serverTimestamp(),
    }));
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
    await assertSucceeds(setDoc(doc(host, "pruebas", "conexion_inicial"), {
      nombre: "Host",
      mensaje: "conexion correcta",
      origen: "android",
      fechaLocal: Date.now(),
      fechaServidor: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(host, "pruebas", "otro_documento"), {
      nombre: "Host",
      mensaje: "conexion correcta",
      origen: "android",
      fechaLocal: Date.now(),
      fechaServidor: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(host, "pruebas", "conexion_inicial"), {
      nombre: "Host",
      mensaje: "sin timestamp",
      origen: "android",
      fechaLocal: Date.now(),
    }));

    await seedRoom(testEnv);
    await assertFails(updateDoc(doc(host, "partidas", "room_auth"), {
      visibilidad: "privada",
      actualizadaEn: serverTimestamp(),
    }));

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

    // Al volver de Gameplay, un jugador que fue liberado por una presencia obsoleta puede
    // recuperar su propio cupo y reconciliar el contador en una sola transaccion.
    await seedRoom(testEnv, "room_rematch_reactivate", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "partidas", "room_rematch_reactivate", "jugadores", "guest_uid"),
        { ...playerData("guest_uid", "Guest", 1), activoEnPartida: false, estado: "desconectado" }
      );
    });
    await assertSucceeds(runTransaction(guest, async (transaction) => {
      transaction.update(
        doc(guest, "partidas", "room_rematch_reactivate", "jugadores", "guest_uid"),
        {
          activoEnPartida: true,
          estado: "conectado",
          listo: false,
          ultimaConexion: serverTimestamp(),
          ultimaConexionLocal: Date.now(),
        }
      );
      transaction.update(doc(guest, "partidas", "room_rematch_reactivate"), {
        jugadoresActuales: increment(1),
        actualizadaEn: serverTimestamp(),
      });
    }));

    // Salir voluntariamente del lobby libera el documento propio y el contador de la sala
    // en la misma transaccion. Asi el navegador no sigue mostrando 5/5 despues de salir.
    await seedRoom(testEnv, "room_self_release", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await updateDoc(doc(db, "partidas", "room_self_release"), {
        jugadoresActuales: 2,
      });
      await setDoc(
        doc(db, "partidas", "room_self_release", "jugadores", "guest_uid"),
        playerData("guest_uid", "Guest", 1)
      );
    });
    await assertSucceeds(runTransaction(guest, async (transaction) => {
      const roomRef = doc(guest, "partidas", "room_self_release");
      const playerRef = doc(
        guest,
        "partidas",
        "room_self_release",
        "jugadores",
        "guest_uid"
      );
      await transaction.get(roomRef);
      await transaction.get(playerRef);
      transaction.update(playerRef, {
        activoEnPartida: false,
        listo: false,
        estado: "desconectado",
        ultimaConexion: serverTimestamp(),
        ultimaConexionLocal: Date.now(),
      });
      transaction.update(roomRef, {
        jugadoresActuales: 1,
        actualizadaEn: serverTimestamp(),
      });
    }));
    const releasedRoom = await assertSucceeds(
      getDoc(doc(guest, "partidas", "room_self_release"))
    );
    const releasedPlayer = await assertSucceeds(
      getDoc(doc(guest, "partidas", "room_self_release", "jugadores", "guest_uid"))
    );
    if (releasedRoom.data().jugadoresActuales !== 1) {
      throw new Error("La salida no decremento jugadoresActuales");
    }
    if (releasedPlayer.data().activoEnPartida !== false) {
      throw new Error("La salida no libero el cupo del jugador");
    }
    // Un expulsado/inactivo ya no puede enumerar el lobby, pero siempre conserva lectura
    // de su documento propio. La app usa ese get/listener puntual para mostrar su salida.
    await assertFails(getDocs(collection(
      guest,
      "partidas",
      "room_self_release",
      "jugadores"
    )));

    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      configLobby: {
        transicionSeg: 5,
        nocheSeg: 40,
        discusionSeg: 90,
        votacionSeg: 40,
        revelarRolesAlMorir: false,
        votosIndividuales: true,
        presetRoles: "CLASSIC",
        roles: "2,1,1,1,0,0,0,0,0,0,0",
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
        presetRoles: "PERSONALIZADO",
        roles: "1,1,1,1,1,0,0,0,0,0,0",
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
      const db = context.firestore();
      await updateDoc(doc(db, "partidas", "room_auth"), {
        estado: "en_juego",
        partidaInicialCreada: true,
        partidaInicial: {
          matchId: "match_rules_1",
          fase: "REPARTO",
          mapa: "pampa",
          mapaNombre: "Pampa",
          jugadores: [
            { orden: 0, uidTemporal: "host_uid", nombre: "Host" },
            { orden: 1, uidTemporal: "guest_uid", nombre: "Guest" },
          ],
        },
      });
      // Reproduce el caso real: una expulsión dejó un hueco, el reingreso reutilizó el
      // contador y el documento de lobby terminó con un orden distinto al roster del match.
      await updateDoc(doc(db, "partidas", "room_auth", "jugadores", "guest_uid"), {
        orden: 4,
      });
      await setDoc(
        doc(db, "partidas", "room_auth", "repartos", "guest_uid"),
        {
          matchId: "match_rules_1",
          uidTemporal: "guest_uid",
          rolesVisibles: [{ orden: 1, rolKey: "policia" }],
          creadaEn: serverTimestamp(),
        }
      );
    });

    const privateClue = {
      matchId: "match_rules_1",
      ronda: 1,
      phaseIndex: 3,
      objetivoNombre: "Mercenario",
      resultado: "sospechoso",
      actualizadaEn: serverTimestamp(),
    };
    const guestDealRef = doc(guest, "partidas", "room_auth", "repartos", "guest_uid");
    await assertSucceeds(updateDoc(
      doc(host, "partidas", "room_auth", "repartos", "guest_uid"),
      { pistaInvestigacion: privateClue }
    ));
    await assertSucceeds(getDoc(guestDealRef));
    await assertFails(updateDoc(guestDealRef, {
      pistaInvestigacion: {
        ...privateClue,
        actualizadaEn: serverTimestamp(),
      },
    }));
    await assertFails(updateDoc(
      doc(host, "partidas", "room_auth", "repartos", "guest_uid"),
      {
        rolesVisibles: [{ orden: 1, rolKey: "asesino" }],
        pistaInvestigacion: {
          ...privateClue,
          actualizadaEn: serverTimestamp(),
        },
      }
    ));

    const guestAction = await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
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
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 0 },
      cambiosVoto: 0,
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    const guestVoteRef = doc(guest, guestAction.path);
    await assertFails(updateDoc(guestVoteRef, {
      objetivoNombre: "Guest",
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 1 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now() + 1,
    }));
    await assertFails(updateDoc(guestVoteRef, {
      objetivoNombre: "Host",
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 0 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now() + 2,
    }));
    await assertFails(updateDoc(doc(host, guestAction.path), {
      objetivoNombre: "Guest",
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 1 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now() + 3,
    }));
    await assertFails(updateDoc(guestVoteRef, {
      fase: "NOCHE_POLICIA",
      objetivoNombre: "Host",
      detalles: { accion: "investigar", actorOrden: 1, objetivoOrden: 0 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now() + 4,
    }));
    await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "accion_jugador",
      actorId: "guest_uid",
      actorNombre: "Guest",
      actorEsHost: false,
      objetivoNombre: "Host",
      fase: "NOCHE_POLICIA",
      ronda: 1,
      phaseIndex: 3,
      modoCliente: "android",
      detalles: {
        accion: "investigar",
        actorOrden: 1,
        objetivoOrden: 0,
        faseResultado: "NOCHE_MEDICO",
        phaseIndexResultado: 4,
      },
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
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 1 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(intruder, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "accion_jugador",
      actorId: "intruder_uid",
      actorNombre: "Intruder",
      actorEsHost: false,
      objetivoNombre: "Host",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { accion: "votar", actorOrden: 2, objetivoOrden: 0 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "accion_jugador",
      actorId: "guest_uid",
      actorNombre: "Host",
      actorEsHost: false,
      objetivoNombre: "Host",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { accion: "votar", actorOrden: 1, objetivoOrden: 0 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
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
      detalles: { accion: "votar", actorOrden: 0, objetivoOrden: 0 },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertFails(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "fase_avanzada",
      actorId: "guest_uid",
      actorNombre: "Guest",
      actorEsHost: false,
      objetivoNombre: "",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { actorOrden: 1, faseNueva: "RECUENTO_VOTOS" },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    const hostAction = await assertSucceeds(addDoc(collection(host, "partidas", "room_auth", "acciones"), {
      matchId: "match_rules_1",
      tipo: "fase_avanzada",
      actorId: "host_uid",
      actorNombre: "Host",
      actorEsHost: true,
      objetivoNombre: "",
      fase: "VOTACION",
      ronda: 1,
      phaseIndex: 7,
      modoCliente: "online",
      detalles: { actorOrden: 0, faseNueva: "RECUENTO_VOTOS" },
      creadaEn: serverTimestamp(),
      creadaEnLocal: Date.now(),
    }));
    await assertSucceeds(getDoc(doc(guest, guestAction.path)));
    await assertSucceeds(getDoc(doc(host, guestAction.path)));
    await assertFails(getDoc(doc(guest, hostAction.path)));
    await assertFails(getDoc(doc(intruder, guestAction.path)));
    await assertSucceeds(getDocs(query(
      collection(guest, "partidas", "room_auth", "acciones"),
      where("actorId", "==", "guest_uid")
    )));
    await assertFails(getDocs(collection(guest, "partidas", "room_auth", "acciones")));

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
      estadoPartida: { fase: "REPARTO" },
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      "estadoPartida.inicioAutomaticoEpochMs": Date.now() + 15_000,
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_auth"), {
      "estadoPartida.inicioAutomaticoEpochMs": Date.now() + 60_000,
      ultimaActividadOnline: serverTimestamp(),
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
      hostVersion: increment(1),
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
    await assertSucceeds(setDoc(doc(oldHost, "codigosSala", "ABC234"), {
      partidaId: "room_handoff",
      codigoSala: "ABC234",
      hostId: "old_host_uid",
      creadaEn: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(guest, "partidas", "room_handoff", "jugadores", "guest_uid"), playerData("guest_uid", "Guest", 1)));
    // Un miembro no puede apropiarse de una partida mientras el anfitrion sigue conectado.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await updateDoc(doc(db, "partidas", "room_handoff"), {
        estado: "en_juego",
        partidaInicialCreada: true,
      });
    });
    await assertFails(updateDoc(doc(guest, "partidas", "room_handoff"), {
      hostActivoId: "guest_uid",
      hostVersion: increment(1),
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(host, "partidas", "room_auth"), {
      configLobby: {
        transicionSeg: 4,
        nocheSeg: 35,
        discusionSeg: 75,
        votacionSeg: 35,
        revelarRolesAlMorir: true,
        votosIndividuales: false,
        presetRoles: "PERSONALIZADO",
        roles: "2,1,1,0,1,0,0,0,0,0,0",
      },
      actualizadaEn: serverTimestamp(),
    }));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(
        doc(context.firestore(), "partidas", "room_handoff", "jugadores", "old_host_uid"),
        { estado: "desconectado" }
      );
    });
    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_handoff"), {
      hostActivoId: "guest_uid",
      hostVersion: increment(1),
      actualizadaEn: serverTimestamp(),
    }));
    // Al terminar, el coordinador temporal no se queda con la sala. Solo el creador estable
    // puede preparar la revancha y recupera la autoridad en el mismo cambio.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_handoff"), {
        estado: "finalizada",
        partidaInicial: { matchId: "handoff_match_1", mapa: "pampa" },
        estadoPartida: { ganador: "Pueblo" },
        estadoClientes: { guest_uid: { fase: "DIA_DEBATE" } },
        entradaLiberadaMatchId: "handoff_match_1",
      });
    });
    await assertFails(updateDoc(doc(guest, "partidas", "room_handoff"), {
      estado: "esperando",
      hostActivoId: "guest_uid",
      hostVersion: increment(1),
      partidaInicialCreada: false,
      limpiezaPendiente: true,
      partidaInicial: deleteField(),
      estadoPartida: deleteField(),
      estadoClientes: deleteField(),
      entradaLiberadaMatchId: deleteField(),
      jugadoresActuales: 2,
      ultimaActividadOnline: serverTimestamp(),
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(oldHost, "partidas", "room_handoff"), {
      estado: "esperando",
      hostActivoId: "old_host_uid",
      hostVersion: increment(1),
      partidaInicialCreada: false,
      limpiezaPendiente: true,
      partidaInicial: deleteField(),
      estadoPartida: deleteField(),
      estadoClientes: deleteField(),
      entradaLiberadaMatchId: deleteField(),
      jugadoresActuales: 2,
      ultimaActividadOnline: serverTimestamp(),
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

    // El botón manual "Pasar anfitrión" cambia la autoridad sin sacar al anfitrión
    // anterior ni alterar la cantidad de jugadores de la sala.
    await seedRoom(testEnv, "room_manual_transfer", "host_uid");
    await assertSucceeds(setDoc(
      doc(guest, "partidas", "room_manual_transfer", "jugadores", "guest_uid"),
      playerData("guest_uid", "Guest", 1)
    ));
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_manual_transfer"), {
        jugadoresActuales: 2,
      });
    });
    await assertSucceeds(runTransaction(host, async (transaction) => {
      transaction.update(doc(host, "partidas", "room_manual_transfer"), {
        hostId: "guest_uid",
        hostNombre: "Guest",
        hostActivoId: "guest_uid",
        hostVersion: increment(1),
        jugadoresActuales: 2,
        actualizadaEn: serverTimestamp(),
      });
      transaction.update(doc(host, "partidas", "room_manual_transfer", "jugadores", "host_uid"), {
        esHost: false,
      });
      transaction.update(doc(host, "partidas", "room_manual_transfer", "jugadores", "guest_uid"), {
        esHost: true,
      });
    }));

    // Si no hay otra cuenta registrada, el anfitrión cierra la sala de forma autoritativa.
    // No borra los documentos de los invitados: así sus clientes leen "abandonada" y no
    // confunden la desaparición de su membresía con una expulsión.
    await seedRoom(testEnv, "room_host_close", "host_uid");
    await assertSucceeds(runTransaction(host, async (transaction) => {
      transaction.update(doc(host, "partidas", "room_host_close"), {
        estado: "abandonada",
        jugadoresActuales: 0,
        actualizadaEn: serverTimestamp(),
        ultimaActividadOnline: serverTimestamp(),
      });
      transaction.update(doc(host, "partidas", "room_host_close", "jugadores", "host_uid"), {
        esHost: true,
        activoEnPartida: false,
        listo: false,
        estado: "desconectado",
        ultimaConexion: serverTimestamp(),
        ultimaConexionLocal: Date.now(),
      });
    }));

    await assertSucceeds(setDoc(doc(guest, "perfiles_publicos", "guest_uid"), {
      uidTemporal: "guest_uid",
      publicId: "2",
      nombrePerfil: "Guest",
      nombreSala: "Guest #2",
      bioPerfil: "Vine a sospechar.",
      avatarPerfil: "grecia_oraculo",
      fotoPlayGames: "https://lh3.googleusercontent.com/traidores-avatar",
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
    await assertFails(setDoc(doc(guest, "perfiles_publicos", "guest_uid"), {
      uidTemporal: "guest_uid",
      publicId: "2",
      nombrePerfil: "Guest",
      nombreSala: "Guest #2",
      bioPerfil: "Vine a sospechar.",
      avatarPerfil: "grecia_oraculo",
      fotoPlayGames: `https://${"a".repeat(1001)}`,
      bannerPerfil: "pampa",
      rolFavoritoPerfil: "pampa_payador",
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(deleteDoc(doc(guest, "codigosSala", "ABC234")));
    await assertSucceeds(deleteDoc(doc(oldHost, "codigosSala", "ABC234")));
    await assertFails(deleteDoc(doc(intruder, "perfiles_publicos", "guest_uid")));
    await assertSucceeds(deleteDoc(doc(guest, "perfiles_publicos", "guest_uid")));

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

    // --- Baneos por sala: solo host/afectado leen y el afectado no puede volver a escribir ---
    await seedRoom(testEnv, "room_bans", "host_uid");
    await assertSucceeds(setDoc(
      doc(guest, "partidas", "room_bans", "jugadores", "guest_uid"),
      playerData("guest_uid", "Guest", 1)
    ));
    const guestBan = doc(host, "partidas", "room_bans", "baneados", "guest_uid");
    await assertSucceeds(setDoc(guestBan, {
      uidTemporal: "guest_uid",
      nombre: "Guest",
      motivo: "abuso",
      baneadoPor: "host_uid",
      creadaEn: serverTimestamp(),
    }));
    await assertSucceeds(getDoc(doc(guest, guestBan.path)));
    await assertSucceeds(getDoc(guestBan));
    await assertFails(getDoc(doc(intruder, guestBan.path)));
    await assertSucceeds(getDocs(collection(host, "partidas", "room_bans", "baneados")));
    await assertFails(getDocs(collection(intruder, "partidas", "room_bans", "baneados")));
    await assertFails(updateDoc(
      doc(guest, "partidas", "room_bans", "jugadores", "guest_uid"),
      { ultimaConexionLocal: Date.now() }
    ));
    await assertSucceeds(updateDoc(
      doc(host, "partidas", "room_bans", "jugadores", "guest_uid"),
      { estado: "desconectado" }
    ));

    // --- Repartos privados y reportes inmutables ---
    const reparto = {
      matchId: "match_private_1",
      uidTemporal: "guest_uid",
      rolesVisibles: [{
        orden: 1,
        rolKey: "policia",
        rolNombre: "Policia",
        rolEquipo: "Pueblo",
        rolImagen: "role_policia",
      }],
      creadaEn: serverTimestamp(),
    };
    const repartoRef = doc(host, "partidas", "room_bans", "repartos", "guest_uid");
    await assertSucceeds(setDoc(repartoRef, reparto));
    await assertSucceeds(getDoc(doc(guest, repartoRef.path)));
    await assertFails(getDoc(doc(intruder, repartoRef.path)));
    await assertSucceeds(getDocs(collection(host, "partidas", "room_bans", "repartos")));
    await assertFails(getDocs(collection(intruder, "partidas", "room_bans", "repartos")));

    const reportId = "match_private_1_guest_uid_host_uid";
    const report = {
      reportanteId: "guest_uid",
      reportadoId: "host_uid",
      reportadoNombre: "Host",
      roomId: "room_bans",
      matchId: "match_private_1",
      motivo: "toxicidad",
      detalle: "Insultos en el chat",
      creadaEn: serverTimestamp(),
    };
    await assertSucceeds(setDoc(doc(guest, "reportes", reportId), report));
    await assertFails(getDoc(doc(guest, "reportes", reportId)));
    await assertFails(setDoc(doc(guest, "reportes", `${reportId}_duplicado`), report));
    await assertFails(setDoc(doc(intruder, "reportes", reportId), report));

    // Si el reparto ya fue enviado pero la barrera de entrada queda bloqueada, el anfitrión
    // puede cancelar ese inicio. Un invitado no puede abandonar la partida para toda la sala.
    await seedRoom(testEnv, "room_start_cancel", "host_uid");
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_start_cancel"), {
      estado: "en_juego",
      partidaInicialCreada: true,
      estadoPartida: { fase: "REPARTO", ganador: "" },
      actualizadaEn: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, "partidas", "room_start_cancel"), {
      estado: "abandonada",
      actualizadaEn: serverTimestamp(),
      ultimaActividadOnline: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_start_cancel"), {
      estado: "abandonada",
      actualizadaEn: serverTimestamp(),
      ultimaActividadOnline: serverTimestamp(),
    }));

    // --- Cierre completo de salas vacias (teardown) ---
    await seedRoom(testEnv, "room_teardown_empty", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "partidas", "room_teardown_empty", "acciones", "accion_1"), {
        matchId: "irrelevante",
        tipo: "fase_avanzada",
        actorId: "host_uid",
        actorNombre: "Host",
        actorEsHost: true,
        fase: "REPARTO",
        ronda: 0,
        phaseIndex: 0,
        modoCliente: "android",
        detalles: {},
        creadaEn: serverTimestamp(),
        creadaEnLocal: Date.now(),
      });
    });
    await assertFails(deleteDoc(doc(intruder, "partidas", "room_teardown_empty")));
    await assertFails(deleteDoc(doc(guest, "partidas", "room_teardown_empty", "jugadores", "host_uid")));
    await assertSucceeds(deleteDoc(doc(host, "partidas", "room_teardown_empty", "jugadores", "host_uid")));
    await assertSucceeds(deleteDoc(doc(host, "partidas", "room_teardown_empty", "acciones", "accion_1")));
    await assertSucceeds(deleteDoc(doc(host, "partidas", "room_teardown_empty")));

    await seedRoom(testEnv, "room_teardown_ingame", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await updateDoc(doc(db, "partidas", "room_teardown_ingame"), { estado: "en_juego" });
    });
    await assertFails(deleteDoc(doc(host, "partidas", "room_teardown_ingame", "jugadores", "host_uid")));
    await assertFails(deleteDoc(doc(host, "partidas", "room_teardown_ingame")));

    // El creador recupera la capacidad de limpiar una sala propia después de 24 horas sin
    // actividad, incluso si la partida quedó en juego y el host activo había cambiado.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const staleRoom = roomData("host_uid");
      staleRoom.estado = "en_juego";
      staleRoom.hostActivoId = "guest_uid";
      staleRoom.actualizadaEn = Timestamp.fromMillis(Date.now() - (25 * 60 * 60 * 1000));
      await setDoc(doc(db, "partidas", "room_stale_creator"), staleRoom);
      await setDoc(
        doc(db, "partidas", "room_stale_creator", "jugadores", "host_uid"),
        playerData("host_uid", "Host", 0, true)
      );
      await setDoc(doc(db, "partidas", "room_stale_creator", "acciones", "accion_1"), {
        actorId: "host_uid",
      });
      await setDoc(doc(db, "codigosSala", "STALE1"), {
        partidaId: "room_stale_creator",
        codigoSala: "STALE1",
        hostId: "host_uid",
        creadaEn: Timestamp.fromMillis(Date.now() - (25 * 60 * 60 * 1000)),
      });

      const recentRoom = roomData("host_uid");
      recentRoom.estado = "en_juego";
      recentRoom.actualizadaEn = Timestamp.fromMillis(Date.now() - (23 * 60 * 60 * 1000));
      await setDoc(doc(db, "partidas", "room_recent_creator"), recentRoom);
    });
    await assertFails(deleteDoc(doc(host, "partidas", "room_recent_creator")));
    await assertSucceeds(getDocs(query(
      collection(host, "partidas"),
      where("hostId", "==", "host_uid")
    )));
    await assertFails(getDocs(query(
      collection(intruder, "partidas"),
      where("hostId", "==", "host_uid")
    )));
    await assertFails(getDocs(collection(intruder, "partidas", "room_stale_creator", "jugadores")));
    await assertSucceeds(getDocs(collection(host, "partidas", "room_stale_creator", "jugadores")));
    await assertSucceeds(deleteDoc(doc(host, "partidas", "room_stale_creator", "jugadores", "host_uid")));
    await assertSucceeds(deleteDoc(doc(host, "partidas", "room_stale_creator", "acciones", "accion_1")));
    const staleCleanupBatch = writeBatch(host);
    staleCleanupBatch.delete(doc(host, "codigosSala", "STALE1"));
    staleCleanupBatch.delete(doc(host, "partidas", "room_stale_creator"));
    await assertSucceeds(staleCleanupBatch.commit());

    const browserQuery = query(
      collection(guest, "partidas"),
      where("estado", "==", "esperando"),
      where("visibilidad", "==", "publica"),
      orderBy("actualizadaEn", "desc"),
      limit(30)
    );
    await assertSucceeds(getDocs(browserQuery));
    await assertSucceeds(getDoc(doc(guest, "partidas", "room_auth")));
    await seedRoom(testEnv, "room_private_state", "host_uid");
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_private_state"), {
        estado: "en_juego",
      });
    });
    await assertSucceeds(getDoc(doc(host, "partidas", "room_private_state")));
    await assertFails(getDoc(doc(intruder, "partidas", "room_private_state")));

    await assertSucceeds(setDoc(doc(host, "codigosSala", "ABC234"), {
      partidaId: "room_auth",
      codigoSala: "ABC234",
      hostId: "host_uid",
      creadaEn: serverTimestamp(),
    }));
    await assertSucceeds(getDoc(doc(guest, "codigosSala", "ABC234")));
    await assertFails(getDocs(collection(guest, "codigosSala")));
    await assertFails(setDoc(doc(intruder, "codigosSala", "XYZ234"), {
      partidaId: "room_auth",
      codigoSala: "XYZ234",
      hostId: "intruder_uid",
      creadaEn: serverTimestamp(),
    }));

    const atomicRoomRef = doc(host, "partidas", "room_atomic_create");
    const atomicBatch = writeBatch(host);
    atomicBatch.set(atomicRoomRef, {
      ...roomData("host_uid"),
      codigoSala: "QWE234",
    });
    atomicBatch.set(
      doc(host, "partidas", "room_atomic_create", "jugadores", "host_uid"),
      playerData("host_uid", "Host", 0, true)
    );
    atomicBatch.set(doc(host, "codigosSala", "QWE234"), {
      partidaId: "room_atomic_create",
      codigoSala: "QWE234",
      hostId: "host_uid",
      creadaEn: serverTimestamp(),
    });
    await assertSucceeds(atomicBatch.commit());

    const rollbackBatch = writeBatch(host);
    rollbackBatch.delete(doc(host, "partidas", "room_atomic_create", "jugadores", "host_uid"));
    rollbackBatch.delete(doc(host, "codigosSala", "QWE234"));
    rollbackBatch.delete(atomicRoomRef);
    await assertSucceeds(rollbackBatch.commit());
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
