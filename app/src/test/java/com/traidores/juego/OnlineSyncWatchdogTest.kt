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
        assertFalse(decision.shouldReportLongWait)
        assertEquals("presence_pulse", decision.reason)
    }

    @Test
    fun presencePulseRespectsThePerClientJitteredInterval() {
        val interval = OnlineSyncWatchdog.PRESENCE_PULSE_MS + 2_000L

        val beforeInterval = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = true,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = interval - 1L,
            elapsedSinceGameplayStartMs = 99_000L,
            presencePulseIntervalMs = interval
        )
        val atInterval = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = true,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = false,
            lastPresencePulseElapsedMs = interval,
            elapsedSinceGameplayStartMs = 99_000L,
            presencePulseIntervalMs = interval
        )

        assertFalse(beforeInterval.shouldPublishPresence)
        assertTrue(atInterval.shouldPublishPresence)
    }

    @Test
    fun presenceJitterStaysInsideThreeSecondsAndVaries() {
        val intervals = (-20..20).map { seed ->
            OnlineSyncWatchdog.jitteredPresencePulseMs(seed)
        }
        val minimum = OnlineSyncWatchdog.PRESENCE_PULSE_MS - OnlineSyncWatchdog.PRESENCE_JITTER_MS
        val maximum = OnlineSyncWatchdog.PRESENCE_PULSE_MS + OnlineSyncWatchdog.PRESENCE_JITTER_MS

        assertTrue(intervals.distinct().size > 1)
        assertTrue(intervals.all { it in minimum..maximum })
    }

    @Test
    fun guestWaitingForHostReportsOnlyAfterLongWaitThreshold() {
        val beforeThreshold = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = false,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = true,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = 99_000L,
            elapsedAwaitingHostMs = OnlineSyncWatchdog.LONG_SYNC_WAIT_MS - 1L
        )
        val atThreshold = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = false,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = true,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = 99_000L,
            elapsedAwaitingHostMs = OnlineSyncWatchdog.LONG_SYNC_WAIT_MS
        )

        assertFalse(beforeThreshold.shouldReportLongWait)
        assertTrue(atThreshold.shouldReportLongWait)
        assertTrue(atThreshold.shouldPublishClientState)
        assertEquals("guest_host_advance_timeout", atThreshold.reason)
    }

    @Test
    fun hostLongWaitIsNeverReportedAsGuestSynchronizationFailure() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = true,
            isStartupPhase = false,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = true,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = 99_000L,
            elapsedAwaitingHostMs = OnlineSyncWatchdog.LONG_SYNC_WAIT_MS
        )

        assertFalse(decision.shouldReportLongWait)
    }

    @Test
    fun reconnectingIndicatorWaitsForAStaleHeartbeat() {
        val now = 50_000L

        assertFalse(
            OnlineSyncWatchdog.shouldShowReconnecting(
                connected = true,
                lastHeartbeatEpochMs = now - OnlineSyncWatchdog.PRESENCE_RECONNECTING_AFTER_MS,
                nowEpochMs = now
            )
        )
        assertTrue(
            OnlineSyncWatchdog.shouldShowReconnecting(
                connected = true,
                lastHeartbeatEpochMs =
                    now - OnlineSyncWatchdog.PRESENCE_RECONNECTING_AFTER_MS - 1L,
                nowEpochMs = now
            )
        )
        assertTrue(
            OnlineSyncWatchdog.shouldShowReconnecting(
                connected = false,
                lastHeartbeatEpochMs = now,
                nowEpochMs = now
            )
        )
    }

    @Test
    fun startupReadingIsNotReportedAsSynchronizationFailure() {
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = true,
            isHost = false,
            isStartupPhase = true,
            hasAppliedAuthoritativeState = true,
            awaitingHostAdvance = true,
            lastPresencePulseElapsedMs = 1_000L,
            elapsedSinceGameplayStartMs = 99_000L,
            elapsedAwaitingHostMs = OnlineSyncWatchdog.LONG_SYNC_WAIT_MS
        )

        assertFalse(decision.shouldReportLongWait)
    }
}
