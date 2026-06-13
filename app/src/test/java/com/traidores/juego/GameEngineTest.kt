package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun roleRevealGateProtectsMinimumReadingTimeEvenWhenEveryoneIsReady() {
        val players = setOf("a", "b", "c")

        val beforeMinimum = RoleRevealGate.evaluate(
            RoleRevealConfig(),
            elapsedSeconds = 9,
            readyPlayerIds = players,
            connectedPlayerIds = players
        )
        val atMinimum = RoleRevealGate.evaluate(
            RoleRevealConfig(),
            elapsedSeconds = 10,
            readyPlayerIds = players,
            connectedPlayerIds = players
        )

        assertFalse(beforeMinimum.canStart)
        assertEquals(RoleRevealStartReason.WAITING_FOR_MINIMUM, beforeMinimum.reason)
        assertTrue(atMinimum.canStart)
        assertEquals(RoleRevealStartReason.ALL_READY, atMinimum.reason)
    }

    @Test
    fun balancedRoleRevealStartsAtTimeLimitWithoutExposingWhoIsReading() {
        val decision = RoleRevealGate.evaluate(
            RoleRevealConfig(
                mode = RoleRevealMode.BALANCED,
                minimumReadingSeconds = 10,
                maximumWaitingSeconds = 30
            ),
            elapsedSeconds = 30,
            readyPlayerIds = setOf("a", "b"),
            connectedPlayerIds = setOf("a", "b", "c", "d")
        )

        assertTrue(decision.canStart)
        assertEquals(RoleRevealStartReason.TIME_LIMIT_REACHED, decision.reason)
        assertEquals(2, decision.readyPlayers)
        assertEquals(4, decision.totalPlayers)
    }

    @Test
    fun waitForAllRoleRevealIgnoresMaximumAndQuickUsesProtectedMinimum() {
        val players = setOf("a", "b", "c")
        val waiting = RoleRevealGate.evaluate(
            RoleRevealConfig(
                mode = RoleRevealMode.WAIT_FOR_ALL,
                minimumReadingSeconds = 5,
                maximumWaitingSeconds = 10
            ),
            elapsedSeconds = 90,
            readyPlayerIds = setOf("a", "b"),
            connectedPlayerIds = players
        )
        val quick = RoleRevealGate.evaluate(
            RoleRevealConfig(
                mode = RoleRevealMode.QUICK,
                minimumReadingSeconds = 10,
                maximumWaitingSeconds = 60
            ),
            elapsedSeconds = 10,
            readyPlayerIds = emptySet(),
            connectedPlayerIds = players
        )

        assertFalse(waiting.canStart)
        assertEquals(RoleRevealStartReason.WAITING_FOR_PLAYERS, waiting.reason)
        assertTrue(quick.canStart)
        assertEquals(RoleRevealStartReason.TIME_LIMIT_REACHED, quick.reason)
    }

    @Test
    fun roleRevealConfigClampsInvalidReadingLimits() {
        val normalized = RoleRevealConfig(
            minimumReadingSeconds = 1,
            maximumWaitingSeconds = 2
        ).normalized()

        assertEquals(RoleRevealConfig.MIN_READING_SECONDS, normalized.minimumReadingSeconds)
        assertEquals(normalized.minimumReadingSeconds, normalized.maximumWaitingSeconds)
    }

    @Test
    fun timingConfigUsesDefaultsAndClampsEveryField() {
        assertEquals("5 / 40 / 120 / 20", GameTimingConfig().summary())
        assertEquals(GameTimingPreset.NORMAL, GameTimingConfig().preset())

        val minimums = GameTimingConfig(
            transitionSeconds = -5,
            nightSeconds = 1,
            discussionSeconds = 2,
            votingSeconds = 3
        ).normalized()
        assertEquals(GameTimingConfig.MIN_TRANSITION_SECONDS, minimums.transitionSeconds)
        assertEquals(GameTimingConfig.MIN_NIGHT_SECONDS, minimums.nightSeconds)
        assertEquals(GameTimingConfig.MIN_DISCUSSION_SECONDS, minimums.discussionSeconds)
        assertEquals(GameTimingConfig.MIN_VOTING_SECONDS, minimums.votingSeconds)

        val maximums = GameTimingConfig(
            transitionSeconds = 99,
            nightSeconds = 99,
            discussionSeconds = 999,
            votingSeconds = 99
        ).normalized()
        assertEquals(GameTimingConfig.MAX_TRANSITION_SECONDS, maximums.transitionSeconds)
        assertEquals(GameTimingConfig.MAX_NIGHT_SECONDS, maximums.nightSeconds)
        assertEquals(GameTimingConfig.MAX_DISCUSSION_SECONDS, maximums.discussionSeconds)
        assertEquals(GameTimingConfig.MAX_VOTING_SECONDS, maximums.votingSeconds)
    }

    @Test
    fun timingPresetsUseExpectedValues() {
        assertEquals(GameTimingConfig(7, 90, 180, 60), GameTimingPreset.SLOW.config)
        assertEquals(GameTimingConfig(5, 40, 120, 20), GameTimingPreset.NORMAL.config)
        assertEquals(GameTimingConfig(3, 20, 60, 15), GameTimingPreset.FAST.config)
        assertEquals(GameTimingPreset.SLOW, GameTimingPreset.SLOW.config.preset())
        assertEquals(null, GameTimingConfig(4, 35, 90, 25).preset())
    }

    @Test
    fun rolesStayHiddenOnDeathByDefault() {
        val session = LocalGameFactory.createSession()

        assertFalse(session.revealRolesOnDeath)
    }

    @Test
    fun assigningRolesKeepsLobbyTimingAndResetsAfkStreaks() {
        val configured = LocalGameFactory.createSession().copy(
            timingConfig = GameTimingConfig(5, 30, 90, 25),
            players = LocalGameFactory.createSession().players.map {
                it.copy(consecutiveNightAfk = 1, consecutiveVoteAfk = 1)
            }
        )

        val assigned = LocalGameFactory.assignRoles(configured)

        assertEquals(GameTimingConfig(5, 30, 90, 25), assigned.timingConfig)
        assertTrue(assigned.players.all { it.consecutiveNightAfk == 0 })
        assertTrue(assigned.players.all { it.consecutiveVoteAfk == 0 })
    }

    @Test
    fun firstNightTimeoutLosesActionAndSecondConsecutiveTimeoutExpelsForAfk() {
        val firstNight = sessionWithHumanRole("medico").copy(phase = GamePhase.NOCHE_MEDICO)

        val firstMiss = GameEngine.resolveHumanTimeout(firstNight)
        val warnedHuman = GameEngine.humanPlayer(firstMiss)

        assertEquals(GamePhase.AMANECER, firstMiss.phase)
        assertTrue(warnedHuman.alive)
        assertEquals(1, warnedHuman.consecutiveNightAfk)
        assertTrue(firstMiss.privateHint.contains("proxima noche"))

        val secondMiss = GameEngine.resolveHumanTimeout(
            firstMiss.copy(phase = GamePhase.NOCHE_MEDICO, round = 2, winner = "")
        )
        val expelledHuman = GameEngine.humanPlayer(secondMiss)

        assertFalse(expelledHuman.alive)
        assertEquals(2, expelledHuman.consecutiveNightAfk)
        assertTrue(secondMiss.publicHistory.any { it.contains("expulsado por inactividad") })
    }

    @Test
    fun afkExpulsionRechecksVictoryImmediately() {
        val firstNight = sessionWithHumanRole("asesino").copy(phase = GamePhase.NOCHE_ASESINO)
        val firstMiss = GameEngine.resolveHumanTimeout(firstNight)

        val secondMiss = GameEngine.resolveHumanTimeout(
            firstMiss.copy(phase = GamePhase.NOCHE_ASESINO, round = 2, winner = "")
        )

        assertFalse(GameEngine.humanPlayer(secondMiss).alive)
        assertEquals(GameRules.TOWN_WINNER, secondMiss.winner)
    }

    @Test
    fun validNightActionResetsOnlyNightAfkStreak() {
        val session = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            players = sessionWithHumanRole("medico").players.map {
                if (it.isHuman) {
                    it.copy(consecutiveNightAfk = 1, consecutiveVoteAfk = 1)
                } else {
                    it
                }
            }
        )

        val resolved = GameEngine.resolveHumanTargetAction(session, "Humano")
        val human = GameEngine.humanPlayer(resolved)

        assertEquals(0, human.consecutiveNightAfk)
        assertEquals(1, human.consecutiveVoteAfk)
        assertEquals(GamePhase.AMANECER, resolved.phase)
    }

    @Test
    fun firstVoteTimeoutAbstainsAndSecondConsecutiveTimeoutExpelsForAfk() {
        val firstVote = baseSession().copy(phase = GamePhase.VOTACION)

        val firstMiss = GameEngine.resolveHumanTimeout(firstVote)
        val warnedHuman = GameEngine.humanPlayer(firstMiss)

        assertEquals(GamePhase.RESULTADO, firstMiss.phase)
        assertTrue(warnedHuman.alive)
        assertEquals(1, warnedHuman.consecutiveVoteAfk)
        assertTrue(firstMiss.privateHint.contains("proxima votacion"))

        val secondMiss = GameEngine.resolveHumanTimeout(
            firstMiss.copy(
                phase = GamePhase.VOTACION,
                round = 2,
                votes = emptyMap(),
                dayEliminationTarget = "",
                winner = ""
            )
        )
        val expelledHuman = GameEngine.humanPlayer(secondMiss)

        assertFalse(expelledHuman.alive)
        assertEquals(2, expelledHuman.consecutiveVoteAfk)
        assertTrue(secondMiss.publicHistory.any { it.contains("expulsado por inactividad") })
    }

    @Test
    fun validVoteResetsVoteAfkWithoutChangingNightStreak() {
        val base = baseSession()
        val session = base.copy(
            phase = GamePhase.VOTACION,
            players = base.players.map {
                if (it.isHuman) {
                    it.copy(consecutiveNightAfk = 1, consecutiveVoteAfk = 1)
                } else {
                    it
                }
            }
        )

        val resolved = GameEngine.resolveHumanTargetAction(session, "Asesino")
        val human = GameEngine.humanPlayer(resolved)

        assertEquals(1, human.consecutiveNightAfk)
        assertEquals(0, human.consecutiveVoteAfk)
    }

    @Test
    fun deadOrMutedHumanDoesNotAccumulateAfk() {
        val base = baseSession()
        val deadNight = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            players = sessionWithHumanRole("medico").players.map {
                if (it.isHuman) it.copy(alive = false) else it
            }
        )
        val mutedVote = base.copy(
            phase = GamePhase.VOTACION,
            players = base.players.map {
                if (it.isHuman) it.copy(muted = true) else it
            }
        )

        val unchangedNight = GameEngine.resolveHumanTimeout(deadNight)
        val resolvedVote = GameEngine.resolveHumanTimeout(mutedVote)

        assertEquals(0, GameEngine.humanPlayer(unchangedNight).consecutiveNightAfk)
        assertEquals(0, GameEngine.humanPlayer(resolvedVote).consecutiveVoteAfk)
        assertTrue(GameEngine.humanPlayer(resolvedVote).alive)
    }

    @Test
    fun roleWithoutNightActionDoesNotAccumulateAfk() {
        val session = sessionWithHumanRole("aldeano").copy(phase = GamePhase.NOCHE_MEDICO)

        val resolved = GameEngine.resolveHumanTimeout(session)

        assertEquals(session, resolved)
        assertEquals(0, GameEngine.humanPlayer(resolved).consecutiveNightAfk)
    }

    @Test
    fun optionalContrapuntoTimeoutDoesNotAccumulateAfk() {
        val session = sessionWithHumanAdvancedRole("payador").copy(
            phase = GamePhase.CONTRAPUNTO,
            contrapuntoPlayers = listOf("Asesino", "Policia")
        )

        val resolved = GameEngine.resolveHumanTimeout(session)

        assertEquals(GamePhase.VOTACION, resolved.phase)
        assertEquals(0, GameEngine.humanPlayer(resolved).consecutiveNightAfk)
        assertEquals(0, GameEngine.humanPlayer(resolved).consecutiveVoteAfk)
    }

    @Test
    fun mayorTieTimeoutExpelsNobodyAndDoesNotCountAfk() {
        val session = sessionWithHumanAdvancedRole("alcalde").copy(
            phase = GamePhase.ALCALDE_DESEMPATE,
            alcaldeRevealed = true,
            alcaldeTieCandidates = listOf("Asesino", "Policia")
        )

        val resolved = GameEngine.resolveHumanTimeout(session)

        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertEquals("", resolved.dayEliminationTarget)
        assertTrue(resolved.alcaldeTieCandidates.isEmpty())
        assertEquals(0, GameEngine.humanPlayer(resolved).consecutiveVoteAfk)
    }

    @Test
    fun assignRolesCreatesOneCoreRoleAndVillagers() {
        val session = LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(1, roleCounts["asesino"])
        assertEquals(1, roleCounts["policia"])
        assertEquals(1, roleCounts["medico"])
        assertEquals(session.players.size - 3, roleCounts["aldeano"])
        assertTrue(session.players.any { it.isHuman && it.role != null })
    }

    @Test
    fun assignRolesAddsAdvancedRolesAsTableGrows() {
        var setup = LocalGameFactory.createSession()
        repeat(5) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }

        val session = LocalGameFactory.assignRoles(setup)
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(1, roleCounts["asesino"])
        assertEquals(1, roleCounts["policia"])
        assertEquals(1, roleCounts["medico"])
        assertEquals(1, roleCounts["mercenario"])
        assertEquals(1, roleCounts["alcalde"])
        assertEquals(1, roleCounts["payador"])
        assertEquals(1, roleCounts["desertor"])
        assertEquals(1, roleCounts["espia"])
        assertEquals(session.players.size - 8, roleCounts["aldeano"])
    }

    @Test
    fun debugRoleSelectionForcesHumanRoleWithoutDuplicatingIt() {
        var setup = LocalGameFactory.createSession()
        repeat(2) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        val session = LocalGameFactory.assignRoles(
            setup,
            forcedHumanRoleKey = "mercenario"
        )
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals("mercenario", GameEngine.humanPlayer(session).role?.key)
        assertEquals(1, roleCounts["mercenario"])
        assertEquals(1, roleCounts["asesino"])
        assertEquals(1, roleCounts["policia"])
        assertEquals(1, roleCounts["medico"])
        assertEquals(session.players.size, roleCounts.values.sum())
    }

    @Test
    fun debugAdvancedRolesRequireBalancedTableSizes() {
        assertEquals(5, LocalGameFactory.minimumPlayersForRole("asesino"))
        assertEquals(7, LocalGameFactory.minimumPlayersForRole("mercenario"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("alcalde"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("payador"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("bufon"))
        assertEquals(9, LocalGameFactory.minimumPlayersForRole("desertor"))
        assertEquals(10, LocalGameFactory.minimumPlayersForRole("espia"))
    }

    @Test
    fun desertorCanReconsiderAtTwoThirdsOfInitialPlayers() {
        assertEquals(6, GameRules.desertorSwitchThreshold(9))
        assertEquals(7, GameRules.desertorSwitchThreshold(10))
        assertEquals(8, GameRules.desertorSwitchThreshold(12))
        assertEquals(10, GameRules.desertorSwitchThreshold(15))
    }

    @Test
    fun desertorIsAssignedFromNinePlayersAndChoosesTeam() {
        var setup = LocalGameFactory.createSession()
        repeat(4) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        val assigned = LocalGameFactory.assignRoles(setup, forcedHumanRoleKey = "desertor")

        assertEquals("desertor", GameEngine.humanPlayer(assigned).role?.key)
        assertTrue(GameEngine.needsInitialDesertorChoice(assigned))

        val townDesertor = GameEngine.chooseDesertorTeam(assigned, GameRules.TOWN_WINNER)

        assertEquals(GameRules.TOWN_WINNER, townDesertor.desertorTeam)
        assertFalse(GameEngine.needsInitialDesertorChoice(townDesertor))
    }

    @Test
    fun desertorCanReconsiderOnlyOnceAtThreshold() {
        var setup = LocalGameFactory.createSession()
        repeat(4) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        val assigned = LocalGameFactory.assignRoles(setup, forcedHumanRoleKey = "desertor")
        val initial = GameEngine.chooseDesertorTeam(assigned, GameRules.TOWN_WINNER)
        val threshold = initial.copy(
            players = initial.players.mapIndexed { index, player ->
                if (index >= 6) player.copy(alive = false, muted = true) else player
            }
        )

        assertTrue(GameEngine.canDesertorReconsider(threshold))
        val switched = GameEngine.chooseDesertorTeam(threshold, GameRules.TRAITOR_WINNER)
        assertEquals(GameRules.TRAITOR_WINNER, switched.desertorTeam)
        assertTrue(switched.desertorChangedTeam)
        assertFalse(GameEngine.canDesertorReconsider(switched))
    }

    @Test
    fun payadorIsExclusiveToPampaMap() {
        var setup = LocalGameFactory.createSession()
        repeat(3) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }

        val pampa = LocalGameFactory.assignRoles(LocalGameFactory.selectMap(setup, "pampa"))
        val greece = LocalGameFactory.assignRoles(LocalGameFactory.selectMap(setup, "grecia"))
        val medieval = LocalGameFactory.assignRoles(LocalGameFactory.selectMap(setup, "medieval"))
        val forcedGreekPayador = LocalGameFactory.assignRoles(
            LocalGameFactory.selectMap(setup, "grecia"),
            forcedHumanRoleKey = "payador"
        )

        assertTrue(pampa.players.any { it.role?.key == "payador" })
        assertFalse(greece.players.any { it.role?.key == "payador" })
        assertFalse(medieval.players.any { it.role?.key == "payador" })
        assertFalse(forcedGreekPayador.players.any { it.role?.key == "payador" })
    }

    @Test
    fun bufonIsAssignedOnceOnMedievalMapFromEightPlayers() {
        var setup = LocalGameFactory.createSession()
        repeat(3) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }

        val medieval = LocalGameFactory.assignRoles(LocalGameFactory.selectMap(setup, "medieval"))
        val pampa = LocalGameFactory.assignRoles(LocalGameFactory.selectMap(setup, "pampa"))

        assertEquals(1, medieval.players.count { it.role?.key == RoleCatalog.BUFON })
        assertEquals(0, pampa.players.count { it.role?.key == RoleCatalog.BUFON })
    }

    @Test
    fun bufonWinsSpecialConditionWhenExpelledAndGameContinues() {
        val session = jesterSession(
            players = listOf(
                GamePlayer("Bufon", "B", role = role("bufon", "Bufon", "Neutral"), isHuman = true),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Pueblo1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
                GamePlayer("Pueblo2", "2", role = role("medico", "Medico", "Pueblo")),
                GamePlayer("Pueblo3", "3", role = role("policia", "Detective", "Pueblo"))
            ),
            target = "Bufon"
        )

        val resolved = GameEngine.resolveResult(session)

        assertFalse(GameEngine.playerByName(resolved, "Bufon")!!.alive)
        assertEquals("", resolved.winner)
        assertEquals(GamePhase.NOCHE_ASESINO, resolved.phase)
        assertEquals(2, resolved.round)
        assertEquals("bufon_expulsado", resolved.specialVictories.single().key)
        assertTrue(resolved.publicHistory.any { it.contains("era el Bufon") })
    }

    @Test
    fun bufonDoesNotWinWhenKilledAtNight() {
        val session = jesterSession(
            players = listOf(
                GamePlayer("Bufon", "B", role = role("bufon", "Bufon", "Neutral"), isHuman = true),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Pueblo1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
                GamePlayer("Pueblo2", "2", role = role("medico", "Medico", "Pueblo")),
                GamePlayer("Pueblo3", "3", role = role("policia", "Detective", "Pueblo"))
            ),
            target = ""
        ).copy(
            phase = GamePhase.AMANECER,
            nightKillTarget = "Bufon"
        )

        val resolved = GameEngine.resolveDawn(session)

        assertFalse(GameEngine.playerByName(resolved, "Bufon")!!.alive)
        assertTrue(resolved.specialVictories.isEmpty())
    }

    @Test
    fun bufonSpecialVictoryIsKeptWhenFactionAlsoWins() {
        val session = jesterSession(
            players = listOf(
                GamePlayer("Bufon", "B", role = role("bufon", "Bufon", "Neutral")),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Pueblo", "P", role = role("aldeano", "Aldeano", "Pueblo"), isHuman = true)
            ),
            target = "Bufon"
        )

        val resolved = GameEngine.resolveResult(session)

        assertEquals(GameRules.TRAITOR_WINNER, resolved.winner)
        assertEquals("Bufon", resolved.specialVictories.single().playerName)
    }

    @Test
    fun onlySpyInheritsKillWhenAssassinIsDead() {
        val assassinDead = advancedPlayers().map {
            if (it.role?.key == "asesino") it.copy(alive = false, muted = true) else it
        }
        val mercenaryHuman = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = assassinDead.map { it.copy(isHuman = it.role?.key == "mercenario") },
            phase = GamePhase.NOCHE_ASESINO
        )
        val spyHuman = mercenaryHuman.copy(
            players = assassinDead.map { it.copy(isHuman = it.role?.key == "espia") }
        )

        assertFalse(GameEngine.requiresHumanInput(mercenaryHuman))
        assertTrue(GameEngine.requiresHumanInput(spyHuman))
        assertEquals("", GameEngine.targetActionLabel(spyHuman, "Mercenario"))
        assertEquals("MATAR", GameEngine.targetActionLabel(spyHuman, "Policia"))
    }

    @Test
    fun mutedAssassinCanStillUseNightAbility() {
        val session = sessionWithHumanAdvancedRole("asesino").copy(
            phase = GamePhase.NOCHE_ASESINO,
            players = sessionWithHumanAdvancedRole("asesino").players.map {
                if (it.isHuman) it.copy(muted = true, lastSilencedRound = 3) else it
            }
        )

        val resolved = GameEngine.resolveAssassin(session, "Policia")

        assertTrue(GameEngine.requiresHumanInput(session))
        assertEquals("MATAR", GameEngine.targetActionLabel(session, "Policia"))
        assertEquals("Policia", resolved.nightKillTarget)
        assertEquals(GamePhase.NOCHE_MERCENARIO, resolved.phase)
    }

    @Test
    fun assassinCannotTargetExplicitTraitorTeammates() {
        val session = sessionWithHumanAdvancedRole("asesino").copy(phase = GamePhase.NOCHE_ASESINO)

        assertEquals("", GameEngine.targetActionLabel(session, "Mercenario"))
        assertEquals("", GameEngine.targetActionLabel(session, "Espia"))
        assertEquals("MATAR", GameEngine.targetActionLabel(session, "Policia"))
        assertEquals(session, GameEngine.resolveAssassin(session, "Mercenario"))
    }

    @Test
    fun botAssassinNeverTargetsExplicitTraitorTeammates() {
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = advancedPlayers(),
            phase = GamePhase.NOCHE_ASESINO
        )
        val assassin = session.players.first { it.role?.key == "asesino" }

        val target = GameEngine.playerByName(session, LocalBotAi.chooseAssassinTarget(session, assassin))

        assertNotNull(target)
        assertFalse(target!!.role?.key in GameRules.traitorRoleKeys)
    }

    @Test
    fun revealedAlcaldeCanResolveTwoPlayerTie() {
        val session = sessionWithHumanAdvancedRole("alcalde").copy(phase = GamePhase.DIA_DEBATE)
        val revealed = GameEngine.revealAlcalde(session)
        val tied = revealed.copy(
            phase = GamePhase.ALCALDE_DESEMPATE,
            alcaldeTieCandidates = listOf("Asesino", "Policia")
        )

        val resolved = GameEngine.chooseAlcaldeTie(tied, "Asesino")

        assertTrue(revealed.alcaldeRevealed)
        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertEquals("Asesino", resolved.dayEliminationTarget)
    }

    @Test
    fun revealedAlcaldeVoteCountsDouble() {
        val session = sessionWithHumanAdvancedRole("alcalde").copy(alcaldeRevealed = true)
        val leaders = GameEngine.weightedVoteLeaders(
            session,
            mapOf(
                "Humano" to "Asesino",
                "Policia" to "Medico",
                "Medico" to "Policia"
            )
        )

        assertEquals(listOf("Asesino"), leaders)
    }

    @Test
    fun deadPlayersAreNotAnnouncedAsTemporarilyMuted() {
        val session = baseSession().copy(
            phase = GamePhase.DIA_DEBATE,
            players = basePlayers().map {
                if (it.name == "Aldeano1") it.copy(alive = false, muted = true) else it
            }
        )

        val voting = GameEngine.resolveDayDebate(session)

        assertFalse(voting.publicAnnouncement.contains("Aldeano1"))
    }

    @Test
    fun payadorSelectsTwoPlayersAndRestrictsContrapuntoChat() {
        val base = sessionWithHumanAdvancedRole("payador").copy(phase = GamePhase.DIA_DEBATE)

        val first = GameEngine.chooseContrapuntoPlayer(base, "Asesino")
        val active = GameEngine.chooseContrapuntoPlayer(first, "Policia")

        assertEquals(listOf("Asesino"), first.contrapuntoPlayers)
        assertEquals(GamePhase.CONTRAPUNTO, active.phase)
        assertTrue(active.payadorUsed)
        assertTrue(GameEngine.canHumanChat(active))

        val outsiderSession = active.copy(
            players = active.players.map {
                it.copy(isHuman = it.name == "Medico")
            }
        )
        assertFalse(GameEngine.canHumanChat(outsiderSession))
    }

    @Test
    fun payadorSuspicionAddsOneVote() {
        val active = sessionWithHumanAdvancedRole("payador").copy(
            phase = GamePhase.CONTRAPUNTO,
            payadorUsed = true,
            contrapuntoPlayers = listOf("Asesino", "Policia")
        )

        val voting = GameEngine.resolveContrapunto(active, "Asesino")

        assertEquals(GamePhase.VOTACION, voting.phase)
        assertEquals("Asesino", voting.contrapuntoSuspicion)
    }

    @Test
    fun assassinKillsUnprotectedVictimAtDawn() {
        val session = baseSession().copy(
            phase = GamePhase.AMANECER,
            nightKillTarget = "Policia",
            protectedPlayer = "Medico"
        )

        val dawn = GameEngine.resolveDawn(session)
        val victim = GameEngine.playerByName(dawn, "Policia")

        assertFalse(victim!!.alive)
        assertFalse(victim.muted)
        assertEquals("Amanecer: murio Policia.", dawn.publicAnnouncement)
    }

    @Test
    fun medicProtectionPreventsDeathWithoutPublicSaveReveal() {
        val session = baseSession().copy(
            phase = GamePhase.AMANECER,
            nightKillTarget = "Policia",
            protectedPlayer = "Policia"
        )

        val dawn = GameEngine.resolveDawn(session)
        val protected = GameEngine.playerByName(dawn, "Policia")

        assertTrue(protected!!.alive)
        assertFalse(protected.muted)
        assertEquals("Amanecer: no murio nadie.", dawn.publicAnnouncement)
        assertFalse(dawn.publicAnnouncement.contains("medico", ignoreCase = true))
        assertFalse(dawn.publicAnnouncement.contains("salv", ignoreCase = true))
    }

    @Test
    fun humanPoliceGetsPrivateInvestigationHint() {
        val session = sessionWithHumanRole("policia").copy(phase = GamePhase.NOCHE_POLICIA)

        val resolved = GameEngine.resolvePolice(session, "Asesino")

        assertEquals(GamePhase.NOCHE_MEDICO, resolved.phase)
        assertEquals("Asesino", resolved.investigatedPlayer)
        assertEquals("sospechoso", resolved.investigatedResult)
        assertTrue(resolved.privateHint.contains("Asesino parece sospechoso"))
        assertFalse(resolved.publicAnnouncement.contains("Asesino"))
    }

    @Test
    fun spyLooksInnocentToPoliceInvestigation() {
        val session = sessionWithHumanAdvancedRole("policia").copy(phase = GamePhase.NOCHE_POLICIA)

        val resolved = GameEngine.resolvePolice(session, "Espia")

        assertEquals("Espia", resolved.investigatedPlayer)
        assertEquals("inocente", resolved.investigatedResult)
        assertTrue(resolved.privateHint.contains("Espia parece inocente"))
    }

    @Test
    fun mercenarySilencesTargetUntilNextRound() {
        val session = sessionWithHumanAdvancedRole("mercenario").copy(phase = GamePhase.NOCHE_MERCENARIO)

        val resolved = GameEngine.resolveMercenary(session, "Policia")
        val dawn = GameEngine.resolveDawn(
            resolved.copy(
                phase = GamePhase.AMANECER,
                nightKillTarget = "",
                protectedPlayer = ""
            )
        )
        val silenced = GameEngine.playerByName(dawn, "Policia")
        val nextRound = GameEngine.resolveResult(
            dawn.copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = ""
            )
        )

        assertEquals(GamePhase.NOCHE_POLICIA, resolved.phase)
        assertEquals("Policia", resolved.nightSilenceTarget)
        assertEquals(GameActionType.SILENCE, resolved.actionHistory.last().type)
        assertTrue(silenced!!.alive)
        assertTrue(silenced.muted)
        assertEquals(1, silenced.lastSilencedRound)
        assertTrue(dawn.publicAnnouncement.contains("Policia no puede hablar ni votar hoy."))
        assertFalse(GameEngine.playerByName(nextRound, "Policia")!!.muted)
        assertEquals(1, GameEngine.playerByName(nextRound, "Policia")!!.lastSilencedRound)
        assertEquals(GamePhase.NOCHE_ASESINO, nextRound.phase)
    }

    @Test
    fun silenceCooldownBlocksNextRoundAndAllowsFollowingRound() {
        val roundFour = sessionWithHumanAdvancedRole("mercenario").copy(
            phase = GamePhase.NOCHE_MERCENARIO,
            round = 4
        )
        val selected = GameEngine.resolveMercenary(roundFour, "Policia")
        val dawn = GameEngine.resolveDawn(
            selected.copy(
                phase = GamePhase.AMANECER,
                nightKillTarget = "",
                protectedPlayer = ""
            )
        )
        val roundFive = GameEngine.resolveResult(
            dawn.copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = ""
            )
        ).copy(phase = GamePhase.NOCHE_MERCENARIO)
        val roundSix = roundFive.copy(round = 6)

        assertEquals("", GameEngine.targetActionLabel(roundFive, "Policia"))
        assertEquals("SILENCIAR", GameEngine.targetActionLabel(roundSix, "Policia"))
    }

    @Test
    fun botMercenaryRespectsSilenceCooldown() {
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = advancedPlayers().map {
                if (it.name == "Policia") it.copy(lastSilencedRound = 4) else it
            },
            phase = GamePhase.NOCHE_MERCENARIO,
            round = 5
        )
        val mercenary = session.players.first { it.role?.key == "mercenario" }

        val target = LocalBotAi.chooseSilenceTarget(session, mercenary)

        assertNotEquals("Policia", target)
    }

    @Test
    fun temporarySilenceDoesNotCountAsDeathForWinCondition() {
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = role("aldeano", "Aldeano", "Pueblo"), isHuman = true),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo"))
            ),
            phase = GamePhase.AMANECER,
            nightSilenceTarget = "Policia"
        )

        val dawn = GameEngine.resolveDawn(session)

        assertEquals("", dawn.winner)
        assertTrue(GameEngine.playerByName(dawn, "Policia")!!.muted)
    }

    @Test
    fun tiedVotingDoesNotChooseAlphabeticalElimination() {
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = role("aldeano", "Aldeano", "Pueblo"), isHuman = true),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores"))
            ),
            phase = GamePhase.VOTACION
        )

        val resolved = GameEngine.resolveVoting(session, "Asesino")

        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertEquals("", resolved.dayEliminationTarget)
        assertTrue(resolved.publicAnnouncement.contains("No hubo mayoria clara"))
    }

    @Test
    fun phaseResolversIgnoreOutOfTurnCalls() {
        val session = baseSession().copy(
            phase = GamePhase.DIA_DEBATE,
            nightKillTarget = "Policia"
        )

        val resolved = GameEngine.resolveDawn(session)

        assertEquals(session, resolved)
    }

    @Test
    fun deadPlayersDoNotVoteOrReceiveActions() {
        val session = baseSession().copy(
            phase = GamePhase.VOTACION,
            players = baseSession().players.map {
                if (it.name == "Aldeano1") it.copy(alive = false, muted = true) else it
            }
        )

        val resolved = GameEngine.resolveVoting(session, "Asesino")

        assertFalse(resolved.votes.containsKey("Aldeano1"))
        assertFalse(resolved.votes.containsValue("Aldeano1"))
    }

    @Test
    fun mutedLivingHumanCannotVoteButCanBeVotedFor() {
        val mutedHuman = baseSession().copy(
            phase = GamePhase.VOTACION,
            players = basePlayers().map {
                if (it.isHuman) it.copy(muted = true, lastSilencedRound = 1) else it
            }
        )
        val mutedTarget = baseSession().copy(
            phase = GamePhase.VOTACION,
            players = basePlayers().map {
                if (it.name == "Policia") it.copy(muted = true, lastSilencedRound = 1) else it
            }
        )

        val resolved = GameEngine.resolveVoting(mutedHuman, "")

        assertFalse(resolved.votes.containsKey("Humano"))
        assertEquals("VOTAR", GameEngine.targetActionLabel(mutedTarget, "Policia"))
    }

    @Test
    fun botDebateAddsPublicChatWithoutRoleLeaks() {
        val session = baseSession().copy(
            phase = GamePhase.AMANECER,
            nightKillTarget = "",
            players = basePlayers().map { player ->
                player.copy(
                    name = when (player.name) {
                        "Asesino" -> "Mateo"
                        "Policia" -> "Julia"
                        "Medico" -> "Rocio"
                        else -> player.name
                    }
                )
            }
        )

        val resolved = GameEngine.resolveDawn(session)
        val chatText = resolved.chatHistory.joinToString(" ") { "${it.speaker}: ${it.message}" }

        assertTrue(resolved.chatHistory.size >= 2)
        assertFalse(chatText.contains("asesino", ignoreCase = true))
        assertFalse(chatText.contains("policia", ignoreCase = true))
        assertFalse(chatText.contains("medico", ignoreCase = true))
    }

    @Test
    fun botOnlyPhasesCanAutoAdvanceButHumanDecisionsStop() {
        val botPhase = baseSession().copy(phase = GamePhase.NOCHE_ASESINO)
        val humanPolicePhase = sessionWithHumanRole("policia").copy(phase = GamePhase.NOCHE_POLICIA)

        assertTrue(GameEngine.shouldAutoAdvance(botPhase))
        assertFalse(GameEngine.shouldAutoAdvance(humanPolicePhase))
    }

    @Test
    fun revealPhaseCanAutoAdvanceToFirstNight() {
        val session = baseSession().copy(phase = GamePhase.REPARTO)

        assertTrue(GameEngine.shouldAutoAdvance(session))
        assertEquals(GamePhase.NOCHE_ASESINO, GameEngine.startNight(session).phase)
    }

    @Test
    fun cardActionsAppearOnlyForValidTargets() {
        val assassin = sessionWithHumanRole("asesino").copy(phase = GamePhase.NOCHE_ASESINO)
        val mercenary = sessionWithHumanAdvancedRole("mercenario").copy(phase = GamePhase.NOCHE_MERCENARIO)
        val medic = sessionWithHumanRole("medico").copy(phase = GamePhase.NOCHE_MEDICO)
        val voting = baseSession().copy(phase = GamePhase.VOTACION)

        assertEquals("MATAR", GameEngine.targetActionLabel(assassin, "Policia"))
        assertEquals("", GameEngine.targetActionLabel(assassin, "Humano"))
        assertEquals("SILENCIAR", GameEngine.targetActionLabel(mercenary, "Policia"))
        assertEquals("", GameEngine.targetActionLabel(mercenary, "Humano"))
        assertEquals("SALVAR", GameEngine.targetActionLabel(medic, "Humano"))
        assertEquals("VOTAR", GameEngine.targetActionLabel(voting, "Asesino"))
        assertEquals("", GameEngine.targetActionLabel(voting, "Humano"))
    }

    @Test
    fun mutedTargetsDoNotExposeCardActions() {
        val session = sessionWithHumanRole("asesino").copy(
            phase = GamePhase.NOCHE_ASESINO,
            players = sessionWithHumanRole("asesino").players.map {
                if (it.name == "Policia") it.copy(alive = false, muted = true) else it
            }
        )

        assertEquals("", GameEngine.targetActionLabel(session, "Policia"))
        assertFalse(GameEngine.canActOnTarget(session, "Policia"))
    }

    @Test
    fun cardVoteActionResolvesVotingPhase() {
        val session = baseSession().copy(phase = GamePhase.VOTACION)

        val resolved = GameEngine.resolveHumanTargetAction(session, "Asesino")

        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertTrue(resolved.votes.containsKey("Humano"))
        assertEquals("Asesino", resolved.votes["Humano"])
    }

    @Test
    fun humanCanWriteChatWhileAlive() {
        val resolved = GameEngine.addHumanChatMessage(
            baseSession().copy(phase = GamePhase.DIA_DEBATE),
            "  Tengo una sospecha.  "
        )

        val humanMessage = resolved.chatHistory.first { it.speaker == "Humano" }
        val botReplies = resolved.chatHistory.filter { it.speaker != "Humano" }

        assertEquals("Tengo una sospecha.", humanMessage.message)
        assertFalse(humanMessage.isGod)
        assertTrue(botReplies.isNotEmpty())
    }

    @Test
    fun botReactionDoesNotEchoHiddenRoleClaims() {
        val session = publicNameSession().copy(phase = GamePhase.DIA_DEBATE)

        val resolved = GameEngine.addHumanChatMessage(session, "Creo que Ana es asesino.")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue(botText.isNotBlank())
        assertFalse(botText.contains("asesino", ignoreCase = true))
        assertFalse(botText.contains("policia", ignoreCase = true))
        assertFalse(botText.contains("medico", ignoreCase = true))
        assertFalse(botText.contains("traidores", ignoreCase = true))
    }

    @Test
    fun botConversationUsesLooseArgentineStyle() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = "Amanecer: no murio nadie."
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .joinToString(" ") { it.second }

        assertTrue(messages.isNotBlank())
        assertTrue(messages == messages.lowercase())
        assertTrue(
            listOf("che", "dale", "igual", "pq", "q ", "nose", "posta", "jaja", "jsjs", "kjjj")
                .any { messages.contains(it) }
        )
        assertTrue(messages.contains("?") || messages.contains("me hace ruido"))
    }

    @Test
    fun botFollowsUpWhenItsQuestionWasIgnored() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Ana", "dina pq cambiaste de tema?"),
                GameChatMessage("Beto", "dina entonces pq lo defendiste?"),
                GameChatMessage("Ciro", "dina vas a responder?"),
                GameChatMessage("Ema", "dina q paso ahi?"),
                GameChatMessage("Humano", "para mi hay que mirar a ema")
            )
        )

        val replies = LocalBotAi.reactionsToHumanMessage(session, "yo sigo dudando de ema")
            .joinToString(" ") { it.second }

        assertTrue(
            "Respuestas: $replies",
            replies.contains("no respondiste") ||
                replies.contains("responde eso") ||
                replies.contains("esquivando") ||
                replies.contains("sigo esperando")
        )
    }

    @Test
    fun botsCanReactWithDifferentEmotionalTones() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Humano", "ana estas re rara y no respondes"),
                GameChatMessage("Beto", "ana contesta de una vez"),
                GameChatMessage("Ciro", "jajaja se esta regalando sola")
            )
        )

        val replies = LocalBotAi.reactionsToHumanMessage(session, "ana por que no contestas?")
            .joinToString(" ") { it.second }

        assertTrue(replies.isNotBlank())
        assertTrue(
            "Respuestas: $replies",
            listOf("dale", "para", "jaja", "amigo", "no inventes", "decime", "q hice", "posta", "nose", "mmm")
                .any { replies.contains(it) }
        )
    }

    @Test
    fun botVotesFollowPublicSuspicionInsteadOfSecretInvestigation() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            investigatedPlayer = "Ana",
            investigatedResult = "sospechoso",
            chatHistory = listOf(
                GameChatMessage("Humano", "Dina cambio de tema y me parece raro."),
                GameChatMessage("Beto", "Dina tiene que responder eso.")
            )
        )

        val resolved = GameEngine.resolveVoting(session, "Dina")
        val botVotes = resolved.votes.filterKeys { it != "Humano" }
        val publicSuspicionVotes = botVotes.values.count { it == "Dina" }
        val secretInvestigationVotes = botVotes.values.count { it == "Ana" }

        assertEquals("Dina", resolved.votes["Humano"])
        assertTrue(publicSuspicionVotes > secretInvestigationVotes)
        assertTrue(publicSuspicionVotes >= 2)
    }

    @Test
    fun localRosterUsesConfiguredHumanNameAndFriendBotNames() {
        var session = LocalGameFactory.createSession(humanName = "  Ignacio  ")
        repeat(LocalGameFactory.MAX_PLAYERS - LocalGameFactory.MIN_PLAYERS) {
            session = LocalGameFactory.addMockPlayer(session)
        }

        assertEquals("Ignacio", session.players.first().name)
        assertTrue(session.players.first().isHuman)
        assertEquals(
            listOf(
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
            ),
            session.players.drop(1).map { it.name }
        )
    }

    @Test
    fun onlineLobbyFactoryPreparesHostSearchAndQuickRooms() {
        val created = LocalGameFactory.createOnlineLobby(
            humanName = "Ignacio",
            playerCount = 1,
            humanIsHost = true
        )
        val searched = LocalGameFactory.createOnlineLobby(
            humanName = "Ignacio",
            playerCount = 8,
            humanIsHost = false
        )
        val quick = LocalGameFactory.createOnlineLobby(
            humanName = "Ignacio",
            playerCount = LocalGameFactory.MAX_PLAYERS,
            humanIsHost = false
        )

        assertEquals(1, created.players.size)
        assertTrue(created.players.first().isHuman)
        assertEquals(8, searched.players.size)
        assertFalse(searched.players.first().isHuman)
        assertTrue(searched.players.last().isHuman)
        assertEquals(LocalGameFactory.MAX_PLAYERS, quick.players.size)
        assertTrue(quick.players.last().isHuman)
        assertEquals("ONLINE-MOCK", quick.code)
    }

    @Test
    fun botVoteKeepsItsLatestPublicAccusation() {
        val session = publicNameSession().copy(
            code = "PUBLIC-VOTE",
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "para mi Dina esta re rara, voto a Dina"),
                GameChatMessage("Humano", "yo sigo dudando de Ana")
            )
        )
        val beto = GameEngine.playerByName(session, "Beto")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, beto))
    }

    @Test
    fun traitorBotsUsuallyAvoidVotingForLivingAllies() {
        val base = GameSession(
            code = "ALLY-VOTE",
            mapKey = "pampa",
            mapName = "Pampa",
            players = advancedPlayers(),
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Humano", "Mercenario esta raro y no responde"),
                GameChatMessage("Policia", "Mercenario tiene que explicar")
            )
        )
        val assassin = GameEngine.playerByName(base, "Asesino")!!
        val allyVotes = (1..30).count { round ->
            val target = LocalBotAi.chooseVoteTarget(base.copy(round = round), assassin)
            GameEngine.playerByName(base, target)?.role?.key in GameRules.traitorRoleKeys
        }

        assertTrue("Votos aliados: $allyVotes", allyVotes <= 6)
    }

    @Test
    fun botsReactToDawnAndPreviousExpulsionUsingOnlyPublicEvents() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = "Amanecer: murio Dina.",
            publicHistory = listOf(
                "La mesa expulso a Ema.",
                "Amanecer: murio Dina."
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 4)
            .joinToString(" ") { it.second }

        assertTrue(messages.contains("dina", ignoreCase = true))
        assertTrue(messages.contains("ema", ignoreCase = true))
        assertFalse(messages.contains("asesino", ignoreCase = true))
        assertFalse(messages.contains("policia", ignoreCase = true))
        assertFalse(messages.contains("medico", ignoreCase = true))
    }

    @Test
    fun mutedHumanCanReadButCannotWriteChat() {
        val session = baseSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(GameChatMessage("Mateo", "Mensaje visible.")),
            players = basePlayers().map {
                if (it.isHuman) it.copy(muted = true, lastSilencedRound = 1) else it
            }
        )

        val resolved = GameEngine.addHumanChatMessage(session, "No deberia salir")

        assertFalse(GameEngine.canHumanChat(resolved))
        assertEquals(session.chatHistory, resolved.chatHistory)
    }

    @Test
    fun townHumanCannotWriteAtNight() {
        val session = baseSession().copy(phase = GamePhase.NOCHE_ASESINO)

        val resolved = GameEngine.addHumanChatMessage(session, "Alguien despierto?")

        assertFalse(GameEngine.canHumanChat(session))
        assertEquals(session.chatHistory, resolved.chatHistory)
    }

    @Test
    fun traitorHumanCannotWriteInSharedLocalNightChat() {
        val session = sessionWithHumanRole("asesino").copy(phase = GamePhase.NOCHE_ASESINO)

        val resolved = GameEngine.addHumanChatMessage(session, "Avanzo en silencio.")

        assertFalse(GameEngine.canHumanChat(session))
        assertEquals(session.chatHistory, resolved.chatHistory)
    }

    @Test
    fun townWinsWhenNoAssassinsRemain() {
        val session = baseSession().copy(
            phase = GamePhase.RESULTADO,
            dayEliminationTarget = "Asesino"
        )

        val resolved = GameEngine.resolveResult(session)

        assertEquals("Pueblo", resolved.winner)
        assertFalse(GameEngine.playerByName(resolved, "Asesino")!!.alive)
        assertFalse(GameEngine.playerByName(resolved, "Asesino")!!.muted)
    }

    @Test
    fun traitorsWinWhenTheyEqualOrOutnumberTown() {
        val session = baseSession().copy(
            phase = GamePhase.AMANECER,
            nightKillTarget = "Policia",
            protectedPlayer = "",
            players = baseSession().players.map {
                if (it.name == "Medico" || it.name == "Aldeano1" || it.name == "Aldeano2") {
                    it.copy(alive = false, muted = true)
                } else {
                    it
                }
            }
        )

        val resolved = GameEngine.resolveDawn(session)

        assertEquals("Traidores", resolved.winner)
    }

    @Test
    fun gameContinuesAcrossRoundsUntilAWinConditionIsMet() {
        val roundTwo = GameEngine.resolveResult(
            baseSession().copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = "",
                round = 1
            )
        )
        val secondDawn = GameEngine.resolveDawn(
            roundTwo.copy(
                phase = GamePhase.AMANECER,
                nightKillTarget = "Aldeano1",
                protectedPlayer = ""
            )
        )
        val roundThree = GameEngine.resolveResult(
            secondDawn.copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = ""
            )
        )

        assertEquals(2, roundTwo.round)
        assertEquals(GamePhase.NOCHE_ASESINO, roundTwo.phase)
        assertEquals("", roundTwo.winner)
        assertEquals("", secondDawn.winner)
        assertEquals(3, roundThree.round)
        assertEquals(GamePhase.NOCHE_ASESINO, roundThree.phase)
        assertEquals("", roundThree.winner)
    }

    @Test
    fun townWinsWhenNoAssassinOrSpyRemainsEvenIfMercenaryLives() {
        val players = advancedPlayers()
        val assassinOnlyDead = players.map {
            if (it.role?.key == "asesino") it.copy(alive = false, muted = true) else it
        }
        val allKillersDead = players.map {
            if (it.role?.key in GameRules.killerRoleKeys) {
                it.copy(alive = false, muted = true)
            } else {
                it
            }
        }

        assertEquals("", GameRules.winnerFor(assassinOnlyDead))
        assertTrue(allKillersDead.any { it.alive && it.role?.key == "mercenario" })
        assertEquals("Pueblo", GameRules.winnerFor(allKillersDead))
    }

    @Test
    fun deadHumanDoesNotBlockBotVotingOrAutoAdvance() {
        val session = baseSession().copy(
            phase = GamePhase.VOTACION,
            players = basePlayers().map {
                if (it.isHuman) it.copy(alive = false, muted = false) else it
            }
        )

        val resolved = GameEngine.resolveVoting(session, "")

        assertFalse(GameEngine.requiresHumanInput(session))
        assertTrue(GameEngine.shouldAutoAdvance(session))
        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertFalse(resolved.votes.containsKey("Humano"))
        assertTrue(resolved.votes.isNotEmpty())
    }

    @Test
    fun traitorRolesWinTogetherAtExactParity() {
        val parityPlayers = advancedPlayers().map {
            if (
                it.name == "Aldeano1" ||
                it.name == "Aldeano2" ||
                it.name == "Payador" ||
                it.name == "Alcalde"
            ) {
                it.copy(alive = false, muted = true)
            } else {
                it
            }
        }

        assertEquals("Traidores", GameRules.winnerFor(parityPlayers))
    }

    private fun sessionWithHumanRole(roleKey: String): GameSession {
        val players = basePlayers()
        val targetRole = players.first { it.role!!.key == roleKey }.role!!
        val originalHumanRole = players.first { it.isHuman }.role!!
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players.map { player ->
                when {
                    player.isHuman -> player.copy(role = targetRole)
                    player.role?.key == roleKey -> player.copy(role = originalHumanRole)
                    else -> player
                }
            }
        )
    }

    private fun sessionWithHumanAdvancedRole(roleKey: String): GameSession {
        val players = advancedPlayers()
        val targetRole = players.first { it.role!!.key == roleKey }.role!!
        val originalHumanRole = players.first { it.isHuman }.role!!
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players.map { player ->
                when {
                    player.isHuman -> player.copy(role = targetRole)
                    player.role?.key == roleKey -> player.copy(role = originalHumanRole)
                    else -> player
                }
            }
        )
    }

    private fun baseSession(): GameSession {
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = basePlayers(),
            privateHint = "Aldeano - Pueblo."
        )
    }

    private fun jesterSession(players: List<GamePlayer>, target: String): GameSession {
        return GameSession(
            code = "JESTER",
            mapKey = "medieval",
            mapName = "Medieval",
            players = players,
            phase = GamePhase.RESULTADO,
            round = 1,
            dayEliminationTarget = target,
            initialPlayerCount = players.size
        )
    }

    private fun publicNameSession(): GameSession {
        val names = mapOf(
            "Asesino" to ("Ana" to "A"),
            "Policia" to ("Beto" to "B"),
            "Medico" to ("Ciro" to "C"),
            "Aldeano1" to ("Dina" to "D"),
            "Aldeano2" to ("Ema" to "E")
        )
        return baseSession().copy(
            players = basePlayers().map { player ->
                val rename = names[player.name]
                if (rename == null) {
                    player
                } else {
                    player.copy(name = rename.first, initial = rename.second)
                }
            }
        )
    }

    private fun advancedPlayers(): List<GamePlayer> {
        return listOf(
            GamePlayer("Humano", "H", role = role("aldeano", "Aldeano", "Pueblo"), isHuman = true),
            GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
            GamePlayer("Mercenario", "R", role = role("mercenario", "Mercenario", "Traidores")),
            GamePlayer("Espia", "E", role = role("espia", "Espia", "Traidores")),
            GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo")),
            GamePlayer("Medico", "M", role = role("medico", "Medico", "Pueblo")),
            GamePlayer("Alcalde", "L", role = role("alcalde", "Alcalde", "Pueblo")),
            GamePlayer("Payador", "Y", role = role("payador", "Payador", "Pueblo")),
            GamePlayer("Aldeano1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
            GamePlayer("Aldeano2", "2", role = role("aldeano", "Aldeano", "Pueblo"))
        )
    }

    private fun basePlayers(): List<GamePlayer> {
        return listOf(
            GamePlayer("Humano", "H", role = role("aldeano", "Aldeano", "Pueblo"), isHuman = true),
            GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
            GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo")),
            GamePlayer("Medico", "M", role = role("medico", "Medico", "Pueblo")),
            GamePlayer("Aldeano1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
            GamePlayer("Aldeano2", "2", role = role("aldeano", "Aldeano", "Pueblo"))
        )
    }

    private fun role(key: String, name: String, team: String): GameRole {
        return GameRole(key, name, team, "rol_${key}_gaucho")
    }
}
