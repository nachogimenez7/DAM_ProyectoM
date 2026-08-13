package com.traidores.juego

import kotlin.random.Random

object OnlineNightReadyGate {
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
}
