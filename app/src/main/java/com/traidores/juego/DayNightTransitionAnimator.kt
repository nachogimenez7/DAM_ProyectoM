package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Path
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

internal class DayNightTransitionAnimator(
    private val handler: Handler,
    private val overlay: FrameLayout,
    private val fromBackground: ImageView,
    private val toBackground: ImageView,
    private val sun: ImageView,
    private val moon: ImageView,
    private val shade: View,
    private val title: TextView,
    private val backgroundFor: (GameplayPeriod) -> Int,
    private val onMusicCue: () -> Unit,
    private val onRevealBackground: (GameplayTransitionSpec) -> Unit,
    private val onFinished: (GameplayTransitionSpec) -> Unit
) {
    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null
    private var durationScale = 1f
    private val musicCue = Runnable {
        if (running) onMusicCue()
    }

    fun start(
        spec: GameplayTransitionSpec,
        fromPeriod: GameplayPeriod,
        durationMs: Long
    ) {
        cancel()
        durationScale = durationMs.coerceAtLeast(MIN_DURATION_MS).toFloat() / BASE_DURATION_MS
        running = true
        fromBackground.setImageResource(backgroundFor(fromPeriod))
        toBackground.setImageResource(backgroundFor(spec.period))
        toBackground.alpha = if (fromPeriod == spec.period) 1f else 0f
        shade.alpha = if (spec.period == GameplayPeriod.NIGHT) 0.48f else 0.26f
        title.text = spec.title
        title.alpha = 0f
        title.scaleX = 0.86f
        title.scaleY = 0.86f
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE

        handler.postDelayed(musicCue, scaled(MUSIC_DELAY_MS))
        overlay.post {
            if (running) animate(spec, fromPeriod)
        }
    }

    fun cancel() {
        running = false
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        handler.removeCallbacks(musicCue)
        overlay.visibility = View.GONE
        overlay.alpha = 1f
    }

    private fun animate(spec: GameplayTransitionSpec, fromPeriod: GameplayPeriod) {
        val width = overlay.width.toFloat()
        val height = overlay.height.toFloat()
        if (width <= 0f || height <= 0f) {
            finish(spec)
            return
        }

        val sunTopX = width * 0.70f - sun.width / 2f
        val moonTopX = width * 0.20f - moon.width / 2f
        val topY = height * 0.10f
        val lowerY = height + maxOf(sun.height, moon.height) * 0.12f
        val animators = mutableListOf<Animator>()

        if (spec.period == GameplayPeriod.NIGHT) {
            if (fromPeriod == GameplayPeriod.DAY) {
                sun.alpha = 1f
                moon.alpha = 0f
                animators += arcAnimator(
                    sun,
                    sunTopX,
                    topY,
                    width * 0.90f,
                    height * 0.48f,
                    width + sun.width * 0.15f,
                    lowerY
                )
                animators += fadeAnimator(sun, 1f, 0f, 1180L, 520L)
            } else {
                sun.alpha = 0f
            }
            animators += risingAnimator(
                moon,
                -moon.width.toFloat(),
                lowerY,
                width * 0.05f,
                height * 0.42f,
                moonTopX,
                topY
            )
        } else {
            if (fromPeriod == GameplayPeriod.NIGHT) {
                moon.alpha = 1f
                sun.alpha = 0f
                animators += arcAnimator(
                    moon,
                    moonTopX,
                    topY,
                    width * 0.05f,
                    height * 0.48f,
                    -moon.width.toFloat(),
                    lowerY
                )
                animators += fadeAnimator(moon, 1f, 0f, 1180L, 520L)
            } else {
                moon.alpha = 0f
            }
            animators += risingAnimator(
                sun,
                width + sun.width * 0.15f,
                lowerY,
                width * 0.88f,
                height * 0.42f,
                sunTopX,
                topY
            )
        }

        animators += ObjectAnimator.ofFloat(
            toBackground,
            View.ALPHA,
            toBackground.alpha,
            1f
        ).apply { duration = scaled(1450L) }
        animators += fadeAnimator(title, 0f, 1f, 480L, 420L)
        animators += ObjectAnimator.ofFloat(title, View.SCALE_X, 0.86f, 1f).apply {
            startDelay = scaled(480L)
            duration = scaled(420L)
        }
        animators += ObjectAnimator.ofFloat(title, View.SCALE_Y, 0.86f, 1f).apply {
            startDelay = scaled(480L)
            duration = scaled(420L)
        }
        animators += fadeAnimator(title, 1f, 0f, 1600L, 360L)
        // Justo cuando el overlay empieza a desvanecerse (todavia 100% opaco) intercambiamos
        // el mapa de fondo real al nuevo periodo. Asi el fade revela el mapa nuevo y no se
        // asoma por un frame el mapa de la fase anterior.
        animators += fadeAnimator(overlay, 1f, 0f, 1850L, 350L).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (running) onRevealBackground(spec)
                }
            })
        }

        animator = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (running) finish(spec)
                }
            })
            start()
        }
    }

    private fun finish(spec: GameplayTransitionSpec) {
        if (!running) return
        running = false
        animator = null
        handler.removeCallbacks(musicCue)
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        onFinished(spec)
    }

    private fun risingAnimator(
        view: View,
        startX: Float,
        startY: Float,
        controlX: Float,
        controlY: Float,
        endX: Float,
        endY: Float
    ): Animator {
        view.alpha = 0f
        return AnimatorSet().apply {
            playTogether(
                arcAnimator(view, startX, startY, controlX, controlY, endX, endY),
                fadeAnimator(view, 0f, 1f, 120L, 560L)
            )
        }
    }

    private fun arcAnimator(
        view: View,
        startX: Float,
        startY: Float,
        controlX: Float,
        controlY: Float,
        endX: Float,
        endY: Float
    ): ObjectAnimator {
        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(controlX, controlY, endX, endY)
        }
        return ObjectAnimator.ofFloat(view, View.X, View.Y, path).apply {
            duration = scaled(1800L)
        }
    }

    private fun fadeAnimator(
        view: View,
        from: Float,
        to: Float,
        delayMs: Long,
        durationMs: Long
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, from, to).apply {
            startDelay = scaled(delayMs)
            duration = scaled(durationMs)
        }
    }

    private fun scaled(valueMs: Long): Long {
        return (valueMs * durationScale).toLong().coerceAtLeast(1L)
    }

    private companion object {
        const val MUSIC_DELAY_MS = 1600L
        const val BASE_DURATION_MS = 2200f
        const val MIN_DURATION_MS = 1000L
    }
}
