package com.traidores.juego

import java.io.Serializable

data class CompanionCardMetrics(
    val columnWidthDp: Int,
    val minCardWidthDp: Int,
    val itemHeightDp: Int,
    val itemGapDp: Int,
    val avatarSizeDp: Int,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val nameHeightDp: Int,
    val nameTextSp: Float,
    val scrollEnabled: Boolean
)

data class TieVoteGridMetrics(
    val columns: Int,
    val rows: Int,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val scrollEnabled: Boolean
)

enum class PublicEventType(val colorHex: String) {
    DEATH("#a83232"),
    VOTING("#d4a24e"),
    DISCUSSION("#4a7fb5"),
    PHASE_START("#5a8a3c")
}

enum class GameplayActionTone(val colorHex: String, val darkText: Boolean) {
    KILL("#D12A1E", false),
    SAVE("#5A8A3C", false),
    INVESTIGATE("#4A7FB5", false),
    SILENCE("#6E2632", false),
    CONTRAPUNTO("#D4A24E", true),
    DECIDE("#E0A838", true),
    INVOKE("#8348C6", false),
    DEFAULT("#2A2318", false)
}

enum class GameplayPeriod {
    DAY,
    NIGHT
}

data class GameplayTransitionSpec(
    val period: GameplayPeriod,
    val title: String,
    val key: String
)

data class GameWinnerPresentation(
    val winningPlayers: List<GamePlayer>,
    val humanWon: Boolean,
    val summary: GameSummaryPresentation,
    val specialVictories: List<GameSpecialVictory> = emptyList(),
    val specialWinningPlayers: List<GamePlayer> = emptyList()
)

data class GameSummaryPresentation(
    val roundsPlayed: Int,
    val durationLabel: String,
    val survivors: Int,
    val eliminated: Int,
    val eliminatedPlayers: List<String>,
    val humanHighlight: String,
    val daySummaries: List<String>,
    val keyMoments: List<String>
)

enum class GameplayFeedbackType : Serializable {
    PRIVATE_RESULT,
    ACTION_CONFIRMATION
}

data class GameplayFeedbackSpec(
    val type: GameplayFeedbackType,
    val title: String,
    val message: String,
    val target: String,
    val tone: GameplayActionTone,
    val durationMs: Long
) : Serializable {
    val blocksGameplay: Boolean
        get() = type == GameplayFeedbackType.PRIVATE_RESULT
}

object GameplayTableUi {

    const val SIDE_COLUMN_WIDTH_DP = 78
    private const val MAX_COMPANION_ITEM_HEIGHT_DP = 132
    private const val MAX_COMPANION_CARD_GROWTH_SCALE = 1.9f

    fun playerInitial(player: GamePlayer): String {
        val initial = player.initial.trim().firstOrNull { it.isLetter() }
        val fallback = player.name.trim().firstOrNull { it.isLetter() }
        return (initial ?: fallback)?.uppercaseChar()?.toString() ?: "?"
    }

    fun traitorTeammatesForReveal(session: GameSession): List<GamePlayer> {
        val human = GameEngine.humanPlayer(session)
        if (human.role?.key !in GameRules.traitorRoleKeys) return emptyList()
        return session.players.filter { player ->
            !player.isHuman && player.role?.key in GameRules.traitorRoleKeys
        }
    }

    fun shouldShowTraitorReveal(session: GameSession, completed: Boolean): Boolean {
        return !completed &&
            session.phase == GamePhase.REPARTO &&
            traitorTeammatesForReveal(session).isNotEmpty()
    }

    fun splitCompanions(
        players: List<GamePlayer>,
        includeEliminated: Boolean = true,
        putOddExtraOnLeft: Boolean = false
    ): Pair<List<GamePlayer>, List<GamePlayer>> {
        val companions = players
            .filterNot { it.isHuman }
            .filter { includeEliminated || it.alive }
        val leftCount = if (putOddExtraOnLeft) {
            (companions.size + 1) / 2
        } else {
            companions.size / 2
        }
        return companions.take(leftCount) to companions.drop(leftCount)
    }

    fun themeForMapKey(mapKey: String): String {
        return when (mapKey) {
            "grecia" -> "griego"
            "medieval" -> "medieval"
            else -> "gaucho"
        }
    }

    fun isNightPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.NOCHE_ASESINO ||
            phase == GamePhase.NOCHE_MERCENARIO ||
            phase == GamePhase.NOCHE_POLICIA ||
            phase == GamePhase.NOCHE_MEDICO ||
            phase == GamePhase.NOCHE_ORACULO
    }

    fun transitionSpec(session: GameSession): GameplayTransitionSpec {
        val period = if (isNightPhase(session.phase)) GameplayPeriod.NIGHT else GameplayPeriod.DAY
        val visualNumber = when {
            period == GameplayPeriod.NIGHT -> session.round
            else -> session.round
        }
        val label = if (period == GameplayPeriod.NIGHT) "NOCHE" else "DÍA"
        return GameplayTransitionSpec(
            period = period,
            title = "$label $visualNumber",
            key = "${period.name}_$visualNumber"
        )
    }

    fun shouldPresentTransition(spec: GameplayTransitionSpec, lastPresentedKey: String?): Boolean {
        return spec.key != lastPresentedKey
    }

    fun canHumanMedicSelfProtect(session: GameSession): Boolean {
        if (session.phase != GamePhase.NOCHE_MEDICO) return false
        val human = GameEngine.humanPlayer(session)
        return GameEngine.isHumanRoleTurn(session, "medico") &&
            GameEngine.canActOnTarget(session, human.name)
    }

    fun validHumanTargets(session: GameSession): List<GamePlayer> {
        return session.players.filter { GameEngine.canActOnTarget(session, it.name) }
    }

    fun confirmedTargetActionLabel(session: GameSession, selectedTarget: String): String? {
        if (selectedTarget.isBlank() || !GameEngine.canActOnTarget(session, selectedTarget)) return null
        return GameEngine.targetActionLabel(session, selectedTarget)
            .takeIf { it.isNotBlank() }
            ?.let { if (it == "CONTRAPUNTO") "SEÑALAR" else it }
    }

    fun actionToneFor(label: String): GameplayActionTone {
        val normalized = GameplayTextMarkers.normalize(label).uppercase()
        return when {
            normalized == "MATAR" || normalized.startsWith("MATAR A ") ||
                normalized == "VICTIMA" -> GameplayActionTone.KILL
            normalized == "SALVAR" || normalized == "SALVARME" ||
                normalized == "PROTEGER" || normalized == "PROTEGERME" ||
                normalized.startsWith("SALVAR A ") ||
                normalized.startsWith("PROTEGER A ") -> GameplayActionTone.SAVE
            normalized == "INVESTIGAR" || normalized.startsWith("INVESTIGAR A ") ||
                normalized == "PISTA" -> GameplayActionTone.INVESTIGATE
            normalized == "INVOCAR" || normalized.startsWith("INVOCAR A ") ->
                GameplayActionTone.INVOKE
            normalized == "SILENCIAR" || normalized.startsWith("SILENCIAR A ") ||
                normalized == "CALLAR" -> GameplayActionTone.SILENCE
            normalized == "CONTRAPUNTO" ||
                normalized == "SENALAR" || normalized.startsWith("SENALAR A ") -> GameplayActionTone.CONTRAPUNTO
            normalized == "VOTAR" || normalized.startsWith("VOTAR A ") ||
                normalized == "DECIDIR" || normalized.startsWith("EXPULSAR A ") ||
                normalized == "EXPULSAR" ||
                normalized == "REVELARME" ||
                normalized == "ELEGIR BANDO" ||
                normalized == "REVISAR BANDO" -> GameplayActionTone.DECIDE
            else -> GameplayActionTone.DEFAULT
        }
    }

    fun feedbackForResolvedAction(
        before: GameSession,
        after: GameSession,
        target: String
    ): GameplayFeedbackSpec? {
        if (target.isBlank() || before == after) return null
        return when (before.phase) {
            GamePhase.NOCHE_ASESINO -> privateFeedback(
                title = "VÍCTIMA ELEGIDA",
                message = "Elegiste a $target. El resultado se anunciará al amanecer.",
                target = target,
                tone = GameplayActionTone.KILL
            )
            GamePhase.NOCHE_MERCENARIO -> privateFeedback(
                title = "SILENCIO REGISTRADO",
                message = "$target no podrá hablar ni votar durante el día.",
                target = target,
                tone = GameplayActionTone.SILENCE
            )
            GamePhase.NOCHE_POLICIA -> {
                val result = after.investigatedResult.uppercase()
                privateFeedback(
                    title = "RESPUESTA PRIVADA",
                    message = "$target parece $result.",
                    target = target,
                    tone = GameplayActionTone.INVESTIGATE
                )
            }
            GamePhase.NOCHE_MEDICO -> privateFeedback(
                title = "PROTECCIÓN REGISTRADA",
                message = if (target == GameEngine.humanPlayer(before).name) {
                    "Te protegiste durante esta noche."
                } else {
                    "Protegiste a $target durante esta noche."
                },
                target = target,
                tone = GameplayActionTone.SAVE
            )
            GamePhase.NOCHE_ORACULO -> privateFeedback(
                title = "INVOCACIÓN REGISTRADA",
                message = "$target regresará para discutir durante el próximo día.",
                target = target,
                tone = GameplayActionTone.INVOKE
            )
            GamePhase.DIA_DEBATE -> actionConfirmation(
                title = "CONTRAPUNTO",
                message = if (after.phase == GamePhase.CONTRAPUNTO) {
                    "Elegiste a $target. El Contrapunto empieza ahora."
                } else {
                    "Elegiste a $target. Falta un participante."
                },
                target = target,
                tone = GameplayActionTone.CONTRAPUNTO
            )
            GamePhase.CONTRAPUNTO -> actionConfirmation(
                title = "SENALAMIENTO",
                message = "Senalaste a $target.",
                target = target,
                tone = GameplayActionTone.CONTRAPUNTO
            )
            GamePhase.VOTACION -> actionConfirmation(
                title = "VOTO REGISTRADO",
                message = "Votaste a $target.",
                target = target
            )
            GamePhase.DESEMPATE_VOTACION -> actionConfirmation(
                title = "VOTO DE DESEMPATE",
                message = "Votaste a $target.",
                target = target
            )
            GamePhase.ALCALDE_DESEMPATE -> actionConfirmation(
                title = "DECISION DEL ALCALDE",
                message = "Elegiste expulsar a $target.",
                target = target
            )
            else -> null
        }
    }

    fun feedbackForMayorReveal(before: GameSession, after: GameSession): GameplayFeedbackSpec? {
        if (before.alcaldeRevealed || !after.alcaldeRevealed) return null
        return actionConfirmation(
            title = "ALCALDE REVELADO",
            message = "Tu voto ahora vale doble.",
            target = GameEngine.humanPlayer(after).name
        )
    }

    fun feedbackForDesertorChoice(team: String, changedTeam: Boolean): GameplayFeedbackSpec {
        return actionConfirmation(
            title = if (changedTeam) "BANDO ACTUALIZADO" else "BANDO ELEGIDO",
            message = "Ahora apoyas a $team.",
            target = team
        )
    }

    fun personalStatus(session: GameSession): String? {
        val human = GameEngine.humanPlayer(session)
        return when {
            session.phase == GamePhase.DIA_DEBATE &&
                session.oracleInvitedPlayer == human.name -> "INVOCADO"
            !human.alive -> "ELIMINADO"
            human.muted -> "SILENCIADO"
            session.protectedPlayer == human.name &&
                human.role?.key == "medico" -> "PROTEGIDO"
            else -> null
        }
    }

    private fun privateFeedback(
        title: String,
        message: String,
        target: String,
        tone: GameplayActionTone
    ): GameplayFeedbackSpec {
        return GameplayFeedbackSpec(
            type = GameplayFeedbackType.PRIVATE_RESULT,
            title = title,
            message = message,
            target = target,
            tone = tone,
            durationMs = 2000L
        )
    }

    private fun actionConfirmation(
        title: String,
        message: String,
        target: String,
        tone: GameplayActionTone = GameplayActionTone.DECIDE
    ): GameplayFeedbackSpec {
        return GameplayFeedbackSpec(
            type = GameplayFeedbackType.ACTION_CONFIRMATION,
            title = title,
            message = message,
            target = target,
            tone = tone,
            durationMs = 1200L
        )
    }

    fun publicEvents(history: List<String>, current: String, fallback: String): List<String> {
        val events = mutableListOf<String>()
        (history + current).forEach { message ->
            publicEventLines(message).forEach { clean ->
                if (clean.isNotBlank() && events.lastOrNull() != clean) {
                    events += clean
                }
            }
        }
        if (events.isEmpty() && fallback.isNotBlank()) events += fallback.trim()
        return events
    }

    private fun publicEventLines(message: String): List<String> {
        val clean = message.trim()
        if (clean.isBlank()) return emptyList()

        val sentenceLines = clean
            .split(Regex("""(?<=\.)\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val important = sentenceLines.filter(::isImportantPublicEvent)
        return important.ifEmpty { listOf(clean) }
    }

    private fun isImportantPublicEvent(message: String): Boolean {
        val text = GameplayTextMarkers.normalize(message)
        return text.contains("murio") ||
            text.contains("no puede hablar ni votar") ||
            text.contains("silenciados:") ||
            text.contains("muteados:") ||
            text.contains("oraculo") ||
            text.contains("regrese para discutir") ||
            text.contains("regresa para hablar") ||
            text.contains("expuls") ||
            text.contains("empate") ||
            text.contains("alcalde") ||
            text.contains("contrapunto") ||
            text.contains("votacion") ||
            text.contains("victoria especial") ||
            text.contains("bufon")
    }

    fun historicalPublicEvents(history: List<String>, current: String, fallback: String): List<String> {
        val normalizedCurrent = current.trim()
        val historical = publicEvents(history, "", fallback).toMutableList()
        if (normalizedCurrent.isNotBlank() && historical.lastOrNull() == normalizedCurrent) {
            historical.removeAt(historical.lastIndex)
        }
        return historical.ifEmpty {
            listOf(fallback.trim().ifBlank { normalizedCurrent })
                .filter { it.isNotBlank() }
        }
    }

    fun centralPhaseMessage(session: GameSession, fallback: String): String {
        if (session.winner.isNotBlank()) {
            return "Fin de partida. Ganó ${session.winner}."
        }
        return when (session.phase) {
            GamePhase.REPARTO -> fallback
            GamePhase.NOCHE_ASESINO -> if (isHumanTurn(session, RoleCatalog.ASESINO)) {
                "Elige a quien eliminar esta noche."
            } else {
                "Los Traidores eligen a su víctima."
            }
            GamePhase.NOCHE_MERCENARIO -> if (isHumanTurn(session, RoleCatalog.MERCENARIO)) {
                "Elige a quién silenciar durante el próximo día."
            } else {
                "El Mercenario decide qué voz callar."
            }
            GamePhase.NOCHE_POLICIA -> if (isHumanTurn(session, RoleCatalog.POLICIA)) {
                "Elige a quien investigar esta noche."
            } else {
                "El investigador busca una pista en secreto."
            }
            GamePhase.NOCHE_MEDICO -> if (isHumanTurn(session, RoleCatalog.MEDICO)) {
                "Elige a quien proteger esta noche."
            } else {
                "El Médico decide a quién proteger."
            }
            GamePhase.NOCHE_ORACULO -> if (isHumanTurn(session, RoleCatalog.ORACULO)) {
                "Elige una voz para el debate o guarda tu poder."
            } else {
                "El Oráculo decide si devuelve una voz al pueblo."
            }
            GamePhase.AMANECER -> session.publicAnnouncement.ifBlank { fallback }
            GamePhase.DIA_DEBATE -> {
                val muted = session.players.filter { it.alive && it.muted }.joinToString(", ") { it.name }
                if (muted.isBlank()) {
                    "Debatan, comparen versiones y preparen la votación."
                } else {
                    "Debatan antes de votar. $muted no puede hablar ni votar hoy."
                }
            }
            GamePhase.CONTRAPUNTO ->
                "Escucha a los participantes y señala al más sospechoso."
            GamePhase.VOTACION ->
                "Elige a un jugador y confirma tu voto."
            GamePhase.RECUENTO_VOTOS ->
                "El pueblo cuenta los votos recibidos."
            GamePhase.DESEMPATE_VOTACION ->
                "Vota solamente entre los jugadores empatados."
            GamePhase.ALCALDE_DESEMPATE ->
                "El Alcalde debe decidir entre los jugadores empatados."
            GamePhase.RESULTADO -> session.publicAnnouncement.ifBlank { fallback }
        }
    }

    private fun isHumanTurn(session: GameSession, roleKey: String): Boolean {
        return GameEngine.isHumanRoleTurn(session, roleKey)
    }

    fun newlyKilledAtDawn(
        session: GameSession,
        knownDeadPlayers: Set<String>
    ): List<GamePlayer> {
        val announcement = GameplayTextMarkers.normalize(session.publicAnnouncement)
        return session.players.filter { player ->
            val playerName = GameplayTextMarkers.normalize(player.name)
            !player.alive &&
                player.name !in knownDeadPlayers &&
                announcement.contains("amanecer: murio $playerName")
        }
    }

    fun newlySilencedAtDawn(
        session: GameSession,
        knownMutedPlayers: Set<String>
    ): List<GamePlayer> {
        val announcement = GameplayTextMarkers.normalize(session.publicAnnouncement)
        return session.players.filter { player ->
            val playerName = GameplayTextMarkers.normalize(player.name)
            player.alive &&
                player.muted &&
                player.name !in knownMutedPlayers &&
                announcement.contains(
                    "$playerName no puede hablar ni votar hoy"
                )
        }
    }

    fun wasNoDeathAtDawn(session: GameSession): Boolean {
        return session.phase == GamePhase.DIA_DEBATE && session.nightHadNoVictim
    }

    fun winnerPresentation(session: GameSession): GameWinnerPresentation {
        if (session.winner.isBlank()) {
            return GameWinnerPresentation(
                emptyList(),
                humanWon = false,
                specialVictories = session.specialVictories,
                summary = gameSummary(session)
            )
        }

        val factionWinningPlayers = session.players.filter { player ->
            when {
                player.role?.key == "desertor" -> session.desertorTeam == session.winner
                session.winner == GameRules.TOWN_WINNER ->
                    player.role?.team == GameRules.TOWN_WINNER
                session.winner == GameRules.TRAITOR_WINNER ->
                    player.role?.key in GameRules.traitorRoleKeys
                else -> false
            }
        }
        val specialWinnerNames = session.specialVictories.map { it.playerName }.toSet()
        val specialWinningPlayers = session.players.filter { it.name in specialWinnerNames }
        return GameWinnerPresentation(
            winningPlayers = factionWinningPlayers,
            humanWon = factionWinningPlayers.any { it.isHuman } || specialWinningPlayers.any { it.isHuman },
            specialVictories = session.specialVictories,
            specialWinningPlayers = specialWinningPlayers,
            summary = gameSummary(session)
        )
    }

    fun gameSummary(
        session: GameSession,
        nowEpochMs: Long = System.currentTimeMillis()
    ): GameSummaryPresentation {
        val alive = session.players.count { it.alive }
        val elapsedMs = (nowEpochMs - session.startedAtEpochMs).coerceAtLeast(0L)
        val totalSeconds = elapsedMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val human = GameEngine.humanPlayer(session)
        val humanActions = session.actionHistory.filter { it.actor == human.name }
        val actionLabel = when (human.role?.key) {
            "asesino", "espia" -> "${humanActions.count { it.type == GameActionType.KILL }} ataques elegidos"
            "mercenario" -> "${humanActions.count { it.type == GameActionType.SILENCE }} silencios"
            "policia" -> "${humanActions.count { it.type == GameActionType.INVESTIGATE }} investigaciones"
            "medico" -> "${humanActions.count { it.type == GameActionType.PROTECT }} protecciones"
            "alcalde" -> if (session.alcaldeRevealed) "Alcalde revelado" else "Alcalde en secreto"
            "payador" -> if (session.payadorUsed) "Contrapunto utilizado" else "Contrapunto sin usar"
            "desertor" -> "Bando final: ${session.desertorTeam.ifBlank { "sin elegir" }}"
            "bufon" -> if (session.specialVictories.any { it.playerName == human.name }) {
                "Engaño completado: expulsado por votación"
            } else {
                "No fue expulsado por votación"
            }
            "oraculo" -> if (session.oracleUsed) {
                "Invocación utilizada"
            } else {
                "Invocación conservada"
            }
            else -> "${humanActions.count { it.type == GameActionType.VOTE }} votos emitidos"
        }
        return GameSummaryPresentation(
            roundsPlayed = session.round.coerceAtLeast(1),
            durationLabel = "%02d:%02d".format(minutes, seconds),
            survivors = alive,
            eliminated = (session.initialPlayerCount - alive).coerceAtLeast(0),
            eliminatedPlayers = session.players.filterNot { it.alive }.map { player ->
                "${player.name} (${player.role?.name ?: "Rol desconocido"})"
            },
            humanHighlight = actionLabel,
            daySummaries = daySummaries(session),
            keyMoments = keyMoments(session)
        )
    }

    private data class RoundOutcome(
        val killed: MutableList<String> = mutableListOf(),
        val silenced: MutableList<String> = mutableListOf(),
        val expelled: MutableList<String> = mutableListOf(),
        val specialVictories: MutableList<String> = mutableListOf(),
        var noDeath: Boolean = false,
        var noExpulsion: Boolean = false,
        var tie: Boolean = false
    )

    private fun daySummaries(session: GameSession): List<String> {
        val outcomes = roundOutcomes(session)
        return (1..session.round.coerceAtLeast(1)).map { round ->
            val outcome = outcomes[round]
            val parts = mutableListOf<String>()
            parts += if (outcome?.killed?.isNotEmpty() == true) {
                "murió ${outcome.killed.joinToString(", ")}"
            } else {
                "no murió nadie"
            }
            parts += if (outcome?.silenced?.isNotEmpty() == true) {
                "se silenció a ${outcome.silenced.joinToString(", ")}"
            } else {
                "nadie fue silenciado"
            }
            if (outcome?.expelled?.isNotEmpty() == true) {
                parts += "se expulsó a ${outcome.expelled.joinToString(", ")}"
            } else if (outcome?.noExpulsion == true) {
                parts += "nadie fue expulsado"
            }
            if (outcome?.tie == true) parts += "hubo empate"
            outcome?.specialVictories?.forEach { parts += it }
            "Día $round: ${parts.joinToString("; ")}."
        }
    }

    fun keyMoments(session: GameSession): List<String> {
        val moments = mutableListOf<String>()
        roundOutcomes(session).forEach { (round, outcome) ->
            outcome.killed.forEach { moments += "Día $round: murió $it." }
            if (outcome.noDeath && outcome.killed.isEmpty()) {
                moments += "Día $round: no murió nadie."
            }
            outcome.silenced.forEach { moments += "Día $round: $it fue silenciado." }
            if (outcome.tie) moments += "Día $round: hubo empate en la votación."
            outcome.expelled.forEach { moments += "Día $round: $it fue expulsado." }
            outcome.specialVictories.forEach { moments += "Día $round: $it." }
        }
        return moments.distinct().takeLast(7).ifEmpty {
            listOf("Día 1: no hubo eventos públicos decisivos.")
        }
    }

    private fun roundOutcomes(session: GameSession): Map<Int, RoundOutcome> {
        val outcomes = linkedMapOf<Int, RoundOutcome>()
        var currentRound = 1
        val nightPattern = Regex("""noche\s+(\d+)""", RegexOption.IGNORE_CASE)
        val dayPattern = Regex("""dia\s+(\d+)""", RegexOption.IGNORE_CASE)
        val killedPattern = Regex("""murio\s+([^.\s]+)""", RegexOption.IGNORE_CASE)
        val silencedPattern = Regex(
            """([^.]+?)\s+no puede hablar ni votar hoy""",
            RegexOption.IGNORE_CASE
        )
        val expelledPattern = Regex(
            """(?:D[ií]a\s+\d+:\s*)?([^.]+?)\s+fue expulsado""",
            RegexOption.IGNORE_CASE
        )

        publicSummaryMessages(session).forEach { message ->
            val normalizedMessage = GameplayTextMarkers.normalize(message)
            nightPattern.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                currentRound = it
            }
            val explicitDay = dayPattern.find(normalizedMessage)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            val round = explicitDay ?: currentRound
            publicEventLines(message).forEach { sentence ->
                val lower = GameplayTextMarkers.normalize(sentence)
                val outcome = outcomes.getOrPut(round) { RoundOutcome() }
                if ("no murio nadie" in lower || "nadie murio" in lower) {
                    outcome.noDeath = true
                } else {
                    killedPattern.find(lower)?.groupValues?.getOrNull(1)?.let {
                        outcome.killed.addUnique(canonicalEventTarget(session, it))
                    }
                }
                silencedPattern.find(sentence)?.groupValues?.getOrNull(1)?.let {
                    outcome.silenced.addUnique(canonicalEventTarget(session, it))
                }
                if ("nadie fue expulsado" in lower || "nadie sera expulsado" in lower) {
                    outcome.noExpulsion = true
                }
                expelledPattern.find(sentence)?.groupValues?.getOrNull(1)?.let {
                    val target = canonicalEventTarget(session, it)
                    if (!target.equals("nadie", ignoreCase = true)) {
                        outcome.expelled.addUnique(target)
                    }
                }
                if ("empate" in lower) {
                    outcome.tie = true
                }
                if ("victoria especial" in lower || ("bufon" in lower && "gano" in lower)) {
                    outcome.specialVictories.addUnique(specialVictorySummary(sentence))
                }
            }
        }
        return outcomes
    }

    private fun publicSummaryMessages(session: GameSession): List<String> {
        val godChatMessages = session.chatHistory
            .filter { it.channel == ChatChannel.PUBLICO && it.isGod }
            .map { it.message }
        return (session.publicHistory + session.godHistory + godChatMessages)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun MutableList<String>.addUnique(value: String) {
        val clean = value.trim()
        if (clean.isNotBlank() && none { it.equals(clean, ignoreCase = true) }) {
            add(clean)
        }
    }

    private fun cleanEventTarget(raw: String): String {
        return raw
            .trim()
            .removePrefix("Amanecer:")
            .substringAfter(":")
            .trim()
            .removeSuffix(".")
            .trim()
    }

    private fun canonicalEventTarget(session: GameSession, raw: String): String {
        val clean = cleanEventTarget(raw)
        val normalized = GameplayTextMarkers.normalize(clean)
        return session.players
            .firstOrNull { GameplayTextMarkers.normalize(it.name) == normalized }
            ?.name
            ?: clean
    }

    private fun specialVictorySummary(sentence: String): String {
        val clean = sentence.trim().removeSuffix(".")
        return if ("bufon" in clean.lowercase()) {
            "victoria especial del Bufón"
        } else {
            clean
        }
    }

    fun companionCardMetrics(
        totalPlayers: Int,
        availableHeightDp: Int = 376,
        availableWidthDp: Int? = null
    ): CompanionCardMetrics {
        val playersPerSide = ((totalPlayers.coerceAtLeast(2) - 1) + 1) / 2
        val scrollEnabled = totalPlayers >= 13
        val base = when (playersPerSide) {
            0, 1, 2 -> CompanionCardMetrics(
                columnWidthDp = 112,
                minCardWidthDp = 104,
                itemHeightDp = 106,
                itemGapDp = 4,
                avatarSizeDp = 22,
                cardWidthDp = 54,
                cardHeightDp = 86,
                nameHeightDp = 18,
                nameTextSp = 10f,
                scrollEnabled = false
            )
            3 -> CompanionCardMetrics(
                columnWidthDp = 104,
                minCardWidthDp = 96,
                itemHeightDp = 89,
                itemGapDp = 3,
                avatarSizeDp = 20,
                cardWidthDp = 45,
                cardHeightDp = 72,
                nameHeightDp = 17,
                nameTextSp = 9.5f,
                scrollEnabled = false
            )
            4 -> CompanionCardMetrics(
                columnWidthDp = 94,
                minCardWidthDp = 86,
                itemHeightDp = 74,
                itemGapDp = 2,
                avatarSizeDp = 18,
                cardWidthDp = 36,
                cardHeightDp = 58,
                nameHeightDp = 16,
                nameTextSp = 9f,
                scrollEnabled = false
            )
            5 -> CompanionCardMetrics(
                columnWidthDp = 86,
                minCardWidthDp = 78,
                itemHeightDp = 65,
                itemGapDp = 2,
                avatarSizeDp = 16,
                cardWidthDp = 31,
                cardHeightDp = 50,
                nameHeightDp = 15,
                nameTextSp = 8.5f,
                scrollEnabled = false
            )
            else -> CompanionCardMetrics(
                columnWidthDp = SIDE_COLUMN_WIDTH_DP,
                minCardWidthDp = 70,
                itemHeightDp = 62,
                itemGapDp = 2,
                avatarSizeDp = 15,
                cardWidthDp = 29,
                cardHeightDp = 46,
                nameHeightDp = 14,
                nameTextSp = 8f,
                scrollEnabled = scrollEnabled
            )
        }
        var fitted = base

        if (availableHeightDp > 0) {
            val usableHeight = (availableHeightDp - base.itemGapDp * (playersPerSide - 1))
                .coerceAtLeast(playersPerSide)
            val idealItemHeight = usableHeight / playersPerSide
            if (idealItemHeight > base.itemHeightDp && availableWidthDp != null) {
                val grownItemHeight = idealItemHeight.coerceAtMost(MAX_COMPANION_ITEM_HEIGHT_DP)
                val fixedContentHeight = base.nameHeightDp
                val grownCardHeight = (grownItemHeight - fixedContentHeight)
                    .coerceAtLeast(base.cardHeightDp)
                val growthScale = grownCardHeight.toFloat() / base.cardHeightDp
                fitted = fitted.scaledBy(
                    scale = growthScale,
                    overrideItemHeightDp = grownItemHeight,
                    allowGrow = true
                )
            } else if (idealItemHeight < base.itemHeightDp && !scrollEnabled) {
                val fittedItemHeight = idealItemHeight
                val fixedContentHeight = base.nameHeightDp
                val fittedCardHeight = (fittedItemHeight - fixedContentHeight).coerceAtLeast(24)
                val fittedCardWidth = (
                    base.cardWidthDp.toFloat() * fittedCardHeight / base.cardHeightDp
                    ).toInt().coerceAtLeast(22)
                fitted = base.copy(
                    itemHeightDp = fittedItemHeight,
                    avatarSizeDp = minOf(
                        base.avatarSizeDp,
                        (fittedCardWidth * 0.42f).toInt().coerceAtLeast(12)
                    ),
                    cardWidthDp = fittedCardWidth,
                    cardHeightDp = fittedCardHeight,
                    nameTextSp = minOf(
                        base.nameTextSp,
                        if (fittedItemHeight < 70) 6.5f else base.nameTextSp
                    )
                )
            }
        }

        if (availableWidthDp != null && availableWidthDp > 0 && availableWidthDp < fitted.columnWidthDp) {
            fitted = fitted.fitToAvailableWidth(availableWidthDp)
        }

        if (availableWidthDp != null && availableHeightDp > 0 && !scrollEnabled && playersPerSide <= 3) {
            fitted = fitted.withPortraitSpacing(playersPerSide, availableHeightDp)
        }

        return fitted
    }

    private const val TIE_CARD_MIN_WIDTH_DP = 78
    private const val TIE_CARD_HORIZONTAL_MARGIN_DP = 10

    fun tieVoteGridMetrics(
        candidateCount: Int,
        maxColumns: Int = 4,
        availableWidthDp: Int = 0
    ): TieVoteGridMetrics {
        val safeCount = candidateCount.coerceAtLeast(0)
        val safeMaxColumns = maxColumns.coerceAtLeast(1)
        val columns = safeCount.coerceIn(1, safeMaxColumns)
        val rows = ((safeCount + columns - 1) / columns).coerceAtLeast(1)
        val preferredWidth = when {
            safeCount <= 2 -> 94
            safeCount == 3 -> 96
            safeCount >= 5 -> 84
            else -> 90
        }
        val cardHeight = when {
            safeCount <= 2 -> 134
            safeCount >= 5 -> 126
            else -> 130
        }
        // El ancho de carta se ajusta para que `columns` cartas (con sus margenes) entren en el
        // ancho disponible del panel; asi la ventana no se corta en pantallas angostas. Nunca
        // crece mas alla del ancho preferido. Con availableWidthDp <= 0 mantiene el ancho viejo.
        val cardWidth = if (availableWidthDp > 0) {
            ((availableWidthDp / columns) - TIE_CARD_HORIZONTAL_MARGIN_DP)
                .coerceIn(TIE_CARD_MIN_WIDTH_DP, preferredWidth)
        } else {
            preferredWidth
        }
        return TieVoteGridMetrics(
            columns = columns,
            rows = rows,
            cardWidthDp = cardWidth,
            cardHeightDp = cardHeight,
            scrollEnabled = rows > 2
        )
    }

    private fun CompanionCardMetrics.scaledBy(
        scale: Float,
        overrideItemHeightDp: Int? = null,
        allowGrow: Boolean = false
    ): CompanionCardMetrics {
        val safeScale = if (allowGrow) {
            scale.coerceIn(1f, MAX_COMPANION_CARD_GROWTH_SCALE)
        } else {
            scale.coerceIn(0.35f, 1f)
        }
        return copy(
            columnWidthDp = (columnWidthDp * safeScale).toInt().coerceAtLeast(48),
            minCardWidthDp = (minCardWidthDp * safeScale).toInt().coerceAtLeast(40),
            itemHeightDp = overrideItemHeightDp
                ?: (itemHeightDp * safeScale).toInt().coerceAtLeast(40),
            avatarSizeDp = (avatarSizeDp * safeScale).toInt().coerceAtLeast(12),
            cardWidthDp = (cardWidthDp * safeScale).toInt().coerceAtLeast(20),
            cardHeightDp = (cardHeightDp * safeScale).toInt().coerceAtLeast(32),
            nameTextSp = (nameTextSp * safeScale).coerceIn(6.5f, 15f)
        )
    }

    private fun CompanionCardMetrics.fitToAvailableWidth(availableWidthDp: Int): CompanionCardMetrics {
        val targetColumnWidth = availableWidthDp.coerceAtLeast(48)
        val maxCardWidth = (targetColumnWidth - 8).coerceAtLeast(20)
        val fittedCardWidth = cardWidthDp.coerceAtMost(maxCardWidth)
        val cardScale = fittedCardWidth.toFloat() / cardWidthDp.coerceAtLeast(1)
        return copy(
            columnWidthDp = targetColumnWidth,
            minCardWidthDp = targetColumnWidth,
            cardWidthDp = fittedCardWidth,
            cardHeightDp = (cardHeightDp * cardScale).toInt().coerceAtLeast(32),
            avatarSizeDp = minOf(
                avatarSizeDp,
                (fittedCardWidth * 0.52f).toInt().coerceAtLeast(12)
            ),
            nameTextSp = (nameTextSp * cardScale).coerceIn(6.5f, nameTextSp)
        )
    }

    private fun CompanionCardMetrics.withPortraitSpacing(
        playersPerSide: Int,
        availableHeightDp: Int
    ): CompanionCardMetrics {
        if (playersPerSide <= 1) return this
        val freeHeight = availableHeightDp - itemHeightDp * playersPerSide
        if (freeHeight <= itemGapDp) return this
        val targetGap = when (playersPerSide) {
            2 -> (freeHeight * 0.08f).toInt().coerceIn(itemGapDp, 28)
            else -> (freeHeight * 0.05f).toInt().coerceIn(itemGapDp, 16)
        }
        return copy(itemGapDp = targetGap)
    }

    fun eventTypeFor(message: String, phase: GamePhase): PublicEventType {
        val text = GameplayTextMarkers.normalize(message)
        return when {
            (text.contains("murio") || text.contains("muerte")) && !text.contains("no murio") ->
                PublicEventType.DEATH
            text.contains("votacion") || text.contains("votar") || text.contains("expuls") ||
                phase == GamePhase.VOTACION ||
                phase == GamePhase.RECUENTO_VOTOS ||
                phase == GamePhase.DESEMPATE_VOTACION ||
                phase == GamePhase.RESULTADO ->
                PublicEventType.VOTING
            text.contains("debat") || phase == GamePhase.DIA_DEBATE ->
                PublicEventType.DISCUSSION
            else ->
                PublicEventType.PHASE_START
        }
    }
}
