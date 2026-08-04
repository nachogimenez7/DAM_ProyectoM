const fs = require("fs");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const projectId = "traidores-local";
const roomId = "room_rules_test";

const chatMessage = (actorId) => ({
  matchId: "match-1",
  actorId,
  speaker: "Jugador",
  mensaje: "Mensaje de prueba",
  fase: "DISCUSION",
  ronda: 1,
  isGod: false,
  tipo: "texto",
  ts: Date.now(),
});

const emoteEvent = (actorId) => ({
  matchId: "match-1",
  actorId,
  player: "Jugador",
  emoteId: "griego_enojado",
  ts: Date.now(),
});

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId,
    database: {
      rules: fs.readFileSync("database.rules.json", "utf8"),
    },
  });

  try {
    const alice = testEnv.authenticatedContext("alice").database();
    const bob = testEnv.authenticatedContext("bob").database();
    const carol = testEnv.authenticatedContext("carol").database();
    const guest = testEnv.unauthenticatedContext().database();

    // Los canales se enganchan recién después de publicar la presencia propia. Estar
    // autenticado no alcanza para leer el chat de una sala ajena.
    await assertFails(bob.ref(`salas/${roomId}/chat`).once("value"));
    await assertSucceeds(
      alice.ref(`salas/${roomId}/presencia/alice`).set({
        estado: "conectado",
        ts: Date.now(),
      })
    );
    // El buscador cuenta presencias conectadas para no confiar en un jugadoresActuales viejo.
    await assertSucceeds(
      bob.ref(`salas/${roomId}/presencia`).once("value")
    );

    await assertSucceeds(
      alice.ref(`salas/${roomId}/chat/message-a`).set(chatMessage("alice"))
    );
    await assertFails(
      alice.ref(`salas/${roomId}/chat/message-bad-actor`).set(chatMessage("bob"))
    );
    await assertFails(
      guest.ref(`salas/${roomId}/chat/message-guest`).set(chatMessage("guest"))
    );
    await assertSucceeds(alice.ref(`salas/${roomId}/chat`).once("value"));
    await assertFails(guest.ref(`salas/${roomId}/chat`).once("value"));

    await assertSucceeds(
      alice.ref(`salas/${roomId}/emotes/emote-a`).set(emoteEvent("alice"))
    );
    await assertFails(
      alice.ref(`salas/${roomId}/emotes/emote-bad-actor`).set(emoteEvent("bob"))
    );
    await assertFails(
      guest.ref(`salas/${roomId}/emotes/emote-guest`).set(emoteEvent("guest"))
    );
    await assertSucceeds(alice.ref(`salas/${roomId}/emotes`).once("value"));
    await assertFails(guest.ref(`salas/${roomId}/emotes`).once("value"));

    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_lobby/message-b`).set({
        actorId: "bob",
        speaker: "Bob",
        mensaje: "Hola",
        tipo: "texto",
        ts: Date.now(),
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_lobby/message-invalid-emote`).set({
        actorId: "bob",
        speaker: "Bob",
        mensaje: "Emote",
        tipo: "emote",
        ts: Date.now(),
      })
    );

    await assertFails(
      bob.ref(`salas/${roomId}/presencia/alice`).set({
        estado: "desconectado",
        ts: Date.now(),
      })
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/presencia/bob`).set({
        estado: "conectado",
        ts: Date.now(),
      })
    );
    await assertSucceeds(bob.ref(`salas/${roomId}/chat_lobby`).once("value"));
    await assertSucceeds(bob.ref(`salas/${roomId}/chat_espectadores`).once("value"));

    await assertSucceeds(
      carol.ref(`salas/${roomId}/presencia/carol`).set({
        estado: "conectado",
        ts: Date.now(),
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/propuesta_silencio`).set({
        objetivoUid: "bob",
        objetivoNombre: "Bob",
        proponenteUid: "alice",
        proponenteNombre: "Alice",
        ts: Date.now(),
      })
    );
    for (const voter of [["alice", alice], ["carol", carol]]) {
      await assertSucceeds(
        voter[1].ref(`salas/${roomId}/votos_silencio/bob/${voter[0]}`).set(Date.now())
      );
    }
    await assertFails(
      bob.ref(`salas/${roomId}/votos_silencio/alice/bob`).set(Date.now())
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}/silenciados/bob`).set({
        ts: Date.now(),
        votos: 3,
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat/message-muted-text`).set(chatMessage("bob"))
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat/message-muted-quick`).set({
        ...chatMessage("bob"),
        tipo: "rapida",
      })
    );

    // Rematch cleanup: authenticated clients can delete chat nodes but cannot
    // overwrite or impersonate message authors.
    await assertSucceeds(
      alice.ref(`salas/${roomId}/chat_traidores/message-plan`).set({
        ...chatMessage("alice"),
        canal: "traidores",
      })
    );
    await assertSucceeds(
      bob.ref(`salas/${roomId}/chat_espectadores/message-spectator`).set({
        ...chatMessage("bob"),
        canal: "espectadores",
        tipo: "rapida",
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/chat_espectadores/message-wrong-channel`).set({
        ...chatMessage("bob"),
        canal: "traidores",
      })
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId}`).update({
        chat: null,
        chat_lobby: null,
        chat_traidores: null,
        chat_espectadores: null,
        emotes: null,
      })
    );

    // Cierre completo de sala vacia: RTDB no puede validar el estado/host de Firestore.
    // El cliente autenticado debe haber publicado su propia presencia para borrar el nodo
    // entero; aun asi no puede sobrescribirlo con contenido arbitrario y un invitado sin
    // sesion no puede borrar nada.
    const roomId2 = "room_rules_teardown";
    await assertSucceeds(
      alice.ref(`salas/${roomId2}/chat/message-a`).set(chatMessage("alice"))
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId2}/emotes/emote-a`).set(emoteEvent("alice"))
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId2}/presencia/alice`).set({ estado: "conectado", ts: Date.now() })
    );
    await assertFails(guest.ref(`salas/${roomId2}`).remove());
    await assertFails(
      bob.ref(`salas/${roomId2}`).set({ chat: { intruso: chatMessage("bob") } })
    );
    await assertFails(bob.ref(`salas/${roomId2}`).remove());
    await assertSucceeds(
      bob.ref(`salas/${roomId2}/presencia/bob`).set({ estado: "conectado", ts: Date.now() })
    );
    await assertSucceeds(bob.ref(`salas/${roomId2}`).remove());

    console.log("Realtime Database rules: OK");
  } finally {
    await testEnv.cleanup();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
