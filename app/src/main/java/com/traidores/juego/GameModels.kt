package com.traidores.juego

import java.io.Serializable

data class GameSession(
    val code: String,
    val mapKey: String,
    val mapName: String,
    val players: List<GamePlayer>,
    val playerProfiles: Map<String, PlayerProfile> = emptyMap(),
    val timingConfig: GameTimingConfig = GameTimingConfig(),
    val roleRevealConfig: RoleRevealConfig = RoleRevealConfig(),
    val roleComposition: RoleCompositionConfig = RoleCompositionConfig(),
    val revealRolesOnDeath: Boolean = false,
    val showIndividualVotes: Boolean = true,
    val onlineTestMode: Boolean = false,
    val quickTestMode: Boolean = false,
    val afkExpulsionEnabled: Boolean = false,
    val botDifficulty: BotDifficulty = BotDifficulty.NORMAL,
    val debugBotsObeyVoteCommands: Boolean = false,
    val debugForceVoteTies: Boolean = false,
    val debugBotsNeverKillHuman: Boolean = false,
    val debugBotsNeverVoteHuman: Boolean = false,
    val phase: GamePhase = GamePhase.REPARTO,
    val round: Int = 1,
    val nightKillTarget: String = "",
    val assassinVotes: Map<String, String> = emptyMap(),
    val protectedPlayer: String = "",
    val nightHadNoVictim: Boolean = false,
    val nightSilenceTarget: String = "",
    val investigatedPlayer: String = "",
    val investigatedResult: String = "",
    val dayEliminationTarget: String = "",
    val votes: Map<String, String> = emptyMap(),
    val voteRound: Int = 0,
    val tieVoteCandidates: List<String> = emptyList(),
    val publicAnnouncement: String = "",
    val privateHint: String = "",
    val publicHistory: List<String> = emptyList(),
    val chatHistory: List<GameChatMessage> = emptyList(),
    val claimLedger: Map<String, List<ClaimRecord>> = emptyMap(),
    val tableMemory: TableMemory = TableMemory(),
    val traitorPlan: TraitorPlan? = null,
    val godHistory: List<String> = emptyList(),
    val actionHistory: List<GameAction> = emptyList(),
    val payadorUsed: Boolean = false,
    val contrapuntoPlayers: List<String> = emptyList(),
    val contrapuntoSuspicion: String = "",
    val oracleUsed: Boolean = false,
    val oracleInvitedPlayer: String = "",
    val oracleRevealPending: Boolean = false,
    val alcaldeRevealed: Boolean = false,
    val alcaldeCorruption: Boolean = false,
    val alcaldeTieCandidates: List<String> = emptyList(),
    val desertorTeam: String = "",
    val desertorChangedTeam: Boolean = false,
    val initialPlayerCount: Int = players.size,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val specialVictories: List<GameSpecialVictory> = emptyList(),
    val winner: String = "",
    val phaseIndex: Int = 0
) : Serializable

enum class BotDifficulty : Serializable {
    NORMAL,
    HARD
}

enum class StatementType : Serializable {
    PROTECTED,
    INVESTIGATED,
    REFUSED_ROLE,
    TRUST,
    ACCUSE,
    VOTE
}

data class ClaimRecord(
    val round: Int,
    val phase: GamePhase,
    val roleKey: String? = null,
    val statementType: StatementType? = null,
    val target: String? = null
) : Serializable

data class TableMemory(
    val suspicion: Map<String, Map<String, Int>> = emptyMap(),
    val pendingQuestions: Map<String, PendingQuestion> = emptyMap(),
    val declaredInvestigationReads: List<InvestigationRead> = emptyList()
) : Serializable

data class TraitorPlan(
    val round: Int,
    val killTarget: String,
    val killRationale: KillRationale,
    val dayPushTarget: String,
    val threats: List<TraitorThreat>,
    val cover: CoverMove? = null,
    val speakingOrder: List<String> = emptyList()
) : Serializable

enum class KillRationale : Serializable {
    LIDER_DE_OPINION,
    CONFIRMA_ROL,
    NOS_MARCO,
    JUNTA_VOTOS_LIMPIOS,
    CALLADO_PELIGROSO,
    SIN_LECTURA
}

data class TraitorThreat(
    val player: String,
    val kind: ThreatKind,
    val markedTraitor: String? = null
) : Serializable

enum class ThreatKind : Serializable {
    DETECTIVE_DECLARADO,
    NOS_MARCO_SOSPECHA,
    JUNTA_VOTOS
}

data class CoverMove(
    val kind: CoverKind,
    val actor: String,
    val backer: String?,
    val fakeRoleKey: String?,
    val targetToDirty: String?
) : Serializable

enum class CoverKind : Serializable {
    LOW_PROFILE,
    COUNTER_CLAIM,
    FAKE_CLAIM,
    BUS_ALLY
}

data class PendingQuestion(
    val round: Int,
    val source: String,
    val target: String,
    val message: String
) : Serializable

data class InvestigationRead(
    val round: Int,
    val source: String,
    val target: String,
    val result: String
) : Serializable

data class RoleCompositionConfig(
    val counts: Map<String, Int> = emptyMap(),
    val customized: Boolean = false
) : Serializable

enum class RoleCompositionPreset(
    val label: String,
    val description: String
) {
    RECOMMENDED(
        "RECOMENDADO",
        "Equilibrado para la cantidad de jugadores y el mapa elegido."
    ),
    CLASSIC(
        "CLASICO",
        "Partida simple: Asesino, Detective, Médico y Aldeanos."
    ),
    CHAOTIC(
        "CAOTICO",
        "Mas presion y roles especiales disponibles para una partida menos predecible."
    )
}

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
        const val DEFAULT_TRANSITION_SECONDS = 4
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
        "Ideal para partidas online o grupos con mucha gente.",
        GameTimingConfig(6, 90, 180, 60)
    ),
    NORMAL(
        "NORMAL",
        "Ritmo equilibrado para la mayoria de las partidas.",
        GameTimingConfig(4, 40, 120, 20)
    ),
    FAST(
        "RAPIDO",
        "Ideal para partidas cortas y jugadores que ya conocen el juego.",
        GameTimingConfig(2, 20, 60, 15)
    )
}

data class GameRole(
    val key: String,
    val name: String,
    val team: String,
    val imageResName: String
) : Serializable

enum class ChatChannel : Serializable {
    PUBLICO,
    TRAIDORES
}

data class GameChatMessage(
    val speaker: String,
    val message: String,
    val isGod: Boolean = false,
    val channel: ChatChannel = ChatChannel.PUBLICO
) : Serializable

data class GameAction(
    val type: GameActionType,
    val actor: String,
    val target: String,
    val round: Int,
    val phase: GamePhase,
    val publiclyKnown: Boolean = false
) : Serializable

data class GameSpecialVictory(
    val key: String,
    val playerName: String,
    val roleKey: String,
    val round: Int
) : Serializable

enum class GameActionType : Serializable {
    KILL,
    SILENCE,
    INVESTIGATE,
    PROTECT,
    INVITE_DEAD,
    VOTE
}

enum class GamePhase : Serializable {
    REPARTO,
    NOCHE_ASESINO,
    NOCHE_MERCENARIO,
    NOCHE_POLICIA,
    NOCHE_MEDICO,
    NOCHE_ORACULO,
    AMANECER,
    DIA_DEBATE,
    CONTRAPUNTO,
    VOTACION,
    RECUENTO_VOTOS,
    DESEMPATE_VOTACION,
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
        if (alive.isEmpty()) return ""
        if (alive.none { it.role?.key in killerRoleKeys }) return TOWN_WINNER

        val desertor = alive.firstOrNull { it.role?.key == "desertor" }
        if (desertor != null && session.desertorTeam.isBlank()) return ""
        val traitors = alive.count { isTraitorRole(it.role) }
        val townForParity = alive.count { it.role?.team == TOWN_WINNER || it.role?.key == "desertor" }
        return when {
            traitors >= townForParity -> TRAITOR_WINNER
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
    const val TEST_MIN_PLAYERS = 3
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 15

    val maps = listOf(
        GameMap("pampa", "Pampa", R.drawable.mapa_pampa, "gaucho"),
        GameMap("grecia", "Grecia", R.drawable.mapa_grecia, "griego"),
        GameMap("medieval", "Medieval", R.drawable.mapa_medieval, "medieval")
    )

    private val defaultBots = listOf(
        "Thiago",
        "Mora",
        "Lautaro",
        "Valen",
        "Rami",
        "Juli",
        "Santi",
        "Mili",
        "Toto",
        "Agus",
        "Bruno",
        "Lola",
        "Fede",
        "Cata"
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
            players = players.take(MIN_PLAYERS),
            quickTestMode = false,
            debugBotsObeyVoteCommands = false,
            debugBotsNeverKillHuman = false,
            debugBotsNeverVoteHuman = false
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
        return session.copy(
            code = "ONLINE-MOCK",
            quickTestMode = false,
            debugBotsObeyVoteCommands = false,
            debugBotsNeverKillHuman = false,
            debugBotsNeverVoteHuman = false
        )
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
        return RoleCatalog.minimumPlayers(roleKey)
    }

    fun assignRoles(session: GameSession, forcedHumanRoleKey: String = ""): GameSession {
        val suffix = maps.firstOrNull { it.key == session.mapKey }?.roleSuffix ?: "gaucho"
        val roleMap = RoleMap.fromSessionKey(session.mapKey)
        val effectiveForcedRole = forcedHumanRoleKey.takeIf {
            it.isBlank() || RoleCatalog.isAvailableOnMap(it, roleMap)
        }.orEmpty()
        val normalizedComposition = normalizedRoleComposition(
            session,
            minimumPlayers = if (session.onlineTestMode) TEST_MIN_PLAYERS else MIN_PLAYERS
        )
        val roles = roleDeckFor(
            session.players.size,
            suffix,
            effectiveForcedRole,
            normalizedComposition
        )

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
            voteRound = 0,
            tieVoteCandidates = emptyList(),
            publicAnnouncement = publicStart,
            privateHint = privateStart,
            publicHistory = listOf(publicStart),
            chatHistory = listOf(GameChatMessage(GameplayFeedMessages.GOD_SPEAKER, publicStart, isGod = true)),
            claimLedger = emptyMap(),
            tableMemory = TableMemory(),
            traitorPlan = null,
            godHistory = listOf(publicStart),
            actionHistory = emptyList(),
            payadorUsed = false,
            contrapuntoPlayers = emptyList(),
            contrapuntoSuspicion = "",
            oracleUsed = false,
            oracleInvitedPlayer = "",
            oracleRevealPending = false,
            alcaldeRevealed = false,
            alcaldeCorruption = false,
            alcaldeTieCandidates = emptyList(),
            desertorTeam = initialDesertorTeam(assignedPlayers, session.code),
            desertorChangedTeam = false,
            initialPlayerCount = assignedPlayers.size,
            startedAtEpochMs = System.currentTimeMillis(),
            specialVictories = emptyList(),
            winner = "",
            roleComposition = normalizedComposition,
            phaseIndex = 0
        )
    }

    fun defaultRoleComposition(playerCount: Int, mapKey: String): RoleCompositionConfig {
        return roleCompositionPreset(
            playerCount,
            mapKey,
            RoleCompositionPreset.RECOMMENDED
        )
    }

    fun roleCompositionPreset(
        playerCount: Int,
        mapKey: String,
        preset: RoleCompositionPreset
    ): RoleCompositionConfig {
        val count = playerCount.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        val map = RoleMap.fromSessionKey(mapKey)
        val counts = linkedMapOf(
            RoleCatalog.POLICIA to 1,
            RoleCatalog.MEDICO to 1,
            RoleCatalog.ASESINO to 1
        )
        when (preset) {
            RoleCompositionPreset.RECOMMENDED -> {
                if (count >= 7) counts[RoleCatalog.MERCENARIO] = 1
                if (count >= 8) counts[RoleCatalog.ALCALDE] = 1
                if (count >= 8) counts[exclusiveRoleForMap(map)] = 1
                if (count >= 9) counts[RoleCatalog.DESERTOR] = 1
                if (count >= 10) counts[RoleCatalog.ESPIA] = 1
                if (count >= 13) counts[RoleCatalog.ASESINO] = 2
            }
            RoleCompositionPreset.CLASSIC -> Unit
            RoleCompositionPreset.CHAOTIC -> {
                counts[RoleCatalog.ASESINO] = maxAssassinsFor(count)
                if (count >= 7) counts[RoleCatalog.MERCENARIO] = 1
                if (count >= 8) counts[RoleCatalog.ALCALDE] = 1
                if (count >= 8) counts[exclusiveRoleForMap(map)] = 1
                if (count >= 9) counts[RoleCatalog.DESERTOR] = 1
                if (count >= 10) counts[RoleCatalog.ESPIA] = 1
            }
        }
        val specialCount = counts.values.sum()
        counts[RoleCatalog.ALDEANO] = (count - specialCount).coerceAtLeast(0)
        return RoleCompositionConfig(
            counts = counts,
            customized = preset != RoleCompositionPreset.RECOMMENDED
        )
    }

    fun onlineSafeRoleComposition(playerCount: Int): RoleCompositionConfig {
        val count = playerCount.coerceIn(TEST_MIN_PLAYERS, MAX_PLAYERS)
        val counts = linkedMapOf(
            RoleCatalog.POLICIA to 1,
            RoleCatalog.MEDICO to 1,
            RoleCatalog.ASESINO to 1
        )
        if (count >= 7) counts[RoleCatalog.MERCENARIO] = 1
        val specialCount = counts.values.sum()
        counts[RoleCatalog.ALDEANO] = (count - specialCount).coerceAtLeast(0)
        return RoleCompositionConfig(counts = counts, customized = true)
    }

    fun editableRoleKeys(): List<String> {
        return listOf(
            RoleCatalog.ALDEANO,
            RoleCatalog.POLICIA,
            RoleCatalog.MEDICO,
            RoleCatalog.ASESINO,
            RoleCatalog.MERCENARIO,
            RoleCatalog.ALCALDE,
            RoleCatalog.DESERTOR,
            RoleCatalog.ESPIA,
            RoleCatalog.PAYADOR,
            RoleCatalog.ORACULO,
            RoleCatalog.BUFON
        )
    }

    fun visibleRoleCompositionKeys(): List<String> {
        return editableRoleKeys().filterNot { it == RoleCatalog.MERCENARIO }
    }

    fun normalizedRoleComposition(
        session: GameSession,
        minimumPlayers: Int = if (session.onlineTestMode) TEST_MIN_PLAYERS else MIN_PLAYERS
    ): RoleCompositionConfig {
        val playerCount = session.players.size.coerceIn(minimumPlayers, MAX_PLAYERS)
        val map = RoleMap.fromSessionKey(session.mapKey)
        val source = if (session.roleComposition.customized) {
            session.roleComposition
        } else {
            defaultRoleComposition(playerCount, session.mapKey)
        }
        val normalized = linkedMapOf<String, Int>()
        editableRoleKeys().forEach { key ->
            if (RoleCatalog.isAvailableOnMap(key, map) && playerCount >= RoleCatalog.minimumPlayers(key)) {
                normalized[key] = source.counts[key]?.coerceAtLeast(0) ?: 0
            } else {
                normalized[key] = 0
            }
        }
        normalized[RoleCatalog.POLICIA] = normalized.getValue(RoleCatalog.POLICIA).coerceAtLeast(1)
        normalized[RoleCatalog.MEDICO] = normalized.getValue(RoleCatalog.MEDICO).coerceAtLeast(1)
        normalized[RoleCatalog.ASESINO] = normalized.getValue(RoleCatalog.ASESINO)
            .coerceIn(1, maxAssassinsFor(playerCount))

        val nonVillagers = normalized
            .filterKeys { it != RoleCatalog.ALDEANO }
            .values
            .sum()
        if (nonVillagers > playerCount) {
            val orderedOptional = listOf(
                RoleCatalog.ESPIA,
                RoleCatalog.DESERTOR,
                exclusiveRoleForMap(map),
                RoleCatalog.ALCALDE,
                RoleCatalog.MERCENARIO,
                RoleCatalog.MEDICO,
                RoleCatalog.POLICIA
            )
            var overflow = nonVillagers - playerCount
            orderedOptional.forEach { key ->
                if (overflow <= 0) return@forEach
                val minimum = if (key == RoleCatalog.POLICIA || key == RoleCatalog.MEDICO) 1 else 0
                val removable = (normalized.getValue(key) - minimum).coerceAtLeast(0)
                val removed = removable.coerceAtMost(overflow)
                normalized[key] = normalized.getValue(key) - removed
                overflow -= removed
            }
        }

        val finalNonVillagers = normalized
            .filterKeys { it != RoleCatalog.ALDEANO }
            .values
            .sum()
        normalized[RoleCatalog.ALDEANO] = (playerCount - finalNonVillagers).coerceAtLeast(0)
        return RoleCompositionConfig(counts = normalized, customized = source.customized)
    }

    fun maxCountForRole(roleKey: String, playerCount: Int, mapKey: String = ""): Int {
        val count = playerCount.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        if (mapKey.isNotBlank() && !RoleCatalog.isAvailableOnMap(roleKey, RoleMap.fromSessionKey(mapKey))) {
            return 0
        }
        return when (roleKey) {
            RoleCatalog.ALDEANO -> count
            RoleCatalog.ASESINO -> maxAssassinsFor(count)
            RoleCatalog.POLICIA, RoleCatalog.MEDICO -> 1
            RoleCatalog.MERCENARIO -> if (count >= 7) 1 else 0
            RoleCatalog.ALCALDE -> if (count >= 8) 1 else 0
            RoleCatalog.DESERTOR -> if (count >= 9) 1 else 0
            RoleCatalog.ESPIA -> if (count >= 10) 1 else 0
            RoleCatalog.PAYADOR, RoleCatalog.BUFON, RoleCatalog.ORACULO -> if (count >= 8) 1 else 0
            else -> 0
        }
    }

    private fun maxAssassinsFor(playerCount: Int): Int {
        return when {
            playerCount >= 13 -> 3
            playerCount >= 8 -> 2
            else -> 1
        }
    }

    private fun exclusiveRoleForMap(map: RoleMap): String {
        return when (map) {
            RoleMap.PAMPA -> RoleCatalog.PAYADOR
            RoleMap.GREECE -> RoleCatalog.ORACULO
            RoleMap.MEDIEVAL -> RoleCatalog.BUFON
        }
    }

    private fun roleDeckFor(
        playerCount: Int,
        suffix: String,
        forcedHumanRoleKey: String,
        composition: RoleCompositionConfig
    ): List<GameRole> {
        val roles = mutableListOf<GameRole>()
        composition.counts.forEach { (key, count) ->
            repeat(count.coerceAtLeast(0)) {
                roles += roleForKey(key, suffix)
            }
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

    internal fun initialDesertorTeam(players: List<GamePlayer>, sessionCode: String): String {
        val desertor = players.firstOrNull { it.role?.key == "desertor" } ?: return ""
        if (desertor.isHuman) return ""
        // El codigo local es siempre "SALA-01", asi que la semilla incorpora el orden ya
        // barajado de roles (players) para que el bando inicial varie entre partidas.
        val seed = stableNoise("$sessionCode|${players.joinToString("|") { "${it.name}:${it.role?.key.orEmpty()}" }}")
        return if ((seed ushr 1) and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
    }

    private fun roleForKey(key: String, suffix: String): GameRole {
        val map = RoleMap.entries.firstOrNull { it.imageSuffix == suffix } ?: RoleMap.PAMPA
        return RoleCatalog.gameRole(key, map)
    }
}

data class GameMap(
    val key: String,
    val name: String,
    val imageRes: Int,
    val roleSuffix: String
)
