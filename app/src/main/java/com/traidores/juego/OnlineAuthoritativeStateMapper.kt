package com.traidores.juego

object OnlineAuthoritativeStateMapper {
    fun nightHadNoVictimFromState(state: Map<String, Any?>): Boolean {
        return (state["nocheSinVictima"] as? Boolean) ?: false
    }

    fun votePresentationFromState(state: Map<String, Any?>): String {
        return (state["presentacionVotacion"] as? String).orEmpty()
    }

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
                lastSilencedRound = (playerState["ultimaRondaSilenciado"] as? Number)?.toInt(),
                consecutiveNightAfk = (playerState["afkNoche"] as? Number)?.toInt()
                    ?: player.consecutiveNightAfk,
                consecutiveVoteAfk = (playerState["afkVoto"] as? Number)?.toInt()
                    ?: player.consecutiveVoteAfk,
                deathCause = deathCauseFromState(
                    playerState["causaEliminacion"],
                    player.deathCause
                )
            )
        }
    }

    private fun deathCauseFromState(value: Any?, fallback: DeathCause): DeathCause {
        val name = (value as? String).orEmpty()
        return runCatching { DeathCause.valueOf(name) }.getOrDefault(fallback)
    }
}
