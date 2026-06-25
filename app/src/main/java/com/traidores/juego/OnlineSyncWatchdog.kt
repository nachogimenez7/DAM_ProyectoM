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
    const val GUEST_AUTHORITY_GRACE_MS = 8_000L

    fun evaluate(
        isOnline: Boolean,
        isHost: Boolean,
        isStartupPhase: Boolean,
        hasAppliedAuthoritativeState: Boolean,
        awaitingHostAdvance: Boolean,
        lastPresencePulseElapsedMs: Long,
        elapsedSinceGameplayStartMs: Long
    ): OnlineSyncWatchdogDecision {
        if (!isOnline) {
            return OnlineSyncWatchdogDecision(false, false, false, "offline")
        }
        val shouldPublishPresence = lastPresencePulseElapsedMs >= PRESENCE_PULSE_MS
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
}
