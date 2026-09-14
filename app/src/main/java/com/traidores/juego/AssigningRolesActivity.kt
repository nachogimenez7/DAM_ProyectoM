package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
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
import com.traidores.juego.GameToast as Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog

class AssigningRolesActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var dealingAnimator: AnimatorSet? = null
    private var ambientAnimator: AnimatorSet? = null
    private val scaleIndependentRunnables = mutableListOf<Runnable>()
    private var leavingScreen = false
    private var exitConfirmationDialog: AlertDialog? = null

    private val openGameRunnable = Runnable { openGame() }
    private val startDealingRunnable = Runnable {
        if (!leavingScreen && !isFinishing && !isDestroyed) startDealingAnimation()
    }

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
        ShortSoundPool.preload(this, listOf(GameSound.CARD_DEAL.res))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleAssigningBack()
            }
        })

        findViewById<ImageButton>(R.id.btnBack).apply {
            alpha = 0f
            isEnabled = false
            setOnClickListener { handleAssigningBack() }
        }

        val presentationStartDelayMs = intent
            .getLongExtra(EXTRA_PRESENTATION_START_DELAY_MS, 0L)
            .coerceIn(0L, OnlineLobbyEntryGate.MAX_PRESENTATION_WAIT_MS)
        findViewById<FrameLayout>(R.id.assigningRoot).post {
            handler.postDelayed(startDealingRunnable, presentationStartDelayMs)
        }
        handler.postDelayed(openGameRunnable, ANIMATION_FALLBACK_MS + presentationStartDelayMs)
    }

    private fun startDealingAnimation() {
        val root = findViewById<FrameLayout>(R.id.assigningRoot)
        val stage = findViewById<FrameLayout>(R.id.animationStage)
        val openHands = findViewById<ImageView>(R.id.assigningHands)
        val cardGripHands = findViewById<ImageView>(R.id.assigningCardGripHands)
        val dealingHands = findViewById<ImageView>(R.id.assigningDealingHands)
        val leftCard = findViewById<ImageView>(R.id.shuffleCardLeft)
        val rightCard = findViewById<ImageView>(R.id.shuffleCardRight)
        val finalCard = findViewById<ImageView>(R.id.finalRoleCard)
        val status = findViewById<TextView>(R.id.assigningStatus)
        val table = findViewById<View>(R.id.assigningTable)
        val candleGlow = findViewById<View>(R.id.assigningCandleGlow)
        val vignette = findViewById<View>(R.id.assigningVignette)
        val cardAura = findViewById<View>(R.id.finalCardAura)
        val cardShadow = findViewById<View>(R.id.finalCardShadow)
        val backButton = findViewById<ImageButton>(R.id.btnBack)

        openHands.setImageResource(R.drawable.assigning_dealer_hands_release)
        cardGripHands.setImageResource(R.drawable.assigning_dealer_hands_release)
        openHands.scaleType = ImageView.ScaleType.CENTER_CROP
        cardGripHands.scaleType = ImageView.ScaleType.CENTER_CROP
        dealingHands.scaleType = ImageView.ScaleType.CENTER_CROP

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
        cardGripHands.visibility = View.VISIBLE
        dealingHands.visibility = View.GONE
        table.layoutParams = (table.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = android.view.Gravity.CENTER
            topMargin = 0
        }
        listOf(openHands, cardGripHands, dealingHands).forEach { hands ->
            hands.layoutParams = (hands.layoutParams as FrameLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = android.view.Gravity.CENTER
                topMargin = 0
            }
            hands.pivotY = root.height.toFloat()
        }
        val handsSplitX = root.width / 2
        openHands.clipBounds = Rect(0, 0, handsSplitX, root.height)
        cardGripHands.clipBounds = Rect(handsSplitX, 0, root.width, root.height)
        openHands.pivotX = root.width * 0.18f
        cardGripHands.pivotX = root.width * 0.82f
        val handsRestScale = 0.94f
        val handsEntranceScale = 0.91f
        cardAura.layoutParams = (cardAura.layoutParams as FrameLayout.LayoutParams).apply {
            width = cardWidth + dp(96)
            height = cardHeight + dp(116)
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            topMargin = cardTop - dp(58)
        }
        cardShadow.layoutParams = (cardShadow.layoutParams as FrameLayout.LayoutParams).apply {
            width = cardWidth + dp(20)
            height = cardHeight + dp(20)
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            topMargin = cardTop + dp(8)
        }
        table.alpha = 0f
        table.scaleX = 1.025f
        table.scaleY = 1.025f
        candleGlow.alpha = 0f
        vignette.alpha = 0f
        cardAura.alpha = 0f
        cardAura.scaleX = 0.74f
        cardAura.scaleY = 0.74f
        cardShadow.alpha = 0f
        cardShadow.scaleX = 0.88f
        cardShadow.scaleY = 0.88f
        openHands.alpha = 0f
        openHands.translationY = dp(58).toFloat()
        openHands.translationX = -dp(28).toFloat()
        openHands.scaleX = handsEntranceScale
        openHands.scaleY = handsEntranceScale
        openHands.rotation = -5f
        cardGripHands.alpha = 0f
        cardGripHands.translationY = dp(58).toFloat()
        cardGripHands.translationX = dp(28).toFloat()
        cardGripHands.scaleX = handsEntranceScale
        cardGripHands.scaleY = handsEntranceScale
        cardGripHands.rotation = 5f
        dealingHands.alpha = 0f
        dealingHands.translationY = dp(46).toFloat()
        dealingHands.translationX = 0f
        dealingHands.scaleX = handsEntranceScale
        dealingHands.scaleY = handsEntranceScale
        backButton.alpha = 0f
        backButton.isEnabled = false

        status.layoutParams = (status.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(108)
        }
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        status.text = DEALING_STATUS_MESSAGE

        finalCard.alpha = 0f
        finalCard.translationX = 0f
        finalCard.translationY = -dp(112).toFloat()
        finalCard.scaleX = 0.78f
        finalCard.scaleY = 0.78f
        finalCard.rotation = -3f
        finalCard.rotationX = 4f
        finalCard.rotationY = -11f
        finalCard.cameraDistance = resources.displayMetrics.density * 8_000f
        cardShadow.translationX = dp(7).toFloat()
        cardShadow.rotation = -3f
        stage.alpha = 1f

        if (EssentialViewAnimation.requiresFallback(root)) {
            OnlineDebugLog.i(
                "essential_presentation_start type=role_dealing engine=frame_clock online=" +
                    intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty().isNotBlank()
            )
            startScaleIndependentDealing(
                stage = stage,
                table = table,
                candleGlow = candleGlow,
                vignette = vignette,
                openHands = openHands,
                cardGripHands = cardGripHands,
                finalCard = finalCard,
                cardAura = cardAura,
                cardShadow = cardShadow,
                status = status,
                backButton = backButton
            )
            return
        }
        OnlineDebugLog.i(
            "essential_presentation_start type=role_dealing engine=platform online=" +
                intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty().isNotBlank()
        )

        val tableReveal = AnimatorSet().apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(stage, View.ALPHA, 0.68f, 1f),
                ObjectAnimator.ofFloat(table, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(table, View.SCALE_X, 1.025f, 1f),
                ObjectAnimator.ofFloat(table, View.SCALE_Y, 1.025f, 1f),
                ObjectAnimator.ofFloat(vignette, View.ALPHA, 0f, 0.78f),
                ObjectAnimator.ofFloat(candleGlow, View.ALPHA, 0f, 0.16f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startAmbientMotion(table, candleGlow)
                }
            })
        }

        val handsEntrance = AnimatorSet().apply {
            interpolator = DecelerateInterpolator()
            val backReveal = ObjectAnimator.ofFloat(backButton, View.ALPHA, 0f, 0.76f).apply {
                duration = 220L
                startDelay = 120L
            }
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 0f, 1f).apply { duration = 420L },
                ObjectAnimator.ofFloat(cardGripHands, View.ALPHA, 0f, 1f).apply { duration = 420L },
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, dp(58).toFloat(), 0f).apply { duration = 480L },
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_Y, dp(58).toFloat(), 0f).apply { duration = 480L },
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, -dp(28).toFloat(), -dp(4).toFloat()).apply { duration = 480L },
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_X, dp(28).toFloat(), dp(4).toFloat()).apply { duration = 480L },
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, handsEntranceScale, handsRestScale).apply { duration = 480L },
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, handsEntranceScale, handsRestScale).apply { duration = 480L },
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_X, handsEntranceScale, handsRestScale).apply { duration = 480L },
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_Y, handsEntranceScale, handsRestScale).apply { duration = 480L },
                ObjectAnimator.ofFloat(openHands, View.ROTATION, -5f, -1f).apply { duration = 480L },
                ObjectAnimator.ofFloat(cardGripHands, View.ROTATION, 5f, 1f).apply { duration = 480L },
                backReveal
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    playSoftCardBeat(volume = 0.28f, playbackRate = 0.94f)
                }

                override fun onAnimationEnd(animation: Animator) {
                    backButton.isEnabled = true
                }
            })
        }

        val cardDeal = AnimatorSet().apply {
            duration = 500L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(finalCard, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(
                    finalCard,
                    View.TRANSLATION_Y,
                    -dp(112).toFloat(),
                    cardSettleY + dp(8)
                ),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 0.78f, 1.04f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 0.78f, 1.04f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION, -3f, 0f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION_X, 4f, -1f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION_Y, -11f, 2f),
                ObjectAnimator.ofFloat(cardAura, View.ALPHA, 0f, 0.38f),
                ObjectAnimator.ofFloat(cardAura, View.SCALE_X, 0.74f, 1f),
                ObjectAnimator.ofFloat(cardAura, View.SCALE_Y, 0.74f, 1f),
                ObjectAnimator.ofFloat(cardShadow, View.ALPHA, 0f, 0.44f),
                ObjectAnimator.ofFloat(cardShadow, View.SCALE_X, 0.88f, 1f),
                ObjectAnimator.ofFloat(cardShadow, View.SCALE_Y, 0.88f, 1f),
                ObjectAnimator.ofFloat(cardShadow, View.TRANSLATION_X, dp(7).toFloat(), dp(2).toFloat()),
                ObjectAnimator.ofFloat(cardShadow, View.ROTATION, -3f, -1f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, -dp(4).toFloat(), dp(10).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_X, dp(4).toFloat(), -dp(10).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, 0f, -dp(4).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_Y, 0f, -dp(4).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.ROTATION, -1f, 1.5f),
                ObjectAnimator.ofFloat(cardGripHands, View.ROTATION, 1f, -1.5f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, handsRestScale, 0.95f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, handsRestScale, 0.95f),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_X, handsRestScale, 0.95f),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_Y, handsRestScale, 0.95f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    playSoftCardBeat(volume = 0.5f, playbackRate = 1.04f)
                }
            })
        }

        val settleCard = AnimatorSet().apply {
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, dp(10).toFloat(), dp(3).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_X, -dp(10).toFloat(), -dp(3).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, -dp(4).toFloat(), dp(2).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_Y, -dp(4).toFloat(), dp(2).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.ROTATION, 1.5f, 0f),
                ObjectAnimator.ofFloat(cardGripHands, View.ROTATION, -1.5f, 0f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, 0.95f, handsRestScale),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, 0.95f, handsRestScale),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_X, 0.95f, handsRestScale),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_Y, 0.95f, handsRestScale),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, cardSettleY + dp(8), cardBounceDown, cardSettleY + dp(2)),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.04f, 1.0f, 1.06f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.04f, 1.0f, 1.06f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION_X, -1f, 0f),
                ObjectAnimator.ofFloat(finalCard, View.ROTATION_Y, 2f, 0f),
                ObjectAnimator.ofFloat(cardShadow, View.ALPHA, 0.44f, 0.58f),
                ObjectAnimator.ofFloat(cardShadow, View.SCALE_X, 1f, 0.94f),
                ObjectAnimator.ofFloat(cardShadow, View.SCALE_Y, 1f, 0.94f),
                ObjectAnimator.ofFloat(cardShadow, View.TRANSLATION_X, dp(2).toFloat(), 0f),
                ObjectAnimator.ofFloat(cardShadow, View.ROTATION, -1f, 0f)
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    playFinalCardBeat()
                }
            })
        }

        val holdDealingPose = AnimatorSet().apply {
            duration = 260L
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 1f, 1f),
                ObjectAnimator.ofFloat(cardGripHands, View.ALPHA, 1f, 1f)
            )
        }

        val releaseHands = AnimatorSet().apply {
            duration = 620L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(openHands, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(cardGripHands, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_Y, dp(2).toFloat(), dp(82).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_Y, dp(2).toFloat(), dp(82).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.TRANSLATION_X, dp(3).toFloat(), -dp(42).toFloat()),
                ObjectAnimator.ofFloat(cardGripHands, View.TRANSLATION_X, -dp(3).toFloat(), dp(42).toFloat()),
                ObjectAnimator.ofFloat(openHands, View.ROTATION, 0f, -6f),
                ObjectAnimator.ofFloat(cardGripHands, View.ROTATION, 0f, 6f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_X, handsRestScale, 0.91f),
                ObjectAnimator.ofFloat(openHands, View.SCALE_Y, handsRestScale, 0.91f),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_X, handsRestScale, 0.91f),
                ObjectAnimator.ofFloat(cardGripHands, View.SCALE_Y, handsRestScale, 0.91f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.06f, 1.13f),
                ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.06f, 1.13f),
                ObjectAnimator.ofFloat(finalCard, View.TRANSLATION_Y, cardSettleY + dp(2), cardSettleY - dp(2)),
                ObjectAnimator.ofFloat(cardAura, View.ALPHA, 0.38f, 0.58f),
                ObjectAnimator.ofFloat(cardShadow, View.ALPHA, 0.58f, 0.48f),
                ObjectAnimator.ofFloat(status, View.ALPHA, 0f, 1f)
            )
        }

        val holdFinalCard = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            val cardPulseX = ObjectAnimator.ofFloat(finalCard, View.SCALE_X, 1.13f, 1.08f).apply {
                duration = 520L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
            }
            val cardPulseY = ObjectAnimator.ofFloat(finalCard, View.SCALE_Y, 1.13f, 1.08f).apply {
                duration = 520L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
            }
            val cardBreath = ObjectAnimator.ofFloat(
                finalCard,
                View.TRANSLATION_Y,
                cardSettleY - dp(2),
                cardSettleY + dp(5)
            ).apply {
                duration = 520L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
            }
            val statusPulse = ObjectAnimator.ofFloat(status, View.ALPHA, 1f, 0.72f).apply {
                duration = 520L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
            }
            val auraPulse = ObjectAnimator.ofFloat(cardAura, View.ALPHA, 0.58f, 0.42f).apply {
                duration = 520L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
            }
            playTogether(cardPulseX, cardPulseY, cardBreath, statusPulse, auraPulse)
        }

        val exit = AnimatorSet().apply {
            duration = 480L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(stage, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(backButton, View.ALPHA, 0f)
            )
        }

        dealingAnimator = AnimatorSet().apply {
            playSequentially(
                tableReveal,
                handsEntrance,
                cardDeal,
                settleCard,
                holdDealingPose,
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

    private fun startScaleIndependentDealing(
        stage: View,
        table: View,
        candleGlow: View,
        vignette: View,
        openHands: View,
        cardGripHands: View,
        finalCard: View,
        cardAura: View,
        cardShadow: View,
        status: View,
        backButton: View
    ) {
        handler.removeCallbacks(openGameRunnable)
        backButton.isEnabled = false
        EssentialViewAnimation.reveal(stage, durationMs = 420L, fromScale = 1f)
        EssentialViewAnimation.reveal(table, durationMs = 420L, fromScale = 1.025f)
        EssentialViewAnimation.reveal(
            vignette,
            durationMs = 420L,
            finalAlpha = 0.78f,
            fromScale = 1f
        )
        EssentialViewAnimation.reveal(
            candleGlow,
            durationMs = 420L,
            finalAlpha = 0.16f,
            fromScale = 1f
        )
        EssentialViewAnimation.slideIn(
            openHands,
            fromX = -dp(28).toFloat(),
            fromY = dp(58).toFloat(),
            durationMs = 480L,
            delayMs = 420L
        )
        EssentialViewAnimation.slideIn(
            cardGripHands,
            fromX = dp(28).toFloat(),
            fromY = dp(58).toFloat(),
            durationMs = 480L,
            delayMs = 420L
        )
        EssentialViewAnimation.slideIn(
            finalCard,
            fromY = -dp(112).toFloat(),
            durationMs = 620L,
            delayMs = 980L
        )
        EssentialViewAnimation.reveal(
            cardAura,
            durationMs = 620L,
            delayMs = 980L,
            fromScale = 0.74f,
            finalAlpha = 0.58f
        )
        EssentialViewAnimation.reveal(
            cardShadow,
            durationMs = 620L,
            delayMs = 980L,
            fromScale = 0.88f,
            finalAlpha = 0.48f
        )
        EssentialViewAnimation.reveal(
            status,
            durationMs = 420L,
            delayMs = 1_850L,
            fromScale = 0.96f
        )
        scheduleScaleIndependent(420L) {
            playSoftCardBeat(volume = 0.28f, playbackRate = 0.94f)
        }
        scheduleScaleIndependent(980L) { playFinalCardBeat() }
        scheduleScaleIndependent(2_750L) {
            EssentialViewAnimation.fadeOut(openHands, 620L) {}
            EssentialViewAnimation.fadeOut(cardGripHands, 620L) {}
        }
        scheduleScaleIndependent(3_650L) {
            EssentialViewAnimation.fadeOut(stage, 480L) {
                OnlineDebugLog.i("essential_presentation_finish type=role_dealing engine=frame_clock")
                openGame()
            }
        }
        handler.postDelayed(openGameRunnable, SCALE_INDEPENDENT_FALLBACK_MS)
    }

    private fun scheduleScaleIndependent(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable {
            if (!leavingScreen && !isFinishing && !isDestroyed) action()
        }
        scaleIndependentRunnables += runnable
        handler.postDelayed(runnable, delayMs)
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
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    val playbackRate = when {
                        handsY > 0f -> 0.94f
                        handsY < 0f -> 1.06f
                        else -> 1f
                    }
                    playSoftCardBeat(volume = 0.34f, playbackRate = playbackRate)
                }
            })
        }
    }

    private fun startAmbientMotion(table: View, candleGlow: View) {
        ambientAnimator?.cancel()
        val tableScaleX = ObjectAnimator.ofFloat(table, View.SCALE_X, 1f, 1.016f).apply {
            duration = 3_400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val tableScaleY = ObjectAnimator.ofFloat(table, View.SCALE_Y, 1f, 1.016f).apply {
            duration = 3_400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val candleFlicker = ValueAnimator.ofFloat(0.12f, 0.22f, 0.15f, 0.2f, 0.13f).apply {
            duration = 1_650L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                candleGlow.alpha = animator.animatedValue as Float
            }
        }
        ambientAnimator = AnimatorSet().apply {
            playTogether(tableScaleX, tableScaleY, candleFlicker)
            start()
        }
    }

    private fun playSoftCardBeat(volume: Float, playbackRate: Float = 1f) {
        GameplaySoundEffects.play(
            context = this,
            soundRes = GameSound.CARD_DEAL.res,
            volumeScale = volume,
            playbackRate = playbackRate
        )
    }

    private fun playFinalCardBeat() {
        GameplayAudioDirector.play(this, GameSound.CARD_DEAL)
    }

    private fun openGame() {
        if (leavingScreen || isFinishing || isDestroyed) return
        if (exitConfirmationDialog?.isShowing == true) {
            handler.removeCallbacks(openGameRunnable)
            handler.postDelayed(openGameRunnable, EXIT_CONFIRMATION_RETRY_MS)
            return
        }
        leavingScreen = true
        handler.removeCallbacks(openGameRunnable)
        handler.removeCallbacks(startDealingRunnable)
        ambientAnimator?.cancel()
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
        handler.removeCallbacks(startDealingRunnable)
        scaleIndependentRunnables.forEach(handler::removeCallbacks)
        scaleIndependentRunnables.clear()
        dealingAnimator?.removeAllListeners()
        dealingAnimator?.cancel()
        ambientAnimator?.cancel()
        finish()
    }

    private fun handleAssigningBack() {
        val isOnline = intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty().isNotBlank()
        when (GameplayExitPolicy.assigningBackAction(isOnline)) {
            GameplayExitAction.BLOCK_ONLINE_EXIT -> GameNotice.show(
                this,
                "La partida online está comenzando. Esperá a que termine el reparto."
            )
            GameplayExitAction.CONFIRM_LOCAL_EXIT -> showLocalExitConfirmation()
            GameplayExitAction.RETURN_TO_LOBBY -> showOnlineExitConfirmation()
        }
    }

    private fun showOnlineExitConfirmation() {
        if (exitConfirmationDialog?.isShowing == true) return
        exitConfirmationDialog = GameDialog.confirm(
            activity = this,
            title = "¿Salir durante el reparto?",
            message = "Podés salir aunque la partida esté preparando las cartas. Tu dispositivo " +
                "volverá a Online y la sala podrá continuar con el resto.",
            positiveLabel = "SALIR",
            negativeLabel = "SEGUIR ESPERANDO",
            onDismiss = { exitConfirmationDialog = null }
        ) {
            val roomId = intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
            OnlineStabilityReport.recordEvent(this, "salida_durante_reparto")
            OnlineRoomRecovery.clearIf(this, roomId)
            startActivity(
                Intent(this, OnlineModeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            leaveAssigningScreen()
        }
    }

    private fun showLocalExitConfirmation() {
        if (exitConfirmationDialog?.isShowing == true) return
        exitConfirmationDialog = GameDialog.confirm(
            activity = this,
            title = "¿Salir de la partida?",
            message = "Si salís ahora, se cancelará la partida y perderás su progreso.",
            positiveLabel = "SALIR",
            negativeLabel = "SEGUIR JUGANDO",
            onDismiss = {
                exitConfirmationDialog = null
                if (!leavingScreen && !isFinishing && !isDestroyed) {
                    handler.post(openGameRunnable)
                }
            }
        ) {
            leaveAssigningScreen()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(openGameRunnable)
        handler.removeCallbacks(startDealingRunnable)
        scaleIndependentRunnables.forEach(handler::removeCallbacks)
        scaleIndependentRunnables.clear()
        dealingAnimator?.removeAllListeners()
        dealingAnimator?.cancel()
        dealingAnimator = null
        ambientAnimator?.cancel()
        ambientAnimator = null
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
        private const val ANIMATION_FALLBACK_MS = 6_000L
        private const val SCALE_INDEPENDENT_FALLBACK_MS = 5_000L
        private const val EXIT_CONFIRMATION_RETRY_MS = 250L
        private const val DEALING_STATUS_MESSAGE = "¡Buena suerte con tu rol!"
        const val EXTRA_ONLINE_PARTIDA_ID = "extra_online_partida_id"
        const val EXTRA_ONLINE_PLAYER_ID = "extra_online_player_id"
        const val EXTRA_ONLINE_IS_HOST = "extra_online_is_host"
        const val EXTRA_PRESENTATION_START_DELAY_MS = "extra_presentation_start_delay_ms"
    }
}
