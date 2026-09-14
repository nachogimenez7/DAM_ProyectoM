package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton

class JugarActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jugar)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val cardLocal: View = findViewById(R.id.cardLocal)
        val cardOnline: View = findViewById(R.id.cardOnline)
        cardLocal.contentDescription = "Jugar contra la IA"
        cardOnline.contentDescription = "Jugar una partida en linea"

        cardLocal.setOnClickListener {
            startActivity(Intent(this, LocalModeActivity::class.java))
        }

        cardOnline.setOnClickListener {
            startActivity(Intent(this, OnlineModeActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }
}
