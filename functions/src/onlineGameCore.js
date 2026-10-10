"use strict";

// Pure server engine. No client clocks, Firebase calls or host authority.
const {createHash, randomInt} = require("node:crypto");
const {isDeepStrictEqual} = require("node:util");
const {TRAITOR_TEAM, MAPS} = require("./onlineStartCore");
const TOWN = "Pueblo";
const CANCELLED = "Cancelada";
const TRAITORS = new Set(["asesino", "espia", "mercenario"]);
const KILLERS = new Set(["asesino", "espia"]);
const NEUTRALS = new Set(["desertor", "bufon"]);
const MAX_ACTIONS_PER_PHASE = 12;
const MAX_RECEIPTS = 180;
// Usual table's "VOTAR ANTES": available after ten seconds of debate (READY_VOTE_MINIMUM_DEBATE_MS).
const READY_VOTE_MINIMUM_MS = 10000;
const ACTION_FIELDS = new Set(["matchId", "phaseIndex", "requestId", "action", "targetUid", "team"]);
const ACTIONS = new Set(["role_ack", "desertor_initial", "matar", "silenciar", "investigar", "salvar",
  "invitar_muerto", "guardar_poder", "votar", "revelar_alcalde", "decidir_empate", "contrapunto",
  "senalar_contrapunto", "desertor_rethink", "listo_votar", "cancelar_listo"]);

class GameActionError extends Error {
  constructor(code) { super(code); this.name = "GameActionError"; this.code = code; }
}
function reject(code) { throw new GameActionError(code); }
function alive(state) { return state.players.filter((p) => p.alive); }
function player(state, uid) { return state.players.find((p) => p.uid === uid); }
function slot(p, action) {
  if (["matar", "silenciar", "investigar", "salvar", "invitar_muerto", "guardar_poder"].includes(action)) return `${p.order}:night`;
  return `${p.order}:${action}`;
}
function actionFor(state, p, action) { return state.actions[slot(p, action)]; }
function record(state, p, input) { state.actions[slot(p, input.action)] = {...input, actorOrder: p.order}; }
function transition(state, phase, nowMs, seconds) {
  state.phase = phase;
  state.phaseIndex++;
  state.phaseStartedAtMs = nowMs;
  state.deadlineMs = phase === "FINALIZADA" ? null : nowMs + seconds * 1000;
  state.actions = {};
  state.phaseActionCounts = {};
}
function notice(state, text, code, players = []) {
  if (!code) reject("missing-event-code");
  state.announcement = text;
  state.eventSeq = (state.eventSeq || 0) + 1;
  state.events.push({seq: state.eventSeq, codigo: code, ronda: state.round, jugadores: [...players], texto: text});
  state.events = state.events.slice(-60);
}
function stableNoise(value, seed = 0) {
  let result = seed;
  // UTF-16 and signed Int32 overflow exactly match Kotlin/Swift parity vectors.
  for (let i = 0; i < value.length; i++) result = (Math.imul(result, 31) + value.charCodeAt(i)) & 0x7fffffff;
  return result;
}
function resolveKillVote(state, votes) {
  const counts = new Map();
  for (const uid of votes) counts.set(uid, (counts.get(uid) || 0) + 1);
  const max = Math.max(0, ...counts.values());
  return [...counts.keys()].filter((uid) => counts.get(uid) === max).sort((a, b) => {
    const left = player(state, a).name, right = player(state, b).name;
    return stableNoise(`${state.code}:${state.round}:${left}`) - stableNoise(`${state.code}:${state.round}:${right}`) ||
      (left < right ? -1 : left > right ? 1 : 0);
  })[0] || null;
}
function winnerFor(state) {
  const living = alive(state);
  if (!living.length) return null;
  if (!living.some((p) => KILLERS.has(p.role.key))) return TOWN;
  if (living.some((p) => p.role.key === "desertor") && !state.deserterTeam) return null;
  const traitors = living.filter((p) => TRAITORS.has(p.role.key)).length;
  const town = living.filter((p) => !NEUTRALS.has(p.role.key) && p.role.team === TOWN).length;
  return traitors >= town ? TRAITOR_TEAM : null;
}
function canReconsider(state) {
  return alive(state).some((p) => p.role.key === "desertor") && state.deserterTeam !== null &&
    !state.deserterUsed && state.round >= 4;
}
function evaluateWinner(state, nowMs) {
  const candidate = winnerFor(state);
  if (state.phase === "DESERTOR_RECONSIDERACION") {
    if (!candidate) { resumeAfterDeserterWindow(state, nowMs); return false; }
    // A departure cannot reopen the same window and extend its deadline.
    if (candidate === TRAITOR_TEAM && canReconsider(state)) return true;
  }
  if (candidate === TRAITOR_TEAM && canReconsider(state)) {
    state.deserterReturn = {phase: state.phase,
      remainingMs: Math.max(1000, state.deadlineMs - nowMs),
      actions: state.actions, phaseActionCounts: state.phaseActionCounts};
    transition(state, "DESERTOR_RECONSIDERACION", nowMs, state.timing.votingSeconds);
    notice(state, "El Desertor puede reconsiderar su bando antes de resolver la partida.", "DESERTER_WINDOW");
  } else if (candidate) {
    state.winner = candidate;
    state.deserterReturn = null;
    transition(state, "FINALIZADA", nowMs, 0);
    notice(state, `Victoria de ${candidate}.`, "VICTORY");
  }
  return !!candidate;
}
function resumeAfterDeserterWindow(state, nowMs) {
  const saved = state.deserterReturn;
  if (!saved || !["REPARTO", "NOCHE", "AMANECER", "DIA_DEBATE", "CONTRAPUNTO", "VOTACION",
    "DESEMPATE_VOTACION", "RECUENTO_VOTOS", "ALCALDE_DESEMPATE", "RESULTADO"].includes(saved.phase) ||
      !Number.isSafeInteger(saved.remainingMs) || saved.remainingMs <= 0 || !saved.actions || !saved.phaseActionCounts) reject("phase-stuck");
  state.deserterReturn = null;
  if (saved.phase === "RESULTADO") { state.round++; startNight(state, nowMs); }
  else if (saved.phase === "AMANECER") {
    transition(state, "DIA_DEBATE", nowMs, state.timing.discussionSeconds);
    notice(state, "La partida continúa. Se abre el debate.", "DESERTER_WINDOW_CLOSED");
  } else {
    // A departure can interrupt any other phase. Resume its remaining time and
    // intentions, without repeating the already resolved dawn/vote effects.
    transition(state, saved.phase, nowMs, 0);
    state.deadlineMs = nowMs + saved.remainingMs;
    state.actions = saved.actions;
    state.phaseActionCounts = saved.phaseActionCounts;
    notice(state, "La partida continúa. Se retoma la fase anterior.", "DESERTER_WINDOW_CLOSED");
  }
}
function closeDeserterWindow(state, nowMs, team = "mantener") {
  if (!winnerFor(state)) { resumeAfterDeserterWindow(state, nowMs); return; }
  if (winnerFor(state) === TRAITOR_TEAM) {
    if (team !== "mantener") state.deserterTeam = team;
    state.deserterUsed = true;
  }
  evaluateWinner(state, nowMs);
}
function readyToVote(state) {
  const living = alive(state);
  return {listos: living.filter((p) => state.actions[`${p.order}:listo_votar`]).length, total: living.length};
}
function maybeOpenVoteEarly(state, nowMs) {
  // Every living player (muted ones too, as on the usual table) marked ready: the debate ends now.
  if (state.phase !== "DIA_DEBATE" || state.winner) return;
  const {listos, total} = readyToVote(state);
  if (total > 0 && listos === total) beginVote(state, nowMs, false);
}
function startNight(state, nowMs) {
  state.players.forEach((p) => { p.muted = false; });
  state.oracleGuestUid = null;
  state.counterpointPlayers = [];
  state.counterpointPointed = null;
  state.tieCandidates = [];
  state.eliminationUid = null;
  state.voteRound = 0;
  state.voteTotals = {};
  state.voteBallots = [];
  state.mayorCorruption = false;
  transition(state, "NOCHE", nowMs, state.timing.nightSeconds);
  notice(state, `Noche ${state.round}.`, "NIGHT_START");
}

function createServerGame({roomId, prepared, nowMs}) {
  if (prepared?.status !== "ready" || !Number.isSafeInteger(nowMs) || nowMs <= 0) reject("invalid-start");
  const {initialMatch} = prepared.payloads;
  return {
    protocolVersion: 3, authorityMode: "server", roomId, matchId: initialMatch.matchId, generation: 1,
    code: initialMatch.codigoSala, mapKey: prepared.mapKey, config: initialMatch.config,
    timing: prepared.config.timing, afkEnabled: true,
    players: prepared.assignedPlayers.map((p, order) => ({uid: p.id, order, name: p.name,
      publicId: p.publicId || "", role: p.role, alive: true, muted: false,
      lastSilencedRound: null, nightAfk: 0, voteAfk: 0, deathCause: "NONE"})),
    phase: "REPARTO", phaseIndex: 0, revision: 1, publicRevision: 1, round: 1,
    phaseStartedAtMs: nowMs, deadlineMs: nowMs + 30000, assignmentMinimumMs: nowMs + 10000,
    actions: {}, phaseActionCounts: {}, receipts: [],
    winner: null, mayorUid: null, mayorCorruption: false, deserterTeam: null, deserterUsed: false, deserterReturn: null,
    payadorUsed: false, counterpointPlayers: [], counterpointPointed: null,
    oracleUsed: false, oracleGuestUid: null, specialVictories: [],
    investigations: [], tieCandidates: [], eliminationUid: null, voteTotals: {}, voteBallots: [], voteRound: 0,
    announcement: prepared.payloads.matchState.anuncioPublico.replace("partida local", "partida online"), events: [], eventSeq: 0,
  };
}
function canSilence(state, actor, target) {
  return target?.alive && target.uid !== actor.uid &&
    (target.lastSilencedRound === null || state.round - target.lastSilencedRound >= 2);
}
function canKill(state, actor, target) {
  return target?.alive && target.uid !== actor.uid && !TRAITORS.has(target.role.key) &&
    !(target.role.key === "desertor" && state.deserterTeam === TRAITOR_TEAM);
}
function oracleEligible(state, actor) {
  return actor.alive && actor.role.key === "oraculo" && state.mapKey === "grecia" &&
    state.round > 1 && !state.oracleUsed && state.players.some((p) => !p.alive && !p.left);
}
function requiredNight(state) {
  return alive(state).filter((p) => {
    if (KILLERS.has(p.role.key)) return state.players.some((t) => canKill(state, p, t));
    if (p.role.key === "mercenario") return state.players.some((t) => canSilence(state, p, t));
    if (p.role.key === "policia") return alive(state).some((t) => t.uid !== p.uid);
    if (p.role.key === "medico") return true;
    return oracleEligible(state, p);
  }).map((p) => p.uid);
}
function applyAfk(state, kind, required, acted, nowMs) {
  if (!state.afkEnabled || state.winner) return;
  const field = kind === "night" ? "nightAfk" : "voteAfk";
  const active = alive(state);
  const requiredSet = new Set(required), actedSet = new Set(acted);
  const expelled = active.filter((p) => requiredSet.has(p.uid) && !actedSet.has(p.uid) && p[field] + 1 >= 2);
  if (expelled.length && expelled.length === active.length) {
    state.winner = CANCELLED;
    state.specialVictories = [];
    transition(state, "FINALIZADA", nowMs, 0);
    notice(state, "Partida cancelada por inactividad. Ningún jugador respondió.", "MATCH_CANCELLED");
    return;
  }
  for (const p of active.filter((p) => requiredSet.has(p.uid))) {
    p[field] = actedSet.has(p.uid) ? 0 : p[field] + 1;
    if (p[field] >= 2) { p.alive = false; p.muted = false; p.deathCause = "AFK";
      notice(state, `${p.name} fue expulsado por inactividad.`, "AFK_EXPULSION", [p.uid]); }
  }
}
function roleMayAct(actor, roles) { if (!actor.alive || !roles.includes(actor.role.key)) reject("role-not-allowed"); }
function phaseIs(state, phases) { if (!phases.includes(state.phase)) reject("wrong-phase"); }
function isVoting(state) { return ["VOTACION", "DESEMPATE_VOTACION"].includes(state.phase); }
function validateNight(state, actor, target, input) {
  phaseIs(state, ["NOCHE"]);
  switch (input.action) {
    case "matar": roleMayAct(actor, [...KILLERS]); if (!canKill(state, actor, target)) reject("invalid-target"); break;
    case "silenciar": roleMayAct(actor, ["mercenario"]); if (!canSilence(state, actor, target)) reject("silence-cooldown-or-target"); break;
    case "investigar": roleMayAct(actor, ["policia"]); if (!target?.alive || target.uid === actor.uid) reject("invalid-target"); break;
    case "salvar": roleMayAct(actor, ["medico"]); if (!target?.alive) reject("invalid-target"); break;
    case "invitar_muerto":
      if (!oracleEligible(state, actor) || !target || target.alive || target.left) reject("invalid-oracle-target"); break;
    case "guardar_poder": if (!oracleEligible(state, actor) || input.targetUid) reject("oracle-unavailable"); break;
  }
}
function rememberInvestigation(state, actorOrder, targetUid) {
  if (state.investigations.some(r => r.actorOrder === actorOrder && r.round === state.round)) return;
  const target = player(state, targetUid);
  state.investigations.push({actorOrder, targetUid, round: state.round,
    traitor: target.role.key === "asesino" || target.role.key === "mercenario"}); // Spy reads innocent.
}
function performAction(state, actor, input, nowMs) {
  const target = player(state, input.targetUid);
  switch (input.action) {
    case "role_ack":
      phaseIs(state, ["REPARTO"]);
      if (actor.role.key === "desertor" && !state.deserterTeam) reject("deserter-choice-required");
      record(state, actor, input); break;
    case "desertor_initial":
      phaseIs(state, ["REPARTO"]); roleMayAct(actor, ["desertor"]);
      if (state.deserterTeam || ![TOWN, TRAITOR_TEAM].includes(input.team)) reject("invalid-team-choice");
      state.deserterTeam = input.team; record(state, actor, input); break;
    case "matar": case "silenciar": case "investigar": case "salvar": case "invitar_muerto": case "guardar_poder":
      validateNight(state, actor, target, input);
      if (actionFor(state, actor, input.action)) reject("night-action-already-submitted");
      record(state, actor, input);
      if (input.action === "investigar") rememberInvestigation(state, actor.order, input.targetUid);
      break;
    case "votar":
      phaseIs(state, ["VOTACION", "DESEMPATE_VOTACION"]);
      if (!actor.alive || actor.muted) reject("voter-unavailable");
      if (!target?.alive || target.uid === actor.uid ||
          (state.phase === "DESEMPATE_VOTACION" && !state.tieCandidates.includes(target.uid))) reject("invalid-target");
      record(state, actor, input); break;
    case "revelar_alcalde":
      phaseIs(state, ["DIA_DEBATE", "VOTACION", "DESEMPATE_VOTACION", "ALCALDE_DESEMPATE"]);
      roleMayAct(actor, ["alcalde"]);
      if (actor.muted || state.mayorUid) reject("mayor-unavailable");
      state.mayorUid = actor.uid; notice(state, `${actor.name} se reveló como Alcalde.`, "MAYOR_REVEALED", [actor.uid]); record(state, actor, input); break;
    case "decidir_empate":
      phaseIs(state, ["ALCALDE_DESEMPATE"]); roleMayAct(actor, ["alcalde"]);
      if (actor.muted || state.mayorUid !== actor.uid || !target?.alive || !state.tieCandidates.includes(target.uid)) reject("mayor-unavailable");
      state.eliminationUid = target.uid; state.voteRound = state.mayorCorruption ? 4 : 3;
      notice(state, `El Alcalde decidió expulsar a ${target.name}.`, "MAYOR_DECISION", [actor.uid, target.uid]);
      enterResult(state, nowMs); break;
    case "contrapunto":
      phaseIs(state, ["DIA_DEBATE"]); roleMayAct(actor, ["payador"]);
      if (actor.muted || state.payadorUsed || state.mapKey !== "pampa" || !target?.alive ||
          target.uid === actor.uid || state.counterpointPlayers.includes(target.uid)) reject("invalid-counterpoint");
      state.counterpointPlayers.push(target.uid);
      if (state.counterpointPlayers.length === 2) {
        state.payadorUsed = true;
        transition(state, "CONTRAPUNTO", nowMs, state.timing.discussionSeconds);
        notice(state, "Se abre un Contrapunto. Solo sus dos participantes pueden hablar.", "COUNTERPOINT_OPEN", state.counterpointPlayers);
      }
      break;
    case "senalar_contrapunto":
      phaseIs(state, ["CONTRAPUNTO"]); roleMayAct(actor, ["payador"]);
      if (actor.muted || !target?.alive || !state.counterpointPlayers.includes(target.uid)) reject("invalid-counterpoint");
      state.counterpointPointed = target.uid;
      beginVote(state, nowMs, false); break;
    case "listo_votar":
      phaseIs(state, ["DIA_DEBATE"]);
      if (!actor.alive) reject("voter-unavailable");
      if (nowMs < state.phaseStartedAtMs + READY_VOTE_MINIMUM_MS) reject("ready-too-early");
      record(state, actor, input);
      maybeOpenVoteEarly(state, nowMs);
      break;
    case "cancelar_listo":
      phaseIs(state, ["DIA_DEBATE"]);
      if (!actor.alive) reject("voter-unavailable");
      delete state.actions[`${actor.order}:listo_votar`];
      break;
    case "desertor_rethink":
      roleMayAct(actor, ["desertor"]);
      phaseIs(state, ["DESERTOR_RECONSIDERACION", "DIA_DEBATE"]);
      if (!canReconsider(state) || ![TOWN, TRAITOR_TEAM, "mantener"].includes(input.team)) reject("reconsideration-unavailable");
      if (state.phase === "DESERTOR_RECONSIDERACION") {
        closeDeserterWindow(state, nowMs, input.team);
      } else {
        if (input.team !== "mantener") state.deserterTeam = input.team;
        state.deserterUsed = true;
      }
      break;
    default: reject("unknown-action");
  }
}
function parseAction(input) {
  if (!input || typeof input !== "object" || Array.isArray(input) || Object.keys(input).some((k) => !ACTION_FIELDS.has(k)) ||
      typeof input.matchId !== "string" || input.matchId.length < 1 || input.matchId.length > 128 ||
      !Number.isSafeInteger(input.phaseIndex) || input.phaseIndex < 0 ||
      typeof input.requestId !== "string" || !/^[A-Za-z0-9_-]{8,80}$/.test(input.requestId) || !ACTIONS.has(input.action) ||
      (input.targetUid !== undefined && (typeof input.targetUid !== "string" || input.targetUid.length < 1 || input.targetUid.length > 128)) ||
      (input.team !== undefined && ![TOWN, TRAITOR_TEAM, "mantener"].includes(input.team))) reject("invalid-action");
  // Only gameplay fields enter the digest; actor identity comes from Auth.
  return {matchId: input.matchId, phaseIndex: input.phaseIndex, requestId: input.requestId,
    action: input.action, ...(input.targetUid !== undefined ? {targetUid: input.targetUid} : {}),
    ...(input.team !== undefined ? {team: input.team} : {})};
}
function acceptAction(current, uid, raw, nowMs) {
  const input = parseAction(raw);
  if (!Number.isSafeInteger(nowMs) || nowMs <= 0) reject("invalid-server-time");
  const actor = player(current, uid);
  if (!actor || actor.left) reject("not-a-member");
  if (input.matchId !== current.matchId) reject("stale-match");
  const digest = createHash("sha256").update(JSON.stringify(input)).digest("hex");
  const receipt = current.receipts.find((r) => r.uid === uid && r.id === input.requestId);
  if (receipt) {
    if (receipt.digest !== digest) reject("request-id-reused");
    return {state: current, changed: false, receipt: receipt.result};
  }
  if (input.phaseIndex !== current.phaseIndex) reject("stale-phase");
  if (current.winner || nowMs >= current.deadlineMs) reject("phase-closed");
  if ((current.phaseActionCounts[actor.order] || 0) >= MAX_ACTIONS_PER_PHASE) reject("too-many-actions");
  const next = structuredClone(current);
  next.phaseActionCounts[actor.order] = (next.phaseActionCounts[actor.order] || 0) + 1;
  performAction(next, player(next, uid), input, nowMs);
  next.revision++;
  if (!isDeepStrictEqual(projectServerGame(current).public, projectServerGame(next).public)) {
    next.publicRevision = (current.publicRevision || 1) + 1;
  }
  // Receipts must not expose the internal revision: it counts other players' secret actions.
  const result = {accepted: true, matchId: next.matchId, phaseIndex: next.phaseIndex, revision: next.publicRevision};
  next.receipts.push({uid, id: input.requestId, digest, result});
  next.receipts = next.receipts.slice(-MAX_RECEIPTS);
  return {state: next, changed: true, receipt: result};
}
function leaveServerGame(current, uid, nowMs) {
  const actor = player(current, uid);
  if (!actor) reject("not-a-member");
  if (actor.left) return {state: current, changed: false};
  const next = structuredClone(current), leaving = player(next, uid);
  leaving.left = true;
  if (!next.winner) {
    leaving.alive = false; leaving.muted = false; leaving.deathCause = "ABANDONO";
    for (const [key, action] of Object.entries(next.actions)) if (action.actorOrder === leaving.order) delete next.actions[key];
    for (const [key, action] of Object.entries(next.deserterReturn?.actions || {})) {
      if (action.actorOrder === leaving.order) delete next.deserterReturn.actions[key];
    }
    if (next.oracleGuestUid === uid) next.oracleGuestUid = null;
    notice(next, `${leaving.name} abandonó la partida.`, "PLAYER_LEFT", [uid]);
    if (!alive(next).length) {
      next.winner = CANCELLED; next.specialVictories = [];
      transition(next, "FINALIZADA", nowMs, 0);
      notice(next, "Partida cancelada: no quedan jugadores.", "MATCH_CANCELLED");
    } else if (!evaluateWinner(next, nowMs)) maybeOpenVoteEarly(next, nowMs);
  }
  next.revision++; next.publicRevision = (current.publicRevision || 1) + 1;
  return {state: next, changed: true};
}
function resolveNight(state, nowMs) {
  const required = requiredNight(state);
  const acted = state.players.filter((p) => actionFor(state, p, "matar")).map((p) => p.uid);
  const submitted = state.players.map((p) => actionFor(state, p, "matar")).filter(Boolean);
  const killing = submitted.filter((a) => a.action === "matar" && player(state, a.targetUid)?.alive);
  const protectedUids = new Set(submitted.filter((a) => a.action === "salvar").map((a) => a.targetUid));
  const victim = resolveKillVote(state, killing.map((a) => a.targetUid));
  const oracle = submitted.find((a) => a.action === "invitar_muerto");
  if (oracle) { state.oracleUsed = true; state.oracleGuestUid = player(state, oracle.targetUid)?.left ? null : oracle.targetUid; }
  for (const a of submitted.filter((a) => a.action === "investigar")) {
    rememberInvestigation(state, a.actorOrder, a.targetUid);
  }
  if (victim && !protectedUids.has(victim)) {
    const p = player(state, victim); p.alive = false; p.muted = false; p.deathCause = "NIGHT";
    notice(state, `${p.name} murió durante la noche.`, "NIGHT_DEATH", [p.uid]);
  } else notice(state, "Amanece sin víctimas.", "DAWN_NO_VICTIMS");
  for (const a of submitted.filter((a) => a.action === "silenciar")) {
    const target = player(state, a.targetUid);
    if (target.alive && !protectedUids.has(target.uid)) { target.muted = true; target.lastSilencedRound = state.round; }
  }
  if (state.oracleGuestUid) notice(state, `${player(state, state.oracleGuestUid).name} vuelve a hablar durante este debate por invitación del Oráculo.`,
    "ORACLE_INVITATION", [oracle.targetUid]);
  transition(state, "AMANECER", nowMs, state.timing.transitionSeconds);
  applyAfk(state, "night", required, acted, nowMs);
  if (!state.winner) evaluateWinner(state, nowMs);
}
function beginVote(state, nowMs, tie) {
  state.oracleGuestUid = null;
  state.voteRound = tie ? 2 : 1;
  transition(state, tie ? "DESEMPATE_VOTACION" : "VOTACION", nowMs, state.timing.votingSeconds);
  notice(state, tie ? `Empate entre ${state.tieCandidates.map((uid) => player(state, uid).name).join(", ")}. Vuelvan a votar.` :
    "El pueblo decide a quién expulsar.", tie ? "TIE_VOTE" : "VOTE_OPEN", tie ? state.tieCandidates : []);
}
function resolveVotes(state, nowMs) {
  const normal = state.phase === "VOTACION";
  const required = alive(state).filter((p) => !p.muted).map((p) => p.uid);
  const votes = alive(state).filter((p) => !p.muted).map((p) => actionFor(state, p, "votar")).filter(Boolean);
  const totals = {};
  for (const a of votes) {
    const actor = state.players.find((p) => p.order === a.actorOrder);
    const target = player(state, a.targetUid);
    if (target?.alive) totals[target.order] = (totals[target.order] || 0) + (state.mayorUid === actor.uid ? 2 : 1);
  }
  if (state.counterpointPointed && player(state, state.counterpointPointed)?.alive &&
      (normal || state.tieCandidates.includes(state.counterpointPointed))) {
    const order = player(state, state.counterpointPointed).order;
    totals[order] = (totals[order] || 0) + 1;
  }
  state.voteTotals = totals;
  // Who voted for whom; published only after the vote closes and only if the room shows votes.
  state.voteBallots = votes.filter((a) => player(state, a.targetUid)?.alive)
    .map((a) => ({votante: a.actorOrder, objetivo: player(state, a.targetUid).order}));
  const max = Math.max(0, ...Object.values(totals));
  state.tieCandidates = state.players.filter((p) => max > 0 && totals[p.order] === max).map((p) => p.uid);
  state.eliminationUid = state.tieCandidates.length === 1 ? state.tieCandidates[0] : null;
  const acted = votes.map((a) => state.players.find((p) => p.order === a.actorOrder).uid);
  // With visible ballots the usual table places one seal per vote (about 0.45 s each, plus the
  // Mayor's second seal and the Payador's pointing); give that ceremony its time before the result.
  const seals = state.voteBallots.length + (state.mayorUid ? 1 : 0) + (state.counterpointPointed ? 1 : 0);
  const recountSeconds = state.config?.votosIndividuales !== false && state.voteBallots.length ?
    Math.max(state.timing.transitionSeconds, Math.min(12, Math.ceil(2 + 0.45 * seals))) : state.timing.transitionSeconds;
  transition(state, "RECUENTO_VOTOS", nowMs, recountSeconds);
  if (state.eliminationUid) notice(state, `${player(state, state.eliminationUid).name} recibió la mayoría de los votos.`,
    "VOTE_MAJORITY", [state.eliminationUid]);
  else if (state.tieCandidates.length > 1) notice(state,
    `Empate entre ${state.tieCandidates.map((uid) => player(state, uid).name).join(", ")}.`, "VOTE_TIE", state.tieCandidates);
  else notice(state, `Día ${state.round}: no hubo una mayoría única.`, "NO_MAJORITY");
  if (normal) applyAfk(state, "vote", required, acted, nowMs);
  if (!state.winner) evaluateWinner(state, nowMs);
}
function afterVoteCount(state, nowMs) {
  state.tieCandidates = state.tieCandidates.filter((uid) => player(state, uid)?.alive);
  if (!player(state, state.eliminationUid)?.alive) state.eliminationUid = null;
  if (state.eliminationUid || state.tieCandidates.length < 2) {
    enterResult(state, nowMs);
  } else if (state.voteRound === 1) {
    beginVote(state, nowMs, true);
  } else {
    const mayor = state.players.find((p) => p.role.key === "alcalde");
    const incapacityPublic = mayor && ((!mayor.alive && (state.config.revelarRolesAlMorir || state.mayorUid === mayor.uid)) ||
      (mayor.muted && state.mayorUid === mayor.uid));
    if (!mayor || incapacityPublic) {
      notice(state, "El empate se repitió. Nadie será expulsado esta jornada.", "TIE_NO_EXPULSION");
      enterResult(state, nowMs);
    } else if (!mayor.alive || mayor.muted) {
      // Same public window as a capable hidden Mayor; inability must not disclose his role.
      transition(state, "ALCALDE_DESEMPATE", nowMs, state.timing.votingSeconds);
      notice(state, "El empate se repitió. El Alcalde puede decidir entre los empatados.", "MAYOR_TIE_WINDOW", state.tieCandidates);
    }
    else if (state.tieCandidates.includes(mayor.uid)) {
      state.mayorUid = mayor.uid; state.mayorCorruption = true;
      notice(state, `${mayor.name} se reveló como Alcalde.`, "MAYOR_REVEALED", [mayor.uid]);
      state.tieCandidates = state.tieCandidates.filter((u) => u !== mayor.uid);
      if (state.tieCandidates.length === 1) {
        state.voteRound = 4; state.eliminationUid = state.tieCandidates[0];
        notice(state, "Corrupción en el pueblo: el Alcalde evitó su expulsión y el otro empatado será expulsado.",
          "MAYOR_CORRUPTION_EXPULSION", [mayor.uid, state.eliminationUid]);
        enterResult(state, nowMs);
      } else {
        transition(state, "ALCALDE_DESEMPATE", nowMs, state.timing.votingSeconds);
        notice(state, "Corrupción en el pueblo: el Alcalde evitó su expulsión y debe decidir entre los otros empatados.",
          "MAYOR_CORRUPTION_CHOICE", state.tieCandidates);
      }
    } else {
      transition(state, "ALCALDE_DESEMPATE", nowMs, state.timing.votingSeconds);
      notice(state, "El empate se repitió. El Alcalde puede decidir entre los empatados.", "MAYOR_TIE_WINDOW", state.tieCandidates);
    }
  }
}
function enterResult(state, nowMs) {
  const expelled = player(state, state.eliminationUid);
  const seconds = expelled?.alive ? Math.max(state.timing.transitionSeconds, expelled.role.key === "bufon" ? 12 : 8) :
    state.timing.transitionSeconds;
  transition(state, "RESULTADO", nowMs, seconds);
  // Death, its event and special victory are committed in the same projection.
  // Waiting until the next night loses this round's event and can skip the death
  // altogether when a departure opens the Desertor window during RESULTADO.
  applyResultElimination(state);
}
function applyResultElimination(state) {
  const expelled = player(state, state.eliminationUid);
  if (expelled?.alive) {
    expelled.alive = false; expelled.muted = false; expelled.deathCause = "VOTE";
    notice(state, `${expelled.name} fue expulsado por el pueblo.`, "DAY_EXPULSION", [expelled.uid]);
    if (expelled.role.key === "bufon") state.specialVictories.push({uid: expelled.uid, round: state.round, reason: "bufon_expulsado"});
  } else {
    state.eliminationUid = null;
    notice(state, `Día ${state.round}: nadie fue expulsado.`, "DAY_NO_EXPULSION");
  }
}
function resolveResult(state, nowMs) {
  // An in-flight RESULTADO created by the previous deployment still has a living
  // target. Finish it safely; new matches already applied this at phase entry.
  if (player(state, state.eliminationUid)?.alive) applyResultElimination(state);
  if (!evaluateWinner(state, nowMs)) { state.round++; startNight(state, nowMs); }
}
function deadlineToken(state) {
  return {matchId: state.matchId, phaseIndex: state.phaseIndex, deadlineMs: state.deadlineMs};
}
function assertPhaseProgress(current, next, nowMs) {
  if (!(next.phaseIndex > current.phaseIndex || next.winner) ||
      (!next.winner && (!Number.isSafeInteger(next.deadlineMs) || next.deadlineMs <= nowMs))) reject("phase-stuck");
}
function expirePhase(current, token, nowMs, {chooseRandomInt = randomInt} = {}) {
  if (!Number.isSafeInteger(nowMs) || nowMs <= 0) reject("invalid-server-time");
  if (current.deadlineMs === null || !token || token.matchId !== current.matchId || token.phaseIndex !== current.phaseIndex ||
      token.deadlineMs !== current.deadlineMs || current.winner || nowMs < current.deadlineMs) return {state: current, changed: false};
  const next = structuredClone(current);
  switch (next.phase) {
    case "REPARTO":
      if (!next.deserterTeam && alive(next).some((p) => p.role.key === "desertor")) {
        const choice = chooseRandomInt(2);
        if (choice !== 0 && choice !== 1) reject("invalid-random-choice");
        next.deserterTeam = choice === 0 ? TOWN : TRAITOR_TEAM;
      }
      startNight(next, nowMs); break;
    case "NOCHE": resolveNight(next, nowMs); break;
    case "AMANECER": transition(next, "DIA_DEBATE", nowMs, next.timing.discussionSeconds); break;
    case "DIA_DEBATE": case "CONTRAPUNTO": beginVote(next, nowMs, false); break;
    case "VOTACION": case "DESEMPATE_VOTACION": resolveVotes(next, nowMs); break;
    case "RECUENTO_VOTOS": afterVoteCount(next, nowMs); break;
    case "ALCALDE_DESEMPATE":
      next.eliminationUid = null;
      notice(next, "El Alcalde no decidió el empate. Nadie será expulsado.", "MAYOR_NO_DECISION");
      enterResult(next, nowMs); break;
    case "RESULTADO": resolveResult(next, nowMs); break;
    case "DESERTOR_RECONSIDERACION": closeDeserterWindow(next, nowMs); break;
    default: reject("unknown-phase");
  }
  assertPhaseProgress(current, next, nowMs);
  next.revision++;
  next.publicRevision = (current.publicRevision || 1) + 1;
  return {state: next, changed: true};
}
function roleView(p) {
  return {orden: p.order, rolKey: p.role.key, rolNombre: p.role.name,
    rolEquipo: p.role.team, rolImagen: p.role.imageResName};
}
function projectServerGame(state) {
  const terminal = state.phase === "FINALIZADA";
  const publicState = {
    protocolVersion: 3, authorityMode: "server", versionEstado: 3, matchId: state.matchId,
    revision: state.publicRevision || 1, fase: state.phase, phaseIndex: state.phaseIndex, ronda: state.round,
    limiteFaseEpochMs: state.deadlineMs, anuncioPublico: state.announcement, ganador: state.winner,
    eventosPublicos: state.events.filter((event) => event.ronda === state.round),
    alcaldeRevelado: state.mayorUid, alcaldeCorrupcion: state.mayorCorruption,
    jugadoresContrapunto: state.counterpointPlayers,
    sospechaContrapunto: state.counterpointPointed, invitadoOraculo: state.oracleGuestUid,
    rondaVoto: state.voteRound, empateVoto: state.tieCandidates, expulsadoDia: state.eliminationUid,
    // Votes and tally stay hidden until the voting window closes.
    votosTotales: isVoting(state) ? null : state.voteTotals,
    votosIndividuales: !isVoting(state) && state.config?.votosIndividuales !== false && state.voteBallots?.length ?
      state.voteBallots : null,
    listosVotar: state.phase === "DIA_DEBATE" && !state.winner ? {...readyToVote(state),
      desdeEpochMs: state.phaseStartedAtMs + READY_VOTE_MINIMUM_MS} : null,
    desertorReconsideracion: state.phase === "DESERTOR_RECONSIDERACION" ? {abierta: true, limiteEpochMs: state.deadlineMs} : null,
    ...(terminal ? {desertorBando: state.deserterTeam} : {}),
    victoriasEspeciales: state.specialVictories.map((v) => ({
      key: `${state.matchId}:${v.uid}:bufon`, jugador: player(state, v.uid).name,
      rol: "bufon", ronda: v.round,
    })),
    jugadores: state.players.map((p) => ({uidTemporal: p.uid, orden: p.order, nombre: p.name,
      publicId: p.publicId, vivo: p.alive, muteado: p.muted, causaEliminacion: p.deathCause,
      ...(terminal || (!p.alive && state.config.revelarRolesAlMorir) || state.mayorUid === p.uid ? roleView(p) : {})})),
  };
  const privateState = Object.fromEntries(state.players.filter((p) => !p.left).map((p) => [p.uid, {
    matchId: state.matchId, phaseIndex: state.phaseIndex,
    accionesConfirmadas: Object.values(state.actions).filter((a) => a.actorOrder === p.order)
      .map((a) => ({action: a.action, ...(a.targetUid ? {targetUid: a.targetUid} : {}), ...(a.team ? {team: a.team} : {})})),
    rolesVisibles: state.players.filter((t) => t.uid === p.uid ||
      (TRAITORS.has(p.role.key) && TRAITORS.has(t.role.key))).map(roleView),
    investigaciones: state.investigations.filter((r) => r.actorOrder === p.order),
    ...(p.role.key === "mercenario" ? {objetivosBloqueados: state.players.filter((t) => !canSilence(state, p, t)).map((t) => t.uid)} : {}),
    ...(p.role.key === "desertor" ? {desertorBando: state.deserterTeam, desertorCambioBando: state.deserterUsed} : {}),
    ...(p.role.key === "oraculo" ? {oraculoUsado: state.oracleUsed} : {}),
    ...(p.role.key === "payador" ? {payadorUsado: state.payadorUsed} : {}),
  }]));
  const permissions = Object.fromEntries(state.players.map((p) => [p.uid, {
    member: !p.left, matchId: state.matchId, phaseIndex: state.phaseIndex, name: p.name, alive: p.alive,
    traitor: p.alive && TRAITORS.has(p.role.key),
    publicChat: !p.left && !state.winner && (["DIA_DEBATE", "VOTACION", "DESEMPATE_VOTACION"].includes(state.phase) && p.alive && !p.muted ||
      state.phase === "DIA_DEBATE" && state.oracleGuestUid === p.uid ||
      state.phase === "CONTRAPUNTO" && p.alive && !p.muted && state.counterpointPlayers.includes(p.uid)),
    traitorChat: !state.winner && p.alive && TRAITORS.has(p.role.key) && state.phase === "NOCHE",
    reactions: !p.left && !state.winner && p.alive && !p.muted &&
      ["DIA_DEBATE", "CONTRAPUNTO", "VOTACION", "RECUENTO_VOTOS", "DESEMPATE_VOTACION", "ALCALDE_DESEMPATE"].includes(state.phase),
    deadChat: !p.left && !state.winner && !p.alive && !(state.phase === "RESULTADO" && p.uid === state.eliminationUid),
  }]));
  return {public: publicState, private: privateState, permissions};
}
module.exports = {GameActionError, MAX_ACTIONS_PER_PHASE, READY_VOTE_MINIMUM_MS, createServerGame, acceptAction, leaveServerGame, expirePhase,
  deadlineToken, projectServerGame, winnerFor, stableNoise, resolveKillVote, canReconsider, requiredNight, assertPhaseProgress};
