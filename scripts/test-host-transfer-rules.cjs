// Host transfer with a real-size room (after a finished match). Small seeded rooms passed while
// real rooms hit Firestore's 1000-expression limit and denied «Pasar anfitrión» (10/10/2026).
const fs = require("fs");
const {initializeTestEnvironment, assertSucceeds, assertFails} = require("@firebase/rules-unit-testing");
const {doc, setDoc, runTransaction, serverTimestamp, increment, Timestamp} = require("firebase/firestore");

const HOST = "host_uid", NEXT = "next_uid", THIRD = "third_uid", R = "room_real_size";
const now = () => Timestamp.now();
const room = {
  origen: "android-online-create", partidaInicialCreada: false, maxJugadores: 3, jugadoresActuales: 3,
  hostId: HOST, hostNombre: "Anfitrion", hostActivoId: HOST, hostVersion: 7, nombre: "Sala QA",
  creadaEn: now(), actualizadaEn: now(), ultimaActividadOnline: now(), entradaLiberadaEn: now(),
  ultimoResultado: {mapa: "pampa", ganador: "Pueblo", ronda: 1, finalizadaEnLocal: Date.now(), matchId: "eb215834-ebf6-4bf5-aa30-fb5e2ff636a3"}, limpiezaPendiente: false, codigoSala: "ABCDEF",
  visibilidad: "privada", configLobby: {transicionSeg: 4, votosIndividuales: true, revelarRolesAlMorir: false, discusionSeg: 120,
    nocheSeg: 40, presetRoles: "RECOMMENDED", votacionSeg: 20, roles: "0,0,0,1,0,0,0,0,0,0,0"}, mapaNombre: "Pampa", modoPrueba: true,
  estado: "esperando", jugadoresEsperados: 3, mapa: "pampa",
};
const player = (uid, name, order, extra = {}) => ({
  uidTemporal: uid, nombre: name, nombreSala: name, nombrePerfil: name, estado: "conectado", esHost: uid === HOST,
  orden: order, listo: false, activoEnPartida: true, listoParaVotar: false, listoParaVotarRonda: 0,
  listoParaVotarPhaseIndex: 0, ultimaConexion: now(), ultimaConexionLocal: Date.now(), unidoEn: now(),
  bannerPerfil: "pampa", rolFavoritoPerfil: "pampa_policia", temaCosmeticoPerfil: "classic",
  avatarPerfil: "avatar_buho", bioPerfil: "No fui yo.", emotesPerfil: ["gaucho_contento"], ...extra,
});

(async () => {
  const env = await initializeTestEnvironment({projectId: "demo-host-transfer", firestore: {
    rules: fs.readFileSync(process.env.RULES_FILE || "firestore.rules", "utf8"), host: "127.0.0.1",
    port: Number((process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080").split(":")[1])}});
  const seed = () => env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), "partidas", R), room);
    await setDoc(doc(c.firestore(), "partidas", R, "jugadores", HOST), player(HOST, "Anfitrion", 0, {publicId: "48"}));
    await setDoc(doc(c.firestore(), "partidas", R, "jugadores", NEXT), player(NEXT, "Nacho", 1, {publicId: "49"}));
    await setDoc(doc(c.firestore(), "partidas", R, "jugadores", THIRD), player(THIRD, "Invitado", 2));
  });
  const ctx = (uid) => env.authenticatedContext(uid, {firebase: {sign_in_provider: "google.com"}, email: `${uid}@example.test`}).firestore();
  const host = ctx(HOST), next = ctx(NEXT);
  const roomUpdate = (db, players) => ({hostId: NEXT, hostNombre: "Nacho", hostActivoId: NEXT,
    hostVersion: increment(1), jugadoresActuales: players, actualizadaEn: serverTimestamp()});

  await seed();
  await assertSucceeds(runTransaction(host, async (t) => {
    t.update(doc(host, "partidas", R), roomUpdate(host, 2));
    t.update(doc(host, "partidas", R, "jugadores", HOST), {esHost: false, activoEnPartida: false, listo: false,
      estado: "desconectado", ultimaConexion: serverTimestamp(), ultimaConexionLocal: Date.now()});
    t.update(doc(host, "partidas", R, "jugadores", NEXT), {esHost: true});
  }));
  console.log("ok - host leaves and hands a real-size room to a registered player");

  await seed();
  await assertSucceeds(runTransaction(host, async (t) => {
    t.update(doc(host, "partidas", R), roomUpdate(host, 3));
    t.update(doc(host, "partidas", R, "jugadores", HOST), {esHost: false});
    t.update(doc(host, "partidas", R, "jugadores", NEXT), {esHost: true});
  }));
  console.log("ok - manual «Pasar anfitrión» in a real-size room");

  await seed();
  await assertFails(runTransaction(next, async (t) => t.update(doc(next, "partidas", R), roomUpdate(next, 3))));
  console.log("ok - a guest cannot take the room while the creator is active");

  await seed();
  await assertFails(runTransaction(host, async (t) => t.update(doc(host, "partidas", R),
    {...roomUpdate(host, 3), hostId: THIRD, hostActivoId: THIRD, hostNombre: "Invitado"})));
  console.log("ok - the room cannot be handed to a guest without an account");
  await env.cleanup();
})().catch((error) => { console.error(error); process.exit(1); });
