package com.traidores.juego

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulacion determinista y sin Firebase de las barreras criticas del online.
 *
 * Repite 100 partidas por cada tamano soportado. Inyecta ACK demorados, datos de otra
 * partida, cartas temporalmente incompletas, orden de llegada alterado, votos corregidos,
 * pulsos perdidos y caida del anfitrion. Si una politica vuelve a permitir un bloqueo o
 * mezcla datos de otra fase, este test falla durante el desarrollo.
 */
class OnlineProtocolSimulationTest {

    @Test
    fun fiveHundredOnlineMatchesRemainRecoverableAndIsolated() {
        val sizes = listOf(3, 6, 9, 12, 15)
        var simulations = 0
        var quorumRecoveries = 0
        var hostHandoffs = 0

        sizes.forEach { playerCount ->
            repeat(SIMULATIONS_PER_SIZE) { iteration ->
                val random = Random(playerCount * 10_000 + iteration)
                val ids = (0 until playerCount).map { "p$it" }
                val expectedIds = ids.toSet()
                val matchId = "match-$playerCount-$iteration"

                simulateLobbyEntry(expectedIds, matchId, random)
                if (simulateStartup(ids, random)) quorumRecoveries++
                simulateVoteResolution(ids, matchId, random)
                simulateNightActions(ids, matchId, random)
                simulateMedicAndVictoryLogic(ids)
                simulatePresencePulses(random)
                simulateHostHandoff(ids, random)
                hostHandoffs++
                simulations++
            }
        }

        assertEquals(500, simulations)
        assertTrue("La simulacion debe ejercer recuperaciones por quorum", quorumRecoveries > 0)
        assertEquals(500, hostHandoffs)
        println(
            "ONLINE_SIMULATION_OK matches=$simulations sizes=$sizes " +
                "quorumRecoveries=$quorumRecoveries hostHandoffs=$hostHandoffs"
        )
    }

    private fun simulateLobbyEntry(
        expectedIds: Set<String>,
        matchId: String,
        random: Random
    ) {
        val hostId = expectedIds.first()
        val arrivalMs = expectedIds.associateWith { random.nextLong(0L, 3_601L) }
        val states = linkedMapOf<String, Any?>()
        var releasedAt: Long? = null

        // Un ACK de la partida anterior nunca puede liberar la barrera actual.
        states[expectedIds.last()] = clientState("old-$matchId", ready = true)
        assertFalse(OnlineLobbyEntryGate.canRelease(expectedIds, matchId, states))

        for (elapsed in 0L..3_000L step 100L) {
            expectedIds.forEach { id ->
                if (id != hostId && arrivalMs.getValue(id) <= elapsed) {
                    states[id] = clientState(matchId, ready = true)
                }
            }
            val allReady = OnlineLobbyEntryGate.canReleaseWithLocalReady(
                expectedPlayerIds = expectedIds,
                matchId = matchId,
                clientStates = states,
                localPlayerId = hostId,
                localPlayerReady = true
            )
            val safeFallback = OnlineLobbyEntryGate.canReleaseAfterTimeout(
                expectedPlayerIds = expectedIds,
                matchId = matchId,
                clientStates = states,
                localPlayerId = hostId,
                localPlayerReady = true,
                connectedPlayerIds = expectedIds,
                elapsedMs = elapsed
            )
            if (allReady || safeFallback) {
                releasedAt = elapsed
                break
            }
        }

        assertNotNull("La entrada no puede quedar esperando indefinidamente", releasedAt)
        assertTrue(releasedAt!! <= OnlineLobbyEntryGate.FULLY_CONNECTED_RELEASE_AFTER_MS)
    }

    /** Devuelve true cuando fue necesario usar el watchdog de quorum. */
    private fun simulateStartup(ids: List<String>, random: Random): Boolean {
        val expected = ids.size
        val toleratedLag = expected - OnlineStartQuorum.requiredPlayers(expected)
        val laggingCount = if (toleratedLag == 0) 0 else random.nextInt(0, toleratedLag + 1)
        val lagging = ids.shuffled(random).take(laggingCount).toSet()
        val states = ids.mapIndexed { order, id ->
            OnlineStartupClientState(
                uid = id,
                inGameplay = true,
                visiblePlayers = if (id in lagging) (expected - 1).coerceAtLeast(0) else expected,
                phase = GamePhase.REPARTO.name,
                phaseIndex = 0,
                roleRead = true,
                order = order
            )
        }
        val result = OnlineStartupGate.evaluate(expected, states)

        if (lagging.isEmpty()) {
            assertTrue(result.canStart)
            return false
        }

        assertFalse(result.canStart)
        assertTrue(
            OnlineStartupGate.shouldHardTimeoutStart(
                startedAtEpochMs = 1_000L,
                nowEpochMs = 1_000L + OnlineStartupGate.HARD_STARTUP_TIMEOUT_MS,
                reportedPlayers = result.reportedPlayers,
                roleReadPlayers = result.roleReadPlayers,
                expectedPlayers = expected,
                connectedPlayers = expected
            )
        )
        return true
    }

    private fun simulateVoteResolution(ids: List<String>, matchId: String, random: Random) {
        val phaseIndex = 7
        val records = mutableListOf<OnlineActionRecord>()
        val expectedVotes = linkedMapOf<String, String>()
        ids.forEachIndexed { index, actorId ->
            val firstTarget = ids[(index + 1) % ids.size]
            val finalTarget = ids[(index + 2) % ids.size]
            records += action(matchId, actorId, "votar", firstTarget, phaseIndex, 10L + index)
            records += action(matchId, actorId, "votar", finalTarget, phaseIndex, 100L + index)
            records += action("old-$matchId", actorId, "votar", ids.first(), phaseIndex, 999L)
            records += action(matchId, actorId, "votar", ids.first(), phaseIndex - 1, 1_000L)
            expectedVotes[actorId] = finalTarget
        }

        val resolved = OnlineActionResolver.votes(
            records = records.shuffled(random),
            matchId = matchId,
            round = 1,
            expectedPhaseName = GamePhase.VOTACION.name,
            phaseIndex = phaseIndex
        )
        assertEquals(expectedVotes, resolved)

        var scheduledPhaseIndex = -1
        var schedules = 0
        repeat(20) {
            if (
                OnlineVoteResolutionGate.canSchedule(
                    isOnline = true,
                    isHost = true,
                    phase = GamePhase.VOTACION,
                    phaseIndex = phaseIndex,
                    scheduledPhaseIndex = scheduledPhaseIndex,
                    resolutionInProgress = false
                )
            ) {
                schedules++
                scheduledPhaseIndex = phaseIndex
            }
        }
        assertEquals("Un plazo vencido solo puede resolver una vez", 1, schedules)
        assertTrue(
            OnlineVoteResolutionGate.blocksCountdown(
                GamePhase.VOTACION,
                phaseIndex,
                scheduledPhaseIndex,
                resolutionInProgress = false
            )
        )
    }

    private fun simulateNightActions(ids: List<String>, matchId: String, random: Random) {
        val phaseIndex = 3
        val victim = ids.last()
        val alternate = ids[1]
        val records = listOf(
            action(matchId, "asesino", "matar", victim, phaseIndex, 10),
            action(matchId, "asesino", "matar", alternate, phaseIndex, 20),
            action(matchId, "mercenario", "silenciar", alternate, phaseIndex, 11),
            action(matchId, "policia", "investigar", victim, phaseIndex, 12),
            action(matchId, "medico", "salvar", victim, phaseIndex, 13),
            action(matchId, "oraculo", "guardar_poder", "", phaseIndex, 14),
            action("old-$matchId", "medico", "salvar", alternate, phaseIndex, 1),
            action(matchId, "medico", "salvar", alternate, phaseIndex - 1, 2)
        ).shuffled(random)

        val resolved = OnlineActionResolver.nightActions(
            records = records,
            matchId = matchId,
            round = 1,
            phaseIndex = phaseIndex
        )
        assertEquals(victim, resolved.assassinVotes["asesino"])
        assertEquals(victim, resolved.medicAction?.targetName)
        assertEquals(alternate, resolved.mercenaryAction?.targetName)
        assertEquals(victim, resolved.policeAction?.targetName)
        assertEquals("guardar_poder", resolved.oracleAction?.action)
    }

    private fun simulateMedicAndVictoryLogic(ids: List<String>) {
        val players = ids.mapIndexed { index, id ->
            val role = when (index) {
                0 -> role(RoleCatalog.ASESINO, GameRules.TRAITOR_WINNER)
                1 -> role(RoleCatalog.MEDICO, GameRules.TOWN_WINNER)
                else -> role(RoleCatalog.ALDEANO, GameRules.TOWN_WINNER)
            }
            GamePlayer(id, id.takeLast(1), role = role, control = PlayerControl.REMOTE)
        }
        val victim = players.last().name
        val dawn = GameEngine.resolveDawn(
            GameSession(
                code = "SIM",
                mapKey = "pampa",
                mapName = "Pampa",
                players = players,
                phase = GamePhase.AMANECER,
                nightKillTarget = victim,
                protectedPlayer = victim
            )
        )
        assertTrue(GameEngine.playerByName(dawn, victim)!!.alive)
        assertTrue(dawn.nightHadNoVictim)

        val withoutKiller = dawn.copy(
            players = dawn.players.map {
                if (it.role?.key == RoleCatalog.ASESINO) it.copy(alive = false) else it
            }
        )
        assertEquals(GameRules.TOWN_WINNER, GameRules.winnerFor(withoutKiller))
    }

    private fun simulatePresencePulses(random: Random) {
        val maximumPulse = OnlineSyncWatchdog.PRESENCE_PULSE_MS +
            OnlineSyncWatchdog.PRESENCE_JITTER_MS
        val nextValidPulseAfterTwoMisses = maximumPulse * 3
        val observation = random.nextLong(maximumPulse * 2, nextValidPulseAfterTwoMisses + 1)
        assertFalse(
            "Dos pulsos demorados no deben mostrar una desconexion falsa",
            OnlineSyncWatchdog.shouldShowReconnecting(
                connected = true,
                lastHeartbeatEpochMs = 1_000L,
                nowEpochMs = 1_000L + observation
            )
        )
        assertTrue(
            OnlineSyncWatchdog.shouldShowReconnecting(
                connected = true,
                lastHeartbeatEpochMs = 1_000L,
                nowEpochMs = 1_000L + OnlineSyncWatchdog.PRESENCE_RECONNECTING_AFTER_MS + 1L
            )
        )
    }

    private fun simulateHostHandoff(ids: List<String>, random: Random) {
        val registeredCandidate = ids.drop(1).shuffled(random).first()
        val players = ids.mapIndexed { index, id ->
            OnlineLobbyParticipant(
                id = id,
                connected = index != 0,
                ready = true,
                activeInMatch = true,
                order = index,
                registered = id == registeredCandidate
            )
        }
        val registered = OnlineLobbyRules.hostHandoffCandidate(
            players = players,
            activeHostId = ids.first(),
            allowGuests = false
        )
        assertEquals(registeredCandidate, registered?.id)

        val emergency = OnlineLobbyRules.hostHandoffCandidate(
            players = players.map { it.copy(registered = false) },
            activeHostId = ids.first(),
            allowGuests = true
        )
        assertNotNull(emergency)
        assertTrue(emergency!!.connected)
        assertTrue(emergency.id != ids.first())
    }

    private fun clientState(matchId: String, ready: Boolean): Map<String, Any?> = mapOf(
        OnlineLobbyEntryGate.FIELD_MATCH_ID to matchId,
        OnlineLobbyEntryGate.FIELD_ENTRY_READY to ready
    )

    private fun action(
        matchId: String,
        actor: String,
        type: String,
        target: String,
        phaseIndex: Int,
        createdAt: Long
    ) = OnlineActionRecord(
        matchId = matchId,
        actorId = "uid-$actor",
        action = type,
        actorName = actor,
        targetName = target,
        phaseName = if (type == "votar") GamePhase.VOTACION.name else GamePhase.NOCHE_ASESINO.name,
        round = 1,
        phaseIndex = phaseIndex,
        createdAtLocal = createdAt
    )

    private fun role(key: String, team: String) = GameRole(
        key = key,
        name = key,
        team = team,
        imageResName = ""
    )

    private companion object {
        const val SIMULATIONS_PER_SIZE = 100
    }
}
