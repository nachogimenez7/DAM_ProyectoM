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
        val btnQuick: Button = findViewById(R.id.btnQuick)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        val btnCreate: Button = findViewById(R.id.btnCreate)

        btnLocal.setOnClickListener {
            Toast.makeText(this, "Ingresar codigo estara disponible pronto.", Toast.LENGTH_LONG).show()
        }

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
