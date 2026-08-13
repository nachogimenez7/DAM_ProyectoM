package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchReturnGateTest {
    @Test
    fun fallbackDeadlineIsSharedAndNotRecreated() {
        val created = OnlineMatchReturnGate.initialDeadline(0L, 1_000L)
        val kept = OnlineMatchReturnGate.initialDeadline(created, 8_000L)

        assertEquals(46_000L, created)
        assertEquals(created, kept)
    }

    @Test
    fun remainingTimeNeverBecomesNegative() {
        assertEquals(3_000L, OnlineMatchReturnGate.remainingMillis(5_000L, 2_000L))
        assertEquals(0L, OnlineMatchReturnGate.remainingMillis(5_000L, 8_000L))
    }

    @Test
    fun waitsForEveryExpectedPlayerWhilePresenceIsStillLoading() {
        val progress = OnlineMatchReturnGate.progress(
            expectedPlayerIds = listOf("host", "guest-a", "guest-b"),
            connectedPlayerIds = listOf("host"),
            acknowledgedPlayerIds = listOf("host"),
            presenceKnown = false
        )

        assertEquals(1, progress.readyCount)
        assertEquals(3, progress.totalCount)
        assertEquals(3, progress.requiredCount)
        assertFalse(progress.allRequiredReady)
    }

    @Test
    fun disconnectedPlayerDoesNotBlockCoordinatedReturn() {
        val progress = OnlineMatchReturnGate.progress(
            expectedPlayerIds = listOf("host", "guest-a", "guest-b"),
            connectedPlayerIds = listOf("host", "guest-a"),
            acknowledgedPlayerIds = listOf("host", "guest-a"),
            presenceKnown = true
        )

        assertEquals(2, progress.readyCount)
        assertEquals(3, progress.totalCount)
        assertEquals(2, progress.requiredCount)
        assertTrue(progress.allRequiredReady)
    }

    @Test
    fun connectedPlayerWithoutConfirmationKeepsEveryoneOnResults() {
        val progress = OnlineMatchReturnGate.progress(
            expectedPlayerIds = listOf("host", "guest-a", "guest-b"),
            connectedPlayerIds = listOf("host", "guest-a", "guest-b"),
            acknowledgedPlayerIds = listOf("host", "guest-a"),
            presenceKnown = true
        )

        assertFalse(progress.allRequiredReady)
    }
}
