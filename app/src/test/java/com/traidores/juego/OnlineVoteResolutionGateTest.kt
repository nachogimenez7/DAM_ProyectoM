package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineVoteResolutionGateTest {

    @Test
    fun hostSchedulesAnOnlineVoteOnlyOncePerPhase() {
        assertTrue(
            OnlineVoteResolutionGate.canSchedule(
                isOnline = true,
                isHost = true,
                phase = GamePhase.VOTACION,
                phaseIndex = 8,
                scheduledPhaseIndex = -1,
                resolutionInProgress = false
            )
        )
        assertFalse(
            OnlineVoteResolutionGate.canSchedule(
                isOnline = true,
                isHost = true,
                phase = GamePhase.VOTACION,
                phaseIndex = 8,
                scheduledPhaseIndex = 8,
                resolutionInProgress = false
            )
        )
    }

    @Test
    fun pendingOrRunningVoteResolutionBlocksExpiredCountdownReentry() {
        assertTrue(
            OnlineVoteResolutionGate.blocksCountdown(
                phase = GamePhase.VOTACION,
                phaseIndex = 8,
                scheduledPhaseIndex = 8,
                resolutionInProgress = false
            )
        )
        assertTrue(
            OnlineVoteResolutionGate.blocksCountdown(
                phase = GamePhase.DESEMPATE_VOTACION,
                phaseIndex = 9,
                scheduledPhaseIndex = -1,
                resolutionInProgress = true
            )
        )
        assertFalse(
            OnlineVoteResolutionGate.blocksCountdown(
                phase = GamePhase.DIA_DEBATE,
                phaseIndex = 7,
                scheduledPhaseIndex = 7,
                resolutionInProgress = true
            )
        )
    }
}
