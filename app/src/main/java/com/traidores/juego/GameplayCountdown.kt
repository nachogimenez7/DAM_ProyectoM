package com.traidores.juego

internal enum class CountdownStage {
    TRANSITION,
    ACTIVE
}

internal class GameplayCountdown {
    var stage: CountdownStage? = null
        private set
    var phaseIndex: Int = NO_PHASE
        private set
    var remainingMs: Long = 0L
        private set
    var totalMs: Long = 0L
        private set
    var running: Boolean = false
        private set

    private var deadlineMs: Long = 0L

    fun restore(
        stage: CountdownStage?,
        phaseIndex: Int,
        remainingMs: Long,
        totalMs: Long
    ) {
        this.stage = stage
        this.phaseIndex = phaseIndex
        this.remainingMs = remainingMs.coerceAtLeast(0L)
        this.totalMs = totalMs.coerceAtLeast(0L)
        running = false
        deadlineMs = 0L
    }

    fun ensurePhase(phaseIndex: Int, transitionDurationMs: Long) {
        if (this.phaseIndex == phaseIndex && stage != null) return
        this.phaseIndex = phaseIndex
        stage = CountdownStage.TRANSITION
        totalMs = transitionDurationMs.coerceAtLeast(0L)
        remainingMs = totalMs
        running = false
        deadlineMs = 0L
    }

    fun start(nowMs: Long): StartResult {
        if (running) return StartResult.ALREADY_RUNNING
        if (remainingMs <= 0L) return StartResult.EXPIRED
        if (totalMs <= 0L) totalMs = remainingMs
        deadlineMs = nowMs + remainingMs
        running = true
        return StartResult.STARTED
    }

    fun tick(nowMs: Long): Tick? {
        if (!running) return null
        remainingMs = (deadlineMs - nowMs).coerceAtLeast(0L)
        if (remainingMs == 0L) running = false
        return Tick(
            remainingMs = remainingMs,
            seconds = kotlin.math.ceil(remainingMs / 1000.0).toInt(),
            expired = remainingMs == 0L
        )
    }

    fun pause(nowMs: Long) {
        if (running) {
            remainingMs = (deadlineMs - nowMs).coerceAtLeast(0L)
        }
        running = false
        deadlineMs = 0L
    }

    fun beginActive(durationMs: Long) {
        stage = CountdownStage.ACTIVE
        totalMs = durationMs.coerceAtLeast(0L)
        remainingMs = totalMs
        running = false
        deadlineMs = 0L
    }

    fun clear() {
        stage = null
        phaseIndex = NO_PHASE
        remainingMs = 0L
        totalMs = 0L
        running = false
        deadlineMs = 0L
    }

    fun isTransitionLocked(currentPhaseIndex: Int): Boolean {
        return stage == CountdownStage.TRANSITION && phaseIndex == currentPhaseIndex
    }

    fun remainingForSave(nowMs: Long): Long {
        return if (running) {
            (deadlineMs - nowMs).coerceAtLeast(0L)
        } else {
            remainingMs
        }
    }

    fun visualTotalMs(transitionMs: Long, activeMs: Long): Long {
        return (transitionMs + activeMs).coerceAtLeast(totalMs)
    }

    fun visualRemainingMs(activeMs: Long): Long {
        return when (stage) {
            CountdownStage.TRANSITION -> remainingMs + activeMs
            CountdownStage.ACTIVE -> remainingMs
            null -> 0L
        }
    }

    data class Tick(
        val remainingMs: Long,
        val seconds: Int,
        val expired: Boolean
    )

    enum class StartResult {
        STARTED,
        ALREADY_RUNNING,
        EXPIRED
    }

    private companion object {
        const val NO_PHASE = -1
    }
}
