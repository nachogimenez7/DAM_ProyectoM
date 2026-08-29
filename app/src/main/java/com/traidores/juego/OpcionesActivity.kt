package com.traidores.juego

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.traidores.juego.GameToast as Toast
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
    private lateinit var titleNotifications: TextView
    private lateinit var descNotifications: TextView
    private lateinit var titleVisualEffects: TextView
    private lateinit var descReducedVisualEffects: TextView
    private lateinit var switchMusic: SwitchCompat
    private lateinit var switchEffects: SwitchCompat
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchReducedVisualEffects: SwitchCompat
    private lateinit var seekMusic: SeekBar
    private lateinit var seekVoices: SeekBar
    private lateinit var spinnerTextSize: Spinner
    private lateinit var spinnerLanguage: Spinner
    private lateinit var btnAbout: Button
    private lateinit var btnResetOptions: Button

    private var currentLanguage = LANGUAGE_SPANISH
    private var updatingControls = false
    private var languageListenerReady = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatingControls = true
        switchNotifications.isChecked = granted
        updatingControls = false
        NotificationPreferences.setEnabled(this, granted)
        Toast.makeText(
            this,
            getString(
                if (granted) R.string.notification_enabled
                else R.string.notification_permission_denied
            ),
            if (granted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
    }

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

    override fun onResume() {
        super.onResume()
        if (!::switchNotifications.isInitialized) return
        val available = NotificationPreferences.canPostNotifications(this)
        val enabled = NotificationPreferences.isEnabled(this) && available
        updatingControls = true
        switchNotifications.isChecked = enabled
        updatingControls = false
        if (!available && NotificationPreferences.isEnabled(this)) {
            NotificationPreferences.setEnabled(this, false)
        }
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
        titleNotifications = findViewById(R.id.titleNotifications)
        descNotifications = findViewById(R.id.descNotifications)
        titleVisualEffects = findViewById(R.id.titleVisualEffects)
        descReducedVisualEffects = findViewById(R.id.descReducedVisualEffects)
        switchMusic = findViewById(R.id.switchMusic)
        switchEffects = findViewById(R.id.switchEffects)
        switchVibration = findViewById(R.id.switchVibration)
        switchNotifications = findViewById(R.id.switchNotifications)
        switchReducedVisualEffects = findViewById(R.id.switchReducedVisualEffects)
        seekMusic = findViewById(R.id.seekMusic)
        seekVoices = findViewById(R.id.seekVoices)
        spinnerTextSize = findViewById(R.id.spinnerTextSize)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        btnAbout = findViewById(R.id.btnAbout)
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

        switchMusic.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            preferences.edit().putBoolean(AudioPreferences.MUSIC_ENABLED, enabled).apply()
            updateAudioControlState()
            MusicManager.refresh(this)
        }

        switchEffects.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            preferences.edit().putBoolean(AudioPreferences.EFFECTS_ENABLED, enabled).apply()
            updateAudioControlState()
            if (enabled) GameplayEffects.play(this, GameplayEffect.CONFIRM)
        }

        switchVibration.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            preferences.edit().putBoolean(PREF_VIBRATION_ON, enabled).apply()
            if (enabled) GameplayEffects.play(this, GameplayEffect.CONFIRM)
        }

        switchReducedVisualEffects.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            VisualEffectsPreferences.setReduced(this, enabled)
        }

        switchNotifications.setOnCheckedChangeListener { _, enabled ->
            if (updatingControls) return@setOnCheckedChangeListener
            NotificationPreferences.markInvitationSeen(this)
            if (enabled) {
                requestNotificationPermissionOrEnable()
            } else {
                NotificationPreferences.setEnabled(this, false)
                Toast.makeText(
                    this,
                    getString(R.string.notification_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        seekMusic.setOnSeekBarChangeListener(volumeListener(PREF_MUSIC_VOLUME))
        seekVoices.setOnSeekBarChangeListener(volumeListener(PREF_VOICE_VOLUME))
        btnAbout.setOnClickListener {
            startActivity(Intent(this, AcercaDeActivity::class.java))
        }
        btnResetOptions.setOnClickListener { resetOptions() }
    }

    private fun loadPreferences() {
        updatingControls = true
        // El selector queda oculto hasta que la traduccion sea completa. Si una instalacion
        // anterior habia guardado ingles, se normaliza para no mezclar textos parciales.
        currentLanguage = LANGUAGE_SPANISH
        preferences.edit().putString(PREF_LANGUAGE, LANGUAGE_SPANISH).apply()
        seekMusic.progress = preferences.getInt(PREF_MUSIC_VOLUME, DEFAULT_VOLUME)
        seekVoices.progress = preferences.getInt(PREF_VOICE_VOLUME, DEFAULT_VOLUME)
        switchMusic.isChecked = AudioPreferences.isMusicEnabled(preferences)
        switchEffects.isChecked = AudioPreferences.areEffectsEnabled(preferences)
        switchVibration.isChecked = preferences.getBoolean(PREF_VIBRATION_ON, false)
        switchNotifications.isChecked = NotificationPreferences.isEnabled(this) &&
            NotificationPreferences.canPostNotifications(this)
        switchReducedVisualEffects.isChecked = VisualEffectsPreferences.isReduced(this)
        spinnerLanguage.setSelection(if (currentLanguage == LANGUAGE_ENGLISH) 1 else 0, false)
        configureTextSizeAdapter(
            preferences.getInt(PREF_GAMEPLAY_TEXT_SIZE, DEFAULT_TEXT_SIZE).coerceIn(0, 2)
        )
        updatingControls = false
        languageListenerReady = true
        updateAudioControlState()
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

    private fun updateAudioControlState() {
        seekMusic.isEnabled = switchMusic.isChecked
        seekVoices.isEnabled = switchEffects.isChecked
        val musicAlpha = if (switchMusic.isChecked) 1f else 0.42f
        val effectsAlpha = if (switchEffects.isChecked) 1f else 0.42f
        seekMusic.alpha = musicAlpha
        labelMusic.alpha = musicAlpha
        seekVoices.alpha = effectsAlpha
        labelVoices.alpha = effectsAlpha
    }

    private fun updateVolumeLabels() {
        if (currentLanguage == LANGUAGE_ENGLISH) {
            labelMusic.text = "Music: ${seekMusic.progress}%"
            labelVoices.text = "Effects: ${seekVoices.progress}%"
        } else {
            labelMusic.text = "Música: ${seekMusic.progress}%"
            labelVoices.text = "Efectos: ${seekVoices.progress}%"
        }
    }

    private fun updateTextSizePreview(position: Int) {
        val previewSize = when (position.coerceIn(0, 2)) {
            0 -> 14f
            2 -> 18f
            else -> 16f
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
            switchMusic.text = "Music"
            switchEffects.text = "Sound effects"
            descSound.text = "Control music and game effects independently."
            switchVibration.text = "Vibration on interaction"
            titleTextSize.text = "READABILITY AND ACCESSIBILITY"
            labelTextSize.text = "Text size"
            descTextSize.text = "Applied to messages, buttons and information during gameplay."
            titleLanguage.text = "LANGUAGE"
            labelLanguage.text = "Game language"
            descLanguage.text = "The full translation is still in development."
            titleNotifications.text = "NEWS"
            switchNotifications.text = "Notifications"
            descNotifications.text = "Receive beta improvements, tests and important updates."
            titleVisualEffects.text = "VISUAL EFFECTS"
            switchReducedVisualEffects.text = "Reduce animations"
            descReducedVisualEffects.text =
                "Removes particles and simplifies transitions for a calmer experience."
            btnResetOptions.text = "RESET OPTIONS"
        } else {
            titleOptions.text = "OPCIONES"
            subtitleOptions.text = "Ajusta el juego para que sea cómodo de leer y escuchar."
            titleAudio.text = "SONIDO Y RESPUESTA"
            switchMusic.text = "Música"
            switchEffects.text = "Efectos de sonido"
            descSound.text = "Controla por separado la música y los efectos del juego."
            switchVibration.text = "Vibración al interactuar"
            titleTextSize.text = "LECTURA Y ACCESIBILIDAD"
            labelTextSize.text = "Tamaño del texto"
            descTextSize.text = "Se aplica a mensajes, botones y datos durante la partida."
            titleLanguage.text = "IDIOMA"
            labelLanguage.text = "Idioma del juego"
            descLanguage.text = "La traducción completa sigue en desarrollo."
            titleNotifications.text = "NOVEDADES"
            switchNotifications.text = "Notificaciones"
            descNotifications.text = "Recibí mejoras, pruebas y avisos importantes de la beta."
            titleVisualEffects.text = "EFECTOS VISUALES"
            switchReducedVisualEffects.text = "Reducir animaciones"
            descReducedVisualEffects.text =
                "Quita partículas y simplifica transiciones para una experiencia más tranquila."
            btnResetOptions.text = "RESTABLECER OPCIONES"
        }
        updateVolumeLabels()
        updateTextSizePreview(spinnerTextSize.selectedItemPosition.coerceIn(0, 2))
    }

    private fun resetOptions() {
        preferences.edit()
            .putBoolean(AudioPreferences.MUSIC_ENABLED, true)
            .putBoolean(AudioPreferences.EFFECTS_ENABLED, true)
            .putInt(PREF_MUSIC_VOLUME, DEFAULT_VOLUME)
            .putInt(PREF_VOICE_VOLUME, DEFAULT_VOLUME)
            .putBoolean(PREF_VIBRATION_ON, false)
            .putBoolean(VisualEffectsPreferences.KEY_REDUCED_EFFECTS, false)
            .putInt(PREF_GAMEPLAY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
            .putString(PREF_LANGUAGE, LANGUAGE_SPANISH)
            .apply()

        updatingControls = true
        currentLanguage = LANGUAGE_SPANISH
        seekMusic.progress = DEFAULT_VOLUME
        seekVoices.progress = DEFAULT_VOLUME
        switchMusic.isChecked = true
        switchEffects.isChecked = true
        switchVibration.isChecked = false
        switchReducedVisualEffects.isChecked = false
        switchNotifications.isChecked = false
        spinnerLanguage.setSelection(0, false)
        configureTextSizeAdapter(DEFAULT_TEXT_SIZE)
        updatingControls = false
        updateAudioControlState()
        updateOptionTexts()
        MusicManager.refresh(this)
        NotificationPreferences.setEnabled(this, false)
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        Toast.makeText(this, "Opciones restablecidas.", Toast.LENGTH_SHORT).show()
    }

    private fun handleRequestedSection() {
        when {
            intent.getBooleanExtra("focus_language", false) -> {
                spinnerLanguage.requestFocus()
                Toast.makeText(this, focusLanguageMessage(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermissionOrEnable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationPreferences.setEnabled(this, true)
            Toast.makeText(
                this,
                getString(R.string.notification_enabled),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            "Idioma cambiado a español."
        }
    }

    private fun focusLanguageMessage(): String {
        return if (currentLanguage == LANGUAGE_ENGLISH) {
            "Choose your preferred language."
        } else {
            "Selecciona tu idioma preferido."
        }
    }

    companion object {
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val PREF_MUSIC_VOLUME = "music_volume"
        private const val PREF_VOICE_VOLUME = "voice_volume"
        private const val PREF_VIBRATION_ON = "vibration_on"
        private const val PREF_GAMEPLAY_TEXT_SIZE = "gameplay_text_size"
        private const val PREF_LANGUAGE = "language"
        private const val DEFAULT_VOLUME = 80
        private const val DEFAULT_TEXT_SIZE = 1
        private const val LANGUAGE_SPANISH = "Español (ES)"
        private const val LANGUAGE_ENGLISH = "English (EN)"

        const val PREF_PLAYER_NAME = "player_name"
        const val PREF_LAST_SELECTED_MAP = "last_selected_map"
    }
}
