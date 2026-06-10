package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

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
        assertTrue(victim.muted)
        assertEquals("Amanecer: murio Policia. Policia queda muteado.", dawn.publicAnnouncement)
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
        assertTrue(dawn.publicAnnouncement.contains("Policia no puede hablar ni votar hoy."))
        assertFalse(GameEngine.playerByName(nextRound, "Policia")!!.muted)
        assertEquals(GamePhase.NOCHE_ASESINO, nextRound.phase)
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
    fun mutedPlayersDoNotVoteOrAct() {
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
    fun mutedHumanCanReadButCannotWriteChat() {
        val session = baseSession().copy(
            phase = GamePhase.DIA_DEBATE,
            chatHistory = listOf(GameChatMessage("Mateo", "Mensaje visible.")),
            players = basePlayers().map {
                if (it.isHuman) it.copy(alive = false, muted = true) else it
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
        assertTrue(GameEngine.playerByName(resolved, "Asesino")!!.muted)
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
    fun townMustEliminateEveryTraitorRoleToWin() {
        val players = advancedPlayers()
        val assassinOnlyDead = players.map {
            if (it.role?.key == "asesino") it.copy(alive = false, muted = true) else it
        }
        val allTraitorsDead = players.map {
            if (it.role?.key in GameRules.traitorRoleKeys) {
                it.copy(alive = false, muted = true)
            } else {
                it
            }
        }

        assertEquals("", GameRules.winnerFor(assassinOnlyDead))
        assertEquals("Pueblo", GameRules.winnerFor(allTraitorsDead))
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
