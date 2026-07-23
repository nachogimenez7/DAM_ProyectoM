package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotTableMemoryTest {
    @Test
    fun publicDetectiveReadIsStoredAsDebatablePublicEvidence() {
        val session = session()

        val updated = GameEngine.addHumanChatMessage(
            session,
            "soy detective, Mora me dio sospechosa",
            includeBotReactions = false
        )

        val humanRecords = updated.claimLedger["Humano"].orEmpty()
        assertEquals(1, humanRecords.size)
        assertEquals(RoleCatalog.POLICIA, humanRecords.single().roleKey)
        assertEquals(StatementType.ACCUSE, humanRecords.single().statementType)
        assertEquals("Mora", humanRecords.single().target)
        assertEquals(
            InvestigationRead(1, "Humano", "Mora", "sospechoso"),
            updated.tableMemory.declaredInvestigationReads.single()
        )
        assertTrue(updated.tableMemory.suspicion["Valen"].orEmpty().getValue("Mora") >= 5)
    }

    @Test
    fun pendingQuestionPersistsInTableMemoryUntilTargetSpeaks() {
        val asked = GameEngine.addBotChatMessage(
            session(),
            "Beto",
            "Humano, que opinas de Mora?"
        )

        assertEquals("Beto", pendingQuestionForHuman(asked)?.speaker)
        assertEquals("Humano", asked.tableMemory.pendingQuestions["Humano"]?.target)

        val answered = GameEngine.addHumanChatMessage(
            asked,
            "no se, Mora me hace ruido",
            includeBotReactions = false
        )

        assertNull(pendingQuestionForHuman(answered))
        assertTrue(answered.tableMemory.pendingQuestions.isEmpty())
    }

    @Test
    fun roundDecayKeepsMemoryButSoftensSuspicion() {
        val memory = TableMemory(
            suspicion = mapOf("Beto" to mapOf("Mora" to 9, "Valen" to -6)),
            pendingQuestions = mapOf(
                "Humano" to PendingQuestion(1, "Beto", "Humano", "Humano, que decis?")
            )
        )

        val decayed = BotTableMemory.decayForNewRound(memory, session().players, newRound = 2)

        assertEquals(6, decayed.suspicion["Beto"].orEmpty()["Mora"])
        assertEquals(-4, decayed.suspicion["Beto"].orEmpty()["Valen"])
        assertNotNull(decayed.pendingQuestions["Humano"])
    }

    @Test
    fun accusationCreatesGrudgeAndDifferentBeliefsInsteadOfHiveMind() {
        val base = session().copy(
            players = session().players.map { player ->
                if (player.name == "Dina") {
                    player.copy(role = RoleCatalog.gameRole(RoleCatalog.ASESINO, RoleMap.PAMPA))
                } else {
                    player
                }
            }
        )

        val updated = GameEngine.addHumanChatMessage(
            base,
            "Mora es traidora porque cambio la historia",
            includeBotReactions = false
        )

        assertEquals(0, updated.tableMemory.suspicion["Dina"].orEmpty()["Mora"] ?: 0)
        assertTrue((updated.tableMemory.suspicion["Valen"].orEmpty()["Mora"] ?: 0) > 0)
        assertTrue((updated.tableMemory.rapport["Mora"].orEmpty()["Humano"] ?: 0) < 0)
        assertTrue((updated.tableMemory.emotionalPressure["Mora"] ?: 0) > 0)
    }

    @Test
    fun directPraiseAndInsultChangeOnlyTheAddressedBotsRelationship() {
        val praised = GameEngine.addHumanChatMessage(
            session(),
            "Mora, sos una genia",
            includeBotReactions = false
        )
        val insulted = GameEngine.addHumanChatMessage(
            session(),
            "Mora, sos mentirosa",
            includeBotReactions = false
        )

        assertTrue((praised.tableMemory.rapport["Mora"].orEmpty()["Humano"] ?: 0) > 0)
        assertTrue((insulted.tableMemory.rapport["Mora"].orEmpty()["Humano"] ?: 0) < 0)
        assertEquals(0, praised.tableMemory.rapport["Valen"].orEmpty()["Humano"] ?: 0)
        assertTrue((insulted.tableMemory.emotionalPressure["Mora"] ?: 0) > 0)
    }

    @Test
    fun conversationMemoryUsesLedgerWhenChatWindowNoLongerHasTheMessage() {
        val session = session().copy(
            chatHistory = emptyList(),
            claimLedger = mapOf(
                "Humano" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        roleKey = RoleCatalog.POLICIA,
                        statementType = StatementType.ACCUSE,
                        target = "Mora"
                    )
                )
            )
        )

        val memory = conversationMemory(session)

        assertEquals(RoleCatalog.POLICIA, memory["Humano"]?.roleClaim?.roleKey)
        assertEquals(StatementType.ACCUSE, memory["Humano"]?.latestStatement?.type)
        assertEquals(setOf("Humano"), memory["Mora"]?.accusedBy)
        assertEquals("Mora", humanSuggestedVoteTarget(session))
    }

    private fun session(): GameSession {
        val map = RoleMap.PAMPA
        return GameSession(
            code = "TABLE-MEMORY",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.DIA_DEBATE,
            players = listOf(
                GamePlayer("Humano", "H", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map), isHuman = true),
                GamePlayer("Mora", "M", RoleCatalog.gameRole(RoleCatalog.ASESINO, map)),
                GamePlayer("Valen", "V", RoleCatalog.gameRole(RoleCatalog.MEDICO, map)),
                GamePlayer("Beto", "B", RoleCatalog.gameRole(RoleCatalog.POLICIA, map)),
                GamePlayer("Dina", "D", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map))
            )
        )
    }
}
