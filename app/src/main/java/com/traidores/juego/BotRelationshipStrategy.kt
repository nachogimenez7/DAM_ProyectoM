package com.traidores.juego

internal fun relationshipReads(session: GameSession, bot: GamePlayer): List<RelationshipRead> {
    return GameEngine.alivePlayers(session)
        .filter { it.name != bot.name }
        .map { player -> relationshipRead(session, bot, player) }
        .sortedWith(
            compareByDescending<RelationshipRead> { it.score }
                .thenBy { stableNoise("${session.code}:${session.round}:${bot.name}:${it.player.name}:relationship") }
                .thenBy { it.player.name }
        )
}
internal fun relationshipRead(
    session: GameSession,
    bot: GamePlayer,
    player: GamePlayer
): RelationshipRead {
    val suspectRead = scoreCandidate(session, bot, player, emptySet())
    val tableMemory = conversationMemory(session)
    val playerMemory = tableMemory[player.name]
    val reasons = suspectRead.reasons.toMutableList()
    var score = suspectRead.score
    val rapport = session.tableMemory.rapport[bot.name]?.get(player.name) ?: 0
    if (rapport > 0) {
        score -= (rapport + 1) / 2
        reasons += "me viene bancando"
    } else if (rapport < 0) {
        score += (-rapport + 1) / 2
        reasons += "me viene atacando"
    }

    publicContradiction(session, player.name)?.let { contradiction ->
        score += if (contradiction.latest.roleKey != null) 10 else 8
        reasons += if (contradiction.latest.roleKey != null) {
            "se contradijo de rol"
        } else {
            "cambio su accion"
        }
    }

    latestClaimBySpeaker(session, player.name)?.let { claim ->
        if (publicClaimants(session, claim.roleKey).size > 1) {
            score += 8
            reasons += "dos dijeron el mismo rol"
        }
    }

    if (playerMemory?.accusedTargets?.contains(bot.name) == true) {
        score += 3
        reasons += "me marco antes"
    }
    if (playerMemory?.defendedTargets?.contains(bot.name) == true) {
        score -= 3
        reasons += "me banco antes"
    }
    if (!playerMemory?.accusedBy.isNullOrEmpty()) {
        score += playerMemory?.accusedBy.orEmpty().size.coerceAtMost(3) * 2
        reasons += "lo marcaron varios"
    }
    if (!playerMemory?.defendedBy.isNullOrEmpty()) {
        score -= playerMemory?.defendedBy.orEmpty().size.coerceAtMost(2)
        reasons += "alguien lo banco"
    }
    playerMemory?.pendingQuestionFrom?.let { speaker ->
        score += if (speaker == bot.name) 5 else 3
        reasons += "dejo una pregunta colgada"
    }
    playerMemory?.roleClaim?.let {
        score += 1
        reasons += "declaro rol"
    }

    if (player.isHuman && pendingQuestionForHuman(session) != null && !hasUsefulPublicRead(session, player.name)) {
        score += 2
        reasons += "debe una respuesta"
    }

    session.votes[player.name]?.takeIf { it == bot.name }?.let {
        score += 4
        reasons += "me voto"
    }
    session.votes[bot.name]?.takeIf { it == player.name }?.let {
        score += 2
        reasons += "yo ya lo venia votando"
    }

    val level = when {
        score <= -2 -> TrustLevel.CONFIA
        score <= 3 -> TrustLevel.NEUTRAL
        score <= 7 -> TrustLevel.DUDA
        score <= 12 -> TrustLevel.SOSPECHA
        else -> TrustLevel.PRESIONA
    }
    return RelationshipRead(
        player = player,
        level = level,
        score = score,
        reason = relationshipReason(reasons)
    )
}

internal fun relationshipReason(reasons: List<String>): String {
    val priority = listOf(
        "tengo una pista privada",
        "mi pista lo baja",
        "se contradijo de rol",
        "cambio su accion",
        "dos dijeron el mismo rol",
        "dejo una pregunta colgada",
        "debe una respuesta",
        "lo marcaron varios",
        "quedo marcado de antes",
        "me voto",
        "me viene atacando",
        "yo ya lo venia votando",
        "me marco antes",
        "me viene bancando",
        "me banco antes",
        "lo habian bancado antes",
        "alguien lo banco",
        "declaro rol"
    )
    return priority.firstOrNull { it in reasons }
        ?: reasons.firstOrNull()
        ?: "no tengo lectura fuerte"
}

internal fun roundObjectiveFor(session: GameSession, bot: GamePlayer): RoundObjective {
    val agenda = agendaFor(session, bot)
    val reads = relationshipReads(session, bot)
    val strongest = reads.firstOrNull()
    val human = GameEngine.humanPlayer(session).takeIf { it.alive }
    val humanRead = human?.let { target -> reads.firstOrNull { it.player.name == target.name } }
    val contradiction = reads.firstOrNull { publicContradiction(session, it.player.name) != null }
    val tableMemory = conversationMemory(session)
    val unansweredTarget = tableMemory.entries.firstOrNull { (_, memory) ->
        memory.pendingQuestionFrom == bot.name
    }?.key

    if (unansweredTarget != null) {
        val targetPlayer = reads.firstOrNull { it.player.name == unansweredTarget }?.player
            ?: GameEngine.playerByName(session, unansweredTarget)
        if (targetPlayer != null && targetPlayer.name != bot.name && targetPlayer.alive) {
            val read = reads.firstOrNull { it.player.name == unansweredTarget }
            return RoundObjective(
                type = RoundObjectiveType.FOLLOW_CONTRADICTION,
                target = safeName(targetPlayer, session),
                reason = "dejo una pregunta colgada",
                confidence = (read?.score ?: 8).coerceAtLeast(8)
            )
        }
    }

    if (isTraitor(bot) && socialRead(session, bot).heated) {
        val target = reads.firstOrNull { !isTraitor(it.player) } ?: strongest
        if (canVoiceStrongAccusation(session, target)) {
            return RoundObjective(
                type = RoundObjectiveType.DEFLECT_PRESSURE,
                target = target?.player?.let { safeName(it, session) },
                reason = target?.reason.orEmpty(),
                confidence = target?.score ?: 0
            )
        }
    }

    if (contradiction != null && contradiction.score >= 8) {
        return RoundObjective(
            type = RoundObjectiveType.FOLLOW_CONTRADICTION,
            target = safeName(contradiction.player, session),
            reason = contradiction.reason,
            confidence = contradiction.score
        )
    }

    if (
        strongest != null &&
        canVoiceStrongAccusation(session, strongest) &&
        strongest.level in setOf(TrustLevel.PRESIONA, TrustLevel.SOSPECHA) &&
        agenda in setOf(BotAgenda.PUSH_VOTE, BotAgenda.FOLLOW_THREAD, BotAgenda.ASK_ROLES)
    ) {
        return RoundObjective(
            type = RoundObjectiveType.PUSH_VOTE,
            target = safeName(strongest.player, session),
            reason = strongest.reason,
            confidence = strongest.score
        )
    }

    val playerToQuestion = reads.firstOrNull { read ->
        read.level in setOf(TrustLevel.NEUTRAL, TrustLevel.DUDA, TrustLevel.SOSPECHA, TrustLevel.PRESIONA) &&
            GameEngine.canSpeak(session, read.player) &&
            !canVoiceStrongAccusation(session, read)
    }
    if (playerToQuestion != null) {
        return RoundObjective(
            type = RoundObjectiveType.ASK_PLAYER,
            target = safeName(playerToQuestion.player, session),
            reason = playerToQuestion.reason,
            confidence = playerToQuestion.score
        )
    }

    if (
        humanRead != null &&
        humanRead.level in setOf(TrustLevel.NEUTRAL, TrustLevel.DUDA) &&
        GameEngine.canSpeak(session, humanRead.player)
    ) {
        return RoundObjective(
            type = RoundObjectiveType.ASK_PLAYER,
            target = safeName(humanRead.player, session),
            reason = humanRead.reason,
            confidence = humanRead.score
        )
    }

    if (agenda == BotAgenda.DEFEND_WEAK) {
        val trusted = reads.lastOrNull { it.level in setOf(TrustLevel.CONFIA, TrustLevel.NEUTRAL) }
        if (trusted != null) {
            return RoundObjective(
                type = RoundObjectiveType.DEFEND_PLAYER,
                target = safeName(trusted.player, session),
                reason = trusted.reason,
                confidence = trusted.score
            )
        }
    }

    return RoundObjective(RoundObjectiveType.CALM_TABLE)
}
