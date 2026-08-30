package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteCatalogTest {

    @Test
    fun catalogUsesStableUniqueIdsAndEveryEmoteHasDescription() {
        assertEquals(EmoteCatalog.all.size, EmoteCatalog.all.map { it.id }.distinct().size)
        assertTrue(EmoteCatalog.all.all { it.label.isNotBlank() })
        assertTrue(EmoteCatalog.all.all { it.description.isNotBlank() })
    }

    @Test
    fun catalogIsOrganizedIntoClassicMemeAndLegendarySections() {
        val sections = EmoteCatalog.byCategory()

        assertEquals(12, sections.getValue(EmoteCategory.CLASSIC).size)
        assertEquals(6, sections.getValue(EmoteCategory.MEME).size)
        assertEquals(2, sections.getValue(EmoteCategory.LEGENDARY).size)
    }

    @Test
    fun cualquieraStaysWithMemesAndSixSevenIsAnimatedLegendary() {
        val cualquiera = EmoteCatalog.byId("premium_desertor_lengua")!!
        val sixSeven = EmoteCatalog.byId("premium_six_seven")!!

        assertEquals("Cualquiera", cualquiera.label)
        assertEquals(EmoteCategory.MEME, cualquiera.category)
        assertEquals(EmoteCategory.LEGENDARY, sixSeven.category)
        assertTrue(sixSeven.isAnimated)
        assertFalse(cualquiera.isAnimated)
    }
}
