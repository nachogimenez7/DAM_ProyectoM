package com.traidores.juego

object OnlineNightReadyGate {
    const val MINIMUM_NIGHT_DISPLAY_MS = 3_500L

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
