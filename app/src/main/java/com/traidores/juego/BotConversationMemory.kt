package com.traidores.juego

private const val BOT_PARSE_CACHE_SIZE = 256
private val parsingNonWordPattern = Regex("[^a-z0-9\\u00f1 ]")
private val parsingWhitespacePattern = Regex("\\s+")

private object ParsingNormalizationCache {
    private val values = object : LinkedHashMap<String, String>(BOT_PARSE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > BOT_PARSE_CACHE_SIZE
        }
    }

    @Synchronized
    fun normalize(value: String): String {
        values[value]?.let { return it }
        val normalized = stripSpanishAccents(value.lowercase())
            .replace(parsingNonWordPattern, " ")
            .replace(parsingWhitespacePattern, " ")
            .trim()
        values[value] = normalized
        return normalized
    }
}

private object MentionPatternCache {
    private val patterns = object : LinkedHashMap<String, Regex>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean {
            return size > 64
        }
    }

    @Synchronized
    fun patternFor(normalizedName: String): Regex {
        return patterns.getOrPut(normalizedName) {
            Regex("(^|[^a-z0-9])${Regex.escape(normalizedName)}($|[^a-z0-9])")
        }
    }
}

internal fun humanMessageIntent(
    session: GameSession,
    message: String,
    roleClaim: RoleClaim?,
    publicStatement: PublicStatement?,
    claimsHiddenInfo: Boolean,
    casualMessage: Boolean,
    questionKind: HumanQuestionKind?,
    socialSignal: HumanSocialSignal? = BotPerception.socialSignal(message)
): HumanMessageIntent {
    if (LocalBotAi.isDebugVoteCommand(session, message)) return HumanMessageIntent.OTHER
    if (claimsHiddenInfo) return HumanMessageIntent.SECRET_LEAK
    if (roleClaim != null) return HumanMessageIntent.ROLE_CLAIM
    return when {
        publicStatement?.type in actionStatementTypes -> HumanMessageIntent.ACTION_CLAIM
        questionKind == HumanQuestionKind.ROLE_HELP -> HumanMessageIntent.ROLE_QUESTION
        questionKind == HumanQuestionKind.ASK_ROLE -> HumanMessageIntent.ROLE_QUESTION
        questionKind == HumanQuestionKind.ACTION_HELP -> HumanMessageIntent.ACTION_HELP
        questionKind == HumanQuestionKind.VOTE_HELP -> HumanMessageIntent.VOTE_HELP
        questionKind == HumanQuestionKind.SUSPECT_HELP -> HumanMessageIntent.SUSPECT_HELP
        pendingQuestionForHuman(session) != null && isRelevantPendingAnswer(session, message) ->
            HumanMessageIntent.ANSWER_PENDING
        publicStatement?.type == StatementType.REFUSED_ROLE -> HumanMessageIntent.REFUSE_ROLE
        publicStatement?.type == StatementType.ACCUSE ||
            publicStatement?.type == StatementType.VOTE -> HumanMessageIntent.ACCUSE
        publicStatement?.type == StatementType.TRUST -> HumanMessageIntent.DEFEND
        socialSignal == HumanSocialSignal.PRAISE -> HumanMessageIntent.PRAISE
        socialSignal == HumanSocialSignal.INSULT -> HumanMessageIntent.INSULT
        isDoubtMessage(message) -> HumanMessageIntent.DOUBT
        casualMessage -> HumanMessageIntent.CASUAL
        BotPerception.isOffTopicMessage(session, message) -> HumanMessageIntent.OFF_TOPIC
        else -> HumanMessageIntent.OTHER
    }
}

internal fun isDoubtMessage(message: String): Boolean {
    val text = normalizedForParsing(message)
    return text.contains("no se") ||
        text.contains("nose") ||
        text.contains("no estoy seguro") ||
        text.contains("capaz") ||
        text.contains("puede ser") ||
        text.contains("tengo duda")
}

internal fun isDirectClarification(message: String): Boolean {
    val text = normalizedForParsing(message)
    return text.contains("a vos te dije") ||
        text.contains("te dije a vos") ||
        text.contains("era para vos") ||
        text.contains("te lo dije a vos") ||
        text.contains("a vos te hablaba")
}

internal fun previousHumanStatement(session: GameSession, currentMessage: String): PublicStatement? {
    val human = GameEngine.humanPlayer(session)
    var skippedCurrent = false
    return recentPublicMessages(session)
        .asReversed()
        .asSequence()
        .filter { it.speaker == human.name }
        .mapNotNull { message ->
            if (!skippedCurrent && message.message == currentMessage) {
                skippedCurrent = true
                null
            } else {
                LocalBotAi.publicStatementFrom(session, message.message)
            }
        }
        .firstOrNull { statement ->
            statement.type in setOf(StatementType.TRUST, StatementType.ACCUSE, StatementType.INVESTIGATED)
        }
}

internal fun humanQuestionKind(message: String): HumanQuestionKind? {
    return BotPerception.humanQuestionKind(message)
}

internal fun isRelevantPendingAnswer(session: GameSession, message: String): Boolean {
    if (message.trim().length < 2) return false
    return LocalBotAi.roleClaimFrom(message) != null ||
        LocalBotAi.publicStatementFrom(session, message) != null ||
        mentionedPlayerNames(session, message).isNotEmpty() ||
        isDoubtMessage(message) ||
        isDirectClarification(message) ||
        BotPerception.isGameRelatedMessage(session, message)
}

internal fun isCasualHumanMessage(message: String): Boolean {
    return BotPerception.isCasualHumanMessage(message)
}

internal fun isWeakSuspicion(read: SuspectRead?): Boolean {
    return read == null || read.score < 6 || read.reason() == "esta hablando poco"
}

/**
 * A high score can come from repetition alone. Grounded suspicion requires both enough weight and
 * a concrete event that a bot can explain in chat, so one unsupported accusation does not snowball.
 */
internal fun hasGroundedSuspicion(read: SuspectRead?): Boolean {
    return read != null && read.score >= 8 && read.reasons.any(::isGroundedSuspicionReason)
}

internal fun hasGroundedSuspicion(read: RelationshipRead?): Boolean {
    return read != null && read.score >= 8 && isGroundedSuspicionReason(read.reason)
}

internal fun canVoiceStrongAccusation(session: GameSession, read: SuspectRead?): Boolean {
    return !isOpeningInvestigationStage(session) && hasGroundedSuspicion(read)
}

internal fun canVoiceStrongAccusation(session: GameSession, read: RelationshipRead?): Boolean {
    return !isOpeningInvestigationStage(session) && hasGroundedSuspicion(read)
}

internal fun investigationSpeakerThreshold(session: GameSession): Int {
    val aliveCount = GameEngine.alivePlayers(session).size
    val desired = when {
        aliveCount <= 4 -> 2
        aliveCount <= 6 -> 3
        else -> 4
    }
    val availableBotSpeakers = session.players.count { player ->
        !player.isHuman && GameEngine.canParticipateInChat(session, player)
    }
    return desired.coerceAtMost(availableBotSpeakers.coerceAtLeast(1))
}

internal fun isOpeningInvestigationStage(session: GameSession): Boolean {
    if (session.phase !in setOf(GamePhase.DIA_DEBATE, GamePhase.CONTRAPUNTO)) return false
    val discussionStart = session.publicDiscussionStartIndex
        .coerceIn(0, session.chatHistory.size)
    val speakersThisPhase = session.chatHistory
        .drop(discussionStart)
        .asSequence()
        .filter { message ->
            message.channel == ChatChannel.PUBLICO &&
                !message.isGod &&
                isBotSpeaker(session, message.speaker)
        }
        .map { it.speaker }
        .distinct()
        .count()
    return speakersThisPhase < investigationSpeakerThreshold(session)
}

internal fun isGroundedSuspicionReason(reason: String): Boolean {
    return reason in setOf(
        "tengo una pista privada",
        "se contradijo de rol",
        "se contradijo con la accion",
        "cambio su accion",
        "dos dijeron el mismo rol",
        "dejo una pregunta colgada",
        "debe una respuesta",
        "esquivo el rol",
        "tiro dato y falta detalle",
        "dio info a medias",
        "me voto"
    )
}

internal fun personalityFor(session: GameSession, bot: GamePlayer): BotPersonality {
    return BotIdentity.personalityFor(session, bot)
}

internal fun competitivenessFor(session: GameSession, bot: GamePlayer): BotCompetitiveness {
    return BotIdentity.competitivenessFor(session, bot)
}

internal fun moodFor(session: GameSession, bot: GamePlayer, latestMessage: String): BotMood {
    val recent = recentPublicMessages(session).takeLast(8)
    val mentions = recent.count { mentionsName(it.message, bot.name) }
    val accusations = recent.count {
        mentionsName(it.message, bot.name) && hasAnySignal(it.message, accusationWords)
    }
    val latestTargetsBot = mentionsName(latestMessage, bot.name)
    val persistentPressure = session.tableMemory.emotionalPressure[bot.name] ?: 0
    return when {
        latestTargetsBot && (accusations >= 2 || persistentPressure >= 5) -> BotMood.ANNOYED
        latestTargetsBot -> BotMood.DEFENSIVE
        latestMessage.contains("jaja", ignoreCase = true) ||
            latestMessage.contains("jsjs", ignoreCase = true) -> BotMood.AMUSED
        mentions >= 3 -> BotMood.SUSPICIOUS
        else -> BotMood.CALM
    }
}

internal fun memoryFor(session: GameSession, bot: GamePlayer): BotMemory {
    val recent = recentPublicMessages(session)
    val table = conversationMemory(session)
    val candidates = GameEngine.alivePlayers(session).filter { it.name != bot.name }
    val lastPressured = recent
        .asReversed()
        .filter { it.speaker == bot.name }
        .mapNotNull { message ->
            candidates.firstOrNull { candidate ->
                mentionsName(message.message, candidate.name) &&
                    (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
            }?.name
        }
        .firstOrNull()
    val recentLines = recent
        .takeLast(10)
        .map { normalizedForParsing(it.message) }
        .filter { it.isNotBlank() }
        .toSet()
    return BotMemory(
        unansweredTarget = unansweredQuestionFor(session, bot),
        lastPressuredTarget = lastPressured,
        pendingHumanQuestion = pendingQuestionForHuman(session),
        table = table,
        recentLines = recentLines
    )
}

internal fun conversationMemory(session: GameSession): Map<String, PlayerConversationMemory> {
    val players = GameEngine.alivePlayers(session).map { it.name }.toSet()
    val roleClaims = mutableMapOf<String, RoleClaim>()
    val latestStatements = mutableMapOf<String, PublicStatement>()
    val accusedTargets = mutableMapOf<String, MutableSet<String>>()
    val defendedTargets = mutableMapOf<String, MutableSet<String>>()
    val accusedBy = mutableMapOf<String, MutableSet<String>>()
    val defendedBy = mutableMapOf<String, MutableSet<String>>()
    val latestQuestionForTarget = mutableMapOf<String, Pair<Int, String>>()
    val messages = recentPublicMessages(session)

    session.claimLedger.forEach { (speaker, records) ->
        if (speaker !in players) return@forEach
        records.forEach { record ->
            claimFromRecord(record)?.let { roleClaims[speaker] = it }
            statementFromRecord(record)?.let { statement ->
                latestStatements[speaker] = statement
                val target = statement.target
                when {
                    target != null && target in players &&
                        statement.type in setOf(StatementType.ACCUSE, StatementType.VOTE) -> {
                        accusedTargets.getOrPut(speaker) { mutableSetOf() } += target
                        accusedBy.getOrPut(target) { mutableSetOf() } += speaker
                    }
                    target != null && target in players && statement.type == StatementType.TRUST -> {
                        defendedTargets.getOrPut(speaker) { mutableSetOf() } += target
                        defendedBy.getOrPut(target) { mutableSetOf() } += speaker
                    }
                }
            }
        }
    }

    messages.forEachIndexed { index, message ->
        val speaker = message.speaker.takeIf { it in players } ?: return@forEachIndexed
        LocalBotAi.roleClaimFrom(message.message)?.let { roleClaims[speaker] = it }
        LocalBotAi.publicStatementFrom(session, message.message)?.let { statement ->
            latestStatements[speaker] = statement
            val target = statement.target
            when {
                target != null && target in players &&
                    statement.type in setOf(StatementType.ACCUSE, StatementType.VOTE) -> {
                    accusedTargets.getOrPut(speaker) { mutableSetOf() } += target
                    accusedBy.getOrPut(target) { mutableSetOf() } += speaker
                }
                target != null && target in players && statement.type == StatementType.TRUST -> {
                    defendedTargets.getOrPut(speaker) { mutableSetOf() } += target
                    defendedBy.getOrPut(target) { mutableSetOf() } += speaker
                }
            }
        }
        if (message.message.contains("?")) {
            mentionedPlayerNames(session, message.message)
                .filter { it != speaker }
                .forEach { target ->
                    latestQuestionForTarget[target] = index to speaker
                }
        }
    }

    val pendingFromTable = session.tableMemory.pendingQuestions
        .filterKeys { it in players }
        .filterValues { it.source in players }
        .mapValues { it.value.source }
    val pendingFromRecent = latestQuestionForTarget.mapValues { (target, question) ->
        val answered = messages.drop(question.first + 1).any { it.speaker == target }
        question.second.takeUnless { answered }
    }
    val pendingQuestionFrom = pendingFromRecent + pendingFromTable

    return players.associateWith { name ->
        PlayerConversationMemory(
            roleClaim = roleClaims[name],
            latestStatement = latestStatements[name],
            accusedTargets = accusedTargets[name] ?: emptySet(),
            defendedTargets = defendedTargets[name] ?: emptySet(),
            accusedBy = accusedBy[name] ?: emptySet(),
            defendedBy = defendedBy[name] ?: emptySet(),
            pendingQuestionFrom = pendingQuestionFrom[name]
        )
    }
}

internal fun pendingQuestionForHuman(session: GameSession): PendingHumanQuestion? {
    val human = GameEngine.humanPlayer(session)
    session.tableMemory.pendingQuestions[human.name]
        ?.takeIf { it.source != human.name && GameEngine.playerByName(session, it.source)?.alive == true }
        ?.let { return PendingHumanQuestion(it.source, it.message) }
    val messages = recentPublicMessages(session)
    val questionIndex = messages.indexOfLast { message ->
        message.speaker != human.name &&
            message.message.contains("?") &&
            mentionsName(message.message, human.name)
    }
    if (questionIndex < 0) return null
    val answered = messages.drop(questionIndex + 1).any { it.speaker == human.name }
    if (answered) return null
    val question = messages[questionIndex]
    return PendingHumanQuestion(question.speaker, question.message)
}

internal fun answeredQuestionForHuman(session: GameSession, currentMessage: String): PendingHumanQuestion? {
    if (!isRelevantPendingAnswer(session, currentMessage)) return null
    val human = GameEngine.humanPlayer(session)
    val messages = recentPublicMessages(session)
    val answerIndex = messages.indexOfLast { message ->
        message.speaker == human.name && message.message == currentMessage
    }
    if (answerIndex <= 0) return null
    val question = messages
        .take(answerIndex)
        .asReversed()
        .firstOrNull { message ->
            message.speaker != human.name &&
                message.message.contains("?") &&
                mentionsName(message.message, human.name)
        }
        ?: return null
    return PendingHumanQuestion(question.speaker, question.message)
}

internal fun unansweredQuestionFor(session: GameSession, bot: GamePlayer): String? {
    session.tableMemory.pendingQuestions.values
        .firstOrNull {
            it.source == bot.name &&
                it.target != bot.name &&
                GameEngine.playerByName(session, it.target)?.alive == true
        }
        ?.let { return it.target }
    val messages = recentPublicMessages(session)
    val botQuestionIndex = messages.indexOfLast {
        it.speaker == bot.name && it.message.contains("?")
    }
    if (botQuestionIndex < 0) return null
    val question = messages[botQuestionIndex]
    val target = mentionedPlayerNames(session, question.message)
        .firstOrNull { it != bot.name }
        ?: return null
    val answered = messages.drop(botQuestionIndex + 1).any { it.speaker == target }
    return if (answered) null else "$target"
}

/**
 * Detecta un hilo que toda la mesa dejó pendiente, aunque el bot elegido para responder
 * no haya sido quien hizo la última pregunta. Exige al menos dos interlocutores distintos
 * para no perseguir a alguien por una sola pregunta casual.
 */
internal fun collectivelyUnansweredTarget(session: GameSession): String? {
    val messages = recentPublicMessages(session)
    return GameEngine.alivePlayers(session)
        .asSequence()
        .map { target ->
            val lastAnswerIndex = messages.indexOfLast { it.speaker == target.name }
            val questioners = messages.withIndex()
                .filter { (index, message) ->
                    index > lastAnswerIndex &&
                        message.speaker != target.name &&
                        message.message.contains("?") &&
                        mentionsName(message.message, target.name)
                }
                .map { it.value.speaker }
                .distinct()
            target.name to questioners.size
        }
        .filter { (_, questionerCount) -> questionerCount >= 2 }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .firstOrNull()
        ?.first
}

internal fun declaredSuspicionTarget(session: GameSession, bot: GamePlayer): String? {
    val candidates = GameEngine.alivePlayers(session)
        .filter { it.name != bot.name }
    val candidateNames = candidates.map { it.name }.toSet()
    session.claimLedger[bot.name].orEmpty()
        .asReversed()
        .firstOrNull {
            it.target in candidateNames &&
                it.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE)
        }
        ?.target
        ?.let { return it }
    return recentPublicMessages(session)
        .asReversed()
        .asSequence()
        .filter { it.speaker == bot.name }
        .mapNotNull { message ->
            candidates.firstOrNull { candidate ->
                mentionsName(message.message, candidate.name) &&
                    (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
            }?.name
        }
        .firstOrNull()
}

internal fun currentRoundDeclaredStance(session: GameSession, bot: GamePlayer): ClaimRecord? {
    val aliveNames = GameEngine.alivePlayers(session)
        .asSequence()
        .map { it.name }
        .filter { it != bot.name }
        .toSet()
    return session.claimLedger[bot.name].orEmpty()
        .asReversed()
        .firstOrNull { record ->
            record.round == session.round &&
                record.target?.let { it in aliveNames } == true &&
                record.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE)
        }
}

internal fun socialRead(session: GameSession, bot: GamePlayer): SocialRead {
    val recent = recentPublicMessages(session)
    val candidates = GameEngine.alivePlayers(session).filter { it.name != bot.name }
    val pressured = recent
        .asReversed()
        .filter { it.speaker == bot.name }
        .mapNotNull { message ->
            candidates.firstOrNull { candidate ->
                mentionsName(message.message, candidate.name) &&
                    (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
            }?.name
        }
        .firstOrNull()
    val defended = recent
        .asReversed()
        .filter { it.speaker == bot.name }
        .mapNotNull { message ->
            candidates.firstOrNull { candidate ->
                mentionsName(message.message, candidate.name) &&
                    hasAnySignal(message.message, defenseWords)
            }?.name
        }
        .firstOrNull()
    val ignoredBy = recent
        .asReversed()
        .filter { it.speaker == bot.name && it.message.contains("?") }
        .mapNotNull { question ->
            val target = mentionedPlayerNames(session, question.message)
                .firstOrNull { it != bot.name }
            target?.takeUnless { player ->
                recent.drop(recent.indexOf(question) + 1).any { it.speaker == player } ||
                    hasUsefulPublicRead(session, player)
            }
        }
        .firstOrNull()
    val expelled = latestExpelledTarget(session)
    val failedPush = expelled?.takeIf { target ->
        recent.any {
            it.speaker == bot.name &&
                mentionsName(it.message, target) &&
                hasAnySignal(it.message, accusationWords)
        }
    }
    val heated = recent.count {
        mentionsName(it.message, bot.name) &&
            hasAnySignal(it.message, accusationWords)
    } >= 2
    return SocialRead(
        defended = defended,
        pressured = pressured,
        ignoredBy = ignoredBy,
        failedPush = failedPush,
        heated = heated
    )
}

internal fun latestOwnAction(session: GameSession, bot: GamePlayer): GameAction? {
    return session.actionHistory
        .asReversed()
        .firstOrNull { it.actor == bot.name && it.round == session.round }
}

internal fun hasClaimedRole(session: GameSession, playerName: String): Boolean {
    return session.claimLedger[playerName].orEmpty().any { it.roleKey != null }
}

internal fun publicContradiction(session: GameSession, playerName: String): ClaimContradiction? {
    return roleContradiction(session, playerName)
        ?: actionContradiction(session, playerName)
        ?: stanceContradiction(session, playerName)
}

internal fun roleContradiction(session: GameSession, playerName: String): ClaimContradiction? {
    val records = session.claimLedger[playerName].orEmpty()
        .filter { it.roleKey != null }
    val first = records.firstOrNull() ?: return null
    val latestDifferent = records.lastOrNull { it.roleKey != first.roleKey } ?: return null
    return ClaimContradiction(first, latestDifferent)
}

internal fun actionContradiction(
    session: GameSession,
    playerName: String,
    latestStatement: PublicStatement? = null
): ClaimContradiction? {
    val records = session.claimLedger[playerName].orEmpty()
        .filter {
            it.statementType != null &&
                it.statementType in actionStatementTypes &&
                it.target != null &&
                it.round == session.round
        }
    val latestSynthetic = latestStatement?.takeIf {
        it.type in actionStatementTypes && it.target != null
    }?.let {
        ClaimRecord(
            round = session.round,
            phase = session.phase,
            statementType = it.type,
            target = it.target,
            reason = it.reason
        )
    }
    val all = if (latestSynthetic != null && records.none {
            it.statementType == latestSynthetic.statementType &&
                it.target == latestSynthetic.target &&
                it.round == latestSynthetic.round
        }
    ) {
        records + latestSynthetic
    } else {
        records
    }
    actionStatementTypes.forEach { type ->
        val sameType = all.filter { it.statementType == type }
        val first = sameType.firstOrNull() ?: return@forEach
        val latestDifferent = sameType.lastOrNull { it.target != first.target }
        if (latestDifferent != null) return ClaimContradiction(first, latestDifferent)
    }
    return null
}

internal fun stanceContradiction(session: GameSession, playerName: String): ClaimContradiction? {
    val records = session.claimLedger[playerName].orEmpty()
        .filter {
            it.statementType in setOf(StatementType.TRUST, StatementType.ACCUSE) &&
                it.target != null
        }
    records
        .groupBy { it.target }
        .values
        .forEach { targetRecords ->
            val first = targetRecords.firstOrNull() ?: return@forEach
            val latestDifferent = targetRecords.lastOrNull { it.statementType != first.statementType }
            if (latestDifferent != null) return ClaimContradiction(first, latestDifferent)
        }
    return null
}

internal fun latestExpelledTarget(session: GameSession): String? {
    val announcement = session.publicHistory
        .asReversed()
        .firstOrNull {
            val normalized = GameplayTextMarkers.normalize(it)
            normalized.contains("fue expulsado") ||
                normalized.contains("expulsar a") ||
                normalized.contains("expulso a")
        }
        ?: return null
    return eventTarget(session, announcement, "fue expulsado")
        ?: eventTarget(session, announcement, "expulsar a")
        ?: eventTarget(session, announcement, "expulso a")
        ?: eventTarget(session, announcement, "expulsó a")
}

internal fun eventTarget(
    session: GameSession,
    announcement: String,
    marker: String
): String? {
    if (!GameplayTextMarkers.contains(announcement, marker)) return null
    return session.players
        .sortedByDescending { it.name.length }
        .firstOrNull { mentionsName(announcement, it.name) }
        ?.let { safeName(it, session) }
}

internal fun messageBots(
    session: GameSession,
    limit: Int,
    preferredFirst: String? = null
): List<GamePlayer> {
    if (limit <= 0) return emptyList()
    val recentSpeakers = recentBotSpeakers(session, amount = 2)
    return session.players
        .filter { !it.isHuman && GameEngine.canParticipateInChat(session, it) }
        .sortedWith(
            compareBy<GamePlayer> { if (it.name == preferredFirst) 0 else 1 }
                .thenBy { if (it.name in recentSpeakers && it.name != preferredFirst) 1 else 0 }
                .thenBy { stableNoise("${session.code}:${session.round}:${socialChatSize(session)}:${it.name}:talk") }
                .thenBy { it.name }
        )
        .take(limit)
}

internal fun limitedReplyCount(session: GameSession, desired: Int): Int {
    val streak = recentBotStreak(session)
    return if (session.botDifficulty == BotDifficulty.HARD) {
        when {
            streak >= 4 -> 0
            streak >= 2 -> desired.coerceAtMost(2)
            else -> desired
        }
    } else {
        when {
            streak >= 3 -> 0
            streak >= 2 -> desired.coerceAtMost(1)
            else -> desired.coerceAtMost(3)
        }
    }
}

internal fun recentBotStreak(session: GameSession): Int {
    return recentPublicMessages(session)
        .asReversed()
        .takeWhile { isBotSpeaker(session, it.speaker) }
        .count()
}

internal fun recentBotSpeakers(session: GameSession, amount: Int): Set<String> {
    return recentPublicMessages(session)
        .asReversed()
        .filter { isBotSpeaker(session, it.speaker) }
        .take(amount)
        .map { it.speaker }
        .toSet()
}

internal fun isBotSpeaker(session: GameSession, speaker: String): Boolean {
    return session.players.any { !it.isHuman && it.name == speaker }
}

internal fun mentionedPlayerNames(session: GameSession, message: String): List<String> {
    val alive = GameEngine.alivePlayers(session)
    val words = normalizedForParsing(message).split(" ").filter(String::isNotBlank).toSet()
    val normalizedAlive = alive.map { player -> player to normalizedForParsing(player.name) }
    val usefulPrefixes = words.filter { it.length >= 4 }
    val prefixMatches = usefulPrefixes.associateWith { prefix ->
        normalizedAlive.count { (_, normalizedName) -> normalizedName.startsWith(prefix) }
    }
    return normalizedAlive
        .filter { (player, normalizedName) ->
            mentionsName(message, player.name) || usefulPrefixes.any { prefix ->
                normalizedName.startsWith(prefix) && prefixMatches[prefix] == 1
            }
        }
        .map { (player, _) -> player.name }
}

internal fun recentPublicMessages(session: GameSession): List<GameChatMessage> {
    return session.chatHistory
        .filter { it.channel == ChatChannel.PUBLICO && !it.isGod }
        .takeLast(16)
}

internal fun recentTraitorMessages(session: GameSession): List<GameChatMessage> {
    return session.chatHistory
        .filter { it.channel == ChatChannel.TRAIDORES && !it.isGod }
        .takeLast(16)
}

internal fun socialChatSize(session: GameSession): Int {
    return session.chatHistory.count { it.channel == ChatChannel.PUBLICO && !it.isGod }
}

internal fun hasAnySignal(message: String, signals: List<String>): Boolean {
    val text = normalized(message)
    return signals.any { text.contains(it) }
}

internal fun hasAccusatoryTargetSignal(message: String): Boolean {
    val text = normalizedForParsing(message)
    return hasAnySignal(message, accusationWords) ||
        listOf(
            "voto a",
            "voy con",
            "punta con",
            "miro a",
            "mirar a",
            "mirar fuerte a",
            "nombro a",
            "sospecho de",
            "tengo a",
            "marca a",
            "marco a",
            "deberia contestar",
            "deberia responder",
            "respondele a",
            "no respondio",
            "no contesto",
            "me hace ruido"
        ).any { text.contains(it) }
}

internal fun containsSecretTerm(message: String, session: GameSession): Boolean {
    if (LocalBotAi.roleClaimFrom(message) != null) return false
    val text = normalized(message)
    return forbiddenTerms(session).any { term -> term.length > 2 && text.contains(normalized(term)) }
}

internal fun mentionsName(message: String, name: String): Boolean {
    val normalizedMessage = normalizedForParsing(message)
    val normalizedName = normalizedForParsing(name)
    if (normalizedName.isBlank()) return false
    return MentionPatternCache.patternFor(normalizedName).containsMatchIn(normalizedMessage)
}

internal fun safeName(player: GamePlayer, session: GameSession): String {
    return sanitizeBotSpeech(player.name, session).ifBlank { "alguien" }
}

internal fun forbiddenTerms(session: GameSession): Set<String> {
    val roleTerms = session.players.flatMap { player ->
        listOfNotNull(player.role?.key, player.role?.name, player.role?.team)
    }
    return (secretWords + roleTerms).map { it.trim() }.filter { it.isNotBlank() }.toSet()
}

internal fun normalized(value: String): String {
    return value.lowercase()
}

internal fun normalizedForParsing(value: String): String {
    return ParsingNormalizationCache.normalize(value)
}

internal fun normalizedVoteCommand(value: String): String {
    return normalizedForParsing(value)
}

internal fun stripSpanishAccents(value: String): String {
    return value
        .replace('\u00e1', 'a')
        .replace('\u00e9', 'e')
        .replace('\u00ed', 'i')
        .replace('\u00f3', 'o')
        .replace('\u00fa', 'u')
        .replace('\u00fc', 'u')
}

internal fun botWithRole(session: GameSession, roleKey: String): GamePlayer? {
    if (!isExclusivePublicClaimRole(roleKey)) return null
    return session.players.firstOrNull {
        !it.isHuman &&
            GameEngine.canSpeak(session, it) &&
            it.role?.key == roleKey &&
            !GameRules.isTraitorRole(it.role)
    }
}

internal fun publicClaimants(session: GameSession, roleKey: String): List<String> {
    if (!isExclusivePublicClaimRole(roleKey)) return emptyList()
    val alive = GameEngine.alivePlayers(session).map { it.name }.toSet()
    val fromLedger = session.claimLedger
        .filterKeys { it in alive }
        .mapNotNull { (speaker, records) ->
            speaker.takeIf { records.any { record -> record.roleKey == roleKey } }
        }
    val fromRecent = recentPublicMessages(session)
        .mapNotNull { message ->
            message.speaker.takeIf {
                it in alive &&
                    LocalBotAi.roleClaimFrom(message.message)?.roleKey == roleKey
            }
        }
    return (fromLedger + fromRecent).distinct()
}

internal fun latestClaimBySpeaker(session: GameSession, speaker: String): RoleClaim? {
    return session.claimLedger[speaker].orEmpty()
        .asReversed()
        .mapNotNull { claimFromRecord(it) }
        .firstOrNull()
        ?: recentPublicMessages(session)
            .asReversed()
            .firstOrNull { it.speaker == speaker }
            ?.message
            ?.let { LocalBotAi.roleClaimFrom(it) }
}

internal fun latestStatementBySpeaker(session: GameSession, speaker: String): PublicStatement? {
    return session.claimLedger[speaker].orEmpty()
        .asReversed()
        .mapNotNull { statementFromRecord(it) }
        .firstOrNull()
        ?: recentPublicMessages(session)
            .asReversed()
            .firstOrNull { it.speaker == speaker }
            ?.message
            ?.let { LocalBotAi.publicStatementFrom(session, it) }
}

internal fun hasUsefulPublicRead(session: GameSession, speaker: String): Boolean {
    val claim = latestClaimBySpeaker(session, speaker) ?: return false
    if (claim.roleKey != RoleCatalog.POLICIA) return false
    val statement = latestStatementBySpeaker(session, speaker) ?: return false
    return statement.target != null &&
        statement.type in setOf(
            StatementType.TRUST,
            StatementType.ACCUSE,
            StatementType.INVESTIGATED
        )
}

internal fun claimFromRecord(record: ClaimRecord): RoleClaim? {
    val roleKey = record.roleKey ?: return null
    return RoleClaim(roleKey, roleAliases[roleKey]?.firstOrNull() ?: roleKey)
}

internal fun statementFromRecord(record: ClaimRecord): PublicStatement? {
    val type = record.statementType ?: return null
    return PublicStatement(type = type, target = record.target, reason = record.reason)
}

internal fun isExclusivePublicClaimRole(roleKey: String): Boolean {
    return roleKey != RoleCatalog.ALDEANO
}
