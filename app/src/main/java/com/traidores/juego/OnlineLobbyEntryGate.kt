package com.traidores.juego

object OnlineLobbyEntryGate {

    // Si todos confirman, la entrada se libera antes. Este plazo solo evita que un eco de
    // presencia demorado congele la sala completa; no retrasa el reparto autoritativo.
    const val HARD_RELEASE_AFTER_MS = 1_000L
    const val FULLY_CONNECTED_RELEASE_AFTER_MS = 3_000L

    fun shouldResetForWaitingLobby(previousState: String, currentState: String): Boolean {
        return currentState == OnlineLobbyRules.ROOM_STATE_WAITING &&
            previousState != OnlineLobbyRules.ROOM_STATE_WAITING
    }

    fun isReleased(matchId: String, releasedMatchId: String): Boolean {
        return matchId.isNotBlank() && releasedMatchId == matchId
    }

    fun acknowledgedPlayerIds(
        matchId: String,
        clientStates: Map<String, Any?>
    ): Set<String> {
        if (matchId.isBlank()) return emptySet()
        return clientStates.mapNotNullTo(linkedSetOf()) { (uid, rawState) ->
            val state = rawState as? Map<*, *> ?: return@mapNotNullTo null
            val acknowledgedMatchId = state[FIELD_MATCH_ID] as? String
            val entryReady = state[FIELD_ENTRY_READY] as? Boolean ?: false
            uid.takeIf { entryReady && acknowledgedMatchId == matchId }
        }
    }

    fun canRelease(
        expectedPlayerIds: Set<String>,
        matchId: String,
        clientStates: Map<String, Any?>
    ): Boolean {
        if (expectedPlayerIds.isEmpty() || matchId.isBlank()) return false
        val acknowledged = acknowledgedPlayerIds(matchId, clientStates)
        return expectedPlayerIds.all(acknowledged::contains)
    }

    /**
     * El anfitrión ya está listo localmente cuando pudo reconstruir la sesión y abrió la
     * barrera de entrada. Esa señal es tan fuerte como su eco en RTDB y evita bloquear a toda
     * la sala si su confirmación se cruza con la publicación inicial de permisos.
     */
    fun readyPlayerIds(
        expectedPlayerIds: Set<String>,
        matchId: String,
        clientStates: Map<String, Any?>,
        localPlayerId: String,
        localPlayerReady: Boolean
    ): Set<String> {
        val ready = acknowledgedPlayerIds(matchId, clientStates)
            .filterTo(linkedSetOf()) { it in expectedPlayerIds }
        if (localPlayerReady && localPlayerId in expectedPlayerIds) ready += localPlayerId
        return ready
    }

    fun canReleaseWithLocalReady(
        expectedPlayerIds: Set<String>,
        matchId: String,
        clientStates: Map<String, Any?>,
        localPlayerId: String,
        localPlayerReady: Boolean
    ): Boolean {
        if (expectedPlayerIds.isEmpty() || matchId.isBlank()) return false
        val ready = readyPlayerIds(
            expectedPlayerIds = expectedPlayerIds,
            matchId = matchId,
            clientStates = clientStates,
            localPlayerId = localPlayerId,
            localPlayerReady = localPlayerReady
        )
        return expectedPlayerIds.all(ready::contains)
    }

    fun canReleaseAfterTimeout(
        expectedPlayerIds: Set<String>,
        matchId: String,
        clientStates: Map<String, Any?>,
        localPlayerId: String,
        localPlayerReady: Boolean,
        connectedPlayerIds: Set<String>,
        elapsedMs: Long
    ): Boolean {
        if (elapsedMs < HARD_RELEASE_AFTER_MS) return false
        val ready = readyPlayerIds(
            expectedPlayerIds = expectedPlayerIds,
            matchId = matchId,
            clientStates = clientStates,
            localPlayerId = localPlayerId,
            localPlayerReady = localPlayerReady
        ).size
        val connected = connectedPlayerIds.count(expectedPlayerIds::contains)
        val quorumReached = OnlineStartQuorum.isReached(
            expectedPlayers = expectedPlayerIds.size,
            readyPlayers = ready,
            connectedPlayers = connected
        )
        val fullyConnectedFallback =
            elapsedMs >= FULLY_CONNECTED_RELEASE_AFTER_MS &&
                localPlayerReady &&
                connected == expectedPlayerIds.size
        return quorumReached || fullyConnectedFallback
    }

    /**
     * El estado observado en RTDB es la verdad para una confirmación efímera. Un `setValue`
     * exitoso no puede impedir reenviarla si una limpieza o reconexión la hizo desaparecer.
     */
    fun shouldPublishAcknowledgement(
        playerId: String,
        matchId: String,
        clientStates: Map<String, Any?>,
        publishInProgress: Boolean
    ): Boolean {
        if (playerId.isBlank() || matchId.isBlank() || publishInProgress) return false
        return playerId !in acknowledgedPlayerIds(matchId, clientStates)
    }

    const val FIELD_MATCH_ID = "matchId"
    const val FIELD_ENTRY_READY = "entradaLobbyLista"
}
