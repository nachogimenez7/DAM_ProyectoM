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
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.TextViewCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
    private var chatKeyboardBottomInset = 0
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
    private var initialRoleReadingActive = false
    private var activePhaseAdvice: String? = null
    private var advicePhaseIndex = -1
    private var restoreRolePreviewOnResume = false
    private var restoreInitialRoleReadingOnResume = false
    private var isWinnerRevealVisible = false
    private var isJesterVictoryVisible = false
    private var isVoteResultVisible = false
    private var isTieVoteVisible = false
    private var restoreTieVoteAfterChat = false
    private var voteExpulsionComplete = false
    private var voteNoExpulsionPresented = false
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
    private var lastPresentedAssassinVoteLogKey: String? = null
    private var stagedBotBurstPhaseIndex = -1
    private val feedbackState = GameplayFeedbackState()
    private lateinit var session: GameSession
    private var unreadChatCount = 0
    private var onlinePartidaId = ""
    private var onlinePlayerId = ""
    private var onlineIsHost = false
    private var lastPublishedOnlineStateKey = ""
    private var lastPublishedAuthoritativeOnlineStateKey = ""
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val voteResultAutoContinueRunnable = Runnable { handleVoteResultAutoContinue() }
    private val feedbackDismissRunnable = Runnable { dismissCurrentFeedback() }
    private val feedbackBannerDismissRunnable = Runnable { hideActionFeedbackBanner() }
    private val pendingBotChatRunnables = mutableListOf<Runnable>()
    private val enableInitialRoleReadyRunnable = Runnable {
        if (initialRoleReadingActive && ::btnContinueRolePreview.isInitialized) {
            btnContinueRolePreview.visibility = View.VISIBLE
            btnContinueRolePreview.isEnabled = true
            btnContinueRolePreview.alpha = 1f
            btnContinueRolePreview.text = "EMPEZAR"
        }
    }
    private val clearPhaseAdviceRunnable = Runnable {
        if (activePhaseAdvice != null && ::phaseSubtitle.isInitialized) {
            activePhaseAdvice = null
            phaseSubtitle.text = currentNarratorMessage()
        }
    }
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
    private lateinit var voteResultAnimator: VoteResultAnimator

    private lateinit var actionFeedbackBanner: LinearLayout
    private lateinit var actionFeedbackBannerMessage: TextView
    private lateinit var actionFeedbackBannerTitle: TextView
    private lateinit var actionFeedbackBannerTone: View
    private lateinit var actionControls: LinearLayout
    private lateinit var btnAction: Button
    private lateinit var btnCloseRolePreview: ImageButton
    private lateinit var btnContinueRolePreview: Button
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
    private lateinit var eliminatedStatePanel: LinearLayout
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
    private lateinit var voteResultOverlay: FrameLayout
    private lateinit var voteResultPanel: LinearLayout
    private lateinit var voteResultCards: LinearLayout
    private lateinit var voteResultScroll: HorizontalScrollView
    private lateinit var voteResultTitle: TextView
    private lateinit var voteResultSubtitle: TextView
    private lateinit var voteResultNotice: TextView
    private lateinit var btnContinueVoteResult: Button
    private lateinit var voteKickBoot: ImageView
    private lateinit var tieVoteOverlay: FrameLayout
    private lateinit var tieVotePanel: LinearLayout
    private lateinit var tieVoteCards: GridLayout
    private lateinit var tieVoteCountdown: TextView
    private lateinit var tieVoteSubtitle: TextView
    private lateinit var tieVoteNotice: TextView
    private lateinit var btnTieVoteChat: Button
    private lateinit var btnTieRevealMayor: Button
    private lateinit var btnConfirmTieVote: Button
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
        onlinePartidaId = savedInstanceState?.getString(STATE_ONLINE_PARTIDA_ID)
            ?: intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
        onlinePlayerId = savedInstanceState?.getString(STATE_ONLINE_PLAYER_ID)
            ?: intent.getStringExtra(EXTRA_ONLINE_PLAYER_ID).orEmpty()
        onlineIsHost = savedInstanceState?.getBoolean(STATE_ONLINE_IS_HOST)
            ?: intent.getBooleanExtra(EXTRA_ONLINE_IS_HOST, false)
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
        val shouldRestoreInitialRoleReading = savedInstanceState
            ?.getBoolean(STATE_INITIAL_ROLE_READING)
            ?.takeIf { session.phase == GamePhase.REPARTO }
            ?: false
        val shouldPresentRolePreview = shouldShowInitialRoleReveal || shouldRestoreRolePreview
        lastPresentedTransitionKey = savedInstanceState?.getString(STATE_TRANSITION_KEY)
        if (shouldShowInitialRoleReveal) {
            lastPresentedTransitionKey = GameplayTableUi.transitionSpec(session).key
        }
        presentedPeriod = savedInstanceState
            ?.getString(STATE_PRESENTED_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        if (shouldShowInitialRoleReveal && presentedPeriod == null) {
            presentedPeriod = GameplayPeriod.DAY
        }
        blockingFeedbackPeriod = savedInstanceState
            ?.getString(STATE_BLOCKING_FEEDBACK_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        traitorRevealCompleted = savedInstanceState?.getBoolean(STATE_TRAITOR_REVEAL_COMPLETED) ?: false
        winnerRevealPresented = savedInstanceState?.getBoolean(STATE_WINNER_REVEAL_PRESENTED) ?: false
        isChatOpen = savedInstanceState?.getBoolean(STATE_CHAT_OPEN) ?: false
        isEventLogExpanded = false
        voteNoExpulsionPresented =
            savedInstanceState?.getBoolean(STATE_VOTE_NO_EXPULSION_PRESENTED) ?: false
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
        actionControls = findViewById(R.id.actionControls)
        btnAction = findViewById(R.id.btnVote)
        btnCloseRolePreview = findViewById(R.id.btnCloseRolePreview)
        btnContinueRolePreview = findViewById(R.id.btnContinueRolePreview)
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
        eliminatedStatePanel = findViewById(R.id.eliminatedStatePanel)
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
        voteResultOverlay = findViewById(R.id.voteResultOverlay)
        voteResultPanel = findViewById(R.id.voteResultPanel)
        voteResultCards = findViewById(R.id.voteResultCards)
        voteResultScroll = findViewById(R.id.voteResultScroll)
        voteResultTitle = findViewById(R.id.voteResultTitle)
        voteResultSubtitle = findViewById(R.id.voteResultSubtitle)
        voteResultNotice = findViewById(R.id.voteResultNotice)
        btnContinueVoteResult = findViewById(R.id.btnContinueVoteResult)
        voteKickBoot = findViewById(R.id.voteKickBoot)
        voteResultAnimator = VoteResultAnimator(
            context = this,
            handler = autoAdvanceHandler,
            overlay = voteResultOverlay,
            panel = voteResultPanel,
            cards = voteResultCards,
            scroll = voteResultScroll,
            title = voteResultTitle,
            subtitle = voteResultSubtitle,
            notice = voteResultNotice,
            continueButton = btnContinueVoteResult,
            boot = voteKickBoot,
            roleImageFor = ::roleImageFor,
            dp = ::dp,
            onContinueReady = ::scheduleVoteResultAutoContinue
        )
        btnContinueVoteResult.setOnClickListener { handleVoteResultContinue() }
        tieVoteOverlay = findViewById(R.id.tieVoteOverlay)
        tieVotePanel = findViewById(R.id.tieVotePanel)
        tieVoteCards = findViewById(R.id.tieVoteCards)
        tieVoteCountdown = findViewById(R.id.tieVoteCountdown)
        tieVoteSubtitle = findViewById(R.id.tieVoteSubtitle)
        tieVoteNotice = findViewById(R.id.tieVoteNotice)
        btnTieVoteChat = findViewById(R.id.btnTieVoteChat)
        btnTieRevealMayor = findViewById(R.id.btnTieRevealMayor)
        btnConfirmTieVote = findViewById(R.id.btnConfirmTieVote)
        btnTieVoteChat.setOnClickListener { openChatFromTieVote() }
        btnTieRevealMayor.setOnClickListener { revealMayorFromTieVote() }
        btnConfirmTieVote.setOnClickListener { confirmTieVoteSelection() }
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
                setChatKeyboardState(true, chatKeyboardBottomInset)
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
        btnCloseRolePreview.setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.PANEL)
            closeRolePreview()
        }
        btnContinueRolePreview.setOnClickListener {
            GameplayEffects.play(this, GameplayEffect.CONFIRM)
            closeRolePreview()
        }
        traitorRevealOverlay.setOnClickListener { dismissTraitorReveal() }
        jesterVictoryOverlay.setOnClickListener { }
        btnContinueJesterVictory.setOnClickListener { dismissJesterVictory() }
        findViewById<Button>(R.id.btnWinnerReturnLobby).setOnClickListener { returnToLobby() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleGameplayBack()
            }
        })

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
                showRolePreview(
                    initialReveal = shouldShowInitialRoleReveal || shouldRestoreInitialRoleReading
                )
            }
        }
    }

    override fun onDestroy() {
        autoAdvanceHandler.removeCallbacks(enableInitialRoleReadyRunnable)
        autoAdvanceHandler.removeCallbacks(clearPhaseAdviceRunnable)
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = false)
        cancelVoteResult()
        hideTieVoteWindow(clearSelection = false)
        settleWinnerReveal()
        cancelActionPulse()
        cancelFeedbackPresentation(keepPending = false)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        cancelPendingBotChat()
        if (isFinishing) {
            MusicManager.stopVictoryMusic()
        } else {
            MusicManager.pauseVictoryMusic()
        }
        super.onDestroy()
    }

    override fun onPause() {
        restoreRolePreviewOnResume = isRolePreviewOpen
        restoreInitialRoleReadingOnResume = initialRoleReadingActive
        pauseCountdown()
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = true)
        cancelVoteResult()
        hideTieVoteWindow(clearSelection = false)
        settleWinnerReveal()
        cancelActionPulse()
        cancelFeedbackPresentation(keepPending = true)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        cancelPendingBotChat()
        MusicManager.pauseVictoryMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameplayRoot.isInitialized) {
            applyGameplayTextScale()
        }
        if (::session.isInitialized && restoreRolePreviewOnResume) {
            val restoreInitialReading = restoreInitialRoleReadingOnResume
            restoreRolePreviewOnResume = false
            restoreInitialRoleReadingOnResume = false
            gameplayRoot.post { showRolePreview(initialReveal = restoreInitialReading) }
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
            voteNoExpulsionPresented &&
            !isVoteResultVisible &&
            session.phase == GamePhase.RESULTADO &&
            session.dayEliminationTarget.isBlank()
        ) {
            dismissActionFeedbackBannerNow()
            isVoteResultVisible = true
            voteResultAnimator.show(session)
            voteResultAnimator.showNoExpulsion()
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
        outState.putString(STATE_ONLINE_PARTIDA_ID, onlinePartidaId)
        outState.putString(STATE_ONLINE_PLAYER_ID, onlinePlayerId)
        outState.putBoolean(STATE_ONLINE_IS_HOST, onlineIsHost)
        outState.putBoolean(
            STATE_VOTE_NO_EXPULSION_PRESENTED,
            voteNoExpulsionPresented
        )
        outState.putBoolean(
            STATE_ROLE_PREVIEW_OPEN,
            isRolePreviewOpen || restoreRolePreviewOnResume
        )
        outState.putBoolean(
            STATE_INITIAL_ROLE_READING,
            initialRoleReadingActive || restoreInitialRoleReadingOnResume
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

    private fun handleGameplayBack() {
        if (
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isOracleRevealVisible ||
            isVoteResultVisible ||
            isTieVoteVisible ||
            isJesterVictoryVisible ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible
        ) {
            return
        }
        when {
            isWinnerRevealVisible -> returnToLobby()
            isRolePreviewOpen -> closeRolePreview()
            isChatOpen -> closeChatPanel()
            actionFeedbackBanner.visibility == View.VISIBLE -> hideActionFeedbackBanner()
            isEventLogExpanded -> toggleEventLog()
            else -> finish()
        }
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
            (session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE) &&
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

        if (mustWaitForPhaseTimer()) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(this, "La fase avanza sola cuando termine el tiempo.", Toast.LENGTH_SHORT).show()
            renderGame()
            return
        }

        advanceCurrentPhase()
    }

    private fun advanceCurrentPhase() {
        cancelPendingBotChat()
        val before = session
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
            GamePhase.RECUENTO_VOTOS -> session
            GamePhase.DESEMPATE_VOTACION -> GameEngine.resolveTieVoting(session, "")
            GamePhase.ALCALDE_DESEMPATE -> session
            GamePhase.RESULTADO -> GameEngine.resolveResult(session)
        }
        recordOnlinePhaseAdvance(before, session)
        stageBotBurstForCurrentPhase()
        clearSelection()
        renderGame()
    }

    private fun performTargetAction(targetName: String) {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        cancelPendingBotChat()
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
        recordOnlinePlayerAction(before, resolved, targetName)
        playResolvedActionSound(before, resolved)
        val feedback = GameplayTableUi.feedbackForResolvedAction(before, resolved, targetName)
        session = resolved
        stageBotBurstForCurrentPhase()
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
        val narratorMessage = currentNarratorMessage(phaseText)
        refreshPhaseAdvice(narratorMessage)
        val eventChanged =
            lastRenderedPhase != session.phase || lastRenderedAnnouncement != narratorMessage
        updateUnreadChatCount()
        val visiblePeriod = when {
            blockingFeedbackPending -> blockingFeedbackPeriod ?: GameplayPeriod.NIGHT
            specialVictoryPending -> presentedPeriod ?: GameplayPeriod.DAY
            isDayNightTransitionRunning -> presentedPeriod ?: transitionSpec.period
            else -> transitionSpec.period
        }
        renderThemedBackground(visiblePeriod)
        renderNarrator(phaseText, activePhaseAdvice ?: narratorMessage, eventChanged)
        maybeExpandPrivateAssassinVoteLog()
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
        lastRenderedAnnouncement = narratorMessage
        publishOnlineClientState()
        publishAuthoritativeOnlineState()
        if (blockingFeedbackPending) {
            showPendingPrivateFeedback()
        } else if (shouldStartTransition) {
            startDayNightTransition(transitionSpec)
        } else if (!isDayNightTransitionRunning) {
            resumeGameFlowAfterBlockingUi()
        }
    }

    private fun publishOnlineClientState() {
        if (onlinePartidaId.isBlank() || onlinePlayerId.isBlank()) return
        val stateKey = listOf(
            session.phase.name,
            session.round,
            session.phaseIndex,
            session.publicAnnouncement,
            session.winner
        ).joinToString("|")
        if (stateKey == lastPublishedOnlineStateKey) return
        lastPublishedOnlineStateKey = stateKey

        val human = GameEngine.humanPlayer(session)
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .update(
                mapOf(
                    "estadoClientes.$onlinePlayerId" to mapOf(
                        "fase" to session.phase.name,
                        "ronda" to session.round,
                        "phaseIndex" to session.phaseIndex,
                        "jugador" to human.name,
                        "rolKey" to human.role?.key.orEmpty(),
                        "anuncioPublico" to session.publicAnnouncement,
                        "ganador" to session.winner,
                        "actualizadaEnLocal" to System.currentTimeMillis()
                    ),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
    }

    private fun publishAuthoritativeOnlineState() {
        if (onlinePartidaId.isBlank() || onlinePlayerId.isBlank() || !onlineIsHost) return
        val stateKey = listOf(
            session.phase.name,
            session.round,
            session.phaseIndex,
            session.publicAnnouncement,
            session.privateHint,
            session.winner,
            session.nightKillTarget,
            session.nightSilenceTarget,
            session.dayEliminationTarget
        ).joinToString("|")
        if (stateKey == lastPublishedAuthoritativeOnlineStateKey) return
        lastPublishedAuthoritativeOnlineStateKey = stateKey

        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .update(
                mapOf(
                    "estadoPartida" to mapOf(
                        "fase" to session.phase.name,
                        "ronda" to session.round,
                        "phaseIndex" to session.phaseIndex,
                        "anuncioPublico" to session.publicAnnouncement,
                        "ganador" to session.winner,
                        "victimaNoche" to session.nightKillTarget,
                        "silenciado" to session.nightSilenceTarget,
                        "expulsadoDia" to session.dayEliminationTarget,
                        "actualizadaEnLocal" to System.currentTimeMillis(),
                        "actualizadaPor" to onlinePlayerId
                    ),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
    }

    private fun recordOnlinePhaseAdvance(before: GameSession, after: GameSession) {
        if (!isOnlineGameplay()) return
        if (before.phase == after.phase && before.phaseIndex == after.phaseIndex) return
        recordOnlineAction(
            type = "fase_avanzada",
            targetName = "",
            details = mapOf(
                "faseAnterior" to before.phase.name,
                "faseNueva" to after.phase.name,
                "phaseIndexAnterior" to before.phaseIndex,
                "phaseIndexNuevo" to after.phaseIndex,
                "anuncioPublico" to after.publicAnnouncement
            )
        )
    }

    private fun recordOnlinePlayerAction(before: GameSession, after: GameSession, targetName: String) {
        if (!isOnlineGameplay()) return
        val human = GameEngine.humanPlayer(before)
        val actionType = when (before.phase) {
            GamePhase.NOCHE_ASESINO -> "matar"
            GamePhase.NOCHE_MERCENARIO -> "silenciar"
            GamePhase.NOCHE_POLICIA -> "investigar"
            GamePhase.NOCHE_MEDICO -> "salvar"
            GamePhase.NOCHE_ORACULO -> "invitar_muerto"
            GamePhase.DIA_DEBATE -> "contrapunto"
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> "votar"
            else -> "accion"
        }
        recordOnlineAction(
            type = "accion_jugador",
            targetName = targetName,
            details = mapOf(
                "accion" to actionType,
                "rolActor" to human.role?.key.orEmpty(),
                "faseResultado" to after.phase.name,
                "phaseIndexResultado" to after.phaseIndex
            )
        )
    }

    private fun recordOnlineAction(
        type: String,
        targetName: String,
        details: Map<String, Any?>
    ) {
        if (!isOnlineGameplay()) return
        val human = GameEngine.humanPlayer(session)
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("acciones")
            .add(
                mapOf(
                    "tipo" to type,
                    "actorId" to onlinePlayerId,
                    "actorNombre" to human.name,
                    "actorEsHost" to onlineIsHost,
                    "objetivoNombre" to targetName,
                    "fase" to session.phase.name,
                    "ronda" to session.round,
                    "phaseIndex" to session.phaseIndex,
                    "modoCliente" to "android",
                    "detalles" to details,
                    "creadaEn" to FieldValue.serverTimestamp(),
                    "creadaEnLocal" to System.currentTimeMillis()
                )
            )
    }

    private fun isOnlineGameplay(): Boolean {
        return onlinePartidaId.isNotBlank() && onlinePlayerId.isNotBlank()
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
        val allEvents = GameplayTableUi.publicEvents(
            session.publicHistory,
            publicMessage,
            phaseText.subtitle
        ) + privateAssassinVoteEvents()
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

    private fun maybeExpandPrivateAssassinVoteLog() {
        val key = privateAssassinVoteLogKey() ?: return
        if (key == lastPresentedAssassinVoteLogKey) return

        lastPresentedAssassinVoteLogKey = key
        if (!isEventLogExpanded) {
            isEventLogExpanded = true
            if (isChatOpen) {
                closeChatPanel()
            }
            lastRenderedEventExpanded = null
        }
    }

    private fun privateAssassinVoteEvents(): List<String> {
        if (privateAssassinVoteLogKey() == null) return emptyList()

        val voteLines = session.assassinVotes.entries
            .sortedBy { it.key }
            .map { "${it.key} voto a ${it.value}." }
        val targetLine = session.nightKillTarget
            .takeIf { it.isNotBlank() }
            ?.let { "Victima elegida por los asesinos: $it." }
        return voteLines + listOfNotNull(targetLine)
    }

    private fun privateAssassinVoteLogKey(): String? {
        if (session.assassinVotes.isEmpty()) return null
        val humanRoleKey = GameEngine.humanPlayer(session).role?.key
        if (humanRoleKey !in GameRules.traitorRoleKeys) return null

        val votesKey = session.assassinVotes.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}>${it.value}" }
        return "${session.round}:${session.phaseIndex}:${session.nightKillTarget}:$votesKey"
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
        val eventLogActionLabel = if (isEventLogExpanded) {
            "Ocultar eventos"
        } else {
            "Expandir eventos"
        }
        btnToggleEventLog.contentDescription = eventLogActionLabel
        eventLogHeader.contentDescription = eventLogActionLabel
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
            ((session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE) &&
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
            (session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE) &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed -> "REVELARME"
            mandatoryTargetSelection -> "ELEGIR OBJETIVO"
            session.phase == GamePhase.REPARTO -> "NOCHE"
            mustWaitForPhaseTimer() -> "ESPERAR"
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
            !mustWaitForPhaseTimer() &&
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
                "El Oraculo puede llamar a una voz que abandono el mundo de los vivos.",
                if (GameEngine.isHumanRoleTurn(session, RoleCatalog.ORACULO)) {
                    "GUARDAR PODER"
                } else {
                    "ACELERAR"
                }
            )
            GamePhase.AMANECER -> PhaseText("AMANECER", "El pueblo despierta y escucha lo ocurrido.", "AMANECER")
            GamePhase.DIA_DEBATE -> PhaseText("DIA ${session.round}", "El pueblo debate antes de votar.", "VOTAR")
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
            GamePhase.RECUENTO_VOTOS -> PhaseText(
                "RECUENTO",
                "El pueblo cuenta los votos.",
                "CONTINUAR"
            )
            GamePhase.DESEMPATE_VOTACION -> PhaseText(
                "DESEMPATE",
                "Vota solamente entre los jugadores empatados.",
                "VOTAR"
            )
            GamePhase.ALCALDE_DESEMPATE -> PhaseText(
                "DESEMPATE",
                "El Alcalde decide entre los jugadores empatados.",
                "DECIDIR"
            )
            GamePhase.RESULTADO -> PhaseText(
                "RESULTADO",
                "El pueblo conoce el resultado.",
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
        setChatKeyboardState(false, 0)
        renderChatPanelVisibility(animate = true)
        renderChatBadge()
        renderNewChatMessageNotice()
        if (
            restoreTieVoteAfterChat &&
            session.phase == GamePhase.DESEMPATE_VOTACION
        ) {
            restoreTieVoteAfterChat = false
            gameplayRoot.post { showTieVoteWindow() }
        }
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
        updateChatToggleContentDescription()
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
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            setChatKeyboardState(imeVisible, imeBottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(gameplayRoot)
    }

    private fun setChatKeyboardState(compact: Boolean, bottomInset: Int) {
        if (
            isChatKeyboardCompact == compact &&
            chatKeyboardBottomInset == bottomInset &&
            chatPanel.isLaidOut
        ) {
            return
        }
        isChatKeyboardCompact = compact
        chatKeyboardBottomInset = bottomInset
        applyChatPanelDimensions()
        if (compact) {
            chatPanel.bringToFront()
            chatPanel.visibility = View.VISIBLE
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun applyChatPanelDimensions() {
        if (!::chatPanel.isInitialized || gameplayRoot.width == 0) return
        val containerWidth = centerColumn.width.takeIf { it > 0 } ?: gameplayRoot.width
        val params = chatPanel.layoutParams as FrameLayout.LayoutParams
        val widthRatio = if (isChatKeyboardCompact) {
            CHAT_PANEL_COMPACT_WIDTH_RATIO
        } else {
            CHAT_PANEL_WIDTH_RATIO
        }
        params.width = (containerWidth * widthRatio)
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
            dp(if (isChatKeyboardCompact) 7 else 11),
            dp(if (isChatKeyboardCompact) 4 else 11),
            dp(if (isChatKeyboardCompact) 7 else 11),
            dp(if (isChatKeyboardCompact) 5 else 11)
        )
        chatHeader.layoutParams = chatHeader.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 24 else 34)
        }
        chatComposer.layoutParams = chatComposer.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 42)
        }
        chatStatusRow.layoutParams = chatStatusRow.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 18 else 22)
        }
        chatInput.layoutParams = chatInput.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 42)
        }
        btnSendChat.layoutParams = btnSendChat.layoutParams.apply {
            height = dp(if (isChatKeyboardCompact) 34 else 42)
        }
    }

    private fun renderChatMessages(messages: List<GameChatMessage>) {
        chatMessagesContainer.removeAllViews()
        if (messages.isEmpty()) {
            chatMessagesContainer.addView(TextView(this).apply {
                text = "Todavia no hay mensajes."
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f * appliedGameplayTextScale
            })
            return
        }

        val humanName = GameEngine.humanPlayer(session).name
        val bubbleMaxWidth = ((chatPanel.width.takeIf { it > 0 } ?: dp(320)) - dp(56))
            .coerceIn(dp(190), dp(300))
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
                textSize = 9f * appliedGameplayTextScale
                typeface = Typeface.DEFAULT_BOLD
            })
            bubble.addView(TextView(this).apply {
                text = message.message
                maxWidth = bubbleMaxWidth
                setTextColor(
                    getColor(if (ownMessage) R.color.bg_dark else R.color.text_primary)
                )
                textSize = 12f * appliedGameplayTextScale
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
        chatNewMessages.contentDescription = if (newChatMessagesWhileTyping > 0) {
            "Ver $newChatMessagesWhileTyping mensajes nuevos"
        } else {
            "Sin mensajes nuevos"
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
        updateChatToggleContentDescription()
    }

    private fun updateChatToggleContentDescription() {
        btnToggleChat.contentDescription = when {
            isChatOpen -> "Cerrar chat"
            unreadChatCount > 0 -> {
                val suffix = if (unreadChatCount == 1) "mensaje nuevo" else "mensajes nuevos"
                "Abrir chat, $unreadChatCount $suffix"
            }
            else -> "Abrir chat"
        }
    }

    private fun sendHumanChatMessage() {
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            Toast.makeText(this, "El chat se habilita al comenzar la fase.", Toast.LENGTH_SHORT).show()
            return
        }
        val rawMessage = chatInput.text.toString()
        val before = session.chatHistory.size
        session = GameEngine.addHumanChatMessage(
            session,
            rawMessage,
            includeBotReactions = false
        )
        if (session.chatHistory.size > before) {
            GameplayEffects.play(this, GameplayEffect.CHAT)
            scheduleBotChatReactions(rawMessage)
            clearChatComposerAfterSend()
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        } else if (!GameEngine.canHumanChat(session)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            val human = GameEngine.humanPlayer(session)
            val message = when {
                !human.alive -> "Estas eliminado. Podes mirar el chat, no escribir."
                human.muted -> "Estas muteado. Podes mirar el chat, no escribir."
                GameplayTableUi.isNightPhase(session.phase) ->
                    "El pueblo duerme. Las voces deben esperar al amanecer."
                else -> "No podes escribir durante esta fase."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        updateUnreadChatCount()
        renderChatPanel()
        renderChatBadge()
    }

    private fun scheduleBotChatReactions(rawHumanMessage: String) {
        cancelPendingBotChat()
        val humanMessage = rawHumanMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        if (humanMessage.isBlank() || LocalBotAi.isDebugVoteCommand(session, humanMessage)) return
        val phaseIndex = session.phaseIndex
        val phase = session.phase
        val reactions = try {
            LocalBotAi.reactionsToHumanMessage(session, humanMessage)
        } catch (_: RuntimeException) {
            emptyList()
        }
        reactions.take(MAX_STAGGERED_BOT_REACTIONS).forEachIndexed { index, (speaker, message) ->
            scheduleBotChatMessage(
                speaker = speaker,
                message = message,
                phaseIndex = phaseIndex,
                phase = phase,
                delayMs = botReactionDelayMs(index, message)
            )
        }
    }

    private fun stageBotBurstForCurrentPhase() {
        if (!::session.isInitialized || stagedBotBurstPhaseIndex == session.phaseIndex) return
        if (
            session.phase != GamePhase.DIA_DEBATE &&
            session.phase != GamePhase.CONTRAPUNTO &&
            session.phase != GamePhase.VOTACION &&
            session.phase != GamePhase.DESEMPATE_VOTACION
        ) {
            return
        }
        val humanName = GameEngine.humanPlayer(session).name
        val trailingBotMessages = session.chatHistory
            .asReversed()
            .takeWhile { !it.isGod && it.speaker != humanName }
            .asReversed()
        if (trailingBotMessages.size <= 1) return

        val visibleNow = trailingBotMessages.first()
        val staged = trailingBotMessages.drop(1)
        session = session.copy(
            chatHistory = session.chatHistory.dropLast(trailingBotMessages.size) + visibleNow
        )
        stagedBotBurstPhaseIndex = session.phaseIndex
        staged.forEachIndexed { index, message ->
            scheduleBotChatMessage(
                speaker = message.speaker,
                message = message.message,
                phaseIndex = session.phaseIndex,
                phase = session.phase,
                delayMs = PHASE_BOT_BURST_DELAY_MS + index * NEXT_BOT_REACTION_DELAY_MS
            )
        }
    }

    private fun scheduleBotChatMessage(
        speaker: String,
        message: String,
        phaseIndex: Int,
        phase: GamePhase,
        delayMs: Long
    ) {
        val runnable = object : Runnable {
            override fun run() {
                pendingBotChatRunnables.remove(this)
                if (
                    !::session.isInitialized ||
                    session.phaseIndex != phaseIndex ||
                    session.phase != phase ||
                    session.winner.isNotBlank()
                ) {
                    return
                }
                val beforeCount = session.chatHistory.size
                session = GameEngine.addBotChatMessage(session, speaker, message)
                if (session.chatHistory.size == beforeCount) return
                GameplayEffects.play(this@GameplayMockActivity, GameplayEffect.CHAT)
                updateUnreadChatCount()
                renderChatPanel()
                renderChatBadge()
            }
        }
        pendingBotChatRunnables += runnable
        autoAdvanceHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingBotChat() {
        pendingBotChatRunnables.forEach(autoAdvanceHandler::removeCallbacks)
        pendingBotChatRunnables.clear()
    }

    private fun botReactionDelayMs(index: Int, message: String): Long {
        val readingDelay = (message.length * 12L).coerceAtMost(420L)
        return FIRST_BOT_REACTION_DELAY_MS + index * NEXT_BOT_REACTION_DELAY_MS + readingDelay
    }

    private fun clearChatComposerAfterSend() {
        chatInput.text.clear()
        chatInput.setText("")
        chatInput.setSelection(0)
        chatInput.post {
            if (::chatInput.isInitialized) {
                chatInput.text.clear()
                chatInput.setText("")
                chatInput.setSelection(0)
                renderChatCharacterCount(0)
            }
        }
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
            GamePhase.NOCHE_MEDICO -> "El pueblo duerme..."
            GamePhase.NOCHE_ORACULO -> "El pueblo duerme..."
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
            isVoteResultVisible ||
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
            transitionDurationMs = 0L
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
        if (::tieVoteCountdown.isInitialized && session.phase == GamePhase.DESEMPATE_VOTACION) {
            tieVoteCountdown.text = seconds.coerceAtLeast(0).toString()
        }
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

        val expiredPhase = session.phase
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
            GamePhase.RECUENTO_VOTOS -> session
            GamePhase.DESEMPATE_VOTACION -> {
                if (GameEngine.requiresHumanInput(session)) {
                    GameEngine.resolveHumanTimeout(session)
                } else {
                    GameEngine.resolveTieVoting(session, "")
                }
            }
            GamePhase.ALCALDE_DESEMPATE -> GameEngine.resolveAlcaldeTieTimeout(session)
            GamePhase.REPARTO -> GameEngine.startNight(session)
            GamePhase.AMANECER -> GameEngine.resolveDawn(session)
            GamePhase.RESULTADO -> GameEngine.resolveResult(session)
        }
        if (expiredPhase == GamePhase.DESEMPATE_VOTACION) {
            hideTieVoteWindow(clearSelection = true)
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
            GamePhase.NOCHE_ORACULO -> {
                val skipBotOnlyNight = session.quickTestMode && !GameEngine.requiresHumanInput(session)
                timing.nightSeconds.takeUnless { skipBotOnlyNight }
            }
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO -> timing.discussionSeconds
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> timing.votingSeconds
            GamePhase.DESEMPATE_VOTACION -> (timing.votingSeconds / 2).coerceAtLeast(10)
            GamePhase.REPARTO,
            GamePhase.AMANECER,
            GamePhase.RECUENTO_VOTOS,
            GamePhase.RESULTADO -> null
        }
    }

    private fun visualCountdownTotalMs(): Long {
        val transitionMs = 0L
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
            isVoteResultVisible ||
            isTieVoteVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible ||
            feedbackState.pending?.blocksGameplay == true ||
            desertorDialogOpen
        ) {
            return
        }
        dismissActionFeedbackBannerNow()
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
        rolePreviewAdvice.text = RoleCatalog.advice(role.key)
        rolePreviewScroll.scrollTo(0, 0)
        initialRoleReadingActive = initialReveal
        autoAdvanceHandler.removeCallbacks(enableInitialRoleReadyRunnable)
        if (initialReveal) {
            btnCloseRolePreview.visibility = View.GONE
            btnContinueRolePreview.visibility = View.GONE
            btnContinueRolePreview.isEnabled = false
            btnContinueRolePreview.alpha = 1f
            btnContinueRolePreview.text = "EMPEZAR"
            val readingDelayMs = initialRoleReadingDelayMs()
            if (readingDelayMs == 0L) {
                enableInitialRoleReadyRunnable.run()
            } else {
                autoAdvanceHandler.postDelayed(
                    enableInitialRoleReadyRunnable,
                    readingDelayMs
                )
            }
        } else {
            btnCloseRolePreview.visibility = View.VISIBLE
            btnContinueRolePreview.visibility = View.VISIBLE
            btnContinueRolePreview.isEnabled = true
            btnContinueRolePreview.alpha = 1f
            btnContinueRolePreview.text = "CERRAR"
        }
        isRolePreviewOpen = true
        GameplayEffects.play(this, GameplayEffect.REVEAL)
        rolePreviewAnimator.show(initialReveal)
    }

    private fun closeRolePreview(resumeGameFlow: Boolean = true) {
        if (!::rolePreviewOverlay.isInitialized) return
        if (
            resumeGameFlow &&
            initialRoleReadingActive &&
            !btnContinueRolePreview.isEnabled
        ) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            Toast.makeText(
                this,
                "Toma unos segundos para leer tu rol.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        autoAdvanceHandler.removeCallbacks(enableInitialRoleReadyRunnable)
        initialRoleReadingActive = false
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
        "asesino" -> "Cada noche elegis una victima para eliminar. Ganas cuando los Traidores logran controlar el pueblo."
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

    private fun refreshPhaseAdvice(publicMessage: String) {
        if (session.phase == GamePhase.REPARTO || session.winner.isNotBlank()) {
            activePhaseAdvice = null
            advicePhaseIndex = session.phaseIndex
            autoAdvanceHandler.removeCallbacks(clearPhaseAdviceRunnable)
            return
        }
        if (advicePhaseIndex == session.phaseIndex) return

        advicePhaseIndex = session.phaseIndex
        autoAdvanceHandler.removeCallbacks(clearPhaseAdviceRunnable)
        activePhaseAdvice = phaseAdvice()?.let { "Consejo: $it" }
        if (activePhaseAdvice != null && activePhaseAdvice != publicMessage) {
            autoAdvanceHandler.postDelayed(clearPhaseAdviceRunnable, PHASE_ADVICE_DURATION_MS)
        }
    }

    private fun phaseAdvice(): String? {
        val human = GameEngine.humanPlayer(session)
        val roleKey = human.role?.key ?: return null
        val allies = session.players
            .filter {
                !it.isHuman &&
                    it.role?.key in GameRules.traitorRoleKeys
            }
            .joinToString(", ") { it.name }

        return when (roleKey) {
            "asesino" -> if (allies.isNotBlank()) {
                "$allies tambien juega con los Traidores. No los defiendas de forma demasiado evidente."
            } else {
                "Desvia las sospechas sin parecer desesperado por controlar la votacion."
            }
            "mercenario" -> if (allies.isNotBlank()) {
                "$allies tambien juega con los Traidores. Silencia a quien pueda unir al pueblo contra ustedes."
            } else {
                "Silencia a quien guie bien la discusion, pero no repitas siempre el mismo objetivo."
            }
            "espia" -> if (allies.isNotBlank()) {
                "$allies tambien juega con los Traidores. Tu apariencia inocente puede ayudar a protegerlos."
            } else {
                "El Policia te vera como inocente. Aprovecha esa ventaja sin confiarte demasiado."
            }
            "medico" -> when {
                session.round == 1 && session.phase == GamePhase.NOCHE_MEDICO ->
                    "No confies demasiado pronto. Protegerte puede darte tiempo para reconocer aliados."
                session.protectedPlayer == human.name ->
                    "Ya te protegiste antes. Cambiar el objetivo puede volver tus decisiones menos predecibles."
                else ->
                    "Una buena alianza con el Policia puede sostener la informacion del pueblo."
            }
            "policia" -> when {
                session.investigatedPlayer.isNotBlank() ->
                    "Recorda tu pista sobre ${session.investigatedPlayer}; revelarla demasiado pronto puede exponerte."
                else ->
                    "No reveles todas tus investigaciones enseguida. Una verdad sin proteccion puede costarte la vida."
            }
            "alcalde" -> if (session.alcaldeRevealed) {
                "Tu voto pesa mas. Usa esa autoridad para ordenar el debate, no solo para imponerlo."
            } else {
                "Podes revelarte para dirigir al pueblo o guardar tu autoridad para un empate decisivo."
            }
            "payador" -> if (session.payadorUsed) {
                "El Contrapunto ya fue usado. Observa si sus respuestas cambiaron las sospechas del pueblo."
            } else {
                "Reserva el Contrapunto para dos jugadores cuyas versiones realmente se contradigan."
            }
            "desertor" -> if (session.desertorTeam.isBlank()) {
                "Observa que bando parece mejor preparado antes de comprometerte."
            } else {
                "Elegiste apoyar a ${session.desertorTeam}. Hace todo lo posible para que ese bando gane."
            }
            "bufon" ->
                "Contradicete, interrumpe y provoca, pero evita parecer demasiado desesperado por recibir votos."
            "oraculo" -> if (session.oracleUsed) {
                "Tu invocacion termino. Escucha como cambia el debate despues de devolver una voz."
            } else {
                "Elegi libremente: una voz experimentada puede orientar al pueblo y un acusado puede defenderse."
            }
            else ->
                "No tener una habilidad no te quita influencia. Compara versiones y recorda quien defendio a quien."
        }
    }

    private fun currentNarratorMessage(phaseText: PhaseText = phaseText(session.phase)): String {
        passiveNightMessage()?.let { return it }
        return GameplayTableUi.centralPhaseMessage(session, phaseText.subtitle)
    }

    private fun passiveNightMessage(): String? {
        if (session.quickTestMode || !isNightPhase(session.phase) || GameEngine.requiresHumanInput(session)) {
            return null
        }
        val messages = listOf(
            "Cerras los ojos. Alguien pisa una rama y todos fingen no haber escuchado.",
            "El pueblo duerme. Una sombra parece saber demasiado, pero no declara.",
            "Se escuchan susurros, pasos y una puerta que nadie va a admitir haber abierto.",
            "La noche hace su trabajo. Vos sobrevivis mirando el techo.",
            "Alguien se mueve en secreto. El mate queda frio y las sospechas calientes."
        )
        val index = (session.round * 31 + session.phaseIndex * 7 + session.phase.ordinal)
            .let { kotlin.math.abs(it) % messages.size }
        return messages[index]
    }

    private fun mustWaitForPhaseTimer(): Boolean {
        val human = GameEngine.humanPlayer(session)
        val hasSpecialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            (session.phase == GamePhase.NOCHE_ORACULO &&
                GameEngine.isHumanRoleTurn(session, RoleCatalog.ORACULO)) ||
            ((session.phase == GamePhase.DIA_DEBATE ||
                session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE) &&
                human.role?.key == "alcalde" &&
                !session.alcaldeRevealed)
        return !session.quickTestMode &&
            session.winner.isBlank() &&
            session.phase != GamePhase.REPARTO &&
            !GameEngine.requiresHumanInput(session) &&
            !hasSpecialDecision &&
            activePhaseSeconds() != null
    }

    private fun isNightPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.NOCHE_ASESINO ||
            phase == GamePhase.NOCHE_MERCENARIO ||
            phase == GamePhase.NOCHE_POLICIA ||
            phase == GamePhase.NOCHE_MEDICO ||
            phase == GamePhase.NOCHE_ORACULO
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
        val eliminated = !GameEngine.humanPlayer(session).alive
        actionControls.visibility = if (eliminated) View.GONE else View.VISIBLE
        eliminatedStatePanel.visibility = if (eliminated) View.VISIBLE else View.GONE
        currentPlayerStatus.visibility =
            if (status == null || eliminated) View.GONE else View.VISIBLE
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
        if (
            isVoteResultVisible ||
            isTieVoteVisible ||
            session.phase == GamePhase.RECUENTO_VOTOS ||
            session.phase == GamePhase.DESEMPATE_VOTACION
        ) {
            return
        }
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

    private fun dismissActionFeedbackBannerNow() {
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        if (!::actionFeedbackBanner.isInitialized) return
        actionFeedbackBanner.animate().cancel()
        actionFeedbackBanner.visibility = View.GONE
        actionFeedbackBanner.alpha = 1f
        actionFeedbackBanner.translationY = 0f
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
            GamePhase.DESEMPATE_VOTACION -> "Selecciona un jugador empatado y confirma VOTAR."
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
            isVoteResultVisible ||
            isTieVoteVisible ||
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
        if (
            voteNoExpulsionPresented &&
            session.phase == GamePhase.RESULTADO &&
            session.dayEliminationTarget.isBlank()
        ) {
            dismissActionFeedbackBannerNow()
            isVoteResultVisible = true
            voteResultAnimator.show(session)
            voteResultAnimator.showNoExpulsion()
            return
        }
        if (maybeShowNextDeathReveal()) return
        if (maybeShowNextSilenceReveal()) return
        if (maybeShowOracleReveal()) return
        if (maybeShowTieVote()) return
        if (maybeShowVoteResult()) return
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
        dismissActionFeedbackBannerNow()
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

    private fun maybeShowVoteResult(): Boolean {
        if (isVoteResultVisible) return true
        if (session.phase != GamePhase.RECUENTO_VOTOS) return false
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.playGamePhase(this, session)
        voteExpulsionComplete = false
        voteNoExpulsionPresented = false
        isVoteResultVisible = true
        voteResultAnimator.show(session)
        return true
    }

    private fun maybeShowTieVote(): Boolean {
        if (isTieVoteVisible) return true
        if (
            session.phase != GamePhase.DESEMPATE_VOTACION ||
            isChatOpen ||
            restoreTieVoteAfterChat
        ) {
            return false
        }
        showTieVoteWindow()
        return true
    }

    private fun showTieVoteWindow() {
        if (session.phase != GamePhase.DESEMPATE_VOTACION) return
        dismissActionFeedbackBannerNow()
        isTieVoteVisible = true
        tieVoteOverlay.visibility = View.VISIBLE
        tieVoteOverlay.alpha = 0f
        tieVotePanel.scaleX = 0.96f
        tieVotePanel.scaleY = 0.96f
        renderTieVoteWindow()
        tieVoteOverlay.animate().alpha(1f).setDuration(180L).start()
        tieVotePanel.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()
        ensureCountdownForCurrentPhase()
    }

    private fun renderTieVoteWindow() {
        val candidates = session.tieVoteCandidates.mapNotNull { candidate ->
            GameEngine.playerByName(session, candidate)
        }
        tieVoteCards.removeAllViews()
        tieVoteCards.columnCount = candidates.size.coerceIn(1, 4)
        tieVoteCards.rowCount = 1
        val cardWidth = when {
            candidates.size <= 2 -> 136
            candidates.size == 3 -> 116
            else -> 102
        }
        val cardHeight = if (candidates.size <= 2) 156 else 140
        candidates.forEach { player ->
            val card = createTieVoteCard(player)
            tieVoteCards.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = dp(cardWidth)
                    height = dp(cardHeight)
                    setMargins(dp(7), dp(4), dp(7), dp(4))
                }
            )
        }

        val human = GameEngine.humanPlayer(session)
        val hiddenHumanMayor =
            human.alive && human.role?.key == "alcalde" && !session.alcaldeRevealed
        btnTieRevealMayor.visibility = if (hiddenHumanMayor) View.VISIBLE else View.GONE
        btnTieVoteChat.isEnabled = GameEngine.canHumanChat(session)
        btnTieVoteChat.alpha = if (btnTieVoteChat.isEnabled) 1f else 0.45f
        tieVoteSubtitle.text = when {
            session.alcaldeRevealed && human.role?.key == "alcalde" ->
                "Tu voto vale doble. Elegi entre las cartas empatadas."
            else -> "Vota nuevamente entre los jugadores empatados."
        }
        tieVoteNotice.text =
            "SI EL EMPATE SE REPITE, NADIE SERA EXPULSADO."
        renderTieVoteSelection()
    }

    private fun createTieVoteCard(player: GamePlayer): View {
        val actionable = GameEngine.canActOnTarget(session, player.name)
        val selected = selectedTarget == player.name
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(9), dp(8), dp(6))
            alpha = if (actionable) 1f else 0.5f
            background = tieVoteCardBackground(selected, actionable)
            isClickable = actionable
            isFocusable = actionable
            contentDescription = when {
                player.isHuman -> "${player.name}, tu carta empatada"
                actionable -> "${player.name}, tocar para votar"
                else -> "${player.name}, no disponible"
            }
            setOnClickListener {
                if (!actionable) {
                    GameplayEffects.play(this@GameplayMockActivity, GameplayEffect.ERROR)
                    return@setOnClickListener
                }
                GameplayEffects.play(this@GameplayMockActivity, GameplayEffect.SELECT)
                selectedTarget = player.name
                renderTieVoteWindow()
            }
        }
        val card = FrameLayout(this)
        card.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.card_back_traidores)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            FrameLayout.LayoutParams(dp(58), dp(78), Gravity.CENTER)
        )
        card.addView(
            TextView(this).apply {
                text = player.initial
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_player_avatar)
                setTextColor(getColor(R.color.bg_dark))
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
            },
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(9)
            }
        )
        container.addView(card, LinearLayout.LayoutParams(dp(66), dp(82)))
        container.addView(
            TextView(this).apply {
                text = player.name
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(if (selected) R.color.accent_gold else R.color.text_primary))
                textSize = 13f
                typeface = ResourcesCompat.getFont(this@GameplayMockActivity, R.font.grenze)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25))
        )
        container.addView(
            TextView(this).apply {
                text = when {
                    player.isHuman -> "TU CARTA"
                    selected -> "SELECCIONADO"
                    else -> "TOCAR PARA VOTAR"
                }
                gravity = Gravity.CENTER
                setTextColor(getColor(if (selected) R.color.accent_gold else R.color.text_secondary))
                textSize = 8f
                setTypeface(null, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(19))
        )
        return container
    }

    private fun tieVoteCardBackground(selected: Boolean, enabled: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(if (selected) "#F03B2A18" else "#E51B150F"))
            setStroke(
                dp(if (selected) 2 else 1),
                getColor(
                    when {
                        selected -> R.color.accent_gold
                        enabled -> R.color.btn_dark_border
                        else -> R.color.text_muted
                    }
                )
            )
            cornerRadius = dp(6).toFloat()
        }
    }

    private fun renderTieVoteSelection() {
        val validSelection = selectedTarget.isNotBlank() &&
            GameEngine.canActOnTarget(session, selectedTarget)
        btnConfirmTieVote.isEnabled = validSelection
        btnConfirmTieVote.alpha = if (validSelection) 1f else 0.55f
        btnConfirmTieVote.text = if (validSelection) {
            "VOTAR A ${selectedTarget.uppercase()}"
        } else {
            "ELEGIR CARTA"
        }
    }

    private fun confirmTieVoteSelection() {
        if (!GameEngine.canActOnTarget(session, selectedTarget)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            return
        }
        hideTieVoteWindow(clearSelection = false)
        performTargetAction(selectedTarget)
    }

    private fun revealMayorFromTieVote() {
        val before = session
        session = GameEngine.revealAlcalde(session)
        if (before == session) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        renderTieVoteWindow()
    }

    private fun openChatFromTieVote() {
        if (!GameEngine.canHumanChat(session)) return
        restoreTieVoteAfterChat = true
        hideTieVoteWindow(clearSelection = false)
        toggleChatPanel()
    }

    private fun hideTieVoteWindow(clearSelection: Boolean) {
        if (!::tieVoteOverlay.isInitialized) return
        tieVoteOverlay.animate().cancel()
        tieVotePanel.animate().cancel()
        tieVoteOverlay.visibility = View.GONE
        tieVoteOverlay.alpha = 1f
        tieVotePanel.scaleX = 1f
        tieVotePanel.scaleY = 1f
        isTieVoteVisible = false
        if (clearSelection) clearSelection()
    }

    private fun handleVoteResultContinue() {
        if (!isVoteResultVisible || !btnContinueVoteResult.isEnabled) return
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        GameplayEffects.play(this, GameplayEffect.PANEL)

        if (voteNoExpulsionPresented) {
            voteNoExpulsionPresented = false
            isVoteResultVisible = false
            voteResultAnimator.hide()
            MusicManager.resumeGamePhaseAfterTransition(this, session)
            renderGame()
            return
        }

        if (
            session.phase == GamePhase.RECUENTO_VOTOS &&
            session.tieVoteCandidates.isEmpty() &&
            session.dayEliminationTarget.isNotBlank() &&
            !voteExpulsionComplete
        ) {
            voteResultAnimator.playExpulsion(session) {
                voteExpulsionComplete = true
            }
            return
        }

        val advanced = GameEngine.continueAfterVoteRecount(session)
        if (
            advanced.phase == GamePhase.RESULTADO &&
            advanced.dayEliminationTarget.isBlank()
        ) {
            session = advanced
            dismissActionFeedbackBannerNow()
            voteNoExpulsionPresented = true
            voteResultAnimator.showNoExpulsion()
            return
        }

        session = advanced
        isVoteResultVisible = false
        voteExpulsionComplete = false
        voteResultAnimator.hide()
        clearSelection()
        MusicManager.resumeGamePhaseAfterTransition(this, session)
        renderGame()
    }

    private fun handleVoteResultAutoContinue() {
        if (!isVoteResultVisible || !btnContinueVoteResult.isEnabled) return
        handleVoteResultContinue()
    }

    private fun scheduleVoteResultAutoContinue() {
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        if (!isVoteResultVisible) return
        val delayMs = if (session.quickTestMode) 1_200L else 8_000L
        autoAdvanceHandler.postDelayed(voteResultAutoContinueRunnable, delayMs)
    }

    private fun cancelVoteResult() {
        if (!::voteResultAnimator.isInitialized) return
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        voteResultAnimator.hide()
        isVoteResultVisible = false
        voteExpulsionComplete = false
    }

    private fun showSilenceReveal(player: GamePlayer) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
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
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        GameplaySoundEffects.play(this, R.raw.oracle_ability)
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
        dismissActionFeedbackBannerNow()
        eventLogHeightAnimator?.cancel()
        isJesterVictoryVisible = true
        presentedSpecialVictoryCount += 1
        jesterVictoryPlayer.text = "${victory.playerName.uppercase()} ERA EL BUFÓN"
        jesterVictoryMessage.text =
            "Consiguió que el pueblo lo expulsara durante la votación."
        val player = session.players.firstOrNull { it.name == victory.playerName }
        jesterVictoryImage.setImageResource(roleImageFor(player?.role))
        MusicManager.playVictoryMusic(this)
        GameplaySoundEffects.play(this, R.raw.jester_victory)
        jesterVictoryAnimator.show(JESTER_VICTORY_DURATION_MS)
    }

    private fun playResolvedActionSound(before: GameSession, after: GameSession) {
        when {
            !before.payadorUsed && after.payadorUsed -> {
                GameplaySoundEffects.play(this, R.raw.payador_ability)
            }
        }
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
        dismissActionFeedbackBannerNow()
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
            0 -> 0.9f
            2 -> 1.15f
            else -> 1f
        }
        val relativeScale = requestedScale / appliedGameplayTextScale
        if (relativeScale != 1f) {
            scaleTextRecursively(gameplayRoot, relativeScale)
            appliedGameplayTextScale = requestedScale
        }
    }

    private fun initialRoleReadingDelayMs(): Long {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(PREF_ROLE_READING_SECONDS, DEFAULT_ROLE_READING_SECONDS)
            .coerceIn(0, 10) * 1000L
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
        dismissActionFeedbackBannerNow()
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
        dayNightTransitionAnimator.start(
            spec,
            fromPeriod,
            session.timingConfig.normalized().transitionSeconds * 1000L
        )
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
        private const val CHAT_PANEL_WIDTH_RATIO = 0.46f
        private const val CHAT_PANEL_COMPACT_WIDTH_RATIO = 0.72f
        private const val CHAT_PANEL_MIN_WIDTH_DP = 320
        private const val CHAT_PANEL_MAX_WIDTH_DP = 420
        private const val CHAT_PANEL_COMPACT_MIN_WIDTH_DP = 300
        private const val CHAT_PANEL_COMPACT_MAX_WIDTH_DP = 520
        private const val CHAT_PANEL_COMPACT_MARGIN_DP = 5
        private const val CHAT_PANEL_TOP_MARGIN_DP = 86
        private const val CHAT_PANEL_BOTTOM_MARGIN_DP = 96
        private const val CHAT_MESSAGE_MAX_LENGTH = 140
        private const val CHAT_MESSAGE_WARNING_LENGTH = 120
        private const val MAX_STAGGERED_BOT_REACTIONS = 2
        private const val FIRST_BOT_REACTION_DELAY_MS = 950L
        private const val NEXT_BOT_REACTION_DELAY_MS = 1_150L
        private const val PHASE_BOT_BURST_DELAY_MS = 1_350L
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val STATE_SESSION = "gameplay_session"
        private const val STATE_CHAT_OPEN = "chat_open"
        private const val STATE_EVENT_LOG_EXPANDED = "event_log_expanded"
        private const val STATE_VOTE_NO_EXPULSION_PRESENTED =
            "vote_no_expulsion_presented"
        private const val STATE_ROLE_PREVIEW_OPEN = "role_preview_open"
        private const val STATE_INITIAL_ROLE_READING = "initial_role_reading"
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
        private const val STATE_ONLINE_PARTIDA_ID = "online_partida_id"
        private const val STATE_ONLINE_PLAYER_ID = "online_player_id"
        private const val STATE_ONLINE_IS_HOST = "online_is_host"
        private const val TRAITOR_REVEAL_DURATION_MS = 8000L
        private const val JESTER_VICTORY_DURATION_MS = 5000L
        private const val COUNTDOWN_TICK_MS = 200L
        private const val INFORMATION_FEEDBACK_DURATION_MS = 10_000L
        private const val PHASE_ADVICE_DURATION_MS = 6_000L
        private const val PREF_ROLE_READING_SECONDS = "role_reading_seconds"
        private const val DEFAULT_ROLE_READING_SECONDS = 6

        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
        const val EXTRA_ONLINE_PARTIDA_ID = "extra_online_partida_id"
        const val EXTRA_ONLINE_PLAYER_ID = "extra_online_player_id"
        const val EXTRA_ONLINE_IS_HOST = "extra_online_is_host"
    }
}
