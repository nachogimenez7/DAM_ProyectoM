package com.traidores.juego

/** Builds short, coherent same-speaker bursts without turning every line into chat spam. */
internal object BotMessageBursts {
    fun afterIdleLine(
        session: GameSession,
        speaker: String,
        primaryMessage: String
    ): List<String> {
        val bot = GameEngine.playerByName(session, speaker) ?: return emptyList()
        val objective = roundObjectiveFor(session, bot)
        val followUpCount = idleFollowUpCount(session, bot, objective, primaryMessage)
        if (followUpCount == 0) return emptyList()

        val target = objective.target
            ?: rankedPublicSuspects(session, bot).firstOrNull()?.player?.let { safeName(it, session) }
            ?: return emptyList()
        val reason = objective.reason.ifBlank { "todavia falta ordenar lo que dijo" }
        val options = when (objective.type) {
            RoundObjectiveType.ASK_PLAYER -> listOf(
                "no te estoy acusando, quiero entender como llegaste a esa lectura",
                "$target, si no tenes un sospechoso decime al menos a quien no votarias"
            )
            RoundObjectiveType.PUSH_VOTE -> listOf(
                "no tiro el nombre porque si: $reason",
                "si $target responde eso bien, puedo cambiar"
            )
            RoundObjectiveType.FOLLOW_CONTRADICTION -> listOf(
                "lo concreto es esto: $reason",
                "$target, responde ese punto y despues seguimos"
            )
            RoundObjectiveType.DEFLECT_PRESSURE -> listOf(
                "no quiero cambiar de tema; lo marco por esto: $reason",
                "si $target lo aclara, lo bajo de mi lista"
            )
            RoundObjectiveType.DEFEND_PLAYER -> listOf(
                "igual que $target hable, defenderlo no significa darle carta libre",
                "si aparece algo concreto cambio de postura"
            )
            RoundObjectiveType.CALM_TABLE -> listOf(
                "arranquemos por una pregunta concreta",
                "$target, que dato tenes de esta ronda?"
            )
        }
        return styleFollowUps(session, bot, options, "idle-burst:$primaryMessage", followUpCount)
    }

    fun afterHumanReply(
        session: GameSession,
        speaker: String,
        humanMessage: String,
        primaryMessage: String
    ): List<String> {
        val bot = GameEngine.playerByName(session, speaker) ?: return emptyList()
        val directAddressee = BotPerception.directAddressee(session, humanMessage)
        val questionKind = BotPerception.humanQuestionKind(humanMessage)
        val roleClaim = LocalBotAi.roleClaimFrom(humanMessage)
        val direct = directAddressee == bot.name
        val target = mentionedPlayerNames(session, humanMessage)
            .firstOrNull { it != bot.name }
            ?: mentionedPlayerNames(session, primaryMessage).firstOrNull { it != bot.name }
        val read = target?.let { targetName ->
            rankedPublicSuspects(session, bot, setOf(targetName))
                .firstOrNull { it.player.name == targetName }
        }
        val hasRecordedVote = session.actionHistory.any { action ->
            action.actor == bot.name && action.type == GameActionType.VOTE
        }
        val hasRecordedStance = session.claimLedger[bot.name].orEmpty().any { record ->
            record.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE)
        }
        val primaryStatesSuspicion = target != null && (
            hasAnySignal(primaryMessage, accusationWords) ||
                normalizedForParsing(primaryMessage).contains("hace ruido")
            )
        val hasExplainedStance = hasRecordedStance || primaryStatesSuspicion

        val options = when {
            direct && questionKind == HumanQuestionKind.ASK_ROLE -> listOf(
                "ahora decime que te hizo preguntarme justo a mi"
            )
            direct && questionKind == HumanQuestionKind.WHY_VOTE && !hasRecordedVote -> emptyList()
            direct && questionKind == HumanQuestionKind.WHY_ACCUSE && !hasExplainedStance -> emptyList()
            direct && questionKind == HumanQuestionKind.EXPLAIN_STANCE && !hasExplainedStance -> emptyList()
            direct && questionKind in setOf(
                HumanQuestionKind.WHY_VOTE,
                HumanQuestionKind.WHY_ACCUSE,
                HumanQuestionKind.EXPLAIN_STANCE
            ) -> listOf(
                if (hasGroundedSuspicion(read)) {
                    "no salio de la nada: ${read?.reason().orEmpty()}"
                } else {
                    "no salio de una pista firme; primero quiero que responda"
                },
                if (target != null) "$target puede hacerme cambiar si explica ese punto" else "si aparece algo mejor cambio"
            )
            direct && questionKind in setOf(HumanQuestionKind.OPINION, HumanQuestionKind.BELIEF) -> listOf(
                if (target != null) "a $target lo quiero escuchar antes de cerrar una postura" else "todavia no cerraria el voto"
            )
            direct && humanMessage.length >= 55 -> listOf(
                "te respondo eso puntual y despues vemos el resto"
            )
            roleClaim != null &&
                stableNoise("${session.code}:${session.phaseIndex}:claim-burst:${bot.name}:$humanMessage") % 100 < 28 ->
                when (roleClaim.roleKey) {
                    RoleCatalog.ALDEANO -> listOf(
                        "${safeName(GameEngine.humanPlayer(session), session)}, entonces juga: a quien miras?"
                    )
                    RoleCatalog.ASESINO,
                    RoleCatalog.MERCENARIO,
                    RoleCatalog.ESPIA -> listOf(
                        "lo decis posta o estas buscando que saltemos?"
                    )
                    else -> listOf(
                        "igual el nombre del rol solo no alcanza, conta algo de la ronda"
                    )
                }
            else -> emptyList()
        }
        if (options.isEmpty()) return emptyList()

        val followUpCount = when {
            questionKind == HumanQuestionKind.ASK_ROLE -> 1
            questionKind in setOf(
                HumanQuestionKind.WHY_VOTE,
                HumanQuestionKind.WHY_ACCUSE,
                HumanQuestionKind.EXPLAIN_STANCE
            ) && hasExplainedStance -> 2
            else -> 1
        }
        return styleFollowUps(session, bot, options, "human-burst:$primaryMessage", followUpCount)
    }

    fun afterTraitorNightLine(
        session: GameSession,
        speaker: String,
        primaryMessage: String
    ): List<String> {
        val bot = GameEngine.playerByName(session, speaker) ?: return emptyList()
        val plan = session.traitorPlan ?: return emptyList()
        val roll = stableNoise("${session.code}:${session.round}:traitor-burst:$speaker:$primaryMessage") % 100
        if (roll >= 28) return emptyList()
        val target = plan.killTarget.ifBlank { plan.dayPushTarget }.ifBlank { return emptyList() }
        val options = listOf(
            "despues en el dia no se apuren a nombrar a $target",
            "primero pregunten y dejen que el pueblo arme el motivo"
        )
        return styleFollowUps(session, bot, options, "traitor-night-burst:$primaryMessage", if (roll < 5) 2 else 1)
    }

    private fun idleFollowUpCount(
        session: GameSession,
        bot: GamePlayer,
        objective: RoundObjective,
        primaryMessage: String
    ): Int {
        val roll = stableNoise(
            "${session.code}:${session.round}:${session.phaseIndex}:${bot.name}:idle-burst:$primaryMessage"
        ) % 100
        val competitivenessAdjustment = when (competitivenessFor(session, bot)) {
            BotCompetitiveness.RELAJADO -> -8
            BotCompetitiveness.EQUILIBRADO -> 0
            BotCompetitiveness.OBSESIVO -> 12
        }
        val allowsThree = objective.type in setOf(
            RoundObjectiveType.PUSH_VOTE,
            RoundObjectiveType.FOLLOW_CONTRADICTION,
            RoundObjectiveType.DEFLECT_PRESSURE
        ) && objective.confidence >= 10
        return if (session.botDifficulty == BotDifficulty.HARD) {
            when {
                allowsThree && roll < 5 + competitivenessAdjustment / 3 -> 2
                roll < 32 + competitivenessAdjustment -> 1
                else -> 0
            }
        } else {
            when {
                allowsThree && roll < 6 + competitivenessAdjustment / 3 -> 2
                roll < 26 + competitivenessAdjustment -> 1
                else -> 0
            }
        }
    }

    private fun styleFollowUps(
        session: GameSession,
        bot: GamePlayer,
        options: List<String>,
        context: String,
        count: Int
    ): List<String> {
        return options
            .take(count.coerceIn(0, 2))
            .mapIndexed { index, raw ->
                finishSpeech(raw, session, bot, "$context:$index")
            }
            .filter { it.isNotBlank() }
            .distinctBy(::normalizedForParsing)
    }
}
