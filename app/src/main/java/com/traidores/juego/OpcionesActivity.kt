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
import androidx.appcompat.widget.SwitchCompat

class OpcionesActivity : BaseActivity() {

    private lateinit var labelMusic: TextView
    private lateinit var labelVoices: TextView
    private lateinit var titleOptions: TextView
    private lateinit var titleAudio: TextView
    private lateinit var titleLanguage: TextView
    private lateinit var titleTextSize: TextView
    private lateinit var labelTextSize: TextView
    private lateinit var labelLanguage: TextView
    private lateinit var titleAccount: TextView
    private lateinit var titleLogin: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var switchVibration: SwitchCompat
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
        titleTextSize = findViewById(R.id.titleTextSize)
        labelTextSize = findViewById(R.id.labelTextSize)
        labelLanguage = findViewById(R.id.labelLanguage)
        titleAccount = findViewById(R.id.titleAccount)
        titleLogin = findViewById(R.id.titleLogin)
        labelMusic = findViewById(R.id.labelMusic)
        labelVoices = findViewById(R.id.labelVoices)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etUsername.setText(sharedPref.getString(PREF_PLAYER_NAME, "").orEmpty())
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        switchVibration = findViewById(R.id.switchVibration)

        val seekMusic: SeekBar = findViewById(R.id.seekMusic)
        val seekVoices: SeekBar = findViewById(R.id.seekVoices)
        seekMusic.progress = sharedPref.getInt("music_volume", 80)
        seekVoices.progress = sharedPref.getInt("voice_volume", 80)
        switchVibration.isChecked = sharedPref.getBoolean("vibration_on", false)
        switchVibration.setOnCheckedChangeListener { _, enabled ->
            sharedPref.edit().putBoolean("vibration_on", enabled).apply()
            if (enabled) GameplayEffects.play(this, GameplayEffect.CONFIRM)
        }
        updateVolumeLabels(seekMusic.progress, seekVoices.progress)

        seekMusic.setOnSeekBarChangeListener(volumeListener("music_volume", seekMusic, seekVoices, sharedPref))
        seekVoices.setOnSeekBarChangeListener(volumeListener("voice_volume", seekMusic, seekVoices, sharedPref))

        val spinnerTextSize: Spinner = findViewById(R.id.spinnerTextSize)
        val textSizes = arrayOf("Compacta", "Normal", "Grande")
        spinnerTextSize.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            textSizes
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTextSize.setSelection(sharedPref.getInt("gameplay_text_size", 1).coerceIn(0, 2))
        spinnerTextSize.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    sharedPref.edit().putInt("gameplay_text_size", position).apply()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

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
                sharedPref.edit().putString(PREF_PLAYER_NAME, username).apply()
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
                sharedPref.edit().putString(PREF_PLAYER_NAME, username).apply()
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

    override fun onPause() {
        if (::etUsername.isInitialized) {
            val username = etUsername.text.toString().trim()
            if (username.isNotBlank()) {
                getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_PLAYER_NAME, username)
                    .apply()
            }
        }
        super.onPause()
    }

    private fun updateVolumeLabels(music: Int, voices: Int) {
        if (currentLanguage == "English (EN)") {
            labelMusic.text = "Music: $music%"
            labelVoices.text = "Voices: $voices%"
            switchVibration.text = "Vibration"
        } else {
            labelMusic.text = "Musica: $music%"
            labelVoices.text = "Voces: $voices%"
            switchVibration.text = "Vibracion"
        }
    }

    private fun updateOptionTexts() {
        if (currentLanguage == "English (EN)") {
            titleOptions.text = "OPTIONS"
            titleAudio.text = "SOUND AND AUDIO"
            titleLanguage.text = "GAME LANGUAGE"
            titleTextSize.text = "TEXT SIZE"
            labelTextSize.text = "Gameplay text"
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
            titleTextSize.text = "TAMAÑO DEL TEXTO"
            labelTextSize.text = "Texto del gameplay"
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

    companion object {
        const val PREF_PLAYER_NAME = "player_name"
    }
}

