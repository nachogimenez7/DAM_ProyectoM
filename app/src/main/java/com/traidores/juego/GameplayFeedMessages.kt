package com.traidores.juego

object GameplayFeedMessages {
    const val GOD_SPEAKER = "Dios"
    const val MAX_FEED_MESSAGES = 60

    fun appendGodEvents(
        chatHistory: List<GameChatMessage>,
        events: List<String>
    ): List<GameChatMessage> {
        if (events.isEmpty()) return chatHistory.takeLast(MAX_FEED_MESSAGES)
        val existingGodMessages = chatHistory
            .filter { it.isGod }
            .map { it.message }
            .toSet()
        val newEvents = events
            .filter { it.isNotBlank() }
            .filterNot { it in existingGodMessages }
            .map { GameChatMessage(GOD_SPEAKER, it, isGod = true) }
        return (chatHistory + newEvents).takeLast(MAX_FEED_MESSAGES)
    }
}
