package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPerceptionTest {
    @Test
    fun parsesRoleClaimsWithNaturalAliasesAndWordOrder() {
        assertEquals(RoleCatalog.POLICIA, LocalBotAi.roleClaimFrom("soy detective")?.roleKey)
        assertEquals(RoleCatalog.MEDICO, LocalBotAi.roleClaimFrom("soy el médico")?.roleKey)
        assertEquals(RoleCatalog.POLICIA, LocalBotAi.roleClaimFrom("me tocó ser policía")?.roleKey)
        assertEquals(RoleCatalog.POLICIA, LocalBotAi.roleClaimFrom("tengo el rol de inspector")?.roleKey)
        assertEquals(RoleCatalog.MEDICO, LocalBotAi.roleClaimFrom("el doc soy yo")?.roleKey)
        assertEquals(RoleCatalog.POLICIA, LocalBotAi.roleClaimFrom("a mí me tocó ser poli")?.roleKey)
    }

    @Test
    fun parsesPublicReadsAcrossGenderAndResultVariants() {
        val session = session()

        val detectiveRead = LocalBotAi.publicStatementFrom(
            session,
            "soy detective, mora me dio sospechoso"
        )
        assertEquals(StatementType.ACCUSE, detectiveRead?.type)
        assertEquals("Mora", detectiveRead?.target)
        assertEquals(StatementType.ACCUSE, LocalBotAi.publicStatementFrom(session, "mora me salió sospechosa")?.type)
        assertEquals(StatementType.ACCUSE, LocalBotAi.publicStatementFrom(session, "MORA ES TRAIDORA")?.type)
        assertEquals(StatementType.ACCUSE, LocalBotAi.publicStatementFrom(session, "morá está rara")?.type)
        assertEquals(StatementType.REFUSED_ROLE, LocalBotAi.publicStatementFrom(session, "no pienso decir mi rol")?.type)
        assertEquals(StatementType.TRUST, LocalBotAi.publicStatementFrom(session, "confío en Valen")?.type)
        assertEquals(StatementType.VOTE, LocalBotAi.publicStatementFrom(session, "voto a Thiago")?.type)
    }

    @Test
    fun understandsDirectAddressNegatedVoteAndStatedReason() {
        val session = session()

        assertEquals("Mora", BotPerception.directAddressee(session, "Mora, que pensas de Valen?"))
        assertNull(BotPerception.directAddressee(session, "Mora esta rara"))
        assertEquals(HumanQuestionKind.WHY_VOTE, BotPerception.humanQuestionKind("Mora, por que me votaste?"))
        assertEquals(HumanQuestionKind.ASK_ROLE, BotPerception.humanQuestionKind("Mora, que rol sos?"))
        assertEquals(HumanQuestionKind.VOTE_HELP, BotPerception.humanQuestionKind("Mora, a quien votarias?"))
        assertEquals(HumanSocialSignal.PRAISE, BotPerception.socialSignal("Mora, sos una genia"))
        assertEquals(HumanSocialSignal.INSULT, BotPerception.socialSignal("Mora, sos mentirosa"))

        val statement = LocalBotAi.publicStatementFrom(
            session,
            "no voto a Mora porque sostuvo la misma historia"
        )
        assertEquals(StatementType.TRUST, statement?.type)
        assertEquals("Mora", statement?.target)
        assertEquals("sostuvo la misma historia", statement?.reason)

        val addressedAccusation = LocalBotAi.publicStatementFrom(session, "Mora, Valen miente")
        assertEquals(StatementType.ACCUSE, addressedAccusation?.type)
        assertEquals("Valen", addressedAccusation?.target)

        assertEquals(
            StatementType.ACCUSE,
            LocalBotAi.publicStatementFrom(session, "no confio en Valen")?.type
        )
        assertEquals(
            StatementType.TRUST,
            LocalBotAi.publicStatementFrom(session, "Mora no me dio sospechosa")?.type
        )
        assertNull(LocalBotAi.publicStatementFrom(session, "no protegi a Valen"))
    }

    @Test
    fun playerMentionsRequireWholeNamesButAcceptUniquePrefixes() {
        val session = session()

        assertFalse(mentionsName("se demora todo", "Mora"))
        assertTrue(mentionsName("Mora, explicá", "Mora"))
        assertEquals(listOf("Lautaro"), mentionedPlayerNames(session, "lauta, qué pensás?"))
    }

    @Test
    fun offTopicIsRedirectedWithoutMisclassifyingGameTalk() {
        val session = session()
        val offTopic = humanMessageIntent(
            session,
            "anoche vi una película buenísima",
            null,
            null,
            claimsHiddenInfo = false,
            casualMessage = false,
            questionKind = null
        )
        val gameTalk = humanMessageIntent(
            session,
            "¿a quién echamos hoy?",
            null,
            null,
            claimsHiddenInfo = false,
            casualMessage = false,
            questionKind = null
        )
        val casual = humanMessageIntent(
            session,
            "hola",
            null,
            null,
            claimsHiddenInfo = false,
            casualMessage = true,
            questionKind = null
        )

        assertEquals(HumanMessageIntent.OFF_TOPIC, offTopic)
        assertEquals(HumanMessageIntent.OTHER, gameTalk)
        assertEquals(HumanMessageIntent.CASUAL, casual)

        val pending = session.copy(
            chatHistory = listOf(GameChatMessage("Beto", "Humano, qué opinás de Mora?"))
        )
        assertEquals(
            HumanMessageIntent.OFF_TOPIC,
            humanMessageIntent(pending, "anoche vi una película buenísima", null, null, false, false, null)
        )
    }

    @Test
    fun generatedSpeechNeverLeaksEnglishClaimJargon() {
        val messages = (1..8).flatMap { index ->
            val base = session(code = "PERCEPTION-$index")
            LocalBotAi.openingDebateMessages(base, limit = 5).map { it.second } +
                LocalBotAi.votingIntentMessages(base.copy(phase = GamePhase.VOTACION), limit = 5).map { it.second } +
                LocalBotAi.reactionsToHumanMessage(base, "soy detective, Mora me dio sospechosa").map { it.second }
        }

        assertTrue(messages.isNotEmpty())
        assertFalse(messages.any { it.contains("claim", ignoreCase = true) })
    }

    private fun session(code: String = "PERCEPTION"): GameSession {
        val map = RoleMap.PAMPA
        return GameSession(
            code = code,
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.DIA_DEBATE,
            players = listOf(
                GamePlayer("Humano", "H", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map), isHuman = true),
                GamePlayer("Mora", "M", RoleCatalog.gameRole(RoleCatalog.ASESINO, map)),
                GamePlayer("Valen", "V", RoleCatalog.gameRole(RoleCatalog.MEDICO, map)),
                GamePlayer("Thiago", "T", RoleCatalog.gameRole(RoleCatalog.POLICIA, map)),
                GamePlayer("Lautaro", "L", RoleCatalog.gameRole(RoleCatalog.ALDEANO, map))
            )
        )
    }
}
