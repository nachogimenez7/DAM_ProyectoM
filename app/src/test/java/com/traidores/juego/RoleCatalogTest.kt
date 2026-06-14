package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleCatalogTest {

    @Test
    fun exclusiveRolesAreAvailableOnlyOnTheirOwnMap() {
        assertTrue(RoleCatalog.isAvailableOnMap(RoleCatalog.BUFON, RoleMap.MEDIEVAL))
        assertFalse(RoleCatalog.isAvailableOnMap(RoleCatalog.BUFON, RoleMap.GREECE))
        assertTrue(RoleCatalog.isAvailableOnMap(RoleCatalog.ORACULO, RoleMap.GREECE))
        assertFalse(RoleCatalog.isAvailableOnMap(RoleCatalog.ORACULO, RoleMap.PAMPA))
        assertTrue(RoleCatalog.isAvailableOnMap(RoleCatalog.PAYADOR, RoleMap.PAMPA))
    }

    @Test
    fun exclusiveRolesKeepTheirCanonicalTeamsAndRequirements() {
        assertEquals("Neutral", RoleCatalog.definition(RoleCatalog.BUFON).team)
        assertEquals(
            "Rol de Mapa",
            RoleCatalog.definition(RoleCatalog.BUFON).displayCategory
        )
        assertEquals(GameRules.TOWN_WINNER, RoleCatalog.definition(RoleCatalog.ORACULO).team)
        assertEquals(8, RoleCatalog.minimumPlayers(RoleCatalog.BUFON))
        assertEquals(8, RoleCatalog.minimumPlayers(RoleCatalog.ORACULO))
    }

    @Test
    fun jesterDescriptionStatesItsOnlyVictoryCondition() {
        val function = RoleCatalog.definition(RoleCatalog.BUFON).function

        assertTrue(function.contains("molesta", ignoreCase = true))
        assertTrue(function.contains("interrumpe", ignoreCase = true))
        assertTrue(function.contains("unica condicion de victoria", ignoreCase = true))
        assertTrue(function.contains("durante la votacion", ignoreCase = true))
    }

    @Test
    fun rolesScreenAndGameRoleUseTheSameDefinition() {
        val screenRole = RoleCatalog.role(RoleCatalog.MERCENARIO, RoleMap.GREECE)
        val gameRole = RoleCatalog.gameRole(RoleCatalog.MERCENARIO, RoleMap.GREECE)

        assertEquals(screenRole.team, gameRole.team)
        assertEquals(screenRole.imageResName, gameRole.imageResName)
        assertEquals(screenRole.name, gameRole.name)
    }
}
