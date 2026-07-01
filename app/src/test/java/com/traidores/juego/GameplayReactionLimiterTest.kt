package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayReactionLimiterTest {

    @Test
    fun allowsOnlyTwoReactionsPerRoundAndResetsNextRound() {
        val limiter = GameplayReactionLimiter(cooldownMs = 0L)

        assertTrue(limiter.record("Nacho", round = 1, nowMs = 0L).allowed)
        assertTrue(limiter.record("Nacho", round = 1, nowMs = 1L).allowed)

        val third = limiter.record("Nacho", round = 1, nowMs = 2L)
        assertFalse(third.allowed)
        assertEquals(ReactionBlockReason.ROUND_LIMIT, third.reason)

        limiter.resetOutsideRound(2)
        assertTrue(limiter.record("Nacho", round = 2, nowMs = 3L).allowed)
    }

    @Test
    fun blocksReactionDuringCooldown() {
        val limiter = GameplayReactionLimiter(cooldownMs = 10_000L)

        assertTrue(limiter.record("Bot", round = 1, nowMs = 1_000L).allowed)

        val blocked = limiter.record("Bot", round = 1, nowMs = 5_000L)
        assertFalse(blocked.allowed)
        assertEquals(ReactionBlockReason.COOLDOWN, blocked.reason)
        assertEquals(6_000L, blocked.remainingCooldownMs)

        assertTrue(limiter.record("Bot", round = 1, nowMs = 11_000L).allowed)
    }
}
