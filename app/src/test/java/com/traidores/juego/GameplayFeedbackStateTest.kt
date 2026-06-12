package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayFeedbackStateTest {

    @Test
    fun privateFeedbackRemainsPendingUntilDismissed() {
        val state = GameplayFeedbackState()
        val spec = feedback(GameplayFeedbackType.PRIVATE_RESULT)

        assertEquals(GameplayFeedbackState.Presentation.PRIVATE, state.submit(spec))
        assertTrue(state.blocksGameplay())
        assertEquals(spec, state.privateToPresent())

        state.markPrivateVisible()
        assertNull(state.privateToPresent())
        state.dismissPrivate()
        assertTrue(state.privateVisible)

        state.finishPrivateDismissal()
        assertFalse(state.blocksGameplay())
    }

    @Test
    fun bannerDoesNotBecomePendingOrBlockGameplay() {
        val state = GameplayFeedbackState()

        assertEquals(
            GameplayFeedbackState.Presentation.BANNER,
            state.submit(feedback(GameplayFeedbackType.ACTION_CONFIRMATION))
        )
        assertNull(state.pending)
        assertFalse(state.blocksGameplay())
    }

    @Test
    fun lifecycleCancellationCanPreservePrivateFeedback() {
        val state = GameplayFeedbackState()
        val spec = feedback(GameplayFeedbackType.PRIVATE_RESULT)
        state.submit(spec)
        state.markPrivateVisible()

        state.cancel(keepPending = true)

        assertEquals(spec, state.pending)
        assertFalse(state.privateVisible)
        assertEquals(spec, state.privateToPresent())
    }

    private fun feedback(type: GameplayFeedbackType): GameplayFeedbackSpec {
        return GameplayFeedbackSpec(
            type = type,
            title = "AVISO",
            message = "Mensaje",
            target = "Objetivo",
            tone = GameplayActionTone.DEFAULT,
            durationMs = 10_000L
        )
    }
}
