package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePresentationGateTest {
    @Test
    fun progressCountsOnlyLivingConnectedPlayers() {
        val progress = OnlinePresentationGate.progress(
            presentationKey = "amanecer-1",
            participants = listOf(
                participant("host", alive = false, ack = ""),
                participant("uno", ack = "amanecer-1"),
                participant("dos", ack = ""),
                participant("offline", connected = false, ack = "amanecer-1")
            )
        )

        assertEquals(1, progress.ready)
        assertEquals(2, progress.total)
    }

    @Test
    fun allReadyAdvancesAfterMinimumDelay() {
        val progress = OnlinePresentationProgress(ready = 3, total = 3)

        assertFalse(
            OnlinePresentationGate.shouldAdvance(
                isCoordinator = true,
                elapsedMs = OnlinePresentationGate.MINIMUM_DISPLAY_MS - 1,
                progress = progress
            )
        )
        assertTrue(
            OnlinePresentationGate.shouldAdvance(
                isCoordinator = true,
                elapsedMs = OnlinePresentationGate.MINIMUM_DISPLAY_MS,
                progress = progress
            )
        )
    }

    @Test
    fun maximumDelayAdvancesEvenWhenSomeoneDidNotConfirm() {
        assertTrue(
            OnlinePresentationGate.shouldAdvance(
                isCoordinator = true,
                elapsedMs = OnlinePresentationGate.MAXIMUM_DISPLAY_MS,
                progress = OnlinePresentationProgress(ready = 2, total = 3)
            )
        )
    }

    @Test
    fun guestNeverPublishesTheSharedAdvance() {
        assertFalse(
            OnlinePresentationGate.shouldAdvance(
                isCoordinator = false,
                elapsedMs = OnlinePresentationGate.MAXIMUM_DISPLAY_MS,
                progress = OnlinePresentationProgress(ready = 3, total = 3)
            )
        )
    }

    private fun participant(
        uid: String,
        connected: Boolean = true,
        alive: Boolean = true,
        ack: String
    ): OnlinePresentationParticipant {
        return OnlinePresentationParticipant(uid, connected, alive, ack)
    }
}
