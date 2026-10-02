package com.traidores.juego

internal object FeedbackQuota {
    const val WINDOW_MS = 24 * 60 * 60 * 1000L
    const val INTERVAL_MS = 5 * 60 * 1000L

    fun rejection(nowMs: Long, windowStartMs: Long?, lastSentMs: Long?, count: Long): String? {
        if (lastSentMs != null && nowMs - lastSentMs < INTERVAL_MS) {
            return "Ya recibimos tu mensaje. Esperá 5 minutos entre reportes."
        }
        if (windowStartMs != null && nowMs - windowStartMs < WINDOW_MS && count >= 3) {
            return "Ya enviaste 3 reportes. Podrás enviar otro cuando se cumplan 24 horas desde el primero."
        }
        return null
    }
}
