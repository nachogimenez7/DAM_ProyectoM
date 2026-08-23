package com.traidores.juego

import kotlin.math.ceil

/**
 * Availability policy for large online rooms.
 *
 * The full roster remains authoritative, but a transient client-side acknowledgement must not
 * freeze every other device forever. Three quarters allows a bounded number of delayed clients
 * in larger rooms to catch up from the authoritative state. A three-player test room has an
 * explicit 2/3 fallback: with a strict 3/3 gate, one lost private-role acknowledgement made the
 * smallest room the only one that could never recover.
 */
object OnlineStartQuorum {
    const val REQUIRED_PERCENT = 75

    fun requiredPlayers(expectedPlayers: Int, minimumPlayers: Int = 3): Int {
        if (expectedPlayers <= 0) return 0
        if (expectedPlayers == 3) return 2
        val percentage = ceil(expectedPlayers * (REQUIRED_PERCENT / 100.0)).toInt()
        return maxOf(minimumPlayers, percentage).coerceAtMost(expectedPlayers)
    }

    fun isReached(expectedPlayers: Int, readyPlayers: Int, connectedPlayers: Int): Boolean {
        val required = requiredPlayers(expectedPlayers)
        // En la sala mínima toleramos una confirmación demorada, no un dispositivo ausente:
        // los tres deben seguir presentes aunque baste con que dos hayan completado el ACK.
        val requiredConnected = if (expectedPlayers == 3) expectedPlayers else required
        return required > 0 &&
            readyPlayers >= required &&
            connectedPlayers >= requiredConnected
    }
}
