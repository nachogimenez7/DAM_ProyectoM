package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.view.Gravity
import android.view.View
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
    private val dp: (Int) -> Int
) {

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
        title.text = when (session.voteRound) {
            2 -> "RECUENTO FINAL"
            3 -> "DECISION DEL ALCALDE"
            4 -> "CORRUPCION EN EL PUEBLO"
            else -> "RECUENTO DE VOTOS"
        }
        subtitle.text = if (session.voteRound == 4) {
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
                    LinearLayout.LayoutParams(dp(118), dp(156)).apply {
                        marginStart = dp(5)
                        marginEnd = dp(5)
                    }
                )
            }
        }

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
            schedule(index * 300L + 280L) {
                addVoteToken(session, token)
                if (index == tokens.lastIndex) {
                    schedule(380L) { finishRecount(session) }
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
        val targetName = session.dayEliminationTarget
        val holder = cardHolders[targetName]
        if (holder == null) {
            title.text = "$targetName FUE EXPULSADO"
            notice.text = "El pueblo dicto su sentencia."
            setContinueReady("CONTINUAR")
            onFinished()
            return
        }

        continueButton.animate().cancel()
        continueButton.visibility = View.INVISIBLE
        continueButton.isEnabled = false
        title.text = if (session.alcaldeCorruption) {
            "CORRUPCION EN EL PUEBLO"
        } else {
            "EXPULSION"
        }
        subtitle.text = if (session.alcaldeCorruption) {
            "El Alcalde impuso su autoridad y evito su propia expulsion."
        } else {
            "$targetName recibio la mayoria de los votos."
        }
        notice.text = if (session.alcaldeCorruption) {
            "$targetName pagara el precio del poder."
        } else {
            "El pueblo dicta su sentencia."
        }
        boot.visibility = View.VISIBLE
        boot.alpha = 1f
        boot.translationX = overlay.width.toFloat()
        boot.rotation = -8f

        val targetCenter = holder.root.x + holder.root.width / 2f
        val bootTravel = targetCenter - overlay.width - dp(36)
        boot.animate()
            .translationX(bootTravel)
            .rotation(-2f)
            .setDuration(440L)
            .withEndAction {
                holder.root.animate()
                    .translationX(-overlay.width.toFloat())
                    .rotation(-18f)
                    .alpha(0f)
                    .setDuration(480L)
                    .start()
                boot.animate()
                    .translationX(bootTravel + dp(80))
                    .rotation(-16f)
                    .setDuration(260L)
                    .withEndAction {
                        boot.visibility = View.INVISIBLE
                        if (session.revealRolesOnDeath) {
                            revealExpelledRole(session, holder, onFinished)
                        } else {
                            title.text = "$targetName FUE EXPULSADO"
                            subtitle.text = "Su carta permanece oculta."
                            notice.text = "El pueblo continua sin conocer su verdadera identidad."
                            setContinueReady("CONTINUAR")
                            onFinished()
                        }
                    }
                    .start()
            }
            .start()
    }

    fun hide() {
        cancelAnimations()
        overlay.visibility = View.GONE
        overlay.alpha = 1f
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
        }
    }

    private fun revealExpelledRole(
        session: GameSession,
        holder: VoteCardHolder,
        onFinished: () -> Unit
    ) {
        val player = session.players.firstOrNull { it.name == session.dayEliminationTarget }
        holder.avatar.visibility = View.GONE
        holder.roleImage.setImageResource(roleImageFor(player?.role))
        holder.roleImage.visibility = View.VISIBLE
        holder.root.translationX = overlay.width.toFloat()
        holder.root.rotation = 12f
        holder.root.alpha = 0f
        title.text = "CARTA REVELADA"
        subtitle.text = "${player?.name.orEmpty()} era ${player?.role?.name ?: "DESCONOCIDO"}."
        notice.text = "La identidad queda expuesta ante todo el pueblo."
        holder.root.animate()
            .translationX(0f)
            .rotation(0f)
            .alpha(1f)
            .setDuration(520L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    holder.root.animate().setListener(null)
                    setContinueReady("CONTINUAR")
                    onFinished()
                }
            })
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
            textSize = if (session.showIndividualVotes) 8f else 11f
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
            width = dp(14)
            height = dp(14)
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
            setPadding(dp(6), dp(7), dp(6), dp(5))
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
            textSize = 22f
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
        portrait.addView(avatar, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        portrait.addView(roleImage, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        root.addView(portrait, LinearLayout.LayoutParams(dp(52), dp(52)))

        root.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            text = player.name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 12f
            typeface = ResourcesCompat.getFont(context, R.font.grenze) ?: Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))

        val total = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "0 VOTOS"
            setTextColor(context.getColor(R.color.accent_gold))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)))

        val voterTokens = GridLayout(context).apply {
            columnCount = 6
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        root.addView(
            voterTokens,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        )
        return VoteCardHolder(root, avatar, roleImage, total, voterTokens)
    }

    private fun emptyVoteMessage(): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            text = "NO SE EMITIERON VOTOS"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 18f
            typeface = ResourcesCompat.getFont(context, R.font.grenze) ?: Typeface.DEFAULT_BOLD
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
