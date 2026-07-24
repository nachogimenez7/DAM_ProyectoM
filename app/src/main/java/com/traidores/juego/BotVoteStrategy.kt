package com.traidores.juego

internal fun activeTraitorPlanForPublicDay(session: GameSession): TraitorPlan? {
    if (
        session.phase != GamePhase.DIA_DEBATE &&
        session.phase != GamePhase.CONTRAPUNTO &&
        session.phase != GamePhase.VOTACION &&
        session.phase != GamePhase.DESEMPATE_VOTACION
    ) {
        return null
    }
    val plan = session.traitorPlan ?: return null
    return plan.takeIf { it.round == session.round || it.round == session.round - 1 }
}
internal fun traitorPlanVotePlan(session: GameSession, voter: GamePlayer): VotePlan? {
    if (!isTraitor(voter)) return null
    val plan = activeTraitorPlanForPublicDay(session) ?: return null
    val cover = plan.cover
    val coverTarget = cover?.targetToDirty.orEmpty()
    val targetName = when {
        cover?.kind == CoverKind.BUS_ALLY && coverTarget.isNotBlank() ->
            coverTarget
        plan.dayPushTarget.isNotBlank() -> plan.dayPushTarget
        coverTarget.isNotBlank() -> coverTarget
        else -> null
    } ?: return null
    if (!canUseBotVoteTarget(session, voter, targetName)) return null
    val target = GameEngine.playerByName(session, targetName)
        ?.takeIf { it.alive && it.name != voter.name }
        ?: return null
    if (isTraitor(target) && cover?.kind != CoverKind.BUS_ALLY) return null

    val basePlan = VotePlan(
        target = target.name,
        reason = traitorPlanVoteReason(plan, target.name),
        confidence = traitorPlanVoteConfidence(session, plan),
        beats = if (cover?.kind == CoverKind.LOW_PROFILE) 1 else 2
    )
    return basePlan.takeIf { canVotePlanTarget(session, voter, it) }
}

private fun traitorPlanVoteReason(plan: TraitorPlan, targetName: String): String {
    val cover = plan.cover
    return when {
        cover?.kind == CoverKind.COUNTER_CLAIM && cover.targetToDirty == targetName ->
            "se contradijo con el rol y fuerza una lectura"
        cover?.kind == CoverKind.FAKE_CLAIM ->
            "viene empujando raro y deja huecos"
        cover?.kind == CoverKind.BUS_ALLY ->
            "ya queda demasiado quemado y no conviene taparlo"
        plan.killRationale == KillRationale.NOS_MARCO ->
            "viene marcando sin cerrar la historia"
        plan.killRationale == KillRationale.LIDER_DE_OPINION ->
            "esta ordenando el voto demasiado facil"
        else ->
            "es el hilo que mas ordena la votacion"
    }
}

private fun traitorPlanVoteConfidence(session: GameSession, plan: TraitorPlan): Int {
    return when (plan.cover?.kind) {
        CoverKind.COUNTER_CLAIM -> if (session.botDifficulty == BotDifficulty.HARD) 18 else 14
        CoverKind.FAKE_CLAIM -> if (session.botDifficulty == BotDifficulty.HARD) 15 else 11
        CoverKind.BUS_ALLY -> 19
        CoverKind.LOW_PROFILE -> if (session.botDifficulty == BotDifficulty.HARD) 13 else 9
        null -> if (session.botDifficulty == BotDifficulty.HARD) 12 else 8
    }
}

internal fun conversationVotePlan(
    session: GameSession,
    voter: GamePlayer,
    precomputedRanked: List<SuspectRead>? = null
): VotePlan? {
    val aliveNames = voteCandidatesFor(session, voter)
        .filter { it.name != voter.name }
        .map { it.name }
        .toSet()
    if (aliveNames.isEmpty()) return null

    val social = socialRead(session, voter)
    val ranked = precomputedRanked ?: rankedPublicSuspects(session, voter)
    val usefulPublicReads = aliveNames.associateWith { name -> hasUsefulPublicRead(session, name) }
    val rawPlans = mutableListOf<VotePlan>()

    aliveNames.forEach { name ->
        publicContradiction(session, name)?.let { contradiction ->
            val reason = if (contradiction.latest.roleKey != null) {
                "se contradijo con el rol"
            } else {
                "cambio la version de su accion"
            }
            rawPlans += VotePlan(name, reason, 18)
        }
    }

    ranked.forEach { read ->
        val name = read.player.name
        latestClaimBySpeaker(session, name)?.let { claim ->
            if (publicClaimants(session, claim.roleKey).size > 1) {
                rawPlans += VotePlan(name, "dos dijeron el mismo rol y uno miente", 15)
            }
        }
    }

    social.ignoredBy
        ?.takeIf { it in aliveNames }
        ?.let { target ->
            if (usefulPublicReads[target] == true) {
                rawPlans += VotePlan(target, "respondió a medias pero dejó una pista", 5)
            } else {
                rawPlans += VotePlan(target, "dejo una pregunta colgada", 12)
            }
        }
    social.pressured
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "viene esquivando una presion de antes", 10) }

    votePluralityTarget(session, voter)
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "el pueblo ya lo esta empujando", 8) }

    humanSuggestedVoteTarget(session)
        ?.takeIf { it in aliveNames }
        ?.let { target ->
            val confidence = if (session.botDifficulty == BotDifficulty.HARD) 11 else 9
            rawPlans += VotePlan(target, "vos lo marcaste y merece respuesta", confidence)
        }

    rawPlans += historicalVotePlans(session, voter, ranked, aliveNames)

    roundObjectiveFor(session, voter).takeIf {
        it.type in setOf(RoundObjectiveType.PUSH_VOTE, RoundObjectiveType.FOLLOW_CONTRADICTION) &&
            it.target in aliveNames
    }?.let { objective ->
        rawPlans += VotePlan(
            objective.target.orEmpty(),
            objective.reason.ifBlank { "es el hilo mas fuerte de la ronda" },
            objective.confidence.coerceAtLeast(10)
        )
    }

    declaredSuspicionTarget(session, voter)
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "yo ya venia marcando eso", 9) }

    ranked.firstOrNull()?.takeIf { it.score >= 9 }?.let { read ->
        rawPlans += VotePlan(
            read.player.name,
            informalReason(read.reason(), "vote-plan:${voter.name}"),
            read.score.coerceIn(9, 13)
        )
    }

    val plans = rawPlans
        .map { plan ->
            if (usefulPublicReads[plan.target] == true) {
                plan.copy(
                    reason = "dejo una pista, aunque falta cerrar su explicacion",
                    confidence = (plan.confidence - 14).coerceIn(1, 3)
                )
            } else {
                plan
            }
        }
        .map { plan -> softenHumanVotePlanInNormal(session, plan) }
        .filter { plan -> plan.confidence >= 4 && canVotePlanTarget(session, voter, plan) }
        .distinctBy { it.target to it.reason }
    return choosePlanForDifficulty(session, voter, plans)
}

private fun softenHumanVotePlanInNormal(session: GameSession, plan: VotePlan): VotePlan {
    if (session.botDifficulty != BotDifficulty.NORMAL) return plan
    val target = GameEngine.playerByName(session, plan.target) ?: return plan
    if (!target.isHuman) return plan
    // La evidencia dura (contradiccion conf 18 / doble claim 15) NO se ablanda; solo los
    // planes blandos (manada, pregunta colgada, presion previa) para cortar el voto por manada
    // sobre el unico humano en modo Normal.
    if (plan.confidence >= 15) return plan
    return plan.copy(confidence = (plan.confidence - HUMAN_NORMAL_VOTE_RELIEF).coerceAtLeast(0))
}

internal fun choosePlanForDifficulty(
    session: GameSession,
    voter: GamePlayer,
    plans: List<VotePlan>
): VotePlan? {
    val sorted = plans.sortedWith(
        compareByDescending<VotePlan> { it.confidence }
            .thenByDescending { it.beats }
            .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.target}:vote-plan") }
            .thenBy { it.target }
    )
    if (session.botDifficulty == BotDifficulty.HARD) return sorted.firstOrNull()
    val top = sorted.firstOrNull() ?: return null
    if (competitivenessFor(session, voter) == BotCompetitiveness.OBSESIVO) return top
    val closePlans = sorted
        .drop(1)
        .filter { plan -> top.confidence - plan.confidence <= 3 }
    if (closePlans.isEmpty()) return top
    val seed = stableNoise("${session.code}:${session.round}:${voter.name}:normal-vote-wobble:${socialChatSize(session)}")
    val changesCourse = when (competitivenessFor(session, voter)) {
        BotCompetitiveness.RELAJADO -> seed % 2 == 0
        BotCompetitiveness.EQUILIBRADO -> seed % 4 == 0
        BotCompetitiveness.OBSESIVO -> false
    }
    return if (changesCourse) {
        closePlans[seed % closePlans.size]
    } else {
        top
    }
}

internal fun historicalVotePlans(
    session: GameSession,
    voter: GamePlayer,
    ranked: List<SuspectRead>,
    aliveNames: Set<String>
): List<VotePlan> {
    val memory = conversationMemory(session)
    val plurality = votePluralityTarget(session, voter)
    val humanTarget = humanSuggestedVoteTarget(session)
    val declared = declaredSuspicionTarget(session, voter)
    val expelled = latestExpelledTarget(session)
    return aliveNames.mapNotNull { name ->
        val playerMemory = memory[name]
        val reasons = mutableListOf<String>()
        var confidence = 0

        publicContradiction(session, name)?.let { contradiction ->
            confidence += if (contradiction.latest.roleKey != null) {
                if (session.botDifficulty == BotDifficulty.HARD) 20 else 16
            } else {
                if (session.botDifficulty == BotDifficulty.HARD) 17 else 13
            }
            reasons += if (contradiction.latest.roleKey != null) {
                "primero dijo un rol y despues otro"
            } else {
                "cambio la historia de su accion"
            }
        }

        latestClaimBySpeaker(session, name)?.let { claim ->
            if (publicClaimants(session, claim.roleKey).size > 1) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 16 else 12
                reasons += "dos dijeron el mismo rol"
            } else if (!hasUsefulPublicRead(session, name)) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 3 else 4
                reasons += "tiro rol y falta detalle"
            }
        }

        playerMemory?.pendingQuestionFrom?.let {
            if (hasUsefulPublicRead(session, name)) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
                reasons += "respondió a medias pero dejó una pista"
            } else {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 13 else 10
                reasons += "dejo una pregunta colgada"
            }
        }
        val accusers = playerMemory?.accusedBy.orEmpty().size
        if (accusers >= 2) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) {
                (accusers * 5).coerceAtMost(14)
            } else {
                (accusers * 4).coerceAtMost(12)
            }
            reasons += "lo marcaron varios"
        } else if (accusers == 1) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 4 else 5
            reasons += "alguien lo marco"
        }

        val defenders = playerMemory?.defendedBy.orEmpty().size
        if (defenders >= 2) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
            reasons += "lo estan bancando demasiado"
        } else if (defenders == 1) {
            confidence -= 1
        }

        when (playerMemory?.latestStatement?.type) {
            StatementType.REFUSED_ROLE -> {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 6 else 4
                reasons += "esquivo el rol"
            }
            StatementType.PROTECTED,
            StatementType.INVESTIGATED -> {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 4 else 2
                reasons += "dio info a medias"
            }
            else -> Unit
        }

        if (name == plurality) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 9
            reasons += "ya junta votos"
        }
        if (name == humanTarget) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 8 else 6
            reasons += "vos lo marcaste"
        }
        if (name == declared) {
            confidence += 6
            reasons += "vengo marcando eso"
        }
        if (expelled != null && pushedPublicTarget(session, name, expelled)) {
            confidence += 5
            reasons += "empujo mal ayer"
        }
        if (followedPluralityWithoutReason(session, name)) {
            confidence += 4
            reasons += "se subio al monton sin explicar"
        }

        val rankedRead = ranked.firstOrNull { it.player.name == name }
        if (rankedRead != null && rankedRead.score >= 7) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) {
                (rankedRead.score / 2).coerceAtMost(7)
            } else {
                (rankedRead.score / 3).coerceAtMost(4)
            }
            reasons += informalReason(rankedRead.reason(), "history-vote:${voter.name}:$name")
        }

        val distinctReasons = reasons.distinct()
        if (session.botDifficulty == BotDifficulty.HARD && distinctReasons.size >= 2) {
            confidence += 3
        }
        val minimumConfidence = if (session.botDifficulty == BotDifficulty.HARD) {
            10
        } else {
            when (competitivenessFor(session, voter)) {
                BotCompetitiveness.RELAJADO -> 10
                BotCompetitiveness.EQUILIBRADO -> 8
                BotCompetitiveness.OBSESIVO -> 6
            }
        }
        if (confidence < minimumConfidence || distinctReasons.isEmpty()) return@mapNotNull null
        VotePlan(
            target = name,
            reason = historyReason(distinctReasons),
            confidence = confidence.coerceAtMost(if (session.botDifficulty == BotDifficulty.HARD) 28 else 22),
            beats = distinctReasons.size
        )
    }
}

internal fun canVotePlanTarget(session: GameSession, voter: GamePlayer, plan: VotePlan): Boolean {
    val target = GameEngine.playerByName(session, plan.target) ?: return false
    if (!target.alive || target.name == voter.name) return false
    if (!isTraitor(voter) || !isTraitor(target)) return true
    if (plan.confidence >= 17 && session.botDifficulty == BotDifficulty.NORMAL) {
        return stableNoise("${session.code}:${session.round}:${voter.name}:${target.name}:traitor-bus") % 5 == 0
    }
    return plan.confidence >= 17 && session.botDifficulty == BotDifficulty.HARD
}

internal fun votePluralityTarget(session: GameSession, voter: GamePlayer): String? {
    val votes = session.votes
        .filterKeys { it != voter.name }
        .values
        .filter { name -> GameEngine.playerByName(session, name)?.alive == true }
    return votes
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= 2 }
        .maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { -stableNoise("${session.code}:${session.round}:${it.key}:plurality") }
        )
        ?.key
}

internal fun humanSuggestedVoteTarget(session: GameSession): String? {
    val human = GameEngine.humanPlayer(session)
    val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
    session.claimLedger[human.name].orEmpty()
        .asReversed()
        .firstOrNull {
            it.target in aliveNames &&
                it.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE)
        }
        ?.target
        ?.let { return it }
    return recentPublicMessages(session)
        .asReversed()
        .filter { it.speaker == human.name }
        .mapNotNull { LocalBotAi.publicStatementFrom(session, it.message) }
        .firstOrNull { it.type == StatementType.ACCUSE || it.type == StatementType.VOTE }
        ?.target
}

internal fun historyReason(reasons: List<String>): String {
    val primary = reasons.take(3)
    return when (primary.size) {
        0 -> "la historia de la ronda lo deja mal"
        1 -> primary.first()
        2 -> "${primary[0]} y ${primary[1]}"
        else -> "${primary[0]}, ${primary[1]} y ${primary[2]}"
    }
}

internal fun pushedPublicTarget(session: GameSession, speaker: String, target: String): Boolean {
    return recentPublicMessages(session).any { message ->
        message.speaker == speaker &&
            mentionsName(message.message, target) &&
            (
                hasAnySignal(message.message, accusationWords) ||
                    LocalBotAi.publicStatementFrom(session, message.message)?.type in setOf(StatementType.ACCUSE, StatementType.VOTE)
                )
    }
}

internal fun followedPluralityWithoutReason(session: GameSession, speaker: String): Boolean {
    val voteTarget = session.votes[speaker] ?: return false
    val plurality = session.votes
        .values
        .filter { target -> GameEngine.playerByName(session, target)?.alive == true }
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= 2 }
        .maxByOrNull { it.value }
        ?.key
        ?: return false
    if (voteTarget != plurality) return false
    return recentPublicMessages(session)
        .filter { it.speaker == speaker }
        .none { message ->
            mentionsName(message.message, voteTarget) &&
                (
                    hasAnySignal(message.message, accusationWords) ||
                        message.message.contains("porque", ignoreCase = true) ||
                        message.message.contains("pq", ignoreCase = true)
                    )
        }
}
