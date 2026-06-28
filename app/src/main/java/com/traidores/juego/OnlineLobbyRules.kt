package com.traidores.juego

data class OnlineLobbyParticipant(
    val id: String,
    val connected: Boolean,
    val ready: Boolean,
    val activeInMatch: Boolean,
    val order: Int
)

object OnlineLobbyRules {
    fun activePlayers(players: List<OnlineLobbyParticipant>): List<OnlineLobbyParticipant> {
        return players.filter { it.activeInMatch }
    }

    fun releasableDisconnectedPlayers(players: List<OnlineLobbyParticipant>): List<OnlineLobbyParticipant> {
        return players.filter { it.activeInMatch && !it.connected }
    }

    fun hostHandoffCandidate(
        players: List<OnlineLobbyParticipant>,
        activeHostId: String
    ): OnlineLobbyParticipant? {
        if (activeHostId.isBlank()) return null
        val activePlayers = activePlayers(players)
        val activeHost = activePlayers.firstOrNull { it.id == activeHostId }
        if (activeHost?.connected == true) return null
        return activePlayers
            .filter { it.connected }
            .minWithOrNull(compareBy<OnlineLobbyParticipant> { it.order }.thenBy { it.id })
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
