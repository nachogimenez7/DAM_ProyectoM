package com.traidores.juego

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/** Reproductor compartido y no bloqueante para efectos cortos. */
object ShortSoundPool {
    private const val MAX_STREAMS = 8

    private data class PlayRequest(
        val soundRes: Int,
        val volume: Float,
        val replaceChannel: String?,
        val playbackRate: Float
    )

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private val soundIdsByRes = mutableMapOf<Int, Int>()
    private val resBySoundId = mutableMapOf<Int, Int>()
    private val loadedResources = mutableSetOf<Int>()
    private val pendingByResource = mutableMapOf<Int, MutableList<PlayRequest>>()
    private val activeStreamsByChannel = mutableMapOf<String, Int>()

    fun preload(context: Context, soundResources: Collection<Int>) {
        val appContext = context.applicationContext
        soundResources.distinct().forEach { soundRes ->
            synchronized(lock) {
                ensurePoolLocked()
                loadLocked(appContext, soundRes)
            }
        }
    }

    fun play(
        context: Context,
        soundRes: Int,
        volume: Float,
        replaceChannel: String? = null,
        playbackRate: Float = 1f
    ) {
        val request = PlayRequest(
            soundRes = soundRes,
            volume = volume.coerceIn(0f, 1f),
            replaceChannel = replaceChannel,
            playbackRate = playbackRate.coerceIn(0.5f, 2f)
        )
        val appContext = context.applicationContext
        val soundId = synchronized(lock) {
            ensurePoolLocked()
            soundIdsByRes[soundRes]
                ?.takeIf { soundRes in loadedResources }
                ?: run {
                    val pending = pendingByResource.getOrPut(soundRes) { mutableListOf() }
                    if (replaceChannel != null) {
                        pending.removeAll { it.replaceChannel == replaceChannel }
                    }
                    pending += request
                    loadLocked(appContext, soundRes)
                    null
                }
        }
        if (soundId != null) playLoaded(request, soundId)
    }

    fun stopChannel(channel: String) {
        val target = synchronized(lock) {
            val currentPool = pool ?: return
            val streamId = activeStreamsByChannel.remove(channel) ?: return
            currentPool to streamId
        }
        target.first.stop(target.second)
    }

    /** Libera los efectos decodificados cuando la interfaz completa queda en segundo plano. */
    fun release() {
        val current = synchronized(lock) {
            val active = pool
            pool = null
            soundIdsByRes.clear()
            resBySoundId.clear()
            loadedResources.clear()
            pendingByResource.clear()
            activeStreamsByChannel.clear()
            active
        }
        mainHandler.removeCallbacksAndMessages(null)
        current?.release()
    }

    private fun ensurePoolLocked(): SoundPool {
        pool?.let { return it }
        val created = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        created.setOnLoadCompleteListener { callbackPool, soundId, status ->
            val result = synchronized(lock) {
                val soundRes = resBySoundId[soundId] ?: return@setOnLoadCompleteListener
                if (pool !== callbackPool) return@setOnLoadCompleteListener
                if (status == 0) {
                    loadedResources += soundRes
                } else {
                    soundIdsByRes.remove(soundRes)
                    resBySoundId.remove(soundId)
                }
                pendingByResource.remove(soundRes).orEmpty() to (status == 0)
            }
            if (result.second) {
                result.first.forEach { request ->
                    mainHandler.post { playLoaded(request, soundId) }
                }
            }
        }
        pool = created
        return created
    }

    private fun loadLocked(context: Context, soundRes: Int) {
        if (soundRes in soundIdsByRes) return
        val soundId = ensurePoolLocked().load(context, soundRes, 1)
        if (soundId != 0) {
            soundIdsByRes[soundRes] = soundId
            resBySoundId[soundId] = soundRes
        }
    }

    private fun playLoaded(request: PlayRequest, soundId: Int) {
        val currentPool = synchronized(lock) { pool } ?: return
        request.replaceChannel?.let { channel ->
            val previous = synchronized(lock) { activeStreamsByChannel.remove(channel) }
            if (previous != null) currentPool.stop(previous)
        }
        val streamId = currentPool.play(
            soundId,
            request.volume,
            request.volume,
            1,
            0,
            request.playbackRate
        )
        if (streamId != 0 && request.replaceChannel != null) {
            synchronized(lock) {
                activeStreamsByChannel[request.replaceChannel] = streamId
            }
        }
    }
}
