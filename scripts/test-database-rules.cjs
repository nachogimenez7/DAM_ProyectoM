const fs = require("fs");
const assert = require("node:assert/strict");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const projectId = "traidores-local";
const roomId = "room_rules_test";
const matchId = "match-123";
const serverTimestamp = { ".sv": "timestamp" };

const member = (
  nombre,
  { lobby = false, alive = true, traitor = false, oracleInvited = false } = {}
) => ({
  nombre,
  activo: true,
  enLobby: lobby,
  vivo: alive,
  traidor: traitor,
  invitadoOraculo: oracleInvited,
  actualizadaEn: Date.now(),
});

const chatMessage = (actorId, speaker, extra = {}) => ({
  matchId,
  actorId,
  speaker,
  mensaje: "Mensaje de prueba",
  fase: "DIA_DEBATE",
  ronda: 1,
  isGod: false,
  tipo: "texto",
  ts: serverTimestamp,
  ...extra,
});

const emoteEvent = (actorId, player) => ({
  matchId,
  actorId,
  player,
  emoteId: "griego_enojado",
  ts: serverTimestamp,
});

const clientSyncState = (uid, extra = {}) => ({
  matchId,
  fase: "DIA_DEBATE",
  ronda: 1,
  phaseIndex: 4,
  enGameplay: true,
  jugadoresVistos: 4,
  jugadoresEsperados: 4,
  uidTemporal: uid,
  actualizadaEn: Date.now(),
  ...extra,
});

const voteReadyState = (nombre, extra = {}) => ({
  matchId,
  nombre,
  listo: true,
  ronda: 1,
  phaseIndex: 4,
  actualizadaEn: Date.now(),
  ...extra,
});

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId,
    database: { rules: fs.readFileSync("database.rules.json", "utf8") },
  });

  try {
    const alice = testEnv.authenticatedContext("alice").database();
    const bob = testEnv.authenticatedContext("bob").database();
    const carol = testEnv.authenticatedContext("carol").database();
    const dana = testEnv.authenticatedContext("dana").database();
    const outsider = testEnv.authenticatedContext("outsider").database();
    const guest = testEnv.unauthenticatedContext().database();

    // El anfitrión crea el registro antes de admitir a los participantes.
    await assertSucceeds(
      alice.ref(`salas/${roomId}/control/hostUid`).set("alice")
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/control/creatorUid`).set("alice")
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}`).update({
        "control/matchId": matchId,
        "control/jugadoresVivos": 4,
        "control/actualizadaEn": Date.now(),
        "miembros/alice": member("Alice"),
        "miembros/bob": member("Bob"),
        "miembros/carol": member("Carol", { traitor: true }),
        "miembros/dana": member("Dana"),
      })
    );

    // Un autenticado ajeno no puede autoadmitirse ni fabricar presencia.
    await assertFails(
      outsider.ref(`salas/${roomId}/miembros/outsider`).set(member("Intruso"))
    );
    await assertFails(
      outsider.ref(`salas/${roomId}/presencia/outsider`).set({
        estado: "conectado",
        ts: serverTimestamp,
      })
    );
    await assertFails(outsider.ref(`salas/${roomId}/chat`).once("value"));
    await assertFails(
      outsider.ref(`salas/${roomId}/sincronizacion/clientes`).once("value")
    );
    await assertFails(outsider.ref(`salas/${roomId}`).remove());

    for (const [uid, db] of [["alice", alice], ["bob", bob], ["carol", carol], ["dana", dana]]) {
      await assertSucceeds(
        db.ref(`salas/${roomId}/presencia/${uid}`).set({
          estado: "conectado",
          ts: serverTimestamp,
        })
      );
    }
    await assertFails(
      bob.ref(`salas/${roomId}/presencia/alice`).set({ estado: "desconectado", ts: Date.now() })
    );
    await assertFails(outsider.ref(`salas/${roomId}/presencia`).once("value"));
    await assertSucceeds(bob.ref(`salas/${roomId}/presencia`).once("value"));

    const authoritativeState = {
      matchId,
      phaseIndex: 4,
      actualizadaPor: "alice",
      actualizadaEn: Date.now(),
      estadoPartida: {
        versionEstado: 2,
        fase: "DIA_DEBATE",
        ronda: 1,
        phaseIndex: 4,
        anuncioPublico: "La mesa debate.",
      },
    };
    await assertSucceeds(
      alice.ref(`salas/${roomId}/estado_partida`).set(authoritativeState)
    );
    await assertSucceeds(bob.ref(`salas/${roomId}/estado_partida`).once("value"));
    await assertFails(outsider.ref(`salas/${roomId}/estado_partida`).once("value"));
    await assertFails(
      bob.ref(`salas/${roomId}/estado_partida`).set({
        ...authoritativeState,
        actualizadaPor: "bob",
        actualizadaEn: Date.now(),
      })
    );
    await assertFails(
      alice.ref(`salas/${roomId}/estado_partida`).set({
        ...authoritativeState,
        matchId: "match-viejo",
        actualizadaEn: Date.now(),
      })
    );

    // Una publicación rezagada del mismo host no puede retroceder una fase que
    // ya confirmó el servidor; las actualizaciones dentro de la fase siguen válidas.
    await assertFails(alice.ref(`salas/${roomId}/estado_partida`).set({
      ...authoritativeState, phaseIndex: 3,
      estadoPartida: { ...authoritativeState.estadoPartida, phaseIndex: 3 },
    }));
    await assertFails(alice.ref(`salas/${roomId}/estado_partida`).set({
      ...authoritativeState, phaseIndex: 4.5,
      estadoPartida: { ...authoritativeState.estadoPartida, phaseIndex: 4.5 },
    }));
    await assertSucceeds(alice.ref(`salas/${roomId}/estado_partida`).set({
      ...authoritativeState,
      estadoPartida: { ...authoritativeState.estadoPartida, anuncioPublico: "Actualizado" },
    }));

    // Confirmaciones y "listo para votar" son efimeros: cada jugador solo publica su
    // nodo, todos los miembros activos pueden leerlos y el matchId evita datos de revancha.
    await assertSucceeds(
      bob.ref(`salas/${roomId}/sincronizacion/clientes/bob`).set(
        clientSyncState("bob")
      )
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/sincronizacion/clientes`).once("value")
    );
    // A heartbeat must preserve readiness and phase, using the existing closed schema.
    const heartbeatRef = bob.ref(`salas/${roomId}/sincronizacion/clientes/bob`);
    const beforeHeartbeat = (await heartbeatRef.once("value")).val();
    await assertSucceeds(heartbeatRef.update({matchId, actualizadaEn: serverTimestamp}));
    const afterHeartbeat = (await heartbeatRef.once("value")).val();
    for (const [key, value] of Object.entries(beforeHeartbeat)) {
      if (key !== "actualizadaEn") assert.deepEqual(afterHeartbeat[key], value);
    }
    await assertFails(alice.ref(`salas/${roomId}/sincronizacion/clientes/bob`).update({actualizadaEn: serverTimestamp}));
    await assertFails(heartbeatRef.update({matchId: "stale-match", actualizadaEn: serverTimestamp}));

    await assertFails(
      bob.ref(`salas/${roomId}/sincronizacion/clientes/alice`).set(
        clientSyncState("alice")
      )
    );
    await assertFails(
      bob.ref(`salas/${roomId}/sincronizacion/clientes/bob`).set(
        clientSyncState("bob", { matchId: "match-anterior" })
      )
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/sincronizacion/listosVotacion/bob`).set(
        voteReadyState("Bob")
      )
    );
    await assertFails(
      bob.ref(`salas/${roomId}/sincronizacion/listosVotacion/bob`).set(
        voteReadyState("Alice", { actualizadaEn: Date.now() + 1_000 })
      )
    );

    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat/message-bob`).set(chatMessage("bob", "Bob"))
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat/message-spoof`).set(chatMessage("bob", "Alice"))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/emotes/bob`).set(emoteEvent("bob", "Bob"))
    );
    await assertFails(
      bob.ref(`salas/${roomId}/emotes/bob`).set({
        ...emoteEvent("bob", "Bob"), emoteId: "griego_contento",
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/emotes/forged-key`).set(emoteEvent("bob", "Bob"))
    );

    // El canal traidor no se protege por UI: las reglas comprueban el bando y que siga vivo.
    await assertFails(bob.ref(`salas/${roomId}/chat_traidores`).once("value"));
    await assertSucceeds(carol.ref(`salas/${roomId}/chat_traidores`).once("value"));
    await assertFails(
      bob.ref(`salas/${roomId}/chat_traidores/message-town`).set(
        chatMessage("bob", "Bob", { canal: "traidores" })
      )
    );
    await assertSucceeds(
      carol.ref(`salas/${roomId}/chat_traidores/message-plan`).set(
        chatMessage("carol", "Carol", { canal: "traidores" })
      )
    );
    // El anfitrión puede publicar avisos del Plan derivados de acciones confirmadas.
    await assertSucceeds(
      alice.ref(`salas/${roomId}/chat_traidores/action-plan`).set(
        chatMessage("alice", "Plan", {
          canal: "traidores", tipo: "accion", isGod: true,
          actorNombre: "Carol", objetivoNombre: "Bob",
          accionRol: "espia", faseIndice: 3,
        })
      )
    );
    await assertFails(
      carol.ref(`salas/${roomId}/chat_traidores/action-forged`).set(
        chatMessage("carol", "Plan", {
          canal: "traidores", tipo: "accion", isGod: true,
        })
      )
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_traidores/action-town-forged`).set(
        chatMessage("bob", "Plan", {
          canal: "traidores", tipo: "accion", isGod: true,
        })
      )
    );

    // Al morir deja de ver traidores y pasa al canal de espectadores.
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/carol`).set(
        member("Carol", { alive: false, traitor: true })
      )
    );
    await assertFails(carol.ref(`salas/${roomId}/chat_traidores`).once("value"));
    await assertSucceeds(carol.ref(`salas/${roomId}/chat_espectadores`).once("value"));
    await assertFails(bob.ref(`salas/${roomId}/chat_espectadores`).once("value"));
    await assertSucceeds(
      carol.ref(`salas/${roomId}/chat_espectadores/message-dead`).set(
        chatMessage("carol", "Carol", { canal: "espectadores" })
      )
    );

    // El Oraculo puede devolver temporalmente a un muerto al chat publico. Solo el
    // anfitrion puede conceder y revocar ese permiso; el muerto no puede fabricarlo.
    await assertFails(
      carol.ref(`salas/${roomId}/chat/message-dead-public`).set(
        chatMessage("carol", "Carol")
      )
    );
    await assertFails(
      carol.ref(`salas/${roomId}/miembros/carol/invitadoOraculo`).set(true)
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/carol`).set(
        member("Carol", { alive: false, traitor: true, oracleInvited: true })
      )
    );
    await assertSucceeds(
      carol.ref(`salas/${roomId}/chat/message-oracle-invited`).set(
        chatMessage("carol", "Carol")
      )
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/carol`).set(
        member("Carol", { alive: false, traitor: true })
      )
    );
    await assertFails(
      carol.ref(`salas/${roomId}/chat/message-oracle-ended`).set(
        chatMessage("carol", "Carol")
      )
    );

    // Lobby y gameplay son permisos distintos.
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/0`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: serverTimestamp,
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/bob`).set(member("Bob", { lobby: true }))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_lobby/bob/0`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: serverTimestamp,
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/1`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Spam", tipo: "texto", ts: serverTimestamp,
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/2`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Slot invalido", tipo: "texto", ts: serverTimestamp,
      })
    );
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.database();
      const oldTimestamp = Date.now() - 5_000;
      await db.ref(`salas/${roomId}/chat_lobby/bob/0`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Anterior", tipo: "texto", ts: oldTimestamp,
      });
      await db.ref(`salas/${roomId}/chat_lobby/bob/1`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Anterior", tipo: "texto", ts: oldTimestamp,
      });
    });
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_lobby/bob/0`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Contento", tipo: "emote",
        emoteId: "griego_contento", ts: serverTimestamp,
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/1`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Contento", tipo: "emote",
        emoteId: "griego_contento", ts: serverTimestamp,
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/bob`).set(member("Bob"))
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/carol`).set(
        member("Carol", { traitor: true })
      )
    );

    // Silenciar exige votos reales de identidades admitidas y mayoría de vivos.
    await assertSucceeds(
      alice.ref(`salas/${roomId}/propuesta_silencio`).set({
        objetivoUid: "bob",
        objetivoNombre: "Bob",
        proponenteUid: "alice",
        proponenteNombre: "Alice",
        ts: serverTimestamp,
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/votos_silencio/bob/alice`).set(Date.now())
    );
    await assertSucceeds(
      dana.ref(`salas/${roomId}/votos_silencio/bob/dana`).set(Date.now())
    );
    await assertFails(
      bob.ref(`salas/${roomId}/silenciados/bob`).set({ ts: serverTimestamp, votos: 3 })
    );
    await assertFails(
      outsider.ref(`salas/${roomId}/votos_silencio/bob/outsider`).set(Date.now())
    );
    await assertSucceeds(
      carol.ref(`salas/${roomId}/votos_silencio/bob/carol`).set(Date.now())
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/silenciados/bob`).set({ ts: serverTimestamp, votos: 3 })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat/message-muted-text`).set(chatMessage("bob", "Bob"))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat/message-muted-quick`).set(
        chatMessage("bob", "Bob", { tipo: "rapida" })
      )
    );

    // Los timestamps enviados por un cliente modificado no pueden fabricar
    // ventanas de cooldown pasadas para emitir una rafaga de emotes/chat.
    await assertFails(dana.ref(`salas/${roomId}/emotes/dana`).set({
      ...emoteEvent("dana", "Dana"), ts: Date.now() - 119_000,
    }));
    await assertFails(dana.ref(`salas/${roomId}/emotes/dana`).set({
      ...emoteEvent("dana", "Dana"), ts: Date.now() + 60_000,
    }));
    await assertSucceeds(alice.ref(`salas/${roomId}/miembros/dana`).set(member("Dana", { lobby: true })));
    const lobbyMessage = {
      actorId: "dana", speaker: "Dana", mensaje: "Hola", tipo: "texto", ts: serverTimestamp,
    };
    await assertFails(dana.ref(`salas/${roomId}/chat_lobby/dana/0`).set({
      ...lobbyMessage, ts: Date.now() - 119_000,
    }));
    // Las reglas de cada slot ven root anterior: validar solo cada hoja admitía
    // ambos envíos en un único update. La validación del padre cierra ese bypass.
    await assertFails(dana.ref(`salas/${roomId}/chat_lobby/dana`).update({
      0: lobbyMessage, 1: lobbyMessage,
    }));
    const simultaneousChat = await Promise.allSettled([0, 1].map((slot) =>
      dana.ref(`salas/${roomId}/chat_lobby/dana/${slot}`).set(lobbyMessage)
    ));
    assert.equal(simultaneousChat.filter((result) => result.status === "fulfilled").length, 1);
    assert.equal(simultaneousChat.filter((result) => result.status === "rejected").length, 1);
    await assertSucceeds(alice.ref(`salas/${roomId}/miembros/dana`).set(member("Dana")));

    // Cierre del plazo de silencio: aun falsificando el timestamp del voto, el
    // reloj del servidor manda. El objetivo tampoco puede votar su propio silencio.
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref(`salas/${roomId}/propuesta_silencio`).set({
        objetivoUid: "dana", objetivoNombre: "Dana", proponenteUid: "alice",
        proponenteNombre: "Alice", ts: Date.now() - 31_000,
      });
    });
    await assertFails(bob.ref(`salas/${roomId}/votos_silencio/dana/bob`).set(Date.now() - 5_000));
    await assertFails(dana.ref(`salas/${roomId}/votos_silencio/dana/dana`).set(Date.now()));

    // Inmutabilidad y validación de payloads, incluso al escribir subcampos.
    await assertFails(carol.ref(`salas/${roomId}/chat_traidores/message-plan/mensaje`).set("Editado"));
    await assertFails(carol.ref(`salas/${roomId}/chat_traidores/message-plan`).remove());
    for (const extra of [
      { actorId: "alice" }, { speaker: "Alice" }, { mensaje: "x".repeat(141) },
      { isGod: true }, { matchId: "match-anterior" }, { campoPrivado: "invalido" },
    ]) {
      await assertFails(dana.ref(`salas/${roomId}/chat/invalid-${Object.keys(extra)[0]}`).set(
        chatMessage("dana", "Dana", extra)
      ));
    }
    await assertFails(bob.ref(`salas/${roomId}/control/hostUid`).set("bob"));
    await assertFails(outsider.ref(`salas/${roomId}/control/hostUid`).set("outsider"));

    // La expulsión revoca futuras operaciones aunque el SDK conserve datos locales.
    await assertSucceeds(alice.ref(`salas/${roomId}/miembros/dana`).remove());
    await assertFails(dana.ref(`salas/${roomId}/chat`).once("value"));
    await assertFails(dana.ref(`salas/${roomId}/sincronizacion`).once("value"));
    await assertFails(dana.ref(`salas/${roomId}/presencia/dana`).set({ estado: "conectado", ts: serverTimestamp }));
    await assertFails(dana.ref(`salas/${roomId}/sincronizacion/clientes/dana`).set(clientSyncState("dana")));
    await assertFails(dana.ref(`salas/${roomId}/control/hostUid`).set("dana"));

    // Dos candidatos admitidos que observan al host desconectado compiten contra
    // el estado vigente: solo uno puede asumir, el otro ya ve al nuevo host conectado.
    const migrationRoom = "migration-race";
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref(`salas/${migrationRoom}`).set({
        control: { hostUid: "alice", creatorUid: "alice", matchId },
        miembros: { alice: member("Alice"), bob: member("Bob"), carol: member("Carol") },
        presencia: {
          alice: { estado: "desconectado", ts: serverTimestamp },
          bob: { estado: "conectado", ts: serverTimestamp },
          carol: { estado: "conectado", ts: serverTimestamp },
        },
      });
    });
    const candidates = await Promise.allSettled([
      bob.ref(`salas/${migrationRoom}/control/hostUid`).set("bob"),
      carol.ref(`salas/${migrationRoom}/control/hostUid`).set("carol"),
    ]);
    assert.equal(candidates.filter((result) => result.status === "fulfilled").length, 1);
    assert.equal(candidates.filter((result) => result.status === "rejected").length, 1);
    await assertFails(alice.ref(`salas/${migrationRoom}/miembros/bob`).remove());

    // El backend congela la sala antes de limpiar. Ni el host ni un participante
    // pueden quitar el tombstone, reactivar permisos o resucitar el espejo.
    const deletingRoom = "room-backend-deleting";
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref(`salas/${deletingRoom}`).set({
        control: { hostUid: "alice", creatorUid: "alice", matchId, cleanupState: "deleting" },
        miembros: { alice: member("Alice"), bob: member("Bob") },
      });
    });
    for (const [path, value] of [
      ["control/hostUid", "alice"], ["control/cleanupState", null], ["miembros/bob", member("Bob")],
      ["presencia/alice", { estado: "conectado", ts: serverTimestamp }],
      ["sincronizacion/clientes/alice", clientSyncState("alice")],
      ["estado_partida", authoritativeState], ["chat/new-message", chatMessage("alice", "Alice")],
    ]) {
      await assertFails(alice.ref(`salas/${deletingRoom}/${path}`).set(value));
    }
    await assertFails(alice.ref(`salas/${deletingRoom}`).remove());
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref(`salas/${deletingRoom}`).set({
        control: { cleanupState: "deleting", cleanupToken: "test-token", cleanupDeletedAt: serverTimestamp },
      });
    });
    await assertFails(alice.ref(`salas/${deletingRoom}/control/hostUid`).set("alice"));
    await assertFails(outsider.ref(`salas/${deletingRoom}/control/hostUid`).set("outsider"));

    // Solo el anfitrión registrado puede limpiar canales o cerrar su propia sala.
    await assertFails(bob.ref(`salas/${roomId}/chat`).remove());
    await assertSucceeds(alice.ref(`salas/${roomId}/chat`).remove());
    await assertFails(bob.ref(`salas/${roomId}/sincronizacion`).remove());
    // Al crear una revancha, el host cambia el matchId y purga los ACK efimeros en una
    // unica escritura. Esto evita borrar confirmaciones que ya pertenezcan al match nuevo.
    await assertSucceeds(alice.ref(`salas/${roomId}`).update({
      "control/matchId": "match-456",
      "control/actualizadaEn": Date.now(),
      sincronizacion: null,
    }));
    await assertFails(guest.ref(`salas/${roomId}`).remove());

    // Si el host activo cambió, el creador original solo puede retirar la sala cuando el
    // timestamp del servidor lleva al menos 24 horas vencido.
    const staleRoomId = "stale-room";
    await assertSucceeds(alice.ref(`salas/${staleRoomId}/control/hostUid`).set("alice"));
    await assertSucceeds(alice.ref(`salas/${staleRoomId}/control/creatorUid`).set("alice"));
    await assertSucceeds(alice.ref(`salas/${staleRoomId}`).update({
      "control/actualizadaEn": Date.now() - (25 * 60 * 60 * 1000),
      "control/matchId": matchId,
      "control/jugadoresVivos": 2,
      "miembros/alice": member("Alice"),
      "miembros/bob": member("Bob"),
    }));
    await assertSucceeds(alice.ref(`salas/${staleRoomId}/control/hostUid`).set("bob"));
    await assertFails(outsider.ref(`salas/${staleRoomId}`).remove());
    await assertFails(alice.ref(`salas/${staleRoomId}`).remove()); // only backend may clean a migrated stale room

    await assertSucceeds(alice.ref(`salas/${roomId}`).remove());

    console.log("Realtime Database rules: OK");
  } finally {
    await testEnv.cleanup();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
