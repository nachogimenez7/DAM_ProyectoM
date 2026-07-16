package com.traidores.juego

object OnlineLobbyEntryGate {

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
        clientStates: Map<String, Any?>,
        force: Boolean = false
    ): Boolean {
        if (expectedPlayerIds.isEmpty() || matchId.isBlank()) return false
        val acknowledged = acknowledgedPlayerIds(matchId, clientStates)
        return expectedPlayerIds.all(acknowledged::contains) ||
            (force && acknowledged.isNotEmpty())
    }

    const val FIELD_MATCH_ID = "matchId"
    const val FIELD_ENTRY_READY = "entradaLobbyLista"
}
