package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.app.AlertDialog
import java.util.ArrayDeque

/**
 * The usual table's views and presentation animators, driven exclusively by V3 projections.
 * Closing an overlay changes no phase. No GameEngine, host writes or additional profile reads.
 */
internal class ServerGameTableRenderer(
    private val activity: Activity,
    private val identity: GameSession,
    private val onAction: (ServerGameActionOption, String?, Int) -> Boolean,
    private val onChatVisibility: (Boolean) -> Unit,
    private val onChooseChannel: (String) -> Unit,
    private val onSendChat: (String, String, (Boolean) -> Unit) -> Unit,
    private val onRematch: () -> Unit,
    private val onLeave: () -> Unit,
    private val onRetry: () -> Unit,
    private val onMetrics: () -> Unit,
    private val nowMs: () -> Long?,
    private val onSendReaction: (String, (Boolean) -> Unit) -> Unit = { _, complete -> complete(false) }
) {
    private fun <T : View> view(id: Int): T = activity.findViewById(id)
    private val map = RoleMap.fromSessionKey(identity.mapKey)
    private val theme = GameplayTableUi.themeForMapKey(identity.mapKey)
    private val gold = activity.getColor(R.color.accent_gold)
    private var state: ServerGameSnapshot? = null
    private var options = emptyList<ServerGameActionOption>()
    private var selectedAction: ServerGameActionOption? = null
    private var selectedUid: String? = null
    private var ready = false
    private var busy = false
    private var lastCanRetry = false
    private var lastRematching = false
    private var lastIsCreator = false
    private var lastChatSending = false
    private var lastChannel = "publico"
    private var phaseIndex = -1
    private var rosterKey = ""
    private var ownRoleShown = false
    private var resultShown = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var phaseFresh = false
    private var activeReveal: String? = null
    private var ceremonyTimeout: Runnable? = null
    private var deathAutoContinue: Runnable? = null
    private var heldUid: String? = null
    private var heldPrevious: ServerGamePlayer? = null
    private var holdDeath = false
    private var holdJester = false
    private var expulsionRunning = false
    private var resultPlan: ServerGameTablePresentation.Expulsion? = null
    private var musicKey = ""
    private data class Reveal(val code: String, val playerUid: String?)
    private val reveals = ArrayDeque<Reveal>()
    private val cards = linkedMapOf<String, SidePlayerCardHolder>()
    private lateinit var columns: GameplayPlayerColumns
    private lateinit var chatController: GameplayChatController
    private var presentedSession = identity
    private val channelMessages = linkedMapOf<String, List<ServerChatMessage>>()
    private var displayedAlive = emptyMap<String, Boolean>()
    private var phaseDurationMs = 1L
    private lateinit var announcement: TextView
    private var connectionStatus = ""
    private var eventsExpanded = false
    private lateinit var rolePreview: RolePreviewAnimator
    private lateinit var death: DeathRevealAnimator
    private lateinit var dawn: NoDeathRevealAnimator
    private lateinit var silence: SilenceRevealAnimator
    private lateinit var results: WinnerRevealAnimator
    private lateinit var voteResult: VoteResultAnimator
    private lateinit var jester: JesterVictoryAnimator
    private lateinit var transition: DayNightTransitionAnimator
    private var voteResultPhaseShown = -1
    private var desertorDialog: AlertDialog? = null
    private var oracleDismiss: Runnable? = null
    private var counterpointDismiss: Runnable? = null
    private var tiePhaseShown = -1
    private var countdown: Long? = null
    private var resolving = false
    private var privateResultDismiss: Runnable? = null
    private var bannerDismiss: Runnable? = null
    private var centralDismiss: Runnable? = null
    private lateinit var traitorReveal: TraitorRevealAnimator
    private var traitorRevealRunning = false
    // A final night/AFK death arrives in the same publication as the winner. The usual
    // table reveals that death first; the victory waits for the reveal queue.
    private var resultPending: ServerGameSnapshot? = null
    private var resultPendingAnimate = false
    val input: EditText get() = view(R.id.chatInput)
    val chatOpen: Boolean get() = ::chatController.isInitialized && chatController.isExpandedOpen

    fun bind() {
        activity.setContentView(R.layout.activity_gameplay_mock)
        view<ImageView>(R.id.mapBackground).setImageResource(background(false))
        view<ImageView>(R.id.chatPanelBackground).setImageResource(GameplayThemeResolver.logDrawableFor(theme))
        view<View>(R.id.btnToggleEmotes).visibility = View.VISIBLE
        view<View>(R.id.btnToggleEmotes).setOnClickListener { openReactions() } // V3 reactions need their own validated protocol.
        view<View>(R.id.eventLogPanel).visibility = View.GONE
        view<View>(R.id.chatButtonFrame).visibility = View.GONE
        // Same settings sheet as the usual table: accessibility, report and leave.
        view<View>(R.id.btnSettings).setOnClickListener {
            GameplayEffects.play(activity, GameplayEffect.PANEL)
            AccessibilityOptionsDialog.show(
                activity = activity as androidx.appcompat.app.AppCompatActivity,
                reportLabel = "REPORTAR UN PROBLEMA",
                onReportRequested = { FeedbackDialog.show(activity, includeMatchContext = true, reportProblem = true) },
                exitLabel = if (state?.publicState?.winner != null) "VOLVER AL LOBBY" else "ABANDONAR PARTIDA",
                onExitRequested = onLeave
            )
        }
        if (BuildConfig.DEBUG) view<View>(R.id.btnSettings).setOnLongClickListener { onMetrics(); true }
        view<View>(R.id.roleCard).setOnClickListener { showRole() }
        view<Button>(R.id.btnRevealCard).apply { text = "VER CARTA"; setOnClickListener { showRole() } }
        view<Button>(R.id.btnVote).setOnClickListener { primaryAction() }
        view<View>(R.id.btnRevealMayorSecondary).visibility = View.GONE
        view<TextView>(R.id.phaseCountdown).visibility = View.VISIBLE
        view<View>(R.id.phaseProgressTrack).visibility = View.VISIBLE
        view<View>(R.id.phaseProgressFill).pivotX = 0f
        val frame = view<FrameLayout>(R.id.centerColumn)
        announcement = view(R.id.phaseSubtitle)
        announcement.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        columns = GameplayPlayerColumns(activity, cards, ::bindPlayer)
        rolePreview = RolePreviewAnimator(view(R.id.rolePreviewOverlay), view(R.id.rolePreviewContent),
            view(R.id.rolePreviewMapBackground), view(R.id.rolePreviewImage), view(R.id.rolePreviewName),
            view(R.id.rolePreviewTeam), view(R.id.rolePreviewFunction), view(R.id.rolePreviewAdvice), ::dp)
        view<View>(R.id.btnCloseRolePreview).setOnClickListener {
            rolePreview.dismiss { if (options.any { it.action == "desertor_initial" }) showDesertorChoice() else maybeShowTraitorReveal() }
        }
        view<View>(R.id.btnContinueRolePreview).setOnClickListener {
            val initialDesertor = options.any { it.action == "desertor_initial" }
            if (!initialDesertor) options.firstOrNull { it.action == "role_ack" }?.let { submit(it, null) }
            rolePreview.dismiss { if (initialDesertor) showDesertorChoice() else maybeShowTraitorReveal() }
        }
        traitorReveal = TraitorRevealAnimator(view(R.id.traitorRevealOverlay), view(R.id.traitorRevealContent),
            view(R.id.traitorRevealCards), handler)
        view<View>(R.id.traitorRevealOverlay).setOnClickListener { dismissTraitorReveal() }
        // Same map frame as the usual table (applyTraitorRevealOverlayTheme), with its padding.
        themeRevealPanel(R.id.traitorRevealContent, tie = false)
        view<View>(R.id.traitorRevealContent).apply {
            val frame = Rect()
            if (!activity.getDrawable(when (identity.mapKey) { "grecia" -> R.drawable.ui_frame_event_grecia
                    "medieval" -> R.drawable.ui_frame_event_medieval; else -> R.drawable.ui_frame_event_pampa })!!
                    .getPadding(frame) || frame.left <= 0) frame.set(dp(12), dp(12), dp(12), dp(12))
            setPadding(frame.left + dp(18), frame.top + dp(24), frame.right + dp(18), frame.bottom + dp(22))
        }
        view<View>(R.id.privateFeedbackOverlay).setOnClickListener { dismissPrivateInvestigation() }
        view<Button>(R.id.btnContinueRolePreview).apply { text = "YA LEÍ MI ROL"; layoutParams.height = dp(48) }
        death = DeathRevealAnimator(view(R.id.deathRevealOverlay), view(R.id.deathRevealContent),
            view(R.id.deathRevealCard), view(R.id.deathRevealCardBack), view(R.id.deathRevealCardFront),
            view(R.id.deathRevealBloodLeft), view(R.id.deathRevealBloodRight), view(R.id.deathRevealFlash),
            view(R.id.deathRevealPlayerName), view(R.id.deathRevealRoleName), ::roleImage, ::dp,
            {
                view<View>(R.id.btnContinueDeathReveal).visibility = View.VISIBLE
                deathAutoContinue = Runnable { death.continueAndFinish() }.also { handler.postDelayed(it, 850) }
            }, ::revealFinished)
        view<View>(R.id.btnContinueDeathReveal).setOnClickListener { death.continueAndFinish() }
        dawn = NoDeathRevealAnimator(view(R.id.noDeathRevealOverlay), view(R.id.noDeathRevealContent),
            view(R.id.noDeathSunCore), ::dp, ::revealFinished)
        silence = SilenceRevealAnimator(view(R.id.silenceRevealOverlay), view(R.id.silenceRevealContent),
            view(R.id.silenceRevealCard), view(R.id.silenceRevealCageLeft), view(R.id.silenceRevealCageRight),
            view(R.id.silenceRevealCageDoor), view(R.id.silenceRevealCageLock), view(R.id.silenceRevealPlayerName),
            ::dp, ::revealFinished)
        view<View>(R.id.oracleRevealOverlay).setOnClickListener { hideOracleReveal(); revealFinished() }
        view<FrameLayout>(R.id.oracleRevealPanel).layoutParams.width = dp(
            (activity.resources.configuration.screenWidthDp - 28).coerceAtMost(360))
        view<View>(R.id.payadorRevealOverlay).setOnClickListener { hideCounterpointReveal(); revealFinished() }
        view<LinearLayout>(R.id.payadorRevealPanel).layoutParams.width = dp(
            (activity.resources.configuration.screenWidthDp - 28).coerceAtMost(360))
        themeRevealPanel(R.id.tieVotePanel, tie = true)
        voteResult = VoteResultAnimator(activity, handler,
            view(R.id.voteResultOverlay), view(R.id.voteResultPanel), view(R.id.voteResultCards),
            view(R.id.voteResultTitle), view(R.id.voteResultSubtitle), view(R.id.voteResultNotice),
            view(R.id.btnContinueVoteResult), view(R.id.voteKickBoot), view(R.id.voteKickDust), ::roleImage, ::dp,
            onImpact = {
                GameplayAudioDirector.play(activity, GameSound.EXPULSION)
                holdDeath = false; refreshPublicPresentation()
                state?.publicState?.events?.lastOrNull { it.code == "DAY_EXPULSION" && heldUid in it.players }
                    ?.let { announcement.announceForAccessibility(it.text) }
            })
        themeRevealPanel(R.id.voteResultPanel, tie = false)
        themeRevealPanel(R.id.privateFeedbackPanel, tie = false)
        view<View>(R.id.btnContinueVoteResult).setOnClickListener { if (!expulsionRunning) voteResult.hide() }
        jester = JesterVictoryAnimator(view(R.id.jesterVictoryOverlay), view(R.id.jesterVictoryPanel),
            view(R.id.jesterHornLeft), view(R.id.jesterHornRight), view(R.id.jesterConfettiLayer), view(R.id.jesterVictoryActions))
        view<FrameLayout>(R.id.jesterVictoryPanel).layoutParams.width = dp(
            (activity.resources.configuration.screenWidthDp - 28).coerceAtMost(440))
        view<Button>(R.id.btnContinueJesterVictory).apply { text = "CONTINUAR PARTIDA"; setOnClickListener { finishJester() } }
        view<View>(R.id.btnReturnJesterVictory).visibility = View.GONE
        view<View>(R.id.dayNightTransitionOverlay).apply { isClickable = false; isFocusable = false }
        transition = DayNightTransitionAnimator(handler, view(R.id.dayNightTransitionOverlay),
            view(R.id.transitionFromBackground), view(R.id.transitionToBackground), view(R.id.transitionSun),
            view(R.id.transitionMoon), view(R.id.transitionShade), view(R.id.transitionTitle),
            { background(it == GameplayPeriod.NIGHT) }, {},
            { spec -> view<ImageView>(R.id.mapBackground).setImageResource(background(spec.period == GameplayPeriod.NIGHT)) },
            { playNextReveal() })
        MusicManager.playGameIntro(activity, identity)
        view<View>(R.id.btnTieVoteChat).setOnClickListener { setChatOpen(true) }
        view<View>(R.id.btnTieRevealMayor).setOnClickListener {
            options.firstOrNull { it.action == "revelar_alcalde" }?.let(::choose)
        }
        view<View>(R.id.btnConfirmTieVote).setOnClickListener {
            if (lastCanRetry) onRetry()
            else selectedAction?.let { option -> selectedUid?.let { submit(option, it) } }
        }
        results = WinnerRevealAnimator(view(R.id.winnerRevealOverlay), view(R.id.winnerRevealPanel),
            view(R.id.winnerRevealTitle), view(R.id.winnerRevealPersonalResult), view(R.id.winnerRevealShine), ::dp)
        view<Button>(R.id.btnWinnerReturnLobby).setOnClickListener { onRematch() }
        view<Button>(R.id.btnWinnerChronicle).apply {
            text = "ANUNCIOS DE LA RONDA"; setOnClickListener { showEvents() }
        }
        showLoadingVeil()
        frame.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                rosterKey = ""; displayState()?.let { renderRoster(it) }
            }
        }
    }

    // Covers the half-built table until the first coherent projection is drawn, so the
    // player never sees placeholder cards rearranging themselves.
    private var loadingVeil: View? = null
    private fun showLoadingVeil() {
        if (loadingVeil != null) return
        val root = view<ViewGroup>(R.id.gameplayRoot)
        // Only the map background (what is behind the table anyway): the table then fades in.
        // A text appears only if the first projection is slow, never as a routine flash.
        val waiting = label("Preparando la mesa…", 18f, gold).apply { gravity = Gravity.CENTER; alpha = 0f }
        loadingVeil = FrameLayout(activity).apply {
            isClickable = true; isFocusable = true; elevation = dp(40).toFloat()
            addView(ImageView(activity).apply { setImageResource(background(false)); scaleType = ImageView.ScaleType.CENTER_CROP },
                FrameLayout.LayoutParams(-1, -1))
            addView(waiting, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        root.addView(loadingVeil, ViewGroup.LayoutParams(-1, -1))
        waiting.postDelayed({ if (waiting.parent != null) waiting.animate().alpha(1f).setDuration(250L).start() }, 1500L)
    }
    private fun hideLoadingVeil() {
        val veil = loadingVeil ?: return
        loadingVeil = null
        veil.animate().alpha(0f).setDuration(260L).withEndAction { (veil.parent as? ViewGroup)?.removeView(veil) }.start()
    }

    fun render(value: ServerGameSnapshot, fresh: ServerGamePresentationUpdate) {
        val previous = state
        val p = value.publicState
        if (BuildConfig.DEBUG && (fresh.phaseChanged || fresh.events.isNotEmpty())) OnlineDebugLog.i(
            "v3_render phase=${p.phase} idx=${p.phaseIndex} round=${p.round} winner=${p.winner} fresh=${fresh.events.joinToString(",") { it.code }}")
        phaseFresh = fresh.phaseChanged
        if (phaseIndex != p.phaseIndex) {
            val carry = ServerGameTablePresentation.canCarryCeremonies(p, nowMs())
            if (!carry) stopReveals() else closePhaseWindows()
            phaseIndex = p.phaseIndex; selectedAction = null; selectedUid = null
            countdown = null; resolving = false
            if (chatOpen && p.phase != ServerGamePhase.DIA_DEBATE && !ServerGameChat.canSend("publico", value))
                setChatOpen(false)
        }
        state = value
        channelMessages.keys.retainAll(ServerGameChat.channels(value).toSet())
        val enteringResult = p.phase == ServerGamePhase.RESULTADO && voteResultPhaseShown != p.phaseIndex
        if (enteringResult) {
            resultPlan = ServerGameTablePresentation.expulsion(p, fresh.events, nowMs())
            val plan = resultPlan
            heldUid = plan?.player?.uid
            heldPrevious = previous?.publicState?.players?.firstOrNull { it.uid == heldUid }
            holdDeath = plan != null && plan.mode != ServerGameTablePresentation.ExpulsionMode.STATIC
            holdJester = holdDeath && plan?.jester == true
            expulsionRunning = holdDeath
        }
        val adapted = ServerGameSessionAdapter.adapt(identity, value)
        val phaseText = GameplayPhasePresentation.phaseText(adapted.phase, p.round, p.winner != null,
            ServerGameTablePresentation.waitingHint(value), false)
        view<TextView>(R.id.phaseTitle).text = if (p.phase == ServerGamePhase.DESERTOR_RECONSIDERACION) "REVISIÓN DE BANDO" else phaseText.title
        if (fresh.phaseChanged || phaseDurationMs <= 1) phaseDurationMs = ((p.deadlineMs ?: 0) - (nowMs() ?: 0)).coerceAtLeast(1)
        view<ImageView>(R.id.mapBackground).setImageResource(background(p.phase == ServerGamePhase.NOCHE))
        refreshPublicPresentation()
        val visible = displayState() ?: value
        val own = ServerGameTablePresentation.player(visible, visible.human, map)
        view<TextView>(R.id.currentPlayerName).text = visible.human.name
        view<TextView>(R.id.roleName).text = "CARTA OCULTA"
        view<ImageView>(R.id.roleImage).setImageResource(R.drawable.card_back_traidores)
        view<GameplayAvatarView>(R.id.currentPlayerPhoto).bind(identity, own, fallbackInitial = own.initial, textSizeSp = 12f)
        playPhaseMusic(p)
        if (fresh.phaseChanged) ServerGameTablePresentation.phaseSound(p)?.let { GameplayAudioDirector.play(activity, it) }
        if (p.winner != null) {
            dismissPrivateInvestigation(); dismissActionBanner()
            setChatOpen(false)
            // Same order as the usual table: the night/AFK death that ended the match is
            // revealed first, then the victory. Seen events (reconnection) never replay.
            val finalReveals = ServerGameTablePresentation.finalRevealEvents(p, fresh.events)
            if (!resultShown && (finalReveals.isNotEmpty() || resultPending != null)) {
                reveals.addAll(finalReveals.map { Reveal(it.code, it.players.firstOrNull()) })
                resultPending = value; resultPendingAnimate = resultPendingAnimate || fresh.phaseChanged
                playNextReveal()
            } else showResult(value, fresh.phaseChanged)
        } else {
            view<View>(R.id.winnerRevealOverlay).visibility = View.GONE
            if (!ownRoleShown && p.phase == ServerGamePhase.REPARTO) { ownRoleShown = true; showRole() }
            reveals.addAll(ServerGameTablePresentation.revealEvents(p, fresh.events).map { Reveal(it.code, it.players.firstOrNull()) })
            reveals.addAll(ServerGameTablePresentation.silencedAtDawn(p, fresh.phaseChanged).map { Reveal("SILENCED", it.uid) })
            ServerGameTablePresentation.oracleReveal(p, fresh.phaseChanged)?.let { reveals.add(Reveal("ORACLE", it.uid)) }
            if (ServerGameTablePresentation.counterpointReveal(p, fresh.phaseChanged).size == 2) reveals.add(Reveal("COUNTERPOINT", null))
            // Dawn has no actions and the debate after it is long: its transition always plays
            // (Cloud publication can leave < 2.5 s of AMANECER). Night keeps room for its powers.
            val transitionRoom = if (p.phase == ServerGamePhase.AMANECER) 0L else 2500L
            if (fresh.phaseChanged && p.phase in setOf(ServerGamePhase.NOCHE, ServerGamePhase.AMANECER) &&
                activeReveal == null && !expulsionRunning && ((p.deadlineMs ?: 0) - (nowMs() ?: Long.MAX_VALUE)) >= transitionRoom) {
                val night = p.phase == ServerGamePhase.NOCHE
                transition.start(GameplayTransitionSpec(if (night) GameplayPeriod.NIGHT else GameplayPeriod.DAY,
                    (if (night) "NOCHE " else "AMANECER ") + p.round, p.matchId + ":" + p.phaseIndex),
                    if (night) GameplayPeriod.DAY else GameplayPeriod.NIGHT,
                    // Same length as the usual table (startDayNightTransition): the room's transition seconds.
                    (identity.timingConfig.normalized().transitionSeconds * 1000L).coerceIn(1000L, 8000L))
            }
            // A traitor who never pressed "YA LEÍ MI ROL" still meets the allies before acting.
            if (fresh.phaseChanged && p.phase == ServerGamePhase.NOCHE && p.round == 1) maybeShowTraitorReveal()
            if (p.phase == ServerGamePhase.RECUENTO_VOTOS && voteResultPhaseShown != p.phaseIndex) {
                voteResultPhaseShown = p.phaseIndex
                val players = p.players.map { ServerGameTablePresentation.player(value, it, map, publicOnly = true) }
                if (p.ballots.isNotEmpty()) {
                    // Room shows votes (usual online default): the usual animated recount, one seal per voter.
                    fun byOrder(order: Int) = p.players.first { it.order == order }.name
                    fun byUid(uid: String?) = p.players.firstOrNull { it.uid == uid }?.name.orEmpty()
                    voteResult.show(identity.copy(players = players, round = p.round,
                        votes = p.ballots.associate { (voter, target) -> byOrder(voter) to byOrder(target) },
                        showIndividualVotes = true, voteRound = p.voteRound,
                        alcaldeRevealed = p.mayorUid != null, alcaldeCorruption = p.mayorCorruption,
                        contrapuntoSuspicion = byUid(p.counterpointPointedUid),
                        tieVoteCandidates = p.tieCandidates.map(::byUid), dayEliminationTarget = byUid(p.dayEliminationUid)))
                } else {
                    val totals = p.voteTotals.mapKeys { (order, _) -> p.players.first { it.order == order }.name }
                    voteResult.showServerTotals(identity.copy(players = players, showIndividualVotes = false), totals,
                        if (p.voteRound == 2) "RECUENTO FINAL" else "RECUENTO DE VOTOS", GameplayTableUi.centralPhaseMessage(adapted, "El pueblo cuenta los votos recibidos."))
                }
            } else if (enteringResult) {
                voteResultPhaseShown = p.phaseIndex
                showDayResult(value)
            }
            playNextReveal()
        }
        displayState()?.publicState?.events?.lastOrNull { event -> fresh.events.any { it.seq == event.seq } }
            ?.let { announcement.announceForAccessibility(it.text) }
        showPrivateInvestigation(value)
        if (p.winner == null) fresh.events.lastOrNull { it.code == "MAYOR_REVEALED" || it.code == "AFK_EXPULSION" }
            ?.let(::showCentralEvent)
        hideLoadingVeil()
    }

    /** Usual table's central banner: only "Alcalde revelado" and inactivity expulsions open it. */
    private fun showCentralEvent(event: ServerGameEvent) {
        val danger = event.code == "AFK_EXPULSION"
        val color = if (danger) "#A83232" else "#D4A24E"
        view<TextView>(R.id.centralPublicEventLabel).text = if (danger) "INACTIVIDAD" else "AUTORIDAD REVELADA"
        view<TextView>(R.id.centralPublicEventIcon).apply { text = if (danger) "!" else "A"; setTextColor(Color.parseColor(color)) }
        view<TextView>(R.id.centralPublicEventTitle).text = if (danger) "FUERA DEL PUEBLO" else "ALCALDE REVELADO"
        view<TextView>(R.id.centralPublicEventMessage).text = if (danger)
            event.text.takeIf { it.length <= 74 } ?: "Un jugador fue expulsado por ausentarse demasiado."
            else "El pueblo ya sabe quien tiene la ultima palabra."
        view<View>(R.id.centralPublicEventTone).setBackgroundColor(Color.parseColor(color))
        val banner = view<View>(R.id.centralPublicEventBanner)
        centralDismiss?.let(handler::removeCallbacks)
        banner.animate().cancel()
        banner.visibility = View.VISIBLE; banner.alpha = 0f; banner.translationY = dp(18).toFloat(); banner.scaleX = .9f; banner.scaleY = .9f
        banner.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(430L).start()
        centralDismiss = Runnable { hideCentralEvent() }.also { handler.postDelayed(it, 5200L) }
    }

    private fun hideCentralEvent() {
        centralDismiss?.let(handler::removeCallbacks); centralDismiss = null
        view<View>(R.id.centralPublicEventBanner).apply { animate().cancel(); visibility = View.GONE; alpha = 1f; translationY = 0f; scaleX = 1f; scaleY = 1f }
    }

    private fun dismissPrivateInvestigation() {
        privateResultDismiss?.let(handler::removeCallbacks); privateResultDismiss = null
        view<View>(R.id.privateFeedbackOverlay).animate().cancel()
        view<View>(R.id.privateFeedbackOverlay).visibility = View.GONE
    }

    private fun showPrivateInvestigation(value: ServerGameSnapshot) {
        val message = ServerGameTablePresentation.investigationHint(value) ?: return
        if (value.publicState.winner != null) return
        val prefs = activity.getSharedPreferences("v3_private_results", android.content.Context.MODE_PRIVATE)
        val key = value.ownUid + ":" + value.publicState.matchId
        val count = value.privateState.investigations.size
        if (prefs.getInt(key, 0) >= count) return
        prefs.edit().putInt(key, count).apply()
        dismissPrivateInvestigation()
        setChatOpen(false)
        view<TextView>(R.id.privateFeedbackTitle).text = "RESPUESTA PRIVADA"
        view<TextView>(R.id.privateFeedbackMessage).text = message
        view<Button>(R.id.btnContinuePrivateFeedback).apply {
            isEnabled = true; visibility = View.VISIBLE; setOnClickListener { dismissPrivateInvestigation() }
        }
        view<View>(R.id.privateFeedbackPanel).apply { alpha = 1f; scaleX = 1f; scaleY = 1f; translationY = 0f }
        view<View>(R.id.privateFeedbackOverlay).apply {
            visibility = View.VISIBLE; alpha = 0f; animate().alpha(1f).setDuration(300).start()
            announceForAccessibility(message)
        }
        GameplayEffects.play(activity, GameplayEffect.CONFIRM)
        privateResultDismiss = Runnable { dismissPrivateInvestigation() }.also { handler.postDelayed(it, 8000) }
    }

    private fun displayState(): ServerGameSnapshot? = state?.let {
        ServerGameTablePresentation.visibleDuringExpulsion(it, heldUid, holdDeath, holdJester, heldPrevious)
    }

    private fun refreshPublicPresentation() {
        val s = displayState() ?: return
        presentedSession = ServerGameSessionAdapter.adapt(identity, s, channelMessages)
        renderRoster(s); renderAnnouncement()
        // Recovery can enter with an empty lobby identity. Initialize only from an
        // actual authorized projection, never from placeholder/stale identity players.
        if (!::chatController.isInitialized) bindSharedChat() else chatController.onSessionUpdated()
        view<TextView>(R.id.currentPlayerStatus).apply {
            text = ServerGameTablePresentation.condition(s.human)
            visibility = if (s.human.alive && !s.human.muted) View.GONE else View.VISIBLE
        }
    }

    private fun playPhaseMusic(p: ServerGamePublic) {
        val key = if (p.winner != null) "winner:" + p.winner else if (p.phase == ServerGamePhase.NOCHE) "night" else "day"
        if (key == musicKey) return
        musicKey = key
        if (p.winner != null) {
            if (p.winner == "Cancelada") MusicManager.playMenuMusic(activity)
            else MusicManager.playVictoryMusic(activity, p.winner)
        } else MusicManager.playGamePhase(activity, identity.copy(winner = "",
            phase = if (p.phase == ServerGamePhase.NOCHE) GamePhase.NOCHE_ASESINO else GamePhase.DIA_DEBATE))
    }

    fun controls(next: List<ServerGameActionOption>, isReady: Boolean, pending: Boolean, canRetry: Boolean,
                 rematching: Boolean, isCreator: Boolean, chatSending: Boolean, channel: String) {
        ready = isReady; busy = pending; options = next
        lastCanRetry = canRetry; lastRematching = rematching; lastIsCreator = isCreator
        lastChatSending = chatSending; lastChannel = channel
        if (selectedAction !in options) { selectedAction = null; selectedUid = null }
        // As on the usual table, a permitted target can be selected directly on
        // its card. No extra "choose action" step for the ordinary vote/power.
        if (selectedAction == null) selectedAction = next.firstOrNull { it.action == "votar" }
            ?: next.filter { it.targets.isNotEmpty() }.singleOrNull()
            ?: next.firstOrNull { it.action == "invitar_muerto" }
        if (selectedUid !in selectedAction?.targets.orEmpty()) selectedUid = null
        val value = displayState()
        // As on the usual table, a vote is cast by touching the card. The confirmed
        // projection keeps that card marked; touching another changes the vote.
        val directVote = selectedAction?.action == "votar"
        val confirmedVote = value?.privateState?.confirmed?.firstOrNull { it.action == "votar" }?.targetUid
        if (directVote && selectedUid == null && confirmedVote in selectedAction!!.targets) selectedUid = confirmedVote
        val selfProtect = selectedAction?.action == "salvar" && selectedUid == null && value?.ownUid in selectedAction!!.targets
        val keepPower = selectedAction?.action == "invitar_muerto" && selectedUid == null && next.any { it.action == "guardar_poder" }
        val deserterChoice = next.isNotEmpty() && next.all { it.action.startsWith("desertor_") }
        val targetName = selectedUid?.let { id -> value?.publicState?.players?.firstOrNull { it.uid == id }?.name }.orEmpty()
        val hint = when {
            !ready -> "Esperando sincronización…"
            busy -> if (canRetry) "Acción pendiente. Podés reintentar." else "Enviando…"
            directVote && confirmedVote != null && selectedUid == confirmedVote -> "Votaste a: $targetName. Podés cambiar hasta el cierre."
            selectedAction != null -> selectedUid?.let { "Objetivo: $targetName" } ?: contextualHint()
            // Debate: the usual table keeps its debate hint; "votar antes" is not a pending action.
            debateMode(value) -> contextualHint()
            next.isNotEmpty() -> value?.privateState?.deserterTeam?.let { "Tu bando: " + it } ?: "Elegí tu acción."
            value != null && ServerGameTablePresentation.investigationHint(value) != null -> ServerGameTablePresentation.investigationHint(value)!!
            value?.privateState?.confirmed?.isNotEmpty() == true -> "Acción registrada."
            value?.human?.muted == true -> "Estás silenciado."
            value?.human?.alive == false -> "Seguís como espectador."
            value != null -> ServerGameTablePresentation.waitingHint(value)
            else -> "Conectando con la partida…"
        }
        view<TextView>(R.id.currentPlayerHint).text = hint
        renderAnnouncement()
        view<Button>(R.id.btnVote).apply {
            val label = when {
                directVote && busy -> "ENVIANDO VOTO..."
                busy -> "ENVIANDO…"
                directVote && confirmedVote != null -> "✓ VOTO REGISTRADO"
                directVote -> "SELECCIONÁ A UN JUGADOR"
                selectedUid != null -> ServerGameTablePresentation.targetActionLabel(selectedAction!!.action, targetName)
                selfProtect -> "SALVARME"
                keepPower -> "GUARDAR PODER"
                selectedAction != null -> "ELEGIR OBJETIVO"
                deserterChoice -> if (next.first().action == "desertor_initial") "ELEGIR BANDO" else "REVISAR BANDO"
                next.size == 1 -> next.single().label
                next.size > 1 -> "ACCIONES"
                else -> "ESPERAR"
            }
            text = label
            isEnabled = !directVote && ready && !busy && next.isNotEmpty() &&
                (selectedAction == null || selectedUid != null || selfProtect || keepPower)
            // The usual table colors the button by the visible action ("MATAR A X" is red,
            // "SALVAR A X" green…), emphasized only once there is something to confirm.
            val attention = isEnabled && (selectedUid != null || selfProtect || keepPower || deserterChoice)
            if (directVote && confirmedVote != null && !busy) {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#234A2D")); setStroke(dp(2), Color.parseColor("#78C98A"))
                    cornerRadius = dp(6).toFloat()
                }
                setTextColor(Color.parseColor("#E9F8EC"))
                alpha = 1f
            } else {
                columns.bindPrimaryAction(this, label, attention)
                alpha = if (isEnabled) 1f else .55f
            }
            contentDescription = label
        }
        view<FrameLayout>(R.id.roleCard).isEnabled = selectedAction == null || value?.ownUid in selectedAction!!.targets
        // The own card is also a valid target for self-protection.
        view<FrameLayout>(R.id.roleCard).setOnClickListener {
            val s = state ?: return@setOnClickListener
            if (selectedAction != null) selectTarget(s.ownUid) else showRole()
        }
        view<Button>(R.id.btnContinueRolePreview).apply {
            val ack = next.firstOrNull { it.action == "role_ack" }
            val initialDesertor = next.any { it.action == "desertor_initial" }
            visibility = if (ack != null || initialDesertor) View.VISIBLE else View.GONE
            text = if (initialDesertor) "ELEGIR MI BANDO" else "YA LEÍ MI ROL"
            isEnabled = ready && !busy && (ack != null || initialDesertor)
        }
        if (!ready || next.none { it.action.startsWith("desertor_") }) {
            desertorDialog?.dismiss(); desertorDialog = null
        }
        val secondary = next.firstOrNull { it.action == "revelar_alcalde" && selectedAction != null }
        view<Button>(R.id.btnRevealMayorSecondary).apply {
            visibility = if (canRetry || !ready || secondary != null) View.VISIBLE else View.GONE
            text = if (secondary != null) "REVELARME · VOTO DOBLE" else "REINTENTAR"
            isEnabled = !busy || canRetry
            setOnClickListener { if (secondary != null) choose(secondary) else onRetry() }
        }
        renderDebateButtons()
        displayState()?.let(::renderRoster)
        view<Button>(R.id.btnWinnerReturnLobby).apply {
            text = if (isCreator) { if (rematching) "PREPARANDO…" else "PREPARAR REVANCHA" } else "SALIR DE LA SALA"
            isEnabled = ready
            setOnClickListener { if (isCreator) onRematch() else onLeave() }
        }
        if (::chatController.isInitialized) {
            if (ready) chatController.onRealtimeAccessReady() else chatController.onRealtimeAccessUnavailable()
            chatController.refreshUi()
        }
        refreshReactions()
        renderTieWindow()
    }

    private fun renderTieWindow() {
        val s = state ?: return
        val p = s.publicState
        val overlay = view<FrameLayout>(R.id.tieVoteOverlay)
        if (!ServerGameTablePresentation.tieWindow(p) || chatOpen) { overlay.visibility = View.GONE; return }
        val panel = view<LinearLayout>(R.id.tieVotePanel)
        overlay.visibility = View.VISIBLE
        if (tiePhaseShown != p.phaseIndex) {
            tiePhaseShown = p.phaseIndex
            if (phaseFresh) GameplayAudioDirector.play(activity, GameSound.TIE_BREAK)
            EssentialViewAnimation.reveal(overlay, 220L, fromScale = 1f)
            EssentialViewAnimation.reveal(panel, 350L, fromScale = 0.92f)
            view<ScrollView>(R.id.tieVoteCardsScroll).scrollTo(0, 0)
        }
        val mayor = p.phase == ServerGamePhase.ALCALDE_DESEMPATE
        ((panel.getChildAt(0) as LinearLayout).getChildAt(0) as TextView).text = if (mayor) "ÚLTIMA PALABRA" else "DESEMPATE"
        view<TextView>(R.id.tieVoteCountdown).text = countdown?.toString().orEmpty()
        // Never describe the Mayor's private health, silence or ability to decide.
        view<TextView>(R.id.tieVoteSubtitle).text = if (mayor) "El Alcalde puede decidir entre las cartas empatadas." else "Votá nuevamente entre las cartas empatadas."
        view<TextView>(R.id.tieVoteNotice).text = when {
            resolving -> "Resolviendo…"
            !ready -> "Esperando sincronización…"
            busy -> "Enviando…"
            lastCanRetry -> "Tu acción sigue pendiente. Podés reintentar."
            mayor -> "SI NO HAY UNA DECISIÓN, NADIE SERÁ EXPULSADO."
            else -> "Tu voto es secreto hasta el cierre de la votación."
        }
        val reveal = options.firstOrNull { it.action == "revelar_alcalde" }
        view<Button>(R.id.btnTieRevealMayor).apply {
            visibility = if (reveal != null) View.VISIBLE else View.GONE
            isEnabled = ready && !busy && !resolving
        }
        val tieVoted = s.privateState.confirmed.firstOrNull { it.action == "votar" }?.targetUid
        val directTieVote = selectedAction?.action == "votar"
        view<Button>(R.id.btnConfirmTieVote).apply {
            text = when { lastCanRetry -> "REINTENTAR"; busy -> if (directTieVote) "ENVIANDO VOTO..." else "ENVIANDO…"
                directTieVote && tieVoted != null -> "✓ VOTO REGISTRADO"
                directTieVote -> "TOCÁ UNA CARTA"
                selectedUid != null -> ServerGameTablePresentation.targetActionLabel(selectedAction!!.action,
                    s.publicState.players.firstOrNull { it.uid == selectedUid }?.name.orEmpty())
                selectedAction != null -> "ELEGIR CARTA"; else -> ServerGameTablePresentation.waitingButton(s) }
            // Votes are cast by touching the card; only the Mayor's decision needs this button.
            isEnabled = ready && !resolving && (lastCanRetry || !busy && !directTieVote && selectedUid != null && selectedAction in options)
        }
        val candidates = ServerGameTablePresentation.tiePlayers(p)
        val availableWidth = (activity.resources.displayMetrics.widthPixels - dp(64) - panel.paddingLeft - panel.paddingRight) / activity.resources.displayMetrics.density
        val metrics = GameplayTableUi.tieVoteGridMetrics(candidates.size, 2, availableWidth.toInt())
        val grid = view<GridLayout>(R.id.tieVoteCards)
        grid.removeAllViews(); grid.columnCount = metrics.columns; grid.rowCount = metrics.rows
        view<ScrollView>(R.id.tieVoteCardsScroll).layoutParams.height = dp((metrics.rows * (metrics.cardHeightDp + 8)).coerceAtMost(264))
        candidates.forEachIndexed { index, player ->
            val allowed = ready && !busy && !resolving && player.uid in selectedAction?.targets.orEmpty()
            val selected = player.uid == selectedUid
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(7), dp(6), dp(5))
                background = panel(if (selected) gold else activity.getColor(R.color.btn_dark_border))
                isEnabled = allowed; isFocusable = allowed; alpha = if (allowed || selectedAction == null) 1f else 0.55f
                contentDescription = player.name + if (selected) ", seleccionado" else ", carta empatada"
                setOnClickListener { selectTarget(player.uid) }
            }
            val face = FrameLayout(activity)
            face.addView(ImageView(activity).apply { setImageResource(R.drawable.card_back_traidores); scaleType = ImageView.ScaleType.FIT_CENTER }, FrameLayout.LayoutParams(dp(52), dp(70), Gravity.CENTER))
            val avatar = GameplayAvatarView(activity).apply {
                val model = ServerGameTablePresentation.player(s, player, map, publicOnly = true)
                bind(identity, model, model.initial, 13f)
            }
            face.addView(avatar, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
            card.addView(face, LinearLayout.LayoutParams(dp(58), dp(74)))
            card.addView(label(player.name, 13f).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, dp(22)))
            val status = when { selected -> "SELECCIONADO ✓"; player.uid == s.ownUid && !allowed -> "TU CARTA"; allowed -> if (mayor) "EXPULSAR" else "VOTAR"; else -> "" }
            card.addView(label(status, 9f, gold).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(18)))
            grid.addView(card, GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(index / metrics.columns); columnSpec = GridLayout.spec(index % metrics.columns)
                width = dp(metrics.cardWidthDp); height = dp(metrics.cardHeightDp); setMargins(dp(5), dp(4), dp(5), dp(4))
            })
        }
    }

    private fun themeRevealPanel(id: Int, tie: Boolean) {
        val (frameId, color) = when (identity.mapKey) {
            "grecia" -> R.drawable.ui_frame_event_grecia to "#EB080A10"
            "medieval" -> R.drawable.ui_frame_event_medieval to "#F0060708"
            else -> R.drawable.ui_frame_event_pampa to "#EC120C07"
        }
        val frame = activity.getDrawable(frameId)!!.mutate()
        val padding = Rect()
        if (!frame.getPadding(padding) || padding.left <= 0) padding.set(dp(12), dp(12), dp(12), dp(12))
        val inner = activity.getDrawable(R.drawable.bg_reveal_inner_panel)!!.mutate().apply { setTint(Color.parseColor(color)) }
        view<View>(id).apply {
            background = LayerDrawable(arrayOf(InsetDrawable(inner, (padding.left - dp(10)).coerceAtLeast(0),
                (padding.top - dp(10)).coerceAtLeast(0), (padding.right - dp(10)).coerceAtLeast(0), (padding.bottom - dp(10)).coerceAtLeast(0)), frame))
            val horizontal = dp(if (tie) 26 else 6)
            setPadding(padding.left + horizontal, padding.top + dp(if (identity.mapKey == "grecia") 18 else if (tie) 14 else 4),
                padding.right + horizontal, padding.bottom + dp(12))
        }
    }

    private fun debateMode(value: ServerGameSnapshot?) = value != null &&
        value.publicState.phase == ServerGamePhase.DIA_DEBATE && value.publicState.winner == null && value.human.alive

    /**
     * Usual table's debate controls (renderReadyToVoteButton + renderDebateAbilityButton): the main
     * button asks to vote early; the secondary one carries the Mayor, Payador or Desertor ability.
     */
    private fun renderDebateButtons() {
        val value = displayState() ?: return
        if (!debateMode(value)) return
        val readyInfo = value.publicState.readyToVote
        val ownReady = value.privateState.confirmed.any { it.action == "listo_votar" }
        val toggle = options.firstOrNull { it.action == "listo_votar" || it.action == "cancelar_listo" }
        val unlockMs = (readyInfo?.fromEpochMs ?: Long.MAX_VALUE) - (nowMs() ?: 0L)
        val progress = readyInfo?.let { " · ${it.ready}/${it.total}" }.orEmpty()
        view<Button>(R.id.btnVote).apply {
            text = when {
                busy -> "ENVIANDO…"
                readyInfo == null -> "ESPERAR"
                unlockMs > 0 -> "VOTAR ANTES EN ${(unlockMs + 999) / 1000}$progress"
                ownReady -> "CANCELAR$progress"
                else -> "LISTOS PARA VOTAR$progress"
            }
            isEnabled = ready && !busy && toggle != null
            background = GradientDrawable().apply {
                setColor(Color.parseColor(when { !isEnabled -> "#2A2318"; ownReady -> "#2A3F2B"; else -> "#5A4017" }))
                setStroke(dp(if (isEnabled) 2 else 1), Color.parseColor(when { !isEnabled -> "#6B4F2A"; ownReady -> "#78C98A"; else -> "#F2BE62" }))
                cornerRadius = dp(10).toFloat()
            }
            setTextColor(Color.parseColor(if (ownReady) "#E9F8EC" else "#FFF2D4"))
            alpha = if (isEnabled) 1f else .58f
            contentDescription = text
        }
        val reveal = options.firstOrNull { it.action == "revelar_alcalde" }
        val counterpoint = options.firstOrNull { it.action == "contrapunto" }
        val rethink = options.firstOrNull { it.action == "desertor_rethink" }
        view<Button>(R.id.btnRevealMayorSecondary).apply {
            val accent = activity.getColor(R.color.accent_red)
            visibility = if (lastCanRetry || reveal != null || counterpoint != null || rethink != null) View.VISIBLE else View.GONE
            background = GradientDrawable().apply {
                setColor(activity.getColor(R.color.btn_dark)); setStroke(dp(1), accent); cornerRadius = dp(6).toFloat()
            }
            setTextColor(accent)
            when {
                lastCanRetry -> { text = "REINTENTAR"; isEnabled = true; setOnClickListener { onRetry() } }
                reveal != null -> { text = "REVELARME - VOTO DOBLE"; isEnabled = ready && !busy; setOnClickListener { choose(reveal) } }
                counterpoint != null -> {
                    val target = selectedUid?.takeIf { selectedAction == counterpoint }
                    text = target?.let { id -> ServerGameTablePresentation.targetActionLabel("contrapunto",
                        value.publicState.players.firstOrNull { it.uid == id }?.name.orEmpty()) } ?: "ELEGÍ UNA CARTA PARA CONTRAPUNTO"
                    isEnabled = ready && !busy && target != null
                    setOnClickListener { target?.let { submit(counterpoint, it) } }
                }
                rethink != null -> { text = "REVISAR BANDO"; isEnabled = ready && !busy; setOnClickListener { showDesertorChoice() } }
            }
            alpha = if (isEnabled) .96f else .52f
        }
    }

    private fun primaryAction() {
        if (!ready || busy) return
        if (debateMode(displayState())) {
            options.firstOrNull { it.action == "listo_votar" || it.action == "cancelar_listo" }?.let { submit(it, null) }
            return
        }
        if (options.isNotEmpty() && options.all { it.action.startsWith("desertor_") }) {
            showDesertorChoice(); return
        }
        selectedAction?.let { option ->
            val target = selectedUid
            if (target != null) submit(option, target)
            else if (option.action == "salvar" && state?.ownUid in option.targets) submit(option, state?.ownUid)
            else if (option.action == "invitar_muerto") options.firstOrNull { it.action == "guardar_poder" }?.let { submit(it, null) }
                ?: chooseTarget(option)
            else chooseTarget(option)
            return
        }
        if (options.size == 1) choose(options.single())
        else if (options.isNotEmpty()) {
            val original = options.toList()
            val expected = phaseIndex
            GameDialog.choose(activity, "TU ACCIÓN", "", original.map { it.label }) {
                if (phaseIndex == expected) original[it].takeIf { option -> option in options }?.let(::choose)
            }
        }
    }
    private fun choose(option: ServerGameActionOption) {
        if (!ready || busy || option !in options) return
        if (option.action.startsWith("desertor_")) { showDesertorChoice(); return }
        if (option.targets.isEmpty()) {
            if (option.action == "revelar_alcalde" || option.action == "desertor_rethink") {
                val expected = phaseIndex
                GameDialog.confirm(activity, option.label, if (option.action == "desertor_rethink")
                    "Esta decisión consume tu única revisión de bando." else "Tu cargo será público durante el resto de la partida.", "CONFIRMAR") {
                    submit(option, null, expected)
                }
            } else submit(option, null)
        } else {
            selectedAction = option; selectedUid = null; refreshSelection()
        }
    }
    private fun selectTarget(id: String) {
        if (!ready || busy || id !in selectedAction?.targets.orEmpty()) return
        val option = selectedAction ?: return
        if (option.action == "votar") {
            // Usual table: touching a card casts (or changes) the vote immediately.
            val name = state?.publicState?.players?.firstOrNull { it.uid == id }?.name.orEmpty()
            val current = state?.privateState?.confirmed?.firstOrNull { it.action == "votar" }?.targetUid
            if (current == id) { GameNotice.show(activity, "Tu voto actual es por $name. Podés elegir otra carta."); return }
            selectedUid = id
            GameplayEffects.play(activity, GameplayEffect.CONFIRM)
            GameNotice.show(activity, "Votaste a $name. Podés cambiar hasta el cierre.")
            submit(option, id); refreshSelection(); return
        }
        GameplayEffects.play(activity, GameplayEffect.SELECT)
        selectedUid = id; refreshSelection()
    }

    private var presentedConfirmation: String? = null

    /** Server receipt. The confirmation was already shown at the touch; never shown twice. */
    fun actionConfirmed(command: ServerGameCommand) {
        if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_action_confirmed action=${command.action} idx=${command.phaseIndex}")
        presentConfirmation(command.action, command.targetUid, command.team, command.phaseIndex)
    }

    /** Server rejection or failure: withdraw an optimistic confirmation of that intention. */
    fun actionFailed() {
        if (presentedConfirmation == null) return
        presentedConfirmation = null
        dismissPrivateInvestigation(); dismissActionBanner()
    }

    private fun presentConfirmation(action: String, targetUid: String?, team: String?, phase: Int) {
        val s = state ?: return
        if (phase != s.publicState.phaseIndex) return
        val key = listOf(s.publicState.matchId, phase, action, targetUid, team).joinToString(":")
        if (key == presentedConfirmation) return
        val target = s.publicState.players.firstOrNull { it.uid == targetUid }?.name
        val spec = ServerGameTablePresentation.confirmationFeedback(presentedSession, action, target,
            team, s.publicState.counterpointPlayers.size) ?: return
        presentedConfirmation = key
        if (spec.blocksGameplay) showPrivateWindow(spec.title, spec.message, spec.tone)
        else showActionBanner(spec)
    }

    private fun showPrivateWindow(title: String, message: String, tone: GameplayActionTone) {
        dismissPrivateInvestigation(); dismissActionBanner()
        if (chatOpen) setChatOpen(false)
        view<TextView>(R.id.privateFeedbackTitle).text = title
        view<TextView>(R.id.privateFeedbackMessage).text = message
        view<View>(R.id.privateFeedbackTone).setBackgroundColor(Color.parseColor(tone.colorHex))
        view<Button>(R.id.btnContinuePrivateFeedback).apply {
            isEnabled = true; visibility = View.VISIBLE; alpha = 1f; setOnClickListener { dismissPrivateInvestigation() }
        }
        val panel = view<View>(R.id.privateFeedbackPanel)
        panel.alpha = 0f; panel.scaleX = .94f; panel.scaleY = .94f; panel.translationY = dp(34).toFloat()
        view<View>(R.id.privateFeedbackOverlay).apply {
            visibility = View.VISIBLE; alpha = 0f; animate().alpha(1f).setDuration(300).start()
            announceForAccessibility(message)
        }
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.25f)).setDuration(300).start()
        GameplayEffects.play(activity, GameplayEffect.CONFIRM)
        // Usual table: the window closes itself after nine seconds (REVEAL_CONTINUE_TIMEOUT_MS).
        privateResultDismiss = Runnable { dismissPrivateInvestigation() }.also { handler.postDelayed(it, 9000) }
    }

    private fun showActionBanner(spec: GameplayFeedbackSpec) {
        val p = state?.publicState ?: return
        if (p.phase in setOf(ServerGamePhase.RECUENTO_VOTOS, ServerGamePhase.DESEMPATE_VOTACION)) return
        dismissActionBanner()
        view<TextView>(R.id.actionFeedbackBannerTitle).text = spec.title
        view<TextView>(R.id.actionFeedbackBannerMessage).text = spec.message
        view<View>(R.id.actionFeedbackBannerTone).setBackgroundColor(Color.parseColor(spec.tone.colorHex))
        GameplayEffects.play(activity, GameplayEffect.CONFIRM)
        view<View>(R.id.actionFeedbackBanner).apply {
            visibility = View.VISIBLE; alpha = 0f; translationY = dp(26).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(260L).start()
        }
        bannerDismiss = Runnable { dismissActionBanner() }.also { handler.postDelayed(it, maxOf(spec.durationMs, 10_000L)) }
    }

    private fun dismissActionBanner() {
        bannerDismiss?.let(handler::removeCallbacks); bannerDismiss = null
        view<View>(R.id.actionFeedbackBanner).apply { animate().cancel(); visibility = View.GONE; alpha = 1f; translationY = 0f }
    }

    private fun maybeShowTraitorReveal() {
        val s = state ?: return
        if (traitorRevealRunning || s.publicState.winner != null) return
        val teammates = GameplayTableUi.traitorTeammatesForReveal(presentedSession)
        if (teammates.isEmpty()) return
        val prefs = activity.getSharedPreferences("v3_traitor_reveal", android.content.Context.MODE_PRIVATE)
        val key = s.ownUid + ":" + s.publicState.matchId
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        traitorRevealRunning = true
        val grid = view<GridLayout>(R.id.traitorRevealCards)
        grid.removeAllViews()
        val columns = when { teammates.size <= 1 -> 1; teammates.size <= 3 -> teammates.size; else -> 2 }
        val viewport = (activity.resources.configuration.screenWidthDp - 32).coerceAtLeast(220)
        val slot = (viewport / columns).coerceAtLeast(68)
        val cardWidth = when (teammates.size) { 0, 1 -> minOf(104, slot - 16); 2 -> minOf(92, slot - 12)
            3 -> minOf(76, slot - 8); else -> minOf(86, slot - 14) }.coerceAtLeast(58)
        grid.columnCount = columns; grid.rowCount = (teammates.size + columns - 1) / columns
        val cardsViews = teammates.map { teammate ->
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), 0, dp(3), dp(8))
                layoutParams = GridLayout.LayoutParams().apply { width = dp(slot); height = GridLayout.LayoutParams.WRAP_CONTENT; setGravity(Gravity.CENTER) }
                addView(FrameLayout(activity).apply {
                    setBackgroundResource(R.drawable.bg_role_card); setPadding(dp(3), dp(3), dp(3), dp(3))
                    addView(ImageView(activity).apply {
                        setImageResource(roleImage(teammate.role)); scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Rol de ${teammate.name}"
                    }, FrameLayout.LayoutParams(-1, -1))
                }, LinearLayout.LayoutParams(dp(cardWidth), dp(cardWidth * 4 / 3)))
                addView(label(teammate.name, if (teammates.size >= 3) 12f else 14f, gold).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setTypeface(null, Typeface.BOLD)
                }, LinearLayout.LayoutParams(dp(slot - 6), -2).apply { topMargin = dp(5) })
                addView(label(teammate.role?.name?.uppercase().orEmpty(), if (teammates.size >= 3) 10f else 11.5f, activity.getColor(R.color.text_secondary)).apply {
                    gravity = Gravity.CENTER; maxLines = 1
                }, LinearLayout.LayoutParams(dp(slot - 6), -2))
            }
        }
        traitorReveal.show(cardsViews, 6000L, ::dismissTraitorReveal)
    }

    private fun dismissTraitorReveal() {
        if (!traitorRevealRunning) return
        traitorReveal.dismiss { traitorRevealRunning = false; playNextReveal() }
    }
    private fun showDesertorChoice() {
        val s = state ?: return
        if (!ready || busy || desertorDialog != null) return
        val available = options.filter { it.action.startsWith("desertor_") }
        if (available.isEmpty()) return
        val initial = available.first().action == "desertor_initial"
        val expectedPhase = phaseIndex
        val content = activity.layoutInflater.inflate(R.layout.dialog_desertor_choice, null)
        content.findViewById<TextView>(R.id.desertorChoiceTitle).text = if (initial) "ELEGÍ TU BANDO" else "REVISÁ TU BANDO"
        content.findViewById<TextView>(R.id.desertorChoiceMessage).text = if (initial)
            "Tu elección es secreta. Si no elegís a tiempo, tu bando se sortea."
        else "Tu bando actual es ${s.privateState.deserterTeam}. Mantenerlo o cambiarlo consume tu única revisión. Si no elegís, lo conservás."
        content.findViewById<ImageView>(R.id.desertorChoiceImage).setImageResource(roleImage(RoleCatalog.gameRole("desertor", map)))
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        desertorDialog = dialog
        for ((id, team) in listOf(R.id.btnDesertorTown to "Pueblo", R.id.btnDesertorTraitors to "Traidores")) {
            val option = available.firstOrNull { it.team == team || it.team == "mantener" && s.privateState.deserterTeam == team }
            content.findViewById<LinearLayout>(id).apply {
                isEnabled = option != null; alpha = if (option == null) 0.5f else 1f
                if (!initial) (getChildAt(0) as TextView).text = if (s.privateState.deserterTeam == team)
                    "MANTENER ${team.uppercase()}" else "CAMBIAR A ${team.uppercase()}"
                setOnClickListener { option?.let { submit(it, null, expectedPhase) }; dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener { if (desertorDialog === dialog) desertorDialog = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dp((activity.resources.configuration.screenWidthDp - 28).coerceAtMost(360)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
    private fun chooseTarget(action: ServerGameActionOption) {
        val s = state ?: return
        val expected = phaseIndex
        GameDialog.choose(activity, action.label, "", action.targets.map { id -> s.publicState.players.first { it.uid == id }.name }) {
            if (phaseIndex == expected && selectedAction == action) selectTarget(action.targets[it])
        }
    }
    private fun submit(option: ServerGameActionOption, target: String?, expectedPhase: Int = phaseIndex) {
        if (ready && !busy && expectedPhase == phaseIndex && option in options) {
            if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_action_submit action=${option.action} idx=$expectedPhase")
            // Usual table answers at the touch. The server receipt confirms it; a rejection
            // withdraws the window and shows the error (actionFailed).
            if (onAction(option, target, expectedPhase)) presentConfirmation(option.action, target, option.team, expectedPhase)
        }
    }
    private fun refreshSelection() {
        // Reapply only local presentation. The controller revalidates every submission.
        controls(options, ready, busy, lastCanRetry, lastRematching, lastIsCreator, lastChatSending, lastChannel)
    }
    fun setStatus(text: String) { connectionStatus = text; renderAnnouncement() }
    private var reactionPalette: PopupWindow? = null
    private var reactionSending = false
    private val reactionLimiter = GameplayReactionLimiter()
    private val reactionUi by lazy {
        GameplayReactionUi(activity, view(R.id.gameplayRoot), { uid ->
            if (uid == state?.ownUid) view<FrameLayout>(R.id.roleCard) else cards[uid]?.cardFace
        }, { it == state?.ownUid }, { uid ->
            val name = state?.publicState?.players?.firstOrNull { it.uid == uid }?.name
            CosmeticPilot.normalizeTheme(identity.playerProfiles[name]?.cosmeticThemeId) ?: CosmeticPilot.THEME_CLASSIC
        }, ::dp)
    }
    private fun refreshReactions() {
        val s = state
        val check = s?.let { reactionLimiter.check(it.ownUid, it.publicState.round, android.os.SystemClock.elapsedRealtime()) }
        view<View>(R.id.btnToggleEmotes).apply {
            isEnabled = ready && !reactionSending && !resolving && s?.permissions?.reactions == true && check?.allowed == true
            alpha = if (isEnabled) 1f else .42f
            contentDescription = when {
                s?.human?.muted == true -> "Estás silenciado. No podés enviar emotes."
                check?.reason == ReactionBlockReason.ROUND_LIMIT -> "Sin emotes disponibles esta ronda"
                check?.reason == ReactionBlockReason.COOLDOWN -> "Esperá diez segundos entre emotes"
                isEnabled -> "Abrir emotes"; else -> "Emotes no disponibles en esta fase"
            }
            if (!isEnabled) { reactionPalette?.dismiss(); reactionPalette = null }
        }
    }
    private fun openReactions() {
        val s = state ?: return
        if (!ready || reactionSending || !s.permissions.reactions || resolving) return
        reactionPalette?.dismiss()
        val selected = EmoteLoadout.selectedSpecs(activity).filterNot { it.isPremium }
        val specs = (selected + EmoteCatalog.defaultLoadoutIds.mapNotNull(EmoteCatalog::byId)).distinctBy { it.id }.take(4)
            .map { ReactionSpec(it.id, it.emotionKey, it.imageRes, it.label, it.toneHex, it.description) }
        reactionPalette = reactionUi.palette(view(R.id.btnToggleEmotes), specs, s.ownUid) { spec ->
            val current = state ?: return@palette
            if (!ready || reactionSending || !current.permissions.reactions ||
                !reactionLimiter.check(current.ownUid, current.publicState.round, android.os.SystemClock.elapsedRealtime()).allowed) return@palette
            reactionPalette?.dismiss(); reactionSending = true; refreshReactions()
            onSendReaction(spec.id) { accepted ->
                reactionSending = false
                if (accepted) reactionLimiter.record(current.ownUid, current.publicState.round, android.os.SystemClock.elapsedRealtime())
                refreshReactions()
            }
        }
    }
    fun reaction(value: ServerReaction) {
        val s = state ?: return
        if (value.matchId != s.publicState.matchId || value.phaseIndex != s.publicState.phaseIndex ||
            !ServerGameReactions.receivePhase(s) || s.publicState.players.none { it.uid == value.actorUid }) return
        val spec = EmoteCatalog.byId(value.emoteId)?.takeUnless { it.isPremium } ?: return
        reactionUi.show(value.actorUid, ReactionSpec(spec.id, spec.emotionKey, spec.imageRes, spec.label, spec.toneHex, spec.description))
    }
    private fun actionText(action: String) = when (action) {
        "salvar" -> "SALVAR"; "votar" -> "VOTAR"; "matar" -> "MATAR"
        "silenciar" -> "SILENCIAR"; "investigar" -> "INVESTIGAR"; "invitar_muerto" -> "INVOCAR"
        // Usual table card badges: the Payador's choice reads CONTRAPUNTO, the pointing SEÑALAR.
        "contrapunto" -> "CONTRAPUNTO"; "senalar_contrapunto" -> "SEÑALAR"
        "decidir_empate" -> "DECIDIR"; else -> options.firstOrNull { it.action == action }?.label.orEmpty()
    }
    private fun contextualHint(): String {
        GameplayPhasePresentation.votingWatchText(presentedSession)?.let { return it.subtitle }
        val role = GameEngine.humanPlayer(presentedSession).role
        val hint = GameEngine.privateRoleHint(presentedSession)
        return hint.removePrefix(role?.let { "${it.name} - ${it.team}." }.orEmpty()).trim()
            .ifBlank { GameplayTableUi.centralPhaseMessage(presentedSession, "Esperá a que termine la fase.") }
    }
    private fun renderAnnouncement() {
        announcement.text = connectionStatus.ifBlank {
            if (state == null) "Conectando con la partida…"
            else if (resolving) "Resolviendo…"
            else if (state?.publicState?.phase == ServerGamePhase.DESERTOR_RECONSIDERACION)
                "El Desertor está revisando su bando."
            // V3 nights end on the server clock; there is no local "skip the night".
            else GameplayPhasePresentation.phaseAdvice(presentedSession)
                ?.replace("Puedes mirar la noche o saltarla.", "Esperá al amanecer.")?.let { "Objetivo: $it" }
                ?: GameplayTableUi.centralPhaseMessage(presentedSession, "Esperá a que termine la fase.")
        }
    }

    private fun showInvestigations() {
        val s = state ?: return
        GameNotice.show(activity, s.privateState.investigations.takeIf { it.isNotEmpty() }?.joinToString("\n") { (id, traitor) ->
            (s.publicState.players.firstOrNull { it.uid == id }?.name ?: "Jugador") + ": " + if (traitor) "Sospechoso" else "Inocente"
        } ?: "Todavía no tenés investigaciones.")
    }
    fun setCountdown(seconds: Long?, resolving: Boolean) {
        countdown = seconds; this.resolving = resolving
        renderDebateButtons() // "VOTAR ANTES EN N" follows the server clock
        refreshReactions()
        view<View>(R.id.phaseProgressFill).scaleX = ((seconds ?: 0) * 1000f / phaseDurationMs).coerceIn(0f, 1f)
        val text = seconds?.toString().orEmpty()
        for (id in listOf(R.id.phaseCountdown, R.id.tieVoteCountdown)) view<TextView>(id).apply {
            if (this.text.toString() != text) this.text = text
        }
        if (resolving) for (id in listOf(R.id.phaseSubtitle, R.id.tieVoteNotice)) view<TextView>(id).apply {
            if (this.text.toString() != "Resolviendo…") this.text = "Resolviendo…"
        }
    }
    fun setChatOpen(open: Boolean) {
        if (!::chatController.isInitialized) return
        if (open) chatController.openExpanded() else chatController.closeForPriorityWindow()
        onChatVisibility(chatOpen)
        renderTieWindow()
    }
    fun messages(messages: List<ServerChatMessage>, channel: String) {
        channelMessages[channel] = messages.takeLast(60)
        val visible = displayState() ?: return
        presentedSession = ServerGameSessionAdapter.adapt(identity, visible, channelMessages)
        chatController.onSessionUpdated()
    }

    private fun renderRoster(value: ServerGameSnapshot) {
        val players = presentedSession.players
        val byPlayer = java.util.IdentityHashMap<GamePlayer, ServerGamePlayer>()
        players.zip(value.publicState.players.sortedBy { it.order }).forEach { (player, public) -> byPlayer[player] = public }
        val nextAlive = value.publicState.players.associate { it.uid to it.alive }
        val newlyDead = nextAlive.filter { (uid, alive) -> !alive && displayedAlive[uid] == true }.keys
        displayedAlive = nextAlive
        columns.render(players, newlyDead) { byPlayer.getValue(it).uid }
    }

    private fun bindPlayer(holder: SidePlayerCardHolder, player: GamePlayer, metrics: CompanionCardMetrics) {
        val value = displayState() ?: return
        val index = presentedSession.players.indexOfFirst { it === player }
        val public = value.publicState.players.sortedBy { it.order }.getOrNull(index) ?: return
        val selected = public.uid == selectedUid
        val actionable = ready && !busy && public.uid in selectedAction?.targets.orEmpty()
        val actionLabel = selectedAction?.let { actionText(it.action) }.orEmpty()
        val oracleGuest = public.uid == value.publicState.oracleGuestUid && value.publicState.phase == ServerGamePhase.DIA_DEBATE
        val showRole = public.publicRoleKey != null
        val renderKey = listOf(player, metrics, selected, actionable, actionLabel, oracleGuest, showRole, value.publicState.phaseIndex).toString()
        if (holder.renderKey == renderKey) return
        holder.renderKey = renderKey
        holder.root.minimumWidth = dp(metrics.minCardWidthDp)
        holder.root.layoutParams = LinearLayout.LayoutParams(-1, dp(metrics.itemHeightDp)).apply { bottomMargin = dp(metrics.itemGapDp) }
        holder.cardFace.layoutParams = LinearLayout.LayoutParams(dp(metrics.cardWidthDp), dp(metrics.cardHeightDp))
        columns.updatePublicRoleCard(holder, player, showRole, player.alive)
        holder.avatar.layoutParams = FrameLayout.LayoutParams(dp(metrics.avatarSizeDp), dp(metrics.avatarSizeDp), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) }
        holder.avatar.bind(presentedSession, player, GameplayTableUi.playerInitial(player), metrics.nameTextSp)
        holder.mutedBadge.visibility = if (player.alive && player.muted) View.VISIBLE else View.GONE
        // As in common online, the frame/name communicate elimination without an extra blood/boot overlay.
        holder.deathCauseOverlay.visibility = View.GONE
        columns.bindActionBadge(holder, metrics,
            selected && value.publicState.phase == ServerGamePhase.VOTACION,
            actionable, actionLabel, if (actionLabel == "DECIDIR") "EXPULSAR" else actionLabel)
        columns.bindAppearance(holder, player, metrics, presentedSession, oracleGuest, showRole,
            selected, actionable, actionLabel, selected && value.publicState.phase == ServerGamePhase.VOTACION)
        holder.root.isFocusable = true
        holder.root.isEnabled = true
        holder.root.contentDescription = player.name + ", " + (public.publicRoleKey?.let { RoleCatalog.gameRole(it, map).name } ?: "rol oculto") + ", " + ServerGameTablePresentation.condition(public)
        // Usual table: a target is selected; any other card opens the player's profile, or the
        // eliminated card when its role is public. A long press always opens the profile.
        holder.root.setOnClickListener {
            val current = displayState()?.publicState?.players?.firstOrNull { it.uid == public.uid } ?: public
            when {
                ready && !busy && current.uid in selectedAction?.targets.orEmpty() -> selectTarget(current.uid)
                !current.alive && current.publicRoleKey != null -> GameplayEliminatedPlayerCard.show(activity,
                    ServerGameTablePresentation.player(displayState() ?: return@setOnClickListener, current, map, publicOnly = true), ::roleImage)
                !current.alive -> {
                    GameplayEffects.play(activity, GameplayEffect.ERROR)
                    GameNotice.show(activity, "${current.name} está eliminado. Su rol sigue oculto.")
                }
                else -> showPlayerProfile(player)
            }
        }
        holder.root.setOnLongClickListener { showPlayerProfile(player); true }
    }

    private fun showPlayerProfile(player: GamePlayer) {
        GameplayEffects.play(activity, GameplayEffect.PANEL)
        PlayerProfileDialog.showFull(activity, PlayerProfileStore.profileFor(activity, presentedSession, player), canEdit = false)
    }

    private fun bindSharedChat() {
        val host = object : GameplayChatController.ChatHost {
            override var currentSession: GameSession
                get() = presentedSession
                set(value) { error("V3 presentation cannot mutate a game session") }
            override val gameplayTextScale = 1f
            override val onlineRoomId = ""
            override val onlinePlayerUid = ""
            override fun isOnlineGameplay() = true
            override fun hasOnlineSpectatorChatAccess() = state?.human?.alive == false
            override fun canOpenExpandedChat() = state?.publicState?.winner == null && !expulsionRunning && activeReveal == null
            override fun dp(value: Int) = this@ServerGameTableRenderer.dp(value)
            override fun isTransitionLocked(phaseIndex: Int) = expulsionRunning || activeReveal != null
            override fun hideKeyboard() {
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
            }
            override fun showToast(message: String, duration: Int) { GameNotice.show(activity, message) }
            override fun showTieVoteWindowAfterChat() = renderTieWindow()
            override fun renderHumanCardIfVisible() {
                view<ImageView>(R.id.roleImage).setImageResource(R.drawable.card_back_traidores)
                if (!chatOpen) view<TextView>(R.id.roleName).text = "CARTA OCULTA"
            }
            override fun renderPersonalStatus() {
                if (!chatOpen) {
                    view<View>(R.id.actionControls).visibility = View.VISIBLE
                    view<View>(R.id.currentPlayerHint).visibility = View.VISIBLE
                }
            }
            override fun chatLogDrawableRes() = GameplayThemeResolver.logDrawableFor(theme)
            override fun onOnlineReactionReceived(playerName: String, emoteId: String) = Unit
            override fun onOnlineTraitorActionMarksChanged(marks: List<OnlineTraitorActionMark>) = Unit
            override fun onRealtimeContentAccessCancelled(error: Exception) = Unit
            override fun isOnlineActorLocallyMuted(actorId: String) = false
            override fun isOwnPlayerTableSilenced() = false
            override fun cosmeticThemeForPlayer(playerName: String) = ""
        }
        val source = object : GameplayChatController.ChatDataSource {
            override fun allowedChannels() = displayState()?.let { ServerGameChat.channels(it).map(ServerGameSessionAdapter::channel).toSet() } ?: setOf(ChatChannel.PUBLICO)
            override fun canSend(channel: ChatChannel): Boolean {
                val s = state ?: return false
                val now = nowMs() ?: return false
                return ready && !lastChatSending && now < (s.publicState.deadlineMs ?: 0) &&
                    ServerGameChat.canSend(ServerGameSessionAdapter.channel(channel), s)
            }
            override fun onVisibilityChanged(open: Boolean) {
                onChatVisibility(open); renderTieWindow()
            }
            override fun select(channel: ChatChannel) = onChooseChannel(ServerGameSessionAdapter.channel(channel))
            override fun send(channel: ChatChannel, text: String, complete: (Boolean) -> Unit) = onSendChat(ServerGameSessionAdapter.channel(channel), text, complete)
        }
        chatController = GameplayChatController(host, view(R.id.gameplayRoot), source)
        chatController.onCreate(null)
    }

    private fun showRole() {
        val s = state ?: return
        val role = RoleCatalog.gameRole(s.ownRoleKey, map)
        view<ImageView>(R.id.rolePreviewMapBackground).setImageResource(background(s.publicState.phase == ServerGamePhase.NOCHE))
        view<ImageView>(R.id.rolePreviewImage).setImageResource(roleImage(role))
        view<TextView>(R.id.rolePreviewName).text = role.name.uppercase()
        view<TextView>(R.id.rolePreviewTeam).text = role.team + (s.privateState.deserterTeam?.let { " · " + it } ?: "")
        view<TextView>(R.id.rolePreviewFunction).text = RoleCatalog.definition(s.ownRoleKey).function
        view<TextView>(R.id.rolePreviewAdvice).text = RoleCatalog.advice(s.ownRoleKey)
        rolePreview.show(initialReveal = s.publicState.phase == ServerGamePhase.REPARTO)
    }
    private fun showDayResult(s: ServerGameSnapshot) {
        val plan = resultPlan
        val p = s.publicState
        if (plan == null) {
            // An older deployment can still be finishing RESULTADO with a living
            // target. Wait for its confirmed death; do not announce no expulsion.
            if (p.players.any { it.uid == p.dayEliminationUid && it.alive }) {
                val players = p.players.map { ServerGameTablePresentation.player(s, it, map, publicOnly = true) }
                val totals = p.voteTotals.mapKeys { (order, _) -> p.players.first { it.order == order }.name }
                voteResult.showServerTotals(identity.copy(players = players, showIndividualVotes = false), totals,
                    "RESULTADO DEL DÍA", "El servidor está confirmando la expulsión.")
            } else {
                val players = p.players.map { ServerGameTablePresentation.player(s, it, map, publicOnly = true) }
                val totals = p.voteTotals.mapNotNull { (order, votes) ->
                    p.players.firstOrNull { it.order == order }?.name?.let { it to votes }
                }.toMap()
                voteResult.showServerNoExpulsion(identity.copy(players = players, showIndividualVotes = false), totals)
            }
            return
        }
        // The shared animator resolves by display name. Supply the UID-selected
        // target only so two players with the same alias cannot swap their cards.
        val session = identity.copy(players = listOf(ServerGameTablePresentation.player(s, plan.player, map, publicOnly = true)),
            dayEliminationTarget = plan.player.name, revealRolesOnDeath = plan.player.publicRoleKey != null,
            showIndividualVotes = false, alcaldeCorruption = p.mayorCorruption, voteRound = p.voteRound)
        if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_expulsion mode=${plan.mode} remainingMs=${plan.remainingMs}")
        voteResult.showServerExpulsion(session, plan.mode, minOf(3500, plan.remainingMs - 250), ::finishExpulsion)
        if (expulsionRunning) {
            ceremonyTimeout = Runnable {
                if (!expulsionRunning) return@Runnable
                voteResult.hide()
                finishExpulsion()
            }.also { handler.postDelayed(it, (plan.remainingMs - 150).coerceAtLeast(1)) }
        }
    }

    private fun finishExpulsion() {
        if (!expulsionRunning) return
        expulsionRunning = false; cancelCeremonyTimers(); holdDeath = false
        refreshPublicPresentation()
        val plan = resultPlan
        val remaining = (state?.publicState?.deadlineMs ?: 0) - (nowMs() ?: Long.MAX_VALUE)
        if (plan?.jester == true && state?.publicState?.winner == null && remaining >= 1500) {
            voteResult.hide(); holdJester = false
            view<TextView>(R.id.jesterVictoryPlayer).text = plan.player.name.uppercase() + " ERA EL BUFÓN"
            view<TextView>(R.id.jesterVictoryMessage).text = "Consiguió que el pueblo lo expulsara durante la votación."
            activeReveal = "JESTER"
            val duration = minOf(8000, remaining - 200).coerceAtLeast(1000)
            GameplayAudioDirector.play(activity, GameSound.JESTER)
            jester.show(minOf(1500, duration - 400).coerceAtLeast(600))
            ceremonyTimeout = Runnable { finishJester() }.also { handler.postDelayed(it, duration) }
            announcement.announceForAccessibility(plan.player.name + " era el Bufón. Consiguió su victoria especial.")
        } else { holdJester = false; heldUid = null; playNextReveal() }
    }

    private fun finishJester() {
        if (activeReveal != "JESTER") return
        jester.hide(); heldUid = null; holdJester = false
        revealFinished()
    }

    private fun playNextReveal() {
        val s = state ?: return
        val terminal = s.publicState.winner != null
        if (terminal && resultPending == null) return
        if (activeReveal != null || expulsionRunning || transition.running || traitorRevealRunning) return
        val event = reveals.pollFirst()
        if (event == null) {
            resultPending?.let { pending -> resultPending = null; showResult(pending, resultPendingAnimate) }
            return
        }
        val p = s.publicState
        val remaining = (p.deadlineMs ?: 0) - (nowMs() ?: Long.MAX_VALUE)
        // AMANECER is a short non-action phase: its queue may continue into debate.
        // Other phases must leave room for a current action and never pause its clock.
        // A finished match has no clock: its last death is revealed before the victory.
        if (!terminal && p.phase != ServerGamePhase.AMANECER && remaining < 2500) { reveals.clear(); return }
        val target = p.players.firstOrNull { it.uid == event.playerUid }
        activeReveal = event.code
        if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_reveal code=${event.code} phase=${p.phase} terminal=$terminal")
        when (event.code) {
            "DAWN_NO_VICTIMS" -> { GameplayAudioDirector.play(activity, GameSound.NO_DEATH); dawn.start() }
            "ORACLE" -> if (target != null && p.phase == ServerGamePhase.DIA_DEBATE) showOracleReveal(target) else { revealFinished(); return }
            "COUNTERPOINT" -> {
                val players = p.counterpointPlayers.mapNotNull { uid -> p.players.firstOrNull { it.uid == uid } }
                if (p.phase == ServerGamePhase.CONTRAPUNTO && players.size == 2) showCounterpointReveal(players)
                else { revealFinished(); return }
            }
            else -> {
                if (target == null) { revealFinished(); return }
                val publicPlayer = ServerGameTablePresentation.player(s, target, map, publicOnly = true)
                if (event.code == "SILENCED") {
                    GameplayAudioDirector.play(activity, GameSound.SILENCE); silence.start(publicPlayer)
                } else {
                    GameplayAudioDirector.play(activity, GameSound.ELIMINATION)
                    view<TextView>(R.id.deathRevealHeadline).text = "MUERTE DURANTE LA NOCHE"
                    view<View>(R.id.btnContinueDeathReveal).visibility = View.INVISIBLE
                    death.start(publicPlayer, revealRole = publicPlayer.role != null)
                }
            }
        }
        val budget = if (terminal || p.phase == ServerGamePhase.AMANECER) 6000L else minOf(6000, remaining - 750)
        ceremonyTimeout = Runnable {
            death.cancel(); dawn.cancel(); silence.cancel(); hideOracleReveal(); hideCounterpointReveal()
            revealFinished()
        }.also { handler.postDelayed(it, budget.coerceAtLeast(1)) }
    }

    private fun cancelCeremonyTimers() {
        ceremonyTimeout?.let(handler::removeCallbacks); ceremonyTimeout = null
        deathAutoContinue?.let(handler::removeCallbacks); deathAutoContinue = null
    }

    private fun revealFinished() {
        cancelCeremonyTimers(); activeReveal = null
        playNextReveal()
    }

    private fun closePhaseWindows() {
        dismissPrivateInvestigation(); dismissActionBanner()
        PlayerProfileDialog.dismissAll(activity) // as the usual table does when the phase moves on
        desertorDialog?.dismiss(); desertorDialog = null
        rolePreview.cancelAndHide()
        if (!expulsionRunning) voteResult.hide()
        // A running dawn transition finishes into the debate; stopReveals cancels it when needed.
        EssentialViewAnimation.clear(view(R.id.tieVoteOverlay), view(R.id.tieVotePanel))
        view<View>(R.id.tieVoteOverlay).visibility = View.GONE
    }

    private fun stopReveals() {
        cancelCeremonyTimers(); reveals.clear(); activeReveal = null
        expulsionRunning = false; holdDeath = false; holdJester = false; heldUid = null; heldPrevious = null; resultPlan = null
        if (::death.isInitialized) death.cancel()
        if (::dawn.isInitialized) dawn.cancel()
        if (::silence.isInitialized) silence.cancel()
        if (::jester.isInitialized) jester.hide()
        if (::voteResult.isInitialized) voteResult.hide()
        if (::transition.isInitialized) transition.cancel()
        closePhaseWindows()
        hideOracleReveal(); hideCounterpointReveal()
    }
    private fun showOracleReveal(player: ServerGamePlayer) {
        hideOracleReveal()
        val overlay = view<FrameLayout>(R.id.oracleRevealOverlay)
        val panel = view<FrameLayout>(R.id.oracleRevealPanel)
        view<TextView>(R.id.oracleRevealPlayer).text = player.name.uppercase() + "\nVOZ RECUPERADA"
        GameplayAudioDirector.play(activity, GameSound.ORACLE)
        EssentialViewAnimation.reveal(overlay, 260L, fromScale = 1f)
        EssentialViewAnimation.reveal(panel, 620L, fromScale = 0.86f)
        view<View>(R.id.oracleRevealProgress).apply {
            scaleX = 1f
            animate().scaleX(0f).setDuration(6000L).start()
        }
        oracleDismiss = Runnable { hideOracleReveal(); revealFinished() }.also { overlay.postDelayed(it, 6000L) }
    }
    private fun hideOracleReveal() {
        val overlay = view<FrameLayout>(R.id.oracleRevealOverlay)
        oracleDismiss?.let(overlay::removeCallbacks); oracleDismiss = null
        val progress = view<View>(R.id.oracleRevealProgress)
        progress.animate().cancel(); progress.scaleX = 1f
        EssentialViewAnimation.clear(overlay, view<FrameLayout>(R.id.oracleRevealPanel))
        overlay.visibility = View.GONE
    }
    private fun showCounterpointReveal(players: List<ServerGamePlayer>) {
        hideCounterpointReveal()
        val overlay = view<FrameLayout>(R.id.payadorRevealOverlay)
        val panel = view<LinearLayout>(R.id.payadorRevealPanel)
        view<TextView>(R.id.payadorRevealFirstPlayer).text = players[0].name.uppercase()
        view<TextView>(R.id.payadorRevealSecondPlayer).text = players[1].name.uppercase()
        GameplayAudioDirector.play(activity, GameSound.PAYADOR)
        EssentialViewAnimation.reveal(overlay, 260L, fromScale = 1f)
        EssentialViewAnimation.reveal(panel, 620L, fromScale = 0.86f)
        view<View>(R.id.payadorRevealProgress).apply { scaleX = 1f; animate().scaleX(0f).setDuration(6000L).start() }
        counterpointDismiss = Runnable { hideCounterpointReveal(); revealFinished() }.also { overlay.postDelayed(it, 6000L) }
    }
    private fun hideCounterpointReveal() {
        val overlay = view<FrameLayout>(R.id.payadorRevealOverlay)
        counterpointDismiss?.let(overlay::removeCallbacks); counterpointDismiss = null
        view<View>(R.id.payadorRevealProgress).apply { animate().cancel(); scaleX = 1f }
        EssentialViewAnimation.clear(overlay, view<LinearLayout>(R.id.payadorRevealPanel))
        overlay.visibility = View.GONE
    }
    private fun showResult(s: ServerGameSnapshot, animate: Boolean) {
        if (resultShown) return
        if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_result winner=${s.publicState.winner}")
        resultShown = true; stopReveals(); rolePreview.cancelAndHide()
        val winner = s.publicState.winner.orEmpty()
        val cancellation = winner == GameRules.CANCELLED_WINNER
        // Same titles, layout and summary line as the usual table's winner ceremony.
        GameplayWinnerRevealLayout.apply(activity)
        view<TextView>(R.id.winnerRevealTitle).text = GameplayWinnerRevealLayout.title(winner)
        view<TextView>(R.id.winnerRevealPersonalResult).text = GameplayWinnerRevealLayout.subtitle(winner)
        view<ImageView>(R.id.winnerRevealBackground).setImageResource(R.drawable.winner_ceremony_background)
        view<Button>(R.id.btnWinnerChronicle).apply { text = "VER CRÓNICA"; setOnClickListener { toggleWinnerChronicle() } }
        val players = s.publicState.players.map { ServerGameTablePresentation.player(s, it, map) }
        val winners = s.publicState.players.filter { s.won(it) }.map { ServerGameTablePresentation.player(s, it, map) }
        val wonNames = winners.map { it.name }.toSet()
        val summary = GameSummaryPresentation(s.publicState.round, "—", players.count { it.alive },
            players.count { !it.alive }, players.filter { !it.alive }.map { it.name },
            "", listOf("Eventos disponibles de la última ronda."), emptyList())
        val renderer = WinnerResultsRenderer(activity, view(R.id.winnerRevealContent), view(R.id.winnerRevealCards),
            view(R.id.winnerSummaryRounds), view(R.id.winnerSummaryDuration), view(R.id.winnerSummaryPlayers),
            view(R.id.winnerSummaryHighlight), view(R.id.winnerSummaryTimeline), ::roleImage) { identity.copy(players = players) }
        val cardViews = renderer.render(winners, summary, emptyList(), emptyList(), winner,
            if (cancellation) emptyList() else players.filter { it.name !in wonNames })
        val personal = if (cancellation) "PARTIDA CANCELADA" else if (s.won(s.human)) "VICTORIA" else "DERROTA"
        view<TextView>(R.id.winnerCeremonySummary).text =
            GameplayWinnerRevealLayout.summary(winners.count { it.alive }, s.publicState.round, personal)
        view<TextView>(R.id.winnerSummaryTimeline).text = s.publicState.events.joinToString("\n") { it.text }
        if (cancellation) view<TextView>(R.id.winnerSummaryHighlight).text =
            "Todos quedaron inactivos. La partida terminó sin ganador y no cuenta para estadísticas."
        view<ScrollView>(R.id.winnerRevealScroll).scrollTo(0, 0)
        results.show(cardViews, animate) {}
    }

    private fun toggleWinnerChronicle() {
        val summary = view<View>(R.id.winnerSummaryPanel)
        val opening = summary.visibility != View.VISIBLE
        GameplayEffects.play(activity, GameplayEffect.PANEL)
        listOf(R.id.winnerRevealHeading, R.id.winnerRevealTitle, R.id.winnerRevealPersonalResult,
            R.id.winnerRevealCards, R.id.winnerCeremonySummary).forEach {
            view<View>(it).visibility = if (opening) View.GONE else View.VISIBLE
        }
        summary.visibility = if (opening) View.VISIBLE else View.GONE
        view<ScrollView>(R.id.winnerRevealScroll).scrollTo(0, 0)
    }
    fun showEvents() {
        val events = displayState()?.publicState?.events.orEmpty()
        GameNotice.show(activity, if (events.isEmpty()) "Aún no hay anuncios en esta ronda." else events.joinToString("\n\n") { it.text })
    }
    fun clear() {
        dismissPrivateInvestigation()
        state = null; cards.clear(); channelMessages.clear(); displayedAlive = emptyMap()
        view<LinearLayout>(R.id.leftPlayersContainer).removeAllViews()
        view<LinearLayout>(R.id.rightPlayersContainer).removeAllViews()
        view<ImageView>(R.id.roleImage).setImageResource(R.drawable.card_back_traidores)
        view<TextView>(R.id.roleName).text = ""
        announcement.text = ""; setChatOpen(false); stop()
    }
    fun stop() {
        dismissPrivateInvestigation(); dismissActionBanner(); hideCentralEvent()
        if (::traitorReveal.isInitialized) traitorReveal.cancelAndHide()
        traitorRevealRunning = false
        reactionPalette?.dismiss(); reactionPalette = null
        reactionUi.clear()
        if (::chatController.isInitialized) chatController.onRealtimeAccessUnavailable()
        stopReveals(); rolePreview.cancelAndHide(); voteResultPhaseShown = -1
        if (::results.isInitialized && resultShown) results.settle()
    }
    fun destroy() { if (::chatController.isInitialized) chatController.onDestroy() }
    private fun roleImage(role: GameRole?): Int = role?.let { DrawableResourceCatalog.resolveOrPlaceholder(it.imageResName) } ?: R.drawable.card_back_traidores
    private fun background(night: Boolean) = GameplayThemeResolver.backgroundDrawableFor(theme, night)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float, color: Int = activity.getColor(R.color.text_primary)) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (size >= 14) typeface = ResourcesCompat.getFont(activity, R.font.bree_serif)
    }
    private fun button(value: String, action: () -> Unit) = Button(activity).apply {
        text = value; textSize = 10f; setTextColor(gold); background = activity.getDrawable(R.drawable.bg_btn_dark_ripple)
        minHeight = dp(48); setPadding(dp(4), 0, dp(4), 0); setOnClickListener { action() }
    }
    private fun panel(stroke: Int = Color.parseColor("#665633")) = GradientDrawable().apply {
        setColor(Color.argb(222, 26, 21, 16)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), stroke)
    }
}
