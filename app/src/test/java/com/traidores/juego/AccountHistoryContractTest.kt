package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class AccountHistoryContractTest {
    private fun session(role: String = RoleCatalog.ALDEANO) = GameSession(
        code = "ABCD", mapKey = "pampa", mapName = "Pampa",
        players = listOf(GamePlayer("Nacho", "N", isHuman = true,
            role = RoleCatalog.gameRole(role, RoleMap.PAMPA)), GamePlayer("Otro", "O")),
        startedAtEpochMs = 1000L, winner = GameRules.TOWN_WINNER)

    @Test fun refusesGuestOnlineAndUnfinishedResults() {
        assertNull(AccountHistoryContract.localRecord(session(), "", 2000))
        assertNull(AccountHistoryContract.localRecord(session().copy(onlineMatchId = "server-match"), "uid", 2000))
        assertNull(AccountHistoryContract.localRecord(session().copy(winner = GameRules.CANCELLED_WINNER), "uid", 2000))
        assertNull(AccountHistoryContract.localRecord(session().copy(winner = ""), "uid", 2000))
    }
    @Test fun bindsLocalResultToAccountAndDistinguishesNewMatches() {
        val record = AccountHistoryContract.localRecord(session(), "uid", 2000)!!
        assertEquals("uid", record["uid"])
        assertEquals(true, record["won"])
        assertEquals("local", record["origen"])
        assertEquals(AccountHistoryContract.id(record["matchKey"] as String), AccountHistoryContract.id("local:ABCD:1000:2"))
        assertNotEquals(AccountHistoryContract.id("local:ABCD:1000:2"), AccountHistoryContract.id("local:ABCD:1001:2"))
    }
    @Test fun winnerUsesRoleAndChosenDesertorTeam() {
        assertEquals(false, AccountHistoryContract.localRecord(session(RoleCatalog.ASESINO), "uid", 2000)!!["won"])
        assertEquals(true, AccountHistoryContract.localRecord(
            session(RoleCatalog.DESERTOR).copy(desertorTeam = GameRules.TOWN_WINNER), "uid", 2000)!!["won"])
    }
    @Test fun idMatchesBackendDigest() {
        assertEquals("local_a7882d6119105ff77408c65a84a57c7a1599aa88519809f89fd69237e077a596",
            AccountHistoryContract.id("local:ABCD:1000:2"))
    }
}
