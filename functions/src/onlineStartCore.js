"use strict";

const crypto = require("node:crypto");

const ROOM_STATE_WAITING = "esperando";
const ROOM_STATE_IN_GAME = "en_juego";
const TOWN_TEAM = "Pueblo";
const TRAITOR_TEAM = "Traidores";
const NEUTRAL_TEAM = "Neutral";
const CURRENT_STATE_SCHEMA = 2;
const TEST_MIN_PLAYERS = 3;
const MIN_PLAYERS = 5;
const MAX_PLAYERS = 15;

const ROLE_KEYS = Object.freeze({
  VILLAGER: "aldeano",
  DETECTIVE: "policia",
  DOCTOR: "medico",
  MAYOR: "alcalde",
  ASSASSIN: "asesino",
  SPY: "espia",
  MERCENARY: "mercenario",
  DESERTER: "desertor",
  PAYADOR: "payador",
  JESTER: "bufon",
  ORACLE: "oraculo",
});

const EDITABLE_ROLE_KEYS = Object.freeze([
  ROLE_KEYS.VILLAGER,
  ROLE_KEYS.DETECTIVE,
  ROLE_KEYS.DOCTOR,
  ROLE_KEYS.ASSASSIN,
  ROLE_KEYS.MERCENARY,
  ROLE_KEYS.MAYOR,
  ROLE_KEYS.DESERTER,
  ROLE_KEYS.SPY,
  ROLE_KEYS.PAYADOR,
  ROLE_KEYS.ORACLE,
  ROLE_KEYS.JESTER,
]);

const MAPS = Object.freeze({
  pampa: Object.freeze({key: "pampa", name: "Pampa", imageSuffix: "gaucho", exclusiveRole: ROLE_KEYS.PAYADOR}),
  grecia: Object.freeze({key: "grecia", name: "Grecia", imageSuffix: "griego", exclusiveRole: ROLE_KEYS.ORACLE}),
  medieval: Object.freeze({key: "medieval", name: "Medieval", imageSuffix: "medieval", exclusiveRole: ROLE_KEYS.JESTER}),
});

const ROLE_DEFINITIONS = Object.freeze({
  [ROLE_KEYS.VILLAGER]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 5}),
  [ROLE_KEYS.DETECTIVE]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 5}),
  [ROLE_KEYS.DOCTOR]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 5}),
  [ROLE_KEYS.MAYOR]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 8}),
  [ROLE_KEYS.ASSASSIN]: Object.freeze({team: TRAITOR_TEAM, minimumPlayers: 5}),
  [ROLE_KEYS.SPY]: Object.freeze({team: TRAITOR_TEAM, minimumPlayers: 10}),
  [ROLE_KEYS.MERCENARY]: Object.freeze({team: TRAITOR_TEAM, minimumPlayers: 7}),
  [ROLE_KEYS.DESERTER]: Object.freeze({team: NEUTRAL_TEAM, minimumPlayers: 14}),
  [ROLE_KEYS.PAYADOR]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 8, mapKey: "pampa"}),
  [ROLE_KEYS.JESTER]: Object.freeze({team: NEUTRAL_TEAM, minimumPlayers: 8, mapKey: "medieval"}),
  [ROLE_KEYS.ORACLE]: Object.freeze({team: TOWN_TEAM, minimumPlayers: 8, mapKey: "grecia"}),
});

class OnlineStartError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "OnlineStartError";
    this.code = code;
  }
}

function clamp(value, minimum, maximum) {
  return Math.min(Math.max(value, minimum), maximum);
}

function integerOr(value, fallback) {
  return Number.isInteger(value) ? value : fallback;
}

function mapFor(key) {
  return MAPS[key] || MAPS.pampa;
}

function maxAssassinsFor(playerCount) {
  if (playerCount >= 13) return 3;
  if (playerCount >= 8) return 2;
  return 1;
}

function roleAvailableOnMap(roleKey, mapKey) {
  const definition = ROLE_DEFINITIONS[roleKey];
  return Boolean(definition) && (!definition.mapKey || definition.mapKey === mapKey);
}

function roleCompositionPreset(playerCount, mapKey, preset) {
  const count = clamp(playerCount, MIN_PLAYERS, MAX_PLAYERS);
  const counts = {
    [ROLE_KEYS.DETECTIVE]: 1,
    [ROLE_KEYS.DOCTOR]: 1,
    [ROLE_KEYS.ASSASSIN]: 1,
  };
  if (preset === "RECOMMENDED") {
    if (count >= 7) counts[ROLE_KEYS.MERCENARY] = 1;
    if (count >= 8) counts[ROLE_KEYS.MAYOR] = 1;
    if (count >= 8) counts[mapFor(mapKey).exclusiveRole] = 1;
    if (count >= 14) counts[ROLE_KEYS.DESERTER] = 1;
    if (count >= 10) counts[ROLE_KEYS.SPY] = 1;
    if (count >= 13) counts[ROLE_KEYS.ASSASSIN] = 2;
  } else if (preset === "CHAOTIC") {
    counts[ROLE_KEYS.ASSASSIN] = maxAssassinsFor(count);
    if (count >= 7) counts[ROLE_KEYS.MERCENARY] = 1;
    if (count >= 8) counts[ROLE_KEYS.MAYOR] = 1;
    if (count >= 8) counts[mapFor(mapKey).exclusiveRole] = 1;
    if (count >= 14) counts[ROLE_KEYS.DESERTER] = 1;
    if (count >= 10) counts[ROLE_KEYS.SPY] = 1;
  }
  for (const roleKey of EDITABLE_ROLE_KEYS) {
    if (!(roleKey in counts)) counts[roleKey] = 0;
  }
  const specialCount = Object.values(counts).reduce((sum, value) => sum + value, 0);
  counts[ROLE_KEYS.VILLAGER] = Math.max(count - specialCount, 0);
  return counts;
}

function onlineSafeRoleComposition(playerCount, mapKey) {
  const count = clamp(playerCount, TEST_MIN_PLAYERS, MAX_PLAYERS);
  const counts = {
    [ROLE_KEYS.DETECTIVE]: 1,
    [ROLE_KEYS.DOCTOR]: 1,
    [ROLE_KEYS.ASSASSIN]: 1,
  };
  if (count >= 7) counts[ROLE_KEYS.MERCENARY] = 1;
  if (count >= 8) counts[ROLE_KEYS.MAYOR] = 1;
  if (count >= 8 && MAPS[mapKey]) counts[mapFor(mapKey).exclusiveRole] = 1;
  if (count >= 14) counts[ROLE_KEYS.DESERTER] = 1;
  if (count >= 10) counts[ROLE_KEYS.SPY] = 1;
  const specialCount = Object.values(counts).reduce((sum, value) => sum + value, 0);
  counts[ROLE_KEYS.VILLAGER] = Math.max(count - specialCount, 0);
  return counts;
}

function parseCustomCounts(raw) {
  if (typeof raw !== "string") return null;
  const values = raw.split(",");
  if (values.length !== EDITABLE_ROLE_KEYS.length) return null;
  const counts = {};
  for (let index = 0; index < EDITABLE_ROLE_KEYS.length; index += 1) {
    const parsed = Number.parseInt(values[index], 10);
    counts[EDITABLE_ROLE_KEYS[index]] = Number.isFinite(parsed) ? clamp(parsed, 0, MAX_PLAYERS) : 0;
  }
  return counts;
}

function normalizedCustomComposition(playerCount, mapKey, sourceCounts, testMode) {
  const minimum = testMode ? TEST_MIN_PLAYERS : MIN_PLAYERS;
  const count = clamp(playerCount, minimum, MAX_PLAYERS);
  const normalized = {};
  for (const roleKey of EDITABLE_ROLE_KEYS) {
    const definition = ROLE_DEFINITIONS[roleKey];
    const allowedInThreePlayerTest = testMode && [
      ROLE_KEYS.ASSASSIN,
      ROLE_KEYS.DOCTOR,
      ROLE_KEYS.DETECTIVE,
    ].includes(roleKey);
    const enabled = roleAvailableOnMap(roleKey, mapKey) &&
      (count >= definition.minimumPlayers || allowedInThreePlayerTest);
    normalized[roleKey] = enabled ? Math.max(integerOr(sourceCounts[roleKey], 0), 0) : 0;
  }
  normalized[ROLE_KEYS.ASSASSIN] = clamp(
    normalized[ROLE_KEYS.ASSASSIN],
    1,
    maxAssassinsFor(count),
  );

  const nonVillagers = () => EDITABLE_ROLE_KEYS
    .filter((key) => key !== ROLE_KEYS.VILLAGER)
    .reduce((sum, key) => sum + normalized[key], 0);
  let overflow = Math.max(nonVillagers() - count, 0);
  const removableOrder = [
    ROLE_KEYS.SPY,
    ROLE_KEYS.DESERTER,
    mapFor(mapKey).exclusiveRole,
    ROLE_KEYS.MAYOR,
    ROLE_KEYS.MERCENARY,
    ROLE_KEYS.DOCTOR,
    ROLE_KEYS.DETECTIVE,
  ];
  for (const roleKey of removableOrder) {
    if (overflow <= 0) break;
    const removed = Math.min(normalized[roleKey] || 0, overflow);
    normalized[roleKey] = (normalized[roleKey] || 0) - removed;
    overflow -= removed;
  }
  normalized[ROLE_KEYS.VILLAGER] = Math.max(count - nonVillagers(), 0);
  return normalized;
}

function normalizedTiming(raw) {
  const source = raw && typeof raw === "object" ? raw : {};
  return {
    transitionSeconds: clamp(integerOr(source.transicionSeg, 4), 1, 10),
    nightSeconds: clamp(integerOr(source.nocheSeg, 40), 10, 90),
    discussionSeconds: clamp(integerOr(source.discusionSeg, 120), 30, 180),
    votingSeconds: clamp(integerOr(source.votacionSeg, 20), 10, 60),
  };
}

function lobbyConfigFromRoom(raw, playerCount, mapKey, testMode) {
  const source = raw && typeof raw === "object" ? raw : {};
  const timing = normalizedTiming(source);
  let counts;
  if (playerCount < MIN_PLAYERS) {
    counts = onlineSafeRoleComposition(playerCount, mapKey);
  } else if (["CLASSIC", "CHAOTIC", "RECOMMENDED"].includes(source.presetRoles)) {
    counts = roleCompositionPreset(playerCount, mapKey, source.presetRoles);
  } else if (source.presetRoles === "PERSONALIZADO") {
    const custom = parseCustomCounts(source.roles) || roleCompositionPreset(playerCount, mapKey, "RECOMMENDED");
    counts = normalizedCustomComposition(playerCount, mapKey, custom, testMode);
  } else {
    counts = roleCompositionPreset(playerCount, mapKey, "RECOMMENDED");
  }
  return {
    timing,
    revealRolesOnDeath: source.revelarRolesAlMorir === true,
    showIndividualVotes: source.votosIndividuales !== false,
    roleCounts: counts,
  };
}

function resolveMap(votes, currentMapKey, hostTieBreakChoice) {
  const mapKeys = Object.keys(MAPS);
  const validVotes = votes.filter((vote) => mapKeys.includes(vote.mapKey));
  const counts = Object.fromEntries(mapKeys.map((mapKey) => [
    mapKey,
    validVotes.filter((vote) => vote.mapKey === mapKey).length,
  ]));
  const totalVotes = Object.values(counts).reduce((sum, value) => sum + value, 0);
  if (totalVotes === 0) {
    return {status: "selected", mapKey: MAPS[currentMapKey] ? currentMapKey : "pampa"};
  }
  const highest = Math.max(...Object.values(counts));
  const leaders = mapKeys.filter((mapKey) => counts[mapKey] === highest);
  if (leaders.length === 1) return {status: "selected", mapKey: leaders[0]};
  if (hostTieBreakChoice && leaders.includes(hostTieBreakChoice)) {
    return {status: "selected", mapKey: hostTieBreakChoice};
  }
  return {status: "tie_break_required", mapKeys: leaders};
}

function evaluateStart({requesterId, room, players, hostTieBreakChoice}) {
  if (!requesterId || (requesterId !== room.hostActivoId && requesterId !== room.hostId)) {
    throw new OnlineStartError("host-required", "Solo el anfitrion puede iniciar.");
  }
  if (room.partidaInicialCreada === true || room.partidaInicial) {
    return {
      status: "already_started",
      matchId: room.partidaInicial && typeof room.partidaInicial.matchId === "string" ?
        room.partidaInicial.matchId : "",
    };
  }
  if (room.estado !== ROOM_STATE_WAITING) {
    throw new OnlineStartError("room-not-waiting", "La sala ya no esta esperando jugadores.");
  }
  if (room.limpiezaPendiente === true) {
    throw new OnlineStartError("cleanup-pending", "La sala todavia esta limpiando la partida anterior.");
  }
  const expectedPlayers = integerOr(room.jugadoresEsperados, 0);
  const orderedPlayers = players
    .filter((player) => player.activeInMatch)
    .sort((left, right) => left.order - right.order ||
      left.name.toLocaleLowerCase("es").localeCompare(right.name.toLocaleLowerCase("es"), "es") ||
      left.id.localeCompare(right.id));
  if (orderedPlayers.length !== expectedPlayers) {
    throw new OnlineStartError("player-count-mismatch", "Faltan jugadores para iniciar.");
  }
  if (orderedPlayers.some((player) => !player.ready)) {
    throw new OnlineStartError("players-not-ready", "Todavia faltan jugadores listos.");
  }
  const mapResolution = resolveMap(
    orderedPlayers.map((player) => ({playerId: player.id, mapKey: player.mapVote})),
    room.mapa,
    hostTieBreakChoice,
  );
  if (mapResolution.status === "tie_break_required") return mapResolution;
  return {status: "ready", mapKey: mapResolution.mapKey, orderedPlayers};
}

function roleName(roleKey, mapKey) {
  if (mapKey === "medieval") {
    return {
      aldeano: "Aldeana", policia: "Detective", medico: "Médico", alcalde: "Alcalde",
      asesino: "Asesino", espia: "Espía", mercenario: "Mercenario", desertor: "Desertora",
      bufon: "Bufón",
    }[roleKey] || "Aldeana";
  }
  if (mapKey === "grecia") {
    return {
      aldeano: "Aldeano", policia: "Detective", medico: "Médico", alcalde: "Alcalde",
      asesino: "Asesina", espia: "Espía", mercenario: "Mercenario", desertor: "Desertor",
      oraculo: "Oráculo",
    }[roleKey] || "Aldeano";
  }
  return {
    aldeano: "Aldeano", policia: "Comisario", medico: "Médica", alcalde: "Alcaldesa",
    asesino: "Asesino", espia: "Espía", mercenario: "Mercenario", desertor: "Desertor",
    payador: "Payador",
  }[roleKey] || "Aldeano";
}

function gameRole(roleKey, mapKey) {
  const definition = ROLE_DEFINITIONS[roleKey] || ROLE_DEFINITIONS[ROLE_KEYS.VILLAGER];
  const safeRoleKey = ROLE_DEFINITIONS[roleKey] ? roleKey : ROLE_KEYS.VILLAGER;
  const imageKey = safeRoleKey === ROLE_KEYS.DETECTIVE ? "detective" : safeRoleKey;
  return {
    key: safeRoleKey,
    name: roleName(safeRoleKey, mapKey),
    team: definition.team,
    imageResName: `rol_${imageKey}_${mapFor(mapKey).imageSuffix}`,
  };
}

function secureShuffle(values, randomInt = crypto.randomInt) {
  const shuffled = [...values];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = randomInt(index + 1);
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  return shuffled;
}

function assignRoles(players, mapKey, config, randomInt) {
  const deck = [];
  for (const roleKey of EDITABLE_ROLE_KEYS) {
    const count = Math.max(integerOr(config.roleCounts[roleKey], 0), 0);
    for (let index = 0; index < count; index += 1) deck.push(gameRole(roleKey, mapKey));
  }
  if (deck.length !== players.length) {
    throw new OnlineStartError("invalid-role-deck", "La composicion de roles no coincide con los jugadores.");
  }
  const shuffledRoles = secureShuffle(deck, randomInt);
  return players.map((player, index) => ({...player, role: shuffledRoles[index], alive: true}));
}

function publicRoleLabel(roleKey, count) {
  const singular = {
    aldeano: "Aldeano", policia: "Detective", medico: "Medico", alcalde: "Alcalde",
    asesino: "Asesino", mercenario: "Mercenario", espia: "Espia", desertor: "Desertor",
    payador: "Payador", oraculo: "Oraculo", bufon: "Bufon",
  }[roleKey] || roleKey.charAt(0).toUpperCase() + roleKey.slice(1);
  if (count === 1) return singular;
  return {
    aldeano: "Aldeanos", policia: "Detectives", medico: "Medicos", alcalde: "Alcaldes",
    asesino: "Asesinos", mercenario: "Mercenarios", espia: "Espias", desertor: "Desertores",
    payador: "Payadores", oraculo: "Oraculos", bufon: "Bufones",
  }[roleKey] || singular;
}

function roleAnnouncement(assignedPlayers) {
  const counts = {};
  for (const player of assignedPlayers) counts[player.role.key] = (counts[player.role.key] || 0) + 1;
  const orderedKeys = [...EDITABLE_ROLE_KEYS.filter((key) => key !== ROLE_KEYS.MERCENARY)];
  for (const key of Object.keys(counts)) if (!orderedKeys.includes(key)) orderedKeys.push(key);
  const summary = orderedKeys
    .filter((key) => (counts[key] || 0) > 0)
    .map((key) => `${counts[key]} ${publicRoleLabel(key, counts[key])}`)
    .join(", ");
  return `Dios preparo una partida local. En juego: ${summary}. ` +
    "Todos conocen la composicion; las identidades siguen ocultas.";
}

function rolePayload(order, role) {
  return {
    orden: order,
    rolKey: role.key,
    rolNombre: role.name,
    rolEquipo: role.team,
    rolImagen: role.imageResName,
  };
}

function buildPayloads({matchId, room, mapKey, players, config, updatedBy, nowMs}) {
  const announcement = roleAnnouncement(players);
  const initialMatch = {
    matchId,
    codigoSala: typeof room.codigoSala === "string" && room.codigoSala ? room.codigoSala : "",
    mapa: mapKey,
    mapaNombre: mapFor(mapKey).name,
    fase: "REPARTO",
    ronda: 1,
    creadaEnLocal: nowMs,
    config: {
      transicionSeg: config.timing.transitionSeconds,
      nocheSeg: config.timing.nightSeconds,
      discusionSeg: config.timing.discussionSeconds,
      votacionSeg: config.timing.votingSeconds,
      revelarRolesAlMorir: config.revealRolesOnDeath,
      votosIndividuales: config.showIndividualVotes,
      roles: config.roleCounts,
    },
    jugadores: players.map((player, index) => ({
      orden: index,
      uidTemporal: player.id,
      publicId: player.publicId,
      simulado: false,
      nombre: player.name,
      inicial: player.initial,
    })),
  };
  const matchState = {
    versionEstado: CURRENT_STATE_SCHEMA,
    fase: "REPARTO",
    ronda: 1,
    phaseIndex: 0,
    anuncioPublico: announcement,
    actualizadaEnLocal: nowMs,
    actualizadaPor: updatedBy,
  };
  const privateRolesByPlayer = {};
  const realtimeAccess = {};
  players.forEach((player, playerIndex) => {
    const visibleRoles = players.flatMap((candidate, index) => {
      const traitorAlly = player.role.team === TRAITOR_TEAM && candidate.role.team === TRAITOR_TEAM;
      return index === playerIndex || traitorAlly ? [rolePayload(index, candidate.role)] : [];
    });
    privateRolesByPlayer[player.id] = {matchId, playerId: player.id, visibleRoles};
    realtimeAccess[player.id] = {
      name: player.name,
      inLobby: false,
      alive: true,
      traitor: player.role.team === TRAITOR_TEAM,
    };
  });
  const roleSummary = Object.entries(players.reduce((counts, player) => {
    counts[player.role.key] = (counts[player.role.key] || 0) + 1;
    return counts;
  }, {})).sort(([left], [right]) => left.localeCompare(right))
    .map(([key, count]) => `${key}:${count}`).join(",");
  return {initialMatch, matchState, privateRolesByPlayer, realtimeAccess, roleSummary};
}

function prepareOnlineMatch({requesterId, room, players, hostTieBreakChoice, matchId, nowMs, randomInt}) {
  const decision = evaluateStart({requesterId, room, players, hostTieBreakChoice});
  if (decision.status !== "ready") return decision;
  const testMode = room.modoPrueba === true;
  const config = lobbyConfigFromRoom(
    room.configLobby,
    decision.orderedPlayers.length,
    decision.mapKey,
    testMode,
  );
  const assignedPlayers = assignRoles(decision.orderedPlayers, decision.mapKey, config, randomInt);
  const payloads = buildPayloads({
    matchId,
    room,
    mapKey: decision.mapKey,
    players: assignedPlayers,
    config,
    updatedBy: requesterId,
    nowMs,
  });
  return {...decision, assignedPlayers, config, payloads};
}

function playerFromDocument(id, data) {
  const rawName = typeof data.nombreSala === "string" && data.nombreSala.trim() ?
    data.nombreSala : data.nombre;
  const withoutPublicId = typeof rawName === "string" ? rawName.replace(/\s+#\d+\s*$/, "").trim() : "";
  const name = withoutPublicId.slice(0, 18);
  if (!id || !name) return null;
  return {
    id,
    name,
    initial: Array.from(name)[0].toLocaleUpperCase("es"),
    ready: data.listo === true,
    order: Number.isInteger(data.orden) ? data.orden : Number.MAX_SAFE_INTEGER,
    activeInMatch: data.activoEnPartida !== false,
    mapVote: MAPS[data.votoMapa] ? data.votoMapa : null,
    publicId: typeof data.publicId === "string" ? data.publicId : "",
  };
}

module.exports = {
  CURRENT_STATE_SCHEMA,
  EDITABLE_ROLE_KEYS,
  MAPS,
  OnlineStartError,
  ROLE_KEYS,
  TRAITOR_TEAM,
  assignRoles,
  buildPayloads,
  evaluateStart,
  lobbyConfigFromRoom,
  normalizedCustomComposition,
  playerFromDocument,
  prepareOnlineMatch,
  resolveMap,
  roleCompositionPreset,
};
