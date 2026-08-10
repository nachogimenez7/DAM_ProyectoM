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
    const val ROOM_STATE_WAITING = "esperando"
    const val ROOM_STATE_IN_GAME = "en_juego"
    const val ROOM_STATE_FINISHED = "finalizada"

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

    fun connectedPresenceCount(states: Iterable<String?>): Int {
        return states.count { it == "conectado" }
    }

    fun activePlayers(players: List<OnlineLobbyParticipant>): List<OnlineLobbyParticipant> {
        return players.filter { it.activeInMatch }
    }

    fun releasableDisconnectedPlayers(
        players: List<OnlineLobbyParticipant>,
        protectedPlayerIds: Set<String> = emptySet()
    ): List<OnlineLobbyParticipant> {
        return players.filter {
            it.activeInMatch && !it.connected && it.id !in protectedPlayerIds
        }
    }

    fun shouldMarkGameplayDisconnected(
        isOnlineGameplay: Boolean,
        isChangingConfigurations: Boolean,
        returningToLobby: Boolean
    ): Boolean {
        return isOnlineGameplay && !isChangingConfigurations && !returningToLobby
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
        // El anfitrion puede seguir coordinando como espectador despues de morir. Relevarlo
        // solo por su estado dentro del juego abre una ventana innecesaria de cambio de
        // autoridad y puede interrumpir una fase que estaba resolviendo correctamente.
        return activeHost?.connected != true
    }

    /**
     * @param allowGuests solo en el escalon de emergencia. Se prefiere una cuenta registrada
     * para conservar las herramientas de moderacion; cualquier candidato recupera y valida el
     * reparto completo despues de que Firestore confirma el relevo.
     */
    fun hostHandoffCandidate(
        players: List<OnlineLobbyParticipant>,
        activeHostId: String,
        allowGuests: Boolean = true
    ): OnlineLobbyParticipant? {
        if (!needsHostHandoff(players, activeHostId)) return null
        return activePlayers(players)
            .filter { it.connected && (allowGuests || it.registered) }
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

    fun isRematchableRoom(
        roomState: String,
        hasAuthoritativeState: Boolean,
        winner: String
    ): Boolean {
        return roomState == ROOM_STATE_FINISHED ||
            (
                roomState == ROOM_STATE_IN_GAME &&
                    (!hasAuthoritativeState || winner.isNotBlank())
                )
    }

    fun canPrepareRematch(
        roomState: String,
        hasAuthoritativeState: Boolean,
        winner: String,
        isHost: Boolean,
        resetInProgress: Boolean,
        cleanupPending: Boolean,
        playerCount: Int
    ): Boolean {
        return isRematchableRoom(roomState, hasAuthoritativeState, winner) &&
            isHost &&
            !resetInProgress &&
            !cleanupPending &&
            playerCount > 0
    }
}
