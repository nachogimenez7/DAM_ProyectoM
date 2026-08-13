package com.traidores.juego

data class OnlineLobbyConfig(
    val timing: GameTimingConfig = GameTimingPreset.NORMAL.config,
    val revealRolesOnDeath: Boolean = false,
    val showIndividualVotes: Boolean = true,
    val roleComposition: RoleCompositionConfig = RoleCompositionConfig(),
    val rolePreset: RoleCompositionPreset? = RoleCompositionPreset.RECOMMENDED
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
            FIELD_INDIVIDUAL_VOTES to safe.showIndividualVotes,
            FIELD_ROLE_PRESET to (safe.rolePreset?.name ?: CUSTOM_ROLE_PRESET),
            FIELD_ROLE_COUNTS to LocalGameFactory.editableRoleKeys().joinToString(",") { roleKey ->
                val count = safe.roleComposition.counts[roleKey]
                    ?.coerceIn(0, LocalGameFactory.MAX_PLAYERS)
                    ?: 0
                (if (roleKey == RoleCatalog.ASESINO) count.coerceAtLeast(1) else count).toString()
            }
        )
    }

    fun compositionFor(playerCount: Int, mapKey: String): RoleCompositionConfig {
        val count = playerCount.coerceIn(LocalGameFactory.TEST_MIN_PLAYERS, LocalGameFactory.MAX_PLAYERS)
        if (count < LocalGameFactory.MIN_PLAYERS) {
            return LocalGameFactory.onlineSafeRoleComposition(count, mapKey)
        }
        rolePreset?.let { preset ->
            return LocalGameFactory.roleCompositionPreset(count, mapKey, preset)
        }
        val players = List(count) { index ->
            GamePlayer(
                name = "Jugador ${index + 1}",
                initial = "J",
                isHuman = index == 0
            )
        }
        return LocalGameFactory.normalizedRoleComposition(
            GameSession(
                code = "ROLES",
                mapKey = mapKey,
                mapName = mapKey,
                players = players,
                onlineTestMode = count < LocalGameFactory.MIN_PLAYERS,
                roleComposition = roleComposition.copy(customized = true)
            )
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
                showIndividualVotes = session.showIndividualVotes,
                roleComposition = session.roleComposition,
                rolePreset = if (session.roleComposition.customized) {
                    null
                } else {
                    RoleCompositionPreset.RECOMMENDED
                }
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
                    ?: fallback.showIndividualVotes,
                roleComposition = roleCompositionFromFirestore(
                    data[FIELD_ROLE_COUNTS],
                    fallback.roleComposition
                ),
                rolePreset = rolePresetFromFirestore(
                    data[FIELD_ROLE_PRESET],
                    fallback.rolePreset
                )
            )
        }

        private fun roleCompositionFromFirestore(
            raw: Any?,
            fallback: RoleCompositionConfig
        ): RoleCompositionConfig {
            val values = (raw as? String)
                ?.split(',')
                ?.takeIf { it.size == LocalGameFactory.editableRoleKeys().size }
                ?: return fallback
            val counts = LocalGameFactory.editableRoleKeys()
                .zip(values)
                .associate { (roleKey, value) ->
                    roleKey to (value.toIntOrNull() ?: 0)
                        .coerceIn(0, LocalGameFactory.MAX_PLAYERS)
                }
            return RoleCompositionConfig(counts = counts, customized = true)
        }

        private fun rolePresetFromFirestore(
            raw: Any?,
            fallback: RoleCompositionPreset?
        ): RoleCompositionPreset? {
            val value = raw as? String ?: return fallback
            if (value == CUSTOM_ROLE_PRESET) return null
            return RoleCompositionPreset.entries.firstOrNull { it.name == value } ?: fallback
        }

        private const val FIELD_ROLE_PRESET = "presetRoles"
        private const val FIELD_ROLE_COUNTS = "roles"
        private const val CUSTOM_ROLE_PRESET = "PERSONALIZADO"
    }
}
