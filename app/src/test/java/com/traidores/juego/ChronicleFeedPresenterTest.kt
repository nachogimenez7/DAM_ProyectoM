package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleFeedPresenterTest {
    @Test
    fun entriesInsertDayDividersWhenRoundChanges() {
        val entries = ChronicleFeedPresenter.entries(
            listOf(
                god("Dia 1: nadie murio esta noche."),
                player("Valen", "no nos durmamos"),
                god("Dia 2: Lautaro fue expulsado."),
                god("La oscuridad vuelve a caer.")
            )
        )

        assertEquals(
            listOf(
                ChronicleEntryKind.DAY_DIVIDER,
                ChronicleEntryKind.DAWN,
                ChronicleEntryKind.PLAYER,
                ChronicleEntryKind.DAY_DIVIDER,
                ChronicleEntryKind.EXPULSION,
                ChronicleEntryKind.NIGHT
            ),
            entries.map { it.kind }
        )
        assertEquals("DIA 1", entries[0].text)
        assertEquals("DIA 2", entries[3].text)
    }

    @Test
    fun showOnlyEventsFiltersPlayerMessages() {
        val entries = ChronicleFeedPresenter.entries(
            listOf(
                player("Mora", "yo no cierro con Thiago"),
                god("Noche 2: el pueblo cierra sus puertas.")
            ),
            showOnlyEvents = true
        )

        assertFalse(entries.any { it.kind == ChronicleEntryKind.PLAYER })
        assertEquals(ChronicleEntryKind.NIGHT, entries.last().kind)
    }

    @Test
    fun noDeathAnnouncementIsDawnNotDeath() {
        val entry = ChronicleFeedPresenter.entryFor(god("Dia 1: nadie murio esta noche."))

        assertEquals(ChronicleEntryKind.DAWN, entry.kind)
        assertTrue(entry.tone == ChronicleTone.DAWN)
    }

    @Test
    fun accentedDayStillCreatesRoundDivider() {
        val entries = ChronicleFeedPresenter.entries(
            listOf(god("Día 3: el pueblo despierta sin bajas."))
        )

        assertEquals(ChronicleEntryKind.DAY_DIVIDER, entries.first().kind)
        assertEquals("DIA 3", entries.first().text)
    }

    @Test
    fun specialVictoryIsSeparatedFromFactionEvents() {
        val entry = ChronicleFeedPresenter.entryFor(god("Victoria especial: Bufon gano al ser expulsado."))

        assertEquals(ChronicleEntryKind.SPECIAL_VICTORY, entry.kind)
        assertEquals(ChronicleTone.SPECIAL, entry.tone)
    }

    @Test
    fun roleCompositionAnnouncementHasItsOwnEventKind() {
        val entry = ChronicleFeedPresenter.entryFor(
            god(
                "Dios preparo una partida local. En juego: 3 Aldeanos, 1 Detective. " +
                    "Todos conocen la composicion; las identidades siguen ocultas."
            )
        )

        assertEquals(ChronicleEntryKind.ROLE_COMPOSITION, entry.kind)
        assertEquals(ChronicleTone.SYSTEM, entry.tone)
    }

    private fun god(message: String): GameChatMessage {
        return GameChatMessage(GameplayFeedMessages.GOD_SPEAKER, message, isGod = true)
    }

    private fun player(speaker: String, message: String): GameChatMessage {
        return GameChatMessage(speaker, message, isGod = false)
    }
}
