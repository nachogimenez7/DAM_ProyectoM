package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class RolePreviewAnimator(
    private val overlay: FrameLayout,
    private val content: FrameLayout,
    private val mapBackground: ImageView,
    private val roleImage: ImageView,
    private val roleName: TextView,
    private val roleTeam: TextView,
    private val roleFunction: TextView,
    private val roleAdvice: TextView,
    private val dp: (Int) -> Int
) {
    private var animator: AnimatorSet? = null

    fun reserveVisible() {
        cancel()
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        content.alpha = 0f
    }

    fun show(initialReveal: Boolean) {
        cancel()
        overlay.alpha = if (initialReveal) 1f else 0f
        content.alpha = 1f
        content.rotationY = -82f
        content.scaleX = 0.34f
        content.scaleY = 0.72f
        content.cameraDistance = content.resources.displayMetrics.density * 9000f
        mapBackground.alpha = 0f
        roleImage.alpha = 0f
        roleImage.translationY = dp(12).toFloat()
        roleName.alpha = 0f
        roleTeam.alpha = 0f
        roleFunction.alpha = 0f
        roleAdvice.alpha = 0f
        overlay.visibility = View.VISIBLE

        val overlayEntrance = ObjectAnimator.ofFloat(
            overlay,
            View.ALPHA,
            overlay.alpha,
            1f
        ).apply {
            duration = if (initialReveal) 1L else 180L
        }
        val cardTurn = AnimatorSet().apply {
            duration = if (initialReveal) 460L else 340L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(content, View.ROTATION_Y, -82f, 0f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, 0.34f, 1f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, 0.72f, 1f)
            )
        }
        val revealDetails = AnimatorSet().apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(mapBackground, View.ALPHA, 0f, 0.68f),
                ObjectAnimator.ofFloat(roleImage, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(roleImage, View.TRANSLATION_Y, dp(12).toFloat(), 0f),
                ObjectAnimator.ofFloat(roleName, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(roleTeam, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(roleFunction, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(roleAdvice, View.ALPHA, 0f, 1f)
            )
        }
        animator = AnimatorSet().apply {
            playTogether(
                overlayEntrance,
                AnimatorSet().apply {
                    startDelay = 80L
                    playSequentially(cardTurn, revealDetails)
                }
            )
            start()
        }
    }

    fun dismiss(onFinished: () -> Unit) {
        cancel()
        animator = AnimatorSet().apply {
            duration = 180L
            interpolator = AccelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(overlay, View.ALPHA, overlay.alpha, 0f),
                ObjectAnimator.ofFloat(content, View.SCALE_X, content.scaleX, 0.82f),
                ObjectAnimator.ofFloat(content, View.SCALE_Y, content.scaleY, 0.82f),
                ObjectAnimator.ofFloat(content, View.ROTATION_Y, content.rotationY, 18f)
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
        cancel()
        resetAndHide()
    }

    private fun cancel() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun resetAndHide() {
        overlay.visibility = View.GONE
        overlay.alpha = 1f
        content.alpha = 1f
        content.rotationY = 0f
        content.scaleX = 1f
        content.scaleY = 1f
    }
}
