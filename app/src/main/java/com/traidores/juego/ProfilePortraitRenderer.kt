package com.traidores.juego

import android.content.Context
import android.graphics.Matrix
import android.widget.ImageView

/** Keeps illustrated faces in the same frame in the Profile, menu and online identity. */
internal object ProfilePortraitRenderer {
    fun render(context: Context, image: ImageView, avatarKey: String, publishedPhoto: String) {
        val entry = ProfileRoleCatalog.find(avatarKey)
        val fallback = DrawableResourceCatalog.resolveOrPlaceholder(entry.role.imageResName)
        fun artwork() {
            image.scaleType = ImageView.ScaleType.MATRIX
            image.setImageResource(fallback)
            alignArtwork(image, entry.verticalFocus)
        }
        image.setTag(R.id.remote_profile_avatar_request, null)
        artwork()
        val requested = PlayGamesProfileAvatar.render(context, image, publishedPhoto, fallback) { artwork() }
        // ImageManager uses the fallback while downloading; it also needs the portrait framing.
        if (!requested || image.scaleType == ImageView.ScaleType.FIT_CENTER) artwork()
    }

    fun alignArtwork(image: ImageView, verticalFocus: Float) {
        val expected = image.drawable
        image.post {
            if (image.drawable !== expected || image.scaleType != ImageView.ScaleType.MATRIX) return@post
            val drawable = image.drawable ?: return@post
            val width = drawable.intrinsicWidth.toFloat()
            val height = drawable.intrinsicHeight.toFloat()
            if (width <= 0 || height <= 0 || image.width <= 0 || image.height <= 0) return@post
            val scale = maxOf(image.width / width, image.height / height) * 1.12f
            val top = (image.height / 2f - height * scale * verticalFocus.coerceIn(0f, 1f))
                .coerceIn(image.height - height * scale, 0f)
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate((image.width - width * scale) / 2f, top)
            }
        }
    }
}
