package com.traidores.juego

data class OnlinePresentationParticipant(
    val uid: String,
    val connected: Boolean,
    val alive: Boolean,
    val acknowledgedKey: String
)

data class OnlinePresentationProgress(
    val ready: Int,
    val total: Int
) {
    val allReady: Boolean
        get() = total > 0 && ready == total
}

object OnlinePresentationGate {
    const val MINIMUM_DISPLAY_MS = 3_000L
    const val MAXIMUM_DISPLAY_MS = 6_000L

    fun progress(
        presentationKey: String,
        participants: List<OnlinePresentationParticipant>
    ): OnlinePresentationProgress {
        val eligible = participants.filter { it.connected && it.alive }
        return OnlinePresentationProgress(
            ready = eligible.count { it.acknowledgedKey == presentationKey },
            total = eligible.size
        )
    }

    fun canAcknowledge(elapsedMs: Long): Boolean {
        return elapsedMs >= MINIMUM_DISPLAY_MS
    }

    fun shouldAdvance(
        isCoordinator: Boolean,
        elapsedMs: Long,
        progress: OnlinePresentationProgress,
        coordinatorPresentationReady: Boolean = true
    ): Boolean {
        if (!isCoordinator) return false
        // El timeout destraba esperas de red o jugadores que no confirman; nunca debe cortar
        // una animacion obligatoria que todavia esta ejecutandose en el coordinador.
        if (!coordinatorPresentationReady) return false
        if (elapsedMs >= MAXIMUM_DISPLAY_MS) return true
        return canAcknowledge(elapsedMs) && progress.allReady
    }
}
