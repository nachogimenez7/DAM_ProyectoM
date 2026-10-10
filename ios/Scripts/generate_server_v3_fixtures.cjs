#!/usr/bin/env node
// Builds TraidoresCore fixtures from the production server engine (functions/src/onlineGameCore.js),
// shaped as Realtime Database delivers them (no nulls, no empty arrays). Rerun after the
// server contract changes: node ios/Scripts/generate_server_v3_fixtures.cjs
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const root = path.join(__dirname, "..", "..");
const {prepareOnlineMatch} = require(path.join(root, "functions/src/onlineStartCore"));
const core = require(path.join(root, "functions/src/onlineGameCore"));
const out = path.join(root, "ios/Packages/TraidoresCore/Tests/TraidoresCoreTests/Fixtures/server-v3");
const NOW = 1_800_000_000_000;

// Same shape as onlineGameService.realtimeShape: RTDB drops nulls and empty containers.
function realtimeShape(value) {
  if (Array.isArray(value)) return value.length ? value.map(realtimeShape) : null;
  if (value && typeof value === "object") {
    const result = Object.fromEntries(Object.entries(value).map(([k, v]) => [k, realtimeShape(v)]).filter(([, v]) => v !== null));
    return Object.keys(result).length ? result : null;
  }
  return value === undefined ? null : value;
}

function game(count, map) {
  const room = {hostId: "p0", estado: "esperando", jugadoresEsperados: count, codigoSala: "ABCDE", mapa: map,
    configLobby: {presetRoles: "RECOMMENDED", votacionSeg: 20}};
  const prepared = prepareOnlineMatch({requesterId: "p0", room, matchId: "match-1", nowMs: NOW, randomInt: () => 0,
    players: Array.from({length: count}, (_, i) => ({id: `p${i}`, order: i, name: `J${i}`, activeInMatch: true, ready: true,
      publicId: `${i + 1}`}))});
  return core.createServerGame({roomId: "room", prepared, nowMs: NOW});
}
const role = (s, key) => s.players.find((p) => p.role.key === key);
let seq = 0;
function act(s, uid, action, fields = {}) {
  return core.acceptAction(s, uid, {matchId: s.matchId, phaseIndex: s.phaseIndex, requestId: `fixture_${++seq}_req`,
    action, ...fields}, s.phaseStartedAtMs + 1).state;
}
const close = (s) => core.expirePhase(s, core.deadlineToken(s), s.deadlineMs, {chooseRandomInt: () => 0}).state;

function write(name, state, uids) {
  const projection = realtimeShape(core.projectServerGame(state));
  const fixture = {public: projection.public, private: {}, permissions: {}};
  for (const uid of uids) {
    fixture.private[uid] = {...projection.private[uid], revision: 1};
    fixture.permissions[uid] = projection.permissions[uid];
  }
  fs.writeFileSync(path.join(out, `${name}.json`), JSON.stringify(fixture, null, 1) + "\n");
}

fs.mkdirSync(out, {recursive: true});
let s = game(8, "pampa");
const assassin = role(s, "asesino"), merc = role(s, "mercenario"), medic = role(s, "medico"), villager = role(s, "aldeano");
write("reparto", s, [assassin.uid, villager.uid]);
s = close(s); // NOCHE
s = act(s, assassin.uid, "matar", {targetUid: villager.uid});
s = act(s, merc.uid, "silenciar", {targetUid: medic.uid});
write("noche", s, [assassin.uid, merc.uid, villager.uid, medic.uid]);
s = close(s); // AMANECER
s = close(s); // DIA_DEBATE
write("debate", s, [assassin.uid, medic.uid, villager.uid]);
s = close(s); // VOTACION
write("votacion", s, [assassin.uid, medic.uid]);
// End: everybody but the assassin and one town player leaves → traitor victory, no deserter.
for (const p of s.players) if (p.uid !== assassin.uid && p.uid !== medic.uid && p.alive) s = core.leaveServerGame(s, p.uid, s.phaseStartedAtMs + 2).state;
write("final", s, [assassin.uid, medic.uid]);
console.log(`fixtures in ${path.relative(root, out)}: phase ${s.phase}, winner ${s.winner}`);
