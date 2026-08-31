package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Estado publico caliente de la partida. RTDB distribuye cada cambio sin convertir una
 * escritura del host en N lecturas Firestore; el checkpoint durable vive en una ruta separada.
 */
class RealtimeAuthoritativeState(
    database: FirebaseDatabase,
    roomId: String,
    private val matchId: String,
    private val uid: String,
    private val onStateChanged: (Map<String, Any?>) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val reference = database.getReference("salas/$roomId/$NODE")
    private var started = false

    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val payload = snapshot.value.asStringAnyMap() ?: return
            if ((payload[FIELD_MATCH_ID] as? String).orEmpty() != matchId) return
            val rawState = payload[FIELD_STATE].asStringAnyMap() ?: return
            val state = OnlineRealtimeStateCodec.fromRealtime(rawState)
            onStateChanged(state)
        }

        override fun onCancelled(error: DatabaseError) {
            started = false
            onError(error.toException())
        }
    }

    fun start() {
        if (started || matchId.isBlank()) return
        started = true
        reference.addValueEventListener(listener)
    }

    fun stop() {
        if (!started) return
        reference.removeEventListener(listener)
        started = false
    }

    fun publish(state: Map<String, Any?>): Task<Void> {
        return reference.setValue(
            mapOf(
                FIELD_MATCH_ID to matchId,
                FIELD_PHASE_INDEX to ((state[FIELD_PHASE_INDEX] as? Number)?.toInt() ?: 0),
                FIELD_AUTHOR to uid,
                FIELD_UPDATED_AT to ServerValue.TIMESTAMP,
                FIELD_STATE to OnlineRealtimeStateCodec.toRealtime(state)
            )
        )
    }

    companion object {
        const val NODE = "estado_partida"
        const val FIELD_MATCH_ID = "matchId"
        const val FIELD_STATE = "estadoPartida"
        const val FIELD_PHASE_INDEX = "phaseIndex"
        const val FIELD_AUTHOR = "actualizadaPor"
        const val FIELD_UPDATED_AT = "actualizadaEn"
    }
}

/** RTDB no admite nombres de jugador como claves si contienen '.', '#', '$', '[' o ']'. */
internal object OnlineRealtimeStateCodec {
    private const val FIELD_VOTES = "votos"
    private const val FIELD_VOTER = "jugador"
    private const val FIELD_TARGET = "objetivo"

    fun toRealtime(state: Map<String, Any?>): Map<String, Any?> {
        val encoded = state.toMutableMap()
        val votes = (state[FIELD_VOTES] as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (voter, target) ->
                val voterName = voter as? String ?: return@mapNotNull null
                val targetName = target as? String ?: return@mapNotNull null
                mapOf(FIELD_VOTER to voterName, FIELD_TARGET to targetName)
            }
            ?.sortedBy { it[FIELD_VOTER] }
            .orEmpty()
        encoded[FIELD_VOTES] = votes
        return encoded
    }

    fun fromRealtime(state: Map<String, Any?>): Map<String, Any?> {
        val decoded = state.toMutableMap()
        val votes = (state[FIELD_VOTES] as? List<*>)
            ?.mapNotNull { it.asStringAnyMap() }
            ?.mapNotNull { vote ->
                val voter = (vote[FIELD_VOTER] as? String).orEmpty()
                val target = (vote[FIELD_TARGET] as? String).orEmpty()
                if (voter.isBlank() || target.isBlank()) null else voter to target
            }
            ?.toMap()
            .orEmpty()
        decoded[FIELD_VOTES] = votes
        return decoded
    }
}

object OnlineAuthoritativeStateStore {
    const val COLLECTION = "runtime"
    const val DOCUMENT = "authoritative"
    const val FIELD_MATCH_ID = "matchId"
    const val FIELD_STATE = "estadoPartida"
    const val FIELD_PHASE_INDEX = "phaseIndex"
    const val FIELD_UPDATED_AT = "actualizadaEn"
    const val FIELD_UPDATED_LOCAL = "actualizadaEnLocal"
    const val FIELD_AUTHOR = "actualizadaPor"

    fun checkpointState(
        checkpoint: Map<String, Any?>?,
        expectedMatchId: String
    ): Map<String, Any?>? {
        if (checkpoint == null || expectedMatchId.isBlank()) return null
        if ((checkpoint[FIELD_MATCH_ID] as? String).orEmpty() != expectedMatchId) return null
        return checkpoint[FIELD_STATE].asStringAnyMap()
    }

    fun freshest(
        current: Map<String, Any?>?,
        candidate: Map<String, Any?>?
    ): Map<String, Any?>? {
        if (candidate == null) return current
        if (current == null) return candidate
        val currentPhase = (current[FIELD_PHASE_INDEX] as? Number)?.toInt() ?: -1
        val candidatePhase = (candidate[FIELD_PHASE_INDEX] as? Number)?.toInt() ?: -1
        if (candidatePhase != currentPhase) {
            return if (candidatePhase > currentPhase) candidate else current
        }
        val currentUpdated = (current[FIELD_UPDATED_LOCAL] as? Number)?.toLong() ?: 0L
        val candidateUpdated = (candidate[FIELD_UPDATED_LOCAL] as? Number)?.toLong() ?: 0L
        return if (candidateUpdated >= currentUpdated) candidate else current
    }

    /** El checkpoint del servidor gana un empate de fase aunque los celulares difieran de reloj. */
    fun freshestForRecovery(
        roomState: Map<String, Any?>?,
        checkpointState: Map<String, Any?>?
    ): Map<String, Any?>? {
        if (checkpointState == null) return roomState
        if (roomState == null) return checkpointState
        val roomPhase = (roomState[FIELD_PHASE_INDEX] as? Number)?.toInt() ?: -1
        val checkpointPhase =
            (checkpointState[FIELD_PHASE_INDEX] as? Number)?.toInt() ?: -1
        return if (checkpointPhase >= roomPhase) checkpointState else roomState
    }
}

private fun Any?.asStringAnyMap(): Map<String, Any?>? {
    return (this as? Map<*, *>)?.entries
        ?.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            key to entry.value
        }
        ?.toMap()
}
