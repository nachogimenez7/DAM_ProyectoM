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
    private var currentTrackRes = R.raw.menu_music
    private var player: MediaPlayer? = null
    private var victoryPlayer: MediaPlayer? = null
    private var transitionPaused = false

    private val pauseIfBackground = Runnable {
        if (activeScreens == 0) {
            player?.pause()
            victoryPlayer?.pause()
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
            player = MediaPlayer.create(appContext, currentTrackRes).apply {
                isLooping = true
            }
        }

        player?.setVolume(volume, volume)
        victoryPlayer?.setVolume(volume, volume)

        if (activeScreens > 0 && soundOn && volume > 0f && !transitionPaused) {
            if (player?.isPlaying == false) {
                player?.start()
            }
        } else {
            player?.pause()
        }

        if (activeScreens > 0 && soundOn && volume > 0f && victoryPlayer != null) {
            if (victoryPlayer?.isPlaying == false) {
                victoryPlayer?.start()
            }
        } else if (!soundOn || volume <= 0f) {
            victoryPlayer?.pause()
        }
    }

    fun playMenuMusic(context: Context) {
        stopVictoryMusic()
        transitionPaused = false
        switchTrack(context, R.raw.menu_music)
    }

    fun playGameIntro(context: Context, session: GameSession) {
        stopVictoryMusic()
        transitionPaused = false
        switchTrack(context, dayTrackForMap(session.mapKey))
    }

    fun playGamePhase(context: Context, session: GameSession) {
        if (session.winner.isNotBlank()) return
        stopVictoryMusic()
        transitionPaused = false
        switchTrack(context, trackForSession(session))
    }

    fun pauseForTransition() {
        transitionPaused = true
        player?.pause()
    }

    fun resumeGamePhaseAfterTransition(context: Context, session: GameSession) {
        if (session.winner.isNotBlank()) {
            transitionPaused = true
            player?.pause()
            return
        }
        transitionPaused = false
        switchTrack(context, trackForSession(session))
    }

    fun prepareGamePhaseWithoutPlayback(session: GameSession) {
        transitionPaused = false
        player?.pause()
        val trackRes = trackForSession(session)
        if (currentTrackRes != trackRes) {
            currentTrackRes = trackRes
            player?.release()
            player = null
        }
    }

    fun playVictoryMusic(context: Context) {
        if (victoryPlayer != null) {
            refresh(context)
            return
        }

        transitionPaused = true
        player?.pause()
        val appContext = context.applicationContext
        val sharedPref = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val soundOn = sharedPref.getBoolean("sound_on", true)
        val volume = sharedPref.getInt("music_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        victoryPlayer = MediaPlayer.create(appContext, R.raw.victory_music)?.apply {
            isLooping = false
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (victoryPlayer === completed) {
                    victoryPlayer = null
                }
                completed.release()
            }
            start()
        }
    }

    fun resumeVictoryMusic(context: Context) {
        transitionPaused = true
        player?.pause()
        refresh(context)
    }

    fun pauseVictoryMusic() {
        victoryPlayer?.pause()
    }

    fun stopVictoryMusic() {
        victoryPlayer?.runCatching {
            stop()
            release()
        }
        victoryPlayer = null
    }

    private fun trackForSession(session: GameSession): Int {
        return when {
            session.phase == GamePhase.REPARTO -> dayTrackForMap(session.mapKey)
            isDecisiveDebate(session) -> R.raw.decisive_debate_music
            isNightPhase(session.phase) -> R.raw.night_phase_music
            else -> dayTrackForMap(session.mapKey)
        }
    }

    private fun dayTrackForMap(mapKey: String): Int {
        return when (mapKey) {
            "grecia" -> R.raw.day_music_greece
            "medieval" -> R.raw.day_music_medieval
            else -> R.raw.day_music_pampa
        }
    }

    private fun isNightPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.NOCHE_ASESINO ||
            phase == GamePhase.NOCHE_MERCENARIO ||
            phase == GamePhase.NOCHE_POLICIA ||
            phase == GamePhase.NOCHE_MEDICO
    }

    private fun isDecisiveDebate(session: GameSession): Boolean {
        val isDebatePhase = session.phase == GamePhase.DIA_DEBATE ||
            session.phase == GamePhase.VOTACION ||
            session.phase == GamePhase.RESULTADO
        if (!isDebatePhase) return false

        val alivePlayers = session.players.filter { it.alive }
        val traitors = alivePlayers.count { GameRules.isTraitorRole(it.role) }
        val town = alivePlayers.size - traitors
        return alivePlayers.size <= 4 && traitors > 0 && town > 0
    }

    private fun switchTrack(context: Context, trackRes: Int) {
        if (currentTrackRes == trackRes && player != null) {
            refresh(context)
            return
        }

        currentTrackRes = trackRes
        player?.release()
        player = null
        refresh(context)
    }
}
