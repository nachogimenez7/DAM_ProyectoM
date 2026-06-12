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

internal class DeathRevealAnimator(
    private val context: Context,
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
    private val onFinished: () -> Unit
) {
    var running: Boolean = false
        private set

    private var animator: AnimatorSet? = null
    private var soundPlayer: MediaPlayer? = null

    fun start(player: GamePlayer, revealRole: Boolean) {
        cancel()
        running = true
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
        playSound()

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.94f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.94f, 1f)
            )
            duration = 280L
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
            duration = 420L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val reveal = if (revealRole) roleRevealAnimation() else hiddenRoleAnimation()
        val hold = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1050L }
        val exit = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f).apply {
            duration = 320L
            interpolator = AccelerateInterpolator()
        }

        animator = AnimatorSet().apply {
            playSequentially(entrance, impact, reveal, hold, exit)
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

    private fun roleRevealAnimation(): Animator {
        val flipOut = ObjectAnimator.ofFloat(card, View.ROTATION_Y, 0f, 90f).apply {
            duration = 230L
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
            duration = 260L
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
            duration = 420L
            interpolator = DecelerateInterpolator()
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
        soundPlayer = MediaPlayer.create(context, R.raw.death_reveal)?.apply {
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
    }
}
