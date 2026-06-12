package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Path
import android.media.MediaPlayer
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

internal class DayNightTransitionAnimator(
    private val context: Context,
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
    private val onFinished: (GameplayTransitionSpec) -> Unit
) {
    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null
    private var soundPlayer: MediaPlayer? = null
    private val musicCue = Runnable {
        if (running) onMusicCue()
    }

    fun start(spec: GameplayTransitionSpec, fromPeriod: GameplayPeriod) {
        cancel()
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

        playSound(spec.period)
        handler.postDelayed(musicCue, MUSIC_DELAY_MS)
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
        releaseSound()
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
        ).apply { duration = 1450L }
        animators += fadeAnimator(title, 0f, 1f, 480L, 420L)
        animators += ObjectAnimator.ofFloat(title, View.SCALE_X, 0.86f, 1f).apply {
            startDelay = 480L
            duration = 420L
        }
        animators += ObjectAnimator.ofFloat(title, View.SCALE_Y, 0.86f, 1f).apply {
            startDelay = 480L
            duration = 420L
        }
        animators += fadeAnimator(title, 1f, 0f, 1600L, 360L)
        animators += fadeAnimator(overlay, 1f, 0f, 1850L, 350L)

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
        releaseSound()
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
            duration = 1800L
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
            startDelay = delayMs
            duration = durationMs
        }
    }

    private fun playSound(period: GameplayPeriod) {
        releaseSound()
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val soundOn = preferences.getBoolean("sound_on", true)
        val volume = preferences.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        val soundRes = if (period == GameplayPeriod.NIGHT) {
            R.raw.transition_night
        } else {
            R.raw.transition_day
        }
        soundPlayer = MediaPlayer.create(context, soundRes)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (soundPlayer === completed) soundPlayer = null
                completed.release()
            }
            start()
        }
    }

    private fun releaseSound() {
        soundPlayer?.runCatching {
            stop()
            release()
        }
        soundPlayer = null
    }

    private companion object {
        const val PREFS_NAME = "TraidoresPrefs"
        const val MUSIC_DELAY_MS = 1600L
    }
}
