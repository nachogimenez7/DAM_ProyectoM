package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayTableUiTest {

    @Test
    fun splitCompanionsExcludesHumanAndPutsOddExtraOnRight() {
        val fivePlayers = players(5)
        val eightPlayers = players(8)
        val fifteenPlayers = players(15)

        assertSplit(fivePlayers, expectedLeft = 2, expectedRight = 2)
        assertSplit(eightPlayers, expectedLeft = 3, expectedRight = 4)
        assertSplit(fifteenPlayers, expectedLeft = 7, expectedRight = 7)
    }

    @Test
    fun companionCardMetricsShrinkAsPlayerCountGrows() {
        val core = GameplayTableUi.companionCardMetrics(8)
        val medium = GameplayTableUi.companionCardMetrics(12)
        val large = GameplayTableUi.companionCardMetrics(15)

        assertEquals(76, core.columnWidthDp)
        assertEquals(76, medium.columnWidthDp)
        assertEquals(76, large.columnWidthDp)
        assertTrue(core.minCardWidthDp >= 64)
        assertTrue(medium.minCardWidthDp >= 60)
        assertTrue(large.minCardWidthDp >= 56)
        assertTrue(core.minCardWidthDp <= core.columnWidthDp)
        assertTrue(medium.minCardWidthDp <= medium.columnWidthDp)
        assertTrue(large.minCardWidthDp <= large.columnWidthDp)
        assertTrue(core.itemHeightDp > medium.itemHeightDp)
        assertTrue(medium.itemHeightDp > large.itemHeightDp)
        assertTrue(core.cardHeightDp > medium.cardHeightDp)
        assertTrue(medium.cardHeightDp > large.cardHeightDp)
    }

    @Test
    fun mapKeysResolveToGameplayThemes() {
        assertEquals("gaucho", GameplayTableUi.themeForMapKey("pampa"))
        assertEquals("griego", GameplayTableUi.themeForMapKey("grecia"))
        assertEquals("medieval", GameplayTableUi.themeForMapKey("medieval"))
        assertEquals("gaucho", GameplayTableUi.themeForMapKey("desconocido"))
    }

    @Test
    fun onlyNightRolePhasesUseNightBackground() {
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_ASESINO))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_MERCENARIO))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_POLICIA))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_MEDICO))

        assertFalse(GameplayTableUi.isNightPhase(GamePhase.REPARTO))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.AMANECER))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.DIA_DEBATE))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.VOTACION))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.RESULTADO))
    }

    @Test
    fun selfProtectionIsOfferedOnlyToActiveHumanMedicOnMedicTurn() {
        val medicRole = GameRole("medico", "Medico", "Pueblo", "rol_medico_gaucho")
        val villagerRole = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.NOCHE_MEDICO,
            players = listOf(
                GamePlayer("Humano", "H", role = medicRole, isHuman = true),
                GamePlayer("Bot", "B", role = villagerRole)
            )
        )

        assertTrue(GameplayTableUi.canHumanMedicSelfProtect(session))
        assertFalse(
            GameplayTableUi.canHumanMedicSelfProtect(
                session.copy(phase = GamePhase.NOCHE_POLICIA)
            )
        )
        assertFalse(
            GameplayTableUi.canHumanMedicSelfProtect(
                session.copy(
                    players = session.players.map {
                        if (it.isHuman) it.copy(alive = false, muted = true) else it
                    }
                )
            )
        )
    }

    @Test
    fun publicEventTypeDetectsDeathVotingDiscussionAndPhaseStart() {
        assertEquals(
            PublicEventType.DEATH,
            GameplayTableUi.eventTypeFor("Amanecer: murio Tomas. Tomas queda muteado.", GamePhase.AMANECER)
        )
        assertEquals(
            PublicEventType.VOTING,
            GameplayTableUi.eventTypeFor("Dios cerro la votacion. Se resolvera la expulsion.", GamePhase.VOTACION)
        )
        assertEquals(
            PublicEventType.DISCUSSION,
            GameplayTableUi.eventTypeFor("Dia 1: debatan. Muteados: Tomas.", GamePhase.DIA_DEBATE)
        )
        assertEquals(
            PublicEventType.PHASE_START,
            GameplayTableUi.eventTypeFor("Noche 1: todos guardan silencio.", GamePhase.NOCHE_ASESINO)
        )
        assertEquals(
            PublicEventType.PHASE_START,
            GameplayTableUi.eventTypeFor("Amanecer: no murio nadie.", GamePhase.AMANECER)
        )
    }

    private fun assertSplit(players: List<GamePlayer>, expectedLeft: Int, expectedRight: Int) {
        val (left, right) = GameplayTableUi.splitCompanions(players)

        assertEquals(expectedLeft, left.size)
        assertEquals(expectedRight, right.size)
        assertTrue((left + right).none { it.isHuman })
        assertEquals(players.drop(1), left + right)
    }

    private fun players(count: Int): List<GamePlayer> {
        return List(count) { index ->
            GamePlayer(
                name = if (index == 0) "Humano" else "Jugador$index",
                initial = index.toString(),
                isHuman = index == 0
            )
        }
    }
}
