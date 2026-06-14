package com.traidores.juego

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat

class OpcionesActivity : BaseActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var titleOptions: TextView
    private lateinit var subtitleOptions: TextView
    private lateinit var titleAudio: TextView
    private lateinit var descSound: TextView
    private lateinit var labelMusic: TextView
    private lateinit var labelVoices: TextView
    private lateinit var titleTextSize: TextView
    private lateinit var labelTextSize: TextView
    private lateinit var descTextSize: TextView
    private lateinit var textSizePreview: TextView
    private lateinit var titleLanguage: TextView
    private lateinit var labelLanguage: TextView
    private lateinit var descLanguage: TextView
    private lateinit var titleAccount: TextView
    private lateinit var accountStatus: TextView
    private lateinit var accountDescription: TextView
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat
    private lateinit var seekMusic: SeekBar
    private lateinit var seekVoices: SeekBar
    private lateinit var spinnerTextSize: Spinner
    private lateinit var spinnerLanguage: Spinner
    private lateinit var optionsScroll: ScrollView
    private lateinit var accountCard: LinearLayout
    private lateinit var btnResetOptions: Button

    private var currentLanguage = LANGUAGE_SPANISH
    private var updatingControls = false
    private var languageListenerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opciones)

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bindViews()
        configureControls()
        loadPreferences()
        updateOptionTexts()
        handleRequestedSection()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        titleOptions = findViewById(R.id.titleOptions)
        subtitleOptions = findViewById(R.id.subtitleOptions)
        titleAudio = findViewById(R.id.titleAudio)
        descSound = findViewById(R.id.descSound)
        labelMusic = findViewById(R.id.labelMusic)
        labelVoices = findViewById(R.id.labelVoices)
        titleTextSize = findViewById(R.id.titleTextSize)
        labelTextSize = findViewById(R.id.labelTextSize)
        descTextSize = findViewById(R.id.descTextSize)
        textSizePreview = findViewById(R.id.textSizePreview)
        titleLanguage = findViewById(R.id.titleLanguage)
        labelLanguage = findViewById(R.id.labelLanguage)
        descLanguage = findViewById(R.id.descLanguage)
        titleAccount = findViewById(R.id.titleAccount)
        accountStatus = findViewById(R.id.accountStatus)
        accountDescription = findViewById(R.id.accountDescription)
        switchSound = findViewById(R.id.switchSound)
        switchVibration = findViewById(R.id.switchVibration)
        seekMusic = findViewById(R.id.seekMusic)
        seekVoices = findViewById(R.id.seekVoices)
        spinnerTextSize = findViewById(R.id.spinnerTextSize)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        optionsScroll = findViewById(R.id.optionsScroll)
        accountCard = findViewById(R.id.accountCard)
        btnResetOptions = findViewById(R.id.btnResetOptions)
    }

    private fun configureControls() {
        spinnerLanguage.adapter = optionAdapter(languageOptions())
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (updatingControls) return
                val selected = languageOptions()[position.coerceIn(0, 1)]
                val changed = languageListenerReady && selected != currentLanguage
                currentLanguage = selected
                preferences.edit().putString(PREF_LANGUAGE, currentLanguage).apply()
                updateOptionTexts()
                configureTextSizeAdapter(spinnerTextSize.selectedItemPosition.coerceIn(0, 2))
                if (changed) {
                    Toast.makeText(
                        this@OpcionesActivity,
                        languageChangedMessage(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                languageListenerReady = true
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerTextSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (updatingControls) return
                val normalized = position.coerceIn(0, 2)
                preferences.edit().putInt(PREF_GAMEPLAY_TEXT_SIZE, normalized).apply()
                updateTextSizePreview(normalized)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        switchSound.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            preferences.edit().putBoolean(PREF_SOUND_ON, enabled).apply()
            updateAudioControlState(enabled)
            MusicManager.refresh(this)
            if (enabled) GameplayEffects.play(this, GameplayEffect.CONFIRM)
        }

        switchVibration.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            preferences.edit().putBoolean(PREF_VIBRATION_ON, enabled).apply()
            if (enabled) GameplayEffects.play(this, GameplayEffect.CONFIRM)
        }

        seekMusic.setOnSeekBarChangeListener(volumeListener(PREF_MUSIC_VOLUME))
        seekVoices.setOnSeekBarChangeListener(volumeListener(PREF_VOICE_VOLUME))
        btnResetOptions.setOnClickListener { resetOptions() }
    }

    private fun loadPreferences() {
        updatingControls = true
        currentLanguage = preferences.getString(PREF_LANGUAGE, LANGUAGE_SPANISH)
            ?.takeIf { it == LANGUAGE_ENGLISH }
            ?: LANGUAGE_SPANISH
        seekMusic.progress = preferences.getInt(PREF_MUSIC_VOLUME, DEFAULT_VOLUME)
        seekVoices.progress = preferences.getInt(PREF_VOICE_VOLUME, DEFAULT_VOLUME)
        switchSound.isChecked = preferences.getBoolean(PREF_SOUND_ON, true)
        switchVibration.isChecked = preferences.getBoolean(PREF_VIBRATION_ON, false)
        spinnerLanguage.setSelection(if (currentLanguage == LANGUAGE_ENGLISH) 1 else 0, false)
        configureTextSizeAdapter(
            preferences.getInt(PREF_GAMEPLAY_TEXT_SIZE, DEFAULT_TEXT_SIZE).coerceIn(0, 2)
        )
        updatingControls = false
        languageListenerReady = true
        updateAudioControlState(switchSound.isChecked)
        updateVolumeLabels()
        updateTextSizePreview(spinnerTextSize.selectedItemPosition.coerceIn(0, 2))
    }

    private fun configureTextSizeAdapter(selection: Int) {
        val previousState = updatingControls
        updatingControls = true
        spinnerTextSize.adapter = optionAdapter(textSizeOptions())
        spinnerTextSize.setSelection(selection.coerceIn(0, 2), false)
        updatingControls = previousState
        updateTextSizePreview(selection)
    }

    private fun optionAdapter(values: Array<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.item_option_spinner, values).also {
            it.setDropDownViewResource(R.layout.item_option_spinner)
        }
    }

    private fun volumeListener(key: String): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (updatingControls) return
                preferences.edit().putInt(key, progress).apply()
                updateVolumeLabels()
                MusicManager.refresh(this@OpcionesActivity)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun updateAudioControlState(enabled: Boolean) {
        seekMusic.isEnabled = enabled
        seekVoices.isEnabled = enabled
        val alpha = if (enabled) 1f else 0.42f
        seekMusic.alpha = alpha
        seekVoices.alpha = alpha
        labelMusic.alpha = alpha
        labelVoices.alpha = alpha
    }

    private fun updateVolumeLabels() {
        if (currentLanguage == LANGUAGE_ENGLISH) {
            labelMusic.text = "Music: ${seekMusic.progress}%"
            labelVoices.text = "Voices and effects: ${seekVoices.progress}%"
        } else {
            labelMusic.text = "Musica: ${seekMusic.progress}%"
            labelVoices.text = "Voces y efectos: ${seekVoices.progress}%"
        }
    }

    private fun updateTextSizePreview(position: Int) {
        val previewSize = when (position.coerceIn(0, 2)) {
            0 -> 15f
            2 -> 19f
            else -> 17f
        }
        textSizePreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, previewSize)
        textSizePreview.text = if (currentLanguage == LANGUAGE_ENGLISH) {
            "The town awakens. Listen, debate and decide."
        } else {
            "El pueblo despierta. Escucha, debate y decide."
        }
    }

    private fun updateOptionTexts() {
        if (currentLanguage == LANGUAGE_ENGLISH) {
            titleOptions.text = "OPTIONS"
            subtitleOptions.text = "Adjust the game so it is comfortable to read and hear."
            titleAudio.text = "SOUND AND FEEDBACK"
            switchSound.text = "Master sound"
            descSound.text = "Mute music, voices and effects without losing their levels."
            switchVibration.text = "Vibration on interaction"
            titleTextSize.text = "READABILITY AND ACCESSIBILITY"
            labelTextSize.text = "Text size"
            descTextSize.text = "Applied to messages, buttons and information during gameplay."
            titleLanguage.text = "LANGUAGE"
            labelLanguage.text = "Game language"
            descLanguage.text = "The full translation is still in development."
            titleAccount.text = "ACCOUNT AND PROFILE"
            accountStatus.text = "ONLINE ACCOUNT - COMING SOON"
            accountDescription.text =
                "When online play is implemented, you will log in here to keep statistics, achievements and customization."
            btnResetOptions.text = "RESET OPTIONS"
        } else {
            titleOptions.text = "OPCIONES"
            subtitleOptions.text = "Ajusta el juego para que sea comodo de leer y escuchar."
            titleAudio.text = "SONIDO Y RESPUESTA"
            switchSound.text = "Sonido general"
            descSound.text = "Apaga musica, voces y efectos sin perder sus niveles."
            switchVibration.text = "Vibracion al interactuar"
            titleTextSize.text = "LECTURA Y ACCESIBILIDAD"
            labelTextSize.text = "Tamano del texto"
            descTextSize.text = "Se aplica a mensajes, botones y datos durante la partida."
            titleLanguage.text = "IDIOMA"
            labelLanguage.text = "Idioma del juego"
            descLanguage.text = "La traduccion completa sigue en desarrollo."
            titleAccount.text = "CUENTA Y PERFIL"
            accountStatus.text = "CUENTA ONLINE - PROXIMAMENTE"
            accountDescription.text =
                "Cuando se implemente el online, desde aqui podras iniciar sesion y conservar estadisticas, logros y personalizacion."
            btnResetOptions.text = "RESTABLECER OPCIONES"
        }
        updateVolumeLabels()
        updateTextSizePreview(spinnerTextSize.selectedItemPosition.coerceIn(0, 2))
    }

    private fun resetOptions() {
        preferences.edit()
            .putBoolean(PREF_SOUND_ON, true)
            .putInt(PREF_MUSIC_VOLUME, DEFAULT_VOLUME)
            .putInt(PREF_VOICE_VOLUME, DEFAULT_VOLUME)
            .putBoolean(PREF_VIBRATION_ON, false)
            .putInt(PREF_GAMEPLAY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
            .putString(PREF_LANGUAGE, LANGUAGE_SPANISH)
            .apply()

        updatingControls = true
        currentLanguage = LANGUAGE_SPANISH
        seekMusic.progress = DEFAULT_VOLUME
        seekVoices.progress = DEFAULT_VOLUME
        switchSound.isChecked = true
        switchVibration.isChecked = false
        spinnerLanguage.setSelection(0, false)
        configureTextSizeAdapter(DEFAULT_TEXT_SIZE)
        updatingControls = false
        updateAudioControlState(true)
        updateOptionTexts()
        MusicManager.refresh(this)
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        Toast.makeText(this, "Opciones restablecidas.", Toast.LENGTH_SHORT).show()
    }

    private fun handleRequestedSection() {
        when {
            intent.getBooleanExtra("focus_language", false) -> {
                spinnerLanguage.requestFocus()
                Toast.makeText(this, focusLanguageMessage(), Toast.LENGTH_SHORT).show()
            }
            intent.getBooleanExtra("focus_account", false) -> {
                accountCard.post {
                    optionsScroll.smoothScrollTo(0, accountCard.top)
                    accountCard.requestFocus()
                }
                Toast.makeText(this, accountPendingMessage(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun languageOptions(): Array<String> =
        arrayOf(LANGUAGE_SPANISH, LANGUAGE_ENGLISH)

    private fun textSizeOptions(): Array<String> {
        return if (currentLanguage == LANGUAGE_ENGLISH) {
            arrayOf("Compact", "Normal", "Large")
        } else {
            arrayOf("Compacto", "Normal", "Grande")
        }
    }

    private fun languageChangedMessage(): String {
        return if (currentLanguage == LANGUAGE_ENGLISH) {
            "Language changed to English."
        } else {
            "Idioma cambiado a espanol."
        }
    }

    private fun focusLanguageMessage(): String {
        return if (currentLanguage == LANGUAGE_ENGLISH) {
            "Choose your preferred language."
        } else {
            "Selecciona tu idioma preferido."
        }
    }

    private fun accountPendingMessage(): String {
        return if (currentLanguage == LANGUAGE_ENGLISH) {
            "Online accounts are not available yet."
        } else {
            "Las cuentas online todavia no estan disponibles."
        }
    }

    companion object {
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val PREF_SOUND_ON = "sound_on"
        private const val PREF_MUSIC_VOLUME = "music_volume"
        private const val PREF_VOICE_VOLUME = "voice_volume"
        private const val PREF_VIBRATION_ON = "vibration_on"
        private const val PREF_GAMEPLAY_TEXT_SIZE = "gameplay_text_size"
        private const val PREF_LANGUAGE = "language"
        private const val DEFAULT_VOLUME = 80
        private const val DEFAULT_TEXT_SIZE = 1
        private const val LANGUAGE_SPANISH = "Espanol (ES)"
        private const val LANGUAGE_ENGLISH = "English (EN)"

        const val PREF_PLAYER_NAME = "player_name"
    }
}
