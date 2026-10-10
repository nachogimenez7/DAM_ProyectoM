package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat

/** Copy of the usual table's winner ceremony layout and texts (GameplayMockActivity.applyWinnerRevealLayout). */
internal object GameplayWinnerRevealLayout {
    fun title(winner: String): String = when (winner) {
        GameRules.CANCELLED_WINNER -> "PARTIDA CANCELADA"
        GameRules.TOWN_WINNER -> "VICTORIA DEL PUEBLO"
        GameRules.TRAITOR_WINNER -> "VICTORIA DE LOS TRAIDORES"
        else -> "VICTORIA DE ${winner.uppercase()}"
    }

    fun subtitle(winner: String): String = when (winner) {
        GameRules.TOWN_WINNER -> "La plaza vuelve a respirar."
        GameRules.TRAITOR_WINNER -> "Las sombras reclaman el pueblo."
        GameRules.CANCELLED_WINNER -> "La historia quedó sin desenlace."
        else -> "La partida ya tiene un vencedor."
    }

    fun summary(livingWinners: Int, rounds: Int, personalResult: String): String {
        val survivors = if (livingWinners == 1) "1 superviviente" else "$livingWinners supervivientes"
        val roundLabel = if (rounds == 1) "1 ronda" else "$rounds rondas"
        return "$survivors · $roundLabel · $personalResult"
    }

    fun apply(activity: Activity) {
        fun <T : View> view(id: Int): T = activity.findViewById(id)
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val panel = view<View>(R.id.winnerRevealPanel)
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = minOf(activity.resources.displayMetrics.heightPixels - dp(28), dp(700))
            setMargins(dp(14), dp(16), dp(14), dp(16))
            gravity = Gravity.CENTER
        }
        panel.setBackgroundResource(R.drawable.bg_winner_premium_panel)
        view<View>(R.id.winnerRevealBackground).alpha = 1f
        view<TextView>(R.id.winnerRevealTitle).apply {
            setBackgroundResource(android.R.color.transparent)
            setTextColor(Color.parseColor("#FFF0BC"))
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#E6000000"))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 16, 27, 1, TypedValue.COMPLEX_UNIT_SP)
            maxLines = 2
        }
        view<TextView>(R.id.winnerRevealPersonalResult).apply {
            setBackgroundResource(android.R.color.transparent)
            setTextColor(Color.parseColor("#F7E8D0"))
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#F0000000"))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 16, 1, TypedValue.COMPLEX_UNIT_SP)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        view<View>(R.id.winnerSummaryPanel).setBackgroundResource(R.drawable.bg_winner_premium_summary)
        view<View>(R.id.winnerSummaryStatsRow).apply { layoutParams = layoutParams.apply { height = dp(52) } }
        listOf(R.id.winnerSummaryRounds, R.id.winnerSummaryDuration, R.id.winnerSummaryPlayers).forEach { id ->
            view<TextView>(id).apply {
                setBackgroundResource(R.drawable.bg_winner_stat_chip)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2; setSingleLine(false)
            }
        }
        listOf(R.id.winnerSummaryHighlight, R.id.winnerSummaryTimeline).forEach { id ->
            view<TextView>(id).apply {
                setBackgroundResource(R.drawable.bg_winner_summary_text)
                setPadding(dp(12), dp(7), dp(9), dp(7))
                setTextColor(Color.parseColor("#B9AD92"))
            }
        }
        view<TextView>(R.id.winnerSummaryHighlight).setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        view<TextView>(R.id.winnerSummaryTimeline).setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        val back = view<Button>(R.id.btnWinnerReturnLobby)
        val chronicle = view<Button>(R.id.btnWinnerChronicle)
        back.setBackgroundResource(R.drawable.bg_winner_action_primary)
        back.setTextColor(Color.parseColor("#211407"))
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        chronicle.setTextColor(Color.parseColor("#F3D488"))
        chronicle.setBackgroundResource(R.drawable.bg_winner_action_secondary)
        chronicle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        listOf(chronicle, back).forEach { button ->
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0; height = dp(46); weight = 1f
            }
        }
        listOf(R.id.winnerRevealHeading, R.id.winnerRevealTitle, R.id.winnerRevealPersonalResult,
            R.id.winnerRevealCards, R.id.winnerCeremonySummary).forEach { view<View>(it).visibility = View.VISIBLE }
        view<View>(R.id.winnerSummaryPanel).visibility = View.GONE
    }
}
