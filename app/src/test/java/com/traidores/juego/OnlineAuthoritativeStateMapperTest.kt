package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAuthoritativeStateMapperTest {

    @Test
    fun publicPresentationFieldsAreReadFromAuthoritativeState() {
        val state = mapOf(
            "nocheSinVictima" to true,
            "presentacionVotacion" to "expulsion|2|1|18|Ana"
        )

        assertTrue(OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(state))
        assertEquals(
            "expulsion|2|1|18|Ana",
            OnlineAuthoritativeStateMapper.votePresentationFromState(state)
        )
    }

    @Test
    fun missingPublicPresentationFieldsUseSafeDefaults() {
        assertFalse(OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(emptyMap()))
        assertEquals("", OnlineAuthoritativeStateMapper.votePresentationFromState(emptyMap()))
    }

    @Test
    fun playersFromStateUsesOrderForDuplicateNames() {
        val players = listOf(
            GamePlayer(name = "Federico", initial = "F", alive = true, muted = false),
            GamePlayer(name = "Federico", initial = "F", alive = true, muted = false),
            GamePlayer(name = "Ana", initial = "A", alive = true, muted = false)
        )
        val state = mapOf(
            "jugadores" to listOf(
                mapOf("orden" to 0, "nombre" to "Federico", "vivo" to false, "muteado" to false),
                mapOf("orden" to 1, "nombre" to "Federico", "vivo" to true, "muteado" to true),
                mapOf("orden" to 2, "nombre" to "Ana", "vivo" to true, "muteado" to false)
            )
        )

        val mapped = OnlineAuthoritativeStateMapper.playersFromState(players, state)!!

        assertEquals(false, mapped[0].alive)
        assertEquals(false, mapped[0].muted)
        assertEquals(true, mapped[1].alive)
        assertEquals(true, mapped[1].muted)
        assertEquals(true, mapped[2].alive)
    }

    @Test
    fun playersFromStateRestoresAfkStreaksAndDeathCause() {
        val players = listOf(
            GamePlayer(name = "Ana", initial = "A"),
            GamePlayer(name = "Bruno", initial = "B")
        )
        val state = mapOf(
            "jugadores" to listOf(
                mapOf(
                    "orden" to 0,
                    "afkNoche" to 1,
                    "afkVoto" to 0,
                    "causaEliminacion" to DeathCause.NONE.name
                ),
                mapOf(
                    "orden" to 1,
                    "vivo" to false,
                    "afkNoche" to 0,
                    "afkVoto" to 2,
                    "causaEliminacion" to DeathCause.AFK.name
                )
            )
        )

        val mapped = OnlineAuthoritativeStateMapper.playersFromState(players, state)!!

        assertEquals(1, mapped[0].consecutiveNightAfk)
        assertEquals(2, mapped[1].consecutiveVoteAfk)
        assertEquals(DeathCause.AFK, mapped[1].deathCause)
    }
}
