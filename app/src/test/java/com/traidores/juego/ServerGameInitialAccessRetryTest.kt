package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class ServerGameInitialAccessRetryTest {
    @Test fun `initial publication retries have a finite budget`() {
        val retry = ServerGameInitialAccessRetry()
        repeat(20) { assertEquals(1500L, retry.nextDelay()) }
        assertNull(retry.nextDelay())
        retry.resetAttempts()
        assertEquals(1500L, retry.nextDelay())
    }
    @Test fun `membership loss after an authorized snapshot is never hidden as startup latency`() {
        val retry = ServerGameInitialAccessRetry()
        retry.nextDelay(); retry.confirmed()
        assertNull(retry.nextDelay())
        retry.resetAttempts()
        assertNull(retry.nextDelay())
    }
}
