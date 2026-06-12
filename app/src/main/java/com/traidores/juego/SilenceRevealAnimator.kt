package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.media.MediaPlayer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal class SilenceRevealAnimator(
    private val context: Context,
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
    private var soundPlayer: MediaPlayer? = null

    fun start(player: GamePlayer) {
        cancel()
        running = true
        playerName.text = player.name.uppercase()
        resetViews()
        overlay.visibility = View.VISIBLE
        playSound()

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
        val hold = ValueAnimator.ofFloat(0f, 1f).apply { duration = 900L }
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
        releaseSound()
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
        releaseSound()
        onFinished()
    }

    private fun playSound() {
        releaseSound()
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val soundOn = preferences.getBoolean("sound_on", true)
        val volume = preferences.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return
        soundPlayer = MediaPlayer.create(context, R.raw.silence_reveal)?.apply {
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
        const val REVEAL_GAP_MS = 300L
    }
}
