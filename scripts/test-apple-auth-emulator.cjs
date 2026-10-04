const fs = require('node:fs');
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {initializeApp, deleteApp} = require('firebase/app');
const {getAuth, connectAuthEmulator, signInAnonymously, signInWithCredential,
  linkWithCredential, OAuthProvider, getIdTokenResult} = require('firebase/auth');
const {getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, serverTimestamp} = require('firebase/firestore');
const {getStorage, connectStorageEmulator, ref, uploadBytes, getBytes} = require('firebase/storage');

// Exercises Firebase tokens and rules, not Apple's native authorization UI or nonce validation.
// Every endpoint is local and the project is a demo project with no production resources.
const projectId = 'demo-traidores-photos';
const apps = [];
function client(name) {
  const app = initializeApp({projectId, apiKey: 'fake-emulator-key',
    storageBucket: `${projectId}.appspot.com`}, name);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', {disableWarnings: true});
  const db = getFirestore(app);
  connectFirestoreEmulator(db, '127.0.0.1', 8081);
  const storage = getStorage(app);
  connectStorageEmulator(storage, '127.0.0.1', 9199);
  return {auth, db, storage};
}
function appleCredential(sub, email) {
  return new OAuthProvider('apple.com').credential({idToken: JSON.stringify({
    sub, ...(email ? {email, email_verified: true} : {})
  })});
}
function room(uid, code) {
  return {nombre: 'Prueba Apple', codigoSala: code, estado: 'esperando', mapa: 'pampa',
    mapaNombre: 'Pampa', hostId: uid, hostNombre: 'Jugador', hostActivoId: uid,
    hostVersion: 0, partidaInicialCreada: false, limpiezaPendiente: false,
    jugadoresEsperados: 5, maxJugadores: 5, jugadoresActuales: 1, modoPrueba: false,
    visibilidad: 'publica', origen: 'apple-auth-emulator',
    creadaEn: serverTimestamp(), actualizadaEn: serverTimestamp()};
}
async function assertRegisteredPermissions(userClient, uid, code) {
  await assertSucceeds(setDoc(doc(userClient.db, 'partidas', `apple-${code}`), room(uid, code)));
  const bytes = Buffer.from(`local JPEG transport fixture ${code}`);
  const hash = createHash('sha256').update(bytes).digest('hex');
  const photo = ref(userClient.storage, `profilePhotos/${uid}/avatar_${hash}.jpg`);
  await assertSucceeds(uploadBytes(photo, bytes, {contentType: 'image/jpeg'}));
  assert.deepEqual(Buffer.from(await assertSucceeds(getBytes(photo))), bytes);
}

(async () => {
  const env = await initializeTestEnvironment({projectId,
    firestore: {host: '127.0.0.1', port: 8081, rules: fs.readFileSync('firestore.rules', 'utf8')},
    storage: {host: '127.0.0.1', port: 9199, rules: fs.readFileSync('storage.rules', 'utf8')}});
  try {
    const owner = client('apple-owner');
    const guestUid = (await signInAnonymously(owner.auth)).user.uid;
    await assertFails(setDoc(doc(owner.db, 'partidas', 'anonymous-room'), room(guestUid, 'ABC234')));
    const guestPhoto = ref(owner.storage, `profilePhotos/${guestUid}/avatar_${'a'.repeat(64)}.jpg`);
    await assertFails(uploadBytes(guestPhoto, new Uint8Array(100), {contentType: 'image/jpeg'}));

    const relay = 'beta-player@privaterelay.appleid.com';
    const linked = await linkWithCredential(owner.auth.currentUser, appleCredential('apple-beta-player', relay));
    assert.equal(linked.user.uid, guestUid, 'Linking must preserve the guest UID');
    assert.equal(linked.user.isAnonymous, false);
    assert.equal(linked.user.email, relay);
    const claims = (await getIdTokenResult(linked.user, true)).claims;
    assert.equal(claims.firebase.sign_in_provider, 'apple.com');
    await assertRegisteredPermissions(owner, guestUid, 'ABC234');
    const savedProfile = {uidTemporal: guestUid, publicId: '42', nombrePerfil: 'Jugador Apple',
      fotoPerfil: '', actualizadaEn: serverTimestamp()};
    await assertSucceeds(setDoc(doc(owner.db, 'perfiles_publicos', guestUid), savedProfile));

    // A different guest recovers the existing Apple UID after a credential collision.
    const recovering = client('apple-recovery');
    const discardedGuest = (await signInAnonymously(recovering.auth)).user.uid;
    assert.notEqual(discardedGuest, guestUid);
    await assert.rejects(linkWithCredential(recovering.auth.currentUser,
      appleCredential('apple-beta-player', relay)), {code: 'auth/credential-already-in-use'});
    const recovered = await signInWithCredential(recovering.auth, appleCredential('apple-beta-player', relay));
    assert.equal(recovered.user.uid, guestUid);
    assert.equal((await getDoc(doc(recovering.db, 'perfiles_publicos', recovered.user.uid))).data().publicId, '42');
    await assertFails(uploadBytes(ref(recovering.storage,
      `profilePhotos/${discardedGuest}/avatar_${'b'.repeat(64)}.jpg`), new Uint8Array(100), {contentType: 'image/jpeg'}));

    // Provider identity is sufficient even if Apple doesn't return an email on this login.
    const withoutEmail = client('apple-no-email');
    const apple = (await signInWithCredential(withoutEmail.auth, appleCredential('apple-no-email'))).user;
    assert.equal(apple.email, null);
    assert.equal((await getIdTokenResult(apple, true)).claims.firebase.sign_in_provider, 'apple.com');
    await assertRegisteredPermissions(withoutEmail, apple.uid, 'DEF567');
    console.log('Apple Auth emulator: guest linking, private relay, token refresh, existing-account recovery and registered room/Storage permissions passed.');
  } finally {
    await Promise.all(apps.map(deleteApp));
    await env.cleanup();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
