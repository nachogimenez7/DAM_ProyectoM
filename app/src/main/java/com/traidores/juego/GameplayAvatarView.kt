package com.traidores.juego

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Avatar compacto del gameplay: foto del humano local o inicial como fallback seguro. */
class GameplayAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val initialView = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(context.getColor(R.color.bg_dark))
        typeface = Typeface.DEFAULT_BOLD
    }
    private val photoView = CircleProfileImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
    }

    init {
        addView(
            initialView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        addView(
            photoView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ).apply {
                val inset = dp(1)
                setMargins(inset, inset, inset, inset)
            }
        )
    }

    fun bind(
        session: GameSession,
        player: GamePlayer?,
        fallbackInitial: String,
        textSizeSp: Float,
        backgroundRes: Int = R.drawable.bg_player_avatar
    ) {
        setBackgroundResource(backgroundRes)
        initialView.text = fallbackInitial
        initialView.textSize = textSizeSp

        val profile = player?.let { PlayerProfileStore.profileFor(context, session, it) }
        val avatarEntry = profile?.let { ProfileRoleCatalog.find(it.avatarKey) }
        val fallbackRes = avatarEntry?.role?.imageResName
            ?.let { resources.getIdentifier(it, "drawable", context.packageName) }
            ?.takeIf { it != 0 }
            ?: R.drawable.placeholder_local
        val showingPhoto = profile != null && (
            LocalProfilePhotoStore.renderForProfile(context, photoView, profile) ||
                PlayGamesProfileAvatar.render(
                    context = context,
                    image = photoView,
                    uriValue = profile.playGamesAvatarUri,
                    fallbackDrawableRes = fallbackRes
                )
            )
        photoView.visibility = if (showingPhoto) View.VISIBLE else View.GONE
        initialView.visibility = if (showingPhoto) View.GONE else View.VISIBLE
        contentDescription = if (showingPhoto) {
            "Foto de perfil de ${player.name}"
        } else {
            "Avatar de ${player?.name.orEmpty().ifBlank { fallbackInitial }}"
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
