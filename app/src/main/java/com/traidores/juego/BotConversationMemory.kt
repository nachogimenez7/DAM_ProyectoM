package com.traidores.juego


internal fun humanMessageIntent(
    session: GameSession,
    message: String,
    roleClaim: RoleClaim?,
    publicStatement: PublicStatement?,
    claimsHiddenInfo: Boolean,
    casualMessage: Boolean,
    questionKind: HumanQuestionKind?
): HumanMessageIntent {
    if (LocalBotAi.isDebugVoteCommand(session, message)) return HumanMessageIntent.OTHER
    if (claimsHiddenInfo) return HumanMessageIntent.SECRET_LEAK
    if (roleClaim != null) return HumanMessageIntent.ROLE_CLAIM
    return when {
        questionKind == HumanQuestionKind.ROLE_HELP -> HumanMessageIntent.ROLE_QUESTION
        questionKind == HumanQuestionKind.ACTION_HELP -> HumanMessageIntent.ACTION_HELP
        questionKind == HumanQuestionKind.VOTE_HELP -> HumanMessageIntent.VOTE_HELP
        questionKind == HumanQuestionKind.SUSPECT_HELP -> HumanMessageIntent.SUSPECT_HELP
        pendingQuestionForHuman(session) != null && message.trim().length >= 4 ->
            HumanMessageIntent.ANSWER_PENDING
        publicStatement?.type == StatementType.REFUSED_ROLE -> HumanMessageIntent.REFUSE_ROLE
        publicStatement?.type == StatementType.ACCUSE ||
            publicStatement?.type == StatementType.VOTE -> HumanMessageIntent.ACCUSE
        publicStatement?.type == StatementType.TRUST -> HumanMessageIntent.DEFEND
        isDoubtMessage(message) -> HumanMessageIntent.DOUBT
        casualMessage -> HumanMessageIntent.CASUAL
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
    val text = normalizedForParsing(message)
    return when {
        text.contains("que soy") ||
            text.contains("quien soy") ||
            text.contains("q soy") ||
            text.contains("cual es mi rol") ||
            text.contains("que rol soy") ||
            text.contains("q rol soy") ||
            text.contains("mi rol") ->
            HumanQuestionKind.ROLE_HELP
        text.contains("a quien voto") ||
            text.contains("a quien votamos") ||
            text.contains("quien voto") ||
            text.contains("voto a quien") ->
            HumanQuestionKind.VOTE_HELP
        text.contains("que hago") ||
            text.contains("q hago") ||
            text.contains("que deberia hacer") ||
            text.contains("como juego") ->
            HumanQuestionKind.ACTION_HELP
        text.contains("quien sospecha") ||
            text.contains("de quien sospechan") ||
            text.contains("a quien miramos") ||
            text.contains("quien les parece") ->
            HumanQuestionKind.SUSPECT_HELP
        else -> null
    }
}

internal fun isCasualHumanMessage(message: String): Boolean {
    val text = normalizedForParsing(message)
    if (text.isBlank()) return false
    val words = text.split(" ").filter { it.isNotBlank() }
    if (words.size <= 2 && words.any { it in casualWords }) return true
    return text in casualPhrases
}

internal fun isWeakSuspicion(read: SuspectRead?): Boolean {
    return read == null || read.score < 6 || read.reason() == "esta hablando poco"
}

internal fun personalityFor(session: GameSession, bot: GamePlayer): BotPersonality {
    val personalities = BotPersonality.entries
    val bots = session.players.filterNot { it.isHuman }
    val tableShift = stableNoise("personality-table:${session.code}:${session.initialPlayerCount}") % personalities.size
    val seatIndex = bots.indexOfFirst { it.name == bot.name }.takeUnless { it < 0 } ?: 0
    val smallJitter = stableNoise("personality-jitter:${session.code}:${bot.name}") % 2
    return personalities[(seatIndex + tableShift + smallJitter) % personalities.size]
}

internal fun moodFor(session: GameSession, bot: GamePlayer, latestMessage: String): BotMood {
    val recent = recentPublicMessages(session).takeLast(8)
    val mentions = recent.count { mentionsName(it.message, bot.name) }
    val accusations = recent.count {
        mentionsName(it.message, bot.name) && hasAnySignal(it.message, accusationWords)
    }
    val latestTargetsBot = mentionsName(latestMessage, bot.name)
    return when {
        latestTargetsBot && accusations >= 2 -> BotMood.ANNOYED
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

    messages.forEachIndexed { index, message ->
        val speaker = message.speaker.takeIf { it in players } ?: return@forEachIndexed
        LocalBotAi.roleClaimFrom(message.message)?.let { roleClaims[speaker] = it }
        LocalBotAi.publicStatementFrom(session, message.message)?.let { statement ->
            latestStatements[speaker] = statement
            val target = statement.target
            when {
                target != null && statement.type in setOf(StatementType.ACCUSE, StatementType.VOTE) -> {
                    accusedTargets.getOrPut(speaker) { mutableSetOf() } += target
                    accusedBy.getOrPut(target) { mutableSetOf() } += speaker
                }
                target != null && statement.type == StatementType.TRUST -> {
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

    val pendingQuestionFrom = latestQuestionForTarget.mapValues { (target, question) ->
        val answered = messages.drop(question.first + 1).any { it.speaker == target }
        question.second.takeUnless { answered }
    }

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

internal fun declaredSuspicionTarget(session: GameSession, bot: GamePlayer): String? {
    val candidates = GameEngine.alivePlayers(session)
        .filter { it.name != bot.name }
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
            target = it.target
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
        ?: eventTarget(session, announcement, "expulsÃ³ a")
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
    return GameEngine.alivePlayers(session)
        .filter { mentionsName(message, it.name) }
        .map { it.name }
}

internal fun recentPublicMessages(session: GameSession): List<GameChatMessage> {
    return session.chatHistory.filterNot { it.isGod }.takeLast(16)
}

internal fun socialChatSize(session: GameSession): Int {
    return session.chatHistory.count { !it.isGod }
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
    return normalized(message).contains(normalized(name))
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
    return stripSpanishAccents(normalized(value))
        .replace(Regex("[^a-z0-9\\u00f1 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
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
    return session.players.firstOrNull {
        !it.isHuman &&
            GameEngine.canSpeak(session, it) &&
            it.role?.key == roleKey &&
            !GameRules.isTraitorRole(it.role)
    }
}

internal fun publicClaimants(session: GameSession, roleKey: String): List<String> {
    return recentPublicMessages(session)
        .mapNotNull { message ->
            message.speaker.takeIf { LocalBotAi.roleClaimFrom(message.message)?.roleKey == roleKey }
        }
        .distinct()
}

internal fun latestClaimBySpeaker(session: GameSession, speaker: String): RoleClaim? {
    return recentPublicMessages(session)
        .asReversed()
        .firstOrNull { it.speaker == speaker }
        ?.message
        ?.let { LocalBotAi.roleClaimFrom(it) }
}

internal fun latestStatementBySpeaker(session: GameSession, speaker: String): PublicStatement? {
    return recentPublicMessages(session)
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
