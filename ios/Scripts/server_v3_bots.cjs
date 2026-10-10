// Emulator-only bot players for a server-authority room. They join by code like Android,
// mark ready, then read their own projection (public/private/permissions under the RTDB
// rules) and send legal intentions through accionPartidaV3. Usage: node bots.cjs <CODE> <count>
const path = require("node:path");
const R = "/Users/ignaciogimenez/.codex/.chatgpt-projects/g-p-6aade7b2232481919f33598df6616dcb/DAM_ProyectoM";
const req = (m) => require(path.join(R, "node_modules", m));
const {initializeApp} = req("firebase/app");
const {getAuth, connectAuthEmulator, signInAnonymously} = req("firebase/auth");
const {getFirestore, connectFirestoreEmulator, doc, getDoc, runTransaction, updateDoc, serverTimestamp, increment} = req("firebase/firestore");
const {getDatabase, connectDatabaseEmulator, ref, onValue} = req("firebase/database");
const {getFunctions, connectFunctionsEmulator, httpsCallable} = req("firebase/functions");
const {initializeAppCheck, CustomProvider} = req("firebase/app-check");
const {randomUUID} = require("node:crypto");

const [code, count = "4"] = process.argv.slice(2);
const names = ["Forastero 1000", "Mufa 1001", "Perejil 1002", "Careta 1003", "Chamuyero 1004", "Rezongón 1005"];
const pick = (list) => list[Math.floor(Math.random() * list.length)];
const TRAITORS = new Set(["asesino", "espia", "mercenario"]);

async function bot(index) {
  const app = initializeApp({projectId: "traidores", apiKey: "fake-key",
    databaseURL: "http://127.0.0.1:29000?ns=traidores-default-rtdb"}, `bot${index}`);
  // Same local token the iOS app sends to the emulators (never valid against the real project).
  initializeAppCheck(app, {provider: new CustomProvider({getToken: async () => ({token: "emulator-only",
    expireTimeMillis: Date.now() + 3600000})}), isTokenAutoRefreshEnabled: false});
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:29099", {disableWarnings: true});
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 28081);
  const rtdb = getDatabase(app);
  connectDatabaseEmulator(rtdb, "127.0.0.1", 29000);
  const functions = getFunctions(app, "southamerica-west1");
  connectFunctionsEmulator(functions, "127.0.0.1", 25001);
  const act = httpsCallable(functions, "accionPartidaV3");
  const {user} = await signInAnonymously(auth);
  const roomId = (await getDoc(doc(db, "codigosSala", code))).data().partidaId;
  const roomRef = doc(db, "partidas", roomId);
  const playerRef = doc(db, "partidas", roomId, "jugadores", user.uid);
  const name = names[index % names.length];
  await runTransaction(db, async (tx) => {
    const room = await tx.get(roomRef);
    tx.set(playerRef, {nombre: name, nombrePerfil: name, nombreSala: name, bioPerfil: "", avatarPerfil: "aldeano",
      bannerPerfil: "pampa", rolFavoritoPerfil: "aldeano", esHost: false, estado: "conectado",
      orden: room.data().jugadoresActuales, activoEnPartida: true, uidTemporal: user.uid, listo: false,
      protocolVersion: 3, puedeArbitrar: false, ultimaConexionLocal: Date.now(), ultimaConexion: serverTimestamp(),
      unidoEn: serverTimestamp()});
    tx.update(roomRef, {jugadoresActuales: increment(1), actualizadaEn: serverTimestamp()});
  });
  await updateDoc(playerRef, {listo: true});
  console.log(`[${name}] joined+ready`);

  let pub = null, own = null, acting = "";
  const decide = async () => {
    if (!pub || !own || pub.matchId !== own.matchId || pub.phaseIndex !== own.phaseIndex || pub.ganador) return;
    const key = `${pub.phaseIndex}`;
    if (acting === key) return;
    const players = Object.values(pub.jugadores || {});
    const me = players.find((p) => p.uidTemporal === user.uid);
    const myRole = Object.values(own.rolesVisibles || {}).find((r) => r.orden === me.orden)?.rolKey;
    const visible = new Map(Object.values(own.rolesVisibles || {}).map((r) => [r.orden, r.rolKey]));
    const living = players.filter((p) => p.vivo);
    const others = living.filter((p) => p.uidTemporal !== user.uid);
    let action = null, targetUid;
    if (pub.fase === "REPARTO") {
      action = myRole === "desertor" ? "desertor_initial" : "role_ack";
    } else if (pub.fase === "NOCHE" && me.vivo) {
      if (myRole === "asesino" || myRole === "espia") {
        action = "matar"; targetUid = pick(others.filter((p) => !TRAITORS.has(visible.get(p.orden) || "")))?.uidTemporal;
      } else if (myRole === "mercenario") {
        action = "silenciar"; targetUid = pick(others.filter((p) => !(own.objetivosBloqueados || []).includes(p.uidTemporal)))?.uidTemporal;
      } else if (myRole === "policia") { action = "investigar"; targetUid = pick(others)?.uidTemporal; }
      else if (myRole === "medico") { action = "salvar"; targetUid = pick(living)?.uidTemporal; }
    } else if ((pub.fase === "VOTACION" || pub.fase === "DESEMPATE_VOTACION") && me.vivo && !me.muteado) {
      const pool = pub.fase === "DESEMPATE_VOTACION" ? others.filter((p) => (pub.empateVoto || []).includes(p.uidTemporal)) : others;
      action = "votar"; targetUid = pick(pool)?.uidTemporal;
    }
    if (!action || (action !== "role_ack" && action !== "desertor_initial" && !targetUid)) return;
    acting = key;
    const payload = {roomId, matchId: pub.matchId, phaseIndex: pub.phaseIndex, requestId: randomUUID(), action,
      ...(targetUid ? {targetUid} : {}), ...(action === "desertor_initial" ? {team: "Pueblo"} : {})};
    // A short human-like delay; the server deadline is what matters.
    setTimeout(() => act(payload).then(() => console.log(`[${name}] ${pub.fase} ${action}`))
      .catch((e) => console.log(`[${name}] ${pub.fase} ${action} rejected: ${e.details?.reason || e.message}`)), 1500 + Math.random() * 2000);
  };
  // Reading is denied until the server publishes this player's membership: retry.
  const watch = (pathName, receive) => onValue(ref(rtdb, pathName), receive, () => setTimeout(() => watch(pathName, receive), 3000));
  watch(`onlineV3/${roomId}/snapshot/public`, (s) => { pub = s.val(); if (pub?.ganador) console.log(`[${name}] winner ${pub.ganador}`); decide(); });
  watch(`onlineV3/${roomId}/snapshot/private/${user.uid}`, (s) => { own = s.val(); decide(); });
}

(async () => {
  for (let i = 0; i < Number(count); i++) await bot(i);
})().catch((e) => { console.error("FAILED", e.code || "", e.message); process.exit(1); });
