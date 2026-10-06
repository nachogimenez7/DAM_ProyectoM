package com.traidores.juego

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

internal class ServerGameAccessLost(message: String) : IllegalStateException(message)

internal class ServerGameCallableClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("southamerica-west1")
) {
    fun start(roomId: String): Task<OnlineStartCallableResult> = call("iniciarPartidaV3", mapOf("roomId" to roomId))
        .continueWith { task -> OnlineStartCallableResponseParser.parse(task.getResult(Exception::class.java)) }
    fun action(command: ServerGameCommand): Task<Map<String, Any?>> = call("accionPartidaV3", command.payload())
    fun recover(roomId: String, state: ServerGamePublic) = call("recuperarFaseV3", mapOf(
        "roomId" to roomId, "matchId" to state.matchId, "phaseIndex" to state.phaseIndex))
    fun leave(roomId: String, matchId: String) = call("abandonarPartidaV3", mapOf("roomId" to roomId, "matchId" to matchId))
    fun rematch(roomId: String, matchId: String) = call("prepararRevanchaV3", mapOf("roomId" to roomId, "matchId" to matchId))
    private fun call(name: String, data: Map<String, Any>): Task<Map<String, Any?>> {
        val started = SystemClock.elapsedRealtime()
        OnlineNetworkMetrics.count(name)
        return functions.getHttpsCallable(name).call(data).continueWith { task ->
            OnlineNetworkMetrics.duration(name, SystemClock.elapsedRealtime() - started)
            val result = task.getResult(Exception::class.java).data as? Map<*, *> ?: error("Respuesta online inválida.")
            result.entries.associate { (it.key as? String ?: error("Respuesta online inválida.")) to it.value }
        }
    }
    companion object {
        fun retryable(error: Exception) = error is FirebaseFunctionsException && error.code in setOf(
            FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED)
    }
}

/** Reads only public, this UID's private state and this UID's permissions. Never reads the root. */
internal class ServerGameRealtimeClient(
    private val roomId: String, private val matchId: String, private val uid: String,
    private val onSnapshot: (ServerGameSnapshot) -> Unit,
    private val onConnection: (Boolean) -> Unit,
    private val onSynchronizing: () -> Unit,
    private val onRematch: (String) -> Unit,
    private val onError: (Exception) -> Unit,
    private val database: FirebaseDatabase = FirebaseEmulatorConfig.database
) {
    private val root = database.getReference("onlineV3/$roomId")
    private val connection = database.getReference(".info/connected")
    private val bindings = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var generation = 0
    private var started = false
    private var connected = false
    private var inbox = ServerGameInbox(uid, matchId)
    private var lastSnapshot: ServerGameSnapshot? = null
    private var presenceArmed = false
    fun start() {
        if (started) return
        started = true; generation++; val currentGeneration = generation
        inbox = ServerGameInbox(uid, matchId); lastSnapshot = null; presenceArmed = false
        fun listen(path: DatabaseReference, receive: (DataSnapshot) -> Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!started || generation != currentGeneration) return
                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) { fail(ServerGameAccessLost("La cuenta cambió.")); return }
                    try { receive(snapshot) } catch (error: Exception) { fail(error) }
                }
                override fun onCancelled(error: DatabaseError) {
                    if (started && generation == currentGeneration) fail(if (error.code == DatabaseError.PERMISSION_DENIED)
                        ServerGameAccessLost("Ya no tenés acceso a esta partida.") else error.toException())
                }
            }
            bindings += path to listener; path.addValueEventListener(listener)
        }
        listen(root.child("snapshot/public")) { snap ->
            if (!snap.exists()) { onSynchronizing(); return@listen }
            val value = ServerGameParser.parsePublic(snap.value)
            if (value.matchId != matchId) {
                onRematch(value.matchId); return@listen
            }
            inbox.acceptPublic(value); deliver()
            OnlineNetworkMetrics.count("v3_estado_publico")
        }
        listen(root.child("snapshot/private/$uid")) { snap ->
            if (!snap.exists()) { onSynchronizing(); return@listen }
            val value = ServerGameParser.parsePrivate(snap.value)
            if (value.matchId != matchId) { onSynchronizing(); return@listen }
            inbox.acceptPrivate(value); deliver()
            OnlineNetworkMetrics.count("v3_estado_privado")
        }
        listen(root.child("snapshot/permissions/$uid")) { snap ->
            if (!snap.exists()) { onSynchronizing(); return@listen }
            val access = ServerGameParser.parsePermissions(snap.value)
            if (access.matchId != matchId) { onSynchronizing(); return@listen }
            if (!access.member) { fail(ServerGameAccessLost("Ya no pertenecés a esta partida.")); return@listen }
            inbox.acceptPermissions(access); deliver(); armPresence(currentGeneration)
            OnlineNetworkMetrics.count("v3_permisos")
        }
        listen(connection) { snap ->
            connected = snap.getValue(Boolean::class.java) == true
            if (!connected) presenceArmed = false
            onConnection(connected)
            if (connected) armPresence(currentGeneration)
        }
    }
    private fun deliver() {
        val next = inbox.snapshot()
        if (next == null) { onSynchronizing(); return }
        if (next != lastSnapshot) { lastSnapshot = next; onSnapshot(next) }
    }
    private fun armPresence(currentGeneration: Int) {
        if (!started || !connected || presenceArmed || inbox.permissions?.member != true) return
        presenceArmed = true
        val own = root.child("presence/$uid")
        own.onDisconnect().setValue(mapOf("estado" to "desconectado", "actualizadaEn" to ServerValue.TIMESTAMP))
            .addOnSuccessListener {
                if (started && generation == currentGeneration && connected && FirebaseAuth.getInstance().currentUser?.uid == uid)
                    own.setValue(mapOf("estado" to "conectado", "actualizadaEn" to ServerValue.TIMESTAMP))
                        .addOnFailureListener { if (started && generation == currentGeneration) onError(it) }
            }.addOnFailureListener { if (started && generation == currentGeneration) { presenceArmed = false; onError(it) } }
    }
    fun stop() {
        if (!started) return
        started = false; generation++; connected = false
        bindings.forEach { (ref, listener) -> ref.removeEventListener(listener) }; bindings.clear()
        if (presenceArmed) root.child("presence/$uid").setValue(mapOf("estado" to "desconectado", "actualizadaEn" to ServerValue.TIMESTAMP))
        presenceArmed = false
    }
    private fun fail(error: Exception) { stop(); onError(error) }
}

/** One pending intention, with the same UUID across retry/recreation; projections remain server-owned. */
internal class ServerGameActionSender(
    private val uid: String, private val client: ServerGameCallableClient,
    private val onPending: (ServerGameCommand?) -> Unit,
    private val onConfirmed: (ServerGameCommand) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    var pending: ServerGameCommand? = null; private set
    private var attempts = 0; private var running = false; private var active = false; private var generation = 0
    private var current: ServerGamePublic? = null
    private var retryAtElapsedMs = 0L
    val canRetry: Boolean get() = pending != null && attempts >= 3 && !running
    fun retryPending() { if (canRetry) { attempts = 0; retryAtElapsedMs = 0; sendPending() } }
    fun start(restored: ServerGameCommand? = null) {
        active = true; generation++
        if (pending == null) pending = restored
        attempts = 0; retryAtElapsedMs = 0; onPending(pending)
    }
    fun update(state: ServerGamePublic) {
        current = state
        pending?.let { command ->
            if (command.matchId != state.matchId || command.phaseIndex != state.phaseIndex || state.winner != null) {
                pending = null; onPending(null)
            } else sendPending()
        }
    }
    fun submit(command: ServerGameCommand) {
        if (!active || pending != null) return
        pending = command; attempts = 0; retryAtElapsedMs = 0; onPending(command); sendPending()
    }
    private fun sendPending() {
        val command = pending ?: return
        if (!active || running || attempts >= 3 || SystemClock.elapsedRealtime() < retryAtElapsedMs ||
            current?.matchId != command.matchId || current?.phaseIndex != command.phaseIndex) return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) { pending = null; onPending(null); onError(IllegalStateException("La cuenta cambió.")); return }
        running = true; attempts++; val currentGeneration = generation
        client.action(command).addOnCompleteListener { task ->
            if (!active || generation != currentGeneration) return@addOnCompleteListener
            running = false
            if (pending != command) return@addOnCompleteListener
            if (task.isSuccessful) {
                val receipt = task.result
                if (receipt["accepted"] != true || receipt["matchId"] != command.matchId) {
                    pending = null; onPending(null); onError(IllegalStateException("El recibo online no coincide.")); return@addOnCompleteListener
                }
                pending = null; onPending(null); onConfirmed(command)
            } else {
                val error = task.exception ?: IllegalStateException("No llegó la confirmación online.")
                if (ServerGameCallableClient.retryable(error) && attempts < 3) {
                    val delayMs = 1000L shl (attempts - 1)
                    retryAtElapsedMs = SystemClock.elapsedRealtime() + delayMs
                    handler.postDelayed({ if (active && generation == currentGeneration && pending == command) sendPending() }, delayMs)
                } else {
                    if (!ServerGameCallableClient.retryable(error)) { pending = null; onPending(null) }
                    onError(error)
                }
            }
        }
    }
    fun stop() { active = false; generation++; running = false; handler.removeCallbacksAndMessages(null) }
}
