package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton

class LocalModeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnNormalAi: Button = findViewById(R.id.btnNormalAi)
        val btnHardAi: Button = findViewById(R.id.btnHardAi)

        btnBack.setOnClickListener { finish() }

        btnNormalAi.setOnClickListener { startVsAi(BotDifficulty.NORMAL) }
        btnHardAi.setOnClickListener { startVsAi(BotDifficulty.HARD) }
    }

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }

    // Tambien contra la IA: el perfil muestra un solo nombre, y para un invitado ese nombre es
    // su alias. Leer la preferencia directo dejaria dos nombres distintos para la misma persona.
    private fun savedPlayerName(): String = PlayerPublicIdentity.profileName(this)

    private fun startVsAi(difficulty: BotDifficulty) {
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(
                    LobbyActivity.EXTRA_SESSION,
                    LocalGameFactory.createSession(humanName = savedPlayerName())
                        .copy(
                            botDifficulty = difficulty,
                            quickTestMode = false
                        )
                )
        )
    }
}
