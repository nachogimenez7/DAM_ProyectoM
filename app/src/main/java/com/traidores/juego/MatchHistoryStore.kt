package com.traidores.juego

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MatchRecord(
    val matchKey: String,
    val dateEpochMs: Long,
    val mapKey: String,
    val mapName: String,
    val roleKey: String,
    val roleName: String,
    val won: Boolean
)

data class LocalMatchStats(
    val matches: Int,
    val wins: Int
) {
    val winRatePercent: Int
        get() = if (matches > 0) ((wins * 100.0) / matches).toInt() else 0
}

internal object MatchOutcome {
    fun matchKey(session: GameSession): String {
        return listOf(
            session.code,
            session.startedAtEpochMs.toString(),
            session.initialPlayerCount.toString()
        ).joinToString(":")
    }

    fun didHumanWin(session: GameSession, human: GamePlayer): Boolean {
        val roleKey = human.role?.key.orEmpty()
        return when {
            session.specialVictories.any { it.playerName == human.name } -> true
            roleKey == RoleCatalog.DESERTOR -> session.desertorTeam == session.winner
            session.winner == GameRules.TOWN_WINNER ->
                human.role?.team == GameRules.TOWN_WINNER
            session.winner == GameRules.TRAITOR_WINNER ->
                roleKey in GameRules.traitorRoleKeys
            else -> false
        }
    }
}

object MatchHistoryStore {
    fun record(context: Context, session: GameSession): Boolean {
        if (session.winner.isBlank()) return false
        val human = session.players.firstOrNull { it.isHuman } ?: return false
        val key = MatchOutcome.matchKey(session)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recordedKeys = prefs.getStringSet(KEY_RECORDED_MATCHES, emptySet())
            .orEmpty()
            .toMutableSet()
        if (key in recordedKeys) return false

        val record = MatchRecord(
            matchKey = key,
            dateEpochMs = System.currentTimeMillis(),
            mapKey = session.mapKey,
            mapName = session.mapName,
            roleKey = human.role?.key.orEmpty(),
            roleName = human.role?.name.orEmpty().ifBlank { "Sin rol" },
            won = MatchOutcome.didHumanWin(session, human)
        )
        val updated = (listOf(record) + loadRecords(prefs))
            .distinctBy { it.matchKey }
            .sortedByDescending { it.dateEpochMs }
            .take(MAX_STORED_MATCHES)
        recordedKeys += key
        val boundedKeys = recordedKeys.toList().takeLast(MAX_RECORDED_KEYS).toSet()
        prefs.edit()
            .putString(KEY_HISTORY_JSON, encode(updated).toString())
            .putStringSet(KEY_RECORDED_MATCHES, boundedKeys)
            .putInt(KEY_TOTAL_MATCHES, prefs.getInt(KEY_TOTAL_MATCHES, 0) + 1)
            .putInt(
                KEY_TOTAL_WINS,
                prefs.getInt(KEY_TOTAL_WINS, 0) + if (record.won) 1 else 0
            )
            .apply()
        PlayGamesProgressSync.onMatchRecorded(context)
        return true
    }

    fun lastMatch(context: Context): MatchRecord? = lastMatches(context, 1).firstOrNull()

    fun lastMatches(context: Context, amount: Int = 5): List<MatchRecord> {
        if (amount <= 0) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return loadRecords(prefs)
            .sortedByDescending { it.dateEpochMs }
            .take(amount)
    }

    fun stats(context: Context): LocalMatchStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LocalMatchStats(
            matches = prefs.getInt(KEY_TOTAL_MATCHES, 0).coerceAtLeast(0),
            wins = prefs.getInt(KEY_TOTAL_WINS, 0).coerceAtLeast(0)
        )
    }

    private fun loadRecords(prefs: android.content.SharedPreferences): List<MatchRecord> {
        val raw = prefs.getString(KEY_HISTORY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    decode(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(records: List<MatchRecord>): JSONArray {
        return JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().apply {
                    put("matchKey", record.matchKey)
                    put("dateEpochMs", record.dateEpochMs)
                    put("mapKey", record.mapKey)
                    put("mapName", record.mapName)
                    put("roleKey", record.roleKey)
                    put("roleName", record.roleName)
                    put("won", record.won)
                })
            }
        }
    }

    private fun decode(value: JSONObject?): MatchRecord? {
        value ?: return null
        val matchKey = value.optString("matchKey").trim()
        val dateEpochMs = value.optLong("dateEpochMs", 0L)
        if (matchKey.isBlank() || dateEpochMs <= 0L) return null
        return MatchRecord(
            matchKey = matchKey,
            dateEpochMs = dateEpochMs,
            mapKey = value.optString("mapKey"),
            mapName = value.optString("mapName").ifBlank { "Mapa desconocido" },
            roleKey = value.optString("roleKey"),
            roleName = value.optString("roleName").ifBlank { "Sin rol" },
            won = value.optBoolean("won", false)
        )
    }

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val KEY_HISTORY_JSON = "local_match_history_json"
    private const val KEY_RECORDED_MATCHES = "local_match_history_keys"
    private const val KEY_TOTAL_MATCHES = "local_match_total"
    private const val KEY_TOTAL_WINS = "local_match_wins"
    private const val MAX_STORED_MATCHES = 10
    private const val MAX_RECORDED_KEYS = 100
}
