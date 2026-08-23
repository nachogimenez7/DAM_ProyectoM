package com.traidores.juego

enum class GameplayExitAction {
    RETURN_TO_LOBBY,
    BLOCK_ONLINE_EXIT,
    CONFIRM_LOCAL_EXIT
}

object GameplayExitPolicy {
    fun gameplayBackAction(
        isOnlineGameplay: Boolean,
        matchFinished: Boolean
    ): GameplayExitAction {
        return when {
            matchFinished -> GameplayExitAction.RETURN_TO_LOBBY
            isOnlineGameplay -> GameplayExitAction.BLOCK_ONLINE_EXIT
            else -> GameplayExitAction.CONFIRM_LOCAL_EXIT
        }
    }

    fun assigningBackAction(isOnlineGameplay: Boolean): GameplayExitAction {
        return if (isOnlineGameplay) {
            GameplayExitAction.RETURN_TO_LOBBY
        } else {
            GameplayExitAction.CONFIRM_LOCAL_EXIT
        }
    }

    fun shouldRecoverGameplayFromLobby(
        roomState: String,
        hasLiveMatch: Boolean,
        returnedFromGameplay: Boolean
    ): Boolean {
        return returnedFromGameplay &&
            roomState == OnlineRoomFirestore.STATE_IN_GAME &&
            hasLiveMatch
    }

    fun shouldOfferStartedMatchCancellation(
        roomState: String,
        hasLiveMatch: Boolean,
        isHost: Boolean,
        returnedFromGameplay: Boolean
    ): Boolean {
        return isHost &&
            !returnedFromGameplay &&
            roomState == OnlineRoomFirestore.STATE_IN_GAME &&
            hasLiveMatch
    }
}
