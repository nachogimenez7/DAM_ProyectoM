package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.random.Random

class JesterVictoryAnimator(
    private val overlay: FrameLayout,
    private val panel: FrameLayout,
    private val hornLeft: ImageView,
    private val hornRight: ImageView,
    private val confettiLayer: FrameLayout,
    private val continueButton: Button
) {
    private val random = Random(73)
    private var entranceAnimator: AnimatorSet? = null
    private val runningAnimators = mutableListOf<Animator>()
    private val finishRunnable = Runnable { revealContinueButton() }

    fun show(durationMs: Long) {
        cancel(hideOverlay = false)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        panel.alpha = 0f
        panel.scaleX = 0.78f
        panel.scaleY = 0.78f
        hornLeft.alpha = 0f
        hornRight.alpha = 0f
        hornLeft.translationX = -dp(42).toFloat()
        hornRight.translationX = dp(42).toFloat()
        continueButton.visibility = View.INVISIBLE
        continueButton.isEnabled = false
        continueButton.alpha = 0f

        entranceAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(panel, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_X, 0.78f, 1.06f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_Y, 0.78f, 1.06f, 1f)
            )
            duration = 650L
            interpolator = DecelerateInterpolator()
            start()
        }
        animateHornEntrance(hornLeft)
        animateHornEntrance(hornRight)
        animateHorn(hornLeft, -10f, 6f)
        animateHorn(hornRight, 10f, -6f)
        overlay.post { launchConfetti() }
        overlay.postDelayed(finishRunnable, durationMs)
    }

    fun settle() {
        overlay.removeCallbacks(finishRunnable)
        entranceAnimator?.cancel()
        entranceAnimator = null
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        confettiLayer.removeAllViews()
        if (overlay.visibility == View.VISIBLE) {
            panel.alpha = 1f
            panel.scaleX = 1f
            panel.scaleY = 1f
            settleHorn(hornLeft)
            settleHorn(hornRight)
            revealContinueButton()
        }
    }

    fun hide() {
        cancel(hideOverlay = true)
    }

    private fun cancel(hideOverlay: Boolean) {
        overlay.removeCallbacks(finishRunnable)
        entranceAnimator?.cancel()
        entranceAnimator = null
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        confettiLayer.removeAllViews()
        if (hideOverlay) {
            overlay.visibility = View.GONE
        }
    }

    private fun animateHorn(view: ImageView, baseRotation: Float, swingRotation: Float) {
        val rotation = ObjectAnimator.ofFloat(
            view,
            View.ROTATION,
            baseRotation,
            swingRotation,
            baseRotation
        ).apply {
            duration = 620L
            repeatCount = 6
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val scale = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.88f, 1.08f, 0.92f, 1f).apply {
            duration = 620L
            repeatCount = 6
            start()
        }
        runningAnimators += rotation
        runningAnimators += scale
    }

    private fun animateHornEntrance(view: ImageView) {
        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, 0f)
            )
            startDelay = 180L
            duration = 480L
            interpolator = DecelerateInterpolator()
            start()
        }
        runningAnimators += entrance
    }

    private fun settleHorn(view: ImageView) {
        view.alpha = 1f
        view.translationX = 0f
        view.scaleY = 1f
    }

    private fun launchConfetti() {
        val width = confettiLayer.width
        val height = confettiLayer.height
        if (width <= 0 || height <= 0) return

        repeat(CONFETTI_COUNT) { index ->
            val fromLeft = index % 2 == 0
            val size = dp(random.nextInt(6, 12))
            val piece = View(confettiLayer.context).apply {
                background = GradientDrawable().apply {
                    shape = if (index % 3 == 0) {
                        GradientDrawable.OVAL
                    } else {
                        GradientDrawable.RECTANGLE
                    }
                    setColor(CONFETTI_COLORS[index % CONFETTI_COLORS.size])
                    cornerRadius = dp(2).toFloat()
                }
                rotation = random.nextInt(0, 180).toFloat()
            }
            confettiLayer.addView(piece, FrameLayout.LayoutParams(size, size * 2))
            piece.x = if (fromLeft) -size.toFloat() else width.toFloat()
            piece.y = random.nextInt(height / 5, (height * 4) / 5).toFloat()

            val horizontalTarget = if (fromLeft) {
                random.nextInt(width / 3, width).toFloat()
            } else {
                random.nextInt(0, (width * 2) / 3).toFloat()
            }
            val verticalTarget = (piece.y + random.nextInt(-height / 4, height / 3))
                .coerceIn(0f, height.toFloat())
            val animator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(piece, View.X, piece.x, horizontalTarget),
                    ObjectAnimator.ofFloat(piece, View.Y, piece.y, verticalTarget),
                    ObjectAnimator.ofFloat(piece, View.ROTATION, piece.rotation, piece.rotation + 540f),
                    ObjectAnimator.ofFloat(piece, View.ALPHA, 0f, 1f, 1f, 0f)
                )
                startDelay = random.nextLong(0L, 1_800L)
                duration = random.nextLong(1_800L, 3_200L)
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        confettiLayer.removeView(piece)
                    }
                })
                start()
            }
            runningAnimators += animator
        }
    }

    private fun revealContinueButton() {
        if (continueButton.visibility == View.VISIBLE && continueButton.isEnabled) return
        continueButton.visibility = View.VISIBLE
        continueButton.isEnabled = true
        continueButton.animate()
            .alpha(1f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dp(value: Int): Int {
        return (value * confettiLayer.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CONFETTI_COUNT = 72
        private val CONFETTI_COLORS = intArrayOf(
            Color.parseColor("#E7B83F"),
            Color.parseColor("#9C3029"),
            Color.parseColor("#26706A"),
            Color.parseColor("#F3D98C")
        )
    }
}
