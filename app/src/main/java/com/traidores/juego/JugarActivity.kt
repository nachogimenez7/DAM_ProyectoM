package com.traidores.juego

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class JugarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jugar)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val btnLocal: Button = findViewById(R.id.btnLocal)
        val btnOnline: Button = findViewById(R.id.btnOnline)

        btnLocal.setOnClickListener {
            Toast.makeText(this, "Modo Local: Iniciando partida rápida...", Toast.LENGTH_LONG).show()
        }

        btnOnline.setOnClickListener {
            Toast.makeText(this, "Modo Online estará disponible en la próxima versión beta.", Toast.LENGTH_LONG).show()
        }
    }
}
