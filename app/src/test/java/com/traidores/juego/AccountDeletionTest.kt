package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeletionTest {

    @Test
    fun confirmationText_isExplicitAndUppercase() {
        assertEquals("ELIMINAR", AccountDeletion.CONFIRMATION_TEXT)
    }
}
