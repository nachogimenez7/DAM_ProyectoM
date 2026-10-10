"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {repairOverdueServerGames, createServerRecoveryFunction} = require("../src/onlineGameRecovery");
const indexes = require("../../firestore.indexes.json");

test("scheduled recovery has one instance, no idle instance and bounded concurrency", () => {
  const job = createServerRecoveryFunction({getFirestore() {}, getDatabase() {}, logger: {}, enqueueDeadline() {}}).__endpoint;
  assert.deepEqual(job.region, ["southamerica-east1"]);
  assert.equal(job.minInstances, 0); assert.equal(job.maxInstances, 1); assert.equal(job.concurrency, 1);
  assert.equal(job.timeoutSeconds, 60); assert.equal(job.scheduleTrigger.schedule, "every 15 minutes");
});

test("private state/projection indexing is disabled while the recovery query has its group index", () => {
  for (const collectionGroup of ["servidor", "serverOutbox"]) assert.deepEqual(indexes.fieldOverrides
    .find((field) => field.collectionGroup === collectionGroup && field.fieldPath === "*").indexes, []);
  assert.deepEqual(indexes.fieldOverrides.find((field) => field.collectionGroup === "serverOutbox" &&
    field.fieldPath === "recoveryAtMs").indexes, [{order: "ASCENDING", queryScope: "COLLECTION_GROUP"}]);
});

test("invalid recovery bounds fail before reading Firestore", async () => {
  const firestore = {doc() {throw new Error("must-not-read");}};
  for (const options of [{nowMs: -1}, {nowMs: 1, pageSize: 51}, {nowMs: 1, maxPages: 5}]) {
    await assert.rejects(repairOverdueServerGames({firestore, ...options}), /invalid-recovery-options/);
  }
});
