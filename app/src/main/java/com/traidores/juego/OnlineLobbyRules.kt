package com.traidores.juego

data class OnlineLobbyParticipant(
    val id: String,
    val connected: Boolean,
    val ready: Boolean,
    val activeInMatch: Boolean,
    val order: Int,
    val lastSeenLocalMs: Long = 0L
)

object OnlineLobbyRules {
    const val HOST_STALE_AFTER_MS = 30_000L

    fun activePlayers(players: List<OnlineLobbyParticipant>): List<OnlineLobbyParticipant> {
        return players.filter { it.activeInMatch }
    }

    fun releasableDisconnectedPlayers(players: List<OnlineLobbyParticipant>): List<OnlineLobbyParticipant> {
        return players.filter { it.activeInMatch && !it.connected }
    }

    fun hostHandoffCandidate(
        players: List<OnlineLobbyParticipant>,
        activeHostId: String,
        nowMs: Long = 0L
    ): OnlineLobbyParticipant? {
        if (activeHostId.isBlank()) return null
        val activePlayers = activePlayers(players)
        val activeHost = activePlayers.firstOrNull { it.id == activeHostId }
        if (activeHost != null && isRecentlyConnected(activeHost, nowMs)) return null
        return activePlayers
            .filter { isRecentlyConnected(it, nowMs) }
            .minWithOrNull(compareBy<OnlineLobbyParticipant> { it.order }.thenBy { it.id })
    }

    fun isRecentlyConnected(player: OnlineLobbyParticipant, nowMs: Long = 0L): Boolean {
        if (!player.connected) return false
        if (nowMs <= 0L || player.lastSeenLocalMs <= 0L) return true
        return nowMs - player.lastSeenLocalMs <= HOST_STALE_AFTER_MS
    }

    fun canStart(
        players: List<OnlineLobbyParticipant>,
        expectedPlayers: Int,
        roomWaiting: Boolean,
        initialMatchCreated: Boolean
    ): Boolean {
        val activePlayers = activePlayers(players)
        return roomWaiting &&
            !initialMatchCreated &&
            activePlayers.size == expectedPlayers &&
            activePlayers.isNotEmpty() &&
            activePlayers.all { it.connected && it.ready }
    }
}
