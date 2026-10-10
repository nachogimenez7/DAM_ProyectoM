package com.traidores.juego

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/** Debug-only handoff into the real lobby/table. Uses the normal Debug App Check provider. */
class CloudQaLaunchActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val label = TextView(this).apply { text = "Preparando sala privada de prueba…"; textSize = 20f }
        setContentView(label)
        if (!BuildConfig.CLOUD_QA_APP || BuildConfig.APPLICATION_ID != "com.traidores.juego.v3qa" ||
            BuildConfig.USE_ONLINE_AUTHORITY_EMULATOR || !BuildConfig.SERVER_ONLINE_V3 ||
            FirebaseApp.getInstance().options.projectId != "traidores") {
            label.text = "Esta entrada requiere el APK aislado de prueba Cloud."; return
        }
        if (intent.getStringExtra("room") == null) {
            // Generates the device Debug token. The controller registers it privately before login.
            FirebaseAppCheck.getInstance().getAppCheckToken(false)
            return
        }
        Thread {
            try {
                val room = requireNotNull(intent.getStringExtra("room"))
                require(room.startsWith("qa-v3-interactive-"))
                Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(true), 45, TimeUnit.SECONDS)
                Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(
                    requireNotNull(intent.getStringExtra("email")), requireNotNull(intent.getStringExtra("password"))), 45, TimeUnit.SECONDS)
                val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
                val profile = Tasks.await(FirebaseFirestore.getInstance().document("perfiles_publicos/$uid").get(), 30, TimeUnit.SECONDS)
                profile.data?.let { AccountProfileSync.restoreConfirmed(this, it) }
                val doc = Tasks.await(FirebaseFirestore.getInstance().document("partidas/$room").get(), 30, TimeUnit.SECONDS)
                val map = doc.getString("mapa") ?: "pampa"
                val session = GameSession(code = doc.getString("codigoSala").orEmpty(), mapKey = map, mapName = map, players = emptyList())
                runOnUiThread {
                    startActivity(Intent(this, LobbyActivity::class.java)
                        .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_CREATE)
                        .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, "Práctica privada")
                        .putExtra(LobbyActivity.EXTRA_ROOM_CODE, session.code)
                        .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, true)
                        .putExtra(LobbyActivity.EXTRA_SESSION, session)
                        .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, room))
                    finish()
                }
            } catch (error: Exception) {
                android.util.Log.e("TRAIDORES_CLOUD_QA", "No se pudo abrir la sala privada", error)
                runOnUiThread { label.text = "No se pudo abrir la sala privada. Revisaremos la conexión." }
            }
        }.start()
    }
}

/** Captures local traffic/timings through adb without adding controls to the game UI. */
class CloudQaMetricsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.CLOUD_QA_APP) {
            resultData = OnlineNetworkMetrics.summary()
            android.util.Log.i("TRAIDORES_CLOUD_QA_METRICS", resultData.orEmpty())
        }
    }
}
