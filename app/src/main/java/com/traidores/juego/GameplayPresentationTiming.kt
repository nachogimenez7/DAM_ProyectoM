package com.traidores.juego

/** Reading time uses a monotonic clock, independently of decorative animator speed. */
internal object GameplayPresentationTiming {
    fun remainingMs(startedAtMs: Long, nowMs: Long, durationMs: Long): Long =
        (durationMs.coerceAtLeast(0L) - (nowMs - startedAtMs).coerceAtLeast(0L)).coerceAtLeast(0L)
}
