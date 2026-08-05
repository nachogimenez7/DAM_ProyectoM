package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayPhasePresentationTest {
    @Test
    fun nightActionLabelReflectsWhetherTheHumanCanAct() {
        val active = GameplayPhasePresentation.phaseText(
            GamePhase.NOCHE_POLICIA,
            round = 3,
            winnerPresent = false,
            nightSubtitle = "Pista",
            humanRoleTurn = true
        )
        val waiting = GameplayPhasePresentation.phaseText(
            GamePhase.NOCHE_POLICIA,
            round = 3,
            winnerPresent = false,
            nightSubtitle = "Pista",
            humanRoleTurn = false
        )

        assertEquals("NOCHE 3", active.title)
        assertEquals("Pista", active.subtitle)
        assertEquals("INVESTIGAR", active.actionLabel)
        assertEquals("ESPERAR", waiting.actionLabel)
    }

    @Test
    fun resultOnlyShowsFinalWhenThereIsAWinner() {
        val ongoing = GameplayPhasePresentation.phaseText(
            GamePhase.RESULTADO,
            round = 2,
            winnerPresent = false,
            nightSubtitle = "",
            humanRoleTurn = false
        )
        val finished = GameplayPhasePresentation.phaseText(
            GamePhase.RESULTADO,
            round = 2,
            winnerPresent = true,
            nightSubtitle = "",
            humanRoleTurn = false
        )

        assertEquals("CONTINUAR", ongoing.actionLabel)
        assertEquals("FINAL", finished.actionLabel)
    }

    @Test
    fun everyMapKeepsFiveDeterministicPassiveNightMessages() {
        listOf("pampa", "grecia", "medieval").forEach { mapKey ->
            assertEquals(5, GameplayPhasePresentation.passiveNightMessages(mapKey).size)
            val first = GameplayPhasePresentation.passiveNightMessage(
                mapKey,
                round = 1,
                phaseIndex = 4,
                phase = GamePhase.NOCHE_MEDICO
            )
            val repeated = GameplayPhasePresentation.passiveNightMessage(
                mapKey,
                round = 1,
                phaseIndex = 4,
                phase = GamePhase.NOCHE_MEDICO
            )
            assertEquals(first, repeated)
        }
        assertNotEquals(
            GameplayPhasePresentation.passiveNightMessages("pampa").first(),
            GameplayPhasePresentation.passiveNightMessages("grecia").first()
        )
    }

    @Test
    fun exclusiveMapRolesKeepTheirGameplayExplanation() {
        assertTrue(GameplayPhasePresentation.roleFunction(RoleCatalog.PAYADOR).contains("Contrapunto"))
        assertTrue(GameplayPhasePresentation.roleFunction(RoleCatalog.ORACULO).contains("invocar"))
        assertTrue(GameplayPhasePresentation.roleFunction(RoleCatalog.ESPIA).contains("inocente"))
    }
}
