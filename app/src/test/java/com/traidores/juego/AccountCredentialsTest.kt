package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountCredentialsTest {

    @Test
    fun validDataDoesNotComplain() {
        assertNull(AccountCredentials.validationError("nacho@correo.com", "secreto123"))
    }

    @Test
    fun surroundingSpacesInTheEmailAreForgiven() {
        assertNull(AccountCredentials.validationError("  nacho@correo.com  ", "secreto123"))
        assertEquals("nacho@correo.com", AccountCredentials.normalizeEmail("  nacho@correo.com  "))
    }

    @Test
    fun anEmailWithoutDomainIsRejected() {
        assertNotNull(AccountCredentials.validationError("nacho@correo", "secreto123"))
        assertNotNull(AccountCredentials.validationError("nacho", "secreto123"))
    }

    @Test
    fun emptyFieldsAreRejected() {
        assertNotNull(AccountCredentials.validationError("", "secreto123"))
        assertNotNull(AccountCredentials.validationError("nacho@correo.com", ""))
    }

    @Test
    fun aShortPasswordIsRejectedBeforeAskingFirebase() {
        val error = AccountCredentials.validationError("nacho@correo.com", "12345")

        assertNotNull(error)
        assertEquals(true, error!!.contains(AccountCredentials.MIN_PASSWORD_LENGTH.toString()))
    }
}
