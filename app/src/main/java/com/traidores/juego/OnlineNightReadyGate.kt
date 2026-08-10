package com.traidores.juego

import kotlin.random.Random

object OnlineNightReadyGate {
    const val MINIMUM_NIGHT_DISPLAY_MS = 10_000L
    const val MAXIMUM_NIGHT_DISPLAY_MS = 15_000L

    fun randomFloorMs(): Long {
        return Random.nextLong(
            from = MINIMUM_NIGHT_DISPLAY_MS,
            until = MAXIMUM_NIGHT_DISPLAY_MS + 1L
        )
    }

    fun shouldResolve(
        isCoordinator: Boolean,
        requiredActorIds: Set<String>,
        actedActorIds: Set<String>,
        elapsedMs: Long,
        floorMs: Long = MINIMUM_NIGHT_DISPLAY_MS
    ): Boolean {
        return isCoordinator &&
            elapsedMs >= floorMs &&
            requiredActorIds.all { it in actedActorIds }
    }
}
