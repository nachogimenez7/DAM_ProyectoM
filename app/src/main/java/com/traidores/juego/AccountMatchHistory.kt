package com.traidores.juego

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object AccountHistoryContract {
    fun id(key: String): String = (if (key.startsWith("online:")) "online_" else "local_") +
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun localRecord(session: GameSession, uid: String, finishedAtMs: Long): Map<String, Any>? {
        if (uid.isBlank() || session.onlineMatchId.isNotBlank() || session.onlinePlayerUids.isNotEmpty() ||
            session.winner !in setOf(GameRules.TOWN_WINNER, GameRules.TRAITOR_WINNER)) return null
        val human = session.players.firstOrNull { it.isHuman } ?: return null
        val role = human.role ?: return null
        return mapOf("schemaVersion" to 1, "uid" to uid,
            "matchKey" to "local:${MatchOutcome.matchKey(session)}", "origen" to "local",
            "roomId" to "", "matchId" to "", "fechaLocalMs" to finishedAtMs,
            "mapKey" to session.mapKey, "mapName" to session.mapName,
            "roleKey" to role.key, "roleName" to role.name,
            "won" to MatchOutcome.didHumanWin(session, human),
            "participantCount" to session.initialPlayerCount, "winner" to session.winner)
    }
}

/** Firestore is the source of account history. The UID-bound outbox only retries unsent local results. */
internal object AccountMatchHistory {
    enum class Status { GUEST, LOADING, READY, ERROR }
    data class Snapshot(val uid: String = "", val status: Status = Status.GUEST,
        val records: List<MatchRecord> = emptyList(), val stats: LocalMatchStats = LocalMatchStats(0, 0),
        val processing: Boolean = false)
    var snapshot = Snapshot()
        private set
    private val observers = linkedSetOf<() -> Unit>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val inFlight = mutableSetOf<String>()
    private val recorded = mutableSetOf<String>()
    private var appContext: Context? = null
    private var initialized = false
    private var generation = 0
    private var summaryReceived = false
    private var historyReceived = false
    private var failed = false
    private val handler = Handler(Looper.getMainLooper())
    private var loadingTimeout: Runnable? = null

    fun registeredUid(): String = runCatching {
        FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid.orEmpty()
    }.getOrDefault("")

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        FirebaseAuth.getInstance().addIdTokenListener(FirebaseAuth.IdTokenListener {
            val uid = registeredUid()
            if (snapshot.uid != uid) {
                stop()
                snapshot = Snapshot(uid, if (uid.isEmpty()) Status.GUEST else Status.LOADING)
                notifyObservers()
                if (observers.isNotEmpty()) listen()
            } else if (snapshot.status == Status.ERROR && observers.isNotEmpty()) {
                stop()
                listen()
            }
            flush()
        })
    }

    fun observe(context: Context, changed: () -> Unit): () -> Unit {
        initialize(context)
        observers.add(changed)
        if (snapshot.uid != registeredUid()) {
            stop()
            snapshot = Snapshot(registeredUid(), if (registeredUid().isEmpty()) Status.GUEST else Status.LOADING)
        }
        if (listeners.isEmpty()) listen()
        changed()
        flush()
        return { observers.remove(changed); if (observers.isEmpty()) stop() }
    }

    fun retry() { stop(); listen(); flush() }
    fun pendingCount(): Int = queue(registeredUid()).length()
    fun forget(uid: String) {
        appContext?.getSharedPreferences(OUTBOX, Context.MODE_PRIVATE)?.edit()?.remove(uid)?.apply()
    }

    fun recordLocal(context: Context, session: GameSession, ownerUid: String) {
        if (ownerUid.isBlank() || ownerUid != registeredUid()) return
        initialize(context)
        val record = AccountHistoryContract.localRecord(session, ownerUid, System.currentTimeMillis()) ?: return
        val key = record["matchKey"] as String
        if (!recorded.add("$ownerUid|$key")) return
        val pending = queue(ownerUid)
        if ((0 until pending.length()).none { pending.optJSONObject(it)?.optString("matchKey") == key }) {
            pending.put(JSONObject(record))
            saveQueue(ownerUid, pending)
        }
        flush()
        notifyObservers()
    }

    private fun queue(uid: String): JSONArray = runCatching {
        JSONArray(appContext?.getSharedPreferences(OUTBOX, Context.MODE_PRIVATE)?.getString(uid, "[]") ?: "[]")
    }.getOrElse { JSONArray() }
    private fun saveQueue(uid: String, queue: JSONArray) {
        appContext?.getSharedPreferences(OUTBOX, Context.MODE_PRIVATE)?.edit()?.putString(uid, queue.toString())?.apply()
    }

    private fun flush() {
        val uid = registeredUid()
        if (uid.isBlank() || appContext == null) return
        val pending = queue(uid)
        for (index in 0 until pending.length()) {
            val json = pending.optJSONObject(index) ?: continue
            val key = json.optString("matchKey")
            val token = "$uid|$key"
            if (!inFlight.add(token)) continue
            val ref = FirebaseFirestore.getInstance().document("cuentas/$uid/historial/${AccountHistoryContract.id(key)}")
            val data = json.keys().asSequence().associateWith { json.get(it) }.toMutableMap()
            data["finalizadaEn"] = FieldValue.serverTimestamp()
            FirebaseFirestore.getInstance().runTransaction { tx ->
                check(registeredUid() == uid) { "La cuenta cambió" }
                val old = tx.get(ref)
                if (!old.exists()) tx.set(ref, data)
                else check(old.getString("uid") == uid && old.getString("matchKey") == key)
            }.addOnSuccessListener {
                val remaining = queue(uid)
                val filtered = JSONArray()
                for (item in 0 until remaining.length()) {
                    remaining.optJSONObject(item)?.takeIf { it.optString("matchKey") != key }?.let(filtered::put)
                }
                saveQueue(uid, filtered)
            }.addOnFailureListener { OnlineDebugLog.e("account_history_upload_failure", it) }
                .addOnCompleteListener { inFlight.remove(token); notifyObservers() }
        }
    }

    private fun listen() {
        val uid = registeredUid()
        if (uid.isBlank()) return
        val attempt = ++generation
        summaryReceived = false; historyReceived = false; failed = false
        snapshot = Snapshot(uid, Status.LOADING)
        notifyObservers()
        val account = FirebaseFirestore.getInstance().document("cuentas/$uid")
        fun current() = attempt == generation && registeredUid() == uid
        fun confirmed() {
            if (!failed && summaryReceived && historyReceived) {
                loadingTimeout?.let(handler::removeCallbacks)
                snapshot = snapshot.copy(status = Status.READY)
            }
            notifyObservers()
        }
        fun failure(error: Exception) {
            failed = true
            snapshot = snapshot.copy(status = Status.ERROR, records = emptyList(), stats = LocalMatchStats(0, 0))
            OnlineDebugLog.e("account_history_read_failure", error)
            notifyObservers()
        }
        loadingTimeout = Runnable {
            if (current() && snapshot.status == Status.LOADING) {
                snapshot = snapshot.copy(status = Status.ERROR)
                notifyObservers()
            }
        }.also { handler.postDelayed(it, 12_000L) }
        listeners += account.addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (!current()) return@addSnapshotListener
            if (error != null) { failure(error); return@addSnapshotListener }
            if (doc == null || doc.metadata.isFromCache || doc.metadata.hasPendingWrites()) return@addSnapshotListener
            val matches = (doc.getLong("partidas") ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val wins = (doc.getLong("victorias") ?: 0L).coerceIn(0L, matches.toLong()).toInt()
            snapshot = snapshot.copy(stats = LocalMatchStats(matches, wins))
            summaryReceived = true; confirmed()
        }
        listeners += account.collection("historial").orderBy("finalizadaEn", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { docs, error ->
                if (!current()) return@addSnapshotListener
                if (error != null) { failure(error); return@addSnapshotListener }
                if (docs == null || docs.metadata.isFromCache || docs.metadata.hasPendingWrites()) return@addSnapshotListener
                snapshot = snapshot.copy(records = docs.documents.mapNotNull { doc ->
                    val time = doc.getTimestamp("finalizadaEn")?.toDate()?.time ?: return@mapNotNull null
                    if (doc.getString("uid") != uid) return@mapNotNull null
                    MatchRecord(doc.getString("matchKey").orEmpty(), time, doc.getString("mapKey").orEmpty(),
                        doc.getString("mapName").orEmpty(), doc.getString("roleKey").orEmpty(),
                        doc.getString("roleName").orEmpty(), doc.getBoolean("won") == true,
                        doc.getString("origen").orEmpty())
                }, processing = docs.documents.any { it.getBoolean("contabilizada") != true })
                historyReceived = true; confirmed()
            }
    }

    private fun stop() {
        generation++
        loadingTimeout?.let(handler::removeCallbacks)
        loadingTimeout = null
        listeners.forEach { it.remove() }; listeners.clear()
    }
    private fun notifyObservers() { observers.toList().forEach { it() } }
    private const val OUTBOX = "AccountHistoryOutbox"
}
