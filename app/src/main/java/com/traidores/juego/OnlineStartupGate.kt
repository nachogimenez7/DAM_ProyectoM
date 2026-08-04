package com.traidores.juego

data class OnlineStartupClientState(
    val uid: String,
    val inGameplay: Boolean,
    val visiblePlayers: Int,
    val phase: String,
    val phaseIndex: Int,
    val roleRead: Boolean,
    val order: Int
)

data class OnlineStartupGateResult(
    val expectedPlayers: Int,
    val loadedPlayers: Int,
    val readyPlayers: Int,
    val mismatchedPlayers: Int,
    val canStart: Boolean
) {
    val missingPlayers: Int
        get() = (expectedPlayers - loadedPlayers).coerceAtLeast(0)

    val canArmAutoStart: Boolean
        get() = expectedPlayers > 0 &&
            loadedPlayers >= expectedPlayers &&
            mismatchedPlayers == 0

    val waitingMessage: String
        get() = when {
            expectedPlayers <= 0 -> "Sincronizando sala online..."
            mismatchedPlayers > 0 -> "Sincronizando cartas..."
            loadedPlayers < expectedPlayers -> "Esperando $missingPlayers jugador(es)..."
            readyPlayers < expectedPlayers -> "Esperando lectura de roles..."
            else -> "Listos para comenzar la noche."
        }
}

object OnlineStartupGate {
    const val AUTO_START_AFTER_MS = 15_000L
    const val STARTUP_PHASE_SYNCING = "sincronizando"
    const val STARTUP_PHASE_READING = "leyendo_rol"
    const val STARTUP_PHASE_READY = "rol_leido"
    const val STARTUP_PHASE_IN_MATCH = "en_partida"

    fun evaluate(
        expectedPlayers: Int,
        clientStates: Collection<OnlineStartupClientState>
    ): OnlineStartupGateResult {
        val safeExpectedPlayers = expectedPlayers.coerceAtLeast(0)
        val startupStates = clientStates
            .filter { it.inGameplay && it.phase == GamePhase.REPARTO.name && it.phaseIndex == 0 }
            .distinctBy { it.uid }

        val loadedPlayers = startupStates
            .count { it.visiblePlayers == safeExpectedPlayers }
            .coerceAtMost(safeExpectedPlayers)
        val readyPlayers = startupStates
            .count { it.visiblePlayers == safeExpectedPlayers && it.roleRead }
            .coerceAtMost(safeExpectedPlayers)
        val mismatchedPlayers = startupStates
            .count { it.visiblePlayers != safeExpectedPlayers }

        val canStart = safeExpectedPlayers > 0 &&
            loadedPlayers >= safeExpectedPlayers &&
            readyPlayers >= safeExpectedPlayers &&
            mismatchedPlayers == 0

        return OnlineStartupGateResult(
            expectedPlayers = safeExpectedPlayers,
            loadedPlayers = loadedPlayers,
            readyPlayers = readyPlayers,
            mismatchedPlayers = mismatchedPlayers,
            canStart = canStart
        )
    }

    fun remainingAutoStartMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long? {
        if (deadlineEpochMs <= 0L) return null
        return (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
    }

    fun shouldAutoStart(deadlineEpochMs: Long, nowEpochMs: Long): Boolean {
        return deadlineEpochMs > 0L && nowEpochMs >= deadlineEpochMs
    }
}
