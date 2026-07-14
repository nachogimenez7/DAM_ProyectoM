package com.traidores.juego

data class OnlineActionRecord(
    val matchId: String = "",
    val actorId: String = "",
    val action: String,
    val actorName: String,
    val targetName: String,
    val phaseName: String,
    val round: Int,
    val phaseIndex: Int,
    val createdAtLocal: Long,
    val actorOrder: Int = -1,
    val targetOrder: Int = -1
)

data class OnlineNightResolutionActions(
    val assassinVotes: Map<String, String>,
    val mercenaryAction: OnlineActionRecord?,
    val policeAction: OnlineActionRecord?,
    val medicAction: OnlineActionRecord?,
    val oracleAction: OnlineActionRecord?,
    val validActionCount: Int
)

object OnlineActionResolver {
    fun nightActions(
        records: List<OnlineActionRecord>,
        matchId: String = "",
        round: Int,
        phaseIndex: Int? = null
    ): OnlineNightResolutionActions {
        val validNightRecords = records
            .filter { it.matchId == matchId }
            .filter { it.round == round }
            .filter { phaseIndex == null || it.phaseIndex == phaseIndex }
            .filter { it.actorName.isNotBlank() && it.targetName.isNotBlank() }
            .filter { it.action in NIGHT_ACTIONS }
            .sortedBy { it.createdAtLocal }

        return OnlineNightResolutionActions(
            assassinVotes = latestByActor(validNightRecords, "matar")
                .mapValues { (_, record) -> record.targetName },
            mercenaryAction = latestAction(validNightRecords, "silenciar"),
            policeAction = latestAction(validNightRecords, "investigar"),
            medicAction = latestAction(validNightRecords, "salvar"),
            oracleAction = latestAction(validNightRecords, "invitar_muerto"),
            validActionCount = validNightRecords.size
        )
    }

    fun votes(
        records: List<OnlineActionRecord>,
        matchId: String = "",
        round: Int,
        expectedPhaseName: String,
        phaseIndex: Int? = null
    ): Map<String, String> {
        return records
            .filter { it.matchId == matchId }
            .filter { it.round == round }
            .filter { phaseIndex == null || it.phaseIndex == phaseIndex }
            .filter { it.phaseName == expectedPhaseName }
            .filter { it.action == "votar" }
            .filter { it.actorName.isNotBlank() && it.targetName.isNotBlank() }
            .sortedBy { it.createdAtLocal }
            .associate { it.actorName to it.targetName }
    }

    private fun latestByActor(
        records: List<OnlineActionRecord>,
        action: String
    ): Map<String, OnlineActionRecord> {
        return records
            .filter { it.action == action }
            .groupBy { it.actorName }
            .mapValues { (_, actorRecords) -> actorRecords.first() }
    }

    private fun latestAction(
        records: List<OnlineActionRecord>,
        action: String
    ): OnlineActionRecord? {
        return records.firstOrNull { it.action == action }
    }

    private val NIGHT_ACTIONS = setOf(
        "matar",
        "silenciar",
        "investigar",
        "salvar",
        "invitar_muerto",
        "guardar_poder"
    )
}
