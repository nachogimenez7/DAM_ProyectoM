package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayThemeResolverTest {

    @Test
    fun themeFromIntentOrSessionPrefersExplicitIntentTheme() {
        assertEquals("medieval", GameplayThemeResolver.themeFromIntentOrSession("medieval", "pampa"))
        assertEquals("griego", GameplayThemeResolver.themeFromIntentOrSession("griego", "medieval"))
        assertEquals("gaucho", GameplayThemeResolver.themeFromIntentOrSession("gaucho", "grecia"))
    }

    @Test
    fun themeFromIntentOrSessionFallsBackToSessionMapKey() {
        assertEquals("gaucho", GameplayThemeResolver.themeFromIntentOrSession(null, "pampa"))
        assertEquals("medieval", GameplayThemeResolver.themeFromIntentOrSession("", "medieval"))
        assertEquals("griego", GameplayThemeResolver.themeFromIntentOrSession("invalid_theme", "grecia"))
    }

    @Test
    fun backgroundDrawableForResolvesVerticalDrawables() {
        assertEquals(
            R.drawable.mapa_medieval_vertical_dia,
            GameplayThemeResolver.backgroundDrawableFor("medieval", isNight = false, isVertical = true)
        )
        assertEquals(
            R.drawable.mapa_medieval_vertical_noche,
            GameplayThemeResolver.backgroundDrawableFor("medieval", isNight = true, isVertical = true)
        )
        assertEquals(
            R.drawable.mapa_grecia_vertical_dia,
            GameplayThemeResolver.backgroundDrawableFor("griego", isNight = false, isVertical = true)
        )
        assertEquals(
            R.drawable.mapa_grecia_vertical_noche,
            GameplayThemeResolver.backgroundDrawableFor("griego", isNight = true, isVertical = true)
        )
        assertEquals(
            R.drawable.mapa_pampa_vertical_dia,
            GameplayThemeResolver.backgroundDrawableFor("gaucho", isNight = false, isVertical = true)
        )
        assertEquals(
            R.drawable.mapa_pampa_vertical_noche,
            GameplayThemeResolver.backgroundDrawableFor("gaucho", isNight = true, isVertical = true)
        )
    }

    @Test
    fun logDrawableForResolvesThemeLog() {
        assertEquals(R.drawable.log_medieval, GameplayThemeResolver.logDrawableFor("medieval"))
        assertEquals(R.drawable.log_griego, GameplayThemeResolver.logDrawableFor("griego"))
        assertEquals(R.drawable.log_gaucho, GameplayThemeResolver.logDrawableFor("gaucho"))
        assertEquals(R.drawable.log_gaucho, GameplayThemeResolver.logDrawableFor("unknown"))
    }
}
