package com.traidores.juego

internal object OnlineLobbySearchWindow {
    const val PAGE_SIZE = 30L
    const val AUTOMATIC_LIMIT = 150L
    fun shouldExpand(limit: Long, received: Int, available: Int): Boolean =
        received.toLong() >= limit && available < PAGE_SIZE && limit < AUTOMATIC_LIMIT
}
