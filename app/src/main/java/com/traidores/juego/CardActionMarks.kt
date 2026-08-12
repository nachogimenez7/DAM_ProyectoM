package com.traidores.juego

data class CardActionMark(
    val id: String,
    val actorName: String,
    val targetName: String,
    val roleKey: String
)

/** Decide qué marcas puede ver este teléfono sin exponer acciones privadas ajenas. */
object CardActionMarks {

    fun visibleForCurrentPhase(
        session: GameSession,
        onlinePlayerId: String,
        records: List<OnlineActionRecord>,
        traitorMarks: List<OnlineTraitorActionMark>
    ): List<CardActionMark> {
        if (session.winner.isNotBlank()) return emptyList()
        val human = GameEngine.humanPlayer(session)
        val playerNames = session.players.mapTo(hashSetOf()) { it.name }
        val own = records.asSequence()
            .filter { it.matchId == session.onlineMatchId }
            .filter { it.round == session.round && it.phaseIndex == session.phaseIndex }
            .filter { it.actorId == onlinePlayerId && it.targetName in playerNames }
            .mapNotNull { record ->
                val roleKey = roleForOwnAction(session, human, record) ?: return@mapNotNull null
                CardActionMark(
                    id = "propia:${record.actorId}:${record.phaseIndex}:${record.action}:${record.targetName}",
                    actorName = human.name,
                    targetName = record.targetName,
                    roleKey = roleKey
                )
            }

        val team = if (
            session.phase in NIGHT_PHASES &&
            GameEngine.canSeeTraitorChat(human)
        ) {
            traitorMarks.asSequence()
                .filter { it.round == session.round && it.phaseIndex == session.phaseIndex }
                .filter { it.roleKey in TRAITOR_ACTION_ROLES && it.targetName in playerNames }
                .map {
                    CardActionMark(
                        id = "equipo:${it.id}",
                        actorName = it.actorName,
                        targetName = it.targetName,
                        roleKey = it.roleKey
                    )
                }
        } else {
            emptySequence()
        }

        val publicPayador = if (session.phase == GamePhase.CONTRAPUNTO) {
            session.contrapuntoPlayers.asSequence()
                .filter { it in playerNames }
                .mapIndexed { index, targetName ->
                    CardActionMark(
                        id = "contrapunto:${session.phaseIndex}:$index:$targetName",
                        actorName = session.players
                            .firstOrNull { it.role?.key == RoleCatalog.PAYADOR }
                            ?.name.orEmpty(),
                        targetName = targetName,
                        roleKey = RoleCatalog.PAYADOR
                    )
                }
        } else {
            emptySequence()
        }

        return (own + team + publicPayador)
            .distinctBy { "${it.actorName}|${it.roleKey}|${it.targetName}" }
            .sortedWith(compareBy<CardActionMark> { it.targetName }.thenBy { roleOrder(it.roleKey) })
            .toList()
    }

    private fun roleForOwnAction(
        session: GameSession,
        human: GamePlayer,
        record: OnlineActionRecord
    ): String? {
        val roleKey = human.role?.key ?: return null
        return when {
            session.phase in NIGHT_PHASES &&
                record.action == "matar" && roleKey in GameRules.killerRoleKeys -> roleKey
            session.phase in NIGHT_PHASES &&
                record.action == "silenciar" && roleKey == RoleCatalog.MERCENARIO -> roleKey
            session.phase in NIGHT_PHASES &&
                record.action == "investigar" && roleKey == RoleCatalog.POLICIA -> roleKey
            session.phase in NIGHT_PHASES &&
                record.action == "salvar" && roleKey == RoleCatalog.MEDICO -> roleKey
            session.phase in NIGHT_PHASES &&
                record.action == "invitar_muerto" && roleKey == RoleCatalog.ORACULO -> roleKey
            session.phase == GamePhase.DIA_DEBATE &&
                record.action == "contrapunto" && roleKey == RoleCatalog.PAYADOR -> roleKey
            else -> null
        }
    }

    private fun roleOrder(roleKey: String): Int = when (roleKey) {
        RoleCatalog.ASESINO -> 0
        RoleCatalog.ESPIA -> 1
        else -> 2
    }

    private val NIGHT_PHASES = setOf(
        GamePhase.NOCHE_ASESINO,
        GamePhase.NOCHE_MERCENARIO,
        GamePhase.NOCHE_POLICIA,
        GamePhase.NOCHE_MEDICO,
        GamePhase.NOCHE_ORACULO
    )

    private val TRAITOR_ACTION_ROLES = setOf(
        RoleCatalog.ASESINO,
        RoleCatalog.ESPIA,
        RoleCatalog.MERCENARIO
    )
}
