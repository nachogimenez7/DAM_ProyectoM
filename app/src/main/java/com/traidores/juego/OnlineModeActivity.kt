package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast

class OnlineModeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnQuick: Button = findViewById(R.id.btnQuick)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        val btnCreate: Button = findViewById(R.id.btnCreate)

        btnBack.setOnClickListener { finish() }

        btnQuick.setOnClickListener {
            openOnlineLobby(
                mode = LobbyActivity.MODE_ONLINE_QUICK,
                playerCount = LocalGameFactory.MAX_PLAYERS,
                humanIsHost = false
            )
        }

        btnSearch.setOnClickListener {
            startActivity(Intent(this, LobbyBrowserActivity::class.java))
        }

        btnCreate.setOnClickListener {
            openOnlineLobby(
                mode = LobbyActivity.MODE_ONLINE_CREATE,
                playerCount = 1,
                humanIsHost = true
            )
        }
    }

    private fun openOnlineLobby(mode: String, playerCount: Int, humanIsHost: Boolean) {
        val playerName = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val session = LocalGameFactory.createOnlineLobby(
            humanName = playerName,
            playerCount = playerCount,
            humanIsHost = humanIsHost
        )
        Toast.makeText(this, "Entrando al lobby online.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, mode)
        )
    }
}

