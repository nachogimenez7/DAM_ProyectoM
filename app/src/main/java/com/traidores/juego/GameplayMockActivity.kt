package com.traidores.juego

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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

class GameplayMockActivity : BaseActivity() {

    private var isCardRevealed = false
    private var isChatOpen = false
    private var isEventLogExpanded = true
    private var lastRenderedAnnouncement = ""
    private var lastRenderedPhase: GamePhase? = null
    private var lastSeenChatCount = 0
    private var selectedTarget = ""
    private lateinit var session: GameSession
    private var unreadChatCount = 0
    private val autoAdvanceHandler = Handler(Looper.getMainLooper())
    private val autoAdvanceRunnable = Runnable { handleCurrentPhase() }
    private val narratorCollapseRunnable = Runnable { collapseNarrator() }

    private var chatDialog: Dialog? = null
    private var dialogChatInput: EditText? = null
    private var dialogChatMessages: TextView? = null
    private var dialogSendButton: Button? = null

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
    private lateinit var roleImage: ImageView
    private lateinit var roleName: TextView
    private lateinit var topStatus: LinearLayout
    private lateinit var themeKey: String
    private var initialNight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        session = readSession() ?: LocalGameFactory.assignRoles(LocalGameFactory.createSession())
        themeKey = themeFromIntentOrSession()
        initialNight = intent.getBooleanExtra(EXTRA_ES_NOCHE, false)
        lastSeenChatCount = session.chatHistory.size

        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        btnAction = findViewById(R.id.btnVote)
        btnRevealCard = findViewById(R.id.btnRevealCard)
        btnToggleChat = findViewById(R.id.btnToggleChat)
        btnToggleEventLog = findViewById(R.id.btnToggleEventLog)
        chatUnreadBadge = findViewById(R.id.chatUnreadBadge)
        currentPlayerHint = findViewById(R.id.currentPlayerHint)
        currentPlayerName = findViewById(R.id.currentPlayerName)
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
        roleImage = findViewById(R.id.roleImage)
        roleName = findViewById(R.id.roleName)
        topStatus = findViewById(R.id.topStatus)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, OpcionesActivity::class.java))
        }
        btnAction.setOnClickListener { handleCurrentPhase() }
        btnRevealCard.setOnClickListener { toggleHumanCard() }
        btnToggleChat.setOnClickListener { toggleChatDialog() }
        btnToggleEventLog.setOnClickListener { toggleEventLog() }

        eventLogBackground.setImageResource(logDrawableFor(themeKey))
        renderGame()
    }

    override fun onDestroy() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable)
        autoAdvanceHandler.removeCallbacks(narratorCollapseRunnable)
        chatDialog?.dismiss()
        super.onDestroy()
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
            GamePhase.VOTACION -> GameEngine.resolveVoting(session, "")
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
        val phaseText = phaseText(session.phase)
        val publicMessage = if (session.winner.isNotBlank()) {
            "Fin de partida. Gano ${session.winner}."
        } else {
            session.publicAnnouncement.ifBlank { phaseText.subtitle }
        }
        MusicManager.playGamePhase(this, session.phase, session.winner.isNotBlank())
        val eventChanged = lastRenderedPhase != session.phase || lastRenderedAnnouncement != publicMessage
        updateUnreadChatCount()
        renderThemedBackground()
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
        scheduleAutoAdvanceIfNeeded()
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
            waitsForTarget -> "ELIGE CARTA"
            session.phase == GamePhase.REPARTO -> "NOCHE"
            else -> phaseText(session.phase).actionLabel
        }
        btnAction.isEnabled = session.winner.isBlank() && (!waitsForTarget || canSelfProtect)
        btnAction.alpha = if (btnAction.isEnabled) 1f else 0.55f
    }

    private fun renderPlayerColumns() {
        leftPlayersContainer.removeAllViews()
        rightPlayersContainer.removeAllViews()

        val (leftPlayers, rightPlayers) = GameplayTableUi.splitCompanions(session.players)
        val totalPlayers = session.players.size.coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
        leftPlayers.forEach { player ->
            leftPlayersContainer.addView(createSidePlayerCard(player, totalPlayers))
        }
        rightPlayers.forEach { player ->
            rightPlayersContainer.addView(createSidePlayerCard(player, totalPlayers))
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
        avatar.text = if (isActive) player.initial else "\u2620"
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
            GamePhase.VOTACION -> PhaseText("VOTACION", "Toca una carta para emitir tu voto.", "VOTAR")
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
            GamePhase.VOTACION -> "Toca una carta con VOTAR para acusar publicamente."
            else -> "Toca una carta valida."
        }
    }

    private fun roleImageFor(role: GameRole?): Int {
        if (role == null) return android.R.drawable.ic_menu_gallery
        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        return if (resId != 0) resId else android.R.drawable.ic_menu_gallery
    }

    private fun renderThemedBackground() {
        val isNight = if (lastRenderedPhase == null) {
            initialNight || GameplayTableUi.isNightPhase(session.phase)
        } else {
            GameplayTableUi.isNightPhase(session.phase)
        }
        mapBackground.setImageResource(backgroundDrawableFor(themeKey, isNight))
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

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession
    }

    private data class PhaseText(
        val title: String,
        val subtitle: String,
        val actionLabel: String
    )

    companion object {
        const val EXTRA_TEMA = "tema"
        const val EXTRA_ES_NOCHE = "es_noche"
    }
}
