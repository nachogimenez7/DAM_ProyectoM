package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStartCallableResponseParserTest {

    @Test
    fun `parsea un inicio nuevo`() {
        val result = OnlineStartCallableResponseParser.parse(
            mapOf(
                "status" to "started",
                "matchId" to "match-123",
                "mapKey" to "pampa"
            )
        ) as OnlineStartCallableResult.Accepted

        assertEquals("match-123", result.matchId)
        assertEquals("pampa", result.mapKey)
        assertFalse(result.alreadyStarted)
    }

    @Test
    fun `parsea un reintento idempotente`() {
        val result = OnlineStartCallableResponseParser.parse(
            mapOf(
                "status" to "already_started",
                "matchId" to "match-existing",
                "mapKey" to "grecia"
            )
        ) as OnlineStartCallableResult.Accepted

        assertTrue(result.alreadyStarted)
    }

    @Test
    fun `filtra el desempate a mapas soportados`() {
        val result = OnlineStartCallableResponseParser.parse(
            mapOf(
                "status" to "tie_break_required",
                "mapKeys" to listOf("pampa", "inventado", "pampa", "medieval")
            )
        ) as OnlineStartCallableResult.MapTieBreakRequired

        assertEquals(listOf("pampa", "medieval"), result.mapKeys)
    }

    @Test
    fun `rechaza payload publico incompleto`() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineStartCallableResponseParser.parse(
                mapOf("status" to "started", "matchId" to "match-123")
            )
        }
    }

    @Test
    fun `rechaza estados desconocidos`() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineStartCallableResponseParser.parse(mapOf("status" to "hack"))
        }
    }
}
