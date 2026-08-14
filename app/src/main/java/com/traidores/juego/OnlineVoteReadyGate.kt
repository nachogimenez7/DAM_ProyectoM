package com.traidores.juego

data class OnlineVoteReadyState(
    val uid: String,
    val playerName: String,
    val ready: Boolean,
    val round: Int,
    val phaseIndex: Int,
    val matchId: String = ""
)

data class OnlineVoteReadyResult(
    val readyCount: Int,
    val totalCount: Int,
    val canSkip: Boolean
)

object OnlineVoteReadyGate {
    fun evaluate(
        eligiblePlayerNames: Collection<String>,
        states: Collection<OnlineVoteReadyState>,
        round: Int,
        phaseIndex: Int,
        matchId: String = ""
    ): OnlineVoteReadyResult {
        val eligibleNames = eligiblePlayerNames
            .map(::normalizedName)
            .filter { it.isNotBlank() }
            .toSet()
        val readyNames = states.asSequence()
            .filter {
                it.ready &&
                    it.round == round &&
                    it.phaseIndex == phaseIndex &&
                    (matchId.isBlank() || it.matchId == matchId)
            }
            .map { normalizedName(it.playerName) }
            .filter { it in eligibleNames }
            .toSet()
        val readyCount = readyNames.size
        val totalCount = eligibleNames.size
        return OnlineVoteReadyResult(
            readyCount = readyCount,
            totalCount = totalCount,
            canSkip = totalCount > 0 && readyCount >= totalCount
        )
    }

    fun shouldResolve(
        isCoordinator: Boolean,
        requiredActorIds: Set<String>,
        actedActorIds: Set<String>,
        hasPendingWrites: Boolean
    ): Boolean {
        return isCoordinator &&
            !hasPendingWrites &&
            requiredActorIds.isNotEmpty() &&
            requiredActorIds.all { it in actedActorIds }
    }

    private fun normalizedName(name: String): String = name.trim().lowercase()
}
