const fs = require('node:fs');
const assert = require('node:assert/strict');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc, serverTimestamp,
  query, orderBy, limit, runTransaction} = require('firebase/firestore');
const {createHash} = require('node:crypto');

(async () => {
  const env = await initializeTestEnvironment({projectId: 'demo-traidores-history', firestore: {
    host: '127.0.0.1', port: Number(process.env.FIRESTORE_EMULATOR_HOST?.split(':').at(-1) || 8081),
    rules: fs.readFileSync('firestore.rules', 'utf8')}});
  try {
    await env.clearFirestore();
    const owner = env.authenticatedContext('owner', {firebase: {sign_in_provider: 'password'}}).firestore();
    const other = env.authenticatedContext('other', {firebase: {sign_in_provider: 'apple.com'}}).firestore();
    const guest = env.authenticatedContext('guest', {firebase: {sign_in_provider: 'anonymous'}}).firestore();
    const anonymous = env.unauthenticatedContext().firestore();
    const key = 'local:account-history-test';
    const recordId = 'local_' + createHash('sha256').update(key).digest('hex');
    const data = {schemaVersion: 1, uid: 'owner', matchKey: key, origen: 'local',
      roomId: '', matchId: '', fechaLocalMs: Date.now(), mapKey: 'pampa', mapName: 'Pampa',
      roleKey: 'aldeano', roleName: 'Aldeano', won: true, participantCount: 5,
      winner: 'Pueblo', finalizadaEn: serverTimestamp()};
    const profile = {uidTemporal: 'owner', publicId: '42', nombrePerfil: 'Nacho', actualizadaEn: serverTimestamp()};
    await assertSucceeds(setDoc(doc(owner, 'perfiles_publicos', 'owner'), profile));
    const record = (db, uid = 'owner', id = recordId) => doc(db, 'cuentas', uid, 'historial', id);
    await assertSucceeds(setDoc(record(owner), data));
    await assertSucceeds(getDoc(record(owner)));
    await assertFails(getDoc(record(other)));
    await assertFails(getDoc(record(anonymous)));
    await assertFails(getDoc(record(guest, 'guest')));
    await assertFails(setDoc(record(other, 'owner', 'local_' + 'b'.repeat(64)), data));
    await assertFails(updateDoc(record(owner), {won: false}));
    await assertFails(deleteDoc(record(owner)));
    await assertFails(setDoc(doc(owner, 'cuentas', 'owner'), {partidas: 99, victorias: 99}));
    await assertFails(setDoc(record(guest, 'guest'), {...data, uid: 'guest'}));
    await assertFails(setDoc(record(owner, 'owner', 'online_' + 'a'.repeat(64)),
      {...data, origen: 'online', matchKey: 'online:fake', roomId: 'room', matchId: 'fake'}));
    for (const invalid of [{contabilizada: true}, {winner: 'Cancelada'}, {participantCount: 31},
      {uid: 'other'}, {finalizadaEn: new Date()}, {mapKey: 'unknown'}, {won: 'true'}]) {
      await assertFails(setDoc(record(owner, 'owner', 'local_' + 'c'.repeat(64)), {...data, ...invalid}));
    }
    // A fresh SDK session for the same UID recovers the persisted result, independent of device storage.
    const recovered = env.authenticatedContext('owner', {firebase: {sign_in_provider: 'google.com'}}).firestore();
    const history = await assertSucceeds(getDocs(query(collection(recovered, 'cuentas', 'owner', 'historial'),
      orderBy('finalizadaEn', 'desc'), limit(50))));
    assert.equal(history.size, 1);
    assert.equal(history.docs[0].data().matchKey, key);
    await assertFails(updateDoc(doc(owner, 'perfiles_publicos', 'owner'), {
      estadisticasPerfil: {partidas: 99, victorias: 99}, actualizadaEn: serverTimestamp()}));
    await env.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), 'perfiles_publicos', 'owner'), {
        estadisticasPerfil: {partidas: 1, victorias: 1}});
      await setDoc(doc(context.firestore(), 'partidas', 'history-final'), {
        nombre: 'Sala', codigoSala: 'ABC234', estado: 'en_juego', mapa: 'pampa', mapaNombre: 'Pampa',
        hostId: 'owner', hostNombre: 'Nacho', hostActivoId: 'owner', hostVersion: 0, partidaInicialCreada: true,
        jugadoresEsperados: 5, maxJugadores: 5, jugadoresActuales: 1, modoPrueba: false, visibilidad: 'publica',
        configLobby: {transicionSeg: 3, nocheSeg: 30, discusionSeg: 60, votacionSeg: 30,
          revelarRolesAlMorir: false, votosIndividuales: true, presetRoles: 'RECOMMENDED',
          roles: '2,1,1,1,0,0,0,0,0,0,0'},
        origen: 'history-rules-test', creadaEn: serverTimestamp(), actualizadaEn: serverTimestamp(),
        partidaInicial: {matchId: 'history-final-match', mapa: 'pampa'}, limpiezaPendiente: false});
      await setDoc(doc(context.firestore(), 'partidas', 'history-final', 'jugadores', 'owner'), {
        uidTemporal: 'owner', nombre: 'Nacho', activoEnPartida: true, esHost: true, estado: 'conectado'});
    });
    await assertSucceeds(updateDoc(doc(owner, 'perfiles_publicos', 'owner'), {
      nombrePerfil: 'Ignacio', actualizadaEn: serverTimestamp()}));
    assert.equal((await getDoc(doc(owner, 'perfiles_publicos', 'owner'))).data().estadisticasPerfil.partidas, 1);
    // The final checkpoint and durable room snapshot must be allowed in the same host transaction.
    const state = {versionEstado: 2, fase: 'RESULTADO', ronda: 2, phaseIndex: 4, ganador: 'Pueblo'};
    await assertSucceeds(runTransaction(owner, async (tx) => {
      const room = doc(owner, 'partidas', 'history-final');
      await tx.get(room);
      tx.set(doc(owner, 'partidas', 'history-final', 'runtime', 'authoritative'), {
        matchId: 'history-final-match', phaseIndex: 4, estadoPartida: state,
        actualizadaEn: serverTimestamp(), actualizadaEnLocal: Date.now(), actualizadaPor: 'owner'});
      tx.update(room, {estadoPartida: state, hostActivoId: 'owner', ultimaActividadOnline: serverTimestamp(),
        ultimoResultado: {ganador: 'Pueblo', ronda: 2, mapa: 'pampa',
          matchId: 'history-final-match', finalizadaEnLocal: Date.now()}});
    }));
    console.log('Historial: privacidad por UID, recuperación, inmutabilidad, estadísticas de servidor y final atómico aprobados.');
  } finally { await env.cleanup(); }
})().catch((error) => { console.error(error); process.exitCode = 1; });
