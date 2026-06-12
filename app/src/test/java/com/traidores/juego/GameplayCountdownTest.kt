package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayCountdownTest {

    @Test
    fun countdownPausesAndResumesWithoutLosingRemainingTime() {
        val countdown = GameplayCountdown()
        countdown.ensurePhase(3, 5_000L)

        assertEquals(GameplayCountdown.StartResult.STARTED, countdown.start(1_000L))
        assertEquals(3_000L, countdown.tick(3_000L)!!.remainingMs)

        countdown.pause(3_500L)
        assertFalse(countdown.running)
        assertEquals(2_500L, countdown.remainingMs)

        countdown.start(10_000L)
        assertEquals(1_500L, countdown.tick(11_000L)!!.remainingMs)
    }

    @Test
    fun transitionCanBecomeAnActivePhase() {
        val countdown = GameplayCountdown()
        countdown.ensurePhase(7, 3_000L)
        countdown.start(0L)
        val transitionEnd = countdown.tick(3_000L)

        assertTrue(transitionEnd!!.expired)
        assertEquals(CountdownStage.TRANSITION, countdown.stage)

        countdown.beginActive(40_000L)

        assertEquals(CountdownStage.ACTIVE, countdown.stage)
        assertEquals(40_000L, countdown.remainingMs)
        assertEquals(40_000L, countdown.totalMs)
    }

    @Test
    fun restoredCountdownStartsPausedAndKeepsItsStage() {
        val countdown = GameplayCountdown()
        countdown.restore(
            stage = CountdownStage.ACTIVE,
            phaseIndex = 5,
            remainingMs = 12_400L,
            totalMs = 20_000L
        )

        assertFalse(countdown.running)
        assertEquals(CountdownStage.ACTIVE, countdown.stage)
        assertEquals(5, countdown.phaseIndex)
        assertEquals(12_400L, countdown.remainingForSave(100_000L))
    }

    @Test
    fun visualProgressIncludesActiveTimeDuringTransition() {
        val countdown = GameplayCountdown()
        countdown.ensurePhase(2, 5_000L)
        countdown.start(0L)
        countdown.tick(2_000L)

        assertEquals(45_000L, countdown.visualTotalMs(5_000L, 40_000L))
        assertEquals(43_000L, countdown.visualRemainingMs(40_000L))
    }
}
