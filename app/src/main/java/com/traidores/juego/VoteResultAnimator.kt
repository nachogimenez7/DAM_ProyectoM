package com.traidores.juego

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.random.Random

class VoteResultAnimator(
    private val context: Context,
    private val handler: Handler,
    private val overlay: FrameLayout,
    private val panel: LinearLayout,
    private val cards: LinearLayout,
    private val scroll: HorizontalScrollView,
    private val title: TextView,
    private val subtitle: TextView,
    private val notice: TextView,
    private val continueButton: Button,
    private val boot: ImageView,
    private val roleImageFor: (GameRole?) -> Int,
    private val dp: (Int) -> Int,
    private val onContinueReady: (() -> Unit)? = null
) {
    private companion object {
        const val RECOUNT_INITIAL_DELAY_MS = 420L
        const val RECOUNT_TOKEN_STEP_MS = 420L
        const val RECOUNT_FINAL_READ_MS = 700L
        const val EXPULSION_NAME_READ_MS = 2_500L
        const val REVEALED_CARD_READ_MS = 3_500L
        const val BOOT_IMPACT_PAUSE_MS = 120L
        const val EXPULSION_AFTER_KICK_READ_MS = 1_800L
        const val RECOUNT_PANEL_WIDTH_DP = 500
        const val EXPULSION_PANEL_WIDTH_DP = 520
        const val RECOUNT_SCROLL_HEIGHT_DP = 170
        const val EXPULSION_SCROLL_HEIGHT_DP = 158
    }

    private data class VoteToken(
        val voterName: String,
        val targetName: String,
        val initial: String
    )

    private data class VoteCardHolder(
        val root: LinearLayout,
        val avatar: TextView,
        val roleImage: ImageView,
        val total: TextView,
        val voterTokens: GridLayout,
        var count: Int = 0
    )

    private val scheduled = mutableListOf<Runnable>()
    private val cardHolders = linkedMapOf<String, VoteCardHolder>()

    fun show(session: GameSession) {
        cancelAnimations()
        applyPanelMode(expulsion = false)
        cardHolders.clear()
        cards.removeAllViews()
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        panel.alpha = 0f
        panel.scaleX = 0.96f
        panel.scaleY = 0.96f
        boot.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE
        continueButton.isEnabled = false
        continueButton.alpha = 0f
        val tiedRecount = session.tieVoteCandidates.size > 1
        title.text = when {
            tiedRecount && session.voteRound == 1 -> "EMPATE"
            session.voteRound == 2 -> "RECUENTO FINAL"
            session.voteRound == 3 -> "DECISION DEL ALCALDE"
            session.voteRound == 4 -> "CORRUPCION EN EL PUEBLO"
            else -> "RECUENTO DE VOTOS"
        }
        subtitle.text = if (tiedRecount && session.voteRound == 1) {
            "Cada sello muestra quien emitio el voto."
        } else if (session.voteRound == 4) {
            "El poder inclino la balanza."
        } else if (session.voteRound == 3) {
            "El Alcalde rompio el empate."
        } else if (session.showIndividualVotes) {
            "Cada sello muestra quien emitio el voto."
        } else {
            "La identidad de los votantes permanece oculta."
        }
        notice.text = ""

        val candidateNames = session.players
            .map { it.name }
            .filter { candidate ->
                session.votes.values.any { it == candidate } ||
                    session.contrapuntoSuspicion == candidate ||
                    (session.voteRound == 3 && session.dayEliminationTarget == candidate)
            }
        if (candidateNames.isEmpty()) {
            cards.addView(emptyVoteMessage())
        } else {
            candidateNames.forEach { candidate ->
                val player = session.players.first { it.name == candidate }
                val holder = createVoteCard(player)
                cardHolders[candidate] = holder
                cards.addView(
                    holder.root,
                    LinearLayout.LayoutParams(dp(136), dp(168)).apply {
                        marginStart = dp(9)
                        marginEnd = dp(9)
                    }
                )
            }
        }
        setCardsViewportWidth(fill = candidateNames.size <= 2)

        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .start()

        val tokens = voteTokens(session).shuffled(
            Random(session.round * 1009 + session.voteRound * 97 + session.votes.size)
        )
        if (tokens.isEmpty()) {
            finishRecount(session)
            return
        }
        tokens.forEachIndexed { index, token ->
            schedule(index * RECOUNT_TOKEN_STEP_MS + RECOUNT_INITIAL_DELAY_MS) {
                addVoteToken(session, token)
                if (index == tokens.lastIndex) {
                    schedule(RECOUNT_FINAL_READ_MS) { finishRecount(session) }
                }
            }
        }
    }

    fun showNoExpulsion() {
        cancelScheduled()
        title.text = "EL PUEBLO NO LLEGO A UN ACUERDO"
        subtitle.text = "Nadie sera expulsado esta jornada."
        notice.text = "La noche volvera a caer sobre el pueblo."
        setContinueReady("CONTINUAR")
    }

    fun playExpulsion(session: GameSession, onFinished: () -> Unit) {
        cancelScheduled()
        applyPanelMode(expulsion = true)
        val targetName = session.dayEliminationTarget
        val targetPlayer = session.players.firstOrNull { it.name == targetName }
        if (targetPlayer == null) {
            title.text = "$targetName FUE EXPULSADO"
            notice.text = "El pueblo dicto su sentencia."
            setContinueReady("CONTINUAR")
            onFinished()
            return
        }

        continueButton.animate().cancel()
        continueButton.visibility = View.INVISIBLE
        continueButton.isEnabled = false
        continueButton.alpha = 0f
        title.text = if (session.alcaldeCorruption) {
            "CORRUPCION EN EL PUEBLO"
        } else {
            "EXPULSION"
        }
        subtitle.text = if (session.alcaldeCorruption) {
            "El poder ha torcido la decision del pueblo."
        } else {
            "El pueblo ha tomado su decision."
        }
        notice.text = if (session.alcaldeCorruption) {
            "$targetName sera expulsado en lugar del Alcalde."
        } else if (session.revealRolesOnDeath) {
            "$targetName sera expulsado. Su carta se revelara primero."
        } else {
            "$targetName sera expulsado del pueblo."
        }
        boot.visibility = View.INVISIBLE
        cards.removeAllViews()
        cardHolders.clear()
        scroll.scrollTo(0, 0)
        setCardsViewportWidth(fill = true)

        val holder = createExpulsionCard(targetPlayer)
        cardHolders[targetName] = holder
        cards.addView(
            holder.root,
            LinearLayout.LayoutParams(dp(156), dp(154)).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
        )
        holder.root.alpha = 0f
        holder.root.scaleX = 0.86f
        holder.root.scaleY = 0.86f
        holder.root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .withEndAction {
                if (session.revealRolesOnDeath) {
                    revealExpelledRoleBeforeKick(session, holder) {
                        schedule(REVEALED_CARD_READ_MS) {
                            kickExpulsionCard(session, holder, onFinished)
                        }
                    }
                } else {
                    schedule(EXPULSION_NAME_READ_MS) {
                        kickExpulsionCard(session, holder, onFinished)
                    }
                }
            }
            .start()
    }

    fun hide() {
        cancelAnimations()
        applyPanelMode(expulsion = false)
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        continueButton.isEnabled = false
    }

    fun cancelAnimations() {
        cancelScheduled()
        panel.animate().cancel()
        continueButton.animate().cancel()
        boot.animate().cancel()
        cardHolders.values.forEach { holder ->
            holder.root.animate().cancel()
            holder.root.alpha = 1f
            holder.root.translationX = 0f
            holder.root.rotation = 0f
            holder.root.scaleX = 1f
            holder.root.scaleY = 1f
        }
    }

    private fun revealExpelledRoleBeforeKick(
        session: GameSession,
        holder: VoteCardHolder,
        onRevealed: () -> Unit
    ) {
        val player = session.players.firstOrNull { it.name == session.dayEliminationTarget }
        title.text = "CARTA REVELADA"
        subtitle.text = "${player?.name.orEmpty()} era ${player?.role?.name ?: "DESCONOCIDO"}."
        notice.text = "La identidad queda expuesta ante todo el pueblo."
        holder.total.text = player?.role?.name ?: "DESCONOCIDO"
        holder.roleImage.setImageResource(roleImageFor(player?.role))
        holder.roleImage.alpha = 0f
        holder.roleImage.scaleX = 0.82f
        holder.roleImage.scaleY = 0.82f
        holder.roleImage.visibility = View.VISIBLE
        holder.avatar.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                holder.avatar.visibility = View.GONE
                holder.roleImage.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260L)
                    .withEndAction(onRevealed)
                    .start()
            }
            .start()
    }

    private fun applyPanelMode(expulsion: Boolean) {
        panel.layoutParams = panel.layoutParams.apply {
            width = if (expulsion) {
                dp(EXPULSION_PANEL_WIDTH_DP)
            } else {
                dp(RECOUNT_PANEL_WIDTH_DP)
            }
        }
        scroll.layoutParams = scroll.layoutParams.apply {
            height = dp(if (expulsion) EXPULSION_SCROLL_HEIGHT_DP else RECOUNT_SCROLL_HEIGHT_DP)
        }
        cards.layoutParams = cards.layoutParams.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        cards.gravity = Gravity.CENTER
    }

    private fun setCardsViewportWidth(fill: Boolean) {
        cards.layoutParams = cards.layoutParams.apply {
            width = if (fill) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        cards.gravity = Gravity.CENTER
    }

    private fun kickExpulsionCard(
        session: GameSession,
        holder: VoteCardHolder,
        onFinished: () -> Unit
    ) {
        val targetName = session.dayEliminationTarget
        title.text = if (session.alcaldeCorruption) {
            "CORRUPCION EN EL PUEBLO"
        } else {
            "SENTENCIA DEL PUEBLO"
        }
        subtitle.text = if (session.revealRolesOnDeath) {
            "$targetName fue revelado y expulsado."
        } else {
            "La carta de $targetName permanece oculta."
        }
        notice.text = "La sentencia esta por cumplirse."
        boot.visibility = View.VISIBLE
        boot.alpha = 1f
        boot.translationX = overlay.width.toFloat()
        boot.rotation = -14f
        boot.scaleX = 1.08f
        boot.scaleY = 1.08f

        val overlayLocation = IntArray(2)
        val holderLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        holder.root.getLocationOnScreen(holderLocation)
        val targetCenter = holderLocation[0] - overlayLocation[0] + holder.root.width / 2f
        val bootTravel = targetCenter - overlay.width - dp(44)
        boot.animate()
            .translationX(bootTravel)
            .rotation(-4f)
            .scaleX(1.16f)
            .scaleY(1.16f)
            .setDuration(360L)
            .withEndAction {
                schedule(BOOT_IMPACT_PAUSE_MS) {
                    holder.root.animate()
                        .translationX(-overlay.width.toFloat())
                        .rotation(46f)
                        .alpha(0f)
                        .setDuration(420L)
                        .start()
                    boot.animate()
                        .translationX(bootTravel + dp(120))
                        .rotation(-22f)
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(260L)
                        .withEndAction {
                            boot.visibility = View.INVISIBLE
                            title.text = "$targetName FUE EXPULSADO"
                            subtitle.text = if (session.revealRolesOnDeath) {
                                "Su carta ya fue revelada."
                            } else {
                                "Su carta permanece oculta."
                            }
                            notice.text = if (session.alcaldeCorruption) {
                                "El poder deja su marca en la jornada."
                            } else {
                                "El pueblo continua con la partida."
                            }
                            schedule(EXPULSION_AFTER_KICK_READ_MS) {
                                setContinueReady("CONTINUAR")
                                onFinished()
                            }
                        }
                        .start()
                }
            }
            .start()
    }

    private fun finishRecount(session: GameSession) {
        val tied = session.tieVoteCandidates.size > 1
        when {
            tied && session.voteRound == 1 -> {
                title.text = "EMPATE"
                notice.text = "SI EL EMPATE SE REPITE, NADIE SERA EXPULSADO."
                setContinueReady("IR AL DESEMPATE")
            }
            tied -> {
                title.text = "EL EMPATE SE REPITIO"
                notice.text = "El Alcalde podra intervenir. Sin su decision, nadie sera expulsado."
                setContinueReady("RESOLVER EMPATE")
            }
            session.dayEliminationTarget.isNotBlank() -> {
                title.text = if (session.voteRound == 3) {
                    "DECISION TOMADA"
                } else if (session.voteRound == 4) {
                    "AUTORIDAD IMPUESTA"
                } else {
                    "MAYORIA ALCANZADA"
                }
                notice.text = if (session.voteRound == 4) {
                    "El Alcalde evito su expulsion. ${session.dayEliminationTarget} pagara el precio."
                } else if (session.voteRound == 3) {
                    "El Alcalde eligio expulsar a ${session.dayEliminationTarget}."
                } else {
                    "${session.dayEliminationTarget} recibio la mayor cantidad de votos."
                }
                setContinueReady("VER EXPULSION")
            }
            else -> {
                title.text = "SIN MAYORIA"
                notice.text = "El pueblo no alcanzo una decision."
                setContinueReady("CONTINUAR")
            }
        }
    }

    private fun setContinueReady(label: String) {
        continueButton.text = label
        continueButton.visibility = View.VISIBLE
        continueButton.isEnabled = true
        continueButton.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()
        onContinueReady?.invoke()
    }

    private fun voteTokens(session: GameSession): List<VoteToken> {
        val playersByName = session.players.associateBy { it.name }
        val result = session.votes.map { (voter, target) ->
            VoteToken(
                voterName = voter,
                targetName = target,
                initial = playersByName[voter]?.initial ?: "?"
            )
        }.toMutableList()
        val mayor = session.players.firstOrNull { it.alive && it.role?.key == "alcalde" }
        if (session.alcaldeRevealed && mayor != null) {
            session.votes[mayor.name]?.let { target ->
                result += VoteToken(mayor.name, target, mayor.initial)
            }
        }
        if (session.contrapuntoSuspicion.isNotBlank()) {
            result += VoteToken("Senalamiento del Payador", session.contrapuntoSuspicion, "P")
        }
        return result
    }

    private fun addVoteToken(session: GameSession, token: VoteToken) {
        val holder = cardHolders[token.targetName] ?: return
        holder.count += 1
        holder.total.text = "${holder.count} ${if (holder.count == 1) "VOTO" else "VOTOS"}"
        val tokenView = TextView(context).apply {
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_vote_token,
                context.theme
            )
            gravity = Gravity.CENTER
            text = if (session.showIndividualVotes) token.initial else "•"
            setTextColor(context.getColor(R.color.bg_dark))
            textSize = if (session.showIndividualVotes) 8f else 10f
            typeface = Typeface.DEFAULT_BOLD
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
            contentDescription = if (session.showIndividualVotes) {
                "${token.voterName} voto a ${token.targetName}"
            } else {
                "Voto anonimo para ${token.targetName}"
            }
        }
        val params = GridLayout.LayoutParams().apply {
            width = dp(16)
            height = dp(16)
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }
        holder.voterTokens.addView(tokenView, params)
        tokenView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .start()
        holder.root.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(90L)
            .withEndAction {
                holder.root.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
            }
            .start()
    }

    private fun createVoteCard(player: GamePlayer): VoteCardHolder {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(6))
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_vote_result_card,
                context.theme
            )
        }
        val portrait = FrameLayout(context)
        val cardBack = ImageView(context).apply {
            setImageResource(R.drawable.card_back_traidores)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val avatar = TextView(context).apply {
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_player_avatar,
                context.theme
            )
            gravity = Gravity.CENTER
            text = player.initial
            setTextColor(context.getColor(R.color.bg_dark))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        val roleImage = ImageView(context).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_profile_avatar_frame,
                context.theme
            )
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        portrait.addView(cardBack, FrameLayout.LayoutParams(dp(56), dp(76), Gravity.CENTER))
        portrait.addView(
            avatar,
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(0)
            }
        )
        portrait.addView(roleImage, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        root.addView(portrait, LinearLayout.LayoutParams(dp(72), dp(78)))

        root.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            text = player.name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25)))

        val total = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "0 VOTOS"
            setTextColor(context.getColor(R.color.accent_gold))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))

        val voterTokens = GridLayout(context).apply {
            columnCount = 6
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(0, dp(3), 0, 0)
        }
        root.addView(
            voterTokens,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25))
        )
        return VoteCardHolder(root, avatar, roleImage, total, voterTokens)
    }

    private fun createExpulsionCard(player: GamePlayer): VoteCardHolder {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(9), dp(8), dp(6))
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_vote_result_card,
                context.theme
            )
        }
        val portrait = FrameLayout(context)
        val avatar = TextView(context).apply {
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_player_avatar,
                context.theme
            )
            gravity = Gravity.CENTER
            text = player.initial
            setTextColor(context.getColor(R.color.bg_dark))
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val roleImage = ImageView(context).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_profile_avatar_frame,
                context.theme
            )
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        portrait.addView(avatar, FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER))
        portrait.addView(roleImage, FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER))
        root.addView(portrait, LinearLayout.LayoutParams(dp(76), dp(76)))

        root.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            text = player.name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

        val total = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "SERA EXPULSADO"
            setTextColor(context.getColor(R.color.accent_gold))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)))

        val voterTokens = GridLayout(context).apply {
            visibility = View.GONE
            columnCount = 1
        }
        root.addView(
            voterTokens,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        )
        return VoteCardHolder(root, avatar, roleImage, total, voterTokens)
    }

    private fun emptyVoteMessage(): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            text = "NO SE EMITIERON VOTOS"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable(action)
        scheduled += runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelScheduled() {
        scheduled.forEach(handler::removeCallbacks)
        scheduled.clear()
    }
}
