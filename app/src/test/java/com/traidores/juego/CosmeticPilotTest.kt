package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticPilotTest {
    @Test
    fun allPublishedThemesAreAccepted() {
        val themes = listOf(
            CosmeticPilot.THEME_CLASSIC,
            CosmeticPilot.THEME_SPACE,
            CosmeticPilot.THEME_SEA,
            CosmeticPilot.THEME_FIRE
        )

        themes.forEach { theme ->
            assertEquals(theme, CosmeticPilot.normalizeTheme(theme))
        }
        assertNull(CosmeticPilot.normalizeTheme("unknown"))
    }

    @Test
    fun seaAndFireAreDecoratedThemes() {
        assertTrue(CosmeticPilot.isDecoratedTheme(CosmeticPilot.THEME_SEA))
        assertTrue(CosmeticPilot.isDecoratedTheme(CosmeticPilot.THEME_FIRE))
    }
}
