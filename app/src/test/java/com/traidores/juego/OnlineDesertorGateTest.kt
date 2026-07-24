package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineDesertorGateTest {

    @Test
    fun theHostWaitsAFullRoundBeforeChoosingForTheDesertor() {
        assertFalse(
            OnlineDesertorGate.needsAutoTeam(
                isHost = true,
                hasAliveDesertor = true,
                teamIsBlank = true,
                round = 1,
                winner = ""
            )
        )
        assertTrue(
            OnlineDesertorGate.needsAutoTeam(
                isHost = true,
                hasAliveDesertor = true,
                teamIsBlank = true,
                round = OnlineDesertorGate.AUTO_TEAM_ROUND,
                winner = ""
            )
        )
    }

    @Test
    fun aChosenTeamIsNeverOverwritten() {
        assertFalse(
            OnlineDesertorGate.needsAutoTeam(
                isHost = true,
                hasAliveDesertor = true,
                teamIsBlank = false,
                round = 5,
                winner = ""
            )
        )
    }

    @Test
    fun onlyTheHostResolvesAndOnlyWhileTheMatchIsOpen() {
        assertFalse(
            OnlineDesertorGate.needsAutoTeam(
                isHost = false,
                hasAliveDesertor = true,
                teamIsBlank = true,
                round = 5,
                winner = ""
            )
        )
        assertFalse(
            OnlineDesertorGate.needsAutoTeam(
                isHost = true,
                hasAliveDesertor = true,
                teamIsBlank = true,
                round = 5,
                winner = GameRules.TOWN_WINNER
            )
        )
        assertFalse(
            OnlineDesertorGate.needsAutoTeam(
                isHost = true,
                hasAliveDesertor = false,
                teamIsBlank = true,
                round = 5,
                winner = ""
            )
        )
    }

    @Test
    fun theAutomaticTeamIsStableSoAHostHandoffResolvesTheSame() {
        val names = listOf("Ana", "Beto", "Ciro", "Dina", "Ema")

        val first = OnlineDesertorGate.autoTeam("SALA-77", names)
        val second = OnlineDesertorGate.autoTeam("SALA-77", names)

        assertEquals(first, second)
        assertTrue(first == GameRules.TOWN_WINNER || first == GameRules.TRAITOR_WINNER)
    }
}
