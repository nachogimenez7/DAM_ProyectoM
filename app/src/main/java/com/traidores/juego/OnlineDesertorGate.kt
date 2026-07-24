package com.traidores.juego

/**
 * Red de seguridad del desertor online.
 *
 * El bando lo elige siempre el jugador. Esto solo cubre el caso de que no elija nunca
 * (se fue, se colgo, cerro la app): sin bando, `GameRules.winnerFor` no puede declarar
 * ganador a los traidores y la partida se queda sin final posible.
 */
object OnlineDesertorGate {

    /** Ronda a partir de la cual el anfitrion deja de esperar la eleccion. */
    const val AUTO_TEAM_ROUND = 2

    fun needsAutoTeam(
        isHost: Boolean,
        hasAliveDesertor: Boolean,
        teamIsBlank: Boolean,
        round: Int,
        winner: String
    ): Boolean {
        return isHost &&
            hasAliveDesertor &&
            teamIsBlank &&
            winner.isBlank() &&
            round >= AUTO_TEAM_ROUND
    }

    /**
     * Bando de reemplazo. Depende solo de la sala y de los nombres repartidos, para que
     * dos anfitriones distintos resuelvan lo mismo si hay traspaso de host.
     */
    fun autoTeam(sessionCode: String, playerNames: List<String>): String {
        val seed = stableNoise("$sessionCode|${playerNames.joinToString("|")}|desertor-auto")
        return if ((seed ushr 1) and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
    }
}
