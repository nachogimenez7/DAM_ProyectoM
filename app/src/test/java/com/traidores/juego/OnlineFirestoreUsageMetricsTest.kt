package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineFirestoreUsageMetricsTest {
    @Test
    fun retainsMeasurementsAcrossLobbyGameplayAndReturningLobby() {
        var now = 1_000L
        val session = OnlineFirestoreUsageSession { now }
        session.counter("lobby").apply {
            listenerStarted("players")
            serverSnapshot("players", false, false, 15, 15, 1)
            write("ready")
        }
        session.counter("partida").apply {
            forcedQuery("roles", 15, 1)
            write("checkpoint")
        }
        session.counter("lobby").apply {
            listenerStarted("players")
            serverSnapshot("players", false, false, 15, 15, 1)
        }
        now += 90_000
        val snapshot = session.snapshot()
        assertEquals(90, snapshot.elapsedSeconds.toInt())
        assertEquals(45L, snapshot.total.observedReads)
        assertEquals(3L, snapshot.total.ruleReads)
        assertEquals(2L, snapshot.total.writeAttempts)
        assertEquals(30L, snapshot.rows.getValue("lobby/players").documentReads)
        assertEquals(2L, snapshot.rows.getValue("lobby/players").listenerStarts)
    }

    @Test
    fun cachePendingAndRepeatedMetadataDoNotInflateSessionEstimate() {
        val session = OnlineFirestoreUsageSession { 0L }
        val counter = session.counter("buscador")
        counter.listenerStarted("salas")
        counter.serverSnapshot("salas", true, false, 30, 30, 1)
        counter.serverSnapshot("salas", false, true, 30, 30, 1)
        counter.serverSnapshot("salas", false, false, 0, 0, 1)
        counter.serverSnapshot("salas", false, false, 0, 0, 1)
        assertEquals(1L, session.snapshot().total.observedReads)
        assertEquals(1L, session.snapshot().total.ruleReads)
    }

    @Test
    fun resettingStartsANewWindowAndExistingCountersContinueWithoutOldTotals() {
        var now = 100L
        val session = OnlineFirestoreUsageSession { now }
        val counter = session.counter("partida")
        counter.listenerStarted("actions")
        counter.serverSnapshot("actions", false, false, 10, 10, 1)
        val beforeReset = session.snapshot()
        now = 20_100L
        session.reset()
        assertTrue(session.snapshot().rows.isEmpty())
        counter.serverSnapshot("actions", false, false, 1, 10, 1)
        counter.write("action")
        now += 5_000
        val afterReset = session.snapshot()
        assertEquals(1L, afterReset.total.observedReads)
        assertEquals(1L, afterReset.total.writeAttempts)
        assertEquals(5L, afterReset.elapsedSeconds)
        assertEquals(10L, beforeReset.total.observedReads)
    }

    @Test
    fun reportExplainsPartialScopeAndSeparatesRuleReadsAndWriteAttempts() {
        val session = OnlineFirestoreUsageSession { 0L }
        session.counter("lobby").forcedQuery("preflight", 15, 1)
        val report = session.snapshot().reportText()
        assertTrue(report.contains("Lecturas observadas estimadas: 15"))
        assertTrue(report.contains("Lecturas dependientes de reglas estimadas: 1"))
        assertTrue(report.contains("No es el total facturado ni la cuota restante"))
        assertTrue(report.contains("RTDB"))
        assertTrue(report.contains("Reiniciar la app reinicia estos contadores"))
    }
}
