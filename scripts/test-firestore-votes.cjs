/** Adversarial multiuser contract tests. Uses only Firebase emulators; never production. */
const fs = require('fs');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const {
  doc, collection, getDocFromServer, getDocsFromServer, query, where, setDoc, updateDoc,
  deleteDoc, serverTimestamp, increment, Timestamp, disableNetwork, enableNetwork,
} = require('firebase/firestore');

const roster = [
  { uid: 'vote_host', name: 'Host', anonymous: false },
  { uid: 'vote_guest_a', name: 'Mufa 1001', anonymous: true },
  { uid: 'vote_guest_b', name: 'Careta 1002', anonymous: true },
  { uid: 'vote_registered', name: 'Registrado', anonymous: false },
];
let passed = 0;
async function check(label, work) {
  await work();
  passed++;
  console.log(`VOTE ${String(passed).padStart(2, '0')} OK ${label}`);
}
function voteId(match, actor, phaseIndex = 7, round = 1) {
  return `${match}_${roster[actor].uid}_r${round}_p${phaseIndex}_votar_s1`;
}
function player(actor) {
  const p = roster[actor];
  return {
    uidTemporal: p.uid, nombre: p.name, nombrePerfil: p.name, nombreSala: p.name,
    ...(p.anonymous ? {} : { publicId: String(actor + 1) }), bioPerfil: '',
    esHost: actor === 0, estado: 'conectado', activoEnPartida: true, orden: actor,
    listo: true, unidoEn: serverTimestamp(), ultimaConexion: serverTimestamp(),
  };
}
function vote(match, actor, target, overrides = {}) {
  return {
    matchId: match, tipo: 'accion_jugador', actorId: roster[actor].uid,
    actorNombre: roster[actor].name, actorEsHost: actor === 0,
    objetivoNombre: roster[target].name, fase: 'VOTACION', ronda: 1, phaseIndex: 7,
    modoCliente: 'android', detalles: {
      accion: 'votar', actorOrden: actor, objetivoOrden: target,
      faseResultado: 'RECUENTO_VOTOS', phaseIndexResultado: 8,
    }, cambiosVoto: 0, creadaEn: serverTimestamp(), actualizadaEn: serverTimestamp(),
    creadaEnLocal: Date.now(), ...overrides,
  };
}
function change(actor, target, overrides = {}) {
  return {
    objetivoNombre: roster[target].name,
    detalles: { accion: 'votar', actorOrden: actor, objetivoOrden: target,
      faseResultado: 'RECUENTO_VOTOS', phaseIndexResultado: 8 },
    cambiosVoto: increment(1), actualizadaEn: serverTimestamp(), ...overrides,
  };
}
async function seed(env, id, stateOverrides = {}, roomOverrides = {}) {
  const match = `match_${id}`;
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'partidas', id), {
      nombre: 'Sala votacion', codigoSala: 'VOT234', estado: 'en_juego', mapa: 'pampa',
      mapaNombre: 'Pampa', hostId: roster[0].uid, hostActivoId: roster[0].uid,
      hostNombre: 'Host', hostVersion: 1, partidaInicialCreada: true,
      jugadoresEsperados: 4, jugadoresActuales: 4, maxJugadores: 4, modoPrueba: true,
      origen: 'votes-emulator-test', actualizadaEn: serverTimestamp(),
      partidaInicial: { matchId: match, mapa: 'pampa', jugadores: roster.map((p, i) => ({
        orden: i, uidTemporal: p.uid, nombre: p.name,
      })) }, ...roomOverrides,
    });
    for (let i = 0; i < roster.length; i++) {
      await setDoc(doc(db, 'partidas', id, 'jugadores', roster[i].uid), player(i));
    }
    await setDoc(doc(db, 'partidas', id, 'runtime', 'authoritative'), {
      matchId: match, phaseIndex: 7, actualizadaPor: roster[0].uid,
      actualizadaEn: serverTimestamp(), actualizadaEnLocal: Date.now(),
      estadoPartida: {
        versionEstado: 2, protocoloVoto: 2, fase: 'VOTACION', ronda: 1, phaseIndex: 7,
        limiteFaseEpochMs: Date.now() + 60000, ganador: '', votacionCerrada: false,
        jugadores: roster.map((p, i) => ({ orden: i, nombre: p.name, vivo: true, muteado: false })),
        candidatosDesempate: [roster[0].name, roster[2].name], ...stateOverrides,
      },
    });
  });
  return match;
}
async function adminPatch(env, path, patch) {
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), path), patch));
}
// Mirrors the Android blind-create + atomic-update + server-confirmed idempotency fallback.
async function submit(ref, initial, patch) {
  try { await setDoc(ref, initial); return 'created'; }
  catch (error) { if (error.code !== 'permission-denied') throw error; }
  try { await updateDoc(ref, patch); return 'updated'; }
  catch (error) {
    if (error.code !== 'permission-denied') throw error;
    const stored = (await getDocFromServer(ref)).data();
    for (const key of ['actorId', 'matchId', 'objetivoNombre', 'fase', 'ronda', 'phaseIndex']) {
      if (stored?.[key] !== initial[key]) throw error;
    }
    return 'idempotent';
  }
}
async function main() {
  const env = await initializeTestEnvironment({
    projectId: 'traidores-local',
    firestore: { host: '127.0.0.1', port: 8081, rules: fs.readFileSync('firestore.rules', 'utf8') },
  });
  try {
    const clients = roster.map(p => env.authenticatedContext(p.uid, {
      firebase: { sign_in_provider: p.anonymous ? 'anonymous' : 'password' },
    }).firestore());
    const outsider = env.authenticatedContext('vote_outsider').firestore();
    const anon = env.unauthenticatedContext().firestore();
    const room = 'vote_contract';
    const match = await seed(env, room);
    const refs = clients.map((db, i) => doc(db, 'partidas', room, 'acciones', voteId(match, i)));

    await check('regression: guest cannot read missing vote, host can', async () => {
      await assertFails(getDocFromServer(refs[1]));
      assert.equal((await assertSucceeds(getDocFromServer(refs[0]))).exists(), false);
    });
    await check('first votes: 4 simultaneous players, 2 simulated anonymous Auth claims', async () => {
      await Promise.all(refs.map((ref, i) => assertSucceeds(setDoc(ref, vote(match, i, (i + 1) % 4)))));
      const stored = await getDocsFromServer(query(collection(clients[0], 'partidas', room, 'acciones'), where('matchId', '==', match)));
      assert.equal(stored.size, 4);
      assert.equal(new Set(stored.docs.map(d => d.data().actorId)).size, 4);
    });
    await check('same retry confirms server result without incrementing change count', async () => {
      assert.equal(await submit(refs[1], vote(match, 1, 2), change(1, 2)), 'idempotent');
      assert.equal((await getDocFromServer(refs[1])).data().cambiosVoto, 0);
    });
    await check('vote change keeps one document and increments once', async () => {
      assert.equal(await submit(refs[1], vote(match, 1, 3), change(1, 3)), 'updated');
      assert.equal((await getDocFromServer(refs[1])).data().cambiosVoto, 1);
    });
    await check('double tap same change is idempotent under concurrent writes', async () => {
      const results = await Promise.all([0, 1].map(() => submit(refs[1], vote(match, 1, 0), change(1, 0))));
      assert.equal(results.filter(r => r === 'updated').length, 1);
      assert.equal((await getDocFromServer(refs[1])).data().cambiosVoto, 2);
    });
    await check('concurrent distinct changes remain serialized and one vote', async () => {
      await Promise.all([2, 3].map(target => assertSucceeds(updateDoc(refs[1], change(1, target)))));
      const result = (await getDocFromServer(refs[1])).data();
      assert.equal(result.cambiosVoto, 4);
      assert.ok([roster[2].name, roster[3].name].includes(result.objetivoNombre));
    });
    await check('outsider and unauthenticated cannot read or write votes', async () => {
      await assertFails(getDocFromServer(doc(outsider, refs[1].path)));
      await assertFails(setDoc(doc(outsider, refs[1].path), vote(match, 1, 0)));
      await assertFails(setDoc(doc(anon, refs[1].path), vote(match, 1, 0)));
    });
    await check('cannot impersonate another UID or overwrite another vote, even host', async () => {
      await assertFails(updateDoc(doc(clients[0], refs[1].path), change(1, 0)));
      await assertFails(setDoc(doc(clients[2], refs[1].path), vote(match, 1, 0)));
      await assertFails(updateDoc(refs[1], { ...change(1, 0), actorId: roster[0].uid }));
      await assertFails(getDocFromServer(doc(clients[2], refs[1].path)));
    });
    await check('cannot bypass one-vote slot with arbitrary document ID', async () => {
      await assertFails(setDoc(doc(clients[1], 'partidas', room, 'acciones', 'alternate-slot'), vote(match, 1, 0)));
    });
    await check('target index, name and self are validated', async () => {
      await assertFails(updateDoc(refs[1], change(1, 0, { objetivoNombre: 'Inventado' })));
      await assertFails(updateDoc(refs[1], change(1, 1)));
      await assertFails(updateDoc(refs[1], change(1, 0, { detalles: { ...change(1, 0).detalles, objetivoOrden: 14 } })));
    });
    await check('old/future phase and old match cannot create another vote', async () => {
      for (const phase of [6, 8]) {
        await assertFails(setDoc(doc(clients[1], 'partidas', room, 'acciones', voteId(match, 1, phase)), vote(match, 1, 0, { phaseIndex: phase })));
      }
      await assertFails(setDoc(doc(clients[1], 'partidas', room, 'acciones', voteId('match_previous', 1)), vote('match_previous', 1, 0)));
      await assertFails(setDoc(doc(clients[1], 'partidas', room, 'acciones', voteId(match, 1, 7, 2)), vote(match, 1, 0, { ronda: 2 })));
    });
    await check('server timestamp prevents client backdating', async () => {
      await assertFails(updateDoc(refs[1], change(1, 0, { actualizadaEn: Timestamp.fromMillis(1000) })));
    });
    await check('bounded update count cannot reset, skip or exceed 24', async () => {
      await assertFails(updateDoc(refs[1], change(1, 0, { cambiosVoto: 0 })));
      await assertFails(updateDoc(refs[1], change(1, 0, { cambiosVoto: 24 })));
      await adminPatch(env, refs[1].path, { cambiosVoto: 24 });
      await assertFails(updateDoc(refs[1], change(1, 0)));
    });
    await check('host migration preserves existing immutable vote actorEsHost', async () => {
      await adminPatch(env, `partidas/${room}`, { hostActivoId: roster[2].uid, hostVersion: 2 });
      await assertSucceeds(updateDoc(refs[0], change(0, 2)));
      await assertSucceeds(updateDoc(refs[2], change(2, 0)));
    });

    for (const [label, state] of [
      ['explicit close', { votacionCerrada: true }],
      ['deadline plus existing 1500ms grace expired', { limiteFaseEpochMs: Date.now() - 10000 }],
      ['already counting', { fase: 'RECUENTO_VOTOS', phaseIndex: 8 }],
      ['winner published', { ganador: 'Pueblo' }],
    ]) {
      await check(`cannot create/update after ${label}`, async () => {
        const id = `vote_closed_${passed}`;
        const m = await seed(env, id);
        const r = doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1));
        await setDoc(r, vote(m, 1, 0));
        await adminPatch(env, `partidas/${id}/runtime/authoritative`, Object.fromEntries(Object.entries(state).map(([k, v]) => [`estadoPartida.${k}`, v])));
        await assertFails(updateDoc(r, change(1, 2)));
        await assertFails(setDoc(doc(clients[2], 'partidas', id, 'acciones', voteId(m, 2)), vote(m, 2, 0)));
      });
    }
    await check('dead/muted actors and dead targets rejected for first vote', async () => {
      for (const fault of ['actorDead', 'actorMuted', 'targetDead']) {
        const players = roster.map((p, i) => ({ nombre: p.name, orden: i, vivo: true, muteado: false }));
        if (fault === 'actorDead') players[1].vivo = false;
        if (fault === 'actorMuted') players[1].muteado = true;
        if (fault === 'targetDead') players[0].vivo = false;
        const id = `vote_${fault}`;
        const m = await seed(env, id, { jugadores: players });
        await assertFails(setDoc(doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1)), vote(m, 1, 0)));
      }
    });
    await check('tie vote only accepts living tied candidates', async () => {
      const id = 'vote_tie';
      const m = await seed(env, id, { fase: 'DESEMPATE_VOTACION' });
      const r = doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1));
      await assertFails(setDoc(r, vote(m, 1, 3, { fase: 'DESEMPATE_VOTACION' })));
      await assertSucceeds(setDoc(r, vote(m, 1, 2, { fase: 'DESEMPATE_VOTACION' })));
    });
    await check('offline queued vote commits after reconnection while window remains open', async () => {
      const id = 'vote_reconnect';
      const m = await seed(env, id);
      const r = doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1));
      await disableNetwork(clients[1]);
      const pending = setDoc(r, vote(m, 1, 0));
      await enableNetwork(clients[1]);
      await assertSucceeds(pending);
      assert.equal((await getDocFromServer(r)).data().objetivoNombre, roster[0].name);
    });
    await check('offline queued vote is denied if host closes before reconnection', async () => {
      const id = 'vote_reconnect_closed';
      const m = await seed(env, id);
      const r = doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1));
      await disableNetwork(clients[1]);
      const pending = setDoc(r, vote(m, 1, 0));
      const expectedFailure = assertFails(pending);
      await adminPatch(env, `partidas/${id}/runtime/authoritative`, { 'estadoPartida.votacionCerrada': true });
      await enableNetwork(clients[1]);
      await expectedFailure;
    });
    await check('outsider cannot manufacture membership in running/orphan room', async () => {
      const forged = { ...player(3), uidTemporal: 'vote_outsider', esHost: false };
      await assertFails(setDoc(doc(outsider, 'partidas', room, 'jugadores', 'vote_outsider'), forged));
      await assertFails(setDoc(doc(outsider, 'partidas', 'missing_room', 'jugadores', 'vote_outsider'), forged));
      await assertFails(getDocFromServer(doc(outsider, 'partidas', room, 'runtime', 'authoritative')));
    });
    await check('player cannot self-promote or resurrect removed membership in running game', async () => {
      const id = 'vote_removed';
      const m = await seed(env, id);
      const p = doc(clients[1], 'partidas', id, 'jugadores', roster[1].uid);
      await assertFails(updateDoc(p, { esHost: true }));
      await adminPatch(env, p.path, { activoEnPartida: false });
      await assertFails(updateDoc(p, { activoEnPartida: true, estado: 'conectado' }));
      await assertFails(setDoc(doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1)), vote(m, 1, 0)));
      await assertFails(getDocFromServer(doc(clients[1], 'partidas', id, 'runtime', 'authoritative')));
    });
    await check('cleanup marker blocks host and guest writes, and cannot be forged/cleared', async () => {
      const id = 'vote_cleanup';
      const m = await seed(env, id);
      const roomRef = doc(clients[0], 'partidas', id);
      await assertFails(updateDoc(roomRef, { cleanupState: 'deleting', actualizadaEn: serverTimestamp() }));
      await adminPatch(env, roomRef.path, { cleanupState: 'deleting' });
      await assertFails(updateDoc(roomRef, { cleanupState: '', actualizadaEn: serverTimestamp() }));
      await assertFails(updateDoc(roomRef, { estado: 'abandonada', actualizadaEn: serverTimestamp() }));
      await assertFails(updateDoc(doc(clients[1], 'partidas', id, 'jugadores', roster[1].uid), { estado: 'desconectado' }));
      await assertFails(setDoc(doc(clients[1], 'partidas', id, 'acciones', voteId(m, 1)), vote(m, 1, 0)));
      await assertFails(deleteDoc(roomRef));
    });
    console.log(`Firestore adversarial vote tests passed: ${passed} scenarios.`);
  } finally { await env.cleanup(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
