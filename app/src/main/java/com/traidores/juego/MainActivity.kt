package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isSoundOn = true
    private var menuMusic: MediaPlayer? = null

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
        setupMenuMusic(sharedPref)

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
            updateMenuMusicState(sharedPref)
            val msg = if (isSoundOn) "Sonido Activado" else "Sonido Silenciado"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        isSoundOn = sharedPref.getBoolean("sound_on", true)
        updateMenuMusicState(sharedPref)
    }

    override fun onPause() {
        super.onPause()
        menuMusic?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        menuMusic?.release()
        menuMusic = null
    }

    private fun updateSoundButtonIcon(btnSound: ImageButton) {
        if (isSoundOn) {
            btnSound.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        } else {
            btnSound.setImageResource(android.R.drawable.ic_lock_silent_mode)
        }
    }

    private fun setupMenuMusic(sharedPref: android.content.SharedPreferences) {
        menuMusic = MediaPlayer.create(this, R.raw.menu_music).apply {
            isLooping = true
        }
        updateMenuMusicState(sharedPref)
    }

    private fun updateMenuMusicState(sharedPref: android.content.SharedPreferences) {
        val volume = sharedPref.getInt("music_volume", 80) / 100f
        menuMusic?.setVolume(volume, volume)

        if (isSoundOn && volume > 0f) {
            if (menuMusic?.isPlaying == false) {
                menuMusic?.start()
            }
        } else {
            menuMusic?.pause()
        }
    }
}
