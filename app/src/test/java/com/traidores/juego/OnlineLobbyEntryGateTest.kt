package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyEntryGateTest {

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
    fun largeRoomCanReleaseAfterTimeoutWithThreeQuarterQuorum() {
        val expected = (1..14).mapTo(linkedSetOf()) { "p$it" }
        val elevenReady = (1..11).associate { "p$it" to readyState("match-actual") }

        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = elevenReady,
                localPlayerId = "p1",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS - 1L
            )
        )
        assertTrue(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = elevenReady,
                localPlayerId = "p1",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
    }

    @Test
    fun threePlayerRoomRecoversAfterTimeoutWhenOneAckNeverArrives() {
        val expected = setOf("host", "guest-1", "guest-2")
        val oneGuestReady = mapOf("guest-1" to readyState("match-actual"))

        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = oneGuestReady,
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS - 1L
            )
        )
        assertTrue(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = oneGuestReady,
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = oneGuestReady,
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = setOf("host", "guest-1"),
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = emptyMap(),
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
    }

    @Test
    fun fullyConnectedRoomCannotRemainBlockedIfEphemeralAcksDisappear() {
        val expected = setOf("host", "guest-1", "guest-2")

        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = emptyMap(),
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.FULLY_CONNECTED_RELEASE_AFTER_MS - 1L
            )
        )
        assertTrue(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = emptyMap(),
                localPlayerId = "host",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.FULLY_CONNECTED_RELEASE_AFTER_MS
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
    fun timeoutStillRejectsTenOfFourteenOrTooFewConnected() {
        val expected = (1..14).mapTo(linkedSetOf()) { "p$it" }
        val tenReady = (1..10).associate { "p$it" to readyState("match-actual") }
        val elevenReady = (1..11).associate { "p$it" to readyState("match-actual") }

        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = tenReady,
                localPlayerId = "p1",
                localPlayerReady = true,
                connectedPlayerIds = expected,
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
        assertFalse(
            OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expected,
                matchId = "match-actual",
                clientStates = elevenReady,
                localPlayerId = "p1",
                localPlayerReady = true,
                connectedPlayerIds = (1..10).mapTo(linkedSetOf()) { "p$it" },
                elapsedMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS
            )
        )
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
