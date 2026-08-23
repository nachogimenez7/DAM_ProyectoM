package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStartQuorumTest {

    @Test
    fun threePlayerRoomsRecoverWithTwoWhileLargeRoomsKeepThreeQuarterQuorum() {
        assertEquals(2, OnlineStartQuorum.requiredPlayers(3))
        assertEquals(4, OnlineStartQuorum.requiredPlayers(5))
        assertEquals(5, OnlineStartQuorum.requiredPlayers(6))
        assertEquals(11, OnlineStartQuorum.requiredPlayers(14))
        assertEquals(12, OnlineStartQuorum.requiredPlayers(15))
    }

    @Test
    fun readyAndConnectedMustBothReachQuorum() {
        assertTrue(OnlineStartQuorum.isReached(3, readyPlayers = 2, connectedPlayers = 3))
        assertFalse(OnlineStartQuorum.isReached(3, readyPlayers = 1, connectedPlayers = 3))
        assertFalse(OnlineStartQuorum.isReached(3, readyPlayers = 2, connectedPlayers = 2))
        assertTrue(OnlineStartQuorum.isReached(14, readyPlayers = 11, connectedPlayers = 14))
        assertFalse(OnlineStartQuorum.isReached(14, readyPlayers = 10, connectedPlayers = 14))
        assertFalse(OnlineStartQuorum.isReached(14, readyPlayers = 11, connectedPlayers = 10))
    }
}
