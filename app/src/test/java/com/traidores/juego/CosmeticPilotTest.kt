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

    @Test
    fun newProfilesStartWithTheClassicTheme() {
        assertEquals(CosmeticPilot.THEME_CLASSIC, CosmeticPilot.DEFAULT_THEME)
    }

    @Test
    fun automaticLegacySpaceThemeMigratesToClassicButExplicitSpaceIsPreserved() {
        assertEquals(
            CosmeticPilot.THEME_CLASSIC,
            CosmeticPilot.resolveStoredTheme(CosmeticPilot.THEME_SPACE, explicitlySelected = false)
        )
        assertEquals(
            CosmeticPilot.THEME_SPACE,
            CosmeticPilot.resolveStoredTheme(CosmeticPilot.THEME_SPACE, explicitlySelected = true)
        )
    }

    @Test
    fun botsAlwaysUseTheClassicTheme() {
        assertEquals(
            CosmeticPilot.THEME_CLASSIC,
            BotProfileFactory.profileFor("Thiago").cosmeticThemeId
        )
        assertEquals(
            CosmeticPilot.THEME_CLASSIC,
            BotProfileFactory.profileFor("Bot de prueba").cosmeticThemeId
        )
    }
}
