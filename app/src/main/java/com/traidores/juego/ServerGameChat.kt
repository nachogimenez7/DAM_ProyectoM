package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

internal data class ServerChatMessage(val id: String, val uid: String, val text: String, val timestampMs: Long)

/** A bounded query and a 16-slot ring per player/channel; no Firestore write per message. */
internal class ServerGameChat(
    private val roomId: String, private val uid: String,
    private val onMessages: (List<ServerChatMessage>) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val root = FirebaseEmulatorConfig.database.getReference("onlineV3/$roomId")
    private var query: Query? = null
    private var listener: ValueEventListener? = null
    private var channel = ""
    private var matchId = ""
    private var generation = 0
    fun watch(nextChannel: String, state: ServerGameSnapshot) {
        require(nextChannel in channels(state))
        if (channel == nextChannel && matchId == state.publicState.matchId && listener != null) return
        stop(); channel = nextChannel; matchId = state.publicState.matchId
        val expected = ++generation
        val next = root.child("chat/$channel").orderByChild("ts").limitToLast(60)
        val receive = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (generation != expected) return
                val messages = snapshot.children.mapNotNull { child ->
                    if (child.child("matchId").getValue(String::class.java) != matchId) return@mapNotNull null
                    val actor = child.child("actorUid").getValue(String::class.java) ?: return@mapNotNull null
                    val text = child.child("text").getValue(String::class.java)?.takeIf { it.length in 1..300 } ?: return@mapNotNull null
                    val time = child.child("ts").getValue(Long::class.java) ?: return@mapNotNull null
                    ServerChatMessage(child.key.orEmpty(), actor, text, time)
                }.sortedBy { it.timestampMs }
                OnlineNetworkMetrics.count("v3_chat_recibido")
                onMessages(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                if (generation == expected) { stop(); onMessages(emptyList()); onError(error.toException()) }
            }
        }
        query = next; listener = receive; next.addValueEventListener(receive)
    }
    fun send(text: String, state: ServerGameSnapshot, nowMs: Long?): Task<Void> {
        val cleaned = text.trim()
        if (cleaned.length !in 1..300 || nowMs == null || FirebaseAuth.getInstance().currentUser?.uid != uid ||
            !canSend(channel, state) || nowMs >= (state.publicState.deadlineMs ?: 0L))
            return Tasks.forException(IllegalStateException("No podés enviar mensajes en este momento."))
        val sendingChannel = channel
        val currentMatch = state.publicState.matchId
        return root.child("chatRate/$uid").get().continueWithTask { task ->
            val rate = task.getResult(Exception::class.java)
            if (FirebaseAuth.getInstance().currentUser?.uid != uid || matchId != currentMatch || channel != sendingChannel)
                return@continueWithTask Tasks.forException<Void>(IllegalStateException("El canal cambió. Probá de nuevo."))
            if (nowMs - (rate.child("ts").getValue(Long::class.java) ?: 0L) < 2000)
                return@continueWithTask Tasks.forException<Void>(IllegalStateException("Esperá dos segundos entre mensajes."))
            val slot = (((rate.child("slot").getValue(Long::class.java) ?: -1L) + 1L) % 16L).toInt()
            val id = "${uid}_$slot"
            OnlineNetworkMetrics.count("v3_chat_enviado")
            root.updateChildren(mapOf(
                "chat/$sendingChannel/$id" to mapOf("actorUid" to uid, "matchId" to currentMatch,
                    "phaseIndex" to state.publicState.phaseIndex, "slot" to slot, "text" to cleaned, "ts" to ServerValue.TIMESTAMP),
                "chatRate/$uid" to mapOf("messageId" to id, "channel" to sendingChannel,
                    "slot" to slot, "ts" to ServerValue.TIMESTAMP)))
        }
    }
    fun stop() {
        generation++; listener?.let { query?.removeEventListener(it) }
        listener = null; query = null; channel = ""; matchId = ""
    }
    companion object {
        fun channels(state: ServerGameSnapshot) = buildList {
            add("publico")
            if (state.human.alive && state.ownRoleKey in setOf("asesino", "mercenario", "espia")) add("traidores")
            if (!state.human.alive) add("muertos")
        }
        fun canSend(channel: String, state: ServerGameSnapshot) = when (channel) {
            "publico" -> state.permissions.publicChat
            "traidores" -> state.permissions.traitorChat
            "muertos" -> state.permissions.deadChat
            else -> false
        }
        fun title(channel: String) = when (channel) { "traidores" -> "TRAIDORES"; "muertos" -> "ESPECTADORES"; else -> "PUEBLO" }
    }
}
