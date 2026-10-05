package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class VoteRecountPresentationTest {
    private fun session() = GameSession("TEST", "pampa", "Pampa", listOf(
        GamePlayer("Alcalde", "A", RoleCatalog.gameRole(RoleCatalog.ALCALDE, RoleMap.PAMPA), isHuman = true),
        GamePlayer("Payador", "P", RoleCatalog.gameRole(RoleCatalog.PAYADOR, RoleMap.PAMPA)),
        GamePlayer("Pueblo", "V", RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.PAMPA))
    ))

    @Test fun revealedMayorHasTwoSealsAndContrapuntoUsesPayadorIdentity() {
        val game = session().copy(votes = mapOf("Alcalde" to "Pueblo", "Payador" to "Alcalde"),
            alcaldeRevealed = true, contrapuntoSuspicion = "Pueblo")
        val seals = VoteRecountPresentation.tokens(game)
        assertEquals(2, seals.count { it.voterName == "Alcalde" && it.targetName == "Pueblo" })
        assertTrue(seals.any { it.voterName == "Payador" && it.targetName == "Pueblo" })
        assertEquals(4, seals.size)
        assertEquals(listOf("Alcalde", "Pueblo"), VoteRecountPresentation.candidates(game).map { it.name })
    }

    @Test fun hiddenMayorDoesNotAddASecondSeal() {
        val game = session().copy(votes = mapOf("Alcalde" to "Pueblo"))
        assertEquals(1, VoteRecountPresentation.tokens(game).size)
    }

    @Test fun mayorDecisionShowsOnlyChosenCandidateAndNoSealsFromPreviousTie() {
        val game = session().copy(voteRound = 3, dayEliminationTarget = "Pueblo", alcaldeRevealed = true,
            votes = mapOf("Alcalde" to "Payador", "Payador" to "Alcalde"), contrapuntoSuspicion = "Payador")
        assertTrue(VoteRecountPresentation.tokens(game).isEmpty())
        assertEquals(listOf("Pueblo"), VoteRecountPresentation.candidates(game).map { it.name })
    }
}
