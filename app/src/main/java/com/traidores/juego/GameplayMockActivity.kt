package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.TextViewCompat
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import java.util.ArrayDeque
import kotlin.math.ceil

class GameplayMockActivity : BaseActivity() {

    private var isCardRevealed = false
    private var appliedGameplayTextScale = 1f
    private var isChatOpen = false
    private var isChatKeyboardCompact = false
    private var newChatMessagesWhileTyping = 0
    private var isEventLogExpanded = false
    private var lastRenderedAnnouncement = ""
    private var lastRenderedPhase: GamePhase? = null
    private var lastSeenChatCount = 0
    private var selectedTarget = ""
    private var desertorDialogOpen = false
    private var isDayNightTransitionRunning = false
    private var isDeathRevealRunning = false
    private var isSilenceRevealRunning = false
    private var isOracleRevealVisible = false
    private var isRolePreviewOpen = false
    private var restoreRolePreviewOnResume = false
    private var isWinnerRevealVisible = false
    private var isJesterVictoryVisible = false
    private var isTraitorRevealDismissing = false
    private var isTraitorRevealRunning = false
    private var lastPresentedTransitionKey: String? = null
    private var lastActionAttentionKey: String? = null
    private var presentedPeriod: GameplayPeriod? = null
    private var blockingFeedbackPeriod: GameplayPeriod? = null
    private var traitorRevealCompleted = false
    private var winnerRevealPresented = false
    private var presentedSpecialVictoryCount = 0
    private val countdown = GameplayCountdown()
    private var lastCountdownSecond = -1
    private var knownDeadPlayers = emptySet<String>()
    private var knownMutedPlayers = emptySet<String>()
    private var lastRenderedEventMessages = emptyList<String>()
    private var lastRenderedEventExpanded: Boolean? = null
    private val feedbackState = GameplayFeedbackState()
    private lateinit var session: GameSession
    private var unreadChatCount = 0
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val feedbackDismissRunnable = Runnable { dismissCurrentFeedback() }
    private val feedbackBannerDismissRunnable = Runnable { hideActionFeedbackBanner() }
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
        }
    }
    private var actionPulseAnimator: AnimatorSet? = null
    private var eventLogHeightAnimator: ValueAnimator? = null
    private var feedbackAnimator: AnimatorSet? = null
    private lateinit var rolePreviewAnimator: RolePreviewAnimator
    private lateinit var traitorRevealAnimator: TraitorRevealAnimator
    private lateinit var jesterVictoryAnimator: JesterVictoryAnimator

    private lateinit var actionFeedbackBanner: LinearLayout
    private lateinit var actionFeedbackBannerMessage: TextView
    private lateinit var actionFeedbackBannerTitle: TextView
    private lateinit var actionFeedbackBannerTone: View
    private lateinit var btnAction: Button
    private lateinit var btnRevealCard: Button
    private lateinit var btnToggleChat: ImageButton
    private lateinit var btnToggleEventLog: Button
    private lateinit var btnSendChat: Button
    private lateinit var chatCharacterCount: TextView
    private lateinit var chatComposer: LinearLayout
    private lateinit var chatHeader: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatMessagesScroll: ScrollView
    private lateinit var chatNewMessages: TextView
    private lateinit var chatPanel: LinearLayout
    private lateinit var chatStatusRow: LinearLayout
    private lateinit var chatUnreadBadge: TextView
    private lateinit var currentPlayerHint: TextView
    private lateinit var currentPlayerName: TextView
    private lateinit var currentPlayerStatus: TextView
    private lateinit var deathRevealBloodLeft: ImageView
    private lateinit var deathRevealBloodRight: ImageView
    private lateinit var deathRevealCard: FrameLayout
    private lateinit var deathRevealCardBack: ImageView
    private lateinit var deathRevealCardFront: ImageView
    private lateinit var deathRevealContent: LinearLayout
    private lateinit var deathRevealFlash: View
    private lateinit var deathRevealOverlay: FrameLayout
    private lateinit var deathRevealPlayerName: TextView
    private lateinit var deathRevealRoleName: TextView
    private lateinit var eventLogBackground: ImageView
    private lateinit var eventLogColorBar: View
    private lateinit var eventLogContent: FrameLayout
    private lateinit var eventLogContainer: LinearLayout
    private lateinit var eventLogHeader: LinearLayout
    private lateinit var eventLogPanel: LinearLayout
    private lateinit var eventLogScroll: ScrollView
    private lateinit var eventLogSummary: TextView
    private lateinit var gameplayBody: LinearLayout
    private lateinit var gameplayRoot: RelativeLayout
    private lateinit var centerColumn: FrameLayout
    private lateinit var leftPlayersScroll: ScrollView
    private lateinit var leftPlayersContainer: LinearLayout
    private lateinit var mapBackground: ImageView
    private lateinit var phaseTitle: TextView
    private lateinit var phaseSubtitle: TextView
    private lateinit var phaseCountdown: TextView
    private lateinit var phaseProgressFill: View
    private lateinit var privateFeedbackMessage: TextView
    private lateinit var privateFeedbackOverlay: FrameLayout
    private lateinit var privateFeedbackPanel: FrameLayout
    private lateinit var privateFeedbackTitle: TextView
    private lateinit var privateFeedbackTone: View
    private lateinit var rightPlayersContainer: LinearLayout
    private lateinit var rightPlayersScroll: ScrollView
    private lateinit var rightColumn: LinearLayout
    private lateinit var roleCard: LinearLayout
    private lateinit var roleImage: ImageView
    private lateinit var roleName: TextView
    private lateinit var rolePreviewContent: FrameLayout
    private lateinit var rolePreviewAdvice: TextView
    private lateinit var rolePreviewFunction: TextView
    private lateinit var rolePreviewImage: ImageView
    private lateinit var rolePreviewMapBackground: ImageView
    private lateinit var rolePreviewName: TextView
    private lateinit var rolePreviewOverlay: FrameLayout
    private lateinit var rolePreviewScroll: ScrollView
    private lateinit var rolePreviewTeam: TextView
    private lateinit var silenceRevealCageDoor: ImageView
    private lateinit var silenceRevealCageLeft: ImageView
    private lateinit var silenceRevealCageLock: ImageView
    private lateinit var silenceRevealCageRight: ImageView
    private lateinit var silenceRevealCard: FrameLayout
    private lateinit var silenceRevealContent: LinearLayout
    private lateinit var silenceRevealOverlay: FrameLayout
    private lateinit var silenceRevealPlayerName: TextView
    private lateinit var topStatus: LinearLayout
    private lateinit var dayNightTransitionOverlay: FrameLayout
    private lateinit var transitionFromBackground: ImageView
    private lateinit var transitionMoon: ImageView
    private lateinit var transitionShade: View
    private lateinit var transitionSun: ImageView
    private lateinit var transitionTitle: TextView
    private lateinit var transitionToBackground: ImageView
    private lateinit var dayNightTransitionAnimator: DayNightTransitionAnimator
    private lateinit var deathRevealAnimator: DeathRevealAnimator
    private lateinit var silenceRevealAnimator: SilenceRevealAnimator
    private lateinit var oracleRevealOverlay: FrameLayout
    private lateinit var oracleRevealPanel: FrameLayout
    private lateinit var oracleRevealPlayer: TextView
    private lateinit var btnContinueOracleReveal: Button
    private lateinit var traitorRevealCards: LinearLayout
    private lateinit var traitorRevealContent: LinearLayout
    private lateinit var traitorRevealOverlay: FrameLayout
    private lateinit var btnContinueJesterVictory: Button
    private lateinit var jesterConfettiLayer: FrameLayout
    private lateinit var jesterHornLeft: ImageView
    private lateinit var jesterHornRight: ImageView
    private lateinit var jesterVictoryImage: ImageView
    private lateinit var jesterVictoryMessage: TextView
    private lateinit var jesterVictoryOverlay: FrameLayout
    private lateinit var jesterVictoryPanel: FrameLayout
    private lateinit var jesterVictoryPlayer: TextView
    private lateinit var winnerRevealBackground: ImageView
    private lateinit var winnerRevealCards: LinearLayout
    private lateinit var winnerRevealContent: LinearLayout
    private lateinit var winnerRevealOverlay: FrameLayout
    private lateinit var winnerRevealPanel: FrameLayout
    private lateinit var winnerRevealPersonalResult: TextView
    private lateinit var winnerRevealScroll: ScrollView
    private lateinit var winnerRevealShine: View
    private lateinit var winnerRevealTitle: TextView
    private lateinit var winnerSummaryDuration: TextView
    private lateinit var winnerSummaryHighlight: TextView
    private lateinit var winnerSummaryPlayers: TextView
    private lateinit var winnerSummaryRounds: TextView
    private lateinit var winnerSummaryTimeline: TextView
    private lateinit var themeKey: String
    private val pendingDeathReveals = ArrayDeque<GamePlayer>()
    private val pendingSilenceReveals = ArrayDeque<GamePlayer>()
    private val playerCardViews = linkedMapOf<String, SidePlayerCardHolder>()
    private lateinit var winnerRevealAnimator: WinnerRevealAnimator
    private lateinit var winnerResultsRenderer: WinnerResultsRenderer

    private data class SidePlayerCardHolder(
        val root: LinearLayout,
        val cardFace: FrameLayout,
        val cardBack: ImageView,
        val avatar: TextView,
        val mutedBadge: TextView,
        val name: TextView,
        var selected: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        @Suppress("DEPRECATION")
        val restoredSession = savedInstanceState?.getSerializable(STATE_SESSION) as? GameSession
        session = restoredSession ?: readSession() ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        presentedSpecialVictoryCount = savedInstanceState
            ?.getInt(STATE_PRESENTED_SPECIAL_VICTORY_COUNT)
            ?.coerceAtMost(session.specialVictories.size)
            ?: 0
        themeKey = themeFromIntentOrSession()
        val shouldShowInitialRoleReveal = savedInstanceState == null &&
            session.phase == GamePhase.REPARTO
        val shouldRestoreRolePreview = savedInstanceState
            ?.getBoolean(STATE_ROLE_PREVIEW_OPEN)
            ?: false
        val shouldPresentRolePreview = shouldShowInitialRoleReveal || shouldRestoreRolePreview
        lastPresentedTransitionKey = savedInstanceState?.getString(STATE_TRANSITION_KEY)
        if (shouldShowInitialRoleReveal) {
            lastPresentedTransitionKey = GameplayTableUi.transitionSpec(session).key
        }
        presentedPeriod = savedInstanceState
            ?.getString(STATE_PRESENTED_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        blockingFeedbackPeriod = savedInstanceState
            ?.getString(STATE_BLOCKING_FEEDBACK_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        traitorRevealCompleted = savedInstanceState?.getBoolean(STATE_TRAITOR_REVEAL_COMPLETED) ?: false
        winnerRevealPresented = savedInstanceState?.getBoolean(STATE_WINNER_REVEAL_PRESENTED) ?: false
        isChatOpen = savedInstanceState?.getBoolean(STATE_CHAT_OPEN) ?: false
        isEventLogExpanded = savedInstanceState?.getBoolean(STATE_EVENT_LOG_EXPANDED) ?: true
        selectedTarget = savedInstanceState?.getString(STATE_SELECTED_TARGET).orEmpty()
        val restoredCountdownStage = savedInstanceState
            ?.getString(STATE_COUNTDOWN_STAGE)
            ?.let { runCatching { CountdownStage.valueOf(it) }.getOrNull() }
        countdown.restore(
            stage = restoredCountdownStage,
            phaseIndex = savedInstanceState?.getInt(STATE_COUNTDOWN_PHASE_INDEX, -1) ?: -1,
            remainingMs = savedInstanceState?.getLong(STATE_COUNTDOWN_REMAINING_MS, 0L) ?: 0L,
            totalMs = savedInstanceState?.getLong(STATE_COUNTDOWN_TOTAL_MS, 0L) ?: 0L
        )
        @Suppress("DEPRECATION")
        feedbackState.restore(
            savedInstanceState?.getSerializable(STATE_PENDING_FEEDBACK) as? GameplayFeedbackSpec
        )
        knownDeadPlayers = session.players.filterNot { it.alive }.map { it.name }.toSet()
        knownMutedPlayers = session.players.filter { it.muted }.map { it.name }.toSet()
        lastSeenChatCount = session.chatHistory.size

        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        actionFeedbackBanner = findViewById(R.id.actionFeedbackBanner)
        actionFeedbackBannerMessage = findViewById(R.id.actionFeedbackBannerMessage)
        actionFeedbackBannerTitle = findViewById(R.id.actionFeedbackBannerTitle)
        actionFeedbackBannerTone = findViewById(R.id.actionFeedbackBannerTone)
        btnAction = findViewById(R.id.btnVote)
        btnRevealCard = findViewById(R.id.btnRevealCard)
        btnToggleChat = findViewById(R.id.btnToggleChat)
        btnToggleEventLog = findViewById(R.id.btnToggleEventLog)
        btnSendChat = findViewById(R.id.btnSendChat)
        chatCharacterCount = findViewById(R.id.chatCharacterCount)
        chatComposer = findViewById(R.id.chatComposer)
        chatHeader = findViewById(R.id.chatHeader)
        chatInput = findViewById(R.id.chatInput)
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer)
        chatMessagesScroll = findViewById(R.id.chatMessagesScroll)
        chatNewMessages = findViewById(R.id.chatNewMessages)
        chatPanel = findViewById(R.id.chatPanel)
        chatStatusRow = findViewById(R.id.chatStatusRow)
        chatUnreadBadge = findViewById(R.id.chatUnreadBadge)
        currentPlayerHint = findViewById(R.id.currentPlayerHint)
        currentPlayerName = findViewById(R.id.currentPlayerName)
        currentPlayerStatus = findViewById(R.id.currentPlayerStatus)
        dayNightTransitionOverlay = findViewById(R.id.dayNightTransitionOverlay)
        deathRevealBloodLeft = findViewById(R.id.deathRevealBloodLeft)
        deathRevealBloodRight = findViewById(R.id.deathRevealBloodRight)
        deathRevealCard = findViewById(R.id.deathRevealCard)
        deathRevealCardBack = findViewById(R.id.deathRevealCardBack)
        deathRevealCardFront = findViewById(R.id.deathRevealCardFront)
        deathRevealContent = findViewById(R.id.deathRevealContent)
        deathRevealFlash = findViewById(R.id.deathRevealFlash)
        deathRevealOverlay = findViewById(R.id.deathRevealOverlay)
        deathRevealPlayerName = findViewById(R.id.deathRevealPlayerName)
        deathRevealRoleName = findViewById(R.id.deathRevealRoleName)
        eventLogBackground = findViewById(R.id.eventLogBackground)
        eventLogColorBar = findViewById(R.id.eventLogColorBar)
        eventLogContent = findViewById(R.id.eventLogContent)
        eventLogContainer = findViewById(R.id.eventLogContainer)
        eventLogHeader = findViewById(R.id.eventLogHeader)
        eventLogPanel = findViewById(R.id.eventLogPanel)
        eventLogScroll = findViewById(R.id.eventLogScroll)
        eventLogSummary = findViewById(R.id.eventLogSummary)
        gameplayBody = findViewById(R.id.gameplayBody)
        gameplayRoot = findViewById(R.id.gameplayRoot)
        centerColumn = findViewById(R.id.centerColumn)
        leftPlayersScroll = findViewById(R.id.leftPlayersScroll)
        leftPlayersContainer = findViewById(R.id.leftPlayersContainer)
        mapBackground = findViewById(R.id.mapBackground)
        phaseTitle = findViewById(R.id.phaseTitle)
        phaseSubtitle = findViewById(R.id.phaseSubtitle)
        phaseCountdown = findViewById(R.id.phaseCountdown)
        phaseProgressFill = findViewById(R.id.phaseProgressFill)
        phaseProgressFill.pivotX = 0f
        privateFeedbackMessage = findViewById(R.id.privateFeedbackMessage)
        privateFeedbackOverlay = findViewById(R.id.privateFeedbackOverlay)
        privateFeedbackPanel = findViewById(R.id.privateFeedbackPanel)
        privateFeedbackTitle = findViewById(R.id.privateFeedbackTitle)
        privateFeedbackTone = findViewById(R.id.privateFeedbackTone)
        rightPlayersContainer = findViewById(R.id.rightPlayersContainer)
        rightPlayersScroll = findViewById(R.id.rightPlayersScroll)
        rightColumn = findViewById(R.id.rightColumn)
        roleCard = findViewById(R.id.roleCard)
        roleImage = findViewById(R.id.roleImage)
        roleName = findViewById(R.id.roleName)
        rolePreviewContent = findViewById(R.id.rolePreviewContent)
        rolePreviewAdvice = findViewById(R.id.rolePreviewAdvice)
        rolePreviewFunction = findViewById(R.id.rolePreviewFunction)
        rolePreviewImage = findViewById(R.id.rolePreviewImage)
        rolePreviewMapBackground = findViewById(R.id.rolePreviewMapBackground)
        rolePreviewName = findViewById(R.id.rolePreviewName)
        rolePreviewOverlay = findViewById(R.id.rolePreviewOverlay)
        rolePreviewScroll = findViewById(R.id.rolePreviewScroll)
        rolePreviewTeam = findViewById(R.id.rolePreviewTeam)
        rolePreviewAnimator = RolePreviewAnimator(
            overlay = rolePreviewOverlay,
            content = rolePreviewContent,
            mapBackground = rolePreviewMapBackground,
            roleImage = rolePreviewImage,
            roleName = rolePreviewName,
            roleTeam = rolePreviewTeam,
            roleFunction = rolePreviewFunction,
            roleAdvice = rolePreviewAdvice,
            dp = ::dp
        )
        silenceRevealCageDoor = findViewById(R.id.silenceRevealCageDoor)
        silenceRevealCageLeft = findViewById(R.id.silenceRevealCageLeft)
        silenceRevealCageLock = findViewById(R.id.silenceRevealCageLock)
        silenceRevealCageRight = findViewById(R.id.silenceRevealCageRight)
        silenceRevealCard = findViewById(R.id.silenceRevealCard)
        silenceRevealContent = findViewById(R.id.silenceRevealContent)
        silenceRevealOverlay = findViewById(R.id.silenceRevealOverlay)
        silenceRevealPlayerName = findViewById(R.id.silenceRevealPlayerName)
        topStatus = findViewById(R.id.topStatus)
        transitionFromBackground = findViewById(R.id.transitionFromBackground)
        transitionMoon = findViewById(R.id.transitionMoon)
        transitionShade = findViewById(R.id.transitionShade)
        transitionSun = findViewById(R.id.transitionSun)
        transitionTitle = findViewById(R.id.transitionTitle)
        transitionToBackground = findViewById(R.id.transitionToBackground)
        dayNightTransitionAnimator = DayNightTransitionAnimator(
            context = this,
            handler = autoAdvanceHandler,
            overlay = dayNightTransitionOverlay,
            fromBackground = transitionFromBackground,
            toBackground = transitionToBackground,
            sun = transitionSun,
            moon = transitionMoon,
            shade = transitionShade,
            title = transitionTitle,
            backgroundFor = { period ->
                backgroundDrawableFor(themeKey, period == GameplayPeriod.NIGHT)
            },
            onMusicCue = {
                MusicManager.resumeGamePhaseAfterTransition(this, session)
            },
            onFinished = { spec ->
                finishDayNightTransition(spec)
            }
        )
        deathRevealAnimator = DeathRevealAnimator(
            context = this,
            overlay = deathRevealOverlay,
            content = deathRevealContent,
            card = deathRevealCard,
            cardBack = deathRevealCardBack,
            cardFront = deathRevealCardFront,
            bloodLeft = deathRevealBloodLeft,
            bloodRight = deathRevealBloodRight,
            flash = deathRevealFlash,
            playerName = deathRevealPlayerName,
            roleName = deathRevealRoleName,
            roleImageFor = ::roleImageFor,
            dp = ::dp,
            onFinished = ::finishDeathReveal
        )
        silenceRevealAnimator = SilenceRevealAnimator(
            context = this,
            overlay = silenceRevealOverlay,
            content = silenceRevealContent,
            card = silenceRevealCard,
            cageLeft = silenceRevealCageLeft,
            cageRight = silenceRevealCageRight,
            cageDoor = silenceRevealCageDoor,
            cageLock = silenceRevealCageLock,
            playerName = silenceRevealPlayerName,
            dp = ::dp,
            onFinished = ::finishSilenceReveal
        )
        oracleRevealOverlay = findViewById(R.id.oracleRevealOverlay)
        oracleRevealPanel = findViewById(R.id.oracleRevealPanel)
        oracleRevealPlayer = findViewById(R.id.oracleRevealPlayer)
        btnContinueOracleReveal = findViewById(R.id.btnContinueOracleReveal)
        btnContinueOracleReveal.setOnClickListener { dismissOracleReveal() }
        traitorRevealCards = findViewById(R.id.traitorRevealCards)
        traitorRevealContent = findViewById(R.id.traitorRevealContent)
        traitorRevealOverlay = findViewById(R.id.traitorRevealOverlay)
        traitorRevealAnimator = TraitorRevealAnimator(
            overlay = traitorRevealOverlay,
            content = traitorRevealContent,
            cards = traitorRevealCards,
            handler = autoAdvanceHandler
        )
        btnContinueJesterVictory = findViewById(R.id.btnContinueJesterVictory)
        jesterConfettiLayer = findViewById(R.id.jesterConfettiLayer)
        jesterHornLeft = findViewById(R.id.jesterHornLeft)
        jesterHornRight = findViewById(R.id.jesterHornRight)
        jesterVictoryImage = findViewById(R.id.jesterVictoryImage)
        jesterVictoryMessage = findViewById(R.id.jesterVictoryMessage)
        jesterVictoryOverlay = findViewById(R.id.jesterVictoryOverlay)
        jesterVictoryPanel = findViewById(R.id.jesterVictoryPanel)
        jesterVictoryPlayer = findViewById(R.id.jesterVictoryPlayer)
        jesterVictoryAnimator = JesterVictoryAnimator(
            overlay = jesterVictoryOverlay,
            panel = jesterVictoryPanel,
            hornLeft = jesterHornLeft,
            hornRight = jesterHornRight,
            confettiLayer = jesterConfettiLayer,
            continueButton = btnContinueJesterVictory
        )
        winnerRevealBackground = findViewById(R.id.winnerRevealBackground)
        winnerRevealCards = findViewById(R.id.winnerRevealCards)
        winnerRevealContent = findViewById(R.id.winnerRevealContent)
        winnerRevealOverlay = findViewById(R.id.winnerRevealOverlay)
        winnerRevealPanel = findViewById(R.id.winnerRevealPanel)
        winnerRevealPersonalResult = findViewById(R.id.winnerRevealPersonalResult)
        winnerRevealScroll = findViewById(R.id.winnerRevealScroll)
        winnerRevealShine = findViewById(R.id.winnerRevealShine)
        winnerRevealTitle = findViewById(R.id.winnerRevealTitle)
        winnerSummaryDuration = findViewById(R.id.winnerSummaryDuration)
        winnerSummaryHighlight = findViewById(R.id.winnerSummaryHighlight)
        winnerSummaryPlayers = findViewById(R.id.winnerSummaryPlayers)
        winnerSummaryRounds = findViewById(R.id.winnerSummaryRounds)
        winnerSummaryTimeline = findViewById(R.id.winnerSummaryTimeline)
        winnerRevealAnimator = WinnerRevealAnimator(
            overlay = winnerRevealOverlay,
            panel = winnerRevealPanel,
            title = winnerRevealTitle,
            personalResult = winnerRevealPersonalResult,
            shine = winnerRevealShine,
            dp = ::dp
        )
        winnerResultsRenderer = WinnerResultsRenderer(
            context = this,
            content = winnerRevealContent,
            cards = winnerRevealCards,
            rounds = winnerSummaryRounds,
            duration = winnerSummaryDuration,
            eliminatedCount = winnerSummaryPlayers,
            eliminatedPlayers = winnerSummaryHighlight,
            timeline = winnerSummaryTimeline,
            roleImageFor = ::roleImageFor
        )

        applyGameplayTextScale()

        btnSettings.setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.PANEL)
            startActivity(Intent(this, OpcionesActivity::class.java))
        }
        btnAction.setOnClickListener { handleCurrentPhase() }
        btnRevealCard.setOnClickListener { toggleHumanCard() }
        btnToggleChat.setOnClickListener { toggleChatPanel() }
        btnToggleEventLog.setOnClickListener { toggleEventLog() }
        eventLogHeader.setOnClickListener { toggleEventLog() }
        findViewById<ImageButton>(R.id.btnCloseChat).setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.PANEL)
            closeChatPanel()
        }
        btnSendChat.setOnClickListener { sendHumanChatMessage() }
        chatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendHumanChatMessage()
                true
            } else {
                false
            }
        }
        chatInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setChatKeyboardCompact(true)
            }
        }
        chatInput.doAfterTextChanged { text ->
            renderChatCharacterCount(text?.length ?: 0)
        }
        chatNewMessages.setOnClickListener {
            acknowledgeNewChatMessages()
        }
        roleCard.setOnClickListener { showRolePreview() }
        rolePreviewContent.setOnClickListener { }
        rolePreviewOverlay.setOnClickListener { closeRolePreview() }
        privateFeedbackOverlay.setOnClickListener { dismissCurrentFeedback() }
        actionFeedbackBanner.setOnClickListener { hideActionFeedbackBanner() }
        findViewById<ImageButton>(R.id.btnCloseRolePreview).setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.PANEL)
            closeRolePreview()
        }
        findViewById<Button>(R.id.btnContinueRolePreview).setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.CONFIRM)
            closeRolePreview()
        }
        traitorRevealOverlay.setOnClickListener { dismissTraitorReveal() }
        jesterVictoryOverlay.setOnClickListener { }
        btnContinueJesterVictory.setOnClickListener { dismissJesterVictory() }
        findViewById<Button>(R.id.btnWinnerReturnLobby).setOnClickListener { returnToLobby() }

        eventLogBackground.setImageResource(logDrawableFor(themeKey))
        configureChatPanelLayout()
        renderChatPanelVisibility(animate = false)
        if (shouldPresentRolePreview) {
            isRolePreviewOpen = true
            rolePreviewAnimator.reserveVisible()
        }
        renderGame()
        gameplayRoot.post {
            renderPlayerColumns()
            if (shouldPresentRolePreview) {
                isRolePreviewOpen = false
                showRolePreview(initialReveal = shouldShowInitialRoleReveal)
            }
        }
    }

    override fun onDestroy() {
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = false)
        settleWinnerReveal()
        cancelActionPulse()
        cancelFeedbackPresentation(keepPending = false)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        if (isFinishing) {
            MusicManager.stopVictoryMusic()
        } else {
            MusicManager.pauseVictoryMusic()
        }
        super.onDestroy()
    }

    override fun onPause() {
        restoreRolePreviewOnResume = isRolePreviewOpen
        pauseCountdown()
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = true)
        settleWinnerReveal()
        cancelActionPulse()
        cancelFeedbackPresentation(keepPending = true)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        MusicManager.pauseVictoryMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameplayRoot.isInitialized) {
            applyGameplayTextScale()
        }
        if (::session.isInitialized && restoreRolePreviewOnResume) {
            restoreRolePreviewOnResume = false
            gameplayRoot.post { showRolePreview() }
            return
        }
        if (::session.isInitialized && isWinnerRevealVisible) {
            MusicManager.resumeVictoryMusic(this)
            return
        }
        if (::session.isInitialized && feedbackState.pending?.blocksGameplay == true) {
            showPendingPrivateFeedback()
            return
        }
        if (
            ::session.isInitialized &&
            !isDayNightTransitionRunning &&
            !isDeathRevealRunning &&
            !isSilenceRevealRunning
        ) {
            MusicManager.playGamePhase(this, session)
            resumeGameFlowAfterBlockingUi()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(STATE_SESSION, session)
        outState.putString(STATE_TRANSITION_KEY, lastPresentedTransitionKey)
        outState.putString(STATE_PRESENTED_PERIOD, presentedPeriod?.name)
        outState.putString(STATE_BLOCKING_FEEDBACK_PERIOD, blockingFeedbackPeriod?.name)
        outState.putBoolean(STATE_TRAITOR_REVEAL_COMPLETED, traitorRevealCompleted)
        outState.putBoolean(STATE_WINNER_REVEAL_PRESENTED, winnerRevealPresented)
        outState.putInt(STATE_PRESENTED_SPECIAL_VICTORY_COUNT, presentedSpecialVictoryCount)
        outState.putBoolean(STATE_CHAT_OPEN, isChatOpen)
        outState.putBoolean(STATE_EVENT_LOG_EXPANDED, isEventLogExpanded)
        outState.putBoolean(
            STATE_ROLE_PREVIEW_OPEN,
            isRolePreviewOpen || restoreRolePreviewOnResume
        )
        outState.putString(STATE_SELECTED_TARGET, selectedTarget)
        outState.putString(STATE_COUNTDOWN_STAGE, countdown.stage?.name)
        outState.putInt(STATE_COUNTDOWN_PHASE_INDEX, countdown.phaseIndex)
        outState.putLong(STATE_COUNTDOWN_REMAINING_MS, countdownRemainingForSave())
        outState.putLong(STATE_COUNTDOWN_TOTAL_MS, countdown.totalMs)
        feedbackState.pending?.takeIf { it.blocksGameplay }?.let {
            outState.putSerializable(STATE_PENDING_FEEDBACK, it)
        }
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isOracleRevealVisible ||
            feedbackState.privateVisible
        ) return
        if (isJesterVictoryVisible) return
        if (isWinnerRevealVisible) {
            returnToLobby()
            return
        }
        if (isRolePreviewOpen) {
            closeRolePreview()
            return
        }
        if (isChatOpen) {
            closeChatPanel()
            return
        }
        if (isEventLogExpanded) {
            toggleEventLog()
            return
        }
        super.onBackPressed()
    }

    private fun handleCurrentPhase() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(this, "La siguiente fase comienza enseguida.", Toast.LENGTH_SHORT).show()
            return
        }
        pauseCountdown()
        if (session.winner.isNotBlank()) {
            renderGame()
            return
        }

        val selectedAction = GameplayTableUi.confirmedTargetActionLabel(session, selectedTarget)
        if (selectedAction != null) {
            performTargetAction(selectedTarget)
            return
        }

        if (selectedTarget.isBlank() && GameplayTableUi.canHumanMedicSelfProtect(session)) {
            performTargetAction(GameEngine.humanPlayer(session).name)
            return
        }

        if (GameEngine.needsInitialDesertorChoice(session) || GameEngine.canDesertorReconsider(session)) {
            showDesertorTeamDialog()
            return
        }

        val human = GameEngine.humanPlayer(session)
        if (
            session.phase == GamePhase.NOCHE_ORACULO &&
            human.role?.key == RoleCatalog.ORACULO
        ) {
            session = GameEngine.skipOraclePower(session)
            renderGame()
            return
        }
        if (
            session.phase == GamePhase.DIA_DEBATE &&
            human.role?.key == "alcalde" &&
            !session.alcaldeRevealed
        ) {
            val before = session
            session = GameEngine.revealAlcalde(session)
            val feedback = GameplayTableUi.feedbackForMayorReveal(before, session)
            renderGame()
            feedback?.let { showActionFeedbackBanner(it) }
            return
        }

        if (GameEngine.requiresHumanInput(session)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(this, targetActionMessage(), Toast.LENGTH_SHORT).show()
            renderGame()
            return
        }

        advanceCurrentPhase()
    }

    private fun advanceCurrentPhase() {
        session = when (session.phase) {
            GamePhase.REPARTO -> GameEngine.startNight(session)
            GamePhase.NOCHE_ASESINO -> GameEngine.resolveAssassin(session, "")
            GamePhase.NOCHE_MERCENARIO -> GameEngine.resolveMercenary(session, "")
            GamePhase.NOCHE_POLICIA -> GameEngine.resolvePolice(session, "")
            GamePhase.NOCHE_MEDICO -> GameEngine.resolveMedic(session, "")
            GamePhase.NOCHE_ORACULO -> GameEngine.resolveOracle(session, "")
            GamePhase.AMANECER -> GameEngine.resolveDawn(session)
            GamePhase.DIA_DEBATE -> GameEngine.resolveDayDebate(session)
            GamePhase.CONTRAPUNTO -> GameEngine.resolveContrapunto(session, "")
            GamePhase.VOTACION -> GameEngine.resolveVoting(session, "")
            GamePhase.ALCALDE_DESEMPATE -> session
            GamePhase.RESULTADO -> GameEngine.resolveResult(session)
        }
        clearSelection()
        renderGame()
    }

    private fun performTargetAction(targetName: String) {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(this, "Espera a que comience la fase.", Toast.LENGTH_SHORT).show()
            return
        }
        pauseCountdown()
        if (!GameEngine.canActOnTarget(session, targetName)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(this, "No podes actuar sobre ese jugador.", Toast.LENGTH_SHORT).show()
            renderGame()
            return
        }

        val before = session
        selectedTarget = targetName
        val resolved = GameEngine.resolveHumanTargetAction(session, targetName)
        val feedback = GameplayTableUi.feedbackForResolvedAction(before, resolved, targetName)
        session = resolved
        clearSelection()
        val feedbackPresentation = feedbackState.submit(feedback)
        blockingFeedbackPeriod = if (
            feedbackPresentation == GameplayFeedbackState.Presentation.PRIVATE
        ) {
            GameplayTableUi.transitionSpec(before).period
        } else {
            null
        }
        when (feedbackPresentation) {
            GameplayFeedbackState.Presentation.PRIVATE -> showPendingPrivateFeedback()
            GameplayFeedbackState.Presentation.BANNER -> {
                renderGame()
                if (feedback != null) {
                    showActionFeedbackBanner(feedback)
                }
            }
            GameplayFeedbackState.Presentation.NONE -> renderGame()
        }
    }

    private fun renderGame() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (
            selectedTarget.isNotBlank() &&
            !GameEngine.canActOnTarget(session, selectedTarget)
        ) {
            clearSelection()
        }
        val newlyDeadPlayers = collectNewlyDeadPlayers()
        collectNewlyMutedPlayers()
        val transitionSpec = GameplayTableUi.transitionSpec(session)
        val blockingFeedbackPending = feedbackState.blocksGameplay()
        val specialVictoryPending =
            session.specialVictories.size > presentedSpecialVictoryCount
        val shouldStartTransition = !blockingFeedbackPending &&
            !specialVictoryPending &&
            !isDayNightTransitionRunning &&
            GameplayTableUi.shouldPresentTransition(transitionSpec, lastPresentedTransitionKey)
        if (blockingFeedbackPending || specialVictoryPending) {
            MusicManager.pauseForTransition()
        } else if (shouldStartTransition) {
            isDayNightTransitionRunning = true
            lastPresentedTransitionKey = transitionSpec.key
            MusicManager.pauseForTransition()
        } else if (!isDayNightTransitionRunning) {
            if (session.winner.isBlank()) {
                MusicManager.playGamePhase(this, session)
            } else {
                MusicManager.pauseForTransition()
            }
        }

        val phaseText = phaseText(session.phase)
        val publicMessage = if (session.winner.isNotBlank()) {
            "Fin de partida. Gano ${session.winner}."
        } else {
            session.publicAnnouncement.ifBlank { phaseText.subtitle }
        }
        val eventChanged = lastRenderedPhase != session.phase || lastRenderedAnnouncement != publicMessage
        updateUnreadChatCount()
        val visiblePeriod = when {
            blockingFeedbackPending -> blockingFeedbackPeriod ?: GameplayPeriod.NIGHT
            specialVictoryPending -> presentedPeriod ?: GameplayPeriod.DAY
            isDayNightTransitionRunning -> presentedPeriod ?: transitionSpec.period
            else -> transitionSpec.period
        }
        renderThemedBackground(visiblePeriod)
        renderNarrator(phaseText, firstRoundRoleTip() ?: publicMessage, eventChanged)
        renderEventLogPanel()
        renderEventLog(publicMessage, phaseText)
        currentPlayerName.text = GameEngine.humanPlayer(session).name
        renderPersonalStatus()
        currentPlayerHint.text = privateHintText()
        renderAdvanceButton()
        renderHumanCardIfVisible()
        renderPlayerColumns(newlyDeadPlayers.map { it.name }.toSet())
        renderChatPanel()
        renderChatBadge()
        lastRenderedPhase = session.phase
        lastRenderedAnnouncement = publicMessage
        if (blockingFeedbackPending) {
            showPendingPrivateFeedback()
        } else if (shouldStartTransition) {
            startDayNightTransition(transitionSpec)
        } else if (!isDayNightTransitionRunning) {
            resumeGameFlowAfterBlockingUi()
        }
    }

    private fun collectNewlyDeadPlayers(): List<GamePlayer> {
        val currentDeadPlayers = session.players.filterNot { it.alive }
        val newlyDeadPlayers = currentDeadPlayers.filterNot { it.name in knownDeadPlayers }
        GameplayTableUi.newlyKilledAtDawn(session, knownDeadPlayers)
            .forEach { pendingDeathReveals.addLast(it) }
        knownDeadPlayers = currentDeadPlayers.map { it.name }.toSet()
        return newlyDeadPlayers
    }

    private fun collectNewlyMutedPlayers() {
        GameplayTableUi.newlySilencedAtDawn(session, knownMutedPlayers)
            .forEach { pendingSilenceReveals.addLast(it) }
        knownMutedPlayers = session.players.filter { it.muted }.map { it.name }.toSet()
    }

    private fun renderNarrator(phaseText: PhaseText, publicMessage: String, eventChanged: Boolean) {
        phaseTitle.text = phaseText.title
        phaseSubtitle.text = publicMessage
        if (!eventChanged) return

        topStatus.alpha = 0f
        topStatus.translationY = -dp(4).toFloat()
        topStatus.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun renderEventLog(publicMessage: String, phaseText: PhaseText) {
        val allEvents = GameplayTableUi.historicalPublicEvents(
            session.publicHistory,
            publicMessage,
            phaseText.subtitle
        )
        val latestEvent = allEvents.last()
        eventLogSummary.text = latestEvent
        (eventLogSummary.parent as? HorizontalScrollView)?.scrollTo(0, 0)
        eventLogColorBar.setBackgroundColor(
            Color.parseColor(GameplayTableUi.eventTypeFor(latestEvent, session.phase).colorHex)
        )
        val visibleEvents = if (isEventLogExpanded) allEvents.takeLast(5) else listOf(latestEvent)
        if (
            visibleEvents == lastRenderedEventMessages &&
            lastRenderedEventExpanded == isEventLogExpanded
        ) {
            return
        }
        val previousLastEvent = lastRenderedEventMessages.lastOrNull()
        eventLogContainer.removeAllViews()
        visibleEvents.forEachIndexed { index, message ->
            val row = createEventRow(message)
            eventLogContainer.addView(row)
            if (message != previousLastEvent && index == visibleEvents.lastIndex) {
                row.alpha = 0f
                row.translationY = dp(6).toFloat()
                row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200L)
                    .start()
            }
        }
        lastRenderedEventMessages = visibleEvents
        lastRenderedEventExpanded = isEventLogExpanded
        eventLogScroll.post {
            if (isEventLogExpanded) {
                eventLogScroll.fullScroll(View.FOCUS_DOWN)
            } else {
                eventLogScroll.scrollTo(0, 0)
            }
        }
    }

    private fun toggleEventLog() {
        GameplayEffects.play(this, GameplayEffect.PANEL)
        isEventLogExpanded = !isEventLogExpanded
        if (isEventLogExpanded && isChatOpen) {
            closeChatPanel()
        }
        renderEventLogPanel(animate = true)
        lastRenderedEventExpanded = null
        renderEventLog(
            session.publicAnnouncement.ifBlank { phaseText(session.phase).subtitle },
            phaseText(session.phase)
        )
    }

    private fun renderEventLogPanel(animate: Boolean = false) {
        val targetHeight = dp(if (isEventLogExpanded) 136 else 32)
        val params = eventLogPanel.layoutParams as FrameLayout.LayoutParams
        btnToggleEventLog.text = if (isEventLogExpanded) "\u25B2" else "\u25BC"
        eventLogPanel.elevation = dp(if (isEventLogExpanded) 8 else 4).toFloat()
        eventLogContent.visibility = if (isEventLogExpanded) View.VISIBLE else View.GONE
        if (!animate || eventLogPanel.height <= 0 || eventLogPanel.height == targetHeight) {
            params.height = targetHeight
            eventLogPanel.layoutParams = params
            return
        }

        eventLogHeightAnimator?.cancel()
        eventLogHeightAnimator = ValueAnimator.ofInt(eventLogPanel.height, targetHeight).apply {
            duration = 200L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedParams = eventLogPanel.layoutParams as FrameLayout.LayoutParams
                animatedParams.height = animator.animatedValue as Int
                eventLogPanel.layoutParams = animatedParams
            }
            start()
        }
    }

    private fun createEventRow(message: String): View {
        val type = GameplayTableUi.eventTypeFor(message, session.phase)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.bg_event_log_row)
        row.setPadding(0, dp(if (isEventLogExpanded) 5 else 2), dp(8), dp(if (isEventLogExpanded) 5 else 2))

        val colorBar = View(this)
        colorBar.setBackgroundColor(Color.parseColor(type.colorHex))
        row.addView(
            colorBar,
            LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
        )

        val text = TextView(this)
        text.text = message
        text.setTextColor(getColor(R.color.text_primary))
        text.textSize = if (isEventLogExpanded) 10f else 9f
        text.maxLines = 1
        text.setSingleLine(true)
        text.setPadding(dp(9), 0, 0, 0)
        val scroller = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalFadingEdgeEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                text,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        row.addView(
            scroller,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(if (isEventLogExpanded) 5 else 2))
        row.layoutParams = params
        return row
    }

    private fun renderAdvanceButton() {
        val selectedAction = GameplayTableUi.confirmedTargetActionLabel(session, selectedTarget)
        val validTargets = GameplayTableUi.validHumanTargets(session)
        val mandatoryTargetSelection = GameEngine.requiresHumanInput(session) && validTargets.isNotEmpty()
        val canSelfProtect = selectedTarget.isBlank() &&
            GameplayTableUi.canHumanMedicSelfProtect(session)
        val transitionLocked = countdown.isTransitionLocked(session.phaseIndex)
        val specialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            (session.phase == GamePhase.NOCHE_ORACULO &&
                GameEngine.isHumanRoleTurn(session, RoleCatalog.ORACULO)) ||
            (session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed)
        val label = when {
            session.winner.isNotBlank() -> "FINAL"
            selectedAction != null -> selectedAction
            canSelfProtect -> "SALVARME"
            GameEngine.needsInitialDesertorChoice(session) -> "ELEGIR BANDO"
            GameEngine.canDesertorReconsider(session) -> "REVISAR BANDO"
            session.phase == GamePhase.NOCHE_ORACULO &&
                GameEngine.isHumanRoleTurn(session, RoleCatalog.ORACULO) -> "GUARDAR PODER"
            session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed -> "REVELARME"
            mandatoryTargetSelection -> "ELEGIR OBJETIVO"
            session.phase == GamePhase.REPARTO -> "NOCHE"
            session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "payador" &&
                !session.payadorUsed -> "VOTAR SIN USAR"
            else -> phaseText(session.phase).actionLabel
        }
        btnAction.text = label
        val requiresAttention = session.winner.isBlank() &&
            (selectedAction != null || canSelfProtect || specialDecision)
        btnAction.isEnabled = !transitionLocked &&
            session.winner.isBlank() &&
            (!mandatoryTargetSelection || selectedAction != null || canSelfProtect || specialDecision)
        applyPrimaryActionVisual(label, requiresAttention)
        btnAction.alpha = when {
            btnAction.isEnabled -> 1f
            requiresAttention -> 0.92f
            else -> 0.55f
        }
        updateActionAttentionPulse(requiresAttention)
    }

    private fun applyPrimaryActionVisual(label: String, emphasized: Boolean) {
        val tone = if (emphasized) {
            GameplayTableUi.actionToneFor(label)
        } else {
            GameplayActionTone.DEFAULT
        }
        btnAction.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(tone.colorHex))
            setStroke(
                dp(1),
                if (emphasized) getColor(R.color.accent_gold) else getColor(R.color.btn_dark_border)
            )
            cornerRadius = dp(6).toFloat()
        }
        btnAction.setTextColor(
            getColor(if (tone.darkText) R.color.bg_dark else R.color.text_primary)
        )
    }

    private fun updateActionAttentionPulse(requiresAttention: Boolean) {
        val attentionKey = if (requiresAttention) {
            "${session.phase.name}:${session.round}:${btnAction.text}"
        } else {
            null
        }
        if (attentionKey == lastActionAttentionKey) return

        cancelActionPulse()
        lastActionAttentionKey = attentionKey
        if (attentionKey == null) return

        val grow = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnAction, View.SCALE_X, 1f, 1.05f),
                ObjectAnimator.ofFloat(btnAction, View.SCALE_Y, 1f, 1.05f)
            )
            duration = 180L
        }
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnAction, View.SCALE_X, 1.05f, 1f),
                ObjectAnimator.ofFloat(btnAction, View.SCALE_Y, 1.05f, 1f)
            )
            duration = 240L
        }
        actionPulseAnimator = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            playSequentially(grow, settle)
            start()
        }
    }

    private fun cancelActionPulse() {
        actionPulseAnimator?.cancel()
        actionPulseAnimator = null
        if (::btnAction.isInitialized) {
            btnAction.scaleX = 1f
            btnAction.scaleY = 1f
        }
    }

    private fun renderPlayerColumns(newlyDeadPlayers: Set<String> = emptySet()) {
        val (leftPlayers, rightPlayers) = GameplayTableUi.splitCompanions(session.players)
        val totalPlayers = session.players.size.coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
        val measuredHeightPx = listOf(leftPlayersScroll.height, rightPlayersScroll.height)
            .filter { it > 0 }
            .minOrNull()
        val availableHeightDp = measuredHeightPx?.let(::pxToDp)
            ?: (resources.configuration.screenHeightDp - 16).coerceAtLeast(240)
        val metrics = GameplayTableUi.companionCardMetrics(totalPlayers, availableHeightDp)
        applyAdaptiveGameplayLayout(metrics)

        val desiredNames = (leftPlayers + rightPlayers).map { it.name }.toSet()
        playerCardViews.keys.toList()
            .filterNot { it in desiredNames }
            .forEach { name ->
                playerCardViews.remove(name)?.root?.let { root ->
                    (root.parent as? ViewGroup)?.removeView(root)
                }
            }

        syncPlayerContainer(leftPlayersContainer, leftPlayers, metrics, newlyDeadPlayers)
        syncPlayerContainer(rightPlayersContainer, rightPlayers, metrics, newlyDeadPlayers)
    }

    private fun applyAdaptiveGameplayLayout(metrics: CompanionCardMetrics) {
        leftPlayersScroll.layoutParams = (leftPlayersScroll.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.columnWidthDp)
        }
        rightColumn.layoutParams = (rightColumn.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.columnWidthDp)
        }

        leftPlayersScroll.isVerticalScrollBarEnabled = metrics.scrollEnabled
        rightPlayersScroll.isVerticalScrollBarEnabled = metrics.scrollEnabled
        leftPlayersScroll.overScrollMode = if (metrics.scrollEnabled) {
            View.OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            View.OVER_SCROLL_NEVER
        }
        rightPlayersScroll.overScrollMode = leftPlayersScroll.overScrollMode
        val containerGravity = if (metrics.scrollEnabled) {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.CENTER
        }
        leftPlayersContainer.gravity = containerGravity
        rightPlayersContainer.gravity = containerGravity

        gameplayBody.requestLayout()
    }

    private fun syncPlayerContainer(
        container: LinearLayout,
        players: List<GamePlayer>,
        metrics: CompanionCardMetrics,
        newlyDeadPlayers: Set<String>
    ) {
        val containerNames = players.map { it.name }.toSet()
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index)
            if (child.tag !in containerNames) {
                container.removeViewAt(index)
            }
        }

        players.forEachIndexed { index, player ->
            val holder = playerCardViews.getOrPut(player.name) {
                createSidePlayerCard(metrics).also { created ->
                    created.root.tag = player.name
                    created.root.alpha = 0f
                    created.root.translationY = dp(6).toFloat()
                    created.root.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200L)
                        .start()
                }
            }
            val currentParent = holder.root.parent as? ViewGroup
            if (currentParent !== container) {
                currentParent?.removeView(holder.root)
                container.addView(holder.root, index.coerceAtMost(container.childCount))
            } else if (container.indexOfChild(holder.root) != index) {
                container.removeView(holder.root)
                container.addView(holder.root, index.coerceAtMost(container.childCount))
            }
            bindSidePlayerCard(holder, player, metrics)
            if (player.name in newlyDeadPlayers) {
                animatePlayerDeath(holder.root)
            }
        }
    }

    private fun animatePlayerDeath(view: View) {
        view.alpha = 1f
        view.background = ColorDrawable(Color.argb(92, 150, 24, 24))

        val shake = ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f,
            -dp(7).toFloat(),
            dp(7).toFloat(),
            -dp(4).toFloat(),
            dp(4).toFloat(),
            0f
        ).apply {
            duration = 320L
        }
        val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.4f).apply {
            startDelay = 150L
            duration = 650L
            interpolator = AccelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(shake, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.background = null
                    view.translationX = 0f
                    view.alpha = 0.4f
                }
            })
            start()
        }
    }

    private fun createSidePlayerCard(metrics: CompanionCardMetrics): SidePlayerCardHolder {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.clipChildren = false
        item.clipToPadding = false
        item.minimumWidth = dp(metrics.minCardWidthDp)

        val cardFace = FrameLayout(this)
        cardFace.clipChildren = false
        cardFace.clipToPadding = false
        val cardBack = ImageView(this)
        cardBack.setImageResource(R.drawable.card_back_traidores)
        cardBack.scaleType = ImageView.ScaleType.FIT_CENTER
        cardFace.addView(
            cardBack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val avatar = TextView(this)
        avatar.gravity = Gravity.CENTER
        avatar.setBackgroundResource(R.drawable.bg_player_avatar)
        avatar.setTextColor(getColor(R.color.accent_gold))
        avatar.setTypeface(null, Typeface.BOLD)
        cardFace.addView(
            avatar,
            FrameLayout.LayoutParams(
                dp(metrics.avatarSizeDp),
                dp(metrics.avatarSizeDp),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val mutedBadge = TextView(this)
        mutedBadge.text = "MUDO"
        mutedBadge.gravity = Gravity.CENTER
        mutedBadge.setTextColor(getColor(R.color.accent_gold))
        mutedBadge.setBackgroundResource(R.drawable.bg_player_chip)
        mutedBadge.textSize = 5.5f
        mutedBadge.setTypeface(null, Typeface.BOLD)
        mutedBadge.setPadding(dp(3), 0, dp(3), 0)
        cardFace.addView(
            mutedBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(12),
                Gravity.TOP or Gravity.END
            )
        )

        val cardParams = LinearLayout.LayoutParams(
            dp(metrics.cardWidthDp),
            dp(metrics.cardHeightDp)
        )
        item.addView(cardFace, cardParams)

        val name = TextView(this)
        name.gravity = Gravity.CENTER
        name.ellipsize = TextUtils.TruncateAt.END
        name.includeFontPadding = false
        name.maxLines = 1
        name.setSingleLine(true)
        name.typeface = ResourcesCompat.getFont(this, R.font.cormorant_garamond)
        name.setTypeface(name.typeface, Typeface.BOLD)
        item.addView(
            name,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(metrics.nameHeightDp)
            )
        )

        return SidePlayerCardHolder(item, cardFace, cardBack, avatar, mutedBadge, name)
    }

    private fun bindSidePlayerCard(
        holder: SidePlayerCardHolder,
        player: GamePlayer,
        metrics: CompanionCardMetrics
    ) {
        val isAlive = GameEngine.isAlive(player)
        val isOracleGuest =
            session.phase == GamePhase.DIA_DEBATE &&
                session.oracleInvitedPlayer == player.name
        val actionLabel = GameEngine.targetActionLabel(session, player.name)
        val transitionLocked = countdown.isTransitionLocked(session.phaseIndex)
        val isActionable = actionLabel.isNotBlank() && !transitionLocked
        val isSelected = player.name == selectedTarget

        holder.root.minimumWidth = dp(metrics.minCardWidthDp)
        holder.root.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(metrics.itemHeightDp)
        ).apply {
            bottomMargin = dp(metrics.itemGapDp)
        }
        holder.cardFace.layoutParams = (holder.cardFace.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.cardWidthDp)
            height = dp(metrics.cardHeightDp)
        }
        holder.avatar.layoutParams = (holder.avatar.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp(metrics.avatarSizeDp)
            height = dp(metrics.avatarSizeDp)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(2)
        }
        holder.avatar.text = if (isAlive || isOracleGuest) {
            GameplayTableUi.playerInitial(player)
        } else {
            "\u2620"
        }
        holder.avatar.textSize =
            if (isAlive || isOracleGuest) metrics.nameTextSp else metrics.nameTextSp + 1f
        holder.mutedBadge.visibility = if (isAlive && player.muted) View.VISIBLE else View.GONE

        holder.name.layoutParams = (holder.name.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(metrics.nameHeightDp)
        }
        holder.name.text = player.name
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            holder.name,
            7,
            ceil(metrics.nameTextSp.toDouble()).toInt().coerceAtLeast(8),
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        holder.name.setTextColor(
            getColor(
                when {
                    isOracleGuest -> R.color.accent_gold
                    !isAlive -> R.color.text_muted
                    isSelected -> R.color.accent_gold
                    else -> R.color.text_primary
                }
            )
        )
        holder.name.paintFlags = if (isAlive) {
            holder.name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            holder.name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        holder.cardFace.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(4).toFloat()
            when {
                isSelected -> setStroke(dp(3), getColor(R.color.accent_gold))
                isActionable -> setStroke(dp(2), getColor(R.color.accent_gold))
            }
        }
        if (holder.selected != isSelected) {
            holder.root.animate()
                .scaleX(if (isSelected) 1.035f else 1f)
                .scaleY(if (isSelected) 1.035f else 1f)
                .setDuration(180L)
                .start()
            holder.selected = isSelected
        }

        holder.root.alpha = if (isAlive || isActionable || isOracleGuest) 1f else 0.4f
        holder.root.setOnClickListener {
            when {
                isActionable -> {
                    GameplayEffects.play(this, GameplayEffect.SELECT)
                    selectedTarget = if (isSelected) "" else player.name
                    currentPlayerHint.text = privateHintText()
                    renderAdvanceButton()
                    renderPlayerColumns()
                }
                !isAlive -> {
                    GameplayEffects.play(this, GameplayEffect.ERROR)
                    Toast.makeText(this, "${player.name} esta eliminado.", Toast.LENGTH_SHORT).show()
                }
                else -> GameplayEffects.play(this, GameplayEffect.ERROR)
            }
        }
        holder.root.contentDescription = when {
            isOracleGuest -> "${player.name}, invocado para discutir"
            !isAlive -> "${player.name}, eliminado"
            player.muted -> "${player.name}, muteado durante el dia"
            isSelected -> "${player.name}, objetivo seleccionado"
            isActionable -> "${player.name}, objetivo disponible para $actionLabel"
            else -> player.name
        }
    }

    private fun phaseText(phase: GamePhase): PhaseText {
        return when (phase) {
            GamePhase.REPARTO -> PhaseText(
                "TU ROL",
                "Revisa tu carta. La primera noche empieza enseguida.",
                "NOCHE"
            )
            GamePhase.NOCHE_ASESINO -> PhaseText(
                "NOCHE ${session.round}",
                "Los Traidores se mueven en silencio.",
                if (GameEngine.isHumanRoleTurn(session, "asesino")) "MATAR" else "ACELERAR"
            )
            GamePhase.NOCHE_MERCENARIO -> PhaseText(
                "NOCHE ${session.round}",
                "Alguien intenta callar una voz para el dia.",
                if (GameEngine.isHumanRoleTurn(session, "mercenario")) "SILENCIAR" else "ACELERAR"
            )
            GamePhase.NOCHE_POLICIA -> PhaseText(
                "NOCHE ${session.round}",
                "Alguien busca una pista en secreto.",
                if (GameEngine.isHumanRoleTurn(session, "policia")) "INVESTIGAR" else "ACELERAR"
            )
            GamePhase.NOCHE_MEDICO -> PhaseText(
                "NOCHE ${session.round}",
                "Alguien intenta proteger a un jugador.",
                if (GameEngine.isHumanRoleTurn(session, "medico")) "SALVAR" else "ACELERAR"
            )
            GamePhase.NOCHE_ORACULO -> PhaseText(
                "NOCHE ${session.round}",
                "El Oraculo puede llamar a una voz que ya abandono la mesa.",
                if (GameEngine.isHumanRoleTurn(session, RoleCatalog.ORACULO)) {
                    "GUARDAR PODER"
                } else {
                    "ACELERAR"
                }
            )
            GamePhase.AMANECER -> PhaseText("AMANECER", "La mesa despierta y escucha lo ocurrido.", "AMANECER")
            GamePhase.DIA_DEBATE -> PhaseText("DIA ${session.round}", "La mesa debate antes de votar.", "VOTAR")
            GamePhase.CONTRAPUNTO -> PhaseText(
                "CONTRAPUNTO",
                "Selecciona un participante y confirma el contrapunto.",
                "SENALAR"
            )
            GamePhase.VOTACION -> PhaseText(
                "VOTACION",
                "Selecciona un jugador y confirma tu voto.",
                "VOTAR"
            )
            GamePhase.ALCALDE_DESEMPATE -> PhaseText(
                "DESEMPATE",
                "El Alcalde decide entre los jugadores empatados.",
                "DECIDIR"
            )
            GamePhase.RESULTADO -> PhaseText(
                "RESULTADO",
                "La mesa recibe el resultado publico.",
                if (session.winner.isBlank()) "CONTINUAR" else "FINAL"
            )
        }
    }

    private fun toggleChatPanel() {
        GameplayEffects.play(this, GameplayEffect.PANEL)
        if (isChatOpen) {
            closeChatPanel()
            return
        }
        if (isEventLogExpanded) {
            isEventLogExpanded = false
            renderEventLogPanel(animate = true)
        }
        isChatOpen = true
        unreadChatCount = 0
        newChatMessagesWhileTyping = 0
        lastSeenChatCount = session.chatHistory.size
        renderChatPanelVisibility(animate = true)
        renderChatPanel()
        renderChatBadge()
    }

    private fun closeChatPanel() {
        if (!isChatOpen) return
        isChatOpen = false
        newChatMessagesWhileTyping = 0
        chatInput.clearFocus()
        WindowCompat.getInsetsController(window, gameplayRoot)
            .hide(WindowInsetsCompat.Type.ime())
        setChatKeyboardCompact(false)
        renderChatPanelVisibility(animate = true)
        renderChatBadge()
        renderNewChatMessageNotice()
    }

    private fun renderChatPanelVisibility(animate: Boolean) {
        chatPanel.animate().cancel()
        if (isChatOpen) {
            chatPanel.visibility = View.VISIBLE
            if (animate) {
                chatPanel.translationX = dp(36).toFloat()
                chatPanel.alpha = 0f
                chatPanel.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(210L)
                    .start()
            } else {
                chatPanel.translationX = 0f
                chatPanel.alpha = 1f
            }
        } else if (animate && chatPanel.visibility == View.VISIBLE) {
            chatPanel.animate()
                .translationX(dp(36).toFloat())
                .alpha(0f)
                .setDuration(190L)
                .withEndAction {
                    chatPanel.visibility = View.GONE
                    chatPanel.translationX = 0f
                    chatPanel.alpha = 1f
                }
                .start()
        } else {
            chatPanel.visibility = View.GONE
            chatPanel.translationX = 0f
            chatPanel.alpha = 1f
        }
        btnToggleChat.alpha = if (isChatOpen) 1f else 0.82f
    }

    private fun renderChatPanel() {
        btnToggleChat.alpha = if (isChatOpen) 1f else 0.9f
        if (!isChatOpen) return

        val messages = session.chatHistory.filterNot { it.isGod }.takeLast(12)
        renderChatMessages(messages)

        val canChat = GameEngine.canHumanChat(session)
        chatInput.isEnabled = canChat
        btnSendChat.isEnabled = canChat
        chatInput.hint = chatInputHint(canChat)
        btnSendChat.alpha = if (canChat) 1f else 0.45f
        renderChatCharacterCount(chatInput.text.length)
        renderNewChatMessageNotice()
        if (newChatMessagesWhileTyping == 0) {
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun configureChatPanelLayout() {
        centerColumn.post {
            applyChatPanelDimensions()
        }
        ViewCompat.setOnApplyWindowInsetsListener(gameplayRoot) { _, insets ->
            setChatKeyboardCompact(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        ViewCompat.requestApplyInsets(gameplayRoot)
    }

    private fun setChatKeyboardCompact(compact: Boolean) {
        if (isChatKeyboardCompact == compact && chatPanel.isLaidOut) return
        isChatKeyboardCompact = compact
        applyChatPanelDimensions()
        if (compact) {
            chatPanel.bringToFront()
            chatPanel.visibility = View.VISIBLE
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun applyChatPanelDimensions() {
        if (!::chatPanel.isInitialized || gameplayRoot.width == 0) return
        val params = chatPanel.layoutParams as FrameLayout.LayoutParams
        val widthRatio = if (isChatKeyboardCompact) {
            CHAT_PANEL_COMPACT_WIDTH_RATIO
        } else {
            CHAT_PANEL_WIDTH_RATIO
        }
        params.width = (gameplayRoot.width * widthRatio)
            .toInt()
            .coerceIn(
                dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MIN_WIDTH_DP else CHAT_PANEL_MIN_WIDTH_DP),
                dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MAX_WIDTH_DP else CHAT_PANEL_MAX_WIDTH_DP)
            )
        params.topMargin = dp(
            if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MARGIN_DP else CHAT_PANEL_TOP_MARGIN_DP
        )
        params.bottomMargin = dp(
            if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MARGIN_DP else CHAT_PANEL_BOTTOM_MARGIN_DP
        )
        chatPanel.layoutParams = params
        chatPanel.setPadding(
            dp(if (isChatKeyboardCompact) 6 else 9),
            dp(if (isChatKeyboardCompact) 4 else 9),
            dp(if (isChatKeyboardCompact) 6 else 9),
            dp(if (isChatKeyboardCompact) 5 else 9)
        )
        chatHeader.layoutParams = chatHeader.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 22 else 28)
        }
        chatComposer.layoutParams = chatComposer.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 40)
        }
        chatStatusRow.layoutParams = chatStatusRow.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 18 else 20)
        }
        chatInput.layoutParams = chatInput.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 40)
        }
        btnSendChat.layoutParams = btnSendChat.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 40)
        }
    }

    private fun renderChatMessages(messages: List<GameChatMessage>) {
        chatMessagesContainer.removeAllViews()
        if (messages.isEmpty()) {
            chatMessagesContainer.addView(TextView(this).apply {
                text = "Todavia no hay mensajes."
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setTextColor(getColor(R.color.text_muted))
                textSize = 11f * appliedGameplayTextScale
            })
            return
        }

        val humanName = GameEngine.humanPlayer(session).name
        messages.forEach { message ->
            val ownMessage = message.speaker == humanName
            val row = LinearLayout(this).apply {
                gravity = if (ownMessage) Gravity.END else Gravity.START
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(8))
                setBackgroundResource(
                    if (ownMessage) {
                        R.drawable.bg_chat_bubble_own
                    } else {
                        R.drawable.bg_chat_bubble_other
                    }
                )
            }
            bubble.addView(TextView(this).apply {
                text = if (ownMessage) "VOS" else message.speaker.uppercase()
                maxLines = 1
                setTextColor(
                    getColor(if (ownMessage) R.color.bg_dark else R.color.accent_gold)
                )
                textSize = 8f * appliedGameplayTextScale
                typeface = Typeface.DEFAULT_BOLD
            })
            bubble.addView(TextView(this).apply {
                text = message.message
                maxWidth = dp(250)
                setTextColor(
                    getColor(if (ownMessage) R.color.bg_dark else R.color.text_primary)
                )
                textSize = 11f * appliedGameplayTextScale
            })
            row.addView(
                bubble,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (ownMessage) marginStart = dp(30) else marginEnd = dp(30)
                }
            )
            chatMessagesContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun updateUnreadChatCount() {
        val currentCount = session.chatHistory.size
        if (currentCount > lastSeenChatCount) {
            val humanName = GameEngine.humanPlayer(session).name
            val newMessages = session.chatHistory.drop(lastSeenChatCount)
                .count { !it.isGod && it.speaker != humanName }
            if (isChatOpen) {
                unreadChatCount = 0
                if (chatInput.hasFocus()) {
                    newChatMessagesWhileTyping += newMessages
                }
            } else {
                unreadChatCount += newMessages
            }
            lastSeenChatCount = currentCount
        }
    }

    private fun renderChatCharacterCount(length: Int) {
        chatCharacterCount.text = "$length/$CHAT_MESSAGE_MAX_LENGTH"
        chatCharacterCount.setTextColor(
            getColor(
                when {
                    length >= CHAT_MESSAGE_MAX_LENGTH -> R.color.accent_red
                    length >= CHAT_MESSAGE_WARNING_LENGTH -> R.color.accent_gold
                    else -> R.color.text_muted
                }
            )
        )
    }

    private fun renderNewChatMessageNotice() {
        chatNewMessages.visibility =
            if (newChatMessagesWhileTyping > 0) View.VISIBLE else View.INVISIBLE
        if (newChatMessagesWhileTyping > 0) {
            val label = if (newChatMessagesWhileTyping == 1) "MENSAJE NUEVO" else "MENSAJES NUEVOS"
            chatNewMessages.text = "$newChatMessagesWhileTyping $label - VER"
        }
    }

    private fun acknowledgeNewChatMessages() {
        newChatMessagesWhileTyping = 0
        renderNewChatMessageNotice()
        chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderChatBadge() {
        chatUnreadBadge.visibility = if (unreadChatCount > 0) View.VISIBLE else View.GONE
        chatUnreadBadge.text = unreadChatCount.coerceAtMost(99).toString()
    }

    private fun sendHumanChatMessage() {
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            Toast.makeText(this, "El chat se habilita al comenzar la fase.", Toast.LENGTH_SHORT).show()
            return
        }
        val before = session.chatHistory.size
        session = GameEngine.addHumanChatMessage(session, chatInput.text.toString())
        if (session.chatHistory.size > before) {
            GameplayEffects.play(this, GameplayEffect.CHAT)
            chatInput.text.clear()
        } else if (!GameEngine.canHumanChat(session)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            val human = GameEngine.humanPlayer(session)
            val message = when {
                !human.alive -> "Estas eliminado. Podes mirar el chat, no escribir."
                human.muted -> "Estas muteado. Podes mirar el chat, no escribir."
                else -> "No podes escribir durante esta fase."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        updateUnreadChatCount()
        renderChatPanel()
        renderChatBadge()
    }

    private fun chatInputHint(canChat: Boolean): String {
        if (canChat) return "Escribir..."
        val human = GameEngine.humanPlayer(session)
        if (!human.alive) return "Eliminado: solo lectura"
        if (human.muted) return "Muteado: solo lectura"
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> "La mesa duerme"
            GamePhase.NOCHE_ORACULO -> "La mesa duerme"
            GamePhase.REPARTO,
            GamePhase.AMANECER,
            GamePhase.RESULTADO -> "Solo lectura"
            else -> "Solo lectura"
        }
    }

    private fun scheduleAutoAdvanceIfNeeded() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isOracleRevealVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isRolePreviewOpen ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible ||
            feedbackState.pending?.blocksGameplay == true ||
            desertorDialogOpen
        ) {
            pauseCountdown()
            return
        }
        ensureCountdownForCurrentPhase()
    }

    private fun ensureCountdownForCurrentPhase() {
        if (session.winner.isNotBlank()) {
            clearCountdown()
            return
        }
        countdown.ensurePhase(
            phaseIndex = session.phaseIndex,
            transitionDurationMs = session.timingConfig.normalized().transitionSeconds * 1000L
        )
        startCountdown()
    }

    private fun startCountdown() {
        when (countdown.start(SystemClock.elapsedRealtime())) {
            GameplayCountdown.StartResult.ALREADY_RUNNING -> return
            GameplayCountdown.StartResult.EXPIRED -> {
                onCountdownExpired()
                return
            }
            GameplayCountdown.StartResult.STARTED -> Unit
        }
        lastCountdownSecond = -1
        phaseCountdown.visibility = View.VISIBLE
        renderAdvanceButton()
        updateCountdown()
    }

    private fun updateCountdown() {
        val tick = countdown.tick(SystemClock.elapsedRealtime()) ?: return
        renderCountdown(tick.seconds)
        if (tick.expired) {
            autoAdvanceHandler.removeCallbacks(countdownRunnable)
            onCountdownExpired()
        } else {
            autoAdvanceHandler.postDelayed(countdownRunnable, COUNTDOWN_TICK_MS)
        }
    }

    private fun renderCountdown(seconds: Int) {
        phaseCountdown.text = seconds.coerceAtLeast(0).toString()
        val urgent = seconds in 1..5
        phaseCountdown.setTextColor(getColor(R.color.text_primary))
        phaseProgressFill.setBackgroundColor(getColor(R.color.accent_gold))
        val visualTotalMs = visualCountdownTotalMs()
        val visualRemainingMs = visualCountdownRemainingMs()
        phaseProgressFill.scaleX = if (visualTotalMs > 0L) {
            (visualRemainingMs.toFloat() / visualTotalMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (urgent && seconds != lastCountdownSecond) {
            GameplayEffects.play(this, GameplayEffect.COUNTDOWN)
            phaseCountdown.animate().cancel()
            phaseProgressFill.animate().cancel()
            phaseCountdown.scaleX = 1f
            phaseCountdown.scaleY = 1f
            phaseProgressFill.alpha = 1f
            phaseCountdown.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(130L)
                .withEndAction {
                    phaseCountdown.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(170L)
                        .start()
                }
                .start()
            phaseProgressFill.animate()
                .alpha(0.58f)
                .setDuration(130L)
                .withEndAction {
                    phaseProgressFill.animate()
                        .alpha(1f)
                        .setDuration(170L)
                        .start()
                }
                .start()
        }
        lastCountdownSecond = seconds
    }

    private fun onCountdownExpired() {
        if (session.winner.isNotBlank() || countdown.phaseIndex != session.phaseIndex) {
            clearCountdown()
            return
        }
        if (countdown.stage == CountdownStage.TRANSITION) {
            val phaseSeconds = activePhaseSeconds()
            if (phaseSeconds != null) {
                countdown.beginActive(phaseSeconds * 1000L)
                renderAdvanceButton()
                renderPlayerColumns()
                startCountdown()
                return
            }
        }

        clearCountdown()
        session = when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> {
                if (GameEngine.requiresHumanInput(session)) {
                    GameEngine.resolveHumanTimeout(session)
                } else {
                    advanceSessionWithoutRendering()
                }
            }
            GamePhase.DIA_DEBATE -> GameEngine.resolveDayDebate(session)
            GamePhase.CONTRAPUNTO -> {
                if (GameEngine.requiresHumanInput(session)) {
                    GameEngine.resolveContrapuntoTimeout(session)
                } else {
                    GameEngine.resolveContrapunto(session, "")
                }
            }
            GamePhase.VOTACION -> {
                if (GameEngine.requiresHumanInput(session)) {
                    GameEngine.resolveHumanTimeout(session)
                } else {
                    GameEngine.resolveVoting(session, "")
                }
            }
            GamePhase.ALCALDE_DESEMPATE -> GameEngine.resolveAlcaldeTieTimeout(session)
            GamePhase.REPARTO -> GameEngine.startNight(session)
            GamePhase.AMANECER -> GameEngine.resolveDawn(session)
            GamePhase.RESULTADO -> GameEngine.resolveResult(session)
        }
        clearSelection()
        renderGame()
    }

    private fun advanceSessionWithoutRendering(): GameSession {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> GameEngine.resolveAssassin(session, "")
            GamePhase.NOCHE_MERCENARIO -> GameEngine.resolveMercenary(session, "")
            GamePhase.NOCHE_POLICIA -> GameEngine.resolvePolice(session, "")
            GamePhase.NOCHE_MEDICO -> GameEngine.resolveMedic(session, "")
            GamePhase.NOCHE_ORACULO -> GameEngine.resolveOracle(session, "")
            else -> session
        }
    }

    private fun activePhaseSeconds(): Int? {
        val timing = session.timingConfig.normalized()
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> timing.nightSeconds.takeIf {
                GameEngine.requiresHumanInput(session)
            }
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO -> timing.discussionSeconds
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> timing.votingSeconds
            GamePhase.REPARTO,
            GamePhase.AMANECER,
            GamePhase.RESULTADO -> null
        }
    }

    private fun visualCountdownTotalMs(): Long {
        val transitionMs = session.timingConfig.normalized().transitionSeconds * 1000L
        val activeMs = activePhaseSeconds()?.times(1000L) ?: 0L
        return countdown.visualTotalMs(transitionMs, activeMs)
    }

    private fun visualCountdownRemainingMs(): Long {
        val activeMs = activePhaseSeconds()?.times(1000L) ?: 0L
        return countdown.visualRemainingMs(activeMs)
    }

    private fun pauseCountdown() {
        countdown.pause(SystemClock.elapsedRealtime())
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        phaseCountdown.animate().cancel()
        phaseProgressFill.animate().cancel()
        phaseCountdown.scaleX = 1f
        phaseCountdown.scaleY = 1f
        phaseProgressFill.alpha = 1f
    }

    private fun clearCountdown() {
        pauseCountdown()
        countdown.clear()
        lastCountdownSecond = -1
        phaseCountdown.visibility = View.INVISIBLE
        phaseCountdown.setTextColor(getColor(R.color.text_primary))
        phaseProgressFill.scaleX = 0f
        phaseProgressFill.alpha = 1f
        phaseProgressFill.setBackgroundColor(getColor(R.color.accent_gold))
    }

    private fun countdownRemainingForSave(): Long {
        return countdown.remainingForSave(SystemClock.elapsedRealtime())
    }

    private fun toggleHumanCard() {
        if (session.phase == GamePhase.REPARTO) return
        GameplayEffects.play(this, GameplayEffect.REVEAL)
        isCardRevealed = !isCardRevealed
        renderHumanCardIfVisible()
    }

    private fun renderHumanCardIfVisible() {
        val role = GameEngine.humanPlayer(session).role
        val showRole = isCardRevealed || session.phase == GamePhase.REPARTO
        val borderColor = if (showRole) {
            when (role?.team) {
                GameRules.TRAITOR_WINNER -> Color.parseColor("#A83A36")
                GameRules.TOWN_WINNER -> Color.parseColor("#3F7D4A")
                "Neutral" -> Color.parseColor("#9A7520")
                else -> getColor(R.color.accent_gold)
            }
        } else {
            getColor(R.color.accent_gold)
        }
        roleCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#E6231810"))
            setStroke(dp(3), borderColor)
            cornerRadius = dp(8).toFloat()
        }
        roleName.setTextColor(getColor(if (showRole) R.color.text_primary else R.color.text_secondary))
        if (showRole) {
            roleImage.setImageResource(roleImageFor(role))
            roleName.text = role?.let {
                "${it.name.uppercase()} - ${it.team.uppercase()}"
            } ?: "SIN ROL"
        } else {
            roleImage.setImageResource(R.drawable.card_back_traidores)
            roleName.text = "CARTA OCULTA"
        }
        btnRevealCard.text = when {
            session.phase == GamePhase.REPARTO -> "ROL"
            showRole -> "OCULTAR"
            else -> "REVELAR"
        }
        btnRevealCard.isEnabled = session.phase != GamePhase.REPARTO
        btnRevealCard.alpha = if (btnRevealCard.isEnabled) 1f else 0.7f
    }

    private fun showRolePreview(initialReveal: Boolean = false) {
        if (
            isRolePreviewOpen ||
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isOracleRevealVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible ||
            feedbackState.pending?.blocksGameplay == true ||
            desertorDialogOpen
        ) {
            return
        }
        val role = GameEngine.humanPlayer(session).role ?: return
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (!isCardRevealed && session.phase != GamePhase.REPARTO) {
            isCardRevealed = true
            renderHumanCardIfVisible()
        }

        rolePreviewImage.setImageResource(roleImageFor(role))
        rolePreviewMapBackground.setImageResource(logDrawableFor(themeKey))
        rolePreviewName.text = role.name.uppercase()
        rolePreviewTeam.text = role.team.uppercase()
        rolePreviewTeam.setTextColor(
            Color.parseColor(
                when (role.team) {
                    GameRules.TRAITOR_WINNER -> "#E1746E"
                    GameRules.TOWN_WINNER -> "#8FCB91"
                    else -> "#E0B85F"
                }
            )
        )
        rolePreviewFunction.text = roleFunction(role.key)
        rolePreviewAdvice.text = if (role.key == "mercenario") {
            "Silencia a quien este guiando bien la discusion, pero evita repetir siempre el mismo patron."
        } else {
            roleAdvice(role.key)
        }
        rolePreviewScroll.scrollTo(0, 0)
        isRolePreviewOpen = true
        GameplayEffects.play(this, GameplayEffect.REVEAL)
        rolePreviewAnimator.show(initialReveal)
    }

    private fun closeRolePreview(resumeGameFlow: Boolean = true) {
        if (!::rolePreviewOverlay.isInitialized) return
        val wasOpen = isRolePreviewOpen
        if (!wasOpen || !resumeGameFlow) {
            rolePreviewAnimator.cancelAndHide()
            isRolePreviewOpen = false
            return
        }

        rolePreviewAnimator.dismiss {
            isRolePreviewOpen = false
            resumeGameFlowAfterBlockingUi()
        }
    }

    private fun roleFunction(roleKey: String): String = when (roleKey) {
        "asesino" -> "Cada noche elegis una victima para eliminar. Ganas cuando los Traidores logran controlar la mesa."
        "mercenario" -> "Cada noche silencias a un jugador. Esa persona no podra hablar ni votar durante el dia siguiente."
        "policia" -> "Cada noche investigas a un jugador y recibis en privado una pista sobre su bando."
        "medico" -> "Cada noche proteges a un jugador. Si los Traidores lo atacan, evitas su eliminacion."
        "alcalde" -> "Podes revelar tu identidad durante el debate. Desde entonces tu voto vale doble y decidis ciertos empates."
        "payador" -> "Una vez por partida inicias un Contrapunto entre dos jugadores y agregas un voto al mas sospechoso."
        "desertor" -> "Elegis un bando al comenzar y ganas con ese equipo si sobrevivis. Mas adelante podes cambiarlo una sola vez."
        "espia" -> "Formas parte de los Traidores, pero cuando te investiga el Detective apareces como inocente."
        "bufon" -> "Tu objetivo es molestar, interrumpir y hacerte odiar para que el pueblo te expulse durante la votacion. Esa es tu unica condicion de victoria."
        "oraculo" -> "Una vez por partida podes invocar a cualquier jugador muerto para el debate del dia siguiente. Su rol permanece oculto: puede hablar, pero no votar ni usar habilidades."
        else -> "No tenes una habilidad especial. Debes debatir, detectar contradicciones y votar para eliminar a los Traidores."
    }

    private fun roleAdvice(roleKey: String): String = when (roleKey) {
        "asesino" -> "Actua con calma. Acusar demasiado o defender siempre a tus aliados puede delatarte."
        "mercenario" -> "Ganas con los Traidores. De noche silencia a quien pueda convencer al pueblo; de dia evita defender demasiado a tus compañeros."
        "policia" -> "No reveles todos tus resultados enseguida. Orienta al Pueblo sin exponerte demasiado pronto."
        "medico" -> "Cambia tus protecciones para que los Traidores no puedan anticiparte."
        "alcalde" -> "Guarda tu revelacion para un voto importante o un empate que realmente necesite tu peso."
        "payador" -> "Usa el Contrapunto cuando dos jugadores generen dudas y escucha bien sus respuestas."
        "desertor" -> "Observa la ventaja de cada bando antes de comprometerte y prioriza seguir con vida."
        "espia" -> "Podes parecer inocente ante una investigacion. Aprovechalo sin llamar demasiado la atencion."
        else -> "Pregunta, escucha y compara versiones. Vota usando solo lo que todos pudieron ver y escuchar."
    }

    private fun firstRoundRoleTip(): String? {
        if (session.round != 1 || session.winner.isNotBlank()) return null
        if (session.phase != GamePhase.REPARTO && !GameplayTableUi.isNightPhase(session.phase)) {
            return null
        }
        val roleKey = GameEngine.humanPlayer(session).role?.key ?: return null
        val advice = when (roleKey) {
            "asesino" -> "elegi una victima y evita llamar la atencion."
            "mercenario" -> "silencia a quien pueda liderar la discusion."
            "policia" -> "investiga a alguien que te parezca sospechoso."
            "medico" -> "protege a quien creas que pueden atacar."
            "alcalde" -> "escucha bien antes de usar el peso de tu voto."
            "payador" -> "usa el contrapunto cuando dos jugadores generen dudas."
            "desertor" -> "elegi un bando y trata de sobrevivir."
            "espia" -> "ayuda a los Traidores sin revelar tu bando."
            "oraculo" -> "elegi bien cuando y a quien devolverle la voz."
            else -> "pregunta, escucha y busca contradicciones."
        }
        return "Consejo: $advice"
    }

    private fun privateHintText(): String {
        val role = GameEngine.humanPlayer(session).role
        val rawHint = session.privateHint.ifBlank { GameEngine.privateRoleHint(session) }
        val rolePrefix = role?.let { "${it.name} - ${it.team}." }.orEmpty()
        val base = rawHint.removePrefix(rolePrefix).trim()
            .ifBlank { phaseText(session.phase).subtitle }
        val selection = if (selectedTarget.isBlank()) "" else " Objetivo: $selectedTarget."
        return "$base$selection"
    }

    private fun renderPersonalStatus() {
        val status = GameplayTableUi.personalStatus(session)
        currentPlayerStatus.visibility = if (status == null) View.GONE else View.VISIBLE
        currentPlayerStatus.text = status.orEmpty()
        val color = when (status) {
            "ELIMINADO" -> Color.parseColor("#A83232")
            "SILENCIADO" -> Color.parseColor("#9A6A32")
            "PROTEGIDO" -> Color.parseColor("#5A8A3C")
            "INVOCADO" -> Color.parseColor("#78C9E8")
            else -> getColor(R.color.accent_gold)
        }
        currentPlayerStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#D9221B15"))
            setStroke(dp(1), color)
            cornerRadius = dp(8).toFloat()
        }
        currentPlayerStatus.setTextColor(color)
    }

    private fun showActionFeedbackBanner(spec: GameplayFeedbackSpec) {
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        actionFeedbackBanner.animate().cancel()
        actionFeedbackBannerTitle.text = spec.title
        actionFeedbackBannerMessage.text = spec.message
        actionFeedbackBannerTone.setBackgroundColor(Color.parseColor(spec.tone.colorHex))
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        actionFeedbackBanner.visibility = View.VISIBLE
        actionFeedbackBanner.alpha = 0f
        actionFeedbackBanner.translationY = dp(8).toFloat()
        actionFeedbackBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L)
            .start()
        autoAdvanceHandler.postDelayed(
            feedbackBannerDismissRunnable,
            spec.durationMs.coerceAtLeast(INFORMATION_FEEDBACK_DURATION_MS)
        )
    }

    private fun hideActionFeedbackBanner() {
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        actionFeedbackBanner.animate()
            .alpha(0f)
            .translationY(dp(6).toFloat())
            .setDuration(180L)
            .withEndAction {
                actionFeedbackBanner.visibility = View.GONE
                actionFeedbackBanner.alpha = 1f
                actionFeedbackBanner.translationY = 0f
            }
            .start()
    }

    private fun showPendingPrivateFeedback() {
        val spec = feedbackState.privateToPresent() ?: return
        pauseCountdown()
        MusicManager.pauseForTransition()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        feedbackAnimator?.cancel()

        privateFeedbackTitle.text = spec.title
        privateFeedbackMessage.text = spec.message
        privateFeedbackTone.setBackgroundColor(Color.parseColor(spec.tone.colorHex))
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        privateFeedbackOverlay.alpha = 0f
        privateFeedbackPanel.alpha = 0f
        privateFeedbackPanel.scaleX = 0.94f
        privateFeedbackPanel.scaleY = 0.94f
        privateFeedbackOverlay.visibility = View.VISIBLE
        feedbackState.markPrivateVisible()

        feedbackAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(privateFeedbackOverlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_X, 0.94f, 1f),
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_Y, 0.94f, 1f)
            )
            duration = 220L
            interpolator = DecelerateInterpolator()
            start()
        }
        autoAdvanceHandler.postDelayed(
            feedbackDismissRunnable,
            spec.durationMs.coerceAtLeast(INFORMATION_FEEDBACK_DURATION_MS)
        )
    }

    private fun dismissCurrentFeedback() {
        if (!feedbackState.privateVisible) return
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        feedbackState.dismissPrivate()
        feedbackAnimator?.cancel()
        feedbackAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(privateFeedbackOverlay, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_X, 1f, 0.96f),
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_Y, 1f, 0.96f)
            )
            duration = 180L
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    feedbackAnimator = null
                    feedbackState.finishPrivateDismissal()
                    blockingFeedbackPeriod = null
                    privateFeedbackOverlay.visibility = View.GONE
                    privateFeedbackOverlay.alpha = 1f
                    privateFeedbackPanel.alpha = 1f
                    privateFeedbackPanel.scaleX = 1f
                    privateFeedbackPanel.scaleY = 1f
                    renderGame()
                }
            })
            start()
        }
    }

    private fun cancelFeedbackPresentation(keepPending: Boolean) {
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        feedbackAnimator?.removeAllListeners()
        feedbackAnimator?.cancel()
        feedbackAnimator = null
        actionFeedbackBanner.animate().cancel()
        actionFeedbackBanner.visibility = View.GONE
        actionFeedbackBanner.alpha = 1f
        actionFeedbackBanner.translationY = 0f
        privateFeedbackOverlay.visibility = View.GONE
        privateFeedbackOverlay.alpha = 1f
        privateFeedbackPanel.alpha = 1f
        privateFeedbackPanel.scaleX = 1f
        privateFeedbackPanel.scaleY = 1f
        feedbackState.cancel(keepPending)
        if (!keepPending) {
            blockingFeedbackPeriod = null
        }
    }

    private fun clearSelection() {
        selectedTarget = ""
    }

    private fun targetActionMessage(): String {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> "Selecciona una victima y confirma MATAR."
            GamePhase.NOCHE_MERCENARIO -> "Selecciona un jugador y confirma SILENCIAR."
            GamePhase.NOCHE_POLICIA -> "Selecciona un jugador y confirma INVESTIGAR."
            GamePhase.NOCHE_MEDICO -> "Selecciona un jugador y confirma SALVAR."
            GamePhase.NOCHE_ORACULO -> "Selecciona un jugador muerto para INVOCAR o guarda el poder."
            GamePhase.DIA_DEBATE -> "Podes usar tu habilidad o continuar a la votacion."
            GamePhase.CONTRAPUNTO -> "Selecciona un participante y confirma SENALAR."
            GamePhase.VOTACION -> "Selecciona un jugador y confirma VOTAR."
            GamePhase.ALCALDE_DESEMPATE -> "Selecciona un jugador empatado y confirma DECIDIR."
            else -> "Toca una carta valida."
        }
    }

    private fun resumeGameFlowAfterBlockingUi() {
        if (
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isOracleRevealVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isRolePreviewOpen ||
            feedbackState.privateVisible
        ) {
            return
        }
        if (feedbackState.pending?.blocksGameplay == true) {
            showPendingPrivateFeedback()
            return
        }
        if (maybeShowNextDeathReveal()) return
        if (maybeShowNextSilenceReveal()) return
        if (maybeShowOracleReveal()) return
        if (maybeShowJesterVictory()) return
        if (maybeShowWinnerReveal()) return
        if (maybeShowTraitorReveal()) return
        maybeShowDesertorChoice()
        if (!desertorDialogOpen) {
            scheduleAutoAdvanceIfNeeded()
        }
    }

    private fun maybeShowNextDeathReveal(): Boolean {
        if (isDeathRevealRunning) return true
        val player = pendingDeathReveals.pollFirst() ?: return false
        showDeathReveal(player)
        return true
    }

    private fun showDeathReveal(player: GamePlayer) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        MusicManager.pauseForTransition()
        isDeathRevealRunning = true
        deathRevealAnimator.start(player, session.revealRolesOnDeath)
    }

    private fun finishDeathReveal() {
        if (!isDeathRevealRunning) return
        isDeathRevealRunning = false
        if (pendingDeathReveals.isEmpty() && pendingSilenceReveals.isEmpty()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelDeathReveal(resumeMusic: Boolean) {
        if (!::deathRevealOverlay.isInitialized) return
        deathRevealAnimator.cancel()
        isDeathRevealRunning = false
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun maybeShowNextSilenceReveal(): Boolean {
        if (isSilenceRevealRunning) return true
        val player = pendingSilenceReveals.pollFirst() ?: return false
        showSilenceReveal(player)
        return true
    }

    private fun showSilenceReveal(player: GamePlayer) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        MusicManager.pauseForTransition()
        isSilenceRevealRunning = true
        silenceRevealAnimator.start(player)
    }

    private fun finishSilenceReveal() {
        if (!isSilenceRevealRunning) return
        isSilenceRevealRunning = false
        if (pendingSilenceReveals.isEmpty()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelSilenceReveal(resumeMusic: Boolean) {
        if (!::silenceRevealOverlay.isInitialized) return
        silenceRevealAnimator.cancel()
        isSilenceRevealRunning = false
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun maybeShowOracleReveal(): Boolean {
        if (isOracleRevealVisible) return true
        if (!session.oracleRevealPending || session.oracleInvitedPlayer.isBlank()) return false
        showOracleReveal()
        return true
    }

    private fun showOracleReveal() {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        MusicManager.pauseForTransition()
        isOracleRevealVisible = true
        oracleRevealPlayer.text = session.oracleInvitedPlayer.uppercase()
        oracleRevealOverlay.visibility = View.VISIBLE
        oracleRevealOverlay.alpha = 0f
        oracleRevealPanel.alpha = 0f
        oracleRevealPanel.scaleX = 0.86f
        oracleRevealPanel.scaleY = 0.86f
        oracleRevealOverlay.animate()
            .alpha(1f)
            .setDuration(260L)
            .start()
        oracleRevealPanel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(620L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dismissOracleReveal() {
        if (!isOracleRevealVisible) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        session = GameEngine.acknowledgeOracleReveal(session)
        isOracleRevealVisible = false
        oracleRevealOverlay.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                oracleRevealOverlay.visibility = View.GONE
                MusicManager.resumeGamePhaseAfterTransition(this, session)
                renderGame()
            }
            .start()
    }

    private fun hideOracleReveal() {
        if (!::oracleRevealOverlay.isInitialized) return
        oracleRevealOverlay.animate().cancel()
        oracleRevealPanel.animate().cancel()
        oracleRevealOverlay.visibility = View.GONE
        oracleRevealOverlay.alpha = 1f
        oracleRevealPanel.alpha = 1f
        oracleRevealPanel.scaleX = 1f
        oracleRevealPanel.scaleY = 1f
        isOracleRevealVisible = false
    }

    private fun maybeShowWinnerReveal(): Boolean {
        if (session.winner.isBlank()) return false
        if (isWinnerRevealVisible) return true
        showWinnerReveal(animate = !winnerRevealPresented)
        return true
    }

    private fun maybeShowJesterVictory(): Boolean {
        if (isJesterVictoryVisible) return true
        val victory = session.specialVictories.getOrNull(presentedSpecialVictoryCount)
            ?.takeIf { it.roleKey == RoleCatalog.BUFON }
            ?: return false
        showJesterVictory(victory)
        return true
    }

    private fun showJesterVictory(victory: GameSpecialVictory) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        eventLogHeightAnimator?.cancel()
        isJesterVictoryVisible = true
        presentedSpecialVictoryCount += 1
        jesterVictoryPlayer.text = "${victory.playerName.uppercase()} ERA EL BUFÓN"
        jesterVictoryMessage.text =
            "Consiguió que el pueblo lo expulsara durante la votación."
        val player = session.players.firstOrNull { it.name == victory.playerName }
        jesterVictoryImage.setImageResource(roleImageFor(player?.role))
        MusicManager.playVictoryMusic(this)
        jesterVictoryAnimator.show(JESTER_VICTORY_DURATION_MS)
    }

    private fun dismissJesterVictory() {
        if (!isJesterVictoryVisible || !btnContinueJesterVictory.isEnabled) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        jesterVictoryAnimator.hide()
        isJesterVictoryVisible = false
        MusicManager.stopVictoryMusic()
        renderGame()
    }

    private fun cancelJesterVictory(requeue: Boolean) {
        if (!::jesterVictoryAnimator.isInitialized || !isJesterVictoryVisible) return
        jesterVictoryAnimator.hide()
        isJesterVictoryVisible = false
        if (requeue) {
            presentedSpecialVictoryCount = (presentedSpecialVictoryCount - 1).coerceAtLeast(0)
        }
        MusicManager.stopVictoryMusic()
    }

    private fun showWinnerReveal(animate: Boolean) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        eventLogHeightAnimator?.cancel()

        val presentation = GameplayTableUi.winnerPresentation(session)
        winnerRevealTitle.text = when (session.winner) {
            GameRules.TOWN_WINNER -> "EL PUEBLO HA GANADO"
            GameRules.TRAITOR_WINNER -> "LOS TRAIDORES HAN GANADO"
            else -> "${session.winner.uppercase()} HA GANADO"
        }
        winnerRevealPersonalResult.text = if (presentation.humanWon) "VICTORIA" else "DERROTA"
        winnerRevealPersonalResult.setTextColor(
            Color.parseColor(if (presentation.humanWon) "#765019" else "#7C2F2A")
        )
        winnerRevealBackground.setImageResource(logDrawableFor(themeKey))
        val cardViews = winnerResultsRenderer.render(
            players = presentation.winningPlayers,
            summary = presentation.summary,
            specialVictories = presentation.specialVictories,
            themeKey = themeKey
        )
        winnerRevealScroll.scrollTo(0, 0)

        isWinnerRevealVisible = true
        winnerRevealPresented = true
        if (!animate) {
            winnerRevealAnimator.show(cardViews, animate = false) {}
            MusicManager.resumeVictoryMusic(this)
            return
        }

        MusicManager.playVictoryMusic(this)
        winnerRevealAnimator.show(cardViews, animate = true) {}
    }

    private fun applyGameplayTextScale() {
        val preference = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt("gameplay_text_size", 1)
            .coerceIn(0, 2)
        val requestedScale = when (preference) {
            0 -> 1f
            2 -> 1.24f
            else -> 1.12f
        }
        val relativeScale = requestedScale / appliedGameplayTextScale
        if (relativeScale != 1f) {
            scaleTextRecursively(gameplayRoot, relativeScale)
            appliedGameplayTextScale = requestedScale
        }
    }

    private fun scaleTextRecursively(view: View, scale: Float) {
        if (view is TextView) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * scale)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                scaleTextRecursively(view.getChildAt(index), scale)
            }
        }
    }

    private fun settleWinnerReveal() {
        if (!::winnerRevealOverlay.isInitialized || !isWinnerRevealVisible) return
        winnerRevealAnimator.settle()
    }

    private fun returnToLobby() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        MusicManager.stopVictoryMusic()
        finish()
    }

    private fun maybeShowTraitorReveal(): Boolean {
        if (isTraitorRevealRunning) return true
        if (!GameplayTableUi.shouldShowTraitorReveal(session, traitorRevealCompleted)) {
            if (session.phase == GamePhase.REPARTO) {
                traitorRevealCompleted = true
            }
            return false
        }

        val teammates = GameplayTableUi.traitorTeammatesForReveal(session)
        showTraitorReveal(teammates)
        return true
    }

    private fun showTraitorReveal(teammates: List<GamePlayer>) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = true
        traitorRevealCards.removeAllViews()

        val cardViews = teammates.map { teammate ->
            createTraitorRevealCard(teammate)
        }
        traitorRevealAnimator.show(
            cardViews = cardViews,
            durationMs = TRAITOR_REVEAL_DURATION_MS,
            onDismissRequested = ::dismissTraitorReveal
        )
    }

    private fun createTraitorRevealCard(player: GamePlayer): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.setPadding(dp(12), 0, dp(12), 0)

        val card = ImageView(this)
        card.setImageResource(roleImageFor(player.role))
        card.scaleType = ImageView.ScaleType.FIT_CENTER
        container.addView(card, LinearLayout.LayoutParams(dp(80), dp(100)))

        val playerName = TextView(this)
        playerName.text = player.name
        playerName.gravity = Gravity.CENTER
        playerName.maxLines = 1
        playerName.ellipsize = TextUtils.TruncateAt.END
        playerName.setTextColor(getColor(R.color.accent_gold))
        playerName.textSize = 13f
        playerName.setTypeface(null, Typeface.BOLD)
        val nameParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
        nameParams.topMargin = dp(5)
        container.addView(playerName, nameParams)

        val roleLabel = TextView(this)
        roleLabel.text = player.role?.name?.uppercase() ?: ""
        roleLabel.gravity = Gravity.CENTER
        roleLabel.maxLines = 1
        roleLabel.setTextColor(getColor(R.color.text_secondary))
        roleLabel.textSize = 10f
        container.addView(
            roleLabel,
            LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return container
    }

    private fun dismissTraitorReveal() {
        if (!isTraitorRevealRunning || isTraitorRevealDismissing) return
        isTraitorRevealDismissing = true
        traitorRevealCompleted = true
        traitorRevealAnimator.dismiss {
            isTraitorRevealDismissing = false
            isTraitorRevealRunning = false
            resumeGameFlowAfterBlockingUi()
        }
    }

    private fun cancelTraitorReveal() {
        if (!::traitorRevealOverlay.isInitialized) return
        traitorRevealAnimator.cancelAndHide()
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = false
    }

    private fun startDayNightTransition(spec: GameplayTransitionSpec) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        val fromPeriod = presentedPeriod ?: spec.period
        dayNightTransitionAnimator.start(spec, fromPeriod)
    }

    private fun finishDayNightTransition(spec: GameplayTransitionSpec) {
        if (!isDayNightTransitionRunning) return
        isDayNightTransitionRunning = false
        presentedPeriod = spec.period
        renderThemedBackground(spec.period)
        MusicManager.resumeGamePhaseAfterTransition(this, session)
        resumeGameFlowAfterBlockingUi()
    }

    private fun settleDayNightTransition(resumeMusic: Boolean) {
        if (!isDayNightTransitionRunning) return
        isDayNightTransitionRunning = false
        dayNightTransitionAnimator.cancel()

        val spec = GameplayTableUi.transitionSpec(session)
        lastPresentedTransitionKey = spec.key
        presentedPeriod = spec.period
        renderThemedBackground(spec.period)
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        } else {
            MusicManager.prepareGamePhaseWithoutPlayback(session)
        }
    }

    private fun roleImageFor(role: GameRole?): Int {
        if (role == null) return android.R.drawable.ic_menu_gallery
        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        return if (resId != 0) resId else android.R.drawable.ic_menu_gallery
    }

    private fun renderThemedBackground(period: GameplayPeriod) {
        mapBackground.setImageResource(
            backgroundDrawableFor(themeKey, period == GameplayPeriod.NIGHT)
        )
        eventLogBackground.setImageResource(logDrawableFor(themeKey))
    }

    private fun themeFromIntentOrSession(): String {
        val requestedTheme = intent.getStringExtra(EXTRA_TEMA)
        return when (requestedTheme) {
            "gaucho", "medieval", "griego" -> requestedTheme
            else -> GameplayTableUi.themeForMapKey(session.mapKey)
        }
    }

    private fun backgroundDrawableFor(theme: String, isNight: Boolean): Int {
        return when (theme) {
            "medieval" -> if (isNight) R.drawable.fondo_medieval_noche else R.drawable.fondo_medieval_dia
            "griego" -> if (isNight) R.drawable.fondo_griego_noche else R.drawable.fondo_griego_dia
            else -> if (isNight) R.drawable.fondo_gaucho_noche else R.drawable.fondo_gaucho_dia
        }
    }

    private fun logDrawableFor(theme: String): Int {
        return when (theme) {
            "medieval" -> R.drawable.log_medieval
            "griego" -> R.drawable.log_griego
            else -> R.drawable.log_gaucho
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(value: Int): Int {
        return (value / resources.displayMetrics.density).toInt()
    }

    private fun readSession(): GameSession? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION, GameSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession
        }
    }

    private fun maybeShowDesertorChoice() {
        if (isDayNightTransitionRunning || isRolePreviewOpen) return
        if (GameEngine.needsInitialDesertorChoice(session) || GameEngine.canDesertorReconsider(session)) {
            showDesertorTeamDialog()
        }
    }

    private fun showDesertorTeamDialog() {
        if (desertorDialogOpen || isFinishing) return
        pauseCountdown()
        desertorDialogOpen = true
        val isInitial = GameEngine.needsInitialDesertorChoice(session)
        val title = if (isInitial) "Elegi tu bando" else "Queres cambiar de bando?"
        val message = if (isInitial) {
            "Tu eleccion es secreta. Para ganar tenes que sobrevivir y lograr que venza tu bando."
        } else {
            "Esta es tu unica oportunidad de reconsiderarlo. Tambien podes mantener el mismo bando."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("PUEBLO") { _, _ ->
                desertorDialogOpen = false
                val changedTeam = session.desertorTeam.isNotBlank() &&
                    session.desertorTeam != GameRules.TOWN_WINNER
                session = GameEngine.chooseDesertorTeam(session, GameRules.TOWN_WINNER)
                renderGame()
                showActionFeedbackBanner(
                    GameplayTableUi.feedbackForDesertorChoice(
                        GameRules.TOWN_WINNER,
                        changedTeam
                    )
                )
            }
            .setNegativeButton("TRAIDORES") { _, _ ->
                desertorDialogOpen = false
                val changedTeam = session.desertorTeam.isNotBlank() &&
                    session.desertorTeam != GameRules.TRAITOR_WINNER
                session = GameEngine.chooseDesertorTeam(session, GameRules.TRAITOR_WINNER)
                renderGame()
                showActionFeedbackBanner(
                    GameplayTableUi.feedbackForDesertorChoice(
                        GameRules.TRAITOR_WINNER,
                        changedTeam
                    )
                )
            }
            .setCancelable(false)
            .show()
    }

    private data class PhaseText(
        val title: String,
        val subtitle: String,
        val actionLabel: String
    )

    companion object {
        private const val CHAT_PANEL_WIDTH_RATIO = 0.42f
        private const val CHAT_PANEL_COMPACT_WIDTH_RATIO = 0.52f
        private const val CHAT_PANEL_MIN_WIDTH_DP = 300
        private const val CHAT_PANEL_MAX_WIDTH_DP = 380
        private const val CHAT_PANEL_COMPACT_MIN_WIDTH_DP = 340
        private const val CHAT_PANEL_COMPACT_MAX_WIDTH_DP = 440
        private const val CHAT_PANEL_COMPACT_MARGIN_DP = 4
        private const val CHAT_PANEL_TOP_MARGIN_DP = 86
        private const val CHAT_PANEL_BOTTOM_MARGIN_DP = 96
        private const val CHAT_MESSAGE_MAX_LENGTH = 140
        private const val CHAT_MESSAGE_WARNING_LENGTH = 120
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val STATE_SESSION = "gameplay_session"
        private const val STATE_CHAT_OPEN = "chat_open"
        private const val STATE_EVENT_LOG_EXPANDED = "event_log_expanded"
        private const val STATE_ROLE_PREVIEW_OPEN = "role_preview_open"
        private const val STATE_SELECTED_TARGET = "selected_target"
        private const val STATE_COUNTDOWN_STAGE = "countdown_stage"
        private const val STATE_COUNTDOWN_PHASE_INDEX = "countdown_phase_index"
        private const val STATE_COUNTDOWN_REMAINING_MS = "countdown_remaining_ms"
        private const val STATE_COUNTDOWN_TOTAL_MS = "countdown_total_ms"
        private const val STATE_PENDING_FEEDBACK = "pending_feedback"
        private const val STATE_PRESENTED_PERIOD = "presented_period"
        private const val STATE_BLOCKING_FEEDBACK_PERIOD = "blocking_feedback_period"
        private const val STATE_TRAITOR_REVEAL_COMPLETED = "traitor_reveal_completed"
        private const val STATE_TRANSITION_KEY = "day_night_transition_key"
        private const val STATE_WINNER_REVEAL_PRESENTED = "winner_reveal_presented"
        private const val STATE_PRESENTED_SPECIAL_VICTORY_COUNT =
            "presented_special_victory_count"
        private const val TRAITOR_REVEAL_DURATION_MS = 8000L
        private const val JESTER_VICTORY_DURATION_MS = 5000L
        private const val COUNTDOWN_TICK_MS = 200L
        private const val INFORMATION_FEEDBACK_DURATION_MS = 10_000L

        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
    }
}
