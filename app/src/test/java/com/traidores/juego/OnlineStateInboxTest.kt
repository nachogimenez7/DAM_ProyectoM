package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class OnlineStateInboxTest {
    private fun state(phase: Int, sequence: Long, epoch: Long = 1, clock: Long = 1000, presentation: String = "") =
        mapOf<String, Any?>("phaseIndex" to phase, "stateSequence" to sequence, "authorityEpoch" to epoch,
            "actualizadaEnLocal" to clock, "presentacionVotacion" to presentation)

    @Test fun handoffWithOlderClockAcceptsNewAuthorityAndRejectsLateFormerHost() {
        val inbox = OnlineStateInbox()
        assertTrue(inbox.offer(state(4, 90, clock = 999999)))
        assertTrue(inbox.offer(state(4, 91, epoch = 2, clock = 1)))
        assertFalse(inbox.offer(state(5, 92, epoch = 1, clock = 9999999)))
        assertEquals(2L, inbox.poll()!!["authorityEpoch"])
    }

    @Test fun slowDeviceRetainsDawnExpulsionAndNightWhileCoalescingSamePresentation() {
        val inbox = OnlineStateInbox()
        inbox.offer(state(2, 1))
        inbox.offer(state(2, 2))
        inbox.offer(state(3, 3, presentation = "expulsion|jugador"))
        inbox.offer(state(4, 4))
        assertEquals(3, inbox.size)
        assertEquals(2L, inbox.poll()!!["stateSequence"])
        assertEquals("expulsion|jugador", inbox.poll()!!["presentacionVotacion"])
        assertEquals(4, inbox.poll()!!["phaseIndex"])
        assertNull(inbox.poll())
    }

    @Test fun duplicateFallbackAndOutOfOrderUpdatesCannotReplayPresentation() {
        val inbox = OnlineStateInbox()
        assertTrue(inbox.offer(state(5, 10)))
        assertFalse(inbox.offer(state(5, 10)))
        assertFalse(inbox.offer(state(5, 9, clock = 9000)))
        assertFalse(inbox.offer(state(4, 11)))
        assertEquals(1, inbox.size)
        inbox.clear()
        assertTrue(inbox.offer(state(0, 1, epoch = 0)))
    }

    @Test fun savedSequenceRejectsOldFirestoreFallbackAfterActivityRecreation() {
        val restored = state(8, 200, epoch = 3, clock = 1)
        assertFalse(OnlineStateOrder.isNewer(state(8, 190, epoch = 3, clock = 999999), restored))
        assertTrue(OnlineStateOrder.isNewer(state(8, 201, epoch = 4, clock = 0), restored))
    }

    @Test fun winnerReturnDeadlineReplacesResultStateWhenSequenceAdvances() {
        val inbox = OnlineStateInbox()
        val result = state(12, 40) + mapOf(
            "ganador" to GameRules.TOWN_WINNER,
            "volverLobbyEpochMs" to 46_000L
        )
        val released = result + mapOf(
            "stateSequence" to 41L,
            "volverLobbyEpochMs" to 4_000L
        )

        assertTrue(inbox.offer(result))
        assertTrue(inbox.offer(released))
        assertEquals(1, inbox.size)
        assertEquals(4_000L, inbox.poll()!!["volverLobbyEpochMs"])
    }

    @Test fun foregroundCatchUpCanSelectLiveStateAndDiscardIntermediateFrames() {
        val inbox = OnlineStateInbox()
        inbox.offer(state(9, 20, presentation = "expulsion|jugador"))
        inbox.offer(state(10, 21))
        inbox.offer(state(11, 22))

        assertEquals(11, inbox.newestPhaseIndex())
        assertEquals(22L, inbox.pollNewestAndDropOlder()!!["stateSequence"])
        assertEquals(0, inbox.size)
    }

    @Test fun foregroundSnapshotWithSamePhaseAndSequenceConfirmsCurrentState() {
        val saved = state(9, 20, presentation = "expulsion|jugador")
        assertTrue(OnlineStateOrder.isSameOrNewer(saved, saved))
        assertTrue(OnlineStateOrder.isSameOrNewer(state(9, 21), saved))
        assertFalse(OnlineStateOrder.isSameOrNewer(state(9, 19), saved))
    }

    @Test fun searchContinuesPastThirtyFullRoomsAndStopsAutomaticReadsAtBound() {
        assertTrue(OnlineLobbySearchWindow.shouldExpand(30, 30, 0))
        assertTrue(OnlineLobbySearchWindow.shouldExpand(60, 60, 5))
        assertFalse(OnlineLobbySearchWindow.shouldExpand(60, 42, 5))
        assertFalse(OnlineLobbySearchWindow.shouldExpand(150, 150, 0))
        assertFalse(OnlineLobbySearchWindow.shouldExpand(60, 60, 30))
    }
}
