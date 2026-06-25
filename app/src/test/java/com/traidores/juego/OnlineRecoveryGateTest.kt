package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRecoveryGateTest {

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
    fun finishedOrUnknownRoomClearsRecovery() {
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRoomState(OnlineRoomFirestore.STATE_FINISHED)
        )
        assertEquals(
            OnlineRecoveryTarget.CLEAR,
            OnlineRecoveryGate.targetForRoomState("rota")
        )
    }
}
