package com.traidores.juego

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class RealtimePresenceState(
    val connected: Boolean,
    val changedAtMs: Long
)

/**
 * RTDB presence for one authenticated player inside one online room.
 *
 * The disconnect operation is queued before publishing "conectado" and is
 * registered again after every socket reconnect through /.info/connected.
 */
class RealtimeRoomPresence(
    private val database: FirebaseDatabase,
    private val roomId: String,
    private val uid: String,
    private val onPresenceChanged: (Map<String, RealtimePresenceState>) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val presenceRoot: DatabaseReference =
        database.getReference("salas/$roomId/presencia")
    private val ownPresence: DatabaseReference = presenceRoot.child(uid)
    private val connectionState: DatabaseReference = database.getReference(".info/connected")

    private var desiredConnected = false
    private var started = false

    private val presenceListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val states = snapshot.children.mapNotNull { child ->
                val playerUid = child.key?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val state = child.child(FIELD_STATE).getValue(String::class.java).orEmpty()
                val timestamp = child.child(FIELD_TIMESTAMP).getValue(Long::class.java) ?: 0L
                playerUid to RealtimePresenceState(
                    connected = state == STATE_CONNECTED,
                    changedAtMs = timestamp
                )
            }.toMap()
            onPresenceChanged(states)
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error.toException())
        }
    }

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val socketConnected = snapshot.getValue(Boolean::class.java) == true
            if (socketConnected && desiredConnected) armDisconnectThenPublishOnline()
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error.toException())
        }
    }

    fun start() {
        if (started) return
        started = true
        desiredConnected = true
        presenceRoot.addValueEventListener(presenceListener)
        connectionState.addValueEventListener(connectionListener)
    }

    fun setConnected(connected: Boolean) {
        if (desiredConnected == connected) return
        desiredConnected = connected
        if (!started) return
        if (connected) {
            armDisconnectThenPublishOnline()
        } else {
            publishOffline()
        }
    }

    fun stop(markDisconnected: Boolean) {
        if (!started) return
        desiredConnected = !markDisconnected
        presenceRoot.removeEventListener(presenceListener)
        connectionState.removeEventListener(connectionListener)
        started = false
        if (markDisconnected) publishOffline()
    }

    private fun armDisconnectThenPublishOnline() {
        ownPresence.onDisconnect().setValue(payload(STATE_DISCONNECTED)) { error, _ ->
            if (error != null) {
                onError(error.toException())
                return@setValue
            }
            if (!desiredConnected) return@setValue
            ownPresence.setValue(payload(STATE_CONNECTED))
                .addOnFailureListener(onError)
        }
    }

    private fun publishOffline() {
        ownPresence.onDisconnect().cancel().addOnCompleteListener {
            ownPresence.setValue(payload(STATE_DISCONNECTED))
                .addOnFailureListener(onError)
        }
    }

    private fun payload(state: String): Map<String, Any> = mapOf(
        FIELD_STATE to state,
        FIELD_TIMESTAMP to ServerValue.TIMESTAMP
    )

    companion object {
        const val STATE_CONNECTED = "conectado"
        const val STATE_DISCONNECTED = "desconectado"
        private const val FIELD_STATE = "estado"
        private const val FIELD_TIMESTAMP = "ts"
    }
}
