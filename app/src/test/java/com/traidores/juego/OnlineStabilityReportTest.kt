package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineStabilityReportTest {
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
