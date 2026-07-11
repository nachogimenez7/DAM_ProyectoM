package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitorPlanBrainTest {

    @Test
    fun normalPlanUsesCurrentAssassinTargetAsKillTarget() {
        val session = baseSession(botDifficulty = BotDifficulty.NORMAL)
        val assassin = session.players.first { it.role?.key == RoleCatalog.ASESINO }

        val plan = TraitorPlanBrain.build(session)

        assertNotNull(plan)
        assertEquals(LocalBotAi.chooseAssassinTargetWithoutPlan(session, assassin), plan!!.killTarget)
    }

    @Test
    fun hardPlanCounterClaimsDetectiveThatMarkedTraitor() {
        val session = baseSession(botDifficulty = BotDifficulty.HARD).copy(
            round = 3,
            claimLedger = mapOf(
                "Detective" to listOf(
                    ClaimRecord(
                        round = 2,
                        phase = GamePhase.DIA_DEBATE,
                        roleKey = RoleCatalog.POLICIA
                    ),
                    ClaimRecord(
                        round = 2,
                        phase = GamePhase.DIA_DEBATE,
                        statementType = StatementType.ACCUSE,
                        target = "Asesino"
                    )
                )
            ),
            tableMemory = TableMemory(
                declaredInvestigationReads = listOf(
                    InvestigationRead(
                        round = 2,
                        source = "Detective",
                        target = "Asesino",
                        result = "sospechoso"
                    )
                )
            )
        )

        val plan = TraitorPlanBrain.build(session)

        assertNotNull(plan)
        assertEquals(CoverKind.COUNTER_CLAIM, plan!!.cover?.kind)
        assertEquals("Asesino", plan.cover?.actor)
        assertEquals("Detective", plan.cover?.targetToDirty)
        assertEquals("Detective", plan.dayPushTarget)
        assertFalse(plan.killTarget == "Detective")
        assertTrue(plan.threats.any {
            it.player == "Detective" &&
                it.kind == ThreatKind.DETECTIVE_DECLARADO &&
                it.markedTraitor == "Asesino"
        })
    }

    @Test
    fun dayPushTargetDoesNotPointToTraitorWithoutBusAlly() {
        val session = baseSession(botDifficulty = BotDifficulty.HARD).copy(round = 2)

        val plan = TraitorPlanBrain.build(session)

        assertNotNull(plan)
        val pushTarget = GameEngine.playerByName(session, plan!!.dayPushTarget)
        assertTrue(pushTarget == null || !GameRules.isTraitorRole(pushTarget.role))
        assertTrue(plan.cover?.kind != CoverKind.BUS_ALLY)
    }

    @Test
    fun traitorVoteUsesNightPlanPushTargetDuringDay() {
        val session = baseSession(botDifficulty = BotDifficulty.HARD).copy(
            phase = GamePhase.VOTACION,
            round = 3,
            traitorPlan = TraitorPlan(
                round = 3,
                killTarget = "Medico",
                killRationale = KillRationale.LIDER_DE_OPINION,
                dayPushTarget = "Detective",
                threats = emptyList(),
                cover = CoverMove(
                    kind = CoverKind.LOW_PROFILE,
                    actor = "Asesino",
                    backer = null,
                    fakeRoleKey = null,
                    targetToDirty = null
                ),
                speakingOrder = listOf("Asesino", "Espia")
            )
        )
        val assassin = session.players.first { it.name == "Asesino" }

        assertEquals("Detective", traitorPlanVotePlan(session, assassin)?.target)
        assertEquals("Detective", LocalBotAi.chooseVoteTarget(session, assassin))
    }

    @Test
    fun counterClaimActorUsesPlannedPublicLine() {
        val session = baseSession(botDifficulty = BotDifficulty.HARD).copy(
            phase = GamePhase.DIA_DEBATE,
            round = 3,
            traitorPlan = TraitorPlan(
                round = 3,
                killTarget = "Medico",
                killRationale = KillRationale.NOS_MARCO,
                dayPushTarget = "Detective",
                threats = listOf(
                    TraitorThreat(
                        player = "Detective",
                        kind = ThreatKind.DETECTIVE_DECLARADO,
                        markedTraitor = "Asesino"
                    )
                ),
                cover = CoverMove(
                    kind = CoverKind.COUNTER_CLAIM,
                    actor = "Asesino",
                    backer = "Espia",
                    fakeRoleKey = RoleCatalog.POLICIA,
                    targetToDirty = "Detective"
                ),
                speakingOrder = listOf("Asesino", "Espia")
            )
        )
        val assassin = session.players.first { it.name == "Asesino" }

        val line = traitorPlannedDayLine(session, assassin, index = 0)

        assertNotNull(line)
        assertTrue(line!!.contains("soy", ignoreCase = true))
        assertTrue(line.contains("Detective"))
    }

    private fun baseSession(botDifficulty: BotDifficulty): GameSession {
        return GameSession(
            code = "PLAN-TEST",
            mapKey = "pampa",
            mapName = "Pampa",
            botDifficulty = botDifficulty,
            phase = GamePhase.NOCHE_ASESINO,
            players = listOf(
                GamePlayer(
                    "Asesino",
                    "A",
                    role = role(RoleCatalog.ASESINO, "Asesino", GameRules.TRAITOR_WINNER)
                ),
                GamePlayer(
                    "Espia",
                    "E",
                    role = role(RoleCatalog.ESPIA, "Espia", GameRules.TRAITOR_WINNER)
                ),
                GamePlayer(
                    "Detective",
                    "D",
                    role = role(RoleCatalog.POLICIA, "Detective", GameRules.TOWN_WINNER)
                ),
                GamePlayer(
                    "Medico",
                    "M",
                    role = role(RoleCatalog.MEDICO, "Medico", GameRules.TOWN_WINNER)
                ),
                GamePlayer(
                    "Aldeano",
                    "P",
                    role = role(RoleCatalog.ALDEANO, "Aldeano", GameRules.TOWN_WINNER),
                    isHuman = true
                )
            )
        )
    }

    private fun role(key: String, name: String, team: String): GameRole {
        return GameRole(key, name, team, "rol_${key}_gaucho")
    }
}
