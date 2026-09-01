"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {iniciarPartidaV2} = require("../src/index");

test("la callable se publica en la region cercana configurada", () => {
  assert.deepEqual(iniciarPartidaV2.__endpoint.region, ["southamerica-west1"]);
  assert.equal(iniciarPartidaV2.__endpoint.platform, "gcfv2");
  assert.equal(iniciarPartidaV2.__endpoint.maxInstances, 10);
});

test("la callable rechaza solicitudes sin Firebase Auth", async () => {
  await assert.rejects(
    iniciarPartidaV2.run({auth: null, data: {roomId: "ABC234"}}),
    (error) => error.code === "unauthenticated",
  );
});
