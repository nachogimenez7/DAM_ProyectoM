"use strict";

const DAY_MS = 24 * 60 * 60 * 1000;
const RETENTION_MS = Object.freeze({
  esperando: DAY_MS,
  finalizada: DAY_MS,
  abandonada: DAY_MS,
  en_juego: 7 * DAY_MS,
});

function timestampMs(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

// Connected is not a heartbeat lease: a healthy socket may keep its original ts
// for hours. Never expire an active presence merely because that ts is old.
function realtimeRetention(realtime, nowMs, retentionMs) {
  if (!realtime) return {eligible: true};
  if (Object.values(realtime.presencia || {}).some((value) => value?.estado === "conectado")) {
    return {eligible: false, reason: "connected-presence"};
  }
  // Only server timestamp fields, never actualizadaEnLocal or client deadlines.
  // Include chat/sync activity without relying exclusively on host control.
  const stack = [realtime];
  let visited = 0;
  while (stack.length) {
    if (++visited > 50000) return {eligible: false, reason: "oversized-realtime"};
    const value = stack.pop();
    if (!value || typeof value !== "object") continue;
    for (const [key, child] of Object.entries(value)) {
      if (key === "ts" || key === "actualizadaEn") {
        const at = timestampMs(child);
        if (at === null || at <= 0 || at > nowMs) {
          return {eligible: false, reason: "uncertain-realtime-clock"};
        }
        if (nowMs - at < retentionMs) return {eligible: false, reason: "recent-realtime"};
      } else if (child && typeof child === "object") stack.push(child);
    }
  }
  return {eligible: true};
}

function roomRetention({room, players = [], checkpoint = null, realtime = null, nowMs}) {
  if (!room) return {eligible: false, reason: "missing-room"};
  if (room.cleanupCompleted === true) return {eligible: false, reason: "already-cleaned"};
  const retentionMs = RETENTION_MS[room.estado];
  if (!retentionMs) return {eligible: false, reason: "unknown-room-state"};
  const timestamps = [room.actualizadaEn ?? room.creadaEn];
  for (const player of players) {
    if (player.ultimaConexion != null) timestamps.push(player.ultimaConexion);
    if (player.unidoEn != null) timestamps.push(player.unidoEn);
  }
  if (checkpoint?.actualizadaEn != null) timestamps.push(checkpoint.actualizadaEn);
  for (const timestamp of timestamps) {
    const at = timestampMs(timestamp);
    if (at === null || at <= 0 || at > nowMs) {
      return {eligible: false, reason: "uncertain-firestore-clock"};
    }
    if (nowMs - at < retentionMs) return {eligible: false, reason: "recent-firestore"};
  }
  return {...realtimeRetention(realtime, nowMs, retentionMs), retentionMs};
}

module.exports = {DAY_MS, RETENTION_MS, timestampMs, roomRetention, realtimeRetention};
