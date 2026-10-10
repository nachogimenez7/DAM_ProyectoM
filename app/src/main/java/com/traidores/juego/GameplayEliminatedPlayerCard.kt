package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** The usual table's card for an eliminated player with a public role; shared by both tables. */
internal object GameplayEliminatedPlayerCard {
    fun show(activity: Activity, player: GamePlayer, roleImageFor: (GameRole?) -> Int) {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val role = player.role ?: return
        GameplayEffects.play(activity, GameplayEffect.PANEL)

        val statusText = when (player.deathCause) {
            DeathCause.NIGHT -> "ASESINADO DURANTE LA NOCHE"
            DeathCause.VOTE -> "EXPULSADO POR EL PUEBLO"
            DeathCause.AFK -> "EXPULSADO POR INACTIVIDAD"
            DeathCause.NONE -> "JUGADOR ELIMINADO"
        }
        val statusColor = when (player.deathCause) {
            DeathCause.NIGHT -> Color.parseColor("#D56B65")
            DeathCause.VOTE,
            DeathCause.AFK -> Color.parseColor("#D0A45A")
            DeathCause.NONE -> activity.getColor(R.color.text_secondary)
        }
        val deathIcon = when (player.deathCause) {
            DeathCause.NIGHT -> R.drawable.death_blood_splatter_art
            DeathCause.VOTE,
            DeathCause.AFK -> R.drawable.ic_kicking_boot
            DeathCause.NONE -> 0
        }
        val teamColor = when (role.team) {
            GameRules.TRAITOR_WINNER -> Color.parseColor("#C75A54")
            GameRules.TOWN_WINNER -> Color.parseColor("#659B68")
            "Neutral" -> Color.parseColor("#C8A04E")
            else -> activity.getColor(R.color.accent_gold)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(2), dp(8), dp(4))
        }
        content.addView(TextView(activity).apply {
            text = player.name.uppercase()
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.accent_gold))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(3) })
        content.addView(TextView(activity).apply {
            text = statusText
            gravity = Gravity.CENTER
            setTextColor(statusColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val card = FrameLayout(activity).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F01A120D"))
                setStroke(dp(3), statusColor)
                cornerRadius = dp(9).toFloat()
            }
            elevation = dp(5).toFloat()
        }
        card.addView(ImageView(activity).apply {
            setImageResource(roleImageFor(role))
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.82f
            contentDescription = "Carta ${role.name} de ${player.name}"
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (deathIcon != 0) {
            card.addView(ImageView(activity).apply {
                setImageResource(deathIcon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                applyDeathCauseIconStyle(this, player.deathCause)
                elevation = dp(8).toFloat()
                contentDescription = statusText.lowercase()
            }, FrameLayout.LayoutParams(
                dp(if (player.deathCause == DeathCause.NIGHT) 126 else 82),
                dp(if (player.deathCause == DeathCause.NIGHT) 126 else 82),
                if (player.deathCause == DeathCause.NIGHT) {
                    Gravity.CENTER
                } else {
                    Gravity.BOTTOM or Gravity.END
                }
            ).apply {
                rightMargin = dp(3)
                bottomMargin = dp(3)
            })
        }
        content.addView(card, LinearLayout.LayoutParams(dp(174), dp(232)).apply {
            bottomMargin = dp(10)
        })
        content.addView(TextView(activity).apply {
            text = role.name.uppercase()
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        content.addView(TextView(activity).apply {
            text = role.team.uppercase()
            gravity = Gravity.CENTER
            setTextColor(teamColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })

        GameDialog.custom(
            activity = activity,
            contentView = content,
            widthDp = 350,
            negativeLabel = null,
            positiveLabel = "CERRAR"
        )
    }

    private fun applyDeathCauseIconStyle(icon: ImageView, cause: DeathCause) {
        icon.alpha = 1f
        if (cause == DeathCause.VOTE || cause == DeathCause.AFK) {
            icon.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                1.12f, 0f, 0f, 0f, 10f,
                0f, 1.12f, 0f, 0f, 7f,
                0f, 0f, 1.08f, 0f, 3f,
                0f, 0f, 0f, 1f, 0f))
        } else {
            icon.clearColorFilter()
        }
    }
}
