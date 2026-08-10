package com.traidores.juego

object OnlineAuthoritativeStateMapper {
    const val CURRENT_SCHEMA_VERSION = 2

    fun schemaVersionFromState(state: Map<String, Any?>): Int {
        return (state["versionEstado"] as? Number)?.toInt() ?: 0
    }

    fun phaseDeadlineFromState(state: Map<String, Any?>): Long {
        return (state["limiteFaseEpochMs"] as? Number)?.toLong() ?: 0L
    }

    fun startupDeadlineFromState(state: Map<String, Any?>): Long {
        return (state["inicioAutomaticoEpochMs"] as? Number)?.toLong() ?: 0L
    }

    fun phaseDeadlineIndexFromState(state: Map<String, Any?>): Int {
        return (state["limiteFasePhaseIndex"] as? Number)?.toInt() ?: -1
    }

    fun remainingPhaseMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long {
        return (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
    }

    fun nightHadNoVictimFromState(state: Map<String, Any?>): Boolean {
        return (state["nocheSinVictima"] as? Boolean) ?: false
    }

    fun votePresentationFromState(state: Map<String, Any?>): String {
        return (state["presentacionVotacion"] as? String).orEmpty()
    }

    fun canPublishPlayerRole(
        revealRolesOnDeath: Boolean,
        playerAlive: Boolean,
        winner: String,
        votePresentation: String,
        playerName: String,
        dayEliminationTarget: String
    ): Boolean {
        if (winner.isNotBlank()) return true
        if (!revealRolesOnDeath) return false
        if (!playerAlive) return true
        return dayEliminationTarget.isNotBlank() &&
            playerName == dayEliminationTarget &&
            votePresentation.startsWith("expulsion|") &&
            votePresentation.substringAfterLast('|') == dayEliminationTarget
    }

    fun specialVictoriesFromState(state: Map<String, Any?>): List<GameSpecialVictory> {
        return (state["victoriasEspeciales"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.mapNotNull { victory ->
                val key = (victory["key"] as? String).orEmpty()
                val playerName = (victory["jugador"] as? String).orEmpty()
                val roleKey = (victory["rol"] as? String).orEmpty()
                val round = (victory["ronda"] as? Number)?.toInt() ?: return@mapNotNull null
                if (key.isBlank() || playerName.isBlank() || roleKey.isBlank()) {
                    null
                } else {
                    GameSpecialVictory(key, playerName, roleKey, round)
                }
            }
            .orEmpty()
            .distinctBy { it.key }
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
                role = roleFromState(playerState) ?: player.role,
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

    private fun roleFromState(state: Map<*, *>): GameRole? {
        val key = (state["rolKey"] as? String).orEmpty()
        if (key.isBlank()) return null
        return GameRole(
            key = key,
            name = (state["rolNombre"] as? String).orEmpty().ifBlank { key },
            team = (state["rolEquipo"] as? String).orEmpty(),
            imageResName = (state["rolImagen"] as? String).orEmpty()
        )
    }

    private fun deathCauseFromState(value: Any?, fallback: DeathCause): DeathCause {
        val name = (value as? String).orEmpty()
        return runCatching { DeathCause.valueOf(name) }.getOrDefault(fallback)
    }
}
