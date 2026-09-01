package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineErrorMessagesTest {

    @Test
    fun `un error de red habla del servidor y no de Firebase`() {
        val message = OnlineErrorMessages.forAction(
            "No se pudo verificar el acceso online",
            IllegalStateException("Firebase client is offline because network is unavailable")
        )

        assertTrue(message.contains("servidor", ignoreCase = true))
        assertFalse(message.contains("Firebase", ignoreCase = true))
    }

    @Test
    fun `un error de permisos no expone reglas internas`() {
        val message = OnlineErrorMessages.forAction(
            "No se pudo completar la accion",
            IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions")
        )

        assertTrue(message.contains("servidor", ignoreCase = true))
        assertFalse(message.contains("firestore", ignoreCase = true))
        assertFalse(message.contains("rules", ignoreCase = true))
    }

    @Test
    fun `un error desconocido no muestra el detalle tecnico`() {
        val message = OnlineErrorMessages.forAction(
            "No se pudo completar la accion",
            IllegalStateException("internal Firebase transport exploded")
        )

        assertTrue(message.contains("error inesperado", ignoreCase = true))
        assertFalse(message.contains("Firebase", ignoreCase = true))
    }
}
