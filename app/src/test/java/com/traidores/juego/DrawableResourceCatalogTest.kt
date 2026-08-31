package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DrawableResourceCatalogTest {
    @Test
    fun `todos los mapas y roles publicados tienen un drawable estatico`() {
        val mapResources = mapOf(
            RoleMap.PAMPA to "mapa_pampa",
            RoleMap.GREECE to "mapa_grecia",
            RoleMap.MEDIEVAL to "mapa_medieval"
        )

        mapResources.forEach { (map, resourceName) ->
            assertNotEquals(0, DrawableResourceCatalog.resolve(resourceName))
            RoleCatalog.rolesForMap(map).forEach { role ->
                assertNotEquals(
                    "Falta ${role.imageResName} para ${role.name}",
                    0,
                    DrawableResourceCatalog.resolve(role.imageResName)
                )
            }
        }
    }

    @Test
    fun `un nombre desconocido usa placeholder`() {
        assertEquals(0, DrawableResourceCatalog.resolve("drawable_inexistente"))
        assertEquals(
            R.drawable.placeholder_local,
            DrawableResourceCatalog.resolveOrPlaceholder("drawable_inexistente")
        )
    }
}
