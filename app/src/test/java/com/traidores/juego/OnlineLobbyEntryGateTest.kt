package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyEntryGateTest {

    @Test
    fun releasesOnlyWhenEveryExpectedPlayerAcknowledgedTheCurrentMatch() {
        val states = mapOf(
            "host" to readyState("match-actual"),
            "guest-1" to readyState("match-actual"),
            "guest-2" to readyState("match-actual")
        )

        assertTrue(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest-1", "guest-2"),
                matchId = "match-actual",
                clientStates = states
            )
        )
    }

    @Test
    fun staleOrMissingAcknowledgementsKeepTheBarrierClosed() {
        val states = mapOf(
            "host" to readyState("match-actual"),
            "guest-1" to readyState("match-anterior")
        )

        assertFalse(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest-1", "guest-2"),
                matchId = "match-actual",
                clientStates = states
            )
        )
    }

    @Test
    fun timeoutCanReleaseAfterAtLeastOneCurrentAcknowledgement() {
        assertTrue(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest"),
                matchId = "match-actual",
                clientStates = mapOf("host" to readyState("match-actual")),
                force = true
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest"),
                matchId = "match-actual",
                clientStates = emptyMap(),
                force = true
            )
        )
    }

    private fun readyState(matchId: String): Map<String, Any?> {
        return mapOf(
            OnlineLobbyEntryGate.FIELD_MATCH_ID to matchId,
            OnlineLobbyEntryGate.FIELD_ENTRY_READY to true
        )
    }
}
