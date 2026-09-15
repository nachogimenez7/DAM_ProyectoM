package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRecoveryGateTest {

    @Test
    fun initialRecoveryReplaysTheRequiredPresentation() {
        assertEquals(
            true,
            OnlineRecoveryGate.shouldShowInitialPresentation(GamePhase.REPARTO, 0)
        )
        assertEquals(
            false,
            OnlineRecoveryGate.shouldShowInitialPresentation(GamePhase.NOCHE_ASESINO, 1)
        )
        assertEquals(
            false,
            OnlineRecoveryGate.shouldShowInitialPresentation(GamePhase.REPARTO, 2)
        )
    }

    @Test
    fun waitingRoomRecoversToLobby() {
        assertEquals(
            OnlineRecoveryTarget.LOBBY,
            OnlineRecoveryGate.targetForRoomState(OnlineRoomFirestore.STATE_WAITING)
        )
    }

    @Test
    fun inGameRoomRecoversToGameplay() {
        assertEquals(
            OnlineRecoveryTarget.GAMEPLAY,
            OnlineRecoveryGate.targetForRoomState(OnlineRoomFirestore.STATE_IN_GAME)
        )
    }

    @Test
    fun finishedRoomRecoversToLobbyButUnknownRoomClearsRecovery() {
        assertEquals(
            OnlineRecoveryTarget.LOBBY,
            OnlineRecoveryGate.targetForRoomState(OnlineRoomFirestore.STATE_FINISHED)
        )
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRoomState("rota")
        )
    }

    @Test
    fun activeMemberCanRecoverFinishedRoomForRematch() {
        assertEquals(
            OnlineRecoveryTarget.LOBBY,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_FINISHED,
                playerExists = true,
                activeInMatch = true,
                inGameEntryReleased = true
            )
        )
    }

    @Test
    fun activeRoomWithoutCurrentPlayerClearsRecovery() {
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_WAITING,
                playerExists = false,
                activeInMatch = true,
                inGameEntryReleased = true
            )
        )
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_IN_GAME,
                playerExists = true,
                activeInMatch = false,
                inGameEntryReleased = true
            )
        )
    }

    @Test
    fun activeMemberCanRecoverActiveRoom() {
        assertEquals(
            OnlineRecoveryTarget.LOBBY,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_WAITING,
                playerExists = true,
                activeInMatch = true,
                inGameEntryReleased = false
            )
        )
        assertEquals(
            OnlineRecoveryTarget.GAMEPLAY,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_IN_GAME,
                playerExists = true,
                activeInMatch = true,
                inGameEntryReleased = true
            )
        )
    }

    @Test
    fun failedInGameBarrierIsNotOfferedAsRecoverableGameplay() {
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRecovery(
                state = OnlineRoomFirestore.STATE_IN_GAME,
                playerExists = true,
                activeInMatch = true,
                inGameEntryReleased = false
            )
        )
    }
}
