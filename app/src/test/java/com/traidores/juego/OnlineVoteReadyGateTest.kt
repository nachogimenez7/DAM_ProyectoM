package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineVoteReadyGateTest {
    @Test
    fun requiresEveryEligiblePlayerInTheCurrentDebate() {
        val result = OnlineVoteReadyGate.evaluate(
            eligiblePlayerNames = listOf("Nacho", "Mora"),
            states = listOf(
                OnlineVoteReadyState("1", "Nacho", true, round = 2, phaseIndex = 8),
                OnlineVoteReadyState("2", "Mora", true, round = 2, phaseIndex = 8)
            ),
            round = 2,
            phaseIndex = 8
        )

        assertEquals(2, result.readyCount)
        assertEquals(2, result.totalCount)
        assertTrue(result.canSkip)
    }

    @Test
    fun ignoresStaleAndIneligibleReadiness() {
        val result = OnlineVoteReadyGate.evaluate(
            eligiblePlayerNames = listOf("Nacho", "Mora"),
            states = listOf(
                OnlineVoteReadyState("1", "Nacho", true, round = 1, phaseIndex = 4),
                OnlineVoteReadyState("2", "Mora", true, round = 2, phaseIndex = 8),
                OnlineVoteReadyState("3", "Silenciado", true, round = 2, phaseIndex = 8)
            ),
            round = 2,
            phaseIndex = 8
        )

        assertEquals(1, result.readyCount)
        assertFalse(result.canSkip)
    }
}
