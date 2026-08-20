package com.traidores.juego

object OnlineLobbyEntryGate {

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

    const val FIELD_MATCH_ID = "matchId"
    const val FIELD_ENTRY_READY = "entradaLobbyLista"
}
