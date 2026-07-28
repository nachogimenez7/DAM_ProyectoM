package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import org.json.JSONArray
import org.json.JSONObject

internal data class PlayGamesCloudPayload(
    val matchCount: Int,
    val updatedAtMs: Long,
    val values: Map<String, Any>
) {
    fun encode(): ByteArray {
        val encodedValues = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            encodePreference(value)?.let { encodedValues.put(key, it) }
        }
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("matchCount", matchCount)
            .put("updatedAtMs", updatedAtMs)
            .put("values", encodedValues)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun applyTo(context: Context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        preferences.all.keys
            .filter(::isCloudPreference)
            .forEach(editor::remove)
        values.forEach { (key, value) ->
            if (!isCloudPreference(key)) return@forEach
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(
                    key,
                    value.filterIsInstance<String>().toSet()
                )
            }
        }
        editor.putLong(PREF_LOCAL_UPDATED_AT, updatedAtMs)
        editor.apply()
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val PREFS_NAME = "TraidoresPrefs"
        internal const val PREF_LOCAL_UPDATED_AT = "play_games_cloud_local_updated_at"
        private val CLOUD_PREFIXES = listOf(
            "profile_",
            "achievement_",
            "local_match_"
        )
        private val CLOUD_EXACT_KEYS = setOf(
            "player_name"
        )
        private val EXCLUDED_KEYS = setOf(
            "profile_public_id",
            "online_temp_uid"
        )

        fun from(context: Context): PlayGamesCloudPayload {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val values = buildMap<String, Any> {
                preferences.all.forEach { (key, value) ->
                    if (isCloudPreference(key) && value != null && isSupportedPreference(value)) {
                        put(key, value)
                    }
                }
            }
            return PlayGamesCloudPayload(
                matchCount = preferences.getInt("local_match_total", 0).coerceAtLeast(0),
                updatedAtMs = preferences
                    .getLong(PREF_LOCAL_UPDATED_AT, 0L)
                    .coerceAtLeast(0L),
                values = values
            )
        }

        fun decode(bytes: ByteArray): PlayGamesCloudPayload? {
            if (bytes.isEmpty()) return null
            return runCatching {
                val root = JSONObject(bytes.toString(Charsets.UTF_8))
                if (root.optInt("schema", -1) != SCHEMA_VERSION) return null
                val encodedValues = root.optJSONObject("values") ?: JSONObject()
                val values = buildMap<String, Any> {
                    val keys = encodedValues.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (!isCloudPreference(key)) continue
                        decodePreference(encodedValues.optJSONObject(key))?.let { put(key, it) }
                    }
                }
                PlayGamesCloudPayload(
                    matchCount = root.optInt("matchCount", 0).coerceAtLeast(0),
                    updatedAtMs = root.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                    values = values
                )
            }.getOrNull()
        }

        fun preferred(
            first: PlayGamesCloudPayload,
            second: PlayGamesCloudPayload
        ): PlayGamesCloudPayload {
            return when {
                first.matchCount != second.matchCount ->
                    if (first.matchCount > second.matchCount) first else second
                first.updatedAtMs != second.updatedAtMs ->
                    if (first.updatedAtMs > second.updatedAtMs) first else second
                else -> first
            }
        }

        private fun isCloudPreference(key: String): Boolean {
            if (key in EXCLUDED_KEYS) return false
            return key in CLOUD_EXACT_KEYS || CLOUD_PREFIXES.any(key::startsWith)
        }

        private fun isSupportedPreference(value: Any?): Boolean {
            return value is String ||
                value is Boolean ||
                value is Int ||
                value is Long ||
                value is Float ||
                (value is Set<*> && value.all { it is String })
        }

        private fun encodePreference(value: Any): JSONObject? {
            val encoded = JSONObject()
            return when (value) {
                is String -> encoded.put("type", "string").put("value", value)
                is Boolean -> encoded.put("type", "boolean").put("value", value)
                is Int -> encoded.put("type", "int").put("value", value)
                is Long -> encoded.put("type", "long").put("value", value)
                is Float -> encoded.put("type", "float").put("value", value.toDouble())
                is Set<*> -> encoded
                    .put("type", "strings")
                    .put("value", JSONArray(value.filterIsInstance<String>().sorted()))
                else -> null
            }
        }

        private fun decodePreference(encoded: JSONObject?): Any? {
            encoded ?: return null
            return when (encoded.optString("type")) {
                "string" -> encoded.optString("value")
                "boolean" -> encoded.optBoolean("value")
                "int" -> encoded.optInt("value")
                "long" -> encoded.optLong("value")
                "float" -> encoded.optDouble("value").toFloat()
                "strings" -> {
                    val array = encoded.optJSONArray("value") ?: return emptySet<String>()
                    buildSet {
                        for (index in 0 until array.length()) {
                            array.optString(index)
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }
                else -> null
            }
        }
    }
}

object PlayGamesCloudSave {
    @Volatile
    private var operationInProgress = false

    @Volatile
    private var savePending = false

    fun markLocalChanged(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(
                PlayGamesCloudPayload.PREF_LOCAL_UPDATED_AT,
                System.currentTimeMillis()
            )
            .apply()
    }

    fun restoreOrUpload(activity: Activity, onDone: (Boolean) -> Unit = {}) {
        start(activity, onDone)
    }

    fun save(activity: Activity) {
        if (operationInProgress) {
            savePending = true
            return
        }
        start(activity)
    }

    /**
     * Elimina el único snapshot de Traidores asociado a la cuenta actual de Play Games.
     * Se abre con resolución automática para que un conflicto pendiente no deje una copia
     * sobreviviente.
     */
    fun deleteAccountSnapshot(activity: Activity, onDone: (Exception?) -> Unit) {
        if (operationInProgress) {
            onDone(IllegalStateException("Hay una sincronización de Play Games en curso."))
            return
        }
        if (!PlayGamesIdentity.isReady(activity)) {
            onDone(IllegalStateException("Play Games no está disponible."))
            return
        }
        operationInProgress = true
        savePending = false
        val client = PlayGames.getSnapshotsClient(activity)
        client.open(
            SNAPSHOT_NAME,
            true,
            SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
        )
            .addOnSuccessListener { result ->
                val snapshot = result.data
                if (snapshot == null) {
                    operationInProgress = false
                    onDone(null)
                    return@addOnSuccessListener
                }
                client.delete(snapshot.metadata)
                    .addOnSuccessListener {
                        operationInProgress = false
                        OnlineDebugLog.i("play_games_cloud_deleted")
                        onDone(null)
                    }
                    .addOnFailureListener { error ->
                        client.discardAndClose(snapshot)
                        operationInProgress = false
                        OnlineDebugLog.e("play_games_cloud_delete_failure", error)
                        onDone(error)
                    }
            }
            .addOnFailureListener { error ->
                operationInProgress = false
                OnlineDebugLog.e("play_games_cloud_delete_open_failure", error)
                onDone(error)
            }
    }

    private fun start(activity: Activity, onDone: (Boolean) -> Unit = {}) {
        if (!PlayGamesIdentity.isReady(activity)) {
            onDone(false)
            return
        }
        if (operationInProgress) {
            savePending = true
            onDone(false)
            return
        }
        operationInProgress = true
        val local = PlayGamesCloudPayload.from(activity)
        val client = PlayGames.getSnapshotsClient(activity)
        client.open(SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
            .addOnSuccessListener { result ->
                processOpenResult(
                    activity = activity,
                    client = client,
                    result = result,
                    local = local,
                    retry = 0,
                    onDone = onDone
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_cloud_open_failure", error)
                finish(activity, onDone, false)
            }
    }

    private fun processOpenResult(
        activity: Activity,
        client: SnapshotsClient,
        result: SnapshotsClient.DataOrConflict<Snapshot>,
        local: PlayGamesCloudPayload,
        retry: Int,
        onDone: (Boolean) -> Unit
    ) {
        if (retry >= MAX_CONFLICT_RETRIES) {
            finish(activity, onDone, false)
            return
        }
        if (result.isConflict) {
            val conflict = result.conflict
            if (conflict == null) {
                finish(activity, onDone, false)
                return
            }
            val server = conflict.snapshot
            val device = conflict.conflictingSnapshot
            val serverPayload = readPayload(server)
            val devicePayload = readPayload(device)
            val chosen = when {
                serverPayload == null -> device
                devicePayload == null -> server
                PlayGamesCloudPayload.preferred(serverPayload, devicePayload) == serverPayload ->
                    server
                else -> device
            }
            client.resolveConflict(conflict.conflictId, chosen)
                .addOnSuccessListener { resolved ->
                    processOpenResult(
                        activity,
                        client,
                        resolved,
                        local,
                        retry + 1,
                        onDone
                    )
                }
                .addOnFailureListener { error ->
                    OnlineDebugLog.e("play_games_cloud_conflict_failure", error)
                    finish(activity, onDone, false)
                }
            return
        }

        val snapshot = result.data
        if (snapshot == null) {
            finish(activity, onDone, false)
            return
        }
        val remote = readPayload(snapshot)
        if (
            remote != null &&
            PlayGamesCloudPayload.preferred(local, remote) == remote
        ) {
            remote.applyTo(activity)
            client.discardAndClose(snapshot)
            OnlineDebugLog.i(
                "play_games_cloud_restored matches=${remote.matchCount} updatedAt=${remote.updatedAtMs}"
            )
            finish(activity, onDone, true)
            return
        }

        val wrote = runCatching {
            snapshot.snapshotContents.writeBytes(local.encode())
        }.getOrDefault(false)
        if (!wrote) {
            client.discardAndClose(snapshot)
            finish(activity, onDone, false)
            return
        }
        val metadata = SnapshotMetadataChange.Builder()
            .setDescription("Perfil y progreso de Traidores")
            .setProgressValue(local.matchCount.toLong())
            .build()
        client.commitAndClose(snapshot, metadata)
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "play_games_cloud_saved matches=${local.matchCount} updatedAt=${local.updatedAtMs}"
                )
                finish(activity, onDone, false)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_cloud_commit_failure", error)
                finish(activity, onDone, false)
            }
    }

    private fun readPayload(snapshot: Snapshot): PlayGamesCloudPayload? {
        return runCatching {
            PlayGamesCloudPayload.decode(snapshot.snapshotContents.readFully())
        }.getOrNull()
    }

    private fun finish(
        activity: Activity,
        onDone: (Boolean) -> Unit,
        restored: Boolean
    ) {
        operationInProgress = false
        onDone(restored)
        if (savePending) {
            savePending = false
            save(activity)
        }
    }

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val SNAPSHOT_NAME = "traidores_profile_v1"
    private const val MAX_CONFLICT_RETRIES = 8
}
