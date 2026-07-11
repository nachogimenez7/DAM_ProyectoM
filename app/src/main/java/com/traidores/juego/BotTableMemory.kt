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

        if (message.contains("?")) {
            mentionedPlayerNames(session, message)
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
        }

        publicStatement?.let { statement ->
            memory = applyStatementPressure(session, memory, speaker, roleClaim, statement)
            memory = recordDeclaredInvestigation(session, memory, speaker, roleClaim, statement)
        }

        return trim(memory, session)
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
        return memory.copy(
            suspicion = suspicion,
            pendingQuestions = memory.pendingQuestions
                .filterKeys { it in aliveNames }
                .filterValues { it.source in aliveNames && newRound - it.round <= 1 },
            declaredInvestigationReads = memory.declaredInvestigationReads
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
                adjustAllObservers(session, memory, target = it, delta = if (isDeclaredPolice) 6 else 4)
            } ?: memory
            StatementType.VOTE -> target?.let {
                adjustAllObservers(session, memory, target = it, delta = 5)
            } ?: memory
            StatementType.TRUST -> target?.let {
                adjustAllObservers(session, memory, target = it, delta = if (isDeclaredPolice) -4 else -2)
            } ?: memory
            StatementType.REFUSED_ROLE -> adjustAllObservers(session, memory, target = speaker, delta = 3)
            StatementType.INVESTIGATED -> target?.let {
                adjustAllObservers(session, memory, target = it, delta = 2)
            } ?: memory
            StatementType.PROTECTED -> target?.let {
                adjustAllObservers(session, memory, target = it, delta = -1)
            } ?: memory
        }
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
        target: String,
        delta: Int
    ): TableMemory {
        val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
        if (target !in aliveNames || delta == 0) return memory
        val suspicion = memory.suspicion.toMutableMap()
        aliveNames
            .filter { it != target }
            .forEach { observer ->
                val current = suspicion[observer].orEmpty()
                val nextScore = ((current[target] ?: 0) + delta).coerceIn(-12, 24)
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

    private fun trim(memory: TableMemory, session: GameSession): TableMemory {
        val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
        return memory.copy(
            suspicion = memory.suspicion
                .filterKeys { it in aliveNames }
                .mapValues { (_, targets) -> targets.filterKeys { it in aliveNames } }
                .filterValues { it.isNotEmpty() },
            pendingQuestions = memory.pendingQuestions
                .filterKeys { it in aliveNames }
                .filterValues { it.source in aliveNames },
            declaredInvestigationReads = memory.declaredInvestigationReads
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
