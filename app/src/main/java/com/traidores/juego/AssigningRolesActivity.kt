package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton

class AssigningRolesActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val openGameRunnable = Runnable {
        val session = readSession() ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        startActivity(
            Intent(this, GameplayMockActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(GameplayMockActivity.EXTRA_TEMA, GameplayTableUi.themeForMapKey(session.mapKey))
                .putExtra(GameplayMockActivity.EXTRA_ES_NOCHE, false)
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assigning_roles)
        val session = readSession() ?: LocalGameFactory.createSession()
        MusicManager.playGameIntro(this, session)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            handler.removeCallbacks(openGameRunnable)
            finish()
        }

        handler.postDelayed(openGameRunnable, 2600)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openGameRunnable)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession
    }
}
