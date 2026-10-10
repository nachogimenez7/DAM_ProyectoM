"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {createServerEndpoints, createDeadlineEnqueuer, gameCallableError} = require("../src/onlineGameFunctions");
const {GameActionError} = require("../src/onlineGameCore");
const logger = {info() {}, warn() {}};
const endpoints = createServerEndpoints({getFirestore: () => {throw new Error("must-not-read-database");},
  getDatabase: () => {throw new Error("must-not-read-database");}, enqueueDeadline: async () => {}, logger});

test("V3 endpoints reserve no idle instances and retain bounded concurrency and a private task queue", () => {
  for (const name of ["iniciarPartidaV3", "accionPartidaV3", "recuperarFaseV3", "prepararRevanchaV3", "abandonarPartidaV3"]) {
    assert.deepEqual(endpoints[name].__endpoint.region, ["southamerica-west1"]);
    assert.equal(endpoints[name].__endpoint.minInstances, 0);
    assert.ok(endpoints[name].__endpoint.maxInstances <= 4);
    assert.ok(endpoints[name].__endpoint.callableTrigger);
  }
  const action = endpoints.accionPartidaV3.__endpoint;
  assert.equal(action.availableMemoryMb, 256); assert.equal(action.cpu, 1); assert.equal(action.concurrency, 20);
  const task = endpoints.resolverFaseV3.__endpoint;
  assert.equal(task.minInstances, 0);
  assert.deepEqual(task.region, ["southamerica-east1"]);
  assert.deepEqual(task.taskQueueTrigger.invoker, ["99323018581-compute@developer.gserviceaccount.com"]);
  assert.equal(task.taskQueueTrigger.retryConfig.maxAttempts, 8);
  assert.equal(task.taskQueueTrigger.rateLimits.maxConcurrentDispatches, 20);
  assert.equal(endpoints.publicarPartidaV3.__endpoint.eventTrigger.retry, true);
});

test("all gameplay callables reject missing Auth before touching storage", async () => {
  for (const name of ["iniciarPartidaV3", "accionPartidaV3", "recuperarFaseV3", "prepararRevanchaV3", "abandonarPartidaV3"]) {
    await assert.rejects(endpoints[name].run({data: {roomId: "room"}}), (e) => e.code === "unauthenticated");
  }
});

test("clients cannot inject identity, roles, deadlines or extra recovery fields", async () => {
  for (const [name, data] of [
    ["iniciarPartidaV3", {roomId: "room", uid: "other"}],
    ["accionPartidaV3", {roomId: "room", role: "asesino"}],
    ["recuperarFaseV3", {roomId: "room", matchId: "match", phaseIndex: 1, deadlineMs: 1}],
    ["recuperarFaseV3", {roomId: "room", matchId: "match", phaseIndex: -1}],
  ]) await assert.rejects(endpoints[name].run({auth: {uid: "real"}, data}), (e) => e.code === "invalid-argument");
});

test("delivery markers, deleted outboxes and unchanged versions do not publish again", async () => {
  const snap = (data) => ({exists: data !== undefined, data: () => data});
  const outbox = {generation: 1, revision: 2};
  await endpoints.publicarPartidaV3.run({params: {roomId: "room"}, data: {
    before: snap({...outbox, deliveredRevision: 0}), after: snap({...outbox, deliveredRevision: 2})}});
  await endpoints.publicarPartidaV3.run({params: {roomId: "room"}, data: {after: snap(undefined)}});
});

test("invalid task payloads cannot reach the engine", async () => {
  for (const token of [{matchId: "match", phaseIndex: 0, deadlineMs: 0},
    {matchId: "match", phaseIndex: 0, deadlineMs: 10, uid: "host"}]) {
    await assert.rejects(endpoints.resolverFaseV3.run({data: {roomId: "room", token}}), (e) => e.code === "invalid-argument");
  }
});

test("the Admin Tasks adapter schedules the immutable deadline with the deterministic ID", async () => {
  let queuePath, sent;
  const enqueue = createDeadlineEnqueuer({taskQueue(path) {
    queuePath = path; return {enqueue: async (data, options) => {sent = {data, options};}};
  }});
  const token = {matchId: "match", phaseIndex: 2, deadlineMs: 123456};
  const scheduleTime = new Date(token.deadlineMs);
  await enqueue({id: "phase-hash", roomId: "room", token, scheduleTime});
  assert.equal(queuePath, "locations/southamerica-east1/functions/resolverFaseV3");
  assert.deepEqual(sent, {data: {roomId: "room", token}, options: {id: "phase-hash", scheduleTime, dispatchDeadlineSeconds: 60}});
});

test("game rejections keep stable reasons and infrastructure failures are retryable without private details", () => {
  assert.equal(gameCallableError(new GameActionError("not-a-member")).code, "permission-denied");
  assert.equal(gameCallableError(new GameActionError("too-many-actions")).code, "resource-exhausted");
  assert.equal(gameCallableError(new GameActionError("phase-closed")).details.reason, "phase-closed");
  const error = gameCallableError(new Error("secret role/token"));
  assert.equal(error.code, "unavailable");
  assert.ok(!error.message.includes("secret"));
});
