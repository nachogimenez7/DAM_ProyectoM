package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineOwnedRoomCleanupPolicyTest {
    @Test
    fun creatorCanReplaceOnlyTheirOwnWaitingRoom() {
        assertTrue(candidate("esperando", "creator", "creator", "creator"))
        assertFalse(candidate("en_juego", "creator", "creator", "creator"))
        assertFalse(candidate("esperando", "other", "other", "creator"))
        assertFalse(candidate("esperando", "creator", "new-host", "creator"))
        assertFalse(candidate("esperando", "creator", "creator", ""))
    }

    private fun candidate(state: String, stable: String, active: String, requester: String) =
        OnlineOwnedRoomCleanupPolicy.canReplaceWithNewRoom(state, stable, active, requester)
}
