package com.traidores.juego

import java.util.UUID

object OnlineActionIdentity {
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
