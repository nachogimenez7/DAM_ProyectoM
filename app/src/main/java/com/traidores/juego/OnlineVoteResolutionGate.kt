package com.traidores.juego

/**
 * Idempotency guard for the asynchronous online vote resolver.
 *
 * The phase deadline remains expired while the host waits a short grace period and reads votes
 * from Firestore. Rendering that same phase without a guard immediately expired the countdown
 * again and recursively scheduled another resolver until the main thread overflowed its stack.
 */
object OnlineVoteResolutionGate {

    fun canSchedule(
        isOnline: Boolean,
        isHost: Boolean,
        phase: GamePhase,
        phaseIndex: Int,
        scheduledPhaseIndex: Int,
        resolutionInProgress: Boolean
    ): Boolean {
        return isOnline &&
            isHost &&
            DirectVotePolicy.isEnabled(phase) &&
            phaseIndex >= 0 &&
            scheduledPhaseIndex != phaseIndex &&
            !resolutionInProgress
    }

    fun blocksCountdown(
        phase: GamePhase,
        phaseIndex: Int,
        scheduledPhaseIndex: Int,
        resolutionInProgress: Boolean
    ): Boolean {
        return DirectVotePolicy.isEnabled(phase) &&
            (scheduledPhaseIndex == phaseIndex || resolutionInProgress)
    }
}
