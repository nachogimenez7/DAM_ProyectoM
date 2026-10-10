package com.traidores.juego

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Downloads published profile photos (Firebase Storage and Google HTTPS URLs).
 *
 * Play Games' ImageManager does not load Firebase Storage URLs, so remote photos fell back to
 * the illustrated avatar without an error. Every photo revision has its own URL, so a cached
 * file never goes stale: replacing a photo publishes a new URL.
 */
internal object RemoteProfilePhotoLoader {
    private const val MAX_BYTES = 512 * 1024 // Uploads are capped at 256 KiB.
    private const val TARGET_PX = 256
    private const val DISK_CACHE_BYTES = 8L * 1024 * 1024
    private const val FAILURE_RETRY_MS = 60_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    private val memory = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()
    private val failedAt = mutableMapOf<String, Long>()

    fun cached(url: String): Bitmap? = synchronized(memory) { memory.get(url) }

    /** [onResult] runs on the main thread; null means the photo is unavailable for now. */
    fun load(context: Context, url: String, onResult: (Bitmap?) -> Unit) {
        cached(url)?.let { bitmap -> onResult(bitmap); return }
        synchronized(pending) {
            failedAt[url]?.let { at ->
                if (SystemClock.elapsedRealtime() - at < FAILURE_RETRY_MS) { main.post { onResult(null) }; return }
            }
            pending[url]?.let { waiting -> waiting += onResult; return }
            pending[url] = mutableListOf(onResult)
        }
        val cacheDir = File(context.applicationContext.cacheDir, "profile_photos")
        executor.execute {
            val bitmap = runCatching { fromDisk(cacheDir, url) ?: download(cacheDir, url) }
                .onFailure { OnlineDebugLog.e("profile_photo_download_failure", it) }
                .getOrNull()
            if (bitmap != null) synchronized(memory) { memory.put(url, bitmap) }
            val waiting = synchronized(pending) {
                if (bitmap == null) failedAt[url] = SystemClock.elapsedRealtime() else failedAt.remove(url)
                pending.remove(url).orEmpty()
            }
            main.post { waiting.forEach { it(bitmap) } }
        }
    }

    private fun fromDisk(cacheDir: File, url: String): Bitmap? {
        val file = File(cacheDir, key(url))
        if (!file.isFile) return null
        val bitmap = decode(file.readBytes())
        if (bitmap == null) file.delete() else file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    private fun download(cacheDir: File, url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
            val bitmap = decode(bytes) ?: return null
            runCatching {
                cacheDir.mkdirs()
                File(cacheDir, key(url)).writeBytes(bytes)
                trim(cacheDir)
            }
            return bitmap
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun trim(cacheDir: File) {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= DISK_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
