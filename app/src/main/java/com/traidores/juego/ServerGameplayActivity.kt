package com.traidores.juego

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.addCallback
import com.google.firebase.auth.FirebaseAuth

/** V3 presentation only. It never runs GameEngine or writes roles, votes, results or timers. */
class ServerGameplayActivity : BaseActivity() {
    private val sharedWindowInsets = GameplayWindowInsets()
    override fun onSystemBarInsetsChanged(safeArea: androidx.core.graphics.Insets) {
        sharedWindowInsets.apply(this, safeArea)
    }
    private lateinit var roomId: String
    private lateinit var matchId: String
    private lateinit var uid: String
    private lateinit var identity: GameSession
    private var creatorId = ""
    private val callable = ServerGameCallableClient()
    private lateinit var realtime: ServerGameRealtimeClient
    private lateinit var sender: ServerGameActionSender
    private lateinit var reactions: ServerGameReactions
    private lateinit var chat: ServerGameChat
    private lateinit var recovery: ServerGameRecoveryPolicy
    private val clock = OnlineServerClock { updateClock(); refreshControls() }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var table: ServerGameTableRenderer
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
    private lateinit var presentation: ServerGamePresentationTracker
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
                    if (active && !it.isSuccessful) table.setStatus("Esperando que el servidor complete la fase…")
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
        val savedCursor = if (Build.VERSION.SDK_INT >= 33)
            savedInstanceState?.getSerializable("v3_presentation", ServerGamePresentationCursor::class.java)
        else @Suppress("DEPRECATION") (savedInstanceState?.getSerializable("v3_presentation") as? ServerGamePresentationCursor)
        presentation = ServerGamePresentationTracker(savedCursor?.takeIf { it.matchId == matchId } ?: loadCursor())
        buildScreen()
        recovery = ServerGameRecoveryPolicy(uid)
        sender = ServerGameActionSender(uid, callable, onPending = { refreshControls() },
            onConfirmed = { command ->
                awaitingProjection = command; clearConfirmedIntention(); refreshControls()
                table.actionConfirmed(command)
            },
            onError = { error -> table.actionFailed(); showError("No se pudo confirmar la acción", error) })
        chat = ServerGameChat(roomId, uid, onMessages = { messages = it; renderMessages() },
            onError = { if (active && coherent && !leaving) showError("No se pudo conectar el chat", it) })
        reactions = ServerGameReactions(roomId, uid, { table.reaction(it) }, { if (active) showError("No se pudieron conectar los emotes", it) })
        realtime = ServerGameRealtimeClient(roomId, matchId, uid,
            onSnapshot = { value ->
                if (!active || navigating || leaving) return@ServerGameRealtimeClient
                val floor = presentation.cursor
                if (floor?.matchId == value.publicState.matchId && (value.publicState.phaseIndex < floor.phaseIndex ||
                    value.publicState.phaseIndex == floor.phaseIndex && value.publicState.revision < floor.revision)) {
                    coherent = false; updateStatus(); refreshControls(); return@ServerGameRealtimeClient
                }
                val fresh = presentation.accept(value.publicState)
                saveCursor()
                snapshot = value; coherent = true
                sender.update(value.publicState); clearConfirmedIntention()
                table.render(value, fresh); renderState(); refreshControls(); updateClock()
            },
            onConnection = { value -> connected = value; updateStatus(); refreshControls() },
            onSynchronizing = { coherent = false; updateStatus(); refreshControls() },
            onRematch = { if (active && !navigating && !leaving) openLobby() },
            onError = { error ->
                coherent = false
                if (error is ServerGameAccessLost) {
                    snapshot = null; awaitingProjection = null; table.clear()
                    reactions.stop(); chat.stop(); messages = emptyList(); renderMessages()
                }
                if (active && !leaving && !navigating) { showError("No se pudo sincronizar la partida", error); refreshControls() }
            })
        onBackPressedDispatcher.addCallback(this) { if (table.chatOpen) table.setChatOpen(false) else confirmLeave() }
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
        if (::reactions.isInitialized) reactions.stop()
        if (::chat.isInitialized) chat.stop()
        if (::realtime.isInitialized) realtime.stop()
        if (::table.isInitialized) table.stop()
        clock.stop(); super.onStop()
    }
    override fun onDestroy() {
        if (::table.isInitialized) table.destroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::sender.isInitialized) outState.putSerializable("pending", sender.pending)
        outState.putSerializable("awaiting", awaitingProjection)
        if (::presentation.isInitialized) outState.putSerializable("v3_presentation", presentation.cursor)
        super.onSaveInstanceState(outState)
    }
    @Suppress("DEPRECATION")
    private fun readCommand(bundle: Bundle?, key: String): ServerGameCommand? =
        if (Build.VERSION.SDK_INT >= 33) bundle?.getSerializable(key, ServerGameCommand::class.java)
        else bundle?.getSerializable(key) as? ServerGameCommand

    private fun buildScreen() {
        table = ServerGameTableRenderer(this, identity, ::submitAction, ::chatVisibility, ::chooseChannel,
            ::sendChat, ::prepareRematch, ::confirmLeave, ::retry, { GameNotice.show(this, OnlineNetworkMetrics.summary()) }, clock::nowMs, ::sendReaction)
        table.bind()
    }
    private fun loadCursor(): ServerGamePresentationCursor? {
        val prefs = getSharedPreferences("v3_presentation", MODE_PRIVATE)
        if (prefs.getString(uid + ":match", null) != matchId) return null
        return ServerGamePresentationCursor(matchId, prefs.getInt(uid + ":phase", -1),
            prefs.getLong(uid + ":revision", -1), prefs.getLong(uid + ":seq", 0))
    }
    private fun saveCursor() {
        val cursor = presentation.cursor ?: return
        getSharedPreferences("v3_presentation", MODE_PRIVATE).edit()
            .putString(uid + ":match", cursor.matchId).putInt(uid + ":phase", cursor.phaseIndex)
            .putLong(uid + ":revision", cursor.revision).putLong(uid + ":seq", cursor.eventSeq).apply()
    }
    private fun renderState() {
        val state = snapshot ?: return
        updateStatus(); controlKey = ""
        if (channel !in ServerGameChat.channels(state)) { channel = "publico"; messages = emptyList(); renderMessages() }
        if (active && coherent && connected) chat.watch(channel, state)
    }
    private fun refreshControls() {
        if (!::table.isInitialized) return
        val state = snapshot
        val ready = active && coherent && connected && !leaving && !rematching
        val pending = if (::sender.isInitialized) sender.pending else null
        val canRetry = ::sender.isInitialized && sender.canRetry
        val options = if (ready && state != null && pending == null && awaitingProjection == null)
            ServerGameActionPolicy.options(state, clock.nowMs()) else emptyList()
        val key = listOf(ready, state?.publicState?.phaseIndex, state?.publicState?.revision,
            options, pending, awaitingProjection, canRetry, rematching, chatSending, channel).toString()
        if (key != controlKey) {
            controlKey = key
            table.controls(options, ready, pending != null || awaitingProjection != null, canRetry,
                rematching, uid == creatorId, chatSending, channel)
        }
    }
    private fun clearConfirmedIntention() {
        val command = awaitingProjection ?: return
        val state = snapshot ?: return
        if (state.publicState.matchId != command.matchId || state.publicState.phaseIndex != command.phaseIndex ||
            command.action == "contrapunto" && command.targetUid in state.publicState.counterpointPlayers ||
            command.action == "desertor_rethink" && state.privateState.deserterUsed ||
            command.action == "cancelar_listo" && state.privateState.confirmed.none { it.action == "listo_votar" } ||
            state.privateState.confirmed.any { it.action == command.action && it.targetUid == command.targetUid && it.team == command.team }) {
            awaitingProjection = null
            if (BuildConfig.DEBUG) OnlineDebugLog.i("v3_projection_confirmed action=${command.action} idx=${command.phaseIndex}")
        }
    }
    private fun submitAction(option: ServerGameActionOption, target: String?, expectedPhase: Int): Boolean {
        val state = snapshot ?: return false
        if (!active || !coherent || !connected || sender.pending != null || awaitingProjection != null ||
            !ServerGameTablePresentation.canSubmit(state, option, target, expectedPhase, clock.nowMs())) return false
        sender.submit(ServerGameCommand.create(roomId, state, option.action, target, option.team))
        return true
    }
    private fun retry() {
        if (::sender.isInitialized && sender.canRetry) sender.retryPending()
        else if (::realtime.isInitialized) { coherent = false; realtime.stop(); realtime.start() }
        updateStatus(); refreshControls()
    }
    private fun chatVisibility(open: Boolean) {
        // The same single selected-channel query also fills the ambient feed.
        val state = snapshot ?: return
        if (::chat.isInitialized && active && coherent && connected) chat.watch(channel, state)
        refreshControls()
    }
    private fun chooseChannel(next: String) {
        val changed = channel != next
        val previous = channel
        channel = next
        if (changed) { messages = emptyList(); if (::table.isInitialized) table.messages(emptyList(), previous) }
        val state = snapshot ?: return
        if (::chat.isInitialized && next in ServerGameChat.channels(state) && active && coherent && connected) chat.watch(next, state)
        // Calling controls here would re-enter the shared controller while it selects a channel.
    }
    private fun sendChat(sendingChannel: String, text: String, complete: (Boolean) -> Unit) {
        val state = snapshot
        if (state == null || !active || !coherent || !connected || chatSending || sendingChannel != channel ||
            !ServerGameChat.canSend(sendingChannel, state)) { complete(false); return }
        chatSending = true; refreshControls()
        chat.send(text, state, clock.nowMs()).addOnCompleteListener { task ->
            chatSending = false
            if (!active) return@addOnCompleteListener
            complete(task.isSuccessful)
            if (!task.isSuccessful) showError("No se pudo enviar el mensaje", task.exception ?: IllegalStateException("Sin confirmación."))
            refreshControls()
        }
    }
    private fun sendReaction(emoteId: String, complete: (Boolean) -> Unit) {
        val s = snapshot
        if (s == null || !active || !coherent || !connected || !s.permissions.reactions) { complete(false); return }
        reactions.send(emoteId, s, clock.nowMs()).addOnCompleteListener {
            if (it.isSuccessful) OnlineNetworkMetrics.count("v3_emote_enviado")
            complete(it.isSuccessful)
            if (!it.isSuccessful && active) showError("No se pudo enviar el emote", it.exception ?: IllegalStateException("Sin confirmación."))
        }
    }
    private fun renderMessages() { if (::table.isInitialized) table.messages(messages, channel) }
    private fun updateStatus() {
        if (!::table.isInitialized || leaving || navigating) return
        table.setStatus(when { !connected -> "Sin conexión. Intentando reconectar…"; !coherent -> "Sincronizando partida…"; else -> "" })
    }
    private fun updateClock() {
        if (::reactions.isInitialized) {
            val s = snapshot
            if (active && coherent && connected && s != null) reactions.watch(s, clock.nowMs()) else reactions.stop()
        }
        if (!::table.isInitialized) return
        val deadline = snapshot?.publicState?.deadlineMs
        val now = clock.nowMs()
        table.setCountdown(if (deadline != null && now != null) maxOf(0, (deadline - now + 999) / 1000) else null,
            deadline != null && now != null && now >= deadline)
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
            "Salir te elimina por abandono y cuenta como derrota, incluso si ya estabas eliminado. Si solo perdés conexión, podés volver."
            else "¿Querés salir de esta sala? El resultado de la partida queda guardado."
        GameDialog.confirm(this, "SALIR DE LA PARTIDA", message, "SALIR") {
            leaving = true; table.setStatus("Confirmando salida…"); refreshControls()
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
    private fun showError(action: String, error: Exception) {
        if (!active || navigating || leaving) return
        table.setStatus(OnlineErrorMessages.forAction(action, error))
        OnlineDebugLog.e("server_v3_client_error room=" + roomId + " action=" + action, error)
        Toast.makeText(this, OnlineErrorMessages.forAction(action, error), Toast.LENGTH_LONG).show()
        refreshControls()
    }
    companion object {
        const val EXTRA_MATCH_ID = "server_match_id"
        const val EXTRA_CREATOR_ID = "server_creator_id"
    }
}
