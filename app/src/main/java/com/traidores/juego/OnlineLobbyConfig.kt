package com.traidores.juego

data class OnlineLobbyConfig(
    val timing: GameTimingConfig = GameTimingPreset.NORMAL.config,
    val revealRolesOnDeath: Boolean = false,
    val showIndividualVotes: Boolean = true
) {
    fun normalized(): OnlineLobbyConfig {
        return copy(timing = timing.normalized())
    }

    fun toFirestore(): Map<String, Any> {
        val safe = normalized()
        return mapOf(
            FIELD_TRANSITION_SECONDS to safe.timing.transitionSeconds,
            FIELD_NIGHT_SECONDS to safe.timing.nightSeconds,
            FIELD_DISCUSSION_SECONDS to safe.timing.discussionSeconds,
            FIELD_VOTING_SECONDS to safe.timing.votingSeconds,
            FIELD_REVEAL_ROLES to safe.revealRolesOnDeath,
            FIELD_INDIVIDUAL_VOTES to safe.showIndividualVotes
        )
    }

    companion object {
        const val FIELD_ROOM_CONFIG = "configLobby"
        private const val FIELD_TRANSITION_SECONDS = "transicionSeg"
        private const val FIELD_NIGHT_SECONDS = "nocheSeg"
        private const val FIELD_DISCUSSION_SECONDS = "discusionSeg"
        private const val FIELD_VOTING_SECONDS = "votacionSeg"
        private const val FIELD_REVEAL_ROLES = "revelarRolesAlMorir"
        private const val FIELD_INDIVIDUAL_VOTES = "votosIndividuales"

        fun fromSession(session: GameSession): OnlineLobbyConfig {
            return OnlineLobbyConfig(
                timing = session.timingConfig,
                revealRolesOnDeath = session.revealRolesOnDeath,
                showIndividualVotes = session.showIndividualVotes
            ).normalized()
        }

        fun fromFirestore(raw: Any?, fallback: OnlineLobbyConfig): OnlineLobbyConfig {
            val data = (raw as? Map<*, *>) ?: return fallback.normalized()
            val timing = GameTimingConfig(
                transitionSeconds = (data[FIELD_TRANSITION_SECONDS] as? Number)?.toInt()
                    ?: fallback.timing.transitionSeconds,
                nightSeconds = (data[FIELD_NIGHT_SECONDS] as? Number)?.toInt()
                    ?: fallback.timing.nightSeconds,
                discussionSeconds = (data[FIELD_DISCUSSION_SECONDS] as? Number)?.toInt()
                    ?: fallback.timing.discussionSeconds,
                votingSeconds = (data[FIELD_VOTING_SECONDS] as? Number)?.toInt()
                    ?: fallback.timing.votingSeconds
            ).normalized()
            return OnlineLobbyConfig(
                timing = timing,
                revealRolesOnDeath = data[FIELD_REVEAL_ROLES] as? Boolean
                    ?: fallback.revealRolesOnDeath,
                showIndividualVotes = data[FIELD_INDIVIDUAL_VOTES] as? Boolean
                    ?: fallback.showIndividualVotes
            )
        }
    }
}
