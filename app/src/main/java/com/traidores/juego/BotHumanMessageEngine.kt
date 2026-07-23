package com.traidores.juego

/** Owns the full reaction pipeline for one human public-chat message. */
internal object BotHumanMessageEngine {
    private val directQuestionKinds = setOf(
        HumanQuestionKind.ASK_ROLE,
        HumanQuestionKind.WHY_VOTE,
        HumanQuestionKind.WHY_ACCUSE,
        HumanQuestionKind.OPINION,
        HumanQuestionKind.BELIEF
    )
    private val multiReplyIntents = setOf(
        HumanMessageIntent.ROLE_CLAIM,
        HumanMessageIntent.ROLE_QUESTION,
        HumanMessageIntent.ACTION_HELP,
        HumanMessageIntent.VOTE_HELP,
        HumanMessageIntent.SUSPECT_HELP,
        HumanMessageIntent.ACCUSE,
        HumanMessageIntent.DEFEND
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

    fun reactionsTo(session: GameSession, humanMessage: String): List<Pair<String, String>> {
        val understanding = understand(session, humanMessage)
        val repeatedOffTopic = understanding.intent == HumanMessageIntent.OFF_TOPIC &&
            recentPublicMessages(session)
                .asReversed()
                .filter { it.speaker == GameEngine.humanPlayer(session).name }
                .take(2)
                .count { BotPerception.isOffTopicMessage(session, it.message) } >= 2
        val desiredReplyCount = when {
            understanding.directAddressee != null && understanding.questionKind in directQuestionKinds -> 1
            understanding.directAddressee != null && understanding.socialSignal != null -> 1
            understanding.intent == HumanMessageIntent.OFF_TOPIC -> if (repeatedOffTopic) 2 else 1
            understanding.intent in multiReplyIntents || understanding.focusNames.isNotEmpty() -> 3
            understanding.publicStatement != null ||
                understanding.claimsHiddenInfo ||
                understanding.intent == HumanMessageIntent.ANSWER_PENDING ||
                humanMessage.length > 45 -> 2
            else -> 1
        }
        val replyCount = limitedReplyCount(session, desiredReplyCount)
        val preferredResponder = understanding.directAddressee
            ?: understanding.claimResponder?.name
            ?: understanding.answeredQuestion?.speaker
        return messageBots(session, replyCount, preferredFirst = preferredResponder)
            .mapIndexed { index, bot ->
                responseFor(
                    session = session,
                    bot = bot,
                    humanMessage = humanMessage,
                    understanding = understanding,
                    repeatedOffTopic = repeatedOffTopic,
                    index = index
                )
            }
            .dropEchoesOfRecentChat(session)
            .dedupeBotMessages()
    }

    private fun understand(session: GameSession, humanMessage: String): Understanding {
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
        val intent = if (answeredQuestion != null && humanMessage.trim().length >= 4) {
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
        return Understanding(
            directAddressee = directAddressee,
            roleClaim = roleClaim,
            publicStatement = publicStatement,
            focusNames = focusNames,
            claimResponder = roleClaim?.let { botWithRole(session, it.roleKey) },
            claimsHiddenInfo = claimsHiddenInfo,
            casualMessage = casualMessage,
            socialSignal = socialSignal,
            questionKind = questionKind,
            answeredQuestion = answeredQuestion,
            intent = intent
        )
    }

    private fun responseFor(
        session: GameSession,
        bot: GamePlayer,
        humanMessage: String,
        understanding: Understanding,
        repeatedOffTopic: Boolean,
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
        val line = when {
            bot.role?.key == RoleCatalog.BUFON && understanding.focusNames.contains(bot.name) ->
                jesterEmbraceAccusationLine(session, bot, index)
            claimStatementLine != null -> claimStatementLine
            claimLine != null -> claimLine
            directQuestionLine != null -> directQuestionLine
            directSocialLine != null -> directSocialLine
            understanding.intent == HumanMessageIntent.ANSWER_PENDING ->
                pendingAnswerReply(session, bot, humanMessage, memory, index)
            understanding.intent == HumanMessageIntent.ACCUSE && understanding.focusNames.contains(bot.name) ->
                defensiveLine(session, bot, mood)
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
                understanding.questionKind == HumanQuestionKind.ASK_ROLE
        )
    }
}
