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
const adb = process.env.TRAIDORES_QA_ADB, device = process.env.TRAIDORES_QA_DEVICE;
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
  ui = await xml(); tap(ui, n => (n.includes('/btnVote') || n.includes('/btnConfirmTieVote')) && n.includes('enabled="true"'));
}
async function deleteDoc(ref) {
  for (const collection of await ref.listCollections()) for (const child of await collection.listDocuments()) await deleteDoc(child);
  for (let attempt = 0; ; attempt++) { try { await ref.delete(); return; } catch (e) { if (e.code !== 1 || attempt === 2) throw e; await sleep(300); } }
}
async function main() {
  try {
    prepareWindowDump();
    gate = await db.doc(AUTHORITY_GATE).get();
    for (let i = 0; i < 15; i++) users.push(await auth.createUser({email: `table-${Date.now()}-${i}@example.test`, password, emailVerified: true}));
    const batch = db.batch();
    batch.set(db.doc(AUTHORITY_GATE), {enabled: true, allowedHostUids: [users[0].uid], allowedRoomIds: [room]});
    batch.set(db.doc(`partidas/${room}`), {hostId: users[0].uid, hostActivoId: users[0].uid, estado: 'esperando',
      protocolVersion: 3, mapa: 'pampa', codigoSala: 'QA6789', jugadoresEsperados: 15, jugadoresActuales: 15,
      maxJugadores: 15, modoPrueba: false, partidaInicialCreada: false, configLobby: {presetRoles: 'RECOMMENDED', transicionSeg: 60, nocheSeg: 60, discusionSeg: 180, votacionSeg: 60}});
    users.forEach((user, i) => batch.set(db.doc(`partidas/${room}/jugadores/${user.uid}`), {nombre: `QA${i}`, orden: i, publicId: `${i + 1}`,
      listo: true, activoEnPartida: true, protocolVersion: 3, puedeArbitrar: false}));
    await batch.commit(); await startServerMatch({firestore: db, roomId: room, requesterId: users[0].uid, chooseRandomInt: () => 0});
    baseline = (await stateRef.get()).data();
    const deserter = byRole('desertor'), mayor = byRole('alcalde'), payador = byRole('payador'), towns = baseline.players.filter(p => p.role.key === 'aldeano');
    assert.ok(deserter && mayor && payador && towns.length > 2);
    const targets = towns.slice(0, 2);
    let ui;
    if (process.env.TRAIDORES_QA_INVESTIGATION_ONLY === 'true') {
      const police = byRole('policia'), killer = byRole('asesino');
      assert.ok(police && killer);
      await fixture('NOCHE', s => {s.afkEnabled = false; s.investigations = [];});
      const permission = rt.ref(`onlineV3/${room}/snapshot/permissions/${police.uid}`);
      const saved = (await permission.get()).val(); await permission.remove();
      const publication = sleep(3200).then(() => permission.set(saved));
      await enter(police); await publication;
      ui = await wait(xml, x => x.includes(`text="${police.name}"`) && !x.includes('Esperando sincronización'), 'delayed initial publication', 30000);
      assert.ok(!ui.includes('Ya no tenés acceso'));
      console.log('INVESTIGATION QA: delayed membership publication recovered without a false access-loss error.');
      await chooseAndConfirm(killer); await actionRegistered('investigar');
      ui = await wait(xml, x => x.includes('RESPUESTA PRIVADA') && x.includes(`${killer.name} parece CULPABLE.`), 'immediate private result');
      assert.equal((await stateRef.get()).data().phase, 'NOCHE'); shot('investigation-private');
      tap(ui, n => n.includes('/btnContinuePrivateFeedback') && n.includes('enabled="true"'));
      ui = await wait(xml, x => !x.includes('/privateFeedbackMessage') && x.includes(`${killer.name} parece CULPABLE.`), 'persistent own hint');
      await enter(police); await sleep(1000); ui = await xml();
      assert.ok(!ui.includes('/privateFeedbackMessage'), 'Reconnect must not replay a seen private-result window');
      assert.ok(ui.includes(`${killer.name} parece CULPABLE.`));
      console.log('INVESTIGATION QA: usual private window during NOCHE, persistent hint and reconnect without replay passed.');
      return;
    }
    if (process.env.TRAIDORES_QA_PARITY_ONLY !== 'true') {

    await fixture('REPARTO', s => {s.round = 1; s.deserterTeam = null;});
    ui = await enter(deserter);
    ui = await wait(xml, x => x.includes('ELEGIR MI BANDO'), 'initial deserter');
    tap(ui, n => n.includes('text="ELEGIR MI BANDO"') && n.includes('enabled="true"'));
    ui = await wait(xml, x => x.includes('desertorChoiceTitle'), 'usual deserter dialog'); shot('deserter-initial');
    tap(ui, n => n.includes('text="PUEBLO"'));
    const initial = await actionRegistered('desertor_initial'); assert.equal(initial.deserterTeam, 'Pueblo');
    await fixture('DIA_DEBATE', s => {s.players.find(p => p.uid === deserter.uid).muted = true;});
    ui = await wait(xml, x => x.includes('DEBATE') && !x.includes('Sincronizando'), 'round four');
    tap(ui, n => n.includes('/btnVote') && n.includes('enabled="true"'));
    ui = await wait(xml, x => x.includes('MANTENER PUEBLO'), 'muted deserter private choice'); shot('deserter-rethink');
    tap(ui, n => n.includes('text="MANTENER PUEBLO"'));
    await wait(async () => (await stateRef.get()).data(), s => s.deserterUsed && s.deserterTeam === 'Pueblo', 'one use consumed');
    console.log('TABLE QA: initial and muted round-four Desertor choices accepted by the server.');

    await fixture('DESEMPATE_VOTACION', s => {s.tieCandidates = targets.map(p => p.uid);});
    await enter(towns[2]); ui = await wait(xml, x => x.includes('/tieVoteCards') && x.includes('DESEMPATE'), 'tie window'); shot('tie');
    await chooseAndConfirm(targets[0]); const voted = await actionRegistered('votar');
    assert.equal(Object.values(voted.actions).find(a => a.action === 'votar').targetUid, targets[0].uid);
    console.log('TABLE QA: usual tie cards send a real vote.');
    await expireServerPhase({firestore: db, roomId: room, token: deadlineToken(voted), nowMs: voted.deadlineMs});
    ui = await wait(xml, x => x.includes('/voteResultCards') && x.includes('RECUENTO DE VOTOS'), 'aggregate recount'); shot('recount');
    assert.ok(ui.includes('1 voto') && ui.includes('La identidad de los votantes permanece oculta.'));
    const beforeContinue = (await stateRef.get()).data().phaseIndex;
    tap(ui, n => n.includes('/btnContinueVoteResult') && n.includes('enabled="true"'));
    assert.equal((await stateRef.get()).data().phaseIndex, beforeContinue, 'Closing presentation must not advance the server');
    console.log('TABLE QA: public aggregate recount shown; Continue has no authority.');

    await fixture('ALCALDE_DESEMPATE', s => {s.tieCandidates = targets.map(p => p.uid); s.players.find(p => p.uid === mayor.uid).muted = true;});
    await enter(mayor); ui = await wait(xml, x => x.includes('ÚLTIMA PALABRA'), 'hidden muted mayor window'); shot('mayor-muted');
    assert.ok(!/resource-id="[^"]*\/btnTieRevealMayor"/.test(ui));
    assert.ok(!/resource-id="[^"]*\/btnConfirmTieVote"[^>]*enabled="true"/.test(ui));
    await fixture('ALCALDE_DESEMPATE', s => {s.tieCandidates = targets.map(p => p.uid);});
    ui = await wait(xml, x => /resource-id="[^"]*\/btnTieRevealMayor"[^>]*enabled="true"/.test(x), 'active mayor reveal');
    tap(ui, n => n.includes('/btnTieRevealMayor')); ui = await wait(xml, x => x.includes('Tu cargo será público'), 'mayor confirmation');
    tap(ui, n => n.includes('text="CONFIRMAR"') && n.includes('clickable="true"'));
    await wait(async () => (await stateRef.get()).data(), s => s.mayorUid === mayor.uid, 'public mayor');
    await wait(xml, x => x.includes('ELEGIR CARTA'), 'mayor targets'); await chooseAndConfirm(targets[1]);
    await wait(async () => (await stateRef.get()).data(), s => s.phase === 'RESULTADO' && s.eliminationUid === targets[1].uid, 'mayor decision');
    console.log('TABLE QA: muted Mayor has no actions; active Mayor reveals and decides through Functions.');

    await fixture('DIA_DEBATE'); await enter(payador);
    for (const target of targets) {
      await wait(xml, x => x.includes('/btnVote') && !x.includes('Enviando…'), 'Payador action');
      await chooseAndConfirm(target);
      await wait(async () => (await stateRef.get()).data(), s => s.counterpointPlayers.includes(target.uid), 'counterpoint participant');
    }
    ui = await wait(xml, x => x.includes('¡COMIENZA EL CONTRAPUNTO!'), 'usual contrapunto illustration'); shot('counterpoint');
    assert.ok(ui.includes(targets[0].name.toUpperCase()) && ui.includes(targets[1].name.toUpperCase()));
    await sleep(7500); await enter(targets[0]);
    ui = await wait(xml, x => x.includes('CONTRAPUNTO') && !x.includes('¡COMIENZA'), 'counterpoint table', 20000);
    tap(ui, n => n.includes('/chatAmbientTitle')); ui = await xml();
    tap(ui, n => n.includes('/chatInput') && n.includes('enabled="true"')); adbCall('shell', 'input', 'text', 'voz%scontrapunto'); adbCall('shell', 'input', 'keyevent', '4');
    ui = await xml(); tap(ui, n => n.includes('/btnSendChat') && n.includes('enabled="true"'));
    await wait(async () => (await rt.ref(`onlineV3/${room}/chat/publico`).get()).val(), value => Object.values(value || {}).some(m => m.actorUid === targets[0].uid && m.text.toLowerCase() === 'voz contrapunto'), 'counterpoint real chat');
    await enter(towns[2]); ui = await wait(xml, x => x.includes('CONTRAPUNTO') && !x.includes('¡COMIENZA'), 'nonparticipant', 20000);
    tap(ui, n => n.includes('/chatAmbientTitle')); ui = await xml();
    assert.ok(/resource-id="[^"]*\/chatInput"[^>]*enabled="false"/.test(ui));
    await enter(payador); await wait(xml, x => x.includes('CONTRAPUNTO') && !x.includes('¡COMIENZA'), 'Payador selection', 20000);
    await chooseAndConfirm(targets[0]); await wait(async () => (await stateRef.get()).data(), s => s.phase === 'VOTACION' && s.counterpointPointed === targets[0].uid, 'pointed target');
    console.log('TABLE QA: Contrapunto introduction, real participant chat, read-only spectator and Payador selection passed.');

    await enter(towns[2]); await fixture('AMANECER', s => {
      s.players.find(p => p.uid === targets[0].uid).muted = true;
      s.events = [{seq: ++s.eventSeq, codigo: 'DAWN_NO_VICTIMS', ronda: s.round, jugadores: [], texto: 'Nadie murió.'}];
    });
    ui = await wait(xml, x => x.includes('/silenceRevealPlayerName'), 'silence cage', 22000); shot('silence');
    assert.ok(ui.includes(targets[0].name));
    await fixture('DIA_DEBATE', s => { const guest = s.players.find(p => p.uid === targets[0].uid); guest.alive = false; guest.deathCause = 'NIGHT'; s.oracleGuestUid = guest.uid; });
    ui = await wait(xml, x => x.includes('VOZ RECUPERADA'), 'Oracle introduction'); shot('oracle');
    await enter(targets[0]); await wait(xml, x => x.includes('DEBATE') && !x.includes('VOZ RECUPERADA'), 'invited dead player', 20000);
    ui = await xml(); tap(ui, n => n.includes('/chatAmbientTitle')); ui = await xml();
    assert.ok(/resource-id="[^"]*\/chatInput"[^>]*enabled="true"/.test(ui));
    assert.ok(!/resource-id="[^"]*\/btnVote"[^>]*enabled="true"/.test(ui));
    console.log('TABLE QA: public silence cage and Oracle invited voice without voting passed.');
    } else console.log('TABLE QA: parity-only run; earlier windows/actions cases intentionally skipped.');

    // Real four-second transition deadlines, not future-clock/long presentation fixtures.
    await fixture('NOCHE', s => {s.afkEnabled = false;}); await enter(towns[2]); await sleep(2200);
    await fixture('NOCHE', s => {
      s.afkEnabled = false; s.timing.transitionSeconds = 4; s.timing.discussionSeconds = 30;
      s.config.revelarRolesAlMorir = true;
      const killer = s.players.find(p => p.role.key === 'asesino'), merc = s.players.find(p => p.role.key === 'mercenario');
      s.actions[`${killer.order}:night`] = {action: 'matar', actorOrder: killer.order, targetUid: targets[0].uid};
      s.actions[`${merc.order}:night`] = {action: 'silenciar', actorOrder: merc.order, targetUid: targets[1].uid};
    }, 4000);
    await advanceReal('NOCHE');
    await wait(xml, x => x.includes('/deathRevealPlayerName'), 'real dawn death'); shot('real-dawn-death');
    await advanceReal('AMANECER');
    ui = await wait(xml, x => x.includes('/silenceRevealPlayerName'), 'silence survives four-second dawn', 20000);
    assert.equal((await stateRef.get()).data().phase, 'DIA_DEBATE'); shot('real-dawn-silence');
    await wait(xml, x => !x.includes('/silenceRevealPlayerName'), 'silence finishes automatically', 10000);
    console.log('PARITY QA: death and silence complete once across a real four-second dawn/debate transition.');

    async function recount(target, reveal) {
      return fixture('RECUENTO_VOTOS', s => {
        s.afkEnabled = false; s.timing.transitionSeconds = 4; s.config.revelarRolesAlMorir = reveal;
        s.eliminationUid = target.uid; s.tieCandidates = [target.uid]; s.voteTotals = {[target.order]: 3}; s.voteRound = 1;
        s.events = [{seq: ++s.eventSeq, codigo: 'VOTE_MAJORITY', ronda: s.round, jugadores: [target.uid], texto: `${target.name} recibió la mayoría de los votos.`}];
        s.announcement = s.events[0].texto;
      }, 4000);
    }
    await recount(targets[0], true); await advanceReal('RECUENTO_VOTOS');
    ui = await wait(xml, x => x.includes('text="EXPULSIÓN"') || x.includes('CARTA REVELADA'), 'ordinary expulsion ceremony');
    const result = (await stateRef.get()).data();
    assert.equal(result.phase, 'RESULTADO'); assert.equal(result.deadlineMs - result.phaseStartedAtMs, 8000);
    assert.equal(result.players.find(p => p.uid === targets[0].uid).deathCause, 'VOTE');
    assert.equal(projectServerGame(result).permissions[targets[0].uid].deadChat, false);
    assert.ok(!ui.includes(`${targets[0].name} fue expulsado por el pueblo.`), 'No announcement before impact');
    assert.ok(ui.includes(`content-desc="${targets[0].name}, rol oculto, En partida`), 'Roster stays alive/hidden before impact');
    shot('real-expulsion-before-impact');
    await wait(xml, x => x.includes('FUE EXPULSADO') || x.includes(`${targets[0].name} fue expulsado por el pueblo.`), 'expulsion impact', 12000);
    shot('real-expulsion-after-impact');
    await advanceReal('RESULTADO');
    ui = await wait(xml, x => x.includes('NOCHE') && !x.includes('/voteResultCards'), 'next night after ceremony');
    assert.equal(projectServerGame((await stateRef.get()).data()).permissions[targets[0].uid].deadChat, true);
    console.log('PARITY QA: real eight-second result, boot impact, no early roster/announcement and next-night chat permission.');

    // Bufón belongs to Medieval. Prepare a separate map fixture through the actual
    // role allocator, retaining the isolated room's authenticated participant IDs.
    const medieval = prepareOnlineMatch({requesterId: users[0].uid, room: {hostId: users[0].uid, estado: 'esperando',
      jugadoresEsperados: 15, codigoSala: 'QA6789', mapa: 'medieval', configLobby: {presetRoles: 'RECOMMENDED', transicionSeg: 4}},
      players: baseline.players.map(p => ({id: p.uid, order: p.order, name: p.name, initial: p.name.slice(0, 1), activeInMatch: true, ready: true, publicId: p.publicId})),
      matchId: baseline.matchId, nowMs: Date.now(), randomInt: () => 0});
    baseline = createServerGame({roomId: room, prepared: medieval, nowMs: Date.now()});
    await db.doc(`partidas/${room}`).update({mapa: 'medieval', partidaInicial: medieval.payloads.initialMatch});
    await fixture('NOCHE', s => {s.afkEnabled = false;}); await enter(towns[2]); await sleep(2200);
    const fool = byRole('bufon'); assert.ok(fool);
    await recount(fool, false); await advanceReal('RECUENTO_VOTOS');
    ui = await wait(xml, x => x.includes('EXPULSIÓN'), 'hidden-role Jester expulsion');
    assert.ok(!ui.includes('ERA EL BUFÓN'), 'Jester stays unannounced until expulsion finishes');
    ui = await wait(xml, x => x.includes(`${fool.name.toUpperCase()} ERA EL BUFÓN`), 'usual Jester ceremony', 12000);
    assert.ok(!ui.includes('/btnReturnJesterVictory')); shot('real-jester');
    const jesterResult = (await stateRef.get()).data();
    assert.equal(jesterResult.phase, 'RESULTADO'); assert.equal(jesterResult.deadlineMs - jesterResult.phaseStartedAtMs, 12000);
    await advanceReal('RESULTADO');
    await wait(xml, x => x.includes('NOCHE') && !x.includes('/jesterVictoryPanel'), 'Jester automatically gives way to night');
    console.log('PARITY QA: public Jester celebration follows the boot with hidden roles and a real twelve-second result.');

    await recount(targets[1], false); await advanceReal('RECUENTO_VOTOS');
    await wait(xml, x => x.includes('EXPULSIÓN'), 'reconnect during expulsion');
    await enter(towns[2]);
    ui = await wait(xml, x => x.includes('FUE EXPULSADO') && x.includes('/voteResultCards'), 'seen event restored statically', 10000);
    assert.equal((await stateRef.get()).data().phase, 'RESULTADO'); shot('real-expulsion-reconnect');
    console.log('PARITY QA: reconnection in RESULTADO restores a static outcome without replaying the ceremony.');

  } catch (error) {
    try { fs.writeFileSync('output/server-v3-table-failure.xml', await xml()); shot('failure'); fs.writeFileSync('output/server-v3-table-failure.log', adbCall('logcat', '-d', '-s', 'AndroidRuntime:E', 'TRAIDORES_V3_QA:E', 'TraidoresOnline:E')); } catch (_) {}
    throw error;
  } finally {
    try {
      adbCall('shell', 'am', 'force-stop', pkg); await sleep(2000);
      await deleteDoc(db.doc(`partidas/${room}`)); await rt.ref(`onlineV3/${room}`).remove();
      if (gate?.exists) await db.doc(AUTHORITY_GATE).set(gate.data()); else if (gate) await deleteDoc(db.doc(AUTHORITY_GATE));
      for (const user of users) { await auth.deleteUser(user.uid); await deleteDoc(db.doc(`onlineRequestLimits/${requestLimitId(user.uid)}`)); await deleteDoc(db.doc(`cuentas/${user.uid}`)); }
    } finally { await deleteApp(app); }
  }
}
main().catch(error => {console.error(error); process.exitCode = 1;});
