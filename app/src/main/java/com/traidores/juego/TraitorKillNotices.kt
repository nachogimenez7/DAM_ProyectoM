package com.traidores.juego

/**
 * Avisos del Plan de los Asesinos cuando hay mas de un killer online.
 *
 * Con un solo asesino no hay nada que coordinar. Con dos (asesino + espia) cada uno elegia
 * a ciegas y solo se enteraba de la decision del otro al amanecer.
 *
 * Las lineas se derivan **localmente** de las acciones que cada celular ya recibe, igual que
 * los eventos de Dios se derivan de `estadoPartida`: no se escriben en Realtime Database. Eso
 * evita que un cliente pueda inyectar avisos falsos y no necesita tocar las reglas.
 */
internal object TraitorKillNotices {

    const val SPEAKER = "Plan"

    private const val KILL_ACTION = "matar"

    fun pendingNotices(
        session: GameSession,
        records: List<OnlineActionRecord>,
        viewerName: String
    ): List<GameChatMessage> {
        if (session.phase != GamePhase.NOCHE_ASESINO || session.winner.isNotBlank()) return emptyList()
        val killers = GameEngine.alivePlayers(session)
            .filter { it.role?.key in GameRules.killerRoleKeys }
        // Sin companero de crimen no hay nada para avisar.
        if (killers.size < 2 || killers.none { it.name == viewerName }) return emptyList()

        val killerNames = killers.map { it.name }.toSet()
        val alreadySeen = session.chatHistory
            .filter { it.channel == ChatChannel.TRAIDORES && it.isGod }
            .map { it.message }
            .toSet()

        return records
            .asSequence()
            .filter { it.matchId == session.onlineMatchId }
            .filter { it.round == session.round && it.phaseIndex == session.phaseIndex }
            .filter { it.action == KILL_ACTION }
            .filter { it.actorName != viewerName && it.actorName in killerNames }
            .filter { it.targetName.isNotBlank() }
            .sortedBy { it.createdAtLocal }
            // La accion nocturna que vale es la primera confirmada, igual que en la
            // resolucion (`OnlineActionResolver`): no se avisa una eleccion que no cuenta.
            .distinctBy { it.actorName }
            .map { noticeFor(it.actorName, it.targetName) }
            .filterNot { it in alreadySeen }
            .distinct()
            .map { message ->
                GameChatMessage(
                    speaker = SPEAKER,
                    message = message,
                    isGod = true,
                    channel = ChatChannel.TRAIDORES,
                    round = session.round
                )
            }
            .toList()
    }

    fun noticeFor(actorName: String, targetName: String): String {
        return "$actorName eligió a $targetName como víctima."
    }
}
