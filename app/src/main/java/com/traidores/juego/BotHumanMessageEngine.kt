package com.traidores.juego

/** Owns the full reaction pipeline for one human public-chat message. */
internal object BotHumanMessageEngine {
    private val directQuestionKinds = setOf(
        HumanQuestionKind.ASK_ROLE,
        HumanQuestionKind.WHY_VOTE,
        HumanQuestionKind.WHY_ACCUSE,
        HumanQuestionKind.EXPLAIN_STANCE,
        HumanQuestionKind.OPINION,
        HumanQuestionKind.BELIEF,
        HumanQuestionKind.VOTE_HELP,
        HumanQuestionKind.ACTION_HELP,
        HumanQuestionKind.SUSPECT_HELP,
        HumanQuestionKind.ROLE_HELP
    )
    private val multiReplyIntents = setOf(
        HumanMessageIntent.ROLE_CLAIM,
        HumanMessageIntent.ROLE_QUESTION,
        HumanMessageIntent.ACTION_HELP,
        HumanMessageIntent.VOTE_HELP,
        HumanMessageIntent.SUSPECT_HELP,
        HumanMessageIntent.ACCUSE,
        HumanMessageIntent.DEFEND,
        HumanMessageIntent.ACTION_CLAIM
    )

    private data class Understanding(
        val directAddressee: String?,
        val roleClaim: RoleClaim?,
        val publicStatement: PublicStatement?,
        val focusNames: Set<String>,
        val claimResponder: GamePlayer?,
        val claimsHiddenInfo: Boolean,
        val casualMessage: Boolean,
        val socialSignal: HumanSocialSignal?,
        val questionKind: HumanQuestionKind?,
        val answeredQuestion: PendingHumanQuestion?,
        val intent: HumanMessageIntent
    )

    fun reactionsTo(
        session: GameSession,
        humanMessage: String,
        intentHint: HumanMessageIntent? = null
    ): List<Pair<String, String>> {
        val understanding = understand(session, humanMessage, intentHint)
        val repeatedOffTopic = understanding.intent == HumanMessageIntent.OFF_TOPIC &&
            recentPublicMessages(session)
                .asReversed()
                .filter { it.speaker == GameEngine.humanPlayer(session).name }
                .take(2)
                .count { BotPerception.isOffTopicMessage(session, it.message) } >= 2
        if (understanding.intent == HumanMessageIntent.OFF_TOPIC && !repeatedOffTopic) {
            return emptyList()
        }
        val desiredReplyCount = when {
            understanding.directAddressee != null && understanding.questionKind in directQuestionKinds -> 1
            understanding.directAddressee != null && understanding.socialSignal != null -> 1
            understanding.intent == HumanMessageIntent.OFF_TOPIC -> 1
            understanding.roleClaim != null && understanding.publicStatement != null -> 4
            understanding.intent in multiReplyIntents || understanding.focusNames.isNotEmpty() -> 3
            understanding.publicStatement != null ||
                understanding.claimsHiddenInfo ||
                understanding.intent == HumanMessageIntent.ANSWER_PENDING ||
                humanMessage.length > 45 -> 2
            understanding.casualMessage -> 1
            else -> 1
        }
        val replyCount = limitedReplyCount(session, desiredReplyCount)
        val collectivelyUnanswered = collectivelyUnansweredTarget(session)
        val threadKeeper = collectivelyUnanswered?.let { target ->
            session.players.firstOrNull { player ->
                !player.isHuman &&
                    player.name != target &&
                    GameEngine.canParticipateInChat(session, player) &&
                    unansweredQuestionFor(session, player) == target
            }?.name
        }
        val preferredResponder = understanding.directAddressee
            ?: understanding.claimResponder?.name
            ?: understanding.answeredQuestion?.speaker
            ?: threadKeeper
        return messageBots(session, replyCount, preferredFirst = preferredResponder)
            .mapIndexed { index, bot ->
                responseFor(
                    session = session,
                    bot = bot,
                    humanMessage = humanMessage,
                    understanding = understanding,
                    repeatedOffTopic = repeatedOffTopic,
                    collectivelyUnanswered = collectivelyUnanswered,
                    index = index
                )
            }
            .dropEchoesOfRecentChat(session)
            .dedupeBotMessages()
    }

    private fun understand(
        session: GameSession,
        humanMessage: String,
        intentHint: HumanMessageIntent?
    ): Understanding {
        val directAddressee = BotPerception.directAddressee(session, humanMessage)
        val roleClaim = LocalBotAi.roleClaimFrom(humanMessage)
        val publicStatement = LocalBotAi.publicStatementFrom(session, humanMessage)
        val focusNames = when (publicStatement?.type) {
            StatementType.ACCUSE,
            StatementType.VOTE,
            StatementType.INVESTIGATED -> setOfNotNull(publicStatement.target)
            else -> emptySet()
        }
        val claimsHiddenInfo = containsSecretTerm(humanMessage, session)
        val casualMessage = isCasualHumanMessage(humanMessage)
        val socialSignal = BotPerception.socialSignal(humanMessage)
        val questionKind = humanQuestionKind(humanMessage)
        val answeredQuestion = answeredQuestionForHuman(session, humanMessage)
        val inferredIntent = if (answeredQuestion != null && humanMessage.trim().length >= 4) {
            HumanMessageIntent.ANSWER_PENDING
        } else {
            humanMessageIntent(
                session = session,
                message = humanMessage,
                roleClaim = roleClaim,
                publicStatement = publicStatement,
                claimsHiddenInfo = claimsHiddenInfo,
                casualMessage = casualMessage,
                questionKind = questionKind,
                socialSignal = socialSignal
            )
        }
        val intent = intentHint ?: inferredIntent
        return Understanding(
            directAddressee = directAddressee,
            roleClaim = roleClaim,
            publicStatement = publicStatement,
            focusNames = focusNames,
            claimResponder = roleClaim
                ?.takeIf { publiclyCounterClaimableRole(it.roleKey) }
                ?.let { botWithRole(session, it.roleKey) },
            claimsHiddenInfo = claimsHiddenInfo,
            casualMessage = casualMessage,
            socialSignal = socialSignal,
            questionKind = questionKind,
            answeredQuestion = answeredQuestion,
            intent = intent
        )
    }

    private fun publiclyCounterClaimableRole(roleKey: String): Boolean {
        return roleKey in setOf(
            RoleCatalog.MEDICO,
            RoleCatalog.POLICIA,
            RoleCatalog.ALCALDE,
            RoleCatalog.PAYADOR,
            RoleCatalog.ORACULO
        )
    }

    private fun responseFor(
        session: GameSession,
        bot: GamePlayer,
        humanMessage: String,
        understanding: Understanding,
        repeatedOffTopic: Boolean,
        collectivelyUnanswered: String?,
        index: Int
    ): Pair<String, String> {
        val read = rankedPublicSuspects(session, bot, understanding.focusNames).firstOrNull()
        val memory = memoryFor(session, bot).let { currentMemory ->
            understanding.answeredQuestion?.let { currentMemory.copy(pendingHumanQuestion = it) }
                ?: currentMemory
        }
        val baseTarget = speechTarget(session, bot, read)
        val contextSeed = "reply:$index:${session.phaseIndex}:${socialChatSize(session)}:${humanMessage.length}"
        val reason = informalReason(read?.reason(), contextSeed)
        val mood = moodFor(session, bot, humanMessage)
        val baseIntent = reactionIntent(
            session,
            bot,
            humanMessage,
            understanding.focusNames,
            mood,
            index,
            memory
        )
        val intent = coordinatedIntent(
            session = session,
            base = baseIntent,
            role = conversationRole(index),
            hasStrongRead = canVoiceStrongAccusation(session, read),
            hasThread = memory.unansweredTarget != null || memory.pendingHumanQuestion != null
        ).let { toneAdjustedIntent(session, it) }
        val target = if (intent == BotSpeechIntent.FOLLOW_UP && memory.lastPressuredTarget != null) {
            memory.lastPressuredTarget
        } else {
            baseTarget
        }
        val unanswered = memory.unansweredTarget
            ?.takeUnless { unansweredTarget -> unansweredTarget == bot.name }
        val claimLine = understanding.roleClaim?.let { claim ->
            roleClaimReaction(session, bot, claim, understanding.claimResponder, index)
        }
        val claimStatementLine = if (
            claimLine == null || bot.name != understanding.claimResponder?.name
        ) {
            roleClaimStatementReaction(
                session,
                understanding.roleClaim,
                understanding.publicStatement,
                index
            )
        } else {
            null
        }
        val statementLine = understanding.publicStatement?.let { statement ->
            actionContradiction(session, GameEngine.humanPlayer(session).name, statement)
                ?.let { contradictionLine(GameEngine.humanPlayer(session).name, it) }
                ?: statementReaction(statement, index)
        }
        val jesterWarningLine = BotJesterAwareness.warningLine(
            session = session,
            speaker = bot,
            focusNames = understanding.focusNames,
            responseIndex = index
        )
        val directQuestionLine = if (
            bot.name == understanding.directAddressee &&
            understanding.questionKind in directQuestionKinds
        ) {
            directHumanQuestionReply(session, bot, humanMessage, understanding.questionKind!!)
        } else {
            null
        }
        val directSocialLine = if (
            bot.name == understanding.directAddressee && understanding.socialSignal != null
        ) {
            directSocialReply(session, bot, understanding.socialSignal)
        } else {
            null
        }
        val directAddressedLine = if (
            bot.name == understanding.directAddressee &&
            understanding.questionKind == null &&
            humanMessage.trim().endsWith("?")
        ) {
            defensiveLine(session, bot, mood)
        } else {
            null
        }
        val line = when {
            bot.role?.key == RoleCatalog.BUFON && understanding.focusNames.contains(bot.name) ->
                jesterEmbraceAccusationLine(session, bot, index)
            claimStatementLine != null -> claimStatementLine
            claimLine != null -> claimLine
            directQuestionLine != null -> directQuestionLine
            directSocialLine != null -> directSocialLine
            directAddressedLine != null -> directAddressedLine
            collectivelyUnanswered != null &&
                collectivelyUnanswered != bot.name &&
                index == 0 -> "$collectivelyUnanswered, sigo esperando que respondas lo de antes"
            unanswered != null && index == 0 && (
                intent == BotSpeechIntent.FOLLOW_UP ||
                    understanding.intent in setOf(
                        HumanMessageIntent.ACCUSE,
                        HumanMessageIntent.DOUBT,
                        HumanMessageIntent.OTHER
                    )
                ) -> "$unanswered, sigo esperando que respondas lo de antes"
            understanding.intent == HumanMessageIntent.ANSWER_PENDING ->
                pendingAnswerReply(session, bot, humanMessage, memory, index)
            understanding.intent == HumanMessageIntent.ACCUSE && understanding.focusNames.contains(bot.name) ->
                defensiveLine(session, bot, mood)
            jesterWarningLine != null -> jesterWarningLine
            statementLine != null -> statementLine
            understanding.questionKind != null ->
                humanQuestionReply(session, bot, understanding.questionKind, read, index)
            understanding.casualMessage -> casualHumanReply(session, bot, humanMessage, index)
            understanding.intent == HumanMessageIntent.OFF_TOPIC ->
                offTopicReply(session, bot, repeatedOffTopic, index)
            unanswered != null && (
                intent == BotSpeechIntent.FOLLOW_UP ||
                    understanding.intent in setOf(
                        HumanMessageIntent.ACCUSE,
                        HumanMessageIntent.DOUBT,
                        HumanMessageIntent.OTHER
                    )
                ) -> "$unanswered igual sigo esperando esa respuesta"
            understanding.intent == HumanMessageIntent.DOUBT -> humanDoubtReply(session, bot, read, index)
            understanding.claimsHiddenInfo && index == 0 ->
                "para para, no demos cartas por hechas. decime q hizo y listo"
            understanding.claimsHiddenInfo -> "$target me hace ruido por lo q vimos nomas, $reason"
            understanding.focusNames.contains(bot.name) -> defensiveLine(session, bot, mood)
            else -> lineForIntent(session, bot, intent, target, reason, contextSeed)
        }
        return bot.name to finishSpeech(
            line,
            session,
            bot,
            "reply:$index:${humanMessage.length}",
            allowRoleTerms = understanding.roleClaim != null ||
                understanding.questionKind == HumanQuestionKind.ASK_ROLE ||
                jesterWarningLine != null
        )
    }
}
