package com.traidores.juego

data class OnlineSyncWatchdogDecision(
    val shouldPublishPresence: Boolean,
    val shouldPublishClientState: Boolean,
    val shouldForceSyncing: Boolean,
    val shouldReportLongWait: Boolean,
    val reason: String
)

object OnlineSyncWatchdog {
    const val CHECK_INTERVAL_MS = 5_000L
    const val PRESENCE_PULSE_MS = 10_000L
    const val PRESENCE_JITTER_MS = 3_000L
    const val GUEST_AUTHORITY_GRACE_MS = 8_000L
    const val LONG_SYNC_WAIT_MS = 30_000L
    // El pulso normal varía entre 7 y 13 segundos. Dar margen para dos pulsos demorados evita
    // marcar como ausente a alguien que sigue jugando; una desconexión real de RTDB continúa
    // detectándose de inmediato mediante onDisconnect.
    // Dos pulsos máximos (13 s cada uno) pueden perderse y el siguiente todavía llegar a
    // los 39 s. Este margen evita mostrar "Reconectando" durante ese intervalo válido.
    const val PRESENCE_RECONNECTING_AFTER_MS = 42_000L

    fun evaluate(
        isOnline: Boolean,
        isHost: Boolean,
        isStartupPhase: Boolean,
        hasAppliedAuthoritativeState: Boolean,
        awaitingHostAdvance: Boolean,
        lastPresencePulseElapsedMs: Long,
        elapsedSinceGameplayStartMs: Long,
        elapsedAwaitingHostMs: Long = 0L,
        presencePulseIntervalMs: Long = PRESENCE_PULSE_MS
    ): OnlineSyncWatchdogDecision {
        if (!isOnline) {
            return OnlineSyncWatchdogDecision(false, false, false, false, "offline")
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
        val shouldReportLongWait =
            !isHost &&
                !isStartupPhase &&
                awaitingHostAdvance &&
                elapsedAwaitingHostMs >= LONG_SYNC_WAIT_MS
        return OnlineSyncWatchdogDecision(
            shouldPublishPresence = shouldPublishPresence,
            shouldPublishClientState =
                shouldPublishPresence || shouldForceSyncing || shouldReportLongWait,
            shouldForceSyncing = shouldForceSyncing,
            shouldReportLongWait = shouldReportLongWait,
            reason = when {
                shouldReportLongWait -> "guest_host_advance_timeout"
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

    fun shouldShowReconnecting(
        connected: Boolean,
        lastHeartbeatEpochMs: Long,
        nowEpochMs: Long
    ): Boolean {
        if (!connected) return true
        if (lastHeartbeatEpochMs <= 0L || nowEpochMs < lastHeartbeatEpochMs) return false
        return nowEpochMs - lastHeartbeatEpochMs > PRESENCE_RECONNECTING_AFTER_MS
    }
}
