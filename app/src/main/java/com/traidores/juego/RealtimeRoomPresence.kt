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
    private val onOwnPresenceReady: () -> Unit = {},
    private val onOwnPresenceUnavailable: () -> Unit = {},
    private val onError: (Exception) -> Unit
) {
    private val presenceRoot: DatabaseReference =
        database.getReference("salas/$roomId/presencia")
    private val ownPresence: DatabaseReference = presenceRoot.child(uid)
    private val ownMembership: DatabaseReference =
        database.getReference("salas/$roomId/miembros/$uid")
    private val connectionState: DatabaseReference = database.getReference(".info/connected")

    private var desiredConnected = false
    private var started = false
    private var socketConnected = false
    private var membershipGranted = false
    private var membershipAccessKey = ""
    private var publishGeneration = 0

    private val membershipListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val granted = snapshot.exists() &&
                snapshot.child("activo").getValue(Boolean::class.java) == true
            val nextAccessKey = if (granted) membershipAccessKey(snapshot) else ""
            val accessChanged = granted && nextAccessKey != membershipAccessKey
            membershipGranted = granted
            membershipAccessKey = nextAccessKey
            if (!granted) {
                markOwnPresenceUnavailable()
            } else if (accessChanged && socketConnected && desiredConnected) {
                // Los permisos de chat dependen de vivo/traidor/invitadoOraculo. Si alguno
                // cambia (por ejemplo, justo al morir), hay que volver a habilitar los
                // listeners de contenido aunque el jugador ya figurara como miembro activo.
                armDisconnectThenPublishOnline()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            membershipGranted = false
            markOwnPresenceUnavailable()
            onError(error.toException())
        }
    }

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
            markOwnPresenceUnavailable()
            onError(error.toException())
        }
    }

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val connected = snapshot.getValue(Boolean::class.java) == true
            socketConnected = connected
            if (connected && desiredConnected && membershipGranted) {
                armDisconnectThenPublishOnline()
            } else if (!connected) {
                markOwnPresenceUnavailable()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            socketConnected = false
            markOwnPresenceUnavailable()
            onError(error.toException())
        }
    }

    fun start() {
        if (started) return
        started = true
        desiredConnected = true
        markOwnPresenceUnavailable()
        ownMembership.addValueEventListener(membershipListener)
        presenceRoot.addValueEventListener(presenceListener)
        connectionState.addValueEventListener(connectionListener)
    }

    fun setConnected(connected: Boolean) {
        if (desiredConnected == connected) return
        desiredConnected = connected
        if (!started) return
        if (connected) {
            if (socketConnected && membershipGranted) armDisconnectThenPublishOnline()
        } else {
            markOwnPresenceUnavailable()
            publishOffline()
        }
    }

    /**
     * Revalida el permiso de los listeners de contenido. Se usa cuando RTDB cancela uno:
     * primero se vuelve a confirmar la presencia propia y solo el callback de exito permite
     * enganchar chats/emotes otra vez.
     */
    fun refresh() {
        if (!started || !desiredConnected || !socketConnected || !membershipGranted) return
        armDisconnectThenPublishOnline()
    }

    fun stop(markDisconnected: Boolean) {
        if (!started) return
        desiredConnected = false
        socketConnected = false
        membershipGranted = false
        membershipAccessKey = ""
        markOwnPresenceUnavailable()
        ownMembership.removeEventListener(membershipListener)
        presenceRoot.removeEventListener(presenceListener)
        connectionState.removeEventListener(connectionListener)
        started = false
        if (markDisconnected) publishOffline()
    }

    private fun armDisconnectThenPublishOnline() {
        val generation = ++publishGeneration
        ownPresence.onDisconnect().setValue(payload(STATE_DISCONNECTED)) { error, _ ->
            if (error != null) {
                if (isCurrentPublish(generation)) markOwnPresenceUnavailable()
                onError(error.toException())
                return@setValue
            }
            if (!isCurrentPublish(generation)) return@setValue
            ownPresence.setValue(payload(STATE_CONNECTED))
                .addOnSuccessListener {
                    if (isCurrentPublish(generation)) onOwnPresenceReady()
                }
                .addOnFailureListener { failure ->
                    if (isCurrentPublish(generation)) markOwnPresenceUnavailable()
                    onError(failure)
                }
        }
    }

    private fun publishOffline() {
        ownPresence.onDisconnect().cancel().addOnCompleteListener {
            ownPresence.setValue(payload(STATE_DISCONNECTED))
                .addOnFailureListener(onError)
        }
    }

    private fun isCurrentPublish(generation: Int): Boolean {
        return started &&
            desiredConnected &&
            socketConnected &&
            membershipGranted &&
            publishGeneration == generation
    }

    private fun markOwnPresenceUnavailable() {
        publishGeneration += 1
        onOwnPresenceUnavailable()
    }

    private fun membershipAccessKey(snapshot: DataSnapshot): String {
        return listOf(
            snapshot.child("nombre").getValue(String::class.java).orEmpty(),
            snapshot.child("enLobby").getValue(Boolean::class.java) == true,
            snapshot.child("vivo").getValue(Boolean::class.java) == true,
            snapshot.child("traidor").getValue(Boolean::class.java) == true,
            snapshot.child("invitadoOraculo").getValue(Boolean::class.java) == true
        ).joinToString("|")
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
