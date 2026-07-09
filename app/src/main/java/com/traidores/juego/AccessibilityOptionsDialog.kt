package com.traidores.juego

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.TextViewCompat

object AccessibilityOptionsDialog {
    private const val PREF_VIBRATION_ON = "vibration_on"
    private const val PREF_GAMEPLAY_TEXT_SIZE = "gameplay_text_size"
    private const val DEFAULT_GAMEPLAY_TEXT_SIZE = 1

    fun show(
        activity: AppCompatActivity,
        onTextSizeChanged: (Int) -> Unit = {}
    ): AlertDialog {
        val preferences = AudioPreferences.preferences(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(12), activity.dp(18), activity.dp(8))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }
        content.addView(TextView(activity).apply {
            text = "ACCESIBILIDAD"
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.accent_gold))
            textSize = 20f
            setPadding(0, 0, 0, activity.dp(8))
        })

        val musicSwitch = optionSwitch(
            activity,
            "Música",
            AudioPreferences.isMusicEnabled(preferences)
        )
        val musicLabel = optionLabel(activity)
        val musicSeek = volumeSeek(
            activity,
            preferences.getInt(AudioPreferences.MUSIC_VOLUME, 80)
        )
        val effectsSwitch = optionSwitch(
            activity,
            "Efectos",
            AudioPreferences.areEffectsEnabled(preferences)
        ).apply {
            setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(4))
        }
        val effectsLabel = optionLabel(activity)
        val effectsSeek = volumeSeek(
            activity,
            preferences.getInt(AudioPreferences.EFFECTS_VOLUME, 80)
        )
        val vibrationSwitch = optionSwitch(
            activity,
            "Vibración al interactuar",
            preferences.getBoolean(PREF_VIBRATION_ON, false)
        ).apply {
            setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(4))
        }
        val textSizeLabel = optionLabel(activity).apply {
            setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(5))
        }
        var textSize = preferences
            .getInt(PREF_GAMEPLAY_TEXT_SIZE, DEFAULT_GAMEPLAY_TEXT_SIZE)
            .coerceIn(0, 2)
        val textSizeButtons = mutableListOf<Button>()

        fun refreshRows() {
            musicLabel.text = "Música: ${musicSeek.progress}%"
            effectsLabel.text = "Efectos: ${effectsSeek.progress}%"
            musicLabel.alpha = if (musicSwitch.isChecked) 1f else 0.42f
            musicSeek.alpha = musicLabel.alpha
            musicSeek.isEnabled = musicSwitch.isChecked
            effectsLabel.alpha = if (effectsSwitch.isChecked) 1f else 0.42f
            effectsSeek.alpha = effectsLabel.alpha
            effectsSeek.isEnabled = effectsSwitch.isChecked
            textSizeLabel.text = "Tamaño de texto: ${textSizeOptions()[textSize]}"
            textSizeButtons.forEachIndexed { index, button ->
                val selected = index == textSize
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(
                    activity.getColor(if (selected) R.color.bg_dark else R.color.text_primary)
                )
                button.alpha = if (selected) 1f else 0.82f
            }
        }

        musicSwitch.setOnCheckedChangeListener { _, enabled ->
            preferences.edit().putBoolean(AudioPreferences.MUSIC_ENABLED, enabled).apply()
            refreshRows()
            MusicManager.refresh(activity)
        }
        effectsSwitch.setOnCheckedChangeListener { _, enabled ->
            preferences.edit().putBoolean(AudioPreferences.EFFECTS_ENABLED, enabled).apply()
            refreshRows()
            if (enabled) GameplayEffects.play(activity, GameplayEffect.CONFIRM)
        }
        vibrationSwitch.setOnCheckedChangeListener { _, enabled ->
            preferences.edit().putBoolean(PREF_VIBRATION_ON, enabled).apply()
            if (enabled) GameplayEffects.play(activity, GameplayEffect.CONFIRM)
        }
        musicSeek.setOnSeekBarChangeListener(
            volumeListener(activity, AudioPreferences.MUSIC_VOLUME) {
                refreshRows()
                MusicManager.refresh(activity)
            }
        )
        effectsSeek.setOnSeekBarChangeListener(
            volumeListener(activity, AudioPreferences.EFFECTS_VOLUME) { refreshRows() }
        )

        content.addView(musicSwitch)
        content.addView(musicLabel)
        content.addView(musicSeek)
        content.addView(effectsSwitch)
        content.addView(effectsLabel)
        content.addView(effectsSeek)
        content.addView(vibrationSwitch)
        content.addView(textSizeLabel)
        val textSizeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        textSizeOptions().forEachIndexed { index, label ->
            val button = compactButton(activity, label).apply {
                setOnClickListener {
                    textSize = index
                    preferences.edit().putInt(PREF_GAMEPLAY_TEXT_SIZE, textSize).apply()
                    GameplayEffects.play(activity, GameplayEffect.CONFIRM)
                    refreshRows()
                    onTextSizeChanged(textSize)
                }
            }
            textSizeButtons += button
            textSizeRow.addView(
                button,
                LinearLayout.LayoutParams(0, activity.dp(38), 1f).apply {
                    if (index > 0) marginStart = activity.dp(7)
                }
            )
        }
        content.addView(textSizeRow)
        refreshRows()

        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton("CERRAR", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val availableWidth = activity.resources.displayMetrics.widthPixels - activity.dp(24)
        dialog.window?.setLayout(
            activity.dp(500).coerceAtMost(availableWidth),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setDimAmount(0.55f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            minHeight = activity.dp(44)
            isAllCaps = false
            setTextColor(activity.getColor(R.color.accent_gold))
        }
        return dialog
    }

    private fun optionSwitch(
        activity: AppCompatActivity,
        label: String,
        checked: Boolean
    ): SwitchCompat {
        return SwitchCompat(activity).apply {
            applyTraidoresSwitchStyle()
            text = label
            isChecked = checked
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 14f
            setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(4))
        }
    }

    private fun optionLabel(activity: AppCompatActivity): TextView {
        return TextView(activity).apply {
            setTextColor(activity.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(activity.dp(4), 0, activity.dp(4), activity.dp(2))
        }
    }

    private fun volumeSeek(activity: AppCompatActivity, value: Int): SeekBar {
        return SeekBar(activity).apply {
            max = 100
            progress = value.coerceIn(0, 100)
        }
    }

    private fun volumeListener(
        activity: AppCompatActivity,
        key: String,
        onChanged: () -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                AudioPreferences.preferences(activity)
                    .edit()
                    .putInt(key, progress.coerceIn(0, 100))
                    .apply()
                onChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun compactButton(activity: AppCompatActivity, label: String): Button {
        return Button(activity).apply {
            text = label
            textSize = 12f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            isAllCaps = false
            maxLines = 1
            setTextColor(activity.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_btn_dark_ripple)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                14,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }
    }

    private fun textSizeOptions(): List<String> = listOf("Compacto", "Normal", "Grande")

    private fun AppCompatActivity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
