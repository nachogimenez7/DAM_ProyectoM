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

enum class PublicEventType(val colorHex: String) {
    DEATH("#a83232"),
    VOTING("#d4a24e"),
    DISCUSSION("#4a7fb5"),
    PHASE_START("#5a8a3c")
}

enum class GameplayActionTone(val colorHex: String, val darkText: Boolean) {
    KILL("#8F2633", false),
    SAVE("#5A8A3C", false),
    INVESTIGATE("#4A7FB5", false),
    SILENCE("#9A6A32", false),
    DECIDE("#D4A24E", true),
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
    val specialVictories: List<GameSpecialVictory> = emptyList()
)

data class GameSummaryPresentation(
    val roundsPlayed: Int,
    val durationLabel: String,
    val survivors: Int,
    val eliminated: Int,
    val eliminatedPlayers: List<String>,
    val humanHighlight: String,
    val daySummaries: List<String>
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

    fun splitCompanions(players: List<GamePlayer>): Pair<List<GamePlayer>, List<GamePlayer>> {
        val companions = players.filterNot { it.isHuman }
        val leftCount = companions.size / 2
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
            phase == GamePhase.NOCHE_MEDICO
    }

    fun transitionSpec(session: GameSession): GameplayTransitionSpec {
        val period = if (isNightPhase(session.phase)) GameplayPeriod.NIGHT else GameplayPeriod.DAY
        val visualNumber = when {
            period == GameplayPeriod.NIGHT -> session.round
            session.phase == GamePhase.REPARTO -> 1
            else -> session.round + 1
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
            ?.let { if (it == "CONTRAPUNTO") "SENALAR" else it }
    }

    fun actionToneFor(label: String): GameplayActionTone {
        return when (label.uppercase()) {
            "MATAR" -> GameplayActionTone.KILL
            "SALVAR", "SALVARME" -> GameplayActionTone.SAVE
            "INVESTIGAR" -> GameplayActionTone.INVESTIGATE
            "SILENCIAR" -> GameplayActionTone.SILENCE
            "VOTAR",
            "DECIDIR",
            "CONTRAPUNTO",
            "SENALAR",
            "REVELARME",
            "ELEGIR BANDO",
            "REVISAR BANDO" -> GameplayActionTone.DECIDE
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
                title = "VICTIMA ELEGIDA",
                message = "Elegiste a $target. El resultado se anunciara al amanecer.",
                target = target,
                tone = GameplayActionTone.KILL
            )
            GamePhase.NOCHE_MERCENARIO -> privateFeedback(
                title = "SILENCIO REGISTRADO",
                message = "$target no podra hablar ni votar durante el dia.",
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
                title = "PROTECCION REGISTRADA",
                message = if (target == GameEngine.humanPlayer(before).name) {
                    "Te protegiste durante esta noche."
                } else {
                    "Protegiste a $target durante esta noche."
                },
                target = target,
                tone = GameplayActionTone.SAVE
            )
            GamePhase.DIA_DEBATE -> actionConfirmation(
                title = "CONTRAPUNTO",
                message = "Elegiste a $target.",
                target = target
            )
            GamePhase.CONTRAPUNTO -> actionConfirmation(
                title = "SENALAMIENTO",
                message = "Senalaste a $target.",
                target = target
            )
            GamePhase.VOTACION -> actionConfirmation(
                title = "VOTO REGISTRADO",
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
        target: String
    ): GameplayFeedbackSpec {
        return GameplayFeedbackSpec(
            type = GameplayFeedbackType.ACTION_CONFIRMATION,
            title = title,
            message = message,
            target = target,
            tone = GameplayActionTone.DECIDE,
            durationMs = 1200L
        )
    }

    fun publicEvents(history: List<String>, current: String, fallback: String): List<String> {
        val events = mutableListOf<String>()
        (history + current).forEach { message ->
            val clean = message.trim()
            if (clean.isNotBlank() && events.lastOrNull() != clean) {
                events += clean
            }
        }
        if (events.isEmpty() && fallback.isNotBlank()) events += fallback.trim()
        return events
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

    fun newlyKilledAtDawn(
        session: GameSession,
        knownDeadPlayers: Set<String>
    ): List<GamePlayer> {
        val announcement = session.publicAnnouncement.lowercase()
        return session.players.filter { player ->
            !player.alive &&
                player.name !in knownDeadPlayers &&
                announcement.contains("amanecer: murio ${player.name.lowercase()}")
        }
    }

    fun newlySilencedAtDawn(
        session: GameSession,
        knownMutedPlayers: Set<String>
    ): List<GamePlayer> {
        val announcement = session.publicAnnouncement.lowercase()
        return session.players.filter { player ->
            player.alive &&
                player.muted &&
                player.name !in knownMutedPlayers &&
                announcement.contains(
                    "${player.name.lowercase()} no puede hablar ni votar hoy"
                )
        }
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
        val winningPlayers = (factionWinningPlayers + specialWinningPlayers).distinctBy { it.name }
        return GameWinnerPresentation(
            winningPlayers = winningPlayers,
            humanWon = winningPlayers.any { it.isHuman },
            specialVictories = session.specialVictories,
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
            daySummaries = daySummaries(session)
        )
    }

    private fun daySummaries(session: GameSession): List<String> {
        data class DayOutcome(
            var killed: String? = null,
            var silenced: String? = null
        )

        val outcomes = linkedMapOf<Int, DayOutcome>()
        var currentRound = 1
        val nightPattern = Regex("""Noche\s+(\d+)""", RegexOption.IGNORE_CASE)
        val dayPattern = Regex("""Dia\s+(\d+)""", RegexOption.IGNORE_CASE)
        val killedPattern = Regex("""murio\s+([^.\s]+)""", RegexOption.IGNORE_CASE)
        val silencedPattern = Regex(
            """([^.]+?)\s+no puede hablar ni votar hoy""",
            RegexOption.IGNORE_CASE
        )

        session.godHistory.forEach { message ->
            nightPattern.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                currentRound = it
            }
            val explicitDay = dayPattern.find(message)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            val round = explicitDay ?: currentRound
            if (
                message.contains("Amanecer:", ignoreCase = true) ||
                message.contains("no puede hablar ni votar hoy", ignoreCase = true)
            ) {
                val outcome = outcomes.getOrPut(round) { DayOutcome() }
                if (!message.contains("no murio nadie", ignoreCase = true)) {
                    killedPattern.find(message)?.groupValues?.getOrNull(1)?.let {
                        outcome.killed = it
                    }
                }
                silencedPattern.find(message)?.groupValues?.getOrNull(1)?.let {
                    outcome.silenced = it.trim()
                }
            }
        }

        return (1..session.round.coerceAtLeast(1)).map { round ->
            val outcome = outcomes[round]
            val deathText = outcome?.killed?.let { "murió $it" } ?: "no murió nadie"
            val silenceText = outcome?.silenced?.let { "se silenció a $it" }
                ?: "nadie fue silenciado"
            "Día $round: $deathText y $silenceText."
        }
    }

    fun companionCardMetrics(
        totalPlayers: Int,
        availableHeightDp: Int = 376
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
        if (scrollEnabled || availableHeightDp <= 0) return base

        val usableHeight = (availableHeightDp - base.itemGapDp * (playersPerSide - 1))
            .coerceAtLeast(playersPerSide)
        val fittedItemHeight = minOf(base.itemHeightDp, usableHeight / playersPerSide)
        if (fittedItemHeight >= base.itemHeightDp) return base

        val fixedContentHeight = base.nameHeightDp
        val fittedCardHeight = (fittedItemHeight - fixedContentHeight).coerceAtLeast(24)
        val fittedCardWidth = (
            base.cardWidthDp.toFloat() * fittedCardHeight / base.cardHeightDp
            ).toInt().coerceAtLeast(22)
        return base.copy(
            itemHeightDp = fittedItemHeight,
            avatarSizeDp = minOf(base.avatarSizeDp, (fittedCardWidth * 0.42f).toInt().coerceAtLeast(12)),
            cardWidthDp = fittedCardWidth,
            cardHeightDp = fittedCardHeight,
            nameTextSp = minOf(base.nameTextSp, if (fittedItemHeight < 70) 6.5f else base.nameTextSp)
        )
    }

    fun eventTypeFor(message: String, phase: GamePhase): PublicEventType {
        val text = message.lowercase()
        return when {
            (text.contains("murio") || text.contains("muerte")) && !text.contains("no murio") ->
                PublicEventType.DEATH
            text.contains("votacion") || text.contains("votar") || text.contains("expuls") ||
                phase == GamePhase.VOTACION || phase == GamePhase.RESULTADO ->
                PublicEventType.VOTING
            text.contains("debat") || phase == GamePhase.DIA_DEBATE ->
                PublicEventType.DISCUSSION
            else ->
                PublicEventType.PHASE_START
        }
    }
}
