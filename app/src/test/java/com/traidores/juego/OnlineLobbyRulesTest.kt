package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyRulesTest {

    @Test
    fun staleWaitingRoomIsHiddenFromBrowser() {
        val now = 10_000_000L

        assertFalse(
            OnlineLobbyRules.isRoomFresh(
                updatedAtMs = now - 31 * 60 * 1000L,
                nowMs = now,
                maxAgeMs = 30 * 60 * 1000L
            )
        )
    }

    @Test
    fun recentOrPendingServerTimestampRoomRemainsVisible() {
        val now = 10_000_000L

        assertTrue(
            OnlineLobbyRules.isRoomFresh(
                updatedAtMs = now - 5 * 60 * 1000L,
                nowMs = now,
                maxAgeMs = 30 * 60 * 1000L
            )
        )
        assertTrue(OnlineLobbyRules.isRoomFresh(0L, now, 30 * 60 * 1000L))
    }

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
    fun tenConnectedReadyPlayersCanStart() {
        val players = (0 until 10).map { index ->
            participant(
                id = if (index == 0) "host" else "p$index",
                connected = true,
                ready = true,
                active = true,
                order = index
            )
        }

        assertTrue(
            OnlineLobbyRules.canStart(
                players = players,
                expectedPlayers = 10,
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
    fun deadConnectedHostHandsOffToFirstLivingConnectedPlayer() {
        val players = listOf(
            participant("host", connected = true, ready = true, active = true, order = 0, alive = false),
            participant("dead", connected = true, ready = true, active = true, order = 1, alive = false),
            participant("living-b", connected = true, ready = true, active = true, order = 3),
            participant("living-a", connected = true, ready = true, active = true, order = 2)
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(players, activeHostId = "host")

        assertEquals("living-a", candidate?.id)
    }

    @Test
    fun connectedHostIsNotReplacedEvenWhenLocalTimestampLooksStale() {
        val players = listOf(
            participant(
                "host",
                connected = true,
                ready = true,
                active = true,
                order = 0,
                lastSeenLocalMs = 1
            ),
            participant(
                "candidate",
                connected = true,
                ready = true,
                active = true,
                order = 1,
                lastSeenLocalMs = 100_000L
            )
        )

        val candidate = OnlineLobbyRules.hostHandoffCandidate(
            players = players,
            activeHostId = "host"
        )

        assertEquals(null, candidate)
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
        lastSeenLocalMs: Long = 0L,
        alive: Boolean = true
    ): OnlineLobbyParticipant {
        return OnlineLobbyParticipant(
            id = id,
            connected = connected,
            ready = ready,
            activeInMatch = active,
            order = order,
            lastSeenLocalMs = lastSeenLocalMs,
            alive = alive
        )
    }
}
