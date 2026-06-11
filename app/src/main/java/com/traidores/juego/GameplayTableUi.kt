package com.traidores.juego

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
    val humanWon: Boolean
)

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
            return GameWinnerPresentation(emptyList(), humanWon = false)
        }

        val winningPlayers = session.players.filter { player ->
            when {
                player.role?.key == "desertor" -> session.desertorTeam == session.winner
                session.winner == GameRules.TOWN_WINNER ->
                    player.role?.team == GameRules.TOWN_WINNER
                session.winner == GameRules.TRAITOR_WINNER ->
                    player.role?.key in GameRules.traitorRoleKeys
                else -> false
            }
        }
        return GameWinnerPresentation(
            winningPlayers = winningPlayers,
            humanWon = winningPlayers.any { it.isHuman }
        )
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
                cardWidthDp = 72,
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
                cardWidthDp = 60,
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
                cardWidthDp = 48,
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
                cardWidthDp = 42,
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
                cardWidthDp = 38,
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
