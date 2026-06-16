package com.traidores.juego

import android.content.Context
import android.media.MediaPlayer

object GameplaySoundEffects {
    private val activePlayers = mutableSetOf<MediaPlayer>()

    fun play(context: Context, soundRes: Int) {
        val preferences = AudioPreferences.preferences(context)
        if (!AudioPreferences.areEffectsEnabled(preferences)) return

        val volume = AudioPreferences.effectsVolume(preferences)
        if (volume <= 0f) return

        val player = MediaPlayer.create(context.applicationContext, soundRes) ?: return
        activePlayers += player
        player.setVolume(volume, volume)
        player.setOnCompletionListener(::release)
        player.setOnErrorListener { failed, _, _ ->
            release(failed)
            true
        }
        player.start()
    }

    private fun release(player: MediaPlayer) {
        activePlayers -= player
        player.release()
    }
}
