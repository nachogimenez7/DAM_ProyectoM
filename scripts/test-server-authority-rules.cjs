"use strict";
const fs = require("node:fs");
const {initializeTestEnvironment, assertFails, assertSucceeds} = require("@firebase/rules-unit-testing");
const {doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs} = require("firebase/firestore");
(async () => {
  const env = await initializeTestEnvironment({projectId: "traidores-local",
    firestore: {rules: fs.readFileSync("firestore.rules", "utf8")}, database: {rules: fs.readFileSync("database.rules.json", "utf8")}});
  let checks = 0;
  const pass = async (promise) => { await assertSucceeds(promise); checks++; };
  const deny = async (promise) => { await assertFails(promise); checks++; };
  const room = "v3-security", path = `onlineV3/${room}`;
  try {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `partidas/${room}`), {estado: "en_juego", hostId: "host", hostActivoId: "host",
        hostVersion: 1, authorityMode: "server", protocolVersion: 3});
      await setDoc(doc(db, `partidas/${room}/jugadores/host`), {activoEnPartida: true, nombre: "Anfitrion",
        estado: "conectado", uidTemporal: "host", protocolVersion: 3, puedeArbitrar: false, listo: false});
      await setDoc(doc(db, `partidas/${room}/servidor/current`), {secretRoles: ["host=alcalde", "bob=asesino"]});
      await setDoc(doc(db, `partidas/${room}/serverOutbox/current`), {privateRoles: true});
      await ctx.database().ref(`${path}/snapshot`).set({public: {fase: "DIA_DEBATE", limiteFaseEpochMs: Date.now() + 120000}, private: {
        host: {rolKey: "alcalde"}, bob: {rolKey: "asesino"}, guest: {rolKey: "aldeano"}}, permissions: {
        host: {member: true, matchId: "match", phaseIndex: 5, alive: true, publicChat: true, traitor: false, deadChat: false},
        bob: {member: true, matchId: "match", phaseIndex: 5, alive: true, publicChat: true, traitor: true, traitorChat: true},
        guest: {member: true, matchId: "match", phaseIndex: 5, alive: false, publicChat: true, deadChat: true}}});
    });
    const host = env.authenticatedContext("host"), bob = env.authenticatedContext("bob"), dead = env.authenticatedContext("guest");
    const outsider = env.authenticatedContext("outsider"), anonymous = env.unauthenticatedContext();
    await pass(getDoc(doc(host.firestore(), `partidas/${room}`)));
    for (const sub of ["servidor/current", "serverOutbox/current"]) {
      await deny(getDoc(doc(host.firestore(), `partidas/${room}/${sub}`)));
      await deny(setDoc(doc(host.firestore(), `partidas/${room}/${sub}`), {winner: "Traidores"}));
      await deny(deleteDoc(doc(host.firestore(), `partidas/${room}/${sub}`)));
    }
    await deny(getDocs(collection(host.firestore(), `partidas/${room}/servidor`)));
    await deny(setDoc(doc(host.firestore(), "onlineMaintenance/serverAuthority"), {enabled: true}));
    await deny(getDoc(doc(host.firestore(), "onlineMaintenance/serverAuthority")));
    await deny(updateDoc(doc(host.firestore(), `partidas/${room}`), {hostVersion: 2}));
    await deny(updateDoc(doc(host.firestore(), `partidas/${room}`), {authorityMode: "client"}));
    await deny(deleteDoc(doc(host.firestore(), `partidas/${room}`)));
    await deny(setDoc(doc(host.firestore(), `partidas/${room}/jugadores/host`), {activoEnPartida: false}));
    await deny(updateDoc(doc(host.firestore(), `partidas/${room}/jugadores/host`), {listo: true}));
    for (const who of [host, bob, dead]) await pass(who.database().ref(`${path}/snapshot/public`).once("value"));
    await deny(outsider.database().ref(`${path}/snapshot/public`).once("value"));
    await deny(anonymous.database().ref(`${path}/snapshot/public`).once("value"));
    await pass(host.database().ref(`${path}/snapshot/private/host`).once("value"));
    await deny(host.database().ref(`${path}/snapshot/private/bob`).once("value"));
    await deny(host.database().ref(path).once("value"));
    await deny(host.database().ref(`${path}/snapshot`).once("value"));
    await deny(host.database().ref(`${path}/snapshot/private`).once("value"));
    await deny(host.database().ref(`${path}/snapshot/permissions/bob`).once("value"));
    await deny(host.database().ref(`${path}/snapshot/public`).update({ganador: "Pueblo"}));
    await deny(host.database().ref(`${path}/snapshot/permissions/host`).update({traitor: true}));
    await deny(host.database().ref(path).remove());
    const presence = {estado: "conectado", actualizadaEn: {".sv": "timestamp"}};
    await pass(host.database().ref(`${path}/presence/host`).set(presence));
    await deny(host.database().ref(`${path}/presence/bob`).set(presence));
    await pass(bob.database().ref(`${path}/presence`).once("value"));
    await deny(host.database().ref(`${path}/presence/host`).set({...presence, actualizadaEn: 1}));
    const message = {actorUid: "host", matchId: "match", phaseIndex: 5, slot: 0, text: "Hola", ts: {".sv": "timestamp"}};
    const send = (who, channel, value = message, slot = value.slot) => {
      const uid = value.actorUid, messageId = `${uid}_${Number.isInteger(slot) ? slot : 0}`;
      return who.database().ref(path).update({[`chat/${channel}/${messageId}`]: {...value, slot},
        [`chatRate/${uid}`]: {messageId, channel, slot, ts: {".sv": "timestamp"}}});
    };
    const resetRate = (uid) => env.withSecurityRulesDisabled((ctx) => ctx.database().ref(`${path}/chatRate/${uid}/ts`).set(Date.now() - 3000));
    await deny(host.database().ref(`${path}/chat/publico/host_0`).set(message)); // Missing atomic rate update.
    await pass(send(host, "publico"));
    await deny(send(host, "publico", {...message, text: "demasiado rápido"}, 1));
    await pass(host.database().ref(`${path}/chatRate/host`).once("value"));
    await deny(bob.database().ref(`${path}/chatRate/host`).once("value"));
    await resetRate("host");
    await deny(send(host, "publico", {...message, actorUid: "bob"}));
    await deny(send(host, "publico", {...message, matchId: "previous"}));
    await deny(send(host, "publico", {...message, phaseIndex: 4}));
    await deny(send(host, "publico", {...message, text: "a".repeat(301)}));
    await deny(send(host, "publico", {...message, isGod: true}));
    await deny(send(host, "publico", message, 16));
    await deny(send(host, "publico", message, 0.5));
    await deny(host.database().ref(`${path}/chatRate/host`).set({messageId: "host_1", channel: "publico", slot: 1, ts: {".sv": "timestamp"}}));
    await deny(host.database().ref(path).update({
      "chat/publico/host_1": {...message, slot: 1}, "chat/publico/host_2": {...message, slot: 2},
      "chatRate/host": {messageId: "host_1", channel: "publico", slot: 1, ts: {".sv": "timestamp"}}}));
    await pass(send(host, "publico", {...message, text: "siguiente"}, 1));
    await resetRate("host");
    await pass(send(host, "publico", {...message, text: "nuevo mensaje en el anillo"}, 0));
    await resetRate("host");
    await deny(send(host, "traidores"));
    await deny(host.database().ref(`${path}/chat/traidores`).once("value"));
    await pass(send(bob, "traidores", {...message, actorUid: "bob"}));
    await deny(send(bob, "publico", {...message, actorUid: "bob"}, 1)); // One cooldown across channels.
    await pass(send(dead, "muertos", {...message, actorUid: "guest"}));
    await deny(host.database().ref(`${path}/chat/muertos`).once("value"));
    // Silence revokes chat server-side even if a modified client keeps its button enabled.
    await env.withSecurityRulesDisabled((ctx) => ctx.database().ref(`${path}/snapshot/permissions/host`).update({publicChat: false}));
    await deny(send(host, "publico"));
    // Cleanup locks freeze the namespace before deleting private membership.
    await env.withSecurityRulesDisabled((ctx) => ctx.database().ref(`${path}/snapshot`).update({cleanupState: "deleting"}));
    await deny(host.database().ref(`${path}/presence/host`).set(presence));
    await resetRate("bob");
    await deny(send(bob, "traidores", {...message, actorUid: "bob"}));
    await resetRate("guest");
    await deny(send(dead, "muertos", {...message, actorUid: "guest"}));
    // Once the server has revoked the previous match, the rematch lobby can accept ready updates again.
    await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), `partidas/${room}`), {
      authorityMode: "lobby", estado: "esperando"}));
    await pass(updateDoc(doc(host.firestore(), `partidas/${room}/jugadores/host`), {listo: true}));
    await deny(updateDoc(doc(host.firestore(), `partidas/${room}/jugadores/host`), {puedeArbitrar: true}));
    await deny(updateDoc(doc(host.firestore(), `partidas/${room}/jugadores/host`), {protocolVersion: 2}));
    console.log(`Server authority rules: ${checks} checks passed.`);
  } finally {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(path).remove();
      for (const p of ["servidor/current", "serverOutbox/current", "jugadores/host", ""]) {
        await deleteDoc(doc(ctx.firestore(), `partidas/${room}${p ? `/${p}` : ""}`));
      }
    });
    await env.cleanup();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
