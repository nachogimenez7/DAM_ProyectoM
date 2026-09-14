"use strict";
const {spawnSync} = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_DATABASE_EMULATOR_HOST"]) {
  if (!/^(127\.0\.0\.1|localhost):\d+$/.test(process.env[key] || "")) throw new Error(`Local emulator required: ${key}`);
}
const jobs = [
  ["firestore-rules", ["scripts/test-firestore-rules.cjs"]],
  ["firestore-guests", ["scripts/test-firestore-rules-invitados.cjs"]],
  ["firestore-votes", ["scripts/test-firestore-votes.cjs"]],
  ["database-rules", ["scripts/test-database-rules.cjs"]],
  ["backend-integration", ["--test", "functions/test/onlineStartService.integration.test.js", "functions/test/onlineRoomCleanup.integration.test.js"]],
  ["online-simulation", ["scripts/simulate-online-firebase.cjs", "--local", "--runs", "10"]],
  ["start-simulation", ["functions/scripts/simulateOnlineStart.js", "--runs", "5"]],
];
fs.mkdirSync("output/online-audit", {recursive: true});
const results = [];
for (const [name, args] of jobs) {
  const started = Date.now();
  const log = path.join("output/online-audit", `${name}.log`);
  const fd = fs.openSync(log, "w");
  const result = spawnSync(process.execPath, args, {stdio: ["ignore", fd, fd], timeout: 600_000});
  fs.closeSync(fd);
  results.push({name, command: `node ${args.join(" ")}`, exitCode: result.status, elapsedMs: Date.now() - started, error: result.error?.message, log});
  console.log(`${name}: ${result.status === 0 ? "PASS" : "FAIL"} (${results.at(-1).elapsedMs}ms) ${log}`);
}
fs.writeFileSync("output/online-audit/results.json", JSON.stringify(results, null, 2));
process.exitCode = results.every((r) => r.exitCode === 0) ? 0 : 1;
