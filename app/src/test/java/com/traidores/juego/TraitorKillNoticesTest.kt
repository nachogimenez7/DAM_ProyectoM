package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class TraitorKillNoticesTest {

    @Test
    fun everyNightStartsASectionAndAnnouncesEveryConfirmedChoice() {
        val notices = TraitorKillNotices.confirmedNotices(
            session = nightSession(),
            records = listOf(
                actionRecord(actor = "Ana", target = "Mora", actorOrder = 0),
                actionRecord(actor = "Beto", target = "Mora", actorOrder = 1)
            )
        )

        assertEquals("Noche 1: comienza un nuevo Plan.", notices[0].message)
        assertEquals("Ana votó a Mora para asesinar esta noche.", notices[1].message)
        assertEquals("Beto votó a Mora para asesinar esta noche.", notices[2].message)
        assertEquals("El Plan acordó eliminar a Mora.", notices[3].message)
        assertEquals(RoleCatalog.ASESINO, notices[1].roleKey)
        assertEquals(RoleCatalog.ESPIA, notices[2].roleKey)
        assertEquals("Ana", notices[1].actorName)
        assertEquals("Mora", notices[1].targetName)
        assertEquals(notices.map { it.id }.distinct().size, notices.size)
    }

    @Test
    fun aTieIsAnnouncedOnlyAfterEveryLivingKillerVoted() {
        val partial = TraitorKillNotices.confirmedNotices(
            session = nightSession(),
            records = listOf(actionRecord(actor = "Ana", target = "Mora", actorOrder = 0))
        )
        val complete = TraitorKillNotices.confirmedNotices(
            session = nightSession(),
            records = listOf(
                actionRecord(actor = "Ana", target = "Mora", actorOrder = 0),
                actionRecord(actor = "Beto", target = "Dina", actorOrder = 1)
            )
        )

        assertEquals(2, partial.size)
        assertEquals(4, complete.size)
        assertEquals(
            "Empate en el Plan entre Dina y Mora. Esta noche no habrá víctima.",
            complete.last().message
        )
    }

    @Test
    fun mercenarySilenceIsAlsoAnnounced() {
        val session = nightSession(
            players = listOf(
                player("Ana", RoleCatalog.ASESINO),
                player("Memo", RoleCatalog.MERCENARIO),
                player("Mora", RoleCatalog.ALDEANO)
            )
        ).copy(phase = GamePhase.NOCHE_MERCENARIO)

        val notices = TraitorKillNotices.confirmedNotices(
            session,
            listOf(actionRecord("Memo", "Mora", action = "silenciar", actorOrder = 1))
        )

        assertEquals(
            listOf(
                "Noche 1: comienza un nuevo Plan.",
                "Memo eligió a Mora para silenciar esta noche."
            ),
            notices.map { it.message }
        )
        assertEquals(RoleCatalog.MERCENARIO, notices.last().roleKey)
        assertEquals("Memo", notices.last().actorName)
        assertEquals("Mora", notices.last().targetName)
    }

    @Test
    fun onlyFirstConfirmedChoicePerActorAndActionIsAnnounced() {
        val notices = TraitorKillNotices.confirmedNotices(
            session = nightSession(),
            records = listOf(
                actionRecord("Ana", "Mora", createdAt = 10L, actorOrder = 0),
                actionRecord("Ana", "Dina", createdAt = 20L, actorOrder = 0)
            )
        )

        assertEquals(2, notices.size)
        assertEquals("Ana votó a Mora para asesinar esta noche.", notices.last().message)
    }

    @Test
    fun invalidChoicesAreIgnoredButTheCurrentNightSectionStillExists() {
        val notices = TraitorKillNotices.confirmedNotices(
            nightSession(),
            listOf(
                actionRecord("Ana", "Mora", round = 2),
                actionRecord("Beto", "Mora", phaseIndex = 9)
            )
        )

        assertEquals(listOf("Noche 1: comienza un nuevo Plan."), notices.map { it.message })
    }

    private fun nightSession(
        players: List<GamePlayer> = listOf(
            player("Ana", RoleCatalog.ASESINO),
            player("Beto", RoleCatalog.ESPIA),
            player("Mora", RoleCatalog.ALDEANO),
            player("Dina", RoleCatalog.MEDICO)
        )
    ) = GameSession(
        code = "ONLINE-KILLERS",
        mapKey = "pampa",
        mapName = "Pampa",
        players = players,
        phase = GamePhase.NOCHE_ASESINO,
        round = 1,
        phaseIndex = 3,
        onlineMatchId = "match-1"
    )

    private fun player(name: String, roleKey: String) = GamePlayer(
        name = name,
        initial = name.take(1),
        role = RoleCatalog.gameRole(roleKey, RoleMap.PAMPA)
    )

    private fun actionRecord(
        actor: String,
        target: String,
        action: String = "matar",
        round: Int = 1,
        phaseIndex: Int = 3,
        createdAt: Long = 1L,
        actorOrder: Int = -1
    ) = OnlineActionRecord(
        matchId = "match-1",
        actorId = "uid-$actor",
        action = action,
        actorName = actor,
        targetName = target,
        phaseName = if (action == "silenciar") {
            GamePhase.NOCHE_MERCENARIO.name
        } else {
            GamePhase.NOCHE_ASESINO.name
        },
        round = round,
        phaseIndex = phaseIndex,
        createdAtLocal = createdAt,
        actorOrder = actorOrder
    )
}
