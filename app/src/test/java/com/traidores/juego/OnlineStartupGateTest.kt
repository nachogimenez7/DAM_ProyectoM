package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStartupGateTest {

    @Test
    fun allPlayersLoadedAndReadCanStart() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = (0 until 5).map { state("p$it", roleRead = true) },
            elapsedMs = 1_000L
        )

        assertTrue(result.canStart)
        assertFalse(result.canForce)
        assertEquals(5, result.loadedPlayers)
        assertEquals(5, result.readyPlayers)
    }

    @Test
    fun missingPlayerBlocksStart() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = (0 until 4).map { state("p$it", roleRead = true) },
            elapsedMs = 1_000L
        )

        assertFalse(result.canStart)
        assertFalse(result.canForce)
        assertEquals(1, result.missingPlayers)
    }

    @Test
    fun mismatchedVisibleCardsBlocksStart() {
        val states = (0 until 4).map { state("p$it", roleRead = true) } +
            state("slow", visiblePlayers = 3, roleRead = true)

        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = states,
            elapsedMs = 1_000L
        )

        assertFalse(result.canStart)
        assertEquals(1, result.mismatchedPlayers)
        assertEquals("Sincronizando cartas...", result.waitingMessage)
    }

    @Test
    fun unreadRoleBlocksStart() {
        val states = (0 until 4).map { state("p$it", roleRead = true) } +
            state("reading", roleRead = false)

        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = states,
            elapsedMs = 1_000L
        )

        assertFalse(result.canStart)
        assertEquals(5, result.loadedPlayers)
        assertEquals(4, result.readyPlayers)
        assertEquals("Esperando lectura de roles...", result.waitingMessage)
    }

    @Test
    fun hostCanForceAfterTimeout() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = listOf(state("host", roleRead = true)),
            elapsedMs = OnlineStartupGate.STARTUP_FORCE_AFTER_MS
        )

        assertFalse(result.canStart)
        assertTrue(result.canForce)
    }

    @Test
    fun nonRepartoStatesDoNotCountForStartup() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = listOf(
                state("host", roleRead = true),
                state("night", phase = GamePhase.NOCHE_ASESINO.name, phaseIndex = 1, roleRead = true)
            ),
            elapsedMs = 1_000L
        )

        assertFalse(result.canStart)
        assertEquals(1, result.loadedPlayers)
    }

    private fun state(
        uid: String,
        visiblePlayers: Int = 5,
        phase: String = GamePhase.REPARTO.name,
        phaseIndex: Int = 0,
        roleRead: Boolean,
        inGameplay: Boolean = true
    ): OnlineStartupClientState {
        return OnlineStartupClientState(
            uid = uid,
            inGameplay = inGameplay,
            visiblePlayers = visiblePlayers,
            phase = phase,
            phaseIndex = phaseIndex,
            roleRead = roleRead,
            order = uid.removePrefix("p").toIntOrNull() ?: 0
        )
    }
}
