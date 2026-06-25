package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSyncWatchdogTest {

    @Test
    fun offlineDoesNothing() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = false,
            isHost = false,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = false,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = 99_000L,
            elapsedSinceGameplayStartMs = 99_000L
        )

        assertFalse(decision.shouldPublishPresence)
        assertFalse(decision.shouldPublishClientState)
        assertFalse(decision.shouldForceSyncing)
        assertEquals("offline", decision.reason)
    }

    @Test
    fun guestWithoutAuthoritativeStateAfterGraceStartsSyncing() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = false,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = false,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = OnlineSyncWatchdog.GUEST_AUTHORITY_GRACE_MS
        )

        assertTrue(decision.shouldForceSyncing)
        assertTrue(decision.shouldPublishClientState)
        assertEquals("guest_missing_authoritative_state", decision.reason)
    }

    @Test
    fun hostNeverForcesGuestSyncing() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = true,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = false,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = 99_000L
        )

        assertFalse(decision.shouldForceSyncing)
        assertEquals("ok", decision.reason)
    }

    @Test
    fun presencePulsePublishesClientState() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = false,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = OnlineSyncWatchdog.PRESENCE_PULSE_MS,
            elapsedSinceGameplayStartMs = 99_000L
        )

        assertTrue(decision.shouldPublishPresence)
        assertTrue(decision.shouldPublishClientState)
        assertFalse(decision.shouldForceSyncing)
        assertEquals("presence_pulse", decision.reason)
    }
}
