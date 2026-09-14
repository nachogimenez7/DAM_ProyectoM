package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source

internal object OnlineOwnedRoomCleanupPolicy {
    fun canReplaceWithNewRoom(
        state: String,
        stableHostId: String,
        activeHostId: String,
        requesterId: String
    ): Boolean = requesterId.isNotBlank() &&
        state == OnlineRoomFirestore.STATE_WAITING &&
        stableHostId == requesterId &&
        activeHostId == requesterId
}

/**
 * Spark has no scheduled backend. When a creator explicitly asks for a new room, remove their
 * older waiting rooms first. This closes rooms left behind by a killed app without granting one
 * client permission to delete another creator's data.
 */
internal class OnlineOwnedWaitingRoomCleaner(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase
) {
    fun cleanupBeforeCreate(requesterId: String): Task<Int> {
        if (requesterId.isBlank()) return Tasks.forResult(0)
        return firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .whereEqualTo(OnlineRoomFirestore.FIELD_HOST_ID, requesterId)
            .get(Source.SERVER)
            .continueWithTask { queryTask ->
                val rooms = queryTask.result.documents.filter { room ->
                    OnlineOwnedRoomCleanupPolicy.canReplaceWithNewRoom(
                        state = room.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty(),
                        stableHostId = room.getString(OnlineRoomFirestore.FIELD_HOST_ID).orEmpty(),
                        activeHostId = room.getString(OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID).orEmpty(),
                        requesterId = requesterId
                    )
                }
                if (rooms.isEmpty()) {
                    Tasks.forResult(0)
                } else {
                    val cleanups = rooms.map { cleanupRoom(it) }
                    Tasks.whenAllComplete(cleanups).continueWith {
                        cleanups.count { it.isSuccessful }
                    }
                }
            }
    }

    private fun cleanupRoom(room: DocumentSnapshot): Task<Void> {
        val queryTasks = CHILD_COLLECTIONS.map { collection ->
            room.reference.collection(collection).get(Source.SERVER)
        }
        return Tasks.whenAllSuccess<QuerySnapshot>(queryTasks)
            .continueWithTask { loadedTask ->
                val batch = firestore.batch()
                loadedTask.result
                    .asSequence()
                    .flatMap { it.documents.asSequence() }
                    .take(MAX_CHILD_DOCUMENTS_PER_ROOM)
                    .forEach { batch.delete(it.reference) }
                batch.delete(
                    room.reference
                        .collection(OnlineAuthoritativeStateStore.COLLECTION)
                        .document(OnlineAuthoritativeStateStore.DOCUMENT)
                )
                val code = room.getString(OnlineRoomFirestore.FIELD_ROOM_CODE).orEmpty()
                if (code.isNotBlank()) {
                    batch.delete(
                        firestore.collection(OnlineRoomFirestore.ROOM_CODES_COLLECTION).document(code)
                    )
                }
                batch.delete(room.reference)
                batch.commit()
            }
            .continueWithTask {
                database.getReference("salas/${room.id}").removeValue()
            }
    }

    private companion object {
        const val MAX_CHILD_DOCUMENTS_PER_ROOM = 480
        val CHILD_COLLECTIONS = listOf(
            OnlineRoomFirestore.PLAYERS_COLLECTION,
            "baneados",
            "repartos",
            "acciones",
            "chat",
            "chat_lobby"
        )
    }
}
