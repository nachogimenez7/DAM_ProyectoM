/**
 * Reglas de invitado contra cuenta registrada.
 *
 * Archivo aparte de `test-firestore-rules.cjs` a proposito: aquel cubre el contrato general
 * de salas y este solo el corte por identidad, que es lo que hay que poder correr solo cuando
 * se toca la lista de alias o `isRegistered()`.
 *
 * Ojo con los contextos: `authenticatedContext(uid)` a secas produce un token cuyo
 * `sign_in_provider` **no** es `anonymous`, o sea que para las reglas es una cuenta
 * registrada. Un invitado de verdad hay que pedirlo explicitamente.
 */
const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  addDoc,
  collection,
  doc,
  runTransaction,
  setDoc,
  updateDoc,
  serverTimestamp,
} = require("firebase/firestore");

const projectId = "traidores-local";

const roomData = (hostUid) => ({
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
  jugadoresEsperados: 5,
  maxJugadores: 5,
  jugadoresActuales: 1,
  modoPrueba: false,
  origen: "rules-test",
  creadaEn: serverTimestamp(),
  actualizadaEn: serverTimestamp(),
});

/** Documento de jugador de una cuenta registrada: nombre libre, `#` y frase propia. */
const registeredPlayer = (uid, name = "Nacho", order = 1) => ({
  nombre: name,
  nombrePerfil: name,
  nombreSala: name,
  publicId: "7",
  bioPerfil: "No fui yo.",
  avatarPerfil: "grecia_oraculo",
  bannerPerfil: "pampa",
  rolFavoritoPerfil: "pampa_payador",
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

/** Lo que escribe la app cuando no hay cuenta: alias de la lista, sin `#` y sin frase. */
const guestPlayer = (uid, name = "Aguafiestas 4821", order = 2) => ({
  nombre: name,
  nombrePerfil: name,
  nombreSala: name,
  bioPerfil: "",
  avatarPerfil: "grecia_oraculo",
  bannerPerfil: "pampa",
  rolFavoritoPerfil: "pampa_payador",
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
    const host = testEnv.authenticatedContext("reg_host_uid").firestore();
    const registered = testEnv.authenticatedContext("reg_uid").firestore();
    // Invitado real: token anonimo, que es lo que devuelve `signInAnonymously`.
    const invitado = testEnv
      .authenticatedContext("guest_uid", {
        firebase: { sign_in_provider: "anonymous" },
      })
      .firestore();
    // Recien vinculado: el proveedor del token todavia dice `anonymous`, pero ya hay correo.
    // Es el caso que rompia si las reglas miraran una sola señal.
    const recienVinculado = testEnv
      .authenticatedContext("linked_uid", {
        firebase: { sign_in_provider: "anonymous" },
        email: "nacho@correo.com",
      })
      .firestore();
    // Play Games: cuenta real **sin correo**. Para este jugador el proveedor del token es la
    // unica señal que lo distingue de un invitado, asi que el refresco del token despues de
    // vincular deja de ser un cinturon de seguridad y pasa a ser la pieza que lo sostiene.
    const playGames = testEnv
      .authenticatedContext("pgs_uid", {
        firebase: { sign_in_provider: "playgames.google.com" },
      })
      .firestore();

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "partidas", "room_guest"), roomData("reg_host_uid"));
      await setDoc(
        doc(db, "partidas", "room_guest", "jugadores", "reg_host_uid"),
        registeredPlayer("reg_host_uid", "Host", 0)
      );
    });

    // --- Crear sala ---
    await assertSucceeds(setDoc(doc(host, "partidas", "room_reg"), roomData("reg_host_uid")));
    await assertFails(setDoc(doc(invitado, "partidas", "room_by_guest"), roomData("guest_uid")));
    // Vincular la cuenta tiene que habilitarlo aunque el proveedor del token no se haya
    // refrescado todavia.
    await assertSucceeds(
      setDoc(doc(recienVinculado, "partidas", "room_linked"), roomData("linked_uid"))
    );

    // --- Entrar a una sala ---
    await assertSucceeds(
      setDoc(
        doc(registered, "partidas", "room_guest", "jugadores", "reg_uid"),
        registeredPlayer("reg_uid")
      )
    );
    await assertSucceeds(
      setDoc(
        doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
        guestPlayer("guest_uid")
      )
    );

    // --- Lo que un invitado no puede mostrar ---
    // Nombre libre.
    await assertFails(
      setDoc(
        doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
        guestPlayer("guest_uid", "Nacho")
      )
    );
    // Alias que no esta en la lista cerrada.
    await assertFails(
      setDoc(
        doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
        guestPlayer("guest_uid", "Insulto 4821")
      )
    );
    // Alias de la lista pero sin numero.
    await assertFails(
      setDoc(
        doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
        guestPlayer("guest_uid", "Aguafiestas")
      )
    );
    // Numero publico propio.
    await assertFails(
      setDoc(doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"), {
        ...guestPlayer("guest_uid"),
        publicId: "7",
      })
    );
    // Frase con texto libre.
    await assertFails(
      setDoc(doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"), {
        ...guestPlayer("guest_uid"),
        bioPerfil: "cualquier cosa",
      })
    );
    // Nombre visible de sala distinto del alias.
    await assertFails(
      setDoc(doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"), {
        ...guestPlayer("guest_uid"),
        nombreSala: "Nacho el crack",
      })
    );

    // --- Todos los alias de la lista tienen que entrar ---
    const aliases = [
      "Forastero",
      "Mala Onda",
      "Aguafiestas",
      "Chamuyero",
      "Careta",
      "Mufa",
      "Perejil",
      "Metepatas",
      "Don Nadie",
      "El Colado",
      "Sospechoso",
      "Rezongón",
    ];
    for (const alias of aliases) {
      await assertSucceeds(
        setDoc(
          doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
          guestPlayer("guest_uid", `${alias} 4821`)
        )
      );
    }

    // --- Marcarse listo sigue funcionando sin cuenta ---
    await assertSucceeds(
      updateDoc(doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"), {
        listo: true,
        ultimaConexionLocal: Date.now(),
      })
    );

    // --- Salida de un invitado ---
    // Reproduce la transacción Android: libera su documento y decrementa el contador sin
    // necesitar permisos de anfitrión.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "partidas", "room_guest_exit"), {
        ...roomData("reg_host_uid"),
        jugadoresActuales: 2,
      });
      await setDoc(
        doc(db, "partidas", "room_guest_exit", "jugadores", "reg_host_uid"),
        registeredPlayer("reg_host_uid", "Host", 0)
      );
      await setDoc(
        doc(db, "partidas", "room_guest_exit", "jugadores", "guest_uid"),
        guestPlayer("guest_uid", "Aguafiestas 4821", 1)
      );
    });
    await assertSucceeds(runTransaction(invitado, async (transaction) => {
      const roomRef = doc(invitado, "partidas", "room_guest_exit");
      const playerRef = doc(
        invitado,
        "partidas",
        "room_guest_exit",
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

    // --- Anfitrion estable ---
    // Un invitado no puede quedarse con la sala aunque el creador este ausente.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "partidas", "room_takeover"), roomData("reg_host_uid"));
      await setDoc(doc(db, "partidas", "room_takeover", "jugadores", "guest_uid"), {
        ...guestPlayer("guest_uid"),
        orden: 1,
      });
      await setDoc(
        doc(db, "partidas", "room_takeover", "jugadores", "reg_uid"),
        registeredPlayer("reg_uid", "Registrado", 2)
      );
    });
    await assertFails(
      updateDoc(doc(invitado, "partidas", "room_takeover"), {
        hostId: "guest_uid",
        hostNombre: "Aguafiestas 4821",
        hostActivoId: "guest_uid",
        hostVersion: 1,
        jugadoresActuales: 1,
        actualizadaEn: serverTimestamp(),
      })
    );
    // Y el anfitrion tampoco puede entregarle la sala a un invitado.
    await assertFails(
      updateDoc(doc(host, "partidas", "room_takeover"), {
        hostId: "guest_uid",
        hostNombre: "Aguafiestas 4821",
        hostActivoId: "guest_uid",
        hostVersion: 1,
        jugadoresActuales: 1,
        actualizadaEn: serverTimestamp(),
      })
    );
    // A una cuenta registrada si.
    await assertSucceeds(
      updateDoc(doc(host, "partidas", "room_takeover"), {
        hostId: "reg_uid",
        hostNombre: "Registrado",
        hostActivoId: "reg_uid",
        hostVersion: 1,
        jugadoresActuales: 1,
        actualizadaEn: serverTimestamp(),
      })
    );
    // --- Compatibilidad con el campo viejo `soloCuentas` ---
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, "partidas", "room_solo_cuentas"), {
        ...roomData("reg_host_uid"),
        soloCuentas: true,
      });
      await setDoc(
        doc(db, "partidas", "room_solo_cuentas", "jugadores", "reg_host_uid"),
        registeredPlayer("reg_host_uid", "Host", 0)
      );
    });
    // La opcion fue retirada: una sala vieja que conserve el campo ya no rechaza invitados.
    await assertSucceeds(
      setDoc(
        doc(invitado, "partidas", "room_solo_cuentas", "jugadores", "guest_uid"),
        guestPlayer("guest_uid")
      )
    );
    // Las cuentas registradas siguen entrando normalmente.
    await assertSucceeds(
      setDoc(
        doc(registered, "partidas", "room_solo_cuentas", "jugadores", "reg_uid"),
        registeredPlayer("reg_uid")
      )
    );
    // El campo obsoleto tampoco bloquea una actualizacion normal de la sala.
    await assertSucceeds(
      updateDoc(doc(host, "partidas", "room_solo_cuentas"), {
        soloCuentas: false,
        actualizadaEn: serverTimestamp(),
      })
    );
    // Una sala normal sigue aceptando invitados aunque no traiga el campo.
    await assertSucceeds(
      setDoc(
        doc(invitado, "partidas", "room_guest", "jugadores", "guest_uid"),
        guestPlayer("guest_uid")
      )
    );

    // --- Play Games cuenta como cuenta registrada, sin cambiar una sola regla ---
    await assertSucceeds(setDoc(doc(playGames, "partidas", "room_pgs"), roomData("pgs_uid")));
    await assertSucceeds(
      setDoc(
        doc(playGames, "partidas", "room_solo_cuentas", "jugadores", "pgs_uid"),
        registeredPlayer("pgs_uid", "Nacho PGS", 3)
      )
    );
    // Y puede usar nombre libre: no le aplica la lista cerrada de alias.
    await assertSucceeds(
      setDoc(
        doc(playGames, "partidas", "room_guest", "jugadores", "pgs_uid"),
        registeredPlayer("pgs_uid", "Nacho el Traidor", 4)
      )
    );

    // --- Gameplay: invitado Detective con orden de lobby desfasado ---
    // El roster del match ubica al invitado en 1, aunque su documento quedó en 2 por un
    // hueco/reingreso. La identidad de la acción debe seguir el roster autoritativo.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "partidas", "room_guest"), {
        estado: "en_juego",
        partidaInicialCreada: true,
        partidaInicial: {
          matchId: "match_guest_detective_1",
          fase: "REPARTO",
          mapa: "pampa",
          mapaNombre: "Pampa",
          jugadores: [
            { orden: 0, uidTemporal: "reg_host_uid", nombre: "Host" },
            { orden: 1, uidTemporal: "guest_uid", nombre: "Aguafiestas 4821" },
          ],
        },
      });
    });
    await assertSucceeds(addDoc(
      collection(invitado, "partidas", "room_guest", "acciones"),
      {
        matchId: "match_guest_detective_1",
        tipo: "accion_jugador",
        actorId: "guest_uid",
        actorNombre: "Aguafiestas 4821",
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
      }
    ));
  } finally {
    await testEnv.cleanup();
  }
}

main()
  .then(() => {
    console.log("Reglas de invitado: OK.");
  })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
