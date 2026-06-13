package com.traidores.juego

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import kotlin.math.ceil

class WinnerResultsRenderer(
    private val context: Context,
    private val content: LinearLayout,
    private val cards: LinearLayout,
    private val rounds: TextView,
    private val duration: TextView,
    private val eliminatedCount: TextView,
    private val eliminatedPlayers: TextView,
    private val timeline: TextView,
    private val roleImageFor: (GameRole?) -> Int
) {
    fun render(
        players: List<GamePlayer>,
        summary: GameSummaryPresentation,
        specialVictories: List<GameSpecialVictory>,
        themeKey: String
    ): List<View> {
        applyThemeInsets(themeKey)
        val cardViews = renderCards(players)
        rounds.text = "${summary.roundsPlayed} RONDAS"
        duration.text = "TIEMPO ${summary.durationLabel}"
        eliminatedCount.text = "${summary.eliminated} ELIMINADOS"
        val eliminatedLabel = if (summary.eliminatedPlayers.isEmpty()) {
            "ELIMINADOS: NINGUNO"
        } else {
            "ELIMINADOS: ${summary.eliminatedPlayers.joinToString(", ")}"
        }
        val specialVictoriesLabel = specialVictories
            .joinToString(", ") { victory ->
                "${victory.playerName} (${victory.roleKey.uppercase()})"
            }
            .takeIf { it.isNotBlank() }
            ?.let { "VICTORIAS ESPECIALES: $it" }
        eliminatedPlayers.text = listOfNotNull(
            eliminatedLabel,
            specialVictoriesLabel
        ).joinToString("\n")
        timeline.text = summary.daySummaries
            .joinToString("\n")
            .ifBlank { "Dia 1: no murio nadie y nadie fue silenciado." }
        return cardViews
    }

    private fun renderCards(players: List<GamePlayer>): List<View> {
        cards.removeAllViews()
        if (players.isEmpty()) return emptyList()

        val cardViews = mutableListOf<View>()
        val rowCount = when (players.size) {
            in 1..6 -> 1
            in 7..10 -> 2
            else -> 3
        }
        val playersPerRow = ceil(players.size / rowCount.toDouble()).toInt()
        players.chunked(playersPerRow).forEach { rowPlayers ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            cards.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            rowPlayers.forEach { player ->
                createCard(player, players.size).also {
                    cardViews += it
                    row.addView(it)
                }
            }
        }
        return cardViews
    }

    private fun applyThemeInsets(themeKey: String) {
        val top = when (themeKey) {
            "medieval" -> 20
            "griego" -> 12
            else -> 8
        }
        val horizontal = when (themeKey) {
            "medieval" -> 54
            "griego" -> 48
            else -> 42
        }
        val bottom = if (themeKey == "medieval") 12 else 8
        content.setPadding(dp(horizontal), dp(top), dp(horizontal), dp(bottom))
    }

    private fun createCard(player: GamePlayer, winnerCount: Int): View {
        val metrics = when {
            winnerCount == 1 -> intArrayOf(136, 80, 96, 16, 12, 21, 18)
            winnerCount == 2 -> intArrayOf(126, 76, 92, 15, 11, 20, 18)
            winnerCount <= 5 -> intArrayOf(106, 64, 77, 13, 10, 20, 18)
            winnerCount <= 10 -> intArrayOf(94, 58, 72, 10, 8, 15, 12)
            else -> intArrayOf(76, 42, 52, 9, 7, 13, 11)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(metrics[0]), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val cardFrame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E6231810"))
                setStroke(
                    dp(if (player.alive) 3 else 2),
                    Color.parseColor(if (player.alive) "#D4A24E" else "#75695B")
                )
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            if (player.alive) elevation = dp(4).toFloat()
        }
        val image = ImageView(context).apply {
            setImageResource(roleImageFor(player.role))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Rol de ${player.name}"
            if (!player.alive) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
        }
        cardFrame.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(cardFrame, LinearLayout.LayoutParams(dp(metrics[1]), dp(metrics[2])))

        val playerName = resultLabel(
            text = player.name,
            textColor = "#352114",
            textSize = metrics[3],
            font = R.font.grenze,
            height = metrics[5]
        )
        container.addView(playerName)

        val roleLabel = resultLabel(
            text = player.role?.name?.uppercase() ?: "SIN ROL",
            textColor = "#4F321A",
            textSize = metrics[4],
            font = R.font.cormorant_garamond,
            height = metrics[6]
        )
        container.addView(roleLabel)
        return container
    }

    private fun resultLabel(
        text: String,
        textColor: String,
        textSize: Int,
        font: Int,
        height: Int
    ): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        maxLines = 1
        setSingleLine(true)
        setTextColor(Color.parseColor(textColor))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
        typeface = ResourcesCompat.getFont(context, font)
        setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(height)
        )
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this,
            7,
            textSize.coerceAtLeast(7),
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
