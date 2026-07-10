package com.traidores.juego

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.max

object MusicManager {
    private const val PAUSE_DELAY_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var activeScreens = 0
    private var currentTrackRes = R.raw.menu_music
    private var currentVictoryTrackRes = 0
    private var player: MediaPlayer? = null
    private var victoryPlayer: MediaPlayer? = null
    private var transitionPaused = false

    private val pauseIfBackground = Runnable {
        if (activeScreens == 0) {
            pausePlayer()
            pauseVictoryPlayer()
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
        val sharedPref = AudioPreferences.preferences(appContext)
        val musicOn = AudioPreferences.isMusicEnabled(sharedPref)
        val volume = AudioPreferences.musicVolume(sharedPref)

        ensurePlayer(appContext)

        player.runCatchingIfPresent { setVolume(volume, volume) }
        victoryPlayer.runCatchingIfPresent { setVolume(volume, volume) }

        if (activeScreens > 0 && musicOn && volume > 0f && !transitionPaused) {
            startPlayer()
        } else {
            pausePlayer()
        }

        if (activeScreens > 0 && musicOn && volume > 0f && victoryPlayer != null) {
            startVictoryPlayer()
        } else if (!musicOn || volume <= 0f) {
            pauseVictoryPlayer()
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
        pausePlayer()
    }

    fun resumeGamePhaseAfterTransition(context: Context, session: GameSession) {
        if (session.winner.isNotBlank()) {
            transitionPaused = true
            pausePlayer()
            return
        }
        transitionPaused = false
        switchTrack(context, trackForSession(session))
    }

    fun prepareGamePhaseWithoutPlayback(session: GameSession) {
        transitionPaused = false
        pausePlayer()
        val trackRes = trackForSession(session)
        if (currentTrackRes != trackRes) {
            currentTrackRes = trackRes
            releasePlayer()
            player = null
        }
    }

    fun playVictoryMusic(context: Context, winnerKey: String) {
        val trackRes = victoryTrackForWinner(winnerKey)
        if (victoryPlayer != null && currentVictoryTrackRes == trackRes) {
            refresh(context)
            return
        }

        transitionPaused = true
        pausePlayer()
        stopVictoryMusic()
        currentVictoryTrackRes = trackRes
        val appContext = context.applicationContext
        val sharedPref = AudioPreferences.preferences(appContext)
        val musicOn = AudioPreferences.isMusicEnabled(sharedPref)
        val volume = AudioPreferences.musicVolume(sharedPref)
        if (!musicOn || volume <= 0f) return

        victoryPlayer = MediaPlayer.create(appContext, trackRes)?.apply {
            isLooping = false
            setVolume(volume, volume)
            setOnErrorListener { errored, _, _ ->
                if (victoryPlayer === errored) {
                    victoryPlayer = null
                }
                errored.runCatching { release() }
                true
            }
            setOnCompletionListener { completed ->
                if (victoryPlayer === completed) {
                    victoryPlayer = null
                }
                completed.runCatching { release() }
            }
            runCatching { start() }.onFailure {
                if (victoryPlayer === this) {
                    victoryPlayer = null
                }
                runCatching { release() }
            }
        }
    }

    fun resumeVictoryMusic(context: Context) {
        transitionPaused = true
        pausePlayer()
        refresh(context)
    }

    fun pauseVictoryMusic() {
        pauseVictoryPlayer()
    }

    fun stopVictoryMusic() {
        victoryPlayer?.runCatching {
            stop()
            release()
        }
        victoryPlayer = null
        currentVictoryTrackRes = 0
    }

    private fun trackForSession(session: GameSession): Int {
        return when {
            session.phase == GamePhase.REPARTO -> dayTrackForMap(session.mapKey)
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

    private fun victoryTrackForWinner(winnerKey: String): Int {
        return when (winnerKey) {
            GameRules.TRAITOR_WINNER -> R.raw.victory_music_traitors
            else -> R.raw.victory_music_town
        }
    }

    private fun isNightPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.NOCHE_ASESINO ||
            phase == GamePhase.NOCHE_MERCENARIO ||
            phase == GamePhase.NOCHE_POLICIA ||
            phase == GamePhase.NOCHE_MEDICO ||
            phase == GamePhase.NOCHE_ORACULO
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

    private fun ensurePlayer(context: Context) {
        if (player != null) return
        player = MediaPlayer.create(context, currentTrackRes)?.apply {
            isLooping = true
            setOnErrorListener { errored, _, _ ->
                if (player === errored) {
                    player = null
                }
                errored.runCatching { release() }
                true
            }
        }
    }

    private fun startPlayer() {
        val current = player ?: return
        runCatching {
            if (!current.isPlaying) current.start()
        }.onFailure {
            if (player === current) {
                player = null
            }
            current.runCatching { release() }
        }
    }

    private fun pausePlayer() {
        player.runCatchingIfPresent { pause() }
    }

    private fun releasePlayer() {
        player.runCatchingIfPresent { release() }
    }

    private fun startVictoryPlayer() {
        val current = victoryPlayer ?: return
        runCatching {
            if (!current.isPlaying) current.start()
        }.onFailure {
            if (victoryPlayer === current) {
                victoryPlayer = null
            }
            current.runCatching { release() }
        }
    }

    private fun pauseVictoryPlayer() {
        victoryPlayer.runCatchingIfPresent { pause() }
    }

    private inline fun MediaPlayer?.runCatchingIfPresent(block: MediaPlayer.() -> Unit) {
        this?.runCatching(block)
    }
}
