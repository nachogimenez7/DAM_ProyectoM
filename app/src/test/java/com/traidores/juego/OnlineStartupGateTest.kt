package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStartupGateTest {

    @Test
    fun allPlayersLoadedAndReadCanStart() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = (0 until 5).map { state("p$it", roleRead = true) }
        )

        assertTrue(result.canStart)
        assertTrue(result.canArmAutoStart)
        assertEquals(5, result.loadedPlayers)
        assertEquals(5, result.readyPlayers)
    }

    @Test
    fun tenLoadedPlayersCanStartWithoutChangingTheGate() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 10,
            clientStates = (0 until 10).map {
                state("p$it", visiblePlayers = 10, roleRead = true)
            }
        )

        assertTrue(result.canStart)
        assertTrue(result.canArmAutoStart)
        assertEquals(10, result.loadedPlayers)
        assertEquals(10, result.readyPlayers)
        assertEquals(0, result.missingPlayers)
    }

    @Test
    fun missingPlayerBlocksStart() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = (0 until 4).map { state("p$it", roleRead = true) }
        )

        assertFalse(result.canStart)
        assertFalse(result.canArmAutoStart)
        assertEquals(1, result.missingPlayers)
    }

    @Test
    fun mismatchedVisibleCardsBlocksStart() {
        val states = (0 until 4).map { state("p$it", roleRead = true) } +
            state("slow", visiblePlayers = 3, roleRead = true)

        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = states
        )

        assertFalse(result.canStart)
        assertFalse(result.canArmAutoStart)
        assertEquals(1, result.mismatchedPlayers)
        assertEquals("Sincronizando cartas...", result.waitingMessage)
    }

    @Test
    fun unreadRoleBlocksStart() {
        val states = (0 until 4).map { state("p$it", roleRead = true) } +
            state("reading", roleRead = false)

        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = states
        )

        assertFalse(result.canStart)
        assertTrue(result.canArmAutoStart)
        assertEquals(5, result.loadedPlayers)
        assertEquals(4, result.readyPlayers)
        assertEquals("Esperando lectura de roles...", result.waitingMessage)
    }

    @Test
    fun sharedDeadlineExpiresAfterFifteenSeconds() {
        val deadline = 30_000L

        assertEquals(15_000L, OnlineStartupGate.AUTO_START_AFTER_MS)
        assertEquals(1_000L, OnlineStartupGate.remainingAutoStartMillis(deadline, 29_000L))
        assertFalse(OnlineStartupGate.shouldAutoStart(deadline, 29_999L))
        assertTrue(OnlineStartupGate.shouldAutoStart(deadline, 30_000L))
        assertEquals(0L, OnlineStartupGate.remainingAutoStartMillis(deadline, 31_000L))
    }

    @Test
    fun missingDeadlineDoesNotAccidentallyStart() {
        assertNull(OnlineStartupGate.remainingAutoStartMillis(0L, 30_000L))
        assertFalse(OnlineStartupGate.shouldAutoStart(0L, 30_000L))
    }

    @Test
    fun nonRepartoStatesDoNotCountForStartup() {
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = 5,
            clientStates = listOf(
                state("host", roleRead = true),
                state("night", phase = GamePhase.NOCHE_ASESINO.name, phaseIndex = 1, roleRead = true)
            )
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
