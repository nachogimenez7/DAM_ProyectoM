"use strict";

const crypto = require("node:crypto");
const {FieldValue} = require("firebase-admin/firestore");
const {ServerValue} = require("firebase-admin/database");
const {
  OnlineStartError,
  TRAITOR_TEAM,
  playerFromDocument,
  prepareOnlineMatch,
} = require("./onlineStartCore");

function validRoomId(roomId) {
  return typeof roomId === "string" &&
    roomId.length >= 1 && roomId.length <= 128 &&
    !roomId.includes("/") && roomId !== "." && roomId !== "..";
}

async function loadExistingRealtimeAccess(firestore, roomId, initialMatch) {
  const publicPlayers = Array.isArray(initialMatch && initialMatch.jugadores) ?
    initialMatch.jugadores : [];
  const roleSnapshot = await firestore.collection("partidas")
    .doc(roomId)
    .collection("repartos")
    .get();
  const rolesByPlayer = new Map(roleSnapshot.docs.map((document) => [document.id, document.data()]));
  const access = {};
  for (const player of publicPlayers) {
    const uid = typeof player.uidTemporal === "string" ? player.uidTemporal : "";
    const order = Number.isInteger(player.orden) ? player.orden : -1;
    const name = typeof player.nombre === "string" ? player.nombre.trim().slice(0, 18) : "";
    const ownDocument = rolesByPlayer.get(uid);
    const visibleRoles = Array.isArray(ownDocument && ownDocument.rolesVisibles) ?
      ownDocument.rolesVisibles : [];
    const ownRole = visibleRoles.find((role) => role && role.orden === order);
    if (!uid || !name || !ownRole) {
      throw new OnlineStartError(
        "incomplete-existing-start",
        "La partida iniciada no tiene un reparto privado completo.",
      );
    }
    access[uid] = {
      name,
      inLobby: false,
      alive: true,
      traitor: ownRole.rolEquipo === TRAITOR_TEAM,
    };
  }
  return access;
}

async function syncRealtimeAccess({database, roomId, hostUid, creatorUid, matchId, members}) {
  const roomReference = database.ref(`salas/${roomId}`);
  const currentMembers = await roomReference.child("miembros").get();
  const updates = {};
  currentMembers.forEach((snapshot) => {
    const uid = snapshot.key || "";
    if (uid && !(uid in members)) {
      updates[`miembros/${uid}`] = null;
      updates[`presencia/${uid}`] = null;
    }
  });
  for (const [uid, member] of Object.entries(members)) {
    updates[`miembros/${uid}`] = {
      nombre: member.name.trim().slice(0, 18) || "Jugador",
      activo: true,
      enLobby: false,
      vivo: member.alive,
      traidor: member.traitor === true,
      invitadoOraculo: false,
      actualizadaEn: ServerValue.TIMESTAMP,
    };
  }
  updates["control/hostUid"] = hostUid;
  updates["control/creatorUid"] = creatorUid;
  updates["control/matchId"] = matchId;
  updates["control/jugadoresVivos"] = Object.values(members).filter((member) => member.alive).length;
  updates["control/actualizadaEn"] = ServerValue.TIMESTAMP;
  await roomReference.update(updates);
}

async function startOnlineMatch({
  firestore,
  database,
  requesterId,
  roomId,
  hostTieBreakChoice = null,
  matchId = crypto.randomUUID(),
  nowMs = Date.now(),
  randomInt = crypto.randomInt,
}) {
  if (!validRoomId(roomId)) {
    throw new OnlineStartError("invalid-room-id", "La sala indicada no es valida.");
  }
  const roomReference = firestore.collection("partidas").doc(roomId);
  const transactionResult = await firestore.runTransaction(async (transaction) => {
    const roomSnapshot = await transaction.get(roomReference);
    if (!roomSnapshot.exists) {
      throw new OnlineStartError("room-not-found", "La sala ya no existe.");
    }
    const room = roomSnapshot.data();
    if (room.partidaInicialCreada === true || room.partidaInicial) {
      if (!requesterId || (requesterId !== room.hostActivoId && requesterId !== room.hostId)) {
        throw new OnlineStartError(
          "host-required",
          "Solo el anfitrion puede reintentar el inicio.",
        );
      }
      const existingMatchId = room.partidaInicial && room.partidaInicial.matchId;
      if (typeof existingMatchId !== "string" || !existingMatchId) {
        throw new OnlineStartError(
          "incomplete-existing-start",
          "La partida iniciada no tiene un matchId valido.",
        );
      }
      return {
        status: "already_started",
        matchId: existingMatchId,
        mapKey: room.partidaInicial.mapa || room.mapa,
        hostUid: room.hostActivoId || room.hostId,
        creatorUid: room.hostId,
        initialMatch: room.partidaInicial,
        realtimeAccess: null,
      };
    }

    const playerSnapshot = await transaction.get(roomReference.collection("jugadores"));
    const players = playerSnapshot.docs
      .map((document) => playerFromDocument(document.id, document.data()))
      .filter(Boolean);
    const prepared = prepareOnlineMatch({
      requesterId,
      room,
      players,
      hostTieBreakChoice,
      matchId,
      nowMs,
      randomInt,
    });
    if (prepared.status === "tie_break_required") return prepared;
    if (prepared.status !== "ready") return prepared;

    prepared.assignedPlayers.forEach((player, index) => {
      transaction.update(roomReference.collection("jugadores").doc(player.id), {orden: index});
      const privateRole = prepared.payloads.privateRolesByPlayer[player.id];
      transaction.set(roomReference.collection("repartos").doc(player.id), {
        matchId: privateRole.matchId,
        uidTemporal: privateRole.playerId,
        rolesVisibles: privateRole.visibleRoles,
        creadaEn: FieldValue.serverTimestamp(),
      });
    });
    transaction.update(roomReference, {
      estado: "en_juego",
      mapa: prepared.mapKey,
      mapaNombre: prepared.payloads.initialMatch.mapaNombre,
      partidaInicial: prepared.payloads.initialMatch,
      estadoPartida: prepared.payloads.matchState,
      partidaInicialCreada: true,
      limpiezaPendiente: false,
      estadoClientes: FieldValue.delete(),
      entradaLiberadaMatchId: FieldValue.delete(),
      hostActivoId: requesterId,
      hostVersion: FieldValue.increment(1),
      jugadoresActuales: prepared.assignedPlayers.length,
      actualizadaEn: FieldValue.serverTimestamp(),
    });
    return {
      status: "started",
      matchId,
      mapKey: prepared.mapKey,
      roleSummary: prepared.payloads.roleSummary,
      hostUid: requesterId,
      creatorUid: room.hostId,
      initialMatch: prepared.payloads.initialMatch,
      realtimeAccess: prepared.payloads.realtimeAccess,
    };
  });

  if (transactionResult.status === "tie_break_required") return transactionResult;
  const realtimeAccess = transactionResult.realtimeAccess || await loadExistingRealtimeAccess(
    firestore,
    roomId,
    transactionResult.initialMatch,
  );
  await syncRealtimeAccess({
    database,
    roomId,
    hostUid: transactionResult.hostUid,
    creatorUid: transactionResult.creatorUid,
    matchId: transactionResult.matchId,
    members: realtimeAccess,
  });
  return {
    status: transactionResult.status,
    matchId: transactionResult.matchId,
    mapKey: transactionResult.mapKey,
  };
}

module.exports = {
  loadExistingRealtimeAccess,
  startOnlineMatch,
  syncRealtimeAccess,
  validRoomId,
};
