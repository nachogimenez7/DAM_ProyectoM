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

    fun progress(
        presentationKey: String,
        participants: List<OnlinePresentationParticipant>
    ): OnlinePresentationProgress {
        val eligible = participants.filter { it.connected }
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
        if (!coordinatorPresentationReady) return false
        // Un timeout no puede saltar una presentación pública en un cliente conectado. Si un
        // participante se desconecta deja de formar parte de progress; si sigue conectado debe
        // confirmar la misma clave antes de que el coordinador publique la fase siguiente.
        return canAcknowledge(elapsedMs) && progress.allReady
    }
}
