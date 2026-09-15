package com.traidores.juego

/** Orders publications without depending on the new coordinator's wall clock. */
internal object OnlineStateOrder {
    fun isNewer(candidate: Map<String, Any?>, previous: Map<String, Any?>?): Boolean {
        if (previous == null) return true
        fun number(state: Map<String, Any?>, key: String) = (state[key] as? Number)?.toLong() ?: 0L
        val phase = number(candidate, "phaseIndex")
        val oldPhase = number(previous, "phaseIndex")
        if (phase < oldPhase) return false
        val epoch = number(candidate, "authorityEpoch")
        val oldEpoch = number(previous, "authorityEpoch")
        if (epoch != oldEpoch) return epoch > oldEpoch
        val sequence = number(candidate, "stateSequence")
        val oldSequence = number(previous, "stateSequence")
        if (sequence > 0L && oldSequence > 0L) return sequence > oldSequence
        if (phase != oldPhase) return phase > oldPhase
        // Legacy clients do not publish sequences. A host change invalidates its clock baseline.
        if (candidate["actualizadaPor"] != previous["actualizadaPor"]) return true
        return candidate != previous && number(candidate, "actualizadaEnLocal") >= number(previous, "actualizadaEnLocal")
    }
}

/** Keeps each received presentation until the UI can display it; coalesces its state updates. */
internal class OnlineStateInbox {
    private val pending = linkedMapOf<String, Map<String, Any?>>()
    private var newest: Map<String, Any?>? = null
    val size: Int get() = pending.size

    fun newestPhaseIndex(): Int? = (newest?.get("phaseIndex") as? Number)?.toInt()

    fun offer(state: Map<String, Any?>): Boolean {
        if (!OnlineStateOrder.isNewer(state, newest)) return false
        newest = state.toMap()
        val key = listOf(state["phaseIndex"], state["presentacionVotacion"], state["ganador"]).joinToString("|")
        pending[key] = state.toMap()
        return true
    }

    fun poll(): Map<String, Any?>? {
        val key = pending.keys.firstOrNull() ?: return null
        return pending.remove(key)
    }

    /** Returns live state after a background gap and discards intermediate obsolete frames. */
    fun pollNewestAndDropOlder(): Map<String, Any?>? {
        val result = newest?.toMap() ?: return null
        pending.clear()
        return result
    }

    fun clear() {
        pending.clear()
        newest = null
    }
}
