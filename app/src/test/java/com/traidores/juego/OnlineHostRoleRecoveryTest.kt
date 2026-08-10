package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineHostRoleRecoveryTest {

    @Test
    fun restoresEveryRoleBeforeFivePlayerHandoff() {
        val restored = OnlineHostRoleRecovery.restore(session(5), assignments(5))

        assertEquals(5, restored?.players?.count { it.role != null })
        assertEquals("asesino", restored?.players?.get(1)?.role?.key)
    }

    @Test
    fun restoresEveryRoleBeforeFifteenPlayerHandoff() {
        val restored = OnlineHostRoleRecovery.restore(session(15), assignments(15))

        assertEquals(15, restored?.players?.count { it.role != null })
        assertTrue(restored?.players?.all { it.role?.team?.isNotBlank() == true } == true)
    }

    @Test
    fun refusesAuthorityWhenOnePrivateRoleIsMissing() {
        assertNull(OnlineHostRoleRecovery.restore(session(8), assignments(7)))
    }

    @Test
    fun refusesConflictingDuplicateAssignment() {
        val conflicting = assignments(5) + role(1, "medico", "Pueblo")

        assertNull(OnlineHostRoleRecovery.restore(session(5), conflicting))
    }

    private fun session(playerCount: Int): GameSession = GameSession(
        code = "TEST",
        mapKey = "medieval",
        mapName = "Medieval",
        players = List(playerCount) { index ->
            GamePlayer(
                name = "Jugador $index",
                initial = "J",
                role = if (index == 0) GameRole("policia", "Detective", "Pueblo", "detective") else null,
                isHuman = index == 0
            )
        }
    )

    private fun assignments(playerCount: Int): List<Map<String, Any?>> =
        List(playerCount) { index ->
            if (index == 1) role(index, "asesino", "Traidores")
            else role(index, "aldeano_$index", "Pueblo")
        }

    private fun role(order: Int, key: String, team: String): Map<String, Any?> = mapOf(
        "orden" to order,
        "rolKey" to key,
        "rolNombre" to key,
        "rolEquipo" to team,
        "rolImagen" to "rol_$key"
    )
}
