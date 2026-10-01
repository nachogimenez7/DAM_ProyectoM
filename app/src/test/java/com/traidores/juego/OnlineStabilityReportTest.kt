package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStabilityReportTest {
    @Test
    fun newWaitingRoomDoesNotInheritAnOldMatchResult() {
        assertTrue(OnlineStabilityReport.shouldResetMatchContext(roomChanged = true, matchChanged = false))
    }

    @Test
    fun returningToSameLobbyPreservesTheCompletedReport() {
        assertFalse(OnlineStabilityReport.shouldResetMatchContext(roomChanged = false, matchChanged = false))
    }

    @Test
    fun newMatchInSameRoomClearsThePreviousPhase() {
        assertTrue(OnlineStabilityReport.shouldResetMatchContext(roomChanged = false, matchChanged = true))
    }
    @Test
    fun reportIdentifiersAreShortAndRoomCodeIsMasked() {
        assertEquals("***K8Z", OnlineStabilityReport.maskedRoomCode("ABC-K8Z"))
        assertEquals("87654321", OnlineStabilityReport.shortToken("match-12345678987654321"))
    }

    @Test
    fun blankIdentifiersRemainBlank() {
        assertEquals("", OnlineStabilityReport.maskedRoomCode(""))
        assertEquals("", OnlineStabilityReport.shortToken(""))
    }
}
