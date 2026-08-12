package com.traidores.juego

import org.junit.Assert.assertEquals
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
}
