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
        assertTrue(beat?.message.orEmpty(), beat?.message.orEmpty().contains("Beto", ignoreCase = true))
    }

    @Test
    fun directSuspectQuestionGetsOnlyTheAddressedBotsAnswer() {
        val reactions = LocalBotAi.reactionsToHumanMessage(
            session(),
            "Mora, a quien sospechas?"
        )

        assertEquals(1, reactions.size)
        assertEquals("Mora", reactions.single().first)
        assertTrue(reactions.single().second.isNotBlank())
    }

    @Test
    fun botDoesNotInventAVoteWhenAskedForAReason() {
        val reply = LocalBotAi.reactionsToHumanMessage(
            session(),
            "Mora, por que me votaste?"
        ).single().second

        assertTrue(reply, reply.contains("no vote") || reply.contains("no tengo un voto"))
        assertFalse(reply, reply.contains("te vote porque"))
    }

    @Test
    fun botCorrectsTheHumanWhenAskedAboutTheWrongVoteTarget() {
        val withVote = session().copy(
            actionHistory = listOf(
                GameAction(
                    type = GameActionType.VOTE,
                    actor = "Mora",
                    target = "Beto",
                    round = 1,
                    phase = GamePhase.VOTACION,
                    publiclyKnown = true
                )
            )
        )

        val reply = LocalBotAi.reactionsToHumanMessage(
            withVote,
            "Mora, por que votaste a Valen?"
        ).single().second

        assertTrue(reply, reply.contains("Beto"))
        assertTrue(reply, reply.contains("Valen"))
    }

    @Test
    fun followUpWhyQuestionKeepsTheBotsDeclaredTarget() {
        val withStance = session().copy(
            claimLedger = mapOf(
                "Mora" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.ACCUSE,
                        target = "Beto",
                        reason = "cambio su version"
                    )
                )
            )
        )

        val reply = LocalBotAi.reactionsToHumanMessage(
            withStance,
            "Mora, por que decis eso?"
        ).single().second

        assertTrue(reply, reply.contains("Beto"))
    }

    @Test
    fun currentRoundStanceRemainsTheBotsObjectiveWithoutNewStrongEvidence() {
        val withStance = session().copy(
            claimLedger = mapOf(
                "Mora" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.ACCUSE,
                        target = "Beto",
                        reason = "cambio su version"
                    )
                )
            )
        )
        val mora = GameEngine.playerByName(withStance, "Mora")!!

        val objective = roundObjectiveFor(withStance, mora)

        assertEquals("Beto", objective.target)
        assertEquals("cambio su version", objective.reason)
    }

    @Test
    fun aDirectBotQuestionIsAnsweredByTheAddressedBot() {
        val withQuestion = session().copy(
            chatHistory = listOf(
                GameChatMessage("Beto", "Mora, a quien sospechas?")
            )
        )
        val mora = GameEngine.playerByName(withQuestion, "Mora")!!

        val reply = botToBotLine(withQuestion, mora)

        assertTrue(reply.orEmpty().isNotBlank())
    }

    @Test
    fun aHumanMessagePreventsReplyingToAnOlderBotQuestion() {
        val withHumanReply = session().copy(
            chatHistory = listOf(
                GameChatMessage("Beto", "Mora, a quien sospechas?"),
                GameChatMessage("Humano", "no se, todavia estoy pensando")
            )
        )
        val mora = GameEngine.playerByName(withHumanReply, "Mora")!!

        assertNull(botToBotLine(withHumanReply, mora))
    }

    @Test
    fun everyVotingMessageNamesTheTargetThatBotWillActuallyVote() {
        val voting = session().copy(phase = GamePhase.VOTACION)

        val messages = LocalBotAi.votingIntentMessages(voting, limit = 4)

        assertTrue(messages.isNotEmpty())
        messages.forEach { (speaker, message) ->
            val voter = GameEngine.playerByName(voting, speaker)!!
            val target = LocalBotAi.chooseVoteTarget(voting, voter)
            assertTrue("$speaker dijo '$message' pero votaria a $target", mentionsName(message, target))
        }
    }

    @Test
    fun mentionedBotSpeaksAboutItselfInFirstPerson() {
        val session = session()
        val mora = GameEngine.playerByName(session, "Mora")!!

        val reply = pendingAnswerReply(
            session = session,
            bot = mora,
            humanMessage = "A Mora",
            memory = memoryFor(session, mora),
            index = 0
        )

        assertTrue(reply, reply.contains("mi") || reply.contains("soy yo"))
        assertFalse(reply, reply.contains("que conteste", ignoreCase = true))
    }

    @Test
    fun spamReactionUsesOneAvailableBotAndKeepsTheConversationHuman() {
        val beat = BotConversationDirector.spamReactionBeat(session(), lastSpeaker = "Beto")

        assertNotNull(beat)
        assertNotEquals("Beto", beat?.speaker)
        assertTrue(
            beat?.message.orEmpty(),
            beat?.message.orEmpty().contains("una por vez") ||
                beat?.message.orEmpty().contains("no llegamos") ||
                beat?.message.orEmpty().contains("vamos de a una")
        )
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
    fun burstDelayLeavesTimeToReadAndSeeTheBotTyping() {
        val delay = BotConversationDirector.burstDelayMs(
            session = session(),
            beatIndex = 1,
            speaker = "Beto",
            message = "y otra cosa"
        )

        assertTrue(delay in 2_400L..4_000L)
    }

    @Test
    fun ordinaryThinkingDelayFeelsHumanWithoutAppearingInstantly() {
        val delays = (0..12).map { beatIndex ->
            BotConversationDirector.naturalDelayMs(
                session = session(),
                beatIndex = beatIndex,
                message = "ok, te escucho",
                reaction = true,
                speaker = "Beto"
            )
        }

        assertTrue(delays.all { it in 3_600L..6_800L })
    }

    @Test
    fun seriousNaturalToneKeepsCasualSpanishWithoutTeenSlang() {
        val line = seriousNaturalSpeech("KJjj dale amigo, q rol decis tener? posta")

        assertTrue(line.contains("que rol decis tener?"))
        assertFalse(line.contains("kjjj"))
        assertFalse(line.contains("amigo"))
        assertFalse(line.contains(" q "))
    }

    @Test
    fun strategicQuestionsUseTheShortNaturalRoleWording() {
        val questions = linesFor(
            intent = BotSpeechIntent.ASK,
            spokenTarget = "Mora",
            reason = "cambio su version"
        )

        assertTrue(questions.contains("Mora, que rol decis tener?"))
        assertTrue(questions.any { it.contains("que hiciste anoche?") })
    }

    @Test
    fun firstOffTopicMessageDoesNotDistractTheBots() {
        val reactions = LocalBotAi.reactionsToHumanMessage(
            session(),
            "anoche vi una pelicula buenisima"
        )

        assertTrue(reactions.isEmpty())
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

        assertTrue(
            line,
            line.contains("miras") ||
                line.contains("sospecha") ||
                line.contains("lectura") ||
                line.contains("votarias")
        )
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
            "no se, no me cierra",
            applyPersonalitySignature("no me cierra", BotPersonality.DESCONFIADO, seed = 6)
        )
        assertEquals(
            "ordenemos esto",
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

    @Test
    fun firstTwoDaysLeaveSpaceForTheHuman() {
        val firstDay = session().copy(round = 1)
        val secondDay = session().copy(round = 2)
        val thirdDay = session().copy(round = 3)

        assertEquals(2, BotConversationDirector.idleBudget(firstDay))
        assertEquals(2, BotConversationDirector.idleBudget(secondDay))
        assertEquals(3, BotConversationDirector.idleBudget(thirdDay))
        assertEquals(1, BotConversationDirector.pauseAfterBotStreak(firstDay))
        assertEquals(2, BotConversationDirector.pauseAfterBotStreak(thirdDay))
    }

    @Test
    fun firstDayDeathProducesOneNeutralReaction() {
        val earlySession = session().copy(
            round = 1,
            publicAnnouncement = "Amanecer: murió Valen."
        )

        val reactions = LocalBotAi.reactionsToEvent(
            earlySession,
            BotEvent(BotEventType.MUERTE_NOCTURNA, "Valen"),
            limit = 3
        )

        assertEquals(1, reactions.size)
        val line = reactions.single().second
        assertTrue(line, line.contains("Valen"))
        assertTrue(
            line,
            line.contains("pista", ignoreCase = true) ||
                line.contains("sabe", ignoreCase = true) ||
                line.contains("vio", ignoreCase = true)
        )
    }

    @Test
    fun earlyDebateAsksBeforeAccusing() {
        val messages = LocalBotAi.openingDebateMessages(
            session().copy(
                round = 1,
                publicAnnouncement = "Amanecer: murió Valen."
            ),
            limit = 3
        )

        assertTrue(messages.isNotEmpty())
        assertTrue(
            messages.take(2).all { (_, message) ->
                message.contains("?", ignoreCase = true) ||
                    message.contains("no votemos por apuro", ignoreCase = true)
            }
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
