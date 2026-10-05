"use strict";

const {createHash} = require("node:crypto");
const {Timestamp, FieldValue} = require("firebase-admin/firestore");

function historyId(matchKey) {
  const prefix = matchKey.startsWith("online:") ? "online_" : "local_";
  return prefix + createHash("sha256").update(matchKey).digest("hex");
}
function pathPart(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 128 && !value.includes("/");
}

/** Uses the event's durable final snapshot, even if the room has since reset or been deleted. */
function recordsForFinishedRoom(roomId, room, finishedAtMs) {
  const initial = room?.partidaInicial;
  const state = room?.estadoPartida;
  if (!pathPart(roomId) || !pathPart(initial?.matchId) || !Number.isFinite(finishedAtMs) ||
      finishedAtMs <= 0 || !["Pueblo", "Traidores"].includes(state?.ganador)) return [];
  if (!["medieval", "pampa", "grecia"].includes(initial.mapa)) return [];
  const roster = initial.jugadores;
  const players = state.jugadores;
  if (!Array.isArray(roster) || !Array.isArray(players) || roster.length < 2 ||
      roster.length > 30 || roster.length !== players.length ||
      roster.some((p) => !p || typeof p !== "object") ||
      players.some((p) => !p || typeof p !== "object") ||
      new Set(roster.map((p) => p.uidTemporal)).size !== roster.length ||
      new Set(roster.map((p) => p.orden)).size !== roster.length ||
      new Set(players.map((p) => p.orden)).size !== players.length ||
      roster.some((p) => !Number.isInteger(p.orden))) return [];
  const finalByOrder = new Map(players.map((p) => [p.orden, p]));
  const records = [];
  for (const member of roster) {
    if (member.simulado === true) continue;
    const player = finalByOrder.get(member.orden);
    if (!pathPart(member.uidTemporal) || !player || player.nombre !== member.nombre ||
        typeof player.rolKey !== "string" || !player.rolKey || player.rolKey.length > 40 ||
        typeof player.rolNombre !== "string" || !player.rolNombre || player.rolNombre.length > 40 ||
        typeof player.rolEquipo !== "string") return [];
    const special = Array.isArray(state.victoriasEspeciales) &&
      state.victoriasEspeciales.some((v) => v.jugador === member.nombre);
    const won = special || (player.rolKey === "desertor" ?
      player.vivo === true && state.desertorBando === state.ganador :
      state.ganador === "Pueblo" ? player.rolEquipo === "Pueblo" :
        ["asesino", "mercenario", "espia"].includes(player.rolKey));
    records.push({schemaVersion: 1, uid: member.uidTemporal, matchKey: `online:${initial.matchId}`,
      origen: "online", roomId, matchId: initial.matchId, fechaLocalMs: finishedAtMs,
      mapKey: initial.mapa, mapName: initial.mapaNombre || initial.mapa,
      roleKey: player.rolKey, roleName: player.rolNombre, won,
      participantCount: roster.length, winner: state.ganador});
  }
  return records;
}

/** One record and its counters are committed together; repeated or concurrent events don't count twice. */
async function saveRecord({firestore, uid, record, recordId = historyId(record.matchKey), existingLocal = false}) {
  const account = firestore.doc(`cuentas/${uid}`);
  const history = account.collection("historial").doc(recordId);
  const profile = firestore.doc(`perfiles_publicos/${uid}`);
  return firestore.runTransaction(async (tx) => {
    const [oldRecord, summary, publicProfile] = await Promise.all([
      tx.get(history), tx.get(account), tx.get(profile),
    ]);
    // Public profiles are reserved for registered accounts. Do not recreate deleted accounts or guests.
    if (!publicProfile.exists || !/^[0-9]{1,12}$/.test(publicProfile.data().publicId || "")) return false;
    if (existingLocal && !oldRecord.exists) return false;
    if (oldRecord.exists && (!existingLocal || oldRecord.data().contabilizada === true)) return false;
    const data = existingLocal ? oldRecord.data() : record;
    if (data.uid !== uid || (existingLocal && data.origen !== "local")) return false;
    if (typeof data.matchKey !== "string" || typeof data.won !== "boolean") return false;
    if (recordId !== historyId(data.matchKey)) return false;
    const old = summary.data() || {};
    const matches = (Number.isSafeInteger(old.partidas) ? old.partidas : 0) + 1;
    const wins = (Number.isSafeInteger(old.victorias) ? old.victorias : 0) + (data.won ? 1 : 0);
    const finished = existingLocal ? data.finalizadaEn : Timestamp.fromMillis(data.fechaLocalMs);
    if (!finished || typeof finished.toMillis !== "function") return false;
    tx.set(history, {...data, finalizadaEn: finished, contabilizada: true});
    const latest = old.ultimaPartidaEn?.toMillis?.() > finished.toMillis() ? old.ultimaPartidaEn : finished;
    tx.set(account, {schemaVersion: 1, partidas: matches, victorias: wins,
      ultimaPartidaEn: latest, actualizadaEn: FieldValue.serverTimestamp()}, {merge: true});
    tx.update(profile, {estadisticasPerfil: {partidas: matches, victorias: wins}});
    return true;
  });
}

async function archiveFinishedRoom({firestore, roomId, room, finishedAtMs}) {
  const records = recordsForFinishedRoom(roomId, room, finishedAtMs);
  const results = await Promise.all(records.map((record) => saveRecord({firestore, uid: record.uid, record})));
  return results.filter(Boolean).length;
}

module.exports = {historyId, recordsForFinishedRoom, saveRecord, archiveFinishedRoom};
