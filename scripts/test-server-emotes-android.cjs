'use strict';
// Directed native presentation QA. Phase fixtures exist only in isolated Firebase emulators.
// Intentions still travel through Android Auth/App Check/Functions and the real V3 motor.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const req = require('node:module').createRequire(require.resolve('../functions/package.json'));
const {initializeApp, deleteApp} = req('firebase-admin/app');
const {getFirestore} = req('firebase-admin/firestore');
const {getDatabase} = req('firebase-admin/database');
const {getAuth} = req('firebase-admin/auth');
const {startServerMatch, expireServerPhase, AUTHORITY_GATE} = require('../functions/src/onlineGameService');
const {projectServerGame, deadlineToken, createServerGame} = require('../functions/src/onlineGameCore');
const {prepareOnlineMatch} = require('../functions/src/onlineStartCore');
const {requestLimitId} = require('../functions/src/onlineRequestLimiter');
for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_DATABASE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST'])
  assert.match(process.env[key] || '', /^127\.0\.0\.1:/);
assert.equal(process.env.GCLOUD_PROJECT, 'traidores-local');
const adb = process.env.TRAIDORES_QA_ADB; let device = process.env.TRAIDORES_QA_DEVICE;
assert.ok(adb && fs.existsSync(adb)); assert.ok(device && !device.includes('\n'));
const pkg = 'com.traidores.juego.v3qa';
const app = initializeApp({projectId: 'traidores-local', databaseURL: 'https://traidores-local-default-rtdb.firebaseio.com'}, 'table-qa');
const db = getFirestore(app), rt = getDatabase(app), auth = getAuth(app);
const room = `table-native-${Date.now()}`, password = 'OnlyEmulatorQA123!', users = [];
const stateRef = db.doc(`partidas/${room}/servidor/current`), outboxRef = db.doc(`partidas/${room}/serverOutbox/current`);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let gate, baseline;
function adbCall(...args) { return execFileSync(adb, ['-s', device, ...args], {encoding: 'utf8', maxBuffer: 8000000, timeout: 30000}); }
let screenSize;
function prepareWindowDump() {
  const target = path.resolve('output/android-qa'); fs.mkdirSync(target, {recursive: true});
  const sdk = path.dirname(path.dirname(adb));
  const versions = fs.readdirSync(path.join(sdk, 'build-tools')).sort((a, b) => a.localeCompare(b, undefined, {numeric: true}));
  const d8 = path.join(sdk, 'build-tools', versions.at(-1), 'd8');
  assert.ok(process.env.JAVA_HOME && fs.existsSync(d8));
  execFileSync(path.join(process.env.JAVA_HOME, 'bin/javac'), ['--release', '8', '-Xlint:-options', '-d', target, 'scripts/android-qa/WindowDump.java']);
  execFileSync(d8, ['--output', path.join(target, 'window-dump.jar'), path.join(target, 'WindowDump.class')]);
  adbCall('push', path.join(target, 'window-dump.jar'), '/data/local/tmp/traidores-window-dump.jar');
  const sizes = [...adbCall('shell', 'wm', 'size').matchAll(/(\d+)x(\d+)/g)]; assert.ok(sizes.length);
  screenSize = sizes.at(-1).slice(1).join(' ');
}
async function xml() {
  for (let attempt = 0; ; attempt++) {
    // Never accept a stale hierarchy if Android kills the shell capture while an
    // activity is starting. Retry the capture, not the game action/assertion.
    adbCall('shell', 'rm', '-f', '/sdcard/table-qa.xml');
    try {
      adbCall('shell', `CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/traidores-window-dump.jar app_process /system/bin WindowDump /sdcard/table-qa.xml ${screenSize}`);
      return adbCall('shell', 'cat', '/sdcard/table-qa.xml');
    } catch (error) {
      if (attempt >= 2 || !(error.status === 137 || /No active accessibility root/.test(String(error)))) throw error;
      await sleep(250);
    }
  }
}
function tap(ui, predicate) {
  const node = (ui.match(/<node\b[^>]*>/g) || []).find(predicate);
  assert.ok(node, 'Missing native view');
  const b = node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/); assert.ok(b);
  adbCall('shell', 'input', 'tap', String(Math.round((+b[1] + +b[3]) / 2)), String(Math.round((+b[2] + +b[4]) / 2)));
}
async function wait(read, accepts, label, ms = 30000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { const value = await read(); if (accepts(value)) return value; await sleep(300); }
  throw Error(`Timeout: ${label}`);
}
function shot(name) {
  fs.writeFileSync(`output/server-v3-table-${name}.png`, execFileSync(adb, ['-s', device, 'exec-out', 'screencap', '-p'], {maxBuffer: 8000000, timeout: 30000}));
}
const byRole = key => baseline.players.find(p => p.role.key === key);
async function enter(player) {
  const user = users.find(u => u.uid === player.uid); assert.ok(user);
  adbCall('shell', 'am', 'force-stop', pkg);
  adbCall('shell', 'am', 'start', '-n', `${pkg}/com.traidores.juego.ServerGameSmokeActivity`,
    '--es', 'room', room, '--es', 'email', user.email, '--es', 'password', password, '--ez', 'recover_only', 'true');
  return wait(xml, ui => ['/currentPlayerName', '/rolePreviewName', '/tieVoteCards', '/payadorRevealPanel', '/oracleRevealPanel'].some(id => ui.includes(id)) || ui.includes('TU ROL'), 'native table');
}
async function fixture(phase, customize = () => {}, durationMs = 180000) {
  // New phaseIndex prevents the real client's persistent presentation cursor from replaying old overlays.
  const current = (await stateRef.get()).data();
  const state = structuredClone(baseline), now = Date.now();
  Object.assign(state, {phase, phaseIndex: current.phaseIndex + 1, revision: current.revision + 1,
    publicRevision: current.publicRevision + 1, phaseStartedAtMs: now, deadlineMs: now + durationMs,
    actions: {}, phaseActionCounts: {}, events: [], eventSeq: current.eventSeq || 0,
    announcement: `Prueba dirigida: ${phase}`, deserterTeam: 'Pueblo', round: 4});
  customize(state);
  const batch = db.batch(); batch.set(stateRef, state);
  batch.set(outboxRef, {generation: state.generation, revision: state.revision, token: deadlineToken(state),
    projection: projectServerGame(state), deliveredRevision: 0, recoveryAtMs: state.deadlineMs + 30000});
  await batch.commit();
  await wait(async () => (await outboxRef.get()).data(), o => o?.deliveredRevision === state.revision, 'published fixture');
  return state;
}
async function advanceReal(phase) {
  let state = (await stateRef.get()).data();
  if (state.phase === phase) {
    await sleep(Math.max(0, state.deadlineMs - Date.now()) + 40);
    state = (await stateRef.get()).data();
    if (state.phase === phase) await expireServerPhase({firestore: db, roomId: room, token: deadlineToken(state), nowMs: Date.now()});
  }
  return wait(async () => (await stateRef.get()).data(), s => s.phase !== phase, `real deadline ${phase}`);
}
async function actionRegistered(action) {
  return wait(async () => (await stateRef.get()).data(), s => Object.values(s.actions || {}).some(a => a.action === action), `native ${action}`);
}
async function chooseAndConfirm(player) {
  let ui = await xml(); tap(ui, n => n.includes(`content-desc="${player.name},`) && n.includes('clickable="true"'));
  ui = await xml(); tap(ui, n => n.includes('text="CONFIRMAR"') && n.includes('enabled="true"'));
}
async function deleteDoc(ref) {
  for (const collection of await ref.listCollections()) for (const child of await collection.listDocuments()) await deleteDoc(child);
  for (let attempt = 0; ; attempt++) { try { await ref.delete(); return; } catch (e) { if (e.code !== 1 || attempt === 2) throw e; await sleep(300); } }
}
async function main() {
  const devices = [process.env.TRAIDORES_QA_DEVICE, process.env.TRAIDORES_QA_PEER];
  assert.ok(devices.every(d => /^emulator-\d+$/.test(d || '')) && devices[0] !== devices[1], 'Two isolated Android emulators required');
  try {
    gate = await db.doc(AUTHORITY_GATE).get();
    for (let i=0;i<5;i++) users.push(await auth.createUser({email:`reactions-${Date.now()}-${i}@example.test`,password,emailVerified:true}));
    const batch = db.batch();
    batch.set(db.doc(AUTHORITY_GATE),{enabled:true,allowedHostUids:[users[0].uid],allowedRoomIds:[room]});
    batch.set(db.doc(`partidas/${room}`),{hostId:users[0].uid,hostActivoId:users[0].uid,estado:'esperando',protocolVersion:3,mapa:'pampa',codigoSala:'QAEMOT',jugadoresEsperados:5,jugadoresActuales:5,maxJugadores:5,modoPrueba:false,partidaInicialCreada:false,configLobby:{presetRoles:'RECOMMENDED',transicionSeg:60,nocheSeg:60,discusionSeg:180,votacionSeg:60}});
    users.forEach((u,i)=>batch.set(db.doc(`partidas/${room}/jugadores/${u.uid}`),{nombre:`QA${i}`,orden:i,publicId:`${i+1}`,listo:true,activoEnPartida:true,protocolVersion:3,puedeArbitrar:false}));
    await batch.commit();await startServerMatch({firestore:db,roomId:room,requesterId:users[0].uid,chooseRandomInt:()=>0});
    baseline=(await stateRef.get()).data();
    await fixture('VOTACION',s=>{s.round=2;});
    for(let i=0;i<2;i++) {device=devices[i];prepareWindowDump();adbCall('logcat','-c');await enter(baseline.players[i]);await wait(xml,x=> /resource-id="[^"]*\/btnToggleEmotes"[^>]*enabled="true"/.test(x),'emotes enabled');}
    device=devices[0];let ui=await xml();tap(ui,n=>n.includes('/btnToggleEmotes'));
    ui=await wait(xml,x=>x.includes('content-desc="Enojado'),'original palette');
    tap(ui,n=>n.includes('content-desc="Enojado'));
    const data=await wait(async()=>(await rt.ref(`onlineV3/${room}/reactions`).get()).val(),x=>Object.values(x||{}).length===1,'emote accepted by SDK/rules');
    const reaction=Object.values(data)[0];assert.equal(reaction.actorUid,users[0].uid);
    assert.equal(reaction.emoteId,'griego_enojado');
    for(let i=0;i<2;i++){device=devices[i];ui=await xml();assert.ok(ui.includes('content-desc="Enojado"'),'original bubble on both phones');}
    for(let i=0;i<2;i++){device=devices[i];shot(`emote-peer-${i}`);}
    await sleep(4200);
    for(let i=0;i<2;i++){device=devices[i];const log=adbCall('logcat','-d');assert.equal(log.split('v3_emote_received key=').length-1,1,'bubble event received exactly once');}
    device=devices[1];await enter(baseline.players[1]);await sleep(1200);ui=await xml();assert.ok(!ui.includes('content-desc="Enojado"'),'no old bubble on reconnect');
    assert.equal(adbCall('logcat','-d').split('v3_emote_received key=').length-1,1,'reconnect must not replay old reaction');
    // Voting chat is sent through the usual expanded UI and real RTDB permissions.
    tap(ui,n=>n.includes('/chatAmbientTitle'));ui=await xml();
    assert.ok(/resource-id="[^"]*\/chatInput"[^>]*enabled="true"/.test(ui),'chat writable in voting');
    tap(ui,n=>n.includes('/chatInput'));adbCall('shell','input','text','chat%sen%svotacion');await sleep(600);
    ui=await xml();tap(ui,n=>n.includes('/btnSendChat'));
    await wait(async()=>(await rt.ref(`onlineV3/${room}/chat/publico`).get()).val(),x=>Object.values(x||{}).some(v=>v.text.toLowerCase()==='chat en votacion'),'voting chat accepted');
    console.log('NATIVE EMOTES PASS: original palette, same bubble on two Android clients once, reconnect no replay, public chat during voting.');
  } catch(error) {
    try{fs.writeFileSync('output/server-v3-emote-failure.xml',await xml());shot('emote-failure');fs.writeFileSync('output/server-v3-emote-failure.log',adbCall('logcat','-d'));}catch(_){}
    throw error;
  } finally {
    for(const d of devices){device=d;try{adbCall('shell','am','force-stop',pkg);}catch(_){}}
    await db.recursiveDelete(db.doc(`partidas/${room}`));await rt.ref(`onlineV3/${room}`).remove();
    if(gate?.exists)await db.doc(AUTHORITY_GATE).set(gate.data());else await db.doc(AUTHORITY_GATE).delete();
    for(const u of users){await auth.deleteUser(u.uid);await db.doc(`cuentas/${u.uid}`).delete();}
    await deleteApp(app);
  }
}
main().catch(error=>{console.error(error);process.exitCode=1;});
