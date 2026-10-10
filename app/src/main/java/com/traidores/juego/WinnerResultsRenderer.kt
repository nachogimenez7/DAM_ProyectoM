package com.traidores.juego

import android.content.Context
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
    private val roleImageFor: (GameRole?) -> Int,
    private val cosmeticThemeFor: (String) -> String = { CosmeticPilot.THEME_CLASSIC },
    private val sessionProvider: () -> GameSession
) {
    /** Festejos of the cards from the last [render]; the activity plays them once the ceremony is on screen. */
    internal var festejos: List<FestejoTarget> = emptyList()
        private set
    private var festejoCards = mutableListOf<FestejoTarget>()
    private var festejosEnabled = true
    private var festejoCardIndex = 0

    fun render(
        players: List<GamePlayer>,
        summary: GameSummaryPresentation,
        specialVictories: List<GameSpecialVictory>,
        specialWinners: List<GamePlayer>,
        winnerKey: String,
        losingPlayers: List<GamePlayer>
    ): List<View> {
        applyThemeInsets()
        val factionAccent = factionAccent(winnerKey)
        val winnerNames = (players + specialWinners).map { it.name }.toSet()
        val allWinners = sessionProvider().players.filter { it.name in winnerNames }
        cards.removeAllViews()
        festejoCards = mutableListOf()
        festejoCardIndex = 0
        festejosEnabled = winnerKey != GameRules.CANCELLED_WINNER
        val cardViews = renderCards(allWinners, factionAccent, "EQUIPO GANADOR", won = true)
        val losingCardViews = renderCards(losingPlayers, Color.parseColor("#A89A82"), "EQUIPO PERDEDOR", compact = true, won = false)
        festejos = festejoCards
        if (festejos.isNotEmpty()) {
            // Stickers stick out over the card corner and cards jump: nothing in between may clip them.
            fun unclip(v: View) {
                (v as? android.view.ViewGroup)?.let { g -> g.clipChildren = false; g.clipToPadding = false; for (i in 0 until g.childCount) unclip(g.getChildAt(i)) }
            }
            unclip(cards)
            var up = cards.parent
            repeat(2) { (up as? android.view.ViewGroup)?.let { it.clipChildren = false; it.clipToPadding = false }; up = up?.parent }
        }
        rounds.text = statText(summary.roundsPlayed.toString(), "RONDAS")
        duration.text = statText(summary.durationLabel, "TIEMPO")
        eliminatedCount.text = statText(summary.eliminated.toString(), "ELIM.")
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
            "RONDA POR RONDA\n$dayLog"
        ).joinToString("\n\n")
        return cardViews + losingCardViews
    }

    private fun statText(value: String, label: String): SpannableString {
        val text = "$value\n$label"
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
                RelativeSizeSpan(0.78f),
                labelStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun renderCards(players: List<GamePlayer>, borderColor: Int, title: String, compact: Boolean = false, won: Boolean = true): List<View> {
        if (players.isEmpty()) return emptyList()

        val cardViews = mutableListOf<View>()
        cards.addView(
            sectionHeader(title, borderColor),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
            ).apply {
                if (compact) topMargin = dp(10)
                bottomMargin = dp(3)
            }
        )
        val rowCount = when (players.size) {
            in 1..2 -> 1
            in 3..4 -> 2
            in 5..8 -> 3
            in 9..12 -> 4
            else -> 5
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
                    bottomMargin = dp(5)
                }
            )
            rowPlayers.forEach { player ->
                createCard(player, if (compact) maxOf(players.size, 5) else players.size, borderColor, won = won).also {
                    cardViews += it
                    row.addView(it)
                }
            }
        }
        return cardViews
    }

    private fun applyThemeInsets() {
        content.setPadding(dp(22), dp(45), dp(22), dp(18))
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
        borderColor: Int,
        forceFullColor: Boolean = false,
        won: Boolean = true
    ): View {
        val metrics = when {
            winnerCount == 1 -> intArrayOf(176, 112, 150, 18, 13, 24, 19)
            winnerCount == 2 -> intArrayOf(152, 96, 128, 16, 12, 22, 18)
            winnerCount <= 4 -> intArrayOf(132, 84, 112, 14, 10, 20, 15)
            winnerCount <= 8 -> intArrayOf(108, 68, 91, 12, 9, 17, 13)
            winnerCount <= 12 -> intArrayOf(90, 56, 75, 10, 8, 14, 12)
            else -> intArrayOf(78, 48, 64, 9, 7, 13, 11)
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
        addFestejo(player, cardFrame, metrics[1], won)
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
            text = GameplayTableUi.winnerRoleLabel(sessionProvider(), player),
            textColor = "#F3D488",
            textSize = metrics[4],
            font = null,
            height = metrics[6]
        )
        container.addView(roleLabel)
        container.addView(GameplayAvatarView(context).apply {
            bind(sessionProvider(), player, player.name.take(1), 12f)
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { topMargin = dp(4) })
        return container
    }

    /** Puts the sticker of the player's equipped style on the card (nothing for Clásico). */
    private fun addFestejo(player: GamePlayer, cardFrame: FrameLayout, cardWidthDp: Int, wonRaw: Boolean) {
        if (!festejosEnabled) return
        val won = wonRaw && !VictoryFestejo.forcedLose(context)
        val style = VictoryFestejo.styleFor(context, cosmeticThemeFor(player.name), festejoCardIndex++)
        val drawable = VictoryFestejo.drawableFor(style, won) ?: return
        val kind = VictoryFestejo.kindFor(style, won) ?: return
        val particles = FestejoParticlesView(context, kind)
        cardFrame.addView(particles, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val size = minOf(78, cardWidthDp - 6)
        val overflow = dp(size / 4)
        val sticker = ImageView(context).apply {
            setImageResource(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        cardFrame.addView(sticker, FrameLayout.LayoutParams(dp(size), dp(size)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = -overflow
            bottomMargin = -overflow / 2
        })
        cardFrame.clipChildren = false
        sticker.rotation = FestejoPlayer.STICKER_TILT
        festejoCards += FestejoTarget(sticker, cardFrame, particles, kind)
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
        // With 13+ cards the size is 7sp or less: autosize needs a max strictly above its min of 7sp.
        if (textSize > 7) {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 7, textSize, 1, TypedValue.COMPLEX_UNIT_SP)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
