package com.traidores.juego

/**
 * A chat message that can be sent with one or two taps. The intent is local-only
 * context for the bot brain; the visible text remains an ordinary chat message.
 */
internal data class QuickChatMessage(
    val text: String,
    val intentHint: HumanMessageIntent,
    val action: QuickChatAction = QuickChatAction.SEND
)

internal enum class QuickChatAction {
    SEND,
    CHOOSE_SUSPECT,
    CHOOSE_ROLE,
    CHOOSE_VOTE
}

/**
 * Contextual teaching prompts and factories for the extended quick-chat menu.
 * Choosing one never performs a vote or a night action.
 */
internal object BotQuickReplies {
    fun forSession(session: GameSession): List<QuickChatMessage> {
        val human = GameEngine.humanPlayer(session)
        if (!human.alive || !GameEngine.canHumanChat(session)) return emptyList()
        val humanHasSpoken = recentPublicMessages(session).any { message ->
            !message.isGod && message.speaker == human.name
        }
        val lastBotMessage = recentPublicMessages(session)
            .asReversed()
            .firstOrNull { !it.isGod && it.speaker != human.name }
            ?.message
            .orEmpty()
        if (lastBotMessage.isBlank()) return generalReplies()

        val normalized = normalizedForParsing(lastBotMessage)
        val claim = latestClaimBySpeaker(session, human.name)
        val mentionedTarget = mentionedPlayerNames(session, lastBotMessage)
            .firstOrNull { it != human.name }
        val asksForImmediateHumanAnswer =
            asksForRole(normalized) ||
                (claim?.roleKey == RoleCatalog.POLICIA && asksForInvestigation(normalized)) ||
                (claim?.roleKey == RoleCatalog.MEDICO && asksForProtection(normalized))
        if (!humanHasSpoken && !asksForImmediateHumanAnswer) return generalReplies()

        return when {
            asksForRole(normalized) -> roleReplies(session)
            claim?.roleKey == RoleCatalog.POLICIA && asksForInvestigation(normalized) ->
                investigationReplies(session)
            claim?.roleKey == RoleCatalog.MEDICO && asksForProtection(normalized) ->
                protectionReplies(session)
            mentionedTarget != null && BotJesterAwareness.isInPlay(session) ->
                targetReplies(mentionedTarget, includeJesterRead = true)
            mentionedTarget != null ->
                targetReplies(mentionedTarget, includeJesterRead = false)
            asksForSuspect(normalized) -> generalReplies()
            else -> generalReplies()
        }.distinctBy { it.text }.take(3)
    }

    fun aliveTargets(session: GameSession): List<GamePlayer> {
        val human = GameEngine.humanPlayer(session)
        return GameEngine.alivePlayers(session)
            .filter { it.name != human.name }
            .sortedBy { it.name.lowercase() }
    }

    fun rolesInPlay(session: GameSession): List<GameRole> {
        val presentKeys = session.players.mapNotNull { it.role?.key }.toSet()
        val map = RoleMap.fromSessionKey(session.mapKey)
        return RoleCatalog.guideKeys()
            .filter { roleKey -> roleKey in presentKeys && RoleCatalog.isAvailableOnMap(roleKey, map) }
            .map { roleKey -> RoleCatalog.gameRole(roleKey, map) }
    }

    fun suspect(target: String): QuickChatMessage =
        QuickChatMessage("Sospecho de $target", HumanMessageIntent.ACCUSE)

    fun defend(target: String): QuickChatMessage =
        QuickChatMessage("Yo no votaria a $target todavia", HumanMessageIntent.DEFEND)

    fun askRole(target: String): QuickChatMessage =
        QuickChatMessage("$target, que rol sos?", HumanMessageIntent.ROLE_QUESTION)

    fun askVote(target: String): QuickChatMessage =
        QuickChatMessage("$target, a quien votarias?", HumanMessageIntent.VOTE_HELP)

    fun askExplanation(target: String): QuickChatMessage =
        QuickChatMessage("$target, por que sospechas de mi?", HumanMessageIntent.SUSPECT_HELP)

    fun claimRole(role: GameRole): QuickChatMessage =
        QuickChatMessage("Soy ${role.name.lowercase()}", HumanMessageIntent.ROLE_CLAIM)

    fun investigation(target: String, suspicious: Boolean): QuickChatMessage {
        val result = if (suspicious) "sospechoso" else "inocente"
        return QuickChatMessage(
            "Investigue a $target y me dio $result",
            HumanMessageIntent.ACTION_CLAIM
        )
    }

    fun protection(target: String): QuickChatMessage =
        QuickChatMessage("Protegi a $target anoche", HumanMessageIntent.ACTION_CLAIM)

    fun voteFor(target: String): QuickChatMessage =
        QuickChatMessage("Yo votaria a $target", HumanMessageIntent.ACCUSE)

    fun voteTogether(target: String): QuickChatMessage =
        QuickChatMessage("Votemos a $target", HumanMessageIntent.ACCUSE)

    fun holdVote(): QuickChatMessage =
        QuickChatMessage("No votemos apurados todavia", HumanMessageIntent.VOTE_HELP)

    fun hearFirst(target: String): QuickChatMessage =
        QuickChatMessage("Quiero escuchar a $target primero", HumanMessageIntent.SUSPECT_HELP)

    private fun roleReplies(session: GameSession): List<QuickChatMessage> {
        val actualRole = GameEngine.humanPlayer(session).role
        val actualLabel = actualRole?.name?.lowercase().orEmpty().ifBlank { "aldeano" }
        val falseLabel = if (actualRole?.key == RoleCatalog.ALDEANO) "detective" else "aldeano"
        return listOf(
            QuickChatMessage("Soy $actualLabel", HumanMessageIntent.ROLE_CLAIM),
            QuickChatMessage("Soy $falseLabel", HumanMessageIntent.ROLE_CLAIM),
            QuickChatMessage(
                "No voy a decir mi rol todavia",
                HumanMessageIntent.REFUSE_ROLE
            )
        )
    }

    private fun investigationReplies(session: GameSession): List<QuickChatMessage> {
        val first = candidateNames(session).firstOrNull() ?: "alguien"
        return listOf(
            QuickChatMessage("Investigue a $first", HumanMessageIntent.ACTION_CLAIM),
            QuickChatMessage("No lo voy a decir todavia", HumanMessageIntent.REFUSE_ROLE),
            QuickChatMessage("Menti, no soy detective", HumanMessageIntent.ROLE_CLAIM)
        )
    }

    private fun protectionReplies(session: GameSession): List<QuickChatMessage> {
        val first = candidateNames(session).firstOrNull() ?: "alguien"
        return listOf(
            QuickChatMessage("Protegi a $first", HumanMessageIntent.ACTION_CLAIM),
            QuickChatMessage("Prefiero no decir a quien", HumanMessageIntent.REFUSE_ROLE),
            QuickChatMessage("Menti, no soy medico", HumanMessageIntent.ROLE_CLAIM)
        )
    }

    private fun targetReplies(
        target: String,
        includeJesterRead: Boolean
    ): List<QuickChatMessage> {
        return buildList {
            add(suspect(target))
            add(hearFirst(target))
            if (includeJesterRead) {
                add(
                    QuickChatMessage(
                        "Ojo, $target puede ser el bufon",
                        HumanMessageIntent.VOTE_HELP
                    )
                )
            } else {
                add(defend(target))
            }
        }
    }

    private fun generalReplies(): List<QuickChatMessage> {
        return listOf(
            QuickChatMessage(
                text = "Sospecho de:",
                intentHint = HumanMessageIntent.ACCUSE,
                action = QuickChatAction.CHOOSE_SUSPECT
            ),
            QuickChatMessage(
                text = "Soy:",
                intentHint = HumanMessageIntent.ROLE_CLAIM,
                action = QuickChatAction.CHOOSE_ROLE
            ),
            QuickChatMessage(
                text = "Votemos a:",
                intentHint = HumanMessageIntent.ACCUSE,
                action = QuickChatAction.CHOOSE_VOTE
            )
        )
    }

    private fun candidateNames(session: GameSession): List<String> {
        val human = GameEngine.humanPlayer(session)
        return GameEngine.alivePlayers(session)
            .filter { it.name != human.name }
            .sortedWith(
                compareBy<GamePlayer> {
                    stableNoise("${session.code}:${session.phaseIndex}:quick-reply:${it.name}")
                }.thenBy { it.name }
            )
            .map { safeName(it, session) }
    }

    private fun asksForRole(message: String): Boolean {
        return "que rol" in message ||
            "tu rol" in message ||
            "sos medico" in message ||
            "sos detective" in message ||
            "sos aldeano" in message
    }

    private fun asksForInvestigation(message: String): Boolean {
        return "a quien investig" in message ||
            "a quien miraste" in message ||
            "que te dio" in message ||
            "nombre que investigaste" in message
    }

    private fun asksForProtection(message: String): Boolean {
        return "a quien cuid" in message ||
            "a quien proteg" in message ||
            "a quien salvaste" in message
    }

    private fun asksForSuspect(message: String): Boolean {
        return "a quien votarias" in message ||
            "quien te hace" in message ||
            "una sospecha" in message ||
            "que pensas" in message ||
            "a quien miras" in message
    }
}
