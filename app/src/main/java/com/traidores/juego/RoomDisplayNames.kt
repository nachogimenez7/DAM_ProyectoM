package com.traidores.juego

object RoomDisplayNames {
    private const val MAX_ROOM_DISPLAY_NAME = 18

    fun withPublicId(profileName: String, publicId: String): String {
        val safeName = OnlineRoomFirestore.normalizedPlayerName(profileName)
        val numericId = publicId.trim().removePrefix("#").takeIf { it.isNotBlank() }
            ?: return safeName.take(MAX_ROOM_DISPLAY_NAME)
        val suffix = " #$numericId"
        val availableNameLength = (MAX_ROOM_DISPLAY_NAME - suffix.length).coerceAtLeast(1)
        val visibleName = if (safeName.length + suffix.length <= MAX_ROOM_DISPLAY_NAME) {
            safeName
        } else {
            safeName.take(availableNameLength).trimEnd()
        }
        return "$visibleName$suffix"
    }
}
