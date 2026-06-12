package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

class TraitorRevealAnimator(
    private val overlay: FrameLayout,
    private val content: LinearLayout,
    private val cards: LinearLayout,
    private val handler: Handler
) {
    private var animator: AnimatorSet? = null
    private var dismissRunnable: Runnable? = null

    fun show(cardViews: List<View>, durationMs: Long, onDismissRequested: () -> Unit) {
        cancelAnimation()
        removeScheduledDismiss()
        cardViews.forEach { card ->
            card.alpha = 0f
            card.translationY = dp(42).toFloat()
            cards.addView(card)
        }

        overlay.alpha = 0f
        content.scaleX = 0.96f
        content.scaleY = 0.96f
        overlay.visibility = View.VISIBLE

        val animators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f).apply {
                duration = 260L
            },
            ObjectAnimator.ofFloat(content, View.SCALE_X, 0.96f, 1f).apply {
                duration = 320L
            },
            ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.96f, 1f).apply {
                duration = 320L
            }
        )
        cardViews.forEachIndexed { index, card ->
            animators += ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).apply {
                startDelay = 120L + index * 150L
                duration = 320L
            }
            animators += ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, card.translationY, 0f).apply {
                startDelay = 120L + index * 150L
                duration = 350L
            }
        }
        animator = AnimatorSet().apply {
            interpolator = DecelerateInterpolator()
            playTogether(animators)
            start()
        }
        dismissRunnable = Runnable(onDismissRequested).also {
            handler.postDelayed(it, durationMs)
        }
    }

    fun dismiss(onFinished: () -> Unit) {
        removeScheduledDismiss()
        cancelAnimation()
        animator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, overlay.alpha, 0f).apply {
                    duration = 220L
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetAndHide()
                    animator = null
                    onFinished()
                }
            })
            start()
        }
    }

    fun cancelAndHide() {
        removeScheduledDismiss()
        cancelAnimation()
        resetAndHide()
    }

    private fun cancelAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun removeScheduledDismiss() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
    }

    private fun resetAndHide() {
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        content.scaleX = 1f
        content.scaleY = 1f
        cards.removeAllViews()
    }

    private fun dp(value: Int): Int =
        (value * overlay.resources.displayMetrics.density).toInt()
}
