"use strict";
const assert = require("node:assert/strict");
const {isDeepStrictEqual} = require("node:util");
const fs = require("node:fs");
const path = require("node:path");
const {createRequire} = require("node:module");
const {initializeTestEnvironment} = require("@firebase/rules-unit-testing");
const sdk = createRequire(require.resolve("../functions/package.json"));
const {initializeApp, deleteApp} = sdk("firebase-admin/app");
const {getFirestore} = sdk("firebase-admin/firestore");
const {getDatabase} = sdk("firebase-admin/database");
const {scenario} = require("./measure-server-v3.cjs");

// Real client SDK listeners with per-UID emulator Auth and production RTDB rules.
// They validate delivery/isolation, not native mobile caching or billed wire bytes.
function createObservers(env, uids, roomId) {
  const subscriptions = [], values = new Map(), errors = [];
  const stats = Object.fromEntries(["public", "private", "permissions"].map((key) => [key, {deliveries: 0, jsonBytes: 0}]));
  for (const uid of uids) {
    const database = env.authenticatedContext(uid).database();
    for (const kind of ["public", "private", "permissions"]) {
      const key = `${uid}/${kind}`, ref = database.ref(`onlineV3/${roomId}/snapshot/${kind}${kind === "public" ? "" : `/${uid}`}`);
      const listener = (snapshot) => {
        const value = snapshot.val(); values.set(key, value);
        stats[kind].deliveries++; stats[kind].jsonBytes += Buffer.byteLength(JSON.stringify(value));
      };
      ref.on("value", listener, (error) => errors.push(error));
      subscriptions.push({ref, listener, uid, kind, database});
    }
  }
  return {
    stats: () => structuredClone(stats),
    async sync(snapshot) {
      const until = performance.now() + 5000;
      while (performance.now() < until) {
        if (errors.length) throw errors[0];
        if (subscriptions.every(({uid, kind}) => values.has(`${uid}/${kind}`) && isDeepStrictEqual(values.get(`${uid}/${kind}`),
          (kind === "public" ? snapshot.public : snapshot[kind]?.[uid]) ?? null))) return;
        await new Promise((resolve) => setTimeout(resolve, 10));
      }
      assert.fail("Per-UID SDK listeners did not converge to the latest server projections");
    },
    close() {
      for (const {ref, listener} of subscriptions) ref.off("value", listener);
      for (const database of new Set(subscriptions.map((s) => s.database))) database.goOffline();
    },
  };
}

async function main() {
  assert.equal(process.env.GCLOUD_PROJECT, "traidores-local");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:18081");
  assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, "127.0.0.1:19000");
  const env = await initializeTestEnvironment({projectId: "traidores-local",
    database: {host: "127.0.0.1", port: 19000, rules: fs.readFileSync(path.resolve(__dirname, "../database.rules.json"), "utf8")}});
  // RulesTestEnvironment uses projectId itself as its RTDB namespace. Both SDKs
  // must use that same local namespace, including the exact rules it installs.
  const app = initializeApp({projectId: "traidores-local", databaseURL: "https://traidores-local.firebaseio.com"}, "cost-clients");
  const firestore = getFirestore(app), database = getDatabase(app), results = [];
  try {
    for (const count of [5, 10, 15]) {
      const result = await scenario(firestore, database, count, false, {
        createObservers: (uids, roomId) => createObservers(env, uids, roomId)});
      results.push(result);
      console.log(JSON.stringify({players: count, ...result.totals, clientValueCallbacks: result.clientValueCallbacks}));
    }
    const output = path.resolve(__dirname, "../output/server-v3-client-delivery.json");
    fs.mkdirSync(path.dirname(output), {recursive: true});
    fs.writeFileSync(output, JSON.stringify({schemaVersion: 1, environment: "local-client-sdk", results}, null, 2) + "\n");
    console.log(`Report: ${output}`);
  } finally {
    await firestore.doc("onlineMaintenance/serverAuthority").delete();
    await env.cleanup(); await deleteApp(app);
  }
}
main().catch((error) => {console.error(error); process.exitCode = 1;});
