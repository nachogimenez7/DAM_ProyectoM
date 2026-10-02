package com.traidores.juego

/** Keeps the player's description intact within the existing feedback rule's 1200-character limit. */
internal object FeedbackSubmission {
    const val MAX_DESCRIPTION = 600
    private const val MAX_MESSAGE = 1200

    fun message(description: String, matchContext: String): String {
        if (matchContext.isBlank()) return description.take(MAX_MESSAGE)
        val text = description.take(MAX_DESCRIPTION)
        val separator = "\n\nResumen de la partida:\n"
        return text + separator + matchContext.take(MAX_MESSAGE - text.length - separator.length)
    }
}
