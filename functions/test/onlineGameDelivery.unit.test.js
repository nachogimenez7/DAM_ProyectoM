"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {readServerDelivery} = require("../src/onlineGameDelivery");

const delivery = {matchId: "match", generation: 2, revision: 5, queuedTaskId: "phase-key"};
function dependencies(url = "https://example-default-rtdb.firebaseio.com/onlineV3/room/snapshot/delivery") {
  return {database: {app: {options: {databaseURL: "https://example-default-rtdb.firebaseio.com",
    credential: {getAccessToken: async () => ({access_token: "unit-test-only"})}}}},
  realtime: {child: () => ({toString: () => url})}};
}
async function withEnvironment(emulator, work) {
  const before = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
  if (emulator) process.env.FIREBASE_DATABASE_EMULATOR_HOST = emulator;
  else delete process.env.FIREBASE_DATABASE_EMULATOR_HOST;
  try { await work(); } finally {
    if (before === undefined) delete process.env.FIREBASE_DATABASE_EMULATOR_HOST;
    else process.env.FIREBASE_DATABASE_EMULATOR_HOST = before;
  }
}

test("delivery uses server REST, scoped origin and an authorization header with no credential in the URL", () =>
  withEnvironment(null, async () => {
    const result = await readServerDelivery({...dependencies(), fetchImpl: async (url, options) => {
      assert.equal(url.href, "https://example-default-rtdb.firebaseio.com/onlineV3/room/snapshot/delivery.json");
      assert.equal(options.headers.Authorization, "Bearer unit-test-only");
      assert.equal(options.redirect, "error"); assert.ok(options.signal instanceof AbortSignal);
      return {ok: true, json: async () => delivery};
    }});
    assert.deepEqual(result, delivery);
  }));
test("regional RTDB origin and absent legacy checkpoint are supported", () => withEnvironment(null, async () => {
  assert.equal(await readServerDelivery({...dependencies("https://example.europe-west1.firebasedatabase.app/onlineV3/room/snapshot/delivery"),
    fetchImpl: async () => ({ok: true, json: async () => null})}), null);
}));
test("emulator REST includes the real namespace and never requests production credentials", () =>
  withEnvironment("127.0.0.1:19000", async () => {
    const args = dependencies("http://127.0.0.1:19000/onlineV3/room/snapshot/delivery");
    args.database.app.options.credential.getAccessToken = () => {throw new Error("must-not-authenticate");};
    await readServerDelivery({...args, fetchImpl: async (url, options) => {
      assert.equal(url.searchParams.get("ns"), "example-default-rtdb");
      assert.equal(options.headers.Authorization, "Bearer owner");
      return {ok: true, json: async () => delivery};
    }});
  }));
test("delivery refuses foreign origins and mismatched emulator hosts before requesting anything", async () => {
  const fetchImpl = () => {throw new Error("must-not-fetch");};
  await withEnvironment(null, async () => {
    for (const origin of ["http://example.firebaseio.com", "https://example.com", "https://firebaseio.com.evil.test"]) {
      await assert.rejects(readServerDelivery({...dependencies(origin), fetchImpl}), /invalid-delivery-origin/);
    }
  });
  await withEnvironment("127.0.0.1:19000", () =>
    assert.rejects(readServerDelivery({...dependencies(), fetchImpl}), /delivery-emulator-mismatch/));
});
test("unavailable or malformed server reads do not become a delivery acknowledgement", () => withEnvironment(null, async () => {
  await assert.rejects(readServerDelivery({...dependencies(), fetchImpl: async () => ({ok: false})}),
    e => e.code === "delivery-check-unavailable");
  for (const value of [[], {generation: 2, revision: 5}, {...delivery, revision: 0},
    {...delivery, generation: 1.5}, {...delivery, queuedTaskId: 7}]) {
    await assert.rejects(readServerDelivery({...dependencies(), fetchImpl: async () => ({ok: true, json: async () => value})}),
      /invalid-delivery-checkpoint/);
  }
}));
