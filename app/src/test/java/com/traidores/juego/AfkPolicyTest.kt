package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AfkPolicyTest {
    @Test
    fun firstNightMissExplainsTheSecondConsecutiveMissRule() {
        val message = AfkPolicy.warning(AfkOpportunity.NIGHT, expulsionEnabled = true)

        assertTrue(message.contains("acción"))
        assertTrue(message.contains("próxima noche"))
        assertTrue(message.contains("expulsado por AFK"))
        assertEquals(2, AfkPolicy.CONSECUTIVE_MISSES_BEFORE_EXPULSION)
    }

    @Test
    fun voteMissWithoutAfkExpulsionOnlyLosesCurrentVote() {
        assertEquals(
            "Perdiste tu voto de esta ronda.",
            AfkPolicy.warning(AfkOpportunity.VOTE, expulsionEnabled = false)
        )
    }

    @Test
    fun selfExpulsionAddressesTheLocalPlayerWithoutRepeatingTheirName() {
        assertEquals(
            "Fuiste expulsado por permanecer inactivo durante dos oportunidades consecutivas.",
            AfkPolicy.selfExpelledMessage()
        )
    }
}
