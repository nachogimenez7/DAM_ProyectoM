package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestIdentityTest {

    @Test
    fun `todo alias de la lista arma un nombre valido`() {
        GuestIdentity.aliases.forEach { alias ->
            assertTrue(
                "El alias '$alias' no pasa la validacion de nombre de invitado",
                GuestIdentity.isValidGuestName("$alias 4821")
            )
        }
    }

    @Test
    fun `ningun alias supera el limite de 18 caracteres con el numero`() {
        GuestIdentity.aliases.forEach { alias ->
            val name = "$alias 4821"
            assertTrue(
                "'$name' supera los 18 caracteres que aceptan las reglas",
                name.length <= 18
            )
        }
    }

    @Test
    fun `un nombre libre no pasa como invitado`() {
        assertFalse(GuestIdentity.isValidGuestName("Nacho"))
        assertFalse(GuestIdentity.isValidGuestName("Forastero"))
        assertFalse(GuestIdentity.isValidGuestName("Forastero 12"))
        assertFalse(GuestIdentity.isValidGuestName("Insulto 4821"))
        assertFalse(GuestIdentity.isValidGuestName("Forastero 4821 "))
        assertFalse(GuestIdentity.isValidGuestName("xForastero 4821"))
    }

    @Test
    fun `el numero es estable para el mismo uid y tiene cuatro digitos`() {
        val uid = "abc123def456ghi789jkl"
        val first = GuestIdentity.numberFor(uid)
        assertEquals(first, GuestIdentity.numberFor(uid))
        assertEquals(4, first.length)
        assertTrue(first.all { it.isDigit() })
    }
}
