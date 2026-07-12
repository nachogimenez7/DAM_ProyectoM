package com.traidores.juego

object GameplayFeedMessages {
    const val GOD_SPEAKER = "Dios"
    const val MAX_FEED_MESSAGES = 60

    fun appendGodEvents(
        chatHistory: List<GameChatMessage>,
        events: List<String>
    ): List<GameChatMessage> {
        if (events.isEmpty()) return chatHistory.takeLastPerChannel(MAX_FEED_MESSAGES)
        val existingGodMessages = chatHistory
            .filter { it.channel == ChatChannel.PUBLICO && it.isGod }
            .map { it.message }
            .toSet()
        val newEvents = events
            .filter { it.isNotBlank() }
            .filterNot { it in existingGodMessages }
            .map { GameChatMessage(GOD_SPEAKER, it, isGod = true) }
        return (chatHistory + newEvents).takeLastPerChannel(MAX_FEED_MESSAGES)
    }

    private fun List<GameChatMessage>.takeLastPerChannel(limit: Int): List<GameChatMessage> {
        val counts = mutableMapOf<ChatChannel, Int>()
        return asReversed()
            .filter { message ->
                val currentCount = counts[message.channel] ?: 0
                if (currentCount >= limit) {
                    false
                } else {
                    counts[message.channel] = currentCount + 1
                    true
                }
            }
            .asReversed()
            .toList()
    }
}
