package com.traidores.juego

import kotlin.random.Random

object OnlineNightReadyGate {
    /** Presence snapshots may be incomplete; only the match roster defines required actors. */
    fun requiredActorIds(session: GameSession): Set<String>? {
        if (session.players.any { it.alive && it.role == null } ||
            session.onlinePlayerUids.size != session.players.size ||
            session.onlinePlayerUids.any { it.isBlank() } ||
            session.onlinePlayerUids.distinct().size != session.players.size
        ) return null
        val candidateCount = GameEngine.oracleCandidates(session).size
        return session.players.mapIndexedNotNull { index, player ->
            session.onlinePlayerUids[index].takeIf {
                player.alive && roleRequiresAction(
                    player.role?.key.orEmpty(), session.round, session.oracleUsed, candidateCount
                )
            }
        }.toSet()
    }

    const val MINIMUM_NIGHT_DISPLAY_MS = 10_000L
    const val MAXIMUM_NIGHT_DISPLAY_MS = 15_000L
    const val MINIMUM_POST_ACTION_DELAY_MS = 2_000L
    const val MAXIMUM_POST_ACTION_DELAY_MS = 4_000L

    fun randomFloorMs(): Long {
        return Random.nextLong(
            from = MINIMUM_NIGHT_DISPLAY_MS,
            until = MAXIMUM_NIGHT_DISPLAY_MS + 1L
        )
    }

    fun randomPostActionDelayMs(): Long {
        return Random.nextLong(
            from = MINIMUM_POST_ACTION_DELAY_MS,
            until = MAXIMUM_POST_ACTION_DELAY_MS + 1L
        )
    }

    fun roleRequiresAction(
        roleKey: String,
        round: Int,
        oracleUsed: Boolean,
        oracleCandidateCount: Int
    ): Boolean {
        return when (roleKey) {
            RoleCatalog.ASESINO,
            RoleCatalog.ESPIA,
            RoleCatalog.MERCENARIO,
            RoleCatalog.POLICIA,
            RoleCatalog.MEDICO -> true
            RoleCatalog.ORACULO -> round > 1 && !oracleUsed && oracleCandidateCount > 0
            else -> false
        }
    }

    fun shouldResolve(
        isCoordinator: Boolean,
        requiredActorIds: Set<String>,
        actedActorIds: Set<String>,
        elapsedMs: Long,
        floorMs: Long = MINIMUM_NIGHT_DISPLAY_MS,
        allActionsReadyForMs: Long = Long.MAX_VALUE,
        postActionDelayMs: Long = 0L
    ): Boolean {
        return isCoordinator &&
            elapsedMs >= floorMs &&
            requiredActorIds.all { it in actedActorIds } &&
            (requiredActorIds.isEmpty() || allActionsReadyForMs >= postActionDelayMs)
    }

    /**
     * Once the authoritative night timer expires, the host either waits for the secret-display
     * floor or loads the confirmed actions from Firestore. Recreating the already-expired
     * countdown while either operation is pending immediately expires it again and can saturate
     * the main thread with render/expiry cycles.
     */
    fun blocksCountdownRestart(
        isOnline: Boolean,
        isCoordinator: Boolean,
        isNightPhase: Boolean,
        timerExpired: Boolean,
        resolutionInProgress: Boolean
    ): Boolean {
        return isOnline &&
            isCoordinator &&
            isNightPhase &&
            (timerExpired || resolutionInProgress)
    }
}
