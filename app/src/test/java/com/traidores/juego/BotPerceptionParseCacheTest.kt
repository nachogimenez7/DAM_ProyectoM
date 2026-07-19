package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPerceptionParseCacheTest {

    @Test
    fun repeatedRoleAndStatementParsingKeepsTheSameResult() {
        val session = mentionSession()
        val claimMessage = "A m\u00ed me toc\u00f3 ser polic\u00eda"
        val statementMessage = "voto a JOS\u00c9 #1"

        repeat(5) {
            assertEquals(RoleCatalog.POLICIA, BotPerception.roleClaimFrom(claimMessage)?.roleKey)
            assertEquals(
                PublicStatement(StatementType.VOTE, "Jos\u00e9 #1"),
                BotPerception.publicStatementFrom(session, statementMessage)
            )
            assertEquals(
                PublicStatement(StatementType.ACCUSE, "Mora"),
                BotPerception.publicStatementFrom(session, "Mora me sali\u00f3 sospechosa")
            )
            assertEquals(
                PublicStatement(StatementType.PROTECTED, "Mora"),
                BotPerception.publicStatementFrom(session, "proteg\u00ed a Mora")
            )
            assertTrue(mentionsName("JOS\u00c9 #1, explic\u00e1", "Jos\u00e9 #1"))
        }
    }

    @Test
    fun publicStatementCacheIncludesTheAliveRoster() {
        val session = mentionSession()
        val message = "voto a laut"

        assertEquals(
            PublicStatement(StatementType.VOTE, "Lautaro"),
            BotPerception.publicStatementFrom(session, message)
        )

        val withoutLautaro = session.copy(
            players = session.players.map { player ->
                if (player.name == "Lautaro") player.copy(alive = false) else player
            }
        )
        assertNull(BotPerception.publicStatementFrom(withoutLautaro, message))
    }

    @Test
    fun tenPlayerVoteTargetAndPrecomputedRankingStayStable() {
        val session = tenPlayerVoteSession()
        val voter = GameEngine.playerByName(session, "Beto")!!
        val ranked = rankedPublicSuspects(session, voter)

        assertEquals(
            conversationVotePlan(session, voter),
            conversationVotePlan(session, voter, ranked)
        )
        repeat(5) {
            assertEquals("Dina", LocalBotAi.chooseVoteTarget(session, voter))
        }
    }

    private fun mentionSession(): GameSession {
        val role = RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.PAMPA)
        return GameSession(
            code = "PARSE-CACHE",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.DIA_DEBATE,
            players = listOf(
                GamePlayer("Humano", "H", role, isHuman = true),
                GamePlayer("Jos\u00e9 #1", "J", role),
                GamePlayer("Lautaro", "L", role),
                GamePlayer("Laura", "A", role),
                GamePlayer("Mora", "M", role)
            )
        )
    }

    private fun tenPlayerVoteSession(): GameSession {
        val role = RoleCatalog.gameRole(RoleCatalog.ALDEANO, RoleMap.PAMPA)
        val names = listOf(
            "Humano", "Beto", "Ciro", "Dina", "Ana",
            "Eva", "Fede", "Gabi", "Hugo", "Iris"
        )
        return GameSession(
            code = "TEN-PLAYER-CACHE-VOTE",
            mapKey = "pampa",
            mapName = "Pampa",
            phase = GamePhase.VOTACION,
            players = names.mapIndexed { index, name ->
                GamePlayer(name, name.take(1), role, isHuman = index == 0)
            },
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
    }
}
