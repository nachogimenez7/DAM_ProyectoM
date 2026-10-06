"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {prepareOnlineMatch} = require("../src/onlineStartCore");
const {createServerGame, acceptAction, leaveServerGame, expirePhase, deadlineToken, projectServerGame, winnerFor,
  stableNoise, resolveKillVote, MAX_ACTIONS_PER_PHASE} = require("../src/onlineGameCore");
const {recordsForFinishedRoom} = require("../src/accountHistoryService");
let sequence = 0;
const NOW = 1000000;
function fixture(count = 14, map = "pampa") {
  const room = {hostId: "p0", estado: "esperando", jugadoresEsperados: count, codigoSala: "ABCDE",
    mapa: map, configLobby: {presetRoles: "RECOMMENDED", votacionSeg: 30}};
  const prepared = prepareOnlineMatch({requesterId: "p0", room,
    players: Array.from({length: count}, (_, i) => ({id: `p${i}`, order: i, name: `J${i}`,
      activeInMatch: true, ready: true, publicId: `${i + 1}`})),
    matchId: "real-match", nowMs: NOW, randomInt: () => 0});
  return {state: createServerGame({roomId: "room", prepared, nowMs: NOW}), prepared};
}
function role(state, key) { return state.players.find((p) => p.role.key === key); }
function phase(state, value, extra = {}) {
  state.phase = value; state.phaseIndex++; state.actions = {}; state.phaseActionCounts = {};
  state.deadlineMs = NOW + 40000; Object.assign(state, extra); return state;
}
function act(state, uid, action, fields = {}, now = NOW + 1) {
  return acceptAction(state, uid, {matchId: state.matchId, phaseIndex: state.phaseIndex,
    requestId: `request_${++sequence}`, action, ...fields}, now).state;
}
function close(state) { return expirePhase(state, deadlineToken(state), state.deadlineMs).state; }
function rejects(state, uid, action, fields, code) {
  const before = structuredClone(state);
  assert.throws(() => act(state, uid, action, fields), (e) => e.code === code);
  assert.deepEqual(state, before);
}
function deserterFinal() {
  const {state} = fixture();
  const keep = new Set(["desertor", "mercenario", "asesino"]);
  let towns = 0;
  state.players.forEach((p) => { p.alive = keep.has(p.role.key) || (p.role.key === "aldeano" && towns++ < 2); });
  state.deserterTeam = "Pueblo";
  return phase(state, "AMANECER", {round: 4});
}

test("server start resets every power, AFK and cooldown without leaking roles publicly", () => {
  const {state} = fixture(15);
  assert.equal(state.authorityMode, "server");
  assert.ok(state.players.every((p) => p.nightAfk === 0 && p.voteAfk === 0 && p.lastSilencedRound === null));
  assert.equal(state.deserterUsed, false); assert.equal(state.payadorUsed, false); assert.equal(state.oracleUsed, false);
  const projection = projectServerGame(state);
  assert.ok(projection.public.jugadores.every((p) => !p.rolKey));
  const host = state.players[0];
  const roles = projection.private[host.uid].rolesVisibles;
  const expected = state.players.filter((p) => p.uid === host.uid ||
    (host.role.team === "Traidores" && p.role.team === "Traidores"));
  assert.deepEqual(roles.map((p) => p.orden), expected.map((p) => p.order));
  assert.ok(!JSON.stringify(projection.public).includes("investigaciones"));
});

test("action authentication, phase/deadline and payload validation reject without mutation", () => {
  const {state} = fixture(8); phase(state, "NOCHE");
  const assassin = role(state, "asesino"), target = role(state, "aldeano");
  rejects(state, "intruder", "matar", {targetUid: target.uid}, "not-a-member");
  rejects(state, target.uid, "matar", {targetUid: assassin.uid}, "role-not-allowed");
  rejects(state, assassin.uid, "matar", {targetUid: assassin.uid}, "invalid-target");
  rejects(state, assassin.uid, "matar", {targetUid: "missing"}, "invalid-target");
  rejects(state, assassin.uid, "matar", {targetUid: target.uid, actorUid: target.uid}, "invalid-action");
  rejects(state, assassin.uid, "matar", {targetUid: target.uid, phaseIndex: 99}, "stale-phase");
  rejects(state, assassin.uid, "matar", {targetUid: target.uid, matchId: "previous"}, "stale-match");
  assert.throws(() => act(state, assassin.uid, "matar", {targetUid: target.uid}, state.deadlineMs), (e) => e.code === "phase-closed");
});

test("identical request retries are idempotent even after advancement and conflicting reuse fails", () => {
  let {state} = fixture(8); phase(state, "NOCHE");
  const killer = role(state, "asesino"), target = role(state, "aldeano");
  const input = {matchId: state.matchId, phaseIndex: state.phaseIndex, requestId: "unique_request", action: "matar", targetUid: target.uid};
  const accepted = acceptAction(state, killer.uid, input, NOW + 1);
  assert.deepEqual(acceptAction(accepted.state, killer.uid, input, NOW + 2), {...accepted, changed: false});
  assert.throws(() => acceptAction(accepted.state, killer.uid, {...input, targetUid: role(state, "medico").uid}, NOW + 2),
    (e) => e.code === "request-id-reused");
  state = close(accepted.state);
  assert.equal(acceptAction(state, killer.uid, input, state.phaseStartedAtMs + 1).changed, false);
});

test("bounded action count and fixed deadline survive changing votes", () => {
  let {state} = fixture(8); phase(state, "VOTACION");
  const [actor, first, second] = state.players;
  const token = deadlineToken(state);
  for (let i = 0; i < MAX_ACTIONS_PER_PHASE; i++) state = act(state, actor.uid, "votar", {targetUid: (i % 2 ? first : second).uid});
  rejects(state, actor.uid, "votar", {targetUid: first.uid}, "too-many-actions");
  assert.deepEqual(deadlineToken(state), token);
  assert.equal(expirePhase(state, token, token.deadlineMs).changed, true);
});

test("Mercenary blocks consecutive silence, allows it after a gap and sends cooldown privately", () => {
  let {state} = fixture(8); phase(state, "NOCHE");
  const mercenary = role(state, "mercenario"), target = role(state, "aldeano");
  state = act(state, mercenary.uid, "silenciar", {targetUid: target.uid}); state = close(state);
  assert.equal(playerAt(state, target.uid).lastSilencedRound, 1);
  assert.equal(playerAt(state, target.uid).muted, true);
  phase(state, "NOCHE", {round: 2});
  rejects(state, mercenary.uid, "silenciar", {targetUid: target.uid}, "silence-cooldown-or-target");
  assert.ok(projectServerGame(state).private[mercenary.uid].objetivosBloqueados.includes(target.uid));
  assert.ok(!("ultimaRondaSilenciado" in projectServerGame(state).public.jugadores[target.order]));
  phase(state, "NOCHE", {round: 3});
  assert.equal(act(state, mercenary.uid, "silenciar", {targetUid: target.uid}).actions[`${mercenary.order}:night`].targetUid, target.uid);
});
function playerAt(state, uid) { return state.players.find((p) => p.uid === uid); }

test("protected or murdered silence target does not consume cooldown", () => {
  for (const killed of [false, true]) {
    let {state} = fixture(8); phase(state, "NOCHE");
    const target = role(state, "aldeano");
    state = act(state, role(state, "mercenario").uid, "silenciar", {targetUid: target.uid});
    state = act(state, role(state, killed ? "asesino" : "medico").uid, killed ? "matar" : "salvar", {targetUid: target.uid});
    state = close(state);
    assert.equal(playerAt(state, target.uid).lastSilencedRound, null);
    assert.equal(playerAt(state, target.uid).muted, false);
  }
});

test("muted Mayor cannot reveal, vote, decide or protect himself in second tie", () => {
  for (const revealed of [false, true]) for (const inTie of [false, true]) {
    let {state} = fixture(8);
    const mayor = role(state, "alcalde"), target = role(state, "aldeano"), other = role(state, "medico");
    mayor.muted = true; state.mayorUid = revealed ? mayor.uid : null;
    phase(state, "DIA_DEBATE");
    assert.equal(projectServerGame(state).permissions[mayor.uid].publicChat, false);
    rejects(state, mayor.uid, "revelar_alcalde", {}, "mayor-unavailable");
    phase(state, "VOTACION"); rejects(state, mayor.uid, "votar", {targetUid: target.uid}, "voter-unavailable");
    phase(state, "ALCALDE_DESEMPATE", {tieCandidates: [target.uid, other.uid]});
    rejects(state, mayor.uid, "decidir_empate", {targetUid: target.uid}, "mayor-unavailable");
    phase(state, "RECUENTO_VOTOS", {voteRound: 2, tieCandidates: [inTie ? mayor.uid : other.uid, target.uid]});
    state = close(state);
    assert.equal(state.phase, revealed ? "RESULTADO" : "ALCALDE_DESEMPATE"); assert.equal(state.eliminationUid, null);
    if (!revealed) {
      assert.equal(state.deadlineMs - state.phaseStartedAtMs, state.timing.votingSeconds * 1000);
      state = close(state); assert.equal(state.phase, "RESULTADO"); assert.equal(state.eliminationUid, null);
    }
    assert.equal(state.mayorUid, revealed ? mayor.uid : null);
  }
});

test("revealed Mayor doubles vote and corruption with three candidates excludes himself", () => {
  let {state} = fixture(8); const mayor = role(state, "alcalde"), target = role(state, "aldeano"), other = role(state, "medico");
  phase(state, "VOTACION");
  state = act(state, mayor.uid, "revelar_alcalde");
  state = act(state, mayor.uid, "votar", {targetUid: target.uid});
  state = act(state, other.uid, "votar", {targetUid: mayor.uid});
  state = close(state); assert.equal(state.voteTotals[target.order], 2);
  phase(state, "RECUENTO_VOTOS", {voteRound: 2, tieCandidates: [mayor.uid, target.uid, other.uid], eliminationUid: null});
  state = close(state); assert.equal(state.phase, "ALCALDE_DESEMPATE");
  assert.deepEqual(state.tieCandidates, [target.uid, other.uid]);
  rejects(state, mayor.uid, "decidir_empate", {targetUid: mayor.uid}, "mayor-unavailable");
  state = act(state, mayor.uid, "decidir_empate", {targetUid: target.uid});
  assert.equal(state.voteRound, 4); assert.equal(state.eliminationUid, target.uid);
});

test("Oracle invitation survives Oracle murder and expires before voting", () => {
  let {state} = fixture(14, "grecia"); phase(state, "NOCHE", {round: 2});
  const oracle = role(state, "oraculo"), guest = role(state, "aldeano"); guest.alive = false;
  state = act(state, oracle.uid, "invitar_muerto", {targetUid: guest.uid});
  state = act(state, role(state, "asesino").uid, "matar", {targetUid: oracle.uid});
  state = close(state); assert.equal(playerAt(state, oracle.uid).alive, false);
    assert.equal(state.oracleGuestUid, guest.uid); assert.equal(state.oracleUsed, true);
  assert.ok(state.events.some((e) => e.codigo === "ORACLE_INVITATION" && e.jugadores.includes(guest.uid)));
  state = close(state); assert.equal(state.phase, "DIA_DEBATE");
  assert.equal(projectServerGame(state).permissions[guest.uid].publicChat, true);
  rejects(state, guest.uid, "revelar_alcalde", {}, "role-not-allowed");
  state = close(state); assert.equal(state.oracleGuestUid, null);
  rejects(state, guest.uid, "votar", {targetUid: oracle.uid}, "voter-unavailable");
});

test("hidden Mayor incapacity keeps the identical tie window; only public incapacity skips it", () => {
  for (const dead of [false, true]) for (const revealed of [false, true]) {
    let {state} = fixture(8);
    const mayor = role(state, "alcalde"), target = role(state, "aldeano"), other = role(state, "medico");
    mayor.alive = !dead; mayor.muted = !dead;
    state.config.revelarRolesAlMorir = false;
    state.mayorUid = revealed ? mayor.uid : null;
    phase(state, "RECUENTO_VOTOS", {voteRound: 2, tieCandidates: [target.uid, other.uid]});
    state = close(state);
    assert.equal(state.phase, revealed ? "RESULTADO" : "ALCALDE_DESEMPATE");
    if (!revealed) {
      assert.ok(state.events.at(-1).texto.includes("puede decidir"));
      state = close(state); assert.equal(state.eliminationUid, null);
    }
  }
});

test("public revision and events do not count secret night intentions", () => {
  let {state} = fixture(8); phase(state, "NOCHE");
  const original = projectServerGame(state).public;
  const doctor = role(state, "medico"), killer = role(state, "asesino"), target = role(state, "aldeano");
  state = act(state, doctor.uid, "salvar", {targetUid: doctor.uid});
  const accepted = acceptAction(state, killer.uid, {matchId: state.matchId, phaseIndex: state.phaseIndex,
    requestId: "secret_receipt", action: "matar", targetUid: target.uid}, NOW + 1);
  state = accepted.state;
  assert.equal(state.revision, 3);
  assert.equal(accepted.receipt.revision, original.revision); // No global secret counter in the callable either.
  assert.deepEqual(projectServerGame(state).public, original);
  state = close(state);
  assert.equal(projectServerGame(state).public.revision, original.revision + 1);
  assert.ok(!Object.hasOwn(projectServerGame(state).private[doctor.uid], "revision"));
});

test("leaving during play is eliminated, never gets a team or special victory, and duplicate leave is harmless", () => {
  let {state, prepared} = fixture(8, "medieval"); phase(state, "DIA_DEBATE");
  const jester = role(state, "bufon"); state.specialVictories.push({uid: jester.uid, round: 1, reason: "bufon_expulsado"});
  state = leaveServerGame(state, jester.uid, NOW + 1).state;
  assert.equal(role(state, "bufon").deathCause, "ABANDONO");
  assert.equal(leaveServerGame(state, jester.uid, NOW + 2).changed, false);
  state.winner = "Pueblo"; state.phase = "FINALIZADA"; state.deadlineMs = null;
  const projection = projectServerGame(state);
  const records = recordsForFinishedRoom("room", {partidaInicial: prepared.payloads.initialMatch,
    estadoPartida: projection.public}, NOW + 3);
  assert.equal(records.find((r) => r.uid === jester.uid).won, false);
  assert.equal(projection.permissions[jester.uid].member, false);
  assert.equal(projection.private[jester.uid], undefined);
});

test("Oracle cannot invite a departed member and a pending invitation never restores their access", () => {
  let {state} = fixture(14, "grecia"); phase(state, "NOCHE", {round: 2});
  const oracle = role(state, "oraculo"), guest = role(state, "aldeano");
  guest.alive = false;
  state = act(state, oracle.uid, "invitar_muerto", {targetUid: guest.uid});
  state = leaveServerGame(state, guest.uid, NOW + 2).state;
  state = close(state);
  assert.equal(state.oracleUsed, true); assert.equal(state.oracleGuestUid, null);
  assert.equal(projectServerGame(state).permissions[guest.uid].member, false);
  assert.ok(!state.events.some((e) => e.codigo === "ORACLE_INVITATION"));
  phase(state, "NOCHE"); state.oracleUsed = false;
  rejects(state, oracle.uid, "invitar_muerto", {targetUid: guest.uid}, "invalid-oracle-target");
});

test("Oracle guardar poder counts for AFK; ineligible first-night Oracle does not", () => {
  let {state} = fixture(14, "grecia"); phase(state, "NOCHE", {round: 2});
  const oracle = role(state, "oraculo"); role(state, "aldeano").alive = false; oracle.nightAfk = 1;
  state = act(state, oracle.uid, "guardar_poder"); state = close(state);
  assert.equal(playerAt(state, oracle.uid).nightAfk, 0); assert.equal(state.oracleUsed, false);
  const first = fixture(8, "grecia").state; phase(first, "NOCHE"); const firstOracle = role(first, "oraculo"); firstOracle.nightAfk = 1;
  role(first, "aldeano").alive = false;
  rejects(first, firstOracle.uid, "guardar_poder", {}, "oracle-unavailable");
  assert.equal(role(close(first), "oraculo").nightAfk, 1);
});

test("server Desertor final window works independently of host, choice and timeout finish immediately", () => {
  for (const choice of ["Pueblo", "Traidores", "mantener", null]) {
    let state = deserterFinal(); phase(state, "RESULTADO");
    const desertor = role(state, "desertor");
    assert.equal(winnerFor(state), "Traidores");
    state = close(state); assert.equal(state.phase, "DESERTOR_RECONSIDERACION");
    assert.equal(state.winner, null); assert.equal(state.deadlineMs - state.phaseStartedAtMs, 30000);
    assert.equal(state.players.filter((p) => p.alive).length, 6);
    rejects(state, role(state, "asesino").uid, "desertor_rethink", {team: "Traidores"}, "role-not-allowed");
    state = choice ? act(state, desertor.uid, "desertor_rethink", {team: choice}, state.phaseStartedAtMs + 1) : close(state);
    assert.equal(state.phase, "FINALIZADA"); assert.equal(state.winner, "Traidores"); assert.equal(state.deserterUsed, true);
    assert.equal(state.deserterTeam, choice === "Traidores" ? "Traidores" : "Pueblo");
    assert.equal(role(state, "desertor").voteAfk, 0); assert.equal(role(state, "desertor").nightAfk, 0);
  }
});

test("Desertor exclusions: town victory, dead, already used, absent choice, before round four", () => {
  for (const scenario of ["town", "dead", "used", "unchosen", "early-round"]) {
    let state = deserterFinal(); phase(state, "RESULTADO");
    if (scenario === "town") state.players.filter((p) => ["asesino", "espia"].includes(p.role.key)).forEach((p) => { p.alive = false; });
    if (scenario === "dead") role(state, "desertor").alive = false;
    if (scenario === "used") state.deserterUsed = true;
    if (scenario === "unchosen") state.deserterTeam = null;
    if (scenario === "early-round") state.round = 3;
    state = close(state); assert.notEqual(state.phase, "DESERTOR_RECONSIDERACION");
    if (scenario === "town") assert.equal(state.winner, "Pueblo");
    if (["dead", "used", "early-round"].includes(scenario)) assert.equal(state.winner, "Traidores");
  }
});

test("silenced Desertor may keep or change once from round four, independent of survivors", () => {
  for (const team of ["Pueblo", "Traidores", "mantener"]) {
    let state = fixture(15).state;
    phase(state, "DIA_DEBATE", {round: 3, deserterTeam: "Pueblo"});
    const deserter = role(state, "desertor"); deserter.muted = true;
    rejects(state, deserter.uid, "desertor_rethink", {team}, "reconsideration-unavailable");
    state.round = 4;
    state = act(state, deserter.uid, "desertor_rethink", {team});
    assert.equal(state.deserterUsed, true);
    assert.equal(state.deserterTeam, team === "Traidores" ? "Traidores" : "Pueblo");
    rejects(state, deserter.uid, "desertor_rethink", {team}, "reconsideration-unavailable");
  }
});

test("Desertor round-two fallback is stable and never changes an already selected side", () => {
  let state = fixture().state; phase(state, "RESULTADO", {round: 1});
  const first = close(state), second = close(state);
  assert.equal(first.round, 2); assert.ok(["Pueblo", "Traidores"].includes(first.deserterTeam));
  assert.equal(first.deserterTeam, second.deserterTeam);
  state.deserterTeam = "Traidores"; assert.equal(close(state).deserterTeam, "Traidores");
});

test("AFK expels only required living actors on second miss and cancels only an entirely absent table", () => {
  let state = fixture(5).state; phase(state, "NOCHE");
  const doctor = role(state, "medico"), villager = role(state, "aldeano");
  doctor.nightAfk = 1; state = close(state);
  assert.equal(playerAt(state, doctor.uid).deathCause, "AFK"); assert.equal(playerAt(state, villager.uid).nightAfk, 0);
  state = fixture(5).state; phase(state, "VOTACION");
  state.players.forEach((p) => { p.voteAfk = 1; });
  state = close(state); assert.equal(state.winner, "Cancelada"); assert.deepEqual(state.specialVictories, []);
  assert.ok(state.players.every((p) => p.alive));
  const tied = fixture(8).state; phase(tied, "DESEMPATE_VOTACION"); tied.players.forEach((p) => { p.voteAfk = 1; });
  assert.ok(close(tied).players.every((p) => p.alive && p.voteAfk === 1));
});

test("Jester wins only through a vote and server final matches history contract", () => {
  let {state, prepared} = fixture(8, "medieval"); phase(state, "RESULTADO");
  const jester = role(state, "bufon"); state.eliminationUid = jester.uid;
  state = close(state); assert.equal(state.specialVictories.length, 1);
  state.players.filter((p) => ["asesino", "espia"].includes(p.role.key)).forEach((p) => { p.alive = false; });
  phase(state, "RESULTADO"); state = close(state);
  const records = recordsForFinishedRoom("room", {partidaInicial: prepared.payloads.initialMatch,
    estadoPartida: projectServerGame(state).public}, state.phaseStartedAtMs);
  assert.equal(records.length, 8); assert.equal(records.find((r) => r.uid === jester.uid).won, true);
  const cancelled = structuredClone(state); cancelled.winner = "Cancelada";
  assert.equal(recordsForFinishedRoom("room", {partidaInicial: prepared.payloads.initialMatch,
    estadoPartida: projectServerGame(cancelled).public}, NOW).length, 0);
});

test("stable murder tie hash matches Kotlin UTF-16 vectors and ordering", () => {
  for (const [seed, value] of [["ABCDE:2:Mora", 2096758992], ["ABCDE:2:Thiago", 839404083],
    ["ABCDE:3:Mora", 2125388143], ["SALA-01:1:Ñandú", 826538268]]) assert.equal(stableNoise(seed), value);
  const state = fixture(8).state; state.round = 2; state.players[0].name = "Mora"; state.players[1].name = "Thiago";
  assert.equal(resolveKillVote(state, ["p0", "p1"]), "p1");
});

test("stale, premature and repeated deadline tasks never resolve twice; no phone needs to be connected", () => {
  const original = fixture(5).state, token = deadlineToken(original);
  assert.equal(expirePhase(original, token, token.deadlineMs - 1).changed, false);
  assert.equal(expirePhase(original, {...token, matchId: "old"}, token.deadlineMs).changed, false);
  let state = close(original); assert.equal(state.phase, "NOCHE");
  assert.equal(expirePhase(state, token, state.deadlineMs).changed, false);
  for (let i = 0; i < 50 && !state.winner; i++) state = close(state);
  assert.ok(state.winner); // Deadline-only execution, with zero client/host callbacks.
});
