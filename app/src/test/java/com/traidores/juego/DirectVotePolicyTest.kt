package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectVotePolicyTest {
    @Test
    fun `solo la votacion comun y el desempate usan voto directo`() {
        assertTrue(DirectVotePolicy.isEnabled(GamePhase.VOTACION))
        assertTrue(DirectVotePolicy.isEnabled(GamePhase.DESEMPATE_VOTACION))
        assertFalse(DirectVotePolicy.isEnabled(GamePhase.NOCHE_ASESINO))
        assertFalse(DirectVotePolicy.isEnabled(GamePhase.ALCALDE_DESEMPATE))
    }

    @Test
    fun `la seleccion puede cambiar localmente hasta confirmar`() {
        assertTrue(DirectVotePolicy.canSelect("", "Mora", confirmed = false))
        assertTrue(DirectVotePolicy.canSelect("Mora", "Dina", confirmed = false))
        assertFalse(DirectVotePolicy.canSelect("Mora", "Mora", confirmed = false))
        assertFalse(DirectVotePolicy.canSelect("Mora", "Dina", confirmed = true))
        assertTrue(DirectVotePolicy.shouldConfirm("Mora", "Mora", confirmed = false))
        assertFalse(DirectVotePolicy.shouldConfirm("Mora", "Dina", confirmed = false))
        assertFalse(DirectVotePolicy.shouldConfirm("Mora", "Mora", confirmed = true))
        assertEquals("Dina", DirectVotePolicy.select("Dina"))
    }

    @Test
    fun `el temporizador solo usa un voto confirmado`() {
        assertEquals("Mora", DirectVotePolicy.timeoutTarget(GamePhase.VOTACION, "Mora", true))
        assertEquals("Dina", DirectVotePolicy.timeoutTarget(GamePhase.DESEMPATE_VOTACION, "Dina", true))
        assertEquals("", DirectVotePolicy.timeoutTarget(GamePhase.VOTACION, "Mora", false))
        assertEquals("", DirectVotePolicy.timeoutTarget(GamePhase.NOCHE_POLICIA, "Mora", true))
    }
}
