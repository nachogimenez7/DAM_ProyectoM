package com.traidores.juego

/**
 * Decisiones pequeñas que evitan abrir listeners o duplicar presencia en Firestore.
 * Se mantienen fuera de las Activities para que puedan verificarse sin Android/Firebase.
 */
object OnlineFirestorePolicy {
    const val HOST_LEASE_REFRESH_MS = 30_000L

    fun shouldListenForPrivateClue(roleKey: String?): Boolean =
        roleKey == RoleCatalog.POLICIA

    fun shouldMirrorLegacyPresence(isHost: Boolean): Boolean = isHost

    fun shouldWriteLegacyHostPresence(
        isHost: Boolean,
        state: String,
        lastState: String,
        nowElapsedMs: Long,
        lastWriteElapsedMs: Long
    ): Boolean {
        if (!isHost) return false
        if (state != lastState) return true
        return state == RealtimeRoomPresence.STATE_CONNECTED &&
            nowElapsedMs - lastWriteElapsedMs >= HOST_LEASE_REFRESH_MS
    }
}
