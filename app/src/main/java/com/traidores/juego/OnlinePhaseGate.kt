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

    fun evaluateIncomingState(
        isHost: Boolean,
        currentPhaseIndex: Int,
        incomingPhaseIndex: Int,
        incomingStateKey: String,
        lastAppliedStateKey: String
    ): OnlinePhaseDecision {
        return when {
            isHost -> OnlinePhaseDecision.HOST_IGNORES
            incomingPhaseIndex < currentPhaseIndex -> OnlinePhaseDecision.IGNORE_OLD
            incomingStateKey == lastAppliedStateKey -> OnlinePhaseDecision.IGNORE_DUPLICATE
            else -> OnlinePhaseDecision.APPLY
        }
    }
}
