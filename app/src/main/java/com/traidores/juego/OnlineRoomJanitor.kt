package com.traidores.juego

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/**
 * Retira salas huérfanas creadas por la cuenta actual.
 *
 * En Spark no existe un proceso de servidor programado. Esta limpieza oportunista se ejecuta
 * al volver a abrir el online, usa timestamps del servidor y solo toca salas propias que llevan
 * al menos 24 horas sin actividad. Las reglas repiten esas condiciones: cambiar el reloj del
 * teléfono no permite borrar una partida vigente.
 */
object OnlineRoomJanitor {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_LAST_SWEEP = "onlineRoomCleanupLastRun"
    private const val SWEEP_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val DELETE_PAGE_SIZE = 100L

    private val childCollections = listOf(
        OnlineRoomFirestore.PLAYERS_COLLECTION,
        "acciones",
        "baneados",
        "repartos",
        "chat",
        "chat_lobby",
        "chat_traidores"
    )

    @Volatile
    private var sweepInProgress = false

    fun sweepOwnedStaleRooms(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank() || sweepInProgress) return

        val now = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSweep = preferences.getLong(PREF_LAST_SWEEP, 0L)
        if (now - lastSweep < SWEEP_INTERVAL_MS) return

        sweepInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .whereEqualTo(OnlineRoomFirestore.FIELD_HOST_ID, uid)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                preferences.edit().putLong(PREF_LAST_SWEEP, now).apply()
                val staleRooms = snapshot.documents
                    .filter { room ->
                        val updatedAt = room.getTimestamp(OnlineRoomFirestore.FIELD_UPDATED_AT)
                            ?.toDate()
                            ?.time
                        updatedAt != null && OnlineRoomRetentionPolicy.isStale(updatedAt, now)
                    }
                    .sortedBy { room ->
                        room.getTimestamp(OnlineRoomFirestore.FIELD_UPDATED_AT)?.toDate()?.time
                            ?: Long.MAX_VALUE
                    }
                cleanupNext(
                    rooms = staleRooms,
                    index = 0,
                    firestore = firestore,
                    database = FirebaseDatabase.getInstance()
                )
            }
            .addOnFailureListener { error ->
                sweepInProgress = false
                OnlineDebugLog.e("stale_room_sweep_query_failure uid=$uid", error)
            }
    }

    private fun cleanupNext(
        rooms: List<DocumentSnapshot>,
        index: Int,
        firestore: FirebaseFirestore,
        database: FirebaseDatabase
    ) {
        if (index >= rooms.size) {
            sweepInProgress = false
            return
        }
        val room = rooms[index]
        cleanupChildCollection(
            roomReference = room.reference,
            collectionIndex = 0,
            onComplete = {
                database.getReference("salas/${room.id}")
                    .removeValue()
                    .addOnSuccessListener {
                        deleteRoomAndCode(
                            room = room,
                            firestore = firestore,
                            onComplete = {
                                OnlineDebugLog.i("stale_room_cleanup_done roomId=${room.id}")
                                cleanupNext(rooms, index + 1, firestore, database)
                            }
                        )
                    }
                    .addOnFailureListener { error ->
                        OnlineDebugLog.e("stale_room_rtdb_cleanup_failure roomId=${room.id}", error)
                        cleanupNext(rooms, index + 1, firestore, database)
                    }
            },
            onFailure = { error ->
                OnlineDebugLog.e("stale_room_children_cleanup_failure roomId=${room.id}", error)
                cleanupNext(rooms, index + 1, firestore, database)
            }
        )
    }

    private fun cleanupChildCollection(
        roomReference: DocumentReference,
        collectionIndex: Int,
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (collectionIndex >= childCollections.size) {
            onComplete()
            return
        }
        val collectionName = childCollections[collectionIndex]
        roomReference.collection(collectionName)
            .limit(DELETE_PAGE_SIZE)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    cleanupChildCollection(
                        roomReference,
                        collectionIndex + 1,
                        onComplete,
                        onFailure
                    )
                    return@addOnSuccessListener
                }
                val batch = roomReference.firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        cleanupChildCollection(
                            roomReference,
                            collectionIndex,
                            onComplete,
                            onFailure
                        )
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    private fun deleteRoomAndCode(
        room: DocumentSnapshot,
        firestore: FirebaseFirestore,
        onComplete: () -> Unit
    ) {
        val batch = firestore.batch()
        val roomCode = room.getString(OnlineRoomFirestore.FIELD_ROOM_CODE).orEmpty()
        if (roomCode.isNotBlank()) {
            batch.delete(
                firestore.collection(OnlineRoomFirestore.ROOM_CODES_COLLECTION).document(roomCode)
            )
        }
        batch.delete(room.reference)
        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("stale_room_firestore_cleanup_failure roomId=${room.id}", error)
                onComplete()
            }
    }
}

object OnlineRoomRetentionPolicy {
    const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L

    fun isStale(updatedAtMs: Long, nowMs: Long): Boolean {
        if (updatedAtMs <= 0L || nowMs < updatedAtMs) return false
        return nowMs - updatedAtMs >= STALE_AFTER_MS
    }
}
