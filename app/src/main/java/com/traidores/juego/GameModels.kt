package com.traidores.juego

import java.io.Serializable

data class GameSession(
    val code: String,
    val mapKey: String,
    val mapName: String,
    val players: List<GamePlayer>,
    val timingConfig: GameTimingConfig = GameTimingConfig(),
    val roleRevealConfig: RoleRevealConfig = RoleRevealConfig(),
    val revealRolesOnDeath: Boolean = false,
    val phase: GamePhase = GamePhase.REPARTO,
    val round: Int = 1,
    val nightKillTarget: String = "",
    val protectedPlayer: String = "",
    val nightSilenceTarget: String = "",
    val investigatedPlayer: String = "",
    val investigatedResult: String = "",
    val dayEliminationTarget: String = "",
    val votes: Map<String, String> = emptyMap(),
    val publicAnnouncement: String = "",
    val privateHint: String = "",
    val publicHistory: List<String> = emptyList(),
    val chatHistory: List<GameChatMessage> = emptyList(),
    val godHistory: List<String> = emptyList(),
    val actionHistory: List<GameAction> = emptyList(),
    val payadorUsed: Boolean = false,
    val contrapuntoPlayers: List<String> = emptyList(),
    val contrapuntoSuspicion: String = "",
    val alcaldeRevealed: Boolean = false,
    val alcaldeTieCandidates: List<String> = emptyList(),
    val desertorTeam: String = "",
    val desertorChangedTeam: Boolean = false,
    val initialPlayerCount: Int = players.size,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val winner: String = "",
    val phaseIndex: Int = 0
) : Serializable

data class GamePlayer(
    val name: String,
    val initial: String,
    val role: GameRole? = null,
    val alive: Boolean = true,
    val muted: Boolean = false,
    val lastSilencedRound: Int? = null,
    val consecutiveNightAfk: Int = 0,
    val consecutiveVoteAfk: Int = 0,
    val isHuman: Boolean = false
) : Serializable

data class RoleRevealConfig(
    val mode: RoleRevealMode = RoleRevealMode.BALANCED,
    val minimumReadingSeconds: Int = DEFAULT_MINIMUM_READING_SECONDS,
    val maximumWaitingSeconds: Int = DEFAULT_MAXIMUM_WAITING_SECONDS
) : Serializable {

    fun normalized(): RoleRevealConfig {
        val minimum = minimumReadingSeconds.coerceIn(
            MIN_READING_SECONDS,
            MAX_READING_SECONDS
        )
        val maximum = maximumWaitingSeconds.coerceIn(
            minimum,
            MAX_WAITING_SECONDS
        )
        return copy(
            minimumReadingSeconds = minimum,
            maximumWaitingSeconds = maximum
        )
    }

    companion object {
        const val DEFAULT_MINIMUM_READING_SECONDS = 10
        const val DEFAULT_MAXIMUM_WAITING_SECONDS = 30
        const val MIN_READING_SECONDS = 5
        const val MAX_READING_SECONDS = 30
        const val MAX_WAITING_SECONDS = 90
    }
}

enum class RoleRevealMode : Serializable {
    WAIT_FOR_ALL,
    BALANCED,
    QUICK
}

enum class RoleRevealStartReason {
    WAITING_FOR_MINIMUM,
    WAITING_FOR_PLAYERS,
    ALL_READY,
    TIME_LIMIT_REACHED
}

data class RoleRevealStartDecision(
    val canStart: Boolean,
    val reason: RoleRevealStartReason,
    val readyPlayers: Int,
    val totalPlayers: Int
)

object RoleRevealGate {

    fun evaluate(
        config: RoleRevealConfig,
        elapsedSeconds: Int,
        readyPlayerIds: Set<String>,
        connectedPlayerIds: Set<String>
    ): RoleRevealStartDecision {
        val normalized = config.normalized()
        val connected = connectedPlayerIds.filter { it.isNotBlank() }.toSet()
        val ready = readyPlayerIds.intersect(connected)
        val minimumReached = elapsedSeconds >= normalized.minimumReadingSeconds
        val allReady = connected.isNotEmpty() && ready.size == connected.size
        val timeLimitReached = normalized.mode != RoleRevealMode.WAIT_FOR_ALL &&
            elapsedSeconds >= effectiveMaximumSeconds(normalized)

        val reason = when {
            !minimumReached -> RoleRevealStartReason.WAITING_FOR_MINIMUM
            allReady -> RoleRevealStartReason.ALL_READY
            timeLimitReached -> RoleRevealStartReason.TIME_LIMIT_REACHED
            else -> RoleRevealStartReason.WAITING_FOR_PLAYERS
        }
        return RoleRevealStartDecision(
            canStart = reason == RoleRevealStartReason.ALL_READY ||
                reason == RoleRevealStartReason.TIME_LIMIT_REACHED,
            reason = reason,
            readyPlayers = ready.size,
            totalPlayers = connected.size
        )
    }

    private fun effectiveMaximumSeconds(config: RoleRevealConfig): Int {
        return when (config.mode) {
            RoleRevealMode.WAIT_FOR_ALL -> Int.MAX_VALUE
            RoleRevealMode.BALANCED -> config.maximumWaitingSeconds
            RoleRevealMode.QUICK -> config.minimumReadingSeconds
        }
    }
}

data class GameTimingConfig(
    val transitionSeconds: Int = DEFAULT_TRANSITION_SECONDS,
    val nightSeconds: Int = DEFAULT_NIGHT_SECONDS,
    val discussionSeconds: Int = DEFAULT_DISCUSSION_SECONDS,
    val votingSeconds: Int = DEFAULT_VOTING_SECONDS
) : Serializable {

    fun normalized(): GameTimingConfig {
        return copy(
            transitionSeconds = transitionSeconds.coerceIn(MIN_TRANSITION_SECONDS, MAX_TRANSITION_SECONDS),
            nightSeconds = nightSeconds.coerceIn(MIN_NIGHT_SECONDS, MAX_NIGHT_SECONDS),
            discussionSeconds = discussionSeconds.coerceIn(MIN_DISCUSSION_SECONDS, MAX_DISCUSSION_SECONDS),
            votingSeconds = votingSeconds.coerceIn(MIN_VOTING_SECONDS, MAX_VOTING_SECONDS)
        )
    }

    fun summary(): String {
        val value = normalized()
        return "${value.transitionSeconds} / ${value.nightSeconds} / " +
            "${value.discussionSeconds} / ${value.votingSeconds}"
    }

    fun preset(): GameTimingPreset? {
        val value = normalized()
        return GameTimingPreset.entries.firstOrNull { it.config == value }
    }

    companion object {
        const val DEFAULT_TRANSITION_SECONDS = 5
        const val DEFAULT_NIGHT_SECONDS = 40
        const val DEFAULT_DISCUSSION_SECONDS = 120
        const val DEFAULT_VOTING_SECONDS = 20

        const val MIN_TRANSITION_SECONDS = 1
        const val MAX_TRANSITION_SECONDS = 10
        const val TRANSITION_STEP_SECONDS = 1

        const val MIN_NIGHT_SECONDS = 10
        const val MAX_NIGHT_SECONDS = 90
        const val NIGHT_STEP_SECONDS = 5

        const val MIN_DISCUSSION_SECONDS = 30
        const val MAX_DISCUSSION_SECONDS = 180
        const val DISCUSSION_STEP_SECONDS = 15

        const val MIN_VOTING_SECONDS = 10
        const val MAX_VOTING_SECONDS = 60
        const val VOTING_STEP_SECONDS = 5
    }
}

enum class GameTimingPreset(
    val label: String,
    val description: String,
    val config: GameTimingConfig
) {
    SLOW(
        "LENTO",
        "Ideal para partidas online o mesas con mucha gente.",
        GameTimingConfig(7, 90, 180, 60)
    ),
    NORMAL(
        "NORMAL",
        "Ritmo equilibrado para la mayoria de las partidas.",
        GameTimingConfig(5, 40, 120, 20)
    ),
    FAST(
        "RAPIDO",
        "Ideal para partidas cortas y jugadores que ya conocen el juego.",
        GameTimingConfig(3, 20, 60, 15)
    )
}

data class GameRole(
    val key: String,
    val name: String,
    val team: String,
    val imageResName: String
) : Serializable

data class GameChatMessage(
    val speaker: String,
    val message: String,
    val isGod: Boolean = false
) : Serializable

data class GameAction(
    val type: GameActionType,
    val actor: String,
    val target: String,
    val round: Int,
    val phase: GamePhase,
    val publiclyKnown: Boolean = false
) : Serializable

enum class GameActionType : Serializable {
    KILL,
    SILENCE,
    INVESTIGATE,
    PROTECT,
    VOTE
}

enum class GamePhase : Serializable {
    REPARTO,
    NOCHE_ASESINO,
    NOCHE_MERCENARIO,
    NOCHE_POLICIA,
    NOCHE_MEDICO,
    AMANECER,
    DIA_DEBATE,
    CONTRAPUNTO,
    VOTACION,
    ALCALDE_DESEMPATE,
    RESULTADO
}

object GameRules {
    const val TOWN_WINNER = "Pueblo"
    const val TRAITOR_WINNER = "Traidores"

    val traitorRoleKeys = setOf("asesino", "mercenario", "espia")
    val killerRoleKeys = setOf("asesino", "espia")

    fun isTraitorRole(role: GameRole?): Boolean {
        return role != null &&
            (role.key in traitorRoleKeys || role.team == TRAITOR_WINNER || role.team == "Asesino")
    }

    fun winnerFor(session: GameSession): String {
        val alive = session.players.filter { it.alive }
        if (alive.none { it.role?.key in killerRoleKeys }) return TOWN_WINNER

        val desertor = alive.firstOrNull { it.role?.key == "desertor" }
        val desertorSupportsTraitors = desertor != null && session.desertorTeam == TRAITOR_WINNER
        val desertorSupportsTown = desertor != null && session.desertorTeam == TOWN_WINNER
        val traitors = alive.count { isTraitorRole(it.role) } + if (desertorSupportsTraitors) 1 else 0
        val town = alive.count { it.role?.team == TOWN_WINNER } + if (desertorSupportsTown) 1 else 0
        return when {
            traitors >= town -> TRAITOR_WINNER
            else -> ""
        }
    }

    fun winnerFor(players: List<GamePlayer>): String {
        return winnerFor(
            GameSession(
                code = "RULES",
                mapKey = "",
                mapName = "",
                players = players
            )
        )
    }

    fun desertorSwitchThreshold(initialPlayerCount: Int): Int {
        return kotlin.math.ceil(initialPlayerCount * 2.0 / 3.0).toInt()
    }
}

object LocalGameFactory {
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 15

    val maps = listOf(
        GameMap("pampa", "Pampa", R.drawable.mapa_pampa, "gaucho"),
        GameMap("grecia", "Grecia", R.drawable.mapa_grecia, "griego"),
        GameMap("medieval", "Medieval", R.drawable.mapa_medieval, "medieval")
    )

    private val defaultBots = listOf(
        "Nanuela",
        "Kamila",
        "Calbo",
        "Carim",
        "Walter",
        "Safia",
        "Emmanuele",
        "Faustinho",
        "JuanNieves",
        "Bartolome",
        "Teresa",
        "CasaMas",
        "Lusiano",
        "Juako"
    )

    fun createSession(
        joinedByCode: Boolean = false,
        humanName: String = ""
    ): GameSession {
        val map = maps.first()
        val requestedName = humanName.trim().take(18).ifBlank { "Nacho" }
        val localPlayerName = if (defaultBots.any { it.equals(requestedName, ignoreCase = true) }) {
            "$requestedName Vos".take(18)
        } else {
            requestedName
        }
        val players = listOf(
            GamePlayer(localPlayerName, playerInitial(localPlayerName), isHuman = true)
        ) + defaultBots.map { name ->
            GamePlayer(name, playerInitial(name))
        }
        return GameSession(
            code = if (joinedByCode) "PAMPA-42" else "SALA-01",
            mapKey = map.key,
            mapName = map.name,
            players = players.take(MIN_PLAYERS)
        )
    }

    fun createOnlineLobby(
        humanName: String,
        playerCount: Int,
        humanIsHost: Boolean
    ): GameSession {
        var session = createSession(humanName = humanName).let {
            it.copy(players = it.players.take(1))
        }
        while (session.players.size < playerCount.coerceIn(1, MAX_PLAYERS)) {
            session = addMockPlayer(session)
        }
        if (!humanIsHost) {
            val human = session.players.first { it.isHuman }
            session = session.copy(
                players = session.players.filterNot { it.isHuman } + human
            )
        }
        return session.copy(code = "ONLINE-MOCK")
    }

    fun selectMap(session: GameSession, mapKey: String): GameSession {
        val map = maps.firstOrNull { it.key == mapKey } ?: maps.first()
        return session.copy(mapKey = map.key, mapName = map.name)
    }

    fun addMockPlayer(session: GameSession): GameSession {
        if (session.players.size >= MAX_PLAYERS) return session
        val currentNames = session.players.map { it.name }.toSet()
        val nextName = defaultBots.firstOrNull { it !in currentNames } ?: return session
        val next = GamePlayer(nextName, playerInitial(nextName))
        return session.copy(players = session.players + next)
    }

    fun removeLastPlayer(session: GameSession): GameSession {
        if (session.players.size <= 1) return session
        return session.copy(players = session.players.dropLast(1))
    }

    fun removePlayer(session: GameSession, index: Int): GameSession {
        if (index <= 0 || index >= session.players.size) return session
        return session.copy(players = session.players.filterIndexed { playerIndex, _ -> playerIndex != index })
    }

    private fun playerInitial(name: String): String {
        return name.trim().firstOrNull()?.uppercase() ?: "?"
    }

    fun minimumPlayersForRole(roleKey: String): Int {
        return when (roleKey) {
            "mercenario" -> 7
            "alcalde" -> 8
            "payador" -> 8
            "desertor" -> 9
            "espia" -> 10
            else -> MIN_PLAYERS
        }
    }

    fun assignRoles(session: GameSession, forcedHumanRoleKey: String = ""): GameSession {
        val suffix = maps.firstOrNull { it.key == session.mapKey }?.roleSuffix ?: "gaucho"
        val effectiveForcedRole = if (forcedHumanRoleKey == "payador" && suffix != "gaucho") {
            ""
        } else {
            forcedHumanRoleKey
        }
        val roles = roleDeckFor(session.players.size, suffix, effectiveForcedRole)

        val shuffledRoles = roles.shuffled()
        val randomlyAssignedPlayers = session.players.mapIndexed { index, player ->
            player.copy(
                role = shuffledRoles[index],
                alive = true,
                muted = false,
                lastSilencedRound = null,
                consecutiveNightAfk = 0,
                consecutiveVoteAfk = 0
            )
        }
        val assignedPlayers = forceHumanRole(randomlyAssignedPlayers, effectiveForcedRole)
        val human = assignedPlayers.firstOrNull { it.isHuman } ?: assignedPlayers.first()
        val publicStart = "Dios preparo una partida local con roles ocultos."
        val privateStart = "Tu rol: ${human.role?.name ?: "desconocido"}."
        return session.copy(
            players = assignedPlayers,
            phase = GamePhase.REPARTO,
            round = 1,
            nightKillTarget = "",
            protectedPlayer = "",
            nightSilenceTarget = "",
            investigatedPlayer = "",
            investigatedResult = "",
            dayEliminationTarget = "",
            votes = emptyMap(),
            publicAnnouncement = publicStart,
            privateHint = privateStart,
            publicHistory = listOf(publicStart),
            chatHistory = emptyList(),
            godHistory = listOf(publicStart),
            actionHistory = emptyList(),
            payadorUsed = false,
            contrapuntoPlayers = emptyList(),
            contrapuntoSuspicion = "",
            alcaldeRevealed = false,
            alcaldeTieCandidates = emptyList(),
            desertorTeam = initialDesertorTeam(assignedPlayers, session.code),
            desertorChangedTeam = false,
            initialPlayerCount = assignedPlayers.size,
            startedAtEpochMs = System.currentTimeMillis(),
            winner = "",
            phaseIndex = 0
        )
    }

    private fun roleDeckFor(
        playerCount: Int,
        suffix: String,
        forcedHumanRoleKey: String
    ): List<GameRole> {
        val roles = mutableListOf(
            roleForKey("policia", suffix),
            roleForKey("asesino", suffix),
            roleForKey("medico", suffix)
        )
        if (playerCount >= 7) {
            roles += roleForKey("mercenario", suffix)
        }
        if (playerCount >= 8) {
            roles += roleForKey("alcalde", suffix)
        }
        if (playerCount >= 8 && suffix == "gaucho") {
            // El Payador es exclusivo del mapa gaucho.
            roles += roleForKey("payador", suffix)
        }
        if (playerCount >= 9) {
            roles += roleForKey("desertor", suffix)
        }
        if (playerCount >= 10) {
            roles += roleForKey("espia", suffix)
        }
        roles += List((playerCount - roles.size).coerceAtLeast(0)) {
            roleForKey("aldeano", suffix)
        }

        if (forcedHumanRoleKey.isNotBlank() && roles.none { it.key == forcedHumanRoleKey }) {
            val replaceIndex = roles.indexOfLast { it.key == "aldeano" }
                .takeIf { it >= 0 }
                ?: roles.lastIndex
            roles[replaceIndex] = roleForKey(forcedHumanRoleKey, suffix)
        }
        return roles.take(playerCount)
    }

    private fun forceHumanRole(players: List<GamePlayer>, forcedHumanRoleKey: String): List<GamePlayer> {
        if (forcedHumanRoleKey.isBlank()) return players
        val humanIndex = players.indexOfFirst { it.isHuman }
        val roleIndex = players.indexOfFirst { it.role?.key == forcedHumanRoleKey }
        if (humanIndex < 0 || roleIndex < 0 || humanIndex == roleIndex) return players

        val humanRole = players[humanIndex].role
        val forcedRole = players[roleIndex].role
        return players.mapIndexed { index, player ->
            when (index) {
                humanIndex -> player.copy(role = forcedRole)
                roleIndex -> player.copy(role = humanRole)
                else -> player
            }
        }
    }

    private fun initialDesertorTeam(players: List<GamePlayer>, sessionCode: String): String {
        val desertor = players.firstOrNull { it.role?.key == "desertor" } ?: return ""
        if (desertor.isHuman) return ""
        return if (sessionCode.hashCode() and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
    }

    private fun roleForKey(key: String, suffix: String): GameRole {
        return when (key) {
            "policia" -> GameRole(
                "policia",
                if (suffix == "gaucho") "Comisario" else "Detective",
                "Pueblo",
                "rol_detective_$suffix"
            )
            "asesino" -> GameRole("asesino", "Asesino", "Traidores", "rol_asesino_$suffix")
            "medico" -> GameRole("medico", "Medico", "Pueblo", "rol_medico_$suffix")
            "mercenario" -> GameRole("mercenario", "Mercenario", "Traidores", "rol_mercenario_$suffix")
            "alcalde" -> GameRole("alcalde", "Alcalde", "Pueblo", "rol_alcalde_$suffix")
            "payador" -> GameRole("payador", "Payador", "Pueblo", "rol_payador_$suffix")
            "desertor" -> GameRole(
                "desertor",
                if (suffix == "gaucho") "Desertora" else "Desertor",
                "Neutral",
                "rol_desertor_$suffix"
            )
            "espia" -> GameRole("espia", "Espia", "Traidores", "rol_espia_$suffix")
            else -> GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_$suffix")
        }
    }
}

data class GameMap(
    val key: String,
    val name: String,
    val imageRes: Int,
    val roleSuffix: String
)
