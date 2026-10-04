const fs = require('node:fs');
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {ref, uploadBytes, getMetadata, getBytes, getDownloadURL, deleteObject} = require('firebase/storage');
const {doc, getDoc, setDoc, updateDoc, serverTimestamp} = require('firebase/firestore');

// The bytes are fixtures for transport checks; these rules validate metadata, not image content.
const photoReference = (storage, bytes) => {
  const revision = createHash('sha256').update(bytes).digest('hex');
  return ref(storage, `profilePhotos/owner/avatar_${revision}.jpg`);
};

async function assertDownload(url, expectedBytes) {
  const response = await fetch(url);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('content-type'), 'image/jpeg');
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), expectedBytes);
}

async function assertRemoved(reference, url) {
  await assert.rejects(getMetadata(reference), {code: 'storage/object-not-found'});
  // The emulator can reject the now-missing download token before reporting a missing object.
  assert.ok([403, 404].includes((await fetch(url)).status));
}

async function testPhotoLifecycle(env, owner, other, db) {
  const firstBytes = Buffer.from('first photo transport fixture');
  const nextBytes = Buffer.from('replacement photo transport fixture');
  const first = photoReference(owner, firstBytes);
  const next = photoReference(owner, nextBytes);
  const metadata = {contentType: 'image/jpeg', cacheControl: 'private,max-age=3600'};
  const profileRef = doc(db, 'perfiles_publicos', 'owner');
  const otherDb = env.authenticatedContext('other').firestore();
  const profile = {uidTemporal: 'owner', publicId: '42', nombrePerfil: 'Jugador',
    fotoPerfil: '', actualizadaEn: serverTimestamp()};

  await assertSucceeds(uploadBytes(first, firstBytes, metadata));
  const firstUrl = `${await getDownloadURL(first)}&v=${first.name.slice(7, -4)}`;
  await assertSucceeds(setDoc(profileRef, {...profile, fotoPerfil: firstUrl}));

  // A new session for the same UID recovers the cloud photo without a local gallery file.
  const recoveredDb = env.authenticatedContext('owner', {
    firebase: {sign_in_provider: 'password'}
  }).firestore();
  const recovered = await assertSucceeds(getDoc(doc(recoveredDb, 'perfiles_publicos', 'owner')));
  assert.equal(recovered.data().fotoPerfil, firstUrl);
  await assertDownload(recovered.data().fotoPerfil, firstBytes);
  assert.deepEqual(Buffer.from(await assertSucceeds(getBytes(ref(other, first.fullPath)))), firstBytes);

  // Seed only the room, then use client permissions to publish and read the roster photo.
  const roomId = 'photo_lifecycle';
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'partidas', roomId), {
      estado: 'esperando', hostId: 'owner', hostActivoId: 'owner', limpiezaPendiente: false
    });
  });
  const playerRef = doc(db, 'partidas', roomId, 'jugadores', 'owner');
  await assertSucceeds(setDoc(playerRef, {
    nombre: profile.nombrePerfil, nombrePerfil: profile.nombrePerfil,
    publicId: profile.publicId, uidTemporal: 'owner', estado: 'conectado',
    activoEnPartida: true, esHost: true, fotoPerfil: firstUrl
  }));
  await assertSucceeds(setDoc(doc(otherDb, 'partidas', roomId, 'jugadores', 'other'), {
    nombre: 'Otro jugador', publicId: '43', uidTemporal: 'other', estado: 'conectado',
    activoEnPartida: true, esHost: false, fotoPerfil: ''
  }));
  const rosterRef = doc(otherDb, 'partidas', roomId, 'jugadores', 'owner');
  const roster = await assertSucceeds(getDoc(rosterRef));
  await assertDownload(roster.data().fotoPerfil, firstBytes);
  await assertFails(updateDoc(rosterRef, {fotoPerfil: ''}));

  // Failed publication must leave the previously published URL and object usable.
  await assertSucceeds(uploadBytes(next, nextBytes, metadata));
  await assertFails(updateDoc(profileRef, {fotoPerfil: 'x'.repeat(1001), actualizadaEn: serverTimestamp()}));
  assert.equal((await getDoc(profileRef)).data().fotoPerfil, firstUrl);
  await assertDownload(firstUrl, firstBytes);

  const nextUrl = `${await getDownloadURL(next)}&v=${next.name.slice(7, -4)}`;
  assert.notEqual(nextUrl, firstUrl);
  await assertSucceeds(updateDoc(profileRef, {fotoPerfil: nextUrl, actualizadaEn: serverTimestamp()}));
  await assertSucceeds(updateDoc(playerRef, {fotoPerfil: nextUrl}));
  const updatedRoster = await assertSucceeds(getDoc(rosterRef));
  await assertDownload(updatedRoster.data().fotoPerfil, nextBytes);
  await assertSucceeds(deleteObject(first));
  await assertRemoved(first, firstUrl);

  await assertSucceeds(updateDoc(profileRef, {fotoPerfil: '', actualizadaEn: serverTimestamp()}));
  await assertSucceeds(updateDoc(playerRef, {fotoPerfil: ''}));
  await assertSucceeds(deleteObject(next));
  assert.equal((await getDoc(doc(recoveredDb, 'perfiles_publicos', 'owner'))).data().fotoPerfil, '');
  assert.equal((await getDoc(rosterRef)).data().fotoPerfil, '');
  await assertRemoved(next, nextUrl);
  console.log('Photo lifecycle: upload, download, account recovery, roster, failed publication, replacement and removal passed.');
}
(async () => {
  const env = await initializeTestEnvironment({projectId: 'demo-traidores-photos', storage: {
    host: '127.0.0.1', port: 9199, rules: fs.readFileSync('storage.rules', 'utf8')
  }, firestore: {host: '127.0.0.1', port: 8081, rules: fs.readFileSync('firestore.rules', 'utf8')}});
  try {
    const owner = env.authenticatedContext('owner', {firebase: {sign_in_provider: 'password'}}).storage();
    const other = env.authenticatedContext('other', {firebase: {sign_in_provider: 'password'}}).storage();
    const guest = env.authenticatedContext('guest', {firebase: {sign_in_provider: 'anonymous'}}).storage();
    const filename = `avatar_${'a'.repeat(64)}.jpg`;
    const path = `profilePhotos/owner/${filename}`;
    const jpeg = {contentType: 'image/jpeg'};
    await assertSucceeds(uploadBytes(ref(owner, path), new Uint8Array(100), jpeg));
    await assertSucceeds(getMetadata(ref(other, path)));
    await assertFails(getMetadata(ref(env.unauthenticatedContext().storage(), path)));
    await assertFails(uploadBytes(ref(other, path), new Uint8Array(100), jpeg));
    await assertFails(uploadBytes(ref(guest, `profilePhotos/guest/${filename}`), new Uint8Array(100), jpeg));
    await assertFails(uploadBytes(ref(owner, path), new Uint8Array(256 * 1024 + 1), jpeg));
    await assertFails(uploadBytes(ref(owner, path), new Uint8Array(100), {contentType: 'text/html'}));
    await assertFails(uploadBytes(ref(owner, 'unrestricted/file.jpg'), new Uint8Array(100), jpeg));
    await assertFails(deleteObject(ref(other, path)));
    await assertSucceeds(deleteObject(ref(owner, path)));
    const profile = {uidTemporal: 'owner', publicId: '42', nombrePerfil: 'Jugador',
      fotoPerfil: 'https://firebasestorage.googleapis.com/v0/b/demo/o/profile.jpg?v=2',
      actualizadaEn: serverTimestamp()};
    const db = env.authenticatedContext('owner').firestore();
    await assertSucceeds(setDoc(doc(db, 'perfiles_publicos', 'owner'), profile));
    await assertFails(setDoc(doc(env.authenticatedContext('other').firestore(), 'perfiles_publicos', 'owner'), profile));
    await assertFails(setDoc(doc(db, 'perfiles_publicos', 'owner'), {...profile, fotoPerfil: 'x'.repeat(1001)}));
    console.log('Storage rules: ownership, authenticated reads, guest rejection, size/type and deletion passed.');
    await testPhotoLifecycle(env, owner, other, db);
  } finally { await env.cleanup(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
