package com.traidores.juego

internal object DirectVotePolicy {
    fun isEnabled(phase: GamePhase): Boolean {
        return phase == GamePhase.VOTACION || phase == GamePhase.DESEMPATE_VOTACION
    }

    fun select(targetName: String): String = targetName

    fun canSelect(currentTarget: String, targetName: String, confirmed: Boolean): Boolean {
        return !confirmed && currentTarget != targetName
    }

    fun shouldConfirm(currentTarget: String, targetName: String, confirmed: Boolean): Boolean {
        return !confirmed && currentTarget.isNotBlank() && currentTarget == targetName
    }

    fun timeoutTarget(
        phase: GamePhase,
        selectedTarget: String,
        confirmed: Boolean
    ): String {
        return selectedTarget.takeIf { confirmed && isEnabled(phase) }.orEmpty()
    }
}
