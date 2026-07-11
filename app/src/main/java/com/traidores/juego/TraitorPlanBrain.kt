package com.traidores.juego

internal object TraitorPlanBrain {

    fun build(session: GameSession): TraitorPlan? {
        val traitors = GameEngine.aliveTraitors(session)
        if (traitors.isEmpty()) return null

        val killer = GameEngine.alivePlayers(session)
            .firstOrNull { it.role?.key in GameRules.killerRoleKeys }
        val threats = detectThreats(session, traitors)
        val cover = chooseCover(session, traitors, threats)
        val rawKillTarget = killer?.let { LocalBotAi.chooseAssassinTargetWithoutPlan(session, it) }.orEmpty()
        val killTarget = if (
            killer != null &&
            cover?.kind == CoverKind.COUNTER_CLAIM &&
            rawKillTarget == cover.targetToDirty
        ) {
            alternateKillTarget(session, killer, exclude = cover.targetToDirty.orEmpty())
                ?: rawKillTarget
        } else {
            rawKillTarget
        }
        val dayPushTarget = chooseDayPushTarget(
            session = session,
            traitors = traitors,
            threats = threats,
            cover = cover,
            killTarget = killTarget
        )

        return TraitorPlan(
            round = session.round,
            killTarget = killTarget,
            killRationale = killRationaleFor(session, killTarget, threats),
            dayPushTarget = dayPushTarget,
            threats = threats,
            cover = cover,
            speakingOrder = traitors
                .sortedWith(
                    compareBy<GamePlayer> {
                        stableNoise("${session.code}:${session.round}:${it.name}:traitor-speaking")
                    }.thenBy { it.name }
                )
                .map { it.name }
        )
    }

    private fun detectThreats(
        session: GameSession,
        traitors: List<GamePlayer>
    ): List<TraitorThreat> {
        val traitorNames = traitors.map { it.name }.toSet()
        val memory = conversationMemory(session)
        val threats = mutableListOf<TraitorThreat>()
        aliveNonTraitorOpposition(session).forEach { player ->
            val playerMemory = memory[player.name]
            val markedTraitor = playerMemory
                ?.accusedTargets
                .orEmpty()
                .firstOrNull { it in traitorNames }
                ?: latestStatementBySpeaker(session, player.name)
                    ?.takeIf {
                        it.target != null &&
                            it.target in traitorNames &&
                            it.type in setOf(
                                StatementType.ACCUSE,
                                StatementType.VOTE,
                                StatementType.INVESTIGATED
                            )
                    }
                    ?.target
                ?: session.tableMemory.declaredInvestigationReads
                    .asReversed()
                    .firstOrNull {
                        it.source == player.name &&
                            it.target in traitorNames &&
                            it.result != "inocente"
                    }
                    ?.target

            val declaredDetective = latestClaimBySpeaker(session, player.name)?.roleKey == RoleCatalog.POLICIA ||
                session.tableMemory.declaredInvestigationReads.any { it.source == player.name }
            if (declaredDetective) {
                threats += TraitorThreat(
                    player = player.name,
                    kind = ThreatKind.DETECTIVE_DECLARADO,
                    markedTraitor = markedTraitor
                )
            }
            if (markedTraitor != null) {
                threats += TraitorThreat(
                    player = player.name,
                    kind = ThreatKind.NOS_MARCO_SOSPECHA,
                    markedTraitor = markedTraitor
                )
            }
            if (looksLikeTownOrganizer(session, player, playerMemory)) {
                threats += TraitorThreat(
                    player = player.name,
                    kind = ThreatKind.JUNTA_VOTOS,
                    markedTraitor = null
                )
            }
        }
        return threats
            .distinctBy { "${it.player}:${it.kind}:${it.markedTraitor}" }
            .sortedWith(
                compareByDescending<TraitorThreat> { threatWeight(it) }
                    .thenBy { stableNoise("${session.code}:${session.round}:${it.player}:${it.kind}") }
                    .thenBy { it.player }
            )
    }

    private fun chooseCover(
        session: GameSession,
        traitors: List<GamePlayer>,
        threats: List<TraitorThreat>
    ): CoverMove? {
        val traitorNames = traitors.map { it.name }.toSet()
        val counterThreat = threats.firstOrNull {
            it.kind == ThreatKind.DETECTIVE_DECLARADO &&
                it.markedTraitor != null &&
                it.markedTraitor in traitorNames
        }
        if (
            session.botDifficulty == BotDifficulty.HARD &&
            traitors.size >= 2 &&
            counterThreat != null &&
            !hasTwoConsistentReads(session, counterThreat.player)
        ) {
            val actor = counterThreat.markedTraitor.orEmpty()
            return CoverMove(
                kind = CoverKind.COUNTER_CLAIM,
                actor = actor,
                backer = traitors.firstOrNull { it.name != actor }?.name,
                fakeRoleKey = RoleCatalog.POLICIA,
                targetToDirty = counterThreat.player
            )
        }

        val exposedTraitor = traitors
            .map { it to traitorHeat(session, it) }
            .filter { (_, heat) -> heat >= if (session.botDifficulty == BotDifficulty.HARD) 10 else 14 }
            .maxWithOrNull(
                compareBy<Pair<GamePlayer, Int>> { it.second }
                    .thenBy { stableNoise("${session.code}:${session.round}:${it.first.name}:cover") }
            )
            ?.first
        if (exposedTraitor != null) {
            return CoverMove(
                kind = CoverKind.FAKE_CLAIM,
                actor = exposedTraitor.name,
                backer = traitors.firstOrNull { it.name != exposedTraitor.name }?.name,
                fakeRoleKey = if (session.botDifficulty == BotDifficulty.HARD) {
                    RoleCatalog.MEDICO
                } else {
                    RoleCatalog.ALDEANO
                },
                targetToDirty = null
            )
        }

        return traitors.firstOrNull()?.let {
            CoverMove(
                kind = CoverKind.LOW_PROFILE,
                actor = it.name,
                backer = null,
                fakeRoleKey = null,
                targetToDirty = null
            )
        }
    }

    private fun chooseDayPushTarget(
        session: GameSession,
        traitors: List<GamePlayer>,
        threats: List<TraitorThreat>,
        cover: CoverMove?,
        killTarget: String
    ): String {
        val traitorNames = traitors.map { it.name }.toSet()
        cover?.targetToDirty
            ?.takeIf { isAliveOpposition(session, it) }
            ?.let { return it }

        threats.firstOrNull {
            it.kind == ThreatKind.DETECTIVE_DECLARADO &&
                it.markedTraitor != null &&
                it.markedTraitor in traitorNames
        }?.player
            ?.takeIf { isAliveOpposition(session, it) }
            ?.let { return it }

        val planner = traitors.firstOrNull() ?: return ""
        return aliveNonTraitorOpposition(session)
            .filter { it.name != killTarget }
            .map { candidate ->
                candidate to dayPushScore(session, planner, candidate)
            }
            .maxWithOrNull(
                compareBy<Pair<GamePlayer, Int>> { it.second }
                    .thenBy { stableNoise("${session.code}:${session.round}:${it.first.name}:day-push") }
            )
            ?.first
            ?.name
            .orEmpty()
    }

    private fun killRationaleFor(
        session: GameSession,
        killTarget: String,
        threats: List<TraitorThreat>
    ): KillRationale {
        if (killTarget.isBlank()) return KillRationale.SIN_LECTURA
        val targetThreats = threats.filter { it.player == killTarget }
        return when {
            targetThreats.any { it.kind == ThreatKind.DETECTIVE_DECLARADO } ->
                KillRationale.CONFIRMA_ROL
            targetThreats.any { it.kind == ThreatKind.NOS_MARCO_SOSPECHA } ->
                KillRationale.NOS_MARCO
            targetThreats.any { it.kind == ThreatKind.JUNTA_VOTOS } ->
                KillRationale.LIDER_DE_OPINION
            votePluralityTarget(session, GameEngine.humanPlayer(session)) == killTarget ->
                KillRationale.JUNTA_VOTOS_LIMPIOS
            session.round > 1 && recentPublicMessages(session).count { it.speaker == killTarget } <= 1 ->
                KillRationale.CALLADO_PELIGROSO
            nightPressureForName(session, killTarget) >= 8 ->
                KillRationale.LIDER_DE_OPINION
            else -> KillRationale.SIN_LECTURA
        }
    }

    private fun alternateKillTarget(
        session: GameSession,
        killer: GamePlayer,
        exclude: String
    ): String? {
        return withoutHumanIfDebug(
            session.debugBotsNeverKillHuman,
            GameEngine.alivePlayers(session)
                .filter { it.name != exclude }
                .filter { GameEngine.isValidKillTarget(session, it.name, killer) }
        )
            .sortedWith(
                compareByDescending<GamePlayer> {
                    nightPressureScore(session, it) + humanNightTargetBonus(session, it, "kill")
                }
                    .thenBy { stableNoise("${session.code}:${session.round}:${killer.name}:${it.name}:plan-alt-kill") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
    }

    private fun aliveNonTraitorOpposition(session: GameSession): List<GamePlayer> {
        return GameEngine.alivePlayers(session).filterNot {
            GameRules.isTraitorRole(it.role) || GameEngine.isDesertorAlignedWithTraitors(session, it)
        }
    }

    private fun isAliveOpposition(session: GameSession, playerName: String): Boolean {
        return aliveNonTraitorOpposition(session).any { it.name == playerName }
    }

    private fun looksLikeTownOrganizer(
        session: GameSession,
        player: GamePlayer,
        memory: PlayerConversationMemory?
    ): Boolean {
        val plurality = GameEngine.aliveTraitors(session)
            .firstOrNull()
            ?.let { votePluralityTarget(session, it) }
        val defendedByCount = memory?.defendedBy.orEmpty().size
        val spokeCount = recentPublicMessages(session).count { it.speaker == player.name }
        return player.name == plurality || defendedByCount >= 2 || spokeCount >= 3
    }

    private fun hasTwoConsistentReads(session: GameSession, detectiveName: String): Boolean {
        return session.tableMemory.declaredInvestigationReads
            .filter { it.source == detectiveName }
            .map { it.target to it.result }
            .distinct()
            .size >= 2
    }

    private fun traitorHeat(session: GameSession, traitor: GamePlayer): Int {
        val memory = conversationMemory(session)[traitor.name]
        val tableHeat = session.tableMemory.suspicion.values.sumOf { targets ->
            targets[traitor.name]?.coerceAtLeast(0) ?: 0
        }
        return tableHeat +
            memory?.accusedBy.orEmpty().size * 5 +
            recentPublicMessages(session).count {
                mentionsName(it.message, traitor.name) && hasAnySignal(it.message, accusationWords)
            } * 3
    }

    private fun dayPushScore(
        session: GameSession,
        planner: GamePlayer,
        candidate: GamePlayer
    ): Int {
        val tableScore = session.tableMemory.suspicion.values.sumOf { targets ->
            targets[candidate.name]?.coerceAtLeast(0) ?: 0
        }
        val publicReadScore = rankedPublicSuspects(session, planner)
            .firstOrNull { it.player.name == candidate.name }
            ?.score
            ?: 0
        val claimPenalty = latestClaimBySpeaker(session, candidate.name)
            ?.takeIf { hasUsefulPublicRead(session, candidate.name) }
            ?.let { -4 }
            ?: 0
        return tableScore + publicReadScore + claimPenalty
    }

    private fun nightPressureForName(session: GameSession, playerName: String): Int {
        val player = GameEngine.playerByName(session, playerName) ?: return 0
        return nightPressureScore(session, player)
    }

    private fun threatWeight(threat: TraitorThreat): Int {
        return when (threat.kind) {
            ThreatKind.DETECTIVE_DECLARADO -> if (threat.markedTraitor != null) 90 else 70
            ThreatKind.NOS_MARCO_SOSPECHA -> 55
            ThreatKind.JUNTA_VOTOS -> 35
        }
    }
}
