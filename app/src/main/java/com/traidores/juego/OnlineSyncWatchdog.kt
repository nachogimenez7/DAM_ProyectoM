package com.traidores.juego

data class OnlineSyncWatchdogDecision(
    val shouldPublishPresence: Boolean,
    val shouldPublishClientState: Boolean,
    val shouldForceSyncing: Boolean,
    val reason: String
)

object OnlineSyncWatchdog {
    const val CHECK_INTERVAL_MS = 5_000L
    const val PRESENCE_PULSE_MS = 10_000L
    const val PRESENCE_JITTER_MS = 3_000L
    const val GUEST_AUTHORITY_GRACE_MS = 8_000L

    fun evaluate(
        isOnline: Boolean,
        isHost: Boolean,
        isStartupPhase: Boolean,
        hasAppliedAuthoritativeState: Boolean,
        awaitingHostAdvance: Boolean,
        lastPresencePulseElapsedMs: Long,
        elapsedSinceGameplayStartMs: Long,
        presencePulseIntervalMs: Long = PRESENCE_PULSE_MS
    ): OnlineSyncWatchdogDecision {
        if (!isOnline) {
            return OnlineSyncWatchdogDecision(false, false, false, "offline")
        }
        val normalizedPulseInterval = presencePulseIntervalMs.coerceIn(
            PRESENCE_PULSE_MS - PRESENCE_JITTER_MS,
            PRESENCE_PULSE_MS + PRESENCE_JITTER_MS
        )
        val shouldPublishPresence = lastPresencePulseElapsedMs >= normalizedPulseInterval
        val guestMissingAuthoritativeState =
            !isHost &&
                !isStartupPhase &&
                !hasAppliedAuthoritativeState &&
                elapsedSinceGameplayStartMs >= GUEST_AUTHORITY_GRACE_MS
        val shouldForceSyncing = guestMissingAuthoritativeState && !awaitingHostAdvance
        return OnlineSyncWatchdogDecision(
            shouldPublishPresence = shouldPublishPresence,
            shouldPublishClientState = shouldPublishPresence || shouldForceSyncing,
            shouldForceSyncing = shouldForceSyncing,
            reason = when {
                shouldForceSyncing -> "guest_missing_authoritative_state"
                shouldPublishPresence -> "presence_pulse"
                else -> "ok"
            }
        )
    }

    fun jitteredPresencePulseMs(seed: Int): Long {
        val possibleOffsets = (PRESENCE_JITTER_MS * 2L + 1L).toInt()
        val offset = Math.floorMod(seed, possibleOffsets).toLong() - PRESENCE_JITTER_MS
        return PRESENCE_PULSE_MS + offset
    }
}
