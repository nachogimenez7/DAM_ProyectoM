package com.traidores.juego

enum class OnlineRecoveryTarget {
    LOBBY,
    GAMEPLAY,
    CLEAR
}

object OnlineRecoveryGate {
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
        activeInMatch: Boolean
    ): OnlineRecoveryTarget {
        if (!playerExists || !activeInMatch) return OnlineRecoveryTarget.CLEAR
        return targetForRoomState(state)
    }
}
