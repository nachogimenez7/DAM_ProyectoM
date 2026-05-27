package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isSoundOn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind main buttons
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnRoles: Button = findViewById(R.id.btnRoles)
        val btnHelp: Button = findViewById(R.id.btnHelp)
        val btnOptions: Button = findViewById(R.id.btnOptions)

        // Bind bottom-bar action
        val btnSound: ImageButton = findViewById(R.id.btnSound)

        // Load sound preference
        val sharedPref = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        isSoundOn = sharedPref.getBoolean("sound_on", true)
        updateSoundButtonIcon(btnSound)

        // On clicks
        btnPlay.setOnClickListener {
            startActivity(Intent(this, JugarActivity::class.java))
        }

        btnRoles.setOnClickListener {
            startActivity(Intent(this, RolesActivity::class.java))
        }

        btnHelp.setOnClickListener {
            startActivity(Intent(this, AyudaActivity::class.java))
        }

        btnOptions.setOnClickListener {
            startActivity(Intent(this, OpcionesActivity::class.java))
        }

        btnSound.setOnClickListener {
            isSoundOn = !isSoundOn
            sharedPref.edit().putBoolean("sound_on", isSoundOn).apply()
            updateSoundButtonIcon(btnSound)
            val msg = if (isSoundOn) "Sonido Activado" else "Sonido Silenciado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSoundButtonIcon(btnSound: ImageButton) {
        if (isSoundOn) {
            btnSound.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        } else {
            btnSound.setImageResource(android.R.drawable.ic_lock_silent_mode)
        }
    }
}
