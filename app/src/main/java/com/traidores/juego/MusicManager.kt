package com.traidores.juego

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.max

/** Musica larga preparada de forma asincrona para no bloquear la interfaz. */
object MusicManager {
    private const val PAUSE_DELAY_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var activeScreens = 0
    private var currentTrackRes = R.raw.menu_music
    private var currentVictoryTrackRes = 0
    private var player: MediaPlayer? = null
    private var victoryPlayer: MediaPlayer? = null
    private var playerPrepared = false
    private var victoryPlayerPrepared = false
    private var playerStarted = false
    private var victoryPlayerStarted = false
    private var victoryCompletionListener: (() -> Unit)? = null
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
        if (activeScreens == 0) handler.postDelayed(pauseIfBackground, PAUSE_DELAY_MS)
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
        }
    }

    fun playVictoryMusic(
        context: Context,
        winnerKey: String,
        onCompletion: (() -> Unit)? = null
    ) {
        val trackRes = victoryTrackForWinner(winnerKey)
        if (victoryPlayer != null && currentVictoryTrackRes == trackRes) {
            victoryCompletionListener = onCompletion
            refresh(context)
            return
        }

        transitionPaused = true
        pausePlayer()
        stopVictoryMusic()
        victoryCompletionListener = onCompletion
        currentVictoryTrackRes = trackRes
        val appContext = context.applicationContext
        val preferences = AudioPreferences.preferences(appContext)
        val musicOn = AudioPreferences.isMusicEnabled(preferences)
        val volume = AudioPreferences.musicVolume(preferences)
        if (!musicOn || volume <= 0f) return

        val created = createAsyncPlayer(appContext, trackRes, looping = false) { prepared ->
            if (victoryPlayer !== prepared) return@createAsyncPlayer
            victoryPlayerPrepared = true
            refresh(appContext)
        } ?: return
        victoryPlayer = created
        created.setVolume(volume, volume)
        created.setOnErrorListener { errored, _, _ ->
            if (victoryPlayer === errored) releaseVictoryPlayer()
            true
        }
        created.setOnCompletionListener { completed ->
            if (victoryPlayer !== completed) return@setOnCompletionListener
            val completion = victoryCompletionListener
            releaseVictoryPlayer()
            completion?.invoke()
        }
    }

    fun resumeVictoryMusic(context: Context) {
        transitionPaused = true
        pausePlayer()
        refresh(context)
    }

    fun pauseVictoryMusic() = pauseVictoryPlayer()

    fun stopVictoryMusic() {
        releaseVictoryPlayer()
        currentVictoryTrackRes = 0
    }

    /** Conserva la pista elegida, pero devuelve al sistema los decodificadores de audio. */
    fun releaseForBackground() {
        handler.removeCallbacks(pauseIfBackground)
        releasePlayer()
    }

    private fun trackForSession(session: GameSession): Int = when {
        session.phase == GamePhase.REPARTO -> dayTrackForMap(session.mapKey)
        isNightPhase(session.phase) -> R.raw.night_phase_music
        else -> dayTrackForMap(session.mapKey)
    }

    private fun dayTrackForMap(mapKey: String): Int = when (mapKey) {
        "grecia" -> R.raw.day_music_greece
        "medieval" -> R.raw.day_music_medieval
        else -> R.raw.day_music_pampa
    }

    private fun victoryTrackForWinner(winnerKey: String): Int = when (winnerKey) {
        GameRules.TRAITOR_WINNER -> R.raw.victory_music_traitors
        else -> R.raw.victory_music_town
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
        releasePlayer()
        refresh(context)
    }

    private fun ensurePlayer(context: Context) {
        if (player != null) return
        val created = createAsyncPlayer(context, currentTrackRes, looping = true) { prepared ->
            if (player !== prepared) return@createAsyncPlayer
            playerPrepared = true
            refresh(context)
        } ?: return
        player = created
        created.setOnErrorListener { errored, _, _ ->
            if (player === errored) releasePlayer()
            true
        }
    }

    private fun createAsyncPlayer(
        context: Context,
        soundRes: Int,
        looping: Boolean,
        onPrepared: (MediaPlayer) -> Unit
    ): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        return runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            context.resources.openRawResourceFd(soundRes).use { descriptor ->
                requireNotNull(descriptor) { "No se pudo abrir el recurso de audio $soundRes" }
                mediaPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            mediaPlayer.isLooping = looping
            mediaPlayer.setOnPreparedListener { prepared -> onPrepared(prepared) }
            mediaPlayer.prepareAsync()
            mediaPlayer
        }.getOrElse {
            mediaPlayer.runCatching { release() }
            null
        }
    }

    private fun startPlayer() {
        val current = player ?: return
        if (!playerPrepared || playerStarted) return
        runCatching {
            current.start()
            playerStarted = true
        }.onFailure {
            if (player === current) releasePlayer()
        }
    }

    private fun pausePlayer() {
        if (!playerStarted) return
        player?.runCatching { pause() }
        playerStarted = false
    }

    private fun releasePlayer() {
        val current = player
        player = null
        playerPrepared = false
        playerStarted = false
        current?.runCatching {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            release()
        }
    }

    private fun startVictoryPlayer() {
        val current = victoryPlayer ?: return
        if (!victoryPlayerPrepared || victoryPlayerStarted) return
        runCatching {
            current.start()
            victoryPlayerStarted = true
        }.onFailure {
            if (victoryPlayer === current) releaseVictoryPlayer()
        }
    }

    private fun pauseVictoryPlayer() {
        if (!victoryPlayerStarted) return
        victoryPlayer?.runCatching { pause() }
        victoryPlayerStarted = false
    }

    private fun releaseVictoryPlayer() {
        val current = victoryPlayer
        victoryPlayer = null
        victoryPlayerPrepared = false
        victoryPlayerStarted = false
        victoryCompletionListener = null
        current?.runCatching {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
    }

    private inline fun MediaPlayer?.runCatchingIfPresent(block: MediaPlayer.() -> Unit) {
        this?.runCatching(block)
    }
}
