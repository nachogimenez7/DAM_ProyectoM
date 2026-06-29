package com.traidores.juego

import android.graphics.Color

object PlayerChatColor {
    private val palette = intArrayOf(
        Color.parseColor("#D4A24E"),
        Color.parseColor("#C96E4A"),
        Color.parseColor("#8FB86A"),
        Color.parseColor("#6AA6B8"),
        Color.parseColor("#B88AD8"),
        Color.parseColor("#D88989"),
        Color.parseColor("#C8B56A"),
        Color.parseColor("#8AB0D8")
    )

    fun colorFor(playerName: String, session: GameSession): Int {
        if (playerName.isBlank()) return palette.first()
        val order = session.players.indexOfFirst { it.name.equals(playerName, ignoreCase = true) }
        val seed = if (order >= 0) order else playerName.lowercase().hashCode() and Int.MAX_VALUE
        return palette[seed % palette.size]
    }
}
