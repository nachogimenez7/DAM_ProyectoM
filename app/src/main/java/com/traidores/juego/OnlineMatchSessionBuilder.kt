package com.traidores.juego

sealed class OnlineMatchSessionResult {
    data class Success(val session: GameSession) : OnlineMatchSessionResult()
    data class Failure(val reason: OnlineMatchSessionError) : OnlineMatchSessionResult()
}

enum class OnlineMatchSessionError(val userMessage: String) {
    MISSING_INITIAL_MATCH("La sala perdio datos de partida. Creen una sala nueva."),
    MISSING_MATCH_STATE("La sala perdio el estado de partida. Creen una sala nueva."),
    MISSING_PLAYERS("La sala no tiene jugadores suficientes para reconstruir la partida."),
    INCOMPLETE_PLAYERS("La sala todavia esta sincronizando jugadores."),
    MISSING_HUMAN_PLAYER("No encontramos tu jugador en esta sala. Entren de nuevo con el codigo."),
    INVALID_PHASE("La sala tiene una fase invalida. Creen una sala nueva.")
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
        showIndividualVotes: Boolean
    ): OnlineMatchSessionResult {
        val initialMatch = initialMatchRaw.asStringAnyMap()
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_INITIAL_MATCH)
        val matchState = matchStateRaw.asStringAnyMap()
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_MATCH_STATE)
        val playerPayloads = initialMatch["jugadores"] as? List<*>
            ?: return OnlineMatchSessionResult.Failure(OnlineMatchSessionError.MISSING_PLAYERS)
        val players = playerPayloads
            .mapNotNull { it.asStringAnyMap() }
            .sortedBy { (it["orden"] as? Number)?.toInt() ?: Int.MAX_VALUE }
            .mapNotNull { playerMap ->
                val name = (playerMap["nombre"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val roleKey = (playerMap["rolKey"] as? String).orEmpty()
                val roleName = (playerMap["rolNombre"] as? String).orEmpty()
                val roleTeam = (playerMap["rolEquipo"] as? String).orEmpty()
                val roleImage = (playerMap["rolImagen"] as? String).orEmpty()
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
                    isHuman = (playerMap["uidTemporal"] as? String) == uidTemporal
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
        val base = GameSession(
            code = sessionCode,
            mapKey = selectedMap.key,
            mapName = (initialMatch["mapaNombre"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackMapName.ifBlank { selectedMap.name },
            players = players,
            phase = GamePhase.REPARTO,
            round = (initialMatch["ronda"] as? Number)?.toInt() ?: 1,
            roleComposition = LocalGameFactory.normalizedRoleComposition(
                GameSession(
                    code = sessionCode,
                    mapKey = selectedMap.key,
                    mapName = selectedMap.name,
                    players = players
                )
            ),
            revealRolesOnDeath = revealRolesOnDeath,
            showIndividualVotes = showIndividualVotes,
            initialPlayerCount = players.size,
            startedAtEpochMs = System.currentTimeMillis()
        )
        val human = players.first { it.isHuman }
        val publicStart = "Dios preparo una partida online con roles ocultos."
        val privateStart = "Tu rol: ${human.role?.name ?: "desconocido"}."
        val initialSession = base.copy(
            publicAnnouncement = publicStart,
            privateHint = privateStart,
            publicHistory = listOf(publicStart),
            godHistory = listOf(publicStart),
            desertorTeam = initialOnlineDesertorTeam(players, base.code)
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
                alive = (playerState["vivo"] as? Boolean) ?: player.alive,
                muted = (playerState["muteado"] as? Boolean) ?: player.muted,
                lastSilencedRound = (playerState["ultimaRondaSilenciado"] as? Number)?.toInt()
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
            alcaldeCorruption = (state["corrupcionAlcalde"] as? Boolean) ?: base.alcaldeCorruption
        )
    }

    private fun initialOnlineDesertorTeam(players: List<GamePlayer>, sessionCode: String): String {
        val desertor = players.firstOrNull { it.role?.key == RoleCatalog.DESERTOR } ?: return ""
        if (desertor.isHuman) return ""
        return if (sessionCode.hashCode() and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
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
