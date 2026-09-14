package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyEntryGateTest {

    @Test
    fun presentationUsesACommonFutureWindow() {
        val releasedAt = 10_000L

        assertEquals(2_500L, OnlineLobbyEntryGate.presentationStartDelayMs(releasedAt, 10_000L))
        assertEquals(500L, OnlineLobbyEntryGate.presentationStartDelayMs(releasedAt, 12_000L))
        assertEquals(0L, OnlineLobbyEntryGate.presentationStartDelayMs(releasedAt, 20_000L))
        assertEquals(
            OnlineLobbyEntryGate.PRESENTATION_LEAD_MS,
            OnlineLobbyEntryGate.presentationStartDelayMs(0L, 20_000L)
        )
    }

    @Test
    fun returningFromFinishedMatchResetsEntryBarrierForRematch() {
        assertTrue(
            OnlineLobbyEntryGate.shouldResetForWaitingLobby(
                previousState = OnlineLobbyRules.ROOM_STATE_FINISHED,
                currentState = OnlineLobbyRules.ROOM_STATE_WAITING
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.shouldResetForWaitingLobby(
                previousState = OnlineLobbyRules.ROOM_STATE_WAITING,
                currentState = OnlineLobbyRules.ROOM_STATE_WAITING
            )
        )
    }

    @Test
    fun noClientEntersUntilTheCurrentMatchIsExplicitlyReleased() {
        assertFalse(OnlineLobbyEntryGate.isReleased("match-actual", ""))
        assertFalse(OnlineLobbyEntryGate.isReleased("match-actual", "match-anterior"))
        assertFalse(OnlineLobbyEntryGate.isReleased("", ""))
        assertTrue(OnlineLobbyEntryGate.isReleased("match-actual", "match-actual"))
    }

    @Test
    fun releasesOnlyWhenEveryExpectedPlayerAcknowledgedTheCurrentMatch() {
        val states = mapOf(
            "host" to readyState("match-actual"),
            "guest-1" to readyState("match-actual"),
            "guest-2" to readyState("match-actual")
        )

        assertTrue(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest-1", "guest-2"),
                matchId = "match-actual",
                clientStates = states
            )
        )
    }

    @Test
    fun staleOrMissingAcknowledgementsKeepTheBarrierClosed() {
        val states = mapOf(
            "host" to readyState("match-actual"),
            "guest-1" to readyState("match-anterior")
        )

        assertFalse(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest-1", "guest-2"),
                matchId = "match-actual",
                clientStates = states
            )
        )
    }

    @Test
    fun timeoutCannotSplitThePlayersAcrossLobbyAndGameplay() {
        assertFalse(
            OnlineLobbyEntryGate.canRelease(
                expectedPlayerIds = setOf("host", "guest"),
                matchId = "match-actual",
                clientStates = mapOf("host" to readyState("match-actual"))
            )
        )
    }

    @Test
    fun largeRoomCannotReleaseWithThreeQuarterQuorum() {
        val expected = (1..14).mapTo(linkedSetOf()) { "p$it" }
        val elevenReady = (1..11).associate { "p$it" to readyState("match-actual") }

        assertFalse(
            OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = elevenReady,
                localPlayerId = "p1",
                localPlayerReady = true
            )
        )
    }

    @Test
    fun threePlayerRoomWaitsWhenOneAckNeverArrives() {
        val expected = setOf("host", "guest-1", "guest-2")
        val oneGuestReady = mapOf("guest-1" to readyState("match-actual"))

        assertFalse(
            OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = oneGuestReady,
                localPlayerId = "host",
                localPlayerReady = true
            )
        )
    }

    @Test
    fun fullyConnectedRoomStillRequiresEveryAck() {
        val expected = setOf("host", "guest-1", "guest-2")

        assertFalse(
            OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = emptyMap(),
                localPlayerId = "host",
                localPlayerReady = true
            )
        )
    }

    @Test
    fun guestRepublishesWhenAStartupCleanupRemovedItsSuccessfulAck() {
        assertFalse(
            OnlineLobbyEntryGate.shouldPublishAcknowledgement(
                playerId = "guest",
                matchId = "match-actual",
                clientStates = mapOf("guest" to readyState("match-actual")),
                publishInProgress = false
            )
        )
        assertTrue(
            OnlineLobbyEntryGate.shouldPublishAcknowledgement(
                playerId = "guest",
                matchId = "match-actual",
                clientStates = emptyMap(),
                publishInProgress = false
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.shouldPublishAcknowledgement(
                playerId = "guest",
                matchId = "match-actual",
                clientStates = emptyMap(),
                publishInProgress = true
            )
        )
    }

    @Test
    fun guestWaitsUntilHostPublishedItsRealtimeMatchAccess() {
        assertFalse(OnlineLobbyEntryGate.isRealtimeMatchAccessReady(active = null, inLobby = null))
        assertFalse(OnlineLobbyEntryGate.isRealtimeMatchAccessReady(active = true, inLobby = true))
        assertFalse(OnlineLobbyEntryGate.isRealtimeMatchAccessReady(active = false, inLobby = false))
        assertTrue(OnlineLobbyEntryGate.isRealtimeMatchAccessReady(active = true, inLobby = false))
    }

    @Test
    fun locallyReadyHostCanReplaceOnlyItsOwnMissingRealtimeEcho() {
        val expected = setOf("host", "guest-1", "guest-2")
        val guestStates = mapOf(
            "guest-1" to readyState("match-actual"),
            "guest-2" to readyState("match-actual")
        )

        assertTrue(
            OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = guestStates,
                localPlayerId = "host",
                localPlayerReady = true
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = mapOf("guest-1" to readyState("match-actual")),
                localPlayerId = "host",
                localPlayerReady = true
            )
        )
    }

    private fun readyState(matchId: String): Map<String, Any?> {
        return mapOf(
            OnlineLobbyEntryGate.FIELD_MATCH_ID to matchId,
            OnlineLobbyEntryGate.FIELD_ENTRY_READY to true
        )
    }
}
