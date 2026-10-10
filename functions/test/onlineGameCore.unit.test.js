"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {prepareOnlineMatch} = require("../src/onlineStartCore");
const {createServerGame, acceptAction, leaveServerGame, expirePhase, deadlineToken, projectServerGame, winnerFor,
  stableNoise, resolveKillVote, MAX_ACTIONS_PER_PHASE, assertPhaseProgress} = require("../src/onlineGameCore");
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
function resultWithTarget(state, uid, nowMs = NOW + 40000) {
  phase(state, "RECUENTO_VOTOS", {eliminationUid: uid, tieCandidates: uid ? [uid] : [], voteRound: 1, afkEnabled: false});
  return expirePhase(state, deadlineToken(state), Math.max(nowMs, state.deadlineMs)).state;
}
function rejects(state, uid, action, fields, code) {
  const before = structuredClone(state);
  assert.throws(() => act(state, uid, action, fields), (e) => e.code === code);
  assert.deepEqual(state, before);
}
test("investigation returns immediately in the investigator's private projection without a public change", () => {
  const state = phase(fixture().state, "NOCHE", {afkEnabled: false});
  const police = role(state, "policia"), target = role(state, "asesino");
  const before = projectServerGame(state).public;
  const next = act(state, police.uid, "investigar", {targetUid: target.uid});
  const projected = projectServerGame(next);
  assert.equal(next.phase, "NOCHE");
  assert.deepEqual(projected.public, before);
  assert.equal(projected.private[police.uid].investigaciones.length, 1);
  assert.equal(projected.private[police.uid].investigaciones[0].traitor, true);
  assert.ok(Object.entries(projected.private).every(([uid, p]) => uid === police.uid || p.investigaciones.length === 0));
  const dawn = close(next);
  assert.equal(dawn.investigations.filter(i => i.actorOrder === police.order && i.round === 1).length, 1);
});
test("immediate investigation still reports the Spy as innocent and rejects a second target", () => {
  const state = phase(fixture().state, "NOCHE", {afkEnabled: false});
  const police = role(state, "policia"), spy = role(state, "espia");
  const next = act(state, police.uid, "investigar", {targetUid: spy.uid});
  assert.equal(projectServerGame(next).private[police.uid].investigaciones[0].traitor, false);
  rejects(next, police.uid, "investigar", {targetUid: role(next, "asesino").uid}, "night-action-already-submitted");
});
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

test("unchosen Desertor gets a private random side before night one, chosen side is preserved", () => {
  for (const bit of [0, 1]) {
    const original = fixture().state;
    const next = expirePhase(original, deadlineToken(original), original.deadlineMs,
      {chooseRandomInt: (max) => {assert.equal(max, 2); return bit;}}).state;
    assert.equal(next.phase, "NOCHE"); assert.equal(next.round, 1);
    assert.equal(next.deserterTeam, bit === 0 ? "Pueblo" : "Traidores");
    assert.equal(original.deserterTeam, null);
    const projections = projectServerGame(next);
    assert.ok(!Object.hasOwn(projections.public, "desertorBando"));
    for (const p of next.players) assert.equal(Object.hasOwn(projections.private[p.uid], "desertorBando"), p.role.key === "desertor");
    assert.equal(expirePhase(next, deadlineToken(original), next.deadlineMs,
      {chooseRandomInt() {throw new Error("duplicate must not draw");}}).changed, false);
  }
  for (const state of [fixture().state, fixture(5).state]) {
    if (role(state, "desertor")) state.deserterTeam = "Traidores";
    const next = expirePhase(state, deadlineToken(state), state.deadlineMs,
      {chooseRandomInt() {throw new Error("no fallback needed");}}).state;
    assert.equal(next.deserterTeam, state.deserterTeam);
  }
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
  let {state, prepared} = fixture(8, "medieval");
  const jester = role(state, "bufon");
  phase(state, "RECUENTO_VOTOS", {eliminationUid: jester.uid});
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

test("result entry atomically publishes death, public role and event; dead chat waits for next phase", () => {
  for (const reveal of [false, true]) {
    let state = fixture(15).state; state.config.revelarRolesAlMorir = reveal;
    const target = role(state, "aldeano"), alreadyDead = role(state, "medico");
    alreadyDead.alive = false; alreadyDead.deathCause = "NIGHT";
    const initial = projectServerGame(state).public;
    assert.ok(initial.jugadores[target.order].vivo);
    state = resultWithTarget(state, target.uid);
    const projection = projectServerGame(state);
    assert.equal(state.phase, "RESULTADO"); assert.equal(state.winner, null);
    assert.equal(state.deadlineMs - state.phaseStartedAtMs, 8000);
    assert.equal(projection.public.jugadores[target.order].vivo, false);
    assert.equal(projection.public.jugadores[target.order].causaEliminacion, "VOTE");
    assert.equal(!!projection.public.jugadores[target.order].rolKey, reveal);
    assert.equal(projection.public.eventosPublicos.filter(e => e.codigo === "DAY_EXPULSION").length, 1);
    assert.equal(projection.permissions[target.uid].deadChat, false);
    assert.equal(projection.permissions[alreadyDead.uid].deadChat, true);
    const token = deadlineToken(state);
    const next = close(state);
    assert.equal(next.phase, "NOCHE"); assert.equal(projectServerGame(next).permissions[target.uid].deadChat, true);
    assert.equal(expirePhase(next, token, next.deadlineMs).changed, false);
    assert.equal(next.events.filter(e => e.codigo === "DAY_EXPULSION").length, 1);
  }
});

test("Jester is public only once expelled, gets twelve seconds and preserves a longer configured transition", () => {
  for (const transitionSeconds of [4, 10, 15]) {
    let state = fixture(8, "medieval").state;
    state.timing.transitionSeconds = transitionSeconds; state.config.revelarRolesAlMorir = false;
    const jester = role(state, "bufon");
    assert.deepEqual(projectServerGame(state).public.victoriasEspeciales, []);
    state = resultWithTarget(state, jester.uid);
    assert.equal(state.deadlineMs - state.phaseStartedAtMs, Math.max(12, transitionSeconds) * 1000);
    assert.equal(state.winner, null);
    const projection = projectServerGame(state);
    assert.equal(projection.public.jugadores[jester.order].rolKey, undefined);
    assert.equal(projection.public.victoriasEspeciales[0].jugador, jester.name);
    assert.equal(projection.permissions[jester.uid].deadChat, false);
    state = close(state);
    assert.equal(state.specialVictories.length, 1);
  }
  const none = resultWithTarget(fixture(8).state, null);
  assert.equal(none.deadlineMs - none.phaseStartedAtMs, none.timing.transitionSeconds * 1000);
  assert.equal(none.events.at(-1).codigo, "DAY_NO_EXPULSION");
});

test("last killer is expelled in RESULTADO before the victory is evaluated at its deadline", () => {
  let state = fixture(5).state;
  state = resultWithTarget(state, role(state, "asesino").uid);
  assert.equal(state.phase, "RESULTADO"); assert.equal(state.winner, null);
  assert.ok(projectServerGame(state).public.eventosPublicos.some(e => e.codigo === "DAY_EXPULSION"));
  state = close(state);
  assert.equal(state.winner, "Pueblo"); assert.equal(state.phase, "FINALIZADA");
});

test("in-flight result from an older deployment still eliminates its living target exactly once", () => {
  let state = fixture(8, "medieval").state;
  const jester = role(state, "bufon");
  phase(state, "RESULTADO", {eliminationUid: jester.uid, afkEnabled: false});
  const token = deadlineToken(state);
  state = close(state);
  assert.equal(playerAt(state, jester.uid).deathCause, "VOTE");
  assert.equal(state.specialVictories.length, 1);
  assert.equal(expirePhase(state, token, state.deadlineMs).changed, false);
});

test("departure during RESULTADO cannot skip a pending expulsion when Desertor parity opens and breaks", () => {
  let state = fixture(15).state;
  const towns = state.players.filter(p => p.role.team === "Pueblo").slice(0, 3);
  const keep = new Set([...towns.map(p => p.uid), ...["desertor", "asesino", "espia", "mercenario"].map(key => role(state, key).uid)]);
  state.players.forEach(p => {p.alive = keep.has(p.uid);});
  state.round = 4; state.deserterTeam = "Pueblo";
  state = resultWithTarget(state, towns[0].uid);
  assert.equal(playerAt(state, towns[0].uid).deathCause, "VOTE");
  state = leaveServerGame(state, role(state, "mercenario").uid, state.phaseStartedAtMs + 1).state;
  assert.equal(state.phase, "DESERTOR_RECONSIDERACION");
  assert.equal(state.deserterReturn.phase, "RESULTADO");
  state = leaveServerGame(state, role(state, "espia").uid, state.phaseStartedAtMs + 1).state;
  assert.equal(state.phase, "NOCHE"); assert.equal(state.round, 5); assert.equal(state.deserterUsed, false);
  assert.equal(playerAt(state, towns[0].uid).deathCause, "VOTE");
  assert.equal(state.events.filter(e => e.codigo === "DAY_EXPULSION").length, 1);
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


test("Desertor window resumes dawn/day or result/next night after parity breaks, without consuming choice", () => {
  for (const origin of ["AMANECER", "RESULTADO"]) {
    let state = deserterParity(); state.afkEnabled = false;
    if (origin === "RESULTADO") { phase(state, "RESULTADO"); state = close(state); }
    else {
      phase(state, "NOCHE"); state = close(state);
    }
    assert.equal(state.phase, "DESERTOR_RECONSIDERACION");
    assert.equal(state.deserterReturn.phase, origin);
    const previousIndex = state.phaseIndex;
    const oldToken = deadlineToken(state);
    state = leaveServerGame(state, role(state, "mercenario").uid, state.phaseStartedAtMs + 1).state;
    assert.equal(state.phase, origin === "AMANECER" ? "DIA_DEBATE" : "NOCHE");
    assert.equal(state.round, origin === "AMANECER" ? 4 : 5);
    assert.ok(state.phaseIndex > previousIndex); assert.equal(state.winner, null);
    assert.equal(state.deserterUsed, false); assert.equal(state.deserterTeam, "Pueblo");
    assert.equal(state.deserterReturn, null);
    assert.equal(expirePhase(state, oldToken, oldToken.deadlineMs).changed, false);
    state = close(state); assert.ok(state.phaseIndex > previousIndex + 1 || state.winner);
  }
});

test("a still valid Desertor window keeps its deadline; a broken window times out or accepts an in-flight choice safely", () => {
  for (const choice of [null, "Traidores", "mantener"]) {
    let state = deserterParity(); phase(state, "RESULTADO"); state = close(state);
    const token = deadlineToken(state), index = state.phaseIndex;
    // Simulates a restored window after parity changed before it was resolved.
    role(state, "mercenario").alive = false;
    const desertor = role(state, "desertor");
    state = choice ? act(state, desertor.uid, "desertor_rethink", {team: choice}, state.phaseStartedAtMs + 1) : close(state);
    assert.equal(state.phase, "NOCHE"); assert.equal(state.deserterUsed, false);
    assert.equal(state.deserterTeam, "Pueblo"); assert.ok(state.phaseIndex > index);
    assert.equal(expirePhase(state, token, token.deadlineMs).changed, false);
  }
  let state = deserterParity(); phase(state, "RESULTADO"); state = close(state);
  const token = deadlineToken(state);
  state = leaveServerGame(state, state.players.find((p) => !p.alive).uid, state.phaseStartedAtMs + 1).state;
  assert.deepEqual(deadlineToken(state), token); // Departure must not prolong the choice window.
});

test("a departure-created window resumes pending night intentions without replaying or restoring the leaver's action", () => {
  let state = deserterParity(); phase(state, "NOCHE"); state.afkEnabled = false;
  const killer = role(state, "asesino"), doctor = role(state, "medico"), target = role(state, "aldeano");
  // Open from a night with Town just above parity, then a Town departure reaches parity.
  doctor.alive = true;
  state = act(state, killer.uid, "matar", {targetUid: target.uid});
  state = act(state, doctor.uid, "salvar", {targetUid: target.uid});
  state = leaveServerGame(state, doctor.uid, NOW + 2).state;
  assert.equal(state.phase, "DESERTOR_RECONSIDERACION");
  assert.equal(state.deserterReturn.phase, "NOCHE");
  state = leaveServerGame(state, role(state, "mercenario").uid, NOW + 3).state;
  assert.equal(state.phase, "NOCHE"); assert.equal(state.deserterUsed, false);
  assert.equal(Object.values(state.actions).length, 1);
  assert.equal(Object.values(state.actions)[0].actorOrder, killer.order);
  state = close(state); assert.equal(playerAt(state, target.uid).deathCause, "NIGHT");
});

test("all expirable phases advance a phase index or finish and stuck state is rejected without mutation", () => {
  const phases = ["REPARTO", "NOCHE", "AMANECER", "DIA_DEBATE", "CONTRAPUNTO", "VOTACION", "DESEMPATE_VOTACION",
    "RECUENTO_VOTOS", "ALCALDE_DESEMPATE", "RESULTADO"];
  for (const value of phases) {
    const current = fixture(14).state; phase(current, value); current.afkEnabled = false; current.deserterTeam = "Pueblo";
    const result = expirePhase(current, deadlineToken(current), current.deadlineMs);
    assert.equal(result.changed, true); assertPhaseProgress(current, result.state, current.deadlineMs);
  }
  const state = deserterParity(); phase(state, "DESERTOR_RECONSIDERACION");
  role(state, "mercenario").alive = false; const original = structuredClone(state);
  assert.throws(() => close(state), (e) => e.code === "phase-stuck"); assert.deepEqual(state, original);
  assert.throws(() => assertPhaseProgress(state, {...state, deadlineMs: state.deadlineMs + 10000}, state.deadlineMs),
    (e) => e.code === "phase-stuck");
});

test("dead Villager and expelled Jester leaving before final always record a defeat", () => {
  for (const key of ["aldeano", "bufon"]) {
    let {state, prepared} = fixture(8, "medieval"); phase(state, "RESULTADO");
    const p = role(state, key);
    if (key === "bufon") { phase(state, "RECUENTO_VOTOS", {eliminationUid: p.uid}); state = close(state); }
    else { p.alive = false; p.deathCause = "NIGHT"; }
    assert.equal(playerAt(state, p.uid).alive, false);
    state = leaveServerGame(state, p.uid, state.phaseStartedAtMs + 1).state;
    state.players.filter((p) => ["asesino", "espia"].includes(p.role.key)).forEach((p) => {p.alive = false;});
    phase(state, "RESULTADO"); state = close(state); assert.equal(state.winner, "Pueblo");
    const records = recordsForFinishedRoom("room", {partidaInicial: prepared.payloads.initialMatch,
      estadoPartida: projectServerGame(state).public}, state.phaseStartedAtMs);
    assert.equal(records.find((r) => r.uid === p.uid).won, false);
    assert.equal(playerAt(state, p.uid).deathCause, "ABANDONO");
  }
});

test("event identifiers survive clipping, retries and rounds without secret-action increments", () => {
  let state = fixture(15).state; state.afkEnabled = false; state.deserterTeam = "Pueblo";
  // Repeated rounds without attacks generate enough real public events to clip the ring.
  for (let i = 0; i < 120; i++) state = close(state);
  assert.equal(state.events.length, 60); assert.ok(state.events[0].seq > 1);
  assert.ok(state.events.every((e, i, all) => e.codigo !== "INFO" && (!i || e.seq === all[i - 1].seq + 1)));
  const before = state.eventSeq; phase(state, "NOCHE");
  const doctor = role(state, "medico"); state = act(state, doctor.uid, "salvar", {targetUid: doctor.uid});
  assert.equal(state.eventSeq, before);
  const projection = projectServerGame(state).public;
  assert.ok(projection.eventosPublicos.every((e) => e.ronda === state.round));
  assert.ok(projection.eventosPublicos.length < state.events.length);
});

function deserterParity() {
  const state = deserterFinal();
  state.players.find((p) => p.role.key === "aldeano" && !p.alive).alive = true;
  return state;
}

test('public chat matches common debate/vote/counterpoint table; Oracle only during debate', () => {
  const {state} = fixture();
  const alive = state.players[0], muted = state.players[1], dead = state.players[2], guest = state.players[3];
  muted.muted = true; dead.alive = false; guest.alive = false;
  state.oracleGuestUid = guest.uid; state.counterpointPlayers = [alive.uid, muted.uid];
  for (const current of ['REPARTO','NOCHE','AMANECER','DIA_DEBATE','CONTRAPUNTO','VOTACION','DESEMPATE_VOTACION','RECUENTO_VOTOS','ALCALDE_DESEMPATE','RESULTADO','DESERTOR_RECONSIDERACION']) {
    state.phase = current;
    const p = projectServerGame(state).permissions;
    assert.equal(p[alive.uid].publicChat, ['DIA_DEBATE','CONTRAPUNTO','VOTACION','DESEMPATE_VOTACION'].includes(current), current);
    assert.equal(p[muted.uid].publicChat, false, `muted ${current}`);
    assert.equal(p[dead.uid].publicChat, false, `dead ${current}`);
    assert.equal(p[guest.uid].publicChat, current === 'DIA_DEBATE', `guest ${current}`);
  }
  state.phase = 'VOTACION'; alive.left = true;
  assert.equal(projectServerGame(state).permissions[alive.uid].publicChat, false);
});

test('reactions follow common public phases but mute blocks gestures (user 8/10)', () => {
  const {state} = fixture();
  for(const phase of ['REPARTO','NOCHE','AMANECER','DIA_DEBATE','CONTRAPUNTO','VOTACION','RECUENTO_VOTOS','DESEMPATE_VOTACION','ALCALDE_DESEMPATE','RESULTADO','DESERTOR_RECONSIDERACION']) {
    state.phase=phase;
    const allowed=['DIA_DEBATE','CONTRAPUNTO','VOTACION','RECUENTO_VOTOS','DESEMPATE_VOTACION','ALCALDE_DESEMPATE'].includes(phase);
    assert.equal(projectServerGame(state).permissions.p0.reactions,allowed,phase);
  }
  state.phase='VOTACION';state.players[0].muted=true;
  assert.equal(projectServerGame(state).permissions.p0.reactions,false);
  state.players[0].muted=false;state.players[0].alive=false;
  assert.equal(projectServerGame(state).permissions.p0.reactions,false);
  state.players[0].alive=true;state.players[0].left=true;
  assert.equal(projectServerGame(state).permissions.p0.reactions,false);
});
test("VOTAR ANTES: after ten seconds every living player (muted too) can mark; all marked opens the vote at once", () => {
  let state = phase(fixture(5).state, "DIA_DEBATE", {afkEnabled: false, phaseStartedAtMs: NOW});
  const living = state.players.filter((p) => p.alive);
  const muted = living[1]; muted.muted = true;
  rejects(state, living[0].uid, "listo_votar", {}, "ready-too-early");
  const later = NOW + 10000;
  state = act(state, living[0].uid, "listo_votar", {}, later);
  assert.deepEqual({listos: 1, total: 5}, (({listos, total}) => ({listos, total}))(projectServerGame(state).public.listosVotar));
  // Only aggregates are public; the own mark is in the private confirmed actions.
  assert.ok(!JSON.stringify(projectServerGame(state).public.listosVotar).includes(living[0].uid));
  assert.ok(projectServerGame(state).private[living[0].uid].accionesConfirmadas.some((a) => a.action === "listo_votar"));
  state = act(state, living[0].uid, "cancelar_listo", {}, later + 1);
  assert.equal(projectServerGame(state).public.listosVotar.listos, 0);
  for (const p of living.slice(0, 4)) state = act(state, p.uid, "listo_votar", {}, later + 2);
  assert.equal(state.phase, "DIA_DEBATE");
  state = act(state, living[4].uid, "listo_votar", {}, later + 3);
  assert.equal(state.phase, "VOTACION");
  assert.equal(projectServerGame(state).public.listosVotar, null);
});
test("VOTAR ANTES: dead players cannot mark, other phases reject it, and a departure can complete the readiness", () => {
  let state = phase(fixture(5).state, "DIA_DEBATE", {afkEnabled: false, phaseStartedAtMs: NOW});
  const dead = state.players[0]; dead.alive = false; dead.deathCause = "NIGHT";
  rejects(state, dead.uid, "listo_votar", {}, "voter-unavailable");
  const living = state.players.filter((p) => p.alive);
  for (const p of living.slice(0, 3)) state = act(state, p.uid, "listo_votar", {}, NOW + 10000);
  const left = leaveServerGame(state, living[3].uid, NOW + 10001).state;
  assert.equal(left.winner, null);
  assert.equal(left.phase, "VOTACION");
  const voting = phase(fixture(5).state, "VOTACION", {afkEnabled: false, phaseStartedAtMs: NOW});
  rejects(voting, voting.players[0].uid, "listo_votar", {}, "wrong-phase");
});
test("individual ballots are published after the vote closes only when the room shows votes", () => {
  let state = phase(fixture(5).state, "VOTACION", {afkEnabled: false});
  const [a, b, c] = state.players;
  state = act(state, a.uid, "votar", {targetUid: b.uid});
  state = act(state, c.uid, "votar", {targetUid: b.uid});
  assert.equal(projectServerGame(state).public.votosIndividuales, null); // secret while voting
  const counted = close(state);
  assert.equal(counted.phase, "RECUENTO_VOTOS");
  assert.deepEqual(projectServerGame(counted).public.votosIndividuales,
    [{votante: a.order, objetivo: b.order}, {votante: c.order, objetivo: b.order}]);
  const hidden = structuredClone(counted); hidden.config.votosIndividuales = false;
  assert.equal(projectServerGame(hidden).public.votosIndividuales, null);
  const night = close(close(counted));
  assert.equal(projectServerGame(night).public.votosIndividuales, null);
});
