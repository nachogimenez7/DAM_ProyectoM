package com.traidores.juego

enum class OnlineLobbyStartCopy {
    CLEANING,
    START_ONLINE,
    PLAY_WITH_PRESENT,
    WAITING,
    SYNCING,
    NOT_READY,
    HOST_READY,
    READY
}

enum class OnlineLobbyProgressKind {
    PLAYERS,
    READY
}

data class OnlineLobbyStartProgress(
    val kind: OnlineLobbyProgressKind,
    val current: Int,
    val total: Int
)

data class OnlineLobbyStartPresentation(
    val buttonCopy: OnlineLobbyStartCopy,
    val isGold: Boolean,
    val progress: OnlineLobbyStartProgress?
)

data class OnlineLobbyMapVoteCardPresentation(
    val count: Int,
    val showVotePrompt: Boolean,
    val showDefaultBadge: Boolean
)

data class LobbyStructurePresentation(
    val selectedMapVisible: Boolean,
    val onlineMapVoteVisible: Boolean,
    val mapDescriptionVisible: Boolean,
    val onlinePlayersVisible: Boolean,
    val localPlayersVisible: Boolean,
    val onlineSectionLabelsVisible: Boolean,
    val mapVoteCardsHeightDp: Int
)

object OnlineLobbyPresentation {
    fun startState(
        activePlayers: Int,
        expectedPlayers: Int,
        disconnectedPlayers: Int,
        missingReady: Int,
        isHost: Boolean,
        canStart: Boolean,
        canStartWithPresent: Boolean,
        cleanupPending: Boolean,
        initialMatchCreated: Boolean,
        currentReady: Boolean
    ): OnlineLobbyStartPresentation {
        val total = expectedPlayers.coerceAtLeast(1)
        val active = activePlayers.coerceIn(0, total)
        val missingPlayers = (total - active).coerceAtLeast(0)
        val safeMissingReady = missingReady.coerceIn(0, active)
        val buttonCopy = when {
            cleanupPending -> OnlineLobbyStartCopy.CLEANING
            canStart -> OnlineLobbyStartCopy.START_ONLINE
            canStartWithPresent -> OnlineLobbyStartCopy.PLAY_WITH_PRESENT
            isHost && missingPlayers > 0 -> OnlineLobbyStartCopy.WAITING
            isHost && disconnectedPlayers > 0 -> OnlineLobbyStartCopy.SYNCING
            isHost && safeMissingReady > 0 -> OnlineLobbyStartCopy.WAITING
            isHost && initialMatchCreated -> OnlineLobbyStartCopy.SYNCING
            currentReady -> OnlineLobbyStartCopy.NOT_READY
            isHost -> OnlineLobbyStartCopy.HOST_READY
            else -> OnlineLobbyStartCopy.READY
        }
        val progress = when {
            !isHost || canStart || canStartWithPresent || cleanupPending ||
                initialMatchCreated || disconnectedPlayers > 0 -> null
            missingPlayers > 0 -> OnlineLobbyStartProgress(
                kind = OnlineLobbyProgressKind.PLAYERS,
                current = active,
                total = total
            )
            safeMissingReady > 0 -> OnlineLobbyStartProgress(
                kind = OnlineLobbyProgressKind.READY,
                current = (active - safeMissingReady).coerceAtLeast(0),
                total = active
            )
            else -> null
        }
        return OnlineLobbyStartPresentation(
            buttonCopy = buttonCopy,
            isGold = canStart || canStartWithPresent,
            progress = progress
        )
    }

    fun emptySlotCount(expectedPlayers: Int, visiblePlayers: Int): Int {
        return (expectedPlayers - visiblePlayers.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun mapVoteCard(
        count: Int,
        totalVotes: Int,
        isCurrentMap: Boolean
    ): OnlineLobbyMapVoteCardPresentation {
        val safeCount = count.coerceAtLeast(0)
        return OnlineLobbyMapVoteCardPresentation(
            count = safeCount,
            showVotePrompt = safeCount == 0,
            showDefaultBadge = totalVotes <= 0 && isCurrentMap
        )
    }

    fun structure(onlineLobby: Boolean): LobbyStructurePresentation {
        return LobbyStructurePresentation(
            selectedMapVisible = !onlineLobby,
            onlineMapVoteVisible = onlineLobby,
            mapDescriptionVisible = !onlineLobby,
            onlinePlayersVisible = onlineLobby,
            localPlayersVisible = !onlineLobby,
            onlineSectionLabelsVisible = onlineLobby,
            mapVoteCardsHeightDp = if (onlineLobby) 112 else 54
        )
    }
}
