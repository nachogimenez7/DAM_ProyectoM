package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
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
    private val cards: GridLayout,
    private val title: TextView,
    private val subtitle: TextView,
    private val notice: TextView,
    private val continueButton: Button,
    private val boot: ImageView,
    private val dust: ImageView,
    private val roleImageFor: (GameRole?) -> Int,
    private val dp: (Int) -> Int,
    private val onImpact: (() -> Unit)? = null,
    private val onContinueReady: (() -> Unit)? = null
) {
    private companion object {
        const val RECOUNT_INITIAL_DELAY_MS = 420L
        const val RECOUNT_TOKEN_STEP_MS = 420L
        const val RECOUNT_FINAL_READ_MS = 700L
        const val EXPULSION_NAME_READ_MS = 1_500L
        const val REVEALED_CARD_READ_MS = 1_700L
        const val EXPULSION_KICK_WINDUP_MS = 120L
        const val EXPULSION_SENTENCE_READ_MS = 700L
        const val BOOT_ENTER_MS = 460L
        const val BOOT_WINDUP_MS = 320L
        const val BOOT_STRIKE_MS = 240L
        const val BOOT_IMPACT_PAUSE_MS = 80L
        const val EXPULSION_LAUNCH_MS = 720L
        const val EXPULSION_AFTER_KICK_READ_MS = 1_700L
        // Cuanto se mete la punta de la bota MAS ALLA del borde de la carta: un
        // "zapatazo" en el borde, no un golpe que llegue hasta el centro.
        const val BOOT_CONTACT_OVERLAP_DP = 18
        const val BOOT_REBOUND_MS = 90L
        const val PANEL_MARGIN_HORIZONTAL_DP = 18
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
        val seal: ImageView? = null,
        var count: Int = 0
    )

    private val scheduled = mutableListOf<Runnable>()
    private val cardHolders = linkedMapOf<String, VoteCardHolder>()
    private var launchAnimator: AnimatorSet? = null
    private var shakeAnimator: ObjectAnimator? = null
    private var flashView: View? = null
    private var flyingCard: View? = null

    fun show(session: GameSession) {
        cancelAnimations()
        // Vaciar la grilla ANTES de tocar columnCount (evita estados invalidos del GridLayout).
        cardHolders.clear()
        cards.removeAllViews()
        applyPanelMode(expulsion = false)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        panel.alpha = 0f
        panel.scaleX = 0.96f
        panel.scaleY = 0.96f
        boot.visibility = View.INVISIBLE
        dust.visibility = View.INVISIBLE
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
        setNotice("")

        val candidateNames = session.players
            .map { it.name }
            .filter { candidate ->
                session.votes.values.any { it == candidate } ||
                    session.contrapuntoSuspicion == candidate ||
                    (session.voteRound == 3 && session.dayEliminationTarget == candidate)
            }
        if (candidateNames.isEmpty()) {
            cards.columnCount = 1
            cards.addView(
                emptyVoteMessage(),
                GridLayout.LayoutParams().apply {
                    width = dp(200)
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            )
        } else {
            val cols = recountColumnCount(candidateNames.size)
            cards.columnCount = cols
            val (cardWidth, cardHeight) = recountCardSize(candidateNames.size)
            val denseCards = candidateNames.size > 4
            val cardMargin = if (denseCards) 3 else 5
            val oddLast = cols == 2 && candidateNames.size % 2 == 1
            candidateNames.forEachIndexed { index, candidate ->
                val player = session.players.first { it.name == candidate }
                val holder = createVoteCard(player, dense = denseCards)
                cardHolders[candidate] = holder
                val row = index / cols
                val col = index % cols
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(col)
                ).apply {
                    width = dp(cardWidth)
                    height = dp(cardHeight)
                    setMargins(dp(cardMargin), dp(cardMargin), dp(cardMargin), dp(cardMargin))
                }
                // La ultima carta impar se centra abarcando ambas columnas.
                if (oddLast && index == candidateNames.lastIndex) {
                    params.columnSpec = GridLayout.spec(0, 2, GridLayout.CENTER)
                }
                cards.addView(holder.root, params)
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
        title.text = "EL PUEBLO NO LLEGÓ A UN ACUERDO"
        subtitle.text = "Nadie será expulsado esta jornada."
        setNotice("La noche volverá a caer sobre el pueblo.")
        setContinueReady("CONTINUAR")
    }

    fun playExpulsion(session: GameSession, onFinished: () -> Unit) {
        cancelScheduled()
        // Vaciar la grilla ANTES de cambiar columnCount (evita estados invalidos del GridLayout).
        cards.removeAllViews()
        cardHolders.clear()
        applyPanelMode(expulsion = true)
        val targetName = session.dayEliminationTarget
        val targetPlayer = session.players.firstOrNull { it.name == targetName }
        if (targetPlayer == null) {
            title.text = "$targetName FUE EXPULSADO"
            setNotice("El pueblo dictó su sentencia.")
            setContinueReady("CONTINUAR")
            onFinished()
            return
        }

        continueButton.animate().cancel()
        continueButton.visibility = View.INVISIBLE
        continueButton.isEnabled = false
        continueButton.alpha = 0f
        title.text = if (session.alcaldeCorruption) {
            "CORRUPCIÓN EN EL PUEBLO"
        } else {
            "EXPULSIÓN"
        }
        subtitle.text = if (session.alcaldeCorruption) {
            "El poder ha torcido la decisión del pueblo."
        } else {
            "El pueblo ha tomado su decisión."
        }
        setNotice(if (session.alcaldeCorruption) {
            "$targetName será expulsado en lugar del Alcalde."
        } else if (session.revealRolesOnDeath) {
            "$targetName será expulsado. Su carta se revelará primero."
        } else {
            "$targetName será expulsado del pueblo."
        })
        boot.visibility = View.INVISIBLE
        cards.columnCount = 1

        val holder = createExpulsionCard(targetPlayer)
        cardHolders[targetName] = holder
        cards.addView(
            holder.root,
            GridLayout.LayoutParams().apply {
                width = dp(142)
                height = dp(142)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
        )
        holder.root.alpha = 0f
        holder.root.scaleX = 0.86f
        holder.root.scaleY = 0.86f
        holder.root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(2.0f))
            .setDuration(320L)
            .withEndAction {
                if (session.revealRolesOnDeath) {
                    revealExpelledRoleBeforeKick(session, holder) {
                        schedule(REVEALED_CARD_READ_MS) {
                            schedule(EXPULSION_KICK_WINDUP_MS) {
                                kickExpulsionCard(session, holder, onFinished)
                            }
                        }
                    }
                } else {
                    schedule(EXPULSION_NAME_READ_MS) {
                        schedule(EXPULSION_KICK_WINDUP_MS) {
                            kickExpulsionCard(session, holder, onFinished)
                        }
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
        launchAnimator?.cancel()
        launchAnimator = null
        shakeAnimator?.cancel()
        shakeAnimator = null
        panel.translationX = 0f
        flashView?.let { overlay.removeView(it) }
        flashView = null
        flyingCard?.let { overlay.removeView(it) }
        flyingCard = null
        panel.animate().cancel()
        continueButton.animate().cancel()
        boot.animate().cancel()
        dust.animate().cancel()
        dust.visibility = View.INVISIBLE
        dust.alpha = 0f
        cardHolders.values.forEach { holder ->
            holder.root.animate().cancel()
            holder.root.alpha = 1f
            holder.root.translationX = 0f
            holder.root.translationY = 0f
            holder.root.rotation = 0f
            holder.root.scaleX = 1f
            holder.root.scaleY = 1f
            holder.seal?.apply {
                animate().cancel()
                visibility = View.GONE
                alpha = 0f
                rotation = 0f
                scaleX = 1f
                scaleY = 1f
            }
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
        setNotice("La identidad queda expuesta ante todo el pueblo.")
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
                    .setInterpolator(OvershootInterpolator(2.4f))
                    .setDuration(300L)
                    .withEndAction(onRevealed)
                    .start()
            }
            .start()
    }

    private fun applyPanelMode(expulsion: Boolean) {
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
            leftMargin = dp(PANEL_MARGIN_HORIZONTAL_DP)
            rightMargin = dp(PANEL_MARGIN_HORIZONTAL_DP)
        }
        cards.layoutParams = cards.layoutParams.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        cards.columnCount = if (expulsion) 1 else 2
        cards.useDefaultMargins = false
    }

    private fun recountColumnCount(candidateCount: Int): Int {
        return when {
            candidateCount <= 1 -> 1
            candidateCount <= 4 -> 2
            else -> 3
        }
    }

    private fun recountCardSize(candidateCount: Int): Pair<Int, Int> {
        // Las cartas deben entrar en el centro util del marco ornamental, sin scroll.
        return when {
            candidateCount <= 2 -> 96 to 136
            candidateCount <= 4 -> 92 to 128
            else -> 64 to 108
        }
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
        setNotice("La sentencia esta por cumplirse.")
        stampSeal(holder)

        // La bota espera fuera de cuadro, "cargada" (echada atras y girada).
        boot.setImageResource(R.drawable.boot_windup)
        boot.visibility = View.VISIBLE
        boot.alpha = 1f
        boot.translationX = overlay.width.toFloat() + dp(40)
        boot.rotation = -26f
        boot.scaleX = 1.12f
        boot.scaleY = 1.12f

        val overlayLocation = IntArray(2)
        val holderLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        holder.root.getLocationOnScreen(holderLocation)
        // El contacto apunta al BORDE derecho de la carta (no al centro): la bota
        // solo "zapatea" un poco mas alla de ese borde, no atraviesa toda la carta.
        val cardRightEdge = holderLocation[0] - overlayLocation[0] + holder.root.width
        val bootTravel = cardRightEdge - overlay.width - dp(44) - dp(BOOT_CONTACT_OVERLAP_DP)
        val cockedX = bootTravel + dp(150)

        // La patada se lee en 3 tiempos para que se entienda (antes pasaba en un borron):
        // Beat 1 — dejar leer la sentencia; despues la bota ENTRA a la vista, cargada.
        schedule(EXPULSION_SENTENCE_READ_MS) {
            boot.animate()
                .translationX(cockedX.toFloat())
                .rotation(-20f)
                .setInterpolator(DecelerateInterpolator())
                .setDuration(BOOT_ENTER_MS)
                .withEndAction {
                    // Beat 2 — pausa "cargada" mientras la carta se afirma (anticipacion).
                    // La carta se inclina en sentido CONTRARIO al giro final (sentido horario)
                    // para que el rebote se sienta como una liberacion, no una continuacion.
                    holder.root.animate()
                        .translationX(dp(10).toFloat())
                        .rotation(-5f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(BOOT_WINDUP_MS)
                        .start()
                    schedule(BOOT_WINDUP_MS) {
                        // Beat 3 — el golpe entra acelerando al contacto. Escala mas contenida
                        // para que la bota no tape el panel entero al pegar.
                        boot.setImageResource(R.drawable.ic_kicking_boot)
                        boot.animate()
                            .translationX(bootTravel.toFloat())
                            .rotation(6f)
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setInterpolator(AccelerateInterpolator(1.6f))
                            .setDuration(BOOT_STRIKE_MS)
                            .withEndAction { onExpulsionImpact(session, holder, targetName, onFinished) }
                            .start()
                    }
                }
                .start()
        }
    }

    private fun onExpulsionImpact(
        session: GameSession,
        holder: VoteCardHolder,
        targetName: String,
        onFinished: () -> Unit
    ) {
        // Pico: sonido + haptico + destello calido + sacudida, en el frame de contacto.
        onImpact?.invoke()
        playImpactFlash()
        shakePanel()
        playImpactDust(holder)

        // La bota clava el golpe, pausa breve y retrocede fuera de cuadro.
        boot.setImageResource(R.drawable.boot_recoil)
        boot.animate()
            .translationX(overlay.width.toFloat() + dp(40))
            .rotation(-30f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(BOOT_IMPACT_PAUSE_MS)
            .setInterpolator(AccelerateInterpolator())
            .setDuration(300L)
            .withEndAction { boot.visibility = View.INVISIBLE }
            .start()

        // Squash marcado de la carta en el impacto, seguido de un REBOTE elastico
        // (se estira para el otro lado) que es lo que la termina de soltar a volar.
        holder.root.animate()
            .scaleX(1.32f)
            .scaleY(0.68f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(90L)
            .withEndAction {
                holder.root.animate()
                    .scaleX(0.82f)
                    .scaleY(1.2f)
                    .setInterpolator(OvershootInterpolator(2.2f))
                    .setDuration(BOOT_REBOUND_MS)
                    .withEndAction { launchExpelledCard(session, holder, targetName, onFinished) }
                    .start()
            }
            .start()
    }

    private fun stampSeal(holder: VoteCardHolder) {
        val seal = holder.seal ?: return
        seal.animate().cancel()
        seal.visibility = View.VISIBLE
        seal.alpha = 0f
        seal.rotation = -18f
        seal.scaleX = 2.1f
        seal.scaleY = 2.1f
        seal.animate()
            .alpha(1f)
            .rotation(-6f)
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.6f))
            .setDuration(240L)
            .start()
    }

    private fun playImpactDust(holder: VoteCardHolder) {
        val overlayLocation = IntArray(2)
        val holderLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        holder.root.getLocationOnScreen(holderLocation)
        // El polvo se ubica del lado IZQUIERDO de la carta: es hacia ahi que sale
        // volando, asi que el estallido de tierra acompaña esa direccion de salida.
        val contactX = holderLocation[0] - overlayLocation[0] + holder.root.width * 0.18f
        val contactY = holderLocation[1] - overlayLocation[1] + holder.root.height * 0.52f
        val dustWidth = dust.width.takeIf { it > 0 } ?: dp(190)
        val dustHeight = dust.height.takeIf { it > 0 } ?: dp(190)

        dust.animate().cancel()
        dust.visibility = View.VISIBLE
        dust.alpha = 0.95f
        dust.scaleX = 0.5f
        dust.scaleY = 0.5f
        dust.translationX = contactX - dustWidth / 2f
        dust.translationY = contactY - dustHeight / 2f
        dust.animate()
            .alpha(0f)
            .scaleX(1.85f)
            .scaleY(1.85f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(360L)
            .withEndAction { dust.visibility = View.INVISIBLE }
            .start()
    }

    private fun launchExpelledCard(
        session: GameSession,
        holder: VoteCardHolder,
        targetName: String,
        onFinished: () -> Unit
    ) {
        val card = holder.root
        // Se reparenta de "cards" (GridLayout, con celdas) a "overlay" (FrameLayout
        // simple) ANTES de volar, para que el vuelo no dependa de ningun limite de
        // celda/columna — solo del propio overlay, que ya es clipChildren=false.
        reparentCardToOverlayForFlight(card)
        val outX = -overlay.width.toFloat() - dp(80)
        // Sale volando en un arco amplio (sube y despues cruza fuera de cuadro) girando
        // en sentido horario (arriba de la carta barriendo de izquierda a derecha).
        val arc = Path().apply {
            moveTo(card.translationX, card.translationY)
            quadTo(-overlay.width * 0.28f, -dp(120).toFloat(), outX, -dp(30).toFloat())
        }
        var cancelled = false
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(card, View.TRANSLATION_X, View.TRANSLATION_Y, arc),
            ObjectAnimator.ofFloat(card, View.ROTATION, card.rotation, 210f),
            ObjectAnimator.ofFloat(card, View.SCALE_X, card.scaleX, 0.55f),
            ObjectAnimator.ofFloat(card, View.SCALE_Y, card.scaleY, 0.55f),
            ObjectAnimator.ofFloat(card, View.ALPHA, 1f, 0f)
        )
        set.duration = EXPULSION_LAUNCH_MS
        set.interpolator = AccelerateInterpolator(1.5f)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (launchAnimator === set) launchAnimator = null
                removeFlyingCardFromOverlay(card)
                if (!cancelled) announceExpulsionOutcome(session, targetName, onFinished)
            }
        })
        launchAnimator = set
        set.start()
    }

    // Saca la carta de "cards" (GridLayout) y la agrega a "overlay" en la misma
    // posicion visual, conservando su translationX/Y actual.
    private fun reparentCardToOverlayForFlight(card: View) {
        val currentParent = card.parent as? ViewGroup ?: return
        if (currentParent === overlay) return
        val overlayLocation = IntArray(2)
        val cardLocation = IntArray(2)
        overlay.getLocationOnScreen(overlayLocation)
        card.getLocationOnScreen(cardLocation)
        val baseLeft = (cardLocation[0] - overlayLocation[0]) - card.translationX
        val baseTop = (cardLocation[1] - overlayLocation[1]) - card.translationY
        val width = card.width
        val height = card.height
        currentParent.removeView(card)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(width, height).apply {
                leftMargin = baseLeft.toInt()
                topMargin = baseTop.toInt()
            }
        )
        flyingCard = card
    }

    private fun removeFlyingCardFromOverlay(card: View) {
        if (card.parent === overlay) overlay.removeView(card)
        if (flyingCard === card) flyingCard = null
    }

    private fun announceExpulsionOutcome(
        session: GameSession,
        targetName: String,
        onFinished: () -> Unit
    ) {
        title.text = "$targetName FUE EXPULSADO"
        subtitle.text = if (session.revealRolesOnDeath) {
            "Su carta ya fue revelada."
        } else {
            "Su carta permanece oculta."
        }
        setNotice(if (session.alcaldeCorruption) {
            "El poder deja su marca en la jornada."
        } else {
            "El pueblo continua con la partida."
        })
        schedule(EXPULSION_AFTER_KICK_READ_MS) {
            setContinueReady("CONTINUAR")
            onFinished()
        }
    }

    private fun playImpactFlash() {
        val flash = View(context).apply {
            setBackgroundColor(Color.parseColor("#FFE9C8"))
            alpha = 0f
        }
        overlay.addView(
            flash,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        flashView = flash
        flash.animate()
            .alpha(0.22f)
            .setDuration(60L)
            .withEndAction {
                flash.animate()
                    .alpha(0f)
                    .setDuration(150L)
                    .withEndAction {
                        overlay.removeView(flash)
                        if (flashView === flash) flashView = null
                    }
                    .start()
            }
            .start()
    }

    private fun shakePanel() {
        shakeAnimator?.cancel()
        shakeAnimator = ObjectAnimator.ofFloat(
            panel,
            View.TRANSLATION_X,
            0f,
            dp(9).toFloat(),
            -dp(7).toFloat(),
            dp(5).toFloat(),
            -dp(3).toFloat(),
            0f
        ).apply {
            duration = 260L
            start()
        }
    }

    private fun finishRecount(session: GameSession) {
        val tied = session.tieVoteCandidates.size > 1
        when {
            tied && session.voteRound == 1 -> {
                title.text = "EMPATE"
                setNotice("SI EL EMPATE SE REPITE, NADIE SERÁ EXPULSADO.")
                setContinueReady("IR AL DESEMPATE")
            }
            tied -> {
                title.text = "EL EMPATE SE REPITIÓ"
                setNotice("El Alcalde podrá intervenir. Sin su decisión, nadie será expulsado.")
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
                setNotice(if (session.voteRound == 4) {
                    "El Alcalde evito su expulsion. ${session.dayEliminationTarget} pagara el precio."
                } else if (session.voteRound == 3) {
                    "El Alcalde eligio expulsar a ${session.dayEliminationTarget}."
                } else {
                    "${session.dayEliminationTarget} recibio la mayor cantidad de votos."
                })
                setContinueReady("VER EXPULSION")
            }
            else -> {
                title.text = "SIN MAYORIA"
                setNotice("El pueblo no alcanzo una decision.")
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

    private fun setNotice(message: String) {
        notice.text = message
        notice.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
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
            text = if (session.showIndividualVotes) token.initial else "*"
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
            width = dp(10)
            height = dp(10)
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

    private fun createVoteCard(player: GamePlayer, dense: Boolean): VoteCardHolder {
        val rootPaddingHorizontal = if (dense) 4 else 5
        val rootPaddingTop = if (dense) 4 else 5
        val rootPaddingBottom = if (dense) 3 else 4
        val cardBackWidth = if (dense) 32 else 38
        val cardBackHeight = if (dense) 44 else 52
        val avatarSize = if (dense) 23 else 28
        val roleImageSize = if (dense) 32 else 38
        val portraitWidth = if (dense) 42 else 52
        val portraitHeight = if (dense) 43 else 54
        val nameHeight = if (dense) 15 else 18
        val totalHeight = if (dense) 15 else 18
        val tokensHeight = 24
        val tokenColumns = if (dense) 4 else 5
        val nameTextSize = if (dense) 10f else 12f
        val totalTextSize = if (dense) 10f else 12f
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dp(rootPaddingHorizontal),
                dp(rootPaddingTop),
                dp(rootPaddingHorizontal),
                dp(rootPaddingBottom)
            )
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
            textSize = if (dense) 12f else 14f
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
        portrait.addView(cardBack, FrameLayout.LayoutParams(dp(cardBackWidth), dp(cardBackHeight), Gravity.CENTER))
        portrait.addView(
            avatar,
            FrameLayout.LayoutParams(dp(avatarSize), dp(avatarSize), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(0)
            }
        )
        portrait.addView(roleImage, FrameLayout.LayoutParams(dp(roleImageSize), dp(roleImageSize), Gravity.CENTER))
        root.addView(portrait, LinearLayout.LayoutParams(dp(portraitWidth), dp(portraitHeight)))

        root.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            text = player.name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = nameTextSize
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(nameHeight)))

        val total = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "0 VOTOS"
            setTextColor(context.getColor(R.color.accent_gold))
            textSize = totalTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(totalHeight)))

        val voterTokens = GridLayout(context).apply {
            columnCount = tokenColumns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(0, dp(2), 0, 0)
        }
        root.addView(
            voterTokens,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(tokensHeight))
        )
        return VoteCardHolder(root, avatar, roleImage, total, voterTokens)
    }

    private fun createExpulsionCard(player: GamePlayer): VoteCardHolder {
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
        val avatar = TextView(context).apply {
            background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.bg_player_avatar,
                context.theme
            )
            gravity = Gravity.CENTER
            text = player.initial
            setTextColor(context.getColor(R.color.bg_dark))
            textSize = 24f
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
        val seal = ImageView(context).apply {
            setImageResource(R.drawable.expulsion_seal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            alpha = 0f
        }
        portrait.addView(avatar, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        portrait.addView(roleImage, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        portrait.addView(
            seal,
            FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM or Gravity.END)
        )
        root.addView(portrait, LinearLayout.LayoutParams(dp(66), dp(62)))

        root.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            text = player.name
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))

        val total = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "SERA EXPULSADO"
            setTextColor(context.getColor(R.color.accent_gold))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)))

        val voterTokens = GridLayout(context).apply {
            visibility = View.GONE
            columnCount = 1
        }
        root.addView(
            voterTokens,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        )
        return VoteCardHolder(root, avatar, roleImage, total, voterTokens, seal)
    }

    private fun emptyVoteMessage(): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            text = "NO SE EMITIERON VOTOS"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
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
