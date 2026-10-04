package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Only receives the identity confirmed by the current access check, never another account's local photo. */
internal class OnlineIdentityCardView(context: Context) : LinearLayout(context) {
    private val gold = context.getColor(R.color.accent_gold)
    private var identity: Identity? = null
    data class Identity(val uid: String, val guest: Boolean, val name: String, val publicId: String,
                        val avatar: String, val publishedPhoto: String, val bio: String = "", val banner: String = "")

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = shape(Color.rgb(26, 21, 16), 14f, gold and 0x00ffffff or (140 shl 24))
        visibility = GONE
    }

    fun bind(value: Identity) { identity = value; render() }
    fun refreshPhotoStatus() { if (visibility == VISIBLE) render() }

    private fun render() {
        val value = identity ?: return
        removeAllViews()
        if (!value.guest) addView(android.widget.ImageView(context).apply {
            setImageResource(ProfileCustomizationCatalog.banner(value.banner).drawableRes)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = shape(Color.TRANSPARENT, 8f, Color.TRANSPARENT)
            clipToOutline = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        val largeText = resources.configuration.fontScale >= 1.5f
        val row = LinearLayout(context).apply {
            orientation = if (largeText) VERTICAL else HORIZONTAL
            gravity = if (largeText) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = if (value.guest) "${value.name}, invitado" else
                "${value.name}, cuenta" + value.publicId.takeIf(String::isNotBlank)?.let { " número $it" }.orEmpty()
        }
        val portrait = CircleProfileImageView(context).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        ProfilePortraitRenderer.render(context, portrait, value.avatar, value.publishedPhoto)
        val frame = FrameLayout(context).apply {
            background = shape(Color.TRANSPARENT, 29f, gold, 2)
            addView(portrait, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
        }
        row.addView(frame, LayoutParams(dp(58), dp(58)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(label(value.name, 21f, context.getColor(R.color.text_primary)).apply {
                typeface = ResourcesCompat.getFont(context, R.font.bree_serif)
                if (largeText) gravity = Gravity.CENTER
            })
            addView(LinearLayout(context).apply {
                gravity = if (largeText) Gravity.CENTER else Gravity.CENTER_VERTICAL
                addView(label(if (value.guest) "INVITADO" else "CUENTA", 12f,
                    if (value.guest) gold else Color.rgb(26, 21, 16)).apply {
                    typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    background = shape(if (value.guest) Color.TRANSPARENT else gold, 20f, gold)
                })
                if (value.publicId.isNotBlank()) addView(label("#${value.publicId}", 14f, gold),
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        row.addView(texts, if (largeText) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        } else LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
        addView(row)
        val bio = value.bio.trim()
        if (!value.guest && bio.isNotEmpty()) {
            addView(label(bio, 13f, context.getColor(R.color.text_secondary)).apply {
                gravity = if (largeText) Gravity.CENTER else Gravity.START
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        if (value.guest) {
            addView(View(context).apply { setBackgroundColor(gold and 0x00ffffff or (55 shl 24)) },
                LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(10) })
            addView(label("Con una cuenta elegís tu nombre, creás salas y tenés tu número.", 13f,
                context.getColor(R.color.text_secondary)).apply { gravity = Gravity.CENTER })
            addView(action("CREAR CUENTA O ENTRAR") {
                context.startActivity(Intent(context, ProfileActivity::class.java).putExtra(ProfileActivity.EXTRA_OPEN_ACCOUNT, true))
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        } else when (ProfilePhotoStorage.publicationStatus(context)) {
            ProfilePhotoStorage.PublicationStatus.UPLOADING -> addView(label("Publicando tu foto…", 13f,
                context.getColor(R.color.text_secondary)))
            ProfilePhotoStorage.PublicationStatus.FAILED -> {
                addView(label("No se pudo publicar tu foto.", 13f, context.getColor(R.color.text_secondary)))
                addView(action("REINTENTAR") { ProfilePhotoStorage.sync(context) })
            }
            ProfilePhotoStorage.PublicationStatus.IDLE -> Unit
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun action(value: String, click: () -> Unit) = Button(context).apply {
        text = value; textSize = 13f; setTextColor(gold); minimumHeight = dp(48)
        background = shape(Color.TRANSPARENT, 10f, gold)
        setOnClickListener { click() }
    }
    private fun shape(fill: Int, radius: Float, stroke: Int, width: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius.toInt()).toFloat(); setStroke(dp(width), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
