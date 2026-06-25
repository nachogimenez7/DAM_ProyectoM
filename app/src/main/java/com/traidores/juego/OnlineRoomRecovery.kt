package com.traidores.juego

import android.content.Context

data class OnlineRecoveredRoom(
    val roomId: String,
    val roomCode: String,
    val roomName: String,
    val mapKey: String,
    val isHost: Boolean
)

object OnlineRoomRecovery {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ROOM_ID = "online_recovery_room_id"
    private const val PREF_ROOM_CODE = "online_recovery_room_code"
    private const val PREF_ROOM_NAME = "online_recovery_room_name"
    private const val PREF_MAP_KEY = "online_recovery_map_key"
    private const val PREF_IS_HOST = "online_recovery_is_host"

    fun save(
        context: Context,
        roomId: String,
        roomCode: String,
        roomName: String,
        mapKey: String,
        isHost: Boolean
    ) {
        if (roomId.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_ROOM_ID, roomId)
            .putString(PREF_ROOM_CODE, roomCode)
            .putString(PREF_ROOM_NAME, roomName)
            .putString(PREF_MAP_KEY, mapKey)
            .putBoolean(PREF_IS_HOST, isHost)
            .apply()
    }

    fun load(context: Context): OnlineRecoveredRoom? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val roomId = prefs.getString(PREF_ROOM_ID, "").orEmpty()
        if (roomId.isBlank()) return null
        return OnlineRecoveredRoom(
            roomId = roomId,
            roomCode = prefs.getString(PREF_ROOM_CODE, "").orEmpty(),
            roomName = prefs.getString(PREF_ROOM_NAME, "Sala online").orEmpty(),
            mapKey = prefs.getString(PREF_MAP_KEY, "pampa").orEmpty(),
            isHost = prefs.getBoolean(PREF_IS_HOST, false)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_ROOM_ID)
            .remove(PREF_ROOM_CODE)
            .remove(PREF_ROOM_NAME)
            .remove(PREF_MAP_KEY)
            .remove(PREF_IS_HOST)
            .apply()
    }

    fun clearIf(context: Context, roomId: String) {
        val recovered = load(context) ?: return
        if (recovered.roomId == roomId) {
            clear(context)
        }
    }
}
