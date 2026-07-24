package com.traidores.juego

internal fun debugVoteCommandTarget(session: GameSession, voter: GamePlayer): String? {
    if (!session.debugBotsObeyVoteCommands || voter.isHuman) return null
    val human = session.players.firstOrNull { it.isHuman && it.alive } ?: return null
    val message = session.chatHistory
        .asReversed()
        .firstOrNull {
            it.channel == ChatChannel.PUBLICO && !it.isGod && it.speaker == human.name
        }
        ?.message
        ?.let(::normalizedVoteCommand)
        ?: return null
    val targetName = when {
        message.contains("votenme") || message.contains("voten por mi") -> human.name
        else -> session.players
            .filter { it.alive && it.name != voter.name }
            .firstOrNull { player ->
                val name = normalizedVoteCommand(player.name)
                message.contains("voten a $name") ||
                    message.contains("voten por $name")
            }
            ?.name
    } ?: return null
    return targetName.takeIf { name ->
        val target = GameEngine.playerByName(session, name)
        target != null && target.alive && target.name != voter.name
    }
}

internal fun agendaFor(session: GameSession, bot: GamePlayer): BotAgenda {
    if (isTraitor(bot) && socialRead(session, bot).heated) return BotAgenda.DEFLECT_PRESSURE
    return when (personalityFor(session, bot)) {
        BotPersonality.TRANQUI -> BotAgenda.CALM_TABLE
        BotPersonality.PICANTE -> BotAgenda.PUSH_VOTE
        BotPersonality.JODON -> listOf(BotAgenda.FOLLOW_THREAD, BotAgenda.PUSH_VOTE, BotAgenda.CALM_TABLE)[
            stableNoise("${session.code}:${bot.name}:agenda:jodon") % 3
        ]
        BotPersonality.DESCONFIADO -> BotAgenda.ASK_ROLES
        BotPersonality.IMPULSIVO -> BotAgenda.PUSH_VOTE
        BotPersonality.ANALITICO -> when (bot.role?.key) {
            RoleCatalog.POLICIA,
            RoleCatalog.MEDICO,
            RoleCatalog.ORACULO -> BotAgenda.FOLLOW_THREAD
            else -> BotAgenda.DEFEND_WEAK
        }
    }
}

internal fun rankedPublicSuspects(
    session: GameSession,
    voter: GamePlayer,
    focusNames: Set<String> = emptySet()
): List<SuspectRead> {
    return voteCandidatesFor(session, voter)
        .filter { it.name != voter.name }
        .map { candidate -> scoreCandidate(session, voter, candidate, focusNames) }
        .sortedWith(
            compareByDescending<SuspectRead> { it.score }
                .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.player.name}:suspect") }
                .thenBy { it.player.name }
        )
}

internal fun scoreCandidate(
    session: GameSession,
    voter: GamePlayer,
    candidate: GamePlayer,
    focusNames: Set<String>
): SuspectRead {
    val recent = recentPublicMessages(session)
    val reasons = mutableListOf<String>()
    var score = stableNoise("${session.code}:${session.round}:${voter.name}:${candidate.name}:base") % 3
    session.tableMemory.privateInvestigationReads
        .asReversed()
        .firstOrNull { read -> read.source == voter.name && read.target == candidate.name }
        ?.let { privateRead ->
            if (normalizedForParsing(privateRead.result) in setOf("sospechoso", "sospechosa", "culpable", "traidor")) {
                score += if (session.botDifficulty == BotDifficulty.HARD) 18 else 14
                reasons += "tengo una pista privada"
            } else if (normalizedForParsing(privateRead.result) in setOf("inocente", "limpio", "limpia")) {
                score -= if (session.botDifficulty == BotDifficulty.HARD) 16 else 12
                reasons += "mi pista lo baja"
            }
        }
    val persistentPressure = session.tableMemory.suspicion[voter.name]?.get(candidate.name) ?: 0
    if (persistentPressure > 0) {
        score += persistentPressure
        reasons += "quedo marcado de antes"
    } else if (persistentPressure < 0) {
        score += persistentPressure
        reasons += "lo habian bancado antes"
    }

    if (candidate.name in focusNames) {
        score += 8
        reasons += "lo nombraron en el pueblo"
    }

    val jesterRisk = jesterRiskFor(session, candidate.name)
    if (jesterRisk.isPlausible) {
        score -= if (session.botDifficulty == BotDifficulty.HARD) 12 else 6
        reasons += "puede estar buscando que lo expulsemos"
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

    val statementsAboutCandidate = recent.mapNotNull { message ->
        LocalBotAi.publicStatementFrom(session, message.message)?.takeIf { it.target == candidate.name }
    }
    val statementPressure = statementsAboutCandidate.count {
        it.type == StatementType.ACCUSE || it.type == StatementType.VOTE
    }
    val statementTrust = statementsAboutCandidate.count { it.type == StatementType.TRUST }
    if (statementPressure > 0) {
        score += statementPressure * if (session.botDifficulty == BotDifficulty.HARD) 5 else 4
        reasons += "lo presionaron con algo concreto"
    }
    if (statementTrust > 0) {
        score -= statementTrust * 2
    }
    latestStatementBySpeaker(session, candidate.name)?.let { statement ->
        when (statement.type) {
            StatementType.REFUSED_ROLE -> {
                score += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
                reasons += "esquivo el rol"
            }
            StatementType.PROTECTED,
            StatementType.INVESTIGATED -> {
                score += 2
                reasons += "tiro dato y falta detalle"
            }
            else -> Unit
        }
    }

    latestClaimBySpeaker(session, candidate.name)?.let { claim ->
        val claimants = publicClaimants(session, claim.roleKey)
        val voterHasClaimedRole = voter.role?.key == claim.roleKey && voter.name != candidate.name
        if (claimants.size > 1 || voterHasClaimedRole) {
            score += if (session.botDifficulty == BotDifficulty.HARD) 14 else 10
            reasons += "dos dijeron el mismo rol"
        } else {
            if (!hasUsefulPublicRead(session, candidate.name)) {
                score += 2
                reasons += "tiro rol y falta detalle"
            }
        }
    }

    publicContradiction(session, candidate.name)?.let { contradiction ->
        if (contradiction.latest.roleKey != null) {
            score += if (session.botDifficulty == BotDifficulty.HARD) 16 else 12
            reasons += "se contradijo de rol"
        } else {
            score += if (session.botDifficulty == BotDifficulty.HARD) 12 else 9
            reasons += "se contradijo con la accion"
        }
    }

    val spokeCount = recent.count { it.speaker == candidate.name }
    when {
        spokeCount == 0 && session.round > 1 && !candidate.isHuman -> {
            score += 1
            reasons += "esta hablando poco"
        }
        spokeCount >= 3 -> {
            score += 1
            reasons += "esta ocupando mucho espacio"
        }
    }
    if (candidate.isHuman && !hasUsefulPublicRead(session, candidate.name)) {
        val bonus = humanDayPressureBonus(session)
        if (bonus > 0) {
            score += bonus
            reasons += if (spokeCount == 0) {
                "esta poco leido"
            } else {
                "falta cerrar su version"
            }
        } else if (session.botDifficulty == BotDifficulty.NORMAL) {
            // En Normal, sin evidencia dura el humano recibe el beneficio de la duda:
            // siendo el unico humano de la mesa, no debe ser el voto por descarte.
            score -= HUMAN_NORMAL_VOTE_RELIEF
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

internal fun nightPressureScore(session: GameSession, candidate: GamePlayer): Int {
    val recent = recentPublicMessages(session)
    val spokeCount = recent.count { it.speaker == candidate.name }
    val namedCount = recent.count { mentionsName(it.message, candidate.name) }
    val accusedCount = recent.count {
        mentionsName(it.message, candidate.name) && hasAnySignal(it.message, accusationWords)
    }
    return spokeCount * 3 +
        namedCount -
        accusedCount * 2 +
        stableNoise("${session.code}:${session.round}:${candidate.name}:night") % 2
}

internal const val HUMAN_NORMAL_VOTE_RELIEF = 5

internal fun humanDayPressureBonus(session: GameSession): Int {
    if (session.botDifficulty != BotDifficulty.HARD) return 0
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (4 + roundsElapsed * 2).coerceAtMost(14)
}

internal fun humanPressureChancePercent(session: GameSession): Int {
    val (base, perRound, cap) = if (session.botDifficulty == BotDifficulty.HARD) {
        Triple(12, 7, 45)
    } else {
        Triple(5, 3, 20)
    }
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (base + perRound * roundsElapsed).coerceAtMost(cap)
}

internal fun humanNightTargetBonus(session: GameSession, candidate: GamePlayer, actionTag: String): Int {
    if (!candidate.isHuman) return 0
    val chance = humanPressureChancePercent(session)
    val roll = stableNoise("${session.code}:${session.round}:$actionTag:human-pressure") % 100
    return if (roll < chance) HUMAN_NIGHT_PRESSURE_BONUS else 0
}

internal fun fallbackTarget(session: GameSession, actor: GamePlayer): String {
    return voteCandidatesFor(session, actor)
        .firstOrNull { it.name != actor.name }
        ?.name
        .orEmpty()
}

internal fun canUseBotVoteTarget(session: GameSession, voter: GamePlayer, targetName: String): Boolean {
    val target = GameEngine.playerByName(session, targetName) ?: return false
    if (!target.alive || target.name == voter.name) return false
    return !session.debugBotsNeverVoteHuman ||
        !target.isHuman ||
        GameEngine.alivePlayers(session).none { it.name != voter.name && !it.isHuman }
}

internal fun voteCandidatesFor(session: GameSession, voter: GamePlayer): List<GamePlayer> {
    return withoutHumanIfDebug(
        session.debugBotsNeverVoteHuman,
        GameEngine.alivePlayers(session).filter { it.name != voter.name }
    )
}

internal fun withoutHumanIfDebug(
    enabled: Boolean,
    candidates: List<GamePlayer>
): List<GamePlayer> {
    if (!enabled) return candidates
    val filtered = candidates.filterNot { it.isHuman }
    return filtered.ifEmpty { candidates }
}

internal fun isTraitor(player: GamePlayer): Boolean {
    return GameRules.isTraitorRole(player.role)
}

internal fun stableNoise(seed: String): Int {
    var value = 17
    seed.forEach { char ->
        value = (value * 31 + char.code) and 0x7fffffff
    }
    return value
}
