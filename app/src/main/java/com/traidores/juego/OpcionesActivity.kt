package com.traidores.juego

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class OpcionesActivity : BaseActivity() {

    private lateinit var labelMusic: TextView
    private lateinit var labelVoices: TextView
    private lateinit var titleOptions: TextView
    private lateinit var titleAudio: TextView
    private lateinit var titleLanguage: TextView
    private lateinit var labelLanguage: TextView
    private lateinit var titleAccount: TextView
    private lateinit var titleLogin: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private var currentLanguage = "Espanol (ES)"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opciones)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val sharedPref = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)

        titleOptions = findViewById(R.id.titleOptions)
        titleAudio = findViewById(R.id.titleAudio)
        titleLanguage = findViewById(R.id.titleLanguage)
        labelLanguage = findViewById(R.id.labelLanguage)
        titleAccount = findViewById(R.id.titleAccount)
        titleLogin = findViewById(R.id.titleLogin)
        labelMusic = findViewById(R.id.labelMusic)
        labelVoices = findViewById(R.id.labelVoices)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)

        val seekMusic: SeekBar = findViewById(R.id.seekMusic)
        val seekVoices: SeekBar = findViewById(R.id.seekVoices)
        seekMusic.progress = sharedPref.getInt("music_volume", 80)
        seekVoices.progress = sharedPref.getInt("voice_volume", 80)
        updateVolumeLabels(seekMusic.progress, seekVoices.progress)

        seekMusic.setOnSeekBarChangeListener(volumeListener("music_volume", seekMusic, seekVoices, sharedPref))
        seekVoices.setOnSeekBarChangeListener(volumeListener("voice_volume", seekMusic, seekVoices, sharedPref))

        val spinnerLanguage: Spinner = findViewById(R.id.spinnerLanguage)
        val languages = arrayOf("Espanol (ES)", "English (EN)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        val selectedLang = sharedPref.getString("language", "Espanol (ES)") ?: "Espanol (ES)"
        val defaultPos = if (selectedLang.contains("English", ignoreCase = true)) 1 else 0
        currentLanguage = languages[defaultPos]
        spinnerLanguage.setSelection(defaultPos)
        updateOptionTexts()

        var isInit = true
        spinnerLanguage.post {
            spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    currentLanguage = languages[position]
                    sharedPref.edit().putString("language", currentLanguage).apply()
                    updateOptionTexts()
                    if (!isInit) {
                        Toast.makeText(this@OpcionesActivity, languageChangedMessage(), Toast.LENGTH_SHORT).show()
                    }
                    isInit = false
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, emptyLoginMessage(), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, welcomeMessage(username), Toast.LENGTH_LONG).show()
                finish()
            }
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, emptyRegisterMessage(), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, registerMessage(username), Toast.LENGTH_LONG).show()
            }
        }

        if (intent.getBooleanExtra("focus_language", false)) {
            spinnerLanguage.requestFocus()
            Toast.makeText(this, focusLanguageMessage(), Toast.LENGTH_SHORT).show()
        } else if (intent.getBooleanExtra("focus_account", false)) {
            etUsername.requestFocus()
            Toast.makeText(this, focusAccountMessage(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun volumeListener(
        key: String,
        seekMusic: SeekBar,
        seekVoices: SeekBar,
        sharedPref: android.content.SharedPreferences
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPref.edit().putInt(key, progress).putBoolean("sound_on", seekMusic.progress > 0 || seekVoices.progress > 0).apply()
                updateVolumeLabels(seekMusic.progress, seekVoices.progress)
                MusicManager.refresh(this@OpcionesActivity)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun updateVolumeLabels(music: Int, voices: Int) {
        if (currentLanguage == "English (EN)") {
            labelMusic.text = "Music: $music%"
            labelVoices.text = "Voices: $voices%"
        } else {
            labelMusic.text = "Musica: $music%"
            labelVoices.text = "Voces: $voices%"
        }
    }

    private fun updateOptionTexts() {
        if (currentLanguage == "English (EN)") {
            titleOptions.text = "OPTIONS"
            titleAudio.text = "SOUND AND AUDIO"
            titleLanguage.text = "GAME LANGUAGE"
            labelLanguage.text = "Select language"
            titleAccount.text = "PLAYER ACCOUNT"
            titleLogin.text = "LOGIN / REGISTER"
            etUsername.hint = "Username"
            etPassword.hint = "Password"
            btnLogin.text = "LOGIN"
            btnRegister.text = "REGISTER"
        } else {
            titleOptions.text = "OPCIONES"
            titleAudio.text = "SONIDO Y AUDIO"
            titleLanguage.text = "IDIOMA DEL JUEGO"
            labelLanguage.text = "Seleccionar idioma"
            titleAccount.text = "CUENTA DEL JUGADOR"
            titleLogin.text = "INICIAR SESION / REGISTRARSE"
            etUsername.hint = "Nombre de usuario"
            etPassword.hint = "Contrasena"
            btnLogin.text = "INGRESAR"
            btnRegister.text = "REGISTRARSE"
        }

        val seekMusic: SeekBar = findViewById(R.id.seekMusic)
        val seekVoices: SeekBar = findViewById(R.id.seekVoices)
        updateVolumeLabels(seekMusic.progress, seekVoices.progress)
    }

    private fun languageChangedMessage(): String {
        return if (currentLanguage == "English (EN)") "Language changed to: English" else "Idioma cambiado a: Espanol"
    }

    private fun emptyLoginMessage(): String {
        return if (currentLanguage == "English (EN)") "Please enter username and password." else "Por favor, ingresa usuario y contrasena."
    }

    private fun emptyRegisterMessage(): String {
        return if (currentLanguage == "English (EN)") {
            "Please enter username and password to register."
        } else {
            "Por favor, ingresa usuario y contrasena para registrarte."
        }
    }

    private fun welcomeMessage(username: String): String {
        return if (currentLanguage == "English (EN)") "Welcome, $username!" else "Bienvenido/a, $username!"
    }

    private fun registerMessage(username: String): String {
        return if (currentLanguage == "English (EN)") {
            "Registration complete for $username. You can now log in."
        } else {
            "Registro exitoso para $username. Ya podes iniciar sesion."
        }
    }

    private fun focusLanguageMessage(): String {
        return if (currentLanguage == "English (EN)") "Choose your preferred language" else "Selecciona tu idioma preferido"
    }

    private fun focusAccountMessage(): String {
        return if (currentLanguage == "English (EN)") "Log in or register to play online" else "Inicia sesion o registrate para jugar en linea"
    }
}

