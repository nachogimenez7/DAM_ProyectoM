package com.traidores.juego

internal data class TraitorPlanNotice(
    val id: String,
    val message: String,
    val actorName: String = "",
    val targetName: String = "",
    val roleKey: String = ""
)

/**
 * Avisos autoritativos que el anfitrión publica en el Plan de los Asesinos.
 *
 * Los invitados solo pueden leer sus propias acciones de Firestore. Por eso el anfitrión,
 * que ya necesita el conjunto completo para resolver la noche, transforma las acciones
 * confirmadas en avisos privados para el equipo. Los identificadores son deterministas para
 * que un reintento o un cambio de anfitrión no duplique el mensaje.
 */
internal object TraitorKillNotices {

    const val SPEAKER = "Plan"

    private const val KILL_ACTION = "matar"
    private const val SILENCE_ACTION = "silenciar"

    fun confirmedNotices(
        session: GameSession,
        records: List<OnlineActionRecord>
    ): List<TraitorPlanNotice> {
        if (
            session.phase !in setOf(GamePhase.NOCHE_ASESINO, GamePhase.NOCHE_MERCENARIO) ||
            session.winner.isNotBlank()
        ) {
            return emptyList()
        }

        val aliveByName = GameEngine.alivePlayers(session).associateBy { it.name }
        val valid = records
            .asSequence()
            .filter { it.matchId == session.onlineMatchId }
            .filter { it.round == session.round && it.phaseIndex == session.phaseIndex }
            .filter { it.targetName.isNotBlank() }
            .filter { record ->
                val actor = aliveByName[record.actorName] ?: return@filter false
                when (record.action) {
                    KILL_ACTION -> session.phase == GamePhase.NOCHE_ASESINO &&
                        actor.role?.key in GameRules.killerRoleKeys
                    SILENCE_ACTION -> session.phase == GamePhase.NOCHE_MERCENARIO &&
                        actor.role?.key == RoleCatalog.MERCENARIO
                    else -> false
                }
            }
            .sortedBy { it.createdAtLocal }
            .distinctBy { "${it.actorName}|${it.action}" }
            .toList()

        val nightStartNotice = if (session.phase == GamePhase.NOCHE_ASESINO) {
            TraitorPlanNotice(
                id = "noche_${session.phaseIndex}_inicio",
                message = "Noche ${session.round}: comienza un nuevo Plan."
            )
        } else {
            null
        }

        val actionNotices = valid.map { record ->
            val actor = aliveByName.getValue(record.actorName)
            TraitorPlanNotice(
                id = actionNoticeId(record),
                message = when (record.action) {
                    SILENCE_ACTION ->
                        "${record.actorName} eligió silenciar a ${record.targetName}."
                    else ->
                        "${record.actorName} votó eliminar a ${record.targetName}."
                },
                actorName = record.actorName,
                targetName = record.targetName,
                roleKey = actor.role?.key.orEmpty()
            )
        }

        val killers = aliveByName.values.filter { it.role?.key in GameRules.killerRoleKeys }
        val killVotes = valid.filter { it.action == KILL_ACTION }
        val finalDecision = if (
            session.phase == GamePhase.NOCHE_ASESINO &&
            killers.size >= 2 &&
            killVotes.map { it.actorName }.distinct().size >= killers.size
        ) {
            val grouped = killVotes.groupingBy { it.targetName }.eachCount()
            val topVotes = grouped.values.maxOrNull() ?: 0
            val leaders = grouped
                .filterValues { it == topVotes }
                .keys
                .sorted()
            val message = if (leaders.size > 1) {
                "Empate en el Plan entre ${leaders.readableNames()}. Esta noche no habrá víctima."
            } else {
                "El Plan acordó eliminar a ${leaders.single()}."
            }
            TraitorPlanNotice(
                id = "resolucion_${session.phaseIndex}_matar",
                message = message
            )
        } else {
            null
        }

        return listOfNotNull(nightStartNotice) + actionNotices + listOfNotNull(finalDecision)
    }

    private fun actionNoticeId(record: OnlineActionRecord): String {
        val actorKey = record.actorOrder
            .takeIf { it >= 0 }
            ?.toString()
            ?: record.actorName.hashCode().toUInt().toString(16)
        return "accion_${record.phaseIndex}_${actorKey}_${record.action}"
    }

    private fun List<String>.readableNames(): String {
        return when (size) {
            0 -> "nadie"
            1 -> first()
            2 -> "${first()} y ${last()}"
            else -> dropLast(1).joinToString(", ") + " y " + last()
        }
    }
}
