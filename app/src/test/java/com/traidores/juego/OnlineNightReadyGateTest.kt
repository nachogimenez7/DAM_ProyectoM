package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineNightReadyGateTest {
    @Test
    fun coordinatorResolvesWhenEveryRequiredActorConfirmedAfterFloor() {
        assertTrue(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer", "medic"),
                actedActorIds = setOf("killer", "medic"),
                elapsedMs = OnlineNightReadyGate.MINIMUM_NIGHT_DISPLAY_MS
            )
        )
    }

    @Test
    fun missingActorOrEarlyElapsedTimeKeepsNightOpen() {
        assertFalse(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer", "medic"),
                actedActorIds = setOf("killer"),
                elapsedMs = 10_000L
            )
        )
        assertFalse(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer"),
                actedActorIds = setOf("killer"),
                elapsedMs = OnlineNightReadyGate.MINIMUM_NIGHT_DISPLAY_MS - 1
            )
        )
    }

    @Test
    fun noConnectedNightActorsResolvesAfterFloorButOnlyForCoordinator() {
        assertTrue(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = emptySet(),
                actedActorIds = emptySet(),
                elapsedMs = OnlineNightReadyGate.MINIMUM_NIGHT_DISPLAY_MS
            )
        )
        assertFalse(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = false,
                requiredActorIds = emptySet(),
                actedActorIds = emptySet(),
                elapsedMs = 10_000L
            )
        )
    }
}
