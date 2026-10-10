package com.traidores.juego

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/** Native SDK driver, absent in Release. A synthetic App Check token is accepted only by emulators. */
class ServerGameSmokeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val label = TextView(this).apply { text = "Conectando prueba nativa V3…"; textSize = 20f }
        setContentView(label)
        if (!BuildConfig.USE_ONLINE_AUTHORITY_EMULATOR || !BuildConfig.SERVER_ONLINE_V3 ||
            FirebaseApp.getInstance().options.projectId != "traidores-local") {
            label.text = "V3 QA BLOCKED: isolated emulator configuration required"; return
        }
        fun encoded(value: String) = Base64.encodeToString(value.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val appId = FirebaseApp.getInstance().options.applicationId
        val epochSeconds = System.currentTimeMillis() / 1000
        val jwt = "${encoded("{\"alg\":\"none\",\"typ\":\"JWT\"}")}.${encoded("{\"app_id\":\"$appId\",\"sub\":\"$appId\",\"iat\":$epochSeconds,\"exp\":${epochSeconds + 3600}}")}.local"
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory { _ ->
            com.google.firebase.appcheck.AppCheckProvider { Tasks.forResult(object : AppCheckToken() {
                override fun getToken() = jwt
                override fun getExpireTimeMillis() = System.currentTimeMillis() + 3600000
            }) }
        }
        val launch = { Thread {
            try {
                val room = requireNotNull(intent.getStringExtra("room"))
                Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(
                    requireNotNull(intent.getStringExtra("email")), requireNotNull(intent.getStringExtra("password"))), 30, TimeUnit.SECONDS)
                val openLobby = intent.getBooleanExtra("open_lobby", false)
                val recoverOnly = intent.getBooleanExtra("recover_only", false)
                val startedMatch = if (openLobby || recoverOnly) "" else {
                    val started = Tasks.await(ServerGameCallableClient().start(room), 30, TimeUnit.SECONDS)
                    (started as? OnlineStartCallableResult.Accepted)?.matchId ?: error("V3 start not accepted")
                }
                val snapshot = Tasks.await(FirebaseFirestore.getInstance().document("partidas/$room").get(), 30, TimeUnit.SECONDS)
                val match = if (recoverOnly) requireNotNull(snapshot.getString("partidaInicial.matchId")) else startedMatch
                val mapKey = snapshot.getString("mapa") ?: "pampa"
                val identity = GameSession(code = snapshot.getString("codigoSala").orEmpty(), mapKey = mapKey, mapName = mapKey, players = emptyList())
                android.util.Log.i("TRAIDORES_V3_QA", "V3 QA ENTER accepted")
                runOnUiThread {
                    val route = if (openLobby || intent.getBooleanExtra("via_lobby", false))
                        Intent(this, LobbyActivity::class.java)
                            .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_CREATE)
                            .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, "QA Android")
                            .putExtra(LobbyActivity.EXTRA_ROOM_CODE, identity.code)
                            .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, true)
                    else Intent(this, ServerGameplayActivity::class.java)
                        .putExtra(ServerGameplayActivity.EXTRA_MATCH_ID, match)
                        .putExtra(ServerGameplayActivity.EXTRA_CREATOR_ID, snapshot.getString("hostId"))
                    startActivity(route.putExtra(LobbyActivity.EXTRA_SESSION, identity)
                        .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, room))
                    finish()
                }
            } catch (error: Exception) {
                android.util.Log.e("TRAIDORES_V3_QA", "V3 QA FAIL", error)
                runOnUiThread { label.text = "V3 QA FAIL: ${error.message}" }
            }
        }.start() }
        if (intent.getBooleanExtra("interactive", false) && !intent.getBooleanExtra("open_lobby", false)) {
            label.text = "PRUEBA V3\n\nVas a jugar con cuatro participantes automáticos. Leé tu rol y usá los botones de cada fase."
            label.setPadding(32, 48, 32, 32)
            val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 96, 24, 32) }
            setContentView(screen)
            screen.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            screen.addView(Button(this).apply {
                text = "EMPEZAR PARTIDA"
                setOnClickListener { isEnabled = false; label.text = "Conectando partida…"; launch() }
            })
        } else launch()
    }
}
