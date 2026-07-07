package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal class DeathRevealAnimator(
    private val overlay: FrameLayout,
    private val content: LinearLayout,
    private val card: FrameLayout,
    private val cardBack: ImageView,
    private val cardFront: ImageView,
    private val bloodLeft: ImageView,
    private val bloodRight: ImageView,
    private val flash: View,
    private val playerName: TextView,
    private val roleName: TextView,
    private val roleImageFor: (GameRole?) -> Int,
    private val dp: (Int) -> Int,
    private val onReadyToContinue: () -> Unit,
    private val onFinished: () -> Unit
) {
    private companion object {
        const val DEATH_REVEAL_AUDIO_MS = 2_900L
        const val ENTRANCE_MS = 280L
        const val IMPACT_MS = 420L
        const val HIDDEN_ROLE_MS = 420L
        const val ROLE_FLIP_OUT_MS = 230L
        const val ROLE_FLIP_IN_MS = 260L
    }

    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null
    private var readyToContinue: Boolean = false

    fun start(player: GamePlayer, revealRole: Boolean) {
        cancel()
        running = true
        readyToContinue = false
        playerName.text = player.name.uppercase()
        roleName.text = if (revealRole) {
            player.role?.name?.uppercase() ?: "ROL DESCONOCIDO"
        } else {
            "ROL OCULTO"
        }
        if (revealRole) cardFront.setImageResource(roleImageFor(player.role))
        resetViews()
        if (!revealRole) roleName.alpha = 1f
        overlay.visibility = View.VISIBLE

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.94f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.94f, 1f)
            )
            duration = ENTRANCE_MS
            interpolator = DecelerateInterpolator()
        }
        val impact = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    card,
                    View.TRANSLATION_X,
                    0f,
                    -dp(6).toFloat(),
                    dp(6).toFloat(),
                    -dp(3).toFloat(),
                    dp(3).toFloat(),
                    0f
                ),
                ObjectAnimator.ofFloat(flash, View.ALPHA, 0f, 0.56f, 0f),
                ObjectAnimator.ofFloat(bloodLeft, View.ALPHA, 0f, 0.9f),
                ObjectAnimator.ofFloat(bloodLeft, View.SCALE_X, 0.55f, 1.08f),
                ObjectAnimator.ofFloat(bloodLeft, View.SCALE_Y, 0.55f, 1.08f),
                ObjectAnimator.ofFloat(bloodRight, View.ALPHA, 0f, 0.76f),
                ObjectAnimator.ofFloat(bloodRight, View.SCALE_X, 0.5f, 1f),
                ObjectAnimator.ofFloat(bloodRight, View.SCALE_Y, 0.5f, 1f)
            )
            duration = IMPACT_MS
            interpolator = AccelerateDecelerateInterpolator()
        }
        val reveal = if (revealRole) roleRevealAnimation() else hiddenRoleAnimation()
        val hold = readHoldAnimation(revealRole)

        animator = AnimatorSet().apply {
            playSequentially(entrance, impact, reveal, hold)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (running) {
                        animator = null
                        readyToContinue = true
                        onReadyToContinue()
                    }
                }
            })
            start()
        }
    }

    fun continueAndFinish() {
        if (!running || !readyToContinue) return
        readyToContinue = false
        animator?.removeAllListeners()
        animator?.cancel()
        animator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 1f, 0.97f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 1f, 0.97f)
            )
            duration = 260L
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            })
            start()
        }
    }

    fun cancel() {
        running = false
        readyToContinue = false
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        overlay.visibility = View.GONE
        overlay.alpha = 1f
    }

    private fun roleRevealAnimation(): Animator {
        val flipOut = ObjectAnimator.ofFloat(card, View.ROTATION_Y, 0f, 90f).apply {
            duration = ROLE_FLIP_OUT_MS
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cardBack.visibility = View.INVISIBLE
                    cardFront.visibility = View.VISIBLE
                    card.rotationY = -90f
                }
            })
        }
        val flipIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ROTATION_Y, -90f, 0f),
                ObjectAnimator.ofFloat(roleName, View.ALPHA, 0f, 1f)
            )
            duration = ROLE_FLIP_IN_MS
            interpolator = DecelerateInterpolator()
        }
        return AnimatorSet().apply { playSequentially(flipOut, flipIn) }
    }

    private fun hiddenRoleAnimation(): Animator {
        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 1.05f, 1f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 1.05f, 1f)
            )
            duration = HIDDEN_ROLE_MS
            interpolator = DecelerateInterpolator()
        }
    }

    private fun readHoldAnimation(revealRole: Boolean): Animator {
        val elapsedBeforeHold = ENTRANCE_MS + IMPACT_MS + if (revealRole) {
            ROLE_FLIP_OUT_MS + ROLE_FLIP_IN_MS
        } else {
            HIDDEN_ROLE_MS
        }
        return ObjectAnimator.ofFloat(content, View.ALPHA, 1f, 1f).apply {
            duration = (DEATH_REVEAL_AUDIO_MS - elapsedBeforeHold).coerceAtLeast(0L)
        }
    }

    private fun resetViews() {
        overlay.alpha = 0f
        content.scaleX = 0.94f
        content.scaleY = 0.94f
        flash.alpha = 0f
        card.translationX = 0f
        card.rotationY = 0f
        card.cameraDistance = dp(900).toFloat()
        cardBack.visibility = View.VISIBLE
        cardFront.visibility = View.INVISIBLE
        roleName.alpha = 0f
        listOf(bloodLeft, bloodRight).forEach { blood ->
            blood.alpha = 0f
            blood.scaleX = 0.5f
            blood.scaleY = 0.5f
        }
    }

    private fun finish() {
        if (!running) return
        running = false
        readyToContinue = false
        animator = null
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        onFinished()
    }

}
