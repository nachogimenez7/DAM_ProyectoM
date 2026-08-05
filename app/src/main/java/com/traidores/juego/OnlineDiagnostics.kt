package com.traidores.juego

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Diagnósticos acotados del gameplay online. No registra sala, UID, nombres ni mensajes.
 */
object OnlineDiagnostics {
    fun recordPhase(session: GameSession, isHost: Boolean, event: String) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("online_phase", session.phase.name)
        crashlytics.setCustomKey("online_phase_index", session.phaseIndex)
        crashlytics.setCustomKey("online_round", session.round)
        crashlytics.setCustomKey("online_is_host", isHost)
        crashlytics.log("Online phase $event: ${session.phase.name}:${session.phaseIndex}")
    }

    fun recordSyncDelay(
        session: GameSession,
        isHost: Boolean,
        connectedPlayers: Int,
        expectedPlayers: Int,
        reason: String
    ) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        recordPhase(session, isHost, event = "sync_delay")
        crashlytics.setCustomKey("online_connected_players", connectedPlayers)
        crashlytics.setCustomKey("online_expected_players", expectedPlayers)
        crashlytics.setCustomKey("online_sync_reason", reason.take(80))
        crashlytics.log("Online gameplay synchronization delay")
        crashlytics.recordException(OnlineSyncDelayException(reason))
    }
}

private class OnlineSyncDelayException(reason: String) :
    IllegalStateException("Online synchronization delay: $reason")
