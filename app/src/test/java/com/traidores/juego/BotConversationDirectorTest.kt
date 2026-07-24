package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotConversationDirectorTest {
    @Test
    fun mentionedBotGetsNextIdleBeat() {
        val session = session().copy(
            chatHistory = listOf(
                GameChatMessage("Dina", "Beto, vos que viste anoche?")
            )
        )

        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = 0,
            lastSpeaker = "Dina",
            humanSpokeThisPhase = false,
            promptedSilentHuman = true
        )

        assertEquals("Beto", beat?.speaker)
        assertTrue(beat?.message.orEmpty().isNotBlank())
    }

    @Test
    fun idleBeatAvoidsSameSpeakerWhenThereAreAlternatives() {
        val session = session()

        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = 0,
            lastSpeaker = "Beto",
            humanSpokeThisPhase = true,
            promptedSilentHuman = true
        )

        assertNotEquals("Beto", beat?.speaker)
        assertTrue(beat?.message.orEmpty().isNotBlank())
    }

    @Test
    fun exhaustedBudgetProducesNoIdleBeat() {
        val session = session()
        val budget = BotConversationDirector.idleBudget(session)

        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = budget,
            lastSpeaker = null,
            humanSpokeThisPhase = true,
            promptedSilentHuman = true
        )

        assertNull(beat)
    }

    @Test
    fun oracleGuestGetsFirstDebateBeatEvenWhenDead() {
        val session = session().copy(
            oracleInvitedPlayer = "Mora",
            players = session().players.map { player ->
                if (player.name == "Mora") player.copy(alive = false) else player
            }
        )

        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = 0,
            lastSpeaker = null,
            humanSpokeThisPhase = false,
            promptedSilentHuman = false
        )

        assertEquals("Mora", beat?.speaker)
        assertTrue(beat?.message.orEmpty().isNotBlank())
    }

    @Test
    fun humanReactionDoesNotRepeatLastSpeaker() {
        val session = session().copy(
            chatHistory = listOf(
                GameChatMessage("Humano", "Mora me hace ruido")
            )
        )

        val beat = BotConversationDirector.nextHumanReactionBeat(
            session = session,
            humanMessage = "Mora me hace ruido",
            deliveredReactions = 0,
            lastSpeaker = "Beto"
        )

        assertNotEquals("Beto", beat?.speaker)
    }

    @Test
    fun directlyAddressedBotAnswersFirst() {
        val beat = BotConversationDirector.nextHumanReactionBeat(
            session = session(),
            humanMessage = "Mora, que pensas de Beto?",
            deliveredReactions = 0,
            lastSpeaker = "Mora"
        )

        assertEquals("Mora", beat?.speaker)
        assertTrue(beat?.message.orEmpty().contains("Beto"))
    }

    @Test
    fun directlyAskedTraitorClaimsOneRoleAndKeepsTheSameStory() {
        val firstQuestion = GameEngine.addHumanChatMessage(
            session(),
            "Mora, que rol sos?",
            includeBotReactions = false
        )
        assertEquals(1, LocalBotAi.reactionsToHumanMessage(firstQuestion, "Mora, que rol sos?").size)
        val firstBeat = BotConversationDirector.nextHumanReactionBeat(
            session = firstQuestion,
            humanMessage = "Mora, que rol sos?",
            deliveredReactions = 0,
            lastSpeaker = "Mora"
        )
        val firstClaim = LocalBotAi.roleClaimFrom(firstBeat?.message.orEmpty())

        assertEquals("Mora", firstBeat?.speaker)
        assertNotNull(firstClaim)

        val afterClaim = GameEngine.addBotChatMessage(firstQuestion, "Mora", firstBeat!!.message)
        val secondQuestion = GameEngine.addHumanChatMessage(
            afterClaim,
            "Mora, que rol sos?",
            includeBotReactions = false
        )
        val secondBeat = BotConversationDirector.nextHumanReactionBeat(
            session = secondQuestion,
            humanMessage = "Mora, que rol sos?",
            deliveredReactions = 0,
            lastSpeaker = "Mora"
        )
        val secondClaim = LocalBotAi.roleClaimFrom(secondBeat?.message.orEmpty())

        assertEquals("Mora", secondBeat?.speaker)
        assertEquals(firstClaim?.roleKey, secondClaim?.roleKey)
    }

    @Test
    fun directRoleAnswerCanBeSentAsARealTwoMessageTurn() {
        val beat = BotConversationDirector.nextHumanReactionBeat(
            session = session(),
            humanMessage = "Mora, que rol sos?",
            deliveredReactions = 0,
            lastSpeaker = null
        )

        assertEquals("Mora", beat?.speaker)
        assertEquals(2, beat?.messageCount)
        assertEquals(1, beat?.followUps?.size)
        assertTrue(beat?.followUps?.firstOrNull().orEmpty().contains("pregunt"))
    }

    @Test
    fun groundedExplanationCanUseAThreeMessageTurn() {
        val withPrivateRead = session().copy(
            tableMemory = TableMemory(
                privateInvestigationReads = listOf(
                    InvestigationRead(
                        round = 1,
                        source = "Beto",
                        target = "Mora",
                        result = "sospechoso"
                    )
                )
            )
        )
        val followUps = BotMessageBursts.afterHumanReply(
            session = withPrivateRead,
            speaker = "Beto",
            humanMessage = "Beto, por que lo acusas a Mora?",
            primaryMessage = "Mora me hace ruido"
        )

        assertEquals(2, followUps.size)
        assertTrue(followUps.first().contains("nada") || followUps.first().contains("pista"))
    }

    @Test
    fun repetitionAloneDoesNotBecomeGroundedEvidence() {
        val mora = GameEngine.playerByName(session(), "Mora")!!

        assertFalse(
            hasGroundedSuspicion(
                SuspectRead(
                    player = mora,
                    score = 20,
                    reasons = listOf("lo nombraron en el pueblo", "le pidieron explicaciones")
                )
            )
        )
        assertTrue(
            hasGroundedSuspicion(
                SuspectRead(
                    player = mora,
                    score = 12,
                    reasons = listOf("se contradijo de rol")
                )
            )
        )
    }

    @Test
    fun emptyTableStartsWithQuestionsInsteadOfUnsupportedPushes() {
        val objectives = session().players
            .filterNot { it.isHuman }
            .map { bot -> roundObjectiveFor(session(), bot).type }

        assertFalse(objectives.contains(RoundObjectiveType.PUSH_VOTE))
        assertFalse(objectives.contains(RoundObjectiveType.DEFLECT_PRESSURE))
        assertTrue(objectives.contains(RoundObjectiveType.ASK_PLAYER))
    }

    @Test
    fun investigationStageOpensEarlierAtASmallTable() {
        val smallTable = session().copy(
            players = session().players.map { player ->
                if (player.name == "Valen") player.copy(alive = false) else player
            }
        )
        assertEquals(2, investigationSpeakerThreshold(smallTable))
        assertTrue(isOpeningInvestigationStage(smallTable))

        val afterTwoBotTurns = smallTable.copy(
            chatHistory = listOf(
                GameChatMessage("Dios", "Comienza el debate", isGod = true),
                GameChatMessage("Beto", "Mora, que viste?"),
                GameChatMessage("Dina", "primero ordenemos las versiones")
            )
        )

        assertFalse(isOpeningInvestigationStage(afterTwoBotTurns))
    }

    @Test
    fun groundedSuspicionWaitsUntilTheInvestigationStageIsComplete() {
        val smallTable = session().copy(
            players = session().players.map { player ->
                if (player.name == "Valen") player.copy(alive = false) else player
            },
            tableMemory = TableMemory(
                privateInvestigationReads = listOf(
                    InvestigationRead(1, "Beto", "Mora", "sospechoso")
                )
            )
        )
        val beto = GameEngine.playerByName(smallTable, "Beto")!!
        val earlyRead = rankedPublicSuspects(smallTable, beto)
            .first { it.player.name == "Mora" }

        assertTrue(hasGroundedSuspicion(earlyRead))
        assertFalse(canVoiceStrongAccusation(smallTable, earlyRead))

        val afterInvestigation = smallTable.copy(
            chatHistory = listOf(
                GameChatMessage("Beto", "Mora, que hiciste?"),
                GameChatMessage("Dina", "quiero escuchar esa respuesta")
            )
        )
        val laterRead = rankedPublicSuspects(afterInvestigation, beto)
            .first { it.player.name == "Mora" }

        assertTrue(canVoiceStrongAccusation(afterInvestigation, laterRead))
    }

    @Test
    fun burstDelayIsShorterThanAFullThinkingPauseRange() {
        val delay = BotConversationDirector.burstDelayMs(
            session = session(),
            beatIndex = 1,
            speaker = "Beto",
            message = "y otra cosa"
        )

        assertTrue(delay in 600L..1_450L)
    }

    @Test
    fun ordinaryThinkingDelayFeelsResponsiveWithoutAppearingInstantly() {
        val delays = (0..12).map { beatIndex ->
            BotConversationDirector.naturalDelayMs(
                session = session(),
                beatIndex = beatIndex,
                message = "ok, te escucho",
                reaction = true,
                speaker = "Beto"
            )
        }

        assertTrue(delays.all { it in 1_400L..3_600L })
    }

    @Test
    fun silentHumanPromptDoesNotFireFromPreviousPhaseBotStreak() {
        val session = session().copy(
            chatHistory = listOf(
                GameChatMessage("Humano", "soy aldeano"),
                GameChatMessage("Beto", "ok aldeano, dame lectura"),
                GameChatMessage("Dina", "tira una sospecha concreta"),
                GameChatMessage("Valen", "ordenemos antes de votar")
            )
        )

        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = 0,
            lastSpeaker = "Valen",
            humanSpokeThisPhase = false,
            promptedSilentHuman = false
        )

        assertFalse(beat?.promptsSilentHuman ?: false)
        assertFalse(beat?.message.orEmpty().contains("callado", ignoreCase = true))
    }

    @Test
    fun villagerClaimAsksForReadsNotNightAction() {
        val session = session()
        val bot = GameEngine.playerByName(session, "Beto")!!
        val line = roleClaimReaction(
            session = session,
            bot = bot,
            claim = RoleClaim(RoleCatalog.ALDEANO, "aldeano"),
            claimResponder = null,
            index = 0
        ).orEmpty()

        assertTrue(line.contains("miras") || line.contains("sospecha") || line.contains("lectura"))
        assertFalse(line.contains("hiciste", ignoreCase = true))
        assertEquals("a quien miras y por que", claimFollowUp(RoleCatalog.ALDEANO))
    }

    @Test
    fun villagerClaimIsNotTreatedAsExclusiveCounterClaim() {
        val session = session().copy(
            claimLedger = mapOf(
                "Humano" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        roleKey = RoleCatalog.ALDEANO
                    )
                ),
                "Valen" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        roleKey = RoleCatalog.ALDEANO
                    )
                )
            )
        )

        assertNull(botWithRole(session, RoleCatalog.ALDEANO))
        assertTrue(publicClaimants(session, RoleCatalog.ALDEANO).isEmpty())
    }

    @Test
    fun pastDetectiveReadCanBeCitedInLaterRound() {
        val session = session().copy(
            round = 2,
            tableMemory = TableMemory(
                declaredInvestigationReads = listOf(
                    InvestigationRead(
                        round = 1,
                        source = "Humano",
                        target = "Mora",
                        result = "sospechoso"
                    )
                )
            )
        )
        val bot = GameEngine.playerByName(session, "Beto")!!

        val line = (0..2).mapNotNull { index -> pastRoundThreadLine(session, bot, index) }.firstOrNull()

        assertTrue(line.orEmpty().contains("Mora"))
        assertTrue(line.orEmpty().contains("marcado") || line.orEmpty().contains("hilo"))
    }

    @Test
    fun eliminationLastWordsAreAddedOnlyOnce() {
        val session = session().copy(
            phase = GamePhase.RECUENTO_VOTOS,
            dayEliminationTarget = "Mora",
            voteRound = 1
        )

        val once = GameEngine.addEliminationLastWords(session)
        val twice = GameEngine.addEliminationLastWords(once)

        assertEquals(session.chatHistory.size + 1, once.chatHistory.size)
        assertEquals(once.chatHistory.size, twice.chatHistory.size)
        assertEquals("Mora", once.chatHistory.last().speaker)
    }

    @Test
    fun personalitySignatureAddsRecognizableFiller() {
        assertEquals(
            "mmm, no me cierra",
            applyPersonalitySignature("no me cierra", BotPersonality.DESCONFIADO, seed = 6)
        )
        assertEquals(
            "van dos cosas: ordenemos esto",
            applyPersonalitySignature("ordenemos esto", BotPersonality.ANALITICO, seed = 12)
        )
        val variants = listOf(6, 12, 18).map { seed ->
            applyPersonalitySignature("no compro esa historia", BotPersonality.PICANTE, seed)
        }
        assertEquals(3, variants.toSet().size)
        assertEquals(
            "no compro esa historia",
            applyPersonalitySignature(
                "no compro esa historia",
                BotPersonality.JODON,
                seed = 6,
                playful = false
            )
        )
    }

    private fun session(): GameSession {
        val map = RoleMap.PAMPA
        return GameSession(
            code = "DIRECTOR",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.DIA_DEBATE,
            players = listOf(
                GamePlayer("Humano", "H", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map), isHuman = true),
                GamePlayer("Mora", "M", RoleCatalog.gameRole(RoleCatalog.ASESINO, map)),
                GamePlayer("Beto", "B", RoleCatalog.gameRole(RoleCatalog.POLICIA, map)),
                GamePlayer("Dina", "D", RoleCatalog.gameRole(RoleCatalog.MEDICO, map)),
                GamePlayer("Valen", "V", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map))
            )
        )
    }
}
