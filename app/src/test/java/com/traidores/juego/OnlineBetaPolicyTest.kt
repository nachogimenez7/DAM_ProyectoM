package com.traidores.juego
import org.junit.Test
import org.junit.Assert.*
class OnlineBetaPolicyTest {
    @Test fun rolloutIsClosedWithoutValidConfigAndDoesNotFallback() {
        assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(true, null,52))
        assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(false,52,52))
        assertEquals(OnlineBetaPolicy.Access.UPDATE, OnlineBetaPolicy.access(true,52,51))
        assertEquals(OnlineBetaPolicy.Access.OPEN, OnlineBetaPolicy.access(true,52,52))
    }
    @Test fun privatePilotOnlyOpensForTheAllowlistedFirebaseUser() {
        assertEquals(OnlineBetaPolicy.Access.OPEN, OnlineBetaPolicy.access(true,52,52,"pilot",listOf("pilot")))
        assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(true,52,52,"other",listOf("pilot")))
        assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(true,52,52,null,listOf("pilot")))
        assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(false,52,52,"pilot",listOf("pilot")))
    }
    @Test fun emptyOrMalformedAllowlistCannotOpenThePilotToEveryone() {
        for (value in listOf(emptyList<String>(), "pilot", listOf(7), listOf("pilot", ""))) {
            assertEquals(OnlineBetaPolicy.Access.MAINTENANCE, OnlineBetaPolicy.access(true,52,52,"pilot",value))
        }
        // Public opening requires the administrator to remove the optional list.
        assertEquals(OnlineBetaPolicy.Access.OPEN, OnlineBetaPolicy.access(true,52,52,"other",null))
    }
}
