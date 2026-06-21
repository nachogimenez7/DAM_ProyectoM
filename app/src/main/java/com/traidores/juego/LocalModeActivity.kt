package com.traidores.juego

import android.content.Context
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

    private fun savedPlayerName(): String {
        return getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
    }

    private fun startVsAi(difficulty: BotDifficulty) {
        val preferences = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(
                    LobbyActivity.EXTRA_SESSION,
                    LocalGameFactory.createSession(humanName = savedPlayerName())
                        .copy(
                            botDifficulty = difficulty,
                            botSpicyLanguage = preferences.getBoolean(
                                OpcionesActivity.PREF_BOT_SPICY_LANGUAGE,
                                true
                            )
                        )
                )
        )
    }
}
