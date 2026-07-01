package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

internal class NoDeathRevealAnimator(
    private val overlay: FrameLayout,
    private val content: LinearLayout,
    private val sunCore: ImageView,
    private val dp: (Int) -> Int,
    private val onFinished: () -> Unit
) {
    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null

    fun start() {
        cancel()
        running = true
        resetViews()
        overlay.visibility = View.VISIBLE

        val entrance = AnimatorSet().apply {
            startDelay = REVEAL_GAP_MS
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.95f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.95f, 1f),
                ObjectAnimator.ofFloat(sunCore, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(sunCore, View.SCALE_X, 0.45f, 1f),
                ObjectAnimator.ofFloat(sunCore, View.SCALE_Y, 0.45f, 1f)
            )
            duration = 360L
            interpolator = DecelerateInterpolator()
        }
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, dp(4).toFloat(), 0f)
            )
            duration = 260L
            interpolator = DecelerateInterpolator()
        }
        val breathe = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(sunCore, View.SCALE_X, 1f, 1.08f, 1f),
                ObjectAnimator.ofFloat(sunCore, View.SCALE_Y, 1f, 1.08f, 1f)
            )
            duration = 900L
            interpolator = DecelerateInterpolator()
        }
        val hold = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1600L }
        val exit = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 1f, 0.97f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 1f, 0.97f)
            )
            duration = 300L
            interpolator = AccelerateInterpolator()
        }

        animator = AnimatorSet().apply {
            playSequentially(entrance, settle, breathe, hold, exit)
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
        content.translationY = dp(4).toFloat()
        sunCore.alpha = 0f
        sunCore.scaleX = 0.45f
        sunCore.scaleY = 0.45f
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
