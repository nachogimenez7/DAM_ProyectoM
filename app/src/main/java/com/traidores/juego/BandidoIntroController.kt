package com.traidores.juego

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.AnimationDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/**
 * Presentación breve de Bandido Games integrada en la pantalla principal.
 *
 * La secuencia completa se reproduce en cada arranque real. `MainActivity` solo llama a
 * [show] cuando no está restaurando una instancia anterior, así que volver desde otra
 * pantalla no vuelve a mostrarla. El ladrido respeta las preferencias de efectos.
 */
class BandidoIntroController(
    private val activity: AppCompatActivity,
    private val onFinished: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val overlay: View = activity.findViewById(R.id.brandIntroOverlay)
    private val idleLogo: ImageView = activity.findViewById(R.id.bandidoIntroIdle)
    private val barkLogo: ImageView = activity.findViewById(R.id.bandidoIntroBark)
    private val goldPulse: View = activity.findViewById(R.id.bandidoIntroGoldPulse)

    private var barkPlayer: MediaPlayer? = null
    private var finished = false

    fun show() {
        finished = false
        resetViews()
        overlay.visibility = View.VISIBLE
        overlay.setOnClickListener(null)
        playFullIntro()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        releaseBark()
        overlay.animate().cancel()
        idleLogo.animate().cancel()
        barkLogo.animate().cancel()
        goldPulse.animate().cancel()
    }

    private fun playFullIntro() {
        idleLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(LOGO_ENTER_MS)
            .setInterpolator(OvershootInterpolator(0.7f))
            .start()

        handler.postDelayed({
            if (finished) return@postDelayed
            idleLogo.alpha = 0f
            barkLogo.alpha = 1f
            startBarkAnimation()
            barkLogo.animate()
                .scaleX(1.045f)
                .scaleY(1.045f)
                .translationY(-activity.resources.displayMetrics.density * 24f)
                .rotation(-1.2f)
                .setDuration(BARK_OPEN_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (finished) return@withEndAction
                    barkLogo.animate()
                        .scaleX(1.018f)
                        .scaleY(1.018f)
                        .translationY(-activity.resources.displayMetrics.density * 7f)
                        .rotation(0f)
                        .setDuration(HAT_SETTLE_MS)
                        .setInterpolator(OvershootInterpolator(0.9f))
                        .start()
                }
                .start()
            playBarkIfAvailable()
        }, BARK_START_MS)

        handler.postDelayed({
            if (finished) return@postDelayed
            barkLogo.animate().cancel()
            barkLogo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .rotation(0f)
                .setDuration(HAT_RETURN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, BARK_RETURN_START_MS)

        handler.postDelayed({
            if (finished) return@postDelayed
            stopBarkAnimation()
            barkLogo.alpha = 0f
            barkLogo.scaleX = 1f
            barkLogo.scaleY = 1f
            barkLogo.translationY = 0f
            barkLogo.rotation = 0f
            idleLogo.alpha = 1f
            idleLogo.animate()
                .scaleX(1.015f)
                .scaleY(1.015f)
                .setDuration(120L)
                .withEndAction {
                    idleLogo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start()
                }
                .start()
        }, BARK_CLOSE_MS)

        handler.postDelayed({
            if (finished) return@postDelayed
            goldPulse.animate()
                .alpha(1f)
                .setDuration(120L)
                .withEndAction {
                    goldPulse.animate().alpha(0f).setDuration(300L).start()
                }
                .start()
        }, GOLD_PULSE_MS)

        handler.postDelayed(
            { finish() },
            FULL_INTRO_DURATION_MS
        )
    }

    private fun finish(fadeDuration: Long = INTRO_EXIT_MS) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        releaseBark()

        overlay.animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
                onFinished()
            }
            .start()
    }

    private fun resetViews() {
        overlay.animate().cancel()
        idleLogo.animate().cancel()
        barkLogo.animate().cancel()
        goldPulse.animate().cancel()

        overlay.alpha = 1f
        idleLogo.alpha = 0f
        idleLogo.scaleX = 0.88f
        idleLogo.scaleY = 0.88f
        idleLogo.translationY = 0f
        barkLogo.alpha = 0f
        barkLogo.scaleX = 1f
        barkLogo.scaleY = 1f
        barkLogo.translationY = 0f
        barkLogo.rotation = 0f
        goldPulse.alpha = 0f
        stopBarkAnimation()
    }

    private fun startBarkAnimation() {
        (barkLogo.drawable as? AnimationDrawable)?.run {
            stop()
            selectDrawable(0)
            start()
        }
    }

    private fun stopBarkAnimation() {
        (barkLogo.drawable as? AnimationDrawable)?.run {
            stop()
            selectDrawable(0)
        }
    }

    private fun playBarkIfAvailable() {
        val preferences = AudioPreferences.preferences(activity)
        if (!AudioPreferences.areEffectsEnabled(preferences)) return
        val volume = AudioPreferences.effectsVolume(preferences)
        if (volume <= 0f) return

        releaseBark()
        barkPlayer = MediaPlayer.create(activity, R.raw.sfx_bandido_bark)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { releaseBark() }
            setOnErrorListener { _, _, _ ->
                releaseBark()
                true
            }
            start()
        }
    }

    private fun releaseBark() {
        val current = barkPlayer
        barkPlayer = null
        current?.runCatching {
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
    }

    companion object {
        private const val LOGO_ENTER_MS = 420L
        private const val BARK_START_MS = 620L
        private const val BARK_OPEN_MS = 115L
        private const val HAT_SETTLE_MS = 165L
        private const val BARK_RETURN_START_MS = 950L
        private const val HAT_RETURN_MS = 220L
        private const val BARK_CLOSE_MS = 1_170L
        private const val GOLD_PULSE_MS = 1_260L
        private const val FULL_INTRO_DURATION_MS = 1_900L
        private const val INTRO_EXIT_MS = 300L

    }
}
