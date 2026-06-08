package com.traidores.juego

data class CompanionCardMetrics(
    val columnWidthDp: Int,
    val minCardWidthDp: Int,
    val itemHeightDp: Int,
    val avatarSizeDp: Int,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val nameTextSp: Float,
    val actionTextSp: Float,
    val actionHeightDp: Int
)

enum class PublicEventType(val colorHex: String) {
    DEATH("#a83232"),
    VOTING("#d4a24e"),
    DISCUSSION("#4a7fb5"),
    PHASE_START("#5a8a3c")
}

object GameplayTableUi {

    const val SIDE_COLUMN_WIDTH_DP = 76

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

    fun canHumanMedicSelfProtect(session: GameSession): Boolean {
        if (session.phase != GamePhase.NOCHE_MEDICO) return false
        val human = GameEngine.humanPlayer(session)
        return GameEngine.isHumanRoleTurn(session, "medico") &&
            GameEngine.canActOnTarget(session, human.name)
    }

    fun companionCardMetrics(totalPlayers: Int): CompanionCardMetrics {
        return when {
            totalPlayers <= 8 -> CompanionCardMetrics(
                columnWidthDp = SIDE_COLUMN_WIDTH_DP,
                minCardWidthDp = 64,
                itemHeightDp = 82,
                avatarSizeDp = 18,
                cardWidthDp = 44,
                cardHeightDp = 52,
                nameTextSp = 8.5f,
                actionTextSp = 7f,
                actionHeightDp = 16
            )
            totalPlayers <= 12 -> CompanionCardMetrics(
                columnWidthDp = SIDE_COLUMN_WIDTH_DP,
                minCardWidthDp = 60,
                itemHeightDp = 74,
                avatarSizeDp = 16,
                cardWidthDp = 40,
                cardHeightDp = 46,
                nameTextSp = 7.5f,
                actionTextSp = 6.5f,
                actionHeightDp = 15
            )
            else -> CompanionCardMetrics(
                columnWidthDp = SIDE_COLUMN_WIDTH_DP,
                minCardWidthDp = 56,
                itemHeightDp = 66,
                avatarSizeDp = 14,
                cardWidthDp = 36,
                cardHeightDp = 40,
                nameTextSp = 7f,
                actionTextSp = 6f,
                actionHeightDp = 14
            )
        }
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
