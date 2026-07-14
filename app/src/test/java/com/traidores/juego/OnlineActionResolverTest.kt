package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineActionResolverTest {

    @Test
    fun nightActionsKeepFirstConfirmedAssassinVotePerActor() {
        val actions = listOf(
            record("matar", actor = "Asesino1", target = "Aldeano1", createdAt = 10),
            record("matar", actor = "Asesino1", target = "Aldeano2", createdAt = 20),
            record("matar", actor = "Asesino2", target = "Aldeano3", createdAt = 15)
        )

        val resolved = OnlineActionResolver.nightActions(actions, round = 1)

        assertEquals(
            mapOf(
                "Asesino1" to "Aldeano1",
                "Asesino2" to "Aldeano3"
            ),
            resolved.assassinVotes
        )
    }

    @Test
    fun nightActionsIgnoreWrongRoundAndInvalidPayloads() {
        val actions = listOf(
            record("matar", actor = "Asesino", target = "Aldeano", round = 2),
            record("matar", actor = "", target = "Aldeano"),
            record("matar", actor = "Asesino", target = ""),
            record("votar", actor = "Asesino", target = "Aldeano")
        )

        val resolved = OnlineActionResolver.nightActions(actions, round = 1)

        assertEquals(emptyMap<String, String>(), resolved.assassinVotes)
        assertEquals(0, resolved.validActionCount)
    }

    @Test
    fun nightActionsKeepFirstConfirmedSingleRoleAction() {
        val actions = listOf(
            record("salvar", actor = "Medico", target = "Aldeano1", createdAt = 10),
            record("salvar", actor = "Medico", target = "Aldeano2", createdAt = 20)
        )

        val resolved = OnlineActionResolver.nightActions(actions, round = 1)

        assertEquals("Aldeano1", resolved.medicAction?.targetName)
    }

    @Test
    fun nightActionsKeepFirstConfirmedMercenarySilence() {
        val actions = listOf(
            record("silenciar", actor = "Mercenario", target = "Aldeano1", createdAt = 10),
            record("silenciar", actor = "Mercenario", target = "Aldeano2", createdAt = 20)
        )

        val resolved = OnlineActionResolver.nightActions(actions, round = 1)

        assertEquals("Mercenario", resolved.mercenaryAction?.actorName)
        assertEquals("Aldeano1", resolved.mercenaryAction?.targetName)
    }

    @Test
    fun votesKeepLatestVotePerActor() {
        val actions = listOf(
            record("votar", actor = "A", target = "B", phase = GamePhase.VOTACION.name, createdAt = 10),
            record("votar", actor = "A", target = "C", phase = GamePhase.VOTACION.name, createdAt = 20),
            record("votar", actor = "B", target = "C", phase = GamePhase.VOTACION.name, createdAt = 15)
        )

        val votes = OnlineActionResolver.votes(
            records = actions,
            round = 1,
            expectedPhaseName = GamePhase.VOTACION.name
        )

        assertEquals(mapOf("A" to "C", "B" to "C"), votes)
    }

    @Test
    fun votesIgnoreWrongPhaseWrongRoundAndBlankPayloads() {
        val actions = listOf(
            record("votar", actor = "A", target = "B", phase = GamePhase.DESEMPATE_VOTACION.name),
            record("votar", actor = "A", target = "B", round = 2),
            record("votar", actor = "", target = "B"),
            record("votar", actor = "A", target = "")
        )

        val votes = OnlineActionResolver.votes(
            records = actions,
            round = 1,
            expectedPhaseName = GamePhase.VOTACION.name
        )

        assertEquals(emptyMap<String, String>(), votes)
    }

    @Test
    fun absentVoteIsNotInvented() {
        val votes = OnlineActionResolver.votes(
            records = listOf(record("votar", actor = "A", target = "B", phase = GamePhase.VOTACION.name)),
            round = 1,
            expectedPhaseName = GamePhase.VOTACION.name
        )

        assertEquals("B", votes["A"])
        assertNull(votes["C"])
    }

    @Test
    fun actionRecordsKeepPlayerOrdersForFirestoreTraceability() {
        val action = record(
            "votar",
            actor = "Federico #2",
            target = "Federico #7",
            phase = GamePhase.VOTACION.name,
            actorOrder = 1,
            targetOrder = 4
        )

        assertEquals(1, action.actorOrder)
        assertEquals(4, action.targetOrder)
    }

    @Test
    fun actionsFromAnotherMatchOrPhaseIndexAreIgnored() {
        val actions = listOf(
            record("matar", actor = "Asesino", target = "A", matchId = "actual", phaseIndex = 3),
            record("matar", actor = "Asesino", target = "B", matchId = "anterior", phaseIndex = 3),
            record("matar", actor = "Asesino", target = "C", matchId = "actual", phaseIndex = 2)
        )

        val resolved = OnlineActionResolver.nightActions(
            records = actions,
            matchId = "actual",
            round = 1,
            phaseIndex = 3
        )

        assertEquals(mapOf("Asesino" to "A"), resolved.assassinVotes)
    }

    private fun record(
        action: String,
        actor: String,
        target: String,
        phase: String = GamePhase.NOCHE_ASESINO.name,
        round: Int = 1,
        phaseIndex: Int = 1,
        createdAt: Long = 1,
        actorOrder: Int = -1,
        targetOrder: Int = -1,
        matchId: String = ""
    ): OnlineActionRecord {
        return OnlineActionRecord(
            matchId = matchId,
            action = action,
            actorName = actor,
            targetName = target,
            phaseName = phase,
            round = round,
            phaseIndex = phaseIndex,
            createdAtLocal = createdAt,
            actorOrder = actorOrder,
            targetOrder = targetOrder
        )
    }
}
