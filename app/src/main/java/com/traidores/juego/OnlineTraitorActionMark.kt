package com.traidores.juego

/**
 * Eleccion nocturna estructurada que solo circula por el canal privado de Traidores.
 * El texto del Plan sigue siendo la fuente visual del historial; estos datos existen para
 * poder dibujar las marcas sobre las cartas sin intentar interpretar frases traducidas.
 */
data class OnlineTraitorActionMark(
    val id: String,
    val actorName: String,
    val targetName: String,
    val roleKey: String,
    val round: Int,
    val phaseIndex: Int
)
