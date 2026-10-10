"use strict";
// Interactive, disposable QA only. Never contacts Cloud or enters the mobile APK.
const assert = require("node:assert/strict");
const {randomUUID, randomInt} = require("node:crypto");
const {execFileSync} = require("node:child_process");
const {createRequire} = require("node:module");
const sdk = createRequire(require.resolve("../functions/package.json"));
const {initializeApp: adminApp, deleteApp: deleteAdminApp} = sdk("firebase-admin/app");
const {getFirestore: adminFirestore} = sdk("firebase-admin/firestore");
const {getDatabase: adminDatabase} = sdk("firebase-admin/database");
const {getAuth: adminAuth} = sdk("firebase-admin/auth");
const {initializeApp, deleteApp} = require("firebase/app");
const {getAuth, connectAuthEmulator, signInWithEmailAndPassword} = require("firebase/auth");
const {getDatabase, connectDatabaseEmulator, ref, onValue, goOffline} = require("firebase/database");
const {getFirestore, connectFirestoreEmulator, doc, updateDoc} = require("firebase/firestore");

// Bots use only their own private projection and the public roster, never Admin game state.
function chooseAction(publicState, own, uid) {
  if (!publicState || !own || publicState.matchId !== own.matchId || publicState.phaseIndex !== own.phaseIndex || publicState.ganador) return null;
  const players = publicState.jugadores || [], self = players.find(p => p.uidTemporal === uid);
  const role = own.rolesVisibles?.find(p => p.orden === self?.orden)?.rolKey;
  if (!self?.vivo || !role) return null;
  const peers = players.filter(p => p.vivo && p.uidTemporal !== uid);
  const pick = list => list.length ? list[randomInt(list.length)] : null;
  const target = (action, list) => { const p = pick(list); return p ? {action, targetUid: p.uidTemporal} : null; };
  switch (publicState.fase) {
    case "REPARTO": return role === "desertor" && !own.desertorBando
      ? {action: "desertor_initial", team: "Pueblo"} : {action: "role_ack"};
    case "NOCHE":
      if (["asesino", "espia"].includes(role)) {
        const allies = new Set((own.rolesVisibles || []).filter(p => ["asesino", "espia", "mercenario"].includes(p.rolKey)).map(p => p.orden));
        return target("matar", peers.filter(p => !allies.has(p.orden)));
      }
      if (role === "mercenario") return target("silenciar", peers.filter(p => !(own.objetivosBloqueados || []).includes(p.uidTemporal)));
      if (role === "policia") return target("investigar", peers);
      if (role === "medico") return {action: "salvar", targetUid: uid};
      if (role === "oraculo" && publicState.ronda > 1 && !own.oraculoUsado && players.some(p => !p.vivo && p.causaEliminacion !== "ABANDONO")) return {action: "guardar_poder"};
      return null;
    case "DIA_DEBATE": return role === "alcalde" && !self.muteado && !publicState.alcaldeRevelado ? {action: "revelar_alcalde"} : null;
    case "VOTACION": case "DESEMPATE_VOTACION": return self.muteado ? null
      : target("votar", peers.filter(p => publicState.fase !== "DESEMPATE_VOTACION" || (publicState.empateVoto || []).includes(p.uidTemporal)));
    case "ALCALDE_DESEMPATE": return role === "alcalde" && !self.muteado && publicState.alcaldeRevelado === uid
      ? target("decidir_empate", players.filter(p => p.vivo && (publicState.empateVoto || []).includes(p.uidTemporal))) : null;
    case "DESERTOR_RECONSIDERACION": return role === "desertor" ? {action: "desertor_rethink", team: "mantener"} : null;
    default: return null;
  }
}

async function main() {
  assert.equal(process.env.GCLOUD_PROJECT, "traidores-local");
  for (const [key, port] of Object.entries({FIRESTORE_EMULATOR_HOST:18081, FIREBASE_DATABASE_EMULATOR_HOST:19000, FIREBASE_AUTH_EMULATOR_HOST:19099}))
    assert.equal(process.env[key], `127.0.0.1:${port}`, "Use isolated QA emulators only");
  const adb = process.env.TRAIDORES_QA_ADB, device = process.env.TRAIDORES_QA_DEVICE;
  const packageName = process.env.TRAIDORES_QA_PACKAGE || 'com.traidores.juego';
  assert.ok(adb); assert.ok(device);
  assert.ok(['com.traidores.juego', 'com.traidores.juego.v3qa'].includes(packageName));
  assert.ok(/^emulator-\d+$/.test(device) || packageName === 'com.traidores.juego.v3qa',
    'Physical-device tests use the isolated QA app to preserve the installed game');
  const admin = adminApp({projectId:"traidores-local", databaseURL:"https://traidores-local-default-rtdb.firebaseio.com"}, "interactive-v3");
  const db = adminFirestore(admin), auth = adminAuth(admin), database = adminDatabase(admin);
  const room = `play-v3-${Date.now()}`, password = "OnlyEmulatorQA123!", members = [], apps = [], unwatch = [];
  const gate = db.doc("onlineMaintenance/serverAuthority"), originalGate = await gate.get();
  let stopping = false; const pending = new Set();
  async function cleanup() {
    if (stopping) return; stopping = true;
    try { execFileSync(adb,["-s",device,"shell","am","force-stop",packageName], {stdio:'ignore'}); }
    catch { console.warn('QA device disconnected; continuing cleanup of the owned fixtures.'); }
    unwatch.forEach(stop => stop());
    await Promise.allSettled([...pending]);
    apps.forEach(app => goOffline(getDatabase(app)));
    await Promise.all(apps.map(deleteApp));
    await db.recursiveDelete(db.doc(`partidas/${room}`));
    await database.ref(`onlineV3/${room}`).remove();
    for (const u of members) {
      await auth.deleteUser(u.uid);
      await db.recursiveDelete(db.doc(`cuentas/${u.uid}`));
      await db.doc(`perfiles_publicos/${u.uid}`).delete();
      const {requestLimitId} = require("../functions/src/onlineRequestLimiter");
      await db.doc(`onlineRequestLimits/${requestLimitId(u.uid)}`).delete();
    }
    if (originalGate.exists) await gate.set(originalGate.data()); else await gate.delete();
    await deleteAdminApp(admin);
    console.log("Interactive V3 fixtures removed.");
  }
  process.once("SIGINT", () => cleanup().then(() => process.exit(0)).catch(e => {console.error(e); process.exit(1);}));
  process.once("SIGTERM", () => cleanup().then(() => process.exit(0)).catch(e => {console.error(e); process.exit(1);}));
  try {
    for (let i=0; i<5; i++) {
      const email = `${room}-${i}@example.test`, user = await auth.createUser({email,password,emailVerified:true});
      members.push({uid:user.uid,email,name:i ? ["", "Mateo", "Luna", "Ramón", "Clara"][i] : "Vos"});
    }
    const host = members[0], batch = db.batch();
    batch.set(gate, {enabled:true});
    batch.set(db.doc(`partidas/${room}`), {estado:"esperando", nombre:"Prueba V3", hostNombre:host.name, hostId:host.uid, hostActivoId:host.uid,
      hostVersion:0, jugadoresEsperados:5,jugadoresActuales:5,maxJugadores:5,modoPrueba:false,partidaInicialCreada:false,limpiezaPendiente:false,
      mapa:"pampa",mapaNombre:"Pampa",codigoSala:"QA2345",origen:"qa-emulator",protocolVersion:3,
      configLobby:{presetRoles:"RECOMMENDED",transicionSeg:3,nocheSeg:30,discusionSeg:45,votacionSeg:25}});
    members.forEach((u,i) => batch.set(db.doc(`partidas/${room}/jugadores/${u.uid}`), {
      nombre:u.name,publicId:String(i+1),listo:true,activoEnPartida:true,esHost:i===0,orden:i,
      protocolVersion:3,puedeArbitrar:false,estado:"conectado",uidTemporal:u.uid,avatarPerfil:"pampa_aldeano"}));
    batch.set(db.doc(`perfiles_publicos/${host.uid}`),{nombrePerfil:host.name,publicId:"1"});
    await batch.commit();
    for (const u of members.slice(1)) {
      const app = initializeApp({projectId:"traidores-local", apiKey:"local-only",databaseURL:"https://traidores-local-default-rtdb.firebaseio.com"}, u.uid);
      apps.push(app);
      const botAuth = getAuth(app); connectAuthEmulator(botAuth,"http://127.0.0.1:19099",{disableWarnings:true});
      await signInWithEmailAndPassword(botAuth,u.email,password);
      const botDb = getDatabase(app); connectDatabaseEmulator(botDb,"127.0.0.1",19000);
      const firestore = getFirestore(app); connectFirestoreEmulator(firestore,"127.0.0.1",18081);
      let publicState, own, busy = false; const submitted = new Set();
      async function act() {
        if (stopping || busy) return;
        if (publicState?.fase === "LOBBY") {
          const key = `${publicState.matchId}:ready`;
          if (submitted.has(key)) return; submitted.add(key);
          await updateDoc(doc(firestore,`partidas/${room}/jugadores/${u.uid}`),{listo:true}); return;
        }
        const action = chooseAction(publicState,own,u.uid);
        if (!action || publicState.limiteFaseEpochMs <= Date.now()) return;
        const key = `${publicState.matchId}:${publicState.phaseIndex}:${action.action}`;
        if (submitted.has(key)) return;
        busy = true; submitted.add(key);
        try {
          const token = await botAuth.currentUser.getIdToken();
          const jwt = [Buffer.from('{"alg":"none","typ":"JWT"}').toString("base64url"),Buffer.from('{"app_id":"interactive-qa"}').toString("base64url"),"local"].join(".");
          const response = await fetch("http://127.0.0.1:15001/traidores-local/southamerica-west1/accionPartidaV3", {
            method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`,"X-Firebase-AppCheck":jwt},
            body:JSON.stringify({data:{roomId:room,matchId:publicState.matchId,phaseIndex:publicState.phaseIndex,requestId:randomUUID(),...action}})});
          const result = await response.json();
          if (!response.ok) console.warn(`Bot action rejected: ${result.error?.details?.reason || response.status}`);
        } finally { busy = false; queueMicrotask(attempt); }
      }
      const attempt = () => {
        const work = act().catch(e => console.error("QA bot:",e.message));
        pending.add(work); work.finally(() => pending.delete(work));
      };
      // Before start there is no RTDB membership yet: a denied listener would be
      // cancelled permanently. Observe only the setup membership bit as Admin,
      // then attach authenticated SDK listeners after the atomic publication.
      const membership = database.ref(`onlineV3/${room}/snapshot/permissions/${u.uid}/member`);
      const attach = s => {
        if (!s.val() || stopping) return; membership.off("value",attach);
        unwatch.push(onValue(ref(botDb,`onlineV3/${room}/snapshot/public`), s => {publicState=s.val(); attempt();},e=>console.error("QA public:",e.message)));
        unwatch.push(onValue(ref(botDb,`onlineV3/${room}/snapshot/private/${u.uid}`), s => {own=s.val(); attempt();},e=>console.error("QA private:",e.message)));
      };
      membership.on("value",attach); unwatch.push(() => membership.off("value",attach));
    }
    execFileSync(adb,["-s",device,"shell","am","force-stop",packageName]);
    execFileSync(adb,["-s",device,"shell","am","start","-n",packageName + "/com.traidores.juego.ServerGameSmokeActivity",
      "--es","room",room,"--es","email",host.email,"--es","password",password,"--ez","open_lobby","true"]);
    console.log(`READY: ${room}. Play in Android. Bots use authenticated callables. Ctrl+C cleans this room only.`);
    await new Promise(() => {});
  } catch (error) { await cleanup(); throw error; }
}
if (require.main === module) main().catch(e => {console.error(e);process.exitCode=1;});
module.exports = {chooseAction};
