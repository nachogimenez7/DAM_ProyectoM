package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class OnlineDeviceRegressionTest {
    private fun session(count: Int = 12): GameSession = GameSession(
        code = "DEVICE", mapKey = "pampa", mapName = "Pampa",
        onlineMatchId = "device-match-0001",
        onlinePlayerUids = (0 until count).map { "uid-$it" },
        phase = GamePhase.NOCHE_ASESINO,
        players = (0 until count).map { index ->
            GamePlayer("Player $index", "P", role = GameRole(
                key = when (index) { 0 -> RoleCatalog.ASESINO; 1 -> RoleCatalog.MEDICO; else -> RoleCatalog.ALDEANO },
                name = "Role", team = GameRules.TOWN_WINNER, imageResName = ""
            ))
        }
    )

    @Test fun delayedMedicCannotBeDroppedFromFiveOrTwelvePlayerNight() {
        listOf(5, 12).forEach { count ->
            val required = OnlineNightReadyGate.requiredActorIds(session(count))!!
            assertEquals(setOf("uid-0", "uid-1"), required)
            assertFalse(OnlineNightReadyGate.shouldResolve(true, required, setOf("uid-0"), 30_000))
            assertTrue(OnlineNightReadyGate.shouldResolve(true, required, required, 30_000))
        }
    }

    @Test fun incompleteOrDuplicateRosterCannotCloseNightEarly() {
        val state = session()
        assertNull(OnlineNightReadyGate.requiredActorIds(state.copy(onlinePlayerUids = listOf("uid-0"))))
        assertNull(OnlineNightReadyGate.requiredActorIds(state.copy(onlinePlayerUids = List(12) { "same" })))
    }

    @Test fun twelvePlayerStartupWaitsForAllTablesToLoad() {
        assertFalse(OnlineStartupGate.shouldHardTimeoutStart(1, 60_000, 9, 9, 12, 12, 9))
        assertFalse(OnlineStartupGate.shouldHardTimeoutStart(1, 60_000, 12, 12, 12, 12, 11))
        assertTrue(OnlineStartupGate.shouldHardTimeoutStart(1, 60_000, 12, 12, 12, 12, 12))
    }

    @Test fun publicationConfirmationCannotReleaseDifferentPhaseOrMatch() {
        val gate = OnlinePublicationGate()
        val night = session()
        val dawn = night.copy(phase = GamePhase.AMANECER, phaseIndex = 5)
        assertTrue(gate.isPending(night))
        gate.confirm(gate.key(night), night)
        assertFalse(gate.isPending(night))
        assertTrue(gate.isPending(dawn))
        gate.confirm(gate.key(night), dawn) // delayed success from the old phase
        assertTrue(gate.isPending(dawn))
        gate.confirm(gate.key(dawn), dawn)
        assertFalse(gate.isPending(dawn))
        gate.confirm(gate.key(night), dawn)
        assertFalse(gate.isPending(dawn))
        assertTrue(gate.isPending(dawn.copy(onlineMatchId = "rematch")))
        assertTrue(gate.isPending(dawn.copy(winner = GameRules.TOWN_WINNER)))
    }

    @Test fun expulsionWithinSamePhaseRequiresItsOwnPublicationConfirmation() {
        val gate = OnlinePublicationGate()
        val state = session().copy(phase = GamePhase.RECUENTO_VOTOS, dayEliminationTarget = "Player 2")
        gate.confirm(gate.key(state), state)
        assertTrue(gate.isPending(state, "expulsion|1|Player 2"))
        gate.confirm(gate.key(state), state, "expulsion|1|Player 2")
        assertTrue(gate.isPending(state, "expulsion|1|Player 2"))
        gate.confirm(gate.key(state, "expulsion|1|Player 2"), state, "expulsion|1|Player 2")
        assertFalse(gate.isPending(state, "expulsion|1|Player 2"))
    }

    @Test fun reconnectKeepsSameHistoryKeyAndRematchGetsNewKey() {
        val state = session()
        assertEquals(MatchOutcome.matchKey(state), MatchOutcome.matchKey(state.copy(startedAtEpochMs = 123L)))
        assertNotEquals(MatchOutcome.matchKey(state), MatchOutcome.matchKey(state.copy(onlineMatchId = "rematch")))
    }

    @Test fun unavailableProfileStatsAreDifferentFromZeroMatches() {
        assertFalse(PublicProfileStats.fromMap(null).hasProgress)
        assertFalse(PublicProfileStats.fromMap(mapOf("partidas" to 2, "victorias" to 3)).hasProgress)
        assertEquals(PlayerStats(0, 0, true), PublicProfileStats.fromMap(mapOf("partidas" to 0, "victorias" to 0)))
        assertEquals(60, PublicProfileStats.fromMap(mapOf("partidas" to 5L, "victorias" to 3L)).winRatePercent)
    }
}
