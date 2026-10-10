package com.traidores.juego

/** Publication can lag behind the start receipt. A real loss after delivery never retries. */
internal class ServerGameInitialAccessRetry {
    private var attempts = 0
    private var delivered = false
    fun nextDelay(): Long? = if (delivered || attempts >= 20) null else { attempts++; 1500L }
    fun confirmed() { delivered = true }
    fun resetAttempts() { attempts = 0 }
}
