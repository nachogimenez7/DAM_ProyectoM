package com.traidores.juego

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitacora compacta para testers beta. Solo conserva identificadores recortados, contadores
 * y codigos tecnicos; nunca nombres, UID completos, correos ni mensajes del chat.
 */
object OnlineStabilityReport {
    private const val PREFS = "online_stability_report"
    private const val KEY_ROOM = "room"
    private const val KEY_MATCH = "match"
    private const val KEY_HOST = "host"
    private const val KEY_PHASE = "phase"
    private const val KEY_PHASE_INDEX = "phase_index"
    private const val KEY_ROUND = "round"
    private const val KEY_CONNECTED = "connected"
    private const val KEY_EXPECTED = "expected"
    private const val KEY_EVENTS = "events"
    private const val EVENT_SEPARATOR = "\u001E"
    private const val MAX_EVENTS = 18

    fun beginRoom(
        context: Context,
        roomCode: String,
        matchId: String,
        isHost: Boolean,
        expectedPlayers: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val room = maskedRoomCode(roomCode)
        val match = shortToken(matchId)
        val roomChanged = room.isNotBlank() && prefs.getString(KEY_ROOM, "") != room
        val matchChanged = match.isNotBlank() && prefs.getString(KEY_MATCH, "") != match
        prefs.edit().apply {
            if (roomChanged) remove(KEY_EVENTS)
            if (room.isNotBlank()) putString(KEY_ROOM, room)
            if (match.isNotBlank()) putString(KEY_MATCH, match)
            putBoolean(KEY_HOST, isHost)
            if (expectedPlayers > 0) putInt(KEY_EXPECTED, expectedPlayers)
        }.apply()
        if (roomChanged || matchChanged || prefs.getString(KEY_EVENTS, "").isNullOrBlank()) {
            recordEvent(context, if (match.isBlank()) "lobby_abierto" else "gameplay_abierto")
        }
    }

    fun updateMatch(context: Context, matchId: String, isHost: Boolean, expectedPlayers: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            shortToken(matchId).takeIf(String::isNotBlank)?.let { putString(KEY_MATCH, it) }
            putBoolean(KEY_HOST, isHost)
            if (expectedPlayers > 0) putInt(KEY_EXPECTED, expectedPlayers)
        }.apply()
    }

    fun recordPhase(
        context: Context,
        session: GameSession,
        isHost: Boolean,
        event: String,
        connectedPlayers: Int? = null,
        expectedPlayers: Int = session.players.size,
        reason: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MATCH, shortToken(session.onlineMatchId))
            .putBoolean(KEY_HOST, isHost)
            .putString(KEY_PHASE, session.phase.name)
            .putInt(KEY_PHASE_INDEX, session.phaseIndex)
            .putInt(KEY_ROUND, session.round)
            .putInt(KEY_CONNECTED, connectedPlayers ?: prefs.getInt(KEY_CONNECTED, -1))
            .putInt(KEY_EXPECTED, expectedPlayers)
            .apply()
        recordEvent(context, event, reason)
    }

    @Synchronized
    fun recordEvent(context: Context, event: String, reason: String = "") {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_EVENTS, "")
            .orEmpty()
            .split(EVENT_SEPARATOR)
            .filter(String::isNotBlank)
            .toMutableList()
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        val safeEvent = safeLabel(event)
        val safeReason = safeLabel(reason)
        existing += buildString {
            append(time).append(' ').append(safeEvent)
            if (safeReason.isNotBlank()) append(" (").append(safeReason).append(')')
        }
        prefs.edit().putString(
            KEY_EVENTS,
            existing.takeLast(MAX_EVENTS).joinToString(EVENT_SEPARATOR)
        ).apply()
    }

    fun reportText(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "desconocida" }
        val connected = prefs.getInt(KEY_CONNECTED, -1)
        val expected = prefs.getInt(KEY_EXPECTED, -1)
        val events = prefs.getString(KEY_EVENTS, "")
            .orEmpty()
            .split(EVENT_SEPARATOR)
            .filter(String::isNotBlank)
        return buildString {
            appendLine("TRAIDORES · REPORTE BETA")
            appendLine("Version: $version")
            appendLine("Sala: ${prefs.getString(KEY_ROOM, "-")}")
            appendLine("Partida: ${prefs.getString(KEY_MATCH, "-")}")
            appendLine("Anfitrion: ${if (prefs.getBoolean(KEY_HOST, false)) "si" else "no"}")
            appendLine(
                "Estado: ${prefs.getString(KEY_PHASE, "LOBBY")}:" +
                    "${prefs.getInt(KEY_PHASE_INDEX, 0)} · ronda ${prefs.getInt(KEY_ROUND, 0)}"
            )
            appendLine(
                "Conectados: ${connected.takeIf { it >= 0 } ?: "-"}/" +
                    "${expected.takeIf { it >= 0 } ?: "-"}"
            )
            appendLine("Eventos recientes:")
            if (events.isEmpty()) appendLine("- sin eventos") else events.forEach { appendLine("- $it") }
            append("No incluye nombres, mensajes, correos ni UID completos.")
        }
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Reporte beta de Traidores", reportText(context)))
        GameDialog.notice(
            activity = context as android.app.Activity,
            title = "REPORTE COPIADO",
            message = "Pegalo junto con una breve explicación de lo que viste. No contiene " +
                "nombres, mensajes, correos ni identificadores completos.",
            positiveLabel = "ENTENDIDO"
        )
    }

    internal fun maskedRoomCode(value: String): String {
        val clean = safeToken(value)
        if (clean.isBlank()) return ""
        return "***${clean.takeLast(3)}"
    }

    internal fun shortToken(value: String): String = safeToken(value).takeLast(8)

    private fun safeToken(value: String): String = value
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .take(48)

    private fun safeLabel(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_:/.-]+"), "_")
        .trim('_')
        .take(72)
}
