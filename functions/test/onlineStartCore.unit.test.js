"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  OnlineStartError,
  TRAITOR_TEAM,
  buildPayloads,
  evaluateStart,
  lobbyConfigFromRoom,
  normalizedCustomComposition,
  prepareOnlineMatch,
  resolveMap,
} = require("../src/onlineStartCore");

function player(index, overrides = {}) {
  return {
    id: index === 0 ? "host" : `p${index}`,
    name: index === 0 ? "Host" : `Jugador ${index}`,
    initial: index === 0 ? "H" : "J",
    ready: true,
    order: index,
    activeInMatch: true,
    mapVote: null,
    publicId: `#${index + 1}`,
    ...overrides,
  };
}

function players(count) {
  return Array.from({length: count}, (_, index) => player(index));
}

function room(count = 5, overrides = {}) {
  return {
    estado: "esperando",
    limpiezaPendiente: false,
    hostId: "host",
    hostActivoId: "host",
    partidaInicialCreada: false,
    jugadoresEsperados: count,
    modoPrueba: count < 5,
    mapa: "pampa",
    codigoSala: "ABC234",
    configLobby: {
      transicionSeg: 4,
      nocheSeg: 40,
      discusionSeg: 120,
      votacionSeg: 20,
      revelarRolesAlMorir: false,
      votosIndividuales: true,
      presetRoles: "RECOMMENDED",
      roles: "0,1,1,1,0,0,0,0,0,0,0",
    },
    ...overrides,
  };
}

test("rechaza a quien no es anfitrion", () => {
  assert.throws(
    () => evaluateStart({
      requesterId: "intruso",
      room: room(),
      players: players(5),
      hostTieBreakChoice: null,
    }),
    (error) => error instanceof OnlineStartError && error.code === "host-required",
  );
});

test("el inicio existente es idempotente", () => {
  const result = evaluateStart({
    requesterId: "host",
    room: room(5, {
      partidaInicialCreada: true,
      partidaInicial: {matchId: "match-existing"},
    }),
    players: [],
    hostTieBreakChoice: null,
  });
  assert.deepEqual(result, {status: "already_started", matchId: "match-existing"});
});

test("un usuario ajeno no puede aprovechar un inicio repetido", () => {
  assert.throws(
    () => evaluateStart({
      requesterId: "intruso",
      room: room(5, {
        partidaInicialCreada: true,
        partidaInicial: {matchId: "match-existing"},
      }),
      players: [],
      hostTieBreakChoice: null,
    }),
    (error) => error instanceof OnlineStartError && error.code === "host-required",
  );
});

test("cantidad y listo se verifican en el backend", () => {
  assert.throws(
    () => evaluateStart({
      requesterId: "host",
      room: room(),
      players: players(4),
      hostTieBreakChoice: null,
    }),
    (error) => error.code === "player-count-mismatch",
  );
  assert.throws(
    () => evaluateStart({
      requesterId: "host",
      room: room(),
      players: players(5).map((value, index) => index === 4 ? {...value, ready: false} : value),
      hostTieBreakChoice: null,
    }),
    (error) => error.code === "players-not-ready",
  );
});

test("el empate solo acepta uno de los mapas lideres", () => {
  const votes = [
    {mapKey: "pampa"},
    {mapKey: "grecia"},
    {mapKey: "medieval"},
  ];
  assert.deepEqual(resolveMap(votes, "pampa", null), {
    status: "tie_break_required",
    mapKeys: ["pampa", "grecia", "medieval"],
  });
  assert.deepEqual(resolveMap(votes, "pampa", "grecia"), {
    status: "selected",
    mapKey: "grecia",
  });
});

test("las salas de prueba de tres jugadores conservan el reparto seguro", () => {
  const config = lobbyConfigFromRoom(room(3).configLobby, 3, "pampa", true);
  assert.equal(config.roleCounts.asesino, 1);
  assert.equal(config.roleCounts.medico, 1);
  assert.equal(config.roleCounts.policia, 1);
  assert.equal(config.roleCounts.aldeano, 0);
});

test("una composicion personalizada elimina roles de otro mapa y limita asesinos", () => {
  const normalized = normalizedCustomComposition(
    10,
    "pampa",
    {
      aldeano: 0,
      policia: 1,
      medico: 1,
      asesino: 9,
      mercenario: 1,
      alcalde: 1,
      desertor: 1,
      espia: 1,
      payador: 1,
      oraculo: 1,
      bufon: 1,
    },
    false,
  );
  assert.equal(normalized.asesino, 2);
  assert.equal(normalized.payador, 1);
  assert.equal(normalized.oraculo, 0);
  assert.equal(normalized.bufon, 0);
  assert.equal(Object.values(normalized).reduce((sum, count) => sum + count, 0), 10);
});

test("el payload publico no contiene asignaciones privadas", () => {
  const prepared = prepareOnlineMatch({
    requesterId: "host",
    room: room(10),
    players: players(10),
    hostTieBreakChoice: null,
    matchId: "match-12345678",
    nowMs: 1234,
    randomInt: () => 0,
  });
  const privateKeys = new Set(["rolKey", "rolNombre", "rolEquipo", "rolImagen", "rolesVisibles"]);
  const keys = collectKeys(prepared.payloads.initialMatch);
  assert.equal([...privateKeys].some((key) => keys.has(key)), false);
  assert.equal(collectKeys(prepared.payloads.matchState).has("rolKey"), false);
});

test("cada ciudadano ve un rol y cada traidor ve a sus aliados", () => {
  const prepared = prepareOnlineMatch({
    requesterId: "host",
    room: room(10),
    players: players(10),
    hostTieBreakChoice: null,
    matchId: "match-12345678",
    nowMs: 1234,
    randomInt: () => 0,
  });
  const traitors = prepared.assignedPlayers.filter((value) => value.role.team === TRAITOR_TEAM);
  const town = prepared.assignedPlayers.find((value) => value.role.team !== TRAITOR_TEAM);
  assert.ok(town);
  assert.equal(
    prepared.payloads.privateRolesByPlayer[town.id].visibleRoles.length,
    1,
  );
  for (const traitor of traitors) {
    assert.equal(
      prepared.payloads.privateRolesByPlayer[traitor.id].visibleRoles.length,
      traitors.length,
    );
  }
});

test("el constructor de payload exige un reparto completo", () => {
  assert.throws(
    () => buildPayloads({
      matchId: "match",
      room: room(3),
      mapKey: "pampa",
      players: players(3),
      config: lobbyConfigFromRoom(room(3).configLobby, 3, "pampa", true),
      updatedBy: "host",
      nowMs: 1,
    }),
    TypeError,
  );
});

function collectKeys(value) {
  const keys = new Set();
  if (Array.isArray(value)) {
    value.forEach((nested) => collectKeys(nested).forEach((key) => keys.add(key)));
  } else if (value && typeof value === "object") {
    Object.entries(value).forEach(([key, nested]) => {
      keys.add(key);
      collectKeys(nested).forEach((nestedKey) => keys.add(nestedKey));
    });
  }
  return keys;
}
