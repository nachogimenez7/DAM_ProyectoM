package com.traidores.juego

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
            Toast.makeText(this, "Abriendo mock de partida local.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GameplayMockActivity::class.java))
        }

        btnJoinCode.setOnClickListener {
            Toast.makeText(this, "Simulando union por codigo.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GameplayMockActivity::class.java))
        }
    }
}

