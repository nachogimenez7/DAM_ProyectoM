package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

// Detalle de logro reutilizable (mismo look que la pantalla de perfil). Muestra el
// nombre, "como obtenerlo" (description) y la medalla por rareza. No aplica el progreso
// del tracker del humano, asi que sirve igual para el logro de un bot o de otro jugador.
object AchievementDetailDialog {

    private data class Style(
        val backgroundHex: String,
        val borderHex: String,
        val textHex: String,
        val titleHex: String,
        val dateHex: String
    )

    fun show(activity: Activity, achievement: ProfileAchievement) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_achievement_detail, null)
        val style = styleFor(achievement.rarity)

        content.findViewById<View>(R.id.achievementDetailPanel).background =
            detailBackground(activity, style)
        content.findViewById<View>(R.id.achievementGlow).alpha =
            if (achievement.rarity == AchievementRarity.GOLD) 1f else 0f
        content.findViewById<TextView>(R.id.achievementDetailTitle).apply {
            text = achievement.name
            setTextColor(Color.parseColor(style.titleHex))
            typeface = if (achievement.rarity == AchievementRarity.GOLD) {
                Typeface.SERIF
            } else {
                Typeface.DEFAULT_BOLD
            }
        }
        content.findViewById<TextView>(R.id.achievementDetailDescription).apply {
            text = achievement.description
            setTextColor(Color.parseColor(style.textHex))
        }
        content.findViewById<TextView>(R.id.achievementDetailDate).apply {
            val pending = achievement.obtainedDate.isBlank() ||
                achievement.obtainedDate.equals("Pendiente", ignoreCase = true)
            if (pending) {
                visibility = View.GONE
            } else {
                text = "Obtenido el ${achievement.obtainedDate}"
                setTextColor(Color.parseColor(style.dateHex))
            }
        }
        content.findViewById<View>(R.id.achievementMedalFrame).background =
            medalFrameBackground(activity, style)
        content.findViewById<ImageView>(R.id.achievementMedal)
            .setImageResource(achievement.rarity.medalRes)

        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = (activity.resources.displayMetrics.widthPixels - dp(activity, 24))
                    .coerceAtLeast(dp(activity, 260))
                setLayout(dp(activity, 420).coerceAtMost(maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    private fun detailBackground(activity: Activity, style: Style): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(activity, 16).toFloat()
            setColor(Color.parseColor(style.backgroundHex))
            setStroke(dp(activity, 2), Color.parseColor(style.borderHex))
        }
    }

    private fun medalFrameBackground(activity: Activity, style: Style): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC2A2318"))
            setStroke(dp(activity, 2), Color.parseColor(style.borderHex))
        }
    }

    private fun styleFor(rarity: AchievementRarity): Style {
        return when (rarity) {
            AchievementRarity.BRONZE -> Style("#E6332017", "#F0A35A", "#FFE2BC", "#FFB56E", "#EBC49A")
            AchievementRarity.SILVER -> Style("#E6202830", "#DCEAFF", "#F4F8FF", "#E7F1FF", "#C8D4E2")
            AchievementRarity.GOLD -> Style("#F2140F05", "#FFF2A3", "#FFF6CF", "#FFF7B2", "#FFE27A")
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
