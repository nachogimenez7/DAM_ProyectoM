package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayTableUiTest {

    @Test
    fun playerInitialUsesValidLetterAndFallsBackToName() {
        assertEquals("M", GameplayTableUi.playerInitial(GamePlayer("Martina", "m")))
        assertEquals("T", GameplayTableUi.playerInitial(GamePlayer("Tomas", "0:450")))
        assertEquals("?", GameplayTableUi.playerInitial(GamePlayer("123", "0:610")))
    }

    @Test
    fun traitorRevealIncludesOnlyExplicitTraitorRoles() {
        val assassin = GameRole("asesino", "Asesino", "Traidores", "rol_asesino_gaucho")
        val mercenary = GameRole("mercenario", "Mercenario", "Traidores", "rol_mercenario_gaucho")
        val spy = GameRole("espia", "Espia", "Traidores", "rol_espia_gaucho")
        val desertor = GameRole("desertor", "Desertora", "Neutral", "rol_desertor_gaucho")
        val villager = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val players = listOf(
            GamePlayer("Humano", "H", role = assassin, isHuman = true),
            GamePlayer("Mercenario", "M", role = mercenary),
            GamePlayer("Espia", "E", role = spy),
            GamePlayer("Desertora", "D", role = desertor),
            GamePlayer("Aldeano", "A", role = villager)
        )
        val session = GameSession("TEST", "pampa", "Pampa", players, desertorTeam = GameRules.TRAITOR_WINNER)

        assertEquals(
            listOf("Mercenario", "Espia"),
            GameplayTableUi.traitorTeammatesForReveal(session).map { it.name }
        )
        assertTrue(GameplayTableUi.shouldShowTraitorReveal(session, completed = false))
        assertFalse(GameplayTableUi.shouldShowTraitorReveal(session, completed = true))
        assertFalse(
            GameplayTableUi.shouldShowTraitorReveal(
                session.copy(phase = GamePhase.NOCHE_ASESINO),
                completed = false
            )
        )
        assertTrue(
            GameplayTableUi.traitorTeammatesForReveal(
                session.copy(players = players.map { it.copy(isHuman = it.name == "Aldeano") })
            ).isEmpty()
        )
        assertTrue(
            GameplayTableUi.traitorTeammatesForReveal(
                session.copy(players = players.filterNot { it.name == "Mercenario" || it.name == "Espia" })
            ).isEmpty()
        )
    }

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
    fun transitionTitlesUseVisualDayAndNightSequence() {
        val base = transitionSession(GamePhase.REPARTO, round = 1)

        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.DAY, "DÍA 1", "DAY_1"),
            GameplayTableUi.transitionSpec(base)
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.NIGHT, "NOCHE 1", "NIGHT_1"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.NOCHE_ASESINO))
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.DAY, "DÍA 2", "DAY_2"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.AMANECER))
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.NIGHT, "NOCHE 2", "NIGHT_2"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.NOCHE_MEDICO, round = 2))
        )
    }

    @Test
    fun subphasesInSamePeriodDoNotRepeatTransition() {
        val nightSpecs = listOf(
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO
        ).map { phase ->
            GameplayTableUi.transitionSpec(transitionSession(phase, round = 1))
        }
        val daySpecs = listOf(
            GamePhase.AMANECER,
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO,
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE,
            GamePhase.RESULTADO
        ).map { phase ->
            GameplayTableUi.transitionSpec(transitionSession(phase, round = 1))
        }

        assertEquals(setOf("NIGHT_1"), nightSpecs.map { it.key }.toSet())
        assertEquals(setOf("DAY_2"), daySpecs.map { it.key }.toSet())
        assertTrue(GameplayTableUi.shouldPresentTransition(nightSpecs.first(), null))
        nightSpecs.drop(1).forEach { spec ->
            assertFalse(GameplayTableUi.shouldPresentTransition(spec, nightSpecs.first().key))
        }
        assertTrue(GameplayTableUi.shouldPresentTransition(daySpecs.first(), nightSpecs.first().key))
        daySpecs.drop(1).forEach { spec ->
            assertFalse(GameplayTableUi.shouldPresentTransition(spec, daySpecs.first().key))
        }
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

    private fun transitionSession(phase: GamePhase, round: Int): GameSession {
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players(5),
            phase = phase,
            round = round
        )
    }
}
