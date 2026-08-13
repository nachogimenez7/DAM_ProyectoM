package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardActionMarksTest {

    @Test
    fun traitorsSeeBothTeamChoicesButTownDoesNot() {
        val shared = listOf(
            OnlineTraitorActionMark("a", "Ana", "Mora", RoleCatalog.ASESINO, 2, 7),
            OnlineTraitorActionMark("b", "Beto", "Dina", RoleCatalog.ESPIA, 2, 7)
        )

        val visible = CardActionMarks.visibleForCurrentPhase(
            session(RoleCatalog.ASESINO), "uid-human", emptyList(), shared
        )
        val hidden = CardActionMarks.visibleForCurrentPhase(
            session(RoleCatalog.MEDICO), "uid-human", emptyList(), shared
        )

        assertEquals(setOf(RoleCatalog.ASESINO, RoleCatalog.ESPIA), visible.map { it.roleKey }.toSet())
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun spySeesTeamMarksEvenIfRealtimeMessageArrivesWithAdjacentPhaseIndex() {
        val shared = listOf(
            OnlineTraitorActionMark("a", "Ana", "Mora", RoleCatalog.ASESINO, 2, 6),
            OnlineTraitorActionMark("s", "Beto", "Dina", RoleCatalog.ESPIA, 2, 8)
        )

        val visible = CardActionMarks.visibleForCurrentPhase(
            session(RoleCatalog.ESPIA), "uid-human", emptyList(), shared
        )

        assertEquals(setOf("Mora", "Dina"), visible.map { it.targetName }.toSet())
        assertEquals(setOf(RoleCatalog.ASESINO, RoleCatalog.ESPIA), visible.map { it.roleKey }.toSet())
    }

    @Test
    fun hostOnlySeesItsOwnPrivateRoleAction() {
        val medic = session(RoleCatalog.MEDICO).copy(phase = GamePhase.NOCHE_MEDICO)
        val records = listOf(
            record("uid-human", "Ana", "Mora", "salvar", GamePhase.NOCHE_MEDICO),
            record("uid-other", "Beto", "Dina", "salvar", GamePhase.NOCHE_MEDICO)
        )

        val marks = CardActionMarks.visibleForCurrentPhase(medic, "uid-human", records, emptyList())

        assertEquals(listOf("Mora"), marks.map { it.targetName })
        assertEquals(RoleCatalog.MEDICO, marks.single().roleKey)
    }

    @Test
    fun medicSelfProtectionTargetsTheHumanCard() {
        val medic = session(RoleCatalog.MEDICO).copy(phase = GamePhase.NOCHE_MEDICO)
        val selfProtection = record(
            "uid-human", "Ana", "Ana", "salvar", GamePhase.NOCHE_MEDICO
        )

        val marks = CardActionMarks.visibleForCurrentPhase(
            medic, "uid-human", listOf(selfProtection), emptyList()
        )

        assertEquals("Ana", marks.single().targetName)
        assertEquals(RoleCatalog.MEDICO, marks.single().roleKey)
    }

    @Test
    fun everyNightRoleKeepsItsOwnMarkDuringTheSharedOnlineNight() {
        val cases = listOf(
            Triple(RoleCatalog.MEDICO, "salvar", "Ana"),
            Triple(RoleCatalog.POLICIA, "investigar", "Mora"),
            Triple(RoleCatalog.ORACULO, "invitar_muerto", "Dina")
        )

        cases.forEach { (roleKey, action, target) ->
            val sharedNight = session(roleKey).copy(phase = GamePhase.NOCHE_ASESINO)
            val mark = CardActionMarks.visibleForCurrentPhase(
                sharedNight,
                "uid-human",
                listOf(record("uid-human", "Ana", target, action, GamePhase.NOCHE_ASESINO)),
                emptyList()
            ).single()

            assertEquals(roleKey, mark.roleKey)
            assertEquals(target, mark.targetName)
        }
    }

    @Test
    fun traitorsSeeMercenarySilenceWithTheActorName() {
        val shared = listOf(
            OnlineTraitorActionMark("m", "Luis", "Mora", RoleCatalog.MERCENARIO, 2, 7)
        )

        val marks = CardActionMarks.visibleForCurrentPhase(
            session(RoleCatalog.ASESINO), "uid-human", emptyList(), shared
        )

        assertEquals(RoleCatalog.MERCENARIO, marks.single().roleKey)
        assertEquals("Luis", marks.single().actorName)
        assertEquals("Mora", marks.single().targetName)
    }

    @Test
    fun threeTraitorActionsOnOnePlayerStayVisibleAndOrdered() {
        val shared = listOf(
            OnlineTraitorActionMark("m", "Luis", "Mora", RoleCatalog.MERCENARIO, 2, 7),
            OnlineTraitorActionMark("s", "Beto", "Mora", RoleCatalog.ESPIA, 2, 7),
            OnlineTraitorActionMark("a", "Ana", "Mora", RoleCatalog.ASESINO, 2, 7)
        )

        val marks = CardActionMarks.visibleForCurrentPhase(
            session(RoleCatalog.ASESINO), "uid-human", emptyList(), shared
        )

        assertEquals(3, marks.size)
        assertEquals(
            listOf(RoleCatalog.ASESINO, RoleCatalog.ESPIA, RoleCatalog.MERCENARIO),
            marks.map { it.roleKey }
        )
        assertEquals(listOf("Ana", "Beto", "Luis"), marks.map { it.actorName })
    }

    @Test
    fun marksDisappearOutsideTheirPhase() {
        val day = session(RoleCatalog.POLICIA).copy(phase = GamePhase.DIA_DEBATE)
        val oldInvestigation = record(
            "uid-human", "Ana", "Mora", "investigar", GamePhase.NOCHE_POLICIA
        ).copy(phaseIndex = 6)

        assertTrue(
            CardActionMarks.visibleForCurrentPhase(
                day, "uid-human", listOf(oldInvestigation), emptyList()
            ).isEmpty()
        )
    }

    @Test
    fun payadorMarksRemainPublicDuringContrapunto() {
        val contrapunto = session(RoleCatalog.ALDEANO).copy(
            players = listOf(
                player("Ana", RoleCatalog.ALDEANO, human = true),
                player("Beto", RoleCatalog.PAYADOR),
                player("Mora", RoleCatalog.ALDEANO),
                player("Dina", RoleCatalog.ALDEANO)
            ),
            phase = GamePhase.CONTRAPUNTO,
            contrapuntoPlayers = listOf("Mora", "Dina")
        )

        val marks = CardActionMarks.visibleForCurrentPhase(
            contrapunto, "uid-human", emptyList(), emptyList()
        )

        assertEquals(setOf("Mora", "Dina"), marks.map { it.targetName }.toSet())
        assertTrue(marks.all { it.roleKey == RoleCatalog.PAYADOR })
    }

    private fun session(roleKey: String) = GameSession(
        code = "MARKS",
        mapKey = "pampa",
        mapName = "Pampa",
        players = listOf(
            player("Ana", roleKey, human = true),
            player("Beto", RoleCatalog.ESPIA),
            player("Mora", RoleCatalog.ALDEANO),
            player("Dina", RoleCatalog.ALDEANO)
        ),
        phase = GamePhase.NOCHE_ASESINO,
        round = 2,
        phaseIndex = 7,
        onlineMatchId = "match-marks"
    )

    private fun player(name: String, roleKey: String, human: Boolean = false) = GamePlayer(
        name = name,
        initial = name.take(1),
        role = RoleCatalog.gameRole(roleKey, RoleMap.PAMPA),
        isHuman = human
    )

    private fun record(
        actorId: String,
        actorName: String,
        target: String,
        action: String,
        phase: GamePhase
    ) = OnlineActionRecord(
        matchId = "match-marks",
        actorId = actorId,
        action = action,
        actorName = actorName,
        targetName = target,
        phaseName = phase.name,
        round = 2,
        phaseIndex = 7,
        createdAtLocal = 1L
    )
}
