package com.traidores.juego

/** UI retention only. Destructive cleanup belongs to the scheduled backend. */
object OnlineRoomRetentionPolicy {
    const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L
    const val BROWSER_FRESH_FOR_MS = 30L * 60L * 1000L
    const val EMPTY_ROOM_RECOVERY_GRACE_MS = 2L * 60L * 1000L

    fun hasRecoverableParticipants(
        presence: Collection<RealtimePresenceState>,
        updatedAtMs: Long,
        nowMs: Long
    ): Boolean {
        // Connected sockets do not need periodic heartbeat writes. Their timestamp may
        // be old; never mistake that for an empty room. Unknown timestamps are conservative.
        if (presence.any { it.connected || it.changedAtMs <= 0L }) return true
        val latestActivityMs = maxOf(updatedAtMs, presence.maxOfOrNull { it.changedAtMs } ?: 0L)
        return latestActivityMs > 0L && nowMs - latestActivityMs < EMPTY_ROOM_RECOVERY_GRACE_MS
    }

    fun isRecoveryAvailable(state: String, updatedAtMs: Long, nowMs: Long): Boolean = when (state) {
        // A crashed host can leave this state behind indefinitely. Keep normal
        // reconnections available, but never advertise a match from a previous day.
        OnlineRoomFirestore.STATE_IN_GAME -> updatedAtMs > 0L &&
            nowMs - updatedAtMs < STALE_AFTER_MS
        OnlineRoomFirestore.STATE_WAITING,
        OnlineRoomFirestore.STATE_FINISHED -> updatedAtMs > 0L &&
            nowMs - updatedAtMs <= BROWSER_FRESH_FOR_MS
        else -> false
    }

    fun isStale(updatedAtMs: Long, nowMs: Long): Boolean {
        if (updatedAtMs <= 0L || nowMs < updatedAtMs) return false
        return nowMs - updatedAtMs >= STALE_AFTER_MS
    }

    fun isDiscoverable(
        updatedAtMs: Long,
        nowMs: Long,
        currentPlayers: Int,
        playerLimit: Int,
        deleting: Boolean,
        allowFullForReturningMember: Boolean = false
    ): Boolean = !deleting && currentPlayers > 0 && playerLimit > 0 &&
        (currentPlayers < playerLimit ||
            (allowFullForReturningMember && currentPlayers == playerLimit)) &&
        updatedAtMs > 0L &&
        // A server timestamp can be ahead of an emulator's wall clock. It is not a stale room.
        nowMs - updatedAtMs <= BROWSER_FRESH_FOR_MS
}
