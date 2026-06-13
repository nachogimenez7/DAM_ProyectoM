package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayTableUiTest {

    @Test
    fun playerInitialUsesValidLetterAndFallsBackToName() {
        assertEquals("M", GameplayTableUi.playerInitial(GamePlayer("Martina", "m")))
        assertEquals("T", GameplayTableUi.playerInitial(GamePlayer("Tomas", "0:450")))
        assertEquals("?", GameplayTableUi.playerInitial(GamePlayer("123", "0:610")))
    }

    @Test
    fun traitorRevealIncludesOnlyExplicitTraitorRoles() {
        val assassin = GameRole("asesino", "Asesino", "Traidores", "rol_asesino_gaucho")
        val mercenary = GameRole("mercenario", "Mercenario", "Traidores", "rol_mercenario_gaucho")
        val spy = GameRole("espia", "Espia", "Traidores", "rol_espia_gaucho")
        val desertor = GameRole("desertor", "Desertora", "Neutral", "rol_desertor_gaucho")
        val villager = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val players = listOf(
            GamePlayer("Humano", "H", role = assassin, isHuman = true),
            GamePlayer("Mercenario", "M", role = mercenary),
            GamePlayer("Espia", "E", role = spy),
            GamePlayer("Desertora", "D", role = desertor),
            GamePlayer("Aldeano", "A", role = villager)
        )
        val session = GameSession("TEST", "pampa", "Pampa", players, desertorTeam = GameRules.TRAITOR_WINNER)

        assertEquals(
            listOf("Mercenario", "Espia"),
            GameplayTableUi.traitorTeammatesForReveal(session).map { it.name }
        )
        assertTrue(GameplayTableUi.shouldShowTraitorReveal(session, completed = false))
        assertFalse(GameplayTableUi.shouldShowTraitorReveal(session, completed = true))
        assertFalse(
            GameplayTableUi.shouldShowTraitorReveal(
                session.copy(phase = GamePhase.NOCHE_ASESINO),
                completed = false
            )
        )
        assertTrue(
            GameplayTableUi.traitorTeammatesForReveal(
                session.copy(players = players.map { it.copy(isHuman = it.name == "Aldeano") })
            ).isEmpty()
        )
        assertTrue(
            GameplayTableUi.traitorTeammatesForReveal(
                session.copy(players = players.filterNot { it.name == "Mercenario" || it.name == "Espia" })
            ).isEmpty()
        )
    }

    @Test
    fun splitCompanionsExcludesHumanAndPutsOddExtraOnRight() {
        val fivePlayers = players(5)
        val eightPlayers = players(8)
        val fifteenPlayers = players(15)

        assertSplit(fivePlayers, expectedLeft = 2, expectedRight = 2)
        assertSplit(eightPlayers, expectedLeft = 3, expectedRight = 4)
        assertSplit(fifteenPlayers, expectedLeft = 7, expectedRight = 7)
    }

    @Test
    fun companionCardMetricsAdaptToPlayersAndAvailableHeight() {
        val five = GameplayTableUi.companionCardMetrics(5, availableHeightDp = 360)
        val eight = GameplayTableUi.companionCardMetrics(8, availableHeightDp = 376)
        val nine = GameplayTableUi.companionCardMetrics(9, availableHeightDp = 376)
        val eightTall = GameplayTableUi.companionCardMetrics(8, availableHeightDp = 420)
        val twelveLow = GameplayTableUi.companionCardMetrics(12, availableHeightDp = 360)
        val fifteen = GameplayTableUi.companionCardMetrics(15, availableHeightDp = 360)

        assertEquals(112, five.columnWidthDp)
        assertEquals(54, five.cardWidthDp)
        assertEquals(86, five.cardHeightDp)
        assertEquals(18, five.nameHeightDp)
        assertEquals(106, five.itemHeightDp)
        assertFalse(five.scrollEnabled)

        assertEquals(94, eight.columnWidthDp)
        assertEquals(16, eight.nameHeightDp)
        assertEquals(eight, nine)
        assertFalse(eight.scrollEnabled)
        assertEquals(eight, eightTall)
        assertEquals(36, eightTall.cardWidthDp)
        assertEquals(58, eightTall.cardHeightDp)

        assertEquals(78, twelveLow.columnWidthDp)
        assertFalse(twelveLow.scrollEnabled)
        assertTrue((twelveLow.itemHeightDp * 6) + (twelveLow.itemGapDp * 5) <= 360)
        assertTrue(twelveLow.cardHeightDp < 46)

        assertEquals(78, fifteen.columnWidthDp)
        assertEquals(29, fifteen.cardWidthDp)
        assertEquals(46, fifteen.cardHeightDp)
        assertEquals(14, fifteen.nameHeightDp)
        assertEquals(62, fifteen.itemHeightDp)
        assertTrue(fifteen.scrollEnabled)
        assertTrue(eight.itemHeightDp < five.itemHeightDp)
    }

    @Test
    fun deathRevealOnlyIncludesNewNightVictims() {
        val role = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val players = listOf(
            GamePlayer("Humano", "H", role = role, isHuman = true),
            GamePlayer("Martina", "M", role = role, alive = false),
            GamePlayer("Tomas", "T", role = role, alive = false)
        )
        val dawn = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players,
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = "Amanecer: murio Martina."
        )

        assertEquals(
            listOf("Martina"),
            GameplayTableUi.newlyKilledAtDawn(dawn, knownDeadPlayers = setOf("Tomas"))
                .map { it.name }
        )
        assertTrue(
            GameplayTableUi.newlyKilledAtDawn(
                dawn.copy(publicAnnouncement = "La mesa expulso a Martina."),
                knownDeadPlayers = setOf("Tomas")
            ).isEmpty()
        )
        assertTrue(
            GameplayTableUi.newlyKilledAtDawn(
                dawn,
                knownDeadPlayers = setOf("Martina", "Tomas")
            ).isEmpty()
        )
    }

    @Test
    fun silenceRevealOnlyIncludesNewLivingMutedPlayer() {
        val role = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val players = listOf(
            GamePlayer("Humano", "H", role = role, isHuman = true),
            GamePlayer("Martina", "M", role = role, muted = true),
            GamePlayer("Tomas", "T", role = role, alive = false, muted = true)
        )
        val dawn = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players,
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = "Amanecer: no murio nadie. Martina no puede hablar ni votar hoy."
        )

        assertEquals(
            listOf("Martina"),
            GameplayTableUi.newlySilencedAtDawn(dawn, knownMutedPlayers = emptySet())
                .map { it.name }
        )
        assertTrue(
            GameplayTableUi.newlySilencedAtDawn(
                dawn,
                knownMutedPlayers = setOf("Martina")
            ).isEmpty()
        )
        assertTrue(
            GameplayTableUi.newlySilencedAtDawn(
                dawn.copy(publicAnnouncement = "Martina fue acusada durante el debate."),
                knownMutedPlayers = emptySet()
            ).isEmpty()
        )
    }

    @Test
    fun townWinnerPresentationIncludesTownAndTownDesertorEvenWhenDead() {
        val town = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val assassin = GameRole("asesino", "Asesino", "Traidores", "rol_asesino_gaucho")
        val desertor = GameRole("desertor", "Desertora", "Neutral", "rol_desertor_gaucho")
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = town, alive = false, isHuman = true),
                GamePlayer("Pueblo vivo", "P", role = town),
                GamePlayer("Asesino", "A", role = assassin),
                GamePlayer("Desertora", "D", role = desertor, alive = false)
            ),
            desertorTeam = GameRules.TOWN_WINNER,
            winner = GameRules.TOWN_WINNER
        )

        val presentation = GameplayTableUi.winnerPresentation(session)

        assertEquals(
            listOf("Humano", "Pueblo vivo", "Desertora"),
            presentation.winningPlayers.map { it.name }
        )
        assertTrue(presentation.humanWon)
        assertTrue(presentation.winningPlayers.any { !it.alive })
    }

    @Test
    fun traitorWinnerPresentationIncludesExplicitTraitorsAndTraitorDesertor() {
        val town = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val assassin = GameRole("asesino", "Asesino", "Traidores", "rol_asesino_gaucho")
        val mercenary = GameRole("mercenario", "Mercenario", "Traidores", "rol_mercenario_gaucho")
        val spy = GameRole("espia", "Espia", "Traidores", "rol_espia_gaucho")
        val desertor = GameRole("desertor", "Desertora", "Neutral", "rol_desertor_gaucho")
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = town, isHuman = true),
                GamePlayer("Asesino", "A", role = assassin),
                GamePlayer("Mercenario", "M", role = mercenary, alive = false),
                GamePlayer("Espia", "E", role = spy),
                GamePlayer("Desertora", "D", role = desertor)
            ),
            desertorTeam = GameRules.TRAITOR_WINNER,
            winner = GameRules.TRAITOR_WINNER
        )

        val presentation = GameplayTableUi.winnerPresentation(session)

        assertEquals(
            listOf("Asesino", "Mercenario", "Espia", "Desertora"),
            presentation.winningPlayers.map { it.name }
        )
        assertFalse(presentation.humanWon)
        assertTrue(presentation.winningPlayers.any { !it.alive })
    }

    @Test
    fun finalPresentationAlsoIncludesEarlierSpecialWinners() {
        val town = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_medieval")
        val assassin = GameRole("asesino", "Asesino", "Traidores", "rol_asesino_medieval")
        val jester = GameRole("bufon", "Bufon", "Neutral", "rol_bufon_medieval")
        val session = GameSession(
            code = "TEST",
            mapKey = "medieval",
            mapName = "Medieval",
            players = listOf(
                GamePlayer("Pueblo", "P", role = town),
                GamePlayer("Traidor", "T", role = assassin),
                GamePlayer("Bufon", "B", role = jester, alive = false, isHuman = true)
            ),
            specialVictories = listOf(
                GameSpecialVictory(
                    key = "bufon_expulsado",
                    playerName = "Bufon",
                    roleKey = "bufon",
                    round = 2
                )
            ),
            winner = GameRules.TOWN_WINNER
        )

        val presentation = GameplayTableUi.winnerPresentation(session)

        assertEquals(listOf("Pueblo", "Bufon"), presentation.winningPlayers.map { it.name })
        assertEquals("Bufon", presentation.specialVictories.single().playerName)
        assertTrue(presentation.humanWon)
    }

    @Test
    fun winnerPresentationIsEmptyBeforeGameEnds() {
        val presentation = GameplayTableUi.winnerPresentation(
            transitionSession(GamePhase.RESULTADO, round = 2)
        )

        assertTrue(presentation.winningPlayers.isEmpty())
        assertFalse(presentation.humanWon)
    }

    @Test
    fun gameSummaryIncludesDurationPlayersRoundsAndHumanRoleHighlight() {
        val medic = actionSession("medico", GamePhase.NOCHE_MEDICO).copy(
            round = 4,
            initialPlayerCount = 5,
            startedAtEpochMs = 1_000L,
            players = actionSession("medico", GamePhase.NOCHE_MEDICO).players.mapIndexed { index, player ->
                if (index == 1) player.copy(alive = false) else player
            },
            actionHistory = listOf(
                GameAction(
                    GameActionType.PROTECT,
                    "Humano",
                    "Objetivo",
                    1,
                    GamePhase.NOCHE_MEDICO
                ),
                GameAction(
                    GameActionType.PROTECT,
                    "Humano",
                    "Humano",
                    2,
                    GamePhase.NOCHE_MEDICO
                )
            ),
            godHistory = listOf(
                "Noche 1: todos guardan silencio.",
                "Amanecer: murio Objetivo. Rival no puede hablar ni votar hoy.",
                "Noche 2: todos guardan silencio.",
                "Amanecer: no murio nadie."
            )
        )

        val summary = GameplayTableUi.gameSummary(medic, nowEpochMs = 126_000L)

        assertEquals(4, summary.roundsPlayed)
        assertEquals("02:05", summary.durationLabel)
        assertEquals(1, summary.survivors)
        assertEquals(4, summary.eliminated)
        assertEquals(listOf("Objetivo (Aldeano)"), summary.eliminatedPlayers)
        assertEquals("2 protecciones", summary.humanHighlight)
        assertEquals(
            listOf(
                "Día 1: murió Objetivo y se silenció a Rival.",
                "Día 2: no murió nadie y nadie fue silenciado.",
                "Día 3: no murió nadie y nadie fue silenciado.",
                "Día 4: no murió nadie y nadie fue silenciado."
            ),
            summary.daySummaries
        )
    }

    @Test
    fun gameSummaryUsesRoleSpecificHumanHighlights() {
        val assassin = actionSession("asesino", GamePhase.RESULTADO).copy(
            actionHistory = listOf(
                GameAction(
                    GameActionType.KILL,
                    "Humano",
                    "Objetivo",
                    1,
                    GamePhase.NOCHE_ASESINO
                )
            )
        )
        val detective = actionSession("policia", GamePhase.RESULTADO).copy(
            actionHistory = listOf(
                GameAction(
                    GameActionType.INVESTIGATE,
                    "Humano",
                    "Objetivo",
                    1,
                    GamePhase.NOCHE_POLICIA
                )
            )
        )
        val desertor = actionSession("desertor", GamePhase.RESULTADO)
            .copy(desertorTeam = GameRules.TOWN_WINNER)

        assertEquals(
            "1 ataques elegidos",
            GameplayTableUi.gameSummary(assassin, assassin.startedAtEpochMs).humanHighlight
        )
        assertEquals(
            "1 investigaciones",
            GameplayTableUi.gameSummary(detective, detective.startedAtEpochMs).humanHighlight
        )
        assertEquals(
            "Bando final: Pueblo",
            GameplayTableUi.gameSummary(desertor, desertor.startedAtEpochMs).humanHighlight
        )
    }

    @Test
    fun mapKeysResolveToGameplayThemes() {
        assertEquals("gaucho", GameplayTableUi.themeForMapKey("pampa"))
        assertEquals("griego", GameplayTableUi.themeForMapKey("grecia"))
        assertEquals("medieval", GameplayTableUi.themeForMapKey("medieval"))
        assertEquals("gaucho", GameplayTableUi.themeForMapKey("desconocido"))
    }

    @Test
    fun onlyNightRolePhasesUseNightBackground() {
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_ASESINO))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_MERCENARIO))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_POLICIA))
        assertTrue(GameplayTableUi.isNightPhase(GamePhase.NOCHE_MEDICO))

        assertFalse(GameplayTableUi.isNightPhase(GamePhase.REPARTO))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.AMANECER))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.DIA_DEBATE))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.VOTACION))
        assertFalse(GameplayTableUi.isNightPhase(GamePhase.RESULTADO))
    }

    @Test
    fun transitionTitlesUseVisualDayAndNightSequence() {
        val base = transitionSession(GamePhase.REPARTO, round = 1)

        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.DAY, "DÍA 1", "DAY_1"),
            GameplayTableUi.transitionSpec(base)
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.NIGHT, "NOCHE 1", "NIGHT_1"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.NOCHE_ASESINO))
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.DAY, "DÍA 2", "DAY_2"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.AMANECER))
        )
        assertEquals(
            GameplayTransitionSpec(GameplayPeriod.NIGHT, "NOCHE 2", "NIGHT_2"),
            GameplayTableUi.transitionSpec(base.copy(phase = GamePhase.NOCHE_MEDICO, round = 2))
        )
    }

    @Test
    fun subphasesInSamePeriodDoNotRepeatTransition() {
        val nightSpecs = listOf(
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO
        ).map { phase ->
            GameplayTableUi.transitionSpec(transitionSession(phase, round = 1))
        }
        val daySpecs = listOf(
            GamePhase.AMANECER,
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO,
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE,
            GamePhase.RESULTADO
        ).map { phase ->
            GameplayTableUi.transitionSpec(transitionSession(phase, round = 1))
        }

        assertEquals(setOf("NIGHT_1"), nightSpecs.map { it.key }.toSet())
        assertEquals(setOf("DAY_2"), daySpecs.map { it.key }.toSet())
        assertTrue(GameplayTableUi.shouldPresentTransition(nightSpecs.first(), null))
        nightSpecs.drop(1).forEach { spec ->
            assertFalse(GameplayTableUi.shouldPresentTransition(spec, nightSpecs.first().key))
        }
        assertTrue(GameplayTableUi.shouldPresentTransition(daySpecs.first(), nightSpecs.first().key))
        daySpecs.drop(1).forEach { spec ->
            assertFalse(GameplayTableUi.shouldPresentTransition(spec, daySpecs.first().key))
        }
    }

    @Test
    fun selfProtectionIsOfferedOnlyToActiveHumanMedicOnMedicTurn() {
        val medicRole = GameRole("medico", "Medico", "Pueblo", "rol_medico_gaucho")
        val villagerRole = GameRole("aldeano", "Aldeano", "Pueblo", "rol_aldeano_gaucho")
        val session = GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.NOCHE_MEDICO,
            players = listOf(
                GamePlayer("Humano", "H", role = medicRole, isHuman = true),
                GamePlayer("Bot", "B", role = villagerRole)
            )
        )

        assertTrue(GameplayTableUi.canHumanMedicSelfProtect(session))
        assertFalse(
            GameplayTableUi.canHumanMedicSelfProtect(
                session.copy(phase = GamePhase.NOCHE_POLICIA)
            )
        )
        assertFalse(
            GameplayTableUi.canHumanMedicSelfProtect(
                session.copy(
                    players = session.players.map {
                        if (it.isHuman) it.copy(alive = false, muted = true) else it
                    }
                )
            )
        )
    }

    @Test
    fun publicEventTypeDetectsDeathVotingDiscussionAndPhaseStart() {
        assertEquals(
            PublicEventType.DEATH,
            GameplayTableUi.eventTypeFor("Amanecer: murio Tomas.", GamePhase.AMANECER)
        )
        assertEquals(
            PublicEventType.VOTING,
            GameplayTableUi.eventTypeFor("Dios cerro la votacion. Se resolvera la expulsion.", GamePhase.VOTACION)
        )
        assertEquals(
            PublicEventType.DISCUSSION,
            GameplayTableUi.eventTypeFor("Dia 1: debatan. Muteados: Tomas.", GamePhase.DIA_DEBATE)
        )
        assertEquals(
            PublicEventType.PHASE_START,
            GameplayTableUi.eventTypeFor("Noche 1: todos guardan silencio.", GamePhase.NOCHE_ASESINO)
        )
        assertEquals(
            PublicEventType.PHASE_START,
            GameplayTableUi.eventTypeFor("Amanecer: no murio nadie.", GamePhase.AMANECER)
        )
    }

    @Test
    fun targetActionsRequireAValidSelectionAndUseSemanticTones() {
        val assassin = actionSession("asesino", GamePhase.NOCHE_ASESINO)
        val mercenary = actionSession("mercenario", GamePhase.NOCHE_MERCENARIO)
        val detective = actionSession("policia", GamePhase.NOCHE_POLICIA)
        val medic = actionSession("medico", GamePhase.NOCHE_MEDICO)
        val voting = actionSession("aldeano", GamePhase.VOTACION)
        val payador = actionSession("payador", GamePhase.DIA_DEBATE)
        val mayor = actionSession("alcalde", GamePhase.ALCALDE_DESEMPATE).copy(
            alcaldeRevealed = true,
            alcaldeTieCandidates = listOf("Objetivo")
        )

        assertEquals(null, GameplayTableUi.confirmedTargetActionLabel(assassin, ""))
        assertEquals("MATAR", GameplayTableUi.confirmedTargetActionLabel(assassin, "Objetivo"))
        assertEquals("SILENCIAR", GameplayTableUi.confirmedTargetActionLabel(mercenary, "Objetivo"))
        assertEquals("INVESTIGAR", GameplayTableUi.confirmedTargetActionLabel(detective, "Objetivo"))
        assertEquals("SALVAR", GameplayTableUi.confirmedTargetActionLabel(medic, "Objetivo"))
        assertEquals("VOTAR", GameplayTableUi.confirmedTargetActionLabel(voting, "Objetivo"))
        assertEquals("SENALAR", GameplayTableUi.confirmedTargetActionLabel(payador, "Objetivo"))
        assertEquals("DECIDIR", GameplayTableUi.confirmedTargetActionLabel(mayor, "Objetivo"))
        assertEquals(listOf("Objetivo"), GameplayTableUi.validHumanTargets(assassin).map { it.name })

        assertEquals(GameplayActionTone.KILL, GameplayTableUi.actionToneFor("MATAR"))
        assertEquals(GameplayActionTone.SAVE, GameplayTableUi.actionToneFor("SALVARME"))
        assertEquals(GameplayActionTone.INVESTIGATE, GameplayTableUi.actionToneFor("INVESTIGAR"))
        assertEquals(GameplayActionTone.SILENCE, GameplayTableUi.actionToneFor("SILENCIAR"))
        assertEquals(GameplayActionTone.DECIDE, GameplayTableUi.actionToneFor("VOTAR"))
        assertEquals(GameplayActionTone.DECIDE, GameplayTableUi.actionToneFor("REVELARME"))
        assertEquals(GameplayActionTone.DECIDE, GameplayTableUi.actionToneFor("ELEGIR BANDO"))
        assertEquals(GameplayActionTone.DEFAULT, GameplayTableUi.actionToneFor("ACELERAR"))
    }

    @Test
    fun publicEventsDeduplicateAndKeepCurrentModeratorMessageOutOfTheSummary() {
        val history = listOf(
            "La partida comenzo.",
            "Noche 1: todos guardan silencio.",
            "Noche 1: todos guardan silencio."
        )

        assertEquals(
            listOf("La partida comenzo.", "Noche 1: todos guardan silencio."),
            GameplayTableUi.publicEvents(
                history,
                "Noche 1: todos guardan silencio.",
                "Sin eventos."
            )
        )
        assertEquals(
            listOf("La partida comenzo."),
            GameplayTableUi.historicalPublicEvents(
                history,
                "Noche 1: todos guardan silencio.",
                "Sin eventos."
            )
        )
        assertEquals(
            listOf("Evento repetido.", "Otro evento."),
            GameplayTableUi.historicalPublicEvents(
                listOf("Evento repetido.", "Otro evento.", "Evento repetido."),
                "Evento repetido.",
                "Sin eventos."
            )
        )
        assertEquals(
            listOf("Sin eventos."),
            GameplayTableUi.historicalPublicEvents(emptyList(), "", "Sin eventos.")
        )
    }

    @Test
    fun nightActionsProduceBlockingPrivateFeedbackWithoutRoleLeaks() {
        val cases = listOf(
            actionSession("asesino", GamePhase.NOCHE_ASESINO) to GameplayActionTone.KILL,
            actionSession("mercenario", GamePhase.NOCHE_MERCENARIO) to GameplayActionTone.SILENCE,
            actionSession("policia", GamePhase.NOCHE_POLICIA) to GameplayActionTone.INVESTIGATE,
            actionSession("medico", GamePhase.NOCHE_MEDICO) to GameplayActionTone.SAVE
        )

        cases.forEach { (before, tone) ->
            val after = GameEngine.resolveHumanTargetAction(before, "Objetivo")
            val feedback = GameplayTableUi.feedbackForResolvedAction(before, after, "Objetivo")

            assertEquals(GameplayFeedbackType.PRIVATE_RESULT, feedback?.type)
            assertEquals(2000L, feedback?.durationMs)
            assertEquals(tone, feedback?.tone)
            assertEquals("Objetivo", feedback?.target)
            assertFalse(feedback?.message.orEmpty().contains("Aldeano", ignoreCase = true))
            assertFalse(feedback?.message.orEmpty().contains("Pueblo", ignoreCase = true))
        }
    }

    @Test
    fun medicSelfProtectionAndDetectiveResultUseExpectedPrivateCopy() {
        val medic = actionSession("medico", GamePhase.NOCHE_MEDICO)
        val medicAfter = GameEngine.resolveHumanTargetAction(medic, "Humano")
        val medicFeedback = GameplayTableUi.feedbackForResolvedAction(
            medic,
            medicAfter,
            "Humano"
        )
        assertEquals("Te protegiste durante esta noche.", medicFeedback?.message)

        val detective = actionSession("policia", GamePhase.NOCHE_POLICIA)
        val detectiveAfter = GameEngine.resolveHumanTargetAction(detective, "Objetivo")
        val detectiveFeedback = GameplayTableUi.feedbackForResolvedAction(
            detective,
            detectiveAfter,
            "Objetivo"
        )
        assertTrue(detectiveFeedback?.message.orEmpty().contains("INOCENTE"))
    }

    @Test
    fun dayActionsProduceNonBlockingConfirmationFeedback() {
        val voting = actionSession("aldeano", GamePhase.VOTACION)
        val voteAfter = GameEngine.resolveHumanTargetAction(voting, "Objetivo")
        val vote = GameplayTableUi.feedbackForResolvedAction(voting, voteAfter, "Objetivo")

        val payador = actionSession("payador", GamePhase.DIA_DEBATE)
        val payadorAfter = GameEngine.resolveHumanTargetAction(payador, "Objetivo")
        val contrapunto = GameplayTableUi.feedbackForResolvedAction(
            payador,
            payadorAfter,
            "Objetivo"
        )
        val payadorFinal = payador.copy(
            phase = GamePhase.CONTRAPUNTO,
            contrapuntoPlayers = listOf("Objetivo")
        )
        val payadorFinalAfter = GameEngine.resolveHumanTargetAction(payadorFinal, "Objetivo")
        val suspicion = GameplayTableUi.feedbackForResolvedAction(
            payadorFinal,
            payadorFinalAfter,
            "Objetivo"
        )

        val mayor = actionSession("alcalde", GamePhase.ALCALDE_DESEMPATE).copy(
            alcaldeRevealed = true,
            alcaldeTieCandidates = listOf("Objetivo")
        )
        val mayorAfter = GameEngine.resolveHumanTargetAction(mayor, "Objetivo")
        val decision = GameplayTableUi.feedbackForResolvedAction(mayor, mayorAfter, "Objetivo")

        listOf(vote, contrapunto, suspicion, decision).forEach { feedback ->
            assertEquals(GameplayFeedbackType.ACTION_CONFIRMATION, feedback?.type)
            assertEquals(1200L, feedback?.durationMs)
            assertFalse(feedback?.blocksGameplay ?: true)
        }
        assertEquals("SENALAMIENTO", suspicion?.title)
    }

    @Test
    fun mayorDesertorAndPersonalStatusesHaveCompactPresentation() {
        val mayor = actionSession("alcalde", GamePhase.DIA_DEBATE)
        val revealedMayor = mayor.copy(alcaldeRevealed = true)
        assertEquals(
            "ALCALDE REVELADO",
            GameplayTableUi.feedbackForMayorReveal(mayor, revealedMayor)?.title
        )
        assertEquals(
            "BANDO ELEGIDO",
            GameplayTableUi.feedbackForDesertorChoice(GameRules.TOWN_WINNER, false).title
        )
        assertEquals(
            "BANDO ACTUALIZADO",
            GameplayTableUi.feedbackForDesertorChoice(GameRules.TRAITOR_WINNER, true).title
        )

        val base = actionSession("medico", GamePhase.NOCHE_MEDICO)
        assertEquals(null, GameplayTableUi.personalStatus(base))
        assertEquals(
            "PROTEGIDO",
            GameplayTableUi.personalStatus(base.copy(protectedPlayer = "Humano"))
        )
        assertEquals(
            "SILENCIADO",
            GameplayTableUi.personalStatus(
                base.copy(players = base.players.map {
                    if (it.isHuman) it.copy(muted = true) else it
                })
            )
        )
        assertEquals(
            "ELIMINADO",
            GameplayTableUi.personalStatus(
                base.copy(players = base.players.map {
                    if (it.isHuman) it.copy(alive = false, muted = true) else it
                })
            )
        )
    }

    private fun assertSplit(players: List<GamePlayer>, expectedLeft: Int, expectedRight: Int) {
        val (left, right) = GameplayTableUi.splitCompanions(players)

        assertEquals(expectedLeft, left.size)
        assertEquals(expectedRight, right.size)
        assertTrue((left + right).none { it.isHuman })
        assertEquals(players.drop(1), left + right)
    }

    private fun players(count: Int): List<GamePlayer> {
        return List(count) { index ->
            GamePlayer(
                name = if (index == 0) "Humano" else "Jugador$index",
                initial = index.toString(),
                isHuman = index == 0
            )
        }
    }

    private fun transitionSession(phase: GamePhase, round: Int): GameSession {
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players(5),
            phase = phase,
            round = round
        )
    }

    private fun actionSession(roleKey: String, phase: GamePhase): GameSession {
        val team = if (roleKey in GameRules.traitorRoleKeys) {
            GameRules.TRAITOR_WINNER
        } else {
            GameRules.TOWN_WINNER
        }
        val humanRole = GameRole(roleKey, roleKey, team, "rol_${roleKey}_gaucho")
        val villager = GameRole("aldeano", "Aldeano", GameRules.TOWN_WINNER, "rol_aldeano_gaucho")
        return GameSession(
            code = "TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(
                GamePlayer("Humano", "H", role = humanRole, isHuman = true),
                GamePlayer("Objetivo", "O", role = villager)
            ),
            phase = phase
        )
    }
}
