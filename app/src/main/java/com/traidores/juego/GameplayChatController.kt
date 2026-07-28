package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.traidores.juego.GameToast as Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class GameplayChatController(
    private val host: ChatHost,
    private val root: RelativeLayout
) {
    interface ChatHost {
        var currentSession: GameSession
        val gameplayTextScale: Float
        val onlineRoomId: String
        val onlinePlayerUid: String

        fun isOnlineGameplay(): Boolean
        fun canOpenExpandedChat(): Boolean
        fun dp(value: Int): Int
        fun isTransitionLocked(phaseIndex: Int): Boolean
        fun hideKeyboard()
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
        fun showTieVoteWindowAfterChat()
        fun renderHumanCardIfVisible()
        fun renderPersonalStatus()
        fun chatLogDrawableRes(): Int
        fun onOnlineReactionReceived(playerName: String, emoteId: String)
        fun onRealtimeContentAccessCancelled(error: Exception)
    }

    private data class OnlineChatEntry(
        val id: String,
        val speaker: String,
        val message: String,
        val isGod: Boolean
    )

    private data class OnlineReactionEntry(
        val id: String,
        val actorId: String,
        val playerName: String,
        val emoteId: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isChatOpen = false
    private var isClosingForInteractivePhase = false
    private var isChatKeyboardCompact = false
    private var isBottomPlayerPanelCompact = false
    private var chatKeyboardBottomInset = 0
    private var newChatMessagesWhileTyping = 0
    private var lastSeenChatCount = 0
    private var restoreTieVoteAfterChat = false
    private var showOnlyEvents = false
    private var selectedChatChannel = ChatChannel.PUBLICO
    private var unreadChatCount = 0
    private var lastAmbientFeedRenderKey = ""
    private var lastExpandedChatRenderKey = ""
    private var lastChatBackgroundRenderKey = ""
    private var onlineChatQuery: Query? = null
    private var onlineChatListener: ValueEventListener? = null
    private var lastOnlineChatSentAtMs = 0L
    private var lastOnlineChatMessage = ""
    private var onlineTraitorChatQuery: Query? = null
    private var onlineTraitorChatListener: ValueEventListener? = null
    private var lastOnlineTraitorChatSentAtMs = 0L
    private var lastOnlineTraitorChatMessage = ""
    private var onlineSpectatorChatQuery: Query? = null
    private var onlineSpectatorChatListener: ValueEventListener? = null
    private var lastOnlineSpectatorChatSentAtMs = 0L
    private var lastOnlineSpectatorChatMessage = ""
    private var onlineReactionQuery: Query? = null
    private var onlineReactionListener: ValueEventListener? = null
    private var onlineReactionMatchId = ""
    private var onlineReactionBaselineReady = false
    private var realtimeAccessReady = false
    private val seenOnlineReactionIds = linkedSetOf<String>()
    private var wasHumanAlive: Boolean? = null
    private var stagedEventReactionKey = ""
    private var directorPhaseIndex = -1
    private var directorIdleLines = 0
    private var directorReactionLines = 0
    private var directorBeatCounter = 0
    private var directorPendingHumanMessage = ""
    private var directorPendingIntentHint: HumanMessageIntent? = null
    private var directorLastSpeaker: String? = null
    private var directorHumanSpokePhaseIndex = -1
    private var directorPromptedSilentHuman = false
    private var traitorDirectorPhaseIndex = -1
    private var traitorDirectorLines = 0
    private var traitorDirectorLastSpeaker: String? = null
    private var traitorPendingHumanMessage = ""
    private val pendingBotChatRunnables = mutableListOf<Runnable>()
    private val typingBotSpeakers = linkedSetOf<String>()
    private var quickChatDialog: AlertDialog? = null

    private val btnToggleChat: ImageButton = root.findViewById(R.id.btnToggleChat)
    private val btnSendChat: Button = root.findViewById(R.id.btnSendChat)
    private val btnCloseChat: ImageButton = root.findViewById(R.id.btnCloseChat)
    private val btnChatFeedFilter: Button = root.findViewById(R.id.btnChatFeedFilter)
    private val chatAmbientFeed: FrameLayout = root.findViewById(R.id.chatAmbientFeed)
    private val chatAmbientBackground: ImageView = root.findViewById(R.id.chatAmbientBackground)
    private val chatAmbientHint: TextView = root.findViewById(R.id.chatAmbientHint)
    private val chatAmbientMessages: LinearLayout = root.findViewById(R.id.chatAmbientMessages)
    private val chatAmbientShade: View = root.findViewById(R.id.chatAmbientShade)
    private val chatAmbientTitle: TextView = root.findViewById(R.id.chatAmbientTitle)
    private val chatCharacterCount: TextView = root.findViewById(R.id.chatCharacterCount)
    private val chatComposer: LinearLayout = root.findViewById(R.id.chatComposer)
    private val chatFeedTitle: TextView = root.findViewById(R.id.chatFeedTitle)
    private val chatHeader: LinearLayout = root.findViewById(R.id.chatHeader)
    private val chatInput: EditText = root.findViewById(R.id.chatInput)
    private val chatMessagesContainer: LinearLayout = root.findViewById(R.id.chatMessagesContainer)
    private val chatMessagesScroll: ScrollView = root.findViewById(R.id.chatMessagesScroll)
    private val chatNewMessages: TextView = root.findViewById(R.id.chatNewMessages)
    private val chatPanel: FrameLayout = root.findViewById(R.id.chatPanel)
    private val chatPanelBackground: ImageView = root.findViewById(R.id.chatPanelBackground)
    private val chatPanelContent: LinearLayout = root.findViewById(R.id.chatPanelContent)
    private val chatPanelShade: View = root.findViewById(R.id.chatPanelShade)
    private val chatQuickReplies: LinearLayout = root.findViewById(R.id.chatQuickReplies)
    private val chatQuickRepliesScroll: HorizontalScrollView = root.findViewById(R.id.chatQuickRepliesScroll)
    private val chatRoleChip: TextView = root.findViewById(R.id.chatRoleChip)
    private val chatStatusRow: LinearLayout = root.findViewById(R.id.chatStatusRow)
    private val chatUnreadBadge: TextView = root.findViewById(R.id.chatUnreadBadge)

    private val centerColumn: FrameLayout = root.findViewById(R.id.centerColumn)
    private val bottomPlayerPanel: LinearLayout = root.findViewById(R.id.bottomPlayerPanel)
    private val roleCard: View = root.findViewById(R.id.roleCard)
    private val currentPlayerName: TextView = root.findViewById(R.id.currentPlayerName)
    private val currentPlayerStatus: TextView = root.findViewById(R.id.currentPlayerStatus)
    private val currentPlayerHint: TextView = root.findViewById(R.id.currentPlayerHint)
    private val actionControls: LinearLayout = root.findViewById(R.id.actionControls)
    private val eliminatedStatePanel: LinearLayout = root.findViewById(R.id.eliminatedStatePanel)
    private val roleName: TextView = root.findViewById(R.id.roleName)

    fun onCreate(savedState: Bundle?) {
        isChatOpen = savedState?.getBoolean(STATE_CHAT_OPEN) ?: false
        val humanAlive = GameEngine.humanPlayer(host.currentSession).alive
        selectedChatChannel = savedState
            ?.getString(STATE_CHAT_CHANNEL)
            ?.let { savedChannel -> runCatching { ChatChannel.valueOf(savedChannel) }.getOrNull() }
            ?: if (host.isOnlineGameplay() && !humanAlive) {
                ChatChannel.ESPECTADORES
            } else {
                ChatChannel.PUBLICO
            }
        wasHumanAlive = humanAlive
        lastSeenChatCount = host.currentSession.chatHistory.size

        btnToggleChat.setOnClickListener { openExpandedOrClose() }
        chatAmbientFeed.setOnClickListener { openExpanded() }
        btnCloseChat.setOnClickListener { closeChatPanel() }
        btnChatFeedFilter.setOnClickListener { toggleFeedFilter() }
        btnSendChat.setOnClickListener { sendHumanChatMessage() }
        chatNewMessages.setOnClickListener { acknowledgeNewChatMessages() }
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
        chatInput.doAfterTextChanged { renderChatCharacterCount(it?.length ?: 0) }

        configureChatPanelLayout()
        renderChatPanelVisibility(animate = false)
        renderFeedFilterButton()
        renderChatTitle()
    }

    fun onSessionUpdated() {
        val humanAlive = GameEngine.humanPlayer(host.currentSession).alive
        if (host.isOnlineGameplay() && wasHumanAlive == true && !humanAlive) {
            selectedChatChannel = ChatChannel.ESPECTADORES
            showOnlyEvents = false
            host.showToast("Caíste. Ahora hablás en el Chat de los Muertos.")
        }
        wasHumanAlive = humanAlive
        if (host.isOnlineGameplay() && realtimeAccessReady) {
            // El rol del humano puede llegar despues de onCreate (reconstruccion online),
            // y la muerte puede llegar en una actualizacion posterior. Ambos listeners
            // condicionales se auto-protegen contra una doble suscripcion.
            startOnlineTraitorChatListener()
            startOnlineSpectatorChatListener()
            startOnlineReactionListener()
        }
        updateUnreadChatCount()
        renderChatPanel()
        applyKeyboardAwarePlayerPanel()
        renderChatBadge()
        stageEventReactionsForCurrentAnnouncement()
    }

    fun onRealtimeAccessReady() {
        if (!host.isOnlineGameplay()) return
        realtimeAccessReady = true
        startOnlineChatListener()
        startOnlineTraitorChatListener()
        startOnlineSpectatorChatListener()
        startOnlineReactionListener()
    }

    fun onRealtimeAccessUnavailable() {
        realtimeAccessReady = false
        stopOnlineContentListeners()
    }

    fun onBackPressed(): Boolean {
        if (!isChatOpen) return false
        closeChatPanel()
        return true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CHAT_OPEN, isChatOpen)
        outState.putString(STATE_CHAT_CHANNEL, selectedChatChannel.name)
    }

    fun onDestroy() {
        quickChatDialog?.dismiss()
        quickChatDialog = null
        onRealtimeAccessUnavailable()
        cancelPendingBotChat()
        handler.removeCallbacksAndMessages(null)
    }

    fun openExpanded() {
        if (isChatOpen || isClosingForInteractivePhase || !host.canOpenExpandedChat()) return
        GameplayEffects.play(root.context, GameplayEffect.PANEL)
        isChatOpen = true
        unreadChatCount = 0
        newChatMessagesWhileTyping = 0
        lastSeenChatCount = host.currentSession.chatHistory.size
        renderChatPanelVisibility(animate = true)
        renderChatPanel()
        renderChatBadge()
    }

    fun openFromTieVote() {
        restoreTieVoteAfterChat = true
        openExpanded()
    }

    fun isOpenOrRestoringTieVote(): Boolean = isChatOpen || restoreTieVoteAfterChat

    fun closeForInteractivePhase(onClosed: () -> Unit): Boolean {
        if (isClosingForInteractivePhase) return true
        val chatStillVisible = chatPanel.visibility == View.VISIBLE
        if (!isChatOpen && !chatStillVisible && !isChatKeyboardCompact) return false

        isClosingForInteractivePhase = true
        isChatOpen = false
        restoreTieVoteAfterChat = false
        newChatMessagesWhileTyping = 0
        clearChatComposerAfterSend()
        chatInput.clearFocus()
        host.hideKeyboard()
        setChatKeyboardState(false, 0)
        renderChatPanelVisibility(animate = true) {
            isClosingForInteractivePhase = false
            onClosed()
        }
        renderChatBadge()
        renderNewChatMessageNotice()
        return true
    }

    fun closeForPriorityWindow() {
        quickChatDialog?.dismiss()
        quickChatDialog = null
        restoreTieVoteAfterChat = false
        isClosingForInteractivePhase = false
        if (!isChatOpen && chatPanel.visibility != View.VISIBLE && !isChatKeyboardCompact) return
        isChatOpen = false
        newChatMessagesWhileTyping = 0
        chatInput.clearFocus()
        host.hideKeyboard()
        setChatKeyboardState(false, 0)
        renderChatPanelVisibility(animate = false)
        renderChatBadge()
        renderNewChatMessageNotice()
    }

    fun cancelPendingBotChat() {
        cancelScheduledBotChat()
        directorPendingHumanMessage = ""
        directorPendingIntentHint = null
        directorReactionLines = 0
    }

    fun sendOnlineReaction(playerName: String, emoteId: String) {
        if (!host.isOnlineGameplay()) return
        if (!realtimeAccessReady) {
            host.showToast("Reconectando la partida...")
            return
        }
        val matchId = host.currentSession.onlineMatchId
        if (
            host.onlineRoomId.isBlank() ||
            host.onlinePlayerUid.isBlank() ||
            matchId.isBlank() ||
            playerName.isBlank() ||
            EmoteCatalog.byId(emoteId) == null
        ) {
            OnlineDebugLog.e(
                "emote_send_rejected roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId player=$playerName emoteId=$emoteId"
            )
            host.showToast("No se pudo sincronizar el emote.", Toast.LENGTH_LONG)
            return
        }

        FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_REACTIONS_NODE")
            .push()
            .setValue(
                mapOf(
                    "matchId" to matchId,
                    "actorId" to host.onlinePlayerUid,
                    "player" to playerName,
                    "emoteId" to emoteId,
                    "ts" to ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "emote_send_success roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId player=$playerName emoteId=$emoteId"
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "emote_send_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId player=$playerName emoteId=$emoteId",
                    error
                )
                host.showToast(
                    OnlineErrorMessages.forAction("No se pudo sincronizar el emote", error),
                    Toast.LENGTH_LONG
                )
            }
    }

    fun onPhaseSettled() {
        if (host.isOnlineGameplay()) return
        val session = host.currentSession
        if (canRunVisibleTraitorNight(session)) {
            cancelPublicDirectorState()
            if (traitorDirectorPhaseIndex != session.phaseIndex) {
                cancelScheduledBotChat()
                resetTraitorDirectorForPhase(session)
            }
            scheduleNextTraitorNightBeat()
            return
        }
        resetTraitorDirectorIfNeeded(session)
        if (!BotConversationDirector.canRun(session)) {
            cancelPendingBotChat()
            return
        }
        if (directorPhaseIndex != session.phaseIndex) {
            cancelScheduledBotChat()
            resetDirectorForPhase(session)
        }
        scheduleNextIdleBeat()
    }

    private fun cancelScheduledBotChat() {
        pendingBotChatRunnables.forEach(handler::removeCallbacks)
        pendingBotChatRunnables.clear()
        typingBotSpeakers.clear()
        renderChatPanel()
    }

    private fun cancelPublicDirectorState() {
        directorPendingHumanMessage = ""
        directorPendingIntentHint = null
        directorReactionLines = 0
        directorIdleLines = 0
    }

    private fun stageEventReactionsForCurrentAnnouncement() {
        if (host.isOnlineGameplay()) return
        val session = host.currentSession
        val event = LocalBotAi.publicEventFromAnnouncement(session) ?: return
        val key = "${session.phaseIndex}:${event.type}:${event.target}:${session.publicAnnouncement}"
        if (stagedEventReactionKey == key) return
        val reactions = LocalBotAi.reactionsToEvent(session, event, limit = MAX_EVENT_BOT_REACTIONS)
        if (reactions.isEmpty()) return
        stagedEventReactionKey = key
        reactions.forEachIndexed { index, (speaker, message) ->
            scheduleBotChatMessage(
                speaker = speaker,
                message = message,
                phaseIndex = session.phaseIndex,
                phase = session.phase,
                delayMs = EVENT_BOT_REACTION_DELAY_MS + index * NEXT_BOT_REACTION_DELAY_MS
            )
        }
    }

    private fun openExpandedOrClose() {
        GameplayEffects.play(root.context, GameplayEffect.PANEL)
        if (isChatOpen) {
            closeChatPanel()
        } else {
            openExpanded()
        }
    }

    private fun closeChatPanel() {
        if (!isChatOpen) return
        isChatOpen = false
        newChatMessagesWhileTyping = 0
        chatInput.clearFocus()
        host.hideKeyboard()
        setChatKeyboardState(false, 0)
        renderChatPanelVisibility(animate = true)
        renderChatBadge()
        renderNewChatMessageNotice()
        if (
            restoreTieVoteAfterChat &&
            host.currentSession.phase == GamePhase.DESEMPATE_VOTACION
        ) {
            restoreTieVoteAfterChat = false
            root.post { host.showTieVoteWindowAfterChat() }
        }
    }

    private fun renderChatPanelVisibility(
        animate: Boolean,
        onClosed: (() -> Unit)? = null
    ) {
        chatPanel.animate().cancel()
        renderAmbientChatFeed()
        if (isChatOpen) {
            chatAmbientFeed.visibility = View.GONE
            chatPanel.visibility = View.VISIBLE
            if (animate) {
                chatPanel.translationX = 0f
                chatPanel.translationY = host.dp(8).toFloat()
                chatPanel.scaleX = 0.96f
                chatPanel.scaleY = 0.96f
                chatPanel.alpha = 0f
                chatPanel.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(210L)
                    .start()
            } else {
                resetChatPanelTransform()
            }
        } else if (animate && chatPanel.visibility == View.VISIBLE) {
            chatPanel.animate()
                .translationY(host.dp(8).toFloat())
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0f)
                .setDuration(170L)
                .withEndAction {
                    chatPanel.visibility = View.GONE
                    resetChatPanelTransform()
                    renderAmbientChatFeed()
                    onClosed?.invoke()
                }
                .start()
        } else {
            chatPanel.visibility = View.GONE
            resetChatPanelTransform()
            renderAmbientChatFeed()
            if (onClosed != null) {
                root.post { onClosed() }
            }
        }
        btnToggleChat.alpha = if (isChatOpen) 1f else 0.82f
        updateChatToggleContentDescription()
    }

    private fun resetChatPanelTransform() {
        chatPanel.translationX = 0f
        chatPanel.translationY = 0f
        chatPanel.scaleX = 1f
        chatPanel.scaleY = 1f
        chatPanel.alpha = 1f
    }

    private fun renderChatPanel() {
        btnToggleChat.alpha = if (isChatOpen) 1f else 0.9f
        renderChatBackgrounds()
        renderFeedFilterButton()
        renderChatTitle()
        renderAmbientChatFeed()
        if (!isChatOpen) return

        val channel = activeChatChannel()
        renderChatMessages(activeChannelMessages(channel), channel)

        val canChat = canHumanChatInChannel(channel)
        chatInput.isEnabled = canChat
        btnSendChat.isEnabled = canChat
        chatInput.hint = chatInputHint(canChat, channel)
        btnSendChat.alpha = if (canChat) 1f else 0.45f
        renderQuickReplies(channel, canChat)
        renderChatCharacterCount(chatInput.text.length)
        renderNewChatMessageNotice()
        if (newChatMessagesWhileTyping == 0) {
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun configureChatPanelLayout() {
        centerColumn.post {
            applyChatPanelDimensions()
            renderChatBackgrounds()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            setChatKeyboardState(imeVisible, imeBottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setChatKeyboardState(compact: Boolean, bottomInset: Int) {
        if (
            isChatKeyboardCompact == compact &&
            chatKeyboardBottomInset == bottomInset &&
            chatPanel.isLaidOut
        ) {
            applyKeyboardAwarePlayerPanel()
            return
        }
        isChatKeyboardCompact = compact
        chatKeyboardBottomInset = bottomInset
        applyChatPanelDimensions()
        applyKeyboardAwarePlayerPanel()
        if (compact && isChatOpen && !isClosingForInteractivePhase) {
            chatPanel.bringToFront()
            chatPanel.visibility = View.VISIBLE
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun shouldCompactBottomPlayerPanel(): Boolean {
        return isChatOpen && isChatKeyboardCompact && chatInput.hasFocus()
    }

    private fun applyKeyboardAwarePlayerPanel() {
        val compact = shouldCompactBottomPlayerPanel()
        if (compact) {
            isBottomPlayerPanelCompact = true
            compactBottomPlayerPanelForKeyboard()
        } else if (isBottomPlayerPanelCompact || !bottomPlayerPanel.isLaidOut) {
            isBottomPlayerPanelCompact = false
            restoreBottomPlayerPanelFromKeyboard()
        } else {
            isBottomPlayerPanelCompact = false
        }
    }

    private fun compactBottomPlayerPanelForKeyboard() {
        bottomPlayerPanel.layoutParams = bottomPlayerPanel.layoutParams.apply {
            height = host.dp(BOTTOM_PLAYER_PANEL_COMPACT_HEIGHT_DP)
        }
        bottomPlayerPanel.gravity = Gravity.CENTER
        bottomPlayerPanel.setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4))
        roleCard.visibility = View.GONE
        currentPlayerName.visibility = View.GONE
        currentPlayerStatus.visibility = View.GONE
        currentPlayerHint.visibility = View.GONE
        actionControls.visibility = View.GONE
        eliminatedStatePanel.visibility = View.GONE
        chatRoleChip.text = compactRoleChipText()
        chatRoleChip.visibility = View.VISIBLE
        roleName.visibility = View.VISIBLE
        roleName.text = compactRoleChipText()
        roleName.gravity = Gravity.CENTER
        roleName.maxLines = 1
        roleName.setPadding(host.dp(10), 0, host.dp(10), 0)
        roleName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        roleName.background = compactRoleChipBackground()
    }

    private fun restoreBottomPlayerPanelFromKeyboard() {
        bottomPlayerPanel.layoutParams = bottomPlayerPanel.layoutParams.apply {
            height = host.dp(BOTTOM_PLAYER_PANEL_HEIGHT_DP)
        }
        bottomPlayerPanel.gravity = Gravity.CENTER
        bottomPlayerPanel.setPadding(host.dp(8), host.dp(6), host.dp(8), host.dp(6))
        roleCard.visibility = View.VISIBLE
        currentPlayerName.visibility = View.VISIBLE
        currentPlayerHint.visibility = View.VISIBLE
        roleName.gravity = Gravity.NO_GRAVITY
        roleName.setPadding(0, 0, 0, 0)
        roleName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        roleName.background = null
        chatRoleChip.visibility = View.GONE
        host.renderHumanCardIfVisible()
        host.renderPersonalStatus()
    }

    private fun compactRoleChipText(): String {
        return GameEngine.humanPlayer(host.currentSession).role?.name?.uppercase() ?: "SIN ROL"
    }

    private fun compactRoleChipBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#E6231810"))
            setStroke(host.dp(1), root.context.getColor(R.color.accent_gold))
            cornerRadius = host.dp(8).toFloat()
        }
    }

    private fun applyChatPanelDimensions() {
        if (root.width == 0) return
        val params = chatPanel.layoutParams
        val heightRatio = if (isChatKeyboardCompact) {
            CHAT_SHEET_COMPACT_HEIGHT_RATIO
        } else {
            CHAT_SHEET_HEIGHT_RATIO
        }
        params.width = (centerColumn.width.takeIf { it > 0 } ?: (root.width - host.dp(140)))
            .coerceAtLeast(host.dp(210))
        params.height = host.dp((root.resources.configuration.screenHeightDp * heightRatio).toInt())
            .coerceIn(host.dp(CHAT_SHEET_MIN_HEIGHT_DP), host.dp(CHAT_SHEET_MAX_HEIGHT_DP))
        (params as? ViewGroup.MarginLayoutParams)?.apply {
            marginStart = 0
            marginEnd = 0
            topMargin = 0
            bottomMargin = 0
        }
        (params as? RelativeLayout.LayoutParams)?.apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
            addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        }
        (params as? FrameLayout.LayoutParams)?.apply {
            gravity = Gravity.CENTER
        }
        chatPanel.layoutParams = params
        chatPanelContent.setPadding(host.dp(12), host.dp(11), host.dp(12), host.dp(11))
        chatHeader.layoutParams = chatHeader.layoutParams.apply {
            height = host.dp(36)
        }
        chatComposer.layoutParams = chatComposer.layoutParams.apply {
            height = host.dp(44)
        }
        chatStatusRow.layoutParams = chatStatusRow.layoutParams.apply {
            height = host.dp(22)
        }
        chatInput.layoutParams = chatInput.layoutParams.apply {
            height = host.dp(44)
        }
        btnSendChat.layoutParams = btnSendChat.layoutParams.apply {
            height = host.dp(44)
        }
    }

    private fun renderAmbientChatFeed() {
        if (isChatOpen) {
            chatAmbientFeed.visibility = View.GONE
            return
        }

        val channel = activeChatChannel()
        val entries = ChronicleFeedPresenter.entries(
            activeChannelMessages(channel).takeLast(CHAT_AMBIENT_SOURCE_LIMIT)
        )
            .filterNot { it.kind == ChronicleEntryKind.DAY_DIVIDER }
            .takeLast(CHAT_AMBIENT_MAX_MESSAGES)
        val canChat = canHumanChatInChannel(channel)
        val renderKey = listOf(
            channel.name,
            entries,
            canChat,
            host.currentSession.phase.name,
            host.gameplayTextScale
        ).joinToString("|")
        if (entries.isEmpty() && !canChat) {
            chatAmbientFeed.visibility = View.GONE
            lastAmbientFeedRenderKey = renderKey
            return
        }

        if (lastAmbientFeedRenderKey == renderKey && chatAmbientMessages.childCount > 0) {
            chatAmbientFeed.visibility = View.VISIBLE
            return
        }
        lastAmbientFeedRenderKey = renderKey

        renderChatBackgrounds()
        renderChatTitle()
        chatAmbientMessages.removeAllViews()
        if (entries.isEmpty()) {
            chatAmbientMessages.addView(createAmbientPlaceholderRow())
        } else {
            entries.forEach { entry ->
                chatAmbientMessages.addView(createAmbientFeedRow(entry))
            }
        }
        chatAmbientHint.text = if (canChat) {
            when (channel) {
                ChatChannel.PUBLICO -> "Toca para hablar"
                ChatChannel.TRAIDORES -> "Toca para tramar"
                ChatChannel.ESPECTADORES -> "Toca para hablar"
            }
        } else {
            chatInputHint(canChat, channel)
        }
        chatAmbientHint.visibility = View.VISIBLE

        if (chatAmbientFeed.visibility != View.VISIBLE) {
            chatAmbientFeed.visibility = View.VISIBLE
            chatAmbientFeed.alpha = 0f
            chatAmbientFeed.translationY = host.dp(6).toFloat()
            chatAmbientFeed.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        }
    }

    private fun createAmbientPlaceholderRow(): View {
        val channel = activeChatChannel()
        return TextView(root.context).apply {
            text = when (channel) {
                ChatChannel.PUBLICO -> "El pueblo aun no hablo"
                ChatChannel.TRAIDORES -> "El plan aun no tiene notas"
                ChatChannel.ESPECTADORES -> "Los muertos todavía no hablaron"
            }
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(
                root.context.getColor(
                    when (channel) {
                        ChatChannel.PUBLICO -> R.color.text_secondary
                        ChatChannel.TRAIDORES -> R.color.traitor_text
                        ChatChannel.ESPECTADORES -> R.color.espectro_muted
                    }
                )
            )
            textSize = 12f * host.gameplayTextScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(0, host.dp(2), 0, host.dp(2))
        }
    }

    private fun createAmbientFeedRow(entry: ChronicleEntry): View {
        if (entry.kind != ChronicleEntryKind.PLAYER) return createAmbientEventRow(entry)
        val channel = activeChatChannel()
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(2), 0, host.dp(2))
        }
        val speakerName = entry.speaker.orEmpty()
        val speaker = TextView(root.context).apply {
            text = "$speakerName:"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(
                when (channel) {
                    ChatChannel.PUBLICO -> PlayerChatColor.colorFor(speakerName, host.currentSession)
                    ChatChannel.TRAIDORES -> root.context.getColor(R.color.traitor_red_bright)
                    ChatChannel.ESPECTADORES -> root.context.getColor(R.color.espectro_blue_bright)
                }
            )
            textSize = 11.5f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextView(root.context).apply {
            text = entry.text
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(
                root.context.getColor(
                    when (channel) {
                        ChatChannel.PUBLICO -> R.color.text_primary
                        ChatChannel.TRAIDORES -> R.color.traitor_text
                        ChatChannel.ESPECTADORES -> R.color.espectro_text
                    }
                )
            )
            textSize = 12f * host.gameplayTextScale
        }
        row.addView(
            speaker,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = host.dp(4)
            }
        )
        row.addView(
            body,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun createAmbientGodRow(message: GameChatMessage): View {
        return TextView(root.context).apply {
            text = "✦ ${message.message}"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(root.context.getColor(R.color.accent_gold))
            textSize = 11.5f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
            setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(2))
        }
    }

    private fun createAmbientEventRow(entry: ChronicleEntry): View {
        val event = eventPresentationFor(entry)
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(2), 0, host.dp(2))
        }
        val iconView = ImageView(root.context).apply {
            setImageResource(event.iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(host.dp(2), host.dp(2), host.dp(2), host.dp(2))
            if (event.tintIcon) setColorFilter(event.iconColor)
        }
        iconView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = host.dp(5).toFloat()
            setColor(event.backgroundColor)
            setStroke(host.dp(1), event.strokeColor)
        }
        row.addView(iconView, LinearLayout.LayoutParams(host.dp(18), host.dp(18)).apply {
            marginEnd = host.dp(6)
        })
        row.addView(TextView(root.context).apply {
            text = entry.text
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(event.iconColor)
            textSize = 11.5f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun renderChatTitle() {
        when (activeChatChannel()) {
            ChatChannel.TRAIDORES -> {
                chatAmbientTitle.text = "CHAT DE LOS ASESINOS"
                chatFeedTitle.text = "CHAT DE LOS ASESINOS"
                chatFeedTitle.maxLines = 1
                chatFeedTitle.ellipsize = TextUtils.TruncateAt.END
                chatFeedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                chatAmbientTitle.setTextColor(root.context.getColor(R.color.traitor_red_bright))
                chatFeedTitle.setTextColor(root.context.getColor(R.color.traitor_red_bright))
                renderTraitorHeaderChip()
                return
            }
            ChatChannel.ESPECTADORES -> {
                chatAmbientTitle.text = "CHAT DE LOS MUERTOS"
                chatFeedTitle.text = "CHAT DE LOS MUERTOS"
                chatFeedTitle.maxLines = 1
                chatFeedTitle.ellipsize = TextUtils.TruncateAt.END
                chatFeedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                chatAmbientTitle.setTextColor(root.context.getColor(R.color.espectro_blue_bright))
                chatFeedTitle.setTextColor(root.context.getColor(R.color.espectro_blue_bright))
                renderSpectatorHeaderChip()
                return
            }
            ChatChannel.PUBLICO -> Unit
        }
        val (compactTitle, expandedTitle) = when (host.currentSession.mapKey) {
            "grecia" -> "QUE SE DICE EN LA POLIS..." to "CRONISTA DE LA POLIS"
            "medieval" -> "QUE SE DICE EN EL FEUDO..." to "CRONISTA DEL FEUDO"
            else -> "QUE SE DICE EN EL PUEBLO..." to "CRONISTA DEL PUEBLO"
        }
        chatAmbientTitle.text = compactTitle
        chatFeedTitle.text = expandedTitle
        chatFeedTitle.maxLines = 1
        chatFeedTitle.ellipsize = TextUtils.TruncateAt.END
        chatFeedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        chatAmbientTitle.setTextColor(root.context.getColor(R.color.accent_gold))
        chatFeedTitle.setTextColor(root.context.getColor(R.color.accent_gold))
        if (!shouldCompactBottomPlayerPanel()) {
            chatRoleChip.visibility = View.GONE
        }
    }

    private fun renderTraitorHeaderChip() {
        val traitors = GameEngine.aliveTraitors(host.currentSession)
        if (traitors.isEmpty()) {
            chatRoleChip.visibility = View.GONE
            return
        }
        chatRoleChip.visibility = View.VISIBLE
        chatRoleChip.maxWidth = host.dp(88)
        chatRoleChip.text = if (traitors.size == 1) {
            "SOLO VOS"
        } else {
            "${traitors.size} MALOS"
        }
        chatRoleChip.setTextColor(root.context.getColor(R.color.traitor_text))
        chatRoleChip.background = roundedBackground(
            fillColor = root.context.getColor(R.color.traitor_panel),
            strokeColor = root.context.getColor(R.color.traitor_red),
            cornerRadiusDp = 8
        )
    }

    private fun renderQuickReplies(channel: ChatChannel, canChat: Boolean) {
        val canShowQuickChat = canChat && when (channel) {
            ChatChannel.PUBLICO ->
                host.isOnlineGameplay() || directorPendingHumanMessage.isBlank()
            ChatChannel.TRAIDORES -> true
            ChatChannel.ESPECTADORES -> false
        }
        val traitorStyle = channel == ChatChannel.TRAIDORES
        val replies = when {
            !canShowQuickChat -> emptyList()
            traitorStyle -> BotQuickReplies.forTraitorChat(host.currentSession)
            else -> BotQuickReplies.forSession(host.currentSession)
        }
        chatQuickReplies.removeAllViews()
        chatQuickRepliesScroll.visibility = if (canShowQuickChat) View.VISIBLE else View.GONE
        replies.forEach { reply ->
            addQuickChatButton(reply.text, traitorStyle = traitorStyle) {
                handleQuickChatMessage(reply)
            }
        }
        if (canShowQuickChat) {
            addQuickChatButton("MÁS", emphasized = true, traitorStyle = traitorStyle) {
                if (traitorStyle) {
                    showTraitorQuickMessageCategories()
                } else {
                    showQuickMessageCategories()
                }
            }
        }
    }

    private fun addQuickChatButton(
        label: String,
        emphasized: Boolean = false,
        traitorStyle: Boolean = false,
        onClick: () -> Unit
    ) {
        val accentColor = root.context.getColor(
            if (traitorStyle) R.color.traitor_red_bright else R.color.accent_gold
        )
        val button = Button(root.context).apply {
            text = label
            isAllCaps = false
            setTextColor(
                when {
                    emphasized -> root.context.getColor(R.color.bg_dark)
                    traitorStyle -> root.context.getColor(R.color.traitor_text)
                    else -> root.context.getColor(R.color.text_primary)
                }
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(host.dp(10), 0, host.dp(10), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(
                    when {
                        emphasized -> accentColor
                        traitorStyle -> root.context.getColor(R.color.traitor_panel)
                        else -> Color.parseColor("#E6211810")
                    }
                )
                setStroke(host.dp(1), accentColor)
                cornerRadius = host.dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }
        chatQuickReplies.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                host.dp(32)
            ).apply {
                marginEnd = host.dp(6)
            }
        )
    }

    private fun showQuickMessageCategories() {
        host.hideKeyboard()
        showQuickChoiceDialog(
            title = "MENSAJES RÁPIDOS",
            options = listOf(
                "Sospechar de...",
                "Defender a...",
                "Preguntar...",
                "Decir mi rol...",
                "Informar una acción...",
                "Estrategia de voto..."
            )
        ) { index ->
            when (index) {
                0 -> showAlivePlayerPicker("¿DE QUIÉN SOSPECHÁS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.suspect(target))
                }
                1 -> showAlivePlayerPicker("¿A QUIÉN DEFENDÉS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.defend(target))
                }
                2 -> showQuickQuestionMenu()
                3 -> showQuickRolePicker()
                4 -> showQuickActionMenu()
                5 -> showQuickVoteMenu()
            }
        }
    }

    private fun showTraitorQuickMessageCategories() {
        host.hideKeyboard()
        showQuickChoiceDialog(
            title = "PLAN DE LOS ASESINOS",
            options = listOf(
                "Matemos a...",
                "A ese no...",
                "Silenciemos a...",
                "Cuidado con...",
                "Cúbranme...",
                "Cerrado, quedamos así"
            )
        ) { index ->
            when (index) {
                0 -> showTraitorTargetPicker("¿A QUIÉN MATAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.killProposal(target))
                }
                1 -> showTraitorTargetPicker("¿A QUIÉN NO TOCAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.doubtKill(target))
                }
                2 -> showTraitorTargetPicker("¿A QUIÉN SILENCIAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.silence(target))
                }
                3 -> showTraitorTargetPicker("¿DE QUIÉN NOS CUIDAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.watchOut(target))
                }
                4 -> showTraitorCoverMenu()
                5 -> sendQuickChatMessage(BotQuickReplies.confirmPlan())
            }
        }
    }

    private fun showTraitorCoverMenu() {
        showQuickChoiceDialog(
            title = "CÚBRANME",
            options = listOf(
                "Me están marcando, cúbranme",
                "Mañana digo que soy...",
                "Estoy limpio, hablo yo",
                "Mañana no nos crucemos",
                "Vamos tranquilos, sin regalarnos"
            )
        ) { index ->
            when (index) {
                0 -> sendQuickChatMessage(BotQuickReplies.askCover())
                1 -> showTraitorFakeRolePicker()
                2 -> sendQuickChatMessage(BotQuickReplies.cleanCover())
                3 -> sendQuickChatMessage(BotQuickReplies.noCrossfire())
                4 -> sendQuickChatMessage(BotQuickReplies.stayCalm())
            }
        }
    }

    private fun showTraitorFakeRolePicker() {
        val roles = BotQuickReplies.townRolesInPlay(host.currentSession)
        if (roles.isEmpty()) {
            host.showToast("No hay roles del pueblo para usar de coartada.")
            return
        }
        showQuickChoiceDialog(
            title = "ROL FALSO",
            options = roles.map { it.name }
        ) { index ->
            roles.getOrNull(index)?.let { role ->
                sendQuickChatMessage(BotQuickReplies.fakeClaim(role))
            }
        }
    }

    private fun showTraitorTargetPicker(
        title: String,
        onSelected: (String) -> Unit
    ) {
        val players = BotQuickReplies.traitorTargets(host.currentSession)
        if (players.isEmpty()) {
            host.showToast("No quedan objetivos fuera del plan.")
            return
        }
        showQuickChoiceDialog(
            title = title,
            options = players.map { it.name }
        ) { index ->
            players.getOrNull(index)?.name?.let(onSelected)
        }
    }

    private fun showQuickQuestionMenu() {
        showQuickChoiceDialog(
            title = "PREGUNTAR",
            options = listOf(
                "¿Qué rol sos?",
                "¿A quién votarías?",
                "¿Por qué sospechás de mí?"
            )
        ) { index ->
            showAlivePlayerPicker("ELEGÍ A QUIÉN PREGUNTAR") { target ->
                val message = when (index) {
                    0 -> BotQuickReplies.askRole(target)
                    1 -> BotQuickReplies.askVote(target)
                    else -> BotQuickReplies.askExplanation(target)
                }
                sendQuickChatMessage(message)
            }
        }
    }

    private fun showQuickRolePicker() {
        val roles = BotQuickReplies.rolesInPlay(host.currentSession)
        if (roles.isEmpty()) {
            host.showToast("Todavía no hay roles disponibles.")
            return
        }
        showQuickChoiceDialog(
            title = "DECIR MI ROL",
            options = roles.map { it.name }
        ) { index ->
            roles.getOrNull(index)?.let { role ->
                sendQuickChatMessage(BotQuickReplies.claimRole(role))
            }
        }
    }

    private fun showQuickActionMenu() {
        showQuickChoiceDialog(
            title = "INFORMAR UNA ACCIÓN",
            options = listOf(
                "Investigación...",
                "Protección..."
            )
        ) { index ->
            when (index) {
                0 -> showAlivePlayerPicker("¿A QUIÉN INVESTIGASTE?") { target ->
                    showQuickChoiceDialog(
                        title = "RESULTADO DE $target",
                        options = listOf("Inocente", "Sospechoso")
                    ) { resultIndex ->
                        sendQuickChatMessage(
                            BotQuickReplies.investigation(
                                target = target,
                                suspicious = resultIndex == 1
                            )
                        )
                    }
                }
                1 -> showAlivePlayerPicker("¿A QUIÉN PROTEGISTE?") { target ->
                    sendQuickChatMessage(BotQuickReplies.protection(target))
                }
            }
        }
    }

    private fun showQuickVoteMenu() {
        showQuickChoiceDialog(
            title = "ESTRATEGIA DE VOTO",
            options = listOf(
                "Votaría a...",
                "No votemos apurados",
                "Quiero escuchar a..."
            )
        ) { index ->
            when (index) {
                0 -> showAlivePlayerPicker("¿A QUIÉN VOTARÍAS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.voteFor(target))
                }
                1 -> sendQuickChatMessage(BotQuickReplies.holdVote())
                2 -> showAlivePlayerPicker("¿A QUIÉN QUERÉS ESCUCHAR?") { target ->
                    sendQuickChatMessage(BotQuickReplies.hearFirst(target))
                }
            }
        }
    }

    private fun showAlivePlayerPicker(
        title: String,
        onSelected: (String) -> Unit
    ) {
        val players = BotQuickReplies.aliveTargets(host.currentSession)
        if (players.isEmpty()) {
            host.showToast("No hay otros jugadores vivos para elegir.")
            return
        }
        showQuickChoiceDialog(
            title = title,
            options = players.map { it.name }
        ) { index ->
            players.getOrNull(index)?.name?.let(onSelected)
        }
    }

    private fun showQuickChoiceDialog(
        title: String,
        options: List<String>,
        onSelected: (Int) -> Unit
    ) {
        if (options.isEmpty()) return
        val activity = root.context as? Activity ?: return
        val traitorPlan = activeChatChannel() == ChatChannel.TRAIDORES
        quickChatDialog?.dismiss()
        quickChatDialog = GameDialog.choose(
            activity = activity,
            title = title,
            message = if (traitorPlan) {
                "Elegí una opción para enviarla al plan."
            } else {
                "Elegí una opción para enviarla al chat."
            },
            options = options,
            theme = if (traitorPlan) GameDialogTheme.TRAITOR else GameDialogTheme.GOLD,
            onSelected = onSelected
        )
    }

    private fun handleQuickChatMessage(message: QuickChatMessage) {
        when (message.action) {
            QuickChatAction.SEND -> sendQuickChatMessage(message)
            QuickChatAction.CHOOSE_SUSPECT -> {
                showAlivePlayerPicker("¿DE QUIÉN SOSPECHÁS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.suspect(target))
                }
            }
            QuickChatAction.CHOOSE_ROLE -> showQuickRolePicker()
            QuickChatAction.CHOOSE_VOTE -> {
                showAlivePlayerPicker("¿A QUIÉN VOTAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.voteTogether(target))
                }
            }
            QuickChatAction.CHOOSE_KILL -> {
                showTraitorTargetPicker("¿A QUIÉN MATAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.killProposal(target))
                }
            }
            QuickChatAction.CHOOSE_SILENCE -> {
                showTraitorTargetPicker("¿A QUIÉN SILENCIAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.silence(target))
                }
            }
            QuickChatAction.CHOOSE_WATCH -> {
                showTraitorTargetPicker("¿DE QUIÉN NOS CUIDAMOS?") { target ->
                    sendQuickChatMessage(BotQuickReplies.watchOut(target))
                }
            }
        }
    }

    private fun sendQuickChatMessage(message: QuickChatMessage) {
        chatInput.setText(message.text)
        chatInput.setSelection(chatInput.text.length)
        sendHumanChatMessage(message.intentHint)
    }

    private fun renderSpectatorHeaderChip() {
        val deadPlayers = host.currentSession.players.count { !it.alive }
        chatRoleChip.visibility = View.VISIBLE
        chatRoleChip.maxWidth = host.dp(112)
        chatRoleChip.text = if (deadPlayers == 1) {
            "1 MUERTO"
        } else {
            "$deadPlayers MUERTOS"
        }
        chatRoleChip.setTextColor(root.context.getColor(R.color.espectro_text))
        chatRoleChip.background = roundedBackground(
            fillColor = root.context.getColor(R.color.espectro_panel),
            strokeColor = root.context.getColor(R.color.espectro_blue),
            cornerRadiusDp = 8
        )
    }

    private fun cronistaTypeface(): Typeface {
        return ResourcesCompat.getFont(root.context, R.font.bree_serif) ?: Typeface.DEFAULT_BOLD
    }

    private data class EventPresentation(
        val label: String,
        val backgroundColor: Int,
        val strokeColor: Int,
        val iconColor: Int,
        val iconRes: Int = R.drawable.ic_chronicle_crest,
        val tintIcon: Boolean = true
    )

    private fun eventPresentationFor(entry: ChronicleEntry): EventPresentation {
        val channel = activeChatChannel()
        if (channel == ChatChannel.TRAIDORES) {
            val bright = root.context.getColor(R.color.traitor_red_bright)
            val normalized = GameplayTextMarkers.normalize(entry.text)
            val isTarget = "objetivo del plan" in normalized || "cambio de plan" in normalized
            return EventPresentation(
                label = if (isTarget) "OBJETIVO" else "PLAN",
                backgroundColor = root.context.getColor(R.color.traitor_panel),
                strokeColor = root.context.getColor(R.color.traitor_red),
                iconColor = bright,
                iconRes = if (isTarget) {
                    R.drawable.ic_chronicle_target
                } else {
                    R.drawable.ic_chronicle_dagger
                }
            )
        }
        if (channel == ChatChannel.ESPECTADORES) {
            return EventPresentation(
                label = "MUERTOS",
                backgroundColor = root.context.getColor(R.color.espectro_panel),
                strokeColor = root.context.getColor(R.color.espectro_blue),
                iconColor = root.context.getColor(R.color.espectro_blue_bright),
                iconRes = R.drawable.ic_chronicle_ghost
            )
        }
        val gold = root.context.getColor(R.color.accent_gold)
        return when (entry.kind) {
            ChronicleEntryKind.ROLE_COMPOSITION -> EventPresentation(
                label = "ROLES",
                backgroundColor = Color.parseColor("#55401F"),
                strokeColor = Color.parseColor("#D6AE52"),
                iconColor = Color.parseColor("#F2D483"),
                iconRes = R.drawable.ic_chronicle_roles
            )
            ChronicleEntryKind.DEATH -> EventPresentation(
                label = "MUERTE",
                backgroundColor = Color.parseColor("#7A2A22"),
                strokeColor = Color.parseColor("#B46A72"),
                iconColor = Color.parseColor("#F0B2A8"),
                iconRes = R.drawable.death_blood_splatter_art,
                tintIcon = false
            )
            ChronicleEntryKind.EXPULSION -> EventPresentation(
                label = "EXPULSION",
                backgroundColor = Color.parseColor("#5F4524"),
                strokeColor = gold,
                iconColor = gold,
                iconRes = R.drawable.expulsion_seal,
                tintIcon = false
            )
            ChronicleEntryKind.VOTE -> EventPresentation(
                label = "VOTACION",
                backgroundColor = Color.parseColor("#5F4524"),
                strokeColor = gold,
                iconColor = gold,
                iconRes = R.drawable.expulsion_seal,
                tintIcon = false
            )
            ChronicleEntryKind.NIGHT -> EventPresentation(
                label = "NOCHE",
                backgroundColor = Color.parseColor("#25334F"),
                strokeColor = Color.parseColor("#6B86B8"),
                iconColor = Color.parseColor("#B7C7E8"),
                iconRes = R.drawable.ic_chronicle_moon
            )
            ChronicleEntryKind.DAWN -> EventPresentation(
                label = "AMANECER",
                backgroundColor = Color.parseColor("#6B5525"),
                strokeColor = Color.parseColor("#E3C46F"),
                iconColor = Color.parseColor("#F4D77D"),
                iconRes = R.drawable.ic_chronicle_sun
            )
            ChronicleEntryKind.SILENCE -> EventPresentation(
                label = "SILENCIO",
                backgroundColor = Color.parseColor("#4F3140"),
                strokeColor = Color.parseColor("#A26A88"),
                iconColor = Color.parseColor("#E6B6CE"),
                iconRes = R.drawable.ic_chronicle_silence
            )
            ChronicleEntryKind.TIE -> EventPresentation(
                label = "EMPATE",
                backgroundColor = Color.parseColor("#4B3B22"),
                strokeColor = gold,
                iconColor = gold,
                iconRes = R.drawable.ic_chronicle_balance
            )
            ChronicleEntryKind.SPECIAL_VICTORY -> EventPresentation(
                label = "ESPECIAL",
                backgroundColor = Color.parseColor("#493058"),
                strokeColor = Color.parseColor("#C392E6"),
                iconColor = Color.parseColor("#E2C8F8"),
                iconRes = R.drawable.ic_chronicle_crown
            )
            else -> EventPresentation(
                label = "SUCESO",
                backgroundColor = Color.parseColor("#4A3518"),
                strokeColor = gold,
                iconColor = gold,
                iconRes = R.drawable.ic_chronicle_crest,
                tintIcon = false
            )
        }
    }

    private fun createDayDivider(entry: ChronicleEntry): View {
        val channel = activeChatChannel()
        return TextView(root.context).apply {
            text = when (channel) {
                ChatChannel.TRAIDORES -> entry.text.replace("DIA", "NOCHE")
                ChatChannel.PUBLICO,
                ChatChannel.ESPECTADORES -> entry.text
            }
            gravity = Gravity.CENTER
            setTextColor(
                root.context.getColor(
                    when (channel) {
                        ChatChannel.PUBLICO -> R.color.accent_gold
                        ChatChannel.TRAIDORES -> R.color.traitor_red_bright
                        ChatChannel.ESPECTADORES -> R.color.espectro_blue_bright
                    }
                )
            )
            textSize = 9f * host.gameplayTextScale
            typeface = cronistaTypeface()
            setPadding(0, host.dp(7), 0, host.dp(5))
        }
    }

    private fun renderChatMessages(messages: List<GameChatMessage>, channel: ChatChannel) {
        val recentMessages = messages.takeLast(CHAT_EXPANDED_SOURCE_LIMIT)
        val renderKey = listOf(
            channel.name,
            recentMessages,
            showOnlyEvents,
            typingBotSpeakers.toList(),
            host.gameplayTextScale,
            chatPanel.width
        ).joinToString("|")
        if (lastExpandedChatRenderKey == renderKey && chatMessagesContainer.childCount > 0) {
            return
        }
        lastExpandedChatRenderKey = renderKey
        chatMessagesContainer.removeAllViews()
        val entries = ChronicleFeedPresenter.entries(
            recentMessages,
            showOnlyEvents && channel == ChatChannel.PUBLICO
        )
            .filterNot { channel == ChatChannel.TRAIDORES && it.kind == ChronicleEntryKind.DAY_DIVIDER }
            .takeLast(24)
        val typingSpeakers = typingBotSpeakers.filter { speaker ->
            typingSpeakerBelongsToChannel(speaker, channel)
        }
        if (entries.isEmpty() && typingSpeakers.isEmpty()) {
            chatMessagesContainer.addView(TextView(root.context).apply {
                text = when {
                    channel == ChatChannel.TRAIDORES -> "Todavia no hay plan."
                    channel == ChatChannel.ESPECTADORES -> "Los muertos todavía no hablaron"
                    showOnlyEvents -> "Todavia no hay sucesos."
                    else -> "Todavia no hay mensajes."
                }
                gravity = Gravity.CENTER
                setPadding(host.dp(8), host.dp(16), host.dp(8), host.dp(16))
                setTextColor(
                    root.context.getColor(
                        when (channel) {
                            ChatChannel.PUBLICO -> R.color.text_secondary
                            ChatChannel.TRAIDORES -> R.color.traitor_text
                            ChatChannel.ESPECTADORES -> R.color.espectro_muted
                        }
                    )
                )
                textSize = 12f * host.gameplayTextScale
            })
            return
        }

        val humanName = GameEngine.humanPlayer(host.currentSession).name
        val bubbleMaxWidth = ((chatPanel.width.takeIf { it > 0 } ?: host.dp(360)) - host.dp(56))
            .coerceIn(host.dp(190), host.dp(420))
        entries.forEach { entry ->
            when (entry.kind) {
                ChronicleEntryKind.DAY_DIVIDER -> {
                    chatMessagesContainer.addView(createDayDivider(entry))
                    return@forEach
                }
                ChronicleEntryKind.PLAYER -> Unit
                else -> {
                    addGodEventBanner(entry)
                    return@forEach
                }
            }
            val speakerName = entry.speaker.orEmpty()
            val ownMessage = speakerName == humanName
            addChatBubble(
                speaker = if (ownMessage) "VOS" else speakerName.uppercase(),
                body = entry.text,
                speakerColor = if (ownMessage) {
                    root.context.getColor(
                        when (channel) {
                            ChatChannel.PUBLICO -> R.color.bg_dark
                            ChatChannel.TRAIDORES -> R.color.traitor_text
                            ChatChannel.ESPECTADORES -> R.color.espectro_blue_bright
                        }
                    )
                } else {
                    when (channel) {
                        ChatChannel.PUBLICO -> PlayerChatColor.colorFor(speakerName, host.currentSession)
                        ChatChannel.TRAIDORES -> root.context.getColor(R.color.traitor_red_bright)
                        ChatChannel.ESPECTADORES -> root.context.getColor(R.color.espectro_blue_bright)
                    }
                },
                ownMessage = ownMessage,
                bubbleMaxWidth = bubbleMaxWidth,
                muted = false
            )
        }
        typingSpeakers.forEach { speaker ->
            addChatBubble(
                speaker = speaker.uppercase(),
                body = "esta escribiendo...",
                speakerColor = PlayerChatColor.colorFor(speaker, host.currentSession),
                ownMessage = false,
                bubbleMaxWidth = bubbleMaxWidth,
                muted = true
            )
        }
    }

    private fun typingSpeakerBelongsToChannel(speaker: String, channel: ChatChannel): Boolean {
        val player = GameEngine.playerByName(host.currentSession, speaker) ?: return false
        return when (channel) {
            ChatChannel.PUBLICO -> !GameRules.isTraitorRole(player.role) ||
                BotConversationDirector.canRun(host.currentSession)
            ChatChannel.TRAIDORES -> GameEngine.canSeeTraitorChat(player)
            ChatChannel.ESPECTADORES -> false
        }
    }

    private fun toggleFeedFilter() {
        if (canUseSpectatorChatUi(host.currentSession)) {
            selectedChatChannel = if (activeChatChannel() == ChatChannel.ESPECTADORES) {
                ChatChannel.PUBLICO
            } else {
                ChatChannel.ESPECTADORES
            }
            showOnlyEvents = false
            renderFeedFilterButton()
            renderChatPanel()
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
            return
        }
        if (canToggleTraitorChannel()) {
            selectedChatChannel = if (activeChatChannel() == ChatChannel.TRAIDORES) {
                ChatChannel.PUBLICO
            } else {
                ChatChannel.TRAIDORES
            }
            showOnlyEvents = false
            renderFeedFilterButton()
            renderChatPanel()
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
            return
        }
        showOnlyEvents = !showOnlyEvents
        renderFeedFilterButton()
        renderChatPanel()
        chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderFeedFilterButton() {
        val channel = activeChatChannel()
        if (canUseSpectatorChatUi(host.currentSession)) {
            btnChatFeedFilter.visibility = View.VISIBLE
            btnChatFeedFilter.isEnabled = true
            btnChatFeedFilter.text = if (channel == ChatChannel.ESPECTADORES) "PUEBLO" else "MUERTOS"
            btnChatFeedFilter.contentDescription = if (channel == ChatChannel.ESPECTADORES) {
                "Volver al chat del pueblo"
            } else {
                "Hablar con los muertos"
            }
            btnChatFeedFilter.alpha = 1f
            btnChatFeedFilter.background = roundedBackground(
                fillColor = root.context.getColor(
                    if (channel == ChatChannel.ESPECTADORES) R.color.espectro_panel else R.color.btn_dark
                ),
                strokeColor = root.context.getColor(
                    if (channel == ChatChannel.ESPECTADORES) R.color.espectro_blue else R.color.btn_dark_border
                ),
                cornerRadiusDp = 7
            )
            btnChatFeedFilter.setTextColor(
                root.context.getColor(
                    if (channel == ChatChannel.ESPECTADORES) R.color.espectro_text else R.color.text_primary
                )
            )
            return
        }
        if (canToggleTraitorChannel()) {
            btnChatFeedFilter.visibility = View.VISIBLE
            btnChatFeedFilter.isEnabled = true
            btnChatFeedFilter.text = if (channel == ChatChannel.TRAIDORES) "PUEBLO" else "PLAN"
            btnChatFeedFilter.contentDescription = if (channel == ChatChannel.TRAIDORES) {
                "Volver al chat del pueblo"
            } else {
                "Ver Plan de los Asesinos"
            }
            btnChatFeedFilter.alpha = 1f
            btnChatFeedFilter.background = roundedBackground(
                fillColor = root.context.getColor(
                    if (channel == ChatChannel.TRAIDORES) R.color.traitor_panel else R.color.btn_dark
                ),
                strokeColor = root.context.getColor(
                    if (channel == ChatChannel.TRAIDORES) R.color.traitor_red else R.color.btn_dark_border
                ),
                cornerRadiusDp = 7
            )
            btnChatFeedFilter.setTextColor(
                root.context.getColor(
                    if (channel == ChatChannel.TRAIDORES) R.color.traitor_text else R.color.text_primary
                )
            )
            return
        }
        if (channel != ChatChannel.PUBLICO) {
            btnChatFeedFilter.visibility = View.GONE
            btnChatFeedFilter.isEnabled = false
            return
        }
        btnChatFeedFilter.text = if (showOnlyEvents) "SUCESOS" else "TODO"
        btnChatFeedFilter.contentDescription = if (showOnlyEvents) {
            "Mostrar todo el feed"
        } else {
            "Mostrar solo sucesos"
        }
        btnChatFeedFilter.visibility = View.VISIBLE
        btnChatFeedFilter.isEnabled = true
        btnChatFeedFilter.alpha = if (showOnlyEvents) 1f else 0.82f
        btnChatFeedFilter.setBackgroundResource(R.drawable.bg_btn_dark)
        btnChatFeedFilter.setTextColor(root.context.getColor(R.color.text_primary))
    }

    private fun addGodEventBanner(entry: ChronicleEntry) {
        val event = eventPresentationFor(entry)
        val row = LinearLayout(root.context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(4), 0, host.dp(4))
        }
        val banner = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(host.dp(10), host.dp(7), host.dp(10), host.dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = host.dp(10).toFloat()
                setColor(event.backgroundColor)
                setStroke(host.dp(1), event.strokeColor)
            }
        }
        val eventHeader = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        eventHeader.addView(
            ImageView(root.context).apply {
                setImageResource(event.iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (event.tintIcon) setColorFilter(event.iconColor)
                contentDescription = null
            },
            LinearLayout.LayoutParams(host.dp(17), host.dp(17)).apply {
                marginEnd = host.dp(5)
            }
        )
        eventHeader.addView(TextView(root.context).apply {
            text = event.label
            gravity = Gravity.CENTER
            setTextColor(event.iconColor)
            textSize = 8.5f * host.gameplayTextScale
            typeface = cronistaTypeface()
        })
        banner.addView(eventHeader)
        banner.addView(TextView(root.context).apply {
            text = entry.text
            gravity = Gravity.CENTER
            maxWidth = (chatPanel.width.takeIf { it > 0 } ?: host.dp(320)) - host.dp(48)
            setTextColor(root.context.getColor(R.color.text_primary))
            textSize = 11.5f * host.gameplayTextScale
            typeface = cronistaTypeface()
        })
        row.addView(
            banner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = host.dp(10)
                marginEnd = host.dp(10)
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

    private fun addChatBubble(
        speaker: String,
        body: String,
        speakerColor: Int,
        ownMessage: Boolean,
        bubbleMaxWidth: Int,
        muted: Boolean
    ) {
        val channel = activeChatChannel()
        val row = LinearLayout(root.context).apply {
            gravity = if (ownMessage) Gravity.END else Gravity.START
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(3), 0, host.dp(3))
        }
        val bubble = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), host.dp(7), host.dp(10), host.dp(8))
            when (channel) {
                ChatChannel.PUBLICO -> setBackgroundResource(
                    if (ownMessage) R.drawable.bg_chat_bubble_own else R.drawable.bg_chat_bubble_other
                )
                ChatChannel.TRAIDORES -> background = roundedBackground(
                    fillColor = root.context.getColor(
                        if (ownMessage) R.color.traitor_red else R.color.traitor_panel
                    ),
                    strokeColor = root.context.getColor(R.color.traitor_red_bright),
                    cornerRadiusDp = 10
                )
                ChatChannel.ESPECTADORES -> background = roundedBackground(
                    fillColor = root.context.getColor(
                        if (ownMessage) R.color.espectro_blue else R.color.espectro_panel
                    ),
                    strokeColor = root.context.getColor(R.color.espectro_blue_bright),
                    cornerRadiusDp = 10
                )
            }
            alpha = if (muted) 0.78f else 1f
        }
        bubble.addView(TextView(root.context).apply {
            text = speaker
            maxLines = 1
            setTextColor(speakerColor)
            textSize = 9f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
        })
        bubble.addView(TextView(root.context).apply {
            text = body
            maxWidth = bubbleMaxWidth
            setTextColor(
                root.context.getColor(
                    when (channel) {
                        ChatChannel.TRAIDORES -> R.color.traitor_text
                        ChatChannel.ESPECTADORES -> R.color.espectro_text
                        ChatChannel.PUBLICO -> if (ownMessage) R.color.bg_dark else R.color.text_primary
                    }
                )
            )
            textSize = 12f * host.gameplayTextScale
            if (muted) typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        })
        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (ownMessage) marginStart = host.dp(30) else marginEnd = host.dp(30)
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

    private fun updateUnreadChatCount() {
        val session = host.currentSession
        val currentCount = session.chatHistory.size
        if (currentCount > lastSeenChatCount) {
            val humanName = GameEngine.humanPlayer(session).name
            val newMessages = session.chatHistory.drop(lastSeenChatCount)
                .count { it.speaker != humanName }
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
            root.context.getColor(
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
        val visibleInAmbient = if (isChatOpen) 0 else CHAT_AMBIENT_MAX_MESSAGES
        val hiddenUnread = (unreadChatCount - visibleInAmbient).coerceAtLeast(0)
        chatUnreadBadge.visibility = if (hiddenUnread > 0) View.VISIBLE else View.GONE
        chatUnreadBadge.text = hiddenUnread.coerceAtMost(99).toString()
        chatUnreadBadge.bringToFront()
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

    private fun sendHumanChatMessage(intentHint: HumanMessageIntent? = null) {
        val session = host.currentSession
        if (host.isTransitionLocked(session.phaseIndex)) {
            host.showToast("El chat se habilita al comenzar la fase.")
            return
        }
        val rawMessage = chatInput.text.toString()
        if (host.isOnlineGameplay()) {
            if (!realtimeAccessReady) {
                GameplayEffects.play(root.context, GameplayEffect.ERROR)
                host.showToast("Reconectando el chat...")
                return
            }
            when (activeChatChannel()) {
                ChatChannel.PUBLICO -> sendOnlineHumanChatMessage(rawMessage)
                ChatChannel.TRAIDORES -> sendOnlineTraitorChatMessage(rawMessage)
                ChatChannel.ESPECTADORES -> sendOnlineSpectatorChatMessage(rawMessage)
            }
            return
        }
        val channel = activeChatChannel()
        val before = session.chatHistory.size
        val updated = GameEngine.addHumanChatMessage(
            session,
            rawMessage,
            includeBotReactions = false,
            channel = channel
        )
        host.currentSession = updated
        if (updated.chatHistory.size > before) {
            GameplayEffects.play(root.context, GameplayEffect.CHAT)
            if (channel == ChatChannel.PUBLICO) {
                onHumanMessage(rawMessage, intentHint)
            } else if (channel == ChatChannel.TRAIDORES) {
                onHumanTraitorMessage(rawMessage)
            }
            clearChatComposerAfterSend()
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        } else if (!canHumanChatInChannel(channel, updated)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast(blockedChatMessage(updated, channel))
        }
        updateUnreadChatCount()
        renderChatPanel()
        renderChatBadge()
    }

    private fun sendOnlineHumanChatMessage(rawMessage: String) {
        val session = host.currentSession
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        if (message.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOnlineChatSentAtMs < ONLINE_CHAT_COOLDOWN_MS) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Espera un momento antes de enviar otro mensaje.")
            return
        }
        if (message.equals(lastOnlineChatMessage, ignoreCase = true)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Ese mensaje ya fue enviado.")
            return
        }
        if (!GameEngine.canHumanChat(session)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast(blockedChatMessage(session))
            return
        }
        val human = GameEngine.humanPlayer(session)
        FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_PUBLIC_CHAT_NODE")
            .push()
            .setValue(
                mapOf(
                    "matchId" to session.onlineMatchId,
                    "actorId" to host.onlinePlayerUid,
                    "speaker" to human.name,
                    "mensaje" to message,
                    "fase" to session.phase.name,
                    "ronda" to session.round,
                    "isGod" to false,
                    "ts" to ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "chat_send_success roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}"
                )
                lastOnlineChatSentAtMs = SystemClock.elapsedRealtime()
                lastOnlineChatMessage = message
                GameplayEffects.play(root.context, GameplayEffect.CHAT)
                clearChatComposerAfterSend()
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "chat_send_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}",
                    error
                )
                GameplayEffects.play(root.context, GameplayEffect.ERROR)
                host.showToast(
                    OnlineErrorMessages.forAction("No se pudo enviar el mensaje", error),
                    Toast.LENGTH_LONG
                )
            }
    }

    private fun sendOnlineTraitorChatMessage(rawMessage: String) {
        val session = host.currentSession
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        if (message.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOnlineTraitorChatSentAtMs < ONLINE_CHAT_COOLDOWN_MS) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Espera un momento antes de enviar otro mensaje.")
            return
        }
        if (message.equals(lastOnlineTraitorChatMessage, ignoreCase = true)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Ese mensaje ya fue enviado.")
            return
        }
        if (!GameEngine.canHumanChatTraitor(session)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast(blockedChatMessage(session, ChatChannel.TRAIDORES))
            return
        }
        val human = GameEngine.humanPlayer(session)
        FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_TRAITOR_CHAT_NODE")
            .push()
            .setValue(
                mapOf(
                    "matchId" to session.onlineMatchId,
                    "actorId" to host.onlinePlayerUid,
                    "speaker" to human.name,
                    "mensaje" to message,
                    "fase" to session.phase.name,
                    "ronda" to session.round,
                    "isGod" to false,
                    "canal" to "traidores",
                    "ts" to ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "traitor_chat_send_success roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}"
                )
                lastOnlineTraitorChatSentAtMs = SystemClock.elapsedRealtime()
                lastOnlineTraitorChatMessage = message
                GameplayEffects.play(root.context, GameplayEffect.CHAT)
                clearChatComposerAfterSend()
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "traitor_chat_send_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}",
                    error
                )
                GameplayEffects.play(root.context, GameplayEffect.ERROR)
                host.showToast(
                    OnlineErrorMessages.forAction("No se pudo enviar el mensaje", error),
                    Toast.LENGTH_LONG
                )
            }
    }

    private fun sendOnlineSpectatorChatMessage(rawMessage: String) {
        val session = host.currentSession
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        if (message.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOnlineSpectatorChatSentAtMs < ONLINE_CHAT_COOLDOWN_MS) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Espera un momento antes de enviar otro mensaje.")
            return
        }
        if (message.equals(lastOnlineSpectatorChatMessage, ignoreCase = true)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast("Ese mensaje ya fue enviado.")
            return
        }
        if (!GameEngine.canHumanChatSpectator(session)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast(blockedChatMessage(session, ChatChannel.ESPECTADORES))
            return
        }
        val human = GameEngine.humanPlayer(session)
        FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_SPECTATOR_CHAT_NODE")
            .push()
            .setValue(
                mapOf(
                    "matchId" to session.onlineMatchId,
                    "actorId" to host.onlinePlayerUid,
                    "speaker" to human.name,
                    "mensaje" to message,
                    "fase" to session.phase.name,
                    "ronda" to session.round,
                    "isGod" to false,
                    "canal" to "espectadores",
                    "ts" to ServerValue.TIMESTAMP
                )
            )
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "spectator_chat_send_success roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}"
                )
                lastOnlineSpectatorChatSentAtMs = SystemClock.elapsedRealtime()
                lastOnlineSpectatorChatMessage = message
                GameplayEffects.play(root.context, GameplayEffect.CHAT)
                clearChatComposerAfterSend()
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "spectator_chat_send_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} speaker=${human.name} phase=${session.phase.name}",
                    error
                )
                GameplayEffects.play(root.context, GameplayEffect.ERROR)
                host.showToast(
                    OnlineErrorMessages.forAction("No se pudo enviar el mensaje", error),
                    Toast.LENGTH_LONG
                )
            }
    }

    private fun startOnlineReactionListener() {
        if (!realtimeAccessReady || !host.isOnlineGameplay() || host.onlineRoomId.isBlank()) return
        val matchId = host.currentSession.onlineMatchId
        if (matchId.isBlank()) return
        if (onlineReactionListener != null && onlineReactionMatchId == matchId) return

        stopOnlineReactionListener()
        OnlineDebugLog.i(
            "emote_listener_start roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId"
        )
        val query = FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_REACTIONS_NODE")
            .orderByKey()
            .limitToLast(ONLINE_REACTION_MAX_EVENTS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = onlineReactionEntries(snapshot, matchId)
                if (!onlineReactionBaselineReady) {
                    entries.forEach { entry -> seenOnlineReactionIds += entry.id }
                    onlineReactionBaselineReady = true
                    trimSeenOnlineReactionIds()
                    return
                }

                entries.forEach { entry ->
                    if (!seenOnlineReactionIds.add(entry.id)) return@forEach
                    if (entry.actorId == host.onlinePlayerUid) return@forEach
                    if (EmoteCatalog.byId(entry.emoteId) == null) return@forEach
                    host.onOnlineReactionReceived(entry.playerName, entry.emoteId)
                }
                trimSeenOnlineReactionIds()
            }

            override fun onCancelled(error: DatabaseError) {
                if (onlineReactionListener !== this) return
                handleRealtimeAccessCancelled(
                    "emote_listener_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId",
                    error
                )
            }
        }

        onlineReactionQuery = query
        onlineReactionListener = listener
        onlineReactionMatchId = matchId
        onlineReactionBaselineReady = false
        seenOnlineReactionIds.clear()

        // Primero tomamos una foto del historial actual. Esas keys se marcan como vistas y
        // luego se adjunta el listener continuo; cualquier emote que llegue entre ambos pasos
        // aparece en el primer callback como nuevo, sin reanimar eventos viejos al entrar tarde.
        query.get()
            .addOnSuccessListener { baseline ->
                if (
                    !realtimeAccessReady ||
                    onlineReactionQuery !== query ||
                    onlineReactionMatchId != matchId
                ) {
                    return@addOnSuccessListener
                }
                onlineReactionEntries(baseline, matchId).forEach { entry ->
                    seenOnlineReactionIds += entry.id
                }
                onlineReactionBaselineReady = true
                trimSeenOnlineReactionIds()
                query.addValueEventListener(listener)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e(
                    "emote_listener_baseline_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} match=$matchId",
                    error
                )
                if (
                    realtimeAccessReady &&
                    onlineReactionQuery === query &&
                    onlineReactionMatchId == matchId
                ) {
                    // El primer callback del listener se convierte en baseline si la lectura
                    // inicial falla, priorizando no repetir el historial sobre mostrar algo viejo.
                    query.addValueEventListener(listener)
                }
            }
    }

    private fun onlineReactionEntries(
        snapshot: DataSnapshot,
        matchId: String
    ): List<OnlineReactionEntry> {
        return snapshot.children.mapNotNull { child ->
            val id = child.key.orEmpty()
            val eventMatchId = child.child("matchId").getValue(String::class.java).orEmpty()
            val actorId = child.child("actorId").getValue(String::class.java).orEmpty()
            val playerName = child.child("player").getValue(String::class.java).orEmpty()
            val emoteId = child.child("emoteId").getValue(String::class.java).orEmpty()
            if (
                id.isBlank() ||
                eventMatchId != matchId ||
                actorId.isBlank() ||
                playerName.isBlank() ||
                emoteId.isBlank()
            ) {
                return@mapNotNull null
            }
            OnlineReactionEntry(id, actorId, playerName, emoteId)
        }
    }

    private fun trimSeenOnlineReactionIds() {
        while (seenOnlineReactionIds.size > ONLINE_REACTION_SEEN_IDS_LIMIT) {
            val oldest = seenOnlineReactionIds.firstOrNull() ?: return
            seenOnlineReactionIds.remove(oldest)
        }
    }

    private fun stopOnlineReactionListener() {
        onlineReactionListener?.let { listener ->
            onlineReactionQuery?.removeEventListener(listener)
        }
        onlineReactionListener = null
        onlineReactionQuery = null
        onlineReactionMatchId = ""
        onlineReactionBaselineReady = false
        seenOnlineReactionIds.clear()
    }

    private fun stopOnlineContentListeners() {
        onlineChatListener?.let { listener ->
            onlineChatQuery?.removeEventListener(listener)
        }
        onlineChatListener = null
        onlineChatQuery = null

        onlineTraitorChatListener?.let { listener ->
            onlineTraitorChatQuery?.removeEventListener(listener)
        }
        onlineTraitorChatListener = null
        onlineTraitorChatQuery = null

        onlineSpectatorChatListener?.let { listener ->
            onlineSpectatorChatQuery?.removeEventListener(listener)
        }
        onlineSpectatorChatListener = null
        onlineSpectatorChatQuery = null

        stopOnlineReactionListener()
    }

    private fun handleRealtimeAccessCancelled(label: String, error: DatabaseError) {
        val exception = error.toException()
        OnlineDebugLog.e(label, exception)
        if (!realtimeAccessReady) return
        onRealtimeAccessUnavailable()
        host.onRealtimeContentAccessCancelled(exception)
    }

    private fun startOnlineChatListener() {
        if (!realtimeAccessReady || !host.isOnlineGameplay() || onlineChatListener != null) return
        OnlineDebugLog.i("chat_listener_start roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}")
        val query = FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_PUBLIC_CHAT_NODE")
            .orderByKey()
            .limitToLast(ONLINE_CHAT_MAX_MESSAGES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentMatchId = host.currentSession.onlineMatchId
                val entries = snapshot.children.mapNotNull { child ->
                    val matchId = child.child("matchId").getValue(String::class.java).orEmpty()
                    if (currentMatchId.isNotBlank() && matchId != currentMatchId) return@mapNotNull null
                    OnlineChatEntry(
                        id = child.key.orEmpty(),
                        speaker = child.child("speaker").getValue(String::class.java).orEmpty(),
                        message = child.child("mensaje").getValue(String::class.java).orEmpty(),
                        isGod = child.child("isGod").getValue(Boolean::class.java) ?: false
                    ).takeIf { it.speaker.isNotBlank() && it.message.isNotBlank() }
                }
                OnlineDebugLog.i(
                    "chat_snapshot roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} messages=${entries.size}"
                )
                applyOnlineChatEntries(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                if (onlineChatListener !== this) return
                handleRealtimeAccessCancelled(
                    "chat_listener_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}",
                    error
                )
            }
        }
        onlineChatQuery = query
        onlineChatListener = listener
        query.addValueEventListener(listener)
    }

    private fun applyOnlineChatEntries(entries: List<OnlineChatEntry>) {
        val onlineMessages = entries.map { GameChatMessage(it.speaker, it.message, it.isGod) }
        mergeOnlineChannelMessages(
            channel = ChatChannel.PUBLICO,
            onlineMessages = onlineMessages,
            preserveGodOfChannel = true
        )
    }

    private fun startOnlineTraitorChatListener() {
        if (!realtimeAccessReady || !host.isOnlineGameplay() || onlineTraitorChatListener != null) return
        if (!GameEngine.canSeeTraitorChat(GameEngine.humanPlayer(host.currentSession))) {
            return
        }
        OnlineDebugLog.i("traitor_chat_listener_start roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}")
        val query = FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_TRAITOR_CHAT_NODE")
            .orderByKey()
            .limitToLast(ONLINE_CHAT_MAX_MESSAGES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentMatchId = host.currentSession.onlineMatchId
                val entries = snapshot.children.mapNotNull { child ->
                    val matchId = child.child("matchId").getValue(String::class.java).orEmpty()
                    if (currentMatchId.isNotBlank() && matchId != currentMatchId) return@mapNotNull null
                    OnlineChatEntry(
                        id = child.key.orEmpty(),
                        speaker = child.child("speaker").getValue(String::class.java).orEmpty(),
                        message = child.child("mensaje").getValue(String::class.java).orEmpty(),
                        isGod = child.child("isGod").getValue(Boolean::class.java) ?: false
                    ).takeIf { it.speaker.isNotBlank() && it.message.isNotBlank() }
                }
                OnlineDebugLog.i(
                    "traitor_chat_snapshot roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} messages=${entries.size}"
                )
                applyOnlineTraitorChatEntries(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                if (onlineTraitorChatListener !== this) return
                handleRealtimeAccessCancelled(
                    "traitor_chat_listener_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}",
                    error
                )
            }
        }
        onlineTraitorChatQuery = query
        onlineTraitorChatListener = listener
        query.addValueEventListener(listener)
    }

    private fun applyOnlineTraitorChatEntries(entries: List<OnlineChatEntry>) {
        val onlineTraitorMessages = entries.map {
            GameChatMessage(it.speaker, it.message, it.isGod, ChatChannel.TRAIDORES)
        }
        mergeOnlineChannelMessages(
            channel = ChatChannel.TRAIDORES,
            onlineMessages = onlineTraitorMessages,
            preserveGodOfChannel = true
        )
    }

    private fun startOnlineSpectatorChatListener() {
        if (!realtimeAccessReady || !host.isOnlineGameplay() || onlineSpectatorChatListener != null) return
        if (GameEngine.humanPlayer(host.currentSession).alive) return
        OnlineDebugLog.i("spectator_chat_listener_start roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}")
        val query = FirebaseDatabase.getInstance()
            .getReference("salas/${host.onlineRoomId}/$RTDB_SPECTATOR_CHAT_NODE")
            .orderByKey()
            .limitToLast(ONLINE_CHAT_MAX_MESSAGES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentMatchId = host.currentSession.onlineMatchId
                val entries = snapshot.children.mapNotNull { child ->
                    val matchId = child.child("matchId").getValue(String::class.java).orEmpty()
                    if (currentMatchId.isNotBlank() && matchId != currentMatchId) return@mapNotNull null
                    OnlineChatEntry(
                        id = child.key.orEmpty(),
                        speaker = child.child("speaker").getValue(String::class.java).orEmpty(),
                        message = child.child("mensaje").getValue(String::class.java).orEmpty(),
                        isGod = child.child("isGod").getValue(Boolean::class.java) ?: false
                    ).takeIf { it.speaker.isNotBlank() && it.message.isNotBlank() }
                }
                OnlineDebugLog.i(
                    "spectator_chat_snapshot roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} messages=${entries.size}"
                )
                applyOnlineSpectatorChatEntries(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                if (onlineSpectatorChatListener !== this) return
                handleRealtimeAccessCancelled(
                    "spectator_chat_listener_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}",
                    error
                )
            }
        }
        onlineSpectatorChatQuery = query
        onlineSpectatorChatListener = listener
        query.addValueEventListener(listener)
    }

    private fun applyOnlineSpectatorChatEntries(entries: List<OnlineChatEntry>) {
        val onlineSpectatorMessages = entries.map {
            GameChatMessage(it.speaker, it.message, it.isGod, ChatChannel.ESPECTADORES)
        }
        mergeOnlineChannelMessages(
            channel = ChatChannel.ESPECTADORES,
            onlineMessages = onlineSpectatorMessages,
            preserveGodOfChannel = false
        )
    }

    private fun mergeOnlineChannelMessages(
        channel: ChatChannel,
        onlineMessages: List<GameChatMessage>,
        preserveGodOfChannel: Boolean
    ) {
        val session = host.currentSession
        val previousCount = session.chatHistory.size
        val otherChannels = session.chatHistory.filter { it.channel != channel }
        val keptGodMessages = if (preserveGodOfChannel) {
            session.chatHistory.filter { it.channel == channel && it.isGod }
        } else {
            emptyList()
        }
        host.currentSession = session.copy(
            chatHistory = otherChannels +
                (keptGodMessages + onlineMessages).takeLast(GameplayFeedMessages.MAX_FEED_MESSAGES)
        )
        if (host.currentSession.chatHistory.size > previousCount) {
            updateUnreadChatCount()
        }
        renderChatPanel()
        renderChatBadge()
    }

    private fun onHumanMessage(
        rawHumanMessage: String,
        intentHint: HumanMessageIntent?
    ) {
        if (host.isOnlineGameplay()) return
        val humanMessage = rawHumanMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        val session = host.currentSession
        if (humanMessage.isBlank() || LocalBotAi.isDebugVoteCommand(session, humanMessage)) return
        if (directorPhaseIndex != session.phaseIndex) {
            resetDirectorForPhase(session)
        }
        directorHumanSpokePhaseIndex = session.phaseIndex
        directorPromptedSilentHuman = false
        if (directorPendingHumanMessage.isNotBlank()) {
            cancelScheduledBotChat()
            directorPendingHumanMessage = humanMessage
            directorPendingIntentHint = intentHint
            directorReactionLines = 0
            scheduleNextHumanReactionBeat()
            return
        }
        cancelScheduledBotChat()
        directorPendingHumanMessage = humanMessage
        directorPendingIntentHint = intentHint
        directorReactionLines = 0
        scheduleNextHumanReactionBeat()
    }

    private fun onHumanTraitorMessage(rawHumanMessage: String) {
        if (host.isOnlineGameplay()) return
        val session = host.currentSession
        if (!canRunVisibleTraitorNight(session)) return
        if (traitorDirectorPhaseIndex != session.phaseIndex) {
            resetTraitorDirectorForPhase(session)
        }
        val humanMessage = rawHumanMessage.trim()
            .replace(Regex("\\s+"), " ")
            .take(CHAT_MESSAGE_MAX_LENGTH)
        if (humanMessage.isBlank()) {
            scheduleNextTraitorNightBeat()
            return
        }
        // Si un aliado estaba por soltar una linea del plan, primero te contesta a vos.
        cancelScheduledBotChat()
        traitorPendingHumanMessage = humanMessage
        scheduleTraitorReactionBeat()
    }

    private fun scheduleTraitorReactionBeat() {
        val session = host.currentSession
        val humanMessage = traitorPendingHumanMessage
        traitorPendingHumanMessage = ""
        if (
            host.isOnlineGameplay() ||
            humanMessage.isBlank() ||
            !canRunVisibleTraitorNight(session) ||
            traitorDirectorPhaseIndex != session.phaseIndex ||
            pendingBotChatRunnables.isNotEmpty()
        ) {
            return
        }
        val beat = BotConversationDirector.nextTraitorReactionBeat(
            session = session,
            humanMessage = humanMessage,
            lastSpeaker = traitorDirectorLastSpeaker
        )
        if (beat == null) {
            scheduleNextTraitorNightBeat()
            return
        }
        val delayMs = BotConversationDirector.naturalDelayMs(
            session = session,
            beatIndex = directorBeatCounter++,
            message = beat.message,
            reaction = true,
            speaker = beat.speaker
        )
        scheduleBotConversationBeat(
            beat = beat,
            phaseIndex = session.phaseIndex,
            phase = session.phase,
            delayMs = delayMs,
            channel = ChatChannel.TRAIDORES
        ) { committed, deliveredMessages ->
            traitorDirectorLastSpeaker = beat.speaker
            traitorDirectorLines += deliveredMessages
            scheduleNextTraitorNightBeat(committed)
        }
    }

    private fun resetDirectorForPhase(session: GameSession) {
        directorPhaseIndex = session.phaseIndex
        directorIdleLines = 0
        directorReactionLines = 0
        directorBeatCounter = 0
        directorPendingHumanMessage = ""
        directorPendingIntentHint = null
        directorLastSpeaker = recentPublicMessages(session)
            .lastOrNull { !it.isGod && isBotSpeaker(session, it.speaker) }
            ?.speaker
        val humanName = GameEngine.humanPlayer(session).name
        directorHumanSpokePhaseIndex = if (
            recentPublicMessages(session)
                .asReversed()
                .take(6)
                .any { it.speaker == humanName }
        ) {
            session.phaseIndex
        } else {
            -1
        }
        directorPromptedSilentHuman = false
    }

    private fun scheduleNextHumanReactionBeat() {
        val session = host.currentSession
        if (
            host.isOnlineGameplay() ||
            directorPendingHumanMessage.isBlank() ||
            !BotConversationDirector.canRun(session) ||
            directorPhaseIndex != session.phaseIndex ||
            pendingBotChatRunnables.isNotEmpty()
        ) {
            return
        }
        val beat = BotConversationDirector.nextHumanReactionBeat(
            session = session,
            humanMessage = directorPendingHumanMessage,
            deliveredReactions = directorReactionLines,
            lastSpeaker = directorLastSpeaker,
            intentHint = directorPendingIntentHint
        )
        if (beat == null) {
            finishHumanConversation()
            return
        }
        val delayMs = BotConversationDirector.naturalDelayMs(
            session = session,
            beatIndex = directorBeatCounter++,
            message = beat.message,
            reaction = true,
            speaker = beat.speaker
        )
        scheduleBotConversationBeat(
            beat = beat,
            phaseIndex = session.phaseIndex,
            phase = session.phase,
            delayMs = delayMs
        ) { committed, deliveredMessages ->
            directorLastSpeaker = beat.speaker
            directorReactionLines += deliveredMessages
            if (directorReactionLines >= MAX_STAGGERED_BOT_REACTIONS) {
                finishHumanConversation(committed)
            } else {
                scheduleNextHumanReactionBeat()
            }
        }
    }

    private fun finishHumanConversation(sessionOverride: GameSession? = null) {
        directorReactionLines = 0
        directorPendingHumanMessage = ""
        directorPendingIntentHint = null
        schedulePlayerNudge(sessionOverride)
        renderChatPanel()
    }

    private fun schedulePlayerNudge(sessionOverride: GameSession? = null) {
        val session = sessionOverride ?: host.currentSession
        if (
            host.isOnlineGameplay() ||
            directorPromptedSilentHuman ||
            !BotConversationDirector.canRun(session) ||
            directorPhaseIndex != session.phaseIndex ||
            directorPendingHumanMessage.isNotBlank() ||
            pendingBotChatRunnables.isNotEmpty()
        ) {
            return
        }
        val beat = BotConversationDirector.playerNudgeBeat(session, directorLastSpeaker) ?: return
        scheduleBotConversationBeat(
            beat = beat,
            phaseIndex = session.phaseIndex,
            phase = session.phase,
            delayMs = BotConversationDirector.silenceDelayMs(session, directorIdleLines)
        ) { _, _ ->
            directorLastSpeaker = beat.speaker
            directorPromptedSilentHuman = true
        }
    }

    private fun scheduleNextIdleBeat(sessionOverride: GameSession? = null) {
        val session = sessionOverride ?: host.currentSession
        if (
            host.isOnlineGameplay() ||
            !BotConversationDirector.canRun(session) ||
            directorPhaseIndex != session.phaseIndex ||
            directorPendingHumanMessage.isNotBlank() ||
            pendingBotChatRunnables.isNotEmpty()
        ) {
            return
        }
        val beat = BotConversationDirector.nextIdleBeat(
            session = session,
            idleLinesUsed = directorIdleLines,
            lastSpeaker = directorLastSpeaker,
            humanSpokeThisPhase = directorHumanSpokePhaseIndex == session.phaseIndex,
            promptedSilentHuman = directorPromptedSilentHuman
        ) ?: return
        val pauseDelay = if (
            directorIdleLines > 0 &&
            recentBotStreak(session) >= BotConversationDirector.pauseAfterBotStreak(session)
        ) {
            BotConversationDirector.silenceDelayMs(session, directorIdleLines)
        } else {
            0L
        }
        val delayMs = pauseDelay.takeIf { it > 0L }
            ?: BotConversationDirector.naturalDelayMs(
                session = session,
                beatIndex = directorBeatCounter++,
                message = beat.message,
                reaction = false,
                speaker = beat.speaker
            )
        scheduleBotConversationBeat(
            beat = beat,
            phaseIndex = session.phaseIndex,
            phase = session.phase,
            delayMs = delayMs
        ) { committed, deliveredMessages ->
            directorLastSpeaker = beat.speaker
            directorIdleLines += deliveredMessages
            if (beat.promptsSilentHuman) {
                directorPromptedSilentHuman = true
            }
            if (directorIdleLines < BotConversationDirector.idleBudget(committed)) {
                scheduleNextIdleBeat(committed)
            }
        }
    }

    private fun resetTraitorDirectorForPhase(session: GameSession) {
        traitorDirectorPhaseIndex = session.phaseIndex
        traitorPendingHumanMessage = ""
        traitorDirectorLines = recentTraitorMessages(session)
            .count { isBotSpeaker(session, it.speaker) }
        traitorDirectorLastSpeaker = recentTraitorMessages(session)
            .lastOrNull { isBotSpeaker(session, it.speaker) }
            ?.speaker
    }

    private fun resetTraitorDirectorIfNeeded(session: GameSession) {
        if (!GameplayTableUi.isNightPhase(session.phase)) {
            traitorDirectorPhaseIndex = -1
            traitorDirectorLines = 0
            traitorDirectorLastSpeaker = null
            traitorPendingHumanMessage = ""
        }
    }

    private fun scheduleNextTraitorNightBeat(sessionOverride: GameSession? = null) {
        val session = sessionOverride ?: host.currentSession
        if (
            host.isOnlineGameplay() ||
            !canRunVisibleTraitorNight(session) ||
            traitorDirectorPhaseIndex != session.phaseIndex ||
            pendingBotChatRunnables.isNotEmpty()
        ) {
            return
        }
        val beat = BotConversationDirector.nextTraitorNightBeat(
            session = session,
            deliveredLines = traitorDirectorLines,
            lastSpeaker = traitorDirectorLastSpeaker
        ) ?: return
        val delayMs = BotConversationDirector.naturalDelayMs(
            session = session,
            beatIndex = directorBeatCounter++,
            message = beat.message,
            reaction = false,
            speaker = beat.speaker
        )
        scheduleBotConversationBeat(
            beat = beat,
            phaseIndex = session.phaseIndex,
            phase = session.phase,
            delayMs = delayMs,
            channel = ChatChannel.TRAIDORES
        ) { committed, deliveredMessages ->
            traitorDirectorLastSpeaker = beat.speaker
            traitorDirectorLines += deliveredMessages
            scheduleNextTraitorNightBeat(committed)
        }
    }

    private fun scheduleBotConversationBeat(
        beat: BotConversationBeat,
        phaseIndex: Int,
        phase: GamePhase,
        delayMs: Long,
        channel: ChatChannel = ChatChannel.PUBLICO,
        afterCommit: ((GameSession, Int) -> Unit)? = null
    ) {
        scheduleBotConversationMessage(
            speaker = beat.speaker,
            messages = listOf(beat.message) + beat.followUps,
            messageIndex = 0,
            phaseIndex = phaseIndex,
            phase = phase,
            delayMs = delayMs,
            channel = channel,
            afterCommit = afterCommit
        )
    }

    private fun scheduleBotConversationMessage(
        speaker: String,
        messages: List<String>,
        messageIndex: Int,
        phaseIndex: Int,
        phase: GamePhase,
        delayMs: Long,
        channel: ChatChannel,
        afterCommit: ((GameSession, Int) -> Unit)?
    ) {
        val message = messages.getOrNull(messageIndex) ?: return
        scheduleBotChatMessage(
            speaker = speaker,
            message = message,
            phaseIndex = phaseIndex,
            phase = phase,
            delayMs = delayMs,
            channel = channel,
            minimumSilentPauseMs = if (messageIndex == 0) {
                PRIMARY_BOT_THINKING_PAUSE_MS
            } else {
                BURST_BOT_THINKING_PAUSE_MS
            }
        ) { committed ->
            val nextIndex = messageIndex + 1
            if (nextIndex < messages.size) {
                val nextMessage = messages[nextIndex]
                scheduleBotConversationMessage(
                    speaker = speaker,
                    messages = messages,
                    messageIndex = nextIndex,
                    phaseIndex = phaseIndex,
                    phase = phase,
                    delayMs = BotConversationDirector.burstDelayMs(
                        session = committed,
                        beatIndex = directorBeatCounter++,
                        speaker = speaker,
                        message = nextMessage
                    ),
                    channel = channel,
                    afterCommit = afterCommit
                )
            } else {
                afterCommit?.invoke(committed, messages.size)
            }
        }
    }

    private fun scheduleBotChatMessage(
        speaker: String,
        message: String,
        phaseIndex: Int,
        phase: GamePhase,
        delayMs: Long,
        channel: ChatChannel = ChatChannel.PUBLICO,
        minimumSilentPauseMs: Long = PRIMARY_BOT_THINKING_PAUSE_MS,
        afterCommit: ((GameSession) -> Unit)? = null
    ) {
        val typingRunnable = object : Runnable {
            override fun run() {
                pendingBotChatRunnables.remove(this)
                val session = host.currentSession
                if (
                    session.phaseIndex != phaseIndex ||
                    session.phase != phase ||
                    session.winner.isNotBlank() ||
                    host.isTransitionLocked(phaseIndex) ||
                    !canScheduledBotSpeak(session, speaker, channel)
                ) {
                    return
                }
                typingBotSpeakers += speaker
                renderChatPanel()
                chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
        val runnable = object : Runnable {
            override fun run() {
                pendingBotChatRunnables.remove(this)
                typingBotSpeakers -= speaker
                val session = host.currentSession
                if (
                    session.phaseIndex != phaseIndex ||
                    session.phase != phase ||
                    session.winner.isNotBlank() ||
                    !canScheduledBotSpeak(session, speaker, channel)
                ) {
                    return
                }
                if (host.isTransitionLocked(phaseIndex)) {
                    scheduleBotChatMessage(
                        speaker = speaker,
                        message = message,
                        phaseIndex = phaseIndex,
                        phase = phase,
                        delayMs = TRANSITION_RETRY_DELAY_MS,
                        channel = channel,
                        minimumSilentPauseMs = minimumSilentPauseMs,
                        afterCommit = afterCommit
                    )
                    return
                }
                val beforeCount = session.chatHistory.size
                val updated = GameEngine.addBotChatMessage(session, speaker, message, channel = channel)
                if (updated.chatHistory.size == beforeCount) return
                host.currentSession = updated
                GameplayEffects.play(root.context, GameplayEffect.CHAT)
                updateUnreadChatCount()
                renderChatPanel()
                renderChatBadge()
                afterCommit?.invoke(updated)
            }
        }
        pendingBotChatRunnables += typingRunnable
        pendingBotChatRunnables += runnable
        val effectiveSilentPause = minimumSilentPauseMs
            .coerceAtMost((delayMs - MIN_BOT_TYPING_VISIBLE_MS).coerceAtLeast(0L))
        handler.postDelayed(
            typingRunnable,
            (delayMs - botTypingVisibleMs(delayMs, message, effectiveSilentPause))
                .coerceAtLeast(effectiveSilentPause)
        )
        handler.postDelayed(runnable, delayMs)
    }

    private fun canScheduledBotSpeak(
        session: GameSession,
        speaker: String,
        channel: ChatChannel = ChatChannel.PUBLICO
    ): Boolean {
        val player = GameEngine.playerByName(session, speaker) ?: return false
        if (player.isHuman) return false
        return when (channel) {
            ChatChannel.PUBLICO -> GameEngine.canParticipateInChat(session, player)
            ChatChannel.TRAIDORES -> GameEngine.canSeeTraitorChat(player) &&
                GameEngine.isTraitorChatWritable(session)
            ChatChannel.ESPECTADORES -> false
        }
    }

    private fun botTypingVisibleMs(delayMs: Long, message: String, silentPauseMs: Long): Long {
        val desiredWritingTime = (700L + message.length * 18L).coerceIn(
            MIN_BOT_TYPING_VISIBLE_MS,
            MAX_BOT_TYPING_VISIBLE_MS
        )
        return desiredWritingTime.coerceAtMost((delayMs - silentPauseMs).coerceAtLeast(0L))
    }

    private fun clearChatComposerAfterSend() {
        chatInput.text.clear()
        chatInput.setText("")
        chatInput.setSelection(0)
        chatInput.post {
            chatInput.text.clear()
            chatInput.setText("")
            chatInput.setSelection(0)
            renderChatCharacterCount(0)
        }
    }

    private fun chatInputHint(canChat: Boolean, channel: ChatChannel = activeChatChannel()): String {
        if (canChat) {
            return when (channel) {
                ChatChannel.PUBLICO -> "Escribir..."
                ChatChannel.TRAIDORES -> "Tramar en las sombras..."
                ChatChannel.ESPECTADORES -> "Escribir en el Chat de los Muertos..."
            }
        }
        val session = host.currentSession
        val human = GameEngine.humanPlayer(session)
        if (canUseSpectatorChatUi(session) && session.winner.isNotBlank()) return "Solo lectura"
        if (!human.alive) {
            return if (canUseSpectatorChatUi(session) && channel == ChatChannel.PUBLICO) {
                "Eliminado: hablá en el Chat de los Muertos"
            } else {
                "Eliminado: solo lectura"
            }
        }
        if (human.muted) return "Muteado: solo lectura"
        if (channel == ChatChannel.TRAIDORES) {
            return if (GameplayTableUi.isNightPhase(session.phase)) {
                "El plan no puede escribirse ahora"
            } else {
                "El plan descansa hasta la noche"
            }
        }
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

    private fun blockedChatMessage(
        session: GameSession,
        channel: ChatChannel = activeChatChannel()
    ): String {
        val human = GameEngine.humanPlayer(session)
        return when {
            channel == ChatChannel.ESPECTADORES && session.winner.isNotBlank() ->
                "La partida terminó. El canal quedó en solo lectura."
            !human.alive && canUseSpectatorChatUi(session) && channel == ChatChannel.PUBLICO ->
                "Estás eliminado. Hablá en el Chat de los Muertos."
            !human.alive -> "Estás eliminado. Puedes mirar el chat, pero no escribir."
            human.muted -> "Estás silenciado. Puedes mirar el chat, pero no escribir."
            channel == ChatChannel.TRAIDORES && !GameEngine.canSeeTraitorChat(human) ->
                "No formas parte del Plan de los Asesinos."
            channel == ChatChannel.TRAIDORES ->
                "El plan descansa hasta la noche."
            GameplayTableUi.isNightPhase(session.phase) ->
                "El pueblo duerme. Las voces deben esperar al amanecer."
            else -> "No puedes escribir durante esta fase."
        }
    }

    private fun renderChatBackgrounds() {
        val channel = activeChatChannel()
        val renderKey = listOf(
            host.currentSession.mapKey,
            channel.name,
            canHumanChatInChannel(channel)
        ).joinToString("|")
        if (lastChatBackgroundRenderKey == renderKey) return
        lastChatBackgroundRenderKey = renderKey
        val logDrawable = host.chatLogDrawableRes()
        chatPanelBackground.setImageResource(logDrawable)
        chatAmbientBackground.setImageResource(logDrawable)
        when (channel) {
            ChatChannel.TRAIDORES -> {
                renderTraitorChatBackgrounds()
                return
            }
            ChatChannel.ESPECTADORES -> {
                renderSpectatorChatBackgrounds()
                return
            }
            ChatChannel.PUBLICO -> Unit
        }
        chatPanel.setBackgroundResource(R.drawable.bg_reveal_text_shade)
        chatAmbientFeed.setBackgroundResource(R.drawable.bg_reveal_text_shade)
        applyChatFrameForegrounds(
            panelFrame = R.drawable.bg_chat_frame_public_expanded,
            ambientFrame = R.drawable.bg_chat_frame_public_collapsed
        )
        chatPanelShade.setBackgroundResource(R.drawable.bg_reveal_text_shade)
        chatAmbientShade.setBackgroundResource(R.drawable.bg_reveal_text_shade)
        chatHeader.background = null
        chatComposer.background = null
        chatInput.setBackgroundResource(R.drawable.bg_chat_input)
        chatInput.setTextColor(root.context.getColor(R.color.text_primary))
        chatInput.setHintTextColor(root.context.getColor(R.color.text_muted))
        btnSendChat.setBackgroundResource(R.drawable.bg_btn_gold)
        btnSendChat.setTextColor(root.context.getColor(R.color.bg_dark))
        chatNewMessages.setTextColor(root.context.getColor(R.color.accent_gold))
        val ambientAlpha = when (host.currentSession.mapKey) {
            "grecia" -> 0.54f
            "medieval" -> 0.64f
            else -> 0.60f
        }
        chatAmbientBackground.alpha = ambientAlpha
        chatPanelBackground.alpha = when (host.currentSession.mapKey) {
            "grecia" -> 0.42f
            "medieval" -> 0.52f
            else -> 0.48f
        }
    }

    private fun renderTraitorChatBackgrounds() {
        val writable = GameEngine.canHumanChatTraitor(host.currentSession)
        val red = root.context.getColor(R.color.traitor_red)
        val bright = root.context.getColor(R.color.traitor_red_bright)
        val panel = root.context.getColor(R.color.traitor_panel)
        val bg = root.context.getColor(R.color.traitor_bg)
        val text = root.context.getColor(R.color.traitor_text)
        val muted = root.context.getColor(R.color.traitor_muted)

        chatPanel.background = roundedBackground(
            fillColor = colorWithAlpha(bg, if (writable) 238 else 220),
            strokeColor = red,
            cornerRadiusDp = 12,
            strokeWidthDp = 2
        )
        chatAmbientFeed.background = roundedBackground(
            fillColor = colorWithAlpha(bg, if (writable) 226 else 208),
            strokeColor = red,
            cornerRadiusDp = 12,
            strokeWidthDp = 1
        )
        applyChatFrameForegrounds(
            panelFrame = R.drawable.bg_chat_frame_traitor_expanded,
            ambientFrame = R.drawable.bg_chat_frame_traitor_collapsed
        )
        chatPanelShade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colorWithAlpha(panel, 236), colorWithAlpha(bg, 246))
        )
        chatAmbientShade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colorWithAlpha(panel, 220), colorWithAlpha(bg, 238))
        )
        chatHeader.background = roundedBackground(
            fillColor = colorWithAlpha(panel, 226),
            strokeColor = colorWithAlpha(red, 180),
            cornerRadiusDp = 9
        )
        chatComposer.background = roundedBackground(
            fillColor = colorWithAlpha(panel, 220),
            strokeColor = colorWithAlpha(red, 170),
            cornerRadiusDp = 10
        )
        chatInput.background = roundedBackground(
            fillColor = colorWithAlpha(bg, 235),
            strokeColor = colorWithAlpha(red, 170),
            cornerRadiusDp = 10
        )
        chatInput.setTextColor(text)
        chatInput.setHintTextColor(muted)
        btnSendChat.background = roundedBackground(
            fillColor = red,
            strokeColor = bright,
            cornerRadiusDp = 10
        )
        btnSendChat.setTextColor(root.context.getColor(R.color.text_primary))
        chatNewMessages.setTextColor(bright)
        chatAmbientBackground.alpha = if (writable) 0.30f else 0.22f
        chatPanelBackground.alpha = if (writable) 0.26f else 0.18f
    }

    private fun renderSpectatorChatBackgrounds() {
        val writable = GameEngine.canHumanChatSpectator(host.currentSession)
        val blue = root.context.getColor(R.color.espectro_blue)
        val bright = root.context.getColor(R.color.espectro_blue_bright)
        val panel = root.context.getColor(R.color.espectro_panel)
        val bg = root.context.getColor(R.color.espectro_bg)
        val text = root.context.getColor(R.color.espectro_text)
        val muted = root.context.getColor(R.color.espectro_muted)

        chatPanel.background = roundedBackground(
            fillColor = colorWithAlpha(bg, if (writable) 238 else 220),
            strokeColor = blue,
            cornerRadiusDp = 12,
            strokeWidthDp = 2
        )
        chatAmbientFeed.background = roundedBackground(
            fillColor = colorWithAlpha(bg, if (writable) 226 else 208),
            strokeColor = blue,
            cornerRadiusDp = 12,
            strokeWidthDp = 1
        )
        applyChatFrameForegrounds(panelFrame = null, ambientFrame = null)
        chatPanelShade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colorWithAlpha(panel, 236), colorWithAlpha(bg, 246))
        )
        chatAmbientShade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colorWithAlpha(panel, 220), colorWithAlpha(bg, 238))
        )
        chatHeader.background = roundedBackground(
            fillColor = colorWithAlpha(panel, 226),
            strokeColor = colorWithAlpha(blue, 180),
            cornerRadiusDp = 9
        )
        chatComposer.background = roundedBackground(
            fillColor = colorWithAlpha(panel, 220),
            strokeColor = colorWithAlpha(blue, 170),
            cornerRadiusDp = 10
        )
        chatInput.background = roundedBackground(
            fillColor = colorWithAlpha(bg, 235),
            strokeColor = colorWithAlpha(blue, 170),
            cornerRadiusDp = 10
        )
        chatInput.setTextColor(text)
        chatInput.setHintTextColor(muted)
        btnSendChat.background = roundedBackground(
            fillColor = blue,
            strokeColor = bright,
            cornerRadiusDp = 10
        )
        btnSendChat.setTextColor(text)
        chatNewMessages.setTextColor(bright)
        chatAmbientBackground.alpha = if (writable) 0.30f else 0.22f
        chatPanelBackground.alpha = if (writable) 0.26f else 0.18f
    }

    private fun applyChatFrameForegrounds(
        panelFrame: Int?,
        ambientFrame: Int?
    ) {
        chatPanel.foreground = panelFrame?.let {
            ResourcesCompat.getDrawable(root.resources, it, root.context.theme)
        }
        chatAmbientFeed.foreground = ambientFrame?.let {
            ResourcesCompat.getDrawable(root.resources, it, root.context.theme)
        }
    }

    private fun activeChatChannel(): ChatChannel {
        val session = host.currentSession
        if (canUseSpectatorChatUi(session)) {
            return if (selectedChatChannel == ChatChannel.PUBLICO) {
                ChatChannel.PUBLICO
            } else {
                ChatChannel.ESPECTADORES
            }
        }
        if (!canUseTraitorChatUi(session)) return ChatChannel.PUBLICO
        if (GameplayTableUi.isNightPhase(session.phase)) return ChatChannel.TRAIDORES
        return selectedChatChannel
    }

    private fun activeChannelMessages(channel: ChatChannel): List<GameChatMessage> {
        return host.currentSession.chatHistory.filter { it.channel == channel }
    }

    private fun canHumanChatInChannel(
        channel: ChatChannel,
        session: GameSession = host.currentSession
    ): Boolean {
        return when (channel) {
            ChatChannel.PUBLICO -> GameEngine.canHumanChat(session)
            ChatChannel.TRAIDORES -> GameEngine.canHumanChatTraitor(session)
            ChatChannel.ESPECTADORES -> host.isOnlineGameplay() &&
                GameEngine.canHumanChatSpectator(session)
        }
    }

    private fun canToggleTraitorChannel(): Boolean {
        val session = host.currentSession
        return canUseTraitorChatUi(session) &&
            !GameplayTableUi.isNightPhase(session.phase) &&
            session.phase != GamePhase.REPARTO &&
            session.phase != GamePhase.RESULTADO
    }

    private fun canUseTraitorChatUi(session: GameSession): Boolean {
        return GameEngine.canSeeTraitorChat(GameEngine.humanPlayer(session))
    }

    private fun canUseSpectatorChatUi(session: GameSession): Boolean {
        return host.isOnlineGameplay() &&
            GameEngine.canSeeSpectatorChat(GameEngine.humanPlayer(session))
    }

    private fun canRunVisibleTraitorNight(session: GameSession): Boolean {
        // El director nocturno de bots nunca corre en online: el online es humano contra
        // humano, y el chat de traidores solo transporta mensajes escritos por jugadores.
        return !host.isOnlineGameplay() &&
            canUseTraitorChatUi(session) &&
            BotConversationDirector.canRunTraitorNight(session)
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        cornerRadiusDp: Int,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = host.dp(cornerRadiusDp).toFloat()
            setColor(fillColor)
            setStroke(host.dp(strokeWidthDp), strokeColor)
        }
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    companion object {
        private const val STATE_CHAT_OPEN = "chat_open"
        private const val STATE_CHAT_CHANNEL = "chat_channel"
        private const val CHAT_SHEET_HEIGHT_RATIO = 0.52f
        private const val CHAT_SHEET_COMPACT_HEIGHT_RATIO = 0.74f
        private const val CHAT_SHEET_MIN_HEIGHT_DP = 320
        private const val CHAT_SHEET_MAX_HEIGHT_DP = 560
        private const val CHAT_AMBIENT_MAX_MESSAGES = 4
        private const val CHAT_AMBIENT_SOURCE_LIMIT = 8
        private const val CHAT_EXPANDED_SOURCE_LIMIT = 32
        private const val BOTTOM_PLAYER_PANEL_HEIGHT_DP = 146
        private const val BOTTOM_PLAYER_PANEL_COMPACT_HEIGHT_DP = 42
        private const val CHAT_MESSAGE_MAX_LENGTH = 140
        private const val CHAT_MESSAGE_WARNING_LENGTH = 120
        private const val ONLINE_CHAT_COOLDOWN_MS = 1200L
        private const val ONLINE_CHAT_MAX_MESSAGES = 60
        private const val RTDB_PUBLIC_CHAT_NODE = "chat"
        private const val RTDB_TRAITOR_CHAT_NODE = "chat_traidores"
        private const val RTDB_SPECTATOR_CHAT_NODE = "chat_espectadores"
        private const val RTDB_REACTIONS_NODE = "emotes"
        private const val ONLINE_REACTION_MAX_EVENTS = 30
        private const val ONLINE_REACTION_SEEN_IDS_LIMIT = ONLINE_REACTION_MAX_EVENTS * 4
        private const val MAX_STAGGERED_BOT_REACTIONS = 4
        private const val MAX_EVENT_BOT_REACTIONS = 3
        private const val NEXT_BOT_REACTION_DELAY_MS = 2_650L
        private const val EVENT_BOT_REACTION_DELAY_MS = 2_400L
        private const val TRANSITION_RETRY_DELAY_MS = 650L
        private const val PRIMARY_BOT_THINKING_PAUSE_MS = 700L
        private const val BURST_BOT_THINKING_PAUSE_MS = 250L
        private const val MIN_BOT_TYPING_VISIBLE_MS = 550L
        private const val MAX_BOT_TYPING_VISIBLE_MS = 2_400L
    }
}
