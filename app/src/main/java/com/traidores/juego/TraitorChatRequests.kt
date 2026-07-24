package com.traidores.juego

/**
 * Lee los pedidos que el jugador humano deja en el Plan de los Asesinos para que
 * sus aliados bot los ejecuten esa misma noche.
 *
 * Solo se leen mensajes del humano y de la ronda en curso: un pedido de anoche no
 * arrastra a la noche siguiente. Un pedido invalido nunca fuerza nada; el bot cae
 * en su propia eleccion.
 */
internal enum class TraitorRequestKind {
    MATAR,
    SILENCIAR,
    DESCARTAR,
    CUIDADO,
    COBERTURA,
    ROL_FALSO,
    CIERRE,
    OTRO
}

internal data class TraitorRequest(
    val kind: TraitorRequestKind,
    val target: String = "",
    val targetIsAlly: Boolean = false
)

internal object TraitorChatRequests {

    private val killVerbs = listOf(
        "matemos",
        "matamos",
        "matar",
        "matalo",
        "matala",
        "bajemos",
        "bajamos",
        "bajar",
        "eliminemos",
        "eliminar"
    )

    private val silenceVerbs = listOf(
        "silenciemos",
        "silenciamos",
        "silenciar",
        "callemos",
        "callamos",
        "callar",
        "mutear",
        "muteamos"
    )

    private val watchSignals = listOf("cuidado con", "ojo con", "nos esta leyendo", "nos va a marcar")

    private val coverSignals = listOf(
        "cubranme",
        "cubrime",
        "cubranos",
        "me estan marcando",
        "estoy limpio",
        "hablo yo",
        "no nos crucemos"
    )

    private val fakeClaimSignals = listOf("digo que soy", "decir que soy", "me tiro de")

    private val closeSignals = listOf("cerrado", "quedamos asi", "de una", "tranquilos", "sin regalarnos")

    fun killTarget(session: GameSession, killer: GamePlayer): String? {
        val human = GameEngine.humanPlayer(session)
        return requestedTarget(session, TraitorRequestKind.MATAR) { candidate ->
            GameEngine.isValidKillTarget(session, candidate.name, killer) &&
                !(session.debugBotsNeverKillHuman && candidate.name == human.name)
        }
    }

    fun silenceTarget(session: GameSession, mercenary: GamePlayer): String? {
        return requestedTarget(session, TraitorRequestKind.SILENCIAR) { candidate ->
            GameEngine.isValidSilenceTarget(session, candidate.name, mercenary) &&
                !GameRules.isTraitorRole(candidate.role) &&
                !GameEngine.isDesertorAlignedWithTraitors(session, candidate)
        }
    }

    /** Lee un mensaje suelto del humano para poder contestarlo en el chat. */
    fun classify(session: GameSession, message: String): TraitorRequest {
        val normalized = normalizedForParsing(message)
        if (normalized.isBlank()) return TraitorRequest(TraitorRequestKind.OTRO)
        val human = GameEngine.humanPlayer(session)
        // Nombrar a dos jugadores en el mismo mensaje es ambiguo: se responde sin objetivo.
        val target = GameEngine.alivePlayers(session)
            .filter { it.name != human.name && mentionsName(message, it.name) }
            .singleOrNull()
        val targetName = target?.name.orEmpty()
        val kind = when {
            targetName.isNotBlank() &&
                rejectsTarget(normalized, normalizedForParsing(targetName)) ->
                TraitorRequestKind.DESCARTAR
            killVerbs.any { it in normalized } -> TraitorRequestKind.MATAR
            silenceVerbs.any { it in normalized } -> TraitorRequestKind.SILENCIAR
            watchSignals.any { it in normalized } -> TraitorRequestKind.CUIDADO
            coverSignals.any { it in normalized } -> TraitorRequestKind.COBERTURA
            fakeClaimSignals.any { it in normalized } -> TraitorRequestKind.ROL_FALSO
            closeSignals.any { it in normalized } -> TraitorRequestKind.CIERRE
            else -> TraitorRequestKind.OTRO
        }
        return TraitorRequest(
            kind = kind,
            target = targetName,
            targetIsAlly = target != null &&
                (
                    GameRules.isTraitorRole(target.role) ||
                        GameEngine.isDesertorAlignedWithTraitors(session, target)
                    )
        )
    }

    private fun requestedTarget(
        session: GameSession,
        kind: TraitorRequestKind,
        isUsable: (GamePlayer) -> Boolean
    ): String? {
        val human = GameEngine.humanPlayer(session)
        if (!GameEngine.canSeeTraitorChat(human)) return null
        val requests = session.chatHistory.filter {
            it.channel == ChatChannel.TRAIDORES &&
                !it.isGod &&
                it.round == session.round &&
                it.speaker == human.name
        }
        if (requests.isEmpty()) return null
        val usableNames = GameEngine.alivePlayers(session).filter(isUsable).map { it.name }.toSet()
        if (usableNames.isEmpty()) return null

        val rejected = mutableSetOf<String>()
        requests.asReversed().forEach { entry ->
            val request = classify(session, entry.message)
            if (request.target.isBlank()) return@forEach
            when {
                request.kind == TraitorRequestKind.DESCARTAR -> rejected += request.target
                request.target in rejected -> Unit
                request.kind == kind && request.target in usableNames -> return request.target
            }
        }
        return null
    }

    private fun rejectsTarget(normalizedMessage: String, normalizedName: String): Boolean {
        return normalizedMessage.startsWith("no ") ||
            normalizedMessage.contains("$normalizedName no")
    }
}
