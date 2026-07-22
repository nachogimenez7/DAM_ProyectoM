package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOutcomeTest {
    @Test
    fun teamAndSpecialVictoriesUseTheSameOutcomeRulesAsAchievements() {
        val townHuman = GamePlayer(
            "Humano",
            "H",
            role = role(RoleCatalog.ALDEANO, "Aldeano", GameRules.TOWN_WINNER),
            isHuman = true
        )
        val traitor = GamePlayer(
            "Asesino",
            "A",
            role = role(RoleCatalog.ASESINO, "Asesino", GameRules.TRAITOR_WINNER)
        )
        val base = GameSession(
            code = "HISTORY",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(townHuman, traitor),
            winner = GameRules.TOWN_WINNER
        )

        assertTrue(MatchOutcome.didHumanWin(base, townHuman))
        assertFalse(MatchOutcome.didHumanWin(base.copy(winner = GameRules.TRAITOR_WINNER), townHuman))

        val special = base.copy(
            winner = GameRules.TRAITOR_WINNER,
            specialVictories = listOf(
                GameSpecialVictory(
                    key = "bufon_expulsado",
                    playerName = townHuman.name,
                    roleKey = RoleCatalog.BUFON,
                    round = 1
                )
            )
        )
        assertTrue(MatchOutcome.didHumanWin(special, townHuman))
    }

    private fun role(key: String, name: String, team: String): GameRole {
        return GameRole(key, name, team, "rol_${key}_gaucho")
    }
}
