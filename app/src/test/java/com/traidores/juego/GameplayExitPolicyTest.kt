package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayExitPolicyTest {
    @Test
    fun activeOnlineMatchBlocksBackExit() {
        assertEquals(
            GameplayExitAction.BLOCK_ONLINE_EXIT,
            GameplayExitPolicy.gameplayBackAction(
                isOnlineGameplay = true,
                matchFinished = false
            )
        )
    }

    @Test
    fun activeLocalMatchRequiresConfirmation() {
        assertEquals(
            GameplayExitAction.CONFIRM_LOCAL_EXIT,
            GameplayExitPolicy.gameplayBackAction(
                isOnlineGameplay = false,
                matchFinished = false
            )
        )
    }

    @Test
    fun finishedMatchCanReturnToLobby() {
        assertEquals(
            GameplayExitAction.RETURN_TO_LOBBY,
            GameplayExitPolicy.gameplayBackAction(
                isOnlineGameplay = true,
                matchFinished = true
            )
        )
    }

    @Test
    fun assigningRolesOffersSafeOnlineExit() {
        assertEquals(
            GameplayExitAction.RETURN_TO_LOBBY,
            GameplayExitPolicy.assigningBackAction(isOnlineGameplay = true)
        )
        assertEquals(
            GameplayExitAction.CONFIRM_LOCAL_EXIT,
            GameplayExitPolicy.assigningBackAction(isOnlineGameplay = false)
        )
    }

    @Test
    fun staleLobbyRecoversOnlyAnActiveMatchThatWasJustLeft() {
        assertTrue(
            GameplayExitPolicy.shouldRecoverGameplayFromLobby(
                roomState = OnlineRoomFirestore.STATE_IN_GAME,
                hasLiveMatch = true,
                returnedFromGameplay = true
            )
        )
        assertFalse(
            GameplayExitPolicy.shouldRecoverGameplayFromLobby(
                roomState = OnlineRoomFirestore.STATE_FINISHED,
                hasLiveMatch = false,
                returnedFromGameplay = true
            )
        )
        assertFalse(
            GameplayExitPolicy.shouldRecoverGameplayFromLobby(
                roomState = OnlineRoomFirestore.STATE_IN_GAME,
                hasLiveMatch = true,
                returnedFromGameplay = false
            )
        )
    }

    @Test
    fun hostCanCancelAStartedMatchThatIsStillBlockedInLobby() {
        assertTrue(
            GameplayExitPolicy.shouldOfferStartedMatchCancellation(
                roomState = OnlineRoomFirestore.STATE_IN_GAME,
                hasLiveMatch = true,
                isHost = true,
                returnedFromGameplay = false
            )
        )
        assertFalse(
            GameplayExitPolicy.shouldOfferStartedMatchCancellation(
                roomState = OnlineRoomFirestore.STATE_IN_GAME,
                hasLiveMatch = true,
                isHost = false,
                returnedFromGameplay = false
            )
        )
        assertFalse(
            GameplayExitPolicy.shouldOfferStartedMatchCancellation(
                roomState = OnlineRoomFirestore.STATE_IN_GAME,
                hasLiveMatch = true,
                isHost = true,
                returnedFromGameplay = true
            )
        )
    }
}
