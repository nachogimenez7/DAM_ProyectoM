package com.traidores.juego

/**
 * Reconstruye el reparto completo antes de permitir que un cliente ascendido ejecute el motor
 * autoritativo. Los invitados conservan solo sus roles visibles durante el juego; una vez que
 * Firestore confirma el relevo, las reglas permiten al anfitrion activo leer todos los repartos.
 */
object OnlineHostRoleRecovery {

    fun restore(
        session: GameSession,
        rawAssignments: List<Map<String, Any?>>
    ): GameSession? {
        val rolesByOrder = completeRolesByOrder(rawAssignments, session.players.size) ?: return null
        return session.copy(
            players = session.players.mapIndexed { index, player ->
                player.copy(role = rolesByOrder.getValue(index))
            }
        )
    }

    fun completeRolesByOrder(
        rawAssignments: List<Map<String, Any?>>,
        playerCount: Int
    ): Map<Int, GameRole>? {
        if (playerCount <= 0) return null
        val rolesByOrder = linkedMapOf<Int, GameRole>()
        rawAssignments.forEach { assignment ->
            val order = (assignment["orden"] as? Number)?.toInt() ?: return@forEach
            if (order !in 0 until playerCount) return@forEach
            val key = (assignment["rolKey"] as? String).orEmpty()
            if (key.isBlank()) return@forEach
            val role = GameRole(
                key = key,
                name = (assignment["rolNombre"] as? String).orEmpty().ifBlank { key },
                team = (assignment["rolEquipo"] as? String).orEmpty(),
                imageResName = (assignment["rolImagen"] as? String).orEmpty()
            )
            val existing = rolesByOrder[order]
            if (existing != null && existing != role) return null
            rolesByOrder[order] = role
        }
        return rolesByOrder.takeIf { it.size == playerCount }
    }
}
