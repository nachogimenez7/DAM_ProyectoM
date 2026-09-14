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
    private var startedAtMs = 0L
    private var pendingReady: Runnable? = null
    private val fallbackRunnables = mutableListOf<Runnable>()
    private var usingScaleIndependentAnimation = false

    fun start(player: GamePlayer, revealRole: Boolean) {
        cancel()
        running = true
        startedAtMs = android.os.SystemClock.uptimeMillis()
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
        usingScaleIndependentAnimation = EssentialViewAnimation.requiresFallback(overlay)
        if (usingScaleIndependentAnimation) {
            startScaleIndependent(revealRole)
            return
        }

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
                ObjectAnimator.ofFloat(bloodLeft, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(bloodLeft, View.SCALE_X, 0.55f, 1.16f),
                ObjectAnimator.ofFloat(bloodLeft, View.SCALE_Y, 0.55f, 1.16f),
                ObjectAnimator.ofFloat(bloodRight, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(bloodRight, View.SCALE_X, 0.5f, 1.1f),
                ObjectAnimator.ofFloat(bloodRight, View.SCALE_Y, 0.5f, 1.1f)
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
                        val remaining = GameplayPresentationTiming.remainingMs(
                            startedAtMs, android.os.SystemClock.uptimeMillis(), DEATH_REVEAL_AUDIO_MS
                        )
                        pendingReady = Runnable {
                            pendingReady = null
                            if (running) {
                                readyToContinue = true
                                onReadyToContinue()
                            }
                        }.also { overlay.postDelayed(it, remaining) }
                    }
                }
            })
            start()
        }
    }

    fun continueAndFinish() {
        if (!running || !readyToContinue) return
        readyToContinue = false
        if (usingScaleIndependentAnimation) {
            EssentialViewAnimation.fadeOut(overlay, 260L) { finish() }
            return
        }
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
        pendingReady?.let(overlay::removeCallbacks)
        pendingReady = null
        fallbackRunnables.forEach(overlay::removeCallbacks)
        fallbackRunnables.clear()
        running = false
        readyToContinue = false
        usingScaleIndependentAnimation = false
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        EssentialViewAnimation.clear(overlay, content, card, cardBack, cardFront, bloodLeft, bloodRight, flash, roleName)
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

    private fun startScaleIndependent(revealRole: Boolean) {
        overlay.alpha = 1f
        content.alpha = 1f
        content.scaleX = 1f
        content.scaleY = 1f
        EssentialViewAnimation.reveal(overlay, ENTRANCE_MS, fromScale = 1f)
        EssentialViewAnimation.reveal(content, ENTRANCE_MS, fromScale = 0.9f)
        scheduleFallback(ENTRANCE_MS) {
            flash.alpha = 0.56f
            bloodLeft.alpha = 1f
            bloodRight.alpha = 1f
            bloodLeft.scaleX = 1f
            bloodLeft.scaleY = 1f
            bloodRight.scaleX = 1f
            bloodRight.scaleY = 1f
            EssentialViewAnimation.slideIn(card, fromX = dp(12).toFloat(), durationMs = IMPACT_MS)
            EssentialViewAnimation.reveal(bloodLeft, IMPACT_MS, fromScale = 0.5f)
            EssentialViewAnimation.reveal(bloodRight, IMPACT_MS, fromScale = 0.5f)
        }
        val revealAt = ENTRANCE_MS + IMPACT_MS
        scheduleFallback(revealAt) {
            if (revealRole) {
                cardBack.visibility = View.INVISIBLE
                cardFront.visibility = View.VISIBLE
                roleName.alpha = 1f
                EssentialViewAnimation.reveal(cardFront, ROLE_FLIP_OUT_MS + ROLE_FLIP_IN_MS, fromScale = 0.76f)
                EssentialViewAnimation.reveal(roleName, ROLE_FLIP_IN_MS, delayMs = ROLE_FLIP_OUT_MS)
            } else {
                roleName.text = "ROL OCULTO"
                roleName.alpha = 1f
                EssentialViewAnimation.reveal(card, HIDDEN_ROLE_MS, fromScale = 0.94f)
            }
        }
        scheduleFallback(DEATH_REVEAL_AUDIO_MS) {
            if (!running) return@scheduleFallback
            readyToContinue = true
            onReadyToContinue()
        }
    }

    private fun scheduleFallback(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable(action)
        fallbackRunnables += runnable
        overlay.postDelayed(runnable, delayMs)
    }

    private fun finish() {
        if (!running) return
        running = false
        readyToContinue = false
        usingScaleIndependentAnimation = false
        fallbackRunnables.forEach(overlay::removeCallbacks)
        fallbackRunnables.clear()
        animator = null
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        onFinished()
    }

}
