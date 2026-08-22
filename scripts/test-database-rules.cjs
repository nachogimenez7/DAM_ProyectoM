const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const projectId = "traidores-local";
const roomId = "room_rules_test";
const matchId = "match-123";

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
  ts: Date.now(),
  ...extra,
});

const emoteEvent = (actorId, player) => ({
  matchId,
  actorId,
  player,
  emoteId: "griego_enojado",
  ts: Date.now(),
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
        ts: Date.now(),
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
          ts: Date.now(),
        })
      );
    }
    await assertFails(
      bob.ref(`salas/${roomId}/presencia/alice`).set({ estado: "desconectado", ts: Date.now() })
    );
    await assertFails(outsider.ref(`salas/${roomId}/presencia`).once("value"));
    await assertSucceeds(bob.ref(`salas/${roomId}/presencia`).once("value"));

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
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: Date.now(),
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/bob`).set(member("Bob", { lobby: true }))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_lobby/bob/0`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: Date.now(),
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/1`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Spam", tipo: "texto", ts: Date.now(),
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/2`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Slot invalido", tipo: "texto", ts: Date.now(),
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
        emoteId: "griego_contento", ts: Date.now(),
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/bob/1`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Contento", tipo: "emote",
        emoteId: "griego_contento", ts: Date.now(),
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
        ts: Date.now(),
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/votos_silencio/bob/alice`).set(Date.now())
    );
    await assertSucceeds(
      dana.ref(`salas/${roomId}/votos_silencio/bob/dana`).set(Date.now())
    );
    await assertFails(
      bob.ref(`salas/${roomId}/silenciados/bob`).set({ ts: Date.now(), votos: 3 })
    );
    await assertFails(
      outsider.ref(`salas/${roomId}/votos_silencio/bob/outsider`).set(Date.now())
    );
    await assertSucceeds(
      carol.ref(`salas/${roomId}/votos_silencio/bob/carol`).set(Date.now())
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/silenciados/bob`).set({ ts: Date.now(), votos: 3 })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat/message-muted-text`).set(chatMessage("bob", "Bob"))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat/message-muted-quick`).set(
        chatMessage("bob", "Bob", { tipo: "rapida" })
      )
    );

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
    await assertSucceeds(alice.ref(`salas/${staleRoomId}`).remove());

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
