package com.traidores.juego

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.max

object MusicManager {
    private const val PREFS = "TraidoresPrefs"
    private const val PAUSE_DELAY_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var activeScreens = 0
    private var player: MediaPlayer? = null

    private val pauseIfBackground = Runnable {
        if (activeScreens == 0) {
            player?.pause()
        }
    }

    fun onActivityStarted(context: Context) {
        activeScreens += 1
        handler.removeCallbacks(pauseIfBackground)
        refresh(context)
    }

    fun onActivityStopped() {
        activeScreens = max(0, activeScreens - 1)
        if (activeScreens == 0) {
            handler.postDelayed(pauseIfBackground, PAUSE_DELAY_MS)
        }
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val sharedPref = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val soundOn = sharedPref.getBoolean("sound_on", true)
        val volume = sharedPref.getInt("music_volume", 80) / 100f

        if (player == null) {
            player = MediaPlayer.create(appContext, R.raw.menu_music).apply {
                isLooping = true
            }
        }

        player?.setVolume(volume, volume)

        if (activeScreens > 0 && soundOn && volume > 0f) {
            if (player?.isPlaying == false) {
                player?.start()
            }
        } else {
            player?.pause()
        }
    }
}
