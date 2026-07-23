package com.traidores.juego

internal object BotTableMemory {
    private const val MAX_READS = 24

    fun recordPublicMessage(
        session: GameSession,
        speaker: String,
        message: String,
        roleClaim: RoleClaim? = LocalBotAi.roleClaimFrom(message),
        publicStatement: PublicStatement? = LocalBotAi.publicStatementFrom(session, message)
    ): TableMemory {
        val speakerPlayer = GameEngine.playerByName(session, speaker)
        var memory = session.tableMemory.copy(
            pendingQuestions = session.tableMemory.pendingQuestions - speaker
        )
        if (speakerPlayer == null || !speakerPlayer.alive) {
            return trim(memory, session)
        }

        val directTarget = BotPerception.directAddressee(session, message)
        if (message.contains("?")) {
            (directTarget?.let(::listOf) ?: mentionedPlayerNames(session, message))
                .filter { it != speaker }
                .forEach { target ->
                    memory = memory.copy(
                        pendingQuestions = memory.pendingQuestions + (
                            target to PendingQuestion(
                                round = session.round,
                                source = speaker,
                                target = target,
                                message = message.take(140)
                            )
                        )
                    )
                }
            if (
                directTarget != null &&
                BotPerception.humanQuestionKind(message) == HumanQuestionKind.ASK_ROLE
            ) {
                memory = adjustEmotionalPressure(memory, directTarget, 1)
            }
        }
        if (speakerPlayer.isHuman && directTarget != null) {
            memory = recordHumanSocialSignal(memory, speaker, directTarget, message)
        }

        publicStatement?.let { statement ->
            memory = applyStatementPressure(session, memory, speaker, roleClaim, statement)
            memory = recordSocialImpact(session, memory, speaker, statement)
            memory = recordDeclaredInvestigation(session, memory, speaker, roleClaim, statement)
        }

        return trim(memory, session)
    }

    fun recordPrivateInvestigation(
        memory: TableMemory,
        round: Int,
        source: String,
        target: String,
        result: String
    ): TableMemory {
        val read = InvestigationRead(round, source, target, result)
        return memory.copy(
            privateInvestigationReads = (
                memory.privateInvestigationReads.filterNot {
                    it.round == round && it.source == source && it.target == target
                } + read
                ).takeLast(MAX_READS)
        )
    }

    fun decayForNewRound(memory: TableMemory, players: List<GamePlayer>, newRound: Int): TableMemory {
        val aliveNames = players.filter { it.alive }.map { it.name }.toSet()
        val suspicion = memory.suspicion
            .filterKeys { it in aliveNames }
            .mapValues { (_, targets) ->
                targets
                    .filterKeys { it in aliveNames }
                    .mapValues { (_, score) -> score.decayTowardZero() }
                    .filterValues { it != 0 }
            }
            .filterValues { it.isNotEmpty() }
        val rapport = memory.rapport
            .filterKeys { it in aliveNames }
            .mapValues { (_, relations) ->
                relations
                    .filterKeys { it in aliveNames }
                    .mapValues { (_, score) -> score.decayTowardZero() }
                    .filterValues { it != 0 }
            }
            .filterValues { it.isNotEmpty() }
        return memory.copy(
            suspicion = suspicion,
            rapport = rapport,
            emotionalPressure = memory.emotionalPressure
                .filterKeys { it in aliveNames }
                .mapValues { (_, pressure) -> pressure.decayTowardZero() }
                .filterValues { it != 0 },
            pendingQuestions = memory.pendingQuestions
                .filterKeys { it in aliveNames }
                .filterValues { it.source in aliveNames && newRound - it.round <= 1 },
            declaredInvestigationReads = memory.declaredInvestigationReads
                .filter { it.source in aliveNames && it.target in aliveNames }
                .takeLast(MAX_READS),
            privateInvestigationReads = memory.privateInvestigationReads
                .filter { it.source in aliveNames && it.target in aliveNames }
                .takeLast(MAX_READS)
        )
    }

    private fun applyStatementPressure(
        session: GameSession,
        memory: TableMemory,
        speaker: String,
        roleClaim: RoleClaim?,
        statement: PublicStatement
    ): TableMemory {
        val target = statement.target
        val declaredRole = roleClaim?.roleKey ?: latestPublicRoleKey(session, speaker)
        val isDeclaredPolice = declaredRole == RoleCatalog.POLICIA
        return when (statement.type) {
            StatementType.ACCUSE -> target?.let {
                adjustAllObservers(
                    session,
                    memory,
                    speaker = speaker,
                    target = it,
                    delta = if (isDeclaredPolice) 6 else 4,
                    supported = statement.reason != null || isDeclaredPolice
                )
            } ?: memory
            StatementType.VOTE -> target?.let {
                adjustAllObservers(
                    session,
                    memory,
                    speaker = speaker,
                    target = it,
                    delta = 5,
                    supported = statement.reason != null
                )
            } ?: memory
            StatementType.TRUST -> target?.let {
                adjustAllObservers(
                    session,
                    memory,
                    speaker = speaker,
                    target = it,
                    delta = if (isDeclaredPolice) -4 else -2,
                    supported = statement.reason != null || isDeclaredPolice
                )
            } ?: memory
            StatementType.REFUSED_ROLE -> adjustAllObservers(
                session,
                memory,
                speaker = speaker,
                target = speaker,
                delta = 3,
                supported = true
            )
            StatementType.INVESTIGATED -> target?.let {
                adjustAllObservers(session, memory, speaker, it, delta = 2, supported = true)
            } ?: memory
            StatementType.PROTECTED -> target?.let {
                adjustAllObservers(session, memory, speaker, it, delta = -1, supported = true)
            } ?: memory
        }
    }

    private fun recordSocialImpact(
        session: GameSession,
        memory: TableMemory,
        speaker: String,
        statement: PublicStatement
    ): TableMemory {
        val target = statement.target ?: return memory
        if (speaker == target || GameEngine.playerByName(session, target)?.alive != true) return memory
        val targetToSpeaker = when (statement.type) {
            StatementType.ACCUSE -> -3
            StatementType.VOTE -> -4
            StatementType.TRUST -> 3
            StatementType.PROTECTED -> 2
            StatementType.INVESTIGATED -> -1
            StatementType.REFUSED_ROLE -> 0
        }
        val speakerToTarget = when (statement.type) {
            StatementType.ACCUSE -> -2
            StatementType.VOTE -> -3
            StatementType.TRUST -> 2
            StatementType.PROTECTED -> 2
            StatementType.INVESTIGATED -> -1
            StatementType.REFUSED_ROLE -> 0
        }
        var rapport = memory.rapport
        if (targetToSpeaker != 0) rapport = adjustRelation(rapport, target, speaker, targetToSpeaker)
        if (speakerToTarget != 0) rapport = adjustRelation(rapport, speaker, target, speakerToTarget)
        val pressureDelta = when (statement.type) {
            StatementType.ACCUSE -> 2
            StatementType.VOTE -> 3
            StatementType.TRUST,
            StatementType.PROTECTED -> -1
            else -> 0
        }
        return adjustEmotionalPressure(memory.copy(rapport = rapport), target, pressureDelta)
    }

    private fun recordDeclaredInvestigation(
        session: GameSession,
        memory: TableMemory,
        speaker: String,
        roleClaim: RoleClaim?,
        statement: PublicStatement
    ): TableMemory {
        val target = statement.target ?: return memory
        val declaredRole = roleClaim?.roleKey ?: latestPublicRoleKey(session, speaker)
        if (declaredRole != RoleCatalog.POLICIA && statement.type != StatementType.INVESTIGATED) return memory
        val result = when (statement.type) {
            StatementType.ACCUSE -> "sospechoso"
            StatementType.TRUST -> "inocente"
            StatementType.INVESTIGATED -> "investigado"
            else -> return memory
        }
        val read = InvestigationRead(
            round = session.round,
            source = speaker,
            target = target,
            result = result
        )
        val reads = (memory.declaredInvestigationReads
            .filterNot {
                it.round == read.round &&
                    it.source == read.source &&
                    it.target == read.target &&
                    it.result == read.result
            } + read).takeLast(MAX_READS)
        return memory.copy(declaredInvestigationReads = reads)
    }

    private fun adjustAllObservers(
        session: GameSession,
        memory: TableMemory,
        speaker: String,
        target: String,
        delta: Int,
        supported: Boolean
    ): TableMemory {
        val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
        if (target !in aliveNames || delta == 0) return memory
        val suspicion = memory.suspicion.toMutableMap()
        aliveNames
            .filter { it != target }
            .forEach { observer ->
                val current = suspicion[observer].orEmpty()
                val observerDelta = individualizedDelta(
                    session = session,
                    memory = memory,
                    observerName = observer,
                    speakerName = speaker,
                    targetName = target,
                    baseDelta = delta,
                    supported = supported
                )
                val nextScore = ((current[target] ?: 0) + observerDelta).coerceIn(-12, 24)
                val nextTargets = if (nextScore == 0) {
                    current - target
                } else {
                    current + (target to nextScore)
                }
                if (nextTargets.isEmpty()) {
                    suspicion -= observer
                } else {
                    suspicion[observer] = nextTargets
                }
            }
        return memory.copy(suspicion = suspicion)
    }

    private fun individualizedDelta(
        session: GameSession,
        memory: TableMemory,
        observerName: String,
        speakerName: String,
        targetName: String,
        baseDelta: Int,
        supported: Boolean
    ): Int {
        val observer = GameEngine.playerByName(session, observerName) ?: return baseDelta
        val target = GameEngine.playerByName(session, targetName) ?: return baseDelta
        if (isTraitor(observer) && isTraitor(target)) {
            return if (baseDelta < 0) baseDelta.coerceAtMost(-2) else 0
        }
        var adjusted = baseDelta
        val sourceSuspicion = memory.suspicion[observerName]?.get(speakerName) ?: 0
        if (speakerName != observerName) {
            adjusted = when {
                sourceSuspicion >= 8 -> adjusted / 2
                sourceSuspicion <= -3 -> adjusted + if (adjusted > 0) 1 else -1
                else -> adjusted
            }
        }
        if (!observer.isHuman) {
            adjusted = when (personalityFor(session, observer)) {
                BotPersonality.TRANQUI -> if (adjusted > 0) adjusted - 1 else adjusted - 1
                BotPersonality.PICANTE -> if (adjusted > 0) adjusted + 1 else adjusted
                BotPersonality.JODON -> adjusted
                BotPersonality.DESCONFIADO -> if (adjusted > 0) adjusted + 1 else adjusted + 1
                BotPersonality.IMPULSIVO -> if (adjusted > 0) adjusted + 2 else adjusted
                BotPersonality.ANALITICO -> if (!supported) {
                    if (adjusted > 0) adjusted - 2 else adjusted + 1
                } else {
                    adjusted
                }
            }
        }
        return if (baseDelta > 0) adjusted.coerceAtLeast(0) else adjusted.coerceAtMost(0)
    }

    private fun adjustRelation(
        rapport: Map<String, Map<String, Int>>,
        observer: String,
        other: String,
        delta: Int
    ): Map<String, Map<String, Int>> {
        val current = rapport[observer].orEmpty()
        val nextScore = ((current[other] ?: 0) + delta).coerceIn(-12, 12)
        val nextRelations = if (nextScore == 0) current - other else current + (other to nextScore)
        return if (nextRelations.isEmpty()) rapport - observer else rapport + (observer to nextRelations)
    }

    private fun adjustEmotionalPressure(memory: TableMemory, target: String, delta: Int): TableMemory {
        if (delta == 0) return memory
        val next = ((memory.emotionalPressure[target] ?: 0) + delta).coerceIn(0, 12)
        val emotionalPressure = if (next == 0) {
            memory.emotionalPressure - target
        } else {
            memory.emotionalPressure + (target to next)
        }
        return memory.copy(emotionalPressure = emotionalPressure)
    }

    private fun recordHumanSocialSignal(
        memory: TableMemory,
        speaker: String,
        target: String,
        message: String
    ): TableMemory {
        val signal = BotPerception.socialSignal(message) ?: return memory
        val rapportDelta = if (signal == HumanSocialSignal.PRAISE) 2 else -2
        val pressureDelta = if (signal == HumanSocialSignal.PRAISE) -1 else 1
        val rapport = adjustRelation(memory.rapport, target, speaker, rapportDelta)
        return adjustEmotionalPressure(memory.copy(rapport = rapport), target, pressureDelta)
    }

    private fun trim(memory: TableMemory, session: GameSession): TableMemory {
        val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
        return memory.copy(
            suspicion = memory.suspicion
                .filterKeys { it in aliveNames }
                .mapValues { (_, targets) -> targets.filterKeys { it in aliveNames } }
                .filterValues { it.isNotEmpty() },
            rapport = memory.rapport
                .filterKeys { it in aliveNames }
                .mapValues { (_, relations) -> relations.filterKeys { it in aliveNames } }
                .filterValues { it.isNotEmpty() },
            emotionalPressure = memory.emotionalPressure.filterKeys { it in aliveNames },
            pendingQuestions = memory.pendingQuestions
                .filterKeys { it in aliveNames }
                .filterValues { it.source in aliveNames },
            declaredInvestigationReads = memory.declaredInvestigationReads
                .filter { it.source in aliveNames && it.target in aliveNames }
                .takeLast(MAX_READS),
            privateInvestigationReads = memory.privateInvestigationReads
                .filter { it.source in aliveNames && it.target in aliveNames }
                .takeLast(MAX_READS)
        )
    }

    private fun latestPublicRoleKey(session: GameSession, speaker: String): String? {
        return session.claimLedger[speaker].orEmpty()
            .asReversed()
            .firstOrNull { it.roleKey != null }
            ?.roleKey
    }

    private fun Int.decayTowardZero(): Int {
        return when {
            this > 0 -> (this * 2) / 3
            this < 0 -> -((-this * 2) / 3)
            else -> 0
        }
    }
}
