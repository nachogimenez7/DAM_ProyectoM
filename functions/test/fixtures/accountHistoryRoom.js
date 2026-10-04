"use strict";
const roster = Array.from({length: 5}, (_, orden) => ({
  orden, uidTemporal: "history-user-" + orden, nombre: "Jugador" + orden, publicId: String(orden + 1),
}));
function room() {
  const roles = [["aldeano", "Pueblo"], ["asesino", "Traidores"], ["desertor", "Neutral"],
    ["bufon", "Neutral"], ["espia", "Traidores"]];
  return {partidaInicial: {matchId: "history-test-match", mapa: "pampa", mapaNombre: "Pampa", jugadores: roster.map((p) => ({...p}))},
    estadoPartida: {ganador: "Pueblo", desertorBando: "Pueblo", victoriasEspeciales: [{jugador: "Jugador3"}],
      jugadores: roster.map((p, index) => ({orden: p.orden, nombre: p.nombre,
        rolKey: roles[index][0], rolNombre: roles[index][0], rolEquipo: roles[index][1]}))}};
}
module.exports = {room, roster};
