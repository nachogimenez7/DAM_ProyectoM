package com.traidores.juego

internal data class BotConversationBeat(
    val speaker: String,
    val message: String,
    val promptsSilentHuman: Boolean = false
)

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
            GamePhase.DESEMPATE_VOTACION -> speakers.coerceAtMost(8)
            GamePhase.CONTRAPUNTO -> speakers.coerceAtMost(6)
            GamePhase.DIA_DEBATE -> ((speakers * 3 + 1) / 2).coerceIn(1, 10)
            else -> 0
        }
    }

    fun pauseAfterBotStreak(session: GameSession): Int {
        return 3 + stableNoise("${session.code}:${session.phaseIndex}:bot-pause") % 3
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
        silentHumanPrompt(
            session = session,
            idleLinesUsed = idleLinesUsed,
            lastSpeaker = lastSpeaker,
            humanSpokeThisPhase = humanSpokeThisPhase,
            promptedSilentHuman = promptedSilentHuman
        )?.let {
            return it
        }
        return chooseSpeakers(session, lastSpeaker)
            .asSequence()
            .mapNotNull { speaker -> lineForSpeaker(session, speaker) }
            .firstOrNull()
    }

    fun nextHumanReactionBeat(
        session: GameSession,
        humanMessage: String,
        deliveredReactions: Int,
        lastSpeaker: String?
    ): BotConversationBeat? {
        if (!canRun(session) || deliveredReactions >= maxHumanReactions) return null
        val reactions = runCatching { LocalBotAi.reactionsToHumanMessage(session, humanMessage) }
            .getOrDefault(emptyList())
        return reactions
            .asSequence()
            .filter { (speaker, _) -> speaker != lastSpeaker }
            .map { (speaker, message) -> BotConversationBeat(speaker, message) }
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
                    ?.let { BotConversationBeat(speaker, it) }
            }
            .firstOrNull()
    }

    fun naturalDelayMs(session: GameSession, beatIndex: Int, message: String, reaction: Boolean): Long {
        val base = if (reaction) 2_500L else 3_200L
        val jitter = (stableNoise("${session.code}:${session.phaseIndex}:$beatIndex:$message") % 1_700).toLong()
        val readingDelay = (message.length * 24L).coerceAtMost(1_400L)
        return base + jitter + readingDelay
    }

    fun silenceDelayMs(session: GameSession, idleLinesUsed: Int): Long {
        return 6_000L + (stableNoise("${session.code}:${session.phaseIndex}:pause:$idleLinesUsed") % 4_000).toLong()
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

    private fun lineForSpeaker(session: GameSession, speaker: String): BotConversationBeat? {
        val message = LocalBotAi.nextConversationLine(session, speaker)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return BotConversationBeat(speaker, message)
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

    private const val maxHumanReactions = 3
}
