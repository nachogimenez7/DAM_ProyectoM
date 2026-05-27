package com.traidores.juego

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OnlineModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnQuick: Button = findViewById(R.id.btnQuick)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        val btnCreate: Button = findViewById(R.id.btnCreate)

        btnBack.setOnClickListener { finish() }

        btnQuick.setOnClickListener {
            Toast.makeText(this, "Buscando partida rapida...", Toast.LENGTH_LONG).show()
        }

        btnSearch.setOnClickListener {
            Toast.makeText(this, "Buscar partida estara disponible pronto.", Toast.LENGTH_LONG).show()
        }

        btnCreate.setOnClickListener {
            Toast.makeText(this, "Crear partida estara disponible pronto.", Toast.LENGTH_LONG).show()
        }
    }
}
