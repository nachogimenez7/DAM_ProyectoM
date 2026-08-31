package com.traidores.juego

/**
 * Estimacion local de lecturas visibles por listener. No reemplaza la consola de Firebase:
 * permite atribuir el consumo a cada flujo y agrega por separado las lecturas dependientes
 * conocidas de las reglas actuales.
 */
internal class OnlineFirestoreUsageCounter {
    private data class Entry(
        var listenerStarts: Int = 0,
        var serverSnapshots: Int = 0,
        var visibleDocumentReads: Int = 0,
        var dependentRuleReads: Int = 0,
        var forcedQueryReads: Int = 0,
        var writes: Int = 0
    )

    private val entries = linkedMapOf<String, Entry>()
    private val listenersWithServerBaseline = mutableSetOf<String>()

    @Synchronized
    fun listenerStarted(name: String) {
        entries.getOrPut(name) { Entry() }.listenerStarts += 1
        listenersWithServerBaseline.remove(name)
    }

    @Synchronized
    fun serverSnapshot(
        name: String,
        fromCache: Boolean,
        pendingWrites: Boolean,
        changedDocuments: Int,
        resultDocuments: Int,
        dependentDocuments: Int = 0
    ) {
        if (fromCache || pendingWrites) return
        val entry = entries.getOrPut(name) { Entry() }
        val firstServerSnapshot = listenersWithServerBaseline.add(name)
        val visibleReads = when {
            changedDocuments > 0 -> changedDocuments
            firstServerSnapshot -> resultDocuments.coerceAtLeast(1)
            else -> 0
        }
        entry.serverSnapshots += 1
        entry.visibleDocumentReads += visibleReads
        if (visibleReads > 0) {
            entry.dependentRuleReads += dependentDocuments
        }
    }

    @Synchronized
    fun forcedQuery(name: String, resultDocuments: Int, dependentDocuments: Int = 0) {
        val entry = entries.getOrPut(name) { Entry() }
        entry.forcedQueryReads += resultDocuments.coerceAtLeast(1)
        entry.dependentRuleReads += dependentDocuments
    }

    @Synchronized
    fun write(name: String) {
        entries.getOrPut(name) { Entry() }.writes += 1
    }

    @Synchronized
    fun summary(): String {
        if (entries.isEmpty()) return "sin_actividad"
        return entries.entries.joinToString(";") { (name, entry) ->
            "$name[start=${entry.listenerStarts},snap=${entry.serverSnapshots}," +
                "docs=${entry.visibleDocumentReads},rules=${entry.dependentRuleReads}," +
                "forced=${entry.forcedQueryReads},writes=${entry.writes}]"
        }
    }
}
