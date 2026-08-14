package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRoomRetentionPolicyTest {
    private val now = 10L * OnlineRoomRetentionPolicy.STALE_AFTER_MS

    @Test
    fun roomBecomesStaleAtTwentyFourHours() {
        assertFalse(
            OnlineRoomRetentionPolicy.isStale(
                now - OnlineRoomRetentionPolicy.STALE_AFTER_MS + 1L,
                now
            )
        )
        assertTrue(
            OnlineRoomRetentionPolicy.isStale(
                now - OnlineRoomRetentionPolicy.STALE_AFTER_MS,
                now
            )
        )
    }

    @Test
    fun invalidOrFutureTimestampsAreNeverDeleted() {
        assertFalse(OnlineRoomRetentionPolicy.isStale(0L, now))
        assertFalse(OnlineRoomRetentionPolicy.isStale(now + 1L, now))
    }
}
