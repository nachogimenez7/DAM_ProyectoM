package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun incompletePrivateRolesNeverDeclareAFalseTownVictory() {
        val session = GameSession(
            code = "ONLINE",
            mapKey = "medieval",
            mapName = "Medieval",
            players = listOf(
                GamePlayer(
                    "Detective",
                    "D",
                    role = role(RoleCatalog.POLICIA, "Detective", GameRules.TOWN_WINNER),
                    isHuman = true
                ),
                GamePlayer("Rol oculto", "R", role = null)
            )
        )

        assertEquals("", GameRules.winnerFor(session))
    }

    @Test
    fun gameRoleNamesMatchEachMapPresentation() {
        assertEquals("Aldeana", RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.MEDIEVAL).name)
        assertEquals("Espía", RoleCatalog.gameRole(RoleCatalog.ESPIA, RoleMap.MEDIEVAL).name)
        assertEquals("Desertora", RoleCatalog.gameRole(RoleCatalog.DESERTOR, RoleMap.MEDIEVAL).name)
        assertEquals("Médico", RoleCatalog.gameRole(RoleCatalog.MEDICO, RoleMap.MEDIEVAL).name)

        assertEquals("Aldeano", RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.PAMPA).name)
        assertEquals("Médica", RoleCatalog.gameRole(RoleCatalog.MEDICO, RoleMap.PAMPA).name)
        assertEquals("Alcaldesa", RoleCatalog.gameRole(RoleCatalog.ALCALDE, RoleMap.PAMPA).name)
        assertEquals("Espía", RoleCatalog.gameRole(RoleCatalog.ESPIA, RoleMap.PAMPA).name)
        assertEquals("Comisario", RoleCatalog.gameRole(RoleCatalog.POLICIA, RoleMap.PAMPA).name)

        assertEquals("Aldeano", RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.GREECE).name)
        assertEquals("Asesina", RoleCatalog.gameRole(RoleCatalog.ASESINO, RoleMap.GREECE).name)
        assertEquals("Espía", RoleCatalog.gameRole(RoleCatalog.ESPIA, RoleMap.GREECE).name)
        assertEquals("Oráculo", RoleCatalog.gameRole(RoleCatalog.ORACULO, RoleMap.GREECE).name)
        assertEquals("Médico", RoleCatalog.gameRole(RoleCatalog.MEDICO, RoleMap.GREECE).name)

        assertEquals("Detective", RoleCatalog.gameRole(RoleCatalog.POLICIA, RoleMap.GREECE).name)
        assertEquals("Detective", RoleCatalog.gameRole(RoleCatalog.POLICIA, RoleMap.MEDIEVAL).name)
    }

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
        assertEquals("4 / 40 / 120 / 20", GameTimingConfig().summary())
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
        assertEquals(GameTimingConfig(6, 90, 180, 60), GameTimingPreset.SLOW.config)
        assertEquals(GameTimingConfig(4, 40, 120, 20), GameTimingPreset.NORMAL.config)
        assertEquals(GameTimingConfig(2, 20, 60, 15), GameTimingPreset.FAST.config)
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
    fun firstNightTimeoutSkipsActionAndWarnsWithoutBlocking() {
        val firstNight = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            afkExpulsionEnabled = true
        )

        val firstMiss = GameEngine.resolveHumanTimeout(firstNight)
        val human = GameEngine.humanPlayer(firstMiss)

        assertEquals(GamePhase.AMANECER, firstMiss.phase)
        assertTrue(human.alive)
        assertEquals(1, human.consecutiveNightAfk)
        assertTrue(firstMiss.privateHint.contains("próxima noche"))
        assertFalse(firstMiss.publicHistory.any { it.contains("expulsado por inactividad") })
    }

    @Test
    fun localAfkDoesNotExpelHumanAndGivesNeutralHint() {
        // En local (afkExpulsionEnabled = false, el default) la ausencia es abstencion, nunca
        // expulsion, y el aviso no amenaza con AFK.
        val session = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            round = 2,
            players = sessionWithHumanRole("medico").players.map {
                if (it.isHuman) it.copy(consecutiveNightAfk = 1) else it
            }
        )

        val secondMiss = GameEngine.resolveHumanTimeout(session)
        val human = GameEngine.humanPlayer(secondMiss)

        assertTrue(human.alive)
        assertFalse(secondMiss.publicHistory.any { it.contains("expulsado por inactividad") })
        assertFalse(secondMiss.privateHint.contains("AFK"))
    }

    @Test
    fun assassinNightTimeoutKillsNobodyAndFinishesUnifiedNight() {
        val firstNight = sessionWithHumanRole("asesino").copy(phase = GamePhase.NOCHE_ASESINO)
        val resolved = GameEngine.resolveHumanTimeout(firstNight)

        assertEquals(GamePhase.AMANECER, resolved.phase)
        assertEquals("", resolved.nightKillTarget)
        assertTrue(resolved.players.all { it.alive })
        assertEquals("", resolved.winner)
        assertEquals(1, GameEngine.humanPlayer(resolved).consecutiveNightAfk)
    }

    @Test
    fun secondNightTimeoutExpelsForAfkAfterSkippingAction() {
        val secondNight = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            round = 2,
            afkExpulsionEnabled = true,
            players = sessionWithHumanRole("medico").players.map {
                if (it.isHuman) it.copy(consecutiveNightAfk = 1) else it
            }
        )

        val secondMiss = GameEngine.resolveHumanTimeout(secondNight)
        val human = GameEngine.humanPlayer(secondMiss)

        assertEquals(GamePhase.AMANECER, secondMiss.phase)
        assertFalse(human.alive)
        assertEquals(2, human.consecutiveNightAfk)
        assertTrue(secondMiss.publicHistory.any { it.contains("expulsado por inactividad") })
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
        val firstVote = baseSession().copy(
            phase = GamePhase.VOTACION,
            afkExpulsionEnabled = true
        )

        val firstMiss = GameEngine.resolveHumanTimeout(firstVote)
        val warnedHuman = GameEngine.humanPlayer(firstMiss)

        assertEquals(GamePhase.RECUENTO_VOTOS, firstMiss.phase)
        assertTrue(warnedHuman.alive)
        assertEquals(1, warnedHuman.consecutiveVoteAfk)
        assertTrue(firstMiss.privateHint.contains("próxima votación"))

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
    fun recommendedRoleCompositionUsesTwoTraitorsFromEightPlayers() {
        var setup = LocalGameFactory.createSession()
        repeat(3) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }

        val session = LocalGameFactory.assignRoles(setup)
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(8, session.players.size)
        assertEquals(1, roleCounts[RoleCatalog.ASESINO])
        assertEquals(1, roleCounts[RoleCatalog.MERCENARIO])
        assertEquals(
            2,
            session.players.count { it.role?.key in GameRules.traitorRoleKeys }
        )
    }

    @Test
    fun recommendedTraitorDistributionScalesAtAgreedThresholds() {
        val expected = mapOf(
            5 to 1,
            6 to 1,
            7 to 2,
            8 to 2,
            9 to 2,
            10 to 3,
            11 to 3,
            12 to 3,
            13 to 4,
            14 to 4,
            15 to 4
        )

        expected.forEach { (playerCount, expectedTraitors) ->
            val composition = LocalGameFactory.roleCompositionPreset(
                playerCount,
                "pampa",
                RoleCompositionPreset.RECOMMENDED
            )
            val traitorCount = GameRules.traitorRoleKeys.sumOf {
                composition.counts[it] ?: 0
            }

            assertEquals("Cantidad de traidores para $playerCount jugadores", expectedTraitors, traitorCount)
            assertEquals(
                "Cantidad de asesinos para $playerCount jugadores",
                if (playerCount >= 13) 2 else 1,
                composition.counts[RoleCatalog.ASESINO]
            )
        }
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
        assertEquals(0, roleCounts["desertor"] ?: 0)
        assertEquals(1, roleCounts["espia"])
        assertEquals(session.players.size - 7, roleCounts["aldeano"])
    }

    @Test
    fun customRoleCompositionAssignsRequestedCounts() {
        var setup = LocalGameFactory.createSession()
        repeat(5) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        setup = setup.copy(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ASESINO to 2,
                    RoleCatalog.POLICIA to 1,
                    RoleCatalog.MEDICO to 1,
                    RoleCatalog.MERCENARIO to 1,
                    RoleCatalog.ALDEANO to 5
                ),
                customized = true
            )
        )

        val session = LocalGameFactory.assignRoles(setup)
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(2, roleCounts[RoleCatalog.ASESINO])
        assertEquals(1, roleCounts[RoleCatalog.POLICIA])
        assertEquals(1, roleCounts[RoleCatalog.MEDICO])
        assertEquals(1, roleCounts[RoleCatalog.MERCENARIO])
        assertEquals(5, roleCounts[RoleCatalog.ALDEANO])
        assertEquals(session.players.size, roleCounts.values.sum())
    }

    @Test
    fun customRoleCompositionAllowsPlayingWithoutDoctorOrDetective() {
        var setup = LocalGameFactory.createSession()
        repeat(3) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        setup = setup.copy(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ASESINO to 1,
                    RoleCatalog.POLICIA to 0,
                    RoleCatalog.MEDICO to 0,
                    RoleCatalog.ALDEANO to 7
                ),
                customized = true
            )
        )

        val assigned = LocalGameFactory.assignRoles(setup)
        val roleCounts = assigned.players.mapNotNull { it.role?.key }.groupingBy { it }.eachCount()

        assertEquals(1, roleCounts[RoleCatalog.ASESINO])
        assertEquals(0, roleCounts[RoleCatalog.POLICIA] ?: 0)
        assertEquals(0, roleCounts[RoleCatalog.MEDICO] ?: 0)
        assertEquals(7, roleCounts[RoleCatalog.ALDEANO])
    }

    @Test
    fun customRoleCompositionCapsTooManyAssassins() {
        var setup = LocalGameFactory.createSession()
        repeat(5) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        setup = setup.copy(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ASESINO to 6,
                    RoleCatalog.POLICIA to 1,
                    RoleCatalog.MEDICO to 1
                ),
                customized = true
            )
        )

        val session = LocalGameFactory.assignRoles(setup)
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(2, roleCounts[RoleCatalog.ASESINO])
        assertEquals(1, roleCounts[RoleCatalog.POLICIA])
        assertEquals(1, roleCounts[RoleCatalog.MEDICO])
        assertEquals(6, roleCounts[RoleCatalog.ALDEANO])
        assertEquals(session.players.size, roleCounts.values.sum())
    }

    @Test
    fun customRoleCompositionDropsRolesFromOtherMaps() {
        var setup = LocalGameFactory.selectMap(LocalGameFactory.createSession(), "grecia")
        repeat(5) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        setup = setup.copy(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ASESINO to 1,
                    RoleCatalog.POLICIA to 1,
                    RoleCatalog.MEDICO to 1,
                    RoleCatalog.PAYADOR to 1,
                    RoleCatalog.ORACULO to 1
                ),
                customized = true
            )
        )

        val session = LocalGameFactory.assignRoles(setup)
        val roleCounts = session.players
            .mapNotNull { it.role?.key }
            .groupingBy { it }
            .eachCount()

        assertEquals(0, roleCounts[RoleCatalog.PAYADOR] ?: 0)
        assertEquals(1, roleCounts[RoleCatalog.ORACULO])
        assertEquals(session.players.size, roleCounts.values.sum())
    }

    @Test
    fun roleCompositionPresetsCreateDifferentDecks() {
        val recommended = LocalGameFactory.roleCompositionPreset(
            10,
            "pampa",
            RoleCompositionPreset.RECOMMENDED
        )
        val classic = LocalGameFactory.roleCompositionPreset(
            10,
            "pampa",
            RoleCompositionPreset.CLASSIC
        )
        val chaotic = LocalGameFactory.roleCompositionPreset(
            10,
            "pampa",
            RoleCompositionPreset.CHAOTIC
        )

        assertEquals(1, recommended.counts[RoleCatalog.ASESINO])
        assertEquals(1, recommended.counts[RoleCatalog.PAYADOR])
        assertEquals(0, classic.counts[RoleCatalog.PAYADOR] ?: 0)
        assertEquals(7, classic.counts[RoleCatalog.ALDEANO])
        assertEquals(2, chaotic.counts[RoleCatalog.ASESINO])
        assertEquals(1, chaotic.counts[RoleCatalog.ESPIA])
        assertEquals(10, chaotic.counts.values.sum())
    }

    @Test
    fun onlineSafeRoleCompositionAddsMercenaryFromSevenPlayers() {
        val fivePlayers = LocalGameFactory.onlineSafeRoleComposition(5)
        val sixPlayers = LocalGameFactory.onlineSafeRoleComposition(6)
        val sevenPlayers = LocalGameFactory.onlineSafeRoleComposition(7)
        val eightPlayers = LocalGameFactory.onlineSafeRoleComposition(8)

        assertEquals(1, fivePlayers.counts[RoleCatalog.ASESINO])
        assertEquals(1, fivePlayers.counts[RoleCatalog.MEDICO])
        assertEquals(1, fivePlayers.counts[RoleCatalog.POLICIA])
        assertEquals(0, fivePlayers.counts[RoleCatalog.MERCENARIO] ?: 0)
        assertEquals(2, fivePlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(5, fivePlayers.counts.values.sum())

        assertEquals(0, sixPlayers.counts[RoleCatalog.MERCENARIO] ?: 0)
        assertEquals(3, sixPlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(6, sixPlayers.counts.values.sum())

        assertEquals(1, sevenPlayers.counts[RoleCatalog.MERCENARIO])
        assertEquals(3, sevenPlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(0, sevenPlayers.counts[RoleCatalog.ALCALDE] ?: 0)
        assertEquals(7, sevenPlayers.counts.values.sum())

        assertEquals(1, eightPlayers.counts[RoleCatalog.MERCENARIO])
        assertEquals(1, eightPlayers.counts[RoleCatalog.ALCALDE])
        assertEquals(3, eightPlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(8, eightPlayers.counts.values.sum())
    }

    @Test
    fun onlineSafeRoleCompositionKeepsDesertorOutUntilFourteenAndAddsSpyAtTen() {
        val eightPlayers = LocalGameFactory.onlineSafeRoleComposition(8)
        val ninePlayers = LocalGameFactory.onlineSafeRoleComposition(9)
        val tenPlayers = LocalGameFactory.onlineSafeRoleComposition(10)
        val fourteenPlayers = LocalGameFactory.onlineSafeRoleComposition(14)

        assertEquals(0, eightPlayers.counts[RoleCatalog.DESERTOR] ?: 0)

        assertEquals(1, ninePlayers.counts[RoleCatalog.ALCALDE])
        assertEquals(0, ninePlayers.counts[RoleCatalog.DESERTOR] ?: 0)
        assertEquals(0, ninePlayers.counts[RoleCatalog.ESPIA] ?: 0)
        assertEquals(4, ninePlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(9, ninePlayers.counts.values.sum())

        assertEquals(1, tenPlayers.counts[RoleCatalog.ESPIA])
        assertEquals(0, tenPlayers.counts[RoleCatalog.DESERTOR] ?: 0)
        assertEquals(4, tenPlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(10, tenPlayers.counts.values.sum())

        assertEquals(1, fourteenPlayers.counts[RoleCatalog.DESERTOR])
        assertEquals(7, fourteenPlayers.counts[RoleCatalog.ALDEANO])
        assertEquals(14, fourteenPlayers.counts.values.sum())
    }

    @Test
    fun onlineSafeRoleCompositionAtTwelveKeepsMapRolesOut() {
        val composition = LocalGameFactory.onlineSafeRoleComposition(12)

        assertEquals(1, composition.counts[RoleCatalog.ASESINO])
        assertEquals(1, composition.counts[RoleCatalog.MEDICO])
        assertEquals(1, composition.counts[RoleCatalog.POLICIA])
        assertEquals(1, composition.counts[RoleCatalog.MERCENARIO])
        assertEquals(1, composition.counts[RoleCatalog.ALCALDE])
        assertEquals(0, composition.counts[RoleCatalog.DESERTOR] ?: 0)
        assertEquals(1, composition.counts[RoleCatalog.ESPIA])
        assertEquals(6, composition.counts[RoleCatalog.ALDEANO])
        listOf(
            RoleCatalog.PAYADOR,
            RoleCatalog.ORACULO,
            RoleCatalog.BUFON
        ).forEach { specialRole ->
            assertEquals(0, composition.counts[specialRole] ?: 0)
        }
        assertEquals(12, composition.counts.values.sum())
    }

    @Test
    fun onlineSafeRoleCompositionAddsTheExclusiveRoleForTheSelectedMap() {
        val pampa = LocalGameFactory.onlineSafeRoleComposition(8, "pampa")
        val greece = LocalGameFactory.onlineSafeRoleComposition(8, "grecia")
        val medieval = LocalGameFactory.onlineSafeRoleComposition(8, "medieval")

        assertEquals(1, pampa.counts[RoleCatalog.PAYADOR])
        assertEquals(0, pampa.counts[RoleCatalog.ORACULO] ?: 0)
        assertEquals(0, pampa.counts[RoleCatalog.BUFON] ?: 0)

        assertEquals(1, greece.counts[RoleCatalog.ORACULO])
        assertEquals(0, greece.counts[RoleCatalog.PAYADOR] ?: 0)
        assertEquals(0, greece.counts[RoleCatalog.BUFON] ?: 0)

        assertEquals(1, medieval.counts[RoleCatalog.BUFON])
        assertEquals(0, medieval.counts[RoleCatalog.PAYADOR] ?: 0)
        assertEquals(0, medieval.counts[RoleCatalog.ORACULO] ?: 0)

        assertEquals(8, pampa.counts.values.sum())
        assertEquals(8, greece.counts.values.sum())
        assertEquals(8, medieval.counts.values.sum())
    }

    @Test
    fun onlineSafeRoleCompositionForThreePlayersUsesOnlyTheThreeCoreRoles() {
        val composition = LocalGameFactory.onlineSafeRoleComposition(3)

        assertEquals(1, composition.counts[RoleCatalog.ASESINO])
        assertEquals(1, composition.counts[RoleCatalog.MEDICO])
        assertEquals(1, composition.counts[RoleCatalog.POLICIA])
        assertEquals(0, composition.counts[RoleCatalog.ALDEANO])
        assertEquals(3, composition.counts.values.sum())
    }

    @Test
    fun onlineTestMatchWithThreePlayersAssignsTheThreeCoreRoles() {
        val session = GameSession(
            code = "TEST-3",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Ana", "A", isHuman = true),
                GamePlayer("Beto", "B"),
                GamePlayer("Ciro", "C")
            ),
            onlineTestMode = true,
            roleComposition = LocalGameFactory.onlineSafeRoleComposition(3)
        )

        val assigned = LocalGameFactory.assignRoles(session)

        assertEquals(
            setOf(RoleCatalog.ASESINO, RoleCatalog.MEDICO, RoleCatalog.POLICIA),
            assigned.players.mapNotNull { it.role?.key }.toSet()
        )
    }

    @Test
    fun onlineRecordedNightMissingActionsAdvanceAsNoAction() {
        val session = baseSession().copy(phase = GamePhase.NOCHE_ASESINO)

        val resolved = GameEngine.resolveAssassinWithRecordedVotes(session, emptyMap())

        assertNotEquals(GamePhase.NOCHE_ASESINO, resolved.phase)
        assertEquals("", resolved.nightKillTarget)
        assertTrue(resolved.assassinVotes.isEmpty())
    }

    @Test
    fun remotePlayersAreNeverResolvedAsBots() {
        val session = GameSession(
            code = "ONLINE-HUMANS",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.NOCHE_MERCENARIO,
            players = listOf(
                GamePlayer(
                    "Local",
                    "L",
                    role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"),
                    isHuman = true,
                    control = PlayerControl.LOCAL
                ),
                GamePlayer(
                    "Remoto",
                    "R",
                    role = role(RoleCatalog.MERCENARIO, "Mercenario", "Traidores"),
                    control = PlayerControl.REMOTE
                ),
                GamePlayer(
                    "Objetivo",
                    "O",
                    role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"),
                    control = PlayerControl.REMOTE
                ),
                GamePlayer(
                    "Asesino remoto",
                    "A",
                    role = role(RoleCatalog.ASESINO, "Asesino", "Traidores"),
                    control = PlayerControl.REMOTE
                )
            )
        )

        val resolved = GameEngine.resolveMercenary(session, "")
        val startedNight = GameEngine.startNight(session.copy(phase = GamePhase.REPARTO))

        assertEquals(GamePhase.NOCHE_POLICIA, resolved.phase)
        assertEquals("", resolved.nightSilenceTarget)
        assertTrue(resolved.actionHistory.isEmpty())
        assertEquals(null, startedNight.traitorPlan)
    }

    @Test
    fun onlineRecordedVotingMissingVotesAreAbstentions() {
        val session = baseSession().copy(phase = GamePhase.VOTACION)

        val resolved = GameEngine.resolveVotingWithRecordedVotes(
            session,
            mapOf("Humano" to "Asesino")
        )

        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertEquals(mapOf("Humano" to "Asesino"), resolved.votes)
    }

    @Test
    fun onlineVotingWithNobodyPlayingExpelsNobodyAndContinues() {
        val voting = baseSession().copy(
            phase = GamePhase.VOTACION,
            round = 2
        )

        val recount = GameEngine.resolveVotingWithRecordedVotes(voting, emptyMap())
        val result = GameEngine.continueAfterVoteRecount(recount)
        val nextRound = GameEngine.resolveResult(result)

        assertTrue(recount.votes.isEmpty())
        assertEquals("", recount.dayEliminationTarget)
        assertEquals(GamePhase.RESULTADO, result.phase)
        assertTrue(result.publicAnnouncement.contains("Nadie será expulsado"))
        assertTrue(nextRound.players.all { it.alive })
        assertEquals(3, nextRound.round)
    }

    @Test
    fun onlineRecordedVotingDoesNotAutoRevealRemoteMayor() {
        val session = sessionWithHumanAdvancedRole(RoleCatalog.ALDEANO).copy(
            phase = GamePhase.VOTACION,
            round = 2,
            alcaldeRevealed = false
        )

        val resolved = GameEngine.resolveVotingWithRecordedVotes(session, emptyMap())

        assertFalse(resolved.alcaldeRevealed)
    }

    @Test
    fun practiceRoleSelectionForcesHumanRoleWithoutDuplicatingIt() {
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
    fun practiceAdvancedRolesRequireBalancedTableSizes() {
        assertEquals(5, LocalGameFactory.minimumPlayersForRole("asesino"))
        assertEquals(7, LocalGameFactory.minimumPlayersForRole("mercenario"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("alcalde"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("payador"))
        assertEquals(8, LocalGameFactory.minimumPlayersForRole("bufon"))
        assertEquals(14, LocalGameFactory.minimumPlayersForRole("desertor"))
        assertEquals(10, LocalGameFactory.minimumPlayersForRole("espia"))
    }

    @Test
    fun practiceRoleReplacesVillagerWhenMissingFromCustomComposition() {
        var setup = LocalGameFactory.selectMap(LocalGameFactory.createSession(), "medieval")
        repeat(3) {
            setup = LocalGameFactory.addMockPlayer(setup)
        }
        setup = setup.copy(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ALDEANO to 5,
                    RoleCatalog.POLICIA to 1,
                    RoleCatalog.MEDICO to 1,
                    RoleCatalog.ASESINO to 1,
                    RoleCatalog.BUFON to 0
                ),
                customized = true
            )
        )

        val assigned = LocalGameFactory.assignRoles(
            setup,
            forcedHumanRoleKey = RoleCatalog.BUFON
        )

        assertEquals(RoleCatalog.BUFON, GameEngine.humanPlayer(assigned).role?.key)
        assertEquals(1, assigned.players.count { it.role?.key == RoleCatalog.BUFON })
        assertEquals(4, assigned.players.count { it.role?.key == RoleCatalog.ALDEANO })
        assertEquals(8, assigned.players.count { it.role != null })
    }

    @Test
    fun desertorCanReconsiderAtTwoThirdsOfInitialPlayers() {
        assertEquals(6, GameRules.desertorSwitchThreshold(9))
        assertEquals(7, GameRules.desertorSwitchThreshold(10))
        assertEquals(8, GameRules.desertorSwitchThreshold(12))
        assertEquals(10, GameRules.desertorSwitchThreshold(15))
    }

    @Test
    fun desertorIsAssignedFromFourteenPlayersAndChoosesTeam() {
        var setup = LocalGameFactory.createSession()
        repeat(9) {
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
    fun desertorAlignedWithTraitorsIsNotAssassinTarget() {
        val assassin = GamePlayer(
            "Asesino",
            "A",
            role = role(RoleCatalog.ASESINO, "Asesino", GameRules.TRAITOR_WINNER)
        )
        val desertor = GamePlayer(
            "Desertor",
            "D",
            role = role(RoleCatalog.DESERTOR, "Desertor", "Neutral")
        )
        val session = GameSession(
            code = "DESERTOR-ALLY",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                assassin,
                desertor,
                GamePlayer("Aldeano", "P", role = role(RoleCatalog.ALDEANO, "Aldeano", GameRules.TOWN_WINNER))
            ),
            phase = GamePhase.NOCHE_ASESINO,
            desertorTeam = GameRules.TRAITOR_WINNER
        )

        assertTrue(GameEngine.isDesertorAlignedWithTraitors(session, desertor))
        assertFalse(GameEngine.isValidKillTarget(session, "Desertor", assassin))
    }

    @Test
    fun desertorAlignedWithTownCanBeAssassinTarget() {
        val assassin = GamePlayer(
            "Asesino",
            "A",
            role = role(RoleCatalog.ASESINO, "Asesino", GameRules.TRAITOR_WINNER)
        )
        val desertor = GamePlayer(
            "Desertor",
            "D",
            role = role(RoleCatalog.DESERTOR, "Desertor", "Neutral")
        )
        val session = GameSession(
            code = "DESERTOR-TOWN",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                assassin,
                desertor,
                GamePlayer("Aldeano", "P", role = role(RoleCatalog.ALDEANO, "Aldeano", GameRules.TOWN_WINNER))
            ),
            phase = GamePhase.NOCHE_ASESINO,
            desertorTeam = GameRules.TOWN_WINNER,
            desertorChangedTeam = true
        )

        assertFalse(GameEngine.isDesertorAlignedWithTraitors(session, desertor))
        assertTrue(GameEngine.isValidKillTarget(session, "Desertor", assassin))
    }

    @Test
    fun eachMapAssignsOnlyItsExclusiveRole() {
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
        assertFalse(pampa.players.any { it.role?.key == RoleCatalog.ORACULO })
        assertFalse(pampa.players.any { it.role?.key == RoleCatalog.BUFON })
        assertFalse(greece.players.any { it.role?.key == "payador" })
        assertTrue(greece.players.any { it.role?.key == RoleCatalog.ORACULO })
        assertFalse(greece.players.any { it.role?.key == RoleCatalog.BUFON })
        assertFalse(medieval.players.any { it.role?.key == "payador" })
        assertFalse(medieval.players.any { it.role?.key == RoleCatalog.ORACULO })
        assertTrue(medieval.players.any { it.role?.key == RoleCatalog.BUFON })
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
    fun oracleCanSavePowerForAnotherNight() {
        val session = oracleSession()

        val skipped = GameEngine.skipOraclePower(session)

        assertEquals(GamePhase.AMANECER, skipped.phase)
        assertFalse(skipped.oracleUsed)
        assertEquals("", skipped.oracleInvitedPlayer)
        assertTrue(skipped.privateHint.contains("Guardaste tu poder"))
    }

    @Test
    fun oracleWithoutDeadPlayersHasNoDecisionAndKeepsPowerAutomatically() {
        val firstNight = oracleSession().copy(
            players = oracleSession().players.map { it.copy(alive = true) }
        )

        assertTrue(GameEngine.oracleCandidates(firstNight).isEmpty())
        assertFalse(GameEngine.requiresHumanInput(firstNight))
        assertFalse(firstNight.oracleUsed)
    }

    @Test
    fun oracleCannotUseOrSavePowerDuringFirstNightEvenWithADeadCandidate() {
        val firstNight = oracleSession().copy(round = 1)

        assertFalse(GameEngine.requiresHumanInput(firstNight))
        assertEquals(firstNight, GameEngine.skipOraclePower(firstNight))
        val advanced = GameEngine.resolveOracle(firstNight, "Fallecido")
        assertEquals(GamePhase.AMANECER, advanced.phase)
        assertFalse(advanced.oracleUsed)
        assertEquals("", advanced.oracleInvitedPlayer)
    }

    @Test
    fun oracleInvitesOneDeadPlayerAnonymouslyForDiscussionOnly() {
        val session = oracleSession()

        val invoked = GameEngine.resolveOracle(session, "Fallecido")
        val dawn = GameEngine.resolveDawn(invoked)
        val invited = dawn.players.first { it.name == "Fallecido" }

        assertEquals(GamePhase.AMANECER, invoked.phase)
        assertTrue(invoked.oracleUsed)
        assertEquals("Fallecido", invoked.oracleInvitedPlayer)
        assertEquals(GameActionType.INVITE_DEAD, invoked.actionHistory.last().type)
        assertEquals(GamePhase.DIA_DEBATE, dawn.phase)
        assertTrue(dawn.oracleRevealPending)
        assertTrue(dawn.publicAnnouncement.contains("Fallecido"))
        assertFalse(dawn.publicAnnouncement.contains("Humano"))
        assertFalse(invited.alive)
        assertTrue(GameEngine.canSpeak(dawn, invited))
        assertFalse(GameEngine.canVote(invited))

        val voting = GameEngine.resolveDayDebate(dawn)
        assertEquals(GamePhase.VOTACION, voting.phase)
        assertEquals("", voting.oracleInvitedPlayer)
        assertFalse(GameEngine.canSpeak(voting, invited))
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
        assertTrue(resolved.publicHistory.any { it.contains("era el Bufón") })
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
                when {
                    it.isHuman -> it.copy(muted = true, lastSilencedRound = 3)
                    it.role?.key == "espia" -> it.copy(alive = false, muted = true)
                    else -> it
                }
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
    fun quickTestModeDoesNotChangeAssassinTarget() {
        val normalSession = baseSession()
        val quickSession = normalSession.copy(quickTestMode = true)
        val normalAssassin = normalSession.players.first { it.role?.key == "asesino" }
        val quickAssassin = quickSession.players.first { it.role?.key == "asesino" }

        val normalTarget = LocalBotAi.chooseAssassinTarget(normalSession, normalAssassin)
        val quickTarget = LocalBotAi.chooseAssassinTarget(quickSession, quickAssassin)

        assertEquals(normalTarget, quickTarget)
    }

    @Test
    fun debugBotsNeverKillHumanAlsoWorksInQuickTestMode() {
        val session = baseSession().copy(
            quickTestMode = true,
            debugBotsNeverKillHuman = true
        )
        val assassin = session.players.first { it.role?.key == "asesino" }

        val target = LocalBotAi.chooseAssassinTarget(session, assassin)

        assertNotEquals("Humano", target)
        assertTrue(target.isNotBlank())
    }

    @Test
    fun debugBotsNeverVoteHumanRejectsBotVoteCommandAgainstHuman() {
        val session = baseSession().copy(
            phase = GamePhase.VOTACION,
            debugBotsObeyVoteCommands = true,
            debugBotsNeverVoteHuman = true,
            chatHistory = listOf(
                GameChatMessage("Humano", "voten a Humano")
            )
        )
        val voter = session.players.first { it.name == "Policia" }

        val target = LocalBotAi.chooseVoteTarget(session, voter)

        assertNotEquals("Humano", target)
        assertTrue(target.isNotBlank())
    }

    @Test
    fun contrapuntoRejectsBotChatFromNonParticipants() {
        val session = sessionWithHumanAdvancedRole("payador").copy(
            phase = GamePhase.CONTRAPUNTO,
            contrapuntoPlayers = listOf("Policia", "Medico")
        )

        val rejected = GameEngine.addBotChatMessage(session, "Aldeano1", "yo tambien opino")
        val accepted = GameEngine.addBotChatMessage(rejected, "Policia", "yo participe")

        assertEquals(session.chatHistory, rejected.chatHistory)
        assertEquals("Policia", accepted.chatHistory.last().speaker)
    }

    @Test
    fun multipleAssassinsVoteForOneNightVictim() {
        val session = GameSession(
            code = "MULTI-KILL",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.NOCHE_ASESINO,
            players = listOf(
                GamePlayer("Asesino1", "A", role = role("asesino", "Asesino", "Traidores"), isHuman = true),
                GamePlayer("Asesino2", "B", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Asesino3", "C", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Espia", "E", role = role("espia", "Espia", "Traidores")),
                GamePlayer("Mercenario", "R", role = role("mercenario", "Mercenario", "Traidores")),
                GamePlayer("Pueblo1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
                GamePlayer("Pueblo2", "2", role = role("policia", "Comisario", "Pueblo")),
                GamePlayer("Pueblo3", "3", role = role("medico", "Medico", "Pueblo"))
            )
        )

        val resolved = GameEngine.resolveAssassin(session, "Pueblo1")

        assertEquals(GamePhase.NOCHE_MERCENARIO, resolved.phase)
        assertEquals(4, resolved.assassinVotes.size)
        assertEquals("Pueblo1", resolved.assassinVotes["Asesino1"])
        assertEquals(setOf("Pueblo1"), resolved.assassinVotes.values.toSet())
        assertTrue(resolved.assassinVotes.containsKey("Espia"))
        assertTrue(resolved.nightKillTarget in resolved.assassinVotes.values)
        assertEquals(
            4,
            resolved.actionHistory.count { it.type == GameActionType.KILL && it.round == 1 }
        )
    }

    @Test
    fun spyVotesWithAssassinsWhenAssassinsAreAlive() {
        val session = GameSession(
            code = "SPY-WATCH",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.NOCHE_ASESINO,
            players = listOf(
                GamePlayer("Espia", "E", role = role("espia", "Espia", "Traidores"), isHuman = true),
                GamePlayer("Asesino1", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Asesino2", "B", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Pueblo1", "1", role = role("aldeano", "Aldeano", "Pueblo")),
                GamePlayer("Pueblo2", "2", role = role("policia", "Comisario", "Pueblo")),
                GamePlayer("Pueblo3", "3", role = role("medico", "Medico", "Pueblo"))
            )
        )

        val resolved = GameEngine.resolveAssassin(session, "Pueblo1")

        assertEquals(3, resolved.assassinVotes.size)
        assertEquals("Pueblo1", resolved.assassinVotes["Espia"])
        assertTrue(resolved.privateHint.contains("Tu voto fue registrado."))
        assertTrue(resolved.privateHint.contains("Victima elegida:"))
        assertTrue(GameEngine.requiresHumanInput(session))
    }

    @Test
    fun botTraitorsFollowHumanNightTargetOnNormalAndHard() {
        BotDifficulty.entries.forEach { difficulty ->
            val session = GameSession(
                code = "COORDINATED-$difficulty",
                mapKey = "pampa",
                mapName = "Pampa",
                phase = GamePhase.NOCHE_ASESINO,
                botDifficulty = difficulty,
                players = listOf(
                    GamePlayer(
                        "Humano",
                        "H",
                        role = role("asesino", "Asesino", "Traidores"),
                        isHuman = true
                    ),
                    GamePlayer("AsesinoBot", "A", role = role("asesino", "Asesino", "Traidores")),
                    GamePlayer("EspiaBot", "E", role = role("espia", "Espia", "Traidores")),
                    GamePlayer("Objetivo", "O", role = role("aldeano", "Aldeano", "Pueblo")),
                    GamePlayer("Pueblo2", "2", role = role("medico", "Medico", "Pueblo")),
                    GamePlayer("Pueblo3", "3", role = role("policia", "Comisario", "Pueblo"))
                )
            )

            val resolved = GameEngine.resolveAssassin(session, "Objetivo")

            assertEquals("Objetivo", resolved.nightKillTarget)
            assertEquals(setOf("Objetivo"), resolved.assassinVotes.values.toSet())
            assertEquals(3, resolved.assassinVotes.size)
        }
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
        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertEquals(3, resolved.voteRound)
        assertEquals("Asesino", resolved.dayEliminationTarget)
    }

    @Test
    fun firstTieOpensRestrictedSecondVoteAndKeepsChatAvailable() {
        val recount = baseSession().copy(
            phase = GamePhase.RECUENTO_VOTOS,
            voteRound = 1,
            tieVoteCandidates = listOf("Asesino", "Policia"),
            votes = mapOf(
                "Humano" to "Asesino",
                "Asesino" to "Policia"
            )
        )

        val tieVote = GameEngine.continueAfterVoteRecount(recount)

        assertEquals(GamePhase.DESEMPATE_VOTACION, tieVote.phase)
        assertTrue(GameEngine.canHumanChat(tieVote))
        assertTrue(GameEngine.canActOnTarget(tieVote, "Asesino"))
        assertFalse(GameEngine.canActOnTarget(tieVote, "Medico"))
        assertEquals("VOTAR", GameEngine.targetActionLabel(tieVote, "Asesino"))
    }

    @Test
    fun secondVoteOnlyAcceptsTiedCandidatesAndCandidatesCannotVoteThemselves() {
        val tieVote = baseSession().copy(
            phase = GamePhase.DESEMPATE_VOTACION,
            tieVoteCandidates = listOf("Asesino", "Policia")
        )

        val invalid = GameEngine.resolveTieVoting(tieVote, "Medico")
        val resolved = GameEngine.resolveTieVoting(tieVote, "Asesino")

        assertEquals(tieVote, invalid)
        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertEquals(2, resolved.voteRound)
        assertTrue(resolved.votes.values.all { it in tieVote.tieVoteCandidates })
        assertTrue(resolved.votes.all { (voter, target) -> voter != target })
    }

    @Test
    fun repeatedTieWithoutMayorEndsTheDayWithoutExpulsion() {
        val recount = baseSession().copy(
            phase = GamePhase.RECUENTO_VOTOS,
            players = basePlayers().filterNot { it.role?.key == "alcalde" },
            voteRound = 2,
            tieVoteCandidates = listOf("Asesino", "Policia"),
            dayEliminationTarget = ""
        )

        val result = GameEngine.continueAfterVoteRecount(recount)

        assertEquals(GamePhase.RESULTADO, result.phase)
        assertEquals("", result.dayEliminationTarget)
        assertTrue(result.publicAnnouncement.contains("Nadie será expulsado"))
    }

    @Test
    fun hiddenHumanMayorMayRevealAfterRepeatedTieAndChooseTheExpelledPlayer() {
        val recount = sessionWithHumanAdvancedRole("alcalde").copy(
            phase = GamePhase.RECUENTO_VOTOS,
            alcaldeRevealed = false,
            voteRound = 2,
            tieVoteCandidates = listOf("Asesino", "Policia"),
            dayEliminationTarget = ""
        )

        val mayorDecision = GameEngine.continueAfterVoteRecount(recount)
        val revealed = GameEngine.revealAlcalde(mayorDecision)
        val result = GameEngine.chooseAlcaldeTie(revealed, "Asesino")

        assertEquals(GamePhase.ALCALDE_DESEMPATE, mayorDecision.phase)
        assertFalse(mayorDecision.alcaldeRevealed)
        assertTrue(revealed.alcaldeRevealed)
        assertTrue(GameEngine.canActOnTarget(revealed, "Asesino"))
        assertEquals(GamePhase.RECUENTO_VOTOS, result.phase)
        assertEquals(3, result.voteRound)
        assertEquals("Asesino", result.dayEliminationTarget)
    }

    @Test
    fun humanMayorCanRevealDuringTheSecondVote() {
        val tieVote = sessionWithHumanAdvancedRole("alcalde").copy(
            phase = GamePhase.DESEMPATE_VOTACION,
            alcaldeRevealed = false,
            tieVoteCandidates = listOf("Asesino", "Policia")
        )

        val revealed = GameEngine.revealAlcalde(tieVote)

        assertTrue(revealed.alcaldeRevealed)
        assertEquals(GamePhase.DESEMPATE_VOTACION, revealed.phase)
        assertTrue(revealed.publicAnnouncement.contains("Alcalde"))
    }

    @Test
    fun mayorCandidateSurvivesARepeatedTwoPlayerTieThroughCorruption() {
        val recount = sessionWithHumanAdvancedRole("alcalde").copy(
            phase = GamePhase.RECUENTO_VOTOS,
            alcaldeRevealed = false,
            voteRound = 2,
            tieVoteCandidates = listOf("Humano", "Asesino"),
            dayEliminationTarget = ""
        )

        val corruption = GameEngine.continueAfterVoteRecount(recount)

        assertEquals(GamePhase.RECUENTO_VOTOS, corruption.phase)
        assertTrue(corruption.alcaldeRevealed)
        assertTrue(corruption.alcaldeCorruption)
        assertEquals(4, corruption.voteRound)
        assertEquals("Asesino", corruption.dayEliminationTarget)
        assertTrue(corruption.publicAnnouncement.contains("Corrupción"))
    }

    @Test
    fun voteVisibilityDefaultsToShowingIndividualVoters() {
        assertTrue(baseSession().showIndividualVotes)
    }

    @Test
    fun debugTieOptionForcesTheInitialVoteToEndTied() {
        val session = baseSession().copy(
            phase = GamePhase.VOTACION,
            debugForceVoteTies = true
        )

        val recount = GameEngine.resolveVoting(session, "Asesino")

        assertEquals(GamePhase.RECUENTO_VOTOS, recount.phase)
        assertTrue(recount.tieVoteCandidates.size >= 2)
        assertEquals("", recount.dayEliminationTarget)
        assertTrue(recount.votes.all { (voter, target) -> voter != target })
        assertTrue(GameEngine.weightedVoteLeaders(recount, recount.votes).size >= 2)
    }

    @Test
    fun debugTieOptionAlsoForcesTheSecondVoteToEndTied() {
        val initialRecount = GameEngine.resolveVoting(
            baseSession().copy(
                phase = GamePhase.VOTACION,
                debugForceVoteTies = true
            ),
            "Asesino"
        )
        val tieVote = GameEngine.continueAfterVoteRecount(initialRecount)
        val selectedTarget = tieVote.tieVoteCandidates.first {
            it != GameEngine.humanPlayer(tieVote).name
        }

        val finalRecount = GameEngine.resolveTieVoting(tieVote, selectedTarget)

        assertEquals(GamePhase.RECUENTO_VOTOS, finalRecount.phase)
        assertEquals(2, finalRecount.voteRound)
        assertTrue(finalRecount.tieVoteCandidates.size >= 2)
        assertEquals("", finalRecount.dayEliminationTarget)
        assertTrue(finalRecount.votes.all { (voter, target) -> voter != target })
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
        assertFalse(GameEngine.canHumanChat(active))
        assertFalse(active.publicAnnouncement.contains("Payador"))

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
        assertTrue(voting.publicAnnouncement.contains("Asesino quedó señalado"))
        assertFalse(voting.publicAnnouncement.contains("Payador"))
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
        assertFalse(dawn.nightHadNoVictim)
        assertEquals(
            "Amanecer: murió Policia. Lo encontraron al alba; nadie vio nada, como siempre.",
            dawn.publicAnnouncement
        )
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
        assertTrue(dawn.nightHadNoVictim)
        assertTrue(GameplayTableUi.wasNoDeathAtDawn(dawn))
        assertEquals(
            "Amanecer: no murió nadie. El pueblo despierta entero, pero nadie durmió tranquilo.",
            dawn.publicAnnouncement
        )
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
        assertTrue(resolved.privateHint.contains("Investigaste a Asesino"))
        assertTrue(resolved.privateHint.contains("parece sospechoso"))
        assertFalse(resolved.publicAnnouncement.contains("Asesino"))
        assertTrue(resolved.tableMemory.privateInvestigationReads.isEmpty())
    }

    @Test
    fun botPoliceStoresItsResultInItsOwnPrivateMemory() {
        val session = publicNameSession().copy(phase = GamePhase.NOCHE_POLICIA)

        val resolved = GameEngine.resolvePolice(session, "")
        val read = resolved.tableMemory.privateInvestigationReads.single()

        assertEquals("Beto", read.source)
        assertEquals(resolved.investigatedPlayer, read.target)
        assertEquals(resolved.investigatedResult, read.result)
    }

    @Test
    fun spyLooksInnocentToPoliceInvestigation() {
        val session = sessionWithHumanAdvancedRole("policia").copy(phase = GamePhase.NOCHE_POLICIA)

        val resolved = GameEngine.resolvePolice(session, "Espia")

        assertEquals("Espia", resolved.investigatedPlayer)
        assertEquals("inocente", resolved.investigatedResult)
        assertTrue(resolved.privateHint.contains("Investigaste a Espia"))
        assertTrue(resolved.privateHint.contains("parece inocente"))
    }

    @Test
    fun mercenaryLooksSuspiciousToPoliceInvestigation() {
        val session = sessionWithHumanAdvancedRole("policia")
            .copy(phase = GamePhase.NOCHE_POLICIA)

        val resolved = GameEngine.resolvePolice(session, "Mercenario")

        assertEquals("Mercenario", resolved.investigatedPlayer)
        assertEquals("sospechoso", resolved.investigatedResult)
        assertTrue(resolved.privateHint.contains("parece sospechoso"))
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

        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertEquals("", resolved.dayEliminationTarget)
        assertEquals(1, resolved.voteRound)
        assertEquals(setOf("Humano", "Asesino"), resolved.tieVoteCandidates.toSet())

        val tieVote = GameEngine.continueAfterVoteRecount(resolved)

        assertEquals(GamePhase.DESEMPATE_VOTACION, tieVote.phase)
        assertTrue(tieVote.publicAnnouncement.contains("Si el empate se repite"))
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
    fun dawnLeavesBotConversationToTheAsyncChatDirector() {
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
        val generatedBotMessages = resolved.chatHistory.filterNot { it.isGod }

        assertEquals(GamePhase.DIA_DEBATE, resolved.phase)
        assertEquals(session.chatHistory.size + 1, resolved.chatHistory.size)
        assertEquals(resolved.publicAnnouncement, resolved.chatHistory.last().message)
        assertTrue(generatedBotMessages.isEmpty())
    }

    @Test
    fun quickTestModeAutoAdvancesBotOnlyPhasesButHumanDecisionsStop() {
        val botPhase = baseSession().copy(phase = GamePhase.NOCHE_ASESINO)
        val quickBotPhase = botPhase.copy(quickTestMode = true)
        val humanPolicePhase = sessionWithHumanRole("policia")
            .copy(phase = GamePhase.NOCHE_POLICIA, quickTestMode = true)

        assertFalse(GameEngine.shouldAutoAdvance(botPhase))
        assertTrue(GameEngine.shouldAutoAdvance(quickBotPhase))
        assertFalse(GameEngine.shouldAutoAdvance(humanPolicePhase))
    }

    @Test
    fun revealPhaseStartsVisibleFirstNight() {
        val session = baseSession().copy(phase = GamePhase.REPARTO, quickTestMode = true)
        val night = GameEngine.startNight(session)

        assertTrue(GameEngine.shouldAutoAdvance(session))
        assertEquals(GamePhase.NOCHE_ASESINO, night.phase)
        assertEquals("", night.nightKillTarget)
    }

    @Test
    fun startNightShowsFirstNightBeforeHumanAction() {
        val session = sessionWithHumanRole("medico").copy(phase = GamePhase.REPARTO)

        val night = GameEngine.startNight(session)

        assertEquals(GamePhase.NOCHE_ASESINO, night.phase)
        assertEquals("", night.nightKillTarget)
        assertFalse(GameEngine.requiresHumanInput(night))
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

        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertTrue(resolved.votes.containsKey("Humano"))
        assertEquals("Asesino", resolved.votes["Humano"])
        assertEquals(1, resolved.voteRound)
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
    fun humanChatCanDeferBotReactionsForStaggeredUi() {
        val withHumanOnly = GameEngine.addHumanChatMessage(
            baseSession().copy(phase = GamePhase.DIA_DEBATE),
            "  Tengo una sospecha.  ",
            includeBotReactions = false
        )

        assertEquals(1, withHumanOnly.chatHistory.size)
        assertEquals("Humano", withHumanOnly.chatHistory.single().speaker)
        assertEquals("Tengo una sospecha.", withHumanOnly.chatHistory.single().message)

        val withBot = GameEngine.addBotChatMessage(
            withHumanOnly,
            "Asesino",
            "eso suena medio raro"
        )

        assertEquals(2, withBot.chatHistory.size)
        assertEquals("Asesino", withBot.chatHistory.last().speaker)
    }

    @Test
    fun jesterDebugVoteCommandIsSentWithoutGeneratingBotReactions() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            debugBotsObeyVoteCommands = true,
            players = publicNameSession().players.map { player ->
                if (player.isHuman) {
                    player.copy(role = RoleCatalog.gameRole(RoleCatalog.BUFON, RoleMap.MEDIEVAL))
                } else {
                    player
                }
            }
        )

        val resolved = GameEngine.addHumanChatMessage(session, "Votenme, quiero probar el Bufon")
        val addedMessages = resolved.chatHistory.drop(session.chatHistory.size)

        assertEquals(1, addedMessages.size)
        assertEquals("Humano", addedMessages.single().speaker)
        assertEquals("Votenme, quiero probar el Bufon", addedMessages.single().message)
    }

    @Test
    fun jesterCanSendRegularChatAndReceiveBotReaction() {
        val base = publicNameSession()
        val session = base.copy(
            phase = GamePhase.DIA_DEBATE,
            players = base.players.map { player ->
                if (player.isHuman) {
                    player.copy(role = RoleCatalog.gameRole(RoleCatalog.BUFON, RoleMap.MEDIEVAL))
                } else {
                    player
                }
            }
        )

        val resolved = GameEngine.addHumanChatMessage(session, "No confio en nadie")
        val addedMessages = resolved.chatHistory.drop(session.chatHistory.size)

        assertEquals("Humano", addedMessages.first().speaker)
        assertTrue(addedMessages.any { it.speaker != "Humano" })
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
    fun botCanChallengeHumanRoleClaimWithOwnPublicClaim() {
        val session = publicNameSession().copy(phase = GamePhase.DIA_DEBATE)

        val resolved = GameEngine.addHumanChatMessage(session, "soy medico")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { "${it.speaker}: ${it.message}" }

        assertTrue("Respuestas: $botText", botText.contains("Ciro:"))
        assertTrue("Respuestas: $botText", botText.contains("yo soy medico"))
        assertTrue(
            "Respuestas: $botText",
            botText.contains("doble claim") || botText.contains("dos q dicen lo mismo")
        )
    }

    @Test
    fun botAsksForDetailsBeforeTurningRoleClaimIntoSuspicion() {
        val session = publicNameSession().copy(phase = GamePhase.DIA_DEBATE)

        val resolved = GameEngine.addHumanChatMessage(session, "soy detective")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue("Respuestas: $botText", botText.contains("a quien investigaste"))
        assertFalse("Respuestas: $botText", botText.contains("esta re raro"))
    }

    @Test
    fun botUnderstandsHumanDetectiveClaimWithInnocentResult() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            players = publicNameSession().players.map { player ->
                when (player.name) {
                    "Humano" -> player.copy(role = role("policia", "Comisario", "Pueblo"))
                    "Beto" -> player.copy(role = role("aldeano", "Aldeano", "Pueblo"))
                    else -> player
                }
            }
        )

        val resolved = GameEngine.addHumanChatMessage(session, "soy detective, pregunte por Dina y es inocente")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue("Respuestas: $botText", botText.contains("dina", ignoreCase = true))
        assertTrue(
            "Respuestas: $botText",
            botText.contains("limpio") ||
                botText.contains("no lo votaria") ||
                botText.contains("queda mas abajo")
        )
        assertFalse("Respuestas: $botText", botText.contains("a quien investigaste"))
    }

    @Test
    fun botKeepsContextWhenHumanClarifiesAnswerWasForIt() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            players = publicNameSession().players.map { player ->
                when (player.name) {
                    "Humano" -> player.copy(role = role("policia", "Comisario", "Pueblo"))
                    "Beto" -> player.copy(role = role("aldeano", "Aldeano", "Pueblo"))
                    else -> player
                }
            },
            chatHistory = listOf(
                GameChatMessage("Humano", "soy detective, pregunte por Dina y es inocente"),
                GameChatMessage("Beto", "Humano vos q decis de todo esto?")
            )
        )

        val resolved = GameEngine.addHumanChatMessage(session, "a vos te dije")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue("Respuestas: $botText", botText.contains("dina", ignoreCase = true))
        assertTrue(
            "Respuestas: $botText",
            botText.contains("me lo decias a mi") ||
                botText.contains("entendi") ||
                botText.contains("tomo esa lectura") ||
                botText.contains("lectura")
        )
    }

    @Test
    fun botDoesNotOvervoteAfkHumanAfterUsefulDetectiveRead() {
        val base = publicNameSession()
        val session = base.copy(
            code = "AFK-DETECTIVE-READ",
            phase = GamePhase.VOTACION,
            players = base.players.map { player ->
                when (player.name) {
                    "Humano" -> player.copy(role = role("policia", "Comisario", "Pueblo"))
                    "Beto" -> player.copy(role = role("aldeano", "Aldeano", "Pueblo"))
                    else -> player
                }
            },
            chatHistory = listOf(
                GameChatMessage("Humano", "soy detective, pregunte por Dina y es inocente"),
                GameChatMessage("Ciro", "Humano vos q lectura tenes?"),
                GameChatMessage("Beto", "Ana esta rara"),
                GameChatMessage("Dina", "Ana tiene que responder")
            )
        )
        val ciro = GameEngine.playerByName(session, "Ciro")!!

        assertEquals(
            "Plan: ${LocalBotAi.votePlanSnapshot(session, ciro.name)}",
            "Ana",
            LocalBotAi.chooseVoteTarget(session, ciro)
        )
    }

    @Test
    fun botReactsToPublicDeathEventWithoutLeakingSecretRoles() {
        val session = publicNameSession().copy(
            code = "EVENT-DEATH",
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = "Amanecer: murio Dina."
        )
        val event = LocalBotAi.publicEventFromAnnouncement(session)

        assertEquals(LocalBotAi.BotEventType.MUERTE_NOCTURNA, event?.type)
        assertEquals("Dina", event?.target)

        val reactions = LocalBotAi.reactionsToEvent(session, event!!)
        val text = reactions.joinToString(" ") { it.second }

        assertTrue("Reacciones: $reactions", reactions.isNotEmpty())
        assertTrue("Reacciones: $reactions", reactions.size <= 3)
        assertTrue("Reacciones: $text", text.contains("Dina", ignoreCase = true))
        assertFalse("Reacciones: $text", text.contains("asesino", ignoreCase = true))
        assertFalse("Reacciones: $text", text.contains("traidor", ignoreCase = true))
    }

    @Test
    fun publicEventPrefersSilenceWhenNobodyDied() {
        val session = publicNameSession().copy(
            publicAnnouncement = "Amanecer: no murio nadie. Dina no puede hablar ni votar hoy."
        )

        val event = LocalBotAi.publicEventFromAnnouncement(session)

        assertEquals(LocalBotAi.BotEventType.SILENCIO, event?.type)
        assertEquals("Dina", event?.target)
    }

    @Test
    fun botVoteUsesPublicTrustThenAccuseContradiction() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            claimLedger = mapOf(
                "Dina" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.TRUST,
                        target = "Ana"
                    ),
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.ACCUSE,
                        target = "Ana"
                    )
                )
            )
        )
        val ciro = GameEngine.playerByName(session, "Ciro")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, ciro))
    }

    @Test
    fun botPayadorSkipsContrapuntoWithoutRealMaterial() {
        val resolved = GameEngine.resolveDayDebate(payadorBotSession())

        assertEquals(GamePhase.VOTACION, resolved.phase)
        assertFalse(resolved.payadorUsed)
        assertTrue(resolved.contrapuntoPlayers.isEmpty())
    }

    @Test
    fun botPayadorOpensContrapuntoForPublicContradictionAndAccuser() {
        val session = payadorBotSession().copy(
            claimLedger = mapOf(
                "Beto" to listOf(
                    ClaimRecord(round = 1, phase = GamePhase.DIA_DEBATE, roleKey = RoleCatalog.POLICIA),
                    ClaimRecord(round = 1, phase = GamePhase.DIA_DEBATE, roleKey = RoleCatalog.MEDICO)
                ),
                "Ciro" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.ACCUSE,
                        target = "Beto"
                    )
                )
            )
        )

        val resolved = GameEngine.resolveDayDebate(session)

        assertEquals(GamePhase.CONTRAPUNTO, resolved.phase)
        assertEquals(listOf("Beto", "Ciro"), resolved.contrapuntoPlayers)
    }

    @Test
    fun botContrapuntoSuspectUsesHighestPublicReadAmongParticipants() {
        val session = payadorBotSession().copy(
            tableMemory = TableMemory(
                suspicion = mapOf("Payador" to mapOf("Beto" to 1, "Ciro" to 12))
            )
        )
        val payador = GameEngine.playerByName(session, "Payador")!!

        assertEquals(
            "Ciro",
            LocalBotAi.chooseBotContrapuntoSuspect(session, payador, listOf("Beto", "Ciro"))
        )
    }

    @Test
    fun jesterSelfAccusationSurvivesFinishSpeechButTownSelfAccusationDoesNot() {
        val session = payadorBotSession()
        val jester = GamePlayer("Bufon", "B", role = role(RoleCatalog.BUFON, "Bufon", "Neutral"))
        val town = GamePlayer("Aldeano", "A", role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"))
        val raw = "saquenme a mi total no aporto nada"

        val jesterLine = finishSpeech(raw, session, jester, "test")
        val townLine = finishSpeech(raw, session, town, "test")

        assertTrue(jesterLine.contains("saquenme"))
        assertFalse(townLine.contains("saquenme"))
    }

    @Test
    fun desertorInitialTeamUsesAssignedRolesInsteadOfOnlySessionCode() {
        val townLeaning = listOf(
            GamePlayer("Humano", "H", role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"), isHuman = true),
            GamePlayer("Desertor", "D", role = role(RoleCatalog.DESERTOR, "Desertor", "Neutral")),
            GamePlayer("Asesino", "A", role = role(RoleCatalog.ASESINO, "Asesino", "Traidores")),
            GamePlayer("Beto", "B", role = role(RoleCatalog.MEDICO, "Medico", "Pueblo"))
        )
        val traitorLeaning = listOf(
            townLeaning[0],
            townLeaning[1],
            townLeaning[2].copy(role = townLeaning[3].role),
            townLeaning[3].copy(role = townLeaning[2].role)
        )

        assertNotEquals(
            LocalGameFactory.initialDesertorTeam(townLeaning, "SALA-01"),
            LocalGameFactory.initialDesertorTeam(traitorLeaning, "SALA-01")
        )
    }

    @Test
    fun gameSoundRelativeVolumesOnlyAttenuate() {
        GameSound.entries.forEach { sound ->
            assertTrue("${sound.name}: ${sound.relativeVolume}", sound.relativeVolume > 0f)
            assertTrue("${sound.name}: ${sound.relativeVolume}", sound.relativeVolume <= 1f)
        }
    }

    @Test
    fun importantSoundsAndFeedbackHaveHapticCues() {
        assertEquals(HapticLevel.LIGHT, GameSound.VOTE_CAST.haptic)
        assertEquals(HapticLevel.LIGHT, GameSound.CARD_DEAL.haptic)
        assertEquals(HapticLevel.LIGHT, GameSound.NO_DEATH.haptic)
        assertEquals(HapticLevel.MEDIUM, GameSound.ORACLE.haptic)
        assertEquals(HapticLevel.MEDIUM, GameSound.PAYADOR.haptic)
        assertEquals(GameplayFeedbackCue.EMOTE, GameplayEffects.cueFor(GameplayEffect.EMOTE))
        assertEquals(GameplayFeedbackCue.ERROR, GameplayEffects.cueFor(GameplayEffect.ERROR))
    }

    @Test
    fun botVotesUsePublicDoubleClaimAsSuspicion() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Humano", "soy medico"),
                GameChatMessage("Ciro", "para, yo soy medico. si decis lo mismo conta a quien cuidaste"),
                GameChatMessage("Dina", "doble claim entonces, uno esta vendiendo humo")
            )
        )
        val ciro = GameEngine.playerByName(session, "Ciro")!!

        assertEquals("Humano", LocalBotAi.chooseVoteTarget(session, ciro))
    }

    @Test
    fun botReactsToHumanActionDetailsWithoutImmediateAccusation() {
        val session = publicNameSession().copy(phase = GamePhase.DIA_DEBATE)

        val resolved = GameEngine.addHumanChatMessage(session, "protegi a Dina")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue("Respuestas: $botText", botText.contains("queda anotado") || botText.contains("confirma"))
        assertFalse("Respuestas: $botText", botText.contains("esta re raro"))
    }

    @Test
    fun pressuredMedicCanRevealRoleAndOwnActionPublicly() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Humano", "Ciro esta raro, para mi hay que votarlo"),
                GameChatMessage("Beto", "Ciro explica ya")
            ),
            actionHistory = listOf(
                GameAction(
                    type = GameActionType.PROTECT,
                    actor = "Ciro",
                    target = "Dina",
                    round = 1,
                    phase = GamePhase.NOCHE_MEDICO
                )
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .filter { it.first == "Ciro" }
            .joinToString(" ") { it.second }

        assertTrue("Mensajes Ciro: $messages", messages.contains("medico", ignoreCase = true))
        assertTrue("Mensajes Ciro: $messages", messages.contains("Dina", ignoreCase = true))
    }

    @Test
    fun detectiveUsesOwnInvestigationAsThreadWithoutClaimingResult() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            botDifficulty = BotDifficulty.HARD,
            chatHistory = listOf(
                GameChatMessage("Humano", "Beto esta raro, explica tu noche")
            ),
            actionHistory = listOf(
                GameAction(
                    type = GameActionType.INVESTIGATE,
                    actor = "Beto",
                    target = "Dina",
                    round = 1,
                    phase = GamePhase.NOCHE_POLICIA
                )
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .filter { it.first == "Beto" }
            .joinToString(" ") { it.second }

        assertTrue("Mensajes Beto: $messages", messages.contains("Dina", ignoreCase = true))
        assertFalse("Mensajes Beto: $messages", messages.contains("sospechoso", ignoreCase = true))
        assertFalse("Mensajes Beto: $messages", messages.contains("inocente", ignoreCase = true))
    }

    @Test
    fun botPressesHumanWhenRoleIsRefused() {
        val session = publicNameSession().copy(phase = GamePhase.DIA_DEBATE)

        val resolved = GameEngine.addHumanChatMessage(session, "no digo mi rol")
        val botText = resolved.chatHistory
            .filter { it.speaker != "Humano" }
            .joinToString(" ") { it.message }

        assertTrue("Respuestas: $botText", botText.contains("aporta algo") || botText.contains("te miran raro"))
    }

    @Test
    fun botVotesFollowExplicitHumanAccusationAsPublicSignal() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Humano", "Dina miente"),
                GameChatMessage("Beto", "puede ser, pero deci que viste de Dina")
            )
        )
        val beto = GameEngine.playerByName(session, "Beto")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, beto))
    }

    @Test
    fun botsAnswerEachOtherInsteadOfOnlyReactingToHuman() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Beto", "dina cambio de tema y me parece raro")
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .joinToString(" ") { "${it.first}: ${it.second}" }

        assertTrue("Mensajes: $messages", messages.contains("beto", ignoreCase = true))
        assertTrue("Mensajes: $messages", messages.contains("dina", ignoreCase = true))
    }

    @Test
    fun botVotingIntentReferencesItsOwnEarlierPressure() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "dina pq cambiaste de tema?"),
                GameChatMessage("Humano", "yo tambien miro a Dina")
            )
        )

        val betoMessage = LocalBotAi.votingIntentMessages(session, limit = 5)
            .firstOrNull { it.first == "Beto" }
            ?.second
            .orEmpty()

        assertTrue(
            "Mensaje Beto: $betoMessage",
            betoMessage.contains("le pregunte") ||
                betoMessage.contains("vengo marcando") ||
                betoMessage.contains("no respondio") ||
                betoMessage.contains("esquivo") ||
                betoMessage.contains("preguntas colgadas") ||
                betoMessage.contains("tenia q contestar")
        )
    }

    @Test
    fun botCanAdmitDoubtAfterPushingWrongExpulsion() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            publicHistory = listOf("El pueblo expulso a Ema."),
            chatHistory = listOf(
                GameChatMessage("Beto", "para mi Ema esta re rara")
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .joinToString(" ") { it.second }

        assertTrue(
            "Mensajes: $messages",
            messages.contains("me pude haber equivocado") ||
                messages.contains("le erre")
        )
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
    fun botPersonalitiesStayBalancedButCanChangeBetweenMatches() {
        val base = publicNameSession()
        val profiles = (1L..12L).map { matchSeed ->
            LocalBotAi.personalityProfile(
                base.copy(
                    code = "PERS",
                    startedAtEpochMs = matchSeed
                )
            )
        }

        assertTrue(profiles.all { profile -> profile.values.toSet().size >= 3 })
        assertTrue("Perfiles: $profiles", profiles.toSet().size > 1)
        assertEquals(
            profiles.first(),
            LocalBotAi.personalityProfile(base.copy(code = "PERS", startedAtEpochMs = 1L))
        )
    }

    @Test
    fun botDebateCoordinatesDifferentConversationRoles() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Humano", "Dina esta rara"),
                GameChatMessage("Beto", "Dina pq cambiaste de tema?")
            )
        )

        val messages = LocalBotAi.openingDebateMessages(session, limit = 5)
            .map { it.second }

        assertTrue("Mensajes: $messages", messages.size >= 4)
        assertTrue("Mensajes: $messages", messages.any { it.contains("?") || it.contains("respond") })
        assertTrue(
            "Mensajes: $messages",
            messages.any {
                it.contains("no votemos") ||
                    it.contains("escuchemos") ||
                    it.contains("puedo estar") ||
                    it.contains("no estoy")
            }
        )
    }

    @Test
    fun botVotingIntentMixesPushAndCaution() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Humano", "Dina esta rara"),
                GameChatMessage("Beto", "Dina pq cambiaste de tema?"),
                GameChatMessage("Ciro", "Dina tiene que responder eso")
            )
        )

        val messages = LocalBotAi.votingIntentMessages(session, limit = 5)
            .map { it.second }

        assertTrue("Mensajes: $messages", messages.any { it.contains("dina", ignoreCase = true) })
        assertTrue(
            "Mensajes: $messages",
            messages.any {
                it.contains("por ahora") ||
                    it.contains("no me gusta votar") ||
                    it.contains("no por manada") ||
                    it.contains("quiero una respuesta")
            }
        )
    }

    @Test
    fun botDebateAndVotingBatchesDoNotRepeatSameLine() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Humano", "Dina esta rara"),
                GameChatMessage("Beto", "Dina pq cambiaste de tema?"),
                GameChatMessage("Ciro", "Dina tiene que responder eso")
            )
        )

        val opening = LocalBotAi.openingDebateMessages(session, limit = 5).map { it.second }
        val voting = LocalBotAi.votingIntentMessages(session.copy(phase = GamePhase.VOTACION), limit = 5)
            .map { it.second }

        assertEquals(opening.map { botLineKey(it) }.distinct().size, opening.size)
        assertEquals(voting.map { botLineKey(it) }.distinct().size, voting.size)
    }

    @Test
    fun botAccusedByAnotherBotDefendsInsteadOfAgreeingAgainstItself() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Beto", "dina esta rara, para mi hay que mirar fuerte a dina")
            )
        )

        val dinaLine = LocalBotAi.openingDebateMessages(session, limit = 5)
            .firstOrNull { it.first == "Dina" }
            ?.second
            .orEmpty()

        assertTrue("Dina no hablo en la tanda: $dinaLine", dinaLine.isNotBlank())
        assertFalse("Dina se autoacuso: $dinaLine", isSelfAccusatoryBotLine("Dina", dinaLine))
        assertTrue(
            "Dina deberia defenderse o pedir razon: $dinaLine",
            dinaLine.contains("me marcas") ||
                dinaLine.contains("ahora yo") ||
                dinaLine.contains("tirame una razon") ||
                dinaLine.contains("decime q hice") ||
                dinaLine.contains("yo no dije")
        )
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
            listOf("dale", "para", "jaja", "amigo", "no inventes", "decime", "q hice", "q decis", "posta", "nose", "mmm", "explica")
                .any { replies.contains(it) }
        )
    }

    @Test
    fun botRepliesPauseAfterSeveralBotMessagesInARow() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Beto", "dina me hace ruido"),
                GameChatMessage("Ciro", "si, q explique algo"),
                GameChatMessage("Ema", "yo tambien la miro")
            )
        )

        val replies = LocalBotAi.reactionsToHumanMessage(session, "dina no me cierra")

        assertTrue("Respuestas: $replies", replies.isEmpty())
    }

    @Test
    fun botAnswerToPendingHumanQuestionKeepsTheSameThread() {
        val session = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Ana", "Humano a quien estas mirando?")
            )
        )

        val replies = LocalBotAi.reactionsToHumanMessage(session, "miro a Dina")
            .joinToString(" ") { it.second }

        assertTrue("Respuestas: $replies", replies.contains("dina", ignoreCase = true))
        assertTrue(
            "Respuestas: $replies",
            replies.contains("sigamos") ||
                replies.contains("conteste") ||
                replies.contains("punta")
        )
    }

    @Test
    fun botVoteUsesUnansweredQuestionFromConversationMemory() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "Dina pq cambiaste de tema?")
            )
        )
        val beto = GameEngine.playerByName(session, "Beto")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, beto))
    }

    @Test
    fun hardDifficultyTrustsAccumulatedVoteHistoryMoreThanNormal() {
        val base = publicNameSession().copy(
            code = "DIFF-HISTORY",
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "Dina pq cambiaste de tema?"),
                GameChatMessage("Ciro", "Dina me hace ruido"),
                GameChatMessage("Humano", "Dina esta rara")
            )
        )

        val normalPlan = LocalBotAi.votePlanSnapshot(
            base.copy(botDifficulty = BotDifficulty.NORMAL),
            "Beto"
        )
        val hardPlan = LocalBotAi.votePlanSnapshot(
            base.copy(botDifficulty = BotDifficulty.HARD),
            "Beto"
        )

        assertEquals("Dina", normalPlan?.target)
        assertEquals("Dina", hardPlan?.target)
        assertTrue("Normal: $normalPlan Hard: $hardPlan", hardPlan!!.confidence > normalPlan!!.confidence)
        assertTrue("Hard: $hardPlan", hardPlan.beats >= normalPlan.beats)
    }

    @Test
    fun hardDifficultyKeepsRespondingToPendingThreadsLonger() {
        val base = publicNameSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(
                GameChatMessage("Beto", "dina me hace ruido"),
                GameChatMessage("Ciro", "si, q explique algo")
            )
        )

        val normalReplies = LocalBotAi.reactionsToHumanMessage(
            base.copy(botDifficulty = BotDifficulty.NORMAL),
            "dina no me cierra"
        )
        val hardReplies = LocalBotAi.reactionsToHumanMessage(
            base.copy(botDifficulty = BotDifficulty.HARD),
            "dina no me cierra"
        )

        assertTrue("Normal: $normalReplies Hard: $hardReplies", hardReplies.size >= normalReplies.size)
        assertTrue("Hard: $hardReplies", hardReplies.any { it.second.contains("dina", ignoreCase = true) })
    }

    @Test
    fun humanDayPressureBonusOnlyAppliesOnHardAndScalesWithRounds() {
        val base = baseSession()

        assertEquals(0, humanDayPressureBonus(base.copy(botDifficulty = BotDifficulty.NORMAL, round = 1)))
        assertEquals(4, humanDayPressureBonus(base.copy(botDifficulty = BotDifficulty.HARD, round = 1)))
        assertEquals(8, humanDayPressureBonus(base.copy(botDifficulty = BotDifficulty.HARD, round = 3)))
        assertEquals(14, humanDayPressureBonus(base.copy(botDifficulty = BotDifficulty.HARD, round = 9)))
    }

    @Test
    fun humanNightPressureChanceUsesConfiguredDifficultyTable() {
        val base = baseSession()

        assertEquals(5, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.NORMAL, round = 1)))
        assertEquals(17, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.NORMAL, round = 5)))
        assertEquals(20, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.NORMAL, round = 9)))
        assertEquals(12, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.HARD, round = 1)))
        assertEquals(40, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.HARD, round = 5)))
        assertEquals(45, humanPressureChancePercent(base.copy(botDifficulty = BotDifficulty.HARD, round = 9)))
    }

    @Test
    fun hardDifficultyPressuresUnreadHumanButNormalDoesNot() {
        val base = baseSession().copy(round = 3, chatHistory = emptyList(), publicHistory = emptyList())
        val voter = base.players.first { it.role?.key == RoleCatalog.ASESINO }
        val human = GameEngine.humanPlayer(base)

        val normalRead = scoreCandidate(base.copy(botDifficulty = BotDifficulty.NORMAL), voter, human, emptySet())
        val hardRead = scoreCandidate(base.copy(botDifficulty = BotDifficulty.HARD), voter, human, emptySet())

        assertFalse(normalRead.reasons.contains("esta poco leido"))
        assertTrue(hardRead.reasons.contains("esta poco leido"))
        assertTrue(hardRead.score > normalRead.score)
    }

    @Test
    fun detectiveBotUsesItsPrivateInvestigationWithoutGivingItToEveryBot() {
        val session = publicNameSession().copy(
            phase = GamePhase.VOTACION,
            investigatedPlayer = "Ana",
            investigatedResult = "sospechoso",
            tableMemory = TableMemory(
                privateInvestigationReads = listOf(
                    InvestigationRead(1, "Beto", "Ana", "sospechoso")
                )
            ),
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
        assertEquals("Ana", resolved.votes["Beto"])
        assertTrue(secretInvestigationVotes >= 1)
        assertTrue(publicSuspicionVotes >= 1)
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
    fun botVotePrefersConversationHistoryOverSingleFreshAccusation() {
        val session = publicNameSession().copy(
            code = "HISTORY-VOTE",
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "Dina pq cambiaste de tema?"),
                GameChatMessage("Ciro", "Dina me hace ruido"),
                GameChatMessage("Humano", "Ana esta rara")
            )
        )
        val ciro = GameEngine.playerByName(session, "Ciro")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, ciro))
    }

    @Test
    fun botVotingIntentExplainsAccumulatedStory() {
        val session = publicNameSession().copy(
            code = "HISTORY-SPEECH",
            phase = GamePhase.VOTACION,
            chatHistory = listOf(
                GameChatMessage("Beto", "Dina pq cambiaste de tema?"),
                GameChatMessage("Ciro", "Dina me hace ruido"),
                GameChatMessage("Humano", "Dina esta rara")
            )
        )

        val message = LocalBotAi.votingIntentMessages(session, limit = 5)
            .firstOrNull { it.first == "Beto" }
            ?.second
            .orEmpty()

        assertTrue("Mensaje Beto: $message", message.contains("dina", ignoreCase = true))
        assertTrue(
            "Mensaje Beto: $message",
            message.contains("secuencia") ||
                message.contains("no es una corazonada") ||
                message.contains("viene mal") ||
                message.contains("sale de esto") ||
                message.contains("pregunta colgada") ||
                message.contains("yo ya lo venia siguiendo")
        )
    }

    @Test
    fun debugVoteCommandMakesBotsVoteForHuman() {
        val session = publicNameSession().copy(
            code = "DEBUG-VOTE-ME",
            phase = GamePhase.VOTACION,
            debugBotsObeyVoteCommands = true,
            chatHistory = listOf(
                GameChatMessage("Ana", "no se a quien votar"),
                GameChatMessage("Humano", "Vótenme, quiero probar algo")
            )
        )

        session.players.filterNot { it.isHuman }.forEach { bot ->
            assertEquals("Humano", LocalBotAi.chooseVoteTarget(session, bot))
        }
    }

    @Test
    fun debugVoteCommandCanNameAnotherLivingPlayer() {
        val session = publicNameSession().copy(
            code = "DEBUG-VOTE-NAMED",
            phase = GamePhase.VOTACION,
            debugBotsObeyVoteCommands = true,
            chatHistory = listOf(
                GameChatMessage("Humano", "voten a Dina")
            )
        )
        val beto = GameEngine.playerByName(session, "Beto")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, beto))
    }

    @Test
    fun debugVoteCommandIsIgnoredWhenOptionIsOff() {
        val session = publicNameSession().copy(
            code = "DEBUG-VOTE-OFF",
            phase = GamePhase.VOTACION,
            debugBotsObeyVoteCommands = false,
            chatHistory = listOf(
                GameChatMessage("Beto", "para mi Dina esta re rara, voto a Dina"),
                GameChatMessage("Humano", "votenme")
            )
        )
        val beto = GameEngine.playerByName(session, "Beto")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, beto))
    }

    @Test
    fun debugVoteCommandIgnoresOrdersWrittenByBots() {
        val session = publicNameSession().copy(
            code = "DEBUG-VOTE-SPOOF",
            phase = GamePhase.VOTACION,
            debugBotsObeyVoteCommands = true,
            chatHistory = listOf(
                GameChatMessage("Beto", "votenme"),
                GameChatMessage("Humano", "voten a Dina")
            )
        )
        val ana = GameEngine.playerByName(session, "Ana")!!

        assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, ana))
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
                "El pueblo expulso a Ema.",
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
    fun nextRoundHistoryDoesNotRepeatExpulsionMessage() {
        val nextRound = GameEngine.resolveResult(
            baseSession().copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = "Aldeano1",
                round = 1
            )
        )

        assertEquals(GamePhase.NOCHE_ASESINO, nextRound.phase)
        assertEquals(1, nextRound.publicHistory.count { it.contains("Aldeano1 fue expulsado") })
        assertTrue(nextRound.publicHistory.any { it.startsWith("Noche 2:") })
        assertFalse(
            nextRound.publicHistory.any {
                it.contains("Aldeano1 fue expulsado") && it.contains("Noche 2:")
            }
        )
        assertTrue(nextRound.publicAnnouncement.contains("Aldeano1 fue expulsado"))
        assertTrue(nextRound.publicAnnouncement.contains("Noche 2:"))
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
            quickTestMode = true,
            players = basePlayers().map {
                if (it.isHuman) it.copy(alive = false, muted = false) else it
            }
        )

        val resolved = GameEngine.resolveVoting(session, "")

        assertFalse(GameEngine.requiresHumanInput(session))
        assertTrue(GameEngine.shouldAutoAdvance(session))
        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
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

    @Test
    fun desertorSupportingTraitorsDoesNotAccelerateParityWin() {
        val players = listOf(
            GamePlayer("Humano", "H", role = role("desertor", "Desertor", "Neutral"), isHuman = true),
            GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
            GamePlayer("Mercenario", "R", role = role("mercenario", "Mercenario", "Traidores")),
            GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo")),
            GamePlayer("Medico", "M", role = role("medico", "Medico", "Pueblo")),
            GamePlayer("Alcalde", "L", role = role("alcalde", "Alcalde", "Pueblo")),
            GamePlayer("Bufon", "B", role = role("bufon", "Bufon", "Neutral")),
            GamePlayer("Aldeano1", "1", role = role("aldeano", "Aldeano", "Pueblo"), alive = false),
            GamePlayer("Aldeano2", "2", role = role("aldeano", "Aldeano", "Pueblo"), alive = false)
        )

        val session = GameSession(
            code = "DESERTOR",
            mapKey = "medieval",
            mapName = "Medieval",
            players = players,
            desertorTeam = GameRules.TRAITOR_WINNER
        )

        assertEquals("", GameRules.winnerFor(session))
    }

    @Test
    fun desertorDoesNotCountForParityEvenWhenSupportingTraitors() {
        val players = listOf(
            GamePlayer("Humano", "H", role = role("desertor", "Desertor", "Neutral"), isHuman = true),
            GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
            GamePlayer("Mercenario", "R", role = role("mercenario", "Mercenario", "Traidores")),
            GamePlayer("Espia", "E", role = role("espia", "Espia", "Traidores")),
            GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo")),
            GamePlayer("Medico", "M", role = role("medico", "Medico", "Pueblo")),
            GamePlayer("Alcalde", "L", role = role("alcalde", "Alcalde", "Pueblo")),
            GamePlayer("Aldeano1", "1", role = role("aldeano", "Aldeano", "Pueblo"), alive = false),
            GamePlayer("Aldeano2", "2", role = role("aldeano", "Aldeano", "Pueblo"), alive = false),
            GamePlayer("Payador", "Y", role = role("payador", "Payador", "Pueblo"), alive = false)
        )

        val session = GameSession(
            code = "DESERTOR-10",
            mapKey = "medieval",
            mapName = "Medieval",
            players = players,
            desertorTeam = GameRules.TRAITOR_WINNER,
            initialPlayerCount = 10
        )

        assertEquals(GameRules.TRAITOR_WINNER, GameRules.winnerFor(session))
    }

    @Test
    fun spyResolvesKillWhenNoAssassinIsAlive() {
        val base = sessionWithHumanAdvancedRole("espia").copy(phase = GamePhase.NOCHE_ASESINO)
        val session = base.copy(
            players = base.players.map {
                if (it.role?.key == "asesino") it.copy(alive = false, muted = true) else it
            }
        )

        val resolved = GameEngine.resolveAssassin(session, "Policia")
        val dawn = GameEngine.resolveDawn(
            resolved.copy(
                phase = GamePhase.AMANECER,
                protectedPlayer = ""
            )
        )

        assertEquals("Policia", resolved.nightKillTarget)
        assertTrue(resolved.actionHistory.any {
            it.type == GameActionType.KILL && it.actor == "Humano" && it.target == "Policia"
        })
        assertFalse(GameEngine.playerByName(dawn, "Policia")!!.alive)
    }

    @Test
    fun spyChoosesVictimAlongsideLivingAssassins() {
        val session = sessionWithHumanAdvancedRole("espia").copy(phase = GamePhase.NOCHE_ASESINO)
        // Con al menos un asesino vivo, el Espia igual participa eligiendo la victima.
        assertTrue(session.players.any { it.role?.key == "asesino" && it.alive })

        val resolved = GameEngine.resolveAssassin(session, "Policia")

        assertTrue(resolved.actionHistory.any {
            it.type == GameActionType.KILL && it.actor == "Humano" && it.target == "Policia"
        })
    }

    @Test
    fun payadorSuspicionAffectsTheRecordedVoteTally() {
        val session = sessionWithHumanAdvancedRole("payador").copy(
            phase = GamePhase.VOTACION,
            contrapuntoSuspicion = "Asesino"
        )

        val resolved = GameEngine.resolveVotingWithRecordedVotes(
            session,
            mapOf(
                "Humano" to "Policia",
                "Mercenario" to "Asesino",
                "Espia" to "Asesino",
                "Policia" to "Asesino",
                "Medico" to "Policia",
                "Alcalde" to "Policia"
            )
        )

        assertEquals(GamePhase.RECUENTO_VOTOS, resolved.phase)
        assertEquals("Asesino", resolved.dayEliminationTarget)
        assertTrue(resolved.tieVoteCandidates.isEmpty())
    }

    @Test
    fun deadMayorCannotResolveARepeatedTie() {
        val base = sessionWithHumanAdvancedRole("alcalde")
        val recount = base.copy(
            phase = GamePhase.RECUENTO_VOTOS,
            voteRound = 2,
            tieVoteCandidates = listOf("Asesino", "Policia"),
            dayEliminationTarget = "",
            players = base.players.map {
                if (it.role?.key == "alcalde") it.copy(alive = false, muted = true) else it
            }
        )

        val resolved = GameEngine.continueAfterVoteRecount(recount)

        assertEquals(GamePhase.RESULTADO, resolved.phase)
        assertEquals("", resolved.dayEliminationTarget)
        assertTrue(resolved.alcaldeTieCandidates.isEmpty())
    }

    @Test
    fun oracleInvitedDeadPlayerDoesNotCountAsAliveForVictory() {
        val invoked = GameEngine.resolveOracle(oracleSession(), "Fallecido")

        val dawn = GameEngine.resolveDawn(
            invoked.copy(
                nightKillTarget = "Ciudadana",
                protectedPlayer = ""
            )
        )

        assertFalse(GameEngine.playerByName(dawn, "Fallecido")!!.alive)
        assertEquals(GameRules.TRAITOR_WINNER, dawn.winner)
    }

    @Test
    fun allPlayersDeadDoesNotProduceAVacuousTownWin() {
        val allDead = basePlayers().map { it.copy(alive = false, muted = true) }

        assertEquals("", GameRules.winnerFor(allDead))
    }

    @Test
    fun desertorWithoutChosenTeamDelaysParityVictory() {
        val session = GameSession(
            code = "DESERTOR-OPEN",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = role("desertor", "Desertor", "Neutral"), isHuman = true),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Policia", "P", role = role("policia", "Comisario", "Pueblo"))
            )
        )

        assertEquals("", GameRules.winnerFor(session))
    }

    @Test
    fun revealedMayorVoteAndPayadorSuspicionCanCombineIntoATie() {
        val session = sessionWithHumanAdvancedRole("alcalde").copy(
            phase = GamePhase.VOTACION,
            alcaldeRevealed = true,
            contrapuntoSuspicion = "Policia"
        )

        val resolved = GameEngine.resolveVotingWithRecordedVotes(
            session,
            mapOf(
                "Humano" to "Asesino",
                "Policia" to "Medico",
                "Medico" to "Policia"
            )
        )

        assertEquals("", resolved.dayEliminationTarget)
        assertEquals(setOf("Asesino", "Policia"), resolved.tieVoteCandidates.toSet())
    }

    @Test
    fun mercenarySilenceDoesNotMuteAPlayerKilledTheSameNight() {
        val session = sessionWithHumanAdvancedRole("mercenario").copy(
            phase = GamePhase.NOCHE_MERCENARIO,
            nightKillTarget = "Policia"
        )

        val silenced = GameEngine.resolveMercenary(session, "Policia")
        val dawn = GameEngine.resolveDawn(
            silenced.copy(
                phase = GamePhase.AMANECER,
                protectedPlayer = ""
            )
        )
        val victim = GameEngine.playerByName(dawn, "Policia")

        assertFalse(victim!!.alive)
        assertFalse(victim.muted)
        assertFalse(dawn.publicAnnouncement.contains("no puede hablar"))
    }

    @Test
    fun medicProtectionPreventsMercenarySilenceOnSameTarget() {
        val session = sessionWithHumanAdvancedRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            nightSilenceTarget = "Policia"
        )

        val protectedByMedic = GameEngine.resolveMedic(session, "Policia")
        val dawn = GameEngine.resolveDawn(protectedByMedic.copy(phase = GamePhase.AMANECER))
        val protected = GameEngine.playerByName(dawn, "Policia")

        assertTrue(protected!!.alive)
        assertFalse(protected.muted)
        assertFalse(dawn.publicAnnouncement.contains("Policia no puede hablar"))
    }

    @Test
    fun oracleCannotRecordASecondUse() {
        val firstUse = GameEngine.resolveOracle(oracleSession(), "Fallecido")
        val repeated = GameEngine.resolveOracle(firstUse.copy(phase = GamePhase.NOCHE_ORACULO), "Fallecido")

        assertTrue(repeated.oracleUsed)
        assertEquals(GamePhase.AMANECER, repeated.phase)
        assertEquals(1, repeated.actionHistory.count { it.type == GameActionType.INVITE_DEAD })
    }

    @Test
    fun bufonExpelledByMayorTieDecisionGetsSpecialVictory() {
        val recount = GameSession(
            code = "JESTER-MAYOR",
            mapKey = "medieval",
            mapName = "Medieval",
            players = listOf(
                GamePlayer("Alcalde", "L", role = role("alcalde", "Alcalde", "Pueblo"), isHuman = true),
                GamePlayer("Bufon", "B", role = role("bufon", "Bufon", "Neutral")),
                GamePlayer("Asesino", "A", role = role("asesino", "Asesino", "Traidores")),
                GamePlayer("Medico", "M", role = role("medico", "Medico", "Pueblo"))
            ),
            phase = GamePhase.RECUENTO_VOTOS,
            voteRound = 2,
            tieVoteCandidates = listOf("Bufon", "Asesino"),
            initialPlayerCount = 4
        )

        val mayorDecision = GameEngine.continueAfterVoteRecount(recount)
        val revealed = GameEngine.revealAlcalde(mayorDecision)
        val chosen = GameEngine.chooseAlcaldeTie(revealed, "Bufon")
        val resultPhase = GameEngine.continueAfterVoteRecount(chosen)
        val resolved = GameEngine.resolveResult(resultPhase)

        assertEquals(GamePhase.ALCALDE_DESEMPATE, mayorDecision.phase)
        assertEquals("bufon_expulsado", resolved.specialVictories.single().key)
        assertEquals("Bufon", resolved.specialVictories.single().playerName)
        assertFalse(GameEngine.playerByName(resolved, "Bufon")!!.alive)
    }

    @Test
    fun medicCanProtectThemself() {
        val session = sessionWithHumanRole("medico").copy(
            phase = GamePhase.NOCHE_MEDICO,
            nightKillTarget = "Humano"
        )

        val protected = GameEngine.resolveMedic(session, "Humano")
        val dawn = GameEngine.resolveDawn(protected)

        assertEquals("Humano", protected.protectedPlayer)
        assertTrue(GameEngine.playerByName(dawn, "Humano")!!.alive)
        assertEquals(
            "Amanecer: no murió nadie. El pueblo despierta entero, pero nadie durmió tranquilo.",
            dawn.publicAnnouncement
        )
    }

    @Test
    fun explicitSelfVoteIsRejected() {
        val session = baseSession().copy(phase = GamePhase.VOTACION)

        val resolved = GameEngine.resolveVoting(session, "Humano")

        assertEquals(session, resolved)
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

    private fun oracleSession(): GameSession {
        return GameSession(
            code = "ORACLE",
            mapKey = "grecia",
            mapName = "Grecia",
            players = listOf(
                GamePlayer(
                    "Humano",
                    "H",
                    role = RoleCatalog.gameRole(RoleCatalog.ORACULO, RoleMap.GREECE),
                    isHuman = true
                ),
                GamePlayer(
                    "Fallecido",
                    "F",
                    role = RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.GREECE),
                    alive = false
                ),
                GamePlayer(
                    "Asesina",
                    "A",
                    role = RoleCatalog.gameRole(RoleCatalog.ASESINO, RoleMap.GREECE)
                ),
                GamePlayer(
                    "Ciudadana",
                    "C",
                    role = RoleCatalog.gameRole(RoleCatalog.MEDICO, RoleMap.GREECE)
                )
            ),
            phase = GamePhase.NOCHE_ORACULO,
            round = 2
        )
    }

    @Test
    fun botMessageCoreRemovesRepeatedLeadingFillers() {
        assertEquals(
            "mora tiene que explicar su voto",
            botMessageCore("Bueno, dale, Mora tiene que explicar su voto.")
        )
        assertEquals("", botMessageCore("Okey"))
    }

    @Test
    fun botMessageDedupeRejectsFillerWrappedEchoes() {
        val messages = listOf(
            "Beto" to "Mora tiene que explicar por qué cambió el voto",
            "Ciro" to "Dale, Mora tiene que explicar por qué cambió el voto",
            "Dina" to "Mora todavía puede contar qué vio anoche"
        )

        assertEquals(listOf(messages[0], messages[2]), messages.dedupeBotMessages())
    }

    @Test
    fun recentEchoFilterOnlyUsesBotMessages() {
        val session = publicNameSession().copy(
            chatHistory = listOf(
                GameChatMessage("Humano", "Mora tiene que explicar por qué cambió el voto"),
                GameChatMessage("Beto", "Dina tiene que explicar por qué cambió el voto")
            )
        )
        val candidates = listOf(
            "Ciro" to "Bien, Dina tiene que explicar por qué cambió el voto",
            "Ema" to "Mora tiene que explicar por qué cambió el voto"
        )

        assertEquals(listOf(candidates[1]), candidates.dropEchoesOfRecentChat(session))
    }

    @Test
    fun deathCauseDistinguishesNightAndVoteEliminations() {
        val dawn = GameEngine.resolveDawn(
            baseSession().copy(
                phase = GamePhase.AMANECER,
                nightKillTarget = "Aldeano1"
            )
        )
        assertEquals(
            DeathCause.NIGHT,
            GameEngine.playerByName(dawn, "Aldeano1")?.deathCause
        )

        val result = GameEngine.resolveResult(
            baseSession().copy(
                phase = GamePhase.RESULTADO,
                dayEliminationTarget = "Aldeano2"
            )
        )
        assertEquals(
            DeathCause.VOTE,
            GameEngine.playerByName(result, "Aldeano2")?.deathCause
        )
    }

    @Test
    fun assigningRolesClearsPreviousDeathCause() {
        val deadSession = baseSession().copy(
            players = baseSession().players.mapIndexed { index, player ->
                if (index == 1) {
                    player.copy(alive = false, deathCause = DeathCause.NIGHT)
                } else {
                    player
                }
            }
        )

        val assigned = LocalGameFactory.assignRoles(deadSession)

        assertTrue(assigned.players.all { it.alive })
        assertTrue(assigned.players.all { it.deathCause == DeathCause.NONE })
    }

    @Test
    fun afkExpulsionKeepsItsOwnDeathCause() {
        val session = sessionWithHumanRole(RoleCatalog.ASESINO).copy(
            phase = GamePhase.NOCHE_ASESINO,
            afkExpulsionEnabled = true,
            players = sessionWithHumanRole(RoleCatalog.ASESINO).players.map { player ->
                if (player.isHuman) player.copy(consecutiveNightAfk = 1) else player
            }
        )

        val resolved = GameEngine.resolveHumanTimeout(session)
        val human = GameEngine.humanPlayer(resolved)

        assertFalse(human.alive)
        assertEquals(DeathCause.AFK, human.deathCause)
    }

    @Test
    fun onlineNightAfkTracksEveryRequiredPlayerAndExpelsOnSecondMiss() {
        val initial = baseSession().copy(
            afkExpulsionEnabled = true,
            players = baseSession().players.mapIndexed { index, player ->
                if (index == 1) player.copy(consecutiveNightAfk = 1) else player
            }
        )

        val firstWindow = GameEngine.applyOnlineAfkOpportunity(
            session = initial,
            opportunity = AfkOpportunity.NIGHT,
            requiredPlayerIndexes = setOf(1, 2),
            actedPlayerIndexes = setOf(1)
        )

        assertEquals(0, firstWindow.players[1].consecutiveNightAfk)
        assertEquals(1, firstWindow.players[2].consecutiveNightAfk)
        assertTrue(firstWindow.players[2].alive)

        val secondWindow = GameEngine.applyOnlineAfkOpportunity(
            session = firstWindow,
            opportunity = AfkOpportunity.NIGHT,
            requiredPlayerIndexes = setOf(2),
            actedPlayerIndexes = emptySet()
        )

        assertFalse(secondWindow.players[2].alive)
        assertEquals(2, secondWindow.players[2].consecutiveNightAfk)
        assertEquals(DeathCause.AFK, secondWindow.players[2].deathCause)
        assertTrue(secondWindow.publicHistory.last().contains("Policia fue expulsado"))
    }

    @Test
    fun onlineVoteActionResetsOnlyVoteAfkStreak() {
        val initial = baseSession().copy(
            afkExpulsionEnabled = true,
            players = baseSession().players.mapIndexed { index, player ->
                if (index == 0) {
                    player.copy(consecutiveNightAfk = 1, consecutiveVoteAfk = 1)
                } else {
                    player
                }
            }
        )

        val resolved = GameEngine.applyOnlineAfkOpportunity(
            session = initial,
            opportunity = AfkOpportunity.VOTE,
            requiredPlayerIndexes = setOf(0),
            actedPlayerIndexes = setOf(0)
        )

        assertEquals(1, resolved.players[0].consecutiveNightAfk)
        assertEquals(0, resolved.players[0].consecutiveVoteAfk)
        assertTrue(resolved.players[0].alive)
    }

    @Test
    fun onlineAfkAccountingIsIgnoredWhenRuleIsDisabled() {
        val session = baseSession()

        val resolved = GameEngine.applyOnlineAfkOpportunity(
            session = session,
            opportunity = AfkOpportunity.VOTE,
            requiredPlayerIndexes = setOf(0),
            actedPlayerIndexes = emptySet()
        )

        assertEquals(session, resolved)
    }

    @Test
    fun oneLocalNightTimeoutSkipsPendingHumanPowerAndReachesDawn() {
        val session = sessionWithHumanRole(RoleCatalog.POLICIA).copy(
            phase = GamePhase.NOCHE_ASESINO,
            afkExpulsionEnabled = true
        )

        val resolved = GameEngine.resolveLocalNightWindowTimeout(session)
        val human = GameEngine.humanPlayer(resolved)

        assertEquals(GamePhase.AMANECER, resolved.phase)
        assertEquals("", resolved.investigatedPlayer)
        assertEquals(1, human.consecutiveNightAfk)
    }

    @Test
    fun onlineDebateTimeoutDoesNotLetHostAiUseRemotePayador() {
        val session = payadorBotSession()

        val resolved = GameEngine.resolveDayDebateWithoutOptionalBotActions(session)

        assertEquals(GamePhase.VOTACION, resolved.phase)
        assertFalse(resolved.payadorUsed)
        assertTrue(resolved.contrapuntoPlayers.isEmpty())
    }

    @Test
    fun skippedOnlineNightActionsNeverInventBotTargets() {
        var resolved = baseSession().copy(phase = GamePhase.NOCHE_MERCENARIO)

        repeat(4) {
            resolved = GameEngine.skipOnlineNightAction(resolved)
        }

        assertEquals(GamePhase.AMANECER, resolved.phase)
        assertEquals("", resolved.nightSilenceTarget)
        assertEquals("", resolved.investigatedPlayer)
        assertEquals("", resolved.protectedPlayer)
        assertEquals("", resolved.oracleInvitedPlayer)
    }

    @Test
    fun onlineAfkCancelsWithoutWinnerWhenEverybodyMissesAgain() {
        val initial = GameSession(
            code = "AFK",
            mapKey = "pampa",
            mapName = "Pampa",
            afkExpulsionEnabled = true,
            players = listOf(
                GamePlayer(
                    "Aldeano",
                    "A",
                    role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"),
                    consecutiveVoteAfk = 1,
                    isHuman = true
                ),
                GamePlayer(
                    "Asesino",
                    "T",
                    role = role(RoleCatalog.ASESINO, "Asesino", "Traidores"),
                    consecutiveVoteAfk = 1
                )
            )
        )

        val allMiss = GameEngine.applyOnlineAfkOpportunity(
            session = initial,
            opportunity = AfkOpportunity.VOTE,
            requiredPlayerIndexes = setOf(0, 1),
            actedPlayerIndexes = emptySet()
        )

        assertTrue(allMiss.players.all { it.alive })
        assertEquals(GameRules.CANCELLED_WINNER, allMiss.winner)
        assertEquals(GamePhase.RESULTADO, allMiss.phase)
        assertTrue(allMiss.publicAnnouncement.contains("cancelada"))

        val oneReturns = GameEngine.applyOnlineAfkOpportunity(
            session = initial,
            opportunity = AfkOpportunity.VOTE,
            requiredPlayerIndexes = setOf(0, 1),
            actedPlayerIndexes = setOf(0)
        )

        assertTrue(oneReturns.players[0].alive)
        assertFalse(oneReturns.players[1].alive)
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

    private fun payadorBotSession(): GameSession {
        return GameSession(
            code = "PAYADOR",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.DIA_DEBATE,
            round = 1,
            players = listOf(
                GamePlayer("Humano", "H", role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo"), isHuman = true),
                GamePlayer("Payador", "P", role = role(RoleCatalog.PAYADOR, "Payador", "Pueblo")),
                GamePlayer("Beto", "B", role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo")),
                GamePlayer("Ciro", "C", role = role(RoleCatalog.ALDEANO, "Aldeano", "Pueblo")),
                GamePlayer("Dina", "D", role = role(RoleCatalog.ASESINO, "Asesino", "Traidores"))
            )
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

    private fun botLineKey(line: String): String {
        return line.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(42)
    }

    private fun isSelfAccusatoryBotLine(name: String, line: String): Boolean {
        val text = line.lowercase()
        val lowerName = name.lowercase()
        val accusatory = listOf(
            "sospe",
            "raro",
            "rara",
            "miente",
            "voto",
            "culpa",
            "callado",
            "punta con",
            "voy con",
            "mirar fuerte",
            "me hace ruido"
        ).any { text.contains(it) }
        val defensive = listOf("confio", "inocente", "limpio", "defiendo", "creo en").any { text.contains(it) }
        return text.contains(lowerName) && accusatory && !defensive
    }
}
