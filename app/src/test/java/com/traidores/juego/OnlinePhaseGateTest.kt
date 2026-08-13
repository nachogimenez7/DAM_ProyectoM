package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePhaseGateTest {

    @Test
    fun guestCannotAdvanceOnlinePhaseLocally() {
        assertFalse(OnlinePhaseGate.canAdvanceLocally(isOnline = true, isHost = false))
    }

    @Test
    fun hostCanAdvanceOnlinePhaseLocally() {
        assertTrue(OnlinePhaseGate.canAdvanceLocally(isOnline = true, isHost = true))
    }

    @Test
    fun localGameCanAdvanceLocally() {
        assertTrue(OnlinePhaseGate.canAdvanceLocally(isOnline = false, isHost = false))
    }

    @Test
    fun oldAuthoritativeStateIsIgnored() {
        val decision = OnlinePhaseGate.evaluateIncomingState(
            isHost = false,
            currentPhaseIndex = 4,
            incomingPhaseIndex = 3,
            incomingStateKey = "old",
            lastAppliedStateKey = "newer"
        )

        assertEquals(OnlinePhaseDecision.IGNORE_OLD, decision)
    }

    @Test
    fun firstAuthoritativeSnapshotReplacesARestoredLocalPhaseEvenWhenItsIndexIsLower() {
        val decision = OnlinePhaseGate.evaluateIncomingState(
            isHost = false,
            currentPhaseIndex = 12,
            incomingPhaseIndex = 2,
            incomingStateKey = "official-day",
            lastAppliedStateKey = ""
        )

        assertEquals(OnlinePhaseDecision.APPLY, decision)
    }

    @Test
    fun duplicateAuthoritativeStateIsIgnored() {
        val decision = OnlinePhaseGate.evaluateIncomingState(
            isHost = false,
            currentPhaseIndex = 4,
            incomingPhaseIndex = 4,
            incomingStateKey = "same",
            lastAppliedStateKey = "same"
        )

        assertEquals(OnlinePhaseDecision.IGNORE_DUPLICATE, decision)
    }

    @Test
    fun newAuthoritativeStateIsApplied() {
        val decision = OnlinePhaseGate.evaluateIncomingState(
            isHost = false,
            currentPhaseIndex = 4,
            incomingPhaseIndex = 5,
            incomingStateKey = "new",
            lastAppliedStateKey = "old"
        )

        assertEquals(OnlinePhaseDecision.APPLY, decision)
    }

    @Test
    fun hostIgnoresItsOwnAuthoritativeSnapshot() {
        val decision = OnlinePhaseGate.evaluateIncomingState(
            isHost = true,
            currentPhaseIndex = 4,
            incomingPhaseIndex = 5,
            incomingStateKey = "new",
            lastAppliedStateKey = "old"
        )

        assertEquals(OnlinePhaseDecision.HOST_IGNORES, decision)
    }

    @Test
    fun hostPublishesOnlyOutsideStartup() {
        assertTrue(
            OnlinePhaseGate.canPublishAuthoritativeState(
                isOnline = true,
                isHost = true,
                isStartupPhase = false
            )
        )
        assertFalse(
            OnlinePhaseGate.canPublishAuthoritativeState(
                isOnline = true,
                isHost = true,
                isStartupPhase = true
            )
        )
        assertFalse(
            OnlinePhaseGate.canPublishAuthoritativeState(
                isOnline = true,
                isHost = false,
                isStartupPhase = false
            )
        )
    }

    @Test
    fun voteResultAutoContinueIsLocalOnly() {
        assertTrue(OnlinePhaseGate.canAutoContinueVoteResult(isOnline = false))
        assertFalse(OnlinePhaseGate.canAutoContinueVoteResult(isOnline = true))
    }
}
