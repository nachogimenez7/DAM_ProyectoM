package com.traidores.juego

import android.content.Context

data class OnlineRecoveredRoom(
    val roomId: String,
    val roomCode: String,
    val roomName: String,
    val mapKey: String,
    val isHost: Boolean,
    val serverProtocol: Boolean = false
)

object OnlineRoomRecovery {
    // Preferences and the original creator cannot grant authority after host migration.
    fun isCurrentHost(uid: String, activeHostId: String?, creatorId: String?): Boolean =
        uid.isNotBlank() && uid == (activeHostId?.takeIf { it.isNotBlank() } ?: creatorId)

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ROOM_ID = "online_recovery_room_id"
    private const val PREF_ROOM_CODE = "online_recovery_room_code"
    private const val PREF_ROOM_NAME = "online_recovery_room_name"
    private const val PREF_MAP_KEY = "online_recovery_map_key"
    private const val PREF_IS_HOST = "online_recovery_is_host"
    private const val PREF_SERVER_PROTOCOL = "online_recovery_server_protocol"

    fun save(
        context: Context,
        roomId: String,
        roomCode: String,
        roomName: String,
        mapKey: String,
        isHost: Boolean
    ) {
        if (roomId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val knownServerProtocol = prefs.getString(PREF_ROOM_ID, "") == roomId && prefs.getBoolean(PREF_SERVER_PROTOCOL, false)
        prefs
            .edit()
            .putString(PREF_ROOM_ID, roomId)
            .putString(PREF_ROOM_CODE, roomCode)
            .putString(PREF_ROOM_NAME, roomName)
            .putString(PREF_MAP_KEY, mapKey)
            .putBoolean(PREF_IS_HOST, isHost)
            .putBoolean(PREF_SERVER_PROTOCOL, knownServerProtocol)
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
            isHost = prefs.getBoolean(PREF_IS_HOST, false),
            serverProtocol = prefs.getBoolean(PREF_SERVER_PROTOCOL, false)
        )
    }

    fun rememberServerProtocol(context: Context, roomId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_ROOM_ID, "") == roomId && !prefs.getBoolean(PREF_SERVER_PROTOCOL, false))
            prefs.edit().putBoolean(PREF_SERVER_PROTOCOL, true).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_ROOM_ID)
            .remove(PREF_ROOM_CODE)
            .remove(PREF_ROOM_NAME)
            .remove(PREF_MAP_KEY)
            .remove(PREF_IS_HOST)
            .remove(PREF_SERVER_PROTOCOL)
            .apply()
    }

    fun clearIf(context: Context, roomId: String) {
        val recovered = load(context) ?: return
        if (recovered.roomId == roomId) {
            clear(context)
        }
    }
}
