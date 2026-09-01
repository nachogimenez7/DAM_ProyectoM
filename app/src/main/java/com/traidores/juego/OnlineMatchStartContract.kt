package com.traidores.juego

/**
 * Datos publicos que participan del inicio de una partida. Mantener este modelo sin Android ni
 * Firebase permite reutilizar el mismo contrato cuando la autoridad se mueva a un backend.
 */
data class OnlineMatchStartPlayer(
    val id: String,
    val name: String,
    val initial: String,
    val ready: Boolean,
    val order: Int,
    val activeInMatch: Boolean,
    val mapVote: String?,
    val publicId: String
)

data class OnlineMatchStartRoomState(
    val state: String,
    val cleanupPending: Boolean,
    val hostId: String,
    val activeHostId: String,
    val initialMatchCreated: Boolean,
    val hasInitialMatch: Boolean,
    val expectedPlayers: Int,
    val currentMapKey: String
)

enum class OnlineMatchStartError(val userMessage: String) {
    ROOM_NOT_WAITING("La sala ya no esta esperando jugadores."),
    CLEANUP_PENDING("La sala todavia esta limpiando la partida anterior."),
    HOST_REQUIRED("Solo el anfitrion puede iniciar."),
    PLAYER_COUNT_MISMATCH("Faltan jugadores para iniciar."),
    PLAYERS_NOT_READY("Todavia faltan jugadores listos.")
}

sealed interface OnlineMatchStartDecision {
    object AlreadyStarted : OnlineMatchStartDecision
    data class Rejected(val error: OnlineMatchStartError) : OnlineMatchStartDecision
    data class MapTieBreakRequired(val mapKeys: List<String>) : OnlineMatchStartDecision
    data class Ready(
        val mapKey: String,
        val orderedPlayers: List<OnlineMatchStartPlayer>
    ) : OnlineMatchStartDecision
}

/**
 * Politica autoritativa pura del inicio. Firestore sigue aportando el snapshot consistente, pero
 * ya no decide dentro de una Activity que combinacion de sala, anfitrion y jugadores es valida.
 */
object OnlineMatchStartPolicy {

    fun evaluate(
        requesterId: String,
        room: OnlineMatchStartRoomState,
        players: List<OnlineMatchStartPlayer>,
        hostTieBreakChoice: String?
    ): OnlineMatchStartDecision {
        if (room.initialMatchCreated || room.hasInitialMatch) {
            return OnlineMatchStartDecision.AlreadyStarted
        }
        if (room.state != OnlineLobbyRules.ROOM_STATE_WAITING) {
            return OnlineMatchStartDecision.Rejected(OnlineMatchStartError.ROOM_NOT_WAITING)
        }
        if (room.cleanupPending) {
            return OnlineMatchStartDecision.Rejected(OnlineMatchStartError.CLEANUP_PENDING)
        }
        if (
            requesterId.isBlank() ||
            (requesterId != room.activeHostId && requesterId != room.hostId)
        ) {
            return OnlineMatchStartDecision.Rejected(OnlineMatchStartError.HOST_REQUIRED)
        }

        val activePlayers = players
            .filter { it.activeInMatch }
            .sortedWith(
                compareBy<OnlineMatchStartPlayer> { it.order }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
        if (activePlayers.size != room.expectedPlayers) {
            return OnlineMatchStartDecision.Rejected(
                OnlineMatchStartError.PLAYER_COUNT_MISMATCH
            )
        }
        if (activePlayers.any { !it.ready }) {
            return OnlineMatchStartDecision.Rejected(OnlineMatchStartError.PLAYERS_NOT_READY)
        }

        val votes = activePlayers.map { player ->
            OnlineMapVote(
                playerId = player.id,
                playerInitial = player.initial,
                mapKey = player.mapVote
            )
        }
        return when (
            val resolution = OnlineMapVoteResolver.resolveAtStart(
                votes = votes,
                currentMapKey = room.currentMapKey,
                hostTieBreakChoice = hostTieBreakChoice
            )
        ) {
            is OnlineMapResolution.Selected -> OnlineMatchStartDecision.Ready(
                mapKey = resolution.mapKey,
                orderedPlayers = activePlayers
            )
            is OnlineMapResolution.HostTieBreakRequired ->
                OnlineMatchStartDecision.MapTieBreakRequired(resolution.mapKeys)
        }
    }
}

data class OnlinePrivateRolePayload(
    val matchId: String,
    val playerId: String,
    val visibleRoles: List<Map<String, Any?>>
)

data class OnlineMatchStartPayloads(
    val initialMatch: Map<String, Any?>,
    val matchState: Map<String, Any?>,
    val privateRolesByPlayer: Map<String, OnlinePrivateRolePayload>,
    val realtimeAccess: Map<String, RealtimeRoomMemberAccess>,
    val roleSummary: String
)

/**
 * Unico lugar que define que parte del reparto es publica y que parte es privada. El payload
 * publico solo contiene identidad y orden; los roles viven exclusivamente en el documento de
 * reparto de cada jugador.
 */
object OnlineMatchStartPayloadFactory {

    fun build(
        assignedSession: GameSession,
        playersAtStart: List<OnlineMatchStartPlayer>,
        updatedBy: String,
        createdAtLocalMs: Long
    ): OnlineMatchStartPayloads {
        require(assignedSession.players.size == playersAtStart.size) {
            "El reparto cambio la cantidad de jugadores capturados."
        }
        require(assignedSession.players.map { it.name } == playersAtStart.map { it.name }) {
            "El reparto cambio el orden de jugadores capturados."
        }
        require(assignedSession.onlineMatchId.isNotBlank()) {
            "El reparto online necesita un matchId."
        }

        val privateRoles = playersAtStart.mapIndexed { playerIndex, onlinePlayer ->
            val ownRole = requireNotNull(assignedSession.players[playerIndex].role) {
                "El reparto quedo incompleto."
            }
            val visibleRoles = assignedSession.players.mapIndexedNotNull { index, candidate ->
                val role = candidate.role ?: return@mapIndexedNotNull null
                val isVisibleTraitorAlly = ownRole.team == GameRules.TRAITOR_WINNER &&
                    role.team == GameRules.TRAITOR_WINNER
                if (index != playerIndex && !isVisibleTraitorAlly) {
                    return@mapIndexedNotNull null
                }
                rolePayload(index, role)
            }
            onlinePlayer.id to OnlinePrivateRolePayload(
                matchId = assignedSession.onlineMatchId,
                playerId = onlinePlayer.id,
                visibleRoles = visibleRoles
            )
        }.toMap()

        val initialMatch = mapOf(
            "matchId" to assignedSession.onlineMatchId,
            "codigoSala" to assignedSession.code,
            "mapa" to assignedSession.mapKey,
            "mapaNombre" to assignedSession.mapName,
            "fase" to assignedSession.phase.name,
            "ronda" to assignedSession.round,
            "creadaEnLocal" to createdAtLocalMs,
            "config" to mapOf(
                "transicionSeg" to assignedSession.timingConfig.transitionSeconds,
                "nocheSeg" to assignedSession.timingConfig.nightSeconds,
                "discusionSeg" to assignedSession.timingConfig.discussionSeconds,
                "votacionSeg" to assignedSession.timingConfig.votingSeconds,
                "revelarRolesAlMorir" to assignedSession.revealRolesOnDeath,
                "votosIndividuales" to assignedSession.showIndividualVotes,
                "roles" to assignedSession.roleComposition.counts
            ),
            "jugadores" to assignedSession.players.mapIndexed { index, player ->
                val onlinePlayer = playersAtStart[index]
                mapOf(
                    "orden" to index,
                    "uidTemporal" to onlinePlayer.id,
                    "publicId" to onlinePlayer.publicId,
                    "simulado" to false,
                    "nombre" to player.name,
                    "inicial" to player.initial
                )
            }
        )
        val matchState = mapOf(
            "versionEstado" to OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
            "fase" to assignedSession.phase.name,
            "ronda" to assignedSession.round,
            "phaseIndex" to assignedSession.phaseIndex,
            "anuncioPublico" to assignedSession.publicAnnouncement,
            "actualizadaEnLocal" to createdAtLocalMs,
            "actualizadaPor" to updatedBy
        )
        val realtimeAccess = playersAtStart.mapIndexed { index, onlinePlayer ->
            val player = assignedSession.players[index]
            onlinePlayer.id to RealtimeRoomMemberAccess(
                name = player.name,
                inLobby = false,
                alive = player.alive,
                traitor = player.role?.team == GameRules.TRAITOR_WINNER
            )
        }.toMap()
        val roleSummary = assignedSession.players
            .groupingBy { it.role?.key.orEmpty().ifBlank { "sin_rol" } }
            .eachCount()
            .toSortedMap()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }

        return OnlineMatchStartPayloads(
            initialMatch = initialMatch,
            matchState = matchState,
            privateRolesByPlayer = privateRoles,
            realtimeAccess = realtimeAccess,
            roleSummary = roleSummary
        )
    }

    private fun rolePayload(order: Int, role: GameRole): Map<String, Any?> = mapOf(
        "orden" to order,
        "rolKey" to role.key,
        "rolNombre" to role.name,
        "rolEquipo" to role.team,
        "rolImagen" to role.imageResName
    )
}
