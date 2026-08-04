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

    @Test
    fun testModeAllowsThreePlayersButNormalModeDoesNot() {
        assertEquals(
            LocalGameFactory.TEST_MIN_PLAYERS,
            OnlineRoomFirestore.normalizedExpectedPlayers(1, modePrueba = true)
        )
        assertEquals(
            LocalGameFactory.MIN_PLAYERS,
            OnlineRoomFirestore.normalizedExpectedPlayers(3, modePrueba = false)
        )
    }

    @Test
    fun roomVisibilityAcceptsPrivateAndDefaultsEverythingElseToPublic() {
        assertEquals(
            OnlineRoomFirestore.VISIBILITY_PRIVATE,
            OnlineRoomFirestore.normalizedVisibility(OnlineRoomFirestore.VISIBILITY_PRIVATE)
        )
        assertEquals(
            OnlineRoomFirestore.VISIBILITY_PUBLIC,
            OnlineRoomFirestore.normalizedVisibility(OnlineRoomFirestore.VISIBILITY_PUBLIC)
        )
        assertEquals(
            OnlineRoomFirestore.VISIBILITY_PUBLIC,
            OnlineRoomFirestore.normalizedVisibility("valor_invalido")
        )
    }

    @Test
    fun customRoomNameIsTrimmedAndLegacyFallbackRemainsAvailable() {
        assertEquals(
            "Los futboleros del barrio",
            OnlineRoomFirestore.normalizedRoomName(
                "  Los   futboleros del barrio  ",
                "Mama",
                "2077"
            )
        )
        assertEquals(
            "Sala de Mama 2077",
            OnlineRoomFirestore.normalizedRoomName("   ", "Mama", "2077")
        )
    }
}
