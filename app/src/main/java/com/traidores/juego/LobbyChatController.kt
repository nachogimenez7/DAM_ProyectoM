package com.traidores.juego

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
    private val onError: (Exception) -> Unit
) {
    private val chatReference
        get() = database.getReference("salas/$roomId/$NODE")

    private var activeQuery: Query? = null
    private var listener: ValueEventListener? = null

    fun start() {
        stop()
        val query = chatReference.orderByKey().limitToLast(MAX_MESSAGES)
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    val actor = child.child(FIELD_ACTOR_ID).getValue(String::class.java).orEmpty()
                    val author = child.child(FIELD_SPEAKER).getValue(String::class.java).orEmpty()
                    val body = child.child(FIELD_MESSAGE).getValue(String::class.java).orEmpty()
                    if (actor.isBlank() || author.isBlank() || body.isBlank()) return@mapNotNull null
                    LobbyChatMessage(
                        id = child.key.orEmpty(),
                        actorId = actor,
                        speaker = author,
                        message = body,
                        emoteId = child.child(FIELD_EMOTE_ID)
                            .getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() },
                        createdAtLocal = child.child(FIELD_TIMESTAMP)
                            .getValue(Long::class.java)
                            ?: 0L
                    )
                }
                onMessagesChanged(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
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
        val payload = hashMapOf<String, Any>(
            FIELD_ACTOR_ID to actorId,
            FIELD_SPEAKER to speaker.take(18),
            FIELD_MESSAGE to message,
            FIELD_TYPE to if (emoteId == null) TYPE_TEXT else TYPE_EMOTE,
            FIELD_TIMESTAMP to ServerValue.TIMESTAMP
        )
        if (emoteId != null) payload[FIELD_EMOTE_ID] = emoteId
        chatReference.push().setValue(payload)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener(onError)
    }

    companion object {
        const val NODE = "chat_lobby"
        const val MAX_MESSAGES = 30
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
