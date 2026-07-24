package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitorKillNoticesTest {

    @Test
    fun aKillerSeesWhoTheOtherKillerChose() {
        val session = nightSession()

        val notices = TraitorKillNotices.pendingNotices(
            session = session,
            records = listOf(killRecord(actor = "Beto", target = "Mora")),
            viewerName = "Ana"
        )

        assertEquals(1, notices.size)
        assertEquals(TraitorKillNotices.noticeFor("Beto", "Mora"), notices.first().message)
        assertEquals(ChatChannel.TRAIDORES, notices.first().channel)
        assertTrue(notices.first().isGod)
    }

    @Test
    fun nobodyIsToldAboutTheirOwnChoice() {
        val notices = TraitorKillNotices.pendingNotices(
            session = nightSession(),
            records = listOf(killRecord(actor = "Ana", target = "Mora")),
            viewerName = "Ana"
        )

        assertTrue(notices.isEmpty())
    }

    @Test
    fun onlyTheFirstConfirmedChoiceIsAnnounced() {
        val notices = TraitorKillNotices.pendingNotices(
            session = nightSession(),
            records = listOf(
                killRecord(actor = "Beto", target = "Mora", createdAt = 10L),
                killRecord(actor = "Beto", target = "Dina", createdAt = 20L)
            ),
            viewerName = "Ana"
        )

        assertEquals(1, notices.size)
        assertEquals(TraitorKillNotices.noticeFor("Beto", "Mora"), notices.first().message)
    }

    @Test
    fun anAlreadyPostedNoticeIsNotRepeated() {
        val posted = GameChatMessage(
            speaker = TraitorKillNotices.SPEAKER,
            message = TraitorKillNotices.noticeFor("Beto", "Mora"),
            isGod = true,
            channel = ChatChannel.TRAIDORES
        )
        val session = nightSession().copy(chatHistory = listOf(posted))

        val notices = TraitorKillNotices.pendingNotices(
            session = session,
            records = listOf(killRecord(actor = "Beto", target = "Mora")),
            viewerName = "Ana"
        )

        assertTrue(notices.isEmpty())
    }

    @Test
    fun theTownNeverSeesTheseNotices() {
        val notices = TraitorKillNotices.pendingNotices(
            session = nightSession(),
            records = listOf(killRecord(actor = "Beto", target = "Mora")),
            viewerName = "Mora"
        )

        assertTrue(notices.isEmpty())
    }

    @Test
    fun withASingleKillerThereIsNothingToCoordinate() {
        val session = nightSession(
            players = listOf(
                player("Ana", RoleCatalog.ASESINO),
                player("Mora", RoleCatalog.ALDEANO),
                player("Dina", RoleCatalog.MEDICO)
            )
        )

        val notices = TraitorKillNotices.pendingNotices(
            session = session,
            records = listOf(killRecord(actor = "Ana", target = "Mora")),
            viewerName = "Ana"
        )

        assertTrue(notices.isEmpty())
    }

    @Test
    fun choicesFromAnotherRoundOrPhaseAreIgnored() {
        val session = nightSession()

        val otherRound = TraitorKillNotices.pendingNotices(
            session = session,
            records = listOf(killRecord(actor = "Beto", target = "Mora", round = 2)),
            viewerName = "Ana"
        )
        val otherPhaseIndex = TraitorKillNotices.pendingNotices(
            session = session,
            records = listOf(killRecord(actor = "Beto", target = "Mora", phaseIndex = 9)),
            viewerName = "Ana"
        )

        assertTrue(otherRound.isEmpty())
        assertTrue(otherPhaseIndex.isEmpty())
    }

    private fun nightSession(
        players: List<GamePlayer> = listOf(
            player("Ana", RoleCatalog.ASESINO),
            player("Beto", RoleCatalog.ESPIA),
            player("Mora", RoleCatalog.ALDEANO),
            player("Dina", RoleCatalog.MEDICO)
        )
    ): GameSession {
        return GameSession(
            code = "ONLINE-KILLERS",
            mapKey = "pampa",
            mapName = "Pampa",
            players = players,
            phase = GamePhase.NOCHE_ASESINO,
            round = 1,
            phaseIndex = 3,
            onlineMatchId = "match-1"
        )
    }

    private fun player(name: String, roleKey: String): GamePlayer {
        return GamePlayer(
            name = name,
            initial = name.take(1),
            role = RoleCatalog.gameRole(roleKey, RoleMap.PAMPA)
        )
    }

    private fun killRecord(
        actor: String,
        target: String,
        round: Int = 1,
        phaseIndex: Int = 3,
        createdAt: Long = 1L
    ): OnlineActionRecord {
        return OnlineActionRecord(
            matchId = "match-1",
            actorId = "uid-$actor",
            action = "matar",
            actorName = actor,
            targetName = target,
            phaseName = GamePhase.NOCHE_ASESINO.name,
            round = round,
            phaseIndex = phaseIndex,
            createdAtLocal = createdAt
        )
    }
}
