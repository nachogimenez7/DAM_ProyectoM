package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback

class AssigningRolesActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var dealingAnimator: AnimatorSet? = null
    private var leavingScreen = false

    private val openGameRunnable = Runnable { openGame() }

    private data class DealAnimationMetrics(
        val cardWidthDp: Int,
        val cardHeightDp: Int,
        val deckOffsetRatio: Float,
        val shuffleWideDp: Int,
        val shuffleNarrowDp: Int,
        val dealDistanceRatio: Float,
        val minDealDistanceDp: Int,
        val dealYRatio: Float,
        val finalScale: Float,
        val finalLiftDp: Int,
        val handsStartYDp: Int,
        val dealingHandsStartYDp: Int,
        val statusBottomMarginDp: Int,
        val statusTextSp: Float,
        val holdFinalMs: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assigning_roles)
        val session = readSession()
        if (session == null && intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty().isNotBlank()) {
            val onlineRoomId = intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
            Toast.makeText(
                this,
                "La sala perdio datos de partida. Volve a entrar desde Online o creen una sala nueva.",
                Toast.LENGTH_LONG
            ).show()
            OnlineRoomRecovery.clearIf(this, onlineRoomId)
            finish()
            return
        }
        val safeSession = session ?: LocalGameFactory.createSession()
        MusicManager.playGameIntro(this, safeSession)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveAssigningScreen()
            }
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { leaveAssigningScreen() }

        findViewById<FrameLayout>(R.id.assigningRoot).post {
            startDealingAnimation()
        }
        handler.postDelayed(openGameRunnable, ANIMATION_FALLBACK_MS)
    }

    private fun startDealingAnimation() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            startPortraitDealingAnimation()
            return
        }
        startLandscapeDealingAnimation()
    }

    private fun startPortraitDealingAnimation() {
        val root = findViewById<FrameLayout>(R.id.assigningRoot)
        val stage = findViewById<FrameLayout>(R.id.animationStage)
        val openHands = findViewById<ImageView>(R.id.assigningHands)
        val dealingHands = findViewById<ImageView>(R.id.assigningDealingHands)
        val leftCard = findViewById<ImageView>(R.id.shuffleCardLeft)
        val rightCard = findViewById<ImageView>(R.id.shuffleCardRight)
        val finalCard = findViewById<ImageView>(R.id.finalRoleCard)
        val status = findViewById<TextView>(R.id.assigningStatus)
        val table = findViewById<View>(R.id.assigningTable)

        openHands.setImageResource(R.drawable.assigning_vertical_hands_throw)
        dealingHands.setImageResource(R.drawable.assigning_roles_dealing_hands)

        val cardWidth = dp(124)
        val cardHeight = dp(196)
        val cardTop = (root.height * 0.29f).toInt()
        val cardSettleY = -root.height * 0.02f
        val cardBounceDown = cardSettleY + dp(12)

        listOf(leftCard, rightCard, finalCard).forEach { card ->
            card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
                width = cardWidth
                height = cardHeight
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = cardTop
            }
        }
        leftCard.visibility = View.GONE
        rightCard.visibility = View.GONE
        openHands.visibility = View.VISIBLE
        dealingHands.visibility = View.GONE
        table.layoutParams = (table.layoutParams as FrameLayout.LayoutParams).apply {
            width = (root.width * 0.9f).toInt()
            height = (root.height * 0.6f).toInt()
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            topMargin = cardTop - dp(94)
        }
        openHands.layoutParams = (openHands.layoutParams as FrameLayout.LayoutParams).apply {
            width = (root.width * 0.96f).toInt()
            height = (root.height * 0.34f).toInt().coerceAtLeast(dp(280))
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            topMargin = cardTop - dp(150)
        }
        table.alpha = 0f
        openHands.alpha = 0f
        openHands.translationY = -dp(92).toFloat()
        openHands.translationX = 0f
        openHands.scaleX = 0.96f
        openHands.scaleY = 0.96f

        status.layoutParams = (status.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(108)
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        status.text = "REPARTIENDO ROLES"

        finalCard.alpha = 0f
        finalCard.translationX = 0f
        finalCard.translationY = -dp(88).toFloat()
        finalCard.scaleX = 0.86f
        finalCard.scaleY = 0.86f
        finalCard.rotation = -2f
        stage.alpha = 1f

        val entrance = AnimatorSet().apply {
            duration = 760L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(stage, View.ALPHA, 0.55f, 1f),
                ObjectAnimator.ofFloat(table, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, -dp(92).toFloat(), 0f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, 0f, 0f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 0.96f, 1.04f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 0.96f, 1.04f),
                ObjectAnimator.ofFloat(finalCard, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, -dp(88).toFloat(), cardSettleY + dp(8)),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 0.86f, 1.04f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 0.86f, 1.04f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION, -2f, 0f),
                ObjectAnimator.ofFloat(status, View.ALPHA, 0f, 1f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "REPARTIENDO TU CARTA"
                }
            })
        }

        val settleCard = AnimatorSet().apply {
            duration = 360L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 1f, 1f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, 0f, dp(10).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 1.04f, 1.02f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 1.04f, 1.02f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, cardSettleY + dp(8), cardBounceDown, cardSettleY + dp(2)),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.04f, 1.0f, 1.06f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.04f, 1.0f, 1.06f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "LA CARTA CAE SOBRE LA MESA"
                }
            })
        }

        val releaseHands = AnimatorSet().apply {
            duration = 780L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, dp(10).toFloat(), -dp(96).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, 0f, 0f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 1.02f, 0.96f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 1.02f, 0.96f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.06f, 1.13f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.06f, 1.13f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, cardSettleY + dp(2), cardSettleY - dp(2))
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "EL DESTINO QUEDA SELLADO"
                }
            })
        }

        val holdFinalCard = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            val cardPulseX = ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.13f, 1.08f).apply {
                duration = 450L
                repeatCount = 3
                repeatMode = ValueAnimator.REVERSE
            }
            val cardPulseY = ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.13f, 1.08f).apply {
                duration = 450L
                repeatCount = 3
                repeatMode = ValueAnimator.REVERSE
            }
            val cardBreath = ObjectAnimator.ofFloat(
                finalCard,
                View.TRANSLATION_Y,
                cardSettleY - dp(2),
                cardSettleY + dp(5)
            ).apply {
                duration = 450L
                repeatCount = 3
                repeatMode = ValueAnimator.REVERSE
            }
            val statusPulse = ObjectAnimator.ofFloat(status, View.ALPHA, 1f, 0.72f).apply {
                duration = 450L
                repeatCount = 3
                repeatMode = ValueAnimator.REVERSE
            }
            playTogether(cardPulseX, cardPulseY, cardBreath, statusPulse)
        }

        val exit = AnimatorSet().apply {
            duration = 480L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(stage, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(table, View.ALPHA, table.alpha, 0f),
                ObjectAnimator.ofFloat(openHands, View.ALPHA, openHands.alpha, 0f),
                ObjectAnimator.ofFloat(findViewById<ImageButton>(R.id.btnBack), View.ALPHA, 1f, 0f)
            )
        }

        playDealingSound()
        dealingAnimator = AnimatorSet().apply {
            playSequentially(
                entrance,
                settleCard,
                releaseHands,
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

    private fun startLandscapeDealingAnimation() {
        val root = findViewById<FrameLayout>(R.id.assigningRoot)
        val stage = findViewById<FrameLayout>(R.id.animationStage)
        val openHands = findViewById<ImageView>(R.id.assigningHands)
        val dealingHands = findViewById<ImageView>(R.id.assigningDealingHands)
        val leftCard = findViewById<ImageView>(R.id.shuffleCardLeft)
        val rightCard = findViewById<ImageView>(R.id.shuffleCardRight)
        val finalCard = findViewById<ImageView>(R.id.finalRoleCard)
        val status = findViewById<TextView>(R.id.assigningStatus)

        openHands.setImageResource(R.drawable.assigning_roles_hands)
        dealingHands.setImageResource(R.drawable.assigning_roles_dealing_hands)
        listOf(openHands, dealingHands).forEach { hands ->
            hands.layoutParams = (hands.layoutParams as FrameLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = android.view.Gravity.CENTER
                topMargin = 0
            }
        }

        val metrics = dealAnimationMetrics()
        val cardViews = listOf(leftCard, rightCard, finalCard)
        cardViews.forEach { card ->
            card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
                width = dp(metrics.cardWidthDp)
                height = dp(metrics.cardHeightDp)
                gravity = android.view.Gravity.CENTER
            }
        }
        status.layoutParams = (status.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = dp(metrics.statusBottomMarginDp)
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.statusTextSp)

        val deckOffset = root.width * metrics.deckOffsetRatio
        openHands.translationY = dp(metrics.handsStartYDp).toFloat()
        openHands.scaleX = 0.98f
        openHands.scaleY = 0.98f
        dealingHands.translationY = dp(metrics.dealingHandsStartYDp).toFloat()
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
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "BARAJANDO DESTINOS"
                }
            })
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
            leftX = deckOffset - dp(metrics.shuffleWideDp),
            rightX = deckOffset + dp(metrics.shuffleWideDp),
            leftRotation = -9f,
            rightRotation = 9f,
            handsY = dp(3).toFloat()
        )
        val shuffleRight = shuffleBeat(
            leftCard = leftCard,
            rightCard = rightCard,
            hands = dealingHands,
            leftX = deckOffset + dp(metrics.shuffleNarrowDp),
            rightX = deckOffset - dp(metrics.shuffleNarrowDp),
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

        val dealDistance = (root.width * metrics.dealDistanceRatio)
            .coerceAtLeast(dp(metrics.minDealDistanceDp).toFloat())
        val dealY = -(root.height * metrics.dealYRatio)
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
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    status.text = "LA MESA ELIGE"
                }
            })
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
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1f, metrics.finalScale),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1f, metrics.finalScale),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_X, deckOffset, 0f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, 0f, -dp(metrics.finalLiftDp).toFloat()),
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
            duration = metrics.holdFinalMs
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

    private fun dealAnimationMetrics(): DealAnimationMetrics {
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (portrait) {
            DealAnimationMetrics(
                cardWidthDp = 86,
                cardHeightDp = 138,
                deckOffsetRatio = 0.01f,
                shuffleWideDp = 32,
                shuffleNarrowDp = 20,
                dealDistanceRatio = 0.22f,
                minDealDistanceDp = 84,
                dealYRatio = 0.08f,
                finalScale = 1.16f,
                finalLiftDp = 10,
                handsStartYDp = 28,
                dealingHandsStartYDp = 10,
                statusBottomMarginDp = 44,
                statusTextSp = 21f,
                holdFinalMs = 820L
            )
        } else {
            DealAnimationMetrics(
                cardWidthDp = 82,
                cardHeightDp = 131,
                deckOffsetRatio = 0.07f,
                shuffleWideDp = 52,
                shuffleNarrowDp = 30,
                dealDistanceRatio = 0.27f,
                minDealDistanceDp = 150,
                dealYRatio = 0.06f,
                finalScale = 1.14f,
                finalLiftDp = 7,
                handsStartYDp = 34,
                dealingHandsStartYDp = 12,
                statusBottomMarginDp = 16,
                statusTextSp = 21f,
                holdFinalMs = 600L
            )
        }
    }

    private fun playDealingSound() {
        GameplayAudioDirector.play(this, GameSound.CARD_DEAL)
    }

    private fun openGame() {
        if (leavingScreen || isFinishing || isDestroyed) return
        leavingScreen = true
        handler.removeCallbacks(openGameRunnable)
        val session = readSession()
        if (session == null && intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty().isNotBlank()) {
            val onlineRoomId = intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
            Toast.makeText(
                this,
                "La sala perdio datos de partida. Volve a entrar desde Online o creen una sala nueva.",
                Toast.LENGTH_LONG
            ).show()
            OnlineRoomRecovery.clearIf(this, onlineRoomId)
            finish()
            return
        }
        val safeSession = session ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        startActivity(
            Intent(this, GameplayMockActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, safeSession)
                .putExtra(GameplayMockActivity.EXTRA_TEMA, GameplayTableUi.themeForMapKey(safeSession.mapKey))
                .putExtra(GameplayMockActivity.EXTRA_ES_NOCHE, false)
                .putExtra(
                    GameplayMockActivity.EXTRA_ONLINE_PARTIDA_ID,
                    intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
                )
                .putExtra(
                    GameplayMockActivity.EXTRA_ONLINE_PLAYER_ID,
                    intent.getStringExtra(EXTRA_ONLINE_PLAYER_ID).orEmpty()
                )
                .putExtra(
                    GameplayMockActivity.EXTRA_ONLINE_IS_HOST,
                    intent.getBooleanExtra(EXTRA_ONLINE_IS_HOST, false)
                )
        )
        finish()
    }

    private fun leaveAssigningScreen() {
        if (leavingScreen) return
        leavingScreen = true
        handler.removeCallbacks(openGameRunnable)
        dealingAnimator?.removeAllListeners()
        dealingAnimator?.cancel()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(openGameRunnable)
        dealingAnimator?.removeAllListeners()
        dealingAnimator?.cancel()
        dealingAnimator = null
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
        const val EXTRA_ONLINE_PARTIDA_ID = "extra_online_partida_id"
        const val EXTRA_ONLINE_PLAYER_ID = "extra_online_player_id"
        const val EXTRA_ONLINE_IS_HOST = "extra_online_is_host"
    }
}
