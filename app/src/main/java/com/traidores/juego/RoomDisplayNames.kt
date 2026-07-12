package com.traidores.juego

object RoomDisplayNames {
    private const val MAX_ROOM_DISPLAY_NAME = 18
    private val LEGACY_PUBLIC_ID_SUFFIX = Regex("\\s+#\\d{1,12}$")

    @Suppress("UNUSED_PARAMETER")
    fun withPublicId(profileName: String, publicId: String): String {
        return OnlineRoomFirestore.normalizedPlayerName(profileName)
            .take(MAX_ROOM_DISPLAY_NAME)
    }

    fun withoutPublicId(storedName: String): String {
        return storedName.trim()
            .replace(LEGACY_PUBLIC_ID_SUFFIX, "")
            .trim()
            .take(MAX_ROOM_DISPLAY_NAME)
    }
}
