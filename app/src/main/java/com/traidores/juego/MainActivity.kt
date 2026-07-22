package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import com.traidores.juego.GameToast as Toast

class MainActivity : BaseActivity() {

    private lateinit var btnMusic: ImageButton
    private var isMusicOn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind main buttons
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnRoles: Button = findViewById(R.id.btnRoles)
        val btnHelp: Button = findViewById(R.id.btnHelp)
        val btnOptions: Button = findViewById(R.id.btnOptions)

        // Bind bottom-bar action
        btnMusic = findViewById(R.id.btnMusic)
        val btnProfile: ImageButton = findViewById(R.id.btnProfile)

        val sharedPref = AudioPreferences.preferences(this)
        loadAudioState(sharedPref)

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

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnMusic.setOnClickListener {
            isMusicOn = !isMusicOn
            sharedPref.edit().putBoolean(AudioPreferences.MUSIC_ENABLED, isMusicOn).apply()
            updateAudioButtonIcon()
            MusicManager.refresh(this)
            val msg = if (isMusicOn) "Musica activada" else "Musica silenciada"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

    }

    override fun onResume() {
        super.onResume()
        loadAudioState(AudioPreferences.preferences(this))
        MusicManager.playMenuMusic(this)
    }

    private fun loadAudioState(sharedPref: android.content.SharedPreferences) {
        isMusicOn = AudioPreferences.isMusicEnabled(sharedPref)
        if (::btnMusic.isInitialized) updateAudioButtonIcon()
    }

    private fun updateAudioButtonIcon() {
        btnMusic.setImageResource(if (isMusicOn) R.drawable.ic_music_note else R.drawable.ic_music_off)
        btnMusic.alpha = if (isMusicOn) 1f else 0.55f
        btnMusic.contentDescription = if (isMusicOn) "Silenciar musica" else "Activar musica"
    }

}
