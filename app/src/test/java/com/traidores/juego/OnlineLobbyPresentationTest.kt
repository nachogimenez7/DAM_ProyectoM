package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLobbyPresentationTest {
    @Test
    fun `native player scrollbar stays disabled for every supported room size`() {
        (5..15).forEach { playerSlots ->
            assertFalse(
                "Native scrollbar must stay disabled for $playerSlots player rooms",
                OnlineLobbyPresentation.shouldShowNativePlayerScrollBar(playerSlots)
            )
        }
    }

    @Test
    fun `new room shows player occupancy progress`() {
        val state = startState(activePlayers = 1, expectedPlayers = 5, missingReady = 1)

        assertEquals(OnlineLobbyStartCopy.WAITING, state.buttonCopy)
        assertFalse(state.isGold)
        assertEquals(
            OnlineLobbyStartProgress(OnlineLobbyProgressKind.PLAYERS, current = 1, total = 5),
            state.progress
        )
        assertEquals(4, OnlineLobbyPresentation.emptySlotCount(5, 1))
    }

    @Test
    fun `ready host can verify authoritative state when local list lags`() {
        val state = startState(
            activePlayers = 5,
            expectedPlayers = 5,
            missingReady = 3,
            currentReady = true
        )

        assertEquals(OnlineLobbyStartCopy.VERIFY_READY, state.buttonCopy)
        assertFalse(state.isGold)
        assertEquals(
            OnlineLobbyStartProgress(OnlineLobbyProgressKind.READY, current = 2, total = 5),
            state.progress
        )
    }

    @Test
    fun `full room switches progress from occupancy to ready players`() {
        val state = startState(activePlayers = 5, expectedPlayers = 5, missingReady = 2)

        assertEquals(OnlineLobbyStartCopy.HOST_READY, state.buttonCopy)
        assertTrue(state.isGold)
        assertEquals(
            OnlineLobbyStartProgress(OnlineLobbyProgressKind.READY, current = 3, total = 5),
            state.progress
        )
    }

    @Test
    fun `all ready enables gold online start without progress`() {
        val state = startState(
            activePlayers = 5,
            expectedPlayers = 5,
            missingReady = 0,
            canStart = true
        )

        assertEquals(OnlineLobbyStartCopy.START_ONLINE, state.buttonCopy)
        assertTrue(state.isGold)
        assertNull(state.progress)
    }

    @Test
    fun `play with present remains an actionable gold state`() {
        val state = startState(
            activePlayers = 5,
            expectedPlayers = 8,
            missingReady = 0,
            canStartWithPresent = true
        )

        assertEquals(OnlineLobbyStartCopy.PLAY_WITH_PRESENT, state.buttonCopy)
        assertTrue(state.isGold)
        assertNull(state.progress)
    }

    @Test
    fun `guest ready action is gold while undo ready stays dark`() {
        val readyAction = startState(
            activePlayers = 3,
            expectedPlayers = 5,
            missingReady = 2,
            isHost = false
        )
        val undoReadyAction = startState(
            activePlayers = 3,
            expectedPlayers = 5,
            missingReady = 2,
            isHost = false,
            currentReady = true
        )

        assertEquals(OnlineLobbyStartCopy.READY, readyAction.buttonCopy)
        assertEquals(OnlineLobbyStartCopy.NOT_READY, undoReadyAction.buttonCopy)
        assertTrue(readyAction.isGold)
        assertFalse(undoReadyAction.isGold)
        assertNull(readyAction.progress)
        assertNull(undoReadyAction.progress)
    }

    @Test
    fun `ready guest waits for host when the complete room is ready`() {
        val state = startState(
            activePlayers = 5,
            expectedPlayers = 5,
            missingReady = 0,
            isHost = false,
            currentReady = true
        )

        assertEquals(OnlineLobbyStartCopy.WAITING_HOST, state.buttonCopy)
        assertFalse(state.isGold)
        assertTrue(
            OnlineLobbyPresentation.shouldLockGuestReadyToggle(
                activePlayers = 5,
                expectedPlayers = 5,
                disconnectedPlayers = 0,
                missingReady = 0,
                isHost = false,
                currentReady = true
            )
        )
    }

    @Test
    fun `ready guest can undo while another participant is still pending`() {
        assertFalse(
            OnlineLobbyPresentation.shouldLockGuestReadyToggle(
                activePlayers = 5,
                expectedPlayers = 5,
                disconnectedPlayers = 0,
                missingReady = 1,
                isHost = false,
                currentReady = true
            )
        )
    }

    @Test
    fun `disconnected player shows synchronization without misleading progress`() {
        val state = startState(
            activePlayers = 5,
            expectedPlayers = 5,
            disconnectedPlayers = 1,
            missingReady = 0
        )

        assertEquals(OnlineLobbyStartCopy.SYNCING, state.buttonCopy)
        assertFalse(state.isGold)
        assertNull(state.progress)
    }

    @Test
    fun `map vote prompt and default badge disappear after first vote`() {
        val defaultWithoutVotes = OnlineLobbyPresentation.mapVoteCard(
            count = 0,
            totalVotes = 0,
            isCurrentMap = true
        )
        val otherWithoutVotes = OnlineLobbyPresentation.mapVoteCard(
            count = 0,
            totalVotes = 0,
            isCurrentMap = false
        )
        val defaultWithVotes = OnlineLobbyPresentation.mapVoteCard(
            count = 2,
            totalVotes = 2,
            isCurrentMap = true
        )

        assertTrue(defaultWithoutVotes.showVotePrompt)
        assertTrue(defaultWithoutVotes.showDefaultBadge)
        assertTrue(otherWithoutVotes.showVotePrompt)
        assertFalse(otherWithoutVotes.showDefaultBadge)
        assertFalse(defaultWithVotes.showVotePrompt)
        assertFalse(defaultWithVotes.showDefaultBadge)
        assertEquals(2, defaultWithVotes.count)
    }

    @Test
    fun `local lobby keeps map description and local player list`() {
        val structure = OnlineLobbyPresentation.structure(onlineLobby = false)

        assertTrue(structure.selectedMapVisible)
        assertTrue(structure.mapDescriptionVisible)
        assertTrue(structure.localPlayersVisible)
        assertFalse(structure.onlineMapVoteVisible)
        assertFalse(structure.onlinePlayersVisible)
        assertFalse(structure.onlineSectionLabelsVisible)
        assertEquals(54, structure.mapVoteCardsHeightDp)
    }

    @Test
    fun `online lobby shows the fixed host map without voting`() {
        val structure = OnlineLobbyPresentation.structure(onlineLobby = true)

        assertTrue(structure.selectedMapVisible)
        assertTrue(structure.mapDescriptionVisible)
        assertFalse(structure.localPlayersVisible)
        assertFalse(structure.onlineMapVoteVisible)
        assertTrue(structure.onlinePlayersVisible)
        assertTrue(structure.onlineSectionLabelsVisible)
        assertEquals(54, structure.mapVoteCardsHeightDp)
    }

    private fun startState(
        activePlayers: Int,
        expectedPlayers: Int,
        missingReady: Int,
        disconnectedPlayers: Int = 0,
        isHost: Boolean = true,
        canStart: Boolean = false,
        canStartWithPresent: Boolean = false,
        currentReady: Boolean = false
    ): OnlineLobbyStartPresentation {
        return OnlineLobbyPresentation.startState(
            activePlayers = activePlayers,
            expectedPlayers = expectedPlayers,
            disconnectedPlayers = disconnectedPlayers,
            missingReady = missingReady,
            isHost = isHost,
            canStart = canStart,
            canStartWithPresent = canStartWithPresent,
            cleanupPending = false,
            initialMatchCreated = false,
            currentReady = currentReady
        )
    }
}
