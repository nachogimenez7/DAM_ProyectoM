package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

class AssigningRolesActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var dealingAnimator: AnimatorSet? = null
    private var dealingSoundPlayer: MediaPlayer? = null
    private var leavingScreen = false

    private val openGameRunnable = Runnable { openGame() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assigning_roles)
        val session = readSession() ?: LocalGameFactory.createSession()
        MusicManager.playGameIntro(this, session)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            leavingScreen = true
            handler.removeCallbacks(openGameRunnable)
            dealingAnimator?.removeAllListeners()
            dealingAnimator?.cancel()
            releaseDealingSound()
            finish()
        }

        findViewById<FrameLayout>(R.id.assigningRoot).post {
            startDealingAnimation()
        }
        handler.postDelayed(openGameRunnable, ANIMATION_FALLBACK_MS)
    }

    private fun startDealingAnimation() {
        val root = findViewById<FrameLayout>(R.id.assigningRoot)
        val stage = findViewById<FrameLayout>(R.id.animationStage)
        val openHands = findViewById<ImageView>(R.id.assigningHands)
        val dealingHands = findViewById<ImageView>(R.id.assigningDealingHands)
        val leftCard = findViewById<ImageView>(R.id.shuffleCardLeft)
        val rightCard = findViewById<ImageView>(R.id.shuffleCardRight)
        val finalCard = findViewById<ImageView>(R.id.finalRoleCard)
        val status = findViewById<TextView>(R.id.assigningStatus)

        val cardViews = listOf(leftCard, rightCard, finalCard)
        val deckOffset = root.width * 0.07f
        openHands.translationY = dp(34).toFloat()
        openHands.scaleX = 0.98f
        openHands.scaleY = 0.98f
        dealingHands.translationY = dp(12).toFloat()
        dealingHands.scaleX = 0.985f
        dealingHands.scaleY = 0.985f
        leftCard.rotation = -2f
        rightCard.rotation = 2f
        cardViews.forEach {
            it.translationX = deckOffset
            it.scaleX = 0.86f
            it.scaleY = 0.86f
        }

        val entrance = AnimatorSet().apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, openHands.translationY, 0f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 0.98f, 1f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 0.98f, 1f),
                ObjectAnimator.ofFloat(status, View.ALPHA, 0f, 1f)
            )
        }

        val changePose = AnimatorSet().apply {
            duration = 360L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 1f, 1.025f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 1f, 1.025f),
                ObjectAnimator.ofFloat(dealingHands, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(dealingHands, View.TRANSLATION_Y, dealingHands.translationY, 0f),
                ObjectAnimator.ofFloat(dealingHands, View.SCALE_X, 0.985f, 1f),
                ObjectAnimator.ofFloat(dealingHands, View.SCALE_Y, 0.985f, 1f)
            )
        }

        val revealDeck = AnimatorSet().apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            playTogether(
                *cardViews.flatMap { card ->
                    listOf(
                        ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(card, View.SCALE_X, 0.86f, 1f),
                        ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.86f, 1f)
                    )
                }.toTypedArray()
            )
        }

        val shuffleLeft = shuffleBeat(
            leftCard = leftCard,
            rightCard = rightCard,
            hands = dealingHands,
            leftX = deckOffset - dp(52),
            rightX = deckOffset + dp(52),
            leftRotation = -9f,
            rightRotation = 9f,
            handsY = dp(3).toFloat()
        )
        val shuffleRight = shuffleBeat(
            leftCard = leftCard,
            rightCard = rightCard,
            hands = dealingHands,
            leftX = deckOffset + dp(30),
            rightX = deckOffset - dp(30),
            leftRotation = 6f,
            rightRotation = -6f,
            handsY = -dp(2).toFloat()
        )
        val shuffleSettle = shuffleBeat(
            leftCard = leftCard,
            rightCard = rightCard,
            hands = dealingHands,
            leftX = deckOffset - dp(5),
            rightX = deckOffset + dp(5),
            leftRotation = -2f,
            rightRotation = 2f,
            handsY = 0f,
            duration = 280L
        )

        val dealDistance = (root.width * 0.27f).coerceAtLeast(dp(150).toFloat())
        val dealY = -(root.height * 0.06f)
        val dealLeft = AnimatorSet().apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(leftCard, View.TRANSLATION_X, -dealDistance),
                ObjectAnimator.ofFloat(leftCard, View.TRANSLATION_Y, dealY),
                ObjectAnimator.ofFloat(leftCard, View.ROTATION, -12f),
                ObjectAnimator.ofFloat(leftCard, View.SCALE_X, 1f, 0.9f),
                ObjectAnimator.ofFloat(leftCard, View.SCALE_Y, 1f, 0.9f)
            )
        }
        val dealRight = AnimatorSet().apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(rightCard, View.TRANSLATION_X, dealDistance),
                ObjectAnimator.ofFloat(rightCard, View.TRANSLATION_Y, dealY),
                ObjectAnimator.ofFloat(rightCard, View.ROTATION, 12f),
                ObjectAnimator.ofFloat(rightCard, View.SCALE_X, 1f, 0.9f),
                ObjectAnimator.ofFloat(rightCard, View.SCALE_Y, 1f, 0.9f)
            )
        }

        val focusFinalCard = AnimatorSet().apply {
            duration = 480L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(leftCard, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(rightCard, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1f, 1.14f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1f, 1.14f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_X, deckOffset, 0f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, 0f, -dp(7).toFloat()),
                ObjectAnimator.ofFloat(dealingHands, View.ALPHA, 1f, 0.82f),
                ObjectAnimator.ofFloat(dealingHands, View.TRANSLATION_Y, 0f, dp(10).toFloat()),
                ObjectAnimator.ofFloat(status, View.ALPHA, 1f, 0.72f, 1f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "TU CARTA ESTA LISTA"
                }
            })
        }

        val holdFinalCard = ObjectAnimator.ofFloat(finalCard, View.ALPHA, 1f, 1f).apply {
            duration = 600L
        }
        val exit = AnimatorSet().apply {
            duration = 350L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(stage, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(findViewById<ImageButton>(R.id.btnBack), View.ALPHA, 1f, 0f)
            )
        }

        playDealingSound()
        dealingAnimator = AnimatorSet().apply {
            playSequentially(
                entrance,
                changePose,
                revealDeck,
                shuffleLeft,
                shuffleRight,
                shuffleSettle,
                dealLeft,
                dealRight,
                focusFinalCard,
                holdFinalCard,
                exit
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    handler.removeCallbacks(openGameRunnable)
                    openGame()
                }
            })
            start()
        }
    }

    private fun shuffleBeat(
        leftCard: ImageView,
        rightCard: ImageView,
        hands: ImageView,
        leftX: Float,
        rightX: Float,
        leftRotation: Float,
        rightRotation: Float,
        handsY: Float,
        duration: Long = 250L
    ): AnimatorSet {
        return AnimatorSet().apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(leftCard, View.TRANSLATION_X, leftX),
                ObjectAnimator.ofFloat(rightCard, View.TRANSLATION_X, rightX),
                ObjectAnimator.ofFloat(leftCard, View.ROTATION, leftRotation),
                ObjectAnimator.ofFloat(rightCard, View.ROTATION, rightRotation),
                ObjectAnimator.ofFloat(hands, View.TRANSLATION_Y, handsY),
                ObjectAnimator.ofFloat(hands, View.SCALE_X, 1f, 1.012f, 1f),
                ObjectAnimator.ofFloat(hands, View.SCALE_Y, 1f, 1.012f, 1f)
            )
        }
    }

    private fun playDealingSound() {
        releaseDealingSound()
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val soundOn = preferences.getBoolean("sound_on", true)
        val volume = preferences.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        dealingSoundPlayer = MediaPlayer.create(this, R.raw.card_shuffle_deal)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (dealingSoundPlayer === completed) {
                    dealingSoundPlayer = null
                }
                completed.release()
            }
            start()
        }
    }

    private fun releaseDealingSound() {
        dealingSoundPlayer?.runCatching {
            stop()
            release()
        }
        dealingSoundPlayer = null
    }

    private fun openGame() {
        if (leavingScreen || isFinishing || isDestroyed) return
        leavingScreen = true
        handler.removeCallbacks(openGameRunnable)
        releaseDealingSound()
        val session = readSession() ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        startActivity(
            Intent(this, GameplayMockActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(GameplayMockActivity.EXTRA_TEMA, GameplayTableUi.themeForMapKey(session.mapKey))
                .putExtra(GameplayMockActivity.EXTRA_ES_NOCHE, false)
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(openGameRunnable)
        dealingAnimator?.removeAllListeners()
        dealingAnimator?.cancel()
        dealingAnimator = null
        releaseDealingSound()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession
    }

    companion object {
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val ANIMATION_FALLBACK_MS = 4800L
    }
}
