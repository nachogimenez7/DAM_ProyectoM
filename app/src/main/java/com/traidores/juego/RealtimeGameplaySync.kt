package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Ephemeral match coordination stored in RTDB.
 *
 * Client acknowledgements used to live inside the shared Firestore room document. Updating one
 * player's acknowledgement therefore invalidated that document for every listener in the room.
 * Keeping one small RTDB node per player preserves the same synchronization gates without turning
 * every acknowledgement into N Firestore document reads.
 */
class RealtimeGameplaySync(
    database: FirebaseDatabase,
    private val roomId: String,
    private val uid: String,
    private val onClientStatesChanged: ((Map<String, Any?>) -> Unit)? = null,
    private val onVoteReadyStatesChanged: ((List<OnlineVoteReadyState>) -> Unit)? = null,
    private val onError: (Exception) -> Unit
) {
    private val syncRoot: DatabaseReference =
        database.getReference("salas/$roomId/sincronizacion")
    private val clientStatesRoot: DatabaseReference = syncRoot.child(NODE_CLIENTS)
    private val ownClientState: DatabaseReference = clientStatesRoot.child(uid)
    private val voteReadyRoot: DatabaseReference = syncRoot.child(NODE_VOTE_READY)
    private val ownVoteReady: DatabaseReference = voteReadyRoot.child(uid)

    private var started = false

    private val clientStatesListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val states = snapshot.children.mapNotNull { child ->
                val childUid = child.key?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                childUid to child.value
            }.toMap()
            onClientStatesChanged?.invoke(states)
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error.toException())
        }
    }

    private val voteReadyListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val states = snapshot.children.mapNotNull { child ->
                val childUid = child.key?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val playerName = child.child(FIELD_PLAYER_NAME)
                    .getValue(String::class.java)
                    .orEmpty()
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                OnlineVoteReadyState(
                    uid = childUid,
                    playerName = playerName,
                    ready = child.child(FIELD_READY).getValue(Boolean::class.java) == true,
                    round = child.child(FIELD_ROUND).getValue(Long::class.java)?.toInt() ?: -1,
                    phaseIndex = child.child(FIELD_PHASE_INDEX)
                        .getValue(Long::class.java)
                        ?.toInt()
                        ?: -1,
                    matchId = child.child(FIELD_MATCH_ID).getValue(String::class.java).orEmpty()
                )
            }
            onVoteReadyStatesChanged?.invoke(states)
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error.toException())
        }
    }

    fun start() {
        if (started) return
        started = true
        if (onClientStatesChanged != null) {
            clientStatesRoot.addValueEventListener(clientStatesListener)
        }
        if (onVoteReadyStatesChanged != null) {
            voteReadyRoot.addValueEventListener(voteReadyListener)
        }
    }

    fun stop() {
        if (!started) return
        if (onClientStatesChanged != null) {
            clientStatesRoot.removeEventListener(clientStatesListener)
        }
        if (onVoteReadyStatesChanged != null) {
            voteReadyRoot.removeEventListener(voteReadyListener)
        }
        started = false
    }

    fun publishClientState(state: Map<String, Any?>): Task<Void> {
        val payload = state.toMutableMap().apply {
            put(FIELD_MATCH_ID, get(FIELD_MATCH_ID) ?: "")
            put(FIELD_UPDATED_AT, ServerValue.TIMESTAMP)
        }
        return ownClientState.setValue(payload)
    }

    fun publishVoteReady(
        matchId: String,
        playerName: String,
        ready: Boolean,
        round: Int,
        phaseIndex: Int
    ): Task<Void> {
        return ownVoteReady.setValue(
            mapOf(
                FIELD_MATCH_ID to matchId,
                FIELD_PLAYER_NAME to playerName,
                FIELD_READY to ready,
                FIELD_ROUND to round,
                FIELD_PHASE_INDEX to phaseIndex,
                FIELD_UPDATED_AT to ServerValue.TIMESTAMP
            )
        )
    }

    companion object {
        private const val NODE_CLIENTS = "clientes"
        private const val NODE_VOTE_READY = "listosVotacion"
        private const val FIELD_MATCH_ID = "matchId"
        private const val FIELD_PLAYER_NAME = "nombre"
        private const val FIELD_READY = "listo"
        private const val FIELD_ROUND = "ronda"
        private const val FIELD_PHASE_INDEX = "phaseIndex"
        private const val FIELD_UPDATED_AT = "actualizadaEn"
    }
}
