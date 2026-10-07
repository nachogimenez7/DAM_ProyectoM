// Sonda de revisión V3 (solo lectura del motor). Uso: node ios/docs/REVISION_CONTRATO_V3_sonda.cjs
const path = require("node:path");
const src = (m) => require(path.join(__dirname, "../../functions/src", m));
const {prepareOnlineMatch} = src("onlineStartCore");
const C = src("onlineGameCore");
const {recordsForFinishedRoom} = src("accountHistoryService");
const NOW = 1000000; let seq = 0;
function fixture(count = 14, map = "pampa") {
  const room = {hostId: "p0", estado: "esperando", jugadoresEsperados: count, codigoSala: "ABCDE", mapa: map,
    configLobby: {presetRoles: "RECOMMENDED", votacionSeg: 30}};
  const prepared = prepareOnlineMatch({requesterId: "p0", room, players: Array.from({length: count}, (_, i) => ({id: `p${i}`, order: i,
    name: `J${i}`, activeInMatch: true, ready: true, publicId: `${i + 1}`})), matchId: "m1", nowMs: NOW, randomInt: () => 0});
  const s = C.createServerGame({roomId: "room", prepared, nowMs: NOW}); s._prepared = prepared; return s;
}
const role = (s, k) => s.players.find((p) => p.role.key === k);
const roles = (s, k) => s.players.filter((p) => p.role.key === k);
const P = (s, uid) => s.players.find((p) => p.uid === uid);
function phase(s, v, extra = {}) { s.phase = v; s.phaseIndex++; s.actions = {}; s.phaseActionCounts = {}; s.phaseStartedAtMs = NOW; s.deadlineMs = NOW + 40000; Object.assign(s, extra); return s; }
function act(s, uid, action, f = {}) { return C.acceptAction(s, uid, {matchId: s.matchId, phaseIndex: s.phaseIndex, requestId: `req_${++seq}_xx`, action, ...f}, s.phaseStartedAtMs + 1).state; }
function tryAct(s, uid, action, f) { try { return {ok: true, s: act(s, uid, action, f)}; } catch (e) { return {ok: false, code: e.code}; } }
const close = (s) => C.expirePhase(s, C.deadlineToken(s), s.deadlineMs).state;
const log = (k, v) => console.log(k.padEnd(52), JSON.stringify(v));
function deserterTable(round, towns = 3) {
  const s = fixture(14); const keep = new Set(["desertor", "mercenario", "asesino"]); let t = 0;
  s.players.forEach((p) => { p.alive = keep.has(p.role.key) || (p.role.key === "aldeano" && t++ < towns); });
  s.deserterTeam = "Pueblo"; s.round = round; return s;
}
function killTown(s) {
  phase(s, "NOCHE"); const victim = s.players.find((p) => p.alive && p.role.key === "aldeano");
  for (const k of roles(s, "asesino")) s = act(s, k.uid, "matar", {targetUid: victim.uid});
  s = act(s, role(s, "mercenario").uid, "silenciar", {targetUid: role(s, "desertor").uid});
  return close(s);
}

// DES-09 / DES-14 con la regla de ronda 4
{
  let s = killTown(deserterTable(4));
  log("DES-09 r4: fase/ganador/desertor silenciado", [s.phase, s.winner, P(s, role(s, "desertor").uid).muted]);
  const r = tryAct(s, role(s, "desertor").uid, "desertor_rethink", {team: "Traidores"});
  log("DES-09 r4: silenciado elige Traidores", r.ok ? [r.s.phase, r.s.winner, r.s.deserterTeam] : r.code);
  const early = killTown(deserterTable(3));
  log("DES-14 r3: sin ventana", [early.phase, early.winner]);
  let debate = deserterTable(3, 5); phase(debate, "DIA_DEBATE");
  log("DES r3 debate: rethink", tryAct(debate, role(debate, "desertor").uid, "desertor_rethink", {team: "Traidores"}).code);
  debate.round = 4; log("DES r4 debate: rethink aceptado", tryAct(debate, role(debate, "desertor").uid, "desertor_rethink", {team: "Traidores"}).ok);
}
// R-04 ventana del Alcalde
for (const [label, setup] of [
  ["capaz oculto", () => {}],
  ["silenciado oculto", (s, m) => { m.muted = true; }],
  ["silenciado revelado", (s, m) => { m.muted = true; s.mayorUid = m.uid; }],
  ["muerto en secreto", (s, m) => { m.alive = false; m.deathCause = "NIGHT"; }],
]) {
  let s = fixture(8); const m = role(s, "alcalde"); const others = s.players.filter((p) => p.uid !== m.uid);
  setup(s, m); phase(s, "RECUENTO_VOTOS", {voteRound: 2, tieCandidates: [others[0].uid, others[1].uid], eliminationUid: null});
  s = close(s); const after = s.phase === "ALCALDE_DESEMPATE" ? close(s) : s;
  log(`R-04 ${label}`, [s.phase, s.events.at(-1).codigo, after.eliminationUid, after.announcement]);
}
// R-03 revisión pública y R-05 eventos
{
  let s = fixture(8, "grecia"); const dead = s.players.find((p) => p.role.key === "aldeano"); dead.alive = false;
  s.round = 2; phase(s, "NOCHE"); const before = s.publicRevision;
  s = act(s, role(s, "medico").uid, "salvar", {targetUid: role(s, "policia").uid});
  s = act(s, role(s, "oraculo").uid, "invitar_muerto", {targetUid: dead.uid});
  const mid = s.publicRevision; s = close(s);
  log("R-03 publicRevision noche/tras 2 secretas/amanecer", [before, mid, s.publicRevision]);
  log("R-05 eventos del amanecer", s.events.map((e) => e.codigo));
}
{
  const s = fixture(5); s.players.forEach((p) => { p.nightAfk = 1; }); phase(s, "NOCHE");
  const t = close(fixture(5)); // REPARTO -> NOCHE
  log("R-05 códigos genéricos (AFK / noche)", [close(s).events.map((e) => e.codigo), t.events.map((e) => e.codigo)]);
}
// Abandono: jugador ya muerto que sale
{
  let s = fixture(8); const villager = role(s, "aldeano");
  villager.alive = false; villager.deathCause = "NIGHT"; phase(s, "DIA_DEBATE");
  s = C.leaveServerGame(s, villager.uid, NOW + 1).state;
  log("ABANDONO muerto: causa tras salir", P(s, villager.uid).deathCause);
  s.players.filter((p) => ["asesino", "espia"].includes(p.role.key)).forEach((p) => { p.alive = false; });
  phase(s, "RESULTADO"); s = close(s);
  const rec = recordsForFinishedRoom("room", {partidaInicial: s._prepared.payloads.initialMatch, estadoPartida: C.projectServerGame(s).public}, NOW);
  log("ABANDONO muerto: gana Pueblo / won del Aldeano", [s.winner, rec.find((r) => r.uid === villager.uid)?.won]);
}
// Abandono de un traidor durante la ventana del Desertor
{
  let s = killTown(deserterTable(4, 4));
  log("ABANDONO en ventana: antes", [s.phase, s.players.filter((p) => p.alive).map((p) => p.role.key)]);
  const merc = role(s, "mercenario");
  s = C.leaveServerGame(s, merc.uid, NOW + 2).state;
  log("ABANDONO en ventana: fase/ganador", [s.phase, s.winner, C.winnerFor(s)]);
  const r = tryAct(s, role(s, "desertor").uid, "desertor_rethink", {team: "Traidores"});
  log("ABANDONO en ventana: elección del Desertor", r.ok || r.code);
  const e1 = C.expirePhase(s, C.deadlineToken(s), s.deadlineMs);
  const e2 = C.expirePhase(e1.state, C.deadlineToken(e1.state), s.deadlineMs + 60000);
  log("ABANDONO en ventana: vencer x2 (fase, phaseIndex)", [e1.state.phase, e1.state.phaseIndex, e2.changed, e2.state.phase, e2.state.phaseIndex]);
}
// AFK-06 y revancha (estado limpio del motor)
{
  let s = fixture(5); phase(s, "VOTACION"); s.players.forEach((p) => { p.voteAfk = 1; });
  log("AFK-06 cancelación", close(s).winner);
  const b = fixture(8);
  log("REV estado nuevo", [b.oracleUsed, b.payadorUsed, b.deserterTeam, b.deserterUsed, b.mayorUid,
    b.players.every((p) => p.lastSilencedRound === null && !p.nightAfk && !p.voteAfk && !p.left), b.round, b.phaseIndex]);
}
