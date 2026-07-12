package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomDisplayNamesTest {

    @Test
    fun publicIdIsNotAppendedToVisibleName() {
        assertEquals("Jugador1", RoomDisplayNames.withPublicId("Jugador1", "983657"))
    }

    @Test
    fun legacyNumericSuffixIsRemoved() {
        assertEquals("Jugador1", RoomDisplayNames.withoutPublicId("Jugador1 #983657"))
    }

    @Test
    fun hashInsideARegularNameIsPreserved() {
        assertEquals("Equipo #Norte", RoomDisplayNames.withoutPublicId("Equipo #Norte"))
    }
}
