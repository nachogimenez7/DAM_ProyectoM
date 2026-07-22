package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
