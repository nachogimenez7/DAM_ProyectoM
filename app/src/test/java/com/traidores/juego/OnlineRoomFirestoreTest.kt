package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRoomFirestoreTest {

    @Test
    fun generatedRoomCodeUsesExpectedAlphabetAndLength() {
        val code = OnlineRoomFirestore.generateRoomCode()

        assertEquals(OnlineRoomFirestore.ROOM_CODE_LENGTH, code.length)
        assertTrue(code.matches(Regex("^[A-HJ-NP-Z2-9]{6}$")))
    }
}
