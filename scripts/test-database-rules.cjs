const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const projectId = "traidores-local";
const roomId = "room_rules_test";
const matchId = "match-123";

const member = (nombre, { lobby = false, alive = true, traitor = false } = {}) => ({
  nombre,
  activo: true,
  enLobby: lobby,
  vivo: alive,
  traidor: traitor,
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
    await assertSucceeds(outsider.ref(`salas/${roomId}/presencia`).once("value"));

    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat/message-bob`).set(chatMessage("bob", "Bob"))
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat/message-spoof`).set(chatMessage("bob", "Alice"))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/emotes/emote-bob`).set(emoteEvent("bob", "Bob"))
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

    // Lobby y gameplay son permisos distintos.
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/message-not-lobby`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: Date.now(),
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/miembros/bob`).set(member("Bob", { lobby: true }))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_lobby/message-lobby`).set({
        actorId: "bob", speaker: "Bob", mensaje: "Hola", tipo: "texto", ts: Date.now(),
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
    await assertFails(guest.ref(`salas/${roomId}`).remove());
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
