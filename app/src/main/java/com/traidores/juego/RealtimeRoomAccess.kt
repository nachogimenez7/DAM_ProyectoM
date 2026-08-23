package com.traidores.juego

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * Access registry mirrored in RTDB by the active host.
 *
 * RTDB rules cannot read Firestore membership or private role documents. This registry gives
 * those rules a server-visible allow-list without letting players admit themselves. The host
 * remains authoritative (the unavoidable Spark-plan limitation), but an authenticated outsider
 * can no longer create presence in an arbitrary room and use it as an access credential.
 */
data class RealtimeRoomMemberAccess(
    val name: String,
    val inLobby: Boolean,
    val alive: Boolean,
    val traitor: Boolean?,
    val oracleInvitedToPublicChat: Boolean = false
)

object RealtimeRoomAccess {
    private const val NODE_CONTROL = "control"
    private const val NODE_MEMBERS = "miembros"
    private const val FIELD_HOST_UID = "hostUid"
    private const val FIELD_CREATOR_UID = "creatorUid"

    fun initializeHost(
        database: FirebaseDatabase,
        roomId: String,
        hostUid: String,
        hostName: String,
        onReady: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        syncMembers(
            database = database,
            roomId = roomId,
            hostUid = hostUid,
            creatorUid = hostUid,
            matchId = "",
            members = mapOf(
                hostUid to RealtimeRoomMemberAccess(
                    name = hostName,
                    inLobby = true,
                    alive = true,
                    traitor = false
                )
            ),
            onComplete = onReady,
            onFailure = onFailure
        )
    }

    fun syncMembers(
        database: FirebaseDatabase,
        roomId: String,
        hostUid: String,
        creatorUid: String? = null,
        matchId: String,
        members: Map<String, RealtimeRoomMemberAccess>,
        onComplete: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (roomId.isBlank() || hostUid.isBlank()) return
        val room = database.getReference("salas/$roomId")
        room.child("$NODE_CONTROL/$FIELD_HOST_UID")
            .setValue(hostUid)
            .addOnSuccessListener {
                val continueSync = {
                    room.child(NODE_MEMBERS).get()
                        .addOnSuccessListener { snapshot ->
                            publishMemberDiff(
                                roomSnapshot = snapshot,
                                roomId = roomId,
                                hostUid = hostUid,
                                matchId = matchId,
                                members = members,
                                database = database,
                                onComplete = onComplete,
                                onFailure = onFailure
                            )
                        }
                        .addOnFailureListener(onFailure)
                    Unit
                }
                if (creatorUid.isNullOrBlank()) {
                    continueSync()
                } else {
                    room.child("$NODE_CONTROL/$FIELD_CREATOR_UID")
                        .setValue(creatorUid)
                        .addOnSuccessListener { continueSync() }
                        .addOnFailureListener(onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun transferHost(
        database: FirebaseDatabase,
        roomId: String,
        nextHostUid: String,
        onComplete: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (roomId.isBlank() || nextHostUid.isBlank()) return
        database.getReference("salas/$roomId/$NODE_CONTROL/$FIELD_HOST_UID")
            .setValue(nextHostUid)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener(onFailure)
    }

    private fun publishMemberDiff(
        roomSnapshot: DataSnapshot,
        roomId: String,
        hostUid: String,
        matchId: String,
        members: Map<String, RealtimeRoomMemberAccess>,
        database: FirebaseDatabase,
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val existing = roomSnapshot.children.associateBy { it.key.orEmpty() }
        val updates = linkedMapOf<String, Any?>()
        existing.keys
            .filter { it.isNotBlank() && it !in members }
            .forEach { uid ->
                updates["$NODE_MEMBERS/$uid"] = null
                updates["presencia/$uid"] = null
            }
        members.forEach { (uid, access) ->
            if (uid.isBlank()) return@forEach
            val previous = existing[uid]
            val previousTraitor = previous
                ?.child("traidor")
                ?.getValue(Boolean::class.java)
                ?: false
            val safeName = access.name.trim().take(18).ifBlank { "Jugador" }
            val traitor = access.traitor ?: previousTraitor
            val changed = previous == null ||
                previous.child("nombre").getValue(String::class.java) != safeName ||
                previous.child("activo").getValue(Boolean::class.java) != true ||
                previous.child("enLobby").getValue(Boolean::class.java) != access.inLobby ||
                previous.child("vivo").getValue(Boolean::class.java) != access.alive ||
                previous.child("traidor").getValue(Boolean::class.java) != traitor ||
                previous.child("invitadoOraculo").getValue(Boolean::class.java) !=
                    access.oracleInvitedToPublicChat
            if (!changed) return@forEach
            updates["$NODE_MEMBERS/$uid"] = mapOf(
                "nombre" to safeName,
                "activo" to true,
                "enLobby" to access.inLobby,
                "vivo" to access.alive,
                "traidor" to traitor,
                "invitadoOraculo" to access.oracleInvitedToPublicChat,
                "actualizadaEn" to ServerValue.TIMESTAMP
            )
        }
        updates["$NODE_CONTROL/$FIELD_HOST_UID"] = hostUid
        updates["$NODE_CONTROL/matchId"] = matchId.take(80)
        updates["$NODE_CONTROL/jugadoresVivos"] = members.values.count { it.alive && !it.inLobby }
        updates["$NODE_CONTROL/actualizadaEn"] = ServerValue.TIMESTAMP
        database.getReference("salas/$roomId")
            .updateChildren(updates)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener(onFailure)
    }
}
