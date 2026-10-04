"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {historyId, recordsForFinishedRoom} = require("../src/accountHistoryService");
const {room, roster} = require("./fixtures/accountHistoryRoom");
test("victorias del pueblo, traidores, desertor y especial coinciden con el motor", () => {
  const value = room();
  assert.deepEqual(recordsForFinishedRoom("room", value, 1000).map((r) => r.won), [true, false, true, true, false]);
  value.estadoPartida.ganador = "Traidores";
  assert.deepEqual(recordsForFinishedRoom("room", value, 1000).map((r) => r.won), [false, true, false, true, true]);
});
test("cancelada, sin final o roster ambiguo no producen historial", () => {
  for (const mutate of [
    (r) => { r.estadoPartida.ganador = "Cancelada"; },
    (r) => { delete r.estadoPartida.ganador; },
    (r) => { r.estadoPartida.jugadores[1].orden = 0; },
    (r) => { r.estadoPartida.jugadores[1].nombre = "Otra identidad"; },
    (r) => { r.partidaInicial.jugadores[1].uidTemporal = roster[0].uidTemporal; },
    (r) => { r.partidaInicial.mapa = "desconocido"; },
    (r) => { r.estadoPartida.jugadores[1] = null; },
  ]) { const value = room(); mutate(value); assert.deepEqual(recordsForFinishedRoom("room", value, 1000), []); }
});
test("los jugadores simulados no reciben registros", () => {
  const value = room();
  value.partidaInicial = {...value.partidaInicial, jugadores: roster.map((p, index) => ({...p, simulado: index === 1}))};
  assert.equal(recordsForFinishedRoom("room", value, 1000).length, 4);
});
test("ID estable, sin colisiones de separadores y separado por modalidad", () => {
  assert.equal(historyId("online:uno"), historyId("online:uno"));
  assert.match(historyId("online:uno"), /^online_[a-f0-9]{64}$/);
  assert.notEqual(historyId("online:uno"), historyId("local:uno"));
  assert.notEqual(historyId("local:ab:c"), historyId("local:a:bc"));
});
