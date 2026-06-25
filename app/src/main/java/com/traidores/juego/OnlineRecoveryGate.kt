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
}
