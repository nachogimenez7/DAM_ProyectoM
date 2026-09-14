package com.traidores.juego

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock

/** Local measurements only. UID traffic includes Auth/Firestore/RTDB and is not a Firebase bill. */
internal object OnlineNetworkMetrics {
    private var match = ""
    private var startedAt = 0L
    private var receivedAtStart = -1L
    private var sentAtStart = -1L
    private val timings = linkedMapOf<String, MutableList<Long>>()
    private val counts = linkedMapOf<String, Long>()

    @Synchronized fun beginMatch(matchId: String) {
        if (matchId.isBlank() || matchId == match) return
        match = matchId
        startedAt = SystemClock.elapsedRealtime()
        receivedAtStart = TrafficStats.getUidRxBytes(Process.myUid())
        sentAtStart = TrafficStats.getUidTxBytes(Process.myUid())
        timings.clear()
        counts.clear()
    }

    @Synchronized fun count(name: String) { counts[name] = (counts[name] ?: 0L) + 1L }
    @Synchronized fun duration(name: String, elapsedMs: Long) {
        val samples = timings.getOrPut(name) { mutableListOf() }
        if (samples.size == 256) samples.removeAt(0)
        samples.add(elapsedMs.coerceAtLeast(0L))
    }

    @Synchronized fun summary(): String {
        if (startedAt == 0L) return "Medición de red: sin partida medida."
        fun delta(current: Long, baseline: Long): String =
            if (baseline < 0 || current < baseline) "no disponible" else "${current - baseline} bytes"
        return buildString {
            appendLine("Red de la aplicación desde el inicio (${(SystemClock.elapsedRealtime() - startedAt) / 1000}s):")
            appendLine("Recibido: ${delta(TrafficStats.getUidRxBytes(Process.myUid()), receivedAtStart)}")
            appendLine("Enviado: ${delta(TrafficStats.getUidTxBytes(Process.myUid()), sentAtStart)}")
            appendLine("Incluye todos los servicios de la app; no equivale a descarga facturada por RTDB.")
            counts.forEach { (name, count) -> appendLine("$name: $count") }
            timings.forEach { (name, values) ->
                val sorted = values.sorted()
                appendLine("$name: n=${sorted.size}, p50=${sorted[(sorted.size - 1) / 2]}ms, p95=${sorted[((sorted.size - 1) * 0.95).toInt()]}ms")
            }
        }
    }
}
