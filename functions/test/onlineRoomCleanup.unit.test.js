"use strict";
const assert = require("node:assert/strict");
const {test} = require("node:test");
const {DAY_MS, roomRetention, realtimeRetention} = require("../src/onlineRoomCleanupPolicy");
const nowMs = 100 * DAY_MS;
const room = {estado: "esperando", actualizadaEn: nowMs - DAY_MS};
test("retención con reloj controlado: lobby lleno, final y abandono 24h; juego 7d", () => {
  for (const estado of ["esperando", "finalizada", "abandonada", "en_juego"]) {
    const age = estado === "en_juego" ? 7 * DAY_MS : DAY_MS;
    assert.equal(roomRetention({room: {estado, jugadoresActuales: 5, actualizadaEn: nowMs - age}, nowMs}).eligible, true);
    assert.equal(roomRetention({room: {estado, actualizadaEn: nowMs - age + 1}, nowMs}).eligible, false);
  }
});
test("conectado no es lease: ts viejo no demuestra abandono", () => {
  assert.equal(roomRetention({room, nowMs, realtime: {presencia: {a: {estado: "conectado", ts: 1}}}}).eligible, false);
});
test("checkpoint y última conexión recientes impiden borrar", () => {
  assert.equal(roomRetention({room, nowMs, checkpoint: {actualizadaEn: nowMs - 1}}).eligible, false);
  assert.equal(roomRetention({room, nowMs, players: [{ultimaConexion: nowMs - 1}]}).eligible, false);
  assert.equal(roomRetention({room, nowMs, realtime: {presencia: {a: {estado: "desconectado", ts: nowMs - 1}}}}).eligible, false);
});
test("relojes inválidos, futuro y estados desconocidos conservan sala", () => {
  for (const value of [undefined, 0, NaN, nowMs + 1]) {
    assert.equal(roomRetention({room: {...room, actualizadaEn: value}, nowMs}).eligible, false);
  }
  assert.equal(roomRetention({room: {...room, estado: "nuevo"}, nowMs}).eligible, false);
  assert.equal(realtimeRetention({chat: {ts: nowMs + 1}}, nowMs, DAY_MS).eligible, false);
});
test("timestamps locales no renuevan ni deciden la retención", () => {
  assert.equal(roomRetention({room: {...room, actualizadaEnLocal: nowMs + DAY_MS}, nowMs,
    realtime: {actualizadaEnLocal: nowMs + DAY_MS}}).eligible, true);
  assert.equal(roomRetention({room: {...room, cleanupCompleted: true}, nowMs}).eligible, false);
});
