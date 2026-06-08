package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout

class JugarActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jugar)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val cardLocal: LinearLayout = findViewById(R.id.cardLocal)
        val cardOnline: LinearLayout = findViewById(R.id.cardOnline)

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
