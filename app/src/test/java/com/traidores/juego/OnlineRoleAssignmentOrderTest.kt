package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRoleAssignmentOrderTest {

    @Test
    fun assignRolesPreservesTheCapturedTenPlayerOrder() {
        val names = (1..10).map { index -> "Jugador $index" }
        val uids = names.indices.map { index -> "uid-$index" }
        val session = GameSession(
            code = "ONLINE-ORDER-10",
            mapKey = "pampa",
            mapName = "Pampa",
            players = names.mapIndexed { index, name ->
                GamePlayer(name, "J${index + 1}", isHuman = index == 0)
            },
            onlinePlayerUids = uids,
            roleComposition = LocalGameFactory.onlineSafeRoleComposition(names.size)
        )

        val assigned = LocalGameFactory.assignRoles(session)

        assertEquals(names, assigned.players.map { it.name })
        assertEquals(uids, assigned.onlinePlayerUids)
    }
}
