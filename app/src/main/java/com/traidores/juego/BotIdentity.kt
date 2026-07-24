package com.traidores.juego

internal enum class BotCompetitiveness {
    RELAJADO,
    EQUILIBRADO,
    OBSESIVO
}

/**
 * Builds a diverse cast for one match. Identity is deterministic from persisted
 * match state, so rotating the device or restoring the Activity never changes it.
 */
internal object BotIdentity {
    fun personalityFor(session: GameSession, bot: GamePlayer): BotPersonality {
        return personalityRoster(session)[bot.name]
            ?: BotPersonality.entries[
                stableNoise("${identitySeed(session)}:${bot.name}:fallback") % BotPersonality.entries.size
            ]
    }

    fun competitivenessFor(session: GameSession, bot: GamePlayer): BotCompetitiveness {
        val orderedBots = orderedBots(session, "competitiveness")
        val index = orderedBots.indexOfFirst { it.name == bot.name }.coerceAtLeast(0)
        val base = when (index % 4) {
            0 -> BotCompetitiveness.RELAJADO
            1, 2 -> BotCompetitiveness.EQUILIBRADO
            else -> BotCompetitiveness.OBSESIVO
        }
        if (session.botDifficulty != BotDifficulty.HARD) return base
        return when (base) {
            BotCompetitiveness.RELAJADO -> BotCompetitiveness.EQUILIBRADO
            BotCompetitiveness.EQUILIBRADO,
            BotCompetitiveness.OBSESIVO -> BotCompetitiveness.OBSESIVO
        }
    }

    fun personalityRoster(session: GameSession): Map<String, BotPersonality> {
        val personalities = BotPersonality.entries
        val offset = stableNoise("${identitySeed(session)}:personality-offset") % personalities.size
        return orderedBots(session, "personality")
            .mapIndexed { index, player ->
                player.name to personalities[(index + offset) % personalities.size]
            }
            .toMap()
    }

    private fun orderedBots(session: GameSession, trait: String): List<GamePlayer> {
        return session.players
            .filterNot { it.isHuman }
            .sortedWith(
                compareBy<GamePlayer> {
                    stableNoise("${identitySeed(session)}:$trait:${normalizedForParsing(it.name)}")
                }.thenBy { it.name }
            )
    }

    private fun identitySeed(session: GameSession): String {
        return "${session.code}:${session.startedAtEpochMs}:${session.players.size}"
    }
}
