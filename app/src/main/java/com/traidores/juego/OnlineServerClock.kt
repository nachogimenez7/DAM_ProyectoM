package com.traidores.juego

import android.os.SystemClock
import com.google.firebase.database.*

/** A server offset anchors a monotonic clock, so changing Android's date does not hide rooms. */
internal class OnlineServerClock(private val onReady: () -> Unit) {
    private val reference = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
    private var baseEpochMs: Long? = null
    private var baseElapsedMs = 0L
    private var started = false
    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!started) return
            val offset = snapshot.getValue(Long::class.java) ?: return
            baseEpochMs = System.currentTimeMillis() + offset
            baseElapsedMs = SystemClock.elapsedRealtime()
            onReady()
        }
        override fun onCancelled(error: DatabaseError) = Unit
    }
    fun nowMs(): Long? = baseEpochMs?.plus(SystemClock.elapsedRealtime() - baseElapsedMs)
    fun start() { if (!started) { started = true; reference.addValueEventListener(listener) } }
    fun stop() { started = false; reference.removeEventListener(listener) }
}
