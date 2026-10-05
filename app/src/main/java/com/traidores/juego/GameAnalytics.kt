package com.traidores.juego

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Eventos de medicion de producto. Solo viajan datos agregados (modo, mapa, cantidad de jugadores,
 * rol y resultado): nunca nombres, codigos de sala, chats ni identificadores de cuenta.
 */
object GameAnalytics {
    const val EVENT_MATCH_START = "match_start"
    const val EVENT_MATCH_COMPLETE = "match_complete"
    const val EVENT_INVITE_SHARE = "invite_share"

    private val startedMatchKeys = mutableSetOf<String>()

    fun initialize(context: Context) {
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context)
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            analytics.setUserProperty("build_type", if (debuggable) "debug" else "release")
        }
    }

    fun matchStarted(context: Context, session: GameSession, online: Boolean) {
        if (session.onlineTestMode || session.quickTestMode) return
        val key = MatchOutcome.matchKey(session)
        if (!synchronized(startedMatchKeys) { startedMatchKeys.add(key) }) return
        log(context, EVENT_MATCH_START, matchBundle(session, online))
    }

    fun matchCompleted(context: Context, session: GameSession, roleKey: String, won: Boolean) {
        if (session.onlineTestMode || session.quickTestMode) return
        val bundle = matchBundle(session, session.onlineMatchId.isNotBlank()).apply {
            putString("role_key", roleKey.ifBlank { "none" })
            putLong("won", if (won) 1L else 0L)
            putLong("rounds", session.round.toLong())
        }
        log(context, EVENT_MATCH_COMPLETE, bundle)
    }

    fun inviteShared(context: Context) {
        log(context, EVENT_INVITE_SHARE, Bundle().apply { putString("method", "room_code") })
    }

    private fun matchBundle(session: GameSession, online: Boolean) = Bundle().apply {
        putString("mode", if (online) "online" else "local")
        putString("map_key", session.mapKey)
        putLong("players", session.initialPlayerCount.toLong())
    }

    private fun log(context: Context, name: String, params: Bundle) {
        runCatching { FirebaseAnalytics.getInstance(context).logEvent(name, params) }
    }
}
