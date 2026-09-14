package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayPresentationTimingTest {
    @Test fun instantAnimatorCompletionStillLeavesTimeToRead() {
        assertEquals(3000L, GameplayPresentationTiming.remainingMs(1000, 1000, 3000))
        assertEquals(2900L, GameplayPresentationTiming.remainingMs(1000, 1100, 3000))
    }

    @Test fun slowFramesDoNotAddAnotherFullPresentationDelay() {
        assertEquals(0L, GameplayPresentationTiming.remainingMs(1000, 4000, 3000))
        assertEquals(0L, GameplayPresentationTiming.remainingMs(1000, 8000, 3000))
    }
}
