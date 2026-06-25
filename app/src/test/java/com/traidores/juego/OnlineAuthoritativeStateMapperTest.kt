package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineAuthoritativeStateMapperTest {

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
}
