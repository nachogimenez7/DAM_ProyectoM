package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class OnlineResolutionReadGateTest {
    private val window = OnlineResolutionReadGate.Window("match-123", 1, 4, "VOTACION")

    @Test fun disconnectAndReconnectCannotTurnMissingVotesIntoAnEmptyResult() {
        val gate = OnlineResolutionReadGate()
        val ticket = gate.begin(window)!!
        var resolutions = 0
        // Cached empty snapshots and local pending writes must leave the read pending.
        listOf(true to false, false to true).forEach { (cache, pending) ->
            if (gate.acceptServerResult(ticket, window, true, true, cache, pending)) resolutions++
        }
        assertEquals(0, resolutions)
        assertTrue(gate.inProgress)
        assertNull(gate.begin(window)) // repeated expired timers cannot start another read
        if (gate.acceptServerResult(ticket, window, true, true, false, false)) resolutions++
        assertEquals(1, resolutions)
        assertFalse(gate.acceptServerResult(ticket, window, true, true, false, false))
    }

    @Test fun lateCallbacksCannotResolveAfterHandoffStopRematchOrPhaseChange() {
        val gate = OnlineResolutionReadGate()
        val old = gate.begin(window)!!
        assertFalse(gate.acceptServerResult(old, window, false, true, false, false))
        assertFalse(gate.acceptServerResult(old, window, true, false, false, false))
        assertFalse(gate.acceptServerResult(old, window.copy(matchId = "new-match"), true, true, false, false))
        assertFalse(gate.acceptServerResult(old, window.copy(phaseIndex = 5), true, true, false, false))
        gate.invalidate()
        val resumed = gate.begin(window)!!
        assertFalse(gate.acceptServerResult(old, window, true, true, false, false))
        assertTrue(gate.acceptServerResult(resumed, window, true, true, false, false))
    }

    @Test fun retriesUseBoundedBackoffWithoutBusyLoopOrOverflow() {
        assertEquals(listOf(1000L, 2000L, 4000L, 5000L, 5000L),
            (0..4).map(OnlineResolutionReadGate::retryDelayMs))
        assertEquals(5000L, OnlineResolutionReadGate.retryDelayMs(Int.MAX_VALUE))
    }
}
