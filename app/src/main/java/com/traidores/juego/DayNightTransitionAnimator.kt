package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnPreDraw

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
    private var generation = 0
    private var presentationStartedAtMs = 0L
    private var presentationDurationMs = 0L
    private var pendingFinish: Runnable? = null
    private var fallbackReveal: Runnable? = null
    private val musicCue = Runnable {
        if (running) onMusicCue()
    }

    fun start(
        spec: GameplayTransitionSpec,
        fromPeriod: GameplayPeriod,
        durationMs: Long
    ) {
        cancel()
        val token = generation
        presentationDurationMs = durationMs.coerceAtLeast(MIN_DURATION_MS)
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

        overlay.doOnPreDraw {
            if (running && generation == token) animate(spec, fromPeriod)
        }
    }

    fun cancel() {
        generation += 1
        pendingFinish?.let(handler::removeCallbacks)
        pendingFinish = null
        fallbackReveal?.let(handler::removeCallbacks)
        fallbackReveal = null
        running = false
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        handler.removeCallbacks(musicCue)
        EssentialViewAnimation.clear(overlay, sun, moon, title)
        overlay.visibility = View.GONE
        overlay.alpha = 1f
    }

    private fun animate(spec: GameplayTransitionSpec, fromPeriod: GameplayPeriod) {
        val width = overlay.width.toFloat()
        val height = overlay.height.toFloat()
        if (width <= 0f || height <= 0f) {
            val token = generation
            overlay.doOnPreDraw {
                if (running && generation == token) animate(spec, fromPeriod)
            }
            return
        }
        presentationStartedAtMs = SystemClock.uptimeMillis()
        if (EssentialViewAnimation.requiresFallback(overlay)) {
            animateScaleIndependent(spec, fromPeriod, width, height)
            return
        }
        handler.postDelayed(musicCue, scaled(MUSIC_DELAY_MS))

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

    private fun animateScaleIndependent(
        spec: GameplayTransitionSpec,
        fromPeriod: GameplayPeriod,
        width: Float,
        height: Float
    ) {
        val entering = if (spec.period == GameplayPeriod.NIGHT) moon else sun
        val leaving = if (spec.period == GameplayPeriod.NIGHT) sun else moon
        val endX = if (spec.period == GameplayPeriod.NIGHT) {
            width * 0.20f - entering.width / 2f
        } else {
            width * 0.70f - entering.width / 2f
        }
        val endY = height * 0.10f
        entering.x = endX
        entering.y = endY
        entering.alpha = 1f
        leaving.alpha = if (fromPeriod == spec.period) 0f else 0.22f
        toBackground.alpha = 1f
        title.alpha = 1f
        title.scaleX = 1f
        title.scaleY = 1f
        EssentialViewAnimation.reveal(overlay, durationMs = 420L, fromScale = 1f)
        EssentialViewAnimation.slideIn(
            view = entering,
            fromX = if (spec.period == GameplayPeriod.NIGHT) -width * 0.34f else width * 0.34f,
            fromY = height * 0.48f,
            durationMs = (presentationDurationMs * 0.58f).toLong().coerceAtLeast(650L),
            finalAlpha = 1f
        )
        EssentialViewAnimation.reveal(
            view = title,
            durationMs = 480L,
            delayMs = 260L,
            fromScale = 0.82f
        )
        handler.postDelayed(musicCue, scaled(MUSIC_DELAY_MS))
        val fadeDuration = minOf(420L, presentationDurationMs / 4).coerceAtLeast(220L)
        val revealDelay = (presentationDurationMs - fadeDuration).coerceAtLeast(700L)
        fallbackReveal = Runnable {
            fallbackReveal = null
            if (!running) return@Runnable
            onRevealBackground(spec)
            EssentialViewAnimation.fadeOut(overlay, fadeDuration) { finish(spec) }
        }.also { handler.postDelayed(it, revealDelay) }
    }

    private fun finish(spec: GameplayTransitionSpec) {
        if (!running) return
        val remaining = GameplayPresentationTiming.remainingMs(
            presentationStartedAtMs, SystemClock.uptimeMillis(), presentationDurationMs
        )
        if (remaining > 0L) {
            // Animator duration scale must not remove the gameplay announcement.
            overlay.alpha = 1f
            title.alpha = 1f
            title.scaleX = 1f
            title.scaleY = 1f
            pendingFinish?.let(handler::removeCallbacks)
            pendingFinish = Runnable { finish(spec) }.also { handler.postDelayed(it, remaining) }
            return
        }
        pendingFinish = null
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
