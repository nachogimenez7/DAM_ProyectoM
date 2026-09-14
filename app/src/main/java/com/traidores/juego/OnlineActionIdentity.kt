package com.traidores.juego

import java.util.UUID

object OnlineActionIdentity {
    fun voteDocumentId(protocol: Int, matchId: String, actorId: String, round: Int, phaseIndex: Int): String =
        if (protocol >= 2) "${matchId}_${actorId}_r${round}_p${phaseIndex}_votar_s1"
        else documentId(matchId, actorId, round, phaseIndex, "votar")

    fun documentId(
        matchId: String,
        actorId: String,
        round: Int,
        phaseIndex: Int,
        action: String,
        slot: Int = 1
    ): String {
        val lockGroup = when (action) {
            "invitar_muerto", "guardar_poder" -> "accion_oraculo"
            else -> action
        }
        val key = listOf(matchId, actorId, round, phaseIndex, lockGroup, slot)
            .joinToString("|")
        return UUID.nameUUIDFromBytes(key.toByteArray()).toString()
    }
}
