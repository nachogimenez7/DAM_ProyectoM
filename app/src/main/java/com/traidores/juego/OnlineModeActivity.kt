package com.traidores.juego

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
            Toast.makeText(this, "Abriendo mock de partida rapida.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GameplayMockActivity::class.java))
        }

        btnSearch.setOnClickListener {
            Toast.makeText(this, "Simulando busqueda de lobby.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GameplayMockActivity::class.java))
        }

        btnCreate.setOnClickListener {
            Toast.makeText(this, "Abriendo mock de lobby creado.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, GameplayMockActivity::class.java))
        }
    }
}

