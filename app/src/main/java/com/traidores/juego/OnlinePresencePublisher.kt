package com.traidores.juego

/** Orders presence writes and ignores completions from a previous connection/access epoch. */
internal class OnlinePresencePublisher(
    private val registerDisconnect: (((Exception?) -> Unit) -> Unit),
    private val writePresence: (Boolean, (Exception?) -> Unit) -> Unit,
    private val onReady: () -> Unit,
    private val onUnavailable: () -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var generation = 0L

    fun invalidate() {
        generation += 1L
        onUnavailable()
    }

    fun publishOnline() {
        val request = ++generation
        registerDisconnect { error ->
            if (request != generation) return@registerDisconnect
            if (error != null) {
                invalidate()
                onError(error)
                return@registerDisconnect
            }
            writePresence(true) { failure ->
                if (request != generation) return@writePresence
                if (failure == null) {
                    onReady()
                } else {
                    invalidate()
                    onError(failure)
                }
            }
        }
    }

    fun publishOffline(reportFailure: Boolean) {
        val request = ++generation
        // Queue offline immediately, before any later online write. Waiting for
        // onDisconnect.cancel() used to let a delayed callback overwrite a reconnect.
        // Keep the server disconnect operation armed: losing the socket before this
        // write is acknowledged must still result in an offline presence.
        writePresence(false) { failure ->
            if (reportFailure && request == generation && failure != null) onError(failure)
        }
    }
}
