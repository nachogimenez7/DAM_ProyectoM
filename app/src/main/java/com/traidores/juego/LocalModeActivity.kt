package com.traidores.juego

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LocalModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnCreateLocal: Button = findViewById(R.id.btnCreateLocal)
        val btnJoinCode: Button = findViewById(R.id.btnJoinCode)

        btnBack.setOnClickListener { finish() }

        btnCreateLocal.setOnClickListener {
            Toast.makeText(this, "Creando sala local...", Toast.LENGTH_LONG).show()
        }

        btnJoinCode.setOnClickListener {
            Toast.makeText(this, "Ingresar codigo estara disponible pronto.", Toast.LENGTH_LONG).show()
        }
    }
}
