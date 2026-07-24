package com.traidores.juego

internal data class BotConversationBeat(
    val speaker: String,
    val message: String,
    val promptsSilentHuman: Boolean = false,
    val followUps: List<String> = emptyList()
) {
    val messageCount: Int
        get() = 1 + followUps.size
}

internal object BotConversationDirector {
    private val chatPhases = setOf(
        GamePhase.DIA_DEBATE,
        GamePhase.CONTRAPUNTO,
        GamePhase.VOTACION,
        GamePhase.DESEMPATE_VOTACION
    )

    fun canRun(session: GameSession): Boolean {
        return session.winner.isBlank() && session.phase in chatPhases
    }

    fun canRunTraitorNight(session: GameSession): Boolean {
        return session.winner.isBlank() &&
            GameplayTableUi.isNightPhase(session.phase) &&
            session.traitorPlan?.round == session.round &&
            eligibleTraitorSpeakers(session).isNotEmpty()
    }

    fun idleBudget(session: GameSession): Int {
        val speakers = eligibleSpeakers(session).size
        if (speakers == 0) return 0
        return when (session.phase) {
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> speakers.coerceAtMost(2)
            GamePhase.CONTRAPUNTO -> speakers.coerceAtMost(3)
            GamePhase.DIA_DEBATE -> speakers.coerceAtMost(3)
            else -> 0
        }
    }

    fun pauseAfterBotStreak(session: GameSession): Int {
        return when (session.phase) {
            GamePhase.DIA_DEBATE -> 2
            else -> 1
        }
    }

    fun traitorNightBudget(session: GameSession): Int {
        val speakers = eligibleTraitorSpeakers(session).size
        if (speakers == 0) return 0
        val coverBonus = when (session.traitorPlan?.cover?.kind) {
            CoverKind.COUNTER_CLAIM,
            CoverKind.FAKE_CLAIM,
            CoverKind.BUS_ALLY -> 1
            else -> 0
        }
        return (speakers * 2 + coverBonus).coerceIn(2, 7)
    }

    fun nextIdleBeat(
        session: GameSession,
        idleLinesUsed: Int,
        lastSpeaker: String?,
        humanSpokeThisPhase: Boolean,
        promptedSilentHuman: Boolean
    ): BotConversationBeat? {
        if (!canRun(session) || idleLinesUsed >= idleBudget(session)) return null
        oracleGuestOpeningBeat(session, idleLinesUsed)?.let { return it }
        silentHumanPrompt(
            session = session,
            idleLinesUsed = idleLinesUsed,
            lastSpeaker = lastSpeaker,
            humanSpokeThisPhase = humanSpokeThisPhase,
            promptedSilentHuman = promptedSilentHuman
        )?.let {
            return it
        }
        val remainingMessages = (idleBudget(session) - idleLinesUsed).coerceAtLeast(1)
        return chooseSpeakers(session, lastSpeaker)
            .asSequence()
            .mapNotNull { speaker -> lineForSpeaker(session, speaker, remainingMessages) }
            .firstOrNull()
    }

    fun nextHumanReactionBeat(
        session: GameSession,
        humanMessage: String,
        deliveredReactions: Int,
        lastSpeaker: String?,
        intentHint: HumanMessageIntent? = null
    ): BotConversationBeat? {
        if (!canRun(session) || deliveredReactions >= maxHumanReactions) return null
        val reactions = runCatching {
            LocalBotAi.reactionsToHumanMessage(session, humanMessage, intentHint)
        }
            .getOrDefault(emptyList())
        val directAddressee = BotPerception.directAddressee(session, humanMessage)
        val remainingMessages = (maxHumanReactions - deliveredReactions).coerceAtLeast(1)
        return reactions
            .asSequence()
            .filter { (speaker, _) ->
                when {
                    directAddressee == null -> speaker != lastSpeaker
                    deliveredReactions == 0 && speaker == directAddressee -> true
                    else -> speaker != lastSpeaker && speaker != directAddressee
                }
            }
            .map { (speaker, message) ->
                BotConversationBeat(
                    speaker = speaker,
                    message = message,
                    followUps = BotMessageBursts.afterHumanReply(
                        session = session,
                        speaker = speaker,
                        humanMessage = humanMessage,
                        primaryMessage = message
                    ).take(remainingMessages - 1)
                )
            }
            .firstOrNull()
    }

    fun nextTraitorNightBeat(
        session: GameSession,
        deliveredLines: Int,
        lastSpeaker: String?
    ): BotConversationBeat? {
        if (!canRunTraitorNight(session) || deliveredLines >= traitorNightBudget(session)) return null
        return chooseTraitorSpeakers(session, lastSpeaker)
            .asSequence()
            .mapNotNull { speaker ->
                LocalBotAi.nextTraitorLine(session, speaker)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { message ->
                        val remainingMessages = (traitorNightBudget(session) - deliveredLines).coerceAtLeast(1)
                        BotConversationBeat(
                            speaker = speaker,
                            message = message,
                            followUps = BotMessageBursts.afterTraitorNightLine(session, speaker, message)
                                .take(remainingMessages - 1)
                        )
                    }
            }
            .firstOrNull()
    }

    fun naturalDelayMs(
        session: GameSession,
        beatIndex: Int,
        message: String,
        reaction: Boolean,
        speaker: String? = null
    ): Long {
        val base = if (reaction) 1_400L else 1_900L
        val jitter = (stableNoise("${session.code}:${session.phaseIndex}:$beatIndex:$message") % 900).toLong()
        val readingDelay = (message.length * 12L).coerceAtMost(700L)
        val normalized = normalizedForParsing(message)
        val thoughtDelay = if (
            message.contains("?") ||
            normalized.contains("por que") ||
            normalized.contains("que rol")
        ) 250L else 0L
        val personalityDelay = speaker
            ?.let { GameEngine.playerByName(session, it) }
            ?.let { bot ->
                when (personalityFor(session, bot)) {
                    BotPersonality.IMPULSIVO -> -300L
                    BotPersonality.JODON -> -200L
                    BotPersonality.PICANTE -> -100L
                    BotPersonality.TRANQUI -> 100L
                    BotPersonality.DESCONFIADO -> 250L
                    BotPersonality.ANALITICO -> 450L
                }
            }
            ?: 0L
        val competitivenessDelay = speaker
            ?.let { GameEngine.playerByName(session, it) }
            ?.let { bot ->
                when (competitivenessFor(session, bot)) {
                    BotCompetitiveness.RELAJADO -> 150L
                    BotCompetitiveness.EQUILIBRADO -> 0L
                    BotCompetitiveness.OBSESIVO -> -150L
                }
            }
            ?: 0L
        val complexAnswer = message.length >= 95 || listOf(
            "contradic",
            "pista",
            "investig",
            "explica",
            "version"
        ).any(normalized::contains)
        val maximumDelay = if (complexAnswer) 4_800L else 3_600L
        return (base + jitter + readingDelay + thoughtDelay + personalityDelay + competitivenessDelay)
            .coerceIn(1_400L, maximumDelay)
    }

    fun burstDelayMs(session: GameSession, beatIndex: Int, speaker: String, message: String): Long {
        val jitter = stableNoise(
            "${session.code}:${session.phaseIndex}:$beatIndex:$speaker:burst:$message"
        ) % 350
        val typingTime = (message.length * 8L).coerceAtMost(400L)
        return (550L + jitter + typingTime).coerceIn(600L, 1_450L)
    }

    fun silenceDelayMs(session: GameSession, idleLinesUsed: Int): Long {
        val base = if (session.botDifficulty == BotDifficulty.HARD) 9_000L else 12_000L
        val range = if (session.botDifficulty == BotDifficulty.HARD) 5_000 else 6_000
        return base + (stableNoise("${session.code}:${session.phaseIndex}:pause:$idleLinesUsed") % range).toLong()
    }

    fun playerNudgeBeat(session: GameSession, lastSpeaker: String?): BotConversationBeat? {
        if (!canRun(session)) return null
        val human = GameEngine.humanPlayer(session).takeIf { it.alive } ?: return null
        val speaker = chooseSpeakers(session, lastSpeaker).firstOrNull() ?: return null
        val bot = GameEngine.playerByName(session, speaker) ?: return null
        val humanName = safeName(human, session)
        val message = when (personalityFor(session, bot)) {
            BotPersonality.PICANTE ->
                "$humanName no te me borres ahora, a quien votarias?"
            BotPersonality.DESCONFIADO ->
                "$humanName te quedaste mirando de afuera. quien te hace mas ruido?"
            BotPersonality.JODON ->
                "$humanName tira algo aunque sea, despues fingimos que fue una gran teoria"
            BotPersonality.ANALITICO ->
                "$humanName cerremos una idea: a quien escucharias y a quien votarias?"
            else ->
                "$humanName, vos que pensas? tira una sospecha y la vemos"
        }
        return BotConversationBeat(
            speaker = speaker,
            message = sanitizeBotSpeech(message, session),
            promptsSilentHuman = true
        )
    }

    private fun silentHumanPrompt(
        session: GameSession,
        idleLinesUsed: Int,
        lastSpeaker: String?,
        humanSpokeThisPhase: Boolean,
        promptedSilentHuman: Boolean
    ): BotConversationBeat? {
        if (humanSpokeThisPhase || promptedSilentHuman) return null
        if (idleLinesUsed < pauseAfterBotStreak(session)) return null
        val human = GameEngine.humanPlayer(session).takeIf { it.alive } ?: return null
        val speaker = chooseSpeakers(session, lastSpeaker).firstOrNull() ?: return null
        val humanName = safeName(human, session)
        val message = when (personalityFor(session, GameEngine.playerByName(session, speaker)!!)) {
            BotPersonality.PICANTE -> "$humanName estas muy callado, juga un poco: a quien votarias?"
            BotPersonality.DESCONFIADO -> "$humanName estas mirando de afuera, eso tambien hace ruido. a quien miras?"
            BotPersonality.JODON -> "$humanName si te quedas mudo te van a usar de voto facil, tira una sospecha"
            else -> "$humanName estas muy callado, a quien miras vos?"
        }
        return BotConversationBeat(
            speaker = speaker,
            message = sanitizeBotSpeech(message, session),
            promptsSilentHuman = true
        )
    }

    private fun lineForSpeaker(
        session: GameSession,
        speaker: String,
        remainingMessages: Int
    ): BotConversationBeat? {
        val message = LocalBotAi.nextConversationLine(session, speaker)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return BotConversationBeat(
            speaker = speaker,
            message = message,
            followUps = BotMessageBursts.afterIdleLine(session, speaker, message)
                .take((remainingMessages - 2).coerceAtLeast(0))
        )
    }

    private fun oracleGuestOpeningBeat(
        session: GameSession,
        idleLinesUsed: Int
    ): BotConversationBeat? {
        if (session.phase != GamePhase.DIA_DEBATE || idleLinesUsed != 0) return null
        val guestName = session.oracleInvitedPlayer.takeIf { it.isNotBlank() } ?: return null
        val guest = GameEngine.playerByName(session, guestName)
            ?.takeIf { !it.isHuman && GameEngine.canParticipateInChat(session, it) }
            ?: return null
        val generated = LocalBotAi.nextConversationLine(session, guest.name)
            ?.takeIf { it.isNotBlank() }
        val message = generated ?: sanitizeBotSpeech(
            "Me dieron una ultima voz. Miren bien a quienes siguen vivos.",
            session
        )
        return BotConversationBeat(guest.name, message)
    }

    private fun chooseSpeakers(session: GameSession, lastSpeaker: String?): List<String> {
        val preferred = preferredSpeakerFromLastMessage(session, lastSpeaker)
            ?.takeIf { it != lastSpeaker }
        val preferredList = preferred?.let { listOf(it) }.orEmpty()
        val others = messageBots(session, limit = eligibleSpeakers(session).size)
            .map { it.name }
            .filter { it != preferred && it != lastSpeaker }
        val fallback = eligibleSpeakers(session)
            .map { it.name }
            .filter { it !in preferredList && it !in others && it != lastSpeaker }
        val repeatedFallback = lastSpeaker?.let { speaker ->
            eligibleSpeakers(session).map { it.name }.filter { it == speaker }
        }.orEmpty()
        return preferredList + others + fallback + repeatedFallback
    }

    private fun preferredSpeakerFromLastMessage(session: GameSession, lastSpeaker: String?): String? {
        val last = recentPublicMessages(session).lastOrNull() ?: return null
        if (last.speaker == lastSpeaker) return null
        val eligible = eligibleSpeakers(session).map { it.name }.toSet()
        if (last.message.contains("?")) {
            mentionedPlayerNames(session, last.message)
                .firstOrNull { it in eligible && it != last.speaker }
                ?.let { return it }
        }
        return mentionedPlayerNames(session, last.message)
            .firstOrNull { it in eligible && it != last.speaker }
    }

    private fun eligibleSpeakers(session: GameSession): List<GamePlayer> {
        return session.players.filter {
            !it.isHuman && GameEngine.canParticipateInChat(session, it)
        }
    }

    private fun chooseTraitorSpeakers(session: GameSession, lastSpeaker: String?): List<String> {
        val eligible = eligibleTraitorSpeakers(session).map { it.name }
        val planned = session.traitorPlan
            ?.speakingOrder
            .orEmpty()
            .filter { it in eligible && it != lastSpeaker }
        val fallback = eligible.filter { it !in planned && it != lastSpeaker }
        val repeatedFallback = lastSpeaker?.takeIf { it in eligible }?.let { listOf(it) }.orEmpty()
        return planned + fallback + repeatedFallback
    }

    private fun eligibleTraitorSpeakers(session: GameSession): List<GamePlayer> {
        return session.players.filter {
            !it.isHuman && GameEngine.canSeeTraitorChat(it)
        }
    }

    private const val maxHumanReactions = 4
}
