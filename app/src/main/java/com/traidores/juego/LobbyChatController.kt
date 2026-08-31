package com.traidores.juego

import android.os.SystemClock
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class LobbyChatMessage(
    val id: String,
    val actorId: String,
    val speaker: String,
    val message: String,
    val emoteId: String?,
    val createdAtLocal: Long,
    val isSystem: Boolean = false
)

class LobbyChatController(
    private val database: FirebaseDatabase,
    private val roomId: String,
    private val actorId: String,
    private val speaker: String,
    private val onMessagesChanged: (List<LobbyChatMessage>) -> Unit,
    private val onError: (Exception) -> Unit,
    private val onRateLimited: (Long) -> Unit = {},
    private val onAccessCancelled: () -> Unit = {}
) {
    private val chatReference
        get() = database.getReference("salas/$roomId/$NODE")

    private var activeQuery: Query? = null
    private var listener: ValueEventListener? = null
    private var lastSendAttemptAtMs = 0L
    private var nextMessageSlot = 0

    fun start() {
        stop()
        val query = chatReference.orderByKey()
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children
                    .flatMap { playerOrLegacyMessage ->
                        if (playerOrLegacyMessage.hasChild(FIELD_ACTOR_ID)) {
                            listOfNotNull(parseMessage(playerOrLegacyMessage))
                        } else {
                            playerOrLegacyMessage.children.mapNotNull(::parseMessage)
                        }
                    }
                    .sortedBy(LobbyChatMessage::createdAtLocal)
                    .takeLast(MAX_MESSAGES)
                onMessagesChanged(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                if (this@LobbyChatController.listener !== this) return
                stop()
                onError(error.toException())
                onAccessCancelled()
            }
        }
        activeQuery = query
        listener = valueListener
        query.addValueEventListener(valueListener)
    }

    fun stop() {
        val valueListener = listener
        val query = activeQuery
        if (valueListener != null && query != null) query.removeEventListener(valueListener)
        listener = null
        activeQuery = null
    }

    fun sendText(rawMessage: String, onComplete: () -> Unit = {}) {
        val normalized = rawMessage.trim().replace(Regex("\\s+"), " ").take(MAX_TEXT_LENGTH)
        if (normalized.isBlank()) return
        send(normalized, null, onComplete)
    }

    fun sendEmote(emote: EmoteSpec, onComplete: () -> Unit = {}) {
        send(emote.label.take(MAX_TEXT_LENGTH), emote.id, onComplete)
    }

    private fun send(message: String, emoteId: String?, onComplete: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val generalRemainingMs = remainingCooldown(now, lastSendAttemptAtMs, MESSAGE_COOLDOWN_MS)
        val emoteRemainingMs = if (emoteId == null) {
            0L
        } else {
            remainingCooldown(now, lastSendAttemptAtMs, EMOTE_COOLDOWN_MS)
        }
        val remainingMs = maxOf(generalRemainingMs, emoteRemainingMs)
        if (remainingMs > 0L) {
            onRateLimited(remainingMs)
            return
        }

        lastSendAttemptAtMs = now
        val payload = hashMapOf<String, Any>(
            FIELD_ACTOR_ID to actorId,
            FIELD_SPEAKER to speaker.take(18),
            FIELD_MESSAGE to message,
            FIELD_TYPE to if (emoteId == null) TYPE_TEXT else TYPE_EMOTE,
            FIELD_TIMESTAMP to ServerValue.TIMESTAMP
        )
        if (emoteId != null) payload[FIELD_EMOTE_ID] = emoteId
        val slot = nextMessageSlot.toString()
        nextMessageSlot = (nextMessageSlot + 1) % MESSAGE_SLOTS_PER_PLAYER
        chatReference.child(actorId).child(slot).setValue(payload)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener(onError)
    }

    private fun parseMessage(snapshot: DataSnapshot): LobbyChatMessage? {
        val actor = snapshot.child(FIELD_ACTOR_ID).getValue(String::class.java).orEmpty()
        val author = snapshot.child(FIELD_SPEAKER).getValue(String::class.java).orEmpty()
        val body = snapshot.child(FIELD_MESSAGE).getValue(String::class.java).orEmpty()
        if (actor.isBlank() || author.isBlank() || body.isBlank()) return null
        val timestamp = snapshot.child(FIELD_TIMESTAMP).getValue(Long::class.java) ?: 0L
        return LobbyChatMessage(
            id = "$actor:${snapshot.key.orEmpty()}:$timestamp",
            actorId = actor,
            speaker = author,
            message = body,
            emoteId = snapshot.child(FIELD_EMOTE_ID)
                .getValue(String::class.java)
                ?.takeIf { it.isNotBlank() },
            createdAtLocal = timestamp
        )
    }

    private fun remainingCooldown(nowMs: Long, lastAttemptAtMs: Long, cooldownMs: Long): Long {
        if (lastAttemptAtMs <= 0L) return 0L
        return (cooldownMs - (nowMs - lastAttemptAtMs)).coerceAtLeast(0L)
    }

    companion object {
        const val NODE = "chat_lobby"
        const val MAX_MESSAGES = 30
        const val MESSAGE_COOLDOWN_MS = 1_200L
        const val EMOTE_COOLDOWN_MS = 4_000L
        const val MESSAGE_SLOTS_PER_PLAYER = 2
        private const val MAX_TEXT_LENGTH = 140
        private const val FIELD_ACTOR_ID = "actorId"
        private const val FIELD_SPEAKER = "speaker"
        private const val FIELD_MESSAGE = "mensaje"
        private const val FIELD_EMOTE_ID = "emoteId"
        private const val FIELD_TYPE = "tipo"
        private const val FIELD_TIMESTAMP = "ts"
        private const val TYPE_TEXT = "texto"
        private const val TYPE_EMOTE = "emote"
    }
}
