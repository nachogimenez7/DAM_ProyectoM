package com.traidores.juego

import android.content.Context
import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Optional device history. Failure must never affect room creation, joining or presence. */
internal class OnlineRoomProfileStatsPublisher {
    private var publishedKey = ""
    private var pending = false
    private var retryAfterMs = 0L

    fun publish(context: Context, roomId: String, uid: String) {
        if (roomId.isBlank() || uid.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val stats = MatchHistoryStore.stats(context)
        val matches = stats.matches.coerceIn(0, 1_000_000)
        val wins = stats.wins.coerceIn(0, matches)
        val key = "$roomId|$uid|$matches|$wins"
        if (pending || publishedKey == key || SystemClock.elapsedRealtime() < retryAfterMs) return
        pending = true
        FirebaseFirestore.getInstance().collection(OnlineRoomFirestore.ROOMS_COLLECTION).document(roomId)
            .collection(OnlineRoomFirestore.PLAYERS_COLLECTION).document(uid)
            .update(PlayerPublicIdentity.FIELD_PROFILE_STATS, mapOf("partidas" to matches, "victorias" to wins))
            .addOnSuccessListener { publishedKey = key }
            .addOnFailureListener { error ->
                retryAfterMs = SystemClock.elapsedRealtime() + 30_000L
                OnlineDebugLog.e("profile_history_publish_failure", error)
            }
            .addOnCompleteListener { pending = false }
    }
}
