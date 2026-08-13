package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineNightReadyGateTest {
    @Test
    fun secretNightFloorAlwaysFallsBetweenTenAndFifteenSeconds() {
        repeat(500) {
            val floorMs = OnlineNightReadyGate.randomFloorMs()
            assertTrue(floorMs >= OnlineNightReadyGate.MINIMUM_NIGHT_DISPLAY_MS)
            assertTrue(floorMs <= OnlineNightReadyGate.MAXIMUM_NIGHT_DISPLAY_MS)
        }
    }

    @Test
    fun coordinatorWaitsForTheChosenSecretFloor() {
        assertFalse(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer"),
                actedActorIds = setOf("killer"),
                elapsedMs = 12_000L,
                floorMs = 15_000L
            )
        )
        assertTrue(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer"),
                actedActorIds = setOf("killer"),
                elapsedMs = 15_000L,
                floorMs = 15_000L
            )
        )
    }

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
    fun lastActionKeepsANaturalTwoToFourSecondPause() {
        repeat(200) {
            val delayMs = OnlineNightReadyGate.randomPostActionDelayMs()
            assertTrue(delayMs >= OnlineNightReadyGate.MINIMUM_POST_ACTION_DELAY_MS)
            assertTrue(delayMs <= OnlineNightReadyGate.MAXIMUM_POST_ACTION_DELAY_MS)
        }
        assertFalse(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer", "medic"),
                actedActorIds = setOf("killer", "medic"),
                elapsedMs = 20_000L,
                floorMs = 10_000L,
                allActionsReadyForMs = 1_999L,
                postActionDelayMs = 2_000L
            )
        )
        assertTrue(
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = true,
                requiredActorIds = setOf("killer", "medic"),
                actedActorIds = setOf("killer", "medic"),
                elapsedMs = 20_000L,
                floorMs = 10_000L,
                allActionsReadyForMs = 2_000L,
                postActionDelayMs = 2_000L
            )
        )
    }

    @Test
    fun oracleOnlyCountsWhenThereIsARealDecision() {
        assertFalse(
            OnlineNightReadyGate.roleRequiresAction(
                RoleCatalog.ORACULO,
                round = 1,
                oracleUsed = false,
                oracleCandidateCount = 0
            )
        )
        assertFalse(
            OnlineNightReadyGate.roleRequiresAction(
                RoleCatalog.ORACULO,
                round = 2,
                oracleUsed = false,
                oracleCandidateCount = 0
            )
        )
        assertTrue(
            OnlineNightReadyGate.roleRequiresAction(
                RoleCatalog.ORACULO,
                round = 2,
                oracleUsed = false,
                oracleCandidateCount = 1
            )
        )
        assertFalse(
            OnlineNightReadyGate.roleRequiresAction(
                RoleCatalog.ORACULO,
                round = 3,
                oracleUsed = true,
                oracleCandidateCount = 2
            )
        )
        assertTrue(
            OnlineNightReadyGate.roleRequiresAction(
                RoleCatalog.MEDICO,
                round = 1,
                oracleUsed = false,
                oracleCandidateCount = 0
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
