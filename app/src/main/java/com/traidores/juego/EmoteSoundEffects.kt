package com.traidores.juego

import android.content.Context
import android.media.MediaPlayer
import android.os.SystemClock

/**
 * Canal único de sonido para los emotes de la mesa.
 *
 * A diferencia de [GameplaySoundEffects] (que superpone un MediaPlayer por efecto),
 * los emotes usan un solo canal con política "el último gana": un emote nuevo corta
 * al anterior en vez de apilarse. Además se aplica un throttle para que una avalancha
 * de emotes (p. ej. muchos jugadores online reaccionando a la vez) no ametralle audio.
 *
 * Un único sonido por emoción se reutiliza en los tres temas (aldeano/asesino/detective),
 * ya que el disparo se resuelve por [emotionKey] y no por rol ni mapa.
 */
object EmoteSoundEffects {
    // Máximo ~1 sonido de emote cada 300ms: evita solapamiento y machine-gun.
    private const val MIN_INTERVAL_MS = 300L

    private var currentPlayer: MediaPlayer? = null
    private var lastPlayAtMs = 0L

    fun play(context: Context, emotionKey: String, volumeScale: Float = 1f) {
        val soundRes = soundResFor(emotionKey) ?: return

        val preferences = AudioPreferences.preferences(context)
        if (!AudioPreferences.areEffectsEnabled(preferences)) return
        val volume = AudioPreferences.effectsVolume(preferences)
        if (volume <= 0f) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAtMs < MIN_INTERVAL_MS) return
        lastPlayAtMs = now

        // Último gana: descarta el emote anterior si todavía sonaba.
        releaseCurrent()

        val player = MediaPlayer.create(context.applicationContext, soundRes) ?: return
        currentPlayer = player
        val effectiveVolume = (volume * volumeScale).coerceIn(0f, 1f)
        player.setVolume(effectiveVolume, effectiveVolume)
        player.setOnCompletionListener { finished ->
            if (currentPlayer === finished) currentPlayer = null
            finished.release()
        }
        player.setOnErrorListener { failed, _, _ ->
            if (currentPlayer === failed) currentPlayer = null
            failed.release()
            true
        }
        player.start()
    }

    private fun releaseCurrent() {
        currentPlayer?.let { player ->
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
        currentPlayer = null
    }

    private fun soundResFor(emotionKey: String): Int? = when (emotionKey) {
        "happy" -> R.raw.sfx_emote_happy
        "sad" -> R.raw.sfx_emote_sad
        "suspicious" -> R.raw.sfx_emote_suspicious
        "angry" -> R.raw.sfx_emote_angry
        "premium_hermosa_manana" -> R.raw.sfx_emote_premium_hermosa_manana
        "premium_mate" -> R.raw.sfx_emote_premium_mate
        "premium_dormida" -> R.raw.sfx_emote_premium_dormida
        else -> null
    }
}
