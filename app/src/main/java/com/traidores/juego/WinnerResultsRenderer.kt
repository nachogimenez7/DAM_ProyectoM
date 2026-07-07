package com.traidores.juego

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
        specialWinners: List<GamePlayer>,
        themeKey: String,
        winnerKey: String
    ): List<View> {
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        applyThemeInsets(themeKey)
        val factionAccent = factionAccent(winnerKey)
        val cardViews = renderCards(players, factionAccent)
        val specialCardViews = renderSpecialVictories(specialVictories, specialWinners)
        if (isPortrait) {
            rounds.text = statText(summary.roundsPlayed.toString(), "RONDAS", true)
            duration.text = statText(summary.durationLabel, "TIEMPO", true)
            eliminatedCount.text = statText(summary.eliminated.toString(), "ELIM.", true)
        } else {
            rounds.text = statText(summary.roundsPlayed.toString(), "RONDAS", false)
            duration.text = statText(summary.durationLabel, "TIEMPO", false)
            eliminatedCount.text = statText(summary.eliminated.toString(), "ELIM.", false)
        }
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
        val keyMoments = summary.keyMoments
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- $it" }
        val dayLog = summary.daySummaries
            .joinToString("\n")
            .ifBlank { "Día 1: no murió nadie y nadie fue silenciado." }
        timeline.text = listOfNotNull(
            keyMoments?.let { "MOMENTOS CLAVE\n$it" },
            "BITACORA\n$dayLog"
        ).joinToString("\n\n")
        return cardViews + specialCardViews
    }

    private fun statText(value: String, label: String, stacked: Boolean): SpannableString {
        val text = if (stacked) "$value\n$label" else "$value $label"
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(Color.parseColor("#F3D488")),
                0,
                value.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                value.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val labelStart = text.indexOf(label)
            setSpan(
                ForegroundColorSpan(Color.parseColor("#B9AD92")),
                labelStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(if (stacked) 0.78f else 0.82f),
                labelStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun renderSpecialVictories(
        specialVictories: List<GameSpecialVictory>,
        specialWinners: List<GamePlayer>
    ): List<View> {
        if (specialVictories.isEmpty() || specialWinners.isEmpty()) return emptyList()

        val accent = context.getColor(R.color.special_victory_accent)
        val headerText = specialVictories.joinToString(" / ") { victory ->
            "VICTORIA ESPECIAL - ${victory.roleKey.uppercase()}"
        }
        cards.addView(
            sectionHeader(headerText, accent),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        )

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(context.getColor(R.color.special_victory_bg))
                setStroke(dp(1), context.getColor(R.color.special_victory_border))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }
        cards.addView(
            box,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        )
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        box.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val victoriesByPlayer = specialVictories.associateBy { it.playerName }
        return specialWinners.map { player ->
            createSpecialCard(
                player = player,
                winnerCount = specialWinners.size,
                isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                victory = victoriesByPlayer[player.name]
            )
                .also { row.addView(it) }
        }
    }

    private fun renderCards(players: List<GamePlayer>, borderColor: Int): List<View> {
        cards.removeAllViews()
        if (players.isEmpty()) return emptyList()

        val cardViews = mutableListOf<View>()
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        cards.addView(
            sectionHeader("BANDO GANADOR", borderColor),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
            ).apply {
                bottomMargin = dp(3)
            }
        )
        val rowCount = if (isPortrait) {
            when (players.size) {
                in 1..2 -> 1
                in 3..4 -> 2
                in 5..8 -> 3
                in 9..12 -> 4
                else -> 5
            }
        } else {
            when (players.size) {
                in 1..6 -> 1
                in 7..10 -> 2
                else -> 3
            }
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
                ).apply {
                    bottomMargin = dp(if (isPortrait) 5 else 0)
                }
            )
            rowPlayers.forEach { player ->
                createCard(player, players.size, isPortrait, borderColor).also {
                    cardViews += it
                    row.addView(it)
                }
            }
        }
        return cardViews
    }

    private fun applyThemeInsets(themeKey: String) {
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val top = when (themeKey) {
            "medieval" -> if (isPortrait) 20 else 20
            "griego" -> if (isPortrait) 20 else 12
            else -> if (isPortrait) 20 else 8
        }
        val horizontal = if (isPortrait) {
            when (themeKey) {
                "medieval" -> 22
                "griego" -> 22
                else -> 22
            }
        } else {
            when (themeKey) {
                "medieval" -> 54
                "griego" -> 48
                else -> 42
            }
        }
        val bottom = if (isPortrait) 10 else if (themeKey == "medieval") 12 else 8
        content.setPadding(dp(horizontal), dp(top), dp(horizontal), dp(bottom))
    }

    private fun sectionHeader(text: String, color: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val lineBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            alpha = 190
        }
        row.addView(View(context).apply { background = lineBackground }, LinearLayout.LayoutParams(
            0,
            dp(1),
            1f
        ).apply {
            marginStart = dp(6)
            marginEnd = dp(8)
        })
        row.addView(TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            maxLines = 1
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        row.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                alpha = 190
            }
        }, LinearLayout.LayoutParams(
            0,
            dp(1),
            1f
        ).apply {
            marginStart = dp(8)
            marginEnd = dp(6)
        })
        return row
    }

    private fun createCard(
        player: GamePlayer,
        winnerCount: Int,
        isPortrait: Boolean,
        borderColor: Int,
        forceFullColor: Boolean = false
    ): View {
        val metrics = if (isPortrait) {
            when {
                winnerCount == 1 -> intArrayOf(156, 94, 114, 18, 13, 24, 19)
                winnerCount == 2 -> intArrayOf(142, 86, 104, 16, 12, 22, 18)
                winnerCount <= 4 -> intArrayOf(124, 74, 90, 14, 10, 20, 15)
                winnerCount <= 8 -> intArrayOf(104, 62, 74, 12, 9, 17, 13)
                winnerCount <= 12 -> intArrayOf(86, 50, 62, 10, 8, 14, 12)
                else -> intArrayOf(76, 44, 54, 9, 7, 13, 11)
            }
        } else {
            when {
                winnerCount == 1 -> intArrayOf(136, 80, 96, 16, 12, 21, 18)
                winnerCount == 2 -> intArrayOf(126, 76, 92, 15, 11, 20, 18)
                winnerCount <= 5 -> intArrayOf(106, 64, 77, 13, 10, 20, 18)
                winnerCount <= 10 -> intArrayOf(94, 58, 72, 10, 8, 15, 12)
                else -> intArrayOf(76, 42, 52, 9, 7, 13, 11)
            }
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
                    if (player.alive || forceFullColor) borderColor else Color.parseColor("#75695B")
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
            if (!player.alive && !forceFullColor) {
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
            textColor = "#FFF0C7",
            textSize = metrics[3],
            font = null,
            height = metrics[5]
        )
        container.addView(playerName)

        val roleLabel = resultLabel(
            text = player.role?.name?.uppercase() ?: "SIN ROL",
            textColor = "#F3D488",
            textSize = metrics[4],
            font = null,
            height = metrics[6]
        )
        container.addView(roleLabel)
        return container
    }

    private fun createSpecialCard(
        player: GamePlayer,
        winnerCount: Int,
        isPortrait: Boolean,
        victory: GameSpecialVictory?
    ): View {
        val accent = context.getColor(R.color.special_victory_accent)
        val card = createCard(player, winnerCount, isPortrait, accent, forceFullColor = true) as LinearLayout
        val roleLabel = victory?.roleKey?.uppercase() ?: player.role?.name?.uppercase() ?: "ESPECIAL"
        val existingRole = card.getChildAt(2) as? TextView
        existingRole?.text = roleLabel
        val reason = TextView(context).apply {
            text = when (victory?.roleKey) {
                RoleCatalog.BUFON -> "Engano al pueblo y gano al ser expulsado."
                else -> "Consiguio una victoria especial durante la partida."
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.parseColor("#D8C9F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isPortrait) 9f else 8.5f)
            maxLines = 2
        }
        card.addView(reason, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(if (isPortrait) 28 else 24)
        ))
        return card
    }

    private fun factionAccent(winnerKey: String): Int {
        return when (winnerKey) {
            GameRules.TOWN_WINNER -> context.getColor(R.color.winner_town_accent)
            GameRules.TRAITOR_WINNER -> context.getColor(R.color.accent_red)
            else -> context.getColor(R.color.accent_gold)
        }
    }

    private fun resultLabel(
        text: String,
        textColor: String,
        textSize: Int,
        font: Int?,
        height: Int
    ): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        maxLines = 1
        setSingleLine(true)
        setTextColor(Color.parseColor(textColor))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
        typeface = font?.let { ResourcesCompat.getFont(context, it) } ?: Typeface.DEFAULT_BOLD
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
