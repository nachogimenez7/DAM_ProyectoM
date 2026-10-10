"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {meterFirestore} = require("./measure-server-v3.cjs");

test("counts reads on both transaction attempts and only committed writes", async () => {
  const ref = {path: "partidas/room"};
  const raw = {doc: () => ref, async runTransaction(work) {
    const tx = {get: async () => ({exists: false, data: () => undefined}), set() {}, update() {}};
    // SDK retries after a conflict; only the second attempt commits.
    await work(tx); return work(tx);
  }};
  const {firestore, measure, rows} = meterFirestore(raw);
  await measure("retry", () => firestore.runTransaction(async (tx) => {
    const doc = firestore.doc("partidas/room"); await tx.get(doc); tx.set(doc, {value: 1});
  }));
  assert.equal(rows.retry.reads, 2); assert.equal(rows.retry.writes, 1);
  assert.equal(rows.retry.transactionAttempts, 2); assert.equal(rows.retry.roomWrites, 1);
});

test("aborted transaction does not count queued writes", async () => {
  const ref = {path: "test/doc"};
  const raw = {doc: () => ref, runTransaction: (work) => work({get: async () => ({exists: true}), set() {}})};
  const {firestore, measure, rows} = meterFirestore(raw);
  await assert.rejects(measure("abort", () => firestore.runTransaction(async (tx) => {
    const doc = firestore.doc("test/doc"); await tx.get(doc); tx.set(doc, {}); throw new Error("abort");
  })), /abort/);
  assert.equal(rows.abort.reads, 1); assert.equal(rows.abort.writes, 0);
});

test("empty query counts one read; nonempty query counts documents", async () => {
  let docs = [];
  const raw = {collection: () => ({get: async () => ({docs})})};
  const {firestore, measure, rows} = meterFirestore(raw);
  await measure("queries", () => firestore.collection("test").get());
  docs = [{}, {}, {}];
  await measure("queries", () => firestore.collection("test").get());
  assert.equal(rows.queries.reads, 4);
});

test("concurrent measurement contexts keep their own reads and committed writes", async () => {
  const raw = {doc: (name) => ({path: name, get: async () => {
    await new Promise((resolve) => setTimeout(resolve, name === "a/doc" ? 5 : 1));
    return {exists: true};
  }, set: async () => {}})};
  const {firestore, measure, rows} = meterFirestore(raw);
  await Promise.all([measure("a", async () => { await firestore.doc("a/doc").get(); await firestore.doc("a/doc").set({}); }),
    measure("b", async () => { await firestore.doc("b/doc").get(); await firestore.doc("b/doc").get(); })]);
  assert.equal(rows.a.reads, 1); assert.equal(rows.a.writes, 1);
  assert.equal(rows.b.reads, 2); assert.equal(rows.b.writes, 0);
  assert.equal(rows.a.errors + rows.b.errors, 0);
});
