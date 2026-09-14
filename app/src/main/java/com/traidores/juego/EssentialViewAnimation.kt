package com.traidores.juego

import android.provider.Settings
import android.view.View
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap

/**
 * Presentaciones de reglas que deben seguir siendo visibles cuando Android o un emulador
 * ponen las escalas de animación en cero. Este motor no usa Animator ni Animation: avanza las
 * propiedades con el reloj monotónico y un callback de dibujo por cuadro. BlueStacks también
 * puede anular las animaciones clásicas de View cuando sus tres escalas globales están en cero.
 */
internal object EssentialViewAnimation {
    private val generations = WeakHashMap<View, Int>()
    private val decelerate = DecelerateInterpolator()

    fun requiresFallback(systemAnimatorScale: Float): Boolean = systemAnimatorScale <= 0f

    fun requiresFallback(view: View): Boolean {
        fun globalScale(name: String): Float = runCatching {
            Settings.Global.getFloat(
                view.context.contentResolver,
                name,
                1f
            )
        }.getOrDefault(1f)
        val animatorScale = globalScale(Settings.Global.ANIMATOR_DURATION_SCALE)
        val transitionScale = globalScale(Settings.Global.TRANSITION_ANIMATION_SCALE)
        val windowScale = globalScale(Settings.Global.WINDOW_ANIMATION_SCALE)
        val animatorsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
        return !animatorsEnabled ||
            requiresFallback(animatorScale) ||
            requiresFallback(transitionScale) ||
            requiresFallback(windowScale)
    }

    fun reveal(
        view: View,
        durationMs: Long,
        delayMs: Long = 0L,
        fromScale: Float = 0.9f,
        finalAlpha: Float = 1f,
        onFinished: (() -> Unit)? = null
    ) {
        cancel(view)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = fromScale
        view.scaleY = fromScale
        runFrames(view, durationMs, delayMs, onFinished) { progress ->
            view.alpha = lerp(0f, finalAlpha, progress)
            val scale = lerp(fromScale, 1f, progress)
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    fun slideIn(
        view: View,
        fromX: Float = 0f,
        fromY: Float = 0f,
        durationMs: Long,
        delayMs: Long = 0L,
        finalAlpha: Float = 1f,
        onFinished: (() -> Unit)? = null
    ) {
        cancel(view)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationX = fromX
        view.translationY = fromY
        runFrames(view, durationMs, delayMs, onFinished) { progress ->
            view.alpha = lerp(0f, finalAlpha, progress)
            view.translationX = lerp(fromX, 0f, progress)
            view.translationY = lerp(fromY, 0f, progress)
        }
    }

    fun fadeOut(
        view: View,
        durationMs: Long,
        onFinished: () -> Unit
    ) {
        cancel(view)
        view.alpha = 1f
        runFrames(view, durationMs, 0L, onFinished) { progress ->
            view.alpha = lerp(1f, 0f, progress)
        }
    }

    fun flyOut(
        view: View,
        toX: Float,
        toY: Float,
        durationMs: Long,
        onFinished: () -> Unit
    ) {
        cancel(view)
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.rotation = 0f
        runFrames(view, durationMs, 0L, onFinished) { progress ->
            view.translationX = lerp(0f, toX, progress)
            view.translationY = lerp(0f, toY, progress)
            view.rotation = lerp(0f, 160f, progress)
            view.alpha = lerp(1f, 0f, progress)
        }
    }

    fun clear(vararg views: View) {
        views.forEach(::cancel)
    }

    private fun runFrames(
        view: View,
        durationMs: Long,
        delayMs: Long,
        onFinished: (() -> Unit)?,
        onFrame: (Float) -> Unit
    ) {
        val token = nextGeneration(view)
        val startsAt = SystemClock.uptimeMillis() + delayMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(1L)
        val frame = object : Runnable {
            override fun run() {
                if (!isCurrent(view, token)) return
                val now = SystemClock.uptimeMillis()
                if (now < startsAt) {
                    view.postOnAnimation(this)
                    return
                }
                val rawProgress = ((now - startsAt).toFloat() / safeDuration).coerceIn(0f, 1f)
                onFrame(decelerate.getInterpolation(rawProgress))
                if (rawProgress < 1f) {
                    view.postOnAnimation(this)
                } else if (isCurrent(view, token)) {
                    onFinished?.invoke()
                }
            }
        }
        view.postOnAnimation(frame)
    }

    @Synchronized
    private fun nextGeneration(view: View): Int {
        val next = (generations[view] ?: 0) + 1
        generations[view] = next
        return next
    }

    @Synchronized
    private fun isCurrent(view: View, token: Int): Boolean = generations[view] == token

    private fun cancel(view: View) {
        synchronized(this) {
            generations[view] = (generations[view] ?: 0) + 1
        }
        view.clearAnimation()
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * progress
}
