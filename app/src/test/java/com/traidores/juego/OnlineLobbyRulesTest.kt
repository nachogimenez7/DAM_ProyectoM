package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyRulesTest {

    @Test
    fun activePlayersExcludeReleasedDisconnectedSlots() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0),
            participant("gone", connected = false, ready = false, active = false, order = 1),
            participant("ready", connected = true, ready = true, active = true, order = 2)
        )

        assertEquals(listOf("host", "ready"), OnlineLobbyRules.activePlayers(players).map { it.id })
    }

    @Test
    fun disconnectedActivePlayersAreReleasable() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0),
            participant("gone", connected = false, ready = false, active = true, order = 1),
            participant("old", connected = false, ready = false, active = false, order = 2)
        )

        assertEquals(listOf("gone"), OnlineLobbyRules.releasableDisconnectedPlayers(players).map { it.id })
    }

    @Test
    fun releasedDisconnectedPlayersDoNotBlockStart() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0),
            participant("a", connected = true, ready = true, active = true, order = 1),
            participant("b", connected = true, ready = true, active = true, order = 2),
            participant("c", connected = true, ready = true, active = true, order = 3),
            participant("d", connected = true, ready = true, active = true, order = 4),
            participant("released", connected = false, ready = false, active = false, order = 5)
        )

        assertTrue(
            OnlineLobbyRules.canStart(
                players = players,
                expectedPlayers = 5,
                roomWaiting = true,
                initialMatchCreated = false
            )
        )
    }

    @Test
    fun activeDisconnectedPlayerBlocksStartUntilReleased() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0),
            participant("a", connected = true, ready = true, active = true, order = 1),
            participant("b", connected = true, ready = true, active = true, order = 2),
            participant("c", connected = true, ready = true, active = true, order = 3),
            participant("gone", connected = false, ready = false, active = true, order = 4)
        )

        assertFalse(
            OnlineLobbyRules.canStart(
                players = players,
                expectedPlayers = 5,
                roomWaiting = true,
                initialMatchCreated = false
            )
        )
    }

    @Test
    fun handoffCandidateIsFirstConnectedActivePlayerWhenHostDisconnects() {
        val players = listOf(
            participant("host", connected = false, ready = false, active = true, order = 0),
            participant("candidate-b", connected = true, ready = true, active = true, order = 2),
            participant("candidate-a", connected = true, ready = true, active = true, order = 1)
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(players, activeHostId = "host")

        assertEquals("candidate-a", candidate?.id)
    }

    @Test
    fun connectedHostDoesNotNeedHandoff() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0),
            participant("candidate", connected = true, ready = true, active = true, order = 1)
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(players, activeHostId = "host")

        assertEquals(null, candidate)
    }

    @Test
    fun staleConnectedHostCanBeReplacedByFreshCandidate() {
        val now = 100_000L
        val players = listOf(
            participant(
                "host",
                connected = true,
                ready = true,
                active = true,
                order = 0,
                lastSeenLocalMs = now - OnlineLobbyRules.HOST_STALE_AFTER_MS - 1
            ),
            participant(
                "candidate",
                connected = true,
                ready = true,
                active = true,
                order = 1,
                lastSeenLocalMs = now
            )
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(
            players = players,
            activeHostId = "host",
            nowMs = now
        )

        assertEquals("candidate", candidate?.id)
    }

    @Test
    fun releasedHostDoesNotBecomeHandoffCandidate() {
        val players = listOf(
            participant("host", connected = false, ready = false, active = true, order = 0),
            participant("released", connected = true, ready = true, active = false, order = 1),
            participant("candidate", connected = true, ready = true, active = true, order = 2)
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(players, activeHostId = "host")

        assertEquals("candidate", candidate?.id)
    }

    private fun participant(
        id: String,
        connected: Boolean,
        ready: Boolean,
        active: Boolean,
        order: Int,
        lastSeenLocalMs: Long = 0L
    ): OnlineLobbyParticipant {
        return OnlineLobbyParticipant(
            id = id,
            connected = connected,
            ready = ready,
            activeInMatch = active,
            order = order,
            lastSeenLocalMs = lastSeenLocalMs
        )
    }
}
