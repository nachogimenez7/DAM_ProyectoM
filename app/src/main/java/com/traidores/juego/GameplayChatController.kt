package com.traidores.juego

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
import android.view.inputmethod.EditorInfo
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

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
        fun isPortrait(): Boolean
        fun dp(value: Int): Int
        fun isTransitionLocked(phaseIndex: Int): Boolean
        fun hideKeyboard()
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
        fun showTieVoteWindowAfterChat()
        fun renderHumanCardIfVisible()
        fun renderPersonalStatus()
        fun chatLogDrawableRes(): Int
    }

    private data class OnlineChatEntry(
        val id: String,
        val speaker: String,
        val message: String,
        val isGod: Boolean
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isChatOpen = false
    private var isChatKeyboardCompact = false
    private var isBottomPlayerPanelCompact = false
    private var chatKeyboardBottomInset = 0
    private var newChatMessagesWhileTyping = 0
    private var lastSeenChatCount = 0
    private var restoreTieVoteAfterChat = false
    private var showOnlyEvents = false
    private var unreadChatCount = 0
    private var onlineChatListener: ListenerRegistration? = null
    private var lastOnlineChatSentAtMs = 0L
    private var lastOnlineChatMessage = ""
    private var stagedBotBurstPhaseIndex = -1
    private var stagedEventReactionKey = ""
    private val pendingBotChatRunnables = mutableListOf<Runnable>()
    private val typingBotSpeakers = linkedSetOf<String>()

    private val btnToggleChat: ImageButton = root.findViewById(R.id.btnToggleChat)
    private val btnSendChat: Button = root.findViewById(R.id.btnSendChat)
    private val btnCloseChat: ImageButton = root.findViewById(R.id.btnCloseChat)
    private val btnChatFeedFilter: Button = root.findViewById(R.id.btnChatFeedFilter)
    private val chatAmbientFeed: FrameLayout = root.findViewById(R.id.chatAmbientFeed)
    private val chatAmbientBackground: ImageView = root.findViewById(R.id.chatAmbientBackground)
    private val chatAmbientHint: TextView = root.findViewById(R.id.chatAmbientHint)
    private val chatAmbientMessages: LinearLayout = root.findViewById(R.id.chatAmbientMessages)
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
    private val chatRoleChip: TextView = root.findViewById(R.id.chatRoleChip)
    private val chatStatusRow: LinearLayout = root.findViewById(R.id.chatStatusRow)
    private val chatUnreadBadge: TextView = root.findViewById(R.id.chatUnreadBadge)

    private val centerColumn: FrameLayout = root.findViewById(R.id.centerColumn)
    private val bottomPlayerPanel: LinearLayout = root.findViewById(R.id.bottomPlayerPanel)
    private val roleCard: LinearLayout = root.findViewById(R.id.roleCard)
    private val currentPlayerName: TextView = root.findViewById(R.id.currentPlayerName)
    private val currentPlayerStatus: TextView = root.findViewById(R.id.currentPlayerStatus)
    private val currentPlayerHint: TextView = root.findViewById(R.id.currentPlayerHint)
    private val actionControls: LinearLayout = root.findViewById(R.id.actionControls)
    private val eliminatedStatePanel: LinearLayout = root.findViewById(R.id.eliminatedStatePanel)
    private val roleName: TextView = root.findViewById(R.id.roleName)

    fun onCreate(savedState: Bundle?) {
        isChatOpen = savedState?.getBoolean(STATE_CHAT_OPEN) ?: false
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
        if (host.isOnlineGameplay()) {
            startOnlineChatListener()
        }
    }

    fun onSessionUpdated() {
        updateUnreadChatCount()
        renderChatPanel()
        applyKeyboardAwarePlayerPanel()
        renderChatBadge()
        stageEventReactionsForCurrentAnnouncement()
    }

    fun onBackPressed(): Boolean {
        if (!isChatOpen) return false
        closeChatPanel()
        return true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CHAT_OPEN, isChatOpen)
    }

    fun onDestroy() {
        onlineChatListener?.remove()
        onlineChatListener = null
        cancelPendingBotChat()
        handler.removeCallbacksAndMessages(null)
    }

    fun openExpanded() {
        if (isChatOpen) return
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

    fun cancelPendingBotChat() {
        pendingBotChatRunnables.forEach(handler::removeCallbacks)
        pendingBotChatRunnables.clear()
        typingBotSpeakers.clear()
    }

    fun stageBotBurstForCurrentPhase() {
        if (host.isOnlineGameplay()) return
        val session = host.currentSession
        if (stagedBotBurstPhaseIndex == session.phaseIndex) return
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
        host.currentSession = session.copy(
            chatHistory = session.chatHistory.dropLast(trailingBotMessages.size) + visibleNow
        )
        stagedBotBurstPhaseIndex = host.currentSession.phaseIndex
        staged.filter { message ->
            val speaker = GameEngine.playerByName(host.currentSession, message.speaker)
            speaker != null && GameEngine.canParticipateInChat(host.currentSession, speaker)
        }.forEachIndexed { index, message ->
            scheduleBotChatMessage(
                speaker = message.speaker,
                message = message.message,
                phaseIndex = host.currentSession.phaseIndex,
                phase = host.currentSession.phase,
                delayMs = PHASE_BOT_BURST_DELAY_MS + index * NEXT_BOT_REACTION_DELAY_MS
            )
        }
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

    private fun renderChatPanelVisibility(animate: Boolean) {
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
                }
                .start()
        } else {
            chatPanel.visibility = View.GONE
            resetChatPanelTransform()
            renderAmbientChatFeed()
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
        renderAmbientChatFeed()
        if (!isChatOpen) return

        renderChatMessages(host.currentSession.chatHistory)

        val canChat = GameEngine.canHumanChat(host.currentSession)
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
        if (compact) {
            chatPanel.bringToFront()
            chatPanel.visibility = View.VISIBLE
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun shouldCompactBottomPlayerPanel(): Boolean {
        return host.isPortrait() && isChatOpen && isChatKeyboardCompact && chatInput.hasFocus()
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
        if (host.isPortrait()) {
            applyChatSheetDimensionsPortrait()
            return
        }
        val containerWidth = centerColumn.width.takeIf { it > 0 } ?: root.width
        val containerHeight = centerColumn.height.takeIf { it > 0 }
            ?: root.height.takeIf { it > 0 }
            ?: host.dp(CHAT_PANEL_MAX_HEIGHT_DP)
        val params = chatPanel.layoutParams as RelativeLayout.LayoutParams
        val widthRatio = if (isChatKeyboardCompact) {
            CHAT_PANEL_COMPACT_WIDTH_RATIO
        } else {
            CHAT_PANEL_WIDTH_RATIO
        }
        params.width = (containerWidth * widthRatio)
            .toInt()
            .coerceIn(
                host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MIN_WIDTH_DP else CHAT_PANEL_MIN_WIDTH_DP),
                host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MAX_WIDTH_DP else CHAT_PANEL_MAX_WIDTH_DP)
            )
        val heightRatio = if (isChatKeyboardCompact) {
            CHAT_PANEL_COMPACT_HEIGHT_RATIO
        } else {
            CHAT_PANEL_HEIGHT_RATIO
        }
        params.height = (containerHeight * heightRatio)
            .toInt()
            .coerceIn(
                host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MIN_HEIGHT_DP else CHAT_PANEL_MIN_HEIGHT_DP),
                host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MAX_HEIGHT_DP else CHAT_PANEL_MAX_HEIGHT_DP)
            )
        params.topMargin = host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MARGIN_DP else 0)
        params.bottomMargin = host.dp(if (isChatKeyboardCompact) CHAT_PANEL_COMPACT_MARGIN_DP else 0)
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        chatPanel.layoutParams = params
        chatPanelContent.setPadding(
            host.dp(if (isChatKeyboardCompact) 7 else 10),
            host.dp(if (isChatKeyboardCompact) 4 else 10),
            host.dp(if (isChatKeyboardCompact) 7 else 10),
            host.dp(if (isChatKeyboardCompact) 5 else 10)
        )
        chatHeader.layoutParams = chatHeader.layoutParams.apply {
            height = host.dp(if (isChatKeyboardCompact) 24 else 34)
        }
        chatComposer.layoutParams = chatComposer.layoutParams.apply {
            height = host.dp(if (isChatKeyboardCompact) 34 else 42)
        }
        chatStatusRow.layoutParams = chatStatusRow.layoutParams.apply {
            height = host.dp(if (isChatKeyboardCompact) 18 else 22)
        }
        chatInput.layoutParams = chatInput.layoutParams.apply {
            height = host.dp(if (isChatKeyboardCompact) 34 else 42)
        }
        btnSendChat.layoutParams = btnSendChat.layoutParams.apply {
            height = host.dp(if (isChatKeyboardCompact) 34 else 42)
        }
    }

    private fun applyChatSheetDimensionsPortrait() {
        val params = chatPanel.layoutParams as RelativeLayout.LayoutParams
        val heightRatio = if (isChatKeyboardCompact) {
            CHAT_SHEET_COMPACT_HEIGHT_RATIO
        } else {
            CHAT_SHEET_HEIGHT_RATIO
        }
        params.width = (centerColumn.width.takeIf { it > 0 } ?: (root.width - host.dp(140)))
            .coerceAtLeast(host.dp(210))
        params.height = host.dp((root.resources.configuration.screenHeightDp * heightRatio).toInt())
            .coerceIn(host.dp(CHAT_SHEET_MIN_HEIGHT_DP), host.dp(CHAT_SHEET_MAX_HEIGHT_DP))
        params.marginStart = 0
        params.marginEnd = 0
        params.topMargin = 0
        params.bottomMargin = 0
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
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

        val entries = ChronicleFeedPresenter.entries(host.currentSession.chatHistory)
            .filterNot { it.kind == ChronicleEntryKind.DAY_DIVIDER }
            .takeLast(CHAT_AMBIENT_MAX_MESSAGES)
        val canChat = GameEngine.canHumanChat(host.currentSession)
        if (entries.isEmpty() && !canChat) {
            chatAmbientFeed.visibility = View.GONE
            return
        }

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
            "Toca para hablar"
        } else {
            chatInputHint(canChat)
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
        return TextView(root.context).apply {
            text = "El pueblo aun no hablo"
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(root.context.getColor(R.color.text_secondary))
            textSize = 12f * host.gameplayTextScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(0, host.dp(2), 0, host.dp(2))
        }
    }

    private fun createAmbientFeedRow(entry: ChronicleEntry): View {
        if (entry.kind != ChronicleEntryKind.PLAYER) return createAmbientEventRow(entry)
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
            setTextColor(PlayerChatColor.colorFor(speakerName, host.currentSession))
            textSize = 11.5f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextView(root.context).apply {
            text = entry.text
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(root.context.getColor(R.color.text_primary))
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
        row.addView(TextView(root.context).apply {
            text = event.icon
            gravity = Gravity.CENTER
            setTextColor(event.iconColor)
            textSize = 8.5f * host.gameplayTextScale
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = host.dp(5).toFloat()
                setColor(event.backgroundColor)
                setStroke(host.dp(1), event.strokeColor)
            }
        }, LinearLayout.LayoutParams(host.dp(18), host.dp(18)).apply {
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
        val (compactTitle, expandedTitle) = when (host.currentSession.mapKey) {
            "grecia" -> "QUE SE DICE EN LA POLIS..." to "CRONISTA DE LA POLIS"
            "medieval" -> "QUE SE DICE EN EL FEUDO..." to "CRONISTA DEL FEUDO"
            else -> "QUE SE DICE EN EL PUEBLO..." to "CRONISTA DEL PUEBLO"
        }
        chatAmbientTitle.text = compactTitle
        chatFeedTitle.text = expandedTitle
    }

    private fun cronistaTypeface(): Typeface {
        return ResourcesCompat.getFont(root.context, R.font.bree_serif) ?: Typeface.DEFAULT_BOLD
    }

    private data class EventPresentation(
        val icon: String,
        val label: String,
        val backgroundColor: Int,
        val strokeColor: Int,
        val iconColor: Int
    )

    private fun eventPresentationFor(entry: ChronicleEntry): EventPresentation {
        val gold = root.context.getColor(R.color.accent_gold)
        return when (entry.kind) {
            ChronicleEntryKind.DEATH -> EventPresentation(
                icon = "X",
                label = "MUERTE",
                backgroundColor = Color.parseColor("#7A2A22"),
                strokeColor = Color.parseColor("#B46A72"),
                iconColor = Color.parseColor("#F0B2A8")
            )
            ChronicleEntryKind.EXPULSION -> EventPresentation(
                icon = "V",
                label = "EXPULSION",
                backgroundColor = Color.parseColor("#5F4524"),
                strokeColor = gold,
                iconColor = gold
            )
            ChronicleEntryKind.VOTE -> EventPresentation(
                icon = "V",
                label = "VOTACION",
                backgroundColor = Color.parseColor("#5F4524"),
                strokeColor = gold,
                iconColor = gold
            )
            ChronicleEntryKind.NIGHT -> EventPresentation(
                icon = "N",
                label = "NOCHE",
                backgroundColor = Color.parseColor("#25334F"),
                strokeColor = Color.parseColor("#6B86B8"),
                iconColor = Color.parseColor("#B7C7E8")
            )
            ChronicleEntryKind.DAWN -> EventPresentation(
                icon = "A",
                label = "AMANECER",
                backgroundColor = Color.parseColor("#6B5525"),
                strokeColor = Color.parseColor("#E3C46F"),
                iconColor = Color.parseColor("#F4D77D")
            )
            ChronicleEntryKind.SILENCE -> EventPresentation(
                icon = "S",
                label = "SILENCIO",
                backgroundColor = Color.parseColor("#4F3140"),
                strokeColor = Color.parseColor("#A26A88"),
                iconColor = Color.parseColor("#E6B6CE")
            )
            ChronicleEntryKind.TIE -> EventPresentation(
                icon = "!",
                label = "EMPATE",
                backgroundColor = Color.parseColor("#4B3B22"),
                strokeColor = gold,
                iconColor = gold
            )
            ChronicleEntryKind.SPECIAL_VICTORY -> EventPresentation(
                icon = "E",
                label = "ESPECIAL",
                backgroundColor = Color.parseColor("#493058"),
                strokeColor = Color.parseColor("#C392E6"),
                iconColor = Color.parseColor("#E2C8F8")
            )
            else -> EventPresentation(
                icon = "*",
                label = "SUCESO",
                backgroundColor = Color.parseColor("#4A3518"),
                strokeColor = gold,
                iconColor = gold
            )
        }
    }

    private fun createDayDivider(entry: ChronicleEntry): View {
        return TextView(root.context).apply {
            text = entry.text
            gravity = Gravity.CENTER
            setTextColor(root.context.getColor(R.color.accent_gold))
            textSize = 9f * host.gameplayTextScale
            typeface = cronistaTypeface()
            setPadding(0, host.dp(7), 0, host.dp(5))
        }
    }

    private fun renderChatMessages(messages: List<GameChatMessage>) {
        chatMessagesContainer.removeAllViews()
        val entries = ChronicleFeedPresenter.entries(messages, showOnlyEvents).takeLast(24)
        if (entries.isEmpty() && typingBotSpeakers.isEmpty()) {
            chatMessagesContainer.addView(TextView(root.context).apply {
                text = if (showOnlyEvents) {
                    "Todavia no hay sucesos."
                } else {
                    "Todavia no hay mensajes."
                }
                gravity = Gravity.CENTER
                setPadding(host.dp(8), host.dp(16), host.dp(8), host.dp(16))
                setTextColor(root.context.getColor(R.color.text_secondary))
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
                    root.context.getColor(R.color.bg_dark)
                } else {
                    PlayerChatColor.colorFor(speakerName, host.currentSession)
                },
                ownMessage = ownMessage,
                bubbleMaxWidth = bubbleMaxWidth,
                muted = false
            )
        }
        typingBotSpeakers.forEach { speaker ->
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

    private fun toggleFeedFilter() {
        showOnlyEvents = !showOnlyEvents
        renderFeedFilterButton()
        renderChatPanel()
        chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderFeedFilterButton() {
        btnChatFeedFilter.text = if (showOnlyEvents) "SUCESOS" else "TODO"
        btnChatFeedFilter.contentDescription = if (showOnlyEvents) {
            "Mostrar todo el feed"
        } else {
            "Mostrar solo sucesos"
        }
        btnChatFeedFilter.alpha = if (showOnlyEvents) 1f else 0.82f
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
        banner.addView(TextView(root.context).apply {
            text = event.label
            gravity = Gravity.CENTER
            setTextColor(event.iconColor)
            textSize = 8.5f * host.gameplayTextScale
            typeface = cronistaTypeface()
        })
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
        val row = LinearLayout(root.context).apply {
            gravity = if (ownMessage) Gravity.END else Gravity.START
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(3), 0, host.dp(3))
        }
        val bubble = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), host.dp(7), host.dp(10), host.dp(8))
            setBackgroundResource(
                if (ownMessage) {
                    R.drawable.bg_chat_bubble_own
                } else {
                    R.drawable.bg_chat_bubble_other
                }
            )
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
            setTextColor(root.context.getColor(if (ownMessage) R.color.bg_dark else R.color.text_primary))
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

    private fun sendHumanChatMessage() {
        val session = host.currentSession
        if (host.isTransitionLocked(session.phaseIndex)) {
            host.showToast("El chat se habilita al comenzar la fase.")
            return
        }
        val rawMessage = chatInput.text.toString()
        if (host.isOnlineGameplay()) {
            sendOnlineHumanChatMessage(rawMessage)
            return
        }
        val before = session.chatHistory.size
        val updated = GameEngine.addHumanChatMessage(
            session,
            rawMessage,
            includeBotReactions = false
        )
        host.currentSession = updated
        if (updated.chatHistory.size > before) {
            GameplayEffects.play(root.context, GameplayEffect.CHAT)
            scheduleBotChatReactions(rawMessage)
            clearChatComposerAfterSend()
            chatMessagesScroll.post { chatMessagesScroll.fullScroll(View.FOCUS_DOWN) }
        } else if (!GameEngine.canHumanChat(updated)) {
            GameplayEffects.play(root.context, GameplayEffect.ERROR)
            host.showToast(blockedChatMessage(updated))
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
        FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(host.onlineRoomId)
            .collection("chat")
            .add(
                mapOf(
                    "actorId" to host.onlinePlayerUid,
                    "speaker" to human.name,
                    "mensaje" to message,
                    "fase" to session.phase.name,
                    "ronda" to session.round,
                    "isGod" to false,
                    "creadaEn" to FieldValue.serverTimestamp(),
                    "creadaEnLocal" to System.currentTimeMillis()
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

    private fun startOnlineChatListener() {
        if (!host.isOnlineGameplay() || onlineChatListener != null) return
        OnlineDebugLog.i("chat_listener_start roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}")
        onlineChatListener = FirebaseFirestore.getInstance()
            .collection("partidas")
            .document(host.onlineRoomId)
            .collection("chat")
            .orderBy("creadaEnLocal")
            .limit(40)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("chat_listener_failure roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val entries = snapshot.documents.map { document ->
                    OnlineChatEntry(
                        id = document.id,
                        speaker = document.getString("speaker").orEmpty(),
                        message = document.getString("mensaje").orEmpty(),
                        isGod = document.getBoolean("isGod") ?: false
                    )
                }.filter { it.speaker.isNotBlank() && it.message.isNotBlank() }
                OnlineDebugLog.i("chat_snapshot roomId=${host.onlineRoomId} uid=${host.onlinePlayerUid} messages=${entries.size}")
                applyOnlineChatEntries(entries)
            }
    }

    private fun applyOnlineChatEntries(entries: List<OnlineChatEntry>) {
        val session = host.currentSession
        val previousCount = session.chatHistory.size
        val godMessages = session.chatHistory.filter { it.isGod }
        val onlineMessages = entries.map { GameChatMessage(it.speaker, it.message, it.isGod) }
        host.currentSession = session.copy(
            chatHistory = (godMessages + onlineMessages).takeLast(GameplayFeedMessages.MAX_FEED_MESSAGES)
        )
        if (host.currentSession.chatHistory.size > previousCount) {
            updateUnreadChatCount()
        }
        renderChatPanel()
        renderChatBadge()
    }

    private fun scheduleBotChatReactions(rawHumanMessage: String) {
        if (host.isOnlineGameplay()) return
        cancelPendingBotChat()
        val humanMessage = rawHumanMessage.trim().replace(Regex("\\s+"), " ").take(CHAT_MESSAGE_MAX_LENGTH)
        val session = host.currentSession
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

    private fun scheduleBotChatMessage(
        speaker: String,
        message: String,
        phaseIndex: Int,
        phase: GamePhase,
        delayMs: Long
    ) {
        val typingRunnable = object : Runnable {
            override fun run() {
                pendingBotChatRunnables.remove(this)
                val session = host.currentSession
                if (
                    session.phaseIndex != phaseIndex ||
                    session.phase != phase ||
                    session.winner.isNotBlank() ||
                    !canScheduledBotSpeak(session, speaker)
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
                    !canScheduledBotSpeak(session, speaker)
                ) {
                    return
                }
                val beforeCount = session.chatHistory.size
                val updated = GameEngine.addBotChatMessage(session, speaker, message)
                if (updated.chatHistory.size == beforeCount) return
                host.currentSession = updated
                GameplayEffects.play(root.context, GameplayEffect.CHAT)
                updateUnreadChatCount()
                renderChatPanel()
                renderChatBadge()
            }
        }
        pendingBotChatRunnables += typingRunnable
        pendingBotChatRunnables += runnable
        handler.postDelayed(
            typingRunnable,
            (delayMs - BOT_TYPING_LEAD_DELAY_MS).coerceAtLeast(250L)
        )
        handler.postDelayed(runnable, delayMs)
    }

    private fun canScheduledBotSpeak(session: GameSession, speaker: String): Boolean {
        val player = GameEngine.playerByName(session, speaker) ?: return false
        return !player.isHuman && GameEngine.canParticipateInChat(session, player)
    }

    private fun botReactionDelayMs(index: Int, message: String): Long {
        val readingDelay = (message.length * 38L).coerceAtMost(1_800L)
        val punctuationDelay = message.count { it == '?' || it == ',' }.coerceAtMost(3) * 180L
        val humanJitter = ((message.hashCode() and 0x7fffffff) % 1_250).toLong()
        return FIRST_BOT_REACTION_DELAY_MS +
            humanJitter +
            index * NEXT_BOT_REACTION_DELAY_MS +
            readingDelay +
            punctuationDelay
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

    private fun chatInputHint(canChat: Boolean): String {
        if (canChat) return "Escribir..."
        val session = host.currentSession
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

    private fun blockedChatMessage(session: GameSession): String {
        val human = GameEngine.humanPlayer(session)
        return when {
            !human.alive -> "Estás eliminado. Puedes mirar el chat, pero no escribir."
            human.muted -> "Estás silenciado. Puedes mirar el chat, pero no escribir."
            GameplayTableUi.isNightPhase(session.phase) ->
                "El pueblo duerme. Las voces deben esperar al amanecer."
            else -> "No puedes escribir durante esta fase."
        }
    }

    private fun renderChatBackgrounds() {
        val logDrawable = host.chatLogDrawableRes()
        chatPanelBackground.setImageResource(logDrawable)
        chatAmbientBackground.setImageResource(logDrawable)
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

    companion object {
        private const val STATE_CHAT_OPEN = "chat_open"
        private const val CHAT_PANEL_WIDTH_RATIO = 0.58f
        private const val CHAT_PANEL_HEIGHT_RATIO = 0.68f
        private const val CHAT_PANEL_COMPACT_WIDTH_RATIO = 0.78f
        private const val CHAT_PANEL_COMPACT_HEIGHT_RATIO = 0.58f
        private const val CHAT_PANEL_MIN_WIDTH_DP = 340
        private const val CHAT_PANEL_MAX_WIDTH_DP = 560
        private const val CHAT_PANEL_MIN_HEIGHT_DP = 340
        private const val CHAT_PANEL_MAX_HEIGHT_DP = 520
        private const val CHAT_PANEL_COMPACT_MIN_WIDTH_DP = 300
        private const val CHAT_PANEL_COMPACT_MAX_WIDTH_DP = 620
        private const val CHAT_PANEL_COMPACT_MIN_HEIGHT_DP = 230
        private const val CHAT_PANEL_COMPACT_MAX_HEIGHT_DP = 380
        private const val CHAT_PANEL_COMPACT_MARGIN_DP = 5
        private const val CHAT_SHEET_HEIGHT_RATIO = 0.52f
        private const val CHAT_SHEET_COMPACT_HEIGHT_RATIO = 0.74f
        private const val CHAT_SHEET_MIN_HEIGHT_DP = 320
        private const val CHAT_SHEET_MAX_HEIGHT_DP = 560
        private const val CHAT_AMBIENT_MAX_MESSAGES = 4
        private const val BOTTOM_PLAYER_PANEL_HEIGHT_DP = 146
        private const val BOTTOM_PLAYER_PANEL_COMPACT_HEIGHT_DP = 42
        private const val CHAT_MESSAGE_MAX_LENGTH = 140
        private const val CHAT_MESSAGE_WARNING_LENGTH = 120
        private const val ONLINE_CHAT_COOLDOWN_MS = 1200L
        private const val MAX_STAGGERED_BOT_REACTIONS = 3
        private const val MAX_EVENT_BOT_REACTIONS = 3
        private const val FIRST_BOT_REACTION_DELAY_MS = 3_200L
        private const val NEXT_BOT_REACTION_DELAY_MS = 2_650L
        private const val PHASE_BOT_BURST_DELAY_MS = 1_800L
        private const val EVENT_BOT_REACTION_DELAY_MS = 2_400L
        private const val BOT_TYPING_LEAD_DELAY_MS = 1_650L
    }
}
