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
  jugadoresEsperados: expectedPlayers,
  maxJugadores: expectedPlayers,
  jugadoresActuales: 1,
  modoPrueba: modePrueba,
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
      port: 8080,
    },
  });

  try {
    const host = testEnv.authenticatedContext("host_uid").firestore();
    const guest = testEnv.authenticatedContext("guest_uid").firestore();
    const intruder = testEnv.authenticatedContext("intruder_uid").firestore();
    const anon = testEnv.unauthenticatedContext().firestore();

    await assertFails(setDoc(doc(anon, "partidas", "room_no_auth"), roomData("host_uid")));
    await assertSucceeds(setDoc(doc(host, "partidas", "room_create"), roomData("host_uid")));
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

    await assertSucceeds(setDoc(doc(guest, "partidas", "room_auth", "jugadores", "guest_uid"), playerData("guest_uid", "Guest", 1)));
    await assertFails(setDoc(doc(guest, "partidas", "room_auth", "jugadores", "other_uid"), playerData("other_uid", "Other", 2)));

    await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "acciones"), {
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

    await assertSucceeds(addDoc(collection(guest, "partidas", "room_auth", "chat"), {
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
      partidaInicial: { fase: "REPARTO" },
      estadoPartida: { fase: "RESULTADO" },
      actualizadaEn: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(host, "partidas", "room_auth"), {
      estado: "esperando",
      partidaInicialCreada: false,
      partidaInicial: deleteField(),
      estadoPartida: deleteField(),
      estadoClientes: deleteField(),
      ultimaActividadOnline: serverTimestamp(),
      actualizadaEn: serverTimestamp(),
    }));

    await seedRoom(testEnv, "room_handoff", "old_host_uid");
    await assertSucceeds(setDoc(doc(guest, "partidas", "room_handoff", "jugadores", "guest_uid"), playerData("guest_uid", "Guest", 1)));
    await assertSucceeds(updateDoc(doc(guest, "partidas", "room_handoff"), {
      hostActivoId: "guest_uid",
      hostVersion: increment(1),
      actualizadaEn: serverTimestamp(),
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
