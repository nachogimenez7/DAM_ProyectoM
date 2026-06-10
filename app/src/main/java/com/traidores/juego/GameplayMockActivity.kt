package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator

class GameplayMockActivity : BaseActivity() {

    private var isCardRevealed = false
    private var isChatOpen = false
    private var isEventLogExpanded = true
    private var lastRenderedAnnouncement = ""
    private var lastRenderedPhase: GamePhase? = null
    private var lastSeenChatCount = 0
    private var selectedTarget = ""
    private var desertorDialogOpen = false
    private var isDayNightTransitionRunning = false
    private var isTraitorRevealDismissing = false
    private var isTraitorRevealRunning = false
    private var lastPresentedTransitionKey: String? = null
    private var lastActionAttentionKey: String? = null
    private var presentedPeriod: GameplayPeriod? = null
    private var traitorRevealCompleted = false
    private var knownDeadPlayers = emptySet<String>()
    private lateinit var session: GameSession
    private var unreadChatCount = 0
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val narratorCollapseRunnable = Runnable { collapseNarrator() }
    private val traitorRevealDismissRunnable = Runnable { dismissTraitorReveal() }
    private val transitionMusicRunnable = Runnable {
        if (isDayNightTransitionRunning) {
            MusicManager.resumeGamePhaseAfterTransition(this, session)
        }
    }

    private var chatDialog: Dialog? = null
    private var actionPulseAnimator: AnimatorSet? = null
    private var dialogChatInput: EditText? = null
    private var dialogChatMessages: TextView? = null
    private var dialogSendButton: Button? = null
    private var dayNightAnimator: AnimatorSet? = null
    private var traitorRevealAnimator: AnimatorSet? = null
    private var transitionSoundPlayer: MediaPlayer? = null

    private lateinit var btnAction: Button
    private lateinit var btnRevealCard: Button
    private lateinit var btnToggleChat: ImageButton
    private lateinit var btnToggleEventLog: Button
    private lateinit var chatUnreadBadge: TextView
    private lateinit var currentPlayerHint: TextView
    private lateinit var currentPlayerName: TextView
    private lateinit var eventLogBackground: ImageView
    private lateinit var eventLogContent: FrameLayout
    private lateinit var eventLogContainer: LinearLayout
    private lateinit var eventLogPanel: LinearLayout
    private lateinit var eventLogScroll: ScrollView
    private lateinit var leftPlayersContainer: LinearLayout
    private lateinit var mapBackground: ImageView
    private lateinit var phaseTitle: TextView
    private lateinit var phaseSubtitle: TextView
    private lateinit var rightPlayersContainer: LinearLayout
    private lateinit var roleCard: LinearLayout
    private lateinit var roleImage: ImageView
    private lateinit var roleName: TextView
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
    private lateinit var themeKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        session = readSession() ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        themeKey = themeFromIntentOrSession()
        lastPresentedTransitionKey = savedInstanceState?.getString(STATE_TRANSITION_KEY)
        presentedPeriod = savedInstanceState
            ?.getString(STATE_PRESENTED_PERIOD)
            ?.let { runCatching { GameplayPeriod.valueOf(it) }.getOrNull() }
        traitorRevealCompleted = savedInstanceState?.getBoolean(STATE_TRAITOR_REVEAL_COMPLETED) ?: false
        knownDeadPlayers = session.players.filterNot { it.alive }.map { it.name }.toSet()
        lastSeenChatCount = session.chatHistory.size

        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        btnAction = findViewById(R.id.btnVote)
        btnRevealCard = findViewById(R.id.btnRevealCard)
        btnToggleChat = findViewById(R.id.btnToggleChat)
        btnToggleEventLog = findViewById(R.id.btnToggleEventLog)
        chatUnreadBadge = findViewById(R.id.chatUnreadBadge)
        currentPlayerHint = findViewById(R.id.currentPlayerHint)
        currentPlayerName = findViewById(R.id.currentPlayerName)
        dayNightTransitionOverlay = findViewById(R.id.dayNightTransitionOverlay)
        eventLogBackground = findViewById(R.id.eventLogBackground)
        eventLogContent = findViewById(R.id.eventLogContent)
        eventLogContainer = findViewById(R.id.eventLogContainer)
        eventLogPanel = findViewById(R.id.eventLogPanel)
        eventLogScroll = findViewById(R.id.eventLogScroll)
        leftPlayersContainer = findViewById(R.id.leftPlayersContainer)
        mapBackground = findViewById(R.id.mapBackground)
        phaseTitle = findViewById(R.id.phaseTitle)
        phaseSubtitle = findViewById(R.id.phaseSubtitle)
        rightPlayersContainer = findViewById(R.id.rightPlayersContainer)
        roleCard = findViewById(R.id.roleCard)
        roleImage = findViewById(R.id.roleImage)
        roleName = findViewById(R.id.roleName)
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

        btnSettings.setOnClickListener {
            startActivity(Intent(this, OpcionesActivity::class.java))
        }
        btnAction.setOnClickListener { handleCurrentPhase() }
        btnRevealCard.setOnClickListener { toggleHumanCard() }
        btnToggleChat.setOnClickListener { toggleChatDialog() }
        btnToggleEventLog.setOnClickListener { toggleEventLog() }
        traitorRevealOverlay.setOnClickListener { dismissTraitorReveal() }

        eventLogBackground.setImageResource(logDrawableFor(themeKey))
        renderGame()
    }

    override fun onDestroy() {
        settleDayNightTransition(resumeMusic = false)
        cancelTraitorReveal()
        cancelActionPulse()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(narratorCollapseRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        releaseTransitionSound()
        chatDialog?.dismiss()
        super.onDestroy()
    }

    override fun onPause() {
        settleDayNightTransition(resumeMusic = false)
        cancelTraitorReveal()
        cancelActionPulse()
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(narratorCollapseRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && !isDayNightTransitionRunning) {
            MusicManager.playGamePhase(this, session)
            resumeGameFlowAfterBlockingUi()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TRANSITION_KEY, lastPresentedTransitionKey)
        outState.putString(STATE_PRESENTED_PERIOD, presentedPeriod?.name)
        outState.putBoolean(STATE_TRAITOR_REVEAL_COMPLETED, traitorRevealCompleted)
        super.onSaveInstanceState(outState)
    }

    private fun handleCurrentPhase() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        if (session.winner.isNotBlank()) {
            renderGame()
            return
        }

        if (GameplayTableUi.canHumanMedicSelfProtect(session)) {
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
        val transitionSpec = GameplayTableUi.transitionSpec(session)
        val shouldStartTransition = !isDayNightTransitionRunning &&
            GameplayTableUi.shouldPresentTransition(transitionSpec, lastPresentedTransitionKey)
        if (shouldStartTransition) {
            isDayNightTransitionRunning = true
            lastPresentedTransitionKey = transitionSpec.key
            MusicManager.pauseForTransition()
        } else if (!isDayNightTransitionRunning) {
            MusicManager.playGamePhase(this, session)
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
        renderPlayerColumns()
        renderChatDialog()
        renderChatBadge()
        lastRenderedPhase = session.phase
        lastRenderedAnnouncement = publicMessage
        if (shouldStartTransition) {
            startDayNightTransition(transitionSpec)
        } else if (!isDayNightTransitionRunning) {
            resumeGameFlowAfterBlockingUi()
        }
    }

    private fun renderNarrator(phaseText: PhaseText, publicMessage: String, eventChanged: Boolean) {
        phaseTitle.text = phaseText.title
        phaseSubtitle.text = publicMessage
        if (!eventChanged) return

        autoAdvanceHandler.removeCallbacks(narratorCollapseRunnable)
        phaseSubtitle.visibility = View.VISIBLE
        topStatus.alpha = 0f
        topStatus.scaleX = 0.96f
        topStatus.scaleY = 0.96f
        topStatus.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420L)
            .start()
        autoAdvanceHandler.postDelayed(narratorCollapseRunnable, 5200L)
    }

    private fun collapseNarrator() {
        phaseSubtitle.visibility = View.GONE
        topStatus.animate()
            .alpha(0.92f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(360L)
            .start()
    }

    private fun renderEventLog(publicMessage: String, phaseText: PhaseText) {
        if (!isEventLogExpanded) return
        eventLogContainer.removeAllViews()
        val events = publicEvents(publicMessage, phaseText)
        events.forEach { message ->
            eventLogContainer.addView(createEventRow(message))
        }
        eventLogScroll.post { eventLogScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun toggleEventLog() {
        isEventLogExpanded = !isEventLogExpanded
        renderGame()
    }

    private fun renderEventLogPanel() {
        val params = eventLogPanel.layoutParams as LinearLayout.LayoutParams
        params.height = if (isEventLogExpanded) dp(160) else ViewGroup.LayoutParams.WRAP_CONTENT
        params.weight = 0f
        eventLogPanel.layoutParams = params
        eventLogContent.visibility = if (isEventLogExpanded) View.VISIBLE else View.GONE
        btnToggleEventLog.text = if (isEventLogExpanded) "\u25B2 ocultar" else "\u25BC mostrar"
    }

    private fun publicEvents(publicMessage: String, phaseText: PhaseText): List<String> {
        val events = mutableListOf<String>()
        (session.publicHistory + publicMessage).forEach { message ->
            val clean = message.trim()
            if (clean.isNotBlank() && events.lastOrNull() != clean) {
                events += clean
            }
        }
        if (events.isEmpty()) events += phaseText.subtitle
        return events.takeLast(8)
    }

    private fun createEventRow(message: String): View {
        val type = GameplayTableUi.eventTypeFor(message, session.phase)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.bg_event_log_row)
        row.setPadding(0, dp(7), dp(10), dp(7))

        val colorBar = View(this)
        colorBar.setBackgroundColor(Color.parseColor(type.colorHex))
        row.addView(
            colorBar,
            LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
        )

        val text = TextView(this)
        text.text = message
        text.setTextColor(getColor(R.color.text_primary))
        text.textSize = 11f
        text.maxLines = 3
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
        params.setMargins(0, 0, 0, dp(6))
        row.layoutParams = params
        return row
    }

    private fun renderAdvanceButton() {
        val waitsForTarget = GameEngine.requiresHumanInput(session)
        val canSelfProtect = GameplayTableUi.canHumanMedicSelfProtect(session)
        btnAction.text = when {
            session.winner.isNotBlank() -> "FINAL"
            canSelfProtect -> "SALVARME"
            GameEngine.needsInitialDesertorChoice(session) -> "ELEGIR BANDO"
            GameEngine.canDesertorReconsider(session) -> "REVISAR BANDO"
            session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed -> "REVELARME"
            waitsForTarget -> "ELIGE CARTA"
            session.phase == GamePhase.REPARTO -> "NOCHE"
            session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "payador" &&
                !session.payadorUsed -> "VOTAR SIN USAR"
            else -> phaseText(session.phase).actionLabel
        }
        val specialDecision = GameEngine.needsInitialDesertorChoice(session) ||
            GameEngine.canDesertorReconsider(session) ||
            (session.phase == GamePhase.DIA_DEBATE &&
                GameEngine.humanPlayer(session).role?.key == "alcalde" &&
                !session.alcaldeRevealed)
        val requiresAttention = session.winner.isBlank() &&
            (waitsForTarget || canSelfProtect || specialDecision)
        btnAction.isEnabled = session.winner.isBlank() && (!waitsForTarget || canSelfProtect || specialDecision)
        btnAction.setBackgroundResource(
            if (requiresAttention) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark
        )
        btnAction.setTextColor(
            getColor(if (requiresAttention) R.color.bg_dark else R.color.text_primary)
        )
        btnAction.alpha = when {
            btnAction.isEnabled -> 1f
            requiresAttention -> 0.92f
            else -> 0.55f
        }
        updateActionAttentionPulse(requiresAttention)
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

    private fun renderPlayerColumns() {
        val currentDeadPlayers = session.players.filterNot { it.alive }.map { it.name }.toSet()
        val newlyDeadPlayers = currentDeadPlayers - knownDeadPlayers
        knownDeadPlayers = currentDeadPlayers
        leftPlayersContainer.removeAllViews()
        rightPlayersContainer.removeAllViews()

        val (leftPlayers, rightPlayers) = GameplayTableUi.splitCompanions(session.players)
        val totalPlayers = session.players.size.coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
        leftPlayers.forEach { player ->
            val playerView = createSidePlayerCard(player, totalPlayers)
            leftPlayersContainer.addView(playerView)
            if (player.name in newlyDeadPlayers) {
                animatePlayerDeath(playerView)
            }
        }
        rightPlayers.forEach { player ->
            val playerView = createSidePlayerCard(player, totalPlayers)
            rightPlayersContainer.addView(playerView)
            if (player.name in newlyDeadPlayers) {
                animatePlayerDeath(playerView)
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

    private fun createSidePlayerCard(player: GamePlayer, totalPlayers: Int): View {
        val metrics = GameplayTableUi.companionCardMetrics(totalPlayers)
        val isActive = GameEngine.isActive(player)
        val actionLabel = GameEngine.targetActionLabel(session, player.name)

        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.clipChildren = false
        item.clipToPadding = false
        item.alpha = if (isActive) 1f else 0.4f
        item.minimumWidth = dp(metrics.minCardWidthDp)
        item.setPadding(dp(2), dp(2), dp(2), dp(1))

        val avatar = TextView(this)
        avatar.text = if (isActive) GameplayTableUi.playerInitial(player) else "\u2620"
        avatar.gravity = Gravity.CENTER
        avatar.setBackgroundResource(R.drawable.bg_player_avatar)
        avatar.setTextColor(getColor(R.color.accent_gold))
        avatar.textSize = if (isActive) metrics.nameTextSp else metrics.nameTextSp + 1f
        avatar.setTypeface(null, Typeface.BOLD)
        item.addView(
            avatar,
            LinearLayout.LayoutParams(dp(metrics.avatarSizeDp), dp(metrics.avatarSizeDp))
        )

        val cardFace = FrameLayout(this)
        cardFace.clipChildren = false
        cardFace.clipToPadding = false
        if (actionLabel.isNotBlank()) {
            cardFace.setBackgroundResource(R.drawable.bg_card_actionable)
            cardFace.setPadding(dp(2), dp(2), dp(2), dp(2))
        }
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

        if (actionLabel.isNotBlank()) {
            val action = Button(this)
            action.text = actionLabel
            action.textSize = metrics.actionTextSp
            action.setTextColor(getColor(R.color.bg_dark))
            action.setBackgroundResource(R.drawable.bg_btn_gold)
            action.minHeight = 0
            action.minimumHeight = 0
            action.minWidth = 0
            action.minimumWidth = 0
            action.setPadding(0, 0, 0, 0)
            action.setOnClickListener { performTargetAction(player.name) }
            val actionParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(metrics.actionHeightDp),
                Gravity.BOTTOM
            )
            actionParams.leftMargin = dp(1)
            actionParams.rightMargin = dp(1)
            actionParams.bottomMargin = dp(1)
            cardFace.addView(action, actionParams)
        }

        val cardParams = LinearLayout.LayoutParams(
            dp(metrics.cardWidthDp),
            dp(metrics.cardHeightDp)
        )
        cardParams.topMargin = dp(1)
        item.addView(cardFace, cardParams)

        val name = TextView(this)
        name.text = player.name
        name.gravity = Gravity.CENTER
        name.ellipsize = TextUtils.TruncateAt.END
        name.maxLines = 1
        name.setTextColor(getColor(if (isActive) R.color.text_primary else R.color.text_muted))
        name.textSize = metrics.nameTextSp
        if (!isActive) {
            name.paintFlags = name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        if (player.name == selectedTarget) {
            name.setTextColor(getColor(R.color.accent_gold))
        }
        item.addView(
            name,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(13))
        )

        item.setOnClickListener {
            when {
                actionLabel.isNotBlank() -> performTargetAction(player.name)
                !isActive -> Toast.makeText(this, "${player.name} esta muteado.", Toast.LENGTH_SHORT).show()
                else -> {
                    selectedTarget = player.name
                    currentPlayerHint.text = privateHintText()
                    renderPlayerColumns()
                }
            }
        }
        item.contentDescription = if (isActive) player.name else "${player.name}, muteado"

        val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(metrics.itemHeightDp)
        )
        itemParams.bottomMargin = dp(4)
        item.layoutParams = itemParams
        return item
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
                "Solo el Payador y los dos participantes pueden hablar.",
                "SENALAR"
            )
            GamePhase.VOTACION -> PhaseText("VOTACION", "Toca una carta para emitir tu voto.", "VOTAR")
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

    private fun toggleChatDialog() {
        if (chatDialog?.isShowing == true) {
            chatDialog?.dismiss()
            return
        }
        unreadChatCount = 0
        lastSeenChatCount = session.chatHistory.size
        showChatDialog()
        renderChatBadge()
    }

    private fun showChatDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(createChatDialogContent())
        dialog.setOnDismissListener {
            isChatOpen = false
            btnToggleChat.alpha = 0.9f
            chatDialog = null
            dialogChatInput = null
            dialogChatMessages = null
            dialogSendButton = null
        }
        chatDialog = dialog
        isChatOpen = true
        unreadChatCount = 0
        lastSeenChatCount = session.chatHistory.size
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM or Gravity.END)
        dialog.window?.setLayout(dp(300), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            x = dp(10)
            y = dp(10)
        }
        renderChatDialog()
        renderChatBadge()
    }

    private fun createChatDialogContent(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundResource(R.drawable.bg_chat_panel)
        container.setPadding(dp(12), dp(9), dp(12), dp(10))

        val title = TextView(this)
        title.text = "CHAT"
        title.setTextColor(getColor(R.color.accent_gold))
        title.textSize = 13f
        title.setTypeface(null, Typeface.BOLD)
        container.addView(title)

        val messages = TextView(this)
        messages.setTextColor(getColor(R.color.text_secondary))
        messages.textSize = 11f
        messages.setLineSpacing(dp(1).toFloat(), 1f)
        dialogChatMessages = messages

        val messagesScroll = ScrollView(this)
        val messagesParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(132)
        )
        messagesParams.topMargin = dp(6)
        messagesScroll.addView(messages)
        container.addView(messagesScroll, messagesParams)

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL

        val input = EditText(this)
        input.setBackgroundResource(R.drawable.bg_chat_input)
        input.hint = "Escribir..."
        input.maxLines = 1
        input.setSingleLine(true)
        input.setTextColor(getColor(R.color.text_primary))
        input.setHintTextColor(getColor(R.color.text_muted))
        input.textSize = 11f
        input.setPadding(dp(9), 0, dp(9), 0)
        dialogChatInput = input
        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, dp(32), 1f)
        )

        val send = Button(this)
        send.text = "ENVIAR"
        send.textSize = 8f
        send.setBackgroundResource(R.drawable.bg_btn_gold)
        send.setTextColor(getColor(R.color.bg_dark))
        send.setPadding(0, 0, 0, 0)
        send.setOnClickListener { sendHumanChatMessage() }
        dialogSendButton = send
        val sendParams = LinearLayout.LayoutParams(dp(62), dp(32))
        sendParams.leftMargin = dp(6)
        inputRow.addView(send, sendParams)

        val inputRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        inputRowParams.topMargin = dp(8)
        container.addView(inputRow, inputRowParams)
        return container
    }

    private fun renderChatDialog() {
        btnToggleChat.alpha = if (isChatOpen) 1f else 0.9f
        if (chatDialog?.isShowing != true) return

        val messages = session.chatHistory.filterNot { it.isGod }.takeLast(12)
        dialogChatMessages?.text = if (messages.isEmpty()) {
            "Todavia no hay mensajes."
        } else {
            messages.joinToString("\n") { message -> "${message.speaker}: ${message.message}" }
        }

        val canChat = GameEngine.canHumanChat(session)
        dialogChatInput?.isEnabled = canChat
        dialogSendButton?.isEnabled = canChat
        dialogChatInput?.hint = chatInputHint(canChat)
        dialogSendButton?.alpha = if (canChat) 1f else 0.45f
    }

    private fun updateUnreadChatCount() {
        val currentCount = session.chatHistory.size
        if (isChatOpen || chatDialog?.isShowing == true) {
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
        val input = dialogChatInput ?: return
        val before = session.chatHistory.size
        session = GameEngine.addHumanChatMessage(session, input.text.toString())
        if (session.chatHistory.size > before) {
            input.text.clear()
        } else if (!GameEngine.canHumanChat(session)) {
            Toast.makeText(this, "Estas muteado. Podes mirar el chat, no escribir.", Toast.LENGTH_SHORT).show()
        }
        renderGame()
    }

    private fun chatInputHint(canChat: Boolean): String {
        if (canChat) return "Escribir..."
        if (!GameEngine.isActive(GameEngine.humanPlayer(session))) return "Muteado: solo lectura"
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
        if (isDayNightTransitionRunning || isTraitorRevealRunning || desertorDialogOpen) return
        if (!GameEngine.shouldAutoAdvance(session)) return
        autoAdvanceHandler.postDelayed(autoAdvanceRunnable, GameEngine.autoAdvanceDelayMs(session))
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
            setColor(Color.parseColor("#F0E6D2"))
            setStroke(dp(3), borderColor)
            cornerRadius = dp(8).toFloat()
        }
        if (showRole) {
            roleImage.setImageResource(roleImageFor(role))
            roleName.text = role?.name?.uppercase() ?: "SIN ROL"
        } else {
            roleImage.setImageResource(R.drawable.card_back_traidores)
            roleName.text = "OCULTO"
        }
        btnRevealCard.text = when {
            session.phase == GamePhase.REPARTO -> "ROL"
            showRole -> "OCULTAR"
            else -> "REVELAR"
        }
        btnRevealCard.isEnabled = session.phase != GamePhase.REPARTO
        btnRevealCard.alpha = if (btnRevealCard.isEnabled) 1f else 0.7f
    }

    private fun privateHintText(): String {
        val base = session.privateHint.ifBlank { GameEngine.privateRoleHint(session) }
        val selection = if (selectedTarget.isBlank()) "" else " Objetivo: $selectedTarget."
        return "$base$selection"
    }

    private fun clearSelection() {
        selectedTarget = ""
    }

    private fun targetActionMessage(): String {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> "Toca una carta con MATAR para elegir victima."
            GamePhase.NOCHE_MERCENARIO -> "Toca una carta con SILENCIAR para callar a alguien durante el dia."
            GamePhase.NOCHE_POLICIA -> "Toca una carta con INVESTIGAR para pedir una pista privada."
            GamePhase.NOCHE_MEDICO -> "Toca una carta con SALVAR para proteger a alguien."
            GamePhase.DIA_DEBATE -> "Podes usar tu habilidad o continuar a la votacion."
            GamePhase.CONTRAPUNTO -> "Toca SENALAR sobre uno de los participantes."
            GamePhase.VOTACION -> "Toca una carta con VOTAR para acusar publicamente."
            GamePhase.ALCALDE_DESEMPATE -> "Toca DECIDIR sobre uno de los jugadores empatados."
            else -> "Toca una carta valida."
        }
    }

    private fun resumeGameFlowAfterBlockingUi() {
        if (isDayNightTransitionRunning) return
        if (maybeShowTraitorReveal()) return
        maybeShowDesertorChoice()
        if (!desertorDialogOpen) {
            scheduleAutoAdvanceIfNeeded()
        }
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
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(traitorRevealDismissRunnable)
        chatDialog?.dismiss()
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
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(transitionMusicRunnable)
        chatDialog?.dismiss()

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

    private fun readSession(): GameSession? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION, GameSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession
        }
    }

    private fun maybeShowDesertorChoice() {
        if (isDayNightTransitionRunning) return
        if (GameEngine.needsInitialDesertorChoice(session) || GameEngine.canDesertorReconsider(session)) {
            showDesertorTeamDialog()
        }
    }

    private fun showDesertorTeamDialog() {
        if (desertorDialogOpen || isFinishing) return
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

    companion object {
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val STATE_PRESENTED_PERIOD = "presented_period"
        private const val STATE_TRAITOR_REVEAL_COMPLETED = "traitor_reveal_completed"
        private const val STATE_TRANSITION_KEY = "day_night_transition_key"
        private const val TRAITOR_REVEAL_DURATION_MS = 8000L
        private const val TRANSITION_MUSIC_DELAY_MS = 1600L

        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
    }
}
