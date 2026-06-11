package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import java.util.ArrayDeque

class GameplayMockActivity : BaseActivity() {

    private var isCardRevealed = false
    private var isChatOpen = false
    private var isEventLogExpanded = false
    private var lastRenderedAnnouncement = ""
    private var lastRenderedPhase: GamePhase? = null
    private var lastSeenChatCount = 0
    private var selectedTarget = ""
    private var desertorDialogOpen = false
    private var isDayNightTransitionRunning = false
    private var isDeathRevealRunning = false
    private var isSilenceRevealRunning = false
    private var isRolePreviewOpen = false
    private var isWinnerRevealVisible = false
    private var isTraitorRevealDismissing = false
    private var isTraitorRevealRunning = false
    private var lastPresentedTransitionKey: String? = null
    private var lastActionAttentionKey: String? = null
    private var presentedPeriod: GameplayPeriod? = null
    private var traitorRevealCompleted = false
    private var winnerRevealPresented = false
    private var countdownStage: CountdownStage? = null
    private var countdownPhaseIndex = -1
    private var countdownRemainingMs = 0L
    private var countdownTotalMs = 0L
    private var countdownDeadlineMs = 0L
    private var countdownRunning = false
    private var lastCountdownSecond = -1
    private var knownDeadPlayers = emptySet<String>()
    private var knownMutedPlayers = emptySet<String>()
    private var lastRenderedEventMessages = emptyList<String>()
    private var lastRenderedEventExpanded: Boolean? = null
    private lateinit var session: GameSession
    private var unreadChatCount = 0
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val traitorRevealDismissRunnable = Runnable { dismissTraitorReveal() }
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
        }
    }
    private val transitionMusicRunnable = Runnable {
        if (isDayNightTransitionRunning) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private var actionPulseAnimator: AnimatorSet? = null
    private var dayNightAnimator: AnimatorSet? = null
    private var deathRevealAnimator: AnimatorSet? = null
    private var deathRevealSoundPlayer: MediaPlayer? = null
    private var eventLogHeightAnimator: ValueAnimator? = null
    private var silenceRevealAnimator: AnimatorSet? = null
    private var silenceRevealSoundPlayer: MediaPlayer? = null
    private var traitorRevealAnimator: AnimatorSet? = null
    private var transitionSoundPlayer: MediaPlayer? = null
    private var winnerRevealAnimator: AnimatorSet? = null

    private lateinit var btnAction: Button
    private lateinit var btnRevealCard: Button
    private lateinit var btnToggleChat: ImageButton
    private lateinit var btnToggleEventLog: Button
    private lateinit var btnSendChat: Button
    private lateinit var chatInput: EditText
    private lateinit var chatMessages: TextView
    private lateinit var chatMessagesScroll: ScrollView
    private lateinit var chatPanel: LinearLayout
    private lateinit var chatUnreadBadge: TextView
    private lateinit var currentPlayerHint: TextView
    private lateinit var currentPlayerName: TextView
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
    private lateinit var rightPlayersContainer: LinearLayout
    private lateinit var rightPlayersScroll: ScrollView
    private lateinit var rightColumn: LinearLayout
    private lateinit var roleCard: LinearLayout
    private lateinit var roleImage: ImageView
    private lateinit var roleName: TextView
    private lateinit var rolePreviewContent: FrameLayout
    private lateinit var rolePreviewImage: ImageView
    private lateinit var rolePreviewName: TextView
    private lateinit var rolePreviewOverlay: FrameLayout
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
    private lateinit var traitorRevealCards: LinearLayout
    private lateinit var traitorRevealContent: LinearLayout
    private lateinit var traitorRevealOverlay: FrameLayout
    private lateinit var winnerRevealBackground: ImageView
    private lateinit var winnerRevealCards: LinearLayout
    private lateinit var winnerRevealContent: LinearLayout
    private lateinit var winnerRevealOverlay: FrameLayout
    private lateinit var winnerRevealPanel: FrameLayout
    private lateinit var winnerRevealPersonalResult: TextView
    private lateinit var winnerRevealShine: View
    private lateinit var winnerRevealTitle: TextView
    private lateinit var themeKey: String
    private val pendingDeathReveals = ArrayDeque<GamePlayer>()
    private val pendingSilenceReveals = ArrayDeque<GamePlayer>()
    private val playerCardViews = linkedMapOf<String, SidePlayerCardHolder>()
    private val winnerCardViews = mutableListOf<View>()
    private val winnerCardFinalAlphas = linkedMapOf<View, Float>()

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
        themeKey = themeFromIntentOrSession()
        lastPresentedTransitionKey = savedInstanceState?.getString(STATE_TRANSITION_KEY)
        presentedPeriod = savedInstanceState
            ?.getString(STATE_PRESENTED_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        traitorRevealCompleted = savedInstanceState?.getBoolean(STATE_TRAITOR_REVEAL_COMPLETED) ?: false
        winnerRevealPresented = savedInstanceState?.getBoolean(STATE_WINNER_REVEAL_PRESENTED) ?: false
        isChatOpen = savedInstanceState?.getBoolean(STATE_CHAT_OPEN) ?: false
        isEventLogExpanded = savedInstanceState?.getBoolean(STATE_EVENT_LOG_EXPANDED) ?: false
        selectedTarget = savedInstanceState?.getString(STATE_SELECTED_TARGET).orEmpty()
        countdownStage = savedInstanceState
            ?.getString(STATE_COUNTDOWN_STAGE)
            ?.let { runCatching { CountdownStage.valueOf(it) }.getOrNull() }
        countdownPhaseIndex = savedInstanceState?.getInt(STATE_COUNTDOWN_PHASE_INDEX, -1) ?: -1
        countdownRemainingMs = savedInstanceState?.getLong(STATE_COUNTDOWN_REMAINING_MS, 0L) ?: 0L
        countdownTotalMs = savedInstanceState?.getLong(STATE_COUNTDOWN_TOTAL_MS, 0L) ?: 0L
        knownDeadPlayers = session.players.filterNot { it.alive }.map { it.name }.toSet()
        knownMutedPlayers = session.players.filter { it.muted }.map { it.name }.toSet()
        lastSeenChatCount = session.chatHistory.size

        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        btnAction = findViewById(R.id.btnVote)
        btnRevealCard = findViewById(R.id.btnRevealCard)
        btnToggleChat = findViewById(R.id.btnToggleChat)
        btnToggleEventLog = findViewById(R.id.btnToggleEventLog)
        btnSendChat = findViewById(R.id.btnSendChat)
        chatInput = findViewById(R.id.chatInput)
        chatMessages = findViewById(R.id.chatMessages)
        chatMessagesScroll = findViewById(R.id.chatMessagesScroll)
        chatPanel = findViewById(R.id.chatPanel)
        chatUnreadBadge = findViewById(R.id.chatUnreadBadge)
        currentPlayerHint = findViewById(R.id.currentPlayerHint)
        currentPlayerName = findViewById(R.id.currentPlayerName)
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
        rightPlayersContainer = findViewById(R.id.rightPlayersContainer)
        rightPlayersScroll = findViewById(R.id.rightPlayersScroll)
        rightColumn = findViewById(R.id.rightColumn)
        roleCard = findViewById(R.id.roleCard)
        roleImage = findViewById(R.id.roleImage)
        roleName = findViewById(R.id.roleName)
        rolePreviewContent = findViewById(R.id.rolePreviewContent)
        rolePreviewImage = findViewById(R.id.rolePreviewImage)
        rolePreviewName = findViewById(R.id.rolePreviewName)
        rolePreviewOverlay = findViewById(R.id.rolePreviewOverlay)
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
        traitorRevealCards = findViewById(R.id.traitorRevealCards)
        traitorRevealContent = findViewById(R.id.traitorRevealContent)
        traitorRevealOverlay = findViewById(R.id.traitorRevealOverlay)
        winnerRevealBackground = findViewById(R.id.winnerRevealBackground)
        winnerRevealCards = findViewById(R.id.winnerRevealCards)
        winnerRevealContent = findViewById(R.id.winnerRevealContent)
        winnerRevealOverlay = findViewById(R.id.winnerRevealOverlay)
        winnerRevealPanel = findViewById(R.id.winnerRevealPanel)
        winnerRevealPersonalResult = findViewById(R.id.winnerRevealPersonalResult)
        winnerRevealShine = findViewById(R.id.winnerRevealShine)
        winnerRevealTitle = findViewById(R.id.winnerRevealTitle)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, OpcionesActivity::class.java))
        }
        btnAction.setOnClickListener { handleCurrentPhase() }
        btnRevealCard.setOnClickListener { toggleHumanCard() }
        btnToggleChat.setOnClickListener { toggleChatPanel() }
        btnToggleEventLog.setOnClickListener { toggleEventLog() }
        eventLogHeader.setOnClickListener { toggleEventLog() }
        findViewById<ImageButton>(R.id.btnCloseChat).setOnClickListener { closeChatPanel() }
        btnSendChat.setOnClickListener { sendHumanChatMessage() }
        roleCard.setOnClickListener { showRolePreview() }
        rolePreviewContent.setOnClickListener { }
        rolePreviewOverlay.setOnClickListener { closeRolePreview() }
        findViewById<ImageButton>(R.id.btnCloseRolePreview).setOnClickListener { closeRolePreview() }
        traitorRevealOverlay.setOnClickListener { dismissTraitorReveal() }
        findViewById<Button>(R.id.btnWinnerReturnLobby).setOnClickListener { returnToLobby() }

        eventLogBackground.setImageResource(logDrawableFor(themeKey))
        renderChatPanelVisibility(animate = false)
        renderGame()
        gameplayRoot.post { renderPlayerColumns() }
    }

    override fun onDestroy() {
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        cancelTraitorReveal()
        settleWinnerReveal()
        cancelActionPulse()
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        releaseTransitionSound()
        releaseDeathRevealSound()
        releaseSilenceRevealSound()
        if (isFinishing) {
            MusicManager.stopVictoryMusic()
        } else {
            MusicManager.pauseVictoryMusic()
        }
        super.onDestroy()
    }

    override fun onPause() {
        pauseCountdown()
        settleDayNightTransition(resumeMusic = false)
        cancelDeathReveal(resumeMusic = false)
        cancelSilenceReveal(resumeMusic = false)
        cancelTraitorReveal()
        settleWinnerReveal()
        cancelActionPulse()
        eventLogHeightAnimator?.cancel()
        closeRolePreview(resumeGameFlow = false)
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        releaseDeathRevealSound()
        releaseSilenceRevealSound()
        MusicManager.pauseVictoryMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && isWinnerRevealVisible) {
            MusicManager.resumeVictoryMusic(this)
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
        outState.putBoolean(STATE_TRAITOR_REVEAL_COMPLETED, traitorRevealCompleted)
        outState.putBoolean(STATE_WINNER_REVEAL_PRESENTED, winnerRevealPresented)
        outState.putBoolean(STATE_CHAT_OPEN, isChatOpen)
        outState.putBoolean(STATE_EVENT_LOG_EXPANDED, isEventLogExpanded)
        outState.putString(STATE_SELECTED_TARGET, selectedTarget)
        outState.putString(STATE_COUNTDOWN_STAGE, countdownStage?.name)
        outState.putInt(STATE_COUNTDOWN_PHASE_INDEX, countdownPhaseIndex)
        outState.putLong(STATE_COUNTDOWN_REMAINING_MS, countdownRemainingForSave())
        outState.putLong(STATE_COUNTDOWN_TOTAL_MS, countdownTotalMs)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isDeathRevealRunning || isSilenceRevealRunning) return
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
        if (countdownStage == CountdownStage.TRANSITION && countdownPhaseIndex == session.phaseIndex) {
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
            session.phase == GamePhase.DIA_DEBATE &&
            human.role?.key == "alcalde" &&
            !session.alcaldeRevealed
        ) {
            session = GameEngine.revealAlcalde(session)
            renderGame()
            return
        }

        if (GameEngine.requiresHumanInput(session)) {
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
        if (countdownStage == CountdownStage.TRANSITION && countdownPhaseIndex == session.phaseIndex) {
            Toast.makeText(this, "Espera a que comience la fase.", Toast.LENGTH_SHORT).show()
            return
        }
        pauseCountdown()
        if (!GameEngine.canActOnTarget(session, targetName)) {
            Toast.makeText(this, "No podes actuar sobre ese jugador.", Toast.LENGTH_SHORT).show()
            renderGame()
            return
        }

        selectedTarget = targetName
        session = GameEngine.resolveHumanTargetAction(session, targetName)
        clearSelection()
        renderGame()
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
        val shouldStartTransition = !isDayNightTransitionRunning &&
            GameplayTableUi.shouldPresentTransition(transitionSpec, lastPresentedTransitionKey)
        if (shouldStartTransition) {
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
        val visiblePeriod = if (isDayNightTransitionRunning) {
            presentedPeriod ?: transitionSpec.period
        } else {
            transitionSpec.period
        }
        renderThemedBackground(visiblePeriod)
        renderNarrator(phaseText, publicMessage, eventChanged)
        renderEventLogPanel()
        renderEventLog(publicMessage, phaseText)
        currentPlayerName.text = GameEngine.humanPlayer(session).name
        currentPlayerHint.text = privateHintText()
        renderAdvanceButton()
        renderHumanCardIfVisible()
        renderPlayerColumns(newlyDeadPlayers.map { it.name }.toSet())
        renderChatPanel()
        renderChatBadge()
        lastRenderedPhase = session.phase
        lastRenderedAnnouncement = publicMessage
        if (shouldStartTransition) {
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
        eventLogColorBar.setBackgroundColor(
            Color.parseColor(GameplayTableUi.eventTypeFor(latestEvent, session.phase).colorHex)
        )
        val visibleEvents = if (isEventLogExpanded) allEvents.takeLast(8) else listOf(latestEvent)
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
        val targetHeight = dp(if (isEventLogExpanded) 170 else 32)
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
        text.textSize = if (isEventLogExpanded) 10.5f else 9f
        text.maxLines = if (isEventLogExpanded) 3 else 1
        text.ellipsize = TextUtils.TruncateAt.END
        text.setPadding(dp(9), 0, 0, 0)
        row.addView(
            text,
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
        val transitionLocked = countdownStage == CountdownStage.TRANSITION &&
            countdownPhaseIndex == session.phaseIndex
        val specialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            (session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed)
        val label = when {
            session.winner.isNotBlank() -> "FINAL"
            selectedAction != null -> selectedAction
            canSelfProtect -> "SALVARME"
            GameEngine.needsInitialDesertorChoice(session) -> "ELEGIR BANDO"
            GameEngine.canDesertorReconsider(session) -> "REVISAR BANDO"
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
                Gravity.TOP or Gravity.START
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
        val actionLabel = GameEngine.targetActionLabel(session, player.name)
        val transitionLocked = countdownStage == CountdownStage.TRANSITION &&
            countdownPhaseIndex == session.phaseIndex
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
        }
        holder.avatar.text = if (isAlive) GameplayTableUi.playerInitial(player) else "\u2620"
        holder.avatar.textSize = if (isAlive) metrics.nameTextSp else metrics.nameTextSp + 1f
        holder.mutedBadge.visibility = if (isAlive && player.muted) View.VISIBLE else View.GONE

        holder.name.layoutParams = (holder.name.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(metrics.nameHeightDp)
        }
        holder.name.text = player.name
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            holder.name,
            7,
            metrics.nameTextSp.toInt().coerceAtLeast(7),
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        holder.name.setTextColor(
            getColor(
                when {
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

        holder.root.alpha = if (isAlive) 1f else 0.4f
        holder.root.setOnClickListener {
            when {
                isActionable -> {
                    selectedTarget = if (isSelected) "" else player.name
                    currentPlayerHint.text = privateHintText()
                    renderAdvanceButton()
                    renderPlayerColumns()
                }
                !isAlive -> Toast.makeText(this, "${player.name} esta eliminado.", Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
        holder.root.contentDescription = when {
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
        lastSeenChatCount = session.chatHistory.size
        renderChatPanelVisibility(animate = true)
        renderChatPanel()
        renderChatBadge()
    }

    private fun closeChatPanel() {
        if (!isChatOpen) return
        isChatOpen = false
        renderChatPanelVisibility(animate = true)
        renderChatBadge()
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
        chatMessages.text = if (messages.isEmpty()) {
            "Todavia no hay mensajes."
        } else {
            messages.joinToString("\n") { message -> "${message.speaker}: ${message.message}" }
        }

        val canChat = GameEngine.canHumanChat(session)
        chatInput.isEnabled = canChat
        btnSendChat.isEnabled = canChat
        chatInput.hint = chatInputHint(canChat)
        btnSendChat.alpha = if (canChat) 1f else 0.45f
        chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateUnreadChatCount() {
        val currentCount = session.chatHistory.size
        if (isChatOpen) {
            unreadChatCount = 0
            lastSeenChatCount = currentCount
            return
        }

        if (currentCount > lastSeenChatCount) {
            val humanName = GameEngine.humanPlayer(session).name
            val newMessages = session.chatHistory.drop(lastSeenChatCount)
                .count { !it.isGod && it.speaker != humanName }
            unreadChatCount += newMessages
            lastSeenChatCount = currentCount
        }
    }

    private fun renderChatBadge() {
        chatUnreadBadge.visibility = if (unreadChatCount > 0) View.VISIBLE else View.GONE
        chatUnreadBadge.text = unreadChatCount.coerceAtMost(99).toString()
    }

    private fun sendHumanChatMessage() {
        if (countdownStage == CountdownStage.TRANSITION && countdownPhaseIndex == session.phaseIndex) {
            Toast.makeText(this, "El chat se habilita al comenzar la fase.", Toast.LENGTH_SHORT).show()
            return
        }
        val before = session.chatHistory.size
        session = GameEngine.addHumanChatMessage(session, chatInput.text.toString())
        if (session.chatHistory.size > before) {
            chatInput.text.clear()
        } else if (!GameEngine.canHumanChat(session)) {
            val human = GameEngine.humanPlayer(session)
            val message = when {
                !human.alive -> "Estas eliminado. Podes mirar el chat, no escribir."
                human.muted -> "Estas muteado. Podes mirar el chat, no escribir."
                else -> "No podes escribir durante esta fase."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        renderGame()
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
            isWinnerRevealVisible ||
            isRolePreviewOpen ||
            isTraitorRevealRunning ||
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
        if (countdownPhaseIndex != session.phaseIndex || countdownStage == null) {
            countdownPhaseIndex = session.phaseIndex
            countdownStage = CountdownStage.TRANSITION
            countdownTotalMs = session.timingConfig.normalized().transitionSeconds * 1000L
            countdownRemainingMs = countdownTotalMs
        }
        startCountdown()
    }

    private fun startCountdown() {
        if (countdownRunning || countdownRemainingMs <= 0L) {
            if (countdownRemainingMs <= 0L) onCountdownExpired()
            return
        }
        countdownRunning = true
        if (countdownTotalMs <= 0L) countdownTotalMs = countdownRemainingMs
        countdownDeadlineMs = SystemClock.elapsedRealtime() + countdownRemainingMs
        lastCountdownSecond = -1
        phaseCountdown.visibility = View.VISIBLE
        renderAdvanceButton()
        updateCountdown()
    }

    private fun updateCountdown() {
        if (!countdownRunning) return
        countdownRemainingMs = (countdownDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val seconds = kotlin.math.ceil(countdownRemainingMs / 1000.0).toInt()
        renderCountdown(seconds)
        if (countdownRemainingMs <= 0L) {
            countdownRunning = false
            autoAdvanceHandler.removeCallbacks(countdownRunnable)
            onCountdownExpired()
        } else {
            autoAdvanceHandler.postDelayed(countdownRunnable, COUNTDOWN_TICK_MS)
        }
    }

    private fun renderCountdown(seconds: Int) {
        phaseCountdown.text = seconds.coerceAtLeast(0).toString()
        val urgent = seconds in 1..5
        phaseCountdown.setTextColor(getColor(if (urgent) R.color.accent_red else R.color.text_primary))
        phaseProgressFill.setBackgroundColor(
            getColor(if (urgent) R.color.accent_red else R.color.accent_gold)
        )
        phaseProgressFill.scaleX = if (countdownTotalMs > 0L) {
            (countdownRemainingMs.toFloat() / countdownTotalMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (urgent && seconds != lastCountdownSecond) {
            phaseCountdown.animate().cancel()
            phaseProgressFill.animate().cancel()
            phaseCountdown.scaleX = 1f
            phaseCountdown.scaleY = 1f
            phaseProgressFill.alpha = 1f
            phaseCountdown.animate()
                .scaleX(1.14f)
                .scaleY(1.14f)
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
        if (session.winner.isNotBlank() || countdownPhaseIndex != session.phaseIndex) {
            clearCountdown()
            return
        }
        if (countdownStage == CountdownStage.TRANSITION) {
            val phaseSeconds = activePhaseSeconds()
            if (phaseSeconds != null) {
                countdownStage = CountdownStage.ACTIVE
                countdownTotalMs = phaseSeconds * 1000L
                countdownRemainingMs = countdownTotalMs
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
            GamePhase.NOCHE_MEDICO -> {
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
            else -> session
        }
    }

    private fun activePhaseSeconds(): Int? {
        val timing = session.timingConfig.normalized()
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> timing.nightSeconds.takeIf {
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

    private fun pauseCountdown() {
        if (countdownRunning) {
            countdownRemainingMs = (countdownDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
        countdownRunning = false
        autoAdvanceHandler.removeCallbacks(countdownRunnable)
        phaseCountdown.animate().cancel()
        phaseProgressFill.animate().cancel()
        phaseCountdown.scaleX = 1f
        phaseCountdown.scaleY = 1f
        phaseProgressFill.alpha = 1f
    }

    private fun clearCountdown() {
        pauseCountdown()
        countdownStage = null
        countdownPhaseIndex = -1
        countdownRemainingMs = 0L
        countdownTotalMs = 0L
        lastCountdownSecond = -1
        phaseCountdown.visibility = View.INVISIBLE
        phaseCountdown.setTextColor(getColor(R.color.text_primary))
        phaseProgressFill.scaleX = 0f
        phaseProgressFill.alpha = 1f
        phaseProgressFill.setBackgroundColor(getColor(R.color.accent_gold))
    }

    private fun countdownRemainingForSave(): Long {
        return if (countdownRunning) {
            (countdownDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            countdownRemainingMs
        }
    }

    private fun toggleHumanCard() {
        if (session.phase == GamePhase.REPARTO) return
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

    private fun showRolePreview() {
        if (
            isRolePreviewOpen ||
            isDayNightTransitionRunning ||
            isDeathRevealRunning ||
            isSilenceRevealRunning ||
            isWinnerRevealVisible ||
            isTraitorRevealRunning ||
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
        rolePreviewName.text = role.name.uppercase()
        rolePreviewOverlay.alpha = 0f
        rolePreviewOverlay.visibility = View.VISIBLE
        isRolePreviewOpen = true
        rolePreviewOverlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun closeRolePreview(resumeGameFlow: Boolean = true) {
        if (!::rolePreviewOverlay.isInitialized) return
        rolePreviewOverlay.animate().cancel()
        rolePreviewOverlay.visibility = View.GONE
        rolePreviewOverlay.alpha = 1f
        val wasOpen = isRolePreviewOpen
        isRolePreviewOpen = false
        if (wasOpen && resumeGameFlow) {
            resumeGameFlowAfterBlockingUi()
        }
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

    private fun clearSelection() {
        selectedTarget = ""
    }

    private fun targetActionMessage(): String {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> "Selecciona una victima y confirma MATAR."
            GamePhase.NOCHE_MERCENARIO -> "Selecciona un jugador y confirma SILENCIAR."
            GamePhase.NOCHE_POLICIA -> "Selecciona un jugador y confirma INVESTIGAR."
            GamePhase.NOCHE_MEDICO -> "Selecciona un jugador y confirma SALVAR."
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
            isWinnerRevealVisible ||
            isRolePreviewOpen
        ) {
            return
        }
        if (maybeShowNextDeathReveal()) return
        if (maybeShowNextSilenceReveal()) return
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

        deathRevealPlayerName.text = player.name.uppercase()
        deathRevealRoleName.text = player.role?.name?.uppercase() ?: "ROL DESCONOCIDO"
        deathRevealCardFront.setImageResource(roleImageFor(player.role))
        resetDeathRevealViews()
        deathRevealOverlay.visibility = View.VISIBLE
        playDeathRevealSound()

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(deathRevealOverlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(deathRevealContent, View.SCALE_X, 0.94f, 1f),
                ObjectAnimator.ofFloat(deathRevealContent, View.SCALE_Y, 0.94f, 1f)
            )
            duration = 280L
            interpolator = DecelerateInterpolator()
        }

        val impact = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    deathRevealCard,
                    View.TRANSLATION_X,
                    0f,
                    -dp(6).toFloat(),
                    dp(6).toFloat(),
                    -dp(3).toFloat(),
                    dp(3).toFloat(),
                    0f
                ),
                ObjectAnimator.ofFloat(deathRevealFlash, View.ALPHA, 0f, 0.56f, 0f),
                ObjectAnimator.ofFloat(deathRevealBloodLeft, View.ALPHA, 0f, 0.9f),
                ObjectAnimator.ofFloat(deathRevealBloodLeft, View.SCALE_X, 0.55f, 1.08f),
                ObjectAnimator.ofFloat(deathRevealBloodLeft, View.SCALE_Y, 0.55f, 1.08f),
                ObjectAnimator.ofFloat(deathRevealBloodRight, View.ALPHA, 0f, 0.76f),
                ObjectAnimator.ofFloat(deathRevealBloodRight, View.SCALE_X, 0.5f, 1f),
                ObjectAnimator.ofFloat(deathRevealBloodRight, View.SCALE_Y, 0.5f, 1f)
            )
            duration = 420L
            interpolator = AccelerateDecelerateInterpolator()
        }

        val flipOut = ObjectAnimator.ofFloat(deathRevealCard, View.ROTATION_Y, 0f, 90f).apply {
            duration = 230L
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    deathRevealCardBack.visibility = View.INVISIBLE
                    deathRevealCardFront.visibility = View.VISIBLE
                    deathRevealCard.rotationY = -90f
                }
            })
        }
        val flipIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(deathRevealCard, View.ROTATION_Y, -90f, 0f),
                ObjectAnimator.ofFloat(deathRevealRoleName, View.ALPHA, 0f, 1f)
            )
            duration = 260L
            interpolator = DecelerateInterpolator()
        }
        val hold = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1050L
        }
        val exit = ObjectAnimator.ofFloat(deathRevealOverlay, View.ALPHA, 1f, 0f).apply {
            duration = 320L
            interpolator = AccelerateInterpolator()
        }

        deathRevealAnimator = AnimatorSet().apply {
            playSequentially(entrance, impact, flipOut, flipIn, hold, exit)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishDeathReveal()
                }
            })
            start()
        }
    }

    private fun resetDeathRevealViews() {
        deathRevealOverlay.alpha = 0f
        deathRevealContent.scaleX = 0.94f
        deathRevealContent.scaleY = 0.94f
        deathRevealFlash.alpha = 0f
        deathRevealCard.translationX = 0f
        deathRevealCard.rotationY = 0f
        deathRevealCard.cameraDistance = dp(900).toFloat()
        deathRevealCardBack.visibility = View.VISIBLE
        deathRevealCardFront.visibility = View.INVISIBLE
        deathRevealRoleName.alpha = 0f
        listOf(deathRevealBloodLeft, deathRevealBloodRight).forEach { blood ->
            blood.alpha = 0f
            blood.scaleX = 0.5f
            blood.scaleY = 0.5f
        }
    }

    private fun finishDeathReveal() {
        if (!isDeathRevealRunning) return
        isDeathRevealRunning = false
        deathRevealAnimator = null
        deathRevealOverlay.visibility = View.GONE
        deathRevealOverlay.alpha = 1f
        releaseDeathRevealSound()
        if (pendingDeathReveals.isEmpty() && pendingSilenceReveals.isEmpty()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelDeathReveal(resumeMusic: Boolean) {
        if (!::deathRevealOverlay.isInitialized) return
        deathRevealAnimator?.removeAllListeners()
        deathRevealAnimator?.cancel()
        deathRevealAnimator = null
        isDeathRevealRunning = false
        deathRevealOverlay.visibility = View.GONE
        deathRevealOverlay.alpha = 1f
        releaseDeathRevealSound()
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun playDeathRevealSound() {
        releaseDeathRevealSound()
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val soundOn = sharedPref.getBoolean("sound_on", true)
        val volume = sharedPref.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        deathRevealSoundPlayer = MediaPlayer.create(this, R.raw.death_reveal)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (deathRevealSoundPlayer === completed) {
                    deathRevealSoundPlayer = null
                }
                completed.release()
            }
            start()
        }
    }

    private fun releaseDeathRevealSound() {
        deathRevealSoundPlayer?.runCatching {
            stop()
            release()
        }
        deathRevealSoundPlayer = null
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

        silenceRevealPlayerName.text = player.name.uppercase()
        resetSilenceRevealViews()
        silenceRevealOverlay.visibility = View.VISIBLE
        playSilenceRevealSound()

        val entrance = AnimatorSet().apply {
            startDelay = SILENCE_REVEAL_GAP_MS
            playTogether(
                ObjectAnimator.ofFloat(silenceRevealOverlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(silenceRevealContent, View.SCALE_X, 0.95f, 1f),
                ObjectAnimator.ofFloat(silenceRevealContent, View.SCALE_Y, 0.95f, 1f)
            )
            duration = 280L
            interpolator = DecelerateInterpolator()
        }
        val buildCage = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(silenceRevealCageLeft, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(
                    silenceRevealCageLeft,
                    View.TRANSLATION_X,
                    -dp(62).toFloat(),
                    0f
                ),
                ObjectAnimator.ofFloat(silenceRevealCageRight, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(
                    silenceRevealCageRight,
                    View.TRANSLATION_X,
                    dp(62).toFloat(),
                    0f
                )
            )
            duration = 460L
            interpolator = DecelerateInterpolator()
        }
        val closeDoor = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(silenceRevealCageDoor, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(silenceRevealCageDoor, View.ROTATION_Y, -72f, 0f),
                ObjectAnimator.ofFloat(
                    silenceRevealCageDoor,
                    View.TRANSLATION_X,
                    dp(28).toFloat(),
                    0f
                )
            )
            duration = 420L
            interpolator = DecelerateInterpolator()
        }
        val lockImpact = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(silenceRevealCageLock, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(silenceRevealCageLock, View.SCALE_X, 1.65f, 1f),
                ObjectAnimator.ofFloat(silenceRevealCageLock, View.SCALE_Y, 1.65f, 1f),
                ObjectAnimator.ofFloat(
                    silenceRevealCard,
                    View.TRANSLATION_X,
                    0f,
                    -dp(3).toFloat(),
                    dp(3).toFloat(),
                    -dp(2).toFloat(),
                    dp(2).toFloat(),
                    0f
                )
            )
            duration = 320L
            interpolator = AccelerateDecelerateInterpolator()
        }
        val hold = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
        }
        val exit = ObjectAnimator.ofFloat(silenceRevealOverlay, View.ALPHA, 1f, 0f).apply {
            duration = 300L
            interpolator = AccelerateInterpolator()
        }

        silenceRevealAnimator = AnimatorSet().apply {
            playSequentially(entrance, buildCage, closeDoor, lockImpact, hold, exit)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishSilenceReveal()
                }
            })
            start()
        }
    }

    private fun resetSilenceRevealViews() {
        silenceRevealOverlay.alpha = 0f
        silenceRevealContent.scaleX = 0.95f
        silenceRevealContent.scaleY = 0.95f
        silenceRevealCard.translationX = 0f
        silenceRevealCard.scaleX = 1f
        silenceRevealCard.scaleY = 1f
        silenceRevealCageLeft.alpha = 0f
        silenceRevealCageLeft.translationX = -dp(62).toFloat()
        silenceRevealCageRight.alpha = 0f
        silenceRevealCageRight.translationX = dp(62).toFloat()
        silenceRevealCageDoor.alpha = 0f
        silenceRevealCageDoor.rotationY = -72f
        silenceRevealCageDoor.translationX = dp(28).toFloat()
        silenceRevealCageDoor.cameraDistance = dp(900).toFloat()
        silenceRevealCageLock.alpha = 0f
        silenceRevealCageLock.scaleX = 1.65f
        silenceRevealCageLock.scaleY = 1.65f
    }

    private fun finishSilenceReveal() {
        if (!isSilenceRevealRunning) return
        isSilenceRevealRunning = false
        silenceRevealAnimator = null
        silenceRevealOverlay.visibility = View.GONE
        silenceRevealOverlay.alpha = 1f
        releaseSilenceRevealSound()
        if (pendingSilenceReveals.isEmpty()) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
        resumeGameFlowAfterBlockingUi()
    }

    private fun cancelSilenceReveal(resumeMusic: Boolean) {
        if (!::silenceRevealOverlay.isInitialized) return
        silenceRevealAnimator?.removeAllListeners()
        silenceRevealAnimator?.cancel()
        silenceRevealAnimator = null
        isSilenceRevealRunning = false
        silenceRevealOverlay.visibility = View.GONE
        silenceRevealOverlay.alpha = 1f
        releaseSilenceRevealSound()
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private fun playSilenceRevealSound() {
        releaseSilenceRevealSound()
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val soundOn = sharedPref.getBoolean("sound_on", true)
        val volume = sharedPref.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        silenceRevealSoundPlayer = MediaPlayer.create(this, R.raw.silence_reveal)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (silenceRevealSoundPlayer === completed) {
                    silenceRevealSoundPlayer = null
                }
                completed.release()
            }
            start()
        }
    }

    private fun releaseSilenceRevealSound() {
        silenceRevealSoundPlayer?.runCatching {
            stop()
            release()
        }
        silenceRevealSoundPlayer = null
    }

    private fun maybeShowWinnerReveal(): Boolean {
        if (session.winner.isBlank()) return false
        if (isWinnerRevealVisible) return true
        showWinnerReveal(animate = !winnerRevealPresented)
        return true
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
        renderWinnerCards(presentation.winningPlayers)

        isWinnerRevealVisible = true
        winnerRevealOverlay.visibility = View.VISIBLE
        winnerRevealPresented = true
        if (!animate) {
            settleWinnerReveal()
            MusicManager.resumeVictoryMusic(this)
            return
        }

        resetWinnerRevealViews()
        MusicManager.playVictoryMusic(this)

        val entrance = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(winnerRevealOverlay, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(winnerRevealPanel, View.SCALE_X, 0.72f, 1f),
                ObjectAnimator.ofFloat(winnerRevealPanel, View.SCALE_Y, 0.72f, 1f),
                ObjectAnimator.ofFloat(winnerRevealPanel, View.ALPHA, 0f, 1f)
            )
            duration = 520L
            interpolator = DecelerateInterpolator()
        }
        val headings = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(winnerRevealTitle, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(winnerRevealTitle, View.TRANSLATION_Y, dp(10).toFloat(), 0f),
                ObjectAnimator.ofFloat(winnerRevealPersonalResult, View.ALPHA, 0f, 1f)
            )
            duration = 360L
            interpolator = DecelerateInterpolator()
        }

        val cardAnimators = winnerCardViews.mapIndexed { index, card ->
            AnimatorSet().apply {
                startDelay = index * WINNER_CARD_STAGGER_MS
                playTogether(
                    ObjectAnimator.ofFloat(
                        card,
                        View.ALPHA,
                        0f,
                        winnerCardFinalAlphas[card] ?: 1f
                    ),
                    ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(16).toFloat(), 0f),
                    ObjectAnimator.ofFloat(card, View.SCALE_X, 0.9f, 1f),
                    ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.9f, 1f)
                )
                duration = 300L
                interpolator = DecelerateInterpolator()
            }
        }
        val cardsEntrance = AnimatorSet().apply {
            playTogether(cardAnimators)
        }
        val shine = ObjectAnimator.ofFloat(winnerRevealShine, View.ALPHA, 0f, 0.34f, 0f).apply {
            duration = 720L
            interpolator = AccelerateDecelerateInterpolator()
        }

        winnerRevealAnimator = AnimatorSet().apply {
            playSequentially(entrance, headings, cardsEntrance, shine)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    winnerRevealAnimator = null
                    settleWinnerReveal()
                }
            })
            start()
        }
    }

    private fun renderWinnerCards(players: List<GamePlayer>) {
        winnerRevealCards.removeAllViews()
        winnerCardViews.clear()
        winnerCardFinalAlphas.clear()
        if (players.isEmpty()) return

        val rowCount = when (players.size) {
            in 1..5 -> 1
            in 6..10 -> 2
            else -> 3
        }
        val playersPerRow = kotlin.math.ceil(players.size / rowCount.toDouble()).toInt()
        players.chunked(playersPerRow).forEach { rowPlayers ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            winnerRevealCards.addView(row, rowParams)
            rowPlayers.forEach { player ->
                val card = createWinnerCard(player, players.size)
                winnerCardViews += card
                winnerCardFinalAlphas[card] = if (player.alive) 1f else 0.62f
                row.addView(card)
            }
        }
    }

    private fun createWinnerCard(player: GamePlayer, winnerCount: Int): View {
        val metrics = when {
            winnerCount <= 5 -> intArrayOf(110, 72, 88, 12, 9)
            winnerCount <= 10 -> intArrayOf(94, 58, 70, 10, 8)
            else -> intArrayOf(76, 42, 50, 9, 7)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(metrics[0]), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(3), dp(1), dp(3), dp(1))
        }
        val cardFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E6231810"))
                setStroke(
                    dp(if (player.alive) 3 else 2),
                    Color.parseColor(if (player.alive) "#D4A24E" else "#75695B")
                )
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            if (player.alive) elevation = dp(4).toFloat()
        }
        val image = ImageView(this).apply {
            setImageResource(roleImageFor(player.role))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Rol de ${player.name}"
            if (!player.alive) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
        }
        cardFrame.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            cardFrame,
            LinearLayout.LayoutParams(dp(metrics[1]), dp(metrics[2]))
        )

        val playerName = TextView(this).apply {
            text = player.name
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#352114"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics[3].toFloat())
            typeface = ResourcesCompat.getFont(this@GameplayMockActivity, R.font.grenze)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            playerName,
            7,
            metrics[3],
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        container.addView(
            playerName,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16))
        )

        val roleLabel = TextView(this).apply {
            text = player.role?.name?.uppercase() ?: "SIN ROL"
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#765019"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics[4].toFloat())
            typeface = ResourcesCompat.getFont(this@GameplayMockActivity, R.font.cormorant_garamond)
            includeFontPadding = false
        }
        container.addView(
            roleLabel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(13))
        )
        return container
    }

    private fun resetWinnerRevealViews() {
        winnerRevealOverlay.alpha = 0f
        winnerRevealPanel.alpha = 0f
        winnerRevealPanel.scaleX = 0.72f
        winnerRevealPanel.scaleY = 0.72f
        winnerRevealTitle.alpha = 0f
        winnerRevealTitle.translationY = dp(10).toFloat()
        winnerRevealPersonalResult.alpha = 0f
        winnerRevealShine.alpha = 0f
        winnerCardViews.forEach { card ->
            card.alpha = 0f
            card.translationY = dp(16).toFloat()
            card.scaleX = 0.9f
            card.scaleY = 0.9f
        }
    }

    private fun settleWinnerReveal() {
        if (!::winnerRevealOverlay.isInitialized || !isWinnerRevealVisible) return
        winnerRevealAnimator?.removeAllListeners()
        winnerRevealAnimator?.end()
        winnerRevealAnimator = null
        winnerRevealOverlay.visibility = View.VISIBLE
        winnerRevealOverlay.alpha = 1f
        winnerRevealPanel.alpha = 1f
        winnerRevealPanel.scaleX = 1f
        winnerRevealPanel.scaleY = 1f
        winnerRevealTitle.alpha = 1f
        winnerRevealTitle.translationY = 0f
        winnerRevealPersonalResult.alpha = 1f
        winnerRevealShine.alpha = 0f
        winnerCardViews.forEach { card ->
            card.alpha = winnerCardFinalAlphas[card] ?: 1f
            card.translationY = 0f
            card.scaleX = 1f
            card.scaleY = 1f
        }
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
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = true
        traitorRevealCards.removeAllViews()

        val cardViews = teammates.map { teammate ->
            createTraitorRevealCard(teammate).also { card ->
                card.alpha = 0f
                card.translationY = dp(42).toFloat()
                traitorRevealCards.addView(card)
            }
        }

        traitorRevealOverlay.alpha = 0f
        traitorRevealContent.scaleX = 0.96f
        traitorRevealContent.scaleY = 0.96f
        traitorRevealOverlay.visibility = View.VISIBLE

        val animators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(traitorRevealOverlay, View.ALPHA, 0f, 1f).apply {
                duration = 260L
            },
            ObjectAnimator.ofFloat(traitorRevealContent, View.SCALE_X, 0.96f, 1f).apply {
                duration = 320L
            },
            ObjectAnimator.ofFloat(traitorRevealContent, View.SCALE_Y, 0.96f, 1f).apply {
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
        traitorRevealAnimator = AnimatorSet().apply {
            interpolator = DecelerateInterpolator()
            playTogether(animators)
            start()
        }
        autoAdvanceHandler.postDelayed(traitorRevealDismissRunnable, TRAITOR_REVEAL_DURATION_MS)
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
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        traitorRevealAnimator?.removeAllListeners()
        traitorRevealAnimator?.cancel()

        val fade = ObjectAnimator.ofFloat(traitorRevealOverlay, View.ALPHA, traitorRevealOverlay.alpha, 0f)
        fade.duration = 220L
        traitorRevealAnimator = AnimatorSet().apply {
            playTogether(fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isTraitorRevealDismissing = false
                    isTraitorRevealRunning = false
                    traitorRevealAnimator = null
                    traitorRevealOverlay.visibility = View.GONE
                    traitorRevealOverlay.alpha = 1f
                    traitorRevealCards.removeAllViews()
                    resumeGameFlowAfterBlockingUi()
                }
            })
            start()
        }
    }

    private fun cancelTraitorReveal() {
        if (!::traitorRevealOverlay.isInitialized) return
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        traitorRevealAnimator?.removeAllListeners()
        traitorRevealAnimator?.cancel()
        traitorRevealAnimator = null
        isTraitorRevealDismissing = false
        isTraitorRevealRunning = false
        traitorRevealOverlay.visibility = View.GONE
        traitorRevealOverlay.alpha = 1f
        traitorRevealCards.removeAllViews()
    }

    private fun startDayNightTransition(spec: GameplayTransitionSpec) {
        pauseCountdown()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)

        val fromPeriod = presentedPeriod ?: spec.period
        transitionFromBackground.setImageResource(
            backgroundDrawableFor(themeKey, fromPeriod == GameplayPeriod.NIGHT)
        )
        transitionToBackground.setImageResource(
            backgroundDrawableFor(themeKey, spec.period == GameplayPeriod.NIGHT)
        )
        transitionToBackground.alpha = if (fromPeriod == spec.period) 1f else 0f
        transitionShade.alpha = if (spec.period == GameplayPeriod.NIGHT) 0.48f else 0.26f
        transitionTitle.text = spec.title
        transitionTitle.alpha = 0f
        transitionTitle.scaleX = 0.86f
        transitionTitle.scaleY = 0.86f
        dayNightTransitionOverlay.alpha = 1f
        dayNightTransitionOverlay.visibility = View.VISIBLE

        playTransitionSound(spec.period)
        autoAdvanceHandler.postDelayed(transitionMusicRunnable, TRANSITION_MUSIC_DELAY_MS)
        dayNightTransitionOverlay.post {
            if (isDayNightTransitionRunning) {
                animateDayNightTransition(spec, fromPeriod)
            }
        }
    }

    private fun animateDayNightTransition(
        spec: GameplayTransitionSpec,
        fromPeriod: GameplayPeriod
    ) {
        val width = dayNightTransitionOverlay.width.toFloat()
        val height = dayNightTransitionOverlay.height.toFloat()
        if (width <= 0f || height <= 0f) {
            finishDayNightTransition(spec)
            return
        }

        val sunTopX = width * 0.70f - transitionSun.width / 2f
        val moonTopX = width * 0.20f - transitionMoon.width / 2f
        val topY = height * 0.10f
        val lowerY = height + maxOf(transitionSun.height, transitionMoon.height) * 0.12f
        val animators = mutableListOf<Animator>()

        if (spec.period == GameplayPeriod.NIGHT) {
            if (fromPeriod == GameplayPeriod.DAY) {
                transitionSun.alpha = 1f
                transitionMoon.alpha = 0f
                animators += arcAnimator(
                    transitionSun,
                    sunTopX,
                    topY,
                    width * 0.90f,
                    height * 0.48f,
                    width + transitionSun.width * 0.15f,
                    lowerY
                )
                animators += fadeAnimator(transitionSun, 1f, 0f, 1180L, 520L)
            } else {
                transitionSun.alpha = 0f
            }
            animators += risingAnimator(
                transitionMoon,
                startX = -transitionMoon.width.toFloat(),
                startY = lowerY,
                controlX = width * 0.05f,
                controlY = height * 0.42f,
                endX = moonTopX,
                endY = topY
            )
        } else {
            if (fromPeriod == GameplayPeriod.NIGHT) {
                transitionMoon.alpha = 1f
                transitionSun.alpha = 0f
                animators += arcAnimator(
                    transitionMoon,
                    moonTopX,
                    topY,
                    width * 0.05f,
                    height * 0.48f,
                    -transitionMoon.width.toFloat(),
                    lowerY
                )
                animators += fadeAnimator(transitionMoon, 1f, 0f, 1180L, 520L)
            } else {
                transitionMoon.alpha = 0f
            }
            animators += risingAnimator(
                transitionSun,
                startX = width + transitionSun.width * 0.15f,
                startY = lowerY,
                controlX = width * 0.88f,
                controlY = height * 0.42f,
                endX = sunTopX,
                endY = topY
            )
        }

        animators += ObjectAnimator.ofFloat(transitionToBackground, View.ALPHA, transitionToBackground.alpha, 1f)
            .apply { duration = 1450L }
        animators += fadeAnimator(transitionTitle, 0f, 1f, 480L, 420L)
        animators += ObjectAnimator.ofFloat(transitionTitle, View.SCALE_X, 0.86f, 1f).apply {
            startDelay = 480L
            duration = 420L
        }
        animators += ObjectAnimator.ofFloat(transitionTitle, View.SCALE_Y, 0.86f, 1f).apply {
            startDelay = 480L
            duration = 420L
        }
        animators += fadeAnimator(transitionTitle, 1f, 0f, 1600L, 360L)
        animators += fadeAnimator(dayNightTransitionOverlay, 1f, 0f, 1850L, 350L)

        dayNightAnimator = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isDayNightTransitionRunning) {
                        finishDayNightTransition(spec)
                    }
                }
            })
            start()
        }
    }

    private fun risingAnimator(
        view: View,
        startX: Float,
        startY: Float,
        controlX: Float,
        controlY: Float,
        endX: Float,
        endY: Float
    ): Animator {
        view.alpha = 0f
        val motion = arcAnimator(view, startX, startY, controlX, controlY, endX, endY)
        val fade = fadeAnimator(view, 0f, 1f, 120L, 560L)
        return AnimatorSet().apply { playTogether(motion, fade) }
    }

    private fun arcAnimator(
        view: View,
        startX: Float,
        startY: Float,
        controlX: Float,
        controlY: Float,
        endX: Float,
        endY: Float
    ): ObjectAnimator {
        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(controlX, controlY, endX, endY)
        }
        return ObjectAnimator.ofFloat(view, View.X, View.Y, path).apply {
            duration = 1800L
        }
    }

    private fun fadeAnimator(
        view: View,
        from: Float,
        to: Float,
        delayMs: Long,
        durationMs: Long
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, from, to).apply {
            startDelay = delayMs
            duration = durationMs
        }
    }

    private fun finishDayNightTransition(spec: GameplayTransitionSpec) {
        if (!isDayNightTransitionRunning) return
        isDayNightTransitionRunning = false
        dayNightAnimator = null
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        releaseTransitionSound()
        presentedPeriod = spec.period
        renderThemedBackground(spec.period)
        dayNightTransitionOverlay.visibility = View.GONE
        dayNightTransitionOverlay.alpha = 1f
        MusicManager.resumeGamePhaseAfterTransition(this, session)
        resumeGameFlowAfterBlockingUi()
    }

    private fun settleDayNightTransition(resumeMusic: Boolean) {
        if (!isDayNightTransitionRunning) return
        isDayNightTransitionRunning = false
        dayNightAnimator?.removeAllListeners()
        dayNightAnimator?.cancel()
        dayNightAnimator = null
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        releaseTransitionSound()

        val spec = GameplayTableUi.transitionSpec(session)
        lastPresentedTransitionKey = spec.key
        presentedPeriod = spec.period
        if (::dayNightTransitionOverlay.isInitialized) {
            dayNightTransitionOverlay.visibility = View.GONE
            dayNightTransitionOverlay.alpha = 1f
            renderThemedBackground(spec.period)
        }
        if (resumeMusic) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        } else {
            MusicManager.prepareGamePhaseWithoutPlayback(session)
        }
    }

    private fun playTransitionSound(period: GameplayPeriod) {
        releaseTransitionSound()
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val soundOn = sharedPref.getBoolean("sound_on", true)
        val volume = sharedPref.getInt("voice_volume", 80) / 100f
        if (!soundOn || volume <= 0f) return

        val soundRes = if (period == GameplayPeriod.NIGHT) {
            R.raw.transition_night
        } else {
            R.raw.transition_day
        }
        transitionSoundPlayer = MediaPlayer.create(this, soundRes)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { completed ->
                if (transitionSoundPlayer === completed) {
                    transitionSoundPlayer = null
                }
                completed.release()
            }
            start()
        }
    }

    private fun releaseTransitionSound() {
        transitionSoundPlayer?.runCatching {
            stop()
            release()
        }
        transitionSoundPlayer = null
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
                session = GameEngine.chooseDesertorTeam(session, GameRules.TOWN_WINNER)
                renderGame()
            }
            .setNegativeButton("TRAIDORES") { _, _ ->
                desertorDialogOpen = false
                session = GameEngine.chooseDesertorTeam(session, GameRules.TRAITOR_WINNER)
                renderGame()
            }
            .setCancelable(false)
            .show()
    }

    private data class PhaseText(
        val title: String,
        val subtitle: String,
        val actionLabel: String
    )

    private enum class CountdownStage {
        TRANSITION,
        ACTIVE
    }

    companion object {
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val STATE_SESSION = "gameplay_session"
        private const val STATE_CHAT_OPEN = "chat_open"
        private const val STATE_EVENT_LOG_EXPANDED = "event_log_expanded"
        private const val STATE_SELECTED_TARGET = "selected_target"
        private const val STATE_COUNTDOWN_STAGE = "countdown_stage"
        private const val STATE_COUNTDOWN_PHASE_INDEX = "countdown_phase_index"
        private const val STATE_COUNTDOWN_REMAINING_MS = "countdown_remaining_ms"
        private const val STATE_COUNTDOWN_TOTAL_MS = "countdown_total_ms"
        private const val STATE_PRESENTED_PERIOD = "presented_period"
        private const val STATE_TRAITOR_REVEAL_COMPLETED = "traitor_reveal_completed"
        private const val STATE_TRANSITION_KEY = "day_night_transition_key"
        private const val STATE_WINNER_REVEAL_PRESENTED = "winner_reveal_presented"
        private const val SILENCE_REVEAL_GAP_MS = 300L
        private const val TRAITOR_REVEAL_DURATION_MS = 8000L
        private const val TRANSITION_MUSIC_DELAY_MS = 1600L
        private const val WINNER_CARD_STAGGER_MS = 105L
        private const val COUNTDOWN_TICK_MS = 200L

        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
    }
}
