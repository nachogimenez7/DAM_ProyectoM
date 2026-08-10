package com.traidores.juego

data class OnlineMapVote(
    val playerId: String,
    val playerInitial: String,
    val mapKey: String?
)

data class OnlineMapVoteSummary(
    val counts: Map<String, Int>,
    val voterInitials: Map<String, List<String>>,
    val leaders: List<String>
) {
    val totalVotes: Int = counts.values.sum()
    val uniqueLeader: String? = leaders.singleOrNull()
}

object OnlineMapVoteResolver {
    val mapKeys = listOf("pampa", "grecia", "medieval")

    fun summarize(votes: List<OnlineMapVote>): OnlineMapVoteSummary {
        val validVotes = votes.filter { it.mapKey in mapKeys }
        val counts = mapKeys.associateWith { mapKey -> validVotes.count { it.mapKey == mapKey } }
        val voterInitials = mapKeys.associateWith { mapKey ->
            validVotes
                .filter { it.mapKey == mapKey }
                .map { it.playerInitial.trim().take(1).uppercase().ifBlank { "?" } }
        }
        val highest = counts.values.maxOrNull() ?: 0
        val leaders = if (highest == 0) {
            emptyList()
        } else {
            mapKeys.filter { counts[it] == highest }
        }
        return OnlineMapVoteSummary(counts, voterInitials, leaders)
    }

    fun liveLobbyMapKey(
        votes: List<OnlineMapVote>,
        currentMapKey: String
    ): String {
        return summarize(votes).uniqueLeader
            ?: currentMapKey.takeIf { it in mapKeys }
            ?: mapKeys.first()
    }

    fun resolveAtStart(
        votes: List<OnlineMapVote>,
        currentMapKey: String,
        hostTieBreakChoice: String? = null
    ): OnlineMapResolution {
        val summary = summarize(votes)
        if (summary.totalVotes == 0) {
            return OnlineMapResolution.Selected(currentMapKey.takeIf { it in mapKeys } ?: mapKeys.first())
        }
        summary.uniqueLeader?.let { return OnlineMapResolution.Selected(it) }
        if (hostTieBreakChoice != null && hostTieBreakChoice in summary.leaders) {
            return OnlineMapResolution.Selected(hostTieBreakChoice)
        }
        return OnlineMapResolution.HostTieBreakRequired(summary.leaders)
    }
}

sealed interface OnlineMapResolution {
    data class Selected(val mapKey: String) : OnlineMapResolution
    data class HostTieBreakRequired(val mapKeys: List<String>) : OnlineMapResolution
}
