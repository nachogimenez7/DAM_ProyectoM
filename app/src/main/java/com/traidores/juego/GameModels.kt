package com.traidores.juego

import java.io.Serializable

data class GameSession(
    val code: String,
    val mapKey: String,
    val mapName: String,
    val players: List<GamePlayer>,
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
    val winner: String = "",
    val phaseIndex: Int = 0
) : Serializable

data class GamePlayer(
    val name: String,
    val initial: String,
    val role: GameRole? = null,
    val alive: Boolean = true,
    val muted: Boolean = false,
    val isHuman: Boolean = false
) : Serializable

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
    VOTACION,
    RESULTADO
}

object LocalGameFactory {
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 15

    val maps = listOf(
        GameMap("pampa", "Pampa", R.drawable.mapa_pampa, "gaucho"),
        GameMap("grecia", "Grecia", R.drawable.mapa_grecia, "griego"),
        GameMap("medieval", "Medieval", R.drawable.mapa_medieval, "medieval")
    )

    private val defaultPlayers = listOf(
        GamePlayer("Nacho", "N", isHuman = true),
        GamePlayer("Martina", "M"),
        GamePlayer("Tomas", "T"),
        GamePlayer("Sofia", "S"),
        GamePlayer("Camila", "C"),
        GamePlayer("Juan", "J"),
        GamePlayer("Valen", "V"),
        GamePlayer("Bruno", "B"),
        GamePlayer("Luz", "L"),
        GamePlayer("Mateo", "A"),
        GamePlayer("Rocio", "R"),
        GamePlayer("Julia", "U"),
        GamePlayer("Nico", "I"),
        GamePlayer("Flor", "F"),
        GamePlayer("Dante", "D")
    )

    fun createSession(joinedByCode: Boolean = false): GameSession {
        val map = maps.first()
        return GameSession(
            code = if (joinedByCode) "PAMPA-42" else "SALA-01",
            mapKey = map.key,
            mapName = map.name,
            players = defaultPlayers.take(MIN_PLAYERS)
        )
    }

    fun selectMap(session: GameSession, mapKey: String): GameSession {
        val map = maps.firstOrNull { it.key == mapKey } ?: maps.first()
        return session.copy(mapKey = map.key, mapName = map.name)
    }

    fun addMockPlayer(session: GameSession): GameSession {
        if (session.players.size >= MAX_PLAYERS) return session
        val currentNames = session.players.map { it.name }.toSet()
        val next = defaultPlayers.firstOrNull { it.name !in currentNames } ?: return session
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

    fun assignRoles(session: GameSession): GameSession {
        val suffix = maps.firstOrNull { it.key == session.mapKey }?.roleSuffix ?: "gaucho"
        val roles = roleDeckFor(session.players.size, suffix)

        val shuffledRoles = roles.shuffled()
        val assignedPlayers = session.players.mapIndexed { index, player ->
            player.copy(role = shuffledRoles[index], alive = true, muted = false)
        }
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
            winner = "",
            phaseIndex = 0
        )
    }

    private fun roleDeckFor(playerCount: Int, suffix: String): List<GameRole> {
        val roles = mutableListOf(
            GameRole("policia", if (suffix == "gaucho") "Comisario" else "Detective", "Pueblo", "rol_detective_$suffix"),
            GameRole("asesino", "Asesino", "Traidores", "rol_asesino_$suffix"),
            GameRole("medico", "Medico", "Pueblo", "rol_medico_$suffix")
        )
        if (playerCount >= 7) {
            roles += GameRole("mercenario", "Mercenario", "Traidores", "rol_mercenario_$suffix")
        }
        if (playerCount >= 8) {
            roles += GameRole("alcalde", "Alcalde", "Pueblo", "rol_alcalde_$suffix")
        }
        if (playerCount >= 10) {
            roles += GameRole("espia", "Espia", "Traidores", "rol_espia_$suffix")
        }
        roles += List((playerCount - roles.size).coerceAtLeast(0)) {
            GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_$suffix")
        }
        return roles.take(playerCount)
    }
}

data class GameMap(
    val key: String,
    val name: String,
    val imageRes: Int,
    val roleSuffix: String
)
