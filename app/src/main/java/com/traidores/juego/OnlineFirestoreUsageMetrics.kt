package com.traidores.juego

/** Observed operations only: no document paths, player data or network calls. */
internal data class FirestoreUsageDelta(
    val listenerStarts: Long = 0,
    val serverSnapshots: Long = 0,
    val documentReads: Long = 0,
    val ruleReads: Long = 0,
    val queryReads: Long = 0,
    val writeAttempts: Long = 0
) {
    val observedReads: Long get() = documentReads + queryReads

    operator fun plus(other: FirestoreUsageDelta) = FirestoreUsageDelta(
        listenerStarts + other.listenerStarts,
        serverSnapshots + other.serverSnapshots,
        documentReads + other.documentReads,
        ruleReads + other.ruleReads,
        queryReads + other.queryReads,
        writeAttempts + other.writeAttempts
    )
}

internal data class FirestoreUsageSnapshot(
    val elapsedSeconds: Long,
    val rows: Map<String, FirestoreUsageDelta>
) {
    val total: FirestoreUsageDelta get() = rows.values.fold(FirestoreUsageDelta()) { sum, row -> sum + row }

    fun reportText(): String = buildString {
        appendLine("Firestore · estimación parcial de este dispositivo (${elapsedSeconds}s):")
        appendLine("Lecturas observadas estimadas: ${total.observedReads}")
        appendLine("Lecturas dependientes de reglas estimadas: ${total.ruleReads}")
        appendLine("Escrituras intentadas: ${total.writeAttempts}")
        appendLine("Desde el inicio o reinicio de la medición en esta ejecución de la app.")
        if (rows.isEmpty()) appendLine("- sin operaciones instrumentadas")
        rows.entries.sortedByDescending { it.value.observedReads + it.value.ruleReads }.forEach { (name, row) ->
            appendLine("- $name: lecturas=${row.observedReads}, reglas≈${row.ruleReads}, " +
                "escrituras_intentadas=${row.writeAttempts}, escuchas_abiertas=${row.listenerStarts}")
        }
        appendLine("No es el total facturado ni la cuota restante del proyecto. No incluye todas las operaciones,")
        appendLine("reintentos internos ni lecturas de reglas; las consultas instrumentadas pueden incluir intentos.")
        appendLine("Las escuchas excluyen caché y cambios locales pendientes. Reiniciar la app reinicia estos contadores.")
        append("Emotes y chat usan RTDB: su tráfico se analiza por separado.")
    }
}

/** Survives Activity transitions; intentionally resets with the process or an explicit user reset. */
internal class OnlineFirestoreUsageSession(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var startedAtMs = nowMs()
    private val rows = linkedMapOf<String, FirestoreUsageDelta>()

    fun counter(scope: String) = OnlineFirestoreUsageCounter { name, delta -> record("$scope/$name", delta) }

    @Synchronized
    private fun record(name: String, delta: FirestoreUsageDelta) {
        rows[name] = (rows[name] ?: FirestoreUsageDelta()) + delta
    }

    @Synchronized
    fun snapshot() = FirestoreUsageSnapshot(
        elapsedSeconds = ((nowMs() - startedAtMs).coerceAtLeast(0) / 1000),
        rows = rows.toMap()
    )

    @Synchronized
    fun reset() {
        rows.clear()
        startedAtMs = nowMs()
    }
}

internal object OnlineFirestoreUsageMetrics {
    private val session = OnlineFirestoreUsageSession { android.os.SystemClock.elapsedRealtime() }
    fun counter(scope: String) = session.counter(scope)
    fun snapshot() = session.snapshot()
    fun reset() = session.reset()
}
