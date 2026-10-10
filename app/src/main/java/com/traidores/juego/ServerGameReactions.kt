package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*

internal data class ServerReaction(val actorUid: String, val matchId: String, val phaseIndex: Int,
    val slot: Int, val emoteId: String, val timestampMs: Long) {
    val key get() = "$matchId:$actorUid:$slot:$timestampMs"
}

/** A fresh subscription cannot replay the historical ring. Slot replacements have new timestamps. */
internal class ServerReactionCursor(private val matchId: String, private val startedMs: Long) {
    private val seen = linkedSetOf<String>()
    private val recentSlotTimes = mutableMapOf<String, Long>()
    fun accept(value: ServerReaction): Boolean {
        if (value.matchId != matchId || value.timestampMs <= startedMs || !seen.add(value.key)) return false
        // RTDB can correct its own optimistic ServerValue.TIMESTAMP after acknowledgement.
        // Rules enforce ten seconds between legitimate reactions; a nearby timestamp
        // correction of the same slot is the same gesture, not a second animation.
        val slotKey = "${value.actorUid}:${value.slot}"
        val previous = recentSlotTimes[slotKey]
        if (previous != null && kotlin.math.abs(value.timestampMs - previous) < 10_000) return false
        recentSlotTimes[slotKey] = value.timestampMs
        if (seen.size > 160) seen.remove(seen.first())
        return true
    }
}

internal class ServerGameReactions(private val roomId: String, private val uid: String,
    private val onReaction: (ServerReaction) -> Unit, private val onError: (Exception) -> Unit) {
    private val root = FirebaseEmulatorConfig.database.getReference("onlineV3/$roomId")
    private var query: Query? = null
    private var listener: ChildEventListener? = null
    private var matchId = ""
    private var generation = 0
    fun watch(state: ServerGameSnapshot, nowMs: Long?) {
        if (nowMs == null || !receivePhase(state)) { stop(); return }
        if (listener != null && matchId == state.publicState.matchId) return
        stop(); matchId = state.publicState.matchId
        val cursor = ServerReactionCursor(matchId, nowMs)
        val expected = ++generation
        val next = root.child("reactions").orderByChild("ts").startAt((nowMs + 1).toDouble()).limitToLast(40)
        val receive = object : ChildEventListener {
            private fun receive(data: DataSnapshot) {
                if (generation != expected) return
                val actor = data.child("actorUid").getValue(String::class.java) ?: return
                val value = ServerReaction(actor, data.child("matchId").getValue(String::class.java) ?: return,
                    data.child("phaseIndex").getValue(Int::class.java) ?: return,
                    data.child("slot").getValue(Int::class.java) ?: return,
                    data.child("emoteId").getValue(String::class.java) ?: return,
                    data.child("ts").getValue(Long::class.java) ?: return)
                if (!cursor.accept(value)) return
                if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_emote_received key=${value.key}")
                OnlineNetworkMetrics.count("v3_emote_recibido")
                OnlineNetworkMetrics.count("v3_emote_payload_bytes", data.value.toString().toByteArray(Charsets.UTF_8).size)
                onReaction(value)
            }
            override fun onChildAdded(data: DataSnapshot, previousChildName: String?) = receive(data)
            override fun onChildChanged(data: DataSnapshot, previousChildName: String?) = receive(data)
            override fun onChildRemoved(data: DataSnapshot) {}
            override fun onChildMoved(data: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { if (generation == expected) { stop(); onError(error.toException()) } }
        }
        query = next; listener = receive
        OnlineNetworkMetrics.count("v3_emote_suscripcion")
        next.addChildEventListener(receive)
    }
    fun send(emoteId: String, state: ServerGameSnapshot, nowMs: Long?): Task<Void> {
        if (!state.permissions.reactions || !receivePhase(state) || emoteId !in baseIds ||
            nowMs == null || nowMs >= (state.publicState.deadlineMs ?: 0))
            return Tasks.forException(IllegalStateException("No podés enviar emotes en este momento."))
        val current = state.publicState
        return root.child("reactionRate/$uid").get().continueWithTask { task ->
            if (!task.isSuccessful) return@continueWithTask Tasks.forException<Void>(task.exception!!)
            val rate = task.result
            if (nowMs - (rate.child("ts").getValue(Long::class.java) ?: 0) < 10_000)
                return@continueWithTask Tasks.forException<Void>(IllegalStateException("Esperá diez segundos entre emotes."))
            val sameRound = rate.child("round").getValue(Int::class.java) == current.round &&
                rate.child("matchId").getValue(String::class.java) == current.matchId
            val uses = if (sameRound) (rate.child("uses").getValue(Int::class.java) ?: 0) + 1 else 1
            if (uses > 2) return@continueWithTask Tasks.forException<Void>(IllegalStateException("Ya usaste tus emotes de esta ronda."))
            val slot = ((rate.child("slot").getValue(Int::class.java) ?: -1) + 1) % 8
            val id = "${uid}_$slot"
            root.updateChildren(mapOf(
                "reactions/$id" to mapOf("actorUid" to uid, "matchId" to current.matchId, "phaseIndex" to current.phaseIndex,
                    "slot" to slot, "emoteId" to emoteId, "ts" to ServerValue.TIMESTAMP),
                "reactionRate/$uid" to mapOf("reactionId" to id, "slot" to slot, "matchId" to current.matchId,
                    "round" to current.round, "uses" to uses, "ts" to ServerValue.TIMESTAMP)))
        }
    }
    fun stop() {
        generation++; listener?.let { query?.removeEventListener(it) }
        listener = null; query = null; matchId = ""
    }
    companion object {
        val baseIds = EmoteCatalog.all.filterNot { it.isPremium }.map { it.id }.toSet()
        fun receivePhase(s: ServerGameSnapshot) = s.permissions.member && s.publicState.winner == null &&
            s.publicState.phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.CONTRAPUNTO, ServerGamePhase.VOTACION,
                ServerGamePhase.RECUENTO_VOTOS, ServerGamePhase.DESEMPATE_VOTACION, ServerGamePhase.ALCALDE_DESEMPATE)
    }
}
