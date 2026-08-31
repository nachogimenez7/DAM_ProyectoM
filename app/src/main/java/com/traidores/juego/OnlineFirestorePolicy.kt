package com.traidores.juego

/**
 * Decisiones pequeñas que evitan abrir listeners o duplicar presencia en Firestore.
 * Se mantienen fuera de las Activities para que puedan verificarse sin Android/Firebase.
 */
object OnlineFirestorePolicy {
    fun shouldListenForPrivateClue(roleKey: String?): Boolean =
        roleKey == RoleCatalog.POLICIA

    fun shouldMirrorLegacyPresence(isHost: Boolean): Boolean = isHost
}
