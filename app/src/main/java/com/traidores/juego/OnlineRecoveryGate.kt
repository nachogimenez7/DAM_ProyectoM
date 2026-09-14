package com.traidores.juego

enum class OnlineRecoveryTarget {
    LOBBY,
    GAMEPLAY,
    CLEAR
}

object OnlineRecoveryGate {
    fun shouldShowInitialPresentation(phase: GamePhase, phaseIndex: Int): Boolean {
        return phase == GamePhase.REPARTO && phaseIndex == 0
    }

    fun targetForRoomState(state: String): OnlineRecoveryTarget {
        return when (state) {
            OnlineRoomFirestore.STATE_WAITING -> OnlineRecoveryTarget.LOBBY
            OnlineRoomFirestore.STATE_IN_GAME -> OnlineRecoveryTarget.GAMEPLAY
            else -> OnlineRecoveryTarget.CLEAR
        }
    }

    fun targetForRecovery(
        state: String,
        playerExists: Boolean,
        activeInMatch: Boolean,
        inGameEntryReleased: Boolean
    ): OnlineRecoveryTarget {
        if (!playerExists || !activeInMatch) return OnlineRecoveryTarget.CLEAR
        if (state == OnlineRoomFirestore.STATE_IN_GAME && !inGameEntryReleased) {
            return OnlineRecoveryTarget.CLEAR
        }
        return targetForRoomState(state)
    }
}
