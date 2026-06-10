package com.traidores.juego

internal object LocalBotAi {
    private val accusationWords = listOf(
        "sospe",
        "raro",
        "miente",
        "menti",
        "acuso",
        "voto",
        "culpa",
        "callado",
        "silencio",
        "cambio de tema",
        "defiende",
        "nervioso"
    )
    private val defenseWords = listOf("confio", "inocente", "limpio", "defiendo", "creo en")
    private val secretWords = listOf(
        "asesino",
        "asesina",
        "traidor",
        "traidores",
        "policia",
        "comisario",
        "detective",
        "medico",
        "aldeano",
        "pueblo",
        "neutral",
        "mercenario",
        "espia"
    )

    fun chooseAssassinTarget(session: GameSession, assassin: GamePlayer): String {
        val candidates = preferredNightTargets(session, assassin)
        return candidates
            .sortedWith(
                compareByDescending<GamePlayer> { nightPressureScore(session, it) }
                    .thenBy { stableNoise("${session.code}:${session.round}:${assassin.name}:${it.name}:kill") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            .orEmpty()
    }

    fun chooseSilenceTarget(session: GameSession, mercenary: GamePlayer): String {
        val candidates = preferredNightTargets(session, mercenary)
        return candidates
            .sortedWith(
                compareByDescending<GamePlayer> { nightPressureScore(session, it) }
                    .thenBy { stableNoise("${session.code}:${session.round}:${mercenary.name}:${it.name}:silence") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            .orEmpty()
    }

    private fun preferredNightTargets(session: GameSession, actor: GamePlayer): List<GamePlayer> {
        val candidates = GameEngine.activePlayers(session).filter { it.name != actor.name }
        val nonTraitors = candidates.filterNot { isTraitor(it) }
        return nonTraitors.ifEmpty { candidates }
    }

    fun chooseInvestigationTarget(session: GameSession, police: GamePlayer): String {
        return rankedPublicSuspects(session, police)
            .firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, police)
    }

    fun chooseProtectionTarget(session: GameSession, medic: GamePlayer): String {
        return GameEngine.activePlayers(session)
            .sortedWith(
                compareByDescending<GamePlayer> { nightPressureScore(session, it) + if (it.name == medic.name) 1 else 0 }
                    .thenBy { stableNoise("${session.code}:${session.round}:${medic.name}:${it.name}:save") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            ?: medic.name
    }

    fun chooseVoteTarget(session: GameSession, voter: GamePlayer): String {
        return rankedPublicSuspects(session, voter)
            .firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, voter)
    }

    fun openingDebateMessages(session: GameSession, limit: Int = 3): List<Pair<String, String>> {
        val mutedNames = session.players.filter { it.muted }.map { safeName(it, session) }
        val noDeath = session.publicAnnouncement.contains("no murio nadie", ignoreCase = true)
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).getOrNull(index)
                ?: rankedPublicSuspects(session, bot).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = read?.reason() ?: "su postura todavia no cierra"
            val muted = mutedNames.lastOrNull()
            val lines = when {
                muted != null && index == 0 ->
                    listOf(
                        "$muted ya no puede responder. Quiero ver si $target sostiene su version.",
                        "Como $muted no puede hablar, necesito que $target aclare su postura."
                    )
                noDeath && index == 1 ->
                    listOf(
                        "Que no haya caido nadie no limpia la mesa. $target me hace ruido porque $reason.",
                        "La noche sin muerte no prueba inocencia. $target sigue bajo duda porque $reason."
                    )
                index % 3 == 0 ->
                    listOf(
                        "$target, explica tu recorrido. Me hace ruido que $reason.",
                        "Empiezo por $target porque $reason. Quiero escuchar su defensa.",
                        "Antes de cambiar de tema, $target tiene que aclarar por que $reason."
                    )
                index % 3 == 1 ->
                    listOf(
                        "No me alcanza el silencio. Estoy mirando a $target porque $reason.",
                        "Todavia no cierro con $target: $reason.",
                        "Pondria el foco en $target porque $reason."
                    )
                else ->
                    listOf(
                        "Antes de votar quiero contrastar a $target con lo que se dijo.",
                        "Quiero que $target responda antes de cerrar la discusion.",
                        "No votaria sin volver sobre la postura de $target."
                    )
            }
            val line = lines[
                stableNoise("${session.code}:${session.round}:${bot.name}:opening:$index") % lines.size
            ]
            bot.name to sanitizeBotSpeech(line, session)
        }
    }

    fun votingIntentMessages(session: GameSession, limit: Int = 2): List<Pair<String, String>> {
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = read?.reason() ?: "su postura todavia no cierra"
            val templates = listOf(
                "Si nada cambia, mi voto va por $target porque $reason.",
                "Hoy estoy mas cerca de votar a $target: $reason.",
                "Mi principal duda es $target porque $reason.",
                "Antes del cierre, $target sigue siendo mi opcion por que $reason."
            )
            val line = templates[
                stableNoise("${session.code}:${session.round}:${bot.name}:vote:$index") % templates.size
            ]
            bot.name to sanitizeBotSpeech(line, session)
        }
    }

    fun reactionsToHumanMessage(session: GameSession, humanMessage: String): List<Pair<String, String>> {
        val focusNames = mentionedPlayerNames(session, humanMessage).toSet()
        val claimsHiddenInfo = containsSecretTerm(humanMessage, session)
        val replyCount = if (focusNames.isNotEmpty() || claimsHiddenInfo || humanMessage.length > 45) 2 else 1
        return messageBots(session, replyCount).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot, focusNames).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = read?.reason() ?: "su postura todavia no cierra"
            val line = when {
                claimsHiddenInfo && index == 0 ->
                    "Anoto la acusacion, pero no demos por cierta ninguna carta. Hablemos de conductas."
                claimsHiddenInfo ->
                    "$target sigue en mi lista solo por lo publico: $reason."
                focusNames.contains(bot.name) ->
                    "Me estan marcando a mi. Respondo con hechos, no con promesas."
                focusNames.isNotEmpty() && index == 0 ->
                    "$target, responde puntual. La duda quedo puesta sobre la mesa."
                focusNames.isNotEmpty() ->
                    "Me sirve esa pista, pero quiero comparar quien acompano demasiado rapido."
                humanMessage.trim().endsWith("?") ->
                    "Buena pregunta. Yo miraria a $target porque $reason."
                else ->
                    "Anoto eso. A mi me hace ruido $target porque $reason."
            }
            bot.name to sanitizeBotSpeech(line, session)
        }
    }

    private fun rankedPublicSuspects(
        session: GameSession,
        voter: GamePlayer,
        focusNames: Set<String> = emptySet()
    ): List<SuspectRead> {
        return GameEngine.activePlayers(session)
            .filter { it.name != voter.name }
            .map { candidate -> scoreCandidate(session, voter, candidate, focusNames) }
            .sortedWith(
                compareByDescending<SuspectRead> { it.score }
                    .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.player.name}:suspect") }
                    .thenBy { it.player.name }
            )
    }

    private fun scoreCandidate(
        session: GameSession,
        voter: GamePlayer,
        candidate: GamePlayer,
        focusNames: Set<String>
    ): SuspectRead {
        val recent = recentPublicMessages(session)
        val reasons = mutableListOf<String>()
        var score = stableNoise("${session.code}:${session.round}:${voter.name}:${candidate.name}:base") % 3

        if (candidate.name in focusNames) {
            score += 8
            reasons += "lo nombraron en la mesa"
        }

        val mentions = recent.filter { mentionsName(it.message, candidate.name) }
        val accusations = mentions.count { hasAnySignal(it.message, accusationWords) }
        val defenses = mentions.count { hasAnySignal(it.message, defenseWords) }

        if (accusations > 0) {
            score += accusations * 5
            reasons += "le pidieron explicaciones"
        }
        if (mentions.size > accusations) {
            score += 2
            reasons += "aparecio demasiado en la charla"
        }
        if (defenses > 0) {
            score -= defenses * 2
        }

        val spokeCount = recent.count { it.speaker == candidate.name }
        when {
            spokeCount == 0 -> {
                score += 2
                reasons += "esta hablando poco"
            }
            spokeCount >= 3 -> {
                score += 1
                reasons += "esta ocupando mucho espacio"
            }
        }

        val voterPressedCandidate = recent.any {
            it.speaker == voter.name && mentionsName(it.message, candidate.name)
        }
        if (voterPressedCandidate) {
            score += 2
            reasons += "ya venia bajo presion"
        }

        return SuspectRead(candidate, score, reasons.distinct())
    }

    private fun nightPressureScore(session: GameSession, candidate: GamePlayer): Int {
        val recent = recentPublicMessages(session)
        val spokeCount = recent.count { it.speaker == candidate.name }
        val namedCount = recent.count { mentionsName(it.message, candidate.name) }
        val accusedCount = recent.count {
            mentionsName(it.message, candidate.name) && hasAnySignal(it.message, accusationWords)
        }
        return (if (candidate.isHuman && session.round > 1) 2 else 0) +
            spokeCount * 3 +
            namedCount -
            accusedCount * 2 +
            stableNoise("${session.code}:${session.round}:${candidate.name}:night") % 2
    }

    private fun messageBots(session: GameSession, limit: Int): List<GamePlayer> {
        return GameEngine.activePlayers(session)
            .filterNot { it.isHuman }
            .sortedBy { stableNoise("${session.code}:${session.round}:${session.chatHistory.size}:${it.name}:talk") }
            .take(limit)
    }

    private fun mentionedPlayerNames(session: GameSession, message: String): List<String> {
        return GameEngine.activePlayers(session)
            .filter { mentionsName(message, it.name) }
            .map { it.name }
    }

    private fun fallbackTarget(session: GameSession, actor: GamePlayer): String {
        return GameEngine.activePlayers(session)
            .firstOrNull { it.name != actor.name }
            ?.name
            .orEmpty()
    }

    private fun isTraitor(player: GamePlayer): Boolean {
        return GameRules.isTraitorRole(player.role)
    }

    private fun recentPublicMessages(session: GameSession): List<GameChatMessage> {
        return session.chatHistory.filterNot { it.isGod }.takeLast(16)
    }

    private fun hasAnySignal(message: String, signals: List<String>): Boolean {
        val text = normalized(message)
        return signals.any { text.contains(it) }
    }

    private fun containsSecretTerm(message: String, session: GameSession): Boolean {
        val text = normalized(message)
        return forbiddenTerms(session).any { term -> term.length > 2 && text.contains(normalized(term)) }
    }

    private fun mentionsName(message: String, name: String): Boolean {
        return normalized(message).contains(normalized(name))
    }

    private fun safeName(player: GamePlayer, session: GameSession): String {
        return sanitizeBotSpeech(player.name, session).ifBlank { "alguien" }
    }

    private fun sanitizeBotSpeech(raw: String, session: GameSession): String {
        var safe = raw
        forbiddenTerms(session).forEach { term ->
            if (term.length > 2) {
                safe = safe.replace(
                    Regex(
                        "(?<![\\wáéíóúüñÁÉÍÓÚÜÑ])${Regex.escape(term)}(?![\\wáéíóúüñÁÉÍÓÚÜÑ])",
                        RegexOption.IGNORE_CASE
                    ),
                    "esa carta"
                )
            }
        }
        return safe.replace(Regex("\\s+"), " ").trim().take(140)
    }

    private fun forbiddenTerms(session: GameSession): Set<String> {
        val roleTerms = session.players.flatMap { player ->
            listOfNotNull(player.role?.key, player.role?.name, player.role?.team)
        }
        return (secretWords + roleTerms).map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun normalized(value: String): String {
        return value.lowercase()
    }

    private fun stableNoise(seed: String): Int {
        var value = 17
        seed.forEach { char ->
            value = (value * 31 + char.code) and 0x7fffffff
        }
        return value
    }

    private data class SuspectRead(
        val player: GamePlayer,
        val score: Int,
        val reasons: List<String>
    ) {
        fun reason(): String = reasons.firstOrNull() ?: "su postura todavia no cierra"
    }
}
