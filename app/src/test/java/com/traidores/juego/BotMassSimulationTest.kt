package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotMassSimulationTest {

    data class SimulationResult(
        val winner: String,
        val rounds: Int,
        val specialVictories: List<GameSpecialVictory>,
        val stalled: Boolean,
        val stallPhase: GamePhase?,
        val stallReason: String,
        val mapKey: String,
        val playerCount: Int
    )

    private fun runSingleMatch(
        playerCount: Int,
        mapKey: String,
        difficulty: BotDifficulty = BotDifficulty.NORMAL
    ): SimulationResult {
        val map = LocalGameFactory.maps.first { it.key == mapKey }
        val botNames = listOf(
            "Thiago", "Mora", "Lautaro", "Valen", "Rami",
            "Juli", "Santi", "Mili", "Toto", "Agus",
            "Bruno", "Lola", "Fede", "Cata", "Nico"
        )
        val players = botNames.take(playerCount).map { name ->
            GamePlayer(name, name.take(1), isHuman = false)
        }

        var session = GameSession(
            code = "SIM-BOTS",
            mapKey = map.key,
            mapName = map.name,
            players = players,
            botDifficulty = difficulty,
            quickTestMode = true
        )
        session = LocalGameFactory.assignRoles(session)
        session = GameEngine.startNight(session)

        var stepCount = 0
        val maxSteps = 500
        var stallReason = ""

        while (session.winner.isBlank() && stepCount < maxSteps) {
            stepCount++
            val prevSession = session

            session = when (session.phase) {
                GamePhase.NOCHE_ASESINO,
                GamePhase.NOCHE_MERCENARIO,
                GamePhase.NOCHE_POLICIA,
                GamePhase.NOCHE_MEDICO,
                GamePhase.NOCHE_ORACULO -> {
                    GameEngine.resolveLocalNightWindowTimeout(session)
                }
                GamePhase.AMANECER -> {
                    GameEngine.resolveDawn(session)
                }
                GamePhase.DIA_DEBATE -> {
                    GameEngine.resolveDayDebate(session)
                }
                GamePhase.CONTRAPUNTO -> {
                    GameEngine.resolveContrapuntoTimeout(session)
                }
                GamePhase.VOTACION -> {
                    GameEngine.resolveVoting(session, "")
                }
                GamePhase.DESEMPATE_VOTACION -> {
                    GameEngine.resolveTieVoting(session, "")
                }
                GamePhase.RECUENTO_VOTOS -> {
                    val withWords = GameEngine.addEliminationLastWords(session)
                    GameEngine.continueAfterVoteRecount(withWords)
                }
                GamePhase.ALCALDE_DESEMPATE -> {
                    GameEngine.resolveAlcaldeTieTimeout(session)
                }
                GamePhase.RESULTADO -> {
                    GameEngine.resolveResult(session)
                }
                GamePhase.REPARTO -> {
                    GameEngine.startNight(session)
                }
            }

            if (session == prevSession && session.winner.isBlank()) {
                val resolved = GameEngine.resolveHumanTimeout(session)
                if (resolved == session) {
                    stallReason = "Sin avance en fase ${session.phase} (round ${session.round}, vivos: ${session.players.filter { it.alive }.map { it.name + ":" + it.role?.key }})"
                    break
                }
                session = resolved
            }
        }

        if (stepCount >= maxSteps && session.winner.isBlank()) {
            stallReason = "Excedió $maxSteps pasos en fase ${session.phase}"
        }

        return SimulationResult(
            winner = session.winner,
            rounds = session.round,
            specialVictories = session.specialVictories,
            stalled = session.winner.isBlank(),
            stallPhase = if (session.winner.isBlank()) session.phase else null,
            stallReason = stallReason,
            mapKey = mapKey,
            playerCount = playerCount
        )
    }

    @Test
    fun simulate500MatchesAcrossAllMapsAndPlayerCounts() {
        val maps = listOf("pampa", "grecia", "medieval")
        val playerCounts = (5..10).toList()
        val totalMatches = 500
        val results = mutableListOf<SimulationResult>()

        var townWins = 0
        var traitorWins = 0
        var jesterWins = 0
        var stalledMatches = 0
        var totalRounds = 0
        val stallDiagnostics = mutableListOf<String>()

        for (i in 1..totalMatches) {
            val mapKey = maps[i % maps.size]
            val playerCount = playerCounts[i % playerCounts.size]
            val difficulty = if (i % 2 == 0) BotDifficulty.NORMAL else BotDifficulty.HARD

            val result = runSingleMatch(playerCount, mapKey, difficulty)
            results.add(result)

            if (result.stalled) {
                stalledMatches++
                if (stallDiagnostics.size < 10) {
                    stallDiagnostics.add("[Map: $mapKey, Players: $playerCount] ${result.stallReason}")
                }
            } else {
                totalRounds += result.rounds
                when (result.winner) {
                    GameRules.TOWN_WINNER -> townWins++
                    GameRules.TRAITOR_WINNER -> traitorWins++
                }
                if (result.specialVictories.any { it.roleKey == RoleCatalog.BUFON }) {
                    jesterWins++
                }
            }
        }

        val completedMatches = totalMatches - stalledMatches
        val avgRounds = if (completedMatches > 0) totalRounds.toDouble() / completedMatches else 0.0
        val townWinRate = if (completedMatches > 0) (townWins.toDouble() / completedMatches) * 100 else 0.0
        val traitorWinRate = if (completedMatches > 0) (traitorWins.toDouble() / completedMatches) * 100 else 0.0

        println("==================================================")
        println("=== REPORTE DE SIMULACIÓN MASIVA: 500 PARTIDAS ===")
        println("==================================================")
        println("Total de partidas: $totalMatches")
        println("Partidas completadas sin bloqueos: $completedMatches / $totalMatches (100%)")
        println("Partidas bloqueadas (Deadlocks): $stalledMatches")
        println("Victorias de Inocentes (Pueblo): $townWins (${"%.1f".format(townWinRate)}%)")
        println("Victorias de Traidores: $traitorWins (${"%.1f".format(traitorWinRate)}%)")
        println("Victorias especiales de Bufón: $jesterWins")
        println("Promedio de rondas por partida: ${"%.2f".format(avgRounds)} rondas")
        if (stallDiagnostics.isNotEmpty()) {
            println("--- MUESTRA DE BLOQUEOS DETECTADOS ---")
            stallDiagnostics.forEach { println(" - $it") }
        }
        println("==================================================")

        assertFalse("No debe haber partidas bloqueadas (deadlocks): $stallDiagnostics", stalledMatches > 0)
        assertTrue("Todas las partidas deben tener un ganador válido", completedMatches == totalMatches)
    }
}
