// Read-only by default. --apply creates two isolated QA accounts, exercises the client SDK
// and a clearly marked admin-seeded final-result event, then deletes only its own fixtures.
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const {randomBytes, createHash} = require("node:crypto");
const {initializeApp, deleteApp} = require("firebase/app");
const {getAuth, createUserWithEmailAndPassword, deleteUser, signOut, signInWithEmailAndPassword} = require("firebase/auth");
const {getFirestore, doc, getDocFromServer, getDocsFromServer, collection, setDoc,
  updateDoc, serverTimestamp, terminate} = require("firebase/firestore");
const {getStorage, ref, uploadBytes, getBytes, getMetadata, getDownloadURL} = require("firebase/storage");
const cliAuth = require("firebase-tools/lib/auth");
const cliApi = require("firebase-tools/lib/api");
const {room} = require("../functions/test/fixtures/accountHistoryRoom");

const root = path.resolve(__dirname, "..");
process.chdir(root);
const args = process.argv.slice(2);
const apply = args.includes("--apply");
const photoIndex = args.indexOf("--photo");
if (args.some((v, i) => !["--apply", "--photo"].includes(v) && !(photoIndex >= 0 && i === photoIndex + 1))) {
  throw new Error("Uso: node scripts/test-firebase-production.cjs [--apply --photo archivo.jpg]");
}
const project = "traidores", batch = "release-qa-" + Date.now() + "-" + randomBytes(3).toString("hex");
const clients = [];
let roomCreated = false, adminHeaders, step = "preflight";
const documentBase = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/`;
async function request(url, options = {}) {
  const response = await fetch(url, {...options, headers: {...adminHeaders, ...options.headers},
    signal: AbortSignal.timeout(30000)});
  if (!response.ok) throw Object.assign(new Error("Remote request rejected"), {code: "HTTP_" + response.status});
  return response.status === 204 ? null : response.json();
}
async function eventually(read, accepted) {
  for (let attempt = 0; attempt < 60; attempt++) {
    const value = await read();
    if (accepted(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  throw Object.assign(new Error("Trigger deadline exceeded"), {code: "TRIGGER_TIMEOUT"});
}
function encode(value) {
  if (value === null) return {nullValue: null};
  if (typeof value === "string") return {stringValue: value};
  if (typeof value === "boolean") return {booleanValue: value};
  if (typeof value === "number") return Number.isInteger(value) ? {integerValue: String(value)} : {doubleValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(encode)}};
  return {mapValue: {fields: Object.fromEntries(Object.entries(value).map(([key, child]) => [key, encode(child)]))}};
}
async function main() {
  for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST", "FIREBASE_STORAGE_EMULATOR_HOST",
    "FIREBASE_DATABASE_EMULATOR_HOST", "FUNCTIONS_EMULATOR"]) assert.ok(!process.env[key], "Emulator environment is not allowed");
  const google = JSON.parse(fs.readFileSync("app/google-services.json", "utf8"));
  assert.equal(google.project_info.project_id, project);
  const android = google.client.find(c => c.client_info.android_client_info.package_name === "com.traidores.juego");
  const config = {projectId: project, apiKey: android.api_key[0].current_key,
    appId: android.client_info.mobilesdk_app_id, storageBucket: google.project_info.storage_bucket};
  const account = cliAuth.getProjectDefaultAccount(root);
  cliApi.setScopes(["https://www.googleapis.com/auth/cloud-platform"]);
  const token = await cliAuth.getAccessToken(account.tokens.refresh_token, cliApi.getScopes());
  adminHeaders = {Authorization: "Bearer " + token.access_token, "Content-Type": "application/json"};
  const bucket = await request("https://storage.googleapis.com/storage/v1/b/" + config.storageBucket);
  assert.equal(bucket.projectNumber, String(google.project_info.project_number));
  if (!apply) { console.log("Production preflight OK. No test accounts or data created. Use --apply --photo archivo.jpg to test."); return; }
  assert.ok(photoIndex >= 0 && args[photoIndex + 1], "A JPEG fixture is required before creating accounts");
  const bytes = fs.readFileSync(args[photoIndex + 1]);
  assert.ok(bytes.length <= 256 * 1024 && bytes[0] === 0xff && bytes[1] === 0xd8, "JPEG fixture exceeds the photo contract");
  fs.mkdirSync("output/firebase-release-qa", {recursive: true});
  step = "QA accounts";
  for (let i = 0; i < 2; i++) {
    const app = initializeApp(config, batch + "-" + i), auth = getAuth(app);
    const entry = {app, auth, db: getFirestore(app), storage: getStorage(app),
      email: `${batch}-${i}@example.test`, password: "Qa!" + randomBytes(20).toString("hex")};
    clients.push(entry);
    entry.user = (await createUserWithEmailAndPassword(auth, entry.email, entry.password)).user;
    entry.uid = entry.user.uid;
    fs.writeFileSync("output/firebase-release-qa/production-manifest.json",
      JSON.stringify({project, batch, uids: clients.map(c => c.uid).filter(Boolean)}, null, 2));
    await setDoc(doc(entry.db, "perfiles_publicos", entry.uid), {uidTemporal: entry.uid,
      publicId: String(900000000000 + (Date.now() % 10000000000) * 2 + i),
      nombrePerfil: "QA Firebase " + i, bioPerfil: "Prueba de despliegue", actualizadaEn: serverTimestamp()});
  }
  const [first, second] = clients;
  step = "cross-account photos";
  const hash = createHash("sha256").update(bytes).digest("hex");
  const photoPath = uid => `profilePhotos/${uid}/avatar_${hash}.jpg`;
  const firstPhoto = ref(first.storage, photoPath(first.uid));
  for (const entry of clients) await uploadBytes(ref(entry.storage, photoPath(entry.uid)), bytes, {contentType: "image/jpeg"});
  const url = await getDownloadURL(firstPhoto);
  await updateDoc(doc(first.db, "perfiles_publicos", first.uid), {fotoPerfil: url, actualizadaEn: serverTimestamp()});
  assert.equal((await getDocFromServer(doc(second.db, "perfiles_publicos", first.uid))).data().fotoPerfil, url);
  assert.deepEqual(Buffer.from(await getBytes(ref(second.storage, photoPath(first.uid)))), bytes);
  await assert.rejects(uploadBytes(ref(second.storage, photoPath(first.uid)), bytes, {contentType: "image/jpeg"}),
    error => error.code === "storage/unauthorized");
  step = "local history trigger";
  const localKey = "local:" + batch;
  const localId = "local_" + createHash("sha256").update(localKey).digest("hex");
  const localRef = doc(first.db, "cuentas", first.uid, "historial", localId);
  await setDoc(localRef, {schemaVersion: 1, uid: first.uid, matchKey: localKey, origen: "local", roomId: "", matchId: "",
    fechaLocalMs: Date.now(), mapKey: "pampa", mapName: "Pampa", roleKey: "aldeano", roleName: "Aldeano",
    won: true, participantCount: 5, winner: "Pueblo", finalizadaEn: serverTimestamp()});
  await eventually(() => getDocFromServer(doc(first.db, "cuentas", first.uid)), snap => snap.data()?.partidas === 1);
  await assert.rejects(updateDoc(localRef, {won: false}), error => error.code === "permission-denied");
  await assert.rejects(getDocsFromServer(collection(second.db, "cuentas", first.uid, "historial")),
    error => error.code === "permission-denied");
  step = "admin-seeded online result trigger";
  const final = room();
  final.estado = "finalizada"; final.visibilidad = "privada"; final.origen = "codex-release-qa";
  final.partidaInicial.matchId = batch;
  final.partidaInicial.jugadores.forEach((player, i) => {
    player.uidTemporal = i < 2 ? clients[i].uid : batch + "-sim-" + i;
    if (i >= 2) player.simulado = true;
  });
  final.estadoPartida.jugadores[0] = {...final.estadoPartida.jugadores[0], rolKey: "desertor", rolNombre: "Desertor", rolEquipo: "Neutral", vivo: false};
  final.estadoPartida.jugadores[1] = {...final.estadoPartida.jugadores[1], rolKey: "aldeano", rolNombre: "Aldeano", rolEquipo: "Pueblo"};
  final.estadoPartida.victoriasEspeciales = [];
  await request(documentBase + "partidas/" + batch, {method: "PATCH", body: JSON.stringify({fields: encode(final).mapValue.fields})});
  roomCreated = true;
  await eventually(() => getDocFromServer(doc(first.db, "cuentas", first.uid)), snap => snap.data()?.partidas === 2);
  const result = await eventually(() => getDocFromServer(doc(second.db, "cuentas", second.uid)), snap => snap.data()?.partidas === 1);
  assert.equal(result.data().victorias, 1);
  assert.equal((await getDocFromServer(doc(first.db, "cuentas", first.uid))).data().victorias, 1);
  // A new write of the same final match must not count it again.
  await request(documentBase + "partidas/" + batch + "?updateMask.fieldPaths=qaRepeat", {method: "PATCH",
    body: JSON.stringify({fields: {qaRepeat: {integerValue: "1"}}})});
  step = "account recovery";
  await signOut(first.auth);
  first.user = (await signInWithEmailAndPassword(first.auth, first.email, first.password)).user;
  const records = await getDocsFromServer(collection(first.db, "cuentas", first.uid, "historial"));
  assert.equal(records.size, 2);
  assert.equal(records.docs.find(d => d.data().origen === "online").data().won, false);
  assert.equal((await getDocFromServer(doc(first.db, "perfiles_publicos", first.uid))).data().fotoPerfil, url);
  step = "Auth deletion trigger";
  await deleteUser(first.user); first.user = null;
  await eventually(() => getDocFromServer(doc(second.db, "perfiles_publicos", first.uid)), snap => !snap.exists());
  await eventually(async () => {
    try { await getMetadata(ref(second.storage, photoPath(first.uid))); return false; }
    catch (error) { if (error.code === "storage/object-not-found") return true; throw error; }
  }, removed => removed);
  assert.ok((await getMetadata(ref(second.storage, photoPath(second.uid)))).size > 0);
  console.log("Production SDK checks passed: shared photo, ownership, private history, local/online triggers, recovery, eliminated Desertor, account deletion isolation.");
}
async function cleanup() {
  const failures = [];
  if (roomCreated) {
    try {
    await request(documentBase + "partidas/" + batch, {method: "DELETE"});
    // Wait for the deletion event before deleting only this run's maintenance fixtures.
    await eventually(async () => {
      const response = await fetch(documentBase + "onlineRoomCleanupOrphans/" + batch, {headers: adminHeaders});
      return response.ok;
    }, exists => exists);
    for (const name of ["onlineRoomCleanupOrphans", "onlineRoomCleanupQueue"]) {
      const response = await fetch(documentBase + name + "/" + batch, {method: "DELETE", headers: adminHeaders});
      assert.ok(response.ok || response.status === 404);
    }
    } catch (error) { failures.push(error); }
  }
  for (const entry of clients) {
    try {
      if (entry.user) await deleteUser(entry.user);
      if (entry.uid) await eventually(async () => {
        const response = await fetch(documentBase + "cuentas/" + entry.uid, {headers: adminHeaders});
        if (response.status === 404) return true;
        if (!response.ok) throw Object.assign(new Error("Cleanup verification failed"), {code: "HTTP_" + response.status});
        return false;
      }, removed => removed);
    } catch (error) { failures.push(error); }
    finally { await terminate(entry.db); await deleteApp(entry.app); }
  }
  if (failures.length) throw failures[0];
}
main().catch(error => {console.error(`Production QA failed at ${step}: ${error.code || error.message}`); process.exitCode = 1;})
  .finally(async () => {
    try { await cleanup(); }
    catch (error) {console.error("QA cleanup incomplete; inspect the owned production-manifest.json: " + (error.code || error.message)); process.exitCode = 1;}
  });
