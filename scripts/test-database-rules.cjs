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
    const guest = testEnv.unauthenticatedContext().database();

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

    await assertSucceeds(
      alice.ref(`salas/${roomId}/presencia/alice`).set({
        estado: "conectado",
        ts: Date.now(),
      })
    );
    await assertFails(
      bob.ref(`salas/${roomId}/presencia/alice`).set({
        estado: "desconectado",
        ts: Date.now(),
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
      })
    );

    // Cierre completo de sala vacia: RTDB no puede validar el estado/host de Firestore,
    // asi que cualquier autenticado puede borrar el nodo entero de una sala (chat +
    // chat_lobby + chat_traidores + chat_espectadores + presencia de una), pero NO
    // sobrescribirlo con contenido arbitrario, y un invitado sin sesion no puede borrar nada.
    const roomId2 = "room_rules_teardown";
    await assertSucceeds(
      alice.ref(`salas/${roomId2}/chat/message-a`).set(chatMessage("alice"))
    );
    await assertSucceeds(
      alice.ref(`salas/${roomId2}/presencia/alice`).set({ estado: "conectado", ts: Date.now() })
    );
    await assertFails(guest.ref(`salas/${roomId2}`).remove());
    await assertFails(
      bob.ref(`salas/${roomId2}`).set({ chat: { intruso: chatMessage("bob") } })
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
