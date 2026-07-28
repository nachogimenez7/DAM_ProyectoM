package com.traidores.juego

data class OnlineLobbyParticipant(
    val id: String,
    val connected: Boolean,
    val ready: Boolean,
    val activeInMatch: Boolean,
    val order: Int,
    val lastSeenLocalMs: Long = 0L,
    val alive: Boolean = true,
    /** Tiene cuenta. Un invitado no puede quedar de anfitrion salvo el escalon de emergencia. */
    val registered: Boolean = true
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

    /**
     * `true` cuando el anfitrion activo ya no puede seguir resolviendo fases. Se separo de
     * [hostHandoffCandidate] porque esa funcion devuelve `null` en dos situaciones muy
     * distintas: "no hace falta relevo" y "hace falta pero no hay a quien darselo". Quien
     * espera para habilitar a un invitado necesita distinguirlas.
     */
    fun needsHostHandoff(
        players: List<OnlineLobbyParticipant>,
        activeHostId: String
    ): Boolean {
        if (activeHostId.isBlank()) return false
        val activeHost = activePlayers(players).firstOrNull { it.id == activeHostId }
        return !(activeHost?.connected == true && activeHost.alive)
    }

    /**
     * @param allowGuests solo en el escalon de emergencia. El anfitrion ve el reparto completo,
     * asi que se prefiere siempre una cuenta registrada; pero si no queda ninguna viva y
     * conectada, la alternativa es que nadie publique las fases y la partida se congele para
     * todos. Ahi si vale que la tome un invitado.
     */
    fun hostHandoffCandidate(
        players: List<OnlineLobbyParticipant>,
        activeHostId: String,
        allowGuests: Boolean = true
    ): OnlineLobbyParticipant? {
        if (!needsHostHandoff(players, activeHostId)) return null
        return activePlayers(players)
            .filter { it.connected && it.alive && (allowGuests || it.registered) }
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
