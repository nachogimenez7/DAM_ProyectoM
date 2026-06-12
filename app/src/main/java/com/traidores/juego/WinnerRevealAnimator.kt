package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

class WinnerRevealAnimator(
    private val overlay: FrameLayout,
    private val panel: FrameLayout,
    private val title: TextView,
    private val personalResult: TextView,
    private val shine: View,
    private val dp: (Int) -> Int
) {
    private var animator: AnimatorSet? = null
    private var cards = emptyList<View>()

    fun show(cardViews: List<View>, animate: Boolean, onAnimationFinished: () -> Unit) {
        cancel()
        cards = cardViews
        overlay.visibility = View.VISIBLE
        if (!animate) {
            settle()
            return
        }
        reset()

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_X, 0.72f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_Y, 0.72f, 1f),
                ObjectAnimator.ofFloat(panel, View.ALPHA, 0f, 1f)
            )
            duration = 520L
            interpolator = DecelerateInterpolator()
        }
        val headings = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, dp(10).toFloat(), 0f),
                ObjectAnimator.ofFloat(personalResult, View.ALPHA, 0f, 1f)
            )
            duration = 360L
            interpolator = DecelerateInterpolator()
        }
        val cardAnimators = cards.mapIndexed { index, card ->
            AnimatorSet().apply {
                startDelay = index * CARD_STAGGER_MS
                playTogether(
                    ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(16).toFloat(), 0f),
                    ObjectAnimator.ofFloat(card, View.SCALE_X, 0.9f, 1f),
                    ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.9f, 1f)
                )
                duration = 300L
                interpolator = DecelerateInterpolator()
            }
        }
        val cardsEntrance = AnimatorSet().apply { playTogether(cardAnimators) }
        val shineAnimation = ObjectAnimator.ofFloat(shine, View.ALPHA, 0f, 0.34f, 0f).apply {
            duration = 720L
            interpolator = AccelerateDecelerateInterpolator()
        }

        animator = AnimatorSet().apply {
            playSequentially(entrance, headings, cardsEntrance, shineAnimation)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    settle()
                    onAnimationFinished()
                }
            })
            start()
        }
    }

    fun settle() {
        cancel(end = true)
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        title.alpha = 1f
        title.translationY = 0f
        personalResult.alpha = 1f
        shine.alpha = 0f
        cards.forEach { card ->
            card.alpha = 1f
            card.translationY = 0f
            card.scaleX = 1f
            card.scaleY = 1f
        }
    }

    private fun reset() {
        overlay.alpha = 0f
        panel.alpha = 0f
        panel.scaleX = 0.72f
        panel.scaleY = 0.72f
        title.alpha = 0f
        title.translationY = dp(10).toFloat()
        personalResult.alpha = 0f
        shine.alpha = 0f
        cards.forEach { card ->
            card.alpha = 0f
            card.translationY = dp(16).toFloat()
            card.scaleX = 0.9f
            card.scaleY = 0.9f
        }
    }

    private fun cancel(end: Boolean = false) {
        animator?.removeAllListeners()
        if (end) animator?.end() else animator?.cancel()
        animator = null
    }

    private companion object {
        const val CARD_STAGGER_MS = 95L
    }
}
