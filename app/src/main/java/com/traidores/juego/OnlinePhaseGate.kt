package com.traidores.juego

enum class OnlinePhaseDecision {
    APPLY,
    IGNORE_OLD,
    IGNORE_DUPLICATE,
    HOST_IGNORES
}

object OnlinePhaseGate {
    fun canAdvanceLocally(isOnline: Boolean, isHost: Boolean): Boolean {
        return !isOnline || isHost
    }

    fun canPublishAuthoritativeState(
        isOnline: Boolean,
        isHost: Boolean,
        isStartupPhase: Boolean
    ): Boolean {
        return isOnline && isHost && !isStartupPhase
    }

    fun canAutoContinueVoteResult(isOnline: Boolean): Boolean {
        return !isOnline
    }

    fun evaluateIncomingState(
        isHost: Boolean,
        currentPhaseIndex: Int,
        incomingPhaseIndex: Int,
        incomingStateKey: String,
        lastAppliedStateKey: String
    ): OnlinePhaseDecision {
        return when {
            isHost -> OnlinePhaseDecision.HOST_IGNORES
            // El primer snapshot autoritativo siempre debe imponerse a una Session restaurada
            // por Android. Esa copia local puede pertenecer a un instante posterior o incluso
            // a la partida anterior y nunca debe dejar al invitado congelado en esa pantalla.
            lastAppliedStateKey.isBlank() -> OnlinePhaseDecision.APPLY
            incomingPhaseIndex < currentPhaseIndex -> OnlinePhaseDecision.IGNORE_OLD
            incomingStateKey == lastAppliedStateKey -> OnlinePhaseDecision.IGNORE_DUPLICATE
            else -> OnlinePhaseDecision.APPLY
        }
    }
}
