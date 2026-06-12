package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast

class LocalModeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnCreateLocal: Button = findViewById(R.id.btnCreateLocal)
        val btnJoinCode: Button = findViewById(R.id.btnJoinCode)

        btnBack.setOnClickListener { finish() }

        btnCreateLocal.setOnClickListener {
            Toast.makeText(this, "Sala local creada.", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, LobbyActivity::class.java)
                    .putExtra(
                        LobbyActivity.EXTRA_SESSION,
                        LocalGameFactory.createSession(humanName = savedPlayerName())
                    )
            )
        }

        btnJoinCode.setOnClickListener {
            Toast.makeText(this, "Codigo mock aceptado.", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, LobbyActivity::class.java)
                    .putExtra(
                        LobbyActivity.EXTRA_SESSION,
                        LocalGameFactory.createSession(
                            joinedByCode = true,
                            humanName = savedPlayerName()
                        )
                    )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }

    private fun savedPlayerName(): String {
        return getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
    }
}
