package com.traidores.juego

import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Votación de mesa para frenar texto libre sin quitar respuestas rápidas.
 * RTDB es la autoridad: el bloqueo también se aplica en sus reglas.
 */
class RealtimeTableSilence(
    private val activity: AppCompatActivity,
    private val roomId: String,
    private val ownUid: String,
    private val ownName: () -> String,
    private val isOwnPlayerAlive: () -> Boolean,
    private val aliveCount: () -> Int,
    private val onOwnSilenceChanged: (Boolean) -> Unit
) {
    private val room = FirebaseDatabase.getInstance().getReference("salas/$roomId")
    private var ownSilenceListener: ValueEventListener? = null
    private var proposalListener: ValueEventListener? = null
    private var votesListener: ValueEventListener? = null
    private var votesTarget = ""
    private var shownProposalKey = ""
    private var lastProposalAtMs = 0L

    fun start() {
        stop()
        ownSilenceListener = room.child("silenciados").child(ownUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onOwnSilenceChanged(snapshot.exists())
                }

                override fun onCancelled(error: DatabaseError) {
                    OnlineDebugLog.e("own_table_silence_listener_failure roomId=$roomId", error.toException())
                }
            })
        proposalListener = room.child("propuesta_silencio")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val targetUid = snapshot.child("objetivoUid").getValue(String::class.java).orEmpty()
                    val targetName = snapshot.child("objetivoNombre").getValue(String::class.java).orEmpty()
                    val proposerUid = snapshot.child("proponenteUid").getValue(String::class.java).orEmpty()
                    val createdAt = snapshot.child("ts").getValue(Long::class.java) ?: 0L
                    if (targetUid.isBlank() || targetName.isBlank()) return
                    listenVotes(targetUid)
                    val key = "$targetUid:$createdAt"
                    if (
                        createdAt <= 0L ||
                        System.currentTimeMillis() - createdAt > PROPOSAL_WINDOW_MS ||
                        key == shownProposalKey ||
                        ownUid == targetUid ||
                        ownUid == proposerUid ||
                        !isOwnPlayerAlive()
                    ) return
                    shownProposalKey = key
                    GameDialog.confirm(
                        activity = activity,
                        title = "VOTACIÓN DE SILENCIO",
                        message = "¿Silenciar el texto libre de $targetName? Sus respuestas rápidas seguirán disponibles.",
                        positiveLabel = "VOTAR SÍ",
                        negativeLabel = "NO"
                    ) { vote(targetUid) }
                }

                override fun onCancelled(error: DatabaseError) {
                    OnlineDebugLog.e("table_silence_proposal_failure roomId=$roomId", error.toException())
                }
            })
    }

    fun propose(targetUid: String, targetName: String) {
        if (targetUid.isBlank() || targetUid == ownUid || aliveCount() < 5 || !isOwnPlayerAlive()) return
        val now = System.currentTimeMillis()
        if (now - lastProposalAtMs < PROPOSAL_COOLDOWN_MS) {
            GameNotice.show(activity, "Esperá un minuto antes de proponer otro silencio.")
            return
        }
        lastProposalAtMs = now
        val proposal = mapOf(
            "objetivoUid" to targetUid,
            "objetivoNombre" to targetName.take(18),
            "proponenteUid" to ownUid,
            "proponenteNombre" to ownName().take(18),
            "ts" to ServerValue.TIMESTAMP
        )
        room.child("propuesta_silencio").setValue(proposal)
            .addOnSuccessListener {
                vote(targetUid)
                listenVotes(targetUid)
                GameNotice.show(activity, "Votación iniciada.")
            }
            .addOnFailureListener { error ->
                GameNotice.show(
                    activity,
                    OnlineErrorMessages.forAction("No se pudo iniciar la votación", error),
                    GameNotice.Duration.LONG
                )
            }
    }

    private fun vote(targetUid: String) {
        room.child("votos_silencio").child(targetUid).child(ownUid)
            .setValue(ServerValue.TIMESTAMP)
    }

    private fun listenVotes(targetUid: String) {
        if (votesTarget == targetUid && votesListener != null) return
        votesListener?.let { room.child("votos_silencio").child(votesTarget).removeEventListener(it) }
        votesTarget = targetUid
        votesListener = room.child("votos_silencio").child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val required = (aliveCount() / 2) + 1
                    if (snapshot.childrenCount < required.coerceAtLeast(3)) return
                    room.child("silenciados").child(targetUid).setValue(
                        mapOf("ts" to ServerValue.TIMESTAMP, "votos" to snapshot.childrenCount)
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    OnlineDebugLog.e("table_silence_votes_failure roomId=$roomId", error.toException())
                }
            })
    }

    fun stop() {
        ownSilenceListener?.let { room.child("silenciados").child(ownUid).removeEventListener(it) }
        proposalListener?.let { room.child("propuesta_silencio").removeEventListener(it) }
        votesListener?.let { room.child("votos_silencio").child(votesTarget).removeEventListener(it) }
        ownSilenceListener = null
        proposalListener = null
        votesListener = null
        votesTarget = ""
    }

    private companion object {
        const val PROPOSAL_WINDOW_MS = 30_000L
        const val PROPOSAL_COOLDOWN_MS = 60_000L
    }
}
