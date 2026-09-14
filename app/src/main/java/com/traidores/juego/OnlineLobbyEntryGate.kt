package com.traidores.juego

object OnlineLobbyEntryGate {

    // Nunca se libera una partida con un jugador esperado todavía en el lobby. Los reintentos
    // recuperan el ACK; un timeout parcial dividía la sala entre lobby y gameplay.
    const val ENTRY_RETRY_AFTER_MS = 1_500L
    const val PRESENTATION_LEAD_MS = 2_500L
    const val MAX_PRESENTATION_WAIT_MS = 5_000L

    fun presentationStartDelayMs(releasedAtEpochMs: Long, nowEpochMs: Long): Long {
        if (releasedAtEpochMs <= 0L) return PRESENTATION_LEAD_MS
        return (releasedAtEpochMs + PRESENTATION_LEAD_MS - nowEpochMs)
            .coerceIn(0L, MAX_PRESENTATION_WAIT_MS)
    }

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

    /**
     * The host publishes the RTDB access registry after committing the Firestore match. Guests can
     * observe that Firestore commit a few milliseconds before their RTDB member entry switches out
     * of the lobby. Waiting for that exact state prevents an expected permission-denied write and
     * avoids relying on a retry to enter every match.
     */
    fun isRealtimeMatchAccessReady(active: Boolean?, inLobby: Boolean?): Boolean {
        return active == true && inLobby == false
    }

    const val FIELD_MATCH_ID = "matchId"
    const val FIELD_ENTRY_READY = "entradaLobbyLista"
}
