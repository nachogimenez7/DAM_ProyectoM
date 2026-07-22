package com.traidores.juego

data class OnlineLobbyParticipant(
    val id: String,
    val connected: Boolean,
    val ready: Boolean,
    val activeInMatch: Boolean,
    val order: Int,
    val lastSeenLocalMs: Long = 0L,
    val alive: Boolean = true
)

object OnlineLobbyRules {
    fun displayedPlayerLimit(
        expectedPlayers: Int?,
        maximumPlayers: Int?,
        fallback: Int
    ): Int {
        return (expectedPlayers ?: maximumPlayers ?: fallback).coerceAtLeast(1)
    }

    fun isRoomFresh(updatedAtMs: Long, nowMs: Long, maxAgeMs: Long): Boolean {
        if (updatedAtMs <= 0L) return true
        return nowMs - updatedAtMs <= maxAgeMs
    }

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
        if (activeHost?.connected == true && activeHost.alive) return null
        return activePlayers
            .filter { it.connected && it.alive }
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
