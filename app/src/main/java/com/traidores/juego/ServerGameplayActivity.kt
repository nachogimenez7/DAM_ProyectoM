package com.traidores.juego

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth

/** V3 presentation only. It never runs GameEngine or writes roles, votes, results or timers. */
class ServerGameplayActivity : BaseActivity() {
    private lateinit var roomId: String
    private lateinit var matchId: String
    private lateinit var uid: String
    private lateinit var identity: GameSession
    private var creatorId = ""
    private val callable = ServerGameCallableClient()
    private lateinit var realtime: ServerGameRealtimeClient
    private lateinit var sender: ServerGameActionSender
    private lateinit var chat: ServerGameChat
    private lateinit var recovery: ServerGameRecoveryPolicy
    private val clock = OnlineServerClock { updateClock(); refreshControls() }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var artwork: ImageView
    private lateinit var title: TextView
    private lateinit var countdown: TextView
    private lateinit var status: TextView
    private lateinit var body: LinearLayout
    private lateinit var actions: LinearLayout
    private lateinit var channelButton: Button
    private lateinit var messagesView: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var retry: Button
    private var snapshot: ServerGameSnapshot? = null
    private var coherent = false
    private var connected = false
    private var active = false
    private var navigating = false
    private var leaving = false
    private var rematching = false
    private var recoveryRunning = false
    private var chatSending = false
    private var channel = "publico"
    private var messages = emptyList<ServerChatMessage>()
    private var restored: ServerGameCommand? = null
    private var awaitingProjection: ServerGameCommand? = null
    private var controlKey = ""
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            updateClock(); refreshControls()
            val state = snapshot?.publicState
            val now = clock.nowMs()
            if (connected && coherent && !recoveryRunning && state != null && now != null && recovery.due(state, now)) {
                recovery.attempted(now); recoveryRunning = true
                callable.recover(roomId, state).addOnCompleteListener {
                    recoveryRunning = false
                    if (active && !it.isSuccessful) status.text = "Esperando que el servidor complete la fase…"
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        roomId = intent.getStringExtra(LobbyActivity.EXTRA_PARTIDA_ID).orEmpty()
        matchId = intent.getStringExtra(EXTRA_MATCH_ID).orEmpty()
        creatorId = intent.getStringExtra(EXTRA_CREATOR_ID).orEmpty()
        val savedIdentity = if (Build.VERSION.SDK_INT >= 33) intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION, GameSession::class.java)
            else @Suppress("DEPRECATION") (intent.getSerializableExtra(LobbyActivity.EXTRA_SESSION) as? GameSession)
        if (uid.isBlank() || roomId.isBlank() || matchId.isBlank() || savedIdentity == null) {
            Toast.makeText(this, "No se pudo recuperar esta partida.", Toast.LENGTH_LONG).show(); finish(); return
        }
        identity = savedIdentity
        restored = readCommand(savedInstanceState, "pending")
        awaitingProjection = readCommand(savedInstanceState, "awaiting")
        buildScreen()
        recovery = ServerGameRecoveryPolicy(uid)
        sender = ServerGameActionSender(uid, callable, onPending = { refreshControls() },
            onConfirmed = { command -> awaitingProjection = command; clearConfirmedIntention(); refreshControls() },
            onError = { error -> showError("No se pudo confirmar la acción", error) })
        chat = ServerGameChat(roomId, uid, onMessages = { messages = it; renderMessages() },
            onError = { if (active && coherent && !leaving) showError("No se pudo conectar el chat", it) })
        realtime = ServerGameRealtimeClient(roomId, matchId, uid,
            onSnapshot = { value ->
                if (!active || navigating || leaving) return@ServerGameRealtimeClient
                snapshot = value; coherent = true
                sender.update(value.publicState); clearConfirmedIntention()
                renderState(); refreshControls(); updateClock()
            },
            onConnection = { value -> connected = value; updateStatus(); refreshControls() },
            onSynchronizing = { coherent = false; updateStatus(); refreshControls() },
            onRematch = { if (active && !navigating && !leaving) openLobby() },
            onError = { error ->
                coherent = false
                if (error is ServerGameAccessLost) {
                    snapshot = null; awaitingProjection = null; body.removeAllViews()
                    chat.stop(); messages = emptyList(); renderMessages()
                }
                if (active && !leaving && !navigating) { showError("No se pudo sincronizar la partida", error); refreshControls() }
            })
        onBackPressedDispatcher.addCallback(this) { confirmLeave() }
        OnlineNetworkMetrics.beginMatch(matchId)
    }
    override fun onStart() {
        super.onStart()
        if (!::realtime.isInitialized || navigating) return
        active = true; coherent = false; connected = false
        sender.start(restored); restored = null
        clock.start(); realtime.start(); handler.post(tick)
    }
    override fun onStop() {
        active = false; coherent = false; handler.removeCallbacksAndMessages(null)
        if (::sender.isInitialized) sender.stop()
        if (::chat.isInitialized) chat.stop()
        if (::realtime.isInitialized) realtime.stop()
        clock.stop(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::sender.isInitialized) outState.putSerializable("pending", sender.pending)
        outState.putSerializable("awaiting", awaitingProjection)
        super.onSaveInstanceState(outState)
    }
    @Suppress("DEPRECATION")
    private fun readCommand(bundle: Bundle?, key: String): ServerGameCommand? =
        if (Build.VERSION.SDK_INT >= 33) bundle?.getSerializable(key, ServerGameCommand::class.java)
        else bundle?.getSerializable(key) as? ServerGameCommand

    private fun buildScreen() {
        val root = FrameLayout(this)
        artwork = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(artwork, FrameLayout.LayoutParams(-1, -1))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = panel(); setPadding(dp(8), dp(4), dp(6), dp(4))
        }
        title = label("PARTIDA EN LÍNEA", 22f, gold())
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("SALIR") { confirmLeave() }, LinearLayout.LayoutParams(-2, dp(48)))
        column.addView(header)
        countdown = label("", 16f, gold()).apply { gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(26, 21, 16)) }
        status = label("Conectando…", 13f).apply {
            gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(26, 21, 16)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        column.addView(countdown); column.addView(status)
        retry = button("REINTENTAR") {
            if (::sender.isInitialized && sender.canRetry) sender.retryPending()
            else { coherent = false; realtime.stop(); realtime.start() }
            updateStatus(); refreshControls()
        }.apply { visibility = View.GONE }
        column.addView(retry)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        column.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(actions)
        val chatPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = panel(); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        channelButton = button("CHAT · PUEBLO") { chooseChannel() }
        chatPanel.addView(channelButton, LinearLayout.LayoutParams(-1, dp(44)))
        messagesView = label("", 14f)
        chatPanel.addView(ScrollView(this).apply { addView(messagesView) }, LinearLayout.LayoutParams(-1, dp(105)))
        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_secondary))
            hint = "Mensaje…"; textSize = 14f; maxLines = 3; filters = arrayOf(InputFilter.LengthFilter(300))
            contentDescription = "Mensaje para el chat"; backgroundTintList = android.content.res.ColorStateList.valueOf(gold())
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = button("ENVIAR") { sendChat() }
        composer.addView(sendButton, LinearLayout.LayoutParams(-2, dp(48)))
        chatPanel.addView(composer); column.addView(chatPanel)
        setContentView(root)
        artwork.setImageResource(background(false))
    }
    private fun renderState() {
        val state = snapshot ?: return
        updateStatus(); controlKey = ""
        title.text = "${state.publicState.phase.title.uppercase()} · ${state.publicState.round}"
        artwork.setImageResource(background(state.publicState.phase == ServerGamePhase.NOCHE))
        body.removeAllViews()
        val role = role(state.ownRoleKey)
        val rolePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = panel(); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        rolePanel.addView(label("TU ROL · ${role.name}", 19f, gold()))
        if (state.publicState.phase == ServerGamePhase.REPARTO) rolePanel.addView(ImageView(this).apply {
            setImageResource(DrawableResourceCatalog.resolveOrPlaceholder(role.imageResName)); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = role.name
        }, LinearLayout.LayoutParams(-1, dp(190)))
        val condition = when { !state.human.alive -> "Estás eliminado. Podés seguir como espectador."; state.human.muted -> "Estás silenciado durante este día."; else -> "${role.team} · ${state.human.name}" }
        rolePanel.addView(label(condition, 14f))
        state.privateState.deserterTeam?.let { rolePanel.addView(label("Tu bando: $it", 14f)) }
        body.addView(rolePanel)
        body.addView(label(state.publicState.announcement, 16f).apply { background = panel(); setPadding(dp(10), dp(10), dp(10), dp(10)) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        state.privateState.investigations.forEach { (target, traitor) ->
            body.addView(label("Investigación: ${state.publicState.players.firstOrNull { it.uid == target }?.name ?: "Jugador"} · ${if (traitor) "Traidor" else "Pueblo"}", 14f, gold()))
        }
        if (state.publicState.winner != null) {
            val result = if (state.publicState.winner == "Cancelada") "PARTIDA CANCELADA"
                else if (state.won(state.human)) "VICTORIA" else "DERROTA"
            body.addView(label("$result · ${state.publicState.winner}", 24f, gold()).apply { gravity = Gravity.CENTER })
            state.publicState.players.filter { state.won(it) }.forEach { winner ->
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = panel(); setPadding(dp(8), dp(10), dp(8), dp(10)) }
                card.addView(label(winner.name, 18f, gold()))
                card.addView(label(state.roleKey(winner)?.let { role(it).name }.orEmpty(), 15f))
                card.addView(portrait(winner), LinearLayout.LayoutParams(dp(52), dp(52)).apply { topMargin = dp(6) })
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
        }
        val roster = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = panel(); setPadding(dp(8), dp(6), dp(8), dp(6)) }
        roster.addView(label("JUGADORES", 17f, gold()).apply { setPadding(0, dp(6), 0, dp(6)) })
        state.publicState.players.forEach { player ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)); alpha = if (player.alive) 1f else 0.68f }
            row.addView(portrait(player), LinearLayout.LayoutParams(dp(42), dp(42)))
            val knownRole = state.roleKey(player)?.let { role(it).name }
            val suffix = when { player.deathCause == "ABANDONO" -> "Abandonó"; !player.alive -> "Eliminado"; player.muted -> "Silenciado"; else -> "En partida" }
            row.addView(label("${player.name}${player.publicId.takeIf { it.isNotBlank() }?.let { " · #$it" }.orEmpty()}\n${knownRole?.let { "$it · " }.orEmpty()}$suffix", 14f).apply { setPadding(dp(10), 0, 0, 0) })
            roster.addView(row)
        }
        body.addView(roster, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        body.addView(button("VER ANUNCIOS") {
            GameDialog.choose(this, "ANUNCIOS DE LA PARTIDA", "", state.publicState.events.takeLast(30).map { "Día ${it.round}: ${it.text}" }) { }
        })
        if (BuildConfig.DEBUG) body.addView(button("MEDICIÓN DE RED") { GameNotice.show(this, OnlineNetworkMetrics.summary()) })
        val readable = ServerGameChat.channels(state)
        if (channel !in readable) { channel = "publico"; messages = emptyList() }
        chat.watch(channel, state); renderMessages()
    }
    private fun refreshControls() {
        if (!::actions.isInitialized) return
        val state = snapshot
        val ready = active && coherent && connected && !leaving && !rematching
        val options = if (ready && state != null && (!::sender.isInitialized || sender.pending == null) && awaitingProjection == null)
            ServerGameActionPolicy.options(state, clock.nowMs()) else emptyList()
        val pending = if (::sender.isInitialized) sender.pending else null
        val key = "$ready:${state?.publicState?.phaseIndex}:${state?.publicState?.revision}:$options:$pending:$awaitingProjection"
        if (key != controlKey) {
            controlKey = key; actions.removeAllViews()
            options.forEach { option -> actions.addView(button(option.label) { chooseAction(option) }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(5) }) }
            if (::sender.isInitialized && (sender.pending != null || awaitingProjection != null))
                actions.addView(label(if (sender.canRetry) "La acción sigue pendiente. Podés reintentar." else "Esperando confirmación del servidor…", 13f, gold()))
            if (state?.publicState?.winner != null && uid == creatorId)
                actions.addView(button(if (rematching) "PREPARANDO…" else "PREPARAR REVANCHA") { prepareRematch() }.apply { isEnabled = ready })
        }
        if (state != null) {
            channelButton.text = "CHAT · ${ServerGameChat.title(channel)}"
            val allowed = ready && ServerGameChat.canSend(channel, state)
            input.isEnabled = allowed; sendButton.isEnabled = allowed && !chatSending
            input.hint = when { !ready -> "Esperando conexión…"; allowed -> "Mensaje…"; else -> "Chat disponible solo para lectura" }
        } else { input.isEnabled = false; sendButton.isEnabled = false }
        retry.visibility = if (active && (!coherent && connected || ::sender.isInitialized && sender.canRetry)) View.VISIBLE else View.GONE
    }
    private fun clearConfirmedIntention() {
        val command = awaitingProjection ?: return
        val state = snapshot ?: return
        if (state.publicState.matchId != command.matchId || state.publicState.phaseIndex != command.phaseIndex ||
            command.action == "contrapunto" && command.targetUid in state.publicState.counterpointPlayers ||
            command.action == "desertor_rethink" && state.privateState.deserterUsed ||
            state.privateState.confirmed.any { it.action == command.action && it.targetUid == command.targetUid && it.team == command.team })
            awaitingProjection = null
    }
    private fun chooseAction(option: ServerGameActionOption) {
        val original = snapshot ?: return
        fun submit(target: String? = null) {
            val latest = snapshot ?: return
            if (!active || !coherent || !connected || original.publicState.phaseIndex != latest.publicState.phaseIndex ||
                option !in ServerGameActionPolicy.options(latest, clock.nowMs())) return
            sender.submit(ServerGameCommand.create(roomId, latest, option.action, target, option.team))
        }
        if (option.targets.isEmpty()) submit() else GameDialog.choose(this, option.label, "Elegí un jugador.",
            option.targets.map { target -> original.publicState.players.first { it.uid == target }.name }) { index -> submit(option.targets[index]) }
    }
    private fun chooseChannel() {
        val state = snapshot ?: return
        val choices = ServerGameChat.channels(state)
        GameDialog.choose(this, "CHAT", "", choices.map(ServerGameChat::title)) { index ->
            val latest = snapshot ?: return@choose
            if (choices[index] !in ServerGameChat.channels(latest)) return@choose
            channel = choices[index]; messages = emptyList(); chat.watch(channel, latest); renderMessages(); refreshControls()
        }
    }
    private fun sendChat() {
        val state = snapshot ?: return
        if (!active || !coherent || !connected || chatSending) return
        val text = input.text.toString(); chatSending = true; refreshControls()
        chat.send(text, state, clock.nowMs()).addOnCompleteListener { task ->
            chatSending = false
            if (!active) return@addOnCompleteListener
            if (task.isSuccessful) { if (input.text.toString() == text) input.text.clear() }
            else showError("No se pudo enviar el mensaje", task.exception ?: IllegalStateException("Sin confirmación."))
            refreshControls()
        }
    }
    private fun renderMessages() {
        messagesView.text = if (messages.isEmpty()) "Todavía no hay mensajes en este canal." else messages.joinToString("\n") { message ->
            val name = snapshot?.publicState?.players?.firstOrNull { it.uid == message.uid }?.name ?: "Jugador"
            "$name: ${message.text}"
        }
    }
    private fun updateStatus() {
        if (!::status.isInitialized || leaving || navigating) return
        status.text = when { !connected -> "Sin conexión. Intentando reconectar…"; !coherent -> "Sincronizando partida…"; else -> "" }
    }
    private fun updateClock() {
        if (!::countdown.isInitialized) return
        val deadline = snapshot?.publicState?.deadlineMs
        val now = clock.nowMs()
        countdown.text = when { deadline == null -> ""; now == null -> "Sincronizando reloj…"; now >= deadline -> "Esperando al servidor…"; else -> "${(deadline - now + 999) / 1000} s" }
    }
    private fun prepareRematch() {
        if (rematching || !coherent || !connected || snapshot?.publicState?.winner == null || uid != creatorId) return
        rematching = true; refreshControls()
        callable.rematch(roomId, matchId).addOnCompleteListener { task ->
            if (navigating) return@addOnCompleteListener
            rematching = false
            if (!task.isSuccessful) { showError("No se pudo preparar la revancha", task.exception ?: IllegalStateException("Sin confirmación.")); refreshControls() }
            // The listener routes only after the server has published clean membership.
        }
    }
    private fun confirmLeave() {
        if (leaving || navigating) return
        val message = if (snapshot?.publicState?.winner == null)
            "Salir te elimina por abandono y no recibirás una victoria. Si solo perdés conexión, podés volver a la partida."
            else "¿Querés salir de esta sala? El resultado de la partida queda guardado."
        GameDialog.confirm(this, "SALIR DE LA PARTIDA", message, "SALIR") {
            leaving = true; status.text = "Confirmando salida…"; refreshControls()
            callable.leave(roomId, matchId).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    navigating = true; OnlineRoomRecovery.clearIf(this, roomId)
                    startActivity(Intent(this, OnlineModeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                } else { leaving = false; showError("No se pudo confirmar la salida", task.exception ?: IllegalStateException("Sin confirmación.")); refreshControls(); realtime.stop(); realtime.start() }
            }
        }
    }
    private fun openLobby() {
        navigating = true
        startActivity(Intent(this, LobbyActivity::class.java)
            .putExtra(LobbyActivity.EXTRA_SESSION, identity.copy(onlineMatchId = ""))
            .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, if (uid == creatorId) LobbyActivity.MODE_ONLINE_CREATE else LobbyActivity.MODE_ONLINE_SEARCH)
            .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, OnlineRoomRecovery.load(this)?.roomName ?: "Sala online")
            .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, roomId).putExtra(LobbyActivity.EXTRA_ROOM_CODE, identity.code)
            .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, false)
            .putExtra(LobbyActivity.EXTRA_RETURNED_FROM_ONLINE_MATCH, true))
        finish()
    }
    private fun portrait(player: ServerGamePlayer): ImageView = CircleProfileImageView(this).apply {
        val profile = identity.playerProfiles[player.name]
        contentDescription = "Foto de ${player.name}"; background = panel()
        ProfilePortraitRenderer.render(this@ServerGameplayActivity, this, profile?.avatarKey ?: "pampa_aldeano", profile?.publicAvatarUri.orEmpty())
    }
    private fun showError(action: String, error: Exception) {
        if (!active || navigating || leaving) return
        status.text = OnlineErrorMessages.forAction(action, error)
        OnlineDebugLog.e("server_v3_client_error room=$roomId action=$action", error)
        Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        refreshControls()
    }
    private fun role(key: String) = RoleCatalog.gameRole(key, RoleMap.fromSessionKey(identity.mapKey))
    private fun background(night: Boolean) = GameplayThemeResolver.backgroundDrawableFor(GameplayTableUi.themeForMapKey(identity.mapKey), night)
    private fun label(value: String, size: Float, color: Int = getColor(R.color.text_primary)) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (size >= 17f) typeface = ResourcesCompat.getFont(this@ServerGameplayActivity, R.font.bree_serif)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 13f; setTextColor(gold()); typeface = Typeface.DEFAULT_BOLD
        background = getDrawable(R.drawable.bg_btn_dark_ripple); minHeight = dp(48); setOnClickListener { action() }
    }
    private fun panel() = GradientDrawable().apply { setColor(Color.rgb(26, 21, 16)); cornerRadius = dp(14).toFloat(); setStroke(dp(1), gold()) }
    private fun gold() = getColor(R.color.accent_gold)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        const val EXTRA_MATCH_ID = "server_match_id"
        const val EXTRA_CREATOR_ID = "server_creator_id"
    }
}
