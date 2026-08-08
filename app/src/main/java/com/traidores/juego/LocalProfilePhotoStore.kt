package com.traidores.juego

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/**
 * Guarda una única foto de perfil dentro del almacenamiento privado de la app.
 *
 * La foto nunca se publica en Firebase ni queda visible para otros jugadores. Se vuelve a
 * codificar para quitar metadatos (incluida la ubicación EXIF), limitar el tamaño en disco y
 * evitar depender para siempre del permiso temporal que entrega el selector de Android.
 */
object LocalProfilePhotoStore {
    private const val DIRECTORY = "profile"
    private const val FINAL_FILE = "local_profile_photo.jpg"
    private const val PENDING_FILE = "local_profile_photo.pending.jpg"
    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 86

    fun importPending(context: Context, uri: Uri): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        val source = decodeSampledBitmap(resolver, uri)
            ?: error("No se pudo leer la imagen seleccionada.")
        val oriented = rotateFromExif(resolver, uri, source)
        val square = cropSquare(oriented)
        val resized = if (square.width > MAX_DIMENSION || square.height > MAX_DIMENSION) {
            Bitmap.createScaledBitmap(square, MAX_DIMENSION, MAX_DIMENSION, true)
        } else {
            square
        }

        val destination = pendingFile(context)
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "No se pudo guardar la foto."
            }
        }

        val bitmapsToRecycle = mutableListOf<Bitmap>()
        listOf(resized, square, oriented, source).forEach { candidate ->
            if (bitmapsToRecycle.none { it === candidate }) bitmapsToRecycle += candidate
        }
        bitmapsToRecycle.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun render(context: Context, image: ImageView, preferPending: Boolean): Boolean {
        val file = when {
            preferPending && pendingFile(context).isFile -> pendingFile(context)
            finalFile(context).isFile -> finalFile(context)
            else -> return false
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.imageMatrix = Matrix()
        image.setImageBitmap(bitmap)
        return true
    }

    fun renderForProfile(
        context: Context,
        image: ImageView,
        profile: PlayerProfile,
        preferPending: Boolean = false
    ): Boolean {
        if (!isEnabledForProfile(context, profile)) return false
        return render(context, image, preferPending)
    }

    fun isEnabledForProfile(context: Context, profile: PlayerProfile): Boolean {
        val currentPublicId = PlayerPublicIdentity.currentPublicId(context)
        if (currentPublicId.isBlank() || currentPublicId != profile.publicId) return false
        val enabled = context
            .getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
        return enabled && hasSavedPhoto(context)
    }

    fun hasSavedPhoto(context: Context): Boolean = finalFile(context).isFile

    fun hasPendingPhoto(context: Context): Boolean = pendingFile(context).isFile

    fun commitPending(context: Context): Boolean {
        val pending = pendingFile(context)
        if (!pending.isFile) return finalFile(context).isFile
        val destination = finalFile(context)
        destination.parentFile?.mkdirs()
        return runCatching {
            pending.copyTo(destination, overwrite = true)
            pending.delete()
            true
        }.getOrDefault(false)
    }

    fun discardPending(context: Context) {
        pendingFile(context).takeIf(File::exists)?.delete()
    }

    fun deleteSavedPhoto(context: Context) {
        discardPending(context)
        finalFile(context).takeIf(File::exists)?.delete()
    }

    private fun decodeSampledBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longestSide / sampleSize > MAX_DIMENSION * 4) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun rotateFromExif(
        resolver: ContentResolver,
        uri: Uri,
        source: Bitmap
    ): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(0)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (rotation == 0) return source
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        )
    }

    private fun cropSquare(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        return Bitmap.createBitmap(source, left, top, size, size)
    }

    private fun profileDirectory(context: Context): File = File(context.filesDir, DIRECTORY)

    private fun finalFile(context: Context): File = File(profileDirectory(context), FINAL_FILE)

    private fun pendingFile(context: Context): File = File(profileDirectory(context), PENDING_FILE)
}
