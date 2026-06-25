package com.traidores.juego

object OnlineAuthoritativeStateMapper {
    fun playersFromState(
        players: List<GamePlayer>,
        state: Map<String, Any?>
    ): List<GamePlayer>? {
        val playerStates = (state["jugadores"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.mapNotNull { playerState ->
                val order = (playerState["orden"] as? Number)?.toInt() ?: return@mapNotNull null
                order to playerState
            }
            ?.toMap()
            ?: return null
        return players.mapIndexed { index, player ->
            val playerState = playerStates[index] ?: return@mapIndexed player
            player.copy(
                alive = (playerState["vivo"] as? Boolean) ?: player.alive,
                muted = (playerState["muteado"] as? Boolean) ?: player.muted,
                lastSilencedRound = (playerState["ultimaRondaSilenciado"] as? Number)?.toInt()
            )
        }
    }
}
