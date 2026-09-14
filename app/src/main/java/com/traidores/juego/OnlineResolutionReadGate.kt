package com.traidores.juego

/** A read belongs to one authority lifetime and one exact game window. */
class OnlineResolutionReadGate {
    data class Window(val matchId: String, val round: Int, val phaseIndex: Int, val phase: String)
    data class Ticket(val generation: Long, val window: Window)

    private var generation = 0L
    private var pending: Ticket? = null
    val inProgress: Boolean get() = pending != null

    fun begin(window: Window): Ticket? {
        if (pending?.window == window) return null
        return Ticket(++generation, window).also { pending = it }
    }

    fun isCurrent(ticket: Ticket, window: Window, isHost: Boolean, active: Boolean): Boolean =
        active && isHost && pending == ticket && ticket.window == window

    fun acceptServerResult(
        ticket: Ticket, window: Window, isHost: Boolean, active: Boolean,
        fromCache: Boolean, pendingWrites: Boolean
    ): Boolean {
        if (!isCurrent(ticket, window, isHost, active) || fromCache || pendingWrites) return false
        pending = null
        return true
    }

    fun invalidate() {
        generation++
        pending = null
    }

    companion object {
        fun retryDelayMs(attempt: Int): Long = (1_000L shl attempt.coerceIn(0, 3)).coerceAtMost(5_000L)
    }
}
