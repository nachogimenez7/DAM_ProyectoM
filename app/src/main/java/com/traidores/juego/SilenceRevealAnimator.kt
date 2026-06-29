package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal class SilenceRevealAnimator(
    private val overlay: FrameLayout,
    private val content: LinearLayout,
    private val card: FrameLayout,
    private val cageLeft: ImageView,
    private val cageRight: ImageView,
    private val cageDoor: ImageView,
    private val cageLock: ImageView,
    private val playerName: TextView,
    private val dp: (Int) -> Int,
    private val onFinished: () -> Unit
) {
    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null

    fun start(player: GamePlayer) {
        cancel()
        running = true
        playerName.text = player.name.uppercase()
        resetViews()
        overlay.visibility = View.VISIBLE

        val entrance = AnimatorSet().apply {
            startDelay = REVEAL_GAP_MS
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.95f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.95f, 1f)
            )
            duration = 280L
            interpolator = DecelerateInterpolator()
        }
        val buildCage = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cageLeft, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cageLeft, View.TRANSLATION_X, -dp(62).toFloat(), 0f),
                ObjectAnimator.ofFloat(cageRight, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cageRight, View.TRANSLATION_X, dp(62).toFloat(), 0f)
            )
            duration = 460L
            interpolator = DecelerateInterpolator()
        }
        val closeDoor = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cageDoor, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cageDoor, View.ROTATION_Y, -72f, 0f),
                ObjectAnimator.ofFloat(cageDoor, View.TRANSLATION_X, dp(28).toFloat(), 0f)
            )
            duration = 420L
            interpolator = DecelerateInterpolator()
        }
        val lockImpact = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cageLock, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cageLock, View.SCALE_X, 1.65f, 1f),
                ObjectAnimator.ofFloat(cageLock, View.SCALE_Y, 1.65f, 1f),
                ObjectAnimator.ofFloat(
                    card,
                    View.TRANSLATION_X,
                    0f,
                    -dp(3).toFloat(),
                    dp(3).toFloat(),
                    -dp(2).toFloat(),
                    dp(2).toFloat(),
                    0f
                )
            )
            duration = 320L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val hold = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1800L }
        val exit = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f).apply {
            duration = 300L
            interpolator = AccelerateInterpolator()
        }

        animator = AnimatorSet().apply {
            playSequentially(entrance, buildCage, closeDoor, lockImpact, hold, exit)
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
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        overlay.visibility = View.GONE
        overlay.alpha = 1f
    }

    private fun resetViews() {
        overlay.alpha = 0f
        content.scaleX = 0.95f
        content.scaleY = 0.95f
        card.translationX = 0f
        card.scaleX = 1f
        card.scaleY = 1f
        cageLeft.alpha = 0f
        cageLeft.translationX = -dp(62).toFloat()
        cageRight.alpha = 0f
        cageRight.translationX = dp(62).toFloat()
        cageDoor.alpha = 0f
        cageDoor.rotationY = -72f
        cageDoor.translationX = dp(28).toFloat()
        cageDoor.cameraDistance = dp(900).toFloat()
        cageLock.alpha = 0f
        cageLock.scaleX = 1.65f
        cageLock.scaleY = 1.65f
    }

    private fun finish() {
        if (!running) return
        running = false
        animator = null
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        onFinished()
    }

    private companion object {
        const val REVEAL_GAP_MS = 300L
    }
}
