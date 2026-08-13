package com.traidores.juego

sealed class OnlineMatchSessionResult {
    data class Success(val session: GameSession) : OnlineMatchSessionResult()
    data class Failure(val reason: OnlineMatchSessionError) : OnlineMatchSessionResult()
}

enum class OnlineMatchSessionError(val userMessage: String) {
    MISSING_INITIAL_MATCH("La sala perdio datos de partida. Creen una sala nueva."),
    MISSING_MATCH_STATE("La sala perdio el estado de partida. Creen una sala nueva."),
    MISSING_PLAYERS("La sala no tiene jugadores suficientes para reconstruir la partida."),
    INCOMPLETE_PLAYERS("La sala todavía está sincronizando jugadores."),
    MISSING_HUMAN_PLAYER("No encontramos tu jugador en esta sala. Entren de nuevo con el codigo."),
    INVALID_PHASE("La sala tiene una fase invalida. Creen una sala nueva."),
    INCOMPATIBLE_STATE("La sala pertenece a una version anterior. Creen una sala nueva.")
}

object OnlineMatchSessionBuilder {

    fun build(
        initialMatchRaw: Any?,
        matchStateRaw: Any?,
        uidTemporal: String,
        expectedPlayers: Int,
        fallbackRoomId: String,
        fallbackRoomCode: String,
        fallbackMapKey: String,
        fallbackMapName: String,
        revealRolesOnDeath: Boolean,
        showIndividualVotes: Boolean,
        privateRoleAssignments: List<Map<String, Any?>> = emptyList()
    ): OnlineMatchSessionResult {
        val initialMatch = initialMatchRaw.asStringAnyMap()
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_INITIAL_MATCH)
        val matchState = matchStateRaw.asStringAnyMap()
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_MATCH_STATE)
        val phaseName = matchState["fase"] as? String
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.INVALID_PHASE)
        if (runCatching { GamePhase.valueOf(phaseName) }.getOrNull() == null) {
            return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.INVALID_PHASE)
        }
        if (
            OnlineAuthoritativeStateMapper.schemaVersionFromState(matchState) !=
            OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION
        ) {
            return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.INCOMPATIBLE_STATE)
        }
        val playerPayloads = initialMatch["jugadores"] as? List<*>
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_PLAYERS)
        val sortedPlayerPayloads = playerPayloads
            .mapNotNull { it.asStringAnyMap() }
            .sortedBy { (it["orden"] as? Number)?.toInt() ?: Int.MAX_VALUE }
        val rolesByOrder = privateRoleAssignments.associateBy {
            (it["orden"] as? Number)?.toInt() ?: -1
        }
        val players = sortedPlayerPayloads.mapIndexedNotNull { index, playerMap ->
                val name = (playerMap["nombre"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val privateRole = rolesByOrder[index]
                val roleKey = (privateRole?.get("rolKey") as? String)
                    ?: (playerMap["rolKey"] as? String).orEmpty()
                val roleName = (privateRole?.get("rolNombre") as? String)
                    ?: (playerMap["rolNombre"] as? String).orEmpty()
                val roleTeam = (privateRole?.get("rolEquipo") as? String)
                    ?: (playerMap["rolEquipo"] as? String).orEmpty()
                val roleImage = (privateRole?.get("rolImagen") as? String)
                    ?: (playerMap["rolImagen"] as? String).orEmpty()
                GamePlayer(
                    name = name,
                    initial = (playerMap["inicial"] as? String)?.takeIf { it.isNotBlank() }
                        ?: name.firstOrNull()?.uppercase()
                        ?: "?",
                    role = if (roleKey.isNotBlank()) {
                        GameRole(
                            key = roleKey,
                            name = roleName.ifBlank { roleKey },
                            team = roleTeam,
                            imageResName = roleImage
                        )
                    } else {
                        null
                    },
                    alive = true,
                    muted = false,
                    isHuman = (playerMap["uidTemporal"] as? String) == uidTemporal,
                    control = if ((playerMap["uidTemporal"] as? String) == uidTemporal) {
                        PlayerControl.LOCAL
                    } else {
                        PlayerControl.REMOTE
                    }
                )
            }
        if (players.isEmpty()) {
            return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_PLAYERS)
        }
        if (players.size < expectedPlayers) {
            return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.INCOMPLETE_PLAYERS)
        }
        if (players.none { it.isHuman }) {
            return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_HUMAN_PLAYER)
        }

        val initialMapKey = (initialMatch["mapa"] as? String).orEmpty()
        val selectedMap = LocalGameFactory.maps.firstOrNull { it.key == initialMapKey }
            ?: LocalGameFactory.maps.firstOrNull { it.key == fallbackMapKey }
            ?: LocalGameFactory.maps.first()
        val sessionCode = (initialMatch["codigoSala"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackRoomCode.ifBlank { fallbackRoomId.take(OnlineRoomFirestore.ROOM_CODE_LENGTH) }
        val config = initialMatch["config"].asStringAnyMap()
        val roleCounts = (config?.get("roles") as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                val roleKey = key as? String ?: return@mapNotNull null
                val count = (value as? Number)?.toInt() ?: return@mapNotNull null
                roleKey to count
            }
            ?.toMap()
        val timingConfig = GameTimingConfig(
            transitionSeconds = (config?.get("transicionSeg") as? Number)?.toInt()
                ?: GameTimingConfig.DEFAULT_TRANSITION_SECONDS,
            nightSeconds = (config?.get("nocheSeg") as? Number)?.toInt()
                ?: GameTimingConfig.DEFAULT_NIGHT_SECONDS,
            discussionSeconds = (config?.get("discusionSeg") as? Number)?.toInt()
                ?: GameTimingConfig.DEFAULT_DISCUSSION_SECONDS,
            votingSeconds = (config?.get("votacionSeg") as? Number)?.toInt()
                ?: GameTimingConfig.DEFAULT_VOTING_SECONDS
        ).normalized()
        val base = GameSession(
            code = sessionCode,
            mapKey = selectedMap.key,
            mapName = (initialMatch["mapaNombre"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackMapName.ifBlank { selectedMap.name },
            players = players,
            timingConfig = timingConfig,
            phase = GamePhase.REPARTO,
            round = (initialMatch["ronda"] as? Number)?.toInt() ?: 1,
            roleComposition = roleCounts
                ?.let { RoleCompositionConfig(counts = it, customized = true) }
                ?: LocalGameFactory.normalizedRoleComposition(
                    GameSession(
                        code = sessionCode,
                        mapKey = selectedMap.key,
                        mapName = selectedMap.name,
                        players = players,
                        onlineTestMode = expectedPlayers < LocalGameFactory.MIN_PLAYERS
                    )
                ),
            revealRolesOnDeath = (config?.get("revelarRolesAlMorir") as? Boolean)
                ?: revealRolesOnDeath,
            showIndividualVotes = (config?.get("votosIndividuales") as? Boolean)
                ?: showIndividualVotes,
            onlineTestMode = expectedPlayers < LocalGameFactory.MIN_PLAYERS,
            afkExpulsionEnabled = true,
            initialPlayerCount = players.size,
            startedAtEpochMs = System.currentTimeMillis(),
            onlineMatchId = (initialMatch["matchId"] as? String).orEmpty(),
            onlinePlayerUids = sortedPlayerPayloads.map { (it["uidTemporal"] as? String).orEmpty() }
        )
        val human = players.first { it.isHuman }
        val publicStart = "Dios preparo una partida online con roles ocultos."
        val privateStart = "Tu rol: ${human.role?.name ?: "desconocido"}."
        val initialSession = base.copy(
            publicAnnouncement = publicStart,
            privateHint = privateStart,
            publicHistory = listOf(publicStart),
            chatHistory = listOf(GameChatMessage(GameplayFeedMessages.GOD_SPEAKER, publicStart, isGod = true)),
            godHistory = listOf(publicStart),
            // En online el bando del desertor nunca se preasigna: lo elige el jugador y el
            // anfitrion lo publica. Preasignarlo aca dependia de `isHuman`, que es distinto
            // en cada celular, asi que cada cliente reconstruia un bando diferente.
            desertorTeam = ""
        )

        return applyState(initialSession, matchState)
            ?.let { OnlineMatchSessionResult.Success(it) }
            ?: OnlineMatchSessionResult.Failure(OnlineMatchSessionError.INVALID_PHASE)
    }

    fun applyState(base: GameSession, state: Map<String, Any?>?): GameSession? {
        if (state == null) return null
        val phaseName = state["fase"] as? String ?: return null
        val phase = runCatching { GamePhase.valueOf(phaseName) }.getOrNull() ?: return null
        val playerStatesByOrder = (state["jugadores"] as? List<*>)
            ?.mapNotNull { it.asStringAnyMap() }
            ?.mapNotNull { playerState ->
                val order = (playerState["orden"] as? Number)?.toInt() ?: return@mapNotNull null
                order to playerState
            }
            ?.toMap()
            .orEmpty()
        val playerStatesByName = (state["jugadores"] as? List<*>)
            ?.mapNotNull { it.asStringAnyMap() }
            ?.associateBy { (it["nombre"] as? String).orEmpty() }
            .orEmpty()
        val updatedPlayers = base.players.mapIndexed { index, player ->
            val playerState = playerStatesByOrder[index] ?: playerStatesByName[player.name] ?: return@mapIndexed player
            player.copy(
                role = roleFromState(playerState) ?: player.role,
                alive = (playerState["vivo"] as? Boolean) ?: player.alive,
                muted = (playerState["muteado"] as? Boolean) ?: player.muted,
                lastSilencedRound = (playerState["ultimaRondaSilenciado"] as? Number)?.toInt(),
                consecutiveNightAfk = (playerState["afkNoche"] as? Number)?.toInt()
                    ?: player.consecutiveNightAfk,
                consecutiveVoteAfk = (playerState["afkVoto"] as? Number)?.toInt()
                    ?: player.consecutiveVoteAfk,
                deathCause = deathCauseFromState(
                    playerState["causaEliminacion"],
                    player.deathCause
                )
            )
        }
        val publicHistory = (state["historialPublico"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.takeIf { it.isNotEmpty() }
            ?: base.publicHistory
        return base.copy(
            phase = phase,
            round = (state["ronda"] as? Number)?.toInt() ?: base.round,
            phaseIndex = (state["phaseIndex"] as? Number)?.toInt() ?: base.phaseIndex,
            players = updatedPlayers,
            publicAnnouncement = (state["anuncioPublico"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: base.publicAnnouncement,
            publicHistory = publicHistory,
            godHistory = publicHistory,
            chatHistory = GameplayFeedMessages.appendGodEvents(
                base.chatHistory,
                publicHistory
            ),
            winner = (state["ganador"] as? String).orEmpty(),
            nightKillTarget = (state["victimaNoche"] as? String).orEmpty(),
            nightSilenceTarget = (state["silenciado"] as? String).orEmpty(),
            dayEliminationTarget = (state["expulsadoDia"] as? String).orEmpty(),
            votes = state["votos"].asStringAnyMap()
                ?.mapValues { (_, value) -> value as? String ?: "" }
                ?.filterValues { it.isNotBlank() }
                ?: emptyMap(),
            voteRound = (state["rondaVoto"] as? Number)?.toInt() ?: base.voteRound,
            tieVoteCandidates = (state["candidatosDesempate"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList(),
            alcaldeTieCandidates = (state["candidatosAlcalde"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList(),
            alcaldeRevealed = (state["alcaldeRevelado"] as? Boolean) ?: base.alcaldeRevealed,
            alcaldeCorruption = (state["corrupcionAlcalde"] as? Boolean) ?: base.alcaldeCorruption,
            onlinePhaseDeadlineEpochMs = OnlineAuthoritativeStateMapper.phaseDeadlineFromState(state),
            onlinePhaseDeadlinePhaseIndex = OnlineAuthoritativeStateMapper
                .phaseDeadlineIndexFromState(state)
        )
    }

    private fun roleFromState(state: Map<String, Any?>): GameRole? {
        val key = (state["rolKey"] as? String).orEmpty()
        if (key.isBlank()) return null
        return GameRole(
            key = key,
            name = (state["rolNombre"] as? String).orEmpty().ifBlank { key },
            team = (state["rolEquipo"] as? String).orEmpty(),
            imageResName = (state["rolImagen"] as? String).orEmpty()
        )
    }

    private fun deathCauseFromState(value: Any?, fallback: DeathCause): DeathCause {
        val name = (value as? String).orEmpty()
        return runCatching { DeathCause.valueOf(name) }.getOrDefault(fallback)
    }

    private fun Any?.asStringAnyMap(): Map<String, Any?>? {
        return (this as? Map<*, *>)?.entries
            ?.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                key to entry.value
            }
            ?.toMap()
    }
}
