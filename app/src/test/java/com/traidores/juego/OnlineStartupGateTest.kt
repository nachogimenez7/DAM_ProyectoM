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

    @Test
    fun hardStartupTimeoutFiresWhenPlayersAreStuck() {
        val startedAt = 10_000L
        assertEquals(25_000L, OnlineStartupGate.HARD_STARTUP_TIMEOUT_MS)

        // Before 25 seconds: does not fire
        assertFalse(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = startedAt,
                nowEpochMs = 34_999L,
                reportedPlayers = 5,
                roleReadPlayers = 5,
                expectedPlayers = 5
            )
        )

        // At or after 25 seconds with at least minimum players: fires and starts night
        assertTrue(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = startedAt,
                nowEpochMs = 35_000L,
                reportedPlayers = 5,
                roleReadPlayers = 5,
                expectedPlayers = 5
            )
        )

        // No fuerza una partida de 6 con solo 3 clientes presentes.
        assertFalse(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = startedAt,
                nowEpochMs = 35_000L,
                reportedPlayers = 3,
                roleReadPlayers = 3,
                expectedPlayers = 6
            )
        )

        // Tampoco salta por encima de alguien que todavia no termino de leer su rol.
        assertFalse(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = startedAt,
                nowEpochMs = 35_000L,
                reportedPlayers = 6,
                roleReadPlayers = 5,
                expectedPlayers = 6
            )
        )
    }

    @Test
    fun hardTimeoutCanRecoverVisibleRosterMismatchOnlyWhenEveryoneReportedReady() {
        val states = (0 until 5).map { state("p$it", visiblePlayers = 6, roleRead = true) } +
            state("slow", visiblePlayers = 5, roleRead = true)
        val result = OnlineStartupGate.evaluate(expectedPlayers = 6, clientStates = states)

        assertEquals(6, result.reportedPlayers)
        assertEquals(6, result.roleReadPlayers)
        assertEquals(5, result.loadedPlayers)
        assertEquals(1, result.mismatchedPlayers)
        assertTrue(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = 10_000L,
                nowEpochMs = 35_000L,
                reportedPlayers = result.reportedPlayers,
                roleReadPlayers = result.roleReadPlayers,
                expectedPlayers = result.expectedPlayers
            )
        )
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
