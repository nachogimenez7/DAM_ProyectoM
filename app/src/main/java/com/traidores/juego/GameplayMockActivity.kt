package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.ceil

private data class RevealPanelTheme(
    val frame: Int,
    val innerColor: Int
)

private data class TraitorRevealCardMetrics(
    val columns: Int,
    val slotWidthDp: Int,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val nameTextSp: Float,
    val roleTextSp: Float
)

class GameplayMockActivity : BaseActivity(), GameplayChatController.ChatHost {

    private var isCardRevealed = false
    private var appliedGameplayTextScale = 1f
    private var isEventLogExpanded = false
    private var lastRenderedAnnouncement = ""
    private var lastRenderedPhase: GamePhase? = null
    private var lastMapBackgroundRes = 0
    private var lastEventLogBackgroundRes = 0
    private var lastRoleCardImageRes = 0
    private var humanCardHasRendered = false
    private var lastHumanPublicRoleVisible = false
    private var selectedTarget = ""
    private var lastCompanionCardMetrics: CompanionCardMetrics? = null
    private var desertorDialogOpen = false
    private var isDayNightTransitionRunning = false
    private var isDeathRevealRunning = false
    private var isSilenceRevealRunning = false
    private var isNoDeathRevealRunning = false
    private var isPayadorRevealVisible = false
    private var isOracleRevealVisible = false
    private var lastPresentedPayadorRevealKey: String? = null
    private var activePayadorRevealKey: String? = null
    private var lastPresentedOracleRevealKey: String? = null
    private var activeOracleRevealKey: String? = null
    private var isRolePreviewOpen = false
    private var initialRoleReadingActive = false
    private var roleReadingReadyAtElapsedMs = 0L
    private var restoredRoleReadingRemainingMs = -1L
    private var activePhaseAdvice: String? = null
    private var advicePhaseIndex = -1
    private var restoreRolePreviewOnResume = false
    private var restoreInitialRoleReadingOnResume = false
    private var isWinnerRevealVisible = false
    private var isJesterVictoryVisible = false
    private var isVoteResultVisible = false
    private var isTieVoteVisible = false
    private var voteExpulsionComplete = false
    private var voteExpulsionAnimationKey = ""
    private var voteNoExpulsionPresented = false
    private var onlineVotePresentation = ""
    private var lastAppliedOnlineVotePresentation = ""
    private var spectatorChoiceOffered = false
    private var isTraitorRevealDismissing = false
    private var isTraitorRevealRunning = false
    private var lastPresentedTransitionKey: String? = null
    private var lastActionAttentionKey: String? = null
    private var presentedPeriod: GameplayPeriod? = null
    private var blockingFeedbackPeriod: GameplayPeriod? = null
    private var traitorRevealCompleted = false
    private var winnerRevealPresented = false
    private var returningToOnlineLobby = false
    private var onlineLobbyReturnEpochMs = 0L
    private var onlineWinnerReturnAckKey = ""
    private var onlineWinnerReturnClientAcks = emptyMap<String, String>()
    private var onlineWinnerReturnAdvanceInProgress = false
    private var presentedSpecialVictoryCount = 0
    private val countdown = GameplayCountdown()
    private var lastCountdownSecond = -1
    private var knownDeadPlayers = emptySet<String>()
    private var knownMutedPlayers = emptySet<String>()
    private var pendingNoDeathReveal = false
    private var lastNoDeathRevealRound = -1
    private var nightSkipArmPhaseIndex = -1
    private var nightSkipEnabledAtMs = 0L
    private var nightSkipEnableScheduled = false
    private var lastRenderedEventMessages = emptyList<String>()
    private var lastRenderedEventExpanded: Boolean? = null
    private var lastPresentedCentralEventKey: String? = null
    private var lastPresentedAssassinVoteLogKey: String? = null
    private var onlineTraitorActionMarks = emptyList<OnlineTraitorActionMark>()
    private var humanActionMarkKey: String? = null
    private var humanActionMarkAnimator: AnimatorSet? = null
    private val readyToVote = mutableSetOf<String>()
    private var readyVotePhaseIndex = -1
    private var readyVoteBotCascadeScheduled = false
    private var readyVoteAdvanceInProgress = false
    private val readyVoteBotRunnables = mutableListOf<Runnable>()
    private var onlineVoteReadyStates = emptyList<OnlineVoteReadyState>()
    private var lastReactionRound = -1
    private var botReactionScheduled = false
    private var botReactionScheduleKey = ""
    private val feedbackState = GameplayFeedbackState()
    private val reactionLimiter = GameplayReactionLimiter()
    private val activeReactionBubbles = mutableMapOf<String, View>()
    private val pendingOnlineReactions = linkedMapOf<String, ReactionSpec>()
    private var gameplayResumed = false
    private val defaultReactionSpecs = reactionSpecsForTheme(EmoteCatalog.THEME_GREEK)
    private val medievalAssassinReactionSpecs =
        reactionSpecsForTheme(EmoteCatalog.THEME_MEDIEVAL_ASSASSIN)
    private val gauchoDetectiveReactionSpecs =
        reactionSpecsForTheme(EmoteCatalog.THEME_GAUCHO_DETECTIVE)
    private lateinit var session: GameSession
    override var currentSession: GameSession
        get() = session
        set(value) {
            session = value
        }
    override val gameplayTextScale: Float
        get() = appliedGameplayTextScale
    override val onlineRoomId: String
        get() = onlinePartidaId
    override val onlinePlayerUid: String
        get() = onlinePlayerId
    override fun isOnlineActorLocallyMuted(actorId: String): Boolean {
        if (actorId.isBlank()) return false
        val index = session.onlinePlayerUids.indexOf(actorId)
        val player = session.players.getOrNull(index)
        val publicId = player?.let { session.playerProfiles[it.name]?.publicId }.orEmpty()
        return LocalMuteStore.isMuted(this, publicId, actorId)
    }
    override fun isOwnPlayerTableSilenced(): Boolean = ownPlayerTableSilenced
    private var onlinePartidaId = ""
    private var onlinePlayerId = ""
    private var onlineIsHost = false
    private var lastPublishedOnlineStateKey = ""
    private var lastPublishedAuthoritativeOnlineStateKey = ""
    private var lastAppliedAuthoritativeOnlineStateKey = ""
    private var lastAppliedAuthoritativePhaseLabel = ""
    private var onlineIncompatibleStateHandled = false
    private var onlineAwaitingHostAdvance = false
    private var onlineAwaitingHostSinceMs = 0L
    private var onlineSyncDelayReported = false
    private var onlineInitialRoleRead = false
    private var onlineStartupDeadlineEpochMs = 0L
    private var onlineStartupDeadlinePublishInProgress = false
    private var onlineStartupGateResult: OnlineStartupGateResult? = null
    private var lastOnlineStartupGateKey = ""
    private val publishedTraitorPlanNoticeIds = mutableSetOf<String>()
    private var lastOnlineStartupClientStates = emptyList<OnlineStartupClientState>()
    private var onlineNightResolutionInProgress = false
    private var onlineVoteResolutionInProgress = false
    private var onlineStateListener: ListenerRegistration? = null
    private var onlinePlayersListener: ListenerRegistration? = null
    private var onlineActionsListener: ListenerRegistration? = null
    private var onlinePrivateClueListener: ListenerRegistration? = null
    private var lastOnlineInvestigationClueKey = ""
    private var lastPublishedOnlineInvestigationClueKey = ""
    private var onlineActiveHostId = ""
    private var onlineHostHandoffInProgress = false
    private var onlineHostPromotionInProgress = false
    private var onlineGuestHostWindowStartedAtMs = 0L
    private var onlinePresencePlayers = emptyList<OnlinePresencePlayer>()
    private var realtimePresence: RealtimeRoomPresence? = null
    private var realtimeTableSilence: RealtimeTableSilence? = null
    private var ownPlayerTableSilenced = false
    private var realtimePresenceStates = emptyMap<String, RealtimePresenceState>()
    private var realtimePresenceBaselineReady = false
    private var lastLegacyPresenceState = ""
    private var onlineNightActionRecords = emptyList<OnlineActionRecord>()
    private var onlineNightActionsServerConfirmed = false
    private var onlineMayorRevealSent = false
    private var onlineDesertorChoiceSent = false
    private var onlineNightGateKey = ""
    private var onlineNightGateStartedAtMs = 0L
    private var onlineNightGateFloorMs = 0L
    private var onlineNightAllActionsReadyAtMs = 0L
    private var onlineNightPostActionDelayMs = 0L
    private var onlineNightTimerExpired = false
    private var onlinePresentationClientAcks = emptyMap<String, String>()
    private var onlinePresentationKey = ""
    private var onlinePresentationAckKey = ""
    private var onlinePresentationStartedAtMs = 0L
    private var onlinePresentationAdvanceInProgress = false
    private var onlineGameplayStartedAtMs = 0L
    private var lastOnlinePresencePulseAtMs = 0L
    private var onlinePresencePulseIntervalMs = OnlineSyncWatchdog.PRESENCE_PULSE_MS
    private var lastOnlineWatchdogReason = ""
    private val submittedOnlineNightActions = mutableSetOf<String>()
    private val pendingOnlineActionSubmissions = mutableSetOf<String>()
    private val submittedOnlinePayadorTargets = mutableSetOf<String>()
    private val pendingOnlinePayadorTargets = mutableSetOf<String>()
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val localPhaseExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "local-phase-resolution").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private var localPhaseResolutionInProgress = false
    private var localPhaseResolutionToken = 0
    private var localPhaseResolutionActionLabel = "RESOLVIENDO"
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val onlineHostPromotionRetryRunnable = Runnable {
        if (!onlineIsHost && onlineActiveHostId == onlinePlayerId) {
            promoteToOnlineHost("role_recovery_retry")
        }
    }
    private val winnerAutoReturnRunnable = Runnable {
        if (isWinnerRevealVisible && ::session.isInitialized && session.winner.isNotBlank()) {
            returnToLobbyNow()
        }
    }
    private val nightSkipEnableRunnable = Runnable {
        nightSkipEnableScheduled = false
        if (::btnAction.isInitialized && ::session.isInitialized) {
            renderAdvanceButton()
        }
    }
    private val voteResultAutoContinueRunnable = Runnable { handleVoteResultAutoContinue() }
    private val onlinePresentationGateRunnable = Runnable { tickOnlinePresentationGate() }
    private val onlineNightGateRunnable = Runnable { handleOnlineNightGateFloorReached() }
    private val feedbackDismissRunnable = Runnable { dismissCurrentFeedback() }
    private val feedbackBannerDismissRunnable = Runnable { hideActionFeedbackBanner() }
    private val deathRevealContinueTimeoutRunnable = Runnable { continueDeathReveal() }
    private val centralPublicEventDismissRunnable = Runnable { hideCentralPublicEventBanner() }
    private val payadorRevealAutoDismissRunnable = Runnable { dismissPayadorReveal() }
    private val oracleRevealAutoDismissRunnable = Runnable { dismissOracleReveal() }
    private val botReactionRunnable = object : Runnable {
        override fun run() {
            botReactionScheduled = false
            maybeTriggerBotReaction()
            scheduleBotReactionIfNeeded()
        }
    }
    private val onlineStartupTickRunnable = Runnable {
        if (isOnlineStartupPhase()) {
            refreshOnlineStartupGateFromLastStates()
        }
    }
    private val guestHostWindowRunnable = Runnable {
        handleOnlineHostHandoff(onlinePresencePlayers)
    }
    private val onlineSyncWatchdogRunnable = object : Runnable {
        override fun run() {
            runOnlineSyncWatchdog()
        }
    }
    private val roleReadingTickRunnable = object : Runnable {
        override fun run() {
            if (!initialRoleReadingActive || !::btnContinueRolePreview.isInitialized) return
            autoAdvanceHandler.removeCallbacks(this)
            val readingRemainingMs = roleReadingRemainingMs()
            val onlineCountdownSeconds = if (isOnlineStartupPhase()) {
                onlineStartupCountdownSeconds()
            } else {
                null
            }
            btnContinueRolePreview.visibility = View.VISIBLE
            btnContinueRolePreview.alpha = if (readingRemainingMs > 0L) 0.72f else 1f
            btnContinueRolePreview.isEnabled = readingRemainingMs <= 0L
            btnContinueRolePreview.text = when {
                // Cuando ya existe la cuenta regresiva compartida, todos deben ver esa misma
                // referencia. Antes se priorizaba el bloqueo local de lectura y un emulador
                // podia mostrar 9 mientras el resto mostraba 15, aunque la partida estuviera bien.
                onlineCountdownSeconds != null -> "EMPEZAR ($onlineCountdownSeconds)"
                readingRemainingMs > 0L ->
                    "EMPEZAR (${ceil(readingRemainingMs / 1000.0).toInt()})"
                else -> "EMPEZAR"
            }
            if (readingRemainingMs > 0L || onlineCountdownSeconds != null) {
                val nextReadingTickMs = readingRemainingMs
                    .takeIf { it > 0L }
                    ?.let { minOf(1_000L, it) }
                    ?: 1_000L
                autoAdvanceHandler.postDelayed(
                    this,
                    nextReadingTickMs
                )
            }
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
    private var centralPublicEventAnimator: AnimatorSet? = null
    private var feedbackAnimator: AnimatorSet? = null
    private var reactionPalette: PopupWindow? = null
    private lateinit var chatController: GameplayChatController
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
    private lateinit var btnRevealMayorSecondary: Button
    private lateinit var btnReadyToVote: Button
    private lateinit var btnToggleEmotes: ImageButton
    private lateinit var btnToggleEventLog: Button
    private lateinit var centralPublicEventBanner: FrameLayout
    private lateinit var centralPublicEventIcon: TextView
    private lateinit var centralPublicEventLabel: TextView
    private lateinit var centralPublicEventMessage: TextView
    private lateinit var centralPublicEventShine: View
    private lateinit var centralPublicEventTitle: TextView
    private lateinit var centralPublicEventTone: View
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
    private lateinit var btnContinueDeathReveal: Button
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
    private lateinit var btnContinuePrivateFeedback: Button
    private lateinit var rightPlayersContainer: LinearLayout
    private lateinit var rightPlayersScroll: ScrollView
    private lateinit var rightColumn: LinearLayout
    private lateinit var bottomPlayerPanel: LinearLayout
    private lateinit var roleCard: FrameLayout
    private lateinit var humanActionMarkOverlay: ImageView
    private lateinit var humanActionMarkSecondaryOverlay: ImageView
    private lateinit var humanActionMarkTertiaryOverlay: ImageView
    private lateinit var humanActionMarkPrimaryLabel: TextView
    private lateinit var humanActionMarkSecondaryLabel: TextView
    private lateinit var humanActionMarkTertiaryLabel: TextView
    private lateinit var roleImage: ImageView
    private lateinit var humanDeathCauseOverlay: ImageView
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
    private lateinit var noDeathRevealContent: LinearLayout
    private lateinit var noDeathRevealOverlay: FrameLayout
    private lateinit var noDeathSunCore: ImageView
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
    private lateinit var noDeathRevealAnimator: NoDeathRevealAnimator
    private lateinit var payadorRevealOverlay: FrameLayout
    // Solo se anima como una View; no dependemos del tipo concreto del contenedor XML.
    // Esto evita que un cambio LinearLayout/FrameLayout vuelva a cerrar el gameplay al abrir.
    private lateinit var payadorRevealPanel: View
    private lateinit var payadorRevealFirstPlayer: TextView
    private lateinit var payadorRevealSecondPlayer: TextView
    private lateinit var payadorRevealProgress: View
    private lateinit var oracleRevealOverlay: FrameLayout
    private lateinit var oracleRevealPanel: FrameLayout
    private lateinit var oracleRevealPlayer: TextView
    private lateinit var oracleRevealProgress: View
    private lateinit var traitorRevealCardsScroll: HorizontalScrollView
    private lateinit var traitorRevealCards: GridLayout
    private lateinit var traitorRevealContent: LinearLayout
    private lateinit var traitorRevealOverlay: FrameLayout
    private lateinit var btnContinueJesterVictory: Button
    private lateinit var btnReturnJesterVictory: Button
    private lateinit var jesterVictoryActions: LinearLayout
    private lateinit var jesterConfettiLayer: FrameLayout
    private lateinit var jesterHornLeft: ImageView
    private lateinit var jesterHornRight: ImageView
    private lateinit var jesterVictoryImage: ImageView
    private lateinit var jesterVictoryMessage: TextView
    private lateinit var jesterVictoryOverlay: FrameLayout
    private lateinit var jesterVictoryPanel: FrameLayout
    private lateinit var jesterVictoryPlayer: TextView
    private var jesterVictoryOffersLocalSpectatorActions = false
    private lateinit var winnerRevealBackground: ImageView
    private lateinit var winnerRevealCards: LinearLayout
    private lateinit var winnerRevealContent: LinearLayout
    private lateinit var winnerRevealOverlay: FrameLayout
    private lateinit var winnerRevealPanel: FrameLayout
    private lateinit var winnerRevealPersonalResult: TextView
    private lateinit var winnerRevealScroll: ScrollView
    private lateinit var winnerRevealShine: View
    private lateinit var winnerRevealTitle: TextView
    private lateinit var winnerSummaryPanel: LinearLayout
    private lateinit var winnerSummaryStatsRow: LinearLayout
    private lateinit var winnerSummaryDuration: TextView
    private lateinit var winnerSummaryHighlight: TextView
    private lateinit var winnerSummaryPlayers: TextView
    private lateinit var winnerSummaryRounds: TextView
    private lateinit var winnerSummaryTimeline: TextView
    private lateinit var btnWinnerReturnLobby: Button
    private lateinit var voteResultOverlay: FrameLayout
    private lateinit var voteResultPanel: LinearLayout
    private lateinit var voteResultCards: GridLayout
    private lateinit var voteResultTitle: TextView
    private lateinit var voteResultSubtitle: TextView
    private lateinit var voteResultNotice: TextView
    private lateinit var btnContinueVoteResult: Button
    private lateinit var voteKickBoot: ImageView
    private lateinit var voteKickDust: ImageView
    private lateinit var tieVoteOverlay: FrameLayout
    private lateinit var tieVotePanel: LinearLayout
    private lateinit var tieVoteCardsScroll: ScrollView
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
    private val tieVoteCardViews = linkedMapOf<String, TieVoteCardHolder>()
    private lateinit var winnerRevealAnimator: WinnerRevealAnimator
    private lateinit var winnerResultsRenderer: WinnerResultsRenderer

    private data class SidePlayerCardHolder(
        val root: LinearLayout,
        val cardFace: FrameLayout,
        val cardBack: ImageView,
        val roleFace: ImageView,
        val deathCauseOverlay: ImageView,
        val actionMarkPrimary: ImageView,
        val actionMarkSecondary: ImageView,
        val actionMarkTertiary: ImageView,
        val actionMarkPrimaryLabel: TextView,
        val actionMarkSecondaryLabel: TextView,
        val actionMarkTertiaryLabel: TextView,
        val avatar: TextView,
        val mutedBadge: TextView,
        val actionBadge: TextView,
        val name: TextView,
        var selected: Boolean = false,
        var actionPulseKey: String? = null,
        var actionBadgeAnimator: AnimatorSet? = null,
        var actionMarkAnimator: AnimatorSet? = null,
        var actionMarkKey: String? = null,
        var hasBound: Boolean = false,
        var publicRoleVisible: Boolean = false,
        var renderKey: String? = null
    )

    private data class TieVoteCardHolder(
        val root: LinearLayout,
        val name: TextView,
        val status: TextView
    )

    private data class ReactionSpec(
        val id: String,
        val key: String,
        val imageRes: Int,
        val label: String,
        val toneHex: String
    )

    private data class OnlineChatEntry(
        val id: String,
        val speaker: String,
        val message: String,
        val isGod: Boolean
    )

    private data class OnlinePresencePlayer(
        val id: String,
        val name: String,
        val order: Int,
        val state: String,
        val activeInMatch: Boolean,
        val lastSeenLocalMs: Long,
        /** Solo las cuentas registradas reservan `publicId`, asi que sirve de señal. */
        val registered: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        @Suppress("DEPRECATION")
        val restoredSession = savedInstanceState?.getSerializable(STATE_SESSION) as? GameSession
        val incomingOnlinePartidaId = savedInstanceState?.getString(STATE_ONLINE_PARTIDA_ID)
            ?: intent.getStringExtra(EXTRA_ONLINE_PARTIDA_ID).orEmpty()
        val incomingOnlinePlayerId = savedInstanceState?.getString(STATE_ONLINE_PLAYER_ID)
            ?: intent.getStringExtra(EXTRA_ONLINE_PLAYER_ID).orEmpty()
        val incomingOnline = incomingOnlinePartidaId.isNotBlank() || incomingOnlinePlayerId.isNotBlank()
        val incomingSession = restoredSession ?: readSession()
        if (incomingSession == null && incomingOnline) {
            OnlineDebugLog.w("gameplay_missing_online_session roomId=$incomingOnlinePartidaId uid=$incomingOnlinePlayerId")
            GameNotice.show(
                activity = this,
                message = "La sala perdio datos de partida. Reingresa desde Online o creen una sala nueva.",
                duration = GameNotice.Duration.LONG
            )
            if (incomingOnlinePartidaId.isNotBlank()) {
                OnlineRoomRecovery.clearIf(this, incomingOnlinePartidaId)
            }
            finish()
            return
        }
        session = PlayerProfileStore.withProfiles(
            this,
            incomingSession ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        )
        onlinePartidaId = incomingOnlinePartidaId
        onlinePlayerId = incomingOnlinePlayerId
        if (incomingOnline) {
            val localPlayerIndex = session.onlinePlayerUids.indexOf(onlinePlayerId)
            session = session.copy(
                players = session.players.mapIndexed { index, player ->
                    val isLocalPlayer = if (localPlayerIndex >= 0) {
                        index == localPlayerIndex
                    } else {
                        player.isHuman
                    }
                    player.copy(
                        isHuman = isLocalPlayer,
                        control = if (isLocalPlayer) PlayerControl.LOCAL else PlayerControl.REMOTE
                    )
                }
            )
        }
        onlineIsHost = savedInstanceState?.getBoolean(STATE_ONLINE_IS_HOST)
            ?: intent.getBooleanExtra(EXTRA_ONLINE_IS_HOST, false)
        onlineInitialRoleRead = savedInstanceState?.getBoolean(STATE_ONLINE_INITIAL_ROLE_READ)
            ?: (session.phase != GamePhase.REPARTO)
        onlineStartupDeadlineEpochMs = savedInstanceState
            ?.getLong(STATE_ONLINE_STARTUP_DEADLINE_EPOCH_MS, 0L)
            ?: 0L
        onlinePresentationAckKey = savedInstanceState
            ?.getString(STATE_ONLINE_PRESENTATION_ACK_KEY)
            .orEmpty()
        onlineWinnerReturnAckKey = savedInstanceState
            ?.getString(STATE_ONLINE_WINNER_RETURN_ACK_KEY)
            .orEmpty()
        if (onlinePartidaId.isNotBlank() || onlinePlayerId.isNotBlank()) {
            OnlineDebugLog.i(
                "gameplay_enter roomId=$onlinePartidaId uid=$onlinePlayerId isHost=$onlineIsHost restored=${savedInstanceState != null}"
            )
        }
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
        restoredRoleReadingRemainingMs = savedInstanceState
            ?.getLong(STATE_ROLE_READING_REMAINING_MS, -1L)
            ?: -1L
        val shouldPresentRolePreview = shouldShowInitialRoleReveal || shouldRestoreRolePreview
        readyVotePhaseIndex = savedInstanceState?.getInt(STATE_READY_VOTE_PHASE_INDEX, -1) ?: -1
        readyToVote += savedInstanceState
            ?.getStringArrayList(STATE_READY_TO_VOTE_PLAYERS)
            .orEmpty()
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
        onlineLobbyReturnEpochMs = savedInstanceState
            ?.getLong(STATE_ONLINE_LOBBY_RETURN_EPOCH_MS, 0L)
            ?: 0L
        lastPresentedPayadorRevealKey =
            savedInstanceState?.getString(STATE_PAYADOR_REVEAL_KEY)
        lastPresentedOracleRevealKey =
            savedInstanceState?.getString(STATE_ORACLE_REVEAL_KEY)
        lastNoDeathRevealRound =
            savedInstanceState?.getInt(STATE_LAST_NO_DEATH_REVEAL_ROUND, -1) ?: -1
        isEventLogExpanded = false
        voteNoExpulsionPresented =
            savedInstanceState?.getBoolean(STATE_VOTE_NO_EXPULSION_PRESENTED) ?: false
        spectatorChoiceOffered =
            savedInstanceState?.getBoolean(STATE_SPECTATOR_CHOICE_OFFERED) ?: false
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
        btnRevealMayorSecondary = findViewById(R.id.btnRevealMayorSecondary)
        btnReadyToVote = findViewById(R.id.btnReadyToVote)
        btnToggleEmotes = findViewById(R.id.btnToggleEmotes)
        btnToggleEventLog = findViewById(R.id.btnToggleEventLog)
        centralPublicEventBanner = findViewById(R.id.centralPublicEventBanner)
        centralPublicEventIcon = findViewById(R.id.centralPublicEventIcon)
        centralPublicEventLabel = findViewById(R.id.centralPublicEventLabel)
        centralPublicEventMessage = findViewById(R.id.centralPublicEventMessage)
        centralPublicEventShine = findViewById(R.id.centralPublicEventShine)
        centralPublicEventTitle = findViewById(R.id.centralPublicEventTitle)
        centralPublicEventTone = findViewById(R.id.centralPublicEventTone)
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
        btnContinueDeathReveal = findViewById(R.id.btnContinueDeathReveal)
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
        btnContinuePrivateFeedback = findViewById(R.id.btnContinuePrivateFeedback)
        rightPlayersContainer = findViewById(R.id.rightPlayersContainer)
        rightPlayersScroll = findViewById(R.id.rightPlayersScroll)
        rightColumn = findViewById(R.id.rightColumn)
        bottomPlayerPanel = findViewById(R.id.bottomPlayerPanel)
        roleCard = findViewById(R.id.roleCard)
        humanActionMarkOverlay = findViewById(R.id.humanActionMarkOverlay)
        humanActionMarkSecondaryOverlay = findViewById(R.id.humanActionMarkSecondaryOverlay)
        humanActionMarkTertiaryOverlay = findViewById(R.id.humanActionMarkTertiaryOverlay)
        humanActionMarkPrimaryLabel = findViewById(R.id.humanActionMarkPrimaryLabel)
        humanActionMarkSecondaryLabel = findViewById(R.id.humanActionMarkSecondaryLabel)
        humanActionMarkTertiaryLabel = findViewById(R.id.humanActionMarkTertiaryLabel)
        roleImage = findViewById(R.id.roleImage)
        humanDeathCauseOverlay = findViewById(R.id.humanDeathCauseOverlay)
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
        noDeathRevealContent = findViewById(R.id.noDeathRevealContent)
        noDeathRevealOverlay = findViewById(R.id.noDeathRevealOverlay)
        noDeathSunCore = findViewById(R.id.noDeathSunCore)
        topStatus = findViewById(R.id.topStatus)
        transitionFromBackground = findViewById(R.id.transitionFromBackground)
        transitionMoon = findViewById(R.id.transitionMoon)
        transitionShade = findViewById(R.id.transitionShade)
        transitionSun = findViewById(R.id.transitionSun)
        transitionTitle = findViewById(R.id.transitionTitle)
        transitionToBackground = findViewById(R.id.transitionToBackground)
        dayNightTransitionAnimator = DayNightTransitionAnimator(
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
            onRevealBackground = { spec ->
                revealDayNightBackground(spec)
            },
            onFinished = { spec ->
                finishDayNightTransition(spec)
            }
        )
        deathRevealAnimator = DeathRevealAnimator(
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
            onReadyToContinue = ::showDeathRevealContinue,
            onFinished = ::finishDeathReveal
        )
        silenceRevealAnimator = SilenceRevealAnimator(
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
        noDeathRevealAnimator = NoDeathRevealAnimator(
            overlay = noDeathRevealOverlay,
            content = noDeathRevealContent,
            sunCore = noDeathSunCore,
            dp = ::dp,
            onFinished = ::finishNoDeathReveal
        )
        payadorRevealOverlay = findViewById(R.id.payadorRevealOverlay)
        payadorRevealPanel = findViewById(R.id.payadorRevealPanel)
        payadorRevealFirstPlayer = findViewById(R.id.payadorRevealFirstPlayer)
        payadorRevealSecondPlayer = findViewById(R.id.payadorRevealSecondPlayer)
        payadorRevealProgress = findViewById(R.id.payadorRevealProgress)
        payadorRevealOverlay.setOnClickListener { /* La revelacion se cierra sola. */ }
        oracleRevealOverlay = findViewById(R.id.oracleRevealOverlay)
        oracleRevealPanel = findViewById(R.id.oracleRevealPanel)
        oracleRevealPlayer = findViewById(R.id.oracleRevealPlayer)
        oracleRevealProgress = findViewById(R.id.oracleRevealProgress)
        oracleRevealOverlay.setOnClickListener { /* La revelacion se cierra sola. */ }
        voteResultOverlay = findViewById(R.id.voteResultOverlay)
        voteResultPanel = findViewById(R.id.voteResultPanel)
        voteResultCards = findViewById(R.id.voteResultCards)
        voteResultTitle = findViewById(R.id.voteResultTitle)
        voteResultSubtitle = findViewById(R.id.voteResultSubtitle)
        voteResultNotice = findViewById(R.id.voteResultNotice)
        btnContinueVoteResult = findViewById(R.id.btnContinueVoteResult)
        voteKickBoot = findViewById(R.id.voteKickBoot)
        voteKickDust = findViewById(R.id.voteKickDust)
        voteResultAnimator = VoteResultAnimator(
            context = this,
            handler = autoAdvanceHandler,
            overlay = voteResultOverlay,
            panel = voteResultPanel,
            cards = voteResultCards,
            title = voteResultTitle,
            subtitle = voteResultSubtitle,
            notice = voteResultNotice,
            continueButton = btnContinueVoteResult,
            boot = voteKickBoot,
            dust = voteKickDust,
            roleImageFor = ::roleImageFor,
            dp = ::dp,
            onImpact = { GameplayAudioDirector.play(this, GameSound.EXPULSION) },
            onContinueReady = ::scheduleVoteResultAutoContinue
        )
        btnContinueVoteResult.setOnClickListener { handleVoteResultContinue() }
        tieVoteOverlay = findViewById(R.id.tieVoteOverlay)
        tieVotePanel = findViewById(R.id.tieVotePanel)
        applyRevealOverlayTheme()
        tieVoteCardsScroll = findViewById(R.id.tieVoteCardsScroll)
        tieVoteCards = findViewById(R.id.tieVoteCards)
        tieVoteCountdown = findViewById(R.id.tieVoteCountdown)
        tieVoteSubtitle = findViewById(R.id.tieVoteSubtitle)
        tieVoteNotice = findViewById(R.id.tieVoteNotice)
        btnTieVoteChat = findViewById(R.id.btnTieVoteChat)
        btnTieRevealMayor = findViewById(R.id.btnTieRevealMayor)
        btnConfirmTieVote = findViewById(R.id.btnConfirmTieVote)
        chatController = GameplayChatController(this, gameplayRoot)
        chatController.onCreate(savedInstanceState)
        btnTieVoteChat.setOnClickListener {
            if (!GameEngine.canHumanChat(session)) return@setOnClickListener
            hideTieVoteWindow(clearSelection = false)
            chatController.openFromTieVote()
        }
        btnTieRevealMayor.setOnClickListener { revealMayorFromTieVote() }
        btnConfirmTieVote.setOnClickListener { confirmTieVoteSelection() }
        traitorRevealCardsScroll = findViewById(R.id.traitorRevealCardsScroll)
        traitorRevealCards = findViewById(R.id.traitorRevealCards)
        traitorRevealContent = findViewById(R.id.traitorRevealContent)
        traitorRevealOverlay = findViewById(R.id.traitorRevealOverlay)
        traitorRevealAnimator = TraitorRevealAnimator(
            overlay = traitorRevealOverlay,
            content = traitorRevealContent,
            cards = traitorRevealCards,
            handler = autoAdvanceHandler
        )
        applyTraitorRevealOverlayTheme()
        btnContinueJesterVictory = findViewById(R.id.btnContinueJesterVictory)
        btnReturnJesterVictory = findViewById(R.id.btnReturnJesterVictory)
        jesterVictoryActions = findViewById(R.id.jesterVictoryActions)
        jesterConfettiLayer = findViewById(R.id.jesterConfettiLayer)
        jesterHornLeft = findViewById(R.id.jesterHornLeft)
        jesterHornRight = findViewById(R.id.jesterHornRight)
        jesterVictoryImage = findViewById(R.id.jesterVictoryImage)
        jesterVictoryMessage = findViewById(R.id.jesterVictoryMessage)
        jesterVictoryOverlay = findViewById(R.id.jesterVictoryOverlay)
        jesterVictoryPanel = findViewById(R.id.jesterVictoryPanel)
        jesterVictoryPlayer = findViewById(R.id.jesterVictoryPlayer)
        jesterVictoryPanel.layoutParams =
            (jesterVictoryPanel.layoutParams as FrameLayout.LayoutParams).apply {
                width = minOf(resources.displayMetrics.widthPixels - dp(28), dp(470))
            }
        jesterVictoryAnimator = JesterVictoryAnimator(
            overlay = jesterVictoryOverlay,
            panel = jesterVictoryPanel,
            hornLeft = jesterHornLeft,
            hornRight = jesterHornRight,
            confettiLayer = jesterConfettiLayer,
            actionsView = jesterVictoryActions
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
        winnerSummaryPanel = findViewById(R.id.winnerSummaryPanel)
        winnerSummaryStatsRow = findViewById(R.id.winnerSummaryStatsRow)
        winnerSummaryDuration = findViewById(R.id.winnerSummaryDuration)
        winnerSummaryHighlight = findViewById(R.id.winnerSummaryHighlight)
        winnerSummaryPlayers = findViewById(R.id.winnerSummaryPlayers)
        winnerSummaryRounds = findViewById(R.id.winnerSummaryRounds)
        winnerSummaryTimeline = findViewById(R.id.winnerSummaryTimeline)
        btnWinnerReturnLobby = findViewById(R.id.btnWinnerReturnLobby)
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
            AccessibilityOptionsDialog.show(this) {
                applyGameplayTextScale()
                renderGame()
            }
        }
        btnAction.setOnClickListener { handleCurrentPhase() }
        btnRevealMayorSecondary.setOnClickListener { revealMayorFromSecondaryAction() }
        btnReadyToVote.setOnClickListener { toggleReadyToVote() }
        btnRevealCard.setOnClickListener { toggleHumanCard() }
        btnToggleEmotes.setOnClickListener { toggleReactionPalette() }
        btnToggleEventLog.setOnClickListener { toggleEventLog() }
        eventLogHeader.setOnClickListener { toggleEventLog() }
        roleCard.setOnClickListener {
            if (
                session.phase != GamePhase.REPARTO &&
                !isCardRevealed &&
                !isHumanCardPubliclyRevealed()
            ) {
                toggleHumanCard()
            } else {
                showRolePreview()
            }
        }
        currentPlayerName.setOnClickListener {
            showMiniPlayerProfile(GameEngine.humanPlayer(session))
        }
        currentPlayerName.isFocusable = true
        currentPlayerName.contentDescription = "Abrir mi perfil"
        rolePreviewContent.setOnClickListener { }
        rolePreviewOverlay.setOnClickListener { closeRolePreview() }
        deathRevealOverlay.setOnClickListener { continueDeathReveal() }
        btnContinueDeathReveal.setOnClickListener { continueDeathReveal() }
        privateFeedbackOverlay.setOnClickListener { dismissCurrentFeedback() }
        btnContinuePrivateFeedback.setOnClickListener { dismissCurrentFeedback() }
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
        btnReturnJesterVictory.setOnClickListener { returnToLobbyFromJesterVictory() }
        btnWinnerReturnLobby.setOnClickListener { handleWinnerReturnButton() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleGameplayBack()
            }
        })

        eventLogBackground.setImageResource(logDrawableFor(themeKey))
        if (shouldPresentRolePreview) {
            isRolePreviewOpen = true
            rolePreviewAnimator.reserveVisible()
        }
        renderGame()
        startAuthoritativeOnlineStateListener()
        gameplayRoot.post {
            renderPlayerColumns()
            if (shouldPresentRolePreview) {
                isRolePreviewOpen = false
                showRolePreview(
                    initialReveal = shouldShowInitialRoleReveal || shouldRestoreInitialRoleReading
                )
            } else {
                resumeGameFlowAfterBlockingUi()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        chatController.onRealtimeAccessUnavailable()
        startRealtimeGameplayPresence()
        markOnlineGameplayPresence(PLAYER_STATE_CONNECTED)
        startOnlinePlayersPresenceListener()
        startOnlineActionsListener()
        startOnlinePrivateClueListener()
        startOnlineSyncWatchdog()
    }

    override fun onStop() {
        stopOnlineSyncWatchdog()
        chatController.onRealtimeAccessUnavailable()
        // La Activity detenida no puede avanzar contadores. Marcar la desconexion permite
        // que otro cliente asuma como anfitrion y evita congelar toda la mesa.
        realtimePresence?.stop(
            markDisconnected = OnlineLobbyRules.shouldMarkGameplayDisconnected(
                isOnlineGameplay = isOnlineGameplay(),
                isChangingConfigurations = isChangingConfigurations,
                returningToLobby = returningToOnlineLobby
            )
        )
        realtimePresence = null
        realtimeTableSilence?.stop()
        realtimeTableSilence = null
        super.onStop()
    }

    override fun onDestroy() {
        localPhaseResolutionToken += 1
        localPhaseResolutionInProgress = false
        localPhaseExecutor.shutdownNow()
        // Solo aca y no en onPause: si la partida se queda sin anfitrion mientras el celular
        // esta en segundo plano, el reintento tiene que seguir vivo o la mesa no destraba.
        autoAdvanceHandler.removeCallbacks(guestHostWindowRunnable)
        autoAdvanceHandler.removeCallbacks(onlineHostPromotionRetryRunnable)
        autoAdvanceHandler.removeCallbacks(roleReadingTickRunnable)
        cancelReadyVoteBotCascade()
        autoAdvanceHandler.removeCallbacks(clearPhaseAdviceRunnable)
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        cancelNoDeathReveal(resumeMusic = false)
        hidePayadorReveal()
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = false)
        cancelVoteResult()
        hideTieVoteWindow(clearSelection = false)
        settleWinnerReveal()
        cancelActionPulse()
        dismissReactionPalette()
        clearReactionBubbles()
        pendingOnlineReactions.clear()
        hideCentralPublicEventBanner(immediate = true)
        cancelFeedbackPresentation(keepPending = false)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(winnerAutoReturnRunnable)
        autoAdvanceHandler.removeCallbacks(nightSkipEnableRunnable)
        nightSkipEnableScheduled = false
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        autoAdvanceHandler.removeCallbacks(onlinePresentationGateRunnable)
        autoAdvanceHandler.removeCallbacks(onlineNightGateRunnable)
        autoAdvanceHandler.removeCallbacks(centralPublicEventDismissRunnable)
        autoAdvanceHandler.removeCallbacks(botReactionRunnable)
        botReactionScheduled = false
        autoAdvanceHandler.removeCallbacks(onlineStartupTickRunnable)
        autoAdvanceHandler.removeCallbacks(onlineSyncWatchdogRunnable)
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        onlineStateListener?.remove()
        onlineStateListener = null
        onlinePlayersListener?.remove()
        onlinePlayersListener = null
        onlineActionsListener?.remove()
        onlineActionsListener = null
        chatController.onDestroy()
        GameNotice.dismissAll(this)
        PlayerProfileDialog.dismissAll(this)
        GameDialog.dismissAll(this)
        if (isFinishing) {
            MusicManager.stopVictoryMusic()
        } else {
            MusicManager.pauseVictoryMusic()
        }
        super.onDestroy()
    }

    override fun onPause() {
        gameplayResumed = false
        restoreRolePreviewOnResume = isRolePreviewOpen
        restoreInitialRoleReadingOnResume = initialRoleReadingActive
        if (initialRoleReadingActive) {
            restoredRoleReadingRemainingMs = roleReadingRemainingMs()
        }
        pauseCountdown()
        cancelReadyVoteBotCascade()
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        cancelNoDeathReveal(resumeMusic = false)
        hidePayadorReveal()
        hideOracleReveal()
        cancelTraitorReveal()
        cancelJesterVictory(requeue = true)
        cancelVoteResult()
        hideTieVoteWindow(clearSelection = false)
        settleWinnerReveal()
        cancelActionPulse()
        dismissReactionPalette()
        clearReactionBubbles()
        hideCentralPublicEventBanner(immediate = true)
        cancelFeedbackPresentation(keepPending = true)
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(winnerAutoReturnRunnable)
        autoAdvanceHandler.removeCallbacks(nightSkipEnableRunnable)
        nightSkipEnableScheduled = false
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        autoAdvanceHandler.removeCallbacks(onlinePresentationGateRunnable)
        autoAdvanceHandler.removeCallbacks(centralPublicEventDismissRunnable)
        autoAdvanceHandler.removeCallbacks(botReactionRunnable)
        botReactionScheduled = false
        chatController.cancelPendingBotChat()
        MusicManager.pauseVictoryMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameplayResumed = true
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
            playVictoryMusicWithAutoReturn()
            scheduleWinnerAutoReturn()
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
            dismissSecondaryUiForPriorityWindow()
            dismissActionFeedbackBannerNow()
            hideCentralPublicEventBanner(immediate = true)
            isVoteResultVisible = true
            voteResultAnimator.show(session)
            voteResultAnimator.showNoExpulsion()
            return
        }
        if (
            ::session.isInitialized &&
            !isDayNightTransitionRunning &&
            !isDeathRevealRunning &&
            !isSilenceRevealRunning &&
            !isNoDeathRevealRunning
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
        outState.putLong(STATE_ONLINE_LOBBY_RETURN_EPOCH_MS, onlineLobbyReturnEpochMs)
        outState.putString(STATE_PAYADOR_REVEAL_KEY, lastPresentedPayadorRevealKey)
        outState.putString(STATE_ORACLE_REVEAL_KEY, lastPresentedOracleRevealKey)
        outState.putInt(
            STATE_PRESENTED_SPECIAL_VICTORY_COUNT,
            (presentedSpecialVictoryCount - if (isJesterVictoryVisible) 1 else 0)
                .coerceAtLeast(0)
        )
        outState.putInt(STATE_LAST_NO_DEATH_REVEAL_ROUND, lastNoDeathRevealRound)
        chatController.onSaveInstanceState(outState)
        outState.putBoolean(STATE_EVENT_LOG_EXPANDED, isEventLogExpanded)
        outState.putString(STATE_ONLINE_PARTIDA_ID, onlinePartidaId)
        outState.putString(STATE_ONLINE_PLAYER_ID, onlinePlayerId)
        outState.putBoolean(STATE_ONLINE_IS_HOST, onlineIsHost)
        outState.putBoolean(STATE_ONLINE_INITIAL_ROLE_READ, onlineInitialRoleRead)
        outState.putLong(
            STATE_ONLINE_STARTUP_DEADLINE_EPOCH_MS,
            onlineStartupDeadlineEpochMs
        )
        outState.putString(STATE_ONLINE_PRESENTATION_ACK_KEY, onlinePresentationAckKey)
        outState.putString(STATE_ONLINE_WINNER_RETURN_ACK_KEY, onlineWinnerReturnAckKey)
        outState.putBoolean(
            STATE_VOTE_NO_EXPULSION_PRESENTED,
            voteNoExpulsionPresented
        )
        outState.putBoolean(STATE_SPECTATOR_CHOICE_OFFERED, spectatorChoiceOffered)
        outState.putBoolean(
            STATE_ROLE_PREVIEW_OPEN,
            isRolePreviewOpen || restoreRolePreviewOnResume
        )
        outState.putBoolean(
            STATE_INITIAL_ROLE_READING,
            initialRoleReadingActive || restoreInitialRoleReadingOnResume
        )
        outState.putLong(
            STATE_ROLE_READING_REMAINING_MS,
            if (initialRoleReadingActive) {
                roleReadingRemainingMs()
            } else {
                restoredRoleReadingRemainingMs.coerceAtLeast(0L)
            }
        )
        outState.putInt(STATE_READY_VOTE_PHASE_INDEX, readyVotePhaseIndex)
        outState.putStringArrayList(
            STATE_READY_TO_VOTE_PLAYERS,
            ArrayList(readyToVote)
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
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
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
            reactionPalette?.isShowing == true -> dismissReactionPalette()
            chatController.onBackPressed() -> Unit
            actionFeedbackBanner.visibility == View.VISIBLE -> hideActionFeedbackBanner()
            isEventLogExpanded -> toggleEventLog()
            else -> finish()
        }
    }

    private fun handleCurrentPhase() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (localPhaseResolutionInProgress) return
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, "La siguiente fase comienza enseguida.")
            return
        }
        pauseCountdown()
        if (session.winner.isNotBlank()) {
            renderGame()
            return
        }
        if (handleOnlineStartupAction()) {
            return
        }

        val selectedAction = confirmedTargetActionLabel()
        if (selectedAction != null) {
            performTargetAction(selectedTarget)
            return
        }

        if (selectedTarget.isBlank() && canHumanMedicSelfProtect()) {
            performTargetAction(GameEngine.humanPlayer(session).name)
            return
        }

        if (GameEngine.needsInitialDesertorChoice(session) || GameEngine.canDesertorReconsider(session)) {
            showDesertorTeamDialog()
            return
        }

        val human = GameEngine.humanPlayer(session)
        val currentActionSession = actionSession()
        if (
            currentActionSession.phase == GamePhase.NOCHE_ORACULO &&
            human.role?.key == RoleCatalog.ORACULO &&
            canHumanOracleChooseThisNight()
        ) {
            if (isOnlineNightActionWindow()) {
                recordOnlineSkippedNightAction(
                    before = currentActionSession,
                    actionType = "guardar_poder"
                )
                return
            }
            val resolved = GameEngine.skipOraclePower(currentActionSession)
            session = resolved
            renderGame()
            return
        }
        if (requiresHumanInput()) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, targetActionMessage())
            renderGame()
            return
        }

        if (canSkipRemainingNight()) {
            if (!isNightSkipButtonReady(canSkipNight = true)) {
                renderGame()
                return
            }
            skipRemainingNight()
            return
        }

        if (mustWaitForPhaseTimer()) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, "La fase avanza sola cuando termine el tiempo.")
            renderGame()
            return
        }

        if (handleOnlinePresentationContinue()) {
            return
        }

        if (blockOnlineGuestLocalPhaseAdvance("manual_or_auto_advance")) {
            return
        }

        advanceCurrentPhase()
    }

    private fun isUnrevealedHumanMayor(): Boolean {
        val human = GameEngine.humanPlayer(session)
        return human.alive &&
            human.role?.key == RoleCatalog.ALCALDE &&
            !session.alcaldeRevealed
    }

    private fun canOfferMayorReveal(): Boolean {
        return isUnrevealedHumanMayor() &&
            selectedTarget.isBlank() &&
            (
                session.phase == GamePhase.DIA_DEBATE ||
                    session.phase == GamePhase.VOTACION ||
                    session.phase == GamePhase.ALCALDE_DESEMPATE
            )
    }

    private fun revealMayorFromSecondaryAction() {
        if (!canOfferMayorReveal()) return
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, "Espera a que comience la fase.")
            return
        }
        if (isOnlineGameplay()) {
            recordOnlineMayorReveal()
            return
        }
        val before = session
        session = GameEngine.revealAlcalde(session)
        if (before == session) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        val feedback = GameplayTableUi.feedbackForMayorReveal(before, session)
        renderGame()
        feedback?.let { showActionFeedbackBanner(it) }
    }

    /**
     * Online el invitado no cambia el estado por su cuenta: registra la revelacion como
     * accion y el anfitrion activo la aplica y la publica para toda la mesa.
     */
    private fun recordOnlineMayorReveal() {
        val human = GameEngine.humanPlayer(session)
        if (human.role?.key != RoleCatalog.ALCALDE || !human.alive) return
        if (session.alcaldeRevealed || onlineMayorRevealSent) return
        onlineMayorRevealSent = true
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        session = session.copy(
            privateHint = "Te revelaste como Alcalde. Tu voto vale doble y decides los empates."
        )
        recordOnlineAction(
            type = "accion_jugador",
            targetName = "",
            details = mapOf("accion" to ONLINE_ACTION_MAYOR_REVEAL),
            onFailure = { onlineMayorRevealSent = false }
        )
        renderGame()
    }

    /**
     * Solo el anfitrion activo. Aplica la revelacion pedida por el alcalde (sea el propio
     * anfitrion o un invitado) y la publica; el resto de la mesa la recibe por
     * `alcaldeRevealed` y el anuncio publico del estado autoritativo.
     */
    private fun maybeApplyOnlineMayorReveal() {
        if (!isOnlineGameplay() || !onlineIsHost || !::session.isInitialized) return
        if (session.alcaldeRevealed || session.winner.isNotBlank()) return
        val mayor = GameEngine.alivePlayers(session)
            .firstOrNull { it.role?.key == RoleCatalog.ALCALDE }
            ?: return
        val asked = onlineNightActionRecords.any {
            it.matchId == session.onlineMatchId &&
                it.action == ONLINE_ACTION_MAYOR_REVEAL &&
                it.actorName == mayor.name
        }
        if (!asked) return
        val before = session
        val hostIsMayor = before.players.firstOrNull { it.isHuman }?.name == mayor.name
        // `revealAlcalde` opera sobre el jugador humano; en el celular del anfitrion el
        // alcalde puede ser un invitado, asi que se marca temporalmente y se restaura.
        val asMayor = before.copy(
            players = before.players.map { it.copy(isHuman = it.name == mayor.name) }
        )
        val revealed = GameEngine.revealAlcalde(asMayor)
        if (revealed == asMayor) return
        session = revealed.copy(
            players = revealed.players.map { player ->
                player.copy(
                    isHuman = before.players.firstOrNull { it.name == player.name }?.isHuman == true
                )
            },
            // La pista privada del alcalde no debe aparecer en el celular del anfitrion.
            privateHint = if (hostIsMayor) revealed.privateHint else before.privateHint
        )
        OnlineDebugLog.i(
            "alcalde_reveal_applied roomId=$onlinePartidaId mayor=${mayor.name} round=${session.round} phase=${session.phase.name}"
        )
        publishAuthoritativeOnlineState()
        renderGame()
    }

    private fun setOnlineAwaitingHostAdvance(
        waiting: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ) {
        if (waiting && !onlineAwaitingHostAdvance) {
            onlineAwaitingHostSinceMs = nowElapsedMs
            onlineSyncDelayReported = false
        } else if (!waiting) {
            onlineAwaitingHostSinceMs = 0L
            onlineSyncDelayReported = false
        }
        onlineAwaitingHostAdvance = waiting
    }

    private fun blockOnlineGuestLocalPhaseAdvance(reason: String): Boolean {
        if (OnlinePhaseGate.canAdvanceLocally(isOnlineGameplay(), onlineIsHost)) return false
        setOnlineAwaitingHostAdvance(true)
        lastPublishedOnlineStateKey = ""
        OnlineDebugLog.i(
            "phase_client_syncing roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason phase=${session.phase.name} phaseIndex=${session.phaseIndex}"
        )
        GameNotice.show(this, immersiveOnlineWaitingHint())
        renderGame()
        return true
    }

    private fun handleOnlinePresentationContinue(): Boolean {
        if (!isOnlineGameplay()) return false
        refreshOnlinePresentationGate()
        val key = currentOnlinePresentationKey() ?: return false
        val elapsedMs = onlinePresentationElapsedMs()
        if (!OnlinePresentationGate.canAcknowledge(elapsedMs)) return true
        if (onlinePresentationAckKey != key) {
            onlinePresentationAckKey = key
            onlinePresentationClientAcks = onlinePresentationClientAcks + (onlinePlayerId to key)
            lastPublishedOnlineStateKey = ""
            publishOnlineClientState()
            GameplayEffects.play(this, GameplayEffect.CONFIRM)
            OnlineDebugLog.i(
                "presentation_ack roomId=$onlinePartidaId uid=$onlinePlayerId key=$key phase=${session.phase.name}"
            )
        }
        refreshOnlinePresentationGate()
        return true
    }

    private fun refreshOnlinePresentationGate() {
        if (!isOnlineGameplay() || !::session.isInitialized) return
        val key = currentOnlinePresentationKey()
        if (key == null) {
            clearOnlinePresentationGate()
            return
        }
        if (onlinePresentationKey != key) {
            onlinePresentationKey = key
            onlinePresentationStartedAtMs = SystemClock.elapsedRealtime()
            onlinePresentationAdvanceInProgress = false
            autoAdvanceHandler.removeCallbacks(onlinePresentationGateRunnable)
            OnlineDebugLog.i(
                "presentation_gate_start roomId=$onlinePartidaId uid=$onlinePlayerId key=$key host=$onlineIsHost"
            )
        }
        updateOnlinePresentationControls(key)
        autoAdvanceHandler.removeCallbacks(onlinePresentationGateRunnable)
        autoAdvanceHandler.postDelayed(onlinePresentationGateRunnable, PRESENTATION_GATE_TICK_MS)
    }

    private fun tickOnlinePresentationGate() {
        if (!isOnlineGameplay() || !::session.isInitialized) return
        val key = currentOnlinePresentationKey()
        if (key == null || key != onlinePresentationKey) {
            refreshOnlinePresentationGate()
            return
        }
        val progress = onlinePresentationProgress(key)
        val elapsedMs = onlinePresentationElapsedMs()
        updateOnlinePresentationControls(key, progress)
        if (
            !onlinePresentationAdvanceInProgress &&
            OnlinePresentationGate.shouldAdvance(
                isCoordinator = onlineIsHost,
                elapsedMs = elapsedMs,
                progress = progress,
                coordinatorPresentationReady = currentOnlinePresentationLocallyComplete()
            )
        ) {
            onlinePresentationAdvanceInProgress = true
            OnlineDebugLog.i(
                "presentation_gate_advance roomId=$onlinePartidaId uid=$onlinePlayerId key=$key ready=${progress.ready}/${progress.total} elapsedMs=$elapsedMs"
            )
            if (isVoteResultVisible) {
                continueVoteResultAuthoritatively()
            } else {
                advanceCurrentPhase()
            }
            return
        }
        autoAdvanceHandler.postDelayed(onlinePresentationGateRunnable, PRESENTATION_GATE_TICK_MS)
    }

    private fun currentOnlinePresentationLocallyComplete(): Boolean {
        if (!isVoteResultVisible) return true
        return !onlineVotePresentation.startsWith("expulsion|") || voteExpulsionComplete
    }

    private fun currentOnlinePresentationKey(): String? {
        if (!isOnlineGameplay() || session.winner.isNotBlank()) return null
        if (isVoteResultVisible) {
            return listOf(
                "votacion",
                session.phase.name,
                session.round,
                session.voteRound,
                session.phaseIndex,
                session.publicHistory.size,
                onlineVotePresentation,
                session.dayEliminationTarget
            ).joinToString("|")
        }
        if (
            isBlockingGameplayUiActive() ||
            hasPendingDawnRevealSequence() ||
            feedbackState.pending?.blocksGameplay == true
        ) {
            return null
        }
        return when (session.phase) {
            GamePhase.AMANECER -> "amanecer|${session.round}|${session.phaseIndex}"
            GamePhase.RESULTADO ->
                "resultado|${session.round}|${session.voteRound}|${session.phaseIndex}|${session.dayEliminationTarget}"
            else -> null
        }
    }

    private fun onlinePresentationProgress(key: String): OnlinePresentationProgress {
        val participants = onlinePresencePlayers.map { player ->
            OnlinePresentationParticipant(
                uid = player.id,
                connected = player.state == PLAYER_STATE_CONNECTED,
                alive = session.players.getOrNull(player.order)?.alive == true,
                acknowledgedKey = onlinePresentationClientAcks[player.id].orEmpty()
            )
        }
        return OnlinePresentationGate.progress(key, participants)
    }

    private fun updateOnlinePresentationControls(
        key: String,
        progress: OnlinePresentationProgress = onlinePresentationProgress(key)
    ) {
        val elapsedMs = onlinePresentationElapsedMs()
        val canAcknowledge = OnlinePresentationGate.canAcknowledge(elapsedMs)
        val acknowledged = onlinePresentationAckKey == key
        val label = if (acknowledged) {
            "LISTOS ${progress.ready}/${progress.total}"
        } else {
            "CONTINUAR · LISTOS ${progress.ready}/${progress.total}"
        }
        if (isVoteResultVisible && ::btnContinueVoteResult.isInitialized) {
            btnContinueVoteResult.text = label
            btnContinueVoteResult.isEnabled = canAcknowledge &&
                !acknowledged &&
                !onlinePresentationAdvanceInProgress
            btnContinueVoteResult.alpha = if (btnContinueVoteResult.isEnabled) 1f else 0.62f
        } else if (::btnAction.isInitialized) {
            btnAction.text = label
            btnAction.isEnabled = canAcknowledge &&
                GameEngine.humanPlayer(session).alive &&
                !acknowledged &&
                !onlinePresentationAdvanceInProgress
            btnAction.alpha = if (btnAction.isEnabled) 1f else 0.62f
        }
    }

    private fun onlinePresentationElapsedMs(): Long {
        if (onlinePresentationStartedAtMs == 0L) return 0L
        return SystemClock.elapsedRealtime() - onlinePresentationStartedAtMs
    }

    private fun clearOnlinePresentationGate() {
        autoAdvanceHandler.removeCallbacks(onlinePresentationGateRunnable)
        onlinePresentationKey = ""
        onlinePresentationStartedAtMs = 0L
        onlinePresentationAdvanceInProgress = false
    }

    private fun advanceCurrentPhase() {
        chatController.cancelPendingBotChat()
        val before = session
        if (!isOnlineGameplay() && shouldResolveLocalPhaseOffMainThread(before.phase)) {
            resolveLocalPhaseOffMainThread(
                before = before,
                operation = "advance",
                progressMessage = localPhaseProgressMessage(before.phase),
                resolver = ::advanceSessionForCurrentPhase
            ) { resolved ->
                session = resolved
                recordOnlinePhaseAdvance(before, resolved)
                chatController.onPhaseSettled()
                clearSelection()
                renderGame()
            }
            return
        }
        session = advanceSessionForCurrentPhase(before)
        recordOnlinePhaseAdvance(before, session)
        chatController.onPhaseSettled()
        clearSelection()
        renderGame()
    }

    private fun advanceSessionForCurrentPhase(source: GameSession): GameSession {
        return when (source.phase) {
            GamePhase.REPARTO -> GameEngine.startNight(source)
            GamePhase.NOCHE_ASESINO -> GameEngine.resolveAssassin(source, "")
            GamePhase.NOCHE_MERCENARIO -> GameEngine.resolveMercenary(source, "")
            GamePhase.NOCHE_POLICIA -> GameEngine.resolvePolice(source, "")
            GamePhase.NOCHE_MEDICO -> GameEngine.resolveMedic(source, "")
            GamePhase.NOCHE_ORACULO -> GameEngine.resolveOracle(source, "")
            GamePhase.AMANECER -> GameEngine.resolveDawn(source)
            GamePhase.DIA_DEBATE -> if (isOnlineGameplay()) {
                GameEngine.resolveDayDebateWithoutOptionalBotActions(source)
            } else {
                GameEngine.resolveDayDebate(source)
            }
            GamePhase.CONTRAPUNTO -> if (isOnlineGameplay()) {
                GameEngine.resolveContrapuntoTimeout(source)
            } else {
                GameEngine.resolveContrapunto(source, "")
            }
            GamePhase.VOTACION -> GameEngine.resolveVoting(source, "")
            GamePhase.RECUENTO_VOTOS -> source
            GamePhase.DESEMPATE_VOTACION -> GameEngine.resolveTieVoting(source, "")
            GamePhase.ALCALDE_DESEMPATE -> source
            GamePhase.RESULTADO -> GameEngine.resolveResult(source)
        }
    }

    private fun shouldResolveLocalPhaseOffMainThread(phase: GamePhase): Boolean {
        return phase != GamePhase.RECUENTO_VOTOS && phase != GamePhase.ALCALDE_DESEMPATE
    }

    private fun performTargetAction(targetName: String) {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        chatController.cancelPendingBotChat()
        if (countdown.isTransitionLocked(session.phaseIndex)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, "Espera a que comience la fase.")
            return
        }
        pauseCountdown()
        val actionSession = actionSession()
        if (!canActOnTarget(targetName)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, "No puedes actuar sobre ese jugador.")
            renderGame()
            return
        }

        val before = actionSession
        selectedTarget = targetName
        if (isOnlinePayadorSelectionWindow()) {
            recordOnlinePayadorSelection(before, targetName)
            return
        }
        if (isOnlineVotingActionWindow()) {
            recordOnlineDeferredPlayerAction(
                before = before,
                resolved = previewOnlineVoteAction(before, targetName),
                targetName = targetName
            )
            return
        }
        if (!isOnlineGameplay()) {
            resolveLocalPhaseOffMainThread(
                before = before,
                operation = "target_action",
                progressMessage = localPhaseProgressMessage(before.phase),
                resolver = { source -> GameEngine.resolveHumanTargetAction(source, targetName) }
            ) { resolved ->
                completeResolvedTargetAction(before, resolved, targetName)
            }
            return
        }
        val resolved = GameEngine.resolveHumanTargetAction(actionSession, targetName)
        if (isOnlineDeferredActionWindow()) {
            recordOnlineDeferredPlayerAction(before, resolved, targetName)
            return
        }
        completeResolvedTargetAction(before, resolved, targetName)
    }

    private fun previewOnlineVoteAction(before: GameSession, targetName: String): GameSession {
        // El servidor/host resuelve el recuento real. El cliente solo necesita una vista
        // previa para el feedback; simular aqui los votos de los demas jugadores bloqueaba UI.
        val human = GameEngine.humanPlayer(before)
        val action = GameAction(
            type = GameActionType.VOTE,
            actor = human.name,
            target = targetName,
            round = before.round,
            phase = before.phase,
            publiclyKnown = true
        )
        return before.copy(
            phase = GamePhase.RECUENTO_VOTOS,
            phaseIndex = before.phaseIndex + 1,
            actionHistory = (before.actionHistory + action)
                .takeLast(ONLINE_PREVIEW_ACTION_HISTORY_LIMIT)
        )
    }

    private fun resolveLocalPhaseOffMainThread(
        before: GameSession,
        operation: String,
        progressMessage: String,
        resolver: (GameSession) -> GameSession,
        onResolved: (GameSession) -> Unit
    ) {
        if (localPhaseResolutionInProgress) return
        val token = ++localPhaseResolutionToken
        // El guard de descarte compara contra la sesion REAL al momento del dispatch, NO contra
        // `before`: en acciones de noche `before` es un preview hacia adelante (p. ej. el turno del
        // Oraculo) mientras `session` sigue en NOCHE_ASESINO. Comparar con before descartaba por
        // error toda accion de noche del medico/policia/mercenario/oraculo -> se trababa el poder.
        val expectedPhase = session.phase
        val expectedPhaseIndex = session.phaseIndex
        val startedAtMs = SystemClock.elapsedRealtime()
        localPhaseResolutionInProgress = true
        val isVoteResolution =
            before.phase == GamePhase.VOTACION || before.phase == GamePhase.DESEMPATE_VOTACION
        val logName = if (isVoteResolution) "local_vote_resolution" else "local_phase_resolution"
        localPhaseResolutionActionLabel = if (isVoteResolution) {
            "CONTANDO VOTOS"
        } else {
            "RESOLVIENDO"
        }
        currentPlayerHint.text = progressMessage
        renderAdvanceButton()

        localPhaseExecutor.execute {
            val result = runCatching { resolver(before) }
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            autoAdvanceHandler.post {
                if (token != localPhaseResolutionToken || isFinishing || isDestroyed) {
                    return@post
                }
                localPhaseResolutionInProgress = false
                localPhaseResolutionActionLabel = "RESOLVIENDO"
                val resolved = result.getOrElse { error ->
                    OnlineDebugLog.e(
                        "${logName}_failure operation=$operation phase=${before.phase.name} phaseIndex=${before.phaseIndex}",
                        error
                    )
                    GameNotice.show(
                        activity = this,
                        message = "No se pudo resolver la fase. Intenta nuevamente.",
                        duration = GameNotice.Duration.LONG
                    )
                    readyVoteAdvanceInProgress = false
                    renderGame()
                    return@post
                }
                if (session.phaseIndex != expectedPhaseIndex || session.phase != expectedPhase) {
                    OnlineDebugLog.w(
                        "${logName}_discarded operation=$operation expected=${expectedPhase.name}:$expectedPhaseIndex actual=${session.phase.name}:${session.phaseIndex}"
                    )
                    readyVoteAdvanceInProgress = false
                    renderGame()
                    return@post
                }
                OnlineDebugLog.i(
                    "${logName}_complete operation=$operation phase=${before.phase.name} players=${before.players.size} durationMs=$elapsedMs"
                )
                onResolved(resolved)
            }
        }
    }

    private fun localPhaseProgressMessage(phase: GamePhase): String {
        return when (phase) {
            GamePhase.VOTACION, GamePhase.DESEMPATE_VOTACION ->
                "Contando los votos del pueblo..."
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> "Resolviendo la noche..."
            else -> "Resolviendo la fase..."
        }
    }

    private fun completeResolvedTargetAction(
        before: GameSession,
        resolved: GameSession,
        targetName: String
    ) {
        recordOnlinePlayerAction(before, resolved, targetName)
        playResolvedActionSound(before, resolved)
        val feedback = GameplayTableUi.feedbackForResolvedAction(before, resolved, targetName)
        session = resolved
        chatController.onPhaseSettled()
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

    private fun recordOnlineDeferredPlayerAction(
        before: GameSession,
        resolved: GameSession,
        targetName: String
    ) {
        val actionKey = onlineDeferredActionKey()
        pendingOnlineActionSubmissions.add(actionKey)
        session = session.copy(privateHint = "Enviando acción...")
        val previousTarget = selectedTarget
        clearSelection()
        currentPlayerHint.text = privateHintText()
        renderAdvanceButton()
        refreshPlayerTargetSelection(previousTarget, "")
        // En online el Detective no conoce las cartas ajenas. Su pista debe venir del
        // anfitrion autoritativo; mostrar este preview local convertia cualquier carta oculta
        // (incluido el Mercenario) en un falso "inocente".
        val feedback = if (before.phase == GamePhase.NOCHE_POLICIA) {
            null
        } else {
            GameplayTableUi.feedbackForResolvedAction(before, resolved, targetName)
        }
        val waitingMessage = when {
            before.phase == GamePhase.NOCHE_POLICIA ->
                "Acción confirmada. Consultando los archivos del pueblo..."
            session.phase == GamePhase.VOTACION ||
                session.phase == GamePhase.DESEMPATE_VOTACION ||
                session.phase == GamePhase.ALCALDE_DESEMPATE ->
                "Voto confirmado. La urna sigue abierta..."
            else -> "Acción confirmada. Esperando a los demás jugadores..."
        }
        recordOnlinePlayerAction(
            before = before,
            after = resolved,
            targetName = targetName,
            onSuccess = {
                pendingOnlineActionSubmissions.remove(actionKey)
                submittedOnlineNightActions.add(actionKey)
                playResolvedActionSound(before, resolved)
                session = session.copy(
                    privateHint = waitingMessage,
                    actionHistory = resolved.actionHistory
                )
                clearSelection()
                val feedbackPresentation = feedbackState.submit(feedback)
                blockingFeedbackPeriod = if (
                    feedbackPresentation == GameplayFeedbackState.Presentation.PRIVATE
                ) {
                    GameplayTableUi.transitionSpec(before).period
                } else {
                    null
                }
                renderGame()
                if (
                    feedbackPresentation == GameplayFeedbackState.Presentation.BANNER &&
                    feedback != null
                ) {
                    showActionFeedbackBanner(feedback)
                }
            },
            onFailure = {
                pendingOnlineActionSubmissions.remove(actionKey)
                session = session.copy(
                    privateHint = "No se pudo enviar. Toca nuevamente para reintentar."
                )
                renderGame()
            }
        )
    }

    private fun recordOnlineSkippedNightAction(
        before: GameSession,
        actionType: String
    ) {
        val actionKey = onlineDeferredActionKey()
        pendingOnlineActionSubmissions.add(actionKey)
        session = session.copy(privateHint = "Enviando acción...")
        renderGame()
        recordOnlineAction(
            type = "accion_jugador",
            targetName = "",
            details = mapOf(
                "accion" to actionType,
                "faseResultado" to before.phase.name,
                "phaseIndexResultado" to before.phaseIndex
            ),
            onSuccess = {
                pendingOnlineActionSubmissions.remove(actionKey)
                submittedOnlineNightActions.add(actionKey)
                session = session.copy(
                    privateHint = "Acción confirmada. Guardaste tu poder."
                )
                clearSelection()
                renderGame()
            },
            onFailure = {
                pendingOnlineActionSubmissions.remove(actionKey)
                session = session.copy(
                    privateHint = "No se pudo enviar. Toca nuevamente para reintentar."
                )
                renderGame()
            }
        )
    }

    private fun isOnlinePayadorSelectionWindow(): Boolean {
        if (!isOnlineGameplay() || session.phase != GamePhase.DIA_DEBATE || session.payadorUsed) {
            return false
        }
        val human = GameEngine.humanPlayer(session)
        return human.alive && human.role?.key == RoleCatalog.PAYADOR
    }

    private fun recordOnlinePayadorSelection(before: GameSession, targetName: String) {
        if (
            submittedOnlinePayadorTargets.size + pendingOnlinePayadorTargets.size >= 2 ||
            targetName in submittedOnlinePayadorTargets ||
            targetName in pendingOnlinePayadorTargets
        ) {
            return
        }
        val actionSlot = (submittedOnlinePayadorTargets.size + pendingOnlinePayadorTargets.size + 1)
            .coerceIn(1, 2)
        pendingOnlinePayadorTargets += targetName
        session = session.copy(privateHint = "Enviando participante del Contrapunto...")
        clearSelection()
        renderGame()
        recordOnlineAction(
            type = "accion_jugador",
            targetName = targetName,
            details = mapOf(
                "accion" to "contrapunto",
                "slotAccion" to actionSlot,
                "faseResultado" to before.phase.name,
                "phaseIndexResultado" to before.phaseIndex
            ),
            onSuccess = {
                pendingOnlinePayadorTargets -= targetName
                submittedOnlinePayadorTargets += targetName
                session = session.copy(
                    privateHint = if (submittedOnlinePayadorTargets.size < 2) {
                        "Acción confirmada. Elegiste a $targetName; falta un participante."
                    } else {
                        "Acción confirmada. El desafío fue lanzado."
                    }
                )
                clearSelection()
                renderGame()
            },
            onFailure = {
                pendingOnlinePayadorTargets -= targetName
                session = session.copy(
                    privateHint = "No se pudo enviar. Toca nuevamente para reintentar."
                )
                renderGame()
            }
        )
    }

    private fun renderGame() {
        val renderStartedAtMs = SystemClock.elapsedRealtime()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        val enteringInteractivePhase =
            lastRenderedPhase != session.phase && requiresHumanInput()
        if (enteringInteractivePhase) {
            dismissSecondaryUiForPriorityWindow()
        }
        syncReactionRound()
        if (
            selectedTarget.isNotBlank() &&
            !canActOnTarget(selectedTarget)
        ) {
            clearSelection()
        }
        val newlyDeadPlayers = collectNewlyDeadPlayers()
        collectNoDeathEvent()
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
        val publicMessage = if (session.winner == GameRules.CANCELLED_WINNER) {
            "Partida cancelada por inactividad. No hubo ganador."
        } else if (session.winner.isNotBlank()) {
            "Fin de partida. Gano ${session.winner}."
        } else {
            session.publicAnnouncement.ifBlank { phaseText.subtitle }
        }
        val narratorMessage = currentNarratorMessage(phaseText)
        refreshPhaseAdvice(narratorMessage)
        val eventChanged =
            lastRenderedPhase != session.phase || lastRenderedAnnouncement != narratorMessage
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
        if (isOnlineStartupPhase()) {
            currentPlayerHint.text = onlineStartupHintText()
        } else if (onlineAwaitingHostAdvance) {
            currentPlayerHint.text = immersiveOnlineWaitingHint()
        }
        renderAdvanceButton()
        renderReadyToVoteButton()
        renderHumanCardIfVisible()
        renderPlayerColumns(newlyDeadPlayers.map { it.name }.toSet())
        handleOnlineHostHandoff(onlinePresencePlayers)
        renderReactionButton()
        scheduleBotReactionIfNeeded()
        chatController.onSessionUpdated()
        if (!isOnlineGameplay()) {
            AchievementTracker.recordMatchIfNeeded(this, session)
            MatchHistoryStore.record(this, session)
        }
        lastRenderedPhase = session.phase
        lastRenderedAnnouncement = narratorMessage
        publishOnlineClientState()
        publishAuthoritativeOnlineState()
        if (!specialVictoryPending && maybeOfferSpectatorChoice()) {
            logSlowGameplayRender(renderStartedAtMs)
            return
        }
        if (blockingFeedbackPending) {
            showPendingPrivateFeedback()
        } else if (shouldStartTransition) {
            startDayNightTransition(transitionSpec)
        } else if (!isDayNightTransitionRunning) {
            resumeGameFlowAfterBlockingUi()
        }
        logSlowGameplayRender(renderStartedAtMs)
    }

    private fun publishOnlineClientState() {
        if (onlinePartidaId.isBlank() || onlinePlayerId.isBlank()) return
        val stateKey = listOf(
            OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
            session.phase.name,
            session.round,
            session.phaseIndex,
            session.players.size,
            onlineInitialRoleRead,
            onlineAwaitingHostAdvance,
            onlinePresentationAckKey,
            onlineWinnerReturnAckKey,
            lastAppliedAuthoritativePhaseLabel,
            session.publicAnnouncement,
            session.winner
        ).joinToString("|")
        if (stateKey == lastPublishedOnlineStateKey) return
        lastPublishedOnlineStateKey = stateKey

        val human = GameEngine.humanPlayer(session)
        OnlineDebugLog.i(
            "client_state_publish_requested roomId=$onlinePartidaId uid=$onlinePlayerId player=${human.name} phase=${session.phase.name} round=${session.round} visiblePlayers=${session.players.size}/${expectedOnlineStartupPlayers()} roleRead=$onlineInitialRoleRead"
        )
        val humanOrder = session.players.indexOfFirst { it.isHuman }.coerceAtLeast(0)
        val startupState = when {
            !isOnlineStartupPhase() -> OnlineStartupGate.STARTUP_PHASE_IN_MATCH
            session.players.size < expectedOnlineStartupPlayers() -> OnlineStartupGate.STARTUP_PHASE_SYNCING
            onlineInitialRoleRead -> OnlineStartupGate.STARTUP_PHASE_READY
            else -> OnlineStartupGate.STARTUP_PHASE_READING
        }
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .update(
                mapOf(
                    "estadoClientes.$onlinePlayerId" to mapOf(
                        "fase" to session.phase.name,
                        "ronda" to session.round,
                        "phaseIndex" to session.phaseIndex,
                        "enGameplay" to true,
                        "jugadoresVistos" to session.players.size,
                        "jugadoresEsperados" to expectedOnlineStartupPlayers(),
                        "uidTemporal" to onlinePlayerId,
                        "orden" to humanOrder,
                        "rolLeido" to onlineInitialRoleRead,
                        "estadoArranque" to startupState,
                        "aplicoEstadoPartida" to authoritativeStateAppliedLocally(),
                        "sincronizando" to onlineAwaitingHostAdvance,
                        FIELD_PRESENTATION_ACK_KEY to onlinePresentationAckKey,
                        FIELD_WINNER_RETURN_ACK_KEY to onlineWinnerReturnAckKey,
                        "ultimaFaseAplicadaEnLocal" to latestAppliedPhaseLabel(),
                        "anuncioPublico" to session.publicAnnouncement,
                        "ganador" to session.winner,
                        "actualizadaEnLocal" to System.currentTimeMillis()
                    ),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                if (lastPublishedOnlineStateKey == stateKey) {
                    lastPublishedOnlineStateKey = ""
                }
                OnlineDebugLog.e(
                    "client_state_publish_failure roomId=$onlinePartidaId uid=$onlinePlayerId phase=${session.phase.name} round=${session.round}",
                    error
                )
            }
    }

    private fun publishAuthoritativeOnlineState() {
        if (onlinePartidaId.isBlank() || onlinePlayerId.isBlank()) return
        if (
            !OnlinePhaseGate.canPublishAuthoritativeState(
                isOnline = isOnlineGameplay(),
                isHost = onlineIsHost,
                isStartupPhase = isOnlineStartupPhase()
            )
        ) {
            return
        }
        if (session.winner.isNotBlank()) {
            onlineLobbyReturnEpochMs = OnlineMatchReturnGate.initialDeadline(
                onlineLobbyReturnEpochMs,
                System.currentTimeMillis()
            )
        }
        if (session.phase == GamePhase.NOCHE_ASESINO) {
            // Abre la seccion de la noche incluso antes del primer voto. El historial no se
            // borra: el nuevo separador queda al final y las noches anteriores permanecen arriba.
            publishTraitorPlanNotices()
        }
        ensureOnlinePhaseDeadlineForHost()
        val stateKey = listOf(
            OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
            session.phase.name,
            session.round,
            session.phaseIndex,
            session.onlinePhaseDeadlineEpochMs,
            session.onlinePhaseDeadlinePhaseIndex,
            session.publicAnnouncement,
            session.publicHistory.joinToString("#"),
            session.winner,
            onlineLobbyReturnEpochMs,
            session.nightKillTarget,
            session.nightSilenceTarget,
            session.dayEliminationTarget,
            session.nightHadNoVictim,
            onlineVotePresentation,
            session.votes.entries.sortedBy { it.key }.joinToString("#") { "${it.key}:${it.value}" },
            session.voteRound,
            session.tieVoteCandidates.joinToString("#"),
            session.alcaldeTieCandidates.joinToString("#"),
            session.alcaldeRevealed,
            session.desertorTeam,
            session.desertorChangedTeam,
            session.payadorUsed,
            session.contrapuntoPlayers.joinToString("#"),
            session.contrapuntoSuspicion,
            session.oracleUsed,
            session.oracleInvitedPlayer,
            session.specialVictories.joinToString("#") {
                "${it.key}:${it.playerName}:${it.roleKey}:${it.round}"
            },
            session.players.joinToString("#") {
                "${it.name}:${it.alive}:${it.muted}:${it.lastSilencedRound}:" +
                    "${it.consecutiveNightAfk}:${it.consecutiveVoteAfk}:${it.deathCause.name}"
            }
        ).joinToString("|")
        if (stateKey == lastPublishedAuthoritativeOnlineStateKey) return
        lastPublishedAuthoritativeOnlineStateKey = stateKey
        OnlineDiagnostics.recordPhase(session, onlineIsHost, event = "host_publish")

        val roomUpdate = mutableMapOf<String, Any>(
            "estadoPartida" to mapOf(
                "versionEstado" to OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
                "fase" to session.phase.name,
                "ronda" to session.round,
                "phaseIndex" to session.phaseIndex,
                "limiteFaseEpochMs" to session.onlinePhaseDeadlineEpochMs,
                "limiteFasePhaseIndex" to session.onlinePhaseDeadlinePhaseIndex,
                "anuncioPublico" to session.publicAnnouncement,
                "ganador" to session.winner,
                "volverLobbyEpochMs" to onlineLobbyReturnEpochMs,
                "victimaNoche" to session.nightKillTarget,
                "silenciado" to session.nightSilenceTarget,
                "expulsadoDia" to session.dayEliminationTarget,
                "nocheSinVictima" to session.nightHadNoVictim,
                "presentacionVotacion" to onlineVotePresentation,
                "votos" to session.votes,
                "rondaVoto" to session.voteRound,
                "candidatosDesempate" to session.tieVoteCandidates,
                "candidatosAlcalde" to session.alcaldeTieCandidates,
                "alcaldeRevelado" to session.alcaldeRevealed,
                "corrupcionAlcalde" to session.alcaldeCorruption,
                "desertorBando" to session.desertorTeam,
                "desertorCambioBando" to session.desertorChangedTeam,
                "payadorUsado" to session.payadorUsed,
                "jugadoresContrapunto" to session.contrapuntoPlayers,
                "sospechaContrapunto" to session.contrapuntoSuspicion,
                "oraculoUsado" to session.oracleUsed,
                "invitadoOraculo" to session.oracleInvitedPlayer,
                "victoriasEspeciales" to session.specialVictories.map { victory ->
                    mapOf(
                        "key" to victory.key,
                        "jugador" to victory.playerName,
                        "rol" to victory.roleKey,
                        "ronda" to victory.round
                    )
                },
                "historialPublico" to session.publicHistory,
                "jugadores" to session.players.mapIndexed { index, player ->
                    buildMap<String, Any?> {
                        putAll(mapOf(
                        "orden" to index,
                        "nombre" to player.name,
                        "vivo" to player.alive,
                        "muteado" to player.muted,
                        "ultimaRondaSilenciado" to player.lastSilencedRound,
                        "afkNoche" to player.consecutiveNightAfk,
                        "afkVoto" to player.consecutiveVoteAfk,
                        "causaEliminacion" to player.deathCause.name
                        ))
                        val roleCanBePublic = OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                            revealRolesOnDeath = session.revealRolesOnDeath,
                            playerAlive = player.alive,
                            winner = session.winner,
                            votePresentation = onlineVotePresentation,
                            playerName = player.name,
                            dayEliminationTarget = session.dayEliminationTarget,
                            alcaldeRevealed = session.alcaldeRevealed,
                            playerRoleKey = player.role?.key.orEmpty()
                        )
                        if (roleCanBePublic) {
                            player.role?.let { role ->
                                put("rolKey", role.key)
                                put("rolNombre", role.name)
                                put("rolEquipo", role.team)
                                put("rolImagen", role.imageResName)
                            }
                        }
                    }
                },
                "actualizadaEnLocal" to System.currentTimeMillis(),
                "actualizadaPor" to onlinePlayerId
            ),
            OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID to onlinePlayerId,
            "ultimaActividadOnline" to FieldValue.serverTimestamp()
        )
        if (session.winner.isNotBlank()) {
            if (session.winner != GameRules.CANCELLED_WINNER) {
                val resultMatchId = session.onlineMatchId
                    .takeIf { it.length in 8..80 }
                    ?: onlinePartidaId.take(80)
                roomUpdate["ultimoResultado"] = mapOf(
                    "ganador" to session.winner,
                    "ronda" to session.round,
                    "mapa" to session.mapKey,
                    "matchId" to resultMatchId,
                    "finalizadaEnLocal" to System.currentTimeMillis()
                )
            }
        }

        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .update(roomUpdate)
            .addOnSuccessListener {
                syncRealtimeGameplayAccess()
                OnlineDebugLog.i(
                    "phase_host_publish roomId=$onlinePartidaId uid=$onlinePlayerId phase=${session.phase.name} phaseIndex=${session.phaseIndex} round=${session.round} winner=${session.winner.ifBlank { "-" }}"
                )
            }
            .addOnFailureListener { error ->
                if (lastPublishedAuthoritativeOnlineStateKey == stateKey) {
                    lastPublishedAuthoritativeOnlineStateKey = ""
                }
                OnlineDebugLog.e(
                    "authoritative_state_publish_failure roomId=$onlinePartidaId uid=$onlinePlayerId phase=${session.phase.name} round=${session.round}",
                    error
                )
            }
    }

    private fun startAuthoritativeOnlineStateListener() {
        if (!isOnlineGameplay() || onlineStateListener != null) return
        OnlineDebugLog.i("authoritative_listener_start roomId=$onlinePartidaId uid=$onlinePlayerId")
        onlineStateListener = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("authoritative_listener_failure roomId=$onlinePartidaId uid=$onlinePlayerId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    OnlineDebugLog.w("authoritative_listener_missing roomId=$onlinePartidaId uid=$onlinePlayerId")
                    return@addSnapshotListener
                }
                val roomState = snapshot.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty()
                if (
                    roomState == OnlineRoomFirestore.STATE_FINISHED ||
                    roomState == OnlineRoomFirestore.STATE_ABANDONED
                ) {
                    OnlineRoomRecovery.clearIf(this, onlinePartidaId)
                }
                onlineActiveHostId = snapshot.getString(OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: onlineActiveHostId
                if (onlineActiveHostId == onlinePlayerId && !onlineIsHost) {
                    promoteToOnlineHost("room_snapshot")
                } else if (onlineActiveHostId != onlinePlayerId && onlineIsHost) {
                    demoteFromOnlineHost("room_snapshot")
                }
                val state = snapshot.get("estadoPartida").asStringAnyMap()
                    ?: run {
                        handleMissingAuthoritativeOnlineState()
                        return@addSnapshotListener
                    }
                if (
                    OnlineAuthoritativeStateMapper.schemaVersionFromState(state) !=
                    OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION
                ) {
                    handleIncompatibleOnlineState()
                    return@addSnapshotListener
                }
                handleOnlineStartupDeadlineSnapshot(state)
                val clientStates = snapshot.get("estadoClientes").asStringAnyMap()
                handleOnlineStartupSnapshot(clientStates)
                handleOnlinePresentationSnapshot(clientStates)
                handleOnlineWinnerReturnSnapshot(clientStates)
                applyAuthoritativeOnlineState(state)
            }
    }

    private fun handleIncompatibleOnlineState() {
        if (onlineIncompatibleStateHandled || isFinishing) return
        onlineIncompatibleStateHandled = true
        OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        GameNotice.show(
            activity = this,
            message = "Esta sala pertenece a otra version. Creen una sala nueva.",
            duration = GameNotice.Duration.LONG
        )
        finish()
    }

    private fun handleMissingAuthoritativeOnlineState() {
        if (!isOnlineGameplay() || onlineIsHost || isOnlineStartupPhase()) return
        if (!onlineAwaitingHostAdvance) {
            OnlineDebugLog.w(
                "phase_missing_authoritative_state roomId=$onlinePartidaId uid=$onlinePlayerId phase=${session.phase.name} phaseIndex=${session.phaseIndex}"
            )
        }
        setOnlineAwaitingHostAdvance(true)
        lastPublishedOnlineStateKey = ""
        renderGame()
    }

    private fun handleOnlineStartupSnapshot(clientStatesRaw: Map<String, Any?>?) {
        if (!isOnlineStartupPhase()) return
        val clientStates = clientStatesRaw
            ?.mapNotNull { (uid, rawState) -> onlineStartupClientState(uid, rawState.asStringAnyMap()) }
            .orEmpty()
        lastOnlineStartupClientStates = clientStates
        refreshOnlineStartupGateFromLastStates()
    }

    private fun handleOnlinePresentationSnapshot(clientStatesRaw: Map<String, Any?>?) {
        if (!isOnlineGameplay()) return
        onlinePresentationClientAcks = clientStatesRaw
            ?.mapValues { (_, rawState) ->
                (rawState.asStringAnyMap()?.get(FIELD_PRESENTATION_ACK_KEY) as? String).orEmpty()
            }
            .orEmpty()
        refreshOnlinePresentationGate()
    }

    private fun handleOnlineWinnerReturnSnapshot(clientStatesRaw: Map<String, Any?>?) {
        if (!isOnlineGameplay()) return
        onlineWinnerReturnClientAcks = clientStatesRaw
            ?.mapValues { (_, rawState) ->
                (rawState.asStringAnyMap()?.get(FIELD_WINNER_RETURN_ACK_KEY) as? String).orEmpty()
            }
            .orEmpty()
        syncOwnWinnerReturnAckFromSnapshot()
        if (::session.isInitialized && session.winner.isNotBlank()) {
            configureWinnerReturnButton()
            maybeCoordinateWinnerReturn()
        }
    }

    private fun handleOnlineStartupDeadlineSnapshot(state: Map<String, Any?>) {
        if (!isOnlineStartupPhase()) return
        val incomingDeadline = OnlineAuthoritativeStateMapper.startupDeadlineFromState(state)
        if (incomingDeadline <= 0L || incomingDeadline == onlineStartupDeadlineEpochMs) return
        onlineStartupDeadlineEpochMs = incomingDeadline
        onlineStartupDeadlinePublishInProgress = false
        OnlineDebugLog.i(
            "startup_auto_deadline_received roomId=$onlinePartidaId uid=$onlinePlayerId deadline=$incomingDeadline"
        )
    }

    private fun refreshOnlineStartupGateFromLastStates() {
        if (!isOnlineStartupPhase()) return
        val expectedPlayers = expectedOnlineStartupPlayers()
        val result = OnlineStartupGate.evaluate(
            expectedPlayers = expectedPlayers,
            clientStates = lastOnlineStartupClientStates
        )
        onlineStartupGateResult = result
        val gateKey = listOf(
            result.loadedPlayers,
            result.readyPlayers,
            result.mismatchedPlayers,
            result.canStart,
            result.canArmAutoStart,
            onlineStartupDeadlineEpochMs
        ).joinToString("|")
        if (gateKey != lastOnlineStartupGateKey) {
            lastOnlineStartupGateKey = gateKey
            OnlineDebugLog.i(
                "startup_gate roomId=$onlinePartidaId uid=$onlinePlayerId isHost=$onlineIsHost loaded=${result.loadedPlayers}/$expectedPlayers ready=${result.readyPlayers}/$expectedPlayers mismatched=${result.mismatchedPlayers} canStart=${result.canStart} autoDeadline=$onlineStartupDeadlineEpochMs"
            )
        }
        if (onlineIsHost && result.canStart) {
            startOnlineFirstNight("all_ready")
            return
        }
        if (
            onlineIsHost &&
            result.canArmAutoStart &&
            onlineStartupDeadlineEpochMs <= 0L &&
            !onlineStartupDeadlinePublishInProgress
        ) {
            publishOnlineStartupDeadline()
        }
        val shouldAutoStart = result.canArmAutoStart && OnlineStartupGate.shouldAutoStart(
            deadlineEpochMs = onlineStartupDeadlineEpochMs,
            nowEpochMs = System.currentTimeMillis()
        )
        when {
            onlineIsHost && shouldAutoStart -> startOnlineFirstNight("automatic_timeout")
            else -> {
                renderOnlineStartupHint()
                scheduleOnlineStartupTick()
            }
        }
    }

    private fun publishOnlineStartupDeadline() {
        if (!onlineIsHost || !isOnlineStartupPhase()) return
        val deadline = System.currentTimeMillis() + OnlineStartupGate.AUTO_START_AFTER_MS
        onlineStartupDeadlineEpochMs = deadline
        onlineStartupDeadlinePublishInProgress = true
        OnlineDebugLog.i(
            "startup_auto_deadline_publish_requested roomId=$onlinePartidaId uid=$onlinePlayerId deadline=$deadline"
        )
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .update(
                mapOf(
                    "estadoPartida.$FIELD_STARTUP_AUTO_DEADLINE" to deadline,
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineStartupDeadlinePublishInProgress = false
                OnlineDebugLog.i(
                    "startup_auto_deadline_publish_success roomId=$onlinePartidaId uid=$onlinePlayerId deadline=$deadline"
                )
            }
            .addOnFailureListener { error ->
                if (onlineStartupDeadlineEpochMs == deadline) {
                    onlineStartupDeadlineEpochMs = 0L
                }
                onlineStartupDeadlinePublishInProgress = false
                OnlineDebugLog.e(
                    "startup_auto_deadline_publish_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                    error
                )
                if (!isFinishing && !isDestroyed) {
                    autoAdvanceHandler.postDelayed(onlineStartupTickRunnable, 1_000L)
                }
            }
    }

    private fun onlineStartupClientState(uid: String, state: Map<String, Any?>?): OnlineStartupClientState? {
        if (uid.isBlank() || state == null) return null
        return OnlineStartupClientState(
            uid = uid,
            inGameplay = (state["enGameplay"] as? Boolean) ?: false,
            visiblePlayers = (state["jugadoresVistos"] as? Number)?.toInt() ?: 0,
            phase = (state["fase"] as? String).orEmpty(),
            phaseIndex = (state["phaseIndex"] as? Number)?.toInt() ?: -1,
            roleRead = (state["rolLeido"] as? Boolean) ?: false,
            order = (state["orden"] as? Number)?.toInt() ?: Int.MAX_VALUE
        )
    }

    private fun markOnlineInitialRoleRead() {
        if (!isOnlineStartupPhase() || onlineInitialRoleRead) return
        onlineInitialRoleRead = true
        setOnlineAwaitingHostAdvance(true)
        lastPublishedOnlineStateKey = ""
        OnlineDebugLog.i(
            "startup_role_read roomId=$onlinePartidaId uid=$onlinePlayerId visiblePlayers=${session.players.size}/${expectedOnlineStartupPlayers()}"
        )
        GameNotice.show(this, "Listo. La primera noche comenzara automaticamente.")
        publishOnlineClientState()
    }

    private fun handleOnlineStartupAction(): Boolean {
        if (!isOnlineStartupPhase()) return false
        GameplayEffects.play(this, GameplayEffect.ERROR)
        GameNotice.show(this, onlineStartupHintText())
        renderGame()
        return true
    }

    private fun startOnlineFirstNight(reason: String) {
        if (!onlineIsHost || !isOnlineStartupPhase()) return
        OnlineDebugLog.i(
            "startup_first_night_start roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason players=${session.players.size}"
        )
        autoAdvanceHandler.removeCallbacks(onlineStartupTickRunnable)
        val before = session
        session = GameEngine.startNight(session)
        setOnlineAwaitingHostAdvance(false)
        onlineStartupDeadlinePublishInProgress = false
        onlineStartupGateResult = null
        recordOnlinePhaseAdvance(before, session)
        clearSelection()
        renderGame()
    }

    private fun renderOnlineStartupHint() {
        if (!isOnlineStartupPhase() || !::currentPlayerHint.isInitialized) return
        currentPlayerHint.text = onlineStartupHintText()
        if (initialRoleReadingActive && ::btnContinueRolePreview.isInitialized) {
            roleReadingTickRunnable.run()
        }
        renderAdvanceButton()
    }

    private fun onlineStartupHintText(): String {
        val gate = onlineStartupGateResult
        val countdownSeconds = onlineStartupCountdownSeconds()
        return when {
            session.players.size < expectedOnlineStartupPlayers() -> "Sincronizando cartas..."
            countdownSeconds != null && !onlineInitialRoleRead ->
                "Lee tu rol. La noche empieza en $countdownSeconds s."
            countdownSeconds != null -> "Listo. La noche empieza en $countdownSeconds s."
            gate?.canArmAutoStart == true -> "Preparando la cuenta regresiva..."
            !onlineInitialRoleRead -> "Lee tu rol y toca EMPEZAR."
            gate != null -> gate.waitingMessage
            else -> "Esperando a que todos terminen de leer..."
        }
    }

    private fun isOnlineStartupPhase(): Boolean {
        return isOnlineGameplay() &&
            ::session.isInitialized &&
            session.phase == GamePhase.REPARTO &&
            session.phaseIndex == 0
    }

    private fun expectedOnlineStartupPlayers(): Int {
        return session.initialPlayerCount.coerceAtLeast(session.players.size)
    }

    private fun onlineStartupCountdownSeconds(): Int? {
        val remainingMs = OnlineStartupGate.remainingAutoStartMillis(
            deadlineEpochMs = onlineStartupDeadlineEpochMs,
            nowEpochMs = System.currentTimeMillis()
        ) ?: return null
        return ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
    }

    private fun scheduleOnlineStartupTick() {
        autoAdvanceHandler.removeCallbacks(onlineStartupTickRunnable)
        if (!isOnlineStartupPhase() || onlineStartupDeadlineEpochMs <= 0L) return
        val remainingMs = OnlineStartupGate.remainingAutoStartMillis(
            deadlineEpochMs = onlineStartupDeadlineEpochMs,
            nowEpochMs = System.currentTimeMillis()
        ) ?: return
        if (remainingMs > 0L) {
            autoAdvanceHandler.postDelayed(
                onlineStartupTickRunnable,
                minOf(1_000L, remainingMs)
            )
        }
    }

    private fun applyAuthoritativeOnlineState(state: Map<String, Any?>) {
        if (!::session.isInitialized) return
        val phaseName = state["fase"] as? String ?: return
        val phase = runCatching { GamePhase.valueOf(phaseName) }.getOrNull() ?: return
        val phaseIndex = (state["phaseIndex"] as? Number)?.toInt() ?: return

        val stateKey = listOf(
            OnlineAuthoritativeStateMapper.schemaVersionFromState(state),
            phase.name,
            (state["ronda"] as? Number)?.toInt() ?: session.round,
            phaseIndex,
            OnlineAuthoritativeStateMapper.phaseDeadlineFromState(state),
            OnlineAuthoritativeStateMapper.phaseDeadlineIndexFromState(state),
            (state["anuncioPublico"] as? String).orEmpty(),
            (state["ganador"] as? String).orEmpty(),
            OnlineAuthoritativeStateMapper.lobbyReturnDeadlineFromState(state),
            (state["victimaNoche"] as? String).orEmpty(),
            (state["silenciado"] as? String).orEmpty(),
            (state["expulsadoDia"] as? String).orEmpty(),
            OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(state),
            OnlineAuthoritativeStateMapper.votePresentationFromState(state),
            publicHistoryFromAuthoritativeState(state).joinToString("#"),
            (state["rondaVoto"] as? Number)?.toInt() ?: session.voteRound,
            votesFromAuthoritativeState(state).entries.sortedBy { it.key }.joinToString("#") { "${it.key}:${it.value}" },
            stringListFromAuthoritativeState(state, "candidatosDesempate").joinToString("#"),
            stringListFromAuthoritativeState(state, "candidatosAlcalde").joinToString("#"),
            (state["payadorUsado"] as? Boolean) ?: false,
            stringListFromAuthoritativeState(state, "jugadoresContrapunto").joinToString("#"),
            (state["sospechaContrapunto"] as? String).orEmpty(),
            (state["oraculoUsado"] as? Boolean) ?: false,
            (state["invitadoOraculo"] as? String).orEmpty(),
            OnlineAuthoritativeStateMapper.specialVictoriesFromState(state)
                .joinToString("#") { it.key },
            publicPlayerStateKey(state)
        ).joinToString("|")
        when (
            OnlinePhaseGate.evaluateIncomingState(
                isHost = onlineIsHost,
                currentPhaseIndex = session.phaseIndex,
                incomingPhaseIndex = phaseIndex,
                incomingStateKey = stateKey,
                lastAppliedStateKey = lastAppliedAuthoritativeOnlineStateKey
            )
        ) {
            OnlinePhaseDecision.HOST_IGNORES -> return
            OnlinePhaseDecision.IGNORE_OLD -> {
                OnlineDebugLog.w(
                    "phase_ignore_old roomId=$onlinePartidaId uid=$onlinePlayerId current=${session.phase.name}:${session.phaseIndex} incoming=$phaseName:$phaseIndex"
                )
                return
            }
            OnlinePhaseDecision.IGNORE_DUPLICATE -> return
            OnlinePhaseDecision.APPLY -> Unit
        }
        lastAppliedAuthoritativeOnlineStateKey = stateKey
        onlineLobbyReturnEpochMs = OnlineAuthoritativeStateMapper
            .lobbyReturnDeadlineFromState(state)

        val previousSession = session
        val previousPhaseIndex = session.phaseIndex
        val previousPrivateHint = session.privateHint
        if (phaseIndex != previousPhaseIndex && isDayNightTransitionRunning) {
            // Se cancela con la fase ANTERIOR aún activa; así el render siguiente puede
            // presentar limpiamente la transición oficial recién recibida.
            settleDayNightTransition(resumeMusic = false)
        }
        val incomingVotePresentation =
            OnlineAuthoritativeStateMapper.votePresentationFromState(state)
        onlineVotePresentation = incomingVotePresentation
        val updatedPlayers = playersFromAuthoritativeState(state) ?: session.players
        OnlineDebugLog.i(
            "phase_apply_authoritative roomId=$onlinePartidaId uid=$onlinePlayerId phase=${phase.name} round=${(state["ronda"] as? Number)?.toInt() ?: session.round} phaseIndex=$phaseIndex"
        )
        val authoritativePublicHistory = publicHistoryFromAuthoritativeState(state)
        session = session.copy(
            phase = phase,
            round = (state["ronda"] as? Number)?.toInt() ?: session.round,
            phaseIndex = phaseIndex,
            onlinePhaseDeadlineEpochMs = OnlineAuthoritativeStateMapper
                .phaseDeadlineFromState(state),
            onlinePhaseDeadlinePhaseIndex = OnlineAuthoritativeStateMapper
                .phaseDeadlineIndexFromState(state),
            players = updatedPlayers,
            publicAnnouncement = (state["anuncioPublico"] as? String).orEmpty(),
            publicHistory = authoritativePublicHistory,
            godHistory = authoritativePublicHistory,
            chatHistory = GameplayFeedMessages.appendGodEvents(
                session.chatHistory,
                authoritativePublicHistory
            ),
            winner = (state["ganador"] as? String).orEmpty(),
            nightKillTarget = (state["victimaNoche"] as? String).orEmpty(),
            nightHadNoVictim = OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(state),
            nightSilenceTarget = (state["silenciado"] as? String).orEmpty(),
            dayEliminationTarget = (state["expulsadoDia"] as? String).orEmpty(),
            votes = votesFromAuthoritativeState(state),
            voteRound = (state["rondaVoto"] as? Number)?.toInt() ?: session.voteRound,
            tieVoteCandidates = stringListFromAuthoritativeState(state, "candidatosDesempate"),
            alcaldeTieCandidates = stringListFromAuthoritativeState(state, "candidatosAlcalde"),
            alcaldeRevealed = (state["alcaldeRevelado"] as? Boolean) ?: session.alcaldeRevealed,
            alcaldeCorruption = (state["corrupcionAlcalde"] as? Boolean) ?: session.alcaldeCorruption,
            desertorTeam = (state["desertorBando"] as? String) ?: session.desertorTeam,
            desertorChangedTeam = (state["desertorCambioBando"] as? Boolean)
                ?: session.desertorChangedTeam,
            payadorUsed = (state["payadorUsado"] as? Boolean) ?: session.payadorUsed,
            contrapuntoPlayers = stringListFromAuthoritativeState(state, "jugadoresContrapunto"),
            contrapuntoSuspicion = (state["sospechaContrapunto"] as? String)
                ?: session.contrapuntoSuspicion,
            oracleUsed = (state["oraculoUsado"] as? Boolean) ?: session.oracleUsed,
            oracleInvitedPlayer = (state["invitadoOraculo"] as? String)
                ?: session.oracleInvitedPlayer,
            oracleRevealPending = phase == GamePhase.DIA_DEBATE &&
                (state["invitadoOraculo"] as? String).orEmpty().isNotBlank(),
            specialVictories = OnlineAuthoritativeStateMapper.specialVictoriesFromState(state),
            privateHint = previousPrivateHint
        )
        OnlineDiagnostics.recordPhase(session, onlineIsHost, event = "guest_apply")
        // El pedido ya llego a la mesa: se libera el candado local del dialogo para que la
        // ventana de reconsideracion pueda abrirse mas adelante.
        if (session.desertorTeam.isNotBlank()) {
            onlineDesertorChoiceSent = false
        }
        setOnlineAwaitingHostAdvance(false)
        lastAppliedAuthoritativePhaseLabel = latestAppliedPhaseLabel()
        if (phaseIndex != previousPhaseIndex) {
            if (previousPhaseIndex == 0 && phase != GamePhase.REPARTO) {
                OnlineDebugLog.i(
                    "startup_first_night_received roomId=$onlinePartidaId uid=$onlinePlayerId phase=${phase.name} phaseIndex=$phaseIndex"
                )
            }
            setOnlineAwaitingHostAdvance(false)
            onlineInitialRoleRead = phase != GamePhase.REPARTO || onlineInitialRoleRead
            clearOnlineAuthoritativePhaseUi()
            submittedOnlineNightActions.clear()
            pendingOnlineActionSubmissions.clear()
            submittedOnlinePayadorTargets.clear()
            pendingOnlinePayadorTargets.clear()
        }
        renderGame()
        if (session.winner.isNotBlank() && isWinnerRevealVisible) {
            configureWinnerReturnButton()
            scheduleWinnerAutoReturn()
            maybeCoordinateWinnerReturn()
        }
        applyOnlineVotePresentation(incomingVotePresentation)
        notifyLocalOnlineAfkChange(previousSession, session)
    }

    private fun applyOnlineVotePresentation(presentation: String) {
        if (
            onlineIsHost ||
            presentation.isBlank() ||
            presentation == lastAppliedOnlineVotePresentation
        ) {
            return
        }
        lastAppliedOnlineVotePresentation = presentation
        dismissSecondaryUiForPriorityWindow()
        dismissActionFeedbackBannerNow()
        hideCentralPublicEventBanner(immediate = true)
        when {
            presentation.startsWith("expulsion|") -> {
                if (!isVoteResultVisible) {
                    isVoteResultVisible = true
                    voteResultAnimator.show(session)
                }
                playVoteExpulsionOnce(presentation)
            }
            presentation.startsWith("sin_expulsion|") -> {
                voteNoExpulsionPresented = true
                if (!isVoteResultVisible) {
                    isVoteResultVisible = true
                    voteResultAnimator.show(session)
                }
                voteResultAnimator.showNoExpulsion()
            }
        }
        refreshOnlinePresentationGate()
    }

    private fun playVoteExpulsionOnce(presentationKey: String) {
        if (
            presentationKey.isNotBlank() &&
            voteExpulsionAnimationKey == presentationKey &&
            !voteExpulsionComplete
        ) {
            OnlineDebugLog.w(
                "vote_expulsion_duplicate_start_blocked roomId=$onlinePartidaId uid=$onlinePlayerId key=$presentationKey"
            )
            return
        }
        voteExpulsionAnimationKey = presentationKey
        voteExpulsionComplete = false
        voteResultAnimator.playExpulsion(session) {
            voteExpulsionComplete = true
            OnlineDebugLog.i(
                "vote_expulsion_animation_complete roomId=$onlinePartidaId uid=$onlinePlayerId key=$presentationKey host=$onlineIsHost"
            )
            refreshOnlinePresentationGate()
        }
    }

    private fun authoritativeStateAppliedLocally(): Boolean {
        return onlineIsHost || lastAppliedAuthoritativeOnlineStateKey.isNotBlank()
    }

    private fun latestAppliedPhaseLabel(): String {
        return "${session.phase.name}:${session.phaseIndex}"
    }

    private fun clearOnlineAuthoritativePhaseUi() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        dismissActionFeedbackBannerNow()
        if (isRolePreviewOpen) {
            closeRolePreview(resumeGameFlow = false)
        }
        hideTieVoteWindow(clearSelection = true)
        cancelVoteResult()
        clearSelection()
        clearCountdown()
    }

    private fun publicPlayerStateKey(state: Map<String, Any?>): String {
        return (state["jugadores"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.joinToString("#") {
                "${it["orden"]}:${it["nombre"]}:${it["vivo"]}:${it["muteado"]}:" +
                    "${it["ultimaRondaSilenciado"]}:${it["afkNoche"]}:${it["afkVoto"]}:" +
                    "${it["causaEliminacion"]}:${it["rolKey"]}:${it["rolNombre"]}:" +
                    "${it["rolEquipo"]}:${it["rolImagen"]}"
            }
            .orEmpty()
    }

    private fun playersFromAuthoritativeState(state: Map<String, Any?>): List<GamePlayer>? {
        return OnlineAuthoritativeStateMapper.playersFromState(session.players, state)
    }

    private fun publicHistoryFromAuthoritativeState(state: Map<String, Any?>): List<String> {
        return (state["historialPublico"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.takeIf { it.isNotEmpty() }
            ?: session.publicHistory
    }

    private fun votesFromAuthoritativeState(state: Map<String, Any?>): Map<String, String> {
        return state["votos"].asStringAnyMap()
            ?.mapValues { (_, value) -> value as? String ?: "" }
            ?.filterValues { it.isNotBlank() }
            ?: emptyMap()
    }

    private fun stringListFromAuthoritativeState(state: Map<String, Any?>, key: String): List<String> {
        return (state[key] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
    }

    private fun Any?.asStringAnyMap(): Map<String, Any?>? {
        return (this as? Map<*, *>)?.entries
            ?.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                key to entry.value
            }
            ?.toMap()
    }

    private fun recordOnlinePhaseAdvance(before: GameSession, after: GameSession) {
        if (!isOnlineGameplay()) return
        if (before.phase == after.phase && before.phaseIndex == after.phaseIndex) return
        if (after.phase == GamePhase.RECUENTO_VOTOS && before.phase != GamePhase.RECUENTO_VOTOS) {
            onlineVotePresentation = ""
            lastAppliedOnlineVotePresentation = ""
        }
        // estadoPartida ya contiene este cambio. No crear un segundo documento por fase
        // reduce escrituras y almacenamiento sin perder autoridad ni recuperacion.
        OnlineDebugLog.i(
            "phase_advanced_local roomId=$onlinePartidaId from=${before.phase.name}:${before.phaseIndex} to=${after.phase.name}:${after.phaseIndex}"
        )
    }

    private fun recordOnlinePlayerAction(
        before: GameSession,
        after: GameSession,
        targetName: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!isOnlineGameplay()) return
        val actionType = when (before.phase) {
            GamePhase.NOCHE_ASESINO -> "matar"
            GamePhase.NOCHE_MERCENARIO -> "silenciar"
            GamePhase.NOCHE_POLICIA -> "investigar"
            GamePhase.NOCHE_MEDICO -> "salvar"
            GamePhase.NOCHE_ORACULO -> "invitar_muerto"
            GamePhase.DIA_DEBATE -> "contrapunto"
            GamePhase.CONTRAPUNTO -> "senalar_contrapunto"
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
                "faseResultado" to after.phase.name,
                "phaseIndexResultado" to after.phaseIndex
            ),
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    private fun recordOnlineAction(
        type: String,
        targetName: String,
        details: Map<String, Any?>,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!isOnlineGameplay()) return
        val human = GameEngine.humanPlayer(session)
        OnlineDebugLog.i(
            "action_record_requested roomId=$onlinePartidaId uid=$onlinePlayerId type=$type actor=${human.name} target=${targetName.ifBlank { "-" }} phase=${session.phase.name} round=${session.round} host=$onlineIsHost"
        )
        val actorOrder = session.players.indexOfFirst { it.isHuman }.coerceAtLeast(0)
        val targetOrder = session.players.indexOfFirst { it.name == targetName }
        val orderedDetails = details +
            mapOf("actorOrden" to actorOrder) +
            if (targetOrder >= 0) {
                mapOf("objetivoOrden" to targetOrder)
            } else {
                emptyMap()
            }
        val payload = mapOf(
            "matchId" to session.onlineMatchId,
            "tipo" to type,
            "actorId" to onlinePlayerId,
            "actorNombre" to human.name,
            "actorEsHost" to onlineIsHost,
            "objetivoNombre" to targetName,
            "fase" to session.phase.name,
            "ronda" to session.round,
            "phaseIndex" to session.phaseIndex,
            "modoCliente" to "android",
            "detalles" to orderedDetails,
            "creadaEn" to FieldValue.serverTimestamp(),
            "creadaEnLocal" to System.currentTimeMillis()
        )
        val actionName = (orderedDetails["accion"] as? String).orEmpty()
        val actionSlot = (orderedDetails["slotAccion"] as? Number)?.toInt() ?: 1
        val actionDocumentId = OnlineActionIdentity.documentId(
            matchId = session.onlineMatchId,
            actorId = onlinePlayerId,
            round = session.round,
            phaseIndex = session.phaseIndex,
            action = actionName,
            slot = actionSlot
        )
        val actionReference = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("acciones")
            .document(actionDocumentId)

        fun reportFailure(error: Exception) {
            OnlineDebugLog.e(
                "action_record_failure roomId=$onlinePartidaId uid=$onlinePlayerId type=$type actor=${human.name} target=${targetName.ifBlank { "-" }} phase=${session.phase.name} round=${session.round}",
                error
            )
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(
                activity = this,
                message = OnlineErrorMessages.forAction("No se pudo registrar la accion online", error),
                duration = GameNotice.Duration.LONG
            )
            onFailure?.invoke(error)
        }

        actionReference
            .set(payload)
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "action_record_success roomId=$onlinePartidaId actionId=$actionDocumentId type=$type actor=${human.name} target=${targetName.ifBlank { "-" }} phase=${session.phase.name} round=${session.round}"
                )
                onSuccess?.invoke()
            }
            .addOnFailureListener { error ->
                if (
                    error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    // Las reglas no permiten actualizar acciones: si ya existe, el primer
                    // toque quedo bloqueado y este reintento tambien cuenta como exito.
                    actionReference.get()
                        .addOnSuccessListener { existing ->
                            if (
                                existing.exists() &&
                                existing.getString("actorId") == onlinePlayerId &&
                                existing.getString("matchId").orEmpty() == session.onlineMatchId
                            ) {
                                onSuccess?.invoke()
                            } else {
                                reportFailure(error)
                            }
                        }
                        .addOnFailureListener(::reportFailure)
                } else {
                    reportFailure(error)
                }
            }
    }

    override fun isOnlineGameplay(): Boolean {
        return onlinePartidaId.isNotBlank() && onlinePlayerId.isNotBlank()
    }

    override fun canOpenExpandedChat(): Boolean {
        return !reactionUiBlocked()
    }

    override fun isTransitionLocked(phaseIndex: Int): Boolean {
        return countdown.isTransitionLocked(phaseIndex)
    }

    override fun hideKeyboard() {
        WindowCompat.getInsetsController(window, gameplayRoot)
            .hide(WindowInsetsCompat.Type.ime())
    }

    override fun showToast(message: String, duration: Int) {
        GameNotice.show(
            this,
            message,
            if (duration > 0) GameNotice.Duration.LONG else GameNotice.Duration.SHORT
        )
    }

    override fun showTieVoteWindowAfterChat() {
        showTieVoteWindow()
    }

    override fun onOnlineReactionReceived(playerName: String, emoteId: String) {
        if (!::session.isInitialized || !isOnlineGameplay()) return
        val player = session.players.firstOrNull { it.name == playerName } ?: return
        val spec = EmoteCatalog.byId(emoteId)?.toReactionSpec() ?: return
        if (
            session.winner.isNotBlank() ||
            !GameEngine.isAlive(player) ||
            !isPublicReactionPhase(session.phase)
        ) {
            return
        }
        if (!gameplayResumed || reactionUiBlocked()) {
            pendingOnlineReactions.remove(playerName)
            pendingOnlineReactions[playerName] = spec
            while (pendingOnlineReactions.size > MAX_PENDING_ONLINE_REACTIONS) {
                val oldestPlayer = pendingOnlineReactions.keys.firstOrNull() ?: break
                pendingOnlineReactions.remove(oldestPlayer)
            }
            return
        }
        showReactionBubble(playerName, spec)
    }

    override fun onOnlineTraitorActionMarksChanged(marks: List<OnlineTraitorActionMark>) {
        val normalized = marks
            .filter {
                it.roleKey == RoleCatalog.ASESINO ||
                    it.roleKey == RoleCatalog.ESPIA ||
                    it.roleKey == RoleCatalog.MERCENARIO
            }
            .distinctBy { it.id }
        if (normalized == onlineTraitorActionMarks) return
        onlineTraitorActionMarks = normalized
        if (::session.isInitialized) refreshVisibleActionMarks()
    }

    override fun onRealtimeContentAccessCancelled(error: Exception) {
        OnlineDebugLog.e(
            "rtdb_gameplay_content_cancelled roomId=$onlinePartidaId uid=$onlinePlayerId",
            error
        )
        realtimePresence?.refresh()
    }

    private fun startRealtimeGameplayPresence() {
        if (!isOnlineGameplay() || realtimePresence != null) return
        if (onlineIsHost) syncRealtimeGameplayAccess()
        val presence = RealtimeRoomPresence(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            uid = onlinePlayerId,
            onPresenceChanged = { states ->
                realtimePresenceStates = states
                realtimePresenceBaselineReady = true
                refreshGameplayPresenceFromRealtime()
            },
            onOwnPresenceReady = {
                if (realtimePresence != null && !isFinishing) {
                    chatController.onRealtimeAccessReady()
                    startRealtimeTableSilence()
                }
            },
            onOwnPresenceUnavailable = {
                chatController.onRealtimeAccessUnavailable()
            },
            onError = { error ->
                OnlineDebugLog.e(
                    "rtdb_gameplay_presence_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                    error
                )
            }
        )
        realtimePresence = presence
        presence.start()
    }

    private fun syncRealtimeGameplayAccess() {
        if (!isOnlineGameplay() || !onlineIsHost || !::session.isInitialized) return
        val members = session.players.mapIndexedNotNull { index, player ->
            val uid = session.onlinePlayerUids.getOrNull(index)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            uid to RealtimeRoomMemberAccess(
                name = player.name,
                inLobby = false,
                alive = player.alive,
                traitor = player.role?.team?.let { it == GameRules.TRAITOR_WINNER },
                oracleInvitedToPublicChat = !player.alive &&
                    session.phase == GamePhase.DIA_DEBATE &&
                    session.oracleInvitedPlayer == player.name
            )
        }.toMap()
        if (members.isEmpty()) return
        RealtimeRoomAccess.syncMembers(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            hostUid = onlinePlayerId,
            matchId = session.onlineMatchId,
            members = members,
            onFailure = { error ->
                OnlineDebugLog.e(
                    "rtdb_gameplay_access_sync_failure roomId=$onlinePartidaId host=$onlinePlayerId",
                    error
                )
            }
        )
    }

    private fun startRealtimeTableSilence() {
        if (!isOnlineGameplay() || realtimeTableSilence != null) return
        realtimeTableSilence = RealtimeTableSilence(
            activity = this,
            roomId = onlinePartidaId,
            ownUid = onlinePlayerId,
            ownName = { GameEngine.humanPlayer(session).name },
            isOwnPlayerAlive = { GameEngine.humanPlayer(session).alive },
            aliveCount = { session.players.count { it.alive } },
            isAuthority = { onlineIsHost },
            onOwnSilenceChanged = { silenced ->
                val changed = ownPlayerTableSilenced != silenced
                ownPlayerTableSilenced = silenced
                chatController.refreshUi()
                if (changed && silenced) {
                    GameNotice.show(
                        this,
                        "La mesa silenció tu texto libre. Todavía podés usar respuestas rápidas.",
                        GameNotice.Duration.LONG
                    )
                }
            }
        ).also { it.start() }
    }

    private fun refreshGameplayPresenceFromRealtime() {
        if (onlinePresencePlayers.isEmpty()) return
        onlinePresencePlayers = onlinePresencePlayers.map { player ->
            player.copy(
                state = if (isOnlineUidConnected(player.id, player.state == PLAYER_STATE_CONNECTED)) {
                    PLAYER_STATE_CONNECTED
                } else {
                    PLAYER_STATE_DISCONNECTED
                },
                lastSeenLocalMs = realtimePresenceStates[player.id]
                    ?.changedAtMs
                    ?.takeIf { it > 0L }
                    ?: player.lastSeenLocalMs
            )
        }
        handleOnlineHostHandoff(onlinePresencePlayers)
        maybeResolveOnlineNightEarly()
        refreshOnlinePresentationGate()
        if (::session.isInitialized && session.winner.isNotBlank()) {
            configureWinnerReturnButton()
            maybeCoordinateWinnerReturn()
        }
        renderReadyToVoteButton()
        maybeAdvanceOnlineReadyVote()
    }

    private fun isOnlineUidConnected(uid: String, legacyConnected: Boolean): Boolean {
        return realtimePresenceStates[uid]?.connected
            ?: (!realtimePresenceBaselineReady && legacyConnected)
    }

    private fun markOnlineGameplayPresence(state: String) {
        if (!isOnlineGameplay() || !::session.isInitialized) return
        lastOnlinePresencePulseAtMs = SystemClock.elapsedRealtime()
        onlinePresencePulseIntervalMs = OnlineSyncWatchdog.jitteredPresencePulseMs(
            "$onlinePlayerId:${System.currentTimeMillis()}".hashCode()
        )
        realtimePresence?.setConnected(state == PLAYER_STATE_CONNECTED)
        if (lastLegacyPresenceState == state) return
        lastLegacyPresenceState = state
        val human = GameEngine.humanPlayer(session)
        FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(onlinePlayerId)
            .set(
                mapOf(
                    OnlineRoomFirestore.FIELD_NAME to human.name,
                    OnlineRoomFirestore.FIELD_PLAYER_STATE to state,
                    "uidTemporal" to onlinePlayerId,
                    OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to true,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "gameplay_presence_failure roomId=$onlinePartidaId uid=$onlinePlayerId state=$state",
                    error
                )
            }
    }

    private fun startOnlineSyncWatchdog() {
        if (!isOnlineGameplay()) return
        if (onlineGameplayStartedAtMs == 0L) {
            onlineGameplayStartedAtMs = SystemClock.elapsedRealtime()
        }
        if (lastOnlinePresencePulseAtMs == 0L) {
            lastOnlinePresencePulseAtMs = SystemClock.elapsedRealtime()
        }
        autoAdvanceHandler.removeCallbacks(onlineSyncWatchdogRunnable)
        autoAdvanceHandler.postDelayed(
            onlineSyncWatchdogRunnable,
            OnlineSyncWatchdog.CHECK_INTERVAL_MS
        )
    }

    private fun stopOnlineSyncWatchdog() {
        autoAdvanceHandler.removeCallbacks(onlineSyncWatchdogRunnable)
    }

    private fun runOnlineSyncWatchdog() {
        if (!isOnlineGameplay() || !::session.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (onlineGameplayStartedAtMs == 0L) {
            onlineGameplayStartedAtMs = now
        }
        if (onlineAwaitingHostAdvance && onlineAwaitingHostSinceMs == 0L) {
            onlineAwaitingHostSinceMs = now
        } else if (!onlineAwaitingHostAdvance) {
            onlineAwaitingHostSinceMs = 0L
            onlineSyncDelayReported = false
        }
        val decision = OnlineSyncWatchdog.evaluate(
            isOnline = isOnlineGameplay(),
            isHost = onlineIsHost,
            isStartupPhase = isOnlineStartupPhase(),
            hasAppliedAuthoritativeState = authoritativeStateAppliedLocally(),
            awaitingHostAdvance = onlineAwaitingHostAdvance,
            lastPresencePulseElapsedMs = now - lastOnlinePresencePulseAtMs,
            elapsedSinceGameplayStartMs = now - onlineGameplayStartedAtMs,
            elapsedAwaitingHostMs = if (onlineAwaitingHostSinceMs == 0L) {
                0L
            } else {
                now - onlineAwaitingHostSinceMs
            },
            presencePulseIntervalMs = onlinePresencePulseIntervalMs
        )
        if (decision.reason != "ok" && decision.reason != lastOnlineWatchdogReason) {
            lastOnlineWatchdogReason = decision.reason
            OnlineDebugLog.w(
                "sync_watchdog roomId=$onlinePartidaId uid=$onlinePlayerId host=$onlineIsHost reason=${decision.reason} phase=${session.phase.name} phaseIndex=${session.phaseIndex} awaiting=$onlineAwaitingHostAdvance applied=${authoritativeStateAppliedLocally()}"
            )
        }
        if (decision.shouldForceSyncing) {
            setOnlineAwaitingHostAdvance(true, now)
            lastPublishedOnlineStateKey = ""
            renderGame()
        } else if (
            decision.shouldPublishClientState &&
            (
                !decision.shouldReportLongWait ||
                    !onlineSyncDelayReported ||
                    decision.shouldPublishPresence
                )
        ) {
            publishOnlineClientState()
        }
        if (decision.shouldReportLongWait && !onlineSyncDelayReported) {
            onlineSyncDelayReported = true
            realtimePresence?.refresh()
            OnlineDiagnostics.recordSyncDelay(
                session = session,
                isHost = onlineIsHost,
                connectedPlayers = onlinePresencePlayers.count {
                    it.activeInMatch &&
                        isOnlineUidConnected(it.id, it.state == PLAYER_STATE_CONNECTED)
                },
                expectedPlayers = session.players.size,
                reason = decision.reason
            )
            GameNotice.show(
                activity = this,
                message = "La sincronización está demorando. Reintentamos la conexión; " +
                    "si continúa, vuelve al lobby y reingresa a la sala.",
                duration = GameNotice.Duration.LONG
            )
        }
        if (decision.shouldPublishPresence) {
            markOnlineGameplayPresence(PLAYER_STATE_CONNECTED)
        }
        autoAdvanceHandler.postDelayed(
            onlineSyncWatchdogRunnable,
            OnlineSyncWatchdog.CHECK_INTERVAL_MS
        )
    }

    private fun startOnlinePlayersPresenceListener() {
        if (!isOnlineGameplay() || onlinePlayersListener != null) return
        onlinePlayersListener = FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("players_presence_listener_failure roomId=$onlinePartidaId uid=$onlinePlayerId", error)
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()
                onlineVoteReadyStates = documents.map { document ->
                    OnlineVoteReadyState(
                        uid = document.id,
                        playerName = document.getString(OnlineRoomFirestore.FIELD_NAME).orEmpty(),
                        ready = document.getBoolean(FIELD_READY_TO_VOTE) == true,
                        round = document.getLong(FIELD_READY_TO_VOTE_ROUND)?.toInt() ?: -1,
                        phaseIndex = document.getLong(FIELD_READY_TO_VOTE_PHASE_INDEX)?.toInt() ?: -1
                    )
                }
                val players = documents
                    .map { document ->
                        val legacyConnected = document.getString(
                            OnlineRoomFirestore.FIELD_PLAYER_STATE
                        ) == PLAYER_STATE_CONNECTED
                        val presence = realtimePresenceStates[document.id]
                        OnlinePresencePlayer(
                            id = document.id,
                            name = document.getString(OnlineRoomFirestore.FIELD_NAME).orEmpty(),
                            order = document.getLong(OnlineRoomFirestore.FIELD_PLAYER_ORDER)?.toInt()
                                ?: Int.MAX_VALUE,
                            state = if (isOnlineUidConnected(document.id, legacyConnected)) {
                                PLAYER_STATE_CONNECTED
                            } else {
                                PLAYER_STATE_DISCONNECTED
                            },
                            activeInMatch = document.getBoolean(OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH) != false,
                            lastSeenLocalMs = presence?.changedAtMs?.takeIf { it > 0L }
                                ?: document.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL)
                                ?: 0L,
                            registered = document
                                .getString(PlayerPublicIdentity.FIELD_PUBLIC_ID)
                                .orEmpty()
                                .isNotBlank()
                        )
                    }
                    .filter { it.activeInMatch }
                    .sortedWith(compareBy<OnlinePresencePlayer> { it.order }.thenBy { it.id })
                onlinePresencePlayers = players
                handleOnlineHostHandoff(players)
                maybeResolveOnlineNightEarly()
                refreshOnlinePresentationGate()
                if (session.winner.isNotBlank()) {
                    configureWinnerReturnButton()
                    maybeCoordinateWinnerReturn()
                }
                renderReadyToVoteButton()
                maybeAdvanceOnlineReadyVote()
            }
    }

    private fun startOnlineActionsListener() {
        if (!isOnlineGameplay() || onlineActionsListener != null) return
        var query: Query = FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection("acciones")
        if (session.onlineMatchId.isNotBlank()) {
            query = query.whereEqualTo("matchId", session.onlineMatchId)
        }
        if (!onlineIsHost) {
            query = query.whereEqualTo("actorId", onlinePlayerId)
        }
        onlineActionsListener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                onlineNightActionsServerConfirmed = false
                OnlineDebugLog.e(
                    "night_actions_listener_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                    error
                )
                return@addSnapshotListener
            }
            onlineNightActionRecords = onlineActionRecordsFromSnapshot(snapshot?.documents.orEmpty())
            syncOwnOnlineDeferredActionSubmission()
            syncOwnOnlinePayadorSelections()
            refreshVisibleActionMarks()
            maybeApplyOnlinePayadorContrapunto()
            maybeApplyOnlinePayadorSuspicion()
            maybeApplyOnlineMayorReveal()
            maybeApplyOnlineDesertorChoice()
            val serverConfirmed = snapshot != null &&
                !snapshot.metadata.hasPendingWrites() &&
                !snapshot.metadata.isFromCache
            onlineNightActionsServerConfirmed = serverConfirmed
            if (serverConfirmed) {
                publishTraitorPlanNotices()
                maybePublishOnlineInvestigationClueEarly(onlineNightActionRecords)
            }
            maybeResolveOnlineNightEarly(
                confirmedActions = onlineNightActionRecords.takeIf { serverConfirmed }
            )
            maybeResolveOnlineVotingEarly(snapshot?.metadata?.hasPendingWrites() == true)
        }
    }

    private fun startOnlinePrivateClueListener() {
        if (!isOnlineGameplay() || onlinePrivateClueListener != null) return
        onlinePrivateClueListener = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("repartos")
            .document(onlinePlayerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e(
                        "private_clue_listener_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                        error
                    )
                    return@addSnapshotListener
                }
                applyOnlineInvestigationClue(snapshot)
            }
    }

    private fun applyOnlineInvestigationClue(snapshot: DocumentSnapshot?) {
        if (snapshot == null || !snapshot.exists() || !::session.isInitialized) return
        if (GameEngine.humanPlayer(session).role?.key != RoleCatalog.POLICIA) return
        val clue = snapshot.get(FIELD_PRIVATE_INVESTIGATION_CLUE).asStringAnyMap() ?: return
        val matchId = (clue["matchId"] as? String).orEmpty()
        val round = (clue["ronda"] as? Number)?.toInt() ?: return
        val phaseIndex = (clue["phaseIndex"] as? Number)?.toInt() ?: return
        val targetName = (clue["objetivoNombre"] as? String).orEmpty()
        val result = (clue["resultado"] as? String).orEmpty()
        if (
            matchId != session.onlineMatchId ||
            round != session.round ||
            targetName.isBlank() ||
            result !in PRIVATE_INVESTIGATION_RESULTS
        ) {
            return
        }
        val clueKey = "$matchId|$round|$phaseIndex|$targetName|$result"
        if (clueKey == lastOnlineInvestigationClueKey) return
        lastOnlineInvestigationClueKey = clueKey

        val updated = session.copy(
            investigatedPlayer = targetName,
            investigatedResult = result
        )
        session = updated.copy(privateHint = GameEngine.privateRoleHint(updated))
        OnlineDebugLog.i(
            "private_investigation_clue_received roomId=$onlinePartidaId uid=$onlinePlayerId round=$round target=$targetName result=$result"
        )
        feedbackState.submit(
            GameplayFeedbackSpec(
                type = GameplayFeedbackType.PRIVATE_RESULT,
                title = "RESPUESTA PRIVADA",
                message = "$targetName parece ${result.uppercase()}.",
                target = targetName,
                tone = GameplayActionTone.INVESTIGATE,
                durationMs = INFORMATION_FEEDBACK_DURATION_MS
            )
        )
        blockingFeedbackPeriod = GameplayPeriod.NIGHT
        renderGame()
        showPendingPrivateFeedback()
    }

    private fun maybeResolveOnlineVotingEarly(hasPendingWrites: Boolean) {
        val tieVote = session.phase == GamePhase.DESEMPATE_VOTACION
        if (session.phase != GamePhase.VOTACION && !tieVote) return
        val expectedPhase = if (tieVote) {
            GamePhase.DESEMPATE_VOTACION.name
        } else {
            GamePhase.VOTACION.name
        }
        val requiredActorIds = session.players.indices.mapNotNullTo(linkedSetOf()) { index ->
            if (!GameEngine.canVote(session.players[index])) return@mapNotNullTo null
            session.onlinePlayerUids.getOrNull(index)?.takeIf(String::isNotBlank)
        }
        val actedActorIds = onlineNightActionRecords.asSequence()
            .filter { onlineRecordMatchesCurrentWindow(session, it) }
            .filter { it.phaseName == expectedPhase && it.action == "votar" }
            .filter { it.actorId in requiredActorIds }
            .map { it.actorId }
            .toSet()
        if (
            !onlineVoteResolutionInProgress &&
            OnlineVoteReadyGate.shouldResolve(
                isCoordinator = onlineIsHost,
                requiredActorIds = requiredActorIds,
                actedActorIds = actedActorIds,
                hasPendingWrites = hasPendingWrites
            )
        ) {
            OnlineDebugLog.i(
                "vote_ready_early roomId=$onlinePartidaId match=${session.onlineMatchId} round=${session.round} acted=${actedActorIds.size}/${requiredActorIds.size}"
            )
            resolveOnlineVotingFromFirestore(tieVote)
        }
    }

    private fun restartOnlineActionsListenerForAuthority() {
        onlineActionsListener?.remove()
        onlineActionsListener = null
        onlinePrivateClueListener?.remove()
        onlinePrivateClueListener = null
        onlineNightActionRecords = emptyList()
        onlineNightActionsServerConfirmed = false
        startOnlineActionsListener()
        startOnlinePrivateClueListener()
    }

    private fun currentOnlinePayadorActions(): List<OnlineActionRecord> {
        val payador = session.players.firstOrNull { it.alive && it.role?.key == RoleCatalog.PAYADOR }
            ?: return emptyList()
        val payadorIndex = session.players.indexOf(payador)
        val expectedUid = session.onlinePlayerUids.getOrNull(payadorIndex).orEmpty()
        val validTargets = session.players
            .filter { it.alive && it.name != payador.name }
            .mapTo(mutableSetOf()) { it.name }
        return OnlineActionResolver.payadorTargets(
            records = onlineNightActionRecords,
            matchId = session.onlineMatchId,
            round = session.round,
            phaseIndex = session.phaseIndex,
            actorName = payador.name,
            actorId = expectedUid,
            validTargets = validTargets
        )
    }

    private fun syncOwnOnlinePayadorSelections() {
        if (!isOnlinePayadorSelectionWindow()) return
        submittedOnlinePayadorTargets += currentOnlinePayadorActions()
            .filter { it.actorId == onlinePlayerId }
            .map { it.targetName }
    }

    private fun syncOwnOnlineDeferredActionSubmission() {
        if (!isOnlineDeferredActionWindow()) return
        val acceptedActions = when {
            isOnlineNightActionWindow() -> onlineNightActionsForRole(
                GameEngine.humanPlayer(session).role?.key.orEmpty()
            )
            isOnlinePayadorSuspicionWindow() -> setOf("senalar_contrapunto")
            isOnlineVotingActionWindow() -> setOf("votar")
            else -> emptySet()
        }
        val alreadyRecorded = onlineNightActionRecords.any { record ->
            onlineRecordMatchesCurrentWindow(session, record) &&
                record.actorId == onlinePlayerId &&
                record.action in acceptedActions
        }
        if (alreadyRecorded) {
            submittedOnlineNightActions += onlineDeferredActionKey()
        }
    }

    private fun maybeApplyOnlinePayadorContrapunto() {
        if (
            !isOnlineGameplay() ||
            !onlineIsHost ||
            session.phase != GamePhase.DIA_DEBATE ||
            session.payadorUsed
        ) {
            return
        }
        val actions = currentOnlinePayadorActions()
        if (actions.size < 2) return
        val before = session
        val afterFirst = GameEngine.chooseContrapuntoPlayer(session, actions[0].targetName)
        val resolved = GameEngine.chooseContrapuntoPlayer(afterFirst, actions[1].targetName)
        if (resolved.phase != GamePhase.CONTRAPUNTO || !resolved.payadorUsed) return
        session = resolved
        recordOnlinePhaseAdvance(before, resolved)
        chatController.onPhaseSettled()
        clearSelection()
        renderGame()
    }

    private fun maybeApplyOnlinePayadorSuspicion() {
        if (!isOnlineGameplay() || !onlineIsHost || session.phase != GamePhase.CONTRAPUNTO) return
        val payador = session.players.firstOrNull { it.alive && it.role?.key == RoleCatalog.PAYADOR }
            ?: return
        val payadorIndex = session.players.indexOf(payador)
        val expectedUid = session.onlinePlayerUids.getOrNull(payadorIndex).orEmpty()
        val action = onlineNightActionRecords
            .asSequence()
            .filter { onlineRecordMatchesCurrentWindow(session, it) }
            .filter { it.phaseName == GamePhase.CONTRAPUNTO.name }
            .filter { it.action == "senalar_contrapunto" }
            .filter { it.actorName == payador.name }
            .filter { expectedUid.isBlank() || it.actorId == expectedUid }
            .filter { it.targetName in session.contrapuntoPlayers }
            .sortedBy { it.createdAtLocal }
            .firstOrNull()
            ?: return
        val before = session
        val actionSession = session.copy(
            players = session.players.map { it.copy(isHuman = it.name == payador.name) }
        )
        val resolvedAsPayador = GameEngine.resolveContrapunto(actionSession, action.targetName)
        val resolved = resolvedAsPayador.copy(
            players = session.players,
            privateHint = if (payador.isHuman) {
                resolvedAsPayador.privateHint
            } else {
                session.privateHint
            }
        )
        if (resolved.phase != GamePhase.VOTACION) return
        session = resolved
        recordOnlinePhaseAdvance(before, resolved)
        chatController.onPhaseSettled()
        clearSelection()
        renderGame()
    }

    private fun maybeResolveOnlineNightEarly(
        confirmedActions: List<OnlineActionRecord>? = null
    ) {
        if (!isOnlineGameplay() || !::session.isInitialized || !isNightPhase(session.phase)) {
            autoAdvanceHandler.removeCallbacks(onlineNightGateRunnable)
            onlineNightGateKey = ""
            onlineNightGateStartedAtMs = 0L
            onlineNightGateFloorMs = 0L
            onlineNightAllActionsReadyAtMs = 0L
            onlineNightPostActionDelayMs = 0L
            onlineNightTimerExpired = false
            return
        }
        val key = "${session.onlineMatchId}|${session.round}|${session.phaseIndex}"
        if (onlineNightGateKey != key) {
            onlineNightGateKey = key
            onlineNightGateStartedAtMs = SystemClock.elapsedRealtime()
            onlineNightGateFloorMs = OnlineNightReadyGate.randomFloorMs()
            onlineNightAllActionsReadyAtMs = 0L
            onlineNightPostActionDelayMs = 0L
            onlineNightTimerExpired = false
            autoAdvanceHandler.removeCallbacks(onlineNightGateRunnable)
            autoAdvanceHandler.postDelayed(onlineNightGateRunnable, onlineNightGateFloorMs)
            OnlineDebugLog.i(
                "night_secret_floor roomId=$onlinePartidaId match=${session.onlineMatchId} round=${session.round} floorMs=$onlineNightGateFloorMs"
            )
        }
        val oracleCandidateCount = GameEngine.oracleCandidates(session).size
        val requiredActorIds = onlinePresencePlayers.mapNotNull { presence ->
            if (presence.state != PLAYER_STATE_CONNECTED) return@mapNotNull null
            val playerIndex = session.onlinePlayerUids.indexOf(presence.id)
                .takeIf { it >= 0 }
                ?: presence.order
            val player = session.players.getOrNull(playerIndex) ?: return@mapNotNull null
            val roleKey = player.role?.key.orEmpty()
            if (
                player.alive &&
                OnlineNightReadyGate.roleRequiresAction(
                    roleKey = roleKey,
                    round = session.round,
                    oracleUsed = session.oracleUsed,
                    oracleCandidateCount = oracleCandidateCount
                )
            ) {
                presence.id
            } else {
                null
            }
        }.toSet()
        val expectedActionsByActor = onlinePresencePlayers.mapNotNull { presence ->
            val playerIndex = session.onlinePlayerUids.indexOf(presence.id)
                .takeIf { it >= 0 }
                ?: presence.order
            val roleKey = session.players.getOrNull(playerIndex)?.role?.key ?: return@mapNotNull null
            val actions = onlineNightActionsForRole(roleKey)
            if (actions.isEmpty()) null else presence.id to actions
        }.toMap()
        val actedActorIds = onlineNightActionRecords
            .asSequence()
            .filter { it.matchId == session.onlineMatchId }
            .filter { it.round == session.round && it.phaseIndex == session.phaseIndex }
            .filter { record -> record.action in expectedActionsByActor[record.actorId].orEmpty() }
            .map { it.actorId }
            .filter { it.isNotBlank() }
            .toSet()
        val nowMs = SystemClock.elapsedRealtime()
        val everyRequiredActorActed = requiredActorIds.all { it in actedActorIds }
        if (requiredActorIds.isNotEmpty() && everyRequiredActorActed) {
            if (onlineNightAllActionsReadyAtMs <= 0L) {
                onlineNightAllActionsReadyAtMs = nowMs
                onlineNightPostActionDelayMs = OnlineNightReadyGate.randomPostActionDelayMs()
                OnlineDebugLog.i(
                    "night_actions_ready roomId=$onlinePartidaId round=${session.round} postDelayMs=$onlineNightPostActionDelayMs"
                )
            }
        } else {
            onlineNightAllActionsReadyAtMs = 0L
            onlineNightPostActionDelayMs = 0L
        }
        val elapsedMs = nowMs - onlineNightGateStartedAtMs
        val allActionsReadyForMs = onlineNightAllActionsReadyAtMs
            .takeIf { it > 0L }
            ?.let { nowMs - it }
            ?: 0L
        val remainingFloorMs = (onlineNightGateFloorMs - elapsedMs).coerceAtLeast(0L)
        val remainingPostActionMs = if (requiredActorIds.isNotEmpty() && everyRequiredActorActed) {
            (onlineNightPostActionDelayMs - allActionsReadyForMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val remainingResolutionDelayMs = maxOf(remainingFloorMs, remainingPostActionMs)
        if (onlineIsHost && everyRequiredActorActed && remainingResolutionDelayMs > 0L) {
            autoAdvanceHandler.removeCallbacks(onlineNightGateRunnable)
            autoAdvanceHandler.postDelayed(onlineNightGateRunnable, remainingResolutionDelayMs)
        }
        if (
            !onlineNightResolutionInProgress &&
            OnlineNightReadyGate.shouldResolve(
                isCoordinator = onlineIsHost,
                requiredActorIds = requiredActorIds,
                actedActorIds = actedActorIds,
                elapsedMs = elapsedMs,
                floorMs = onlineNightGateFloorMs,
                allActionsReadyForMs = allActionsReadyForMs,
                postActionDelayMs = onlineNightPostActionDelayMs
            )
        ) {
            OnlineDebugLog.i(
                "night_ready_early roomId=$onlinePartidaId match=${session.onlineMatchId} round=${session.round} acted=${actedActorIds.size}/${requiredActorIds.size} elapsedMs=$elapsedMs"
            )
            val latestConfirmedActions = confirmedActions
                ?: onlineNightActionRecords.takeIf { onlineNightActionsServerConfirmed }
            if (latestConfirmedActions != null) {
                resolveOnlineNightWindowFromConfirmedActions(
                    actions = latestConfirmedActions,
                    countAfkMisses = false
                )
            } else {
                resolveOnlineNightWindowFromFirestore(countAfkMisses = false)
            }
        }
    }

    private fun handleOnlineNightGateFloorReached() {
        if (
            !isOnlineGameplay() ||
            !::session.isInitialized ||
            !isNightPhase(session.phase) ||
            onlineNightResolutionInProgress
        ) {
            return
        }
        if (onlineNightTimerExpired && onlineIsHost) {
            val confirmedActions = onlineNightActionRecords
                .takeIf { onlineNightActionsServerConfirmed }
            if (confirmedActions != null) {
                resolveOnlineNightWindowFromConfirmedActions(
                    actions = confirmedActions,
                    countAfkMisses = true
                )
            } else {
                resolveOnlineNightWindowFromFirestore(countAfkMisses = true)
            }
            return
        }
        maybeResolveOnlineNightEarly(
            confirmedActions = onlineNightActionRecords
                .takeIf { onlineNightActionsServerConfirmed }
        )
    }

    private fun onlineNightActionsForRole(roleKey: String): Set<String> {
        return when (roleKey) {
            RoleCatalog.ASESINO, RoleCatalog.ESPIA -> setOf("matar")
            RoleCatalog.MERCENARIO -> setOf("silenciar")
            RoleCatalog.POLICIA -> setOf("investigar")
            RoleCatalog.MEDICO -> setOf("salvar")
            RoleCatalog.ORACULO -> setOf("invitar_muerto", "guardar_poder")
            else -> emptySet()
        }
    }

    private fun handleOnlineHostHandoff(players: List<OnlinePresencePlayer>) {
        if (!isOnlineGameplay() || players.isEmpty() || onlineHostHandoffInProgress) return
        val activeHostId = onlineActiveHostId.takeIf { it.isNotBlank() } ?: return
        val participants = players.map { player ->
            OnlineLobbyParticipant(
                id = player.id,
                connected = player.state == PLAYER_STATE_CONNECTED,
                ready = true,
                activeInMatch = player.activeInMatch,
                order = player.order,
                lastSeenLocalMs = player.lastSeenLocalMs,
                alive = session.players.getOrNull(player.order)?.alive == true,
                registered = player.registered
            )
        }
        if (!OnlineLobbyRules.needsHostHandoff(participants, activeHostId)) {
            onlineGuestHostWindowStartedAtMs = 0L
            return
        }
        val registeredCandidate = OnlineLobbyRules.hostHandoffCandidate(
            players = participants,
            activeHostId = activeHostId,
            allowGuests = false
        )
        val candidate = if (registeredCandidate != null) {
            onlineGuestHostWindowStartedAtMs = 0L
            registeredCandidate
        } else {
            // No queda ninguna cuenta registrada conectada. Antes de dejar que un
            // invitado tome el relevo se espera un rato, por si el anfitrion vuelve o entra
            // alguien con cuenta; pasado ese tiempo, un invitado de anfitrion es mejor que una
            // partida congelada para toda la mesa.
            if (onlineGuestHostWindowStartedAtMs == 0L) {
                onlineGuestHostWindowStartedAtMs = System.currentTimeMillis()
                OnlineDebugLog.w(
                    "host_handoff_guest_window_open roomId=$onlinePartidaId uid=$onlinePlayerId"
                )
            }
            val waitedMs = System.currentTimeMillis() - onlineGuestHostWindowStartedAtMs
            if (waitedMs < GUEST_HOST_GRACE_MS) {
                // El relevo se dispara por snapshots y puede no llegar ninguno mientras se
                // espera: sin este reintento la ventana se cumple y nadie la mira.
                scheduleGuestHostWindowRecheck(GUEST_HOST_GRACE_MS - waitedMs)
                return
            }
            OnlineLobbyRules.hostHandoffCandidate(
                players = participants,
                activeHostId = activeHostId,
                allowGuests = true
            )
        } ?: return
        if (candidate.id == onlinePlayerId) {
            claimOnlineHostHandoff(activeHostId)
        }
    }

    private fun scheduleGuestHostWindowRecheck(delayMs: Long) {
        autoAdvanceHandler.removeCallbacks(guestHostWindowRunnable)
        autoAdvanceHandler.postDelayed(guestHostWindowRunnable, delayMs.coerceAtLeast(0L))
    }

    private fun claimOnlineHostHandoff(previousHostId: String) {
        onlineHostHandoffInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION).document(onlinePartidaId)
        val previousHostReference = roomReference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(previousHostId)
        val candidateReference = roomReference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(onlinePlayerId)
        OnlineDebugLog.w(
            "host_handoff_claim_requested roomId=$onlinePartidaId previousHost=$previousHostId candidate=$onlinePlayerId"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            val currentHostId = room.getString(OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID)
                ?.takeIf { it.isNotBlank() }
                ?: previousHostId
            val previousHost = transaction.get(previousHostReference)
            val candidate = transaction.get(candidateReference)
            if (currentHostId != previousHostId) {
                return@runTransaction false
            }
            val previousHostParticipant = OnlineLobbyParticipant(
                id = previousHostId,
                connected = isOnlineUidConnected(
                    previousHostId,
                    previousHost.getString(OnlineRoomFirestore.FIELD_PLAYER_STATE) ==
                        PLAYER_STATE_CONNECTED
                ),
                ready = true,
                activeInMatch = previousHost.getBoolean(OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH) != false,
                order = previousHost.getLong(OnlineRoomFirestore.FIELD_PLAYER_ORDER)?.toInt() ?: Int.MAX_VALUE,
                lastSeenLocalMs = previousHost.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL) ?: 0L
            )
            // Morir no impide coordinar: el anfitrion sigue ejecutando el motor como
            // espectador. El relevo solo se habilita cuando realmente se desconecta.
            if (previousHostParticipant.connected) {
                return@runTransaction false
            }
            if (!isOnlineUidConnected(
                    onlinePlayerId,
                    candidate.getString(OnlineRoomFirestore.FIELD_PLAYER_STATE) ==
                        PLAYER_STATE_CONNECTED
                )
            ) {
                return@runTransaction false
            }
            transaction.update(
                roomReference,
                mapOf(
                    OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID to onlinePlayerId,
                    OnlineRoomFirestore.FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener { claimed ->
            onlineHostHandoffInProgress = false
            if (claimed == true) {
                OnlineDebugLog.w(
                    "host_handoff_claim_success roomId=$onlinePartidaId previousHost=$previousHostId newHost=$onlinePlayerId"
                )
                promoteToOnlineHost("handoff_claim")
            }
        }.addOnFailureListener { error ->
            onlineHostHandoffInProgress = false
            OnlineDebugLog.e(
                "host_handoff_claim_failure roomId=$onlinePartidaId previousHost=$previousHostId candidate=$onlinePlayerId",
                error
            )
        }
    }

    private fun promoteToOnlineHost(reason: String) {
        if (onlineIsHost || onlineHostPromotionInProgress) return
        onlineHostPromotionInProgress = true
        setOnlineAwaitingHostAdvance(true)
        autoAdvanceHandler.removeCallbacks(onlineHostPromotionRetryRunnable)
        OnlineDebugLog.w(
            "host_role_recovery_requested roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason"
        )
        FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection("repartos")
            .get()
            .addOnSuccessListener { snapshot ->
                if (onlineActiveHostId != onlinePlayerId) {
                    onlineHostPromotionInProgress = false
                    return@addOnSuccessListener
                }
                val assignments = snapshot.documents
                    .filter { document ->
                        document.getString("matchId") == session.onlineMatchId
                    }
                    .flatMap { document ->
                        (document.get("rolesVisibles") as? List<*>)
                            .orEmpty()
                            .mapNotNull { it.asStringAnyMap() }
                    }
                val restored = OnlineHostRoleRecovery.restore(session, assignments)
                if (restored == null) {
                    handleOnlineHostRoleRecoveryFailure(
                        reason = "incomplete_roles_${assignments.size}_${session.players.size}",
                        error = null
                    )
                    return@addOnSuccessListener
                }
                session = restored
                onlineHostPromotionInProgress = false
                finishOnlineHostPromotion(reason)
            }
            .addOnFailureListener { error ->
                handleOnlineHostRoleRecoveryFailure("read_failed", error)
            }
    }

    private fun finishOnlineHostPromotion(reason: String) {
        if (onlineIsHost || onlineActiveHostId != onlinePlayerId) return
        onlineIsHost = true
        onlineActiveHostId = onlinePlayerId
        OnlineDebugLog.w(
            "host_promoted roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason phase=${session.phase.name} round=${session.round} roles=${session.players.count { it.role != null }}/${session.players.size}"
        )
        restartOnlineActionsListenerForAuthority()
        syncRealtimeGameplayAccess()
        if (onlineAwaitingHostAdvance) {
            setOnlineAwaitingHostAdvance(false)
            autoAdvanceHandler.post { renderGame() }
        }
        // Si un rol pidio algo mientras el anfitrion anterior se caia, el pedido ya esta
        // escrito y sin aplicar: este dispositivo lo resuelve al tomar el host.
        maybeApplyOnlinePayadorContrapunto()
        maybeApplyOnlinePayadorSuspicion()
        maybeApplyOnlineMayorReveal()
        maybeApplyOnlineDesertorChoice()
        maybeResolveOnlineNightEarly()
        refreshOnlinePresentationGate()
        if (session.winner.isNotBlank()) {
            configureWinnerReturnButton()
            maybeCoordinateWinnerReturn()
        }
    }

    private fun handleOnlineHostRoleRecoveryFailure(reason: String, error: Exception?) {
        onlineHostPromotionInProgress = false
        if (error == null) {
            OnlineDebugLog.e(
                "host_role_recovery_failure roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason",
                IllegalStateException("No se recupero el reparto completo")
            )
        } else {
            OnlineDebugLog.e(
                "host_role_recovery_failure roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason",
                error
            )
        }
        // Mantener la mesa pausada es preferible a publicar una victoria o una muerte con un
        // reparto incompleto. Firestore puede tardar un instante en habilitar la lectura justo
        // despues del traspaso, por eso el reintento es automatico.
        setOnlineAwaitingHostAdvance(true)
        lastPublishedOnlineStateKey = ""
        if (!isFinishing && onlineActiveHostId == onlinePlayerId) {
            autoAdvanceHandler.removeCallbacks(onlineHostPromotionRetryRunnable)
            autoAdvanceHandler.postDelayed(onlineHostPromotionRetryRunnable, HOST_ROLE_RECOVERY_RETRY_MS)
        }
        renderGame()
    }

    private fun demoteFromOnlineHost(reason: String) {
        if (!onlineIsHost && !onlineHostPromotionInProgress) return
        onlineHostPromotionInProgress = false
        autoAdvanceHandler.removeCallbacks(onlineHostPromotionRetryRunnable)
        onlineIsHost = false
        setOnlineAwaitingHostAdvance(true)
        lastPublishedAuthoritativeOnlineStateKey = ""
        OnlineDebugLog.w(
            "host_demoted roomId=$onlinePartidaId uid=$onlinePlayerId reason=$reason activeHost=$onlineActiveHostId phase=${session.phase.name} round=${session.round}"
        )
        restartOnlineActionsListenerForAuthority()
        refreshOnlinePresentationGate()
        renderGame()
    }

    private fun actionSession(): GameSession {
        if (!isNightPhase(session.phase)) return session
        if (!isOnlineGameplay()) {
            return localPendingNightActionSession()
        }
        val human = GameEngine.humanPlayer(session)
        if (!GameEngine.isAlive(human)) return session
        val actionPhase = when (human.role?.key) {
            RoleCatalog.ASESINO,
            RoleCatalog.ESPIA -> GamePhase.NOCHE_ASESINO
            RoleCatalog.MERCENARIO -> GamePhase.NOCHE_MERCENARIO
            RoleCatalog.POLICIA -> GamePhase.NOCHE_POLICIA
            RoleCatalog.MEDICO -> GamePhase.NOCHE_MEDICO
            RoleCatalog.ORACULO -> GamePhase.NOCHE_ORACULO
            else -> null
        } ?: return session
        return session.copy(phase = actionPhase)
    }

    private fun localPendingNightActionSession(): GameSession {
        if (GameEngine.requiresHumanInput(session)) return session

        var preview = session
        var guard = 0
        while (
            isNightPhase(preview.phase) &&
            !GameEngine.requiresHumanInput(preview) &&
            guard < MAX_NIGHT_SKIP_STEPS
        ) {
            val advanced = advanceNightSessionWithoutRendering(preview)
            if (advanced == preview) break
            preview = advanced
            guard += 1
        }
        return preview
    }

    private fun isOnlineNightActionWindow(): Boolean {
        return isOnlineGameplay() && isNightPhase(session.phase)
    }

    private fun isOnlineVotingActionWindow(): Boolean {
        return isOnlineGameplay() &&
            (
                session.phase == GamePhase.VOTACION ||
                    session.phase == GamePhase.DESEMPATE_VOTACION ||
                    session.phase == GamePhase.ALCALDE_DESEMPATE
                )
    }

    private fun isOnlinePayadorSuspicionWindow(): Boolean {
        if (!isOnlineGameplay() || session.phase != GamePhase.CONTRAPUNTO) return false
        val human = GameEngine.humanPlayer(session)
        return human.alive && human.role?.key == RoleCatalog.PAYADOR
    }

    private fun isOnlineDeferredActionWindow(): Boolean {
        return isOnlineNightActionWindow() ||
            isOnlineVotingActionWindow() ||
            isOnlinePayadorSuspicionWindow()
    }

    private fun onlineDeferredActionKey(): String {
        val human = GameEngine.humanPlayer(session)
        val roleKey = human.role?.key.orEmpty()
        val matchKey = session.onlineMatchId.ifBlank { onlinePartidaId }
        return "$matchKey:$onlinePlayerId:${session.round}:${session.phase.name}:${session.phaseIndex}:$roleKey"
    }

    private fun onlineDeferredActionSubmitted(): Boolean {
        return isOnlineDeferredActionWindow() &&
            (
                onlineDeferredActionKey() in submittedOnlineNightActions ||
                    onlineDeferredActionKey() in pendingOnlineActionSubmissions
                )
    }

    private fun onlineDeferredActionPending(): Boolean {
        return isOnlineDeferredActionWindow() &&
            onlineDeferredActionKey() in pendingOnlineActionSubmissions
    }

    private fun canActOnTarget(targetName: String): Boolean {
        if (onlineDeferredActionSubmitted()) return false
        if (
            isOnlinePayadorSelectionWindow() &&
            (
                submittedOnlinePayadorTargets.size + pendingOnlinePayadorTargets.size >= 2 ||
                    targetName in submittedOnlinePayadorTargets ||
                    targetName in pendingOnlinePayadorTargets
                )
        ) {
            return false
        }
        return GameEngine.canActOnTarget(actionSession(), targetName)
    }

    private fun targetActionLabel(targetName: String): String {
        if (!canActOnTarget(targetName)) return ""
        return GameEngine.targetActionLabel(actionSession(), targetName)
    }

    private fun validHumanTargets(): List<GamePlayer> {
        return session.players.filter { canActOnTarget(it.name) }
    }

    private fun confirmedTargetActionLabel(): String? {
        if (selectedTarget.isBlank() || !canActOnTarget(selectedTarget)) return null
        return targetActionLabel(selectedTarget)
            .takeIf { it.isNotBlank() }
            ?.let { if (it == "CONTRAPUNTO") "SEÑALAR" else it }
    }

    private fun canHumanMedicSelfProtect(): Boolean {
        val current = actionSession()
        if (current.phase != GamePhase.NOCHE_MEDICO || onlineDeferredActionSubmitted()) return false
        val human = GameEngine.humanPlayer(current)
        return GameEngine.isHumanRoleTurn(current, "medico") &&
            GameEngine.canActOnTarget(current, human.name)
    }

    private fun requiresHumanInput(): Boolean {
        if (onlineDeferredActionSubmitted()) return false
        return GameEngine.requiresHumanInput(actionSession())
    }

    private fun canHumanOracleChooseThisNight(): Boolean {
        val current = actionSession()
        return current.phase == GamePhase.NOCHE_ORACULO &&
            current.round > 1 &&
            !current.oracleUsed &&
            GameEngine.isHumanRoleTurn(current, RoleCatalog.ORACULO) &&
            GameEngine.oracleCandidates(current).isNotEmpty() &&
            !onlineDeferredActionSubmitted()
    }

    private fun isHumanRoleTurn(roleKey: String): Boolean {
        if (onlineDeferredActionSubmitted()) return false
        return GameEngine.isHumanRoleTurn(actionSession(), roleKey)
    }

    private fun mergeOnlineNightActionResult(current: GameSession, resolved: GameSession): GameSession {
        return current.copy(
            nightKillTarget = resolved.nightKillTarget,
            assassinVotes = resolved.assassinVotes,
            protectedPlayer = resolved.protectedPlayer,
            nightSilenceTarget = resolved.nightSilenceTarget,
            investigatedPlayer = resolved.investigatedPlayer,
            investigatedResult = resolved.investigatedResult,
            privateHint = resolved.privateHint,
            actionHistory = resolved.actionHistory,
            oracleUsed = resolved.oracleUsed,
            oracleInvitedPlayer = resolved.oracleInvitedPlayer,
            oracleRevealPending = resolved.oracleRevealPending
        )
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

    private fun collectNoDeathEvent() {
        if (
            GameplayTableUi.wasNoDeathAtDawn(session) &&
            lastNoDeathRevealRound != session.round
        ) {
            pendingNoDeathReveal = true
            lastNoDeathRevealRound = session.round
        }
    }

    private fun renderNarrator(
        phaseText: GameplayPhaseText,
        publicMessage: String,
        eventChanged: Boolean
    ) {
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

    private fun renderEventLog(publicMessage: String, phaseText: GameplayPhaseText) {
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
        val previousLastEvent = lastRenderedEventMessages.lastOrNull()
        if (latestEvent != previousLastEvent) {
            maybeShowCentralPublicEvent(latestEvent)
        }
        val visibleEvents = if (isEventLogExpanded) allEvents.takeLast(5) else listOf(latestEvent)
        if (
            visibleEvents == lastRenderedEventMessages &&
            lastRenderedEventExpanded == isEventLogExpanded
        ) {
            return
        }
        eventLogContainer.removeAllViews()
        visibleEvents.forEachIndexed { index, message ->
            val row = createEventRow(message)
            eventLogContainer.addView(row)
            if (message != previousLastEvent && index == visibleEvents.lastIndex) {
                animateNewEventRow(row)
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

    private fun animateNewEventRow(row: View) {
        row.animate().cancel()
        row.alpha = 0f
        row.translationY = dp(8).toFloat()
        row.scaleX = 0.985f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(row, View.TRANSLATION_Y, row.translationY, 0f),
                ObjectAnimator.ofFloat(row, View.SCALE_X, 0.985f, 1f)
            )
            duration = 280L
            interpolator = DecelerateInterpolator()
            start()
        }

        eventLogColorBar.animate().cancel()
        eventLogColorBar.pivotX = 0f
        eventLogColorBar.scaleX = 1f
        eventLogColorBar.animate()
            .scaleX(2.35f)
            .setDuration(110L)
            .withEndAction {
                eventLogColorBar.animate()
                    .scaleX(1f)
                    .setDuration(260L)
                    .start()
            }
            .start()

        eventLogSummary.animate().cancel()
        eventLogSummary.alpha = 0.58f
        eventLogSummary.translationX = dp(8).toFloat()
        eventLogSummary.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun maybeShowCentralPublicEvent(message: String) {
        val spec = centralPublicEventSpec(message) ?: return
        val key = "${session.round}:${session.phaseIndex}:${spec.title}:${spec.message}"
        if (key == lastPresentedCentralEventKey) return
        lastPresentedCentralEventKey = key
        showCentralPublicEventBanner(spec)
    }

    private fun centralPublicEventSpec(message: String): CentralPublicEventSpec? {
        val clean = message.trim()
        if (clean.isBlank()) return null

        val text = clean.lowercase()
        return when {
            text.contains("alcalde") && (text.contains("revelo") || text.contains("revelado")) ->
                CentralPublicEventSpec(
                    icon = "A",
                    label = "AUTORIDAD REVELADA",
                    title = "ALCALDE REVELADO",
                    message = "El pueblo ya sabe quien tiene la ultima palabra.",
                    colorHex = CENTRAL_EVENT_VOTE_HEX,
                    iconColorHex = CENTRAL_EVENT_VOTE_HEX
                )
            text.contains("expulsad") && text.contains("inactividad") ->
                CentralPublicEventSpec(
                    icon = "!",
                    label = "INACTIVIDAD",
                    title = "FUERA DEL PUEBLO",
                    message = clean.takeIf { it.length <= 74 } ?: "Un jugador fue expulsado por ausentarse demasiado.",
                    colorHex = CENTRAL_EVENT_DANGER_HEX,
                    iconColorHex = CENTRAL_EVENT_DANGER_HEX
                )
            // "Noche sin muertes" ya se anuncia en el chat central (mensaje de Dios);
            // no abrimos una ventana aparte para ese caso.
            else -> null
        }
    }

    private fun showCentralPublicEventBanner(spec: CentralPublicEventSpec) {
        if (!::centralPublicEventBanner.isInitialized) return
        autoAdvanceHandler.removeCallbacks(centralPublicEventDismissRunnable)
        centralPublicEventAnimator?.cancel()
        centralPublicEventBanner.animate().cancel()
        centralPublicEventShine.animate().cancel()

        centralPublicEventLabel.text = spec.label
        centralPublicEventIcon.text = spec.icon
        centralPublicEventIcon.setTextColor(Color.parseColor(spec.iconColorHex))
        centralPublicEventTitle.text = spec.title
        centralPublicEventMessage.text = spec.message
        centralPublicEventTone.setBackgroundColor(Color.parseColor(spec.colorHex))

        centralPublicEventBanner.visibility = View.VISIBLE
        centralPublicEventBanner.alpha = 0f
        centralPublicEventBanner.translationY = dp(18).toFloat()
        centralPublicEventBanner.scaleX = 0.9f
        centralPublicEventBanner.scaleY = 0.9f
        centralPublicEventShine.alpha = 0f
        centralPublicEventShine.translationX = -dp(96).toFloat()

        centralPublicEventAnimator = AnimatorSet().apply {
            playSequentially(
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.TRANSLATION_Y, dp(18).toFloat(), 0f),
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.SCALE_X, 0.9f, 1.04f),
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.SCALE_Y, 0.9f, 1.04f)
                    )
                    duration = 260L
                    interpolator = DecelerateInterpolator()
                },
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.SCALE_X, 1.04f, 1f),
                        ObjectAnimator.ofFloat(centralPublicEventBanner, View.SCALE_Y, 1.04f, 1f)
                    )
                    duration = 170L
                    interpolator = AccelerateDecelerateInterpolator()
                }
            )
            interpolator = DecelerateInterpolator()
            start()
        }
        centralPublicEventBanner.post {
            if (centralPublicEventBanner.visibility != View.VISIBLE) return@post
            centralPublicEventShine.animate()
                .alpha(1f)
                .translationX(centralPublicEventBanner.width + dp(96).toFloat())
                .setStartDelay(260L)
                .setDuration(760L)
                .withEndAction {
                    centralPublicEventShine.alpha = 0f
                    centralPublicEventShine.translationX = -dp(96).toFloat()
                }
                .start()
        }
        autoAdvanceHandler.postDelayed(centralPublicEventDismissRunnable, CENTRAL_PUBLIC_EVENT_DURATION_MS)
    }

    private fun hideCentralPublicEventBanner(immediate: Boolean = false) {
        autoAdvanceHandler.removeCallbacks(centralPublicEventDismissRunnable)
        if (!::centralPublicEventBanner.isInitialized) return
        centralPublicEventAnimator?.cancel()
        centralPublicEventAnimator = null
        centralPublicEventBanner.animate().cancel()
        centralPublicEventShine.animate().cancel()
        if (immediate || centralPublicEventBanner.visibility != View.VISIBLE) {
            centralPublicEventBanner.visibility = View.GONE
            centralPublicEventBanner.alpha = 1f
            centralPublicEventBanner.translationY = 0f
            centralPublicEventBanner.scaleX = 1f
            centralPublicEventBanner.scaleY = 1f
            centralPublicEventShine.alpha = 0f
            centralPublicEventShine.translationX = -dp(96).toFloat()
            return
        }
        centralPublicEventBanner.animate()
            .alpha(0f)
            .translationY(-dp(6).toFloat())
            .setDuration(220L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                centralPublicEventBanner.visibility = View.GONE
                centralPublicEventBanner.alpha = 1f
                centralPublicEventBanner.translationY = 0f
                centralPublicEventBanner.scaleX = 1f
                centralPublicEventBanner.scaleY = 1f
                centralPublicEventShine.alpha = 0f
                centralPublicEventShine.translationX = -dp(96).toFloat()
            }
            .start()
    }

    private data class CentralPublicEventSpec(
        val icon: String,
        val label: String,
        val title: String,
        val message: String,
        val colorHex: String,
        val iconColorHex: String
    )

    private fun maybeExpandPrivateAssassinVoteLog() {
        val key = privateAssassinVoteLogKey() ?: return
        if (key == lastPresentedAssassinVoteLogKey) return

        lastPresentedAssassinVoteLogKey = key
        if (!isEventLogExpanded) {
            isEventLogExpanded = true
            chatController.onBackPressed()
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
        if (reactionUiBlocked()) return
        GameplayEffects.play(this, GameplayEffect.PANEL)
        isEventLogExpanded = !isEventLogExpanded
        if (isEventLogExpanded) {
            chatController.onBackPressed()
        }
        renderEventLogPanel(animate = true)
        lastRenderedEventExpanded = null
        renderEventLog(
            session.publicAnnouncement.ifBlank { phaseText(session.phase).subtitle },
            phaseText(session.phase)
        )
    }

    private fun renderEventLogPanel(animate: Boolean = false) {
        val targetHeight = dp(if (isEventLogExpanded) eventLogExpandedHeightDp() else eventLogCollapsedHeightDp())
        val params = eventLogPanel.layoutParams as FrameLayout.LayoutParams
        eventLogHeader.layoutParams = (eventLogHeader.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(eventLogCollapsedHeightDp())
        }
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
        text.textSize = if (isEventLogExpanded) 11.5f else 9f
        text.maxLines = if (isEventLogExpanded) 3 else 1
        text.setSingleLine(!isEventLogExpanded)
        text.setPadding(dp(9), 0, 0, 0)
        if (isEventLogExpanded) {
            row.addView(
                text,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        } else {
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
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(if (isEventLogExpanded) 5 else 2))
        row.layoutParams = params
        return row
    }

    private fun syncReactionRound() {
        if (lastReactionRound == session.round) return
        lastReactionRound = session.round
        reactionLimiter.resetOutsideRound(session.round)
        botReactionScheduleKey = ""
        botReactionScheduled = false
        autoAdvanceHandler.removeCallbacks(botReactionRunnable)
    }

    private fun renderReactionButton() {
        if (!::btnToggleEmotes.isInitialized || !::session.isInitialized) return
        btnToggleEmotes.visibility = View.VISIBLE
        val human = GameEngine.humanPlayer(session)
        val now = SystemClock.elapsedRealtime()
        val check = reactionLimiter.check(human.name, session.round, now)
        val baseMessage = reactionBaseUnavailableMessage(human)
        val limitReached = check.reason == ReactionBlockReason.ROUND_LIMIT
        btnToggleEmotes.isEnabled = baseMessage == null && !limitReached
        btnToggleEmotes.alpha = when {
            btnToggleEmotes.isEnabled && check.reason == ReactionBlockReason.COOLDOWN -> 0.78f
            btnToggleEmotes.isEnabled -> 1f
            else -> 0.42f
        }
        btnToggleEmotes.contentDescription = when {
            baseMessage != null -> baseMessage
            limitReached -> "Sin emotes disponibles esta ronda"
            check.reason == ReactionBlockReason.COOLDOWN ->
                "Emotes disponibles en ${cooldownSeconds(check.remainingCooldownMs)} segundos"
            else -> "Abrir emotes"
        }
        if (!btnToggleEmotes.isEnabled) {
            dismissReactionPalette()
        }
    }

    private fun toggleReactionPalette() {
        if (reactionPalette?.isShowing == true) {
            dismissReactionPalette()
            return
        }

        val human = GameEngine.humanPlayer(session)
        val blockMessage = reactionBaseUnavailableMessage(human)
        if (blockMessage != null) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, blockMessage)
            return
        }
        val check = reactionLimiter.check(human.name, session.round, SystemClock.elapsedRealtime())
        if (check.reason == ReactionBlockReason.ROUND_LIMIT) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, reactionBlockMessage(check))
            renderReactionButton()
            return
        }

        showReactionPalette()
    }

    private fun showReactionPalette() {
        dismissReactionPalette()
        val specs = reactionSpecsFor(GameEngine.humanPlayer(session))

        val palette = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E8211710"))
                setStroke(dp(1), getColor(R.color.accent_gold))
            }
        }

        specs.forEachIndexed { index, spec ->
            val option = ImageButton(this).apply {
                setImageResource(spec.imageRes)
                background = reactionOptionBackground(spec)
                contentDescription = spec.label
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { trySendHumanReaction(spec) }
            }
            palette.addView(
                option,
                LinearLayout.LayoutParams(dp(45), dp(45)).apply {
                    if (index > 0) leftMargin = dp(6)
                }
            )
        }

        palette.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        reactionPalette = PopupWindow(
            palette,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(10).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(
                btnToggleEmotes,
                btnToggleEmotes.width - palette.measuredWidth,
                dp(6)
            )
        }
        GameplayEffects.play(this, GameplayEffect.PANEL)
    }

    private fun reactionOptionBackground(spec: ReactionSpec): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#2A2318"))
            setStroke(dp(2), Color.parseColor(spec.toneHex))
        }
    }

    private fun trySendHumanReaction(spec: ReactionSpec) {
        val human = GameEngine.humanPlayer(session)
        val baseMessage = reactionBaseUnavailableMessage(human)
        if (baseMessage != null) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, baseMessage)
            dismissReactionPalette()
            renderReactionButton()
            return
        }

        val check = reactionLimiter.record(
            playerName = human.name,
            round = session.round,
            nowMs = SystemClock.elapsedRealtime()
        )
        if (!check.allowed) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            GameNotice.show(this, reactionBlockMessage(check))
            renderReactionButton()
            return
        }

        dismissReactionPalette()
        GameplayEffects.play(this, GameplayEffect.EMOTE)
        showReactionBubble(human.name, spec)
        if (isOnlineGameplay()) {
            chatController.sendOnlineReaction(human.name, spec.id)
        }
        renderReactionButton()
    }

    private fun reactionBaseUnavailableMessage(player: GamePlayer): String? {
        return when {
            session.winner.isNotBlank() || session.phase == GamePhase.RESULTADO ->
                "La partida ya terminó."
            session.phase == GamePhase.REPARTO -> "Primero empieza la partida."
            !GameEngine.isAlive(player) -> "No puedes tirar emotes eliminado."
            !isPublicReactionPhase(session.phase) ->
                "Los emotes se usan durante el debate y la votación."
            reactionUiBlocked() -> "Espera a que termine el evento."
            else -> null
        }
    }

    private fun reactionBlockMessage(check: ReactionCheck): String {
        return when (check.reason) {
            ReactionBlockReason.COOLDOWN ->
                "Espera ${cooldownSeconds(check.remainingCooldownMs)}s para otro emote."
            ReactionBlockReason.ROUND_LIMIT -> "Ya usaste tus emotes de esta ronda."
            ReactionBlockReason.NONE -> ""
        }
    }

    private fun cooldownSeconds(remainingMs: Long): Long {
        return ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).coerceAtLeast(1L)
    }

    private fun isPublicReactionPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.DIA_DEBATE ||
            phase == GamePhase.CONTRAPUNTO ||
            phase == GamePhase.VOTACION ||
            phase == GamePhase.RECUENTO_VOTOS ||
            phase == GamePhase.DESEMPATE_VOTACION ||
            phase == GamePhase.ALCALDE_DESEMPATE
    }

    private fun reactionUiBlocked(): Boolean {
        return isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
            isOracleRevealVisible ||
            isRolePreviewOpen ||
            isVoteResultVisible ||
            isTieVoteVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible ||
            feedbackState.pending?.blocksGameplay == true ||
            desertorDialogOpen
    }

    private fun dismissReactionPalette() {
        reactionPalette?.dismiss()
        reactionPalette = null
    }

    private fun dismissSecondaryUiForPriorityWindow() {
        if (::chatController.isInitialized) {
            chatController.closeForPriorityWindow()
        }
        dismissReactionPalette()
        clearReactionBubbles()
        GameNotice.dismissAll(this)
        PlayerProfileDialog.dismissAll(this)
        GameDialog.dismissAll(this)
        if (::eventLogPanel.isInitialized && isEventLogExpanded) {
            eventLogHeightAnimator?.cancel()
            isEventLogExpanded = false
            lastRenderedEventExpanded = null
            renderEventLogPanel(animate = false)
        }
    }

    private fun clearReactionBubbles() {
        activeReactionBubbles.values.toList().forEach { bubble ->
            bubble.animate().cancel()
            (bubble.parent as? ViewGroup)?.removeView(bubble)
        }
        activeReactionBubbles.clear()
    }

    private fun showReactionBubble(playerName: String, spec: ReactionSpec) {
        val anchor = reactionAnchorFor(playerName) ?: return
        if (anchor.width <= 0 || anchor.height <= 0 || gameplayRoot.width <= 0) {
            anchor.post { showReactionBubble(playerName, spec) }
            return
        }

        // Sonido del emote (humano y bots): canal único con "el último gana" + throttle.
        EmoteSoundEffects.play(this, spec.key)

        activeReactionBubbles.remove(playerName)?.let { oldBubble ->
            oldBubble.animate().cancel()
            (oldBubble.parent as? ViewGroup)?.removeView(oldBubble)
        }

        val humanName = GameEngine.humanPlayer(session).name
        val isHuman = playerName == humanName
        val bubbleSize = dp(if (isHuman) 58 else 46)
        val tailSize = dp(if (isHuman) 12 else 9)
        val bubbleWidth = bubbleSize
        val bubbleHeight = bubbleSize + tailSize / 2

        val bubble = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            scaleX = 0.78f
            scaleY = 0.78f
            translationY = dp(6).toFloat()
        }

        val shell = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(13).toFloat()
                setColor(Color.parseColor("#2A2318"))
                setStroke(dp(2), Color.parseColor(spec.toneHex))
            }
            elevation = dp(8).toFloat()
        }
        val icon = ImageView(this).apply {
            setImageResource(spec.imageRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = spec.label
        }
        shell.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        bubble.addView(
            shell,
            FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )

        val tail = View(this).apply {
            rotation = 45f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#2A2318"))
                setStroke(dp(1), Color.parseColor(spec.toneHex))
            }
        }
        bubble.addView(
            tail,
            FrameLayout.LayoutParams(tailSize, tailSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )

        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        gameplayRoot.getLocationOnScreen(rootLocation)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorCenterX = anchorLocation[0] - rootLocation[0] + anchor.width / 2
        val anchorTop = anchorLocation[1] - rootLocation[1]
        val left = (anchorCenterX - bubbleWidth / 2)
            .coerceIn(dp(4), (gameplayRoot.width - bubbleWidth - dp(4)).coerceAtLeast(dp(4)))
        val top = (anchorTop - bubbleHeight + dp(if (isHuman) 6 else 2))
            .coerceIn(dp(6), (gameplayRoot.height - bubbleHeight - dp(6)).coerceAtLeast(dp(6)))

        gameplayRoot.addView(
            bubble,
            RelativeLayout.LayoutParams(bubbleWidth, bubbleHeight).apply {
                leftMargin = left
                topMargin = top
            }
        )
        activeReactionBubbles[playerName] = bubble

        bubble.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                bubble.animate()
                    .alpha(0f)
                    .translationY(-dp(10).toFloat())
                    .setStartDelay(3_650L)
                    .setDuration(240L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (activeReactionBubbles[playerName] === bubble) {
                            activeReactionBubbles.remove(playerName)
                        }
                        (bubble.parent as? ViewGroup)?.removeView(bubble)
                    }
                    .start()
            }
            .start()
    }

    private fun reactionAnchorFor(playerName: String): View? {
        val humanName = GameEngine.humanPlayer(session).name
        return if (playerName == humanName) {
            roleCard
        } else {
            playerCardViews[playerName]?.cardFace
        }
    }

    private fun scheduleBotReactionIfNeeded() {
        val key = "${session.round}:${session.phaseIndex}:${session.phase.name}"
        if (!canBotsUseReactions()) {
            autoAdvanceHandler.removeCallbacks(botReactionRunnable)
            botReactionScheduled = false
            botReactionScheduleKey = ""
            return
        }
        if (botReactionScheduleKey != key) {
            autoAdvanceHandler.removeCallbacks(botReactionRunnable)
            botReactionScheduled = false
            botReactionScheduleKey = key
        }
        if (botReactionScheduled) return

        botReactionScheduled = true
        autoAdvanceHandler.postDelayed(botReactionRunnable, nextBotReactionDelayMs())
    }

    private fun canBotsUseReactions(): Boolean {
        return !isOnlineGameplay() &&
            session.winner.isBlank() &&
            isPublicReactionPhase(session.phase) &&
            !reactionUiBlocked() &&
            session.players.any { !it.isHuman && GameEngine.isAlive(it) }
    }

    private fun nextBotReactionDelayMs(): Long {
        val noise = reactionNoise(
            "${session.code}:${session.round}:${session.phaseIndex}:${SystemClock.elapsedRealtime() / 1000L}"
        )
        return 5_000L + (noise % 5_000)
    }

    private fun maybeTriggerBotReaction() {
        if (!canBotsUseReactions()) return
        val now = SystemClock.elapsedRealtime()
        val eligible = session.players
            .filter { !it.isHuman && GameEngine.isAlive(it) }
            .filter { reactionLimiter.check(it.name, session.round, now).allowed }
        if (eligible.isEmpty()) return

        val seed = reactionNoise(
            "${session.code}:${session.round}:${session.phaseIndex}:${now / 1000L}:${eligible.size}"
        )
        val bot = eligible[seed % eligible.size]
        val spec = chooseBotReaction(bot, seed)
        val check = reactionLimiter.record(bot.name, session.round, now)
        if (check.allowed) {
            showReactionBubble(bot.name, spec)
        }
    }

    private fun chooseBotReaction(bot: GamePlayer, seed: Int): ReactionSpec {
        val specs = reactionSpecsFor(bot)
        val phasePool = when (session.phase) {
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> listOf("suspicious", "angry")
            GamePhase.CONTRAPUNTO -> listOf("angry", "suspicious", "sad")
            GamePhase.RECUENTO_VOTOS -> listOf("suspicious", "sad")
            else -> listOf("happy", "suspicious", "angry", "sad")
        }
        val roleBias = when (bot.role?.key) {
            "asesino", "mercenario" -> "suspicious"
            "payador" -> "happy"
            "medico", "oraculo" -> "sad"
            else -> null
        }
        val keys = if (roleBias != null && seed % 3 == 0) {
            listOf(roleBias) + phasePool
        } else {
            phasePool
        }
        val key = keys[seed % keys.size]
        return specs.firstOrNull { it.key == key }
            ?: defaultReactionSpecs.firstOrNull { it.key == key }
            ?: defaultReactionSpecs.first()
    }

    private fun reactionSpecsFor(player: GamePlayer): List<ReactionSpec> {
        return when {
            player.isHuman ->
                EmoteLoadout.selectedSpecs(this).map { it.toReactionSpec() }
            session.mapKey == "medieval" && player.role?.key == RoleCatalog.ASESINO ->
                medievalAssassinReactionSpecs
            session.mapKey == "pampa" && player.role?.key == RoleCatalog.POLICIA ->
                gauchoDetectiveReactionSpecs
            else ->
                defaultReactionSpecs
        }
    }

    private fun reactionSpecsForTheme(themeKey: String): List<ReactionSpec> {
        return EmoteCatalog.byTheme()[themeKey]
            ?.map { it.toReactionSpec() }
            .orEmpty()
    }

    private fun EmoteSpec.toReactionSpec(): ReactionSpec {
        return ReactionSpec(
            id = id,
            key = emotionKey,
            imageRes = imageRes,
            label = label,
            toneHex = toneHex
        )
    }

    private fun reactionNoise(seed: String): Int {
        var hash = 17
        seed.forEach { char -> hash = 31 * hash + char.code }
        return hash and Int.MAX_VALUE
    }

    private fun renderAdvanceButton() {
        val selectedAction = confirmedTargetActionLabel()
        val validTargets = validHumanTargets()
        val mandatoryTargetSelection = requiresHumanInput() && validTargets.isNotEmpty()
        val canSelfProtect = selectedTarget.isBlank() &&
            canHumanMedicSelfProtect()
        val transitionLocked = countdown.isTransitionLocked(session.phaseIndex)
        val canSkipNight = canSkipRemainingNight()
        val nightSkipReady = isNightSkipButtonReady(canSkipNight)
        val mayorRevealAvailable = canOfferMayorReveal()
        val mayorDebateOnlyReveal = mayorRevealAvailable && session.phase == GamePhase.DIA_DEBATE
        val mayorVotingWithoutSelection = mayorRevealAvailable &&
            (session.phase == GamePhase.VOTACION || session.phase == GamePhase.ALCALDE_DESEMPATE) &&
            selectedAction == null
        val mayorNeedsRevealBeforeDecision = mayorRevealAvailable &&
            session.phase == GamePhase.ALCALDE_DESEMPATE &&
            selectedAction == null
        val specialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            canHumanOracleChooseThisNight()
        val label = when {
            localPhaseResolutionInProgress -> localPhaseResolutionActionLabel
            session.winner.isNotBlank() -> "FINAL"
            isOnlineStartupPhase() && onlineStartupCountdownSeconds() != null ->
                "NOCHE EN ${onlineStartupCountdownSeconds()}"
            isOnlineStartupPhase() -> "ESPERANDO"
            onlineAwaitingHostAdvance -> "SINCRONIZANDO"
            selectedAction != null -> primaryTargetActionLabel(selectedAction, selectedTarget)
            canSelfProtect -> "SALVARME"
            GameEngine.needsInitialDesertorChoice(session) -> "ELEGIR BANDO"
            GameEngine.canDesertorReconsider(session) -> "REVISAR BANDO"
            canHumanOracleChooseThisNight() -> "GUARDAR PODER"
            mayorVotingWithoutSelection -> "VOTAR"
            mayorDebateOnlyReveal -> "ESPERAR"
            mandatoryTargetSelection -> "ELEGIR OBJETIVO"
            session.phase == GamePhase.REPARTO -> "NOCHE"
            nightSkipReady -> "SALTAR NOCHE"
            mustWaitForPhaseTimer() -> "ESPERAR"
            session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "payador" &&
                !session.payadorUsed -> "VOTAR SIN USAR"
            else -> phaseText(session.phase).actionLabel
        }
        btnAction.text = label
        val requiresAttention = session.winner.isBlank() &&
            (selectedAction != null || canSelfProtect || specialDecision)
        val actionReadyDuringTimer = selectedAction != null || canSelfProtect || specialDecision
        btnAction.isEnabled = !localPhaseResolutionInProgress &&
            !transitionLocked &&
            session.winner.isBlank() &&
            !isOnlineStartupPhase() &&
            !onlineAwaitingHostAdvance &&
            (!mustWaitForPhaseTimer() || actionReadyDuringTimer || nightSkipReady) &&
            !mayorDebateOnlyReveal &&
            !mayorNeedsRevealBeforeDecision &&
            (!mandatoryTargetSelection || selectedAction != null || canSelfProtect || specialDecision)
        applyPrimaryActionVisual(label, requiresAttention)
        renderMayorRevealSecondaryButton(mayorRevealAvailable)
        btnAction.alpha = when {
            btnAction.isEnabled -> 1f
            requiresAttention -> 0.92f
            else -> 0.55f
        }
        updateActionAttentionPulse(requiresAttention)
    }

    private fun renderReadyToVoteButton() {
        if (!::btnReadyToVote.isInitialized || !::session.isInitialized) return
        syncReadyVoteStateForPhase()
        val human = GameEngine.humanPlayer(session)
        val visible = session.phase == GamePhase.DIA_DEBATE &&
            session.winner.isBlank() &&
            human.alive
        btnReadyToVote.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        val unlockRemainingMs = readyVoteUnlockRemainingMs()
        val progress = readyVoteProgress()
        val humanReady = isHumanReadyToVote()
        btnReadyToVote.text = when {
            unlockRemainingMs > 0L ->
                "VOTAR ANTES EN ${ceil(unlockRemainingMs / 1000.0).toInt()} · ${progress.first}/${progress.second}"
            humanReady -> "CANCELAR · ${progress.first}/${progress.second}"
            else -> "LISTOS PARA VOTAR · ${progress.first}/${progress.second}"
        }
        btnReadyToVote.isEnabled = unlockRemainingMs <= 0L &&
            !readyVoteAdvanceInProgress &&
            !localPhaseResolutionInProgress &&
            !countdown.isTransitionLocked(session.phaseIndex) &&
            !onlineAwaitingHostAdvance
        btnReadyToVote.alpha = if (btnReadyToVote.isEnabled) 1f else 0.58f

        if (!isOnlineGameplay() && humanReady && !readyVoteBotCascadeScheduled) {
            scheduleReadyVoteBotCascade()
        }
    }

    private fun toggleReadyToVote() {
        if (
            session.phase != GamePhase.DIA_DEBATE ||
            readyVoteUnlockRemainingMs() > 0L ||
            readyVoteAdvanceInProgress ||
            localPhaseResolutionInProgress
        ) {
            return
        }
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        if (isOnlineGameplay()) {
            publishOnlineVoteReady(!isHumanReadyToVote())
            return
        }

        val humanName = GameEngine.humanPlayer(session).name
        if (humanName in readyToVote) {
            cancelReadyVoteBotCascade()
            readyToVote.clear()
        } else {
            readyToVote += humanName
            scheduleReadyVoteBotCascade()
        }
        renderReadyToVoteButton()
        checkLocalReadyVoteCompletion()
    }

    private fun syncReadyVoteStateForPhase() {
        if (session.phase != GamePhase.DIA_DEBATE) {
            if (readyVotePhaseIndex != -1) {
                cancelReadyVoteBotCascade()
                readyToVote.clear()
                readyVotePhaseIndex = -1
                readyVoteAdvanceInProgress = false
            }
            return
        }
        if (readyVotePhaseIndex != session.phaseIndex) {
            cancelReadyVoteBotCascade()
            readyToVote.clear()
            readyVotePhaseIndex = session.phaseIndex
            readyVoteAdvanceInProgress = false
        }
    }

    private fun eligibleReadyVoters(): List<GamePlayer> {
        return session.players.filter { it.alive }
    }

    private fun readyVoteProgress(): Pair<Int, Int> {
        val eligible = eligibleReadyVoters()
        if (isOnlineGameplay()) {
            val result = OnlineVoteReadyGate.evaluate(
                eligiblePlayerNames = eligible.map { it.name },
                states = onlineVoteReadyStates,
                round = session.round,
                phaseIndex = session.phaseIndex
            )
            return result.readyCount to result.totalCount
        }
        val eligibleNames = eligible.map { it.name }.toSet()
        return readyToVote.count { it in eligibleNames } to eligibleNames.size
    }

    private fun isHumanReadyToVote(): Boolean {
        val humanName = GameEngine.humanPlayer(session).name
        if (!isOnlineGameplay()) return humanName in readyToVote
        return onlineVoteReadyStates.any {
            it.uid == onlinePlayerId &&
                it.playerName == humanName &&
                it.ready &&
                it.round == session.round &&
                it.phaseIndex == session.phaseIndex
        }
    }

    private fun readyVoteUnlockRemainingMs(): Long {
        if (
            session.phase != GamePhase.DIA_DEBATE ||
            countdown.phaseIndex != session.phaseIndex ||
            countdown.stage != CountdownStage.ACTIVE
        ) {
            return READY_VOTE_MINIMUM_DEBATE_MS
        }
        val elapsedMs = (
            countdown.totalMs -
                countdown.remainingForSave(SystemClock.elapsedRealtime())
            ).coerceAtLeast(0L)
        return (READY_VOTE_MINIMUM_DEBATE_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun scheduleReadyVoteBotCascade() {
        if (isOnlineGameplay() || readyVoteBotCascadeScheduled || localPhaseResolutionInProgress) return
        val human = GameEngine.humanPlayer(session)
        if (human.name !in readyToVote || session.phase != GamePhase.DIA_DEBATE) return
        readyVoteBotCascadeScheduled = true
        eligibleReadyVoters()
            .filterNot { it.isHuman || it.name in readyToVote }
            .forEach { bot ->
                val delayMs = 800L + (
                    stableNoise("${session.code}:${session.round}:${bot.name}:ready") % 2700
                    ).toLong()
                val runnable = Runnable {
                    if (
                        session.phase == GamePhase.DIA_DEBATE &&
                        session.phaseIndex == readyVotePhaseIndex &&
                        human.name in readyToVote &&
                        !localPhaseResolutionInProgress
                    ) {
                        readyToVote += bot.name
                        renderReadyToVoteButton()
                        checkLocalReadyVoteCompletion()
                    }
                }
                readyVoteBotRunnables += runnable
                autoAdvanceHandler.postDelayed(runnable, delayMs)
            }
        checkLocalReadyVoteCompletion()
    }

    private fun cancelReadyVoteBotCascade() {
        readyVoteBotRunnables.forEach { autoAdvanceHandler.removeCallbacks(it) }
        readyVoteBotRunnables.clear()
        readyVoteBotCascadeScheduled = false
    }

    private fun checkLocalReadyVoteCompletion() {
        if (
            isOnlineGameplay() ||
            session.phase != GamePhase.DIA_DEBATE ||
            localPhaseResolutionInProgress
        ) return
        val eligibleNames = eligibleReadyVoters().map { it.name }.toSet()
        if (eligibleNames.isNotEmpty() && readyToVote.containsAll(eligibleNames)) {
            skipDebateToVoting("local_all_ready")
        }
    }

    private fun publishOnlineVoteReady(ready: Boolean) {
        if (!isOnlineGameplay() || session.phase != GamePhase.DIA_DEBATE) return
        val previousStates = onlineVoteReadyStates
        val human = GameEngine.humanPlayer(session)
        val optimisticState = OnlineVoteReadyState(
            uid = onlinePlayerId,
            playerName = human.name,
            ready = ready,
            round = session.round,
            phaseIndex = session.phaseIndex
        )
        onlineVoteReadyStates = previousStates.filterNot { it.uid == onlinePlayerId } + optimisticState
        renderReadyToVoteButton()

        FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(onlinePlayerId)
            .update(
                mapOf(
                    FIELD_READY_TO_VOTE to ready,
                    FIELD_READY_TO_VOTE_ROUND to session.round,
                    FIELD_READY_TO_VOTE_PHASE_INDEX to session.phaseIndex
                )
            )
            .addOnFailureListener { error ->
                onlineVoteReadyStates = previousStates
                OnlineDebugLog.e(
                    "vote_ready_publish_failure roomId=$onlinePartidaId uid=$onlinePlayerId phaseIndex=${session.phaseIndex}",
                    error
                )
                GameplayEffects.play(this, GameplayEffect.ERROR)
                GameNotice.show(
                    activity = this,
                    message = OnlineErrorMessages.forAction("No se pudo marcar listo", error),
                    duration = GameNotice.Duration.LONG
                )
                renderReadyToVoteButton()
            }
    }

    private fun maybeAdvanceOnlineReadyVote() {
        if (
            !isOnlineGameplay() ||
            !onlineIsHost ||
            session.phase != GamePhase.DIA_DEBATE ||
            readyVoteAdvanceInProgress ||
            readyVoteUnlockRemainingMs() > 0L
        ) {
            return
        }
        val result = OnlineVoteReadyGate.evaluate(
            eligiblePlayerNames = eligibleReadyVoters().map { it.name },
            states = onlineVoteReadyStates,
            round = session.round,
            phaseIndex = session.phaseIndex
        )
        if (result.canSkip) {
            autoAdvanceHandler.post { skipDebateToVoting("online_all_ready") }
        }
    }

    private fun skipDebateToVoting(reason: String) {
        if (
            session.phase != GamePhase.DIA_DEBATE ||
            readyVoteAdvanceInProgress ||
            localPhaseResolutionInProgress ||
            readyVoteUnlockRemainingMs() > 0L ||
            (isOnlineGameplay() && !onlineIsHost)
        ) {
            return
        }
        readyVoteAdvanceInProgress = true
        cancelReadyVoteBotCascade()
        pauseCountdown()
        clearCountdown()
        chatController.cancelPendingBotChat()
        val before = session
        if (!isOnlineGameplay()) {
            resolveLocalPhaseOffMainThread(
                before = before,
                operation = "all_ready",
                progressMessage = "Preparando la votacion...",
                resolver = { source -> GameEngine.resolveDayDebate(source) }
            ) { resolved ->
                session = resolved
                OnlineDebugLog.i(
                    "vote_ready_advance mode=local reason=$reason round=${before.round} phaseIndex=${before.phaseIndex}"
                )
                recordOnlinePhaseAdvance(before, resolved)
                chatController.onPhaseSettled()
                clearSelection()
                renderGame()
            }
            return
        }
        session = GameEngine.resolveDayDebateWithoutOptionalBotActions(before)
        OnlineDebugLog.i(
            "vote_ready_advance mode=${if (isOnlineGameplay()) "online" else "local"} reason=$reason round=${before.round} phaseIndex=${before.phaseIndex}"
        )
        recordOnlinePhaseAdvance(before, session)
        chatController.onPhaseSettled()
        clearSelection()
        renderGame()
    }

    private fun primaryTargetActionLabel(actionLabel: String, targetName: String): String {
        val target = targetName.uppercase()
        return when (actionLabel) {
            "MATAR" -> "MATAR A $target"
            "SILENCIAR" -> "SILENCIAR A $target"
            "INVESTIGAR" -> "INVESTIGAR A $target"
            "SALVAR" -> "SALVAR A $target"
            "INVOCAR" -> "INVOCAR A $target"
            "SEÑALAR", "SENALAR" -> "SEÑALAR A $target"
            "DECIDIR" -> "EXPULSAR A $target"
            "VOTAR" -> "VOTAR A $target"
            else -> actionLabel
        }
    }

    private fun compactTargetActionLabel(actionLabel: String): String {
        // El badge de la carta usa el mismo verbo que el boton de accion para no confundir
        // (antes decia VICTIMA/PISTA/CALLAR, distinto a MATAR/INVESTIGAR/SILENCIAR).
        return when (actionLabel) {
            "DECIDIR" -> "EXPULSAR"
            else -> actionLabel
        }
    }

    private fun applyRevealOverlayTheme() {
        val panelTheme = revealPanelThemeForMap(session.mapKey)
        // Los 9-patch de marco estan recortados al arte y declaran su hueco interior
        // real como padding. Panel oscuro y padding de contenido se derivan de ahi:
        // una sola fuente de verdad, sin numeros calibrados a mano.
        listOf(
            deathRevealContent,
            silenceRevealContent,
            noDeathRevealContent,
            voteResultPanel,
            privateFeedbackPanel,
            tieVotePanel
        ).forEach { panel ->
            val framePadding = Rect()
            panel.background = createRevealPanelBackground(panelTheme, framePadding)
            val greekFrame = session.mapKey == "grecia"
            val pampaSilenceFrame = session.mapKey == "pampa" && panel == silenceRevealContent
            // El panel de desempate no tiene un contenedor interno con padding propio
            // (a diferencia de deathReveal/silenceReveal), asi que sus hijos (titulo,
            // cartas, botones) necesitan mas aire horizontal para no tocar el marco.
            val isTieVotePanel = panel == tieVotePanel
            val horizontalInset = if (isTieVotePanel) dp(26) else dp(6)
            panel.setPadding(
                framePadding.left + horizontalInset,
                framePadding.top + when {
                    greekFrame -> dp(18)
                    isTieVotePanel -> dp(14)
                    else -> dp(4)
                },
                framePadding.right + horizontalInset,
                framePadding.bottom + when {
                    greekFrame -> dp(12)
                    pampaSilenceFrame -> dp(20)
                    isTieVotePanel -> dp(12)
                    else -> dp(4)
                }
            )
            if (greekFrame) panel.applyRevealTextShadow()
        }
    }

    private fun applyTraitorRevealOverlayTheme() {
        val framePadding = Rect()
        traitorRevealContent.background = createRevealPanelBackground(
            revealPanelThemeForMap(session.mapKey),
            framePadding
        )
        traitorRevealContent.setPadding(
            framePadding.left + dp(18),
            framePadding.top + dp(24),
            framePadding.right + dp(18),
            framePadding.bottom + dp(22)
        )
        if (session.mapKey == "grecia") {
            traitorRevealContent.applyRevealTextShadow()
        }
    }

    private fun createRevealPanelBackground(
        panelTheme: RevealPanelTheme,
        outFramePadding: Rect
    ): LayerDrawable {
        val inner = ResourcesCompat.getDrawable(
            resources,
            R.drawable.bg_reveal_inner_panel,
            theme
        )?.mutate() ?: ColorDrawable(panelTheme.innerColor)
        inner.setTint(panelTheme.innerColor)
        val frame = ResourcesCompat.getDrawable(resources, panelTheme.frame, theme)?.mutate()
            ?: ColorDrawable(Color.TRANSPARENT)
        if (!frame.getPadding(outFramePadding) || outFramePadding.left <= 0) {
            outFramePadding.set(dp(12), dp(12), dp(12), dp(12))
        }
        // El panel oscuro se mete unos dp por debajo del labio del marco para sellar
        // la union sin asomar nunca por fuera del arte.
        val overlap = dp(10)
        return LayerDrawable(
            arrayOf(
                InsetDrawable(
                    inner,
                    (outFramePadding.left - overlap).coerceAtLeast(0),
                    (outFramePadding.top - overlap).coerceAtLeast(0),
                    (outFramePadding.right - overlap).coerceAtLeast(0),
                    (outFramePadding.bottom - overlap).coerceAtLeast(0)
                ),
                frame
            )
        )
    }

    private fun revealPanelThemeForMap(mapKey: String): RevealPanelTheme {
        return when (mapKey) {
            "grecia" -> RevealPanelTheme(
                frame = R.drawable.ui_frame_event_grecia,
                innerColor = Color.parseColor("#EB080A10")
            )
            "medieval" -> RevealPanelTheme(
                frame = R.drawable.ui_frame_event_medieval,
                innerColor = Color.parseColor("#F0060708")
            )
            "pampa" -> RevealPanelTheme(
                frame = R.drawable.ui_frame_event_pampa,
                innerColor = Color.parseColor("#EC120C07")
            )
            else -> RevealPanelTheme(
                frame = R.drawable.bg_reveal_event_panel,
                innerColor = Color.parseColor("#EA08090D")
            )
        }
    }

    private fun View.applyRevealTextShadow() {
        if (this is TextView) {
            setShadowLayer(4f, 0f, 1.5f, Color.BLACK)
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).applyRevealTextShadow()
            }
        }
    }

    private fun applyPrimaryActionVisual(label: String, emphasized: Boolean) {
        val tone = if (emphasized) {
            GameplayTableUi.actionToneFor(label)
        } else {
            GameplayActionTone.DEFAULT
        }
        // El boton de accion es el CTA principal de la mesa: borde dorado siempre y, en
        // reposo, un relleno bronce calido (en vez del casi-negro DEFAULT) para que pese
        // mas que el boton secundario de "ver carta" que quedo a su izquierda.
        val fillHex = if (emphasized) tone.colorHex else PRIMARY_ACTION_RESTING_FILL
        btnAction.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), getColor(R.color.accent_gold))
            cornerRadius = dp(6).toFloat()
        }
        btnAction.setTextColor(
            getColor(if (emphasized && tone.darkText) R.color.bg_dark else R.color.text_primary)
        )
    }

    private fun renderMayorRevealSecondaryButton(visible: Boolean) {
        if (!::btnRevealMayorSecondary.isInitialized) return
        btnRevealMayorSecondary.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        val accent = getColor(R.color.accent_red)
        btnRevealMayorSecondary.isEnabled = !countdown.isTransitionLocked(session.phaseIndex)
        btnRevealMayorSecondary.alpha = if (btnRevealMayorSecondary.isEnabled) 0.96f else 0.52f
        btnRevealMayorSecondary.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(getColor(R.color.btn_dark))
            setStroke(dp(1), accent)
            cornerRadius = dp(6).toFloat()
        }
        btnRevealMayorSecondary.setTextColor(accent)
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

        val isKillAction = btnAction.text.toString().startsWith("MATAR", ignoreCase = true)
        val lift = dp(1).toFloat()
        val floatY = ObjectAnimator.ofFloat(btnAction, View.TRANSLATION_Y, 0f, -lift, 0f).apply {
            duration = if (isKillAction) 1150L else 1250L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val scaleX = ObjectAnimator.ofFloat(btnAction, View.SCALE_X, 1f, if (isKillAction) 1.012f else 1.006f, 1f).apply {
            duration = floatY.duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val scaleY = ObjectAnimator.ofFloat(btnAction, View.SCALE_Y, 1f, if (isKillAction) 1.012f else 1.006f, 1f).apply {
            duration = floatY.duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        actionPulseAnimator = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(floatY, scaleX, scaleY)
            start()
        }
    }

    private fun cancelActionPulse() {
        actionPulseAnimator?.cancel()
        actionPulseAnimator = null
        if (::btnAction.isInitialized) {
            btnAction.scaleX = 1f
            btnAction.scaleY = 1f
            btnAction.translationX = 0f
            btnAction.translationY = 0f
        }
    }

    private fun renderPlayerColumns(newlyDeadPlayers: Set<String> = emptySet()) {
        val (leftPlayers, rightPlayers) = GameplayTableUi.splitCompanions(
            session.players,
            includeEliminated = true,
            putOddExtraOnLeft = true
        )
        val displayedPlayers = leftPlayers.size + rightPlayers.size + 1
        val totalPlayers = displayedPlayers.coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
        val measuredHeightPx = listOf(leftPlayersScroll.height, rightPlayersScroll.height)
            .filter { it > 0 }
            .minOrNull()
        val bottomPanelInsetDp = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12
        val availableHeightDp = measuredHeightPx
            ?.let { (pxToDp(it) - bottomPanelInsetDp).coerceAtLeast(1) }
            ?: (resources.configuration.screenHeightDp - 16 - bottomPanelInsetDp)
                .coerceAtLeast(240)
        val metrics = GameplayTableUi.companionCardMetrics(
            totalPlayers,
            availableHeightDp,
            availableWidthDp = availableSideColumnWidthDp()
        )
        if (lastCompanionCardMetrics != metrics) {
            lastCompanionCardMetrics = metrics
            applyAdaptiveGameplayLayout(metrics)
        }

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
        val verticalGravity = if (metrics.scrollEnabled) {
            Gravity.TOP
        } else {
            Gravity.CENTER_VERTICAL
        }
        leftPlayersContainer.gravity = verticalGravity or Gravity.START
        rightPlayersContainer.gravity = verticalGravity or Gravity.END
        leftPlayersContainer.setPadding(dp(2), 0, 0, 0)
        rightPlayersContainer.setPadding(0, 0, dp(2), 0)
        val bottomScrollInset = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12
        leftPlayersScroll.setPadding(0, 0, 0, dp(bottomScrollInset))
        rightPlayersScroll.setPadding(0, 0, 0, dp(bottomScrollInset))
        bottomPlayerPanel.layoutParams = (bottomPlayerPanel.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp((resources.configuration.screenWidthDp - 24).coerceIn(244, 372))
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        applyAdaptiveHudSizing()

        gameplayBody.requestLayout()
    }

    private fun applyAdaptiveHudSizing() {
        val playerCount = session.players.size
        val roomy = playerCount <= 8
        val relaxed = playerCount <= 10
        val topHeightDp = when {
            roomy -> 90
            relaxed -> 82
            else -> 76
        }
        val headerHeightDp = if (roomy) 44 else 38
        val subtitleHeightDp = topHeightDp - headerHeightDp - 4
        topStatus.layoutParams = (topStatus.layoutParams as FrameLayout.LayoutParams).apply {
            height = dp(topHeightDp)
        }
        (topStatus.getChildAt(0).layoutParams as LinearLayout.LayoutParams).height = dp(headerHeightDp)
        phaseSubtitle.layoutParams = (phaseSubtitle.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(subtitleHeightDp)
        }
        phaseTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (roomy) 18f else 16f)
        phaseSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (roomy) 11.5f else 10.5f)
        eventLogPanel.layoutParams = (eventLogPanel.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = dp(topHeightDp + 4)
        }
        eventLogHeader.layoutParams = (eventLogHeader.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(eventLogCollapsedHeightDp())
        }
    }

    private fun eventLogCollapsedHeightDp(): Int {
        return if (::session.isInitialized && session.players.size <= 8) 40 else 32
    }

    private fun eventLogExpandedHeightDp(): Int {
        return if (!::session.isInitialized) {
            136
        } else {
            when {
                session.players.size <= 8 -> 196
                session.players.size <= 12 -> 166
                else -> 142
            }
        }
    }

    private fun availableSideColumnWidthDp(): Int {
        val totalWidthDp = resources.configuration.screenWidthDp
        val gameplayBodyHorizontalMarginsDp = 8
        val centerColumnHorizontalMarginsDp = 8
        val centerColumnPreferredWidthDp = 220
        val combinedSideWidth = (
            totalWidthDp -
                gameplayBodyHorizontalMarginsDp -
                centerColumnHorizontalMarginsDp -
                centerColumnPreferredWidthDp
            ).coerceAtLeast(108)
        return (combinedSideWidth / 2).coerceIn(54, 78)
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
            holder.root.gravity = Gravity.CENTER_VERTICAL or if (container === rightPlayersContainer) {
                Gravity.END
            } else {
                Gravity.START
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
        val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.48f, 1f).apply {
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
                    // El estado eliminado se representa dentro de la carta. Mantener todo el
                    // contenedor al 40 % hacía desaparecer también el marco, el nombre y el
                    // indicador de muerte contra los fondos claros del mapa.
                    view.alpha = 1f
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

        val roleFace = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        cardFace.addView(
            roleFace,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val deathCauseOverlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = null
            elevation = dp(8).toFloat()
        }
        cardFace.addView(
            deathCauseOverlay,
            FrameLayout.LayoutParams(
                dp(22),
                dp(22),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(1)
                bottomMargin = dp(1)
            }
        )

        val actionMarkPrimary = createCardActionMarkView()
        val actionMarkSecondary = createCardActionMarkView()
        val actionMarkTertiary = createCardActionMarkView()
        cardFace.addView(actionMarkPrimary)
        cardFace.addView(actionMarkSecondary)
        cardFace.addView(actionMarkTertiary)
        val actionMarkPrimaryLabel = createCardActionMarkLabel()
        val actionMarkSecondaryLabel = createCardActionMarkLabel()
        val actionMarkTertiaryLabel = createCardActionMarkLabel()
        cardFace.addView(actionMarkPrimaryLabel)
        cardFace.addView(actionMarkSecondaryLabel)
        cardFace.addView(actionMarkTertiaryLabel)

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
        mutedBadge.includeFontPadding = false
        mutedBadge.setTextColor(getColor(R.color.text_primary))
        mutedBadge.setBackgroundResource(R.drawable.bg_player_chip)
        mutedBadge.textSize = 6.5f
        mutedBadge.setTypeface(null, Typeface.BOLD)
        mutedBadge.setPadding(dp(2), 0, dp(2), 0)
        cardFace.addView(
            mutedBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(13),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val actionBadge = TextView(this)
        actionBadge.gravity = Gravity.CENTER
        actionBadge.includeFontPadding = false
        actionBadge.maxLines = 1
        actionBadge.ellipsize = TextUtils.TruncateAt.END
        actionBadge.setTypeface(null, Typeface.BOLD)
        actionBadge.setPadding(dp(4), dp(1), dp(4), dp(1))
        actionBadge.visibility = View.GONE
        cardFace.addView(
            actionBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(13),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(2)
            }
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
        name.typeface = Typeface.DEFAULT_BOLD
        item.addView(
            name,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(metrics.nameHeightDp)
            )
        )

        return SidePlayerCardHolder(
            item,
            cardFace,
            cardBack,
            roleFace,
            deathCauseOverlay,
            actionMarkPrimary,
            actionMarkSecondary,
            actionMarkTertiary,
            actionMarkPrimaryLabel,
            actionMarkSecondaryLabel,
            actionMarkTertiaryLabel,
            avatar,
            mutedBadge,
            actionBadge,
            name
        )
    }

    private fun createCardActionMarkView(): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            alpha = 0f
            contentDescription = null
            isClickable = false
            isFocusable = false
            elevation = dp(7).toFloat()
        }
    }

    private fun createCardActionMarkLabel(): TextView {
        return TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            textSize = 6f
            setPadding(dp(2), 0, dp(2), 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                4,
                7,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            visibility = View.GONE
            alpha = 0f
            elevation = dp(9).toFloat()
            isClickable = false
            isFocusable = false
        }
    }

    private fun visibleCardActionMarks(): List<CardActionMark> {
        if (!isOnlineGameplay() || !::session.isInitialized) return emptyList()
        return CardActionMarks.visibleForCurrentPhase(
            session = session,
            onlinePlayerId = onlinePlayerId,
            records = onlineNightActionRecords,
            traitorMarks = onlineTraitorActionMarks
        )
    }

    /** Refresco acotado: una acción remota no necesita reconstruir todo el tablero. */
    private fun refreshVisibleActionMarks() {
        if (!::session.isInitialized || !::roleCard.isInitialized) return
        val metrics = lastCompanionCardMetrics
        if (metrics != null) {
            session.players.forEach { player ->
                playerCardViews[player.name]?.let { holder ->
                    // El estado visual forma parte de renderKey; se invalida sólo para que el
                    // nuevo sello se pinte y anime en el instante en que llega del servidor.
                    holder.renderKey = null
                    bindSidePlayerCard(holder, player, metrics)
                }
            }
        }
        renderHumanCardIfVisible()
    }

    private fun bindCardActionMarks(
        holder: SidePlayerCardHolder,
        marks: List<CardActionMark>,
        metrics: CompanionCardMetrics
    ) {
        val visible = marks.take(MAX_CARD_ACTION_MARKS)
        val key = visible.joinToString(";") { "${it.id}:${it.roleKey}" }
        val views = listOf(
            holder.actionMarkPrimary,
            holder.actionMarkSecondary,
            holder.actionMarkTertiary
        )
        val labels = listOf(
            holder.actionMarkPrimaryLabel,
            holder.actionMarkSecondaryLabel,
            holder.actionMarkTertiaryLabel
        )
        val hasMercenary = visible.any { it.roleKey == RoleCatalog.MERCENARIO }
        views.forEachIndexed { index, view ->
            val mark = visible.getOrNull(index)
            val label = labels[index]
            if (mark == null) {
                view.animate().cancel()
                view.visibility = View.GONE
                view.alpha = 0f
                label.visibility = View.GONE
                label.alpha = 0f
                return@forEachIndexed
            }
            view.setImageResource(actionMarkImageFor(mark.roleKey))
            view.layoutParams = actionMarkLayoutParams(
                mark.roleKey,
                metrics,
                index,
                visible.size,
                hasMercenary
            )
            view.contentDescription = actionMarkDescription(mark)
            view.visibility = View.VISIBLE
            val showActor = mark.roleKey in GameRules.traitorRoleKeys && mark.actorName.isNotBlank()
            label.visibility = if (showActor) View.VISIBLE else View.GONE
            if (showActor) {
                label.text = mark.actorName
                label.layoutParams = actionMarkLabelLayoutParams(
                    mark.roleKey,
                    metrics,
                    index,
                    visible.size,
                    hasMercenary
                )
                label.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(
                        Color.parseColor(
                            actionMarkLabelColor(mark.roleKey)
                        )
                    )
                    setStroke(dp(1), Color.parseColor("#F4D79B"))
                }
            }
        }
        if (holder.actionMarkKey != key) {
            holder.actionMarkAnimator?.cancel()
            visible.forEachIndexed { index, mark ->
                val view = views[index]
                val spec = CardActionAnimations.forRole(mark.roleKey, index, visible.size)
                view.alpha = 0f
                view.scaleX = spec.startScaleX
                view.scaleY = spec.startScaleY
                view.rotation = spec.startRotation
                view.translationX = dp(metrics.cardWidthDp).toFloat() * spec.startTranslationXFraction
                view.translationY = dp(metrics.cardHeightDp).toFloat() * spec.startTranslationYFraction
                labels[index].alpha = 0f
            }
            val animators = visible.flatMapIndexed { index, mark ->
                val view = views[index]
                val spec = CardActionAnimations.forRole(mark.roleKey, index, visible.size)
                listOf(
                    ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(
                        view,
                        View.SCALE_X,
                        spec.startScaleX,
                        spec.overshootScale,
                        1f
                    ),
                    ObjectAnimator.ofFloat(
                        view,
                        View.SCALE_Y,
                        spec.startScaleY,
                        spec.overshootScale,
                        1f
                    ),
                    ObjectAnimator.ofFloat(
                        view,
                        View.ROTATION,
                        *spec.rotationKeyframes.toFloatArray()
                    ),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, 0f),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, 0f),
                    ObjectAnimator.ofFloat(labels[index], View.ALPHA, 0f, 1f)
                ).onEach { animator ->
                    animator.startDelay = index * 90L
                    animator.duration = spec.durationMs
                }
            }
            holder.actionMarkAnimator = AnimatorSet().apply {
                playTogether(animators)
                interpolator = DecelerateInterpolator(1.5f)
                start()
            }
            holder.actionMarkKey = key
        } else {
            visible.forEachIndexed { index, mark ->
                val spec = CardActionAnimations.forRole(mark.roleKey, index, visible.size)
                views[index].alpha = 1f
                views[index].scaleX = 1f
                views[index].scaleY = 1f
                views[index].rotation = spec.endRotation
                views[index].translationX = 0f
                views[index].translationY = 0f
                labels[index].alpha = 1f
            }
        }
    }

    private fun actionMarkLabelLayoutParams(
        roleKey: String,
        metrics: CompanionCardMetrics,
        index: Int,
        count: Int,
        hasMercenary: Boolean
    ): FrameLayout.LayoutParams {
        if (count == 3) {
            val isRope = roleKey == RoleCatalog.MERCENARIO
            return FrameLayout.LayoutParams(
                dp((metrics.cardWidthDp * if (isRope) 0.54f else 0.58f).toInt().coerceAtLeast(18)),
                dp(11),
                if (isRope) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM or Gravity.START
            ).apply {
                bottomMargin = dp(if (!isRope && index == 0) 14 else 2)
            }
        }
        if (count == 2 && hasMercenary) {
            val isRope = roleKey == RoleCatalog.MERCENARIO
            return FrameLayout.LayoutParams(
                dp((metrics.cardWidthDp * 0.56f).toInt().coerceAtLeast(18)),
                dp(11),
                Gravity.BOTTOM or if (isRope) Gravity.END else Gravity.START
            ).apply { bottomMargin = dp(2) }
        }
        return FrameLayout.LayoutParams(
            dp((metrics.cardWidthDp - 2).coerceAtLeast(18)),
            dp(12),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            // El botón MATAR/INVESTIGAR ocupa el borde inferior mientras el actor aún puede
            // elegir. La placa queda arriba de esa franja y nunca compite con el verbo.
            bottomMargin = dp(if (count > 1 && index == 0) 28 else 15)
        }
    }

    private fun actionMarkLayoutParams(
        roleKey: String,
        metrics: CompanionCardMetrics,
        index: Int,
        count: Int,
        hasMercenary: Boolean
    ): FrameLayout.LayoutParams {
        val isRope = roleKey == RoleCatalog.MERCENARIO
        if (count == 3) {
            return if (isRope) {
                FrameLayout.LayoutParams(
                    dp((metrics.cardWidthDp * 0.58f).toInt().coerceAtLeast(24)),
                    dp((metrics.cardHeightDp * 0.9f).toInt().coerceAtLeast(32)),
                    Gravity.CENTER_VERTICAL or Gravity.END
                )
            } else {
                FrameLayout.LayoutParams(
                    dp((metrics.cardWidthDp * 0.62f).toInt().coerceAtLeast(24)),
                    dp((metrics.cardHeightDp * 0.56f).toInt().coerceAtLeast(28)),
                    (if (index == 0) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
                )
            }
        }
        if (count == 2 && hasMercenary) {
            return FrameLayout.LayoutParams(
                dp((metrics.cardWidthDp * if (isRope) 0.58f else 0.68f).toInt().coerceAtLeast(24)),
                dp((metrics.cardHeightDp * if (isRope) 0.9f else 0.72f).toInt().coerceAtLeast(30)),
                Gravity.CENTER_VERTICAL or if (isRope) Gravity.END else Gravity.START
            )
        }
        val width = if (isRope) metrics.cardWidthDp else (metrics.cardWidthDp * 0.78f).toInt()
        val height = if (isRope) metrics.cardHeightDp else (metrics.cardHeightDp * 0.72f).toInt()
        return FrameLayout.LayoutParams(dp(width.coerceAtLeast(22)), dp(height.coerceAtLeast(28))).apply {
            gravity = Gravity.CENTER
            if (count > 1 && !isRope) {
                leftMargin = dp(if (index == 0) -metrics.cardWidthDp / 9 else metrics.cardWidthDp / 9)
                topMargin = dp(if (index == 0) -metrics.cardHeightDp / 12 else metrics.cardHeightDp / 12)
            }
        }
    }

    private fun actionMarkLabelColor(roleKey: String): String = when (roleKey) {
        RoleCatalog.ESPIA -> "#E36B159B"
        RoleCatalog.MERCENARIO -> "#E37B551F"
        else -> "#E3A91419"
    }

    private fun actionMarkImageFor(roleKey: String): Int = when (roleKey) {
        RoleCatalog.ASESINO -> R.drawable.action_mark_assassin
        RoleCatalog.ESPIA -> R.drawable.action_mark_spy
        RoleCatalog.POLICIA -> R.drawable.action_mark_detective
        RoleCatalog.MEDICO -> R.drawable.action_mark_medic
        RoleCatalog.MERCENARIO -> R.drawable.action_mark_mercenary
        RoleCatalog.ORACULO -> R.drawable.action_mark_oracle
        RoleCatalog.PAYADOR -> R.drawable.action_mark_payador
        else -> R.drawable.action_mark_assassin
    }

    private fun actionMarkDescription(mark: CardActionMark): String = when (mark.roleKey) {
        RoleCatalog.ASESINO -> "${mark.actorName} eligió eliminar a ${mark.targetName}"
        RoleCatalog.ESPIA -> "${mark.actorName} marcó a ${mark.targetName}"
        RoleCatalog.POLICIA -> "Investigación sobre ${mark.targetName}"
        RoleCatalog.MEDICO -> "Protección sobre ${mark.targetName}"
        RoleCatalog.MERCENARIO -> "Silencio sobre ${mark.targetName}"
        RoleCatalog.ORACULO -> "Invocación de ${mark.targetName}"
        RoleCatalog.PAYADOR -> "Contrapunto con ${mark.targetName}"
        else -> "Acción sobre ${mark.targetName}"
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
        val isRevealedMayor =
            session.alcaldeRevealed && player.role?.key == RoleCatalog.ALCALDE
        val isRevealedEliminated =
            !isAlive && session.revealRolesOnDeath
        val showPublicRole = isRevealedMayor || isRevealedEliminated
        val actionLabel = targetActionLabel(player.name)
        val transitionLocked = countdown.isTransitionLocked(session.phaseIndex)
        val isActionable = actionLabel.isNotBlank() && !transitionLocked
        val isSelected = player.name == selectedTarget
        val actionMarks = visibleCardActionMarks().filter { it.targetName == player.name }
        val actionMarkKey = actionMarks.joinToString(";") { "${it.id}:${it.roleKey}" }
        val renderKey = listOf(
            player.name,
            player.initial,
            isAlive,
            player.muted,
            player.role?.key.orEmpty(),
            player.deathCause,
            isOracleGuest,
            session.revealRolesOnDeath,
            session.alcaldeRevealed,
            showPublicRole,
            actionLabel,
            transitionLocked,
            isSelected,
            actionMarkKey,
            isOnlineGameplay(),
            session.phaseIndex,
            metrics
        ).joinToString("|")
        if (holder.renderKey == renderKey) return
        holder.renderKey = renderKey

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
        updatePublicRoleCard(holder, player, showPublicRole, isAlive)
        val overlaySizeDp = when (player.deathCause) {
            DeathCause.NIGHT -> metrics.cardWidthDp.coerceIn(34, 52)
            DeathCause.VOTE,
            DeathCause.AFK -> (metrics.cardWidthDp * 2 / 3).coerceIn(24, 36)
            DeathCause.NONE -> 24
        }
        holder.deathCauseOverlay.layoutParams =
            (holder.deathCauseOverlay.layoutParams as FrameLayout.LayoutParams).apply {
                width = dp(overlaySizeDp)
                height = dp(overlaySizeDp)
                gravity = if (player.deathCause == DeathCause.NIGHT) {
                    Gravity.CENTER
                } else {
                    Gravity.BOTTOM or Gravity.END
                }
                rightMargin = if (player.deathCause == DeathCause.NIGHT) 0 else dp(1)
                bottomMargin = if (player.deathCause == DeathCause.NIGHT) 0 else dp(1)
            }
        val deathIcon = when (player.deathCause) {
            DeathCause.NIGHT -> R.drawable.death_blood_splatter_art
            DeathCause.VOTE,
            DeathCause.AFK -> R.drawable.ic_kicking_boot
            DeathCause.NONE -> 0
        }
        holder.deathCauseOverlay.visibility = if (
            !isOnlineGameplay() && !isAlive && deathIcon != 0
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (deathIcon != 0) {
            holder.deathCauseOverlay.setImageResource(deathIcon)
            applyDeathCauseIconStyle(holder.deathCauseOverlay, player.deathCause)
        }
        bindCardActionMarks(holder, actionMarks, metrics)
        holder.avatar.layoutParams = (holder.avatar.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp(metrics.avatarSizeDp)
            height = dp(metrics.avatarSizeDp)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(2)
        }
        holder.avatar.visibility = if (isOnlineGameplay() && !showPublicRole) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (isOnlineGameplay() && !showPublicRole) {
            holder.avatar.text = if (isAlive || isOracleGuest) {
                GameplayTableUi.playerInitial(player)
            } else {
                "\u2620"
            }
            holder.avatar.setBackgroundResource(R.drawable.bg_player_avatar)
            holder.avatar.setTextColor(getColor(R.color.accent_gold))
            holder.avatar.textSize =
                if (isAlive || isOracleGuest) metrics.nameTextSp else metrics.nameTextSp + 1f
        }
        holder.mutedBadge.visibility = if (isAlive && player.muted) View.VISIBLE else View.GONE
        holder.actionBadge.layoutParams = (holder.actionBadge.layoutParams as FrameLayout.LayoutParams).apply {
            height = dp((metrics.nameHeightDp - 2).coerceIn(12, 16))
            bottomMargin = dp(2)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        holder.actionBadge.maxWidth = dp((metrics.minCardWidthDp - 6).coerceAtLeast(44))
        // Verbos completos (MATAR/INVESTIGAR/SILENCIAR...) pueden ser largos: autosize para
        // que se achiquen en vez de cortarse en mesas de 8+ con cartas chicas.
        val badgeMaxSp = ceil((metrics.nameTextSp - 1f).coerceIn(5.5f, 8.5f).toDouble())
            .toInt().coerceAtLeast(6)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            holder.actionBadge,
            5,
            badgeMaxSp,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        holder.actionBadge.visibility = if (isActionable) View.VISIBLE else View.GONE
        if (isActionable) {
            val tone = GameplayTableUi.actionToneFor(actionLabel)
            holder.actionBadge.text = compactTargetActionLabel(actionLabel)
            holder.actionBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor(tone.colorHex))
                when (tone) {
                    GameplayActionTone.KILL -> setStroke(dp(1), Color.parseColor("#F1C36A"))
                    GameplayActionTone.SILENCE -> setStroke(dp(1), Color.parseColor("#B46A72"))
                    else -> Unit
                }
            }
            holder.actionBadge.setTextColor(
                getColor(if (tone.darkText) R.color.bg_dark else R.color.text_primary)
            )
            updateSideActionBadgePulse(holder, player, actionLabel, tone)
        } else {
            stopSideActionBadgePulse(holder)
            holder.actionPulseKey = null
        }

        holder.name.layoutParams = (holder.name.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.cardWidthDp)
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
            when {
                isOracleGuest -> getColor(R.color.accent_gold)
                !isAlive -> getColor(R.color.text_muted)
                isSelected -> getColor(R.color.accent_gold)
                else -> PlayerChatColor.colorFor(player.name, session)
            }
        )
        holder.name.alpha = if (isAlive || isOracleGuest) 1f else 0.86f
        holder.name.setShadowLayer(
            if (isActionable || isSelected) 3f else 1.8f,
            0f,
            1f,
            Color.BLACK
        )
        holder.name.paintFlags = if (isAlive) {
            holder.name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            holder.name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        val eliminatedBorderColor = when (player.deathCause) {
            DeathCause.NIGHT -> Color.parseColor("#C75A54")
            DeathCause.VOTE,
            DeathCause.AFK -> Color.parseColor("#B8924E")
            DeathCause.NONE -> Color.parseColor("#8C7652")
        }
        val outlineWidthDp = when {
            isSelected -> 3
            isActionable || !isAlive -> 2
            else -> 0
        }
        holder.cardFace.setPadding(
            dp(outlineWidthDp),
            dp(outlineWidthDp),
            dp(outlineWidthDp),
            dp(outlineWidthDp)
        )
        holder.cardFace.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                if (isAlive || isOracleGuest) {
                    Color.TRANSPARENT
                } else {
                    Color.parseColor(if (showPublicRole) "#D91A120D" else "#C916110D")
                }
            )
            cornerRadius = dp(4).toFloat()
            when {
                isSelected -> setStroke(dp(3), getColor(R.color.accent_gold))
                isActionable -> {
                    val tone = GameplayTableUi.actionToneFor(actionLabel)
                    setStroke(
                        dp(2),
                        when (tone) {
                            GameplayActionTone.KILL -> Color.parseColor("#D12A1E")
                            GameplayActionTone.SILENCE -> Color.parseColor("#7C2A37")
                            else -> getColor(R.color.accent_gold)
                        }
                    )
                }
                !isAlive -> setStroke(dp(2), eliminatedBorderColor)
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

        val eliminatedContentAlpha = if (isAlive || isActionable || isOracleGuest) 1f else 0.72f
        holder.cardBack.alpha = eliminatedContentAlpha
        holder.roleFace.alpha = when {
            isAlive || isOracleGuest -> 1f
            showPublicRole -> 0.58f
            else -> eliminatedContentAlpha
        }
        holder.avatar.alpha = eliminatedContentAlpha
        holder.root.alpha = 1f
        holder.root.setOnClickListener {
            val canSelectNow = if (isOnlineGameplay()) isActionable else canActOnTarget(player.name)
            when {
                canSelectNow -> {
                    GameplayEffects.play(this, GameplayEffect.SELECT)
                    val previousTarget = selectedTarget
                    selectedTarget = if (selectedTarget == player.name) "" else player.name
                    currentPlayerHint.text = privateHintText()
                    renderAdvanceButton()
                    refreshPlayerTargetSelection(previousTarget, selectedTarget)
                }
                !isAlive && showPublicRole -> {
                    showEliminatedPlayerCard(player)
                }
                !isAlive -> {
                    GameplayEffects.play(this, GameplayEffect.ERROR)
                    GameNotice.show(this, "${player.name} esta eliminado. Su rol sigue oculto.")
                }
                !isOnlineGameplay() && requiresHumanInput() -> {
                    GameplayEffects.play(this, GameplayEffect.ERROR)
                    GameNotice.show(this, "${player.name} no es un objetivo disponible.")
                }
                else -> showMiniPlayerProfile(player)
            }
        }
        holder.root.setOnLongClickListener {
            showMiniPlayerProfile(player)
            true
        }
        holder.root.contentDescription = when {
            isOracleGuest -> "${player.name}, invocado para discutir"
            !isAlive && showPublicRole ->
                "${player.name}, eliminado, rol ${player.role?.name ?: "desconocido"}"
            !isAlive -> "${player.name}, eliminado"
            player.muted -> "${player.name}, silenciado durante el día"
            isSelected -> "${player.name}, objetivo seleccionado"
            isActionable -> "${player.name}, objetivo disponible para $actionLabel"
            else -> player.name
        }
    }

    private fun updatePublicRoleCard(
        holder: SidePlayerCardHolder,
        player: GamePlayer,
        showPublicRole: Boolean,
        isAlive: Boolean
    ) {
        val applyFace = {
            holder.cardBack.visibility = if (showPublicRole) View.GONE else View.VISIBLE
            holder.roleFace.visibility = if (showPublicRole) View.VISIBLE else View.GONE
            if (showPublicRole) {
                holder.roleFace.setImageResource(roleImageFor(player.role))
                holder.roleFace.alpha = if (isAlive) 1f else 0.58f
            }
        }
        val animateReveal = holder.hasBound && showPublicRole && !holder.publicRoleVisible
        holder.hasBound = true
        holder.publicRoleVisible = showPublicRole
        if (!animateReveal) {
            holder.cardFace.animate().cancel()
            holder.cardFace.rotationY = 0f
            applyFace()
            return
        }
        holder.cardFace.cameraDistance = dp(900).toFloat()
        holder.cardFace.animate()
            .rotationY(90f)
            .setDuration(150L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                applyFace()
                holder.cardFace.rotationY = -90f
                holder.cardFace.animate()
                    .rotationY(0f)
                    .setDuration(190L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun applyDeathCauseIconStyle(icon: ImageView, cause: DeathCause) {
        icon.alpha = 1f
        if (cause == DeathCause.VOTE || cause == DeathCause.AFK) {
            icon.colorFilter = ColorMatrixColorFilter(
                floatArrayOf(
                    1.12f, 0f, 0f, 0f, 10f,
                    0f, 1.12f, 0f, 0f, 7f,
                    0f, 0f, 1.08f, 0f, 3f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        } else {
            icon.clearColorFilter()
        }
    }

    private fun showMiniPlayerProfile(player: GamePlayer) {
        if (reactionUiBlocked()) return
        GameplayEffects.play(this, GameplayEffect.PANEL)
        val profile = PlayerProfileStore.profileFor(this, session, player)
        val playerIndex = session.players.indexOfFirst { it.name == player.name }
        val targetUid = session.onlinePlayerUids.getOrNull(playerIndex).orEmpty()
        val actions = buildList {
            if (isOnlineGameplay() && targetUid.isNotBlank() && targetUid != onlinePlayerId) {
                val muted = LocalMuteStore.isMuted(this@GameplayMockActivity, profile.publicId, targetUid)
                add(
                    PlayerProfileAction(
                        label = if (muted) "VOLVER A ESCUCHAR" else "SILENCIAR PARA MÍ"
                    ) {
                        val nowMuted = LocalMuteStore.toggle(
                            this@GameplayMockActivity,
                            profile.publicId,
                            targetUid
                        )
                        GameNotice.show(
                            this@GameplayMockActivity,
                            if (nowMuted) {
                                "Ya no verás el chat ni los emotes de ${player.name}."
                            } else {
                                "Volverás a ver el chat y los emotes de ${player.name}."
                            }
                        )
                        chatController.restartRealtimeContentListeners()
                    }
                )
                add(
                    PlayerProfileAction(label = "REPORTAR", dangerous = true) {
                        PlayerModeration.showReportDialog(
                            activity = this@GameplayMockActivity,
                            roomId = onlinePartidaId,
                            matchId = session.onlineMatchId,
                            reportedUid = targetUid,
                            reportedName = player.name
                        )
                    }
                )
                val canProposeSilence = player.alive &&
                    GameEngine.humanPlayer(session).alive &&
                    session.players.count { it.alive } >= 5 &&
                    targetUid != onlineActiveHostId &&
                    session.phase in setOf(GamePhase.DIA_DEBATE, GamePhase.VOTACION)
                if (canProposeSilence) {
                    add(
                        PlayerProfileAction(label = "PROPONER SILENCIO DE MESA") {
                            realtimeTableSilence?.propose(targetUid, player.name)
                        }
                    )
                }
            }
        }
        PlayerProfileDialog.showMini(this, profile, actions)
    }

    private fun showEliminatedPlayerCard(player: GamePlayer) {
        if (reactionUiBlocked()) return
        val role = player.role ?: return
        GameplayEffects.play(this, GameplayEffect.PANEL)

        val statusText = when (player.deathCause) {
            DeathCause.NIGHT -> "ASESINADO DURANTE LA NOCHE"
            DeathCause.VOTE -> "EXPULSADO POR EL PUEBLO"
            DeathCause.AFK -> "EXPULSADO POR INACTIVIDAD"
            DeathCause.NONE -> "JUGADOR ELIMINADO"
        }
        val statusColor = when (player.deathCause) {
            DeathCause.NIGHT -> Color.parseColor("#D56B65")
            DeathCause.VOTE,
            DeathCause.AFK -> Color.parseColor("#D0A45A")
            DeathCause.NONE -> getColor(R.color.text_secondary)
        }
        val deathIcon = when (player.deathCause) {
            DeathCause.NIGHT -> R.drawable.death_blood_splatter_art
            DeathCause.VOTE,
            DeathCause.AFK -> R.drawable.ic_kicking_boot
            DeathCause.NONE -> 0
        }
        val teamColor = when (role.team) {
            GameRules.TRAITOR_WINNER -> Color.parseColor("#C75A54")
            GameRules.TOWN_WINNER -> Color.parseColor("#659B68")
            "Neutral" -> Color.parseColor("#C8A04E")
            else -> getColor(R.color.accent_gold)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(2), dp(8), dp(4))
        }
        content.addView(TextView(this).apply {
            text = player.name.uppercase()
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(3) })
        content.addView(TextView(this).apply {
            text = statusText
            gravity = Gravity.CENTER
            setTextColor(statusColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val card = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F01A120D"))
                setStroke(dp(3), statusColor)
                cornerRadius = dp(9).toFloat()
            }
            elevation = dp(5).toFloat()
        }
        card.addView(ImageView(this).apply {
            setImageResource(roleImageFor(role))
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.82f
            contentDescription = "Carta ${role.name} de ${player.name}"
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (deathIcon != 0) {
            card.addView(ImageView(this).apply {
                setImageResource(deathIcon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                applyDeathCauseIconStyle(this, player.deathCause)
                elevation = dp(8).toFloat()
                contentDescription = statusText.lowercase()
            }, FrameLayout.LayoutParams(
                dp(if (player.deathCause == DeathCause.NIGHT) 126 else 82),
                dp(if (player.deathCause == DeathCause.NIGHT) 126 else 82),
                if (player.deathCause == DeathCause.NIGHT) {
                    Gravity.CENTER
                } else {
                    Gravity.BOTTOM or Gravity.END
                }
            ).apply {
                rightMargin = dp(3)
                bottomMargin = dp(3)
            })
        }
        content.addView(card, LinearLayout.LayoutParams(dp(174), dp(232)).apply {
            bottomMargin = dp(10)
        })
        content.addView(TextView(this).apply {
            text = role.name.uppercase()
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        content.addView(TextView(this).apply {
            text = role.team.uppercase()
            gravity = Gravity.CENTER
            setTextColor(teamColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })

        GameDialog.custom(
            activity = this,
            contentView = content,
            widthDp = 350,
            negativeLabel = null,
            positiveLabel = "CERRAR"
        )
    }

    private fun updateSideActionBadgePulse(
        holder: SidePlayerCardHolder,
        player: GamePlayer,
        actionLabel: String,
        tone: GameplayActionTone
    ) {
        val pulseKey = "${session.phaseIndex}:${player.name}:$actionLabel:${tone.name}"
        if (holder.actionPulseKey == pulseKey) return

        stopSideActionBadgePulse(holder)
        holder.actionPulseKey = pulseKey

        val lift = dp(1).toFloat()
        val durationMs = if (tone == GameplayActionTone.KILL) 1100L else 1250L
        val floatY = ObjectAnimator.ofFloat(holder.actionBadge, View.TRANSLATION_Y, 0f, -lift, 0f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val scaleX = ObjectAnimator.ofFloat(holder.actionBadge, View.SCALE_X, 1f, 1.016f, 1f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val scaleY = ObjectAnimator.ofFloat(holder.actionBadge, View.SCALE_Y, 1f, 1.016f, 1f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        holder.actionBadgeAnimator = AnimatorSet().apply {
            playTogether(floatY, scaleX, scaleY)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopSideActionBadgePulse(holder: SidePlayerCardHolder) {
        holder.actionBadgeAnimator?.cancel()
        holder.actionBadgeAnimator = null
        holder.actionBadge.animate().cancel()
        holder.actionBadge.translationY = 0f
        holder.actionBadge.scaleX = 1f
        holder.actionBadge.scaleY = 1f
        holder.actionBadge.alpha = 1f
    }

    private fun nightSubtitle(): String {
        if (!isOnlineGameplay()) {
            return when (actionSession().phase) {
                GamePhase.NOCHE_ASESINO -> "Los Traidores se mueven en silencio."
                GamePhase.NOCHE_MERCENARIO -> "Alguien intenta callar una voz para el día."
                GamePhase.NOCHE_POLICIA -> "Alguien busca una pista en secreto."
                GamePhase.NOCHE_MEDICO -> "Alguien intenta proteger a un jugador."
                GamePhase.NOCHE_ORACULO -> "El Oráculo puede llamar a una voz que abandonó el mundo de los vivos."
                else -> "El pueblo duerme."
            }
        }
        return if (onlineDeferredActionPending()) {
            "Enviando acción..."
        } else if (onlineDeferredActionSubmitted()) {
            "Acción confirmada. Esperando a los demás jugadores..."
        } else if (requiresHumanInput()) {
            when (actionSession().phase) {
                GamePhase.NOCHE_ASESINO -> "Elige junto a los Traidores a quién atacar esta noche."
                GamePhase.NOCHE_MERCENARIO -> "Elige a quién silenciar mientras el pueblo duerme."
                GamePhase.NOCHE_POLICIA -> "Elige a quién investigar durante esta noche."
                GamePhase.NOCHE_MEDICO -> "Elige a quién proteger antes del amanecer."
                GamePhase.NOCHE_ORACULO -> "Elige si una voz eliminada vuelve a discutir mañana."
                else -> "El pueblo duerme. Las acciones nocturnas ocurren a la vez."
            }
        } else {
            "El pueblo duerme. Las acciones nocturnas ocurren a la vez."
        }
    }

    private fun immersiveOnlineWaitingHint(): String {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO ->
                "La noche guarda sus últimos secretos..."
            GamePhase.AMANECER ->
                "Las campanas anuncian el amanecer..."
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION,
            GamePhase.ALCALDE_DESEMPATE ->
                "Los últimos votos caen en la urna..."
            else ->
                "El pueblo contiene el aliento..."
        }
    }

    private fun phaseText(phase: GamePhase): GameplayPhaseText {
        val roleForPhase = when (phase) {
            GamePhase.NOCHE_ASESINO -> RoleCatalog.ASESINO
            GamePhase.NOCHE_MERCENARIO -> RoleCatalog.MERCENARIO
            GamePhase.NOCHE_POLICIA -> RoleCatalog.POLICIA
            GamePhase.NOCHE_MEDICO -> RoleCatalog.MEDICO
            GamePhase.NOCHE_ORACULO -> RoleCatalog.ORACULO
            else -> null
        }
        return GameplayPhasePresentation.phaseText(
            phase = phase,
            round = session.round,
            winnerPresent = session.winner.isNotBlank(),
            nightSubtitle = if (roleForPhase == null) "" else nightSubtitle(),
            humanRoleTurn = when (roleForPhase) {
                RoleCatalog.ORACULO -> canHumanOracleChooseThisNight()
                null -> false
                else -> isHumanRoleTurn(roleForPhase)
            }
        )
    }

    private fun scheduleAutoAdvanceIfNeeded() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
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
        // El arranque de la primera noche online lo decide el gate de sincronizacion
        // (todos terminaron o vencio la cuenta compartida), NO el countdown general de fases.
        // REPARTO no tiene timer (activePhaseSeconds == null), asi que sin esta
        // guarda el countdown expira al instante y el host arranca la noche por la via
        // generica (when(REPARTO) -> startNight), salteando el gate y sin limpiar
        // onlineAwaitingHostAdvance. Ese flag queda en true y hace que activePhaseSeconds
        // devuelva null en todas las fases, resolviendolas sin esperar el reloj.
        if (isOnlineGameplay() && isOnlineStartupPhase()) {
            clearCountdown()
            return
        }
        // El invitado online que espera al host no corre countdown: se queda quieto hasta
        // recibir estadoPartida. Sin esta guarda, en fases sin timer (AMANECER/RECUENTO) un
        // countdown de duracion 0 expira apenas arranca, onCountdownExpired vuelve a renderizar,
        // y el ciclo renderGame -> startCountdown -> onCountdownExpired se realimenta en bucle,
        // satura el hilo principal y ahoga el listener de Firestore. El host avanza por timer.
        if (
            isOnlineGameplay() &&
            !onlineIsHost &&
            (onlineAwaitingHostAdvance || activePhaseSeconds() == null)
        ) {
            clearCountdown()
            return
        }
        if (isOnlineGameplay()) {
            if (onlineIsHost) {
                ensureOnlinePhaseDeadlineForHost()
            }
            val activeDurationMs = activePhaseSeconds()?.times(1000L)
            if (activeDurationMs != null) {
                val hasCurrentDeadline =
                    session.onlinePhaseDeadlinePhaseIndex == session.phaseIndex &&
                        session.onlinePhaseDeadlineEpochMs > 0L
                if (!hasCurrentDeadline) {
                    clearCountdown()
                    return
                }
                val remainingMs = OnlineAuthoritativeStateMapper.remainingPhaseMillis(
                    deadlineEpochMs = session.onlinePhaseDeadlineEpochMs,
                    nowEpochMs = System.currentTimeMillis()
                )
                countdown.syncActive(
                    phaseIndex = session.phaseIndex,
                    totalMs = activeDurationMs,
                    remainingMs = remainingMs
                )
                startCountdown()
                return
            }
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
        renderReadyToVoteButton()
        maybeAdvanceOnlineReadyVote()
        maybeResolveOnlineNightEarly()
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
        // DIAG temporal: entender por que el host resuelve fases sin esperar el timer.
        OnlineDebugLog.i(
            "diag_cd_expired phase=${session.phase.name} pIdx=${session.phaseIndex} cIdx=${countdown.phaseIndex} stage=${countdown.stage} active=${activePhaseSeconds()} awaiting=$onlineAwaitingHostAdvance host=$onlineIsHost timing=${session.timingConfig.summary()}"
        )
        if (localPhaseResolutionInProgress) {
            OnlineDebugLog.i(
                "local_phase_timer_ignored phase=${session.phase.name} phaseIndex=${session.phaseIndex} reason=resolution_in_progress"
            )
            clearCountdown()
            return
        }
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
        if (isOnlineGameplay()) {
            if (!onlineIsHost) {
                setOnlineAwaitingHostAdvance(true)
                lastPublishedOnlineStateKey = ""
                OnlineDebugLog.i(
                    "phase_gate_wait roomId=$onlinePartidaId uid=$onlinePlayerId phase=${session.phase.name} phaseIndex=${session.phaseIndex} reason=timer_expired_guest"
                )
                renderGame()
                return
            }
            if (isNightPhase(expiredPhase)) {
                if (onlineNightGateFloorMs <= 0L) {
                    maybeResolveOnlineNightEarly()
                }
                val elapsedMs = SystemClock.elapsedRealtime() - onlineNightGateStartedAtMs
                val remainingFloorMs = onlineNightGateFloorMs - elapsedMs
                if (remainingFloorMs > 0L) {
                    onlineNightTimerExpired = true
                    autoAdvanceHandler.removeCallbacks(onlineNightGateRunnable)
                    autoAdvanceHandler.postDelayed(onlineNightGateRunnable, remainingFloorMs)
                    OnlineDebugLog.i(
                        "night_timer_waits_for_secret_floor roomId=$onlinePartidaId round=${session.round} remainingMs=$remainingFloorMs"
                    )
                    renderGame()
                    return
                }
                resolveOnlineNightWindowFromFirestore()
                return
            }
            if (expiredPhase == GamePhase.VOTACION) {
                resolveOnlineVotingFromFirestore(tieVote = false)
                return
            }
            if (expiredPhase == GamePhase.DESEMPATE_VOTACION) {
                resolveOnlineVotingFromFirestore(tieVote = true)
                return
            }
            if (expiredPhase == GamePhase.ALCALDE_DESEMPATE) {
                resolveOnlineAlcaldeDecisionFromFirestore()
                return
            }
        }
        if (!isOnlineGameplay()) {
            val before = session
            resolveLocalPhaseOffMainThread(
                before = before,
                operation = "timer_expired",
                progressMessage = localPhaseProgressMessage(expiredPhase),
                resolver = ::resolveExpiredLocalPhase
            ) { resolved ->
                session = resolved
                if (expiredPhase == GamePhase.DESEMPATE_VOTACION) {
                    hideTieVoteWindow(clearSelection = true)
                }
                clearSelection()
                renderGame()
            }
            return
        }
        session = when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> resolveOnlineNightWindow()
            GamePhase.DIA_DEBATE -> GameEngine.resolveDayDebateWithoutOptionalBotActions(session)
            GamePhase.CONTRAPUNTO -> GameEngine.resolveContrapuntoTimeout(session)
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

    private fun resolveExpiredLocalPhase(source: GameSession): GameSession {
        return when (source.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> GameEngine.resolveLocalNightWindowTimeout(source)
            GamePhase.DIA_DEBATE -> GameEngine.resolveDayDebate(source)
            GamePhase.CONTRAPUNTO -> {
                if (GameEngine.requiresHumanInput(source)) {
                    GameEngine.resolveContrapuntoTimeout(source)
                } else {
                    GameEngine.resolveContrapunto(source, "")
                }
            }
            GamePhase.VOTACION -> {
                if (GameEngine.requiresHumanInput(source)) {
                    GameEngine.resolveHumanTimeout(source)
                } else {
                    GameEngine.resolveVoting(source, "")
                }
            }
            GamePhase.RECUENTO_VOTOS -> source
            GamePhase.DESEMPATE_VOTACION -> {
                if (GameEngine.requiresHumanInput(source)) {
                    GameEngine.resolveHumanTimeout(source)
                } else {
                    GameEngine.resolveTieVoting(source, "")
                }
            }
            GamePhase.ALCALDE_DESEMPATE -> GameEngine.resolveAlcaldeTieTimeout(source)
            GamePhase.REPARTO -> GameEngine.startNight(source)
            GamePhase.AMANECER -> GameEngine.resolveDawn(source)
            GamePhase.RESULTADO -> GameEngine.resolveResult(source)
        }
    }

    private fun skipRemainingNight() {
        if (!canSkipRemainingNight()) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        clearCountdown()
        resetNightSkipArm()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        chatController.cancelPendingBotChat()

        val before = session
        resolveLocalPhaseOffMainThread(
            before = before,
            operation = "skip_night",
            progressMessage = "Resolviendo la noche...",
            resolver = ::resolveLocalNightWithoutHumanInput
        ) { resolved ->
            session = resolved
            clearSelection()
            renderGame()
        }
    }

    private fun resolveLocalNightWithoutHumanInput(source: GameSession): GameSession {
        var advanced = source
        var guard = 0
        while (
            isNightPhase(advanced.phase) &&
            !GameEngine.requiresHumanInput(advanced) &&
            guard < MAX_NIGHT_SKIP_STEPS
        ) {
            val before = advanced
            advanced = advanceNightSessionWithoutRendering(advanced)
            if (advanced == before) break
            guard += 1
        }

        return advanced
    }

    private fun advanceSessionWithoutRendering(): GameSession {
        return advanceNightSessionWithoutRendering(session)
    }

    private fun advanceNightSessionWithoutRendering(source: GameSession): GameSession {
        return when (source.phase) {
            GamePhase.NOCHE_ASESINO -> GameEngine.resolveAssassin(source, "")
            GamePhase.NOCHE_MERCENARIO -> GameEngine.resolveMercenary(source, "")
            GamePhase.NOCHE_POLICIA -> GameEngine.resolvePolice(source, "")
            GamePhase.NOCHE_MEDICO -> GameEngine.resolveMedic(source, "")
            GamePhase.NOCHE_ORACULO -> GameEngine.resolveOracle(source, "")
            else -> source
        }
    }

    private fun resolveOnlineNightWindow(): GameSession {
        var resolved = session
        while (isNightPhase(resolved.phase)) {
            val before = resolved
            resolved = GameEngine.skipOnlineNightAction(resolved)
            if (resolved == before) break
        }
        return resolved
    }

    private fun resolveOnlineNightWindowFromFirestore(countAfkMisses: Boolean = true) {
        if (onlineNightResolutionInProgress) return
        // Una vez por noche, y siempre antes de que la resolucion pueda declarar ganador.
        maybeAutoResolveOnlineDesertorTeam()
        onlineNightResolutionInProgress = true
        OnlineDebugLog.i("night_resolve_requested roomId=$onlinePartidaId host=$onlineIsHost round=${session.round}")
        var query: Query = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("acciones")
        if (session.onlineMatchId.isNotBlank()) {
            query = query.whereEqualTo("matchId", session.onlineMatchId)
        }
        query
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val actions = onlineActionRecordsFromSnapshot(snapshot.documents)
                applyConfirmedOnlineNightActions(actions, countAfkMisses)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("night_resolve_actions_failure roomId=$onlinePartidaId round=${session.round}", error)
                GameNotice.show(
                    activity = this,
                    message = OnlineErrorMessages.forAction("No se pudieron leer acciones de noche", error),
                    duration = GameNotice.Duration.LONG
                )
                val before = session
                session = resolveOnlineNightWindow()
                onlineNightResolutionInProgress = false
                recordOnlinePhaseAdvance(before, session)
                chatController.onPhaseSettled()
                clearSelection()
                renderGame()
            }
    }

    private fun resolveOnlineNightWindowFromConfirmedActions(
        actions: List<OnlineActionRecord>,
        countAfkMisses: Boolean
    ) {
        if (onlineNightResolutionInProgress) return
        maybeAutoResolveOnlineDesertorTeam()
        onlineNightResolutionInProgress = true
        OnlineDebugLog.i(
            "night_resolve_confirmed_actions roomId=$onlinePartidaId host=$onlineIsHost round=${session.round}"
        )
        applyConfirmedOnlineNightActions(actions, countAfkMisses)
    }

    private fun applyConfirmedOnlineNightActions(
        actions: List<OnlineActionRecord>,
        countAfkMisses: Boolean
    ) {
        val before = session
        val nightActions = OnlineActionResolver.nightActions(
            records = actions,
            matchId = session.onlineMatchId,
            round = session.round,
            phaseIndex = session.phaseIndex
        )
        OnlineDebugLog.i(
            "night_resolve_actions_loaded roomId=$onlinePartidaId round=${session.round} actions=${actions.size} valid=${nightActions.validActionCount} assassinVotes=${nightActions.assassinVotes.size}"
        )
        val requiredIndexes = requiredOnlineNightPlayerIndexes(session)
        val actedIndexes = actedOnlineNightPlayerIndexes(session, actions)
        val afkRequiredIndexes = if (countAfkMisses) requiredIndexes else actedIndexes
        val afterAfk = GameEngine.applyOnlineAfkOpportunity(
            session = session,
            opportunity = AfkOpportunity.NIGHT,
            requiredPlayerIndexes = afkRequiredIndexes,
            actedPlayerIndexes = actedIndexes
        )
        val afkAnnouncement = onlineAfkExpulsionAnnouncement(before, afterAfk)
        session = prependOnlineAnnouncement(
            resolveOnlineNightWindow(nightActions, afterAfk),
            afkAnnouncement
        )
        publishOnlineInvestigationClue(nightActions.policeAction)
        onlineNightResolutionInProgress = false
        recordOnlinePhaseAdvance(before, session)
        chatController.onPhaseSettled()
        clearSelection()
        renderGame()
        notifyLocalOnlineAfkChange(before, session)
    }

    private fun maybePublishOnlineInvestigationClueEarly(actions: List<OnlineActionRecord>) {
        if (!onlineIsHost || session.winner.isNotBlank()) return
        val action = OnlineActionResolver.nightActions(
            records = actions,
            matchId = session.onlineMatchId,
            round = session.round,
            phaseIndex = session.phaseIndex
        ).policeAction ?: return
        val actorIndex = onlineActorIndex(session, action) ?: return
        val actor = session.players.getOrNull(actorIndex) ?: return
        val expectedActorUid = session.onlinePlayerUids.getOrNull(actorIndex).orEmpty()
        val target = session.players.firstOrNull { it.name == action.targetName } ?: return
        if (
            action.action != "investigar" ||
            action.actorId.isBlank() ||
            expectedActorUid != action.actorId ||
            actor.name != action.actorName ||
            !actor.alive ||
            actor.role?.key != RoleCatalog.POLICIA ||
            !target.alive ||
            target.name == actor.name
        ) {
            return
        }
        publishOnlineInvestigationClue(
            action = action,
            targetName = target.name,
            result = GameEngine.investigationResult(target)
        )
    }

    private fun publishOnlineInvestigationClue(action: OnlineActionRecord?) {
        if (!onlineIsHost || action == null || action.actorId.isBlank()) return
        val targetName = session.investigatedPlayer.takeIf { it.isNotBlank() }
            ?: action.targetName.takeIf { it.isNotBlank() }
            ?: return
        val result = session.investigatedResult
        if (result !in PRIVATE_INVESTIGATION_RESULTS) return
        publishOnlineInvestigationClue(action, targetName, result)
    }

    private fun publishOnlineInvestigationClue(
        action: OnlineActionRecord,
        targetName: String,
        result: String
    ) {
        if (!onlineIsHost || action.actorId.isBlank()) return
        if (result !in PRIVATE_INVESTIGATION_RESULTS) return
        val clueKey = listOf(
            action.matchId,
            action.round,
            action.phaseIndex,
            action.actorId,
            targetName,
            result
        ).joinToString("|")
        if (clueKey == lastPublishedOnlineInvestigationClueKey) return
        lastPublishedOnlineInvestigationClueKey = clueKey
        val clue = mapOf(
            "matchId" to action.matchId,
            "ronda" to action.round,
            "phaseIndex" to action.phaseIndex,
            "objetivoNombre" to targetName,
            "resultado" to result,
            "actualizadaEn" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("repartos")
            .document(action.actorId)
            .set(mapOf(FIELD_PRIVATE_INVESTIGATION_CLUE to clue), SetOptions.merge())
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "private_investigation_clue_published roomId=$onlinePartidaId actor=${action.actorId} round=${action.round} target=$targetName result=$result"
                )
            }
            .addOnFailureListener { error ->
                if (lastPublishedOnlineInvestigationClueKey == clueKey) {
                    lastPublishedOnlineInvestigationClueKey = ""
                }
                OnlineDebugLog.e(
                    "private_investigation_clue_publish_failure roomId=$onlinePartidaId actor=${action.actorId} round=${action.round}",
                    error
                )
            }
    }

    private fun resolveOnlineNightWindow(
        actions: OnlineNightResolutionActions,
        source: GameSession = session
    ): GameSession {
        if (source.winner.isNotBlank()) return source
        var resolved = source
        resolved = GameEngine.resolveAssassinWithRecordedVotes(
            resolved.copy(phase = GamePhase.NOCHE_ASESINO),
            actions.assassinVotes
        )
        resolved = resolveOnlineNightPhaseAction(
            current = resolved,
            phase = GamePhase.NOCHE_MERCENARIO,
            action = actions.mercenaryAction,
            expectedRoleKey = "mercenario",
            resolver = { current, target -> GameEngine.resolveMercenary(current, target) }
        )
        resolved = resolveOnlineNightPhaseAction(
            current = resolved,
            phase = GamePhase.NOCHE_POLICIA,
            action = actions.policeAction,
            expectedRoleKey = "policia",
            resolver = { current, target -> GameEngine.resolvePolice(current, target) }
        )
        resolved = resolveOnlineNightPhaseAction(
            current = resolved,
            phase = GamePhase.NOCHE_MEDICO,
            action = actions.medicAction,
            expectedRoleKey = "medico",
            resolver = { current, target -> GameEngine.resolveMedic(current, target) }
        )
        resolved = resolveOnlineNightPhaseAction(
            current = resolved,
            phase = GamePhase.NOCHE_ORACULO,
            action = actions.oracleAction,
            expectedRoleKey = "oraculo",
            resolver = { current, target -> GameEngine.resolveOracle(current, target) }
        )
        return resolved
    }

    private fun resolveOnlineNightPhaseAction(
        current: GameSession,
        phase: GamePhase,
        action: OnlineActionRecord?,
        expectedRoleKey: String,
        resolver: (GameSession, String) -> GameSession
    ): GameSession {
        if (current.winner.isNotBlank()) return current
        val phased = current.copy(phase = phase)
        if (
            action == null ||
            action.targetName.isBlank() ||
            phased.players.firstOrNull { it.name == action.actorName }?.role?.key != expectedRoleKey
        ) {
            return GameEngine.skipOnlineNightAction(phased)
        }
        val actorName = action.actorName
        val actionSession = phased.copy(
            players = phased.players.map { player ->
                player.copy(isHuman = player.name == actorName)
            }
        )
        val resolved = resolver(actionSession, action.targetName)
        val actorWasLocal = current.players.firstOrNull { it.name == actorName }?.isHuman == true
        return resolved.copy(
            players = resolved.players.map { resolvedPlayer ->
                val original = current.players.firstOrNull { it.name == resolvedPlayer.name }
                resolvedPlayer.copy(isHuman = original?.isHuman == true)
            },
            privateHint = if (actorWasLocal) resolved.privateHint else current.privateHint
        )
    }

    private fun onlineActionRecordsFromSnapshot(documents: List<DocumentSnapshot>): List<OnlineActionRecord> {
        return documents.mapNotNull { document ->
            if (document.getString("tipo").orEmpty() != "accion_jugador") return@mapNotNull null
            val details = document.get("detalles").asStringAnyMap()
            val action = details?.get("accion") as? String ?: return@mapNotNull null
            val actorOrder = (details["actorOrden"] as? Number)?.toInt() ?: -1
            val targetOrder = (details["objetivoOrden"] as? Number)?.toInt() ?: -1
            val actorName = session.players.getOrNull(actorOrder)?.name
                ?: document.getString("actorNombre").orEmpty()
            val targetName = session.players.getOrNull(targetOrder)?.name
                ?: document.getString("objetivoNombre").orEmpty()
            OnlineActionRecord(
                matchId = document.getString("matchId").orEmpty(),
                actorId = document.getString("actorId").orEmpty(),
                action = action,
                actorName = actorName,
                targetName = targetName,
                phaseName = document.getString("fase").orEmpty(),
                round = document.getLong("ronda")?.toInt() ?: -1,
                phaseIndex = document.getLong("phaseIndex")?.toInt() ?: -1,
                createdAtLocal = document.getLong("creadaEnLocal") ?: 0L,
                actorOrder = actorOrder,
                targetOrder = targetOrder
            )
        }
    }

    private fun requiredOnlineNightPlayerIndexes(source: GameSession): Set<Int> {
        val oracleCandidateCount = GameEngine.oracleCandidates(source).size
        return source.players.indices.filterTo(mutableSetOf()) { index ->
            val player = source.players[index]
            player.alive &&
                OnlineNightReadyGate.roleRequiresAction(
                    roleKey = player.role?.key.orEmpty(),
                    round = source.round,
                    oracleUsed = source.oracleUsed,
                    oracleCandidateCount = oracleCandidateCount
                )
        }
    }

    private fun actedOnlineNightPlayerIndexes(
        source: GameSession,
        records: List<OnlineActionRecord>
    ): Set<Int> {
        return records.asSequence()
            .filter { record -> onlineRecordMatchesCurrentWindow(source, record) }
            .mapNotNull { record ->
                val actorIndex = onlineActorIndex(source, record) ?: return@mapNotNull null
                val actor = source.players[actorIndex]
                actorIndex.takeIf {
                    actor.alive && record.action in onlineNightActionsForRole(actor.role?.key.orEmpty())
                }
            }
            .toSet()
    }

    private fun actedOnlineVotePlayerIndexes(
        source: GameSession,
        records: List<OnlineActionRecord>,
        expectedPhaseName: String
    ): Set<Int> {
        return records.asSequence()
            .filter { record -> onlineRecordMatchesCurrentWindow(source, record) }
            .filter { record ->
                record.phaseName == expectedPhaseName &&
                    record.action == "votar" &&
                    record.targetName.isNotBlank()
            }
            .mapNotNull { record -> onlineActorIndex(source, record) }
            .filter { index -> GameEngine.canVote(source.players[index]) }
            .toSet()
    }

    private fun onlineRecordMatchesCurrentWindow(
        source: GameSession,
        record: OnlineActionRecord
    ): Boolean {
        return record.matchId == source.onlineMatchId &&
            record.round == source.round &&
            record.phaseIndex == source.phaseIndex
    }

    private fun onlineActorIndex(source: GameSession, record: OnlineActionRecord): Int? {
        if (record.actorOrder in source.players.indices) return record.actorOrder
        return source.players.indexOfFirst { it.name == record.actorName }.takeIf { it >= 0 }
    }

    private fun onlineAfkExpulsionAnnouncement(
        before: GameSession,
        after: GameSession
    ): String {
        val names = before.players.mapIndexedNotNull { index, previous ->
            val updated = after.players.getOrNull(index) ?: return@mapIndexedNotNull null
            previous.name.takeIf {
                previous.alive && !updated.alive && updated.deathCause == DeathCause.AFK
            }
        }
        if (names.isEmpty()) return ""
        val readableNames = when (names.size) {
            1 -> names.first()
            2 -> names.joinToString(" y ")
            else -> names.dropLast(1).joinToString(", ") + " y " + names.last()
        }
        return if (names.size == 1) {
            "$readableNames fue expulsado por inactividad."
        } else {
            "$readableNames fueron expulsados por inactividad."
        }
    }

    private fun prependOnlineAnnouncement(source: GameSession, prefix: String): GameSession {
        if (prefix.isBlank() || source.publicAnnouncement.startsWith(prefix)) return source
        val message = listOf(prefix, source.publicAnnouncement)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return source.copy(publicAnnouncement = message)
    }

    private fun notifyLocalOnlineAfkChange(before: GameSession, after: GameSession) {
        if (!isOnlineGameplay()) return
        val expelledNames = before.players.mapIndexedNotNull { index, previous ->
            val updated = after.players.getOrNull(index) ?: return@mapIndexedNotNull null
            previous.name.takeIf {
                previous.alive && !updated.alive && updated.deathCause == DeathCause.AFK
            }
        }
        if (expelledNames.isNotEmpty()) {
            val names = when (expelledNames.size) {
                1 -> expelledNames.first()
                2 -> expelledNames.joinToString(" y ")
                else -> expelledNames.dropLast(1).joinToString(", ") + " y " + expelledNames.last()
            }
            val humanName = before.players.firstOrNull { it.isHuman }?.name.orEmpty()
            val base = if (expelledNames.size == 1) {
                "$names fue expulsado por inactividad."
            } else {
                "$names fueron expulsados por inactividad."
            }
            val consequence = if (after.winner.isNotBlank()) {
                "Esto decidió la partida. A continuación verás el resultado."
            } else {
                "La partida continúa sin ellos."
            }
            val personal = if (humanName in expelledNames) {
                "\n\nTambién quedaste fuera y seguirás como espectador."
            } else {
                ""
            }
            GameDialog.notice(
                activity = this,
                title = "INACTIVIDAD",
                message = "$base $consequence$personal"
            )
            return
        }
        val previousIndex = before.players.indexOfFirst { it.isHuman }
        if (previousIndex !in before.players.indices) return
        val previous = before.players[previousIndex]
        val updated = after.players.getOrNull(previousIndex) ?: return
        val message = when {
            previous.alive && !updated.alive && updated.deathCause == DeathCause.AFK ->
                AfkPolicy.selfExpelledMessage()
            updated.alive && updated.consecutiveNightAfk > previous.consecutiveNightAfk ->
                AfkPolicy.warning(AfkOpportunity.NIGHT, expulsionEnabled = true)
            updated.alive && updated.consecutiveVoteAfk > previous.consecutiveVoteAfk ->
                AfkPolicy.warning(AfkOpportunity.VOTE, expulsionEnabled = true)
            else -> return
        }
        GameNotice.show(
            activity = this,
            message = message,
            duration = GameNotice.Duration.LONG
        )
    }

    private fun resolveOnlineVotingFromFirestore(tieVote: Boolean) {
        if (onlineVoteResolutionInProgress) return
        onlineVoteResolutionInProgress = true
        OnlineDebugLog.i(
            "vote_resolve_requested roomId=$onlinePartidaId host=$onlineIsHost round=${session.round} tie=$tieVote phase=${session.phase.name}"
        )
        var query: Query = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("acciones")
        if (session.onlineMatchId.isNotBlank()) {
            query = query.whereEqualTo("matchId", session.onlineMatchId)
        }
        query
            .get()
            .addOnSuccessListener { snapshot ->
                val expectedPhase = if (tieVote) {
                    GamePhase.DESEMPATE_VOTACION.name
                } else {
                    GamePhase.VOTACION.name
                }
                val actionRecords = onlineActionRecordsFromSnapshot(snapshot.documents)
                val votes = OnlineActionResolver.votes(
                    records = actionRecords,
                    matchId = session.onlineMatchId,
                    round = session.round,
                    expectedPhaseName = expectedPhase,
                    phaseIndex = session.phaseIndex
                )
                val before = session
                OnlineDebugLog.i(
                    "vote_resolve_votes_loaded roomId=$onlinePartidaId round=${session.round} tie=$tieVote actions=${actionRecords.size} votes=${votes.size}"
                )
                val afterAfk = GameEngine.applyOnlineAfkOpportunity(
                    session = session,
                    opportunity = AfkOpportunity.VOTE,
                    requiredPlayerIndexes = session.players.indices
                        .filterTo(mutableSetOf()) { GameEngine.canVote(session.players[it]) },
                    actedPlayerIndexes = actedOnlineVotePlayerIndexes(
                        source = session,
                        records = actionRecords,
                        expectedPhaseName = expectedPhase
                    )
                )
                val afkAnnouncement = onlineAfkExpulsionAnnouncement(before, afterAfk)
                val resolved = if (tieVote) {
                    GameEngine.resolveTieVotingWithRecordedVotes(afterAfk, votes)
                } else {
                    GameEngine.resolveVotingWithRecordedVotes(afterAfk, votes)
                }
                session = prependOnlineAnnouncement(resolved, afkAnnouncement)
                onlineVoteResolutionInProgress = false
                recordOnlinePhaseAdvance(before, session)
                if (tieVote) hideTieVoteWindow(clearSelection = true)
                clearSelection()
                renderGame()
                notifyLocalOnlineAfkChange(before, session)
            }
            .addOnFailureListener { error ->
                onlineVoteResolutionInProgress = false
                OnlineDebugLog.e(
                    "vote_resolve_failure roomId=$onlinePartidaId round=${session.round} tie=$tieVote phase=${session.phase.name}",
                    error
                )
                GameNotice.show(
                    activity = this,
                    message = OnlineErrorMessages.forAction("No se pudieron leer votos online", error),
                    duration = GameNotice.Duration.LONG
                )
                val before = session
                session = if (tieVote) {
                    GameEngine.resolveTieVotingWithRecordedVotes(session, emptyMap())
                } else {
                    GameEngine.resolveVotingWithRecordedVotes(session, emptyMap())
                }
                recordOnlinePhaseAdvance(before, session)
                if (tieVote) hideTieVoteWindow(clearSelection = true)
                clearSelection()
                renderGame()
            }
    }

    private fun resolveOnlineAlcaldeDecisionFromFirestore() {
        OnlineDebugLog.i(
            "alcalde_resolve_requested roomId=$onlinePartidaId host=$onlineIsHost round=${session.round} phase=${session.phase.name}"
        )
        var query: Query = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(onlinePartidaId)
            .collection("acciones")
        if (session.onlineMatchId.isNotBlank()) {
            query = query.whereEqualTo("matchId", session.onlineMatchId)
        }
        query
            .get()
            .addOnSuccessListener { snapshot ->
                val decision = snapshot.documents.mapNotNull { document ->
                    if (document.getString("tipo").orEmpty() != "accion_jugador") return@mapNotNull null
                    if (document.getString("matchId").orEmpty() != session.onlineMatchId) return@mapNotNull null
                    if (document.getLong("ronda")?.toInt() != session.round) return@mapNotNull null
                    if (document.getLong("phaseIndex")?.toInt() != session.phaseIndex) return@mapNotNull null
                    if (document.getString("fase").orEmpty() != GamePhase.ALCALDE_DESEMPATE.name) return@mapNotNull null
                    val details = document.get("detalles").asStringAnyMap()
                    if ((details?.get("accion") as? String) != "votar") return@mapNotNull null
                    val actor = document.getString("actorNombre").orEmpty()
                    val target = document.getString("objetivoNombre").orEmpty()
                    if (actor.isBlank() || target.isBlank()) return@mapNotNull null
                    Triple(actor, target, document.getLong("creadaEnLocal") ?: 0L)
                }
                    .sortedBy { it.third }
                    .lastOrNull()
                val before = session
                OnlineDebugLog.i(
                    "alcalde_resolve_decision_loaded roomId=$onlinePartidaId round=${session.round} hasDecision=${decision != null}"
                )
                session = if (decision == null) {
                    GameEngine.resolveAlcaldeTieTimeout(session)
                } else {
                    val actorName = decision.first
                    val targetName = decision.second
                    val mayorSession = session.copy(
                        alcaldeRevealed = true,
                        players = session.players.map { player ->
                            player.copy(isHuman = player.name == actorName)
                        }
                    )
                    val resolved = GameEngine.chooseAlcaldeTie(mayorSession, targetName)
                    resolved.copy(
                        players = resolved.players.map { resolvedPlayer ->
                            val original = session.players.firstOrNull { it.name == resolvedPlayer.name }
                            resolvedPlayer.copy(isHuman = original?.isHuman == true)
                        }
                    )
                }
                recordOnlinePhaseAdvance(before, session)
                clearSelection()
                renderGame()
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "alcalde_resolve_failure roomId=$onlinePartidaId round=${session.round} phase=${session.phase.name}",
                    error
                )
                GameNotice.show(
                    activity = this,
                    message = OnlineErrorMessages.forAction("No se pudo leer decision del alcalde", error),
                    duration = GameNotice.Duration.LONG
                )
                val before = session
                session = GameEngine.resolveAlcaldeTieTimeout(session)
                recordOnlinePhaseAdvance(before, session)
                clearSelection()
                renderGame()
            }
    }

    private fun activePhaseSeconds(): Int? {
        // onlineAwaitingHostAdvance significa "invitado esperando el estado del host": solo
        // suprime el reloj local del invitado. El host nunca espera a nadie, asi que su reloj
        // no debe anularse aunque el flag quede en true por algun camino de arranque.
        if (!onlineIsHost && onlineAwaitingHostAdvance) return null
        val timing = session.timingConfig.normalized()
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO,
            GamePhase.NOCHE_ORACULO -> {
                if (!isOnlineGameplay() && !GameEngine.requiresHumanInput(actionSession())) {
                    LOCAL_NO_INPUT_NIGHT_SECONDS
                } else {
                    timing.nightSeconds
                }
            }
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO -> timing.discussionSeconds
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> timing.votingSeconds
            GamePhase.DESEMPATE_VOTACION -> (timing.votingSeconds / 2).coerceAtLeast(20)
            GamePhase.REPARTO,
            GamePhase.AMANECER,
            GamePhase.RECUENTO_VOTOS,
            GamePhase.RESULTADO -> null
        }
    }

    private fun ensureOnlinePhaseDeadlineForHost() {
        if (!isOnlineGameplay() || !onlineIsHost) return
        val activeSeconds = activePhaseSeconds()
        if (session.winner.isNotBlank() || activeSeconds == null) {
            if (
                session.onlinePhaseDeadlineEpochMs != 0L ||
                session.onlinePhaseDeadlinePhaseIndex != session.phaseIndex
            ) {
                session = session.copy(
                    onlinePhaseDeadlineEpochMs = 0L,
                    onlinePhaseDeadlinePhaseIndex = session.phaseIndex
                )
            }
            return
        }
        if (
            session.onlinePhaseDeadlinePhaseIndex == session.phaseIndex &&
            session.onlinePhaseDeadlineEpochMs > 0L
        ) {
            return
        }
        session = session.copy(
            onlinePhaseDeadlineEpochMs = System.currentTimeMillis() + activeSeconds * 1000L,
            onlinePhaseDeadlinePhaseIndex = session.phaseIndex
        )
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
        if (isHumanCardPubliclyRevealed()) {
            showRolePreview()
            return
        }
        GameplayEffects.play(this, GameplayEffect.REVEAL)
        isCardRevealed = !isCardRevealed
        renderHumanCardIfVisible()
    }

    override fun renderHumanCardIfVisible() {
        val human = GameEngine.humanPlayer(session)
        val role = human.role
        val publicRoleVisible = isHumanCardPubliclyRevealed()
        val showRole = isCardRevealed || session.phase == GamePhase.REPARTO || publicRoleVisible
        val animatePublicReveal =
            humanCardHasRendered && publicRoleVisible && !lastHumanPublicRoleVisible
        humanCardHasRendered = true
        lastHumanPublicRoleVisible = publicRoleVisible
        if (animatePublicReveal) {
            roleCard.cameraDistance = dp(900).toFloat()
            roleCard.animate().cancel()
            roleCard.animate()
                .rotationY(90f)
                .setDuration(150L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    applyHumanCardVisual(role, showRole, publicRoleVisible)
                    roleCard.rotationY = -90f
                    roleCard.animate()
                        .rotationY(0f)
                        .setDuration(190L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
        } else {
            applyHumanCardVisual(role, showRole, publicRoleVisible)
        }
    }

    private fun isHumanCardPubliclyRevealed(): Boolean {
        if (isOnlineGameplay()) return false
        val human = GameEngine.humanPlayer(session)
        return (session.alcaldeRevealed && human.role?.key == RoleCatalog.ALCALDE) ||
            (!human.alive && session.revealRolesOnDeath)
    }

    private fun applyHumanCardVisual(
        role: GameRole?,
        showRole: Boolean,
        publicRoleVisible: Boolean
    ) {
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
        val human = GameEngine.humanPlayer(session)
        val deathIcon = when (human.deathCause) {
            DeathCause.NIGHT -> R.drawable.death_blood_splatter_art
            DeathCause.VOTE,
            DeathCause.AFK -> R.drawable.ic_kicking_boot
            DeathCause.NONE -> 0
        }
        humanDeathCauseOverlay.visibility = if (!human.alive && deathIcon != 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (deathIcon != 0) {
            humanDeathCauseOverlay.setImageResource(deathIcon)
            applyDeathCauseIconStyle(humanDeathCauseOverlay, human.deathCause)
        }
        bindHumanActionMark(human)
        roleImage.alpha = if (human.alive) 1f else 0.58f
        if (showRole) {
            val imageRes = roleImageFor(role)
            if (lastRoleCardImageRes != imageRes) {
                roleImage.setImageResource(imageRes)
                lastRoleCardImageRes = imageRes
            }
            roleName.text = role?.let {
                "${it.name.uppercase()} - ${it.team.uppercase()}"
            } ?: "SIN ROL"
        } else {
            if (lastRoleCardImageRes != R.drawable.card_back_traidores) {
                roleImage.setImageResource(R.drawable.card_back_traidores)
                lastRoleCardImageRes = R.drawable.card_back_traidores
            }
            roleName.text = "CARTA OCULTA"
        }
        btnRevealCard.text = when {
            session.phase == GamePhase.REPARTO -> "MI CARTA"
            publicRoleVisible -> "REVELADA"
            showRole -> "OCULTAR"
            else -> "VER CARTA"
        }
        btnRevealCard.isEnabled = session.phase != GamePhase.REPARTO && !publicRoleVisible
        btnRevealCard.alpha = if (btnRevealCard.isEnabled) 1f else 0.7f
    }

    private fun showRolePreview(initialReveal: Boolean = false) {
        if (
            isRolePreviewOpen ||
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
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
        dismissSecondaryUiForPriorityWindow()
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
        autoAdvanceHandler.removeCallbacks(roleReadingTickRunnable)
        if (initialReveal) {
            btnCloseRolePreview.visibility = View.GONE
            val readingDelayMs = restoredRoleReadingRemainingMs
                .takeIf { it >= 0L }
                ?: initialRoleReadingDelayMs()
            restoredRoleReadingRemainingMs = -1L
            roleReadingReadyAtElapsedMs = SystemClock.elapsedRealtime() + readingDelayMs
            roleReadingTickRunnable.run()
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
            return
        }
        val shouldMarkOnlineRoleRead = resumeGameFlow &&
            initialRoleReadingActive &&
            isOnlineStartupPhase()
        autoAdvanceHandler.removeCallbacks(roleReadingTickRunnable)
        initialRoleReadingActive = false
        roleReadingReadyAtElapsedMs = 0L
        val wasOpen = isRolePreviewOpen
        if (!wasOpen || !resumeGameFlow) {
            rolePreviewAnimator.cancelAndHide()
            isRolePreviewOpen = false
            return
        }

        rolePreviewAnimator.dismiss {
            isRolePreviewOpen = false
            if (shouldMarkOnlineRoleRead) {
                markOnlineInitialRoleRead()
                renderGame()
            } else {
                resumeGameFlowAfterBlockingUi()
            }
        }
    }

    private fun roleFunction(roleKey: String): String =
        GameplayPhasePresentation.roleFunction(roleKey)

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
        activePhaseAdvice = phaseAdvice()?.let { "Objetivo: $it" }
        // Si es tu turno de actuar y todavia no elegiste objetivo, la guia queda fija
        // (no se auto-borra a los 8s) para que siempre veas que tenes que hacer.
        val keepUntilAction = requiresHumanInput() && selectedTarget.isBlank()
        if (activePhaseAdvice != null && activePhaseAdvice != publicMessage && !keepUntilAction) {
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

        if (!human.alive) return "Estás eliminado. Observa la partida y lee el cronista."
        if (human.muted && session.phase == GamePhase.DIA_DEBATE) {
            return "Estás silenciado. Lee el debate y prepara tu voto."
        }

        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> if (roleKey in GameRules.killerRoleKeys) {
                val allyHint = if (allies.isNotBlank()) " Aliados: $allies." else ""
                "Elige una víctima y confirma MATAR.$allyHint"
            } else {
                "No actúas en esta fase. Puedes mirar la noche o saltarla."
            }
            GamePhase.NOCHE_MERCENARIO -> if (roleKey == RoleCatalog.MERCENARIO) {
                "Elige a quién silenciar y confirma SILENCIAR."
            } else {
                "No actúas en esta fase. Puedes mirar la noche o saltarla."
            }
            GamePhase.NOCHE_POLICIA -> if (roleKey == RoleCatalog.POLICIA) {
                "Elige a quién investigar y confirma INVESTIGAR."
            } else {
                "No actúas en esta fase. Puedes mirar la noche o saltarla."
            }
            GamePhase.NOCHE_MEDICO -> if (roleKey == RoleCatalog.MEDICO) {
                "Elige a quién proteger y confirma PROTEGER."
            } else {
                "No actúas en esta fase. Puedes mirar la noche o saltarla."
            }
            GamePhase.NOCHE_ORACULO -> when {
                roleKey != RoleCatalog.ORACULO ->
                    "No actúas en esta fase. Puedes mirar la noche o saltarla."
                session.oracleUsed ->
                    "Ya usaste la invocación. Espera el amanecer."
                GameEngine.oracleCandidates(session).isEmpty() ->
                    "Todavía no hay muertos para invocar. Tu poder se conserva automáticamente."
                else ->
                    "Elige un muerto para invocar o guarda el poder para otra noche."
            }
            GamePhase.DIA_DEBATE -> when (roleKey) {
                RoleCatalog.PAYADOR -> if (session.payadorUsed) {
                    "El Contrapunto ya fue usado. Debate y prepara tu voto."
                } else {
                    "Puedes iniciar Contrapunto con dos jugadores o seguir al voto."
                }
                RoleCatalog.ALCALDE -> if (session.alcaldeRevealed) {
                    "Tu voto vale doble. Ordena el debate antes de votar."
                } else {
                    "Puedes revelarte como Alcalde o guardar tu autoridad."
                }
                RoleCatalog.ORACULO -> if (session.oracleInvitedPlayer.isNotBlank()) {
                    "Escucha al invocado y decide si conviene creerle."
                } else {
                    "Debate con el pueblo y guarda tu invocación para una muerte clave."
                }
                RoleCatalog.BUFON ->
                    "Tu objetivo es que el pueblo te expulse durante la votación."
                else ->
                    "Debate, compara versiones y prepara tu voto."
            }
            GamePhase.CONTRAPUNTO ->
                "Elige al participante que queda más sospechoso y confirma SEÑALAR."
            GamePhase.VOTACION ->
                "Elige a quién expulsar y confirma VOTAR."
            GamePhase.DESEMPATE_VOTACION ->
                "Vota solo entre los empatados."
            GamePhase.ALCALDE_DESEMPATE -> if (roleKey == RoleCatalog.ALCALDE) {
                "Elige quién será expulsado entre los empatados."
            } else {
                "El Alcalde debe resolver el empate."
            }
            GamePhase.AMANECER ->
                "Lee el resultado de la noche antes de debatir."
            GamePhase.RESULTADO ->
                "Revisa el resultado y continua cuando estes listo."
            GamePhase.REPARTO,
            GamePhase.RECUENTO_VOTOS -> null
        }
    }

    private fun currentNarratorMessage(
        phaseText: GameplayPhaseText = phaseText(session.phase)
    ): String {
        passiveNightMessage()?.let { return it }
        return GameplayTableUi.centralPhaseMessage(session, phaseText.subtitle)
    }

    private fun passiveNightMessage(): String? {
        if (
            session.quickTestMode ||
            isOnlineGameplay() ||
            !isNightPhase(session.phase) ||
            GameEngine.requiresHumanInput(session)
        ) {
            return null
        }
        return GameplayPhasePresentation.passiveNightMessage(
            mapKey = session.mapKey,
            round = session.round,
            phaseIndex = session.phaseIndex,
            phase = session.phase
        )
    }

    private fun mustWaitForPhaseTimer(): Boolean {
        val hasSpecialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            canHumanOracleChooseThisNight()
        return !session.quickTestMode &&
            session.winner.isBlank() &&
            session.phase != GamePhase.REPARTO &&
            !requiresHumanInput() &&
            !hasSpecialDecision &&
            activePhaseSeconds() != null
    }

    private fun canSkipRemainingNight(): Boolean {
        val currentActionSession = actionSession()
        return !isOnlineGameplay() &&
            session.winner.isBlank() &&
            isNightPhase(session.phase) &&
            !GameEngine.requiresHumanInput(currentActionSession) &&
            !countdown.isTransitionLocked(session.phaseIndex) &&
            !isBlockingGameplayUiActive()
    }

    private fun isNightSkipButtonReady(canSkipNight: Boolean = canSkipRemainingNight()): Boolean {
        if (!canSkipNight) {
            resetNightSkipArm()
            return false
        }

        if (nightSkipArmPhaseIndex != session.phaseIndex) {
            nightSkipArmPhaseIndex = session.phaseIndex
            nightSkipEnabledAtMs = SystemClock.elapsedRealtime() + NIGHT_SKIP_ARM_DELAY_MS
            nightSkipEnableScheduled = false
        }

        val remainingMs = nightSkipEnabledAtMs - SystemClock.elapsedRealtime()
        if (remainingMs <= 0L) return true

        if (!nightSkipEnableScheduled) {
            nightSkipEnableScheduled = true
            autoAdvanceHandler.postDelayed(nightSkipEnableRunnable, remainingMs)
        }
        return false
    }

    private fun resetNightSkipArm() {
        nightSkipArmPhaseIndex = -1
        nightSkipEnabledAtMs = 0L
        nightSkipEnableScheduled = false
        autoAdvanceHandler.removeCallbacks(nightSkipEnableRunnable)
    }

    private fun isBlockingGameplayUiActive(): Boolean {
        return isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
            isOracleRevealVisible ||
            isVoteResultVisible ||
            isTieVoteVisible ||
            isJesterVictoryVisible ||
            isWinnerRevealVisible ||
            isRolePreviewOpen ||
            isTraitorRevealRunning ||
            feedbackState.privateVisible ||
            feedbackState.pending?.blocksGameplay == true ||
            desertorDialogOpen
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
        val selection = when {
            selectedTarget.isBlank() -> ""
            session.phase == GamePhase.NOCHE_ORACULO ->
                " Elegiste a $selectedTarget. Tocá su carta otra vez para cancelar y guardar el poder."
            else -> " Objetivo: $selectedTarget."
        }
        return "$base$selection"
    }

    override fun renderPersonalStatus() {
        val status = GameplayTableUi.personalStatus(session)
        val eliminated = !GameEngine.humanPlayer(session).alive
        actionControls.visibility = if (eliminated) View.GONE else View.VISIBLE
        if (eliminated && ::btnRevealMayorSecondary.isInitialized) {
            btnRevealMayorSecondary.visibility = View.GONE
        }
        eliminatedStatePanel.visibility = if (eliminated) View.VISIBLE else View.GONE
        currentPlayerHint.maxLines = if (eliminated) 1 else 2
        if (eliminated) {
            currentPlayerHint.text = "Observando la partida."
        }
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

    private fun animateWindowRiseIn(
        view: View,
        fromDy: Int = 32,
        durationMs: Long = 300L,
        fade: Boolean = true
    ) {
        // Entrada "sube desde abajo + rebote": la ventana arranca corrida hacia abajo y sube
        // a su lugar con un pequeno overshoot. Reutilizado por los overlays simples.
        view.animate().cancel()
        view.translationY = dp(fromDy).toFloat()
        if (fade) view.alpha = 0f
        val anim = view.animate()
            .translationY(0f)
            .setInterpolator(OvershootInterpolator(1.5f))
            .setDuration(durationMs)
        if (fade) anim.alpha(1f)
        anim.start()
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
        animateWindowRiseIn(actionFeedbackBanner, fromDy = 26, durationMs = 260L)
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
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        MusicManager.pauseForTransition()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackDismissRunnable)
        autoAdvanceHandler.removeCallbacks(feedbackBannerDismissRunnable)
        feedbackAnimator?.cancel()

        privateFeedbackTitle.text = spec.title
        privateFeedbackMessage.text = spec.message
        btnContinuePrivateFeedback.isEnabled = true
        btnContinuePrivateFeedback.visibility = View.VISIBLE
        btnContinuePrivateFeedback.alpha = 1f
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        privateFeedbackOverlay.alpha = 0f
        privateFeedbackPanel.alpha = 0f
        privateFeedbackPanel.scaleX = 0.94f
        privateFeedbackPanel.scaleY = 0.94f
        privateFeedbackPanel.translationY = dp(34).toFloat()
        hideCentralPublicEventBanner(immediate = true)
        privateFeedbackOverlay.visibility = View.VISIBLE
        feedbackState.markPrivateVisible()

        // El fondo hace fade suave; el panel sube desde abajo con rebote (overshoot).
        val riseOvershoot = OvershootInterpolator(1.25f)
        feedbackAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(privateFeedbackOverlay, View.ALPHA, 0f, 1f).apply {
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.ALPHA, 0f, 1f).apply {
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_X, 0.94f, 1f).apply {
                    interpolator = riseOvershoot
                },
                ObjectAnimator.ofFloat(privateFeedbackPanel, View.SCALE_Y, 0.94f, 1f).apply {
                    interpolator = riseOvershoot
                },
                ObjectAnimator.ofFloat(
                    privateFeedbackPanel,
                    View.TRANSLATION_Y,
                    dp(34).toFloat(),
                    0f
                ).apply {
                    interpolator = riseOvershoot
                }
            )
            duration = 300L
            start()
        }
        autoAdvanceHandler.postDelayed(
            feedbackDismissRunnable,
            REVEAL_CONTINUE_TIMEOUT_MS
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
                    btnContinuePrivateFeedback.isEnabled = false
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
        if (::btnContinuePrivateFeedback.isInitialized) {
            btnContinuePrivateFeedback.isEnabled = false
            btnContinuePrivateFeedback.visibility = View.VISIBLE
            btnContinuePrivateFeedback.alpha = 1f
        }
        feedbackState.cancel(keepPending)
        if (!keepPending) {
            blockingFeedbackPeriod = null
        }
    }

    private fun clearSelection() {
        selectedTarget = ""
    }

    private fun targetActionMessage(): String {
        return when (actionSession().phase) {
            GamePhase.NOCHE_ASESINO -> "Elige una víctima y confirma MATAR."
            GamePhase.NOCHE_MERCENARIO -> "Elige a quién silenciar y confirma SILENCIAR."
            GamePhase.NOCHE_POLICIA -> "Elige a quién investigar y confirma INVESTIGAR."
            GamePhase.NOCHE_MEDICO -> "Elige a quién proteger y confirma PROTEGER."
            GamePhase.NOCHE_ORACULO -> if (GameEngine.oracleCandidates(actionSession()).isEmpty()) {
                "Todavía no hay muertos. Tu poder se conserva automáticamente."
            } else {
                "Elige un jugador muerto para INVOCAR o guarda el poder."
            }
            GamePhase.DIA_DEBATE -> "Puedes usar tu habilidad o seguir a la votación."
            GamePhase.CONTRAPUNTO -> "Elige un participante y confirma SEÑALAR."
            GamePhase.VOTACION -> "Elige a quién expulsar y confirma VOTAR."
            GamePhase.DESEMPATE_VOTACION -> "Elige un jugador empatado y confirma VOTAR."
            GamePhase.ALCALDE_DESEMPATE -> "Elige un jugador empatado y confirma EXPULSAR."
            else -> "Toca una carta valida."
        }
    }

    private fun resumeGameFlowAfterBlockingUi() {
        if (
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isNoDeathRevealRunning ||
            isPayadorRevealVisible ||
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
            session.winner.isBlank() &&
            session.phase == GamePhase.RESULTADO &&
            session.dayEliminationTarget.isBlank()
        ) {
            dismissSecondaryUiForPriorityWindow()
            dismissActionFeedbackBannerNow()
            hideCentralPublicEventBanner(immediate = true)
            isVoteResultVisible = true
            voteResultAnimator.show(session)
            voteResultAnimator.showNoExpulsion()
            return
        }
        if (maybeShowNextDeathReveal()) return
        if (session.winner.isNotBlank()) {
            if (maybeShowWinnerReveal()) return
        }
        if (maybeShowNoDeathReveal()) return
        if (maybeShowNextSilenceReveal()) return
        if (maybeShowPayadorReveal()) return
        if (maybeShowOracleReveal()) return
        if (maybeShowTieVote()) return
        if (maybeShowVoteResult()) return
        if (maybeShowJesterVictory()) return
        if (maybeShowWinnerReveal()) return
        if (maybeShowTraitorReveal()) return
        if (maybeOfferSpectatorChoice()) return
        maybeShowDesertorChoice()
        if (!desertorDialogOpen) {
            flushPendingOnlineReactions()
            refreshOnlinePresentationGate()
            scheduleAutoAdvanceIfNeeded()
        }
    }

    private fun flushPendingOnlineReactions() {
        if (pendingOnlineReactions.isEmpty()) return
        if (session.winner.isNotBlank() || !isPublicReactionPhase(session.phase)) {
            pendingOnlineReactions.clear()
            return
        }
        if (!gameplayResumed || reactionUiBlocked()) return
        val reactions = pendingOnlineReactions.toMap()
        pendingOnlineReactions.clear()
        reactions.forEach { (playerName, spec) ->
            val player = session.players.firstOrNull { it.name == playerName } ?: return@forEach
            if (GameEngine.isAlive(player)) {
                showReactionBubble(playerName, spec)
            }
        }
    }

    private fun maybeShowNextDeathReveal(): Boolean {
        if (isDeathRevealRunning) return true
        val player = pendingDeathReveals.pollFirst() ?: return false
        showDeathReveal(player)
        return true
    }

    private fun showDeathReveal(player: GamePlayer) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        isDeathRevealRunning = true
        GameplayAudioDirector.play(this, GameSound.ELIMINATION)
        btnContinueDeathReveal.animate().cancel()
        btnContinueDeathReveal.visibility = View.INVISIBLE
        btnContinueDeathReveal.isEnabled = false
        btnContinueDeathReveal.alpha = 0f
        hideCentralPublicEventBanner(immediate = true)
        deathRevealAnimator.start(player, session.revealRolesOnDeath)
    }

    private fun showDeathRevealContinue() {
        if (!isDeathRevealRunning) return
        if (isOnlineGameplay()) {
            btnContinueDeathReveal.visibility = View.INVISIBLE
            btnContinueDeathReveal.isEnabled = true
            btnContinueDeathReveal.alpha = 0f
            autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
            autoAdvanceHandler.postDelayed(
                deathRevealContinueTimeoutRunnable,
                ONLINE_DEATH_REVEAL_BEAT_MS
            )
            return
        }
        btnContinueDeathReveal.visibility = View.VISIBLE
        btnContinueDeathReveal.isEnabled = true
        btnContinueDeathReveal.alpha = 0f
        btnContinueDeathReveal.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        autoAdvanceHandler.postDelayed(
            deathRevealContinueTimeoutRunnable,
            REVEAL_CONTINUE_TIMEOUT_MS
        )
    }

    private fun continueDeathReveal() {
        if (!isDeathRevealRunning || !::btnContinueDeathReveal.isInitialized) return
        if (!btnContinueDeathReveal.isEnabled) return
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        btnContinueDeathReveal.isEnabled = false
        btnContinueDeathReveal.animate().cancel()
        btnContinueDeathReveal.alpha = 0f
        btnContinueDeathReveal.visibility = View.INVISIBLE
        deathRevealAnimator.continueAndFinish()
    }

    private fun finishDeathReveal() {
        if (!isDeathRevealRunning) return
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        if (::btnContinueDeathReveal.isInitialized) {
            btnContinueDeathReveal.animate().cancel()
            btnContinueDeathReveal.isEnabled = false
            btnContinueDeathReveal.visibility = View.INVISIBLE
            btnContinueDeathReveal.alpha = 0f
        }
        isDeathRevealRunning = false
        if (!hasPendingDawnRevealSequence()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelDeathReveal(resumeMusic: Boolean) {
        if (!::deathRevealOverlay.isInitialized) return
        autoAdvanceHandler.removeCallbacks(deathRevealContinueTimeoutRunnable)
        if (::btnContinueDeathReveal.isInitialized) {
            btnContinueDeathReveal.animate().cancel()
            btnContinueDeathReveal.isEnabled = false
            btnContinueDeathReveal.visibility = View.INVISIBLE
            btnContinueDeathReveal.alpha = 0f
        }
        deathRevealAnimator.cancel()
        isDeathRevealRunning = false
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun maybeShowNoDeathReveal(): Boolean {
        if (isNoDeathRevealRunning) return true
        if (!pendingNoDeathReveal) return false
        showNoDeathReveal()
        return true
    }

    private fun showNoDeathReveal() {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        pendingNoDeathReveal = false
        isNoDeathRevealRunning = true
        hideCentralPublicEventBanner(immediate = true)
        GameplayAudioDirector.play(this, GameSound.NO_DEATH)
        noDeathRevealAnimator.start()
    }

    private fun finishNoDeathReveal() {
        if (!isNoDeathRevealRunning) return
        isNoDeathRevealRunning = false
        if (!hasPendingDawnRevealSequence()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelNoDeathReveal(resumeMusic: Boolean) {
        if (!::noDeathRevealOverlay.isInitialized) return
        noDeathRevealAnimator.cancel()
        isNoDeathRevealRunning = false
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun hasPendingDawnRevealSequence(): Boolean {
        return pendingDeathReveals.isNotEmpty() ||
            pendingNoDeathReveal ||
            pendingSilenceReveals.isNotEmpty()
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
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.playGamePhase(this, session)
        voteExpulsionComplete = false
        voteExpulsionAnimationKey = ""
        voteNoExpulsionPresented = false
        isVoteResultVisible = true
        hideCentralPublicEventBanner(immediate = true)
        voteResultAnimator.show(session)
        refreshOnlinePresentationGate()
        return true
    }

    private fun maybeShowTieVote(): Boolean {
        if (isTieVoteVisible) return true
        if (
            session.phase != GamePhase.DESEMPATE_VOTACION ||
            chatController.isOpenOrRestoringTieVote()
        ) {
            return false
        }
        showTieVoteWindow()
        return true
    }

    private fun showTieVoteWindow() {
        if (session.phase != GamePhase.DESEMPATE_VOTACION) return
        dismissSecondaryUiForPriorityWindow()
        dismissActionFeedbackBannerNow()
        selectedTarget = selectedTarget.takeIf { canActOnTarget(it) }.orEmpty()
        isTieVoteVisible = true
        GameplayAudioDirector.play(this, GameSound.TIE_BREAK)
        tieVoteOverlay.visibility = View.VISIBLE
        tieVoteOverlay.alpha = 0f
        tieVoteCardsScroll.scrollTo(0, 0)
        renderTieVoteWindow()
        tieVoteOverlay.animate().alpha(1f).setDuration(180L).start()
        // El backdrop hace fade; el panel sube desde abajo con rebote.
        animateWindowRiseIn(tieVotePanel, fromDy = 40, fade = false)
        ensureCountdownForCurrentPhase()
    }

    private fun tieVotePanelAvailableWidthDp(): Int {
        val metrics = resources.displayMetrics
        val margins = (tieVotePanel.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.leftMargin + params.rightMargin
        } ?: 0
        // El overlay estaba GONE antes de abrirse y puede no estar medido todavía. En ese caso
        // reconstruimos el ancho del panel desde la pantalla, sus márgenes y el padding real que
        // agrega el marco temático. Así las cartas nunca se meten debajo de los bordes decorados.
        val panelWidthPx = tieVotePanel.width
            .takeIf { it > tieVotePanel.paddingLeft + tieVotePanel.paddingRight }
            ?: (metrics.widthPixels - margins)
        val contentWidthPx = (
            panelWidthPx - tieVotePanel.paddingLeft - tieVotePanel.paddingRight
            ).coerceAtLeast(0)
        return (contentWidthPx / metrics.density).toInt().coerceAtLeast(120)
    }

    private fun renderTieVoteWindow() {
        val candidates = session.tieVoteCandidates.mapNotNull { candidate ->
            GameEngine.playerByName(session, candidate)
        }
        val desiredNames = candidates.map { it.name }.toSet()
        tieVoteCardViews.keys.toList()
            .filterNot { it in desiredNames }
            .forEach { name ->
                tieVoteCardViews.remove(name)?.root?.let { tieVoteCards.removeView(it) }
            }
        val gridMetrics = GameplayTableUi.tieVoteGridMetrics(
            candidateCount = candidates.size,
            maxColumns = 2,
            availableWidthDp = tieVotePanelAvailableWidthDp()
        )
        tieVoteCards.columnCount = gridMetrics.columns
        tieVoteCards.rowCount = gridMetrics.rows
        tieVoteCardsScroll.layoutParams = tieVoteCardsScroll.layoutParams.apply {
            height = if (gridMetrics.scrollEnabled) {
                dp(TIE_VOTE_GRID_MAX_HEIGHT_DP)
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        tieVoteCardsScroll.isVerticalScrollBarEnabled = gridMetrics.scrollEnabled
        tieVoteCardsScroll.overScrollMode = if (gridMetrics.scrollEnabled) {
            View.OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            View.OVER_SCROLL_NEVER
        }
        candidates.forEachIndexed { index, player ->
            val holder = tieVoteCardViews.getOrPut(player.name) { createTieVoteCard(player) }
            val currentParent = holder.root.parent as? ViewGroup
            if (currentParent !== tieVoteCards) {
                currentParent?.removeView(holder.root)
                tieVoteCards.addView(holder.root, index.coerceAtMost(tieVoteCards.childCount))
            } else if (tieVoteCards.indexOfChild(holder.root) != index) {
                tieVoteCards.removeView(holder.root)
                tieVoteCards.addView(holder.root, index.coerceAtMost(tieVoteCards.childCount))
            }
            holder.root.layoutParams = GridLayout.LayoutParams().apply {
                width = dp(gridMetrics.cardWidthDp)
                height = dp(gridMetrics.cardHeightDp)
                setMargins(dp(5), dp(4), dp(5), dp(4))
            }
            bindTieVoteCard(holder, player)
        }

        val human = GameEngine.humanPlayer(session)
        val hiddenHumanMayor = isUnrevealedHumanMayor()
        btnTieRevealMayor.visibility = if (hiddenHumanMayor) View.VISIBLE else View.GONE
        btnTieVoteChat.isEnabled = GameEngine.canHumanChat(session)
        btnTieVoteChat.alpha = if (btnTieVoteChat.isEnabled) 1f else 0.45f
        tieVoteSubtitle.text = when {
            session.alcaldeRevealed && human.role?.key == "alcalde" ->
                "Tu voto vale doble. Elige entre las cartas empatadas."
            else -> "Vota nuevamente entre los jugadores empatados."
        }
        tieVoteNotice.text =
            "SI EL EMPATE SE REPITE, NADIE SERA EXPULSADO."
        renderTieVoteSelection()
    }

    private fun createTieVoteCard(player: GamePlayer): TieVoteCardHolder {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(7), dp(6), dp(5))
        }
        val card = FrameLayout(this)
        card.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.card_back_traidores)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            FrameLayout.LayoutParams(dp(52), dp(70), Gravity.CENTER)
        )
        card.addView(
            GameplayAvatarView(this).apply {
                bind(
                    session = session,
                    player = player,
                    fallbackInitial = player.initial,
                    textSizeSp = 17f
                )
            },
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(8)
            }
        )
        container.addView(card, LinearLayout.LayoutParams(dp(58), dp(74)))
        val name = TextView(this).apply {
            text = player.name
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.DEFAULT_BOLD
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            name,
            8,
            13,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        container.addView(
            name,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22))
        )
        val status = TextView(this).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            status,
            7,
            10,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        container.addView(
            status,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18))
        )
        return TieVoteCardHolder(container, name, status)
    }

    private fun bindTieVoteCard(holder: TieVoteCardHolder, player: GamePlayer) {
        val actionable = canActOnTarget(player.name)
        val selected = selectedTarget == player.name
        holder.root.alpha = if (actionable) 1f else 0.5f
        holder.root.background = tieVoteCardBackground(selected, actionable)
        holder.root.isClickable = actionable
        holder.root.isFocusable = actionable
        holder.root.contentDescription = when {
            player.isHuman -> "${player.name}, tu carta empatada"
            actionable -> "${player.name}, tocar para votar"
            else -> "${player.name}, no disponible"
        }
        holder.root.setOnClickListener {
            if (!canActOnTarget(player.name)) {
                GameplayEffects.play(this, GameplayEffect.ERROR)
                return@setOnClickListener
            }
            GameplayEffects.play(this, GameplayEffect.SELECT)
            selectedTarget = player.name
            tieVoteCardViews.forEach { (name, cardHolder) ->
                GameEngine.playerByName(session, name)?.let { bindTieVoteCard(cardHolder, it) }
            }
            renderTieVoteSelection()
        }
        holder.name.text = player.name
        holder.name.setTextColor(getColor(if (selected) R.color.accent_gold else R.color.text_primary))
        holder.status.text = when {
            player.isHuman -> "TU CARTA"
            selected -> "SELECCIONADO"
            else -> "VOTAR"
        }
        holder.status.setTextColor(getColor(if (selected) R.color.accent_gold else R.color.text_secondary))
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
            canActOnTarget(selectedTarget)
        btnConfirmTieVote.isEnabled = validSelection
        btnConfirmTieVote.alpha = if (validSelection) 1f else 0.55f
        btnConfirmTieVote.text = if (validSelection) {
            "VOTAR"
        } else {
            "ELEGIR CARTA"
        }
    }

    private fun confirmTieVoteSelection() {
        if (!canActOnTarget(selectedTarget)) {
            GameplayEffects.play(this, GameplayEffect.ERROR)
            return
        }
        hideTieVoteWindow(clearSelection = false)
        performTargetAction(selectedTarget)
    }

    private fun revealMayorFromTieVote() {
        if (isOnlineGameplay()) {
            recordOnlineMayorReveal()
            renderTieVoteWindow()
            return
        }
        val before = session
        session = GameEngine.revealAlcalde(session)
        if (before == session) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        renderTieVoteWindow()
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
        if (isOnlineGameplay()) {
            handleOnlinePresentationContinue()
            return
        }
        continueVoteResultAuthoritatively()
    }

    private fun continueVoteResultAuthoritatively() {
        if (!isVoteResultVisible) return
        if (isOnlineGameplay() && !onlineIsHost) return
        clearOnlinePresentationGate()
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        GameplayEffects.play(this, GameplayEffect.PANEL)

        if (voteNoExpulsionPresented) {
            voteNoExpulsionPresented = false
            isVoteResultVisible = false
            voteResultAnimator.hide()
            MusicManager.resumeGamePhaseAfterTransition(this, session)
            if (isOnlineGameplay() && session.phase == GamePhase.RESULTADO && session.winner.isBlank()) {
                val before = session
                session = GameEngine.resolveResult(session)
                recordOnlinePhaseAdvance(before, session)
            }
            renderGame()
            return
        }

        if (
            session.phase == GamePhase.RECUENTO_VOTOS &&
            session.tieVoteCandidates.isEmpty() &&
            session.dayEliminationTarget.isNotBlank() &&
            !voteExpulsionComplete
        ) {
            val withLastWords = GameEngine.addEliminationLastWords(session)
            if (withLastWords != session) {
                session = withLastWords
                chatController.onSessionUpdated()
                renderGame()
                return
            }
            val expulsionPresentation = listOf(
                "expulsion",
                session.round,
                session.voteRound,
                session.phaseIndex,
                session.dayEliminationTarget
            ).joinToString("|")
            onlineVotePresentation = expulsionPresentation
            lastPublishedAuthoritativeOnlineStateKey = ""
            publishAuthoritativeOnlineState()
            playVoteExpulsionOnce(expulsionPresentation)
            return
        }

        val advanced = GameEngine.continueAfterVoteRecount(session)
        if (
            advanced.phase == GamePhase.RESULTADO &&
            advanced.dayEliminationTarget.isBlank()
        ) {
            session = advanced
            dismissActionFeedbackBannerNow()
            hideCentralPublicEventBanner(immediate = true)
            voteNoExpulsionPresented = true
            onlineVotePresentation = listOf(
                "sin_expulsion",
                session.round,
                session.voteRound,
                session.phaseIndex
            ).joinToString("|")
            lastPublishedAuthoritativeOnlineStateKey = ""
            publishAuthoritativeOnlineState()
            voteResultAnimator.showNoExpulsion()
            return
        }

        session = if (
            isOnlineGameplay() &&
            advanced.phase == GamePhase.RESULTADO &&
            advanced.winner.isBlank()
        ) {
            GameEngine.resolveResult(advanced)
        } else {
            advanced
        }
        isVoteResultVisible = false
        voteExpulsionComplete = false
        voteExpulsionAnimationKey = ""
        voteResultAnimator.hide()
        clearSelection()
        MusicManager.resumeGamePhaseAfterTransition(this, session)
        renderGame()
    }

    private fun handleVoteResultAutoContinue() {
        if (!OnlinePhaseGate.canAutoContinueVoteResult(isOnlineGameplay())) return
        if (!isVoteResultVisible || !btnContinueVoteResult.isEnabled) return
        handleVoteResultContinue()
    }

    private fun scheduleVoteResultAutoContinue() {
        autoAdvanceHandler.removeCallbacks(voteResultAutoContinueRunnable)
        if (isOnlineGameplay()) {
            refreshOnlinePresentationGate()
            return
        }
        if (!OnlinePhaseGate.canAutoContinueVoteResult(isOnlineGameplay())) return
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
        voteExpulsionAnimationKey = ""
    }

    private fun showSilenceReveal(player: GamePlayer) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        isSilenceRevealRunning = true
        GameplayAudioDirector.play(this, GameSound.SILENCE)
        hideCentralPublicEventBanner(immediate = true)
        silenceRevealAnimator.start(player)
    }

    private fun finishSilenceReveal() {
        if (!isSilenceRevealRunning) return
        isSilenceRevealRunning = false
        if (!hasPendingDawnRevealSequence()) {
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

    private fun payadorRevealKey(): String? {
        if (session.phase != GamePhase.CONTRAPUNTO) return null
        val players = session.contrapuntoPlayers.take(2)
        if (!session.payadorUsed || players.size < 2) return null
        return "${session.round}:${session.phaseIndex}:${players.joinToString("|")}"
    }

    private fun maybeShowPayadorReveal(): Boolean {
        if (isPayadorRevealVisible) return true
        val revealKey = payadorRevealKey() ?: return false
        if (revealKey == lastPresentedPayadorRevealKey) return false
        showPayadorReveal(revealKey)
        return true
    }

    private fun showPayadorReveal(revealKey: String) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(payadorRevealAutoDismissRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        isPayadorRevealVisible = true
        activePayadorRevealKey = revealKey
        payadorRevealFirstPlayer.text = session.contrapuntoPlayers[0].uppercase()
        payadorRevealSecondPlayer.text = session.contrapuntoPlayers[1].uppercase()
        payadorRevealOverlay.visibility = View.VISIBLE
        payadorRevealOverlay.alpha = 0f
        payadorRevealPanel.alpha = 0f
        payadorRevealPanel.scaleX = 0.86f
        payadorRevealPanel.scaleY = 0.86f
        payadorRevealProgress.animate().cancel()
        payadorRevealProgress.scaleX = 1f
        payadorRevealOverlay.animate()
            .alpha(1f)
            .setDuration(260L)
            .start()
        payadorRevealPanel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(620L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        payadorRevealProgress.animate()
            .scaleX(0f)
            .setDuration(SPECIAL_ROLE_REVEAL_DURATION_MS)
            .setInterpolator(LinearInterpolator())
            .start()
        GameplayAudioDirector.play(this, GameSound.PAYADOR)
        autoAdvanceHandler.postDelayed(
            payadorRevealAutoDismissRunnable,
            SPECIAL_ROLE_REVEAL_DURATION_MS
        )
    }

    private fun dismissPayadorReveal() {
        if (!isPayadorRevealVisible) return
        autoAdvanceHandler.removeCallbacks(payadorRevealAutoDismissRunnable)
        payadorRevealProgress.animate().cancel()
        lastPresentedPayadorRevealKey = activePayadorRevealKey
        activePayadorRevealKey = null
        isPayadorRevealVisible = false
        payadorRevealOverlay.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                payadorRevealOverlay.visibility = View.GONE
                MusicManager.resumeGamePhaseAfterTransition(this, session)
                renderGame()
            }
            .start()
    }

    private fun hidePayadorReveal() {
        if (!::payadorRevealOverlay.isInitialized) return
        autoAdvanceHandler.removeCallbacks(payadorRevealAutoDismissRunnable)
        payadorRevealOverlay.animate().cancel()
        payadorRevealPanel.animate().cancel()
        payadorRevealProgress.animate().cancel()
        payadorRevealOverlay.visibility = View.GONE
        payadorRevealOverlay.alpha = 1f
        payadorRevealPanel.alpha = 1f
        payadorRevealPanel.scaleX = 1f
        payadorRevealPanel.scaleY = 1f
        payadorRevealProgress.scaleX = 1f
        activePayadorRevealKey = null
        isPayadorRevealVisible = false
    }

    private fun maybeShowOracleReveal(): Boolean {
        if (isOracleRevealVisible) return true
        val revealKey = oracleRevealKey() ?: return false
        if (revealKey == lastPresentedOracleRevealKey) return false
        showOracleReveal(revealKey)
        return true
    }

    private fun oracleRevealKey(): String? {
        if (session.phase != GamePhase.DIA_DEBATE || session.oracleInvitedPlayer.isBlank()) return null
        return "${session.round}:${session.phaseIndex}:${session.oracleInvitedPlayer}"
    }

    private fun showOracleReveal(revealKey: String) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(oracleRevealAutoDismissRunnable)
        dismissActionFeedbackBannerNow()
        MusicManager.pauseForTransition()
        GameplayAudioDirector.play(this, GameSound.ORACLE)
        isOracleRevealVisible = true
        activeOracleRevealKey = revealKey
        oracleRevealPlayer.text = "${session.oracleInvitedPlayer.uppercase()}\nVOZ RECUPERADA"
        oracleRevealOverlay.visibility = View.VISIBLE
        oracleRevealOverlay.alpha = 0f
        oracleRevealPanel.alpha = 0f
        oracleRevealPanel.scaleX = 0.86f
        oracleRevealPanel.scaleY = 0.86f
        oracleRevealProgress.animate().cancel()
        oracleRevealProgress.scaleX = 1f
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
        oracleRevealProgress.animate()
            .scaleX(0f)
            .setDuration(SPECIAL_ROLE_REVEAL_DURATION_MS)
            .setInterpolator(LinearInterpolator())
            .start()
        autoAdvanceHandler.postDelayed(
            oracleRevealAutoDismissRunnable,
            SPECIAL_ROLE_REVEAL_DURATION_MS
        )
    }

    private fun dismissOracleReveal() {
        if (!isOracleRevealVisible) return
        autoAdvanceHandler.removeCallbacks(oracleRevealAutoDismissRunnable)
        oracleRevealProgress.animate().cancel()
        lastPresentedOracleRevealKey = activeOracleRevealKey
        activeOracleRevealKey = null
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
        autoAdvanceHandler.removeCallbacks(oracleRevealAutoDismissRunnable)
        oracleRevealOverlay.animate().cancel()
        oracleRevealPanel.animate().cancel()
        oracleRevealProgress.animate().cancel()
        oracleRevealOverlay.visibility = View.GONE
        oracleRevealOverlay.alpha = 1f
        oracleRevealPanel.alpha = 1f
        oracleRevealPanel.scaleX = 1f
        oracleRevealPanel.scaleY = 1f
        oracleRevealProgress.scaleX = 1f
        activeOracleRevealKey = null
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
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
        eventLogHeightAnimator?.cancel()
        isJesterVictoryVisible = true
        presentedSpecialVictoryCount += 1
        jesterVictoryPlayer.text = "${victory.playerName.uppercase()} ERA EL BUFÓN"
        jesterVictoryMessage.text =
            "Consiguió que el pueblo lo expulsara durante la votación."
        val human = GameEngine.humanPlayer(session)
        jesterVictoryOffersLocalSpectatorActions =
            !isOnlineGameplay() &&
                victory.playerName == human.name &&
                !human.alive
        btnContinueJesterVictory.text = if (jesterVictoryOffersLocalSpectatorActions) {
            "SEGUIR MIRANDO"
        } else {
            "CONTINUAR PARTIDA"
        }
        btnReturnJesterVictory.visibility = if (jesterVictoryOffersLocalSpectatorActions) {
            View.VISIBLE
        } else {
            View.GONE
        }
        GameplayAudioDirector.play(this, GameSound.JESTER)
        jesterVictoryAnimator.show(JESTER_VICTORY_DURATION_MS)
    }

    private fun playResolvedActionSound(before: GameSession, after: GameSession) {
        when {
            before.phase == GamePhase.VOTACION ||
                before.phase == GamePhase.DESEMPATE_VOTACION ||
                before.phase == GamePhase.ALCALDE_DESEMPATE -> {
                GameplayAudioDirector.play(this, GameSound.VOTE_CAST)
            }
        }
    }

    private fun dismissJesterVictory() {
        if (!isJesterVictoryVisible || !jesterVictoryActions.isEnabled) return
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        jesterVictoryAnimator.hide()
        isJesterVictoryVisible = false
        if (jesterVictoryOffersLocalSpectatorActions) {
            spectatorChoiceOffered = true
            jesterVictoryOffersLocalSpectatorActions = false
            enterSpectatorFastForward()
            return
        }
        jesterVictoryOffersLocalSpectatorActions = false
        renderGame()
    }

    private fun returnToLobbyFromJesterVictory() {
        if (
            !isJesterVictoryVisible ||
            !jesterVictoryActions.isEnabled ||
            !jesterVictoryOffersLocalSpectatorActions
        ) {
            return
        }
        GameplayEffects.play(this, GameplayEffect.CONFIRM)
        jesterVictoryAnimator.hide()
        isJesterVictoryVisible = false
        jesterVictoryOffersLocalSpectatorActions = false
        spectatorChoiceOffered = true
        returnToLobby()
    }

    private fun cancelJesterVictory(requeue: Boolean) {
        if (!::jesterVictoryAnimator.isInitialized || !isJesterVictoryVisible) return
        jesterVictoryAnimator.hide()
        isJesterVictoryVisible = false
        jesterVictoryOffersLocalSpectatorActions = false
        if (requeue) {
            presentedSpecialVictoryCount = (presentedSpecialVictoryCount - 1).coerceAtLeast(0)
        }
    }

    private fun showWinnerReveal(animate: Boolean) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
        chatController.onBackPressed()
        hideTieVoteWindow(clearSelection = false)
        hideCentralPublicEventBanner(immediate = true)
        eventLogHeightAnimator?.cancel()

        val presentation = GameplayTableUi.winnerPresentation(session)
        val winnerTitle = when (session.winner) {
            GameRules.CANCELLED_WINNER -> "SIN GANADOR"
            GameRules.TOWN_WINNER -> "EL PUEBLO HA GANADO"
            GameRules.TRAITOR_WINNER -> "LOS TRAIDORES HAN GANADO"
            else -> "${session.winner.uppercase()} HA GANADO"
        }
        val personalResult = when {
            session.winner == GameRules.CANCELLED_WINNER -> "PARTIDA CANCELADA"
            presentation.humanWon -> "VICTORIA"
            else -> "DERROTA"
        }
        winnerRevealTitle.text = personalResult
        winnerRevealPersonalResult.text = winnerTitle
        applyWinnerRevealLayout(session.winner)
        configureWinnerReturnButton()
        winnerRevealBackground.setImageDrawable(null)
        val cardViews = winnerResultsRenderer.render(
            players = presentation.winningPlayers,
            summary = presentation.summary,
            specialVictories = presentation.specialVictories,
            specialWinners = presentation.specialWinningPlayers,
            winnerKey = session.winner
        )
        if (session.winner == GameRules.CANCELLED_WINNER) {
            winnerSummaryHighlight.text =
                "Todos quedaron inactivos. La partida terminó sin ganador y no cuenta para estadísticas."
        }
        winnerRevealScroll.scrollTo(0, 0)

        isWinnerRevealVisible = true
        winnerRevealPresented = true
        if (!animate) {
            winnerRevealAnimator.show(cardViews, animate = false) {}
            playVictoryMusicWithAutoReturn()
            scheduleWinnerAutoReturn()
            return
        }

        playVictoryMusicWithAutoReturn()
        scheduleWinnerAutoReturn()
        winnerRevealAnimator.show(cardViews, animate = true) {}
    }

    private fun applyWinnerRevealLayout(winnerKey: String) {
        winnerRevealPanel.layoutParams = (winnerRevealPanel.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.MATCH_PARENT
            setMargins(dp(14), dp(16), dp(14), dp(16))
            gravity = Gravity.CENTER
        }
        val factionColor = winnerAccentColor(winnerKey)
        winnerRevealPanel.setBackgroundResource(R.drawable.bg_winner_premium_panel)
        winnerRevealBackground.alpha = 0f
        winnerRevealTitle.setBackgroundResource(R.drawable.bg_winner_premium_header)
        winnerRevealTitle.setTextColor(Color.parseColor("#F3D488"))
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            winnerRevealTitle,
            26,
            34,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        winnerRevealTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        winnerRevealPersonalResult.setBackgroundResource(android.R.color.transparent)
        winnerRevealPersonalResult.setTextColor(factionColor)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            winnerRevealPersonalResult,
            12,
            17,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        winnerRevealPersonalResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        winnerSummaryPanel.setBackgroundResource(R.drawable.bg_winner_premium_summary)
        winnerSummaryStatsRow.layoutParams = winnerSummaryStatsRow.layoutParams.apply {
            height = dp(52)
        }
        listOf(winnerSummaryRounds, winnerSummaryDuration, winnerSummaryPlayers).forEach { stat ->
            stat.setBackgroundResource(R.drawable.bg_winner_stat_chip)
            stat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            stat.maxLines = 2
            stat.setSingleLine(false)
        }
        listOf(winnerSummaryHighlight, winnerSummaryTimeline).forEach { summaryText ->
            summaryText.setBackgroundResource(R.drawable.bg_winner_summary_text)
            summaryText.setPadding(dp(12), dp(7), dp(9), dp(7))
        }
        winnerSummaryHighlight.setTextColor(Color.parseColor("#B9AD92"))
        winnerSummaryHighlight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        winnerSummaryTimeline.setTextColor(Color.parseColor("#B9AD92"))
        winnerSummaryTimeline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        btnWinnerReturnLobby.setBackgroundResource(R.drawable.bg_winner_premium_button)
        btnWinnerReturnLobby.setTextColor(Color.parseColor("#211407"))
        btnWinnerReturnLobby.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        btnWinnerReturnLobby.layoutParams = (btnWinnerReturnLobby.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(208)
            height = dp(46)
            topMargin = dp(12)
            bottomMargin = dp(8)
        }
    }

    private fun winnerAccentColor(winnerKey: String): Int {
        return when (winnerKey) {
            GameRules.TOWN_WINNER -> getColor(R.color.winner_town_accent)
            GameRules.TRAITOR_WINNER -> getColor(R.color.accent_red)
            else -> getColor(R.color.accent_gold)
        }
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

    private fun roleReadingRemainingMs(): Long {
        return (roleReadingReadyAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
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

    private fun scheduleWinnerAutoReturn() {
        autoAdvanceHandler.removeCallbacks(winnerAutoReturnRunnable)
        val delayMs = if (isOnlineGameplay()) {
            onlineLobbyReturnEpochMs = OnlineMatchReturnGate.initialDeadline(
                onlineLobbyReturnEpochMs,
                System.currentTimeMillis()
            )
            OnlineMatchReturnGate.remainingMillis(
                onlineLobbyReturnEpochMs,
                System.currentTimeMillis()
            )
        } else {
            WINNER_AUTO_RETURN_MS
        }
        autoAdvanceHandler.postDelayed(winnerAutoReturnRunnable, delayMs)
    }

    private fun playVictoryMusicWithAutoReturn() {
        if (session.winner == GameRules.CANCELLED_WINNER) {
            MusicManager.stopVictoryMusic()
            return
        }
        MusicManager.playVictoryMusic(this, session.winner) {
            if (!isOnlineGameplay() && isWinnerRevealVisible && !isFinishing) {
                returnToLobbyNow()
            }
        }
    }

    private fun bindHumanActionMark(human: GamePlayer) {
        val marks = visibleCardActionMarks()
            .filter { it.targetName == human.name }
            .take(MAX_CARD_ACTION_MARKS)
        val views = listOf(
            humanActionMarkOverlay,
            humanActionMarkSecondaryOverlay,
            humanActionMarkTertiaryOverlay
        )
        val labels = listOf(
            humanActionMarkPrimaryLabel,
            humanActionMarkSecondaryLabel,
            humanActionMarkTertiaryLabel
        )
        if (marks.isEmpty()) {
            humanActionMarkAnimator?.cancel()
            views.forEach { view ->
                view.animate().cancel()
                view.visibility = View.GONE
                view.alpha = 0f
            }
            labels.forEach { label ->
                label.visibility = View.GONE
                label.alpha = 0f
            }
            humanActionMarkKey = null
            return
        }
        val key = marks.joinToString(";") { "${it.id}:${it.roleKey}" }
        val hasMercenary = marks.any { it.roleKey == RoleCatalog.MERCENARIO }
        views.forEachIndexed { index, view ->
            val mark = marks.getOrNull(index)
            val label = labels[index]
            if (mark == null) {
                view.visibility = View.GONE
                view.alpha = 0f
                label.visibility = View.GONE
                label.alpha = 0f
                return@forEachIndexed
            }
            view.setImageResource(actionMarkImageFor(mark.roleKey))
            view.contentDescription = actionMarkDescription(mark)
            view.layoutParams = humanActionMarkLayoutParams(
                mark.roleKey,
                index,
                marks.size,
                hasMercenary
            )
            view.visibility = View.VISIBLE
            val showActor = mark.roleKey in GameRules.traitorRoleKeys && mark.actorName.isNotBlank()
            label.visibility = if (showActor) View.VISIBLE else View.GONE
            if (showActor) {
                label.text = mark.actorName
                label.layoutParams = humanActionMarkLabelLayoutParams(
                    mark.roleKey,
                    index,
                    marks.size,
                    hasMercenary
                )
                label.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor(actionMarkLabelColor(mark.roleKey)))
                    setStroke(dp(1), Color.parseColor("#F4D79B"))
                }
            }
        }
        if (humanActionMarkKey == key) {
            marks.forEachIndexed { index, mark ->
                val settled = CardActionAnimations.forRole(mark.roleKey, index, marks.size)
                views[index].alpha = 1f
                views[index].scaleX = 1f
                views[index].scaleY = 1f
                views[index].rotation = settled.endRotation
                views[index].translationX = 0f
                views[index].translationY = 0f
                labels[index].alpha = 1f
            }
            return
        }
        humanActionMarkAnimator?.cancel()
        marks.forEachIndexed { index, mark ->
            val spec = CardActionAnimations.forRole(mark.roleKey, index, marks.size)
            views[index].alpha = 0f
            views[index].scaleX = spec.startScaleX
            views[index].scaleY = spec.startScaleY
            views[index].rotation = spec.startRotation
            views[index].translationX = dp(48).toFloat() * spec.startTranslationXFraction
            views[index].translationY = dp(76).toFloat() * spec.startTranslationYFraction
            labels[index].alpha = 0f
        }
        val animators = marks.flatMapIndexed { index, mark ->
            val view = views[index]
            val label = labels[index]
            val spec = CardActionAnimations.forRole(mark.roleKey, index, marks.size)
            listOf(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, spec.startScaleX, spec.overshootScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, spec.startScaleY, spec.overshootScale, 1f),
                ObjectAnimator.ofFloat(view, View.ROTATION, *spec.rotationKeyframes.toFloatArray()),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_X, view.translationX, 0f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, 0f),
                ObjectAnimator.ofFloat(label, View.ALPHA, 0f, 1f)
            ).onEach { animator ->
                animator.startDelay = index * 90L
                animator.duration = spec.durationMs
            }
        }
        humanActionMarkAnimator = AnimatorSet().apply {
            playTogether(animators)
            interpolator = DecelerateInterpolator(1.5f)
            start()
        }
        humanActionMarkKey = key
    }

    private fun humanActionMarkLayoutParams(
        roleKey: String,
        index: Int,
        count: Int,
        hasMercenary: Boolean
    ): FrameLayout.LayoutParams {
        val isRope = roleKey == RoleCatalog.MERCENARIO
        if (count == 3) {
            return FrameLayout.LayoutParams(
                dp(if (isRope) 29 else 31),
                dp(if (isRope) 92 else 52),
                if (isRope) Gravity.CENTER_VERTICAL or Gravity.END
                else (if (index == 0) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
            )
        }
        if (count == 2 && hasMercenary) {
            return FrameLayout.LayoutParams(
                dp(if (isRope) 29 else 34),
                dp(if (isRope) 92 else 64),
                Gravity.CENTER_VERTICAL or if (isRope) Gravity.END else Gravity.START
            )
        }
        return FrameLayout.LayoutParams(
            dp(if (isRope) 46 else 42),
            dp(if (isRope) 76 else 66),
            Gravity.CENTER
        ).apply {
            if (count > 1 && !isRope) {
                leftMargin = dp(if (index == 0) -5 else 5)
                topMargin = dp(if (index == 0) -6 else 6)
            }
        }
    }

    private fun humanActionMarkLabelLayoutParams(
        roleKey: String,
        index: Int,
        count: Int,
        hasMercenary: Boolean
    ): FrameLayout.LayoutParams {
        val isRope = roleKey == RoleCatalog.MERCENARIO
        val gravity = when {
            count >= 2 && hasMercenary && isRope -> Gravity.BOTTOM or Gravity.END
            count >= 2 && hasMercenary -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        return FrameLayout.LayoutParams(dp(if (isRope) 28 else 30), dp(11), gravity).apply {
            bottomMargin = dp(if (count == 3 && !isRope && index == 0) 13 else 1)
        }
    }

    private fun configureWinnerReturnButton() {
        if (!isOnlineGameplay()) {
            btnWinnerReturnLobby.text = "VOLVER AL LOBBY"
            btnWinnerReturnLobby.isEnabled = true
            btnWinnerReturnLobby.alpha = 1f
            return
        }

        syncOwnWinnerReturnAckFromSnapshot()
        val progress = onlineWinnerReturnProgress()
        val ownReady = onlineWinnerReturnAckKey == winnerReturnKey()
        btnWinnerReturnLobby.text = if (ownReady) {
            "VOLVIENDO AL LOBBY · ${progress.readyCount}/${progress.totalCount}"
        } else {
            "LISTO PARA VOLVER · ${progress.readyCount}/${progress.totalCount}"
        }
        btnWinnerReturnLobby.isEnabled = !ownReady && !returningToOnlineLobby
        btnWinnerReturnLobby.alpha = if (btnWinnerReturnLobby.isEnabled) 1f else 0.72f
    }

    private fun handleWinnerReturnButton() {
        if (!isOnlineGameplay() || session.winner.isBlank()) {
            returnToLobbyNow()
            return
        }
        val key = winnerReturnKey()
        if (key.isBlank() || onlineWinnerReturnAckKey == key) return

        onlineWinnerReturnAckKey = key
        onlineWinnerReturnClientAcks = onlineWinnerReturnClientAcks + (onlinePlayerId to key)
        lastPublishedOnlineStateKey = ""
        configureWinnerReturnButton()
        publishOnlineClientState()
        maybeCoordinateWinnerReturn()
    }

    private fun winnerReturnKey(): String {
        if (!::session.isInitialized || session.winner.isBlank()) return ""
        val matchKey = session.onlineMatchId.ifBlank { onlinePartidaId }
        return "$matchKey|${session.winner}|${session.phaseIndex}"
    }

    private fun syncOwnWinnerReturnAckFromSnapshot() {
        val key = winnerReturnKey()
        if (key.isNotBlank() && onlineWinnerReturnClientAcks[onlinePlayerId] == key) {
            onlineWinnerReturnAckKey = key
        }
    }

    private fun onlineWinnerReturnProgress(): OnlineMatchReturnGate.Progress {
        val expectedIds = session.onlinePlayerUids
            .filter { it.isNotBlank() }
            .ifEmpty { onlinePresencePlayers.map { it.id } }
        val connectedIds = onlinePresencePlayers
            .filter {
                it.activeInMatch &&
                    isOnlineUidConnected(it.id, it.state == PLAYER_STATE_CONNECTED)
            }
            .map { it.id }
        val key = winnerReturnKey()
        val acknowledgedIds = onlineWinnerReturnClientAcks
            .filterValues { it == key }
            .keys
            .toMutableSet()
            .apply {
                if (onlineWinnerReturnAckKey == key) add(onlinePlayerId)
            }
        return OnlineMatchReturnGate.progress(
            expectedPlayerIds = expectedIds,
            connectedPlayerIds = connectedIds,
            acknowledgedPlayerIds = acknowledgedIds,
            presenceKnown = onlinePresencePlayers.isNotEmpty()
        )
    }

    private fun maybeCoordinateWinnerReturn() {
        if (
            !isOnlineGameplay() ||
            !onlineIsHost ||
            session.winner.isBlank() ||
            onlineWinnerReturnAdvanceInProgress ||
            !onlineWinnerReturnProgress().allRequiredReady
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val requestedDeadline = now + OnlineMatchReturnGate.HOST_REQUEST_GRACE_MS
        if (onlineLobbyReturnEpochMs in (now + 1)..requestedDeadline) return

        onlineWinnerReturnAdvanceInProgress = true
        FirebaseFirestore.getInstance()
            .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .update(
                mapOf(
                    OnlineRoomFirestore.FIELD_STATE to OnlineRoomFirestore.STATE_FINISHED,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    "estadoPartida.volverLobbyEpochMs" to requestedDeadline,
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineWinnerReturnAdvanceInProgress = false
                onlineLobbyReturnEpochMs = requestedDeadline
                lastPublishedAuthoritativeOnlineStateKey = ""
                scheduleWinnerAutoReturn()
                OnlineDebugLog.i(
                    "winner_return_ready roomId=$onlinePartidaId uid=$onlinePlayerId ready=${onlineWinnerReturnProgress().readyCount}"
                )
            }
            .addOnFailureListener { error ->
                onlineWinnerReturnAdvanceInProgress = false
                OnlineDebugLog.e(
                    "winner_return_ready_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                    error
                )
                autoAdvanceHandler.postDelayed(
                    { maybeCoordinateWinnerReturn() },
                    WINNER_RETURN_RETRY_MS
                )
                GameNotice.show(
                    activity = this,
                    message = "La vuelta conjunta se demoró. La reintentamos automáticamente.",
                    duration = GameNotice.Duration.LONG
                )
            }
    }

    private fun refreshPlayerTargetSelection(previousTarget: String, currentTarget: String) {
        val metrics = lastCompanionCardMetrics ?: return
        setOf(previousTarget, currentTarget)
            .filter { it.isNotBlank() }
            .forEach { playerName ->
                val player = session.players.firstOrNull { it.name == playerName } ?: return@forEach
                val holder = playerCardViews[playerName] ?: return@forEach
                bindSidePlayerCard(holder, player, metrics)
            }
    }

    private fun returnToLobby() {
        if (isOnlineGameplay() && session.winner.isNotBlank()) {
            handleWinnerReturnButton()
            return
        }
        returnToLobbyNow()
    }

    private fun returnToLobbyNow() {
        returningToOnlineLobby = true
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(winnerAutoReturnRunnable)
        MusicManager.stopVictoryMusic()
        if (isOnlineGameplay() && onlineIsHost && session.winner.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection(OnlineRoomFirestore.ROOMS_COLLECTION)
                .document(onlinePartidaId)
                .update(
                    mapOf(
                        OnlineRoomFirestore.FIELD_STATE to OnlineRoomFirestore.STATE_FINISHED,
                        OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                        "ultimaActividadOnline" to FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener { error ->
                    OnlineDebugLog.e(
                        "winner_finish_room_failure roomId=$onlinePartidaId uid=$onlinePlayerId",
                        error
                    )
                }
        }
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
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        dismissActionFeedbackBannerNow()
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = true
        traitorRevealCards.removeAllViews()
        val cardsViewportWidthDp = (
            resources.configuration.screenWidthDp -
                32 -
                pxToDp(traitorRevealContent.paddingLeft + traitorRevealContent.paddingRight)
        ).coerceAtLeast(220)
        val cardMetrics = traitorRevealCardMetrics(
            teammateCount = teammates.size,
            viewportWidthDp = cardsViewportWidthDp
        )
        traitorRevealCardsScroll.layoutParams =
            (traitorRevealCardsScroll.layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(cardsViewportWidthDp)
            }
        traitorRevealCards.minimumWidth = dp(cardsViewportWidthDp)
        traitorRevealCards.columnCount = cardMetrics.columns
        traitorRevealCards.rowCount =
            ceil(teammates.size.toDouble() / cardMetrics.columns).toInt().coerceAtLeast(1)
        traitorRevealCardsScroll.scrollTo(0, 0)

        val cardViews = teammates.map { teammate ->
            createTraitorRevealCard(teammate, cardMetrics)
        }
        traitorRevealAnimator.show(
            cardViews = cardViews,
            durationMs = TRAITOR_REVEAL_DURATION_MS,
            onDismissRequested = ::dismissTraitorReveal
        )
    }

    private fun traitorRevealCardMetrics(
        teammateCount: Int,
        viewportWidthDp: Int
    ): TraitorRevealCardMetrics {
        val columns = when {
            teammateCount <= 1 -> 1
            teammateCount <= 3 -> teammateCount
            else -> 2
        }
        val slotWidthDp = (viewportWidthDp / columns).coerceAtLeast(68)
        val cardWidthDp = when (teammateCount) {
            0, 1 -> minOf(104, slotWidthDp - 16)
            2 -> minOf(92, slotWidthDp - 12)
            3 -> minOf(76, slotWidthDp - 8)
            else -> minOf(86, slotWidthDp - 14)
        }.coerceAtLeast(58)
        return TraitorRevealCardMetrics(
            columns = columns,
            slotWidthDp = slotWidthDp,
            cardWidthDp = cardWidthDp,
            cardHeightDp = cardWidthDp * 4 / 3,
            nameTextSp = if (teammateCount >= 3) 12f else 14f,
            roleTextSp = if (teammateCount >= 3) 10f else 11.5f
        )
    }

    private fun createTraitorRevealCard(
        player: GamePlayer,
        metrics: TraitorRevealCardMetrics
    ): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.setPadding(dp(3), 0, dp(3), dp(8))
        container.layoutParams = GridLayout.LayoutParams().apply {
            width = dp(metrics.slotWidthDp)
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setGravity(Gravity.CENTER)
        }

        val cardFrame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_role_card)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        cardFrame.addView(ImageView(this).apply {
            setImageResource(roleImageFor(player.role))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Rol de ${player.name}"
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        container.addView(
            cardFrame,
            LinearLayout.LayoutParams(dp(metrics.cardWidthDp), dp(metrics.cardHeightDp))
        )

        val playerName = TextView(this)
        playerName.text = player.name
        playerName.gravity = Gravity.CENTER
        playerName.includeFontPadding = false
        playerName.maxLines = 1
        playerName.ellipsize = TextUtils.TruncateAt.END
        playerName.setTextColor(getColor(R.color.accent_gold))
        playerName.textSize = metrics.nameTextSp
        playerName.setTypeface(null, Typeface.BOLD)
        val nameParams = LinearLayout.LayoutParams(
            dp(metrics.slotWidthDp - 6),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        nameParams.topMargin = dp(5)
        container.addView(playerName, nameParams)

        val roleLabel = TextView(this)
        roleLabel.text = player.role?.name?.uppercase() ?: ""
        roleLabel.gravity = Gravity.CENTER
        roleLabel.includeFontPadding = false
        roleLabel.maxLines = 1
        roleLabel.setTextColor(getColor(R.color.text_secondary))
        roleLabel.textSize = metrics.roleTextSp
        container.addView(
            roleLabel,
            LinearLayout.LayoutParams(
                dp(metrics.slotWidthDp - 6),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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

    private fun logSlowGameplayRender(startedAtMs: Long) {
        val durationMs = SystemClock.elapsedRealtime() - startedAtMs
        if (durationMs < 48L) return
        OnlineDebugLog.w(
            "gameplay_slow_render phase=${session.phase.name} phaseIndex=${session.phaseIndex} players=${session.players.size} durationMs=$durationMs"
        )
    }

    private fun cancelTraitorReveal() {
        if (!::traitorRevealOverlay.isInitialized) return
        traitorRevealAnimator.cancelAndHide()
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = false
    }

    private fun startDayNightTransition(spec: GameplayTransitionSpec) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        val fromPeriod = presentedPeriod ?: spec.period
        transitionSoundForCurrentPhase()?.let { GameplayAudioDirector.play(this, it) }
        dayNightTransitionAnimator.start(
            spec,
            fromPeriod,
            session.timingConfig.normalized().transitionSeconds * 1000L
        )
    }

    private fun transitionSoundForCurrentPhase(): GameSound? {
        return when {
            GameplayTableUi.isNightPhase(session.phase) -> GameSound.NIGHT_FALL
            session.phase == GamePhase.AMANECER && !session.nightHadNoVictim -> GameSound.DAWN
            else -> null
        }
    }

    private fun revealDayNightBackground(spec: GameplayTransitionSpec) {
        // Llamado cuando el overlay empieza a desvanecerse: cambiamos el mapa de fondo real
        // al nuevo periodo mientras el overlay todavia lo tapa, para que el fade no muestre
        // por un frame el mapa de la fase anterior.
        if (!isDayNightTransitionRunning) return
        presentedPeriod = spec.period
        renderThemedBackground(spec.period)
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
        if (role == null) return R.drawable.placeholder_local
        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        return if (resId != 0) resId else R.drawable.placeholder_local
    }

    private fun renderThemedBackground(period: GameplayPeriod) {
        val mapRes = backgroundDrawableFor(themeKey, period == GameplayPeriod.NIGHT)
        if (lastMapBackgroundRes != mapRes) {
            mapBackground.setImageResource(mapRes)
            lastMapBackgroundRes = mapRes
        }
        val logRes = logDrawableFor(themeKey)
        if (lastEventLogBackgroundRes != logRes) {
            eventLogBackground.setImageResource(logRes)
            lastEventLogBackgroundRes = logRes
        }
    }

    private fun themeFromIntentOrSession(): String {
        val requestedTheme = intent.getStringExtra(EXTRA_TEMA)
        return when (requestedTheme) {
            "gaucho", "medieval", "griego" -> requestedTheme
            else -> GameplayTableUi.themeForMapKey(session.mapKey)
        }
    }

    private fun backgroundDrawableFor(theme: String, isNight: Boolean): Int {
        if (usesVerticalGameplayBackgrounds()) {
            return when (theme) {
                "medieval" -> if (isNight) {
                    R.drawable.mapa_medieval_vertical_noche
                } else {
                    R.drawable.mapa_medieval_vertical_dia
                }
                "griego" -> if (isNight) {
                    R.drawable.mapa_grecia_vertical_noche
                } else {
                    R.drawable.mapa_grecia_vertical_dia
                }
                else -> if (isNight) {
                    R.drawable.mapa_pampa_vertical_noche
                } else {
                    R.drawable.mapa_pampa_vertical_dia
                }
            }
        }
        return when (theme) {
            "medieval" -> if (isNight) R.drawable.fondo_medieval_noche else R.drawable.fondo_medieval_dia
            "griego" -> if (isNight) R.drawable.fondo_griego_noche else R.drawable.fondo_griego_dia
            else -> if (isNight) R.drawable.fondo_gaucho_noche else R.drawable.fondo_gaucho_dia
        }
    }

    private fun usesVerticalGameplayBackgrounds(): Boolean {
        // El juego es vertical: se elimino el modo horizontal legacy y su preferencia.
        return true
    }

    private fun logDrawableFor(theme: String): Int {
        return when (theme) {
            "medieval" -> R.drawable.log_medieval
            "griego" -> R.drawable.log_griego
            else -> R.drawable.log_gaucho
        }
    }

    override fun chatLogDrawableRes(): Int {
        return logDrawableFor(themeKey)
    }

    override fun dp(value: Int): Int {
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
        // Online el bando no cambia en este celular hasta que el anfitrion lo publica; sin
        // este candado el dialogo volveria a abrirse en cada render despues de elegir.
        if (isOnlineGameplay() && onlineDesertorChoiceSent) return
        // Durante el arranque online el dialogo taparia el boton EMPEZAR, y el anfitrion no
        // larga la primera noche hasta que todos lo tocaron: colgaria la sala entera.
        if (isOnlineStartupPhase() || (isOnlineGameplay() && !onlineInitialRoleRead)) return
        if (GameEngine.needsInitialDesertorChoice(session) || GameEngine.canDesertorReconsider(session)) {
            showDesertorTeamDialog()
        }
    }

    /**
     * Online el desertor no cambia el estado por su cuenta: manda el bando como accion y el
     * anfitrion activo lo aplica y lo publica. La eleccion inicial y la reconsideracion son
     * dos acciones distintas a proposito: si fueran la misma, al abrirse la ventana de
     * reconsideracion el anfitrion volveria a leer la eleccion inicial y le quemaria el
     * cambio al jugador sin que lo pidiera.
     */
    private fun recordOnlineDesertorChoice(team: String, isInitial: Boolean) {
        if (team != GameRules.TOWN_WINNER && team != GameRules.TRAITOR_WINNER) return
        val human = GameEngine.humanPlayer(session)
        if (human.role?.key != RoleCatalog.DESERTOR || !human.alive) return
        onlineDesertorChoiceSent = true
        session = session.copy(privateHint = "Desertor - Neutral. Tu bando actual es $team.")
        recordOnlineAction(
            type = "accion_jugador",
            targetName = team,
            details = mapOf(
                "accion" to if (isInitial) {
                    ONLINE_ACTION_DESERTOR_TEAM
                } else {
                    ONLINE_ACTION_DESERTOR_RETHINK
                },
                "bando" to team
            ),
            onFailure = { onlineDesertorChoiceSent = false }
        )
    }

    /**
     * Solo el anfitrion activo. Aplica el bando pedido por el desertor y lo publica; el
     * resto de la mesa lo recibe por `desertorBando`.
     */
    private fun maybeApplyOnlineDesertorChoice() {
        if (!isOnlineGameplay() || !onlineIsHost || !::session.isInitialized) return
        if (session.winner.isNotBlank()) return
        val desertor = GameEngine.alivePlayers(session)
            .firstOrNull { it.role?.key == RoleCatalog.DESERTOR }
            ?: return
        val expectedAction = if (session.desertorTeam.isBlank()) {
            ONLINE_ACTION_DESERTOR_TEAM
        } else {
            ONLINE_ACTION_DESERTOR_RETHINK
        }
        val team = onlineNightActionRecords
            .filter {
                it.matchId == session.onlineMatchId &&
                    it.action == expectedAction &&
                    it.actorName == desertor.name
            }
            .maxByOrNull { it.createdAtLocal }
            ?.targetName
            ?: return
        if (team != GameRules.TOWN_WINNER && team != GameRules.TRAITOR_WINNER) return
        val before = session
        val hostIsDesertor = before.players.firstOrNull { it.isHuman }?.name == desertor.name
        // `chooseDesertorTeam` opera sobre el jugador humano; en el celular del anfitrion el
        // desertor puede ser un invitado, asi que se marca temporalmente y se restaura.
        val asDesertor = before.copy(
            players = before.players.map { it.copy(isHuman = it.name == desertor.name) }
        )
        val applied = GameEngine.chooseDesertorTeam(asDesertor, team)
        if (applied == asDesertor) return
        session = applied.copy(
            players = applied.players.map { player ->
                player.copy(
                    isHuman = before.players.firstOrNull { it.name == player.name }?.isHuman == true
                )
            },
            // La pista privada del desertor no debe aparecer en el celular del anfitrion.
            privateHint = if (hostIsDesertor) applied.privateHint else before.privateHint
        )
        OnlineDebugLog.i(
            "desertor_team_applied roomId=$onlinePartidaId desertor=${desertor.name} accion=$expectedAction round=${session.round}"
        )
        publishAuthoritativeOnlineState()
        renderGame()
    }

    /**
     * El anfitrión ya recibe todas las acciones confirmadas para resolver la noche. Publica
     * una copia mínima y privada en RTDB para que todos los Traidores vean quién eligió a
     * quién, incluida su propia decisión, sin abrir la colección completa de acciones.
     */
    private fun publishTraitorPlanNotices() {
        if (!isOnlineGameplay() || !onlineIsHost || !::session.isInitialized) return
        val notices = TraitorKillNotices.confirmedNotices(session, onlineNightActionRecords)
        if (notices.isEmpty()) return

        val planReference = FirebaseDatabase.getInstance()
            .getReference("salas/$onlinePartidaId/chat_traidores")
        notices.forEach { notice ->
            val remoteNoticeId =
                "plan_${session.onlineMatchId.hashCode().toUInt().toString(16)}_${notice.id}"
            if (!publishedTraitorPlanNoticeIds.add(remoteNoticeId)) return@forEach
            planReference.child(remoteNoticeId)
                .setValue(
                    mapOf(
                        "matchId" to session.onlineMatchId,
                        "actorId" to onlinePlayerId,
                        "speaker" to TraitorKillNotices.SPEAKER,
                        "mensaje" to notice.message,
                        "fase" to session.phase.name,
                        "ronda" to session.round,
                        "isGod" to true,
                        "canal" to "traidores",
                        "tipo" to "accion",
                        "actorNombre" to notice.actorName,
                        "objetivoNombre" to notice.targetName,
                        "accionRol" to notice.roleKey,
                        "faseIndice" to session.phaseIndex,
                        "ts" to ServerValue.TIMESTAMP
                    )
                )
                .addOnFailureListener { error ->
                    publishedTraitorPlanNoticeIds.remove(remoteNoticeId)
                    OnlineDebugLog.w(
                        "traitor_plan_notice_write_skipped roomId=$onlinePartidaId id=$remoteNoticeId reason=${error.message.orEmpty()}"
                    )
                }
        }
    }

    /**
     * Sin bando elegido, `GameRules.winnerFor` no puede declarar ganadores a los traidores y
     * la partida se queda sin final posible. Pasada una ronda entera, el anfitrion elige por
     * el desertor ausente.
     */
    private fun maybeAutoResolveOnlineDesertorTeam() {
        if (!isOnlineGameplay() || !::session.isInitialized) return
        val hasAliveDesertor = GameEngine.alivePlayers(session)
            .any { it.role?.key == RoleCatalog.DESERTOR }
        if (
            !OnlineDesertorGate.needsAutoTeam(
                isHost = onlineIsHost,
                hasAliveDesertor = hasAliveDesertor,
                teamIsBlank = session.desertorTeam.isBlank(),
                round = session.round,
                winner = session.winner
            )
        ) {
            return
        }
        val team = OnlineDesertorGate.autoTeam(session.code, session.players.map { it.name })
        session = session.copy(desertorTeam = team)
        OnlineDebugLog.w(
            "desertor_team_auto_assigned roomId=$onlinePartidaId round=${session.round} team=$team"
        )
        publishAuthoritativeOnlineState()
    }

    // Cuando el humano queda fuera (muerto o expulsado) y la partida sigue, o gano como Bufon,
    // le ofrecemos quedarse a mirar (con la partida acelerada) o volver a la sala. Solo local.
    private fun maybeOfferSpectatorChoice(): Boolean {
        if (spectatorChoiceOffered) return false
        if (isOnlineGameplay()) return false
        if (session.winner.isNotBlank()) return false
        if (session.specialVictories.size > presentedSpecialVictoryCount) return false
        val human = GameEngine.humanPlayer(session)
        if (human.alive) return false
        if (hasPendingDawnRevealSequence()) return false
        if (isBlockingGameplayUiActive()) return false
        spectatorChoiceOffered = true
        showSpectatorChoiceDialog(human)
        return true
    }

    private fun showSpectatorChoiceDialog(human: GamePlayer) {
        dismissSecondaryUiForPriorityWindow()
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        val wonAsJester = session.specialVictories.any {
            it.playerName == human.name && it.roleKey == RoleCatalog.BUFON
        }
        val title = if (wonAsJester) "¡GANASTE COMO BUFÓN!" else "TE ELIMINARON"
        val message = if (wonAsJester) {
            "El pueblo te expulsó y cumpliste tu objetivo. Podés quedarte a ver cómo termina la partida o volver a la sala."
        } else {
            "Quedaste fuera de la partida. Podés quedarte a mirar cómo sigue o volver a la sala."
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(18))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }
        content.addView(
            TextView(this).apply {
                text = title
                setTextColor(getColor(R.color.accent_gold))
                textSize = 24f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            TextView(this).apply {
                text = message
                setTextColor(getColor(R.color.text_secondary))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(18))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun dialogButton(label: String, gold: Boolean): Button {
            return Button(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                minHeight = 0
                minWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(getColor(if (gold) R.color.bg_dark else R.color.text_primary))
                setBackgroundResource(if (gold) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark)
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val continueButton = dialogButton("SEGUIR MIRANDO", gold = true)
        val menuButton = dialogButton("VOLVER A SALA", gold = false)
        val availableButtonRowWidthDp = resources.configuration.screenWidthDp - 32 - 48 - 12
        val buttonWidth = (availableButtonRowWidthDp / 2).coerceIn(112, 138)
        buttonRow.addView(
            continueButton,
            LinearLayout.LayoutParams(dp(buttonWidth), dp(44)).apply { rightMargin = dp(6) }
        )
        buttonRow.addView(
            menuButton,
            LinearLayout.LayoutParams(dp(buttonWidth), dp(44)).apply { leftMargin = dp(6) }
        )
        content.addView(buttonRow)

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create()
        continueButton.setOnClickListener {
            dialog.dismiss()
            enterSpectatorFastForward()
        }
        menuButton.setOnClickListener {
            dialog.dismiss()
            returnToLobby()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                minOf(resources.displayMetrics.widthPixels - dp(32), dp(430)),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun enterSpectatorFastForward() {
        // Reusar quickTestMode: shouldAutoAdvance exige quickTestMode && !requiresHumanInput, y con
        // el humano fuera de juego requiresHumanInput es false, asi que las fases autoavanzan.
        // Los reveals siguen mostrandose (los dispara renderGame y scheduleAutoAdvanceIfNeeded
        // pausa el auto-avance mientras hay un overlay).
        session = session.copy(quickTestMode = true)
        renderGame()
        scheduleAutoAdvanceIfNeeded()
    }

    private fun showDesertorTeamDialog() {
        if (desertorDialogOpen || isFinishing) return
        dismissSecondaryUiForPriorityWindow()
        // Online no se pausa el reloj: si el desertor fuera el anfitrion, dejar la fase
        // congelada hasta que toque el dialogo frenaria la partida de toda la mesa.
        if (!isOnlineGameplay()) pauseCountdown()
        desertorDialogOpen = true
        val isInitial = GameEngine.needsInitialDesertorChoice(session)
        val title = if (isInitial) "Elige tu bando" else "¿Quieres cambiar de bando?"
        val message = if (isInitial) {
            "Tu elección es secreta. Para ganar tienes que sobrevivir y lograr que venza tu bando."
        } else {
            "Esta es tu única oportunidad de reconsiderarlo. También puedes mantener el mismo bando."
        }
        val content = layoutInflater.inflate(R.layout.dialog_desertor_choice, null)
        content.findViewById<TextView>(R.id.desertorChoiceTitle).text = title.uppercase()
        content.findViewById<TextView>(R.id.desertorChoiceMessage).text = message
        content.findViewById<ImageView>(R.id.desertorChoiceImage)
            .setImageResource(roleImageFor(GameEngine.humanPlayer(session).role))

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create()

        fun chooseTeam(team: String) {
            desertorDialogOpen = false
            val changedTeam = session.desertorTeam.isNotBlank() && session.desertorTeam != team
            if (isOnlineGameplay()) {
                recordOnlineDesertorChoice(team, isInitial)
                dialog.dismiss()
                renderGame()
                showActionFeedbackBanner(
                    GameplayTableUi.feedbackForDesertorChoice(team, changedTeam)
                )
                return
            }
            session = GameEngine.chooseDesertorTeam(session, team)
            dialog.dismiss()
            renderGame()
            showActionFeedbackBanner(
                GameplayTableUi.feedbackForDesertorChoice(team, changedTeam)
            )
            resumeGameFlowAfterBlockingUi()
        }

        content.findViewById<View>(R.id.btnDesertorTown).setOnClickListener {
            chooseTeam(GameRules.TOWN_WINNER)
        }
        content.findViewById<View>(R.id.btnDesertorTraitors).setOnClickListener {
            chooseTeam(GameRules.TRAITOR_WINNER)
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = (resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(280))
                setLayout(dp(360).coerceAtMost(maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    companion object {
        private const val PLAYER_STATE_CONNECTED = "conectado"
        private const val PLAYER_STATE_DISCONNECTED = "desconectado"
        private const val BOTTOM_PLAYER_PANEL_HEIGHT_DP = 146
        private const val MAX_CARD_ACTION_MARKS = 3
        private const val TIE_VOTE_GRID_MAX_HEIGHT_DP = 264
        // Panel de desempate: margen horizontal (32*2) + padding (24*2) + un respiro = 120dp.
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val STATE_SESSION = "gameplay_session"
        private const val STATE_EVENT_LOG_EXPANDED = "event_log_expanded"
        private const val STATE_VOTE_NO_EXPULSION_PRESENTED =
            "vote_no_expulsion_presented"
        private const val STATE_SPECTATOR_CHOICE_OFFERED = "spectator_choice_offered"
        private const val STATE_ROLE_PREVIEW_OPEN = "role_preview_open"
        private const val STATE_INITIAL_ROLE_READING = "initial_role_reading"
        private const val STATE_ROLE_READING_REMAINING_MS = "role_reading_remaining_ms"
        private const val STATE_READY_VOTE_PHASE_INDEX = "ready_vote_phase_index"
        private const val STATE_READY_TO_VOTE_PLAYERS = "ready_to_vote_players"
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
        private const val STATE_PAYADOR_REVEAL_KEY = "payador_reveal_key"
        private const val STATE_ORACLE_REVEAL_KEY = "oracle_reveal_key"
        private const val STATE_LAST_NO_DEATH_REVEAL_ROUND = "last_no_death_reveal_round"
        private const val STATE_PRESENTED_SPECIAL_VICTORY_COUNT =
            "presented_special_victory_count"
        private const val STATE_ONLINE_PARTIDA_ID = "online_partida_id"
        private const val STATE_ONLINE_PLAYER_ID = "online_player_id"
        private const val STATE_ONLINE_IS_HOST = "online_is_host"
        private const val STATE_ONLINE_INITIAL_ROLE_READ = "online_initial_role_read"
        private const val STATE_ONLINE_STARTUP_DEADLINE_EPOCH_MS =
            "online_startup_deadline_epoch_ms"
        private const val STATE_ONLINE_LOBBY_RETURN_EPOCH_MS =
            "online_lobby_return_epoch_ms"
        private const val STATE_ONLINE_PRESENTATION_ACK_KEY = "online_presentation_ack_key"
        private const val STATE_ONLINE_WINNER_RETURN_ACK_KEY =
            "online_winner_return_ack_key"
        private const val TRAITOR_REVEAL_DURATION_MS = 8000L
        private const val SPECIAL_ROLE_REVEAL_DURATION_MS = 7000L
        private const val JESTER_VICTORY_DURATION_MS = 8000L
        private const val WINNER_AUTO_RETURN_MS = 45_000L
        private const val WINNER_RETURN_RETRY_MS = 2_000L
        private const val COUNTDOWN_TICK_MS = 200L
        private const val REVEAL_CONTINUE_TIMEOUT_MS = 9_000L
        private const val ONLINE_DEATH_REVEAL_BEAT_MS = 900L
        private const val PRESENTATION_GATE_TICK_MS = 250L
        /**
         * Cuanto se espera a que aparezca una cuenta registrada antes de dejar que un invitado
         * tome el anfitrionazgo. Pasado ese tiempo, la unica alternativa es una partida que no
         * avanza mas para nadie.
         */
        private const val GUEST_HOST_GRACE_MS = 20_000L
        private const val HOST_ROLE_RECOVERY_RETRY_MS = 2_000L
        private const val INFORMATION_FEEDBACK_DURATION_MS = 10_000L
        private const val PHASE_ADVICE_DURATION_MS = 8_000L
        private const val CENTRAL_PUBLIC_EVENT_DURATION_MS = 5_200L
        private const val LOCAL_NO_INPUT_NIGHT_SECONDS = 10
        private const val NIGHT_SKIP_ARM_DELAY_MS = 3_500L
        private const val READY_VOTE_MINIMUM_DEBATE_MS = 10_000L
        private const val MAX_NIGHT_SKIP_STEPS = 8
        private const val ONLINE_PREVIEW_ACTION_HISTORY_LIMIT = 60
        private const val MAX_PENDING_ONLINE_REACTIONS = 12
        private const val CENTRAL_EVENT_DANGER_HEX = "#A83232"
        private const val CENTRAL_EVENT_VOTE_HEX = "#D4A24E"
        private const val PRIMARY_ACTION_RESTING_FILL = "#4A3A1E"
        private const val PREF_ROLE_READING_SECONDS = "role_reading_seconds"
        private const val DEFAULT_ROLE_READING_SECONDS = 0
        private const val FIELD_READY_TO_VOTE = "listoParaVotar"
        private const val FIELD_READY_TO_VOTE_ROUND = "listoParaVotarRonda"
        private const val FIELD_READY_TO_VOTE_PHASE_INDEX = "listoParaVotarPhaseIndex"
        private const val FIELD_PRESENTATION_ACK_KEY = "presentacionConfirmada"
        private const val FIELD_WINNER_RETURN_ACK_KEY = "regresoLobbyConfirmado"
        private const val FIELD_STARTUP_AUTO_DEADLINE = "inicioAutomaticoEpochMs"
        private const val FIELD_PRIVATE_INVESTIGATION_CLUE = "pistaInvestigacion"
        private val PRIVATE_INVESTIGATION_RESULTS = setOf("inocente", "sospechoso")
        private const val ONLINE_ACTION_MAYOR_REVEAL = "revelar_alcalde"
        private const val ONLINE_ACTION_DESERTOR_TEAM = "elegir_bando"
        private const val ONLINE_ACTION_DESERTOR_RETHINK = "reconsiderar_bando"
        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
        const val EXTRA_ONLINE_PARTIDA_ID = "extra_online_partida_id"
        const val EXTRA_ONLINE_PLAYER_ID = "extra_online_player_id"
        const val EXTRA_ONLINE_IS_HOST = "extra_online_is_host"
    }
}
