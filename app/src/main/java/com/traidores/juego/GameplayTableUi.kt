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
    val actionTextSp: Float,
    val actionHeightDp: Int,
    val actionWidthDp: Int,
    val scrollEnabled: Boolean
)

enum class PublicEventType(val colorHex: String) {
    DEATH("#a83232"),
    VOTING("#d4a24e"),
    DISCUSSION("#4a7fb5"),
    PHASE_START("#5a8a3c")
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
                itemHeightDp = 136,
                itemGapDp = 4,
                avatarSizeDp = 22,
                cardWidthDp = 72,
                cardHeightDp = 86,
                nameHeightDp = 18,
                nameTextSp = 10f,
                actionTextSp = 9f,
                actionHeightDp = 28,
                actionWidthDp = 104,
                scrollEnabled = false
            )
            3 -> CompanionCardMetrics(
                columnWidthDp = 104,
                minCardWidthDp = 96,
                itemHeightDp = 116,
                itemGapDp = 3,
                avatarSizeDp = 20,
                cardWidthDp = 60,
                cardHeightDp = 72,
                nameHeightDp = 17,
                nameTextSp = 9.5f,
                actionTextSp = 8f,
                actionHeightDp = 24,
                actionWidthDp = 96,
                scrollEnabled = false
            )
            4 -> CompanionCardMetrics(
                columnWidthDp = 94,
                minCardWidthDp = 86,
                itemHeightDp = 98,
                itemGapDp = 2,
                avatarSizeDp = 18,
                cardWidthDp = 48,
                cardHeightDp = 58,
                nameHeightDp = 16,
                nameTextSp = 9f,
                actionTextSp = 7.5f,
                actionHeightDp = 22,
                actionWidthDp = 86,
                scrollEnabled = false
            )
            5 -> CompanionCardMetrics(
                columnWidthDp = 86,
                minCardWidthDp = 78,
                itemHeightDp = 89,
                itemGapDp = 2,
                avatarSizeDp = 16,
                cardWidthDp = 42,
                cardHeightDp = 50,
                nameHeightDp = 15,
                nameTextSp = 8.5f,
                actionTextSp = 7f,
                actionHeightDp = 20,
                actionWidthDp = 78,
                scrollEnabled = false
            )
            else -> CompanionCardMetrics(
                columnWidthDp = SIDE_COLUMN_WIDTH_DP,
                minCardWidthDp = 70,
                itemHeightDp = 84,
                itemGapDp = 2,
                avatarSizeDp = 15,
                cardWidthDp = 38,
                cardHeightDp = 46,
                nameHeightDp = 14,
                nameTextSp = 8f,
                actionTextSp = 6.5f,
                actionHeightDp = 20,
                actionWidthDp = 70,
                scrollEnabled = scrollEnabled
            )
        }
        if (scrollEnabled || availableHeightDp <= 0) return base

        val usableHeight = (availableHeightDp - base.itemGapDp * (playersPerSide - 1))
            .coerceAtLeast(playersPerSide)
        val fittedItemHeight = minOf(base.itemHeightDp, usableHeight / playersPerSide)
        if (fittedItemHeight >= base.itemHeightDp) return base

        val fixedContentHeight = base.actionHeightDp + base.nameHeightDp
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
