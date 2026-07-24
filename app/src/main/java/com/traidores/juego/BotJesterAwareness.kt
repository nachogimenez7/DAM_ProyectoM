package com.traidores.juego

internal data class JesterRiskRead(
    val target: String,
    val score: Int,
    val reasons: List<String>
) {
    val isPlausible: Boolean
        get() = score >= 4
}

/**
 * Estimates whether somebody is trying to be expelled using public behaviour only.
 * The real role is never consulted except to know whether the match composition
 * contains a Jester at all.
 */
internal object BotJesterAwareness {
    fun isInPlay(session: GameSession): Boolean {
        return session.players.any { it.role?.key == RoleCatalog.BUFON }
    }

    fun read(session: GameSession, targetName: String): JesterRiskRead {
        if (!isInPlay(session)) return JesterRiskRead(targetName, 0, emptyList())
        val target = GameEngine.playerByName(session, targetName)
            ?.takeIf { it.alive }
            ?: return JesterRiskRead(targetName, 0, emptyList())
        val ownMessages = recentPublicMessages(session)
            .filter { it.speaker == target.name }
            .map { normalizedForParsing(it.message) }
        val reasons = mutableListOf<String>()
        var score = 0

        val asksForVotes = ownMessages.count { message ->
            selfVoteSignals.any(message::contains)
        }
        if (asksForVotes > 0) {
            score += 4 + (asksForVotes - 1).coerceAtMost(2)
            reasons += "parece buscar que lo voten"
        }

        val enjoysPressure = ownMessages.any { message ->
            pressureEnjoymentSignals.any(message::contains)
        }
        if (enjoysPressure) {
            score += 2
            reasons += "se agranda cuando lo acusan"
        }

        if (publicContradiction(session, target.name) != null) {
            score += 1
            reasons += "se contradice demasiado facil"
        }

        val latestStatement = latestStatementBySpeaker(session, target.name)
        if (latestStatement?.type == StatementType.REFUSED_ROLE && asksForVotes > 0) {
            score += 1
            reasons += "esquiva el rol pero pide votos"
        }

        return JesterRiskRead(target.name, score, reasons.distinct())
    }

    fun warningLine(
        session: GameSession,
        speaker: GamePlayer,
        focusNames: Set<String>,
        responseIndex: Int
    ): String? {
        if (responseIndex == 0 || !isInPlay(session)) return null
        val risk = focusNames
            .asSequence()
            .map { read(session, it) }
            .filter { it.target != speaker.name && it.isPlausible }
            .maxByOrNull { it.score }
            ?: return null
        val target = GameEngine.playerByName(session, risk.target)
            ?.let { safeName(it, session) }
            ?: return null
        val line = when (personalityFor(session, speaker)) {
            BotPersonality.JODON ->
                "para, $target esta pidiendo el voto con moño. ojo que puede ser el bufon"
            BotPersonality.PICANTE,
            BotPersonality.IMPULSIVO ->
                "no le regalemos la expulsion a $target, puede estar jugando a ser el bufon"
            BotPersonality.ANALITICO ->
                "hay otra lectura: $target parece buscar la expulsion. puede ser el bufon"
            else ->
                "ojo con votar a $target tan facil, puede ser el bufon"
        }
        return line
    }

    private val selfVoteSignals = listOf(
        "votame",
        "votenme",
        "voten por mi",
        "expulsenme",
        "saquenme",
        "mandame a plaza",
        "mandenme a plaza",
        "seria un buen expulsado"
    )

    private val pressureEnjoymentSignals = listOf(
        "por fin alguien me mira",
        "tirenme toda la sospecha",
        "me la banco",
        "marcame si queres",
        "a ver quien se anima"
    )
}

internal fun jesterRiskFor(session: GameSession, targetName: String): JesterRiskRead {
    return BotJesterAwareness.read(session, targetName)
}
