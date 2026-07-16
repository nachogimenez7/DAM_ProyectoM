package com.traidores.juego

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.TextViewCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.UUID

class LobbyActivity : BaseActivity() {

    private lateinit var session: GameSession
    private lateinit var btnAddPlayer: Button
    private lateinit var btnRemovePlayer: Button
    private lateinit var btnAdvancedOptions: Button
    private lateinit var lobbyMapBackground: ImageView
    private lateinit var playersContainer: LinearLayout
    private lateinit var playerCount: TextView
    private lateinit var startButton: Button
    private lateinit var mapCards: List<ImageView>
    private lateinit var selectedMapImage: ImageView
    private lateinit var selectedMapName: TextView
    private lateinit var selectedMapRole: TextView
    private lateinit var debugRoleButton: Button
    private lateinit var timingOptionsButton: Button
    private lateinit var lobbyTitle: TextView
    private lateinit var lobbyModeHint: TextView
    private lateinit var onlineCodePanel: LinearLayout
    private lateinit var onlineRoomCodeText: TextView
    private lateinit var btnCopyRoomCode: Button
    private lateinit var btnShareRoomCode: Button
    private lateinit var btnReleaseDisconnected: Button
    private lateinit var onlinePlayerTargetPanel: LinearLayout
    private lateinit var btnDecreaseExpectedPlayers: Button
    private lateinit var onlineExpectedPlayersText: TextView
    private lateinit var btnIncreaseExpectedPlayers: Button
    private lateinit var btnPlayWithPresent: Button
    private lateinit var mapDescription: TextView
    private lateinit var selectedMapCard: View
    private lateinit var onlineMapVoteHeader: View
    private lateinit var mapVoteCardsRow: LinearLayout
    private lateinit var mapVoteResultHint: TextView
    private lateinit var onlinePlayersScroll: HorizontalScrollView
    private lateinit var onlinePlayersContainer: LinearLayout
    private lateinit var playersListPanel: ScrollView
    private lateinit var lobbyPlayersLabel: TextView
    private lateinit var lobbyBodyScroll: ScrollView
    private lateinit var lobbyChatDock: LinearLayout
    private lateinit var lobbyChatActionText: TextView
    private lateinit var lobbyChatPreview: TextView
    private lateinit var btnToggleLobbyChat: ImageButton
    private lateinit var mapVoteViews: Map<String, MapVoteViews>
    private var lobbyMode = MODE_LOCAL
    private var onlineLobbyName = ""
    private var onlinePartidaId = ""
    private var onlineRoomCode = ""
    private var onlineRoomState = ONLINE_ROOM_STATE_WAITING
    private var onlineRoomModePrueba = false
    private var onlineRoomMaxPlayers = LocalGameFactory.MAX_PLAYERS
    private var onlineExpectedPlayers = LocalGameFactory.MIN_PLAYERS
    private var onlineHostId = ""
    private var onlineActiveHostId = ""
    private var onlineHostVersion = 0
    private var onlineInitialMatchCreated = false
    private var onlineCleanupPending = false
    private var onlineInitialMatch: Map<String, Any?>? = null
    private var onlineMatchState: Map<String, Any?>? = null
    private var onlineStartedNoticeShown = false
    private var onlineClientStates: Map<String, Any?> = emptyMap()
    private var onlineEntryReleasedMatchId = ""
    private var onlineRoomSnapshotHasPendingWrites = false
    private var onlineEntryBarrierMatchId = ""
    private var onlineEntryBarrierStartedAtMs = 0L
    private var onlineEntryAckMatchId = ""
    private var onlineEntryAckInProgress = false
    private var onlineEntryReleaseInProgress = false
    private var onlineEntryReleaseTimeoutScheduled = false
    private var recoveringOnlineMatch = false
    private var onlineRoomDeletedHandled = false
    private var onlineRemovalHandled = false
    private var leavingOnlineLobby = false
    private var enteringOnlineMatch = false
    private var onlineHostHandoffInProgress = false
    private var onlineHostHandoffCheckScheduled = false
    private var onlineRematchResetInProgress = false
    private var onlineCleanupInProgress = false
    private var onlineExpectedUpdateInProgress = false
    private var pendingExpectedPlayersForStart: Int? = null
    private var roomListener: ListenerRegistration? = null
    private var playersListener: ListenerRegistration? = null
    private var onlinePlayers = emptyList<OnlineLobbyPlayer>()
    private var onlineLobbyConfig = OnlineLobbyConfig()
    private var lobbyChatController: LobbyChatController? = null
    private var lobbyChatMessages = emptyList<LobbyChatMessage>()
    private val lobbySystemNotices = ArrayDeque<LobbyChatMessage>()
    private var lobbyChatKnownMessageIds: Set<String>? = null
    private var lastLobbyEmoteSoundAtMs = 0L
    private var lobbyChatExpandedMessages: LinearLayout? = null
    private var lobbyPlayersBaselineReady = false
    private var lastMapVoteLeaderKey: String? = null
    private var lastOnlineResultKey = ""
    private var lobbyRoomBaselineReady = false
    private var realtimePresence: RealtimeRoomPresence? = null
    private var realtimePresenceStates = emptyMap<String, RealtimePresenceState>()
    private var realtimePresenceBaselineReady = false
    private var onlineTempUid = ""
    private var onlinePlayerName = ""
    private var debugRoleIndex = 0

    private val onlineEntryReleaseTimeoutRunnable = Runnable {
        onlineEntryReleaseTimeoutScheduled = false
        maybeReleaseOnlineMatchEntry(force = true)
    }

    private val debugRoles = listOf(
        "" to "AZAR",
        "asesino" to "ASESINO",
        "mercenario" to "MERCENARIO",
        "policia" to "COMISARIO",
        "medico" to "MEDICO",
        "alcalde" to "ALCALDE",
        "payador" to "PAYADOR",
        "bufon" to "BUFON",
        "oraculo" to "ORACULO",
        "desertor" to "DESERTOR",
        "espia" to "ESPIA",
        "aldeano" to "ALDEANO"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)

        session = PlayerProfileStore.withProfiles(this, readSession() ?: LocalGameFactory.createSession())
        onlineLobbyConfig = OnlineLobbyConfig.fromSession(session)
        lobbyMode = intent.getStringExtra(EXTRA_LOBBY_MODE) ?: MODE_LOCAL
        onlineLobbyName = intent.getStringExtra(EXTRA_LOBBY_NAME).orEmpty()
        onlinePartidaId = intent.getStringExtra(EXTRA_PARTIDA_ID).orEmpty()
        onlineRoomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        recoveringOnlineMatch = intent.getBooleanExtra(EXTRA_RECOVERING_ONLINE, false)
        if (onlinePartidaId.isNotBlank()) {
            onlineTempUid = OnlineTempIdentity.getOrCreate(this)
            onlinePlayerName = OnlineRoomFirestore.normalizedPlayerName(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
                    .orEmpty()
            )
        }

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnLobbySettings: ImageButton = findViewById(R.id.btnLobbySettings)
        btnAddPlayer = findViewById(R.id.btnAddPlayer)
        btnRemovePlayer = findViewById(R.id.btnRemovePlayer)
        btnAdvancedOptions = findViewById(R.id.btnAdvancedOptions)
        val debugRoleSection: LinearLayout = findViewById(R.id.debugRoleSection)
        debugRoleButton = findViewById(R.id.btnDebugRole)
        timingOptionsButton = findViewById(R.id.btnTimingOptions)
        lobbyTitle = findViewById(R.id.lobbyTitle)
        lobbyModeHint = findViewById(R.id.lobbyModeHint)
        onlineCodePanel = findViewById(R.id.onlineCodePanel)
        onlineRoomCodeText = findViewById(R.id.onlineRoomCodeText)
        btnCopyRoomCode = findViewById(R.id.btnCopyRoomCode)
        btnShareRoomCode = findViewById(R.id.btnShareRoomCode)
        btnReleaseDisconnected = findViewById(R.id.btnReleaseDisconnected)
        onlinePlayerTargetPanel = findViewById(R.id.onlinePlayerTargetPanel)
        btnDecreaseExpectedPlayers = findViewById(R.id.btnDecreaseExpectedPlayers)
        onlineExpectedPlayersText = findViewById(R.id.onlineExpectedPlayersText)
        btnIncreaseExpectedPlayers = findViewById(R.id.btnIncreaseExpectedPlayers)
        btnPlayWithPresent = findViewById(R.id.btnPlayWithPresent)
        mapDescription = findViewById(R.id.mapDescription)
        selectedMapCard = findViewById(R.id.selectedMapCard)
        onlineMapVoteHeader = findViewById(R.id.onlineMapVoteHeader)
        mapVoteCardsRow = findViewById(R.id.mapVoteCardsRow)
        mapVoteResultHint = findViewById(R.id.mapVoteResultHint)
        onlinePlayersScroll = findViewById(R.id.onlinePlayersScroll)
        onlinePlayersContainer = findViewById(R.id.onlinePlayersContainer)
        playersListPanel = findViewById(R.id.playersListPanel)
        lobbyPlayersLabel = findViewById(R.id.lobbyPlayersLabel)
        lobbyBodyScroll = findViewById(R.id.lobbyBodyScroll)
        lobbyChatDock = findViewById(R.id.lobbyChatDock)
        lobbyChatActionText = findViewById(R.id.lobbyChatActionText)
        lobbyChatPreview = findViewById(R.id.lobbyChatPreview)
        btnToggleLobbyChat = findViewById(R.id.btnToggleLobbyChat)
        lobbyMapBackground = findViewById(R.id.lobbyMapBackground)
        selectedMapImage = findViewById(R.id.selectedMapImage)
        selectedMapName = findViewById(R.id.selectedMapName)
        selectedMapRole = findViewById(R.id.selectedMapRole)
        startButton = findViewById(R.id.btnStartGame)
        playersContainer = findViewById(R.id.playersContainer)
        playerCount = findViewById(R.id.playerCount)
        mapCards = listOf(
            findViewById(R.id.mapPampa),
            findViewById(R.id.mapGrecia),
            findViewById(R.id.mapMedieval)
        )
        mapVoteViews = mapOf(
            "pampa" to MapVoteViews(
                findViewById(R.id.mapPampaShade),
                findViewById(R.id.mapPampaVoteOverlay),
                findViewById(R.id.mapPampaVoteCount),
                findViewById(R.id.mapPampaVoteVoters)
            ),
            "grecia" to MapVoteViews(
                findViewById(R.id.mapGreciaShade),
                findViewById(R.id.mapGreciaVoteOverlay),
                findViewById(R.id.mapGreciaVoteCount),
                findViewById(R.id.mapGreciaVoteVoters)
            ),
            "medieval" to MapVoteViews(
                findViewById(R.id.mapMedievalShade),
                findViewById(R.id.mapMedievalVoteOverlay),
                findViewById(R.id.mapMedievalVoteCount),
                findViewById(R.id.mapMedievalVoteVoters)
            )
        )

        btnBack.setOnClickListener { requestLobbyExit() }
        btnLobbySettings.setOnClickListener { showLobbyOptionsDialog() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestLobbyExit()
            }
        })
        setupMapSelector()
        debugRoleSection.visibility = View.GONE
        debugRoleButton.setOnClickListener {
            debugRoleIndex = (debugRoleIndex + 1) % debugRoles.size
            renderDebugRole()
        }
        timingOptionsButton.setOnClickListener { showTestOptionsDialog() }
        btnAdvancedOptions.setOnClickListener { showAdvancedOptionsDialog() }
        btnCopyRoomCode.setOnClickListener { copyOnlineRoomCode() }
        btnShareRoomCode.setOnClickListener { shareOnlineRoomCode() }
        btnReleaseDisconnected.setOnClickListener { releaseDisconnectedOnlinePlayers() }
        btnDecreaseExpectedPlayers.setOnClickListener { updateOnlineExpectedPlayers(onlineExpectedPlayers - 1) }
        btnIncreaseExpectedPlayers.setOnClickListener { updateOnlineExpectedPlayers(onlineExpectedPlayers + 1) }
        btnPlayWithPresent.setOnClickListener { playOnlineWithPresentPlayers() }
        lobbyChatDock.setOnClickListener {
            if (isLobbyChatPreviewHidden()) {
                setLobbyChatPreviewHidden(false)
            } else {
                showLobbyChatSheet()
            }
        }
        btnToggleLobbyChat.setOnClickListener {
            setLobbyChatPreviewHidden(!isLobbyChatPreviewHidden())
        }

        updateOnlineControlState()

        btnAddPlayer.setOnClickListener {
            val updated = LocalGameFactory.addMockPlayer(session)
            if (updated.players.size == session.players.size) {
                Toast.makeText(this, "Maximo ${LocalGameFactory.MAX_PLAYERS} jugadores en esta demo.", Toast.LENGTH_SHORT).show()
            }
            session = PlayerProfileStore.withProfiles(this, updated)
            renderLobby()
        }

        btnRemovePlayer.setOnClickListener {
            val updated = LocalGameFactory.removeLastPlayer(session)
            if (updated.players.size == session.players.size) {
                val message = if (lobbyMode == MODE_ONLINE_CREATE) {
                    "La sala necesita conservar al anfitrion."
                } else {
                    "Minimo ${LocalGameFactory.MIN_PLAYERS} jugadores para iniciar."
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            session = PlayerProfileStore.withProfiles(this, updated)
            renderLobby()
        }

        startButton.setOnClickListener {
            if (isFirestoreOnlineLobby()) {
                handleOnlineStartButton()
                return@setOnClickListener
            }
            val selectedRoleKey = debugRoles[debugRoleIndex].first
            val minimumPlayers = LocalGameFactory.minimumPlayersForRole(selectedRoleKey)
            val selectedRoleMap = RoleMap.fromSessionKey(session.mapKey)
            if (
                selectedRoleKey.isNotBlank() &&
                !RoleCatalog.isAvailableOnMap(selectedRoleKey, selectedRoleMap)
            ) {
                Toast.makeText(
                    this,
                    "${debugRoles[debugRoleIndex].second} no esta disponible en este mapa.",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (session.players.size < minimumPlayers) {
                Toast.makeText(
                    this,
                    "Ese rol necesita al menos $minimumPlayers jugadores.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val assigned = LocalGameFactory.assignRoles(session, selectedRoleKey)
                val message = if (lobbyMode == MODE_ONLINE_CREATE) {
                    "Iniciando simulacion de partida online."
                } else {
                    "Iniciando partida local."
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, AssigningRolesActivity::class.java)
                        .putExtra(EXTRA_SESSION, assigned)
                )
            }
        }

        renderLobby()
    }

    override fun onStart() {
        super.onStart()
        if (isFirestoreOnlineLobby()) {
            enteringOnlineMatch = false
            startRealtimePresence()
            markOnlinePresence(PLAYER_STATE_CONNECTED)
            listenToOnlineRoom()
            listenToOnlinePlayers()
            startLobbyChat()
        }
    }

    override fun onStop() {
        if (isFirestoreOnlineLobby()) {
            val shouldStopRealtimeAsDisconnected = !enteringOnlineMatch &&
                (leavingOnlineLobby || onlineRemovalHandled || isFinishing)
            val shouldWriteLegacyDisconnected = shouldStopRealtimeAsDisconnected && !onlineRemovalHandled
            realtimePresence?.stop(markDisconnected = shouldStopRealtimeAsDisconnected)
            realtimePresence = null
            if (shouldWriteLegacyDisconnected) {
                markOnlinePresence(PLAYER_STATE_DISCONNECTED)
            }
        }
        roomListener?.remove()
        playersListener?.remove()
        lobbyChatController?.stop()
        if (::startButton.isInitialized) {
            startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
        }
        onlineEntryReleaseTimeoutScheduled = false
        roomListener = null
        playersListener = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }

    private fun renderLobby() {
        updateOnlineControlState()
        val onlineLobby = isFirestoreOnlineLobby()
        playerCount.text = if (onlineLobby) {
            val visible = currentVisiblePlayerCount()
            val missingPlayers = (onlineExpectedPlayers - visible).coerceAtLeast(0)
            val missingReady = activeOnlinePlayers().count {
                !isOnlinePlayerConnected(it) || !it.ready
            }
            buildString {
                append("$visible/$onlineExpectedPlayers jugadores")
                when {
                    missingPlayers > 0 -> append(" - faltan $missingPlayers")
                    missingReady > 0 -> append(" - faltan $missingReady listos")
                    else -> append(" - todos listos")
                }
            }
        } else {
            "${currentVisiblePlayerCount()}/${currentMaxPlayers()} jugadores"
        }
        lobbyTitle.text = when (lobbyMode) {
            MODE_ONLINE_CREATE -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "Lobby online - $it" }
                ?: "Lobby online - Tu sala"
            MODE_ONLINE_SEARCH -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "Lobby online - $it" }
                ?: "Lobby online - Sala encontrada"
            else -> "Modo local"
        }
        lobbyModeHint.text = when (lobbyMode) {
            MODE_ONLINE_CREATE, MODE_ONLINE_SEARCH -> onlineLobbyHint()
            else -> if (session.botDifficulty == BotDifficulty.HARD) {
                "Modo dificil: la IA traidora coordina mejor sus votos."
            } else {
                "Modo normal: elegi mapa, tiempos y participantes antes de iniciar."
            }
        }
        renderOnlineCodePanel()
        renderReleaseDisconnectedButton()
        renderOnlinePlayerTargetControls()
        renderLobbyStructure(onlineLobby)
        renderStartButtonState()
        val currentMap = currentMap()
        selectedMapName.text = currentMap.name.uppercase()
        selectedMapRole.text = selectedMapRoleLabel(session.mapKey)
        selectedMapImage.setImageResource(currentMap.imageRes)
        lobbyMapBackground.setImageResource(currentMap.imageRes)
        mapCards.forEachIndexed { index, imageView ->
            val selected = LocalGameFactory.maps[index].key == session.mapKey
            imageView.alpha = if (onlineLobby || selected) 1f else 0.55f
            (imageView.parent as? FrameLayout)?.setBackgroundResource(
                if (!onlineLobby && selected) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark
            )
            val map = LocalGameFactory.maps[index]
            imageView.contentDescription = if (onlineLobby) {
                "Votar por ${map.name}"
            } else if (selected) {
                "${map.name}, mapa seleccionado"
            } else {
                "Elegir ${map.name}"
            }
        }
        renderOnlineMapVoting()
        playersContainer.removeAllViews()
        onlinePlayersContainer.removeAllViews()
        renderDebugRole()
        timingOptionsButton.text = "Opciones de testeo"
        btnAdvancedOptions.text = "Opciones avanzadas"

        val visibleOnlinePlayers = activeOnlinePlayers()
        val onlineChipWidth = if (session.players.isEmpty()) {
            dp(64)
        } else {
            val availableWidth = resources.displayMetrics.widthPixels - dp(64)
            val totalGaps = dp(5) * (session.players.size - 1).coerceAtLeast(0)
            ((availableWidth - totalGaps) / session.players.size).coerceIn(dp(62), dp(70))
        }
        onlinePlayersScroll.isHorizontalScrollBarEnabled = session.players.size > 6
        onlinePlayersScroll.isScrollbarFadingEnabled = session.players.size <= 6
        session.players.forEachIndexed { index, player ->
            if (onlineLobby) {
                onlinePlayersContainer.addView(
                    createOnlinePlayerChip(player, visibleOnlinePlayers.getOrNull(index)),
                    LinearLayout.LayoutParams(onlineChipWidth, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        if (index > 0) marginStart = dp(5)
                    }
                )
                return@forEachIndexed
            }
            val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
            val onlinePlayer = visibleOnlinePlayers.getOrNull(index)
            row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
            row.findViewById<TextView>(R.id.playerName).text = player.name
            row.findViewById<TextView>(R.id.playerStatus).text =
                onlinePlayer?.statusLabel(onlineHostId.ifBlank { onlineActiveHostId })
                    ?: if (index == 0) "Anfitrion" else "Listo"
            row.findViewById<ImageButton>(R.id.btnPlayerProfile).setOnClickListener {
                showPlayerProfile(player)
            }
            row.findViewById<ImageButton>(R.id.btnPlayerProfile).contentDescription =
                "Ver perfil de ${player.name}"
            row.findViewById<ImageButton>(R.id.btnKickPlayer).apply {
                val canRemoveOnlinePlayer = isFirestoreOnlineLobby() &&
                    onlinePlayer != null &&
                    canCurrentHostRemoveOnlinePlayer(onlinePlayer)
                isEnabled = if (isFirestoreOnlineLobby()) {
                    canRemoveOnlinePlayer
                } else {
                    index != 0 && !isOnlineGuest()
                }
                alpha = if (isEnabled) 1f else 0.28f
                contentDescription = when {
                    isFirestoreOnlineLobby() && onlinePlayer?.id == onlineTempUid ->
                        "No puedes expulsarte de la sala"
                    isFirestoreOnlineLobby() && !currentUserIsOnlineHost() ->
                        "Solo el anfitrion puede expulsar jugadores online"
                    isFirestoreOnlineLobby() && onlinePlayer != null ->
                        "Expulsar a ${onlinePlayer.name} de la sala online"
                    isOnlineGuest() -> "Solo el anfitrion puede expulsar jugadores"
                    index == 0 -> "El anfitrion no se puede expulsar"
                    else -> "Expulsar a ${player.name}"
                }
                setOnClickListener { confirmPlayerRemoval(index, player, onlinePlayer) }
            }
            playersContainer.addView(row)
        }
        renderLobbyChatDock()
    }

    private fun renderLobbyStructure(onlineLobby: Boolean) {
        selectedMapCard.visibility = if (onlineLobby) View.GONE else View.VISIBLE
        onlineMapVoteHeader.visibility = if (onlineLobby) View.VISIBLE else View.GONE
        mapVoteResultHint.visibility = if (onlineLobby) View.VISIBLE else View.GONE
        mapDescription.visibility = if (onlineLobby) View.GONE else View.VISIBLE
        mapDescription.text = mapDescriptionFor(session.mapKey)
        onlinePlayersScroll.visibility = if (onlineLobby) View.VISIBLE else View.GONE
        playersListPanel.visibility = if (onlineLobby) View.GONE else View.VISIBLE
        lobbyPlayersLabel.visibility = if (onlineLobby) View.GONE else View.VISIBLE
        mapVoteCardsRow.layoutParams = mapVoteCardsRow.layoutParams.apply {
            height = dp(if (onlineLobby) 112 else 54)
        }
        val bodyParams = lobbyBodyScroll.layoutParams as RelativeLayout.LayoutParams
        if (onlineLobby) {
            bodyParams.addRule(RelativeLayout.ABOVE, R.id.lobbyChatDock)
        } else {
            bodyParams.removeRule(RelativeLayout.ABOVE)
        }
        lobbyBodyScroll.layoutParams = bodyParams
    }

    private fun renderOnlineMapVoting() {
        if (!isFirestoreOnlineLobby()) {
            mapVoteViews.values.forEach { views ->
                views.shade.visibility = View.GONE
                views.overlay.visibility = View.GONE
            }
            return
        }
        val summary = OnlineMapVoteResolver.summarize(
            activeOnlinePlayers().map { player ->
                OnlineMapVote(player.id, player.initial, player.mapVote)
            }
        )
        val currentVote = currentOnlinePlayer()?.mapVote
        LocalGameFactory.maps.forEachIndexed { index, map ->
            val views = mapVoteViews.getValue(map.key)
            val count = summary.counts[map.key] ?: 0
            val initials = summary.voterInitials[map.key].orEmpty()
            val selectedByCurrentPlayer = currentVote == map.key
            val leading = map.key in summary.leaders
            views.shade.visibility = View.VISIBLE
            views.overlay.visibility = View.VISIBLE
            views.count.text = "$count ${if (count == 1) "voto" else "votos"}"
            views.voters.text = compactVoterInitials(initials)
            (mapCards[index].parent as? FrameLayout)?.setBackgroundResource(
                if (selectedByCurrentPlayer || leading) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark
            )
        }
        mapVoteResultHint.text = when {
            summary.totalVotes == 0 ->
                "Sin votos: al iniciar se mantiene ${currentMap().name}."
            summary.uniqueLeader != null ->
                "${mapName(summary.uniqueLeader)} va ganando - " +
                    "${selectedMapRoleLabel(summary.uniqueLeader)}. Se fija al iniciar."
            else ->
                "Empate entre ${summary.leaders.joinToString(" y ") { mapName(it) }}. Decide el anfitrion al iniciar."
        }
        val leaderKey = when {
            summary.totalVotes == 0 -> "none"
            summary.uniqueLeader != null -> summary.uniqueLeader
            else -> "tie:${summary.leaders.joinToString(",")}"
        }
        if (lastMapVoteLeaderKey != null && lastMapVoteLeaderKey != leaderKey) {
            addLobbySystemNotice(
                if (summary.uniqueLeader != null) {
                    "${mapName(summary.uniqueLeader)} paso a liderar la votacion de mapa."
                } else {
                    "La votacion de mapa quedo empatada."
                }
            )
        }
        lastMapVoteLeaderKey = leaderKey
    }

    private fun compactVoterInitials(initials: List<String>): String {
        if (initials.isEmpty()) return "-"
        val visible = initials.take(3).joinToString("  ")
        val remaining = initials.size - 3
        return if (remaining > 0) "$visible  +$remaining" else visible
    }

    private fun mapName(mapKey: String): String {
        return LocalGameFactory.maps.firstOrNull { it.key == mapKey }?.name ?: mapKey
    }

    private fun createOnlinePlayerChip(player: GamePlayer, onlinePlayer: OnlineLobbyPlayer?): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_dark)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
            contentDescription = "Ver perfil de ${player.name}"
            addView(TextView(this@LobbyActivity).apply {
                text = buildString {
                    append(player.initial)
                    if (onlinePlayer?.ready == true) append("  ✓")
                }
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent_gold))
                textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_btn_dark_ripple)
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(this@LobbyActivity).apply {
                text = if (onlinePlayer?.id == onlineTempUid) "Vos" else player.name
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.text_secondary))
                textSize = 10f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))
            setOnClickListener { showPlayerProfile(player) }
            if (onlinePlayer != null && canCurrentHostRemoveOnlinePlayer(onlinePlayer)) {
                setOnLongClickListener {
                    confirmPlayerRemoval(session.players.indexOf(player), player, onlinePlayer)
                    true
                }
            }
        }
    }

    private fun startLobbyChat() {
        if (!isFirestoreOnlineLobby() || onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        if (lobbyChatController == null) {
            lobbyChatController = LobbyChatController(
                database = FirebaseDatabase.getInstance(),
                roomId = onlinePartidaId,
                actorId = onlineTempUid,
                speaker = onlinePlayerName,
                onMessagesChanged = { messages ->
                    playLobbyEmoteSoundForNewMessages(messages)
                    lobbyChatMessages = messages
                    renderLobbyChatDock()
                    lobbyChatExpandedMessages?.let(::renderLobbyChatMessages)
                },
                onError = { error ->
                    OnlineDebugLog.e("lobby_chat_failure roomId=$onlinePartidaId uid=$onlineTempUid", error)
                }
            )
        }
        lobbyChatController?.start()
    }

    private fun renderLobbyChatDock() {
        if (!::lobbyChatDock.isInitialized) return
        val visible = isFirestoreOnlineLobby()
        lobbyChatDock.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        val hidden = isLobbyChatPreviewHidden()
        lobbyChatPreview.visibility = if (hidden) View.GONE else View.VISIBLE
        lobbyChatActionText.text = if (hidden) "Mostrar chat" else "Toca para hablar"
        btnToggleLobbyChat.contentDescription = if (hidden) {
            "Mostrar vista previa del chat"
        } else {
            "Ocultar vista previa del chat"
        }
        btnToggleLobbyChat.setImageResource(
            if (hidden) R.drawable.ic_chat_speaking else R.drawable.ic_close
        )
        if (!hidden) {
            val recent = allLobbyChatMessages().takeLast(LOBBY_CHAT_PREVIEW_LINES)
            lobbyChatPreview.text = if (recent.isEmpty()) {
                "Todavia no hay mensajes."
            } else {
                recent.joinToString("\n") { message ->
                    when {
                        message.isSystem -> message.message
                        message.emoteId != null -> "${message.speaker} envio ${message.message}"
                        else -> "${message.speaker}: ${message.message}"
                    }
                }
            }
        }
    }

    private fun isLobbyChatPreviewHidden(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_LOBBY_CHAT_PREVIEW_HIDDEN, false)
    }

    private fun setLobbyChatPreviewHidden(hidden: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_LOBBY_CHAT_PREVIEW_HIDDEN, hidden)
            .apply()
        renderLobbyChatDock()
    }

    private fun addLobbySystemNotice(message: String) {
        if (message.isBlank()) return
        lobbySystemNotices.addLast(
            LobbyChatMessage(
                id = "system-${System.nanoTime()}",
                actorId = "system",
                speaker = "Sistema",
                message = message,
                emoteId = null,
                createdAtLocal = System.currentTimeMillis(),
                isSystem = true
            )
        )
        while (lobbySystemNotices.size > MAX_LOCAL_LOBBY_NOTICES) lobbySystemNotices.removeFirst()
        renderLobbyChatDock()
        lobbyChatExpandedMessages?.let(::renderLobbyChatMessages)
    }

    private fun allLobbyChatMessages(): List<LobbyChatMessage> {
        return (lobbyChatMessages + lobbySystemNotices)
            .sortedBy { it.createdAtLocal }
            .takeLast(LobbyChatController.MAX_MESSAGES)
    }

    // Suena solo para emotes NUEVOS (diff contra el snapshot anterior), nunca para el
    // historial que llega al abrir/reconectar. Ademas del throttle propio de
    // EmoteSoundEffects, se aplica un cooldown mas largo para que una racha de emotes
    // en el lobby no ametralle audio.
    private fun playLobbyEmoteSoundForNewMessages(messages: List<LobbyChatMessage>) {
        val currentIds = messages.mapTo(mutableSetOf()) { it.id }
        val previousIds = lobbyChatKnownMessageIds
        lobbyChatKnownMessageIds = currentIds
        if (previousIds == null) return
        val newEmote = messages.lastOrNull { it.id !in previousIds && it.emoteId != null } ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLobbyEmoteSoundAtMs < LOBBY_EMOTE_SOUND_COOLDOWN_MS) return
        val emotionKey = EmoteCatalog.byId(newEmote.emoteId.orEmpty())?.emotionKey ?: return
        lastLobbyEmoteSoundAtMs = now
        EmoteSoundEffects.play(this, emotionKey)
    }

    private fun showLobbyChatSheet() {
        if (!isFirestoreOnlineLobby()) return
        val dialog = BottomSheetDialog(this)
        val content = dialogColumn().apply {
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        content.addView(dialogTitle("QUÉ SE DICE EN LA SALA"))
        content.addView(TextView(this).apply {
            text = "Se conservan los ultimos 30 mensajes de esta sala."
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(0, 0, 0, dp(7))
        })
        val messageScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundResource(R.drawable.bg_btn_dark)
        }
        val messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }
        lobbyChatExpandedMessages = messageContainer
        messageScroll.addView(messageContainer)
        val messageAreaHeight = (resources.displayMetrics.heightPixels * 0.36f)
            .toInt()
            .coerceIn(dp(220), dp(320))
        content.addView(
            messageScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, messageAreaHeight)
        )

        val input = EditText(this).apply {
            hint = "Escribí un mensaje..."
            setHintTextColor(getColor(R.color.text_muted))
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            maxLines = 3
            setBackgroundResource(R.drawable.bg_btn_dark)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val sendButton = compactDialogButton("Enviar").apply {
            setOnClickListener {
                val message = input.text?.toString().orEmpty()
                if (message.isBlank()) return@setOnClickListener
                lobbyChatController?.sendText(message) { input.setText("") }
            }
        }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, dp(7))
            addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(sendButton, LinearLayout.LayoutParams(dp(88), dp(52)).apply {
                marginStart = dp(7)
            })
        }
        content.addView(inputRow)

        content.addView(TextView(this).apply {
            text = "EMOTES DEL PERFIL"
            setTextColor(getColor(R.color.accent_gold))
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        val emoteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        EmoteLoadout.selectedSpecs(this).forEachIndexed { index, emote ->
            emoteRow.addView(ImageButton(this).apply {
                setImageResource(emote.imageRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundResource(R.drawable.bg_btn_dark_ripple)
                contentDescription = "Enviar emote ${emote.label}"
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener { lobbyChatController?.sendEmote(emote) }
            }, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                if (index > 0) marginStart = dp(9)
            })
        }
        content.addView(emoteRow)
        dialog.setContentView(content)
        dialog.setOnDismissListener { lobbyChatExpandedMessages = null }
        dialog.show()
        renderLobbyChatMessages(messageContainer)
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderLobbyChatMessages(container: LinearLayout) {
        container.removeAllViews()
        allLobbyChatMessages().forEach { message ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }
            val emote = message.emoteId?.let(EmoteCatalog::byId)
            if (emote != null) {
                row.addView(ImageView(this).apply {
                    setImageResource(emote.imageRes)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = emote.label
                }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
            }
            row.addView(TextView(this).apply {
                text = when {
                    message.isSystem -> message.message
                    emote != null -> "${message.speaker} envio ${emote.label}"
                    else -> "${message.speaker}: ${message.message}"
                }
                setTextColor(getColor(if (message.isSystem) R.color.accent_gold else R.color.text_primary))
                textSize = 12f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(row)
        }
        if (container.childCount == 0) {
            container.addView(TextView(this).apply {
                text = "Todavia no hay mensajes."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, dp(24), 0, dp(24))
            })
        }
    }

    private fun listenToOnlineRoom() {
        roomListener?.remove()
        OnlineDebugLog.i("lobby_room_listen_start roomId=$onlinePartidaId uid=$onlineTempUid")
        roomListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("lobby_room_listen_failure roomId=$onlinePartidaId", error)
                    Toast.makeText(
                        this,
                        OnlineErrorMessages.forAction("Error cargando sala online", error),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    OnlineDebugLog.w("lobby_room_missing roomId=$onlinePartidaId")
                    handleDeletedOnlineRoom()
                    return@addSnapshotListener
                }
                applyOnlineRoomSnapshot(snapshot)
            }
    }

    private fun startRealtimePresence() {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank() || realtimePresence != null) return
        realtimePresence = RealtimeRoomPresence(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            uid = onlineTempUid,
            onPresenceChanged = { states ->
                realtimePresenceStates = states
                realtimePresenceBaselineReady = true
                if (::startButton.isInitialized) {
                    maybeClaimOnlineLobbyHostHandoff()
                    renderLobby()
                }
            },
            onError = { error ->
                OnlineDebugLog.e(
                    "rtdb_lobby_presence_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                    error
                )
            }
        ).also { it.start() }
    }

    private fun markOnlinePresence(state: String) {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        realtimePresence?.setConnected(state == PLAYER_STATE_CONNECTED)
        OnlineDebugLog.i("presence_update roomId=$onlinePartidaId uid=$onlineTempUid state=$state")
        val firestore = FirebaseFirestore.getInstance()
        val currentlyReleased = onlinePlayers.firstOrNull { it.id == onlineTempUid }?.activeInMatch == false
        val publicId = PlayerPublicIdentity.currentPublicId(this)
        val playerData = hashMapOf<String, Any>(
            FIELD_NAME to onlinePlayerName,
            FIELD_PLAYER_STATE to state,
            "uidTemporal" to onlineTempUid,
            OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to !currentlyReleased,
            OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
            OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
        )
        playerData.putAll(PlayerPublicIdentity.publicProfileFields(this, publicId, onlinePlayerName))
        if (currentlyReleased) {
            playerData[FIELD_PLAYER_READY] = false
        }
        firestore
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(playerData, SetOptions.merge())
            .addOnFailureListener { error ->
                OnlineDebugLog.e("presence_update_failure roomId=$onlinePartidaId uid=$onlineTempUid state=$state", error)
            }

        if (
            state == PLAYER_STATE_DISCONNECTED &&
            currentUserIsOnlineHost() &&
            (leavingOnlineLobby || isFinishing)
        ) {
            // El ultimo anfitrion se va y no queda nadie mas conectado (si hubiera otro
            // jugador conectado, este dispositivo ya habria transferido el host antes de
            // salir y currentUserIsOnlineHost() daria false aca). La sala queda vacia:
            // se borra por completo en vez de dejarla como "abandonada" para siempre.
            teardownEmptyOnlineRoom()
        }
    }

    private fun teardownEmptyOnlineRoom() {
        val roomId = onlinePartidaId
        OnlineDebugLog.i("room_teardown_requested roomId=$roomId hostId=$onlineTempUid")
        FirebaseDatabase.getInstance()
            .getReference("salas/$roomId")
            .removeValue()
            .addOnFailureListener { error ->
                OnlineDebugLog.e("rtdb_room_teardown_failure roomId=$roomId", error)
            }
        cleanupOnlineMatchCollections(
            collectionNames = listOf(ONLINE_PLAYERS_COLLECTION, "acciones"),
            index = 0,
            onComplete = {
                FirebaseFirestore.getInstance()
                    .collection(ONLINE_ROOMS_COLLECTION)
                    .document(roomId)
                    .delete()
                    .addOnSuccessListener {
                        OnlineDebugLog.i("room_teardown_success roomId=$roomId hostId=$onlineTempUid")
                    }
                    .addOnFailureListener { error ->
                        OnlineDebugLog.e("room_teardown_room_delete_failure roomId=$roomId hostId=$onlineTempUid", error)
                    }
            },
            onFailure = { error ->
                OnlineDebugLog.e("room_teardown_subcollection_failure roomId=$roomId hostId=$onlineTempUid", error)
            }
        )
    }

    private fun renderOnlineCodePanel() {
        val showCode = isFirestoreOnlineLobby() && onlineRoomCode.isNotBlank()
        onlineCodePanel.visibility = if (showCode) View.VISIBLE else View.GONE
        if (!showCode) return
        onlineRoomCodeText.text = onlineRoomCode
        btnCopyRoomCode.contentDescription = "Copiar codigo de sala $onlineRoomCode"
        btnShareRoomCode.contentDescription = "Compartir codigo de sala $onlineRoomCode"
    }

    private fun renderReleaseDisconnectedButton() {
        val releasableCount = releasableDisconnectedOnlinePlayers().size
        val visible = isFirestoreOnlineLobby() &&
            currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            releasableCount > 0
        btnReleaseDisconnected.visibility = if (visible) View.VISIBLE else View.GONE
        btnReleaseDisconnected.isEnabled = visible
        btnReleaseDisconnected.text = if (releasableCount > 0) {
            "LIBERAR $releasableCount DESCONECTADO${if (releasableCount == 1) "" else "S"}"
        } else {
            "LIBERAR DESCONECTADOS"
        }
        btnReleaseDisconnected.contentDescription =
            "Liberar cupos de jugadores desconectados"
    }

    private fun renderOnlinePlayerTargetControls() {
        val visible = isFirestoreOnlineLobby() &&
            currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineInitialMatchCreated
        onlinePlayerTargetPanel.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        val minimum = minimumOnlinePlayerLimit().coerceAtLeast(activeOnlinePlayers().size)
        val enabled = !onlineCleanupPending && !onlineExpectedUpdateInProgress
        onlineExpectedPlayersText.text = "$onlineExpectedPlayers JUGADORES"
        btnDecreaseExpectedPlayers.isEnabled = enabled && onlineExpectedPlayers > minimum
        btnIncreaseExpectedPlayers.isEnabled = enabled && onlineExpectedPlayers < LocalGameFactory.MAX_PLAYERS
        btnPlayWithPresent.isEnabled = enabled && activeOnlinePlayers().size >= minimumOnlinePlayerLimit()
        btnPlayWithPresent.visibility = View.GONE
        listOf(btnDecreaseExpectedPlayers, btnIncreaseExpectedPlayers, btnPlayWithPresent).forEach { button ->
            button.alpha = if (button.isEnabled) 1f else 0.45f
        }
    }

    private fun minimumOnlinePlayerLimit(): Int {
        return if (onlineRoomModePrueba) {
            LocalGameFactory.TEST_MIN_PLAYERS
        } else {
            LocalGameFactory.MIN_PLAYERS
        }
    }

    private fun updateOnlineExpectedPlayers(target: Int, startAfterSnapshot: Boolean = false) {
        if (!currentUserIsOnlineHost() || onlineExpectedUpdateInProgress) return
        val minimum = minimumOnlinePlayerLimit().coerceAtLeast(activeOnlinePlayers().size)
        val normalizedTarget = target.coerceIn(minimum, LocalGameFactory.MAX_PLAYERS)
        if (normalizedTarget == onlineExpectedPlayers) {
            if (startAfterSnapshot) startOnlineRoomForEveryone()
            return
        }
        if (onlineCleanupPending) {
            Toast.makeText(this, "Terminando de limpiar la partida anterior...", Toast.LENGTH_SHORT).show()
            return
        }
        onlineExpectedUpdateInProgress = true
        if (startAfterSnapshot) pendingExpectedPlayersForStart = normalizedTarget
        renderOnlinePlayerTargetControls()
        val roomReference = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
        roomReference.update(
            mapOf(
                FIELD_EXPECTED_PLAYERS to normalizedTarget,
                FIELD_MAX_PLAYERS to normalizedTarget,
                OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            onlineExpectedUpdateInProgress = false
            OnlineDebugLog.i(
                "lobby_expected_players_updated roomId=$onlinePartidaId host=$onlineTempUid expected=$normalizedTarget"
            )
            renderOnlinePlayerTargetControls()
            maybeStartAfterExpectedPlayersUpdate()
        }.addOnFailureListener { error ->
            onlineExpectedUpdateInProgress = false
            pendingExpectedPlayersForStart = null
            renderOnlinePlayerTargetControls()
            OnlineDebugLog.e("lobby_expected_players_update_failure roomId=$onlinePartidaId", error)
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo cambiar la cantidad", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun playOnlineWithPresentPlayers() {
        val players = activeOnlinePlayers()
        val minimum = minimumOnlinePlayerLimit()
        if (players.size < minimum) {
            Toast.makeText(this, "Faltan jugadores para el minimo de $minimum.", Toast.LENGTH_SHORT).show()
            return
        }
        val unavailable = players.count { !isOnlinePlayerConnected(it) || !it.ready }
        if (unavailable > 0) {
            Toast.makeText(this, "Todavia faltan $unavailable jugador(es) listos.", Toast.LENGTH_SHORT).show()
            return
        }
        updateOnlineExpectedPlayers(players.size, startAfterSnapshot = true)
    }

    private fun releaseDisconnectedOnlinePlayers() {
        if (!isFirestoreOnlineLobby() || !currentUserIsOnlineHost()) return
        if (onlineRoomState != ONLINE_ROOM_STATE_WAITING) {
            Toast.makeText(this, "La sala ya no esta esperando jugadores.", Toast.LENGTH_SHORT).show()
            return
        }
        val playersToRelease = releasableDisconnectedOnlinePlayers()
        if (playersToRelease.isEmpty()) {
            Toast.makeText(this, "No hay desconectados para liberar.", Toast.LENGTH_SHORT).show()
            return
        }
        btnReleaseDisconnected.isEnabled = false
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        OnlineDebugLog.i(
            "release_disconnected_requested roomId=$onlinePartidaId hostId=$onlineTempUid count=${playersToRelease.size}"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta esperando jugadores.")
            }
            if (room.getString(FIELD_ACTIVE_HOST_ID) != onlineTempUid && room.getString(FIELD_HOST_ID) != onlineTempUid) {
                throw IllegalStateException("Solo el anfitrion puede liberar cupos.")
            }

            val releasableReferences = playersToRelease.map { player ->
                player.id to roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id)
            }
            val releasableSnapshots = releasableReferences.map { (playerId, reference) ->
                Triple(playerId, reference, transaction.get(reference))
            }
            val snapshotsToRelease = releasableSnapshots.filter { (_, _, playerSnapshot) ->
                val stillActive = playerSnapshot.getBoolean(FIELD_ACTIVE_IN_MATCH) != false
                val stillDisconnected = playerSnapshot.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_DISCONNECTED
                playerSnapshot.exists() && stillActive && stillDisconnected
            }
            snapshotsToRelease.forEach { (_, playerReference, _) ->
                transaction.update(
                    playerReference,
                    mapOf(
                        FIELD_ACTIVE_IN_MATCH to false,
                        FIELD_PLAYER_READY to false,
                        OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                        OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                    )
                )
            }
            val releasedCount = snapshotsToRelease.size
            val currentPlayers = (room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: activeOnlinePlayers().size.toLong())
                .toInt()
            val newActiveCount = (currentPlayers - releasedCount).coerceAtLeast(0)
            transaction.update(
                roomReference,
                mapOf(
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to newActiveCount,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            releasedCount
        }.addOnSuccessListener { releasedCount ->
            OnlineDebugLog.i(
                "release_disconnected_success roomId=$onlinePartidaId hostId=$onlineTempUid count=$releasedCount"
            )
            Toast.makeText(
                this,
                "Se liberaron $releasedCount cupos desconectados.",
                Toast.LENGTH_SHORT
            ).show()
            btnReleaseDisconnected.isEnabled = true
            renderLobby()
        }.addOnFailureListener { error ->
            OnlineDebugLog.e("release_disconnected_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
            btnReleaseDisconnected.isEnabled = true
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudieron liberar cupos", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun canCurrentHostRemoveOnlinePlayer(player: OnlineLobbyPlayer): Boolean {
        return isFirestoreOnlineLobby() &&
            currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            player.activeInMatch &&
            player.id != onlineTempUid
    }

    private fun removeOnlinePlayer(player: OnlineLobbyPlayer) {
        if (!canCurrentHostRemoveOnlinePlayer(player)) {
            Toast.makeText(this, "No se puede expulsar a ese jugador ahora.", Toast.LENGTH_SHORT).show()
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id)
        OnlineDebugLog.w(
            "online_kick_requested roomId=$onlinePartidaId hostId=$onlineTempUid target=${player.id}"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta esperando jugadores.")
            }
            val activeHostId = room.getString(FIELD_ACTIVE_HOST_ID).orEmpty()
            val hostId = room.getString(FIELD_HOST_ID).orEmpty()
            if (activeHostId != onlineTempUid && hostId != onlineTempUid) {
                throw IllegalStateException("Solo el anfitrion puede expulsar jugadores.")
            }
            if (activeHostId == player.id) {
                throw IllegalStateException("No se puede expulsar al anfitrion activo.")
            }
            val target = transaction.get(playerReference)
            if (!target.exists()) {
                throw IllegalStateException("El jugador ya no esta en la sala.")
            }
            if (target.getString("uidTemporal") != player.id) {
                throw IllegalStateException("El jugador no coincide con la sala.")
            }
            val targetStillActive = target.getBoolean(FIELD_ACTIVE_IN_MATCH) != false
            if (!targetStillActive) return@runTransaction false

            transaction.update(
                playerReference,
                mapOf(
                    FIELD_ACTIVE_IN_MATCH to false,
                    FIELD_PLAYER_READY to false,
                    FIELD_PLAYER_STATE to PLAYER_STATE_DISCONNECTED,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                )
            )
            val currentPlayers = (room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS)
                ?: activeOnlinePlayers().size.toLong()).toInt()
            transaction.update(
                roomReference,
                mapOf(
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to (currentPlayers - 1).coerceAtLeast(0),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener { removed ->
            OnlineDebugLog.w(
                "online_kick_success roomId=$onlinePartidaId hostId=$onlineTempUid target=${player.id} removed=$removed"
            )
            Toast.makeText(this, "${player.name} fue expulsado de la sala.", Toast.LENGTH_SHORT).show()
            renderLobby()
        }.addOnFailureListener { error ->
            OnlineDebugLog.e(
                "online_kick_failure roomId=$onlinePartidaId hostId=$onlineTempUid target=${player.id}",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo expulsar al jugador", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun copyOnlineRoomCode() {
        if (onlineRoomCode.isBlank()) {
            Toast.makeText(this, "Todavia no hay codigo de sala.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Codigo de sala Traidores", onlineRoomCode))
        Toast.makeText(this, "Codigo copiado: $onlineRoomCode", Toast.LENGTH_SHORT).show()
    }

    private fun shareOnlineRoomCode() {
        if (onlineRoomCode.isBlank()) {
            Toast.makeText(this, "Todavia no hay codigo de sala.", Toast.LENGTH_SHORT).show()
            return
        }
        val shareText = "Unite a mi sala de Traidores con el codigo: $onlineRoomCode"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, shareText),
                "Compartir codigo de sala"
            )
        )
    }

    private fun requestLobbyExit() {
        if (!isFirestoreOnlineLobby()) {
            finish()
            return
        }
        val isHost = currentUserIsOnlineHost()
        val handoffCandidate = if (isHost) onlineLobbyHostHandoffCandidate(excludeCurrent = true) else null
        val title = if (isHost) "Salir de la sala online" else "Salir del lobby"
        val message = if (isHost && handoffCandidate != null) {
            "Si salis, ${handoffCandidate.name} quedara como anfitrion activo de la sala."
        } else if (isHost) {
            "Si salis, la sala quedara abandonada y ya no aparecera en buscar partida."
        } else {
            "Vas a salir de la sala. El resto de jugadores seguira en el lobby."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("SALIR") { _, _ ->
                if (isHost && handoffCandidate != null) {
                    transferLobbyHostAndExit(handoffCandidate)
                } else {
                    leavingOnlineLobby = true
                    finish()
                }
            }
            .show()
    }

    private fun listenToOnlinePlayers() {
        playersListener?.remove()
        OnlineDebugLog.i("lobby_players_listen_start roomId=$onlinePartidaId")
        playersListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("lobby_players_listen_failure roomId=$onlinePartidaId", error)
                    Toast.makeText(
                        this,
                        OnlineErrorMessages.forAction("Error cargando jugadores", error),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                val updatedPlayers = snapshot?.documents
                    ?.mapNotNull(::parseOnlinePlayer)
                    ?.sortedWith(
                        compareBy<OnlineLobbyPlayer> { it.order }
                            .thenBy { it.name.lowercase() }
                            .thenBy { it.id }
                    )
                    .orEmpty()
                trackLobbyPlayerNotices(onlinePlayers, updatedPlayers)
                onlinePlayers = updatedPlayers
                if (onlinePlayers.firstOrNull { it.id == onlineTempUid }?.activeInMatch == false) {
                    handleRemovedFromOnlineLobby()
                    return@addSnapshotListener
                }
                OnlineDebugLog.i(
                    "lobby_players_snapshot roomId=$onlinePartidaId players=${onlinePlayers.size} active=${activeOnlinePlayers().size} connected=${activeOnlinePlayers().count(::isOnlinePlayerConnected)}"
                )
                val visiblePlayers = activeOnlinePlayers()
                session = PlayerProfileStore.withProfiles(this, session.copy(
                    players = visiblePlayers.map { player ->
                        GamePlayer(
                            name = player.name,
                            initial = player.initial,
                            isHuman = player.id == onlineTempUid
                        )
                    },
                    playerProfiles = visiblePlayers.associate { player -> player.name to player.profile }
                ))
                coordinateOnlineMatchEntry()
                maybeClaimOnlineLobbyHostHandoff()
                maybeResetFinishedOnlineRoomForRematch()
                maybeContinuePendingOnlineCleanup()
                renderLobby()
            }
    }

    private fun applyOnlineRoomSnapshot(snapshot: DocumentSnapshot) {
        val previousActiveHostId = onlineActiveHostId
        val previousLobbyConfig = onlineLobbyConfig
        onlineLobbyName = snapshot.getString(FIELD_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: onlineLobbyName
        onlineRoomState = snapshot.getString(FIELD_STATE) ?: ONLINE_ROOM_STATE_WAITING
        onlineRoomModePrueba = snapshot.getBoolean(FIELD_TEST_MODE) ?: false
        onlineRoomMaxPlayers = snapshot.getLong(FIELD_MAX_PLAYERS)
            ?.toInt()
            ?.coerceIn(1, LocalGameFactory.MAX_PLAYERS)
            ?: LocalGameFactory.MAX_PLAYERS
        val onlineMinimumPlayers = if (onlineRoomModePrueba) {
            LocalGameFactory.TEST_MIN_PLAYERS
        } else {
            LocalGameFactory.MIN_PLAYERS
        }
        onlineExpectedPlayers = snapshot.getLong(FIELD_EXPECTED_PLAYERS)
            ?.toInt()
            ?.coerceIn(onlineMinimumPlayers, LocalGameFactory.MAX_PLAYERS)
            ?: onlineRoomMaxPlayers.coerceIn(onlineMinimumPlayers, LocalGameFactory.MAX_PLAYERS)
        onlineHostId = snapshot.getString(FIELD_HOST_ID).orEmpty()
        onlineActiveHostId = snapshot.getString(FIELD_ACTIVE_HOST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: onlineHostId
        onlineHostVersion = snapshot.getLong(FIELD_HOST_VERSION)?.toInt() ?: 0
        onlineRoomCode = snapshot.getString(FIELD_ROOM_CODE).orEmpty()
        onlineInitialMatchCreated = snapshot.getBoolean(FIELD_INITIAL_MATCH_CREATED) == true
        onlineCleanupPending = snapshot.getBoolean(FIELD_CLEANUP_PENDING) == true
        onlineLobbyConfig = OnlineLobbyConfig.fromFirestore(
            snapshot.get(OnlineLobbyConfig.FIELD_ROOM_CONFIG),
            onlineLobbyConfig
        )
        onlineInitialMatch = snapshot.get(FIELD_INITIAL_MATCH).asStringAnyMap()
        onlineMatchState = snapshot.get(FIELD_MATCH_STATE).asStringAnyMap()
        onlineClientStates = snapshot.get(FIELD_CLIENT_STATES).asStringAnyMap().orEmpty()
        onlineEntryReleasedMatchId = snapshot.getString(FIELD_ENTRY_RELEASED_MATCH_ID).orEmpty()
        onlineRoomSnapshotHasPendingWrites = snapshot.metadata.hasPendingWrites()
        session = session.copy(
            timingConfig = onlineLobbyConfig.timing,
            revealRolesOnDeath = onlineLobbyConfig.revealRolesOnDeath,
            showIndividualVotes = onlineLobbyConfig.showIndividualVotes
        )
        if (lobbyRoomBaselineReady) {
            if (previousActiveHostId.isNotBlank() && previousActiveHostId != onlineActiveHostId) {
                val newHostName = onlinePlayers.firstOrNull { it.id == onlineActiveHostId }?.name
                    ?: snapshot.getString(FIELD_HOST_NAME).orEmpty()
                addLobbySystemNotice("$newHostName ahora es el anfitrion.")
            }
            if (previousLobbyConfig != onlineLobbyConfig) {
                addLobbySystemNotice("Se actualizaron las opciones de partida.")
            }
        } else {
            lobbyRoomBaselineReady = true
        }
        trackLastOnlineResult(snapshot.get(FIELD_LAST_RESULT).asStringAnyMap())
        OnlineDebugLog.i(
            "lobby_room_snapshot roomId=$onlinePartidaId state=$onlineRoomState players=${onlinePlayers.size}/$onlineExpectedPlayers host=$onlineHostId activeHost=$onlineActiveHostId code=$onlineRoomCode initial=$onlineInitialMatchCreated"
        )

        val requestedMapKey = snapshot.getString(FIELD_MAP_KEY).orEmpty()
        val selectedMap = LocalGameFactory.maps.firstOrNull { it.key == requestedMapKey }
            ?: LocalGameFactory.maps.first()
        session = PlayerProfileStore.withProfiles(this, LocalGameFactory.selectMap(session, selectedMap.key))

        if (
            onlineRoomState == OnlineRoomFirestore.STATE_FINISHED ||
            onlineRoomState == ONLINE_ROOM_STATE_ABANDONED
        ) {
            OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        }

        val liveOnlineMatch = onlineInitialMatchCreated &&
            onlineInitialMatch != null &&
            onlineMatchState != null &&
            (onlineMatchState?.get("ganador") as? String).orEmpty().isBlank()
        maybeResetFinishedOnlineRoomForRematch()
        maybeContinuePendingOnlineCleanup()

        if (
            onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
            liveOnlineMatch &&
            !onlineStartedNoticeShown
        ) {
            coordinateOnlineMatchEntry()
        }
        maybeStartAfterExpectedPlayersUpdate()
        maybeClaimOnlineLobbyHostHandoff()
        renderLobby()
    }

    private fun maybeStartAfterExpectedPlayersUpdate() {
        val pendingExpected = pendingExpectedPlayersForStart ?: return
        if (
            pendingExpected == onlineExpectedPlayers &&
            !onlineExpectedUpdateInProgress &&
            !onlineCleanupPending
        ) {
            pendingExpectedPlayersForStart = null
            startOnlineRoomForEveryone()
        }
    }

    private fun parseOnlinePlayer(document: DocumentSnapshot): OnlineLobbyPlayer? {
        val name = (document.getString(PlayerPublicIdentity.FIELD_ROOM_NAME)
            ?: document.getString(FIELD_NAME))
            ?.trim()
            ?.let(RoomDisplayNames::withoutPublicId)
            ?.take(18)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val profile = onlineProfileFromDocument(document, name)
        return OnlineLobbyPlayer(
            id = document.id,
            name = name,
            initial = name.firstOrNull()?.uppercase() ?: "?",
            status = document.getString(FIELD_PLAYER_STATE) ?: PLAYER_STATE_CONNECTED,
            ready = document.getBoolean(FIELD_PLAYER_READY) == true,
            order = document.getLong(FIELD_PLAYER_ORDER)?.toInt() ?: Int.MAX_VALUE,
            activeInMatch = document.getBoolean(FIELD_ACTIVE_IN_MATCH) != false,
            mapVote = document.getString(FIELD_MAP_VOTE)?.takeIf { it in OnlineMapVoteResolver.mapKeys },
            lastSeenLocalMs = document.getTimestamp(OnlineRoomFirestore.FIELD_LAST_SEEN_AT)
                ?.toDate()
                ?.time
                ?: document.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL)
                ?: 0L,
            publicId = profile.publicId,
            profile = profile
        )
    }

    private fun trackLobbyPlayerNotices(
        previous: List<OnlineLobbyPlayer>,
        updated: List<OnlineLobbyPlayer>
    ) {
        if (!lobbyPlayersBaselineReady) {
            lobbyPlayersBaselineReady = true
            lastMapVoteLeaderKey = null
            return
        }
        val previousActive = previous.filter { it.activeInMatch }.associateBy { it.id }
        val updatedActive = updated.filter { it.activeInMatch }.associateBy { it.id }
        updatedActive.keys.minus(previousActive.keys).forEach { id ->
            addLobbySystemNotice("${updatedActive.getValue(id).name} entro a la sala.")
        }
        previousActive.keys.minus(updatedActive.keys).forEach { id ->
            addLobbySystemNotice("${previousActive.getValue(id).name} dejo la sala.")
        }
        previousActive.keys.intersect(updatedActive.keys).forEach { id ->
            val before = previousActive.getValue(id)
            val after = updatedActive.getValue(id)
            if (before.ready != after.ready) {
                addLobbySystemNotice(
                    if (after.ready) "${after.name} esta listo." else "${after.name} ya no esta listo."
                )
            }
        }
    }

    private fun trackLastOnlineResult(result: Map<String, Any?>?) {
        val winner = (result?.get("ganador") as? String).orEmpty()
        if (winner.isBlank()) return
        val resultKey = listOf(
            winner,
            (result?.get("ronda") as? Number)?.toInt() ?: 0,
            (result?.get("matchId") as? String).orEmpty()
        ).joinToString("|")
        if (resultKey == lastOnlineResultKey) return
        lastOnlineResultKey = resultKey
        addLobbySystemNotice("Ultima partida: $winner gano la partida.")
    }

    private fun onlineProfileFromDocument(document: DocumentSnapshot, fallbackName: String): PlayerProfile {
        val publicId = document.getString(PlayerPublicIdentity.FIELD_PUBLIC_ID).orEmpty()
        val profileName = document.getString(PlayerPublicIdentity.FIELD_PROFILE_NAME)
            ?.trim()
            ?.take(18)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
        val avatarKey = document.getString(PlayerPublicIdentity.FIELD_PROFILE_AVATAR)
            ?.takeIf { it.isNotBlank() }
            ?: "aldeana"
        val bannerKey = document.getString(PlayerPublicIdentity.FIELD_PROFILE_BANNER)
            ?.takeIf { it.isNotBlank() }
            ?: "pampa"
        val favoriteRoleKey = document.getString(PlayerPublicIdentity.FIELD_PROFILE_FAVORITE_ROLE)
            ?.takeIf { it.isNotBlank() }
            ?: "detective"
        return PlayerProfile(
            name = profileName,
            publicId = publicId,
            bio = document.getString(PlayerPublicIdentity.FIELD_PROFILE_BIO)
                ?.take(40)
                .orEmpty(),
            avatarKey = ProfileRoleCatalog.find(avatarKey).key,
            bannerKey = ProfileCustomizationCatalog.normalizeBannerKey(bannerKey),
            favoriteRoleKey = ProfileRoleCatalog.find(favoriteRoleKey).key,
            featuredAchievementIds = emptyList(),
            emoteIds = emptyList(),
            stats = PlayerStats(matches = 0, wins = 0, hasProgress = false)
        )
    }

    private fun onlineParticipants(): List<OnlineLobbyParticipant> {
        return onlinePlayers.map { player ->
            val presence = realtimePresenceStates[player.id]
            OnlineLobbyParticipant(
                id = player.id,
                connected = presence?.connected
                    ?: (!realtimePresenceBaselineReady && player.status == PLAYER_STATE_CONNECTED),
                ready = player.ready,
                activeInMatch = player.activeInMatch,
                order = player.order,
                lastSeenLocalMs = presence?.changedAtMs?.takeIf { it > 0L }
                    ?: player.lastSeenLocalMs
            )
        }
    }

    private fun isOnlinePlayerConnected(player: OnlineLobbyPlayer): Boolean {
        return isOnlineUidConnected(player.id, player.status == PLAYER_STATE_CONNECTED)
    }

    private fun isOnlineUidConnected(uid: String, legacyConnected: Boolean): Boolean {
        return realtimePresenceStates[uid]?.connected
            ?: (!realtimePresenceBaselineReady && legacyConnected)
    }

    private fun onlinePlayerLastSeenMs(player: OnlineLobbyPlayer): Long {
        return realtimePresenceStates[player.id]?.changedAtMs?.takeIf { it > 0L }
            ?: player.lastSeenLocalMs
    }

    private fun activeOnlinePlayers(): List<OnlineLobbyPlayer> {
        val activeIds = OnlineLobbyRules.activePlayers(onlineParticipants()).map { it.id }.toSet()
        return onlinePlayers.filter { it.id in activeIds }
    }

    private fun releasableDisconnectedOnlinePlayers(): List<OnlineLobbyPlayer> {
        val releasableIds = OnlineLobbyRules.releasableDisconnectedPlayers(onlineParticipants())
            .map { it.id }
            .toSet()
        return onlinePlayers.filter { it.id in releasableIds }
    }

    private fun currentVisiblePlayerCount(): Int {
        return if (isFirestoreOnlineLobby()) activeOnlinePlayers().size else session.players.size
    }

    private fun handleDeletedOnlineRoom() {
        if (onlineRoomDeletedHandled) return
        onlineRoomDeletedHandled = true
        cleanupRealtimeChatNodes(
            onComplete = {},
            onFailure = { error ->
                OnlineDebugLog.e("rtdb_deleted_room_chat_cleanup_failure roomId=$onlinePartidaId", error)
            }
        )
        Toast.makeText(this, "La sala fue eliminada. Volviendo a buscar partida.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleRemovedFromOnlineLobby() {
        if (onlineRemovalHandled) return
        onlineRemovalHandled = true
        OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        Toast.makeText(this, "Fuiste expulsado de la sala online.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun onlineLobbyHint(): String {
        val codeText = if (onlineRoomCode.isNotBlank()) {
            " Codigo $onlineRoomCode."
        } else if (onlineRoomModePrueba && onlinePartidaId.isNotBlank()) {
            " ID ${onlinePartidaId.take(6)}."
        } else {
            ""
        }
        return when (onlineRoomState) {
            ONLINE_ROOM_STATE_IN_GAME -> "Partida online iniciada.$codeText"
            else -> {
                val missing = (onlineExpectedPlayers - activeOnlinePlayers().size).coerceAtLeast(0)
                if (missing > 0) {
                    "Esperando $missing jugador${if (missing == 1) "" else "es"} mas.$codeText"
                } else {
                    "Sala completa. Faltan listos para iniciar.$codeText"
                }
            }
        }
    }

    private fun currentMaxPlayers(): Int {
        return if (isFirestoreOnlineLobby()) {
            onlineExpectedPlayers
        } else {
            LocalGameFactory.MAX_PLAYERS
        }
    }

    private fun currentUserIsOnlineHost(): Boolean {
        val creatorKeepsHost = onlineLobbyCreatorKeepsHost()
        return when {
            creatorKeepsHost && onlineHostId == onlineTempUid -> true
            !creatorKeepsHost && onlineLobbyHostFallbackId() == onlineTempUid -> true
            onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE -> true
            else -> false
        }
    }

    private fun onlineLobbyCreatorKeepsHost(nowMs: Long = System.currentTimeMillis()): Boolean {
        val creator = onlinePlayers.firstOrNull {
            it.id == onlineHostId && it.activeInMatch
        } ?: return false
        val lastSeenMs = onlinePlayerLastSeenMs(creator)
        return isOnlinePlayerConnected(creator) ||
            lastSeenMs <= 0L ||
            nowMs - lastSeenMs < LOBBY_HOST_DISCONNECT_GRACE_MS
    }

    private fun onlineLobbyHostFallbackId(): String {
        return activeOnlinePlayers()
            .asSequence()
            .filter(::isOnlinePlayerConnected)
            .sortedWith(compareBy<OnlineLobbyPlayer> { it.order }.thenBy { it.id })
            .firstOrNull()
            ?.id
            .orEmpty()
    }

    private fun onlineLobbyHostHandoffCandidate(excludeCurrent: Boolean = false): OnlineLobbyPlayer? {
        if (excludeCurrent) {
            return activeOnlinePlayers()
                .filter { it.id != onlineTempUid && isOnlinePlayerConnected(it) }
                .minWithOrNull(compareBy<OnlineLobbyPlayer> { it.order }.thenBy { it.id })
        }
        val candidateId = onlineLobbyHostFallbackId().takeIf { it.isNotBlank() } ?: return null
        return onlinePlayers.firstOrNull { it.id == candidateId }
    }

    private fun maybeClaimOnlineLobbyHostHandoff() {
        if (
            !isFirestoreOnlineLobby() ||
            onlineRoomState !in setOf(ONLINE_ROOM_STATE_WAITING, OnlineRoomFirestore.STATE_FINISHED) ||
            onlineHostHandoffInProgress ||
            onlineTempUid.isBlank()
        ) {
            return
        }
        if (onlineLobbyCreatorKeepsHost()) {
            scheduleOnlineLobbyHostHandoffCheck()
            return
        }
        val previousHostId = onlineHostId
        val candidate = onlineLobbyHostHandoffCandidate() ?: return
        if (candidate.id == onlineTempUid) {
            claimOnlineLobbyHostHandoff(previousHostId)
        }
    }

    private fun claimOnlineLobbyHostHandoff(previousHostId: String) {
        if (previousHostId.isBlank()) return
        onlineHostHandoffInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val previousHostReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION)
            .document(previousHostId)
        val candidateReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
        OnlineDebugLog.w(
            "lobby_host_handoff_claim_requested roomId=$onlinePartidaId previousHost=$previousHostId candidate=$onlineTempUid"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getString(FIELD_STATE) !in setOf(ONLINE_ROOM_STATE_WAITING, OnlineRoomFirestore.STATE_FINISHED)) {
                return@runTransaction false
            }
            val currentHostId = room.getString(FIELD_HOST_ID).orEmpty()
            if (currentHostId != previousHostId) {
                return@runTransaction false
            }
            val previousHost = transaction.get(previousHostReference)
            val candidate = transaction.get(candidateReference)
            val previousHostParticipant = OnlineLobbyParticipant(
                id = previousHostId,
                connected = isOnlineUidConnected(
                    previousHostId,
                    previousHost.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED
                ),
                ready = previousHost.getBoolean(FIELD_PLAYER_READY) == true,
                activeInMatch = previousHost.getBoolean(FIELD_ACTIVE_IN_MATCH) != false,
                order = previousHost.getLong(FIELD_PLAYER_ORDER)?.toInt() ?: Int.MAX_VALUE,
                lastSeenLocalMs = previousHost.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL) ?: 0L
            )
            val lastSeenAtMs = realtimePresenceStates[previousHostId]
                ?.changedAtMs
                ?.takeIf { it > 0L }
                ?: previousHost.getTimestamp(OnlineRoomFirestore.FIELD_LAST_SEEN_AT)
                ?.toDate()
                ?.time
                ?: previousHostParticipant.lastSeenLocalMs
            val creatorStillProtected = previousHost.exists() &&
                previousHostParticipant.activeInMatch &&
                (
                    previousHostParticipant.connected ||
                        lastSeenAtMs <= 0L ||
                        System.currentTimeMillis() - lastSeenAtMs < LOBBY_HOST_DISCONNECT_GRACE_MS
                    )
            if (creatorStillProtected) {
                return@runTransaction false
            }
            if (!isOnlineUidConnected(
                    onlineTempUid,
                    candidate.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED
                )
            ) {
                return@runTransaction false
            }
            if (candidate.getBoolean(FIELD_ACTIVE_IN_MATCH) == false) {
                return@runTransaction false
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_HOST_ID to onlineTempUid,
                    FIELD_HOST_NAME to candidate.getString(FIELD_NAME).orEmpty().ifBlank { onlinePlayerName },
                    FIELD_ACTIVE_HOST_ID to onlineTempUid,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to if (
                        previousHost.exists() && previousHostParticipant.activeInMatch
                    ) {
                        ((room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 1L) - 1L).coerceAtLeast(1L)
                    } else {
                        room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 1L
                    },
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            if (previousHost.exists()) {
                transaction.update(
                    previousHostReference,
                    mapOf(
                        FIELD_IS_HOST to false,
                        FIELD_ACTIVE_IN_MATCH to false,
                        FIELD_PLAYER_READY to false
                    )
                )
            }
            transaction.update(candidateReference, mapOf(FIELD_IS_HOST to true))
            true
        }.addOnSuccessListener { claimed ->
            onlineHostHandoffInProgress = false
            if (claimed == true) {
                onlineActiveHostId = onlineTempUid
                onlineHostId = onlineTempUid
                onlineHostVersion += 1
                OnlineDebugLog.w(
                    "lobby_host_handoff_claim_success roomId=$onlinePartidaId previousHost=$previousHostId newHost=$onlineTempUid"
                )
                Toast.makeText(this, "Ahora sos el anfitrion de la sala.", Toast.LENGTH_SHORT).show()
                renderLobby()
            }
        }.addOnFailureListener { error ->
            onlineHostHandoffInProgress = false
            OnlineDebugLog.e(
                "lobby_host_handoff_claim_failure roomId=$onlinePartidaId previousHost=$previousHostId candidate=$onlineTempUid",
                error
            )
        }
    }

    private fun transferLobbyHostAndExit(candidate: OnlineLobbyPlayer) {
        if (onlineHostHandoffInProgress) return
        onlineHostHandoffInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val candidateReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(candidate.id)
        val currentHostReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlineTempUid)
        OnlineDebugLog.w(
            "lobby_host_transfer_exit_requested roomId=$onlinePartidaId previousHost=$onlineTempUid candidate=${candidate.id}"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta esperando jugadores.")
            }
            val activeHostId = room.getString(FIELD_ACTIVE_HOST_ID).orEmpty()
            val hostId = room.getString(FIELD_HOST_ID).orEmpty()
            if (activeHostId != onlineTempUid && hostId != onlineTempUid) {
                throw IllegalStateException("Ya no sos el anfitrion activo.")
            }
            val candidateSnapshot = transaction.get(candidateReference)
            if (!isOnlineUidConnected(
                    candidate.id,
                    candidateSnapshot.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED
                )
            ) {
                throw IllegalStateException("El nuevo anfitrion ya no esta conectado.")
            }
            if (candidateSnapshot.getBoolean(FIELD_ACTIVE_IN_MATCH) == false) {
                throw IllegalStateException("El nuevo anfitrion ya no esta activo.")
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_HOST_ID to candidate.id,
                    FIELD_HOST_NAME to candidate.name,
                    FIELD_ACTIVE_HOST_ID to candidate.id,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to
                        ((room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 1L) - 1L)
                            .coerceAtLeast(1L),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.update(
                currentHostReference,
                mapOf(
                    FIELD_IS_HOST to false,
                    FIELD_ACTIVE_IN_MATCH to false,
                    FIELD_PLAYER_READY to false,
                    FIELD_PLAYER_STATE to PLAYER_STATE_DISCONNECTED,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis()
                )
            )
            transaction.update(candidateReference, mapOf(FIELD_IS_HOST to true))
            true
        }.addOnSuccessListener {
            onlineHostHandoffInProgress = false
            onlineActiveHostId = candidate.id
            onlineHostId = candidate.id
            leavingOnlineLobby = false
            onlineRemovalHandled = true
            OnlineDebugLog.w(
                "lobby_host_transfer_exit_success roomId=$onlinePartidaId previousHost=$onlineTempUid newHost=${candidate.id}"
            )
            finish()
        }.addOnFailureListener { error ->
            onlineHostHandoffInProgress = false
            OnlineDebugLog.e(
                "lobby_host_transfer_exit_failure roomId=$onlinePartidaId previousHost=$onlineTempUid candidate=${candidate.id}",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo transferir el anfitrion", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun renderStartButtonState() {
        if (!isFirestoreOnlineLobby()) {
            startButton.isEnabled = !isOnlineGuest() && session.players.size >= LocalGameFactory.MIN_PLAYERS
            startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
            startButton.text = if (isOnlineGuest()) "ESPERANDO AL ANFITRION" else "INICIAR PARTIDA"
            startButton.contentDescription = when {
                isOnlineGuest() -> "Esperando al anfitrion para iniciar la partida"
                !startButton.isEnabled -> "Faltan jugadores para iniciar la partida"
                else -> "Iniciar partida"
            }
            return
        }

        if (
            onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
            onlineInitialMatchCreated &&
            !onlineStartedNoticeShown
        ) {
            startButton.isEnabled = false
            startButton.alpha = 0.72f
            startButton.text = "SINCRONIZANDO ENTRADA..."
            startButton.contentDescription = "Sincronizando el inicio con todos los jugadores"
            return
        }

        val currentPlayer = currentOnlinePlayer()
        val currentReady = currentPlayer?.ready == true
        val canStart = currentUserIsOnlineHost() && onlineRoomCanStart()
        val activePlayers = activeOnlinePlayers()
        val missingPlayers = (onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)
        val disconnectedPlayers = activePlayers.count { !isOnlinePlayerConnected(it) }
        val missingReady = activePlayers.count {
            isOnlinePlayerConnected(it) && !it.ready
        }
        val canStartWithPresent = currentUserIsOnlineHost() &&
            activePlayers.size >= minimumOnlinePlayerLimit() &&
            missingPlayers > 0 &&
            disconnectedPlayers == 0 &&
            missingReady == 0 &&
            !onlineInitialMatchCreated
        startButton.isEnabled = onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineCleanupPending &&
            activePlayers.isNotEmpty() &&
            (canStart || currentPlayer != null)
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        startButton.text = when {
            onlineCleanupPending -> "LIMPIANDO..."
            canStart -> "INICIAR ONLINE"
            canStartWithPresent -> "JUGAR CON ${activePlayers.size} PRESENTES"
            currentUserIsOnlineHost() && missingPlayers > 0 -> "FALTAN $missingPlayers"
            currentUserIsOnlineHost() && disconnectedPlayers > 0 -> "SINCRONIZANDO"
            currentUserIsOnlineHost() && missingReady > 0 -> "FALTAN LISTOS"
            currentUserIsOnlineHost() && onlineInitialMatchCreated -> "SINCRONIZANDO"
            currentReady -> "NO LISTO"
            currentUserIsOnlineHost() -> "LISTO HOST"
            else -> "LISTO"
        }
        startButton.contentDescription = when {
            onlineCleanupPending -> "Limpiando datos de la partida anterior"
            canStart -> "Iniciar partida online para todos los jugadores"
            canStartWithPresent -> "Ajustar la sala e iniciar con ${activePlayers.size} jugadores presentes"
            currentUserIsOnlineHost() && missingPlayers > 0 ->
                "Faltan jugadores para iniciar la partida online"
            currentUserIsOnlineHost() && missingReady > 0 ->
                "Faltan jugadores listos para iniciar la partida online"
            currentUserIsOnlineHost() && disconnectedPlayers > 0 ->
                "Hay jugadores desconectados o sincronizando"
            currentReady -> "Marcarte como no listo"
            else -> "Marcarte como listo"
        }
    }

    private fun handleOnlineStartButton() {
        if (onlineRoomState != ONLINE_ROOM_STATE_WAITING) {
            Toast.makeText(this, "La sala ya no esta esperando jugadores.", Toast.LENGTH_SHORT).show()
            return
        }
        if (onlineCleanupPending) {
            Toast.makeText(this, "Terminando de limpiar la partida anterior...", Toast.LENGTH_SHORT).show()
            return
        }
        val activePlayers = activeOnlinePlayers()
        val canStartWithPresent = currentUserIsOnlineHost() &&
            activePlayers.size >= minimumOnlinePlayerLimit() &&
            activePlayers.size < onlineExpectedPlayers &&
            activePlayers.all { isOnlinePlayerConnected(it) && it.ready }
        if (currentUserIsOnlineHost() && onlineRoomCanStart()) {
            startOnlineRoomForEveryone()
        } else if (canStartWithPresent) {
            playOnlineWithPresentPlayers()
        } else {
            toggleCurrentOnlineReady()
        }
    }

    private fun toggleCurrentOnlineReady() {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        val nextReady = !(currentOnlinePlayer()?.ready == true)
        val publicId = PlayerPublicIdentity.currentPublicId(this)
        OnlineDebugLog.i("ready_update_requested roomId=$onlinePartidaId uid=$onlineTempUid ready=$nextReady")
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(
                PlayerPublicIdentity.publicProfileFields(this, publicId, onlinePlayerName) + mapOf(
                    FIELD_NAME to onlinePlayerName,
                    FIELD_PLAYER_STATE to PLAYER_STATE_CONNECTED,
                    FIELD_PLAYER_READY to nextReady,
                    "uidTemporal" to onlineTempUid,
                    OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to true,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                OnlineDebugLog.i("ready_update_success roomId=$onlinePartidaId uid=$onlineTempUid ready=$nextReady")
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("ready_update_failure roomId=$onlinePartidaId uid=$onlineTempUid", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo actualizar listo", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun startOnlineRoomForEveryone(hostTieBreakChoice: String? = null) {
        if (!currentUserIsOnlineHost()) {
            Toast.makeText(this, "Solo el anfitrion puede iniciar.", Toast.LENGTH_SHORT).show()
            return
        }
        val preflight = onlineStartPreflightMessage()
        if (preflight != null) {
            OnlineDebugLog.w(
                "online_start_preflight_blocked roomId=$onlinePartidaId hostId=$onlineTempUid reason=$preflight players=${activeOnlinePlayers().size}/$onlineExpectedPlayers"
            )
            Toast.makeText(this, preflight, Toast.LENGTH_SHORT).show()
            return
        }
        if (onlineInitialMatchCreated || onlineInitialMatch != null) {
            OnlineDebugLog.w("online_start_skipped_existing_initial_match roomId=$onlinePartidaId hostId=$onlineTempUid")
            coordinateOnlineMatchEntry()
            return
        }
        startButton.isEnabled = false
        startButton.text = "INICIANDO..."
        val onlineMatchId = UUID.randomUUID().toString()
        val activePlayersAtStart = activeOnlinePlayers()
        OnlineDebugLog.i(
            "online_start_requested roomId=$onlinePartidaId code=${onlineRoomCode.ifBlank { "-" }} hostId=$onlineTempUid expected=$onlineExpectedPlayers active=${activePlayersAtStart.size} tieBreak=${hostTieBreakChoice ?: "-"}"
        )
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getBoolean(FIELD_INITIAL_MATCH_CREATED) == true || room.get(FIELD_INITIAL_MATCH) != null) {
                return@runTransaction OnlineStartTransactionResult.AlreadyStarted
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta esperando jugadores.")
            }
            if (room.getBoolean(FIELD_CLEANUP_PENDING) == true) {
                throw IllegalStateException("La sala todavia esta limpiando la partida anterior.")
            }
            val activeHostId = room.getString(FIELD_ACTIVE_HOST_ID).orEmpty()
            val hostId = room.getString(FIELD_HOST_ID).orEmpty()
            if (activeHostId != onlineTempUid && hostId != onlineTempUid) {
                throw IllegalStateException("Solo el anfitrion puede iniciar.")
            }
            val expectedPlayers = room.getLong(FIELD_EXPECTED_PLAYERS)?.toInt() ?: onlineExpectedPlayers
            if (activePlayersAtStart.size != expectedPlayers) {
                throw IllegalStateException("Faltan jugadores para iniciar.")
            }
            val currentVotes = mutableListOf<OnlineMapVote>()
            activePlayersAtStart.forEach { player ->
                val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id)
                val snapshot = transaction.get(playerReference)
                val stillActive = snapshot.getBoolean(FIELD_ACTIVE_IN_MATCH) != false
                val connected = isOnlineUidConnected(
                    player.id,
                    snapshot.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED
                )
                val ready = snapshot.getBoolean(FIELD_PLAYER_READY) == true
                if (!snapshot.exists() || !stillActive || !connected || !ready) {
                    throw IllegalStateException("Todavia faltan jugadores listos.")
                }
                currentVotes += OnlineMapVote(
                    playerId = player.id,
                    playerInitial = player.initial,
                    mapKey = snapshot.getString(FIELD_MAP_VOTE)
                )
            }
            val selectedMapKey = when (
                val resolution = OnlineMapVoteResolver.resolveAtStart(
                    votes = currentVotes,
                    currentMapKey = room.getString(FIELD_MAP_KEY).orEmpty(),
                    hostTieBreakChoice = hostTieBreakChoice
                )
            ) {
                is OnlineMapResolution.Selected -> resolution.mapKey
                is OnlineMapResolution.HostTieBreakRequired -> {
                    return@runTransaction OnlineStartTransactionResult.MapTieBreakRequired(
                        resolution.mapKeys
                    )
                }
            }
            val selectedMap = LocalGameFactory.maps.first { it.key == selectedMapKey }
            val roomConfig = OnlineLobbyConfig.fromFirestore(
                room.get(OnlineLobbyConfig.FIELD_ROOM_CONFIG),
                onlineLobbyConfig
            )
            val assignedSession = LocalGameFactory.assignRoles(
                buildOnlineBaseSession(selectedMapKey, roomConfig).copy(onlineMatchId = onlineMatchId)
            )
            val initialMatch = initialMatchPayload(assignedSession)
            val matchState = matchStatePayload(assignedSession)
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_IN_GAME,
                    FIELD_MAP_KEY to selectedMap.key,
                    OnlineRoomFirestore.FIELD_MAP_NAME to selectedMap.name,
                    FIELD_INITIAL_MATCH to initialMatch,
                    FIELD_MATCH_STATE to matchState,
                    FIELD_INITIAL_MATCH_CREATED to true,
                    FIELD_CLEANUP_PENDING to false,
                    FIELD_CLIENT_STATES to FieldValue.delete(),
                    FIELD_ENTRY_RELEASED_MATCH_ID to FieldValue.delete(),
                    FIELD_ACTIVE_HOST_ID to onlineTempUid,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to activePlayersAtStart.size,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            OnlineStartTransactionResult.Started(selectedMap.key, onlineRoleSummary(assignedSession))
        }.addOnSuccessListener { result ->
            when (result) {
                OnlineStartTransactionResult.AlreadyStarted -> {
                    OnlineDebugLog.w("online_start_already_created roomId=$onlinePartidaId hostId=$onlineTempUid")
                    Toast.makeText(this, "La partida ya fue iniciada. Sincronizando...", Toast.LENGTH_SHORT).show()
                    coordinateOnlineMatchEntry()
                }
                is OnlineStartTransactionResult.MapTieBreakRequired -> {
                    startButton.isEnabled = true
                    renderStartButtonState()
                    showMapTieBreakDialog(result.mapKeys)
                }
                is OnlineStartTransactionResult.Started -> {
                    OnlineDebugLog.i(
                        "online_start_success roomId=$onlinePartidaId hostId=$onlineTempUid map=${result.mapKey} roles=${result.roleSummary}"
                    )
                }
            }
        }.addOnFailureListener { error ->
            OnlineDebugLog.e("online_start_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
            startButton.isEnabled = true
            renderStartButtonState()
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo iniciar online", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun coordinateOnlineMatchEntry() {
        if (
            onlineStartedNoticeShown ||
            onlineRoomState != ONLINE_ROOM_STATE_IN_GAME ||
            onlineInitialMatch == null ||
            onlineMatchState == null
        ) {
            return
        }
        val rebuiltSession = onlineInitialMatch?.let(::sessionFromInitialMatch)
        if (rebuiltSession == null || onlineMatchEntryProblem(rebuiltSession) != null) {
            startOnlineMatch()
            return
        }
        val matchId = rebuiltSession.onlineMatchId
        if (matchId.isBlank()) {
            startOnlineMatch()
            return
        }
        if (recoveringOnlineMatch) {
            onlineStartedNoticeShown = true
            startOnlineMatch()
            return
        }
        if (onlineEntryBarrierMatchId != matchId) {
            if (::startButton.isInitialized) {
                startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
            }
            onlineEntryReleaseTimeoutScheduled = false
            onlineEntryBarrierMatchId = matchId
            onlineEntryBarrierStartedAtMs = SystemClock.elapsedRealtime()
            onlineEntryAckMatchId = ""
            onlineEntryAckInProgress = false
            onlineEntryReleaseInProgress = false
        }
        if (
            onlineEntryReleasedMatchId == matchId &&
            !onlineRoomSnapshotHasPendingWrites
        ) {
            enterReleasedOnlineMatch(matchId)
            return
        }
        acknowledgeOnlineMatchEntry(matchId)
        maybeReleaseOnlineMatchEntry()
    }

    private fun acknowledgeOnlineMatchEntry(matchId: String) {
        if (activeOnlinePlayers().size != onlineExpectedPlayers) return
        val acknowledgedIds = OnlineLobbyEntryGate.acknowledgedPlayerIds(matchId, onlineClientStates)
        if (onlineTempUid in acknowledgedIds) {
            onlineEntryAckMatchId = matchId
            return
        }
        if (onlineEntryAckMatchId == matchId || onlineEntryAckInProgress) return
        onlineEntryAckInProgress = true
        OnlineDebugLog.i(
            "online_entry_ack_requested roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
        )
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .update(
                mapOf(
                    "$FIELD_CLIENT_STATES.$onlineTempUid" to mapOf(
                        "fase" to GamePhase.REPARTO.name,
                        "ronda" to 0,
                        "phaseIndex" to 0,
                        "enGameplay" to false,
                        "jugadoresVistos" to activeOnlinePlayers().size,
                        "jugadoresEsperados" to onlineExpectedPlayers,
                        "uidTemporal" to onlineTempUid,
                        OnlineLobbyEntryGate.FIELD_MATCH_ID to matchId,
                        OnlineLobbyEntryGate.FIELD_ENTRY_READY to true,
                        "actualizadaEnLocal" to System.currentTimeMillis()
                    ),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineEntryAckInProgress = false
                onlineEntryAckMatchId = matchId
                OnlineDebugLog.i(
                    "online_entry_ack_success roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
                )
                maybeReleaseOnlineMatchEntry()
            }
            .addOnFailureListener { error ->
                onlineEntryAckInProgress = false
                OnlineDebugLog.e(
                    "online_entry_ack_failure roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId",
                    error
                )
                if (::startButton.isInitialized && !isFinishing && !isDestroyed) {
                    startButton.postDelayed(
                        { coordinateOnlineMatchEntry() },
                        ONLINE_ENTRY_RETRY_MS
                    )
                }
            }
    }

    private fun maybeReleaseOnlineMatchEntry(force: Boolean = false) {
        if (
            onlineStartedNoticeShown ||
            recoveringOnlineMatch ||
            onlineRoomState != ONLINE_ROOM_STATE_IN_GAME ||
            !currentUserIsOnlineHost() ||
            onlineRoomSnapshotHasPendingWrites ||
            onlineEntryReleaseInProgress
        ) {
            return
        }
        val matchId = onlineEntryBarrierMatchId
        if (matchId.isBlank() || onlineEntryReleasedMatchId == matchId) return
        val expectedPlayerIds = activeOnlinePlayers().mapTo(linkedSetOf()) { it.id }
        if (expectedPlayerIds.size != onlineExpectedPlayers) {
            scheduleOnlineEntryReleaseTimeout()
            return
        }
        val elapsedMs = SystemClock.elapsedRealtime() - onlineEntryBarrierStartedAtMs
        val timedOut = force && elapsedMs >= ONLINE_ENTRY_RELEASE_TIMEOUT_MS
        val canRelease = OnlineLobbyEntryGate.canRelease(
            expectedPlayerIds = expectedPlayerIds,
            matchId = matchId,
            clientStates = onlineClientStates,
            force = timedOut
        )
        if (!canRelease) {
            scheduleOnlineEntryReleaseTimeout()
            return
        }
        val acknowledgedCount = OnlineLobbyEntryGate
            .acknowledgedPlayerIds(matchId, onlineClientStates)
            .count { it in expectedPlayerIds }
        onlineEntryReleaseInProgress = true
        OnlineDebugLog.i(
            "online_entry_release_requested roomId=$onlinePartidaId host=$onlineTempUid match=$matchId acknowledged=$acknowledgedCount/${expectedPlayerIds.size} forced=$timedOut"
        )
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .update(
                mapOf(
                    FIELD_ENTRY_RELEASED_MATCH_ID to matchId,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineEntryReleaseInProgress = false
                OnlineDebugLog.i(
                    "online_entry_release_success roomId=$onlinePartidaId host=$onlineTempUid match=$matchId"
                )
            }
            .addOnFailureListener { error ->
                onlineEntryReleaseInProgress = false
                OnlineDebugLog.e(
                    "online_entry_release_failure roomId=$onlinePartidaId host=$onlineTempUid match=$matchId",
                    error
                )
                scheduleOnlineEntryReleaseTimeout()
            }
    }

    private fun scheduleOnlineEntryReleaseTimeout() {
        if (
            onlineEntryReleaseTimeoutScheduled ||
            onlineStartedNoticeShown ||
            !currentUserIsOnlineHost() ||
            !::startButton.isInitialized
        ) {
            return
        }
        val elapsedMs = SystemClock.elapsedRealtime() - onlineEntryBarrierStartedAtMs
        val remainingMs = ONLINE_ENTRY_RELEASE_TIMEOUT_MS - elapsedMs
        val delayMs = if (remainingMs > 0L) remainingMs else ONLINE_ENTRY_RETRY_MS
        onlineEntryReleaseTimeoutScheduled = true
        startButton.postDelayed(onlineEntryReleaseTimeoutRunnable, delayMs)
    }

    private fun enterReleasedOnlineMatch(matchId: String) {
        if (onlineStartedNoticeShown) return
        onlineStartedNoticeShown = true
        if (::startButton.isInitialized) {
            startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
        }
        onlineEntryReleaseTimeoutScheduled = false
        OnlineDebugLog.i(
            "online_entry_released roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
        )
        startOnlineMatch()
    }

    private fun startOnlineMatch() {
        val sharedSession = onlineInitialMatch?.let(::sessionFromInitialMatch)
        if (sharedSession == null) {
            if (onlineRoomState == ONLINE_ROOM_STATE_IN_GAME || onlineInitialMatchCreated) {
                OnlineDebugLog.e(
                    "online_match_corrupt roomId=$onlinePartidaId uid=$onlineTempUid initial=$onlineInitialMatchCreated expected=$onlineExpectedPlayers players=${onlinePlayers.size}"
                )
                Toast.makeText(
                    this,
                    "La sala perdio datos de partida. Creen una sala nueva.",
                    Toast.LENGTH_LONG
                ).show()
                OnlineRoomRecovery.clearIf(this, onlinePartidaId)
                finish()
                return
            }
            if (currentUserIsOnlineHost()) {
                Toast.makeText(this, "Preparando partida online...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Esperando datos de partida online...", Toast.LENGTH_LONG).show()
            }
            onlineStartedNoticeShown = false
            return
        }
        val entryProblem = onlineMatchEntryProblem(sharedSession)
        if (entryProblem != null) {
            OnlineDebugLog.e(
                "online_match_entry_blocked roomId=$onlinePartidaId uid=$onlineTempUid reason=$entryProblem players=${sharedSession.players.size} expected=$onlineExpectedPlayers"
            )
            Toast.makeText(this, entryProblem, Toast.LENGTH_LONG).show()
            return
        }
        enteringOnlineMatch = true
        OnlineRoomRecovery.save(
            this,
            roomId = onlinePartidaId,
            roomCode = onlineRoomCode,
            roomName = onlineLobbyName.ifBlank { "Sala online" },
            mapKey = sharedSession.mapKey,
            isHost = currentUserIsOnlineHost()
        )
        OnlineDebugLog.i(
            "online_match_enter roomId=$onlinePartidaId code=${onlineRoomCode.ifBlank { "-" }} uid=$onlineTempUid isHost=${currentUserIsOnlineHost()} players=${sharedSession.players.size} expected=$onlineExpectedPlayers phase=${sharedSession.phase.name}:${sharedSession.phaseIndex} roles=${onlineRoleSummary(sharedSession)} recovering=$recoveringOnlineMatch"
        )
        Toast.makeText(this, "Partida online iniciada.", Toast.LENGTH_LONG).show()
        val targetActivity = if (recoveringOnlineMatch) {
            GameplayMockActivity::class.java
        } else {
            AssigningRolesActivity::class.java
        }
        startActivity(
            Intent(this, targetActivity)
                .putExtra(EXTRA_SESSION, sharedSession)
                .putExtra(GameplayMockActivity.EXTRA_TEMA, GameplayTableUi.themeForMapKey(sharedSession.mapKey))
                .putExtra(GameplayMockActivity.EXTRA_ES_NOCHE, false)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_PARTIDA_ID, onlinePartidaId)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_PLAYER_ID, onlineTempUid)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_IS_HOST, currentUserIsOnlineHost())
        )
    }

    private fun buildOnlineBaseSession(
        mapKey: String = session.mapKey,
        config: OnlineLobbyConfig = onlineLobbyConfig
    ): GameSession {
        val realPlayers = activeOnlinePlayers().map { player ->
            GamePlayer(
                name = player.name,
                initial = player.initial,
                isHuman = player.id == onlineTempUid
            )
        }
        val map = LocalGameFactory.maps.firstOrNull { it.key == mapKey } ?: currentMap()
        return GameSession(
            code = onlineRoomCode.ifBlank { onlinePartidaId.take(6) },
            mapKey = map.key,
            mapName = map.name,
            players = realPlayers,
            timingConfig = config.timing.normalized(),
            revealRolesOnDeath = config.revealRolesOnDeath,
            showIndividualVotes = config.showIndividualVotes,
            onlineTestMode = onlineRoomModePrueba,
            onlinePlayerUids = activeOnlinePlayers().map { it.id },
            roleComposition = LocalGameFactory.onlineSafeRoleComposition(realPlayers.size)
        )
    }

    private fun onlineRoleSummary(session: GameSession): String {
        return session.players
            .groupingBy { it.role?.key.orEmpty().ifBlank { "sin_rol" } }
            .eachCount()
            .toSortedMap()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun onlineStartPreflightMessage(): String? {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) {
            return "La sala online todavía no está lista."
        }
        if (onlineRoomState != ONLINE_ROOM_STATE_WAITING) {
            return "La sala ya no esta esperando jugadores."
        }
        if (onlineCleanupPending) {
            return "Terminando de limpiar la partida anterior."
        }
        if (onlineInitialMatchCreated || onlineInitialMatch != null) {
            return null
        }
        val activePlayers = activeOnlinePlayers()
        val missing = (onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)
        if (missing > 0) return "Faltan $missing jugadores para iniciar."
        if (activePlayers.size != onlineExpectedPlayers) {
            return "La cantidad de jugadores no coincide con la sala."
        }
        val disconnected = activePlayers.count { !isOnlinePlayerConnected(it) }
        if (disconnected > 0) {
            return "Hay $disconnected jugador(es) desconectado(s). Libera cupos o espera."
        }
        val notReady = activePlayers.count { !it.ready }
        if (notReady > 0) return "Todavia faltan $notReady jugador(es) listos."
        if (!onlineRoomCanStart()) {
            return "La sala sigue sincronizando. Espera unos segundos."
        }
        return null
    }

    private fun onlineMatchEntryProblem(session: GameSession): String? {
        if (onlineExpectedPlayers > 0 && session.players.size != onlineExpectedPlayers) {
            return "La sala no coincide con la cantidad esperada de jugadores."
        }
        if (session.players.none { it.isHuman }) {
            return "No se encontro tu jugador en esta partida. Reingresa por codigo."
        }
        if (session.players.any { it.role == null }) {
            return "El reparto online llego incompleto. Creen una sala nueva."
        }
        return null
    }

    private fun initialMatchPayload(assignedSession: GameSession): Map<String, Any?> {
        return mapOf(
            "matchId" to assignedSession.onlineMatchId,
            "codigoSala" to assignedSession.code,
            "mapa" to assignedSession.mapKey,
            "mapaNombre" to assignedSession.mapName,
            "fase" to assignedSession.phase.name,
            "ronda" to assignedSession.round,
            "creadaEnLocal" to System.currentTimeMillis(),
            "config" to mapOf(
                "transicionSeg" to assignedSession.timingConfig.transitionSeconds,
                "nocheSeg" to assignedSession.timingConfig.nightSeconds,
                "discusionSeg" to assignedSession.timingConfig.discussionSeconds,
                "votacionSeg" to assignedSession.timingConfig.votingSeconds,
                "revelarRolesAlMorir" to assignedSession.revealRolesOnDeath,
                "votosIndividuales" to assignedSession.showIndividualVotes
            ),
            "jugadores" to assignedSession.players.mapIndexed { index, player ->
                val onlinePlayer = activeOnlinePlayers().getOrNull(index)
                mapOf(
                    "orden" to index,
                    "uidTemporal" to onlinePlayer?.id.orEmpty(),
                    "publicId" to onlinePlayer?.publicId.orEmpty(),
                    "simulado" to false,
                    "nombre" to player.name,
                    "inicial" to player.initial,
                    "rolKey" to player.role?.key.orEmpty(),
                    "rolNombre" to player.role?.name.orEmpty(),
                    "rolEquipo" to player.role?.team.orEmpty(),
                    "rolImagen" to player.role?.imageResName.orEmpty()
                )
            }
        )
    }

    private fun matchStatePayload(assignedSession: GameSession): Map<String, Any?> {
        return mapOf(
            "fase" to assignedSession.phase.name,
            "ronda" to assignedSession.round,
            "phaseIndex" to assignedSession.phaseIndex,
            "anuncioPublico" to assignedSession.publicAnnouncement,
            "actualizadaEnLocal" to System.currentTimeMillis(),
            "actualizadaPor" to onlineTempUid
        )
    }

    private fun sessionFromInitialMatch(payload: Map<String, Any?>): GameSession? {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = payload,
            matchStateRaw = onlineMatchState,
            uidTemporal = onlineTempUid,
            expectedPlayers = onlineExpectedPlayers,
            fallbackRoomId = onlinePartidaId,
            fallbackRoomCode = onlineRoomCode,
            fallbackMapKey = currentMap().key,
            fallbackMapName = currentMap().name,
            revealRolesOnDeath = session.revealRolesOnDeath,
            showIndividualVotes = session.showIndividualVotes
        )
        return when (result) {
            is OnlineMatchSessionResult.Success -> result.session
            is OnlineMatchSessionResult.Failure -> {
                OnlineDebugLog.e(
                    "online_match_rebuild_failure roomId=$onlinePartidaId uid=$onlineTempUid reason=${result.reason.name}"
                )
                null
            }
        }
    }

    private fun Any?.asStringAnyMap(): Map<String, Any?>? {
        return (this as? Map<*, *>)?.entries
            ?.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                key to entry.value
            }
            ?.toMap()
    }

    private fun currentOnlinePlayer(): OnlineLobbyPlayer? {
        return activeOnlinePlayers().firstOrNull { it.id == onlineTempUid }
    }

    private fun maybeResetFinishedOnlineRoomForRematch() {
        val endedInGame = onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
            (
                onlineMatchState == null ||
                    (onlineMatchState?.get("ganador") as? String).orEmpty().isNotBlank()
                )
        if (
            (onlineRoomState != OnlineRoomFirestore.STATE_FINISHED && !endedInGame) ||
            !currentUserIsOnlineHost() ||
            onlineRematchResetInProgress ||
            onlineCleanupPending ||
            onlinePlayers.isEmpty()
        ) {
            return
        }
        onlineRematchResetInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val playersToReset = onlinePlayers.filter { it.activeInMatch }
        OnlineDebugLog.i(
            "rematch_reset_requested roomId=$onlinePartidaId hostId=$onlineTempUid players=${playersToReset.size}"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            val roomState = room.getString(FIELD_STATE).orEmpty()
            val authoritativeState = room.get(FIELD_MATCH_STATE).asStringAnyMap()
            val roomEndedInGame = roomState == ONLINE_ROOM_STATE_IN_GAME &&
                (
                    authoritativeState == null ||
                        (authoritativeState["ganador"] as? String).orEmpty().isNotBlank()
                    )
            if (roomState != OnlineRoomFirestore.STATE_FINISHED && !roomEndedInGame) {
                return@runTransaction false
            }
            val stableHostId = room.getString(FIELD_HOST_ID).orEmpty()
            if (stableHostId != onlineTempUid) {
                throw IllegalStateException("Solo el anfitrion puede preparar la revancha.")
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_WAITING,
                    FIELD_ACTIVE_HOST_ID to stableHostId,
                    FIELD_INITIAL_MATCH_CREATED to false,
                    FIELD_CLEANUP_PENDING to true,
                    FIELD_INITIAL_MATCH to FieldValue.delete(),
                    FIELD_MATCH_STATE to FieldValue.delete(),
                    FIELD_CLIENT_STATES to FieldValue.delete(),
                    FIELD_ENTRY_RELEASED_MATCH_ID to FieldValue.delete(),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            playersToReset.forEach { player ->
                transaction.update(
                    roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id),
                    mapOf(
                        FIELD_PLAYER_READY to false,
                        FIELD_IS_HOST to (player.id == stableHostId),
                        FIELD_MAP_VOTE to FieldValue.delete(),
                        "listoParaVotar" to false,
                        "listoParaVotarRonda" to 0,
                        "listoParaVotarPhaseIndex" to 0
                    )
                )
            }
            true
        }.addOnSuccessListener { reset ->
            onlineRematchResetInProgress = false
            if (reset == true) {
                onlineInitialMatchCreated = false
                onlineInitialMatch = null
                onlineMatchState = null
                onlineCleanupPending = true
                onlineStartedNoticeShown = false
                recoveringOnlineMatch = false
                OnlineRoomRecovery.clearIf(this, onlinePartidaId)
                OnlineDebugLog.i(
                    "rematch_reset_success roomId=$onlinePartidaId hostId=$onlineTempUid players=${playersToReset.size}"
                )
                Toast.makeText(
                    this,
                    "Sala preparada. Limpiando la partida anterior...",
                    Toast.LENGTH_LONG
                ).show()
                maybeContinuePendingOnlineCleanup()
            }
        }.addOnFailureListener { error ->
            onlineRematchResetInProgress = false
            OnlineDebugLog.e(
                "rematch_reset_failure roomId=$onlinePartidaId hostId=$onlineTempUid",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo preparar otra partida", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showMapTieBreakDialog(mapKeys: List<String>) {
        val validKeys = mapKeys.filter { it in OnlineMapVoteResolver.mapKeys }.distinct()
        if (validKeys.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Empate en la votacion")
            .setMessage("Tu voto ya conto como uno. Ahora elegi entre los mapas empatados para iniciar.")
            .setItems(validKeys.map(::mapName).toTypedArray()) { _, index ->
                startOnlineRoomForEveryone(validKeys[index])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun scheduleOnlineLobbyHostHandoffCheck() {
        if (onlineHostHandoffCheckScheduled || !::startButton.isInitialized) return
        val creator = onlinePlayers.firstOrNull {
            it.id == onlineHostId && it.activeInMatch && !isOnlinePlayerConnected(it)
        } ?: return
        val lastSeenMs = onlinePlayerLastSeenMs(creator)
        if (lastSeenMs <= 0L) return
        val elapsedMs = System.currentTimeMillis() - lastSeenMs
        val remainingMs = (LOBBY_HOST_DISCONNECT_GRACE_MS - elapsedMs).coerceAtLeast(250L)
        onlineHostHandoffCheckScheduled = true
        startButton.postDelayed(
            {
                onlineHostHandoffCheckScheduled = false
                maybeClaimOnlineLobbyHostHandoff()
            },
            remainingMs
        )
    }

    private fun maybeContinuePendingOnlineCleanup() {
        if (
            !onlineCleanupPending ||
            onlineCleanupInProgress ||
            onlineRoomState != ONLINE_ROOM_STATE_WAITING ||
            !currentUserIsOnlineHost()
        ) {
            return
        }
        onlineCleanupInProgress = true
        // La limpieza es housekeeping y es best-effort: nunca debe trabar volver a jugar.
        // Si borrar el chat o las acciones falla, se loguea y se sigue; lo unico
        // imprescindible es bajar limpiezaPendiente para desbloquear la sala. Ademas el
        // matchId ya aisla los datos viejos, asi que borrarlos es cosmetico.
        cleanupRealtimeChatNodes(
            onComplete = { cleanupOnlineActionsThenFinish() },
            onFailure = { error ->
                OnlineDebugLog.e("rtdb_chat_cleanup_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
                cleanupOnlineActionsThenFinish()
            }
        )
    }

    private fun cleanupOnlineActionsThenFinish() {
        cleanupOnlineMatchCollections(
            collectionNames = listOf("acciones"),
            index = 0,
            onComplete = { finishOnlineCleanup() },
            onFailure = { error ->
                OnlineDebugLog.e("acciones_cleanup_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
                finishOnlineCleanup()
            }
        )
    }

    private fun finishOnlineCleanup() {
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .update(
                mapOf(
                    FIELD_CLEANUP_PENDING to false,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineCleanupInProgress = false
                onlineCleanupPending = false
                OnlineDebugLog.i("rematch_cleanup_done roomId=$onlinePartidaId hostId=$onlineTempUid")
                Toast.makeText(
                    this,
                    "Sala lista. Todos deben marcarse listos otra vez.",
                    Toast.LENGTH_LONG
                ).show()
                renderLobby()
            }
            .addOnFailureListener(::handleOnlineCleanupFailure)
    }

    private fun cleanupRealtimeChatNodes(
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updates = mapOf<String, Any?>(
            LobbyChatController.NODE to null,
            RTDB_PUBLIC_CHAT_NODE to null,
            RTDB_TRAITOR_CHAT_NODE to null,
            RTDB_SPECTATOR_CHAT_NODE to null
        )
        FirebaseDatabase.getInstance()
            .getReference("salas/$onlinePartidaId")
            .updateChildren(updates)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener(onFailure)
    }

    private fun cleanupOnlineMatchCollections(
        collectionNames: List<String>,
        index: Int,
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (index >= collectionNames.size) {
            onComplete()
            return
        }
        val collection = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(collectionNames[index])
        collection.limit(CLEANUP_BATCH_SIZE).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    cleanupOnlineMatchCollections(collectionNames, index + 1, onComplete, onFailure)
                    return@addOnSuccessListener
                }
                val batch = FirebaseFirestore.getInstance().batch()
                snapshot.documents.forEach { document -> batch.delete(document.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        cleanupOnlineMatchCollections(collectionNames, index, onComplete, onFailure)
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    private fun handleOnlineCleanupFailure(error: Exception) {
        onlineCleanupInProgress = false
        OnlineDebugLog.e("rematch_cleanup_flag_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
        if (::startButton.isInitialized) {
            startButton.postDelayed({ maybeContinuePendingOnlineCleanup() }, CLEANUP_RETRY_DELAY_MS)
        }
    }

    private fun allOnlinePlayersReady(): Boolean {
        return activeOnlinePlayers().isNotEmpty() &&
            activeOnlinePlayers().all { isOnlinePlayerConnected(it) && it.ready }
    }

    private fun onlineRoomCanStart(): Boolean {
        return OnlineLobbyRules.canStart(
            players = onlineParticipants(),
            expectedPlayers = onlineExpectedPlayers,
            roomWaiting = onlineRoomState == ONLINE_ROOM_STATE_WAITING,
            initialMatchCreated = onlineInitialMatchCreated || onlineCleanupPending
        )
    }

    private fun minimumOnlinePlayersToStart(): Int {
        return onlineExpectedPlayers
    }

    private fun updateOnlineControlState() {
        val firestoreLobby = isFirestoreOnlineLobby()
        val localPlayerControlsVisibility = if (firestoreLobby) View.GONE else View.VISIBLE
        (btnAddPlayer.parent as? View)?.visibility = localPlayerControlsVisibility
        btnAddPlayer.visibility = localPlayerControlsVisibility
        btnRemovePlayer.visibility = localPlayerControlsVisibility

        timingOptionsButton.visibility = if (firestoreLobby) View.GONE else View.VISIBLE
        timingOptionsButton.isEnabled = !firestoreLobby
        btnAdvancedOptions.visibility = View.VISIBLE
        btnAdvancedOptions.isEnabled = true
        btnAdvancedOptions.alpha = 1f
    }

    private fun setupMapSelector() {
        mapCards.forEachIndexed { index, imageView ->
            val map = LocalGameFactory.maps[index]
            imageView.setImageResource(map.imageRes)
            imageView.setOnClickListener {
                if (isFirestoreOnlineLobby()) {
                    updateOnlineMapVote(map.key)
                    return@setOnClickListener
                }
                if (isOnlineGuest()) {
                    Toast.makeText(this, "El mapa lo administra la sala online.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                session = PlayerProfileStore.withProfiles(this, LocalGameFactory.selectMap(session, map.key).let {
                    it.copy(
                        roleComposition = LocalGameFactory.defaultRoleComposition(
                            it.players.size,
                            it.mapKey
                        )
                    )
                })
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(OpcionesActivity.PREF_LAST_SELECTED_MAP, session.mapKey)
                    .apply()
                renderLobby()
            }
        }
    }

    private fun updateOnlineMapVote(mapKey: String) {
        if (onlineRoomState != ONLINE_ROOM_STATE_WAITING || onlineInitialMatchCreated) {
            Toast.makeText(this, "La votacion de mapa ya termino.", Toast.LENGTH_SHORT).show()
            return
        }
        if (mapKey !in OnlineMapVoteResolver.mapKeys || onlineTempUid.isBlank()) return
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .update(
                mapOf(
                    FIELD_MAP_VOTE to mapKey,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                OnlineDebugLog.e("map_vote_failure roomId=$onlinePartidaId uid=$onlineTempUid map=$mapKey", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo votar el mapa", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun renderDebugRole() {
        val (roleKey, label) = debugRoles[debugRoleIndex]
        val minimumPlayers = LocalGameFactory.minimumPlayersForRole(roleKey)
        val requirement = if (minimumPlayers > LocalGameFactory.MIN_PLAYERS) " ($minimumPlayers+)" else ""
        debugRoleButton.text = "ROL: $label$requirement"
    }

    private fun showTimingDialog() {
        var draft = session.timingConfig.normalized()
        val content = dialogColumn()
        content.addView(dialogTitle("TIEMPOS DE PARTIDA"))

        val valueViews = linkedMapOf<TimingField, TextView>()
        val minusButtons = linkedMapOf<TimingField, Button>()
        val plusButtons = linkedMapOf<TimingField, Button>()
        val presetButtons = linkedMapOf<GameTimingPreset, Button>()
        var customButton: Button? = null
        var customMode = draft.preset() == null
        val presetDescription = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(6), dp(3), dp(6), dp(4))
            maxLines = 2
        }
        fun refreshValues() {
            valueViews.forEach { (field, view) ->
                view.text = "${field.value(draft)} s"
                val currentValue = field.value(draft)
                updateTimingStepButton(
                    minusButtons.getValue(field),
                    enabled = currentValue > field.minimum
                )
                updateTimingStepButton(
                    plusButtons.getValue(field),
                    enabled = currentValue < field.maximum
                )
            }
            val selectedPreset = draft.preset()
            presetButtons.forEach { (preset, button) ->
                val selected = !customMode && preset == selectedPreset
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
                button.alpha = if (selected) 1f else 0.82f
            }
            customButton?.apply {
                setBackgroundResource(
                    if (customMode) {
                        R.drawable.bg_btn_gold_ripple
                    } else {
                        R.drawable.bg_btn_dark_ripple
                    }
                )
                setTextColor(
                    getColor(if (customMode) R.color.bg_dark else R.color.text_primary)
                )
                alpha = if (customMode) 1f else 0.82f
            }
            presetDescription.text = if (customMode) {
                "Configuracion personalizada. Puedes ajustar cada tiempo manualmente."
            } else {
                selectedPreset?.description.orEmpty()
            }
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        GameTimingPreset.entries.forEachIndexed { index, preset ->
            val button = compactDialogButton(preset.label).apply {
                textSize = 12f
                isAllCaps = false
                setOnClickListener {
                    draft = preset.config
                    customMode = false
                    refreshValues()
                }
            }
            presetButtons[preset] = button
            val params = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                if (index > 0) marginStart = dp(7)
            }
            presetRow.addView(button, params)
        }
        val customPresetButton = compactDialogButton("PERSONALIZADO").apply {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                11,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            maxLines = 1
            isSingleLine = true
            isAllCaps = false
            setOnClickListener {
                customMode = true
                refreshValues()
            }
        }
        customButton = customPresetButton
        presetRow.addView(
            customPresetButton,
            LinearLayout.LayoutParams(0, dp(40), 1.18f).apply { marginStart = dp(7) }
        )
        content.addView(
            presetRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(presetDescription)

        TimingField.entries.forEach { field ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
            }
            val label = TextView(this).apply {
                text = field.label
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                maxLines = 1
            }
            row.addView(label, LinearLayout.LayoutParams(0, dp(34), 1f))

            val minus = compactDialogButton("-")
            val value = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent_gold))
                textSize = 15f
                maxLines = 1
            }
            val plus = compactDialogButton("+")
            valueViews[field] = value
            minusButtons[field] = minus
            plusButtons[field] = plus
            minus.setOnClickListener {
                customMode = true
                draft = field.update(draft, field.value(draft) - field.step)
                refreshValues()
            }
            plus.setOnClickListener {
                customMode = true
                draft = field.update(draft, field.value(draft) + field.step)
                refreshValues()
            }
            row.addView(minus, LinearLayout.LayoutParams(dp(40), dp(36)))
            row.addView(value, LinearLayout.LayoutParams(dp(64), dp(36)))
            row.addView(plus, LinearLayout.LayoutParams(dp(40), dp(36)))
            content.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
        }
        refreshValues()

        val dialogContent = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogContent)
            .setNegativeButton("CANCELAR", null)
            .setNeutralButton("RESTABLECER", null)
            .setPositiveButton("APLICAR", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                draft = GameTimingPreset.NORMAL.config
                customMode = false
                refreshValues()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                session = session.copy(timingConfig = draft.normalized())
                renderLobby()
                dialog.dismiss()
            }
        }
        showLandscapeDialog(dialog, widthDp = 620)
    }

    private fun buildTimingEditor(initial: GameTimingConfig): TimingEditor {
        var draft = initial.normalized()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(8))
        }
        val presetButtons = linkedMapOf<GameTimingPreset, Button>()
        val valueViews = linkedMapOf<TimingField, TextView>()
        val minusButtons = linkedMapOf<TimingField, Button>()
        val plusButtons = linkedMapOf<TimingField, Button>()
        var customMode = draft.preset() == null

        fun refresh() {
            val selectedPreset = draft.preset()
            presetButtons.forEach { (preset, button) ->
                val selected = !customMode && preset == selectedPreset
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
            }
            valueViews.forEach { (field, view) ->
                val value = field.value(draft)
                view.text = "$value s"
                updateTimingStepButton(minusButtons.getValue(field), value > field.minimum)
                updateTimingStepButton(plusButtons.getValue(field), value < field.maximum)
            }
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        GameTimingPreset.entries.forEachIndexed { index, preset ->
            val button = compactDialogButton(preset.label).apply {
                setOnClickListener {
                    draft = preset.config
                    customMode = false
                    refresh()
                }
            }
            presetButtons[preset] = button
            presetRow.addView(button, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        container.addView(presetRow)

        TimingField.entries.forEach { field ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.addView(TextView(this).apply {
                text = field.label.lowercase().replaceFirstChar { it.uppercase() }
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                maxLines = 1
            }, LinearLayout.LayoutParams(0, dp(38), 1f))
            val minus = compactDialogButton("-")
            val value = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
            }
            val plus = compactDialogButton("+")
            minusButtons[field] = minus
            valueViews[field] = value
            plusButtons[field] = plus
            minus.setOnClickListener {
                customMode = true
                draft = field.update(draft, field.value(draft) - field.step)
                refresh()
            }
            plus.setOnClickListener {
                customMode = true
                draft = field.update(draft, field.value(draft) + field.step)
                refresh()
            }
            row.addView(minus, LinearLayout.LayoutParams(dp(40), dp(36)))
            row.addView(value, LinearLayout.LayoutParams(dp(62), dp(36)))
            row.addView(plus, LinearLayout.LayoutParams(dp(40), dp(36)))
            container.addView(row)
        }
        refresh()
        return TimingEditor(container) { draft.normalized() }
    }

    private fun showTestOptionsDialog() {
        var quickTestMode = session.quickTestMode
        var botsObeyVotes = session.debugBotsObeyVoteCommands
        var forceTies = session.debugForceVoteTies
        var botsNeverKill = session.debugBotsNeverKillHuman
        var botsNeverVote = session.debugBotsNeverVoteHuman
        var forcedRoleIndex = debugRoleIndex
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES DE TESTEO"))
        content.addView(dialogSectionTitle("PRUEBA RAPIDA"))

        fun addTestSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            content.addView(SwitchCompat(this).apply {
                applyTraidoresSwitchStyle()
                text = label
                isChecked = checked
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                minHeight = dp(46)
                setPadding(dp(6), dp(7), dp(6), dp(7))
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(2)
            })
        }

        addTestSwitch("Modo test rapido", quickTestMode) { quickTestMode = it }
        content.addView(TextView(this).apply {
            text = "Permite saltear fases sin accion humana y acelera las votaciones."
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(8))
        })
        if (isDebugBuild) {
            content.addView(dialogSectionTitle("HERRAMIENTAS DEBUG"))
            addTestSwitch("IA obedece votos del chat", botsObeyVotes) { botsObeyVotes = it }
            addTestSwitch("Forzar empates", forceTies) { forceTies = it }
            addTestSwitch("Bots no te matan de noche", botsNeverKill) { botsNeverKill = it }
            addTestSwitch("Bots no te votan", botsNeverVote) { botsNeverVote = it }
            val roleButton = compactDialogButton("")
            fun refreshForcedRole() {
                val (_, label) = debugRoles[forcedRoleIndex]
                roleButton.text = "Forzar tu rol: $label"
            }
            roleButton.setOnClickListener {
                forcedRoleIndex = (forcedRoleIndex + 1) % debugRoles.size
                refreshForcedRole()
            }
            refreshForcedRole()
            content.addView(
                roleButton,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(6)
                }
            )
        } else {
            content.addView(TextView(this).apply {
                text = "Las herramientas debug solo aparecen en compilaciones de prueba."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
        }
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("APLICAR") { _, _ ->
                debugRoleIndex = forcedRoleIndex
                session = session.copy(
                    quickTestMode = quickTestMode,
                    debugBotsObeyVoteCommands = botsObeyVotes,
                    debugForceVoteTies = forceTies,
                    debugBotsNeverKillHuman = botsNeverKill,
                    debugBotsNeverVoteHuman = botsNeverVote
                )
                renderLobby()
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 560)
    }

    private fun showAdvancedOptionsDialog() {
        if (isFirestoreOnlineLobby()) {
            showOnlineAdvancedOptionsDialog()
            return
        }
        var revealRolesOnDeath = session.revealRolesOnDeath
        var showIndividualVotes = session.showIndividualVotes
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var roleReadingSeconds = preferences
            .getInt(PREF_ROLE_READING_SECONDS, DEFAULT_ROLE_READING_SECONDS)
            .let {
                when {
                    it <= 0 -> 0
                    it <= 6 -> 6
                    else -> 10
                }
            }
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES AVANZADAS"))
        content.addView(dialogSectionTitle("REGLAS DE LA PARTIDA"))
        val revealRolesSwitch = SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "Mostrar roles al morir"
            isChecked = revealRolesOnDeath
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(2), dp(4), dp(8))
            setOnCheckedChangeListener { _, checked ->
                revealRolesOnDeath = checked
            }
        }
        content.addView(revealRolesSwitch)
        content.addView(TextView(this).apply {
            text = "Desactivado por defecto: las cartas eliminadas permanecen ocultas."
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(8))
        })
        val individualVotesSwitch = SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "MOSTRAR VOTOS INDIVIDUALES"
            isChecked = showIndividualVotes
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(2), dp(4), dp(4))
            setOnCheckedChangeListener { _, checked ->
                showIndividualVotes = checked
            }
        }
        content.addView(individualVotesSwitch)
        content.addView(TextView(this).apply {
            text = "Activado por defecto: muestra quien voto a cada jugador. " +
                "Desactivado: solo muestra los votos recibidos."
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(8))
        })
        content.addView(TextView(this).apply {
            text = "LECTURA INICIAL DEL ROL"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(2), dp(4), dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Define cuando aparece EMPEZAR despues de recibir la carta."
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(5))
        })
        val readingButtons = mutableListOf<Pair<Int, Button>>()
        val readingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun refreshReadingButtons() {
            readingButtons.forEach { (seconds, button) ->
                val selected = seconds == roleReadingSeconds
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(
                    getColor(if (selected) R.color.bg_dark else R.color.text_primary)
                )
            }
        }
        listOf(0 to "INMEDIATO", 6 to "6 S", 10 to "10 S").forEachIndexed {
                index, (seconds, label) ->
            val button = compactDialogButton(label)
            readingButtons += seconds to button
            button.setOnClickListener {
                roleReadingSeconds = seconds
                refreshReadingButtons()
            }
            readingRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (index > 0) marginStart = dp(6)
                }
            )
        }
        refreshReadingButtons()
        content.addView(
            readingRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )
        val timingEditor = buildTimingEditor(session.timingConfig)
        content.addView(dialogSectionTitle("TIEMPOS DE PARTIDA"))
        content.addView(timingEditor.view)
        content.addView(TextView(this).apply {
            text = "COMPOSICION DE ROLES"
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "Los aldeanos rellenan los espacios libres. Los roles bloqueados no pertenecen al mapa actual o requieren mas jugadores."
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(6))
        })

        val roleKeys = LocalGameFactory.visibleRoleCompositionKeys()
        val draftCounts = linkedMapOf<String, Int>().apply {
            val normalized = LocalGameFactory.normalizedRoleComposition(session)
            roleKeys.forEach { key ->
                put(key, normalized.counts[key] ?: 0)
            }
        }
        val countViews = linkedMapOf<String, TextView>()
        val minusRoleButtons = linkedMapOf<String, Button>()
        val plusRoleButtons = linkedMapOf<String, Button>()
        val compositionPresetButtons = linkedMapOf<RoleCompositionPreset, Button>()
        var selectedCompositionPreset: RoleCompositionPreset? =
            if (session.roleComposition.customized) null else RoleCompositionPreset.RECOMMENDED
        val compositionPresetDescription = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(6))
            maxLines = 2
        }
        fun minRoleCount(key: String): Int {
            return when (key) {
                RoleCatalog.POLICIA, RoleCatalog.MEDICO, RoleCatalog.ASESINO -> 1
                else -> 0
            }
        }
        fun nonVillagerTotal(): Int {
            return draftCounts
                .filterKeys { it != RoleCatalog.ALDEANO }
                .values
                .sum()
        }
        fun refreshRoleComposition() {
            val playerTotal = session.players.size
            val villagers = (playerTotal - nonVillagerTotal()).coerceAtLeast(0)
            draftCounts[RoleCatalog.ALDEANO] = villagers
            roleKeys.forEach { key ->
                val value = draftCounts[key] ?: 0
                countViews[key]?.text = value.toString()
                val max = LocalGameFactory.maxCountForRole(key, playerTotal, session.mapKey)
                val canDecrease = key != RoleCatalog.ALDEANO && value > minRoleCount(key)
                val canIncrease = key != RoleCatalog.ALDEANO &&
                    value < max &&
                    villagers > 0
                updateRoleStepButton(minusRoleButtons[key], canDecrease)
                updateRoleStepButton(plusRoleButtons[key], canIncrease)
            }
            compositionPresetButtons.forEach { (preset, button) ->
                val selected = preset == selectedCompositionPreset
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
                button.alpha = if (selected) 1f else 0.82f
            }
            compositionPresetDescription.text = selectedCompositionPreset?.description
                ?: "Personalizado: ajustaste la cantidad exacta de roles manualmente."
        }
        fun applyCompositionPreset(preset: RoleCompositionPreset) {
            val presetComposition = LocalGameFactory.roleCompositionPreset(
                session.players.size,
                session.mapKey,
                preset
            )
            roleKeys.forEach { key ->
                draftCounts[key] = presetComposition.counts[key] ?: 0
            }
            selectedCompositionPreset = preset
            refreshRoleComposition()
        }
        val compositionPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        RoleCompositionPreset.entries.forEachIndexed { index, preset ->
            val button = compactDialogButton(preset.label).apply {
                textSize = 11f
                isAllCaps = false
                setOnClickListener { applyCompositionPreset(preset) }
            }
            compositionPresetButtons[preset] = button
            compositionPresetRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (index > 0) marginStart = dp(6)
                }
            )
        }
        content.addView(
            compositionPresetRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        )
        content.addView(compositionPresetDescription)
        val scroll = ScrollView(this)
        val roles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        roleKeys.forEach { key ->
            val minimum = LocalGameFactory.minimumPlayersForRole(key)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            val locked = LocalGameFactory.maxCountForRole(
                key,
                session.players.size,
                session.mapKey
            ) == 0
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@LobbyActivity).apply {
                    text = roleLabel(key)
                    setTextColor(
                        if (locked) {
                            getColor(R.color.text_muted)
                        } else {
                            roleCompositionColor(key)
                        }
                    )
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(this@LobbyActivity).apply {
                    text = roleMeta(key, minimum)
                    setTextColor(getColor(if (locked) R.color.text_muted else R.color.text_secondary))
                    textSize = 9f
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            val minus = compactDialogButton("-").apply {
                textSize = 13f
                setOnClickListener {
                    val current = draftCounts[key] ?: 0
                    if (current > minRoleCount(key)) {
                        draftCounts[key] = current - 1
                        selectedCompositionPreset = null
                        refreshRoleComposition()
                    }
                }
            }
            minusRoleButtons[key] = minus
            row.addView(minus, LinearLayout.LayoutParams(dp(34), dp(34)))
            val countView = TextView(this).apply {
                text = "0"
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_btn_dark)
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            countViews[key] = countView
            row.addView(countView, LinearLayout.LayoutParams(dp(46), dp(34)).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            })
            val plus = compactDialogButton("+").apply {
                textSize = 13f
                setOnClickListener {
                    val current = draftCounts[key] ?: 0
                    val max = LocalGameFactory.maxCountForRole(key, session.players.size, session.mapKey)
                    if (current < max && (draftCounts[RoleCatalog.ALDEANO] ?: 0) > 0) {
                        draftCounts[key] = current + 1
                        selectedCompositionPreset = null
                        refreshRoleComposition()
                    }
                }
            }
            plusRoleButtons[key] = plus
            row.addView(plus, LinearLayout.LayoutParams(dp(34), dp(34)))
            roles.addView(row)
        }
        refreshRoleComposition()
        scroll.addView(roles)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
            )
        )

        val advancedDialogContent = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(advancedDialogContent)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("APLICAR") { _, _ ->
                preferences.edit()
                    .putInt(PREF_ROLE_READING_SECONDS, roleReadingSeconds)
                    .apply()
                val roleComposition = RoleCompositionConfig(
                    counts = draftCounts.toMap(),
                    customized = selectedCompositionPreset != RoleCompositionPreset.RECOMMENDED
                )
                session = session.copy(
                    roleComposition = LocalGameFactory.normalizedRoleComposition(
                        session.copy(roleComposition = roleComposition)
                    ),
                    revealRolesOnDeath = revealRolesOnDeath,
                    showIndividualVotes = showIndividualVotes,
                    timingConfig = timingEditor.currentConfig()
                )
                renderLobby()
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 640)
    }

    private fun showOnlineAdvancedOptionsDialog() {
        var revealRolesOnDeath = session.revealRolesOnDeath
        var showIndividualVotes = session.showIndividualVotes
        val canEdit = currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineInitialMatchCreated
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES DE PARTIDA"))
        if (!canEdit) {
            content.addView(TextView(this).apply {
                text = "Configuracion sincronizada por el anfitrion."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), 0, dp(4), dp(7))
            })
        }
        content.addView(dialogSectionTitle("REGLAS"))
        content.addView(SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "Mostrar roles al morir"
            isChecked = revealRolesOnDeath
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isEnabled = canEdit
            setOnCheckedChangeListener { _, checked -> revealRolesOnDeath = checked }
        })
        content.addView(SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "Mostrar votos individuales"
            isChecked = showIndividualVotes
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isEnabled = canEdit
            setOnCheckedChangeListener { _, checked -> showIndividualVotes = checked }
        })
        content.addView(dialogSectionTitle("TIEMPOS"))
        val timingEditor = buildTimingEditor(session.timingConfig)
        content.addView(timingEditor.view)
        if (!canEdit) setViewTreeEnabled(timingEditor.view, false)
        val scroll = ScrollView(this).apply { addView(content) }
        val builder = AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton(if (canEdit) "CANCELAR" else "CERRAR", null)
        if (canEdit) {
            builder.setPositiveButton("APLICAR") { _, _ ->
                saveOnlineLobbyConfig(
                    OnlineLobbyConfig(
                        timing = timingEditor.currentConfig(),
                        revealRolesOnDeath = revealRolesOnDeath,
                        showIndividualVotes = showIndividualVotes
                    )
                )
            }
        }
        showLandscapeDialog(builder.create(), widthDp = 560)
    }

    private fun saveOnlineLobbyConfig(config: OnlineLobbyConfig) {
        val safe = config.normalized()
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .update(
                mapOf(
                    OnlineLobbyConfig.FIELD_ROOM_CONFIG to safe.toFirestore(),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onlineLobbyConfig = safe
                session = session.copy(
                    timingConfig = safe.timing,
                    revealRolesOnDeath = safe.revealRolesOnDeath,
                    showIndividualVotes = safe.showIndividualVotes
                )
                renderLobby()
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("lobby_config_update_failure roomId=$onlinePartidaId", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudieron guardar las opciones", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun setViewTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.68f
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> setViewTreeEnabled(view.getChildAt(index), enabled) }
        }
    }

    private fun showLobbyOptionsDialog() {
        AccessibilityOptionsDialog.show(this)
    }

    private fun roleLabel(roleKey: String): String {
        return when (roleKey) {
            RoleCatalog.ALDEANO -> "ALDEANO"
            RoleCatalog.POLICIA -> if (session.mapKey == "pampa") "COMISARIO" else "DETECTIVE"
            RoleCatalog.MEDICO -> "MEDICO"
            RoleCatalog.ASESINO -> "ASESINO"
            RoleCatalog.MERCENARIO -> "MERCENARIO"
            RoleCatalog.ALCALDE -> "ALCALDE"
            RoleCatalog.DESERTOR -> "DESERTOR"
            RoleCatalog.ESPIA -> "ESPIA"
            RoleCatalog.PAYADOR -> "PAYADOR"
            RoleCatalog.ORACULO -> "ORACULO"
            RoleCatalog.BUFON -> "BUFON"
            else -> roleKey.uppercase()
        }
    }

    private fun roleMeta(roleKey: String, minimumPlayers: Int): String {
        val availability = when (roleKey) {
            RoleCatalog.PAYADOR -> "Mapa Pampa"
            RoleCatalog.ORACULO -> "Mapa Grecia"
            RoleCatalog.BUFON -> "Mapa Medieval"
            RoleCatalog.ASESINO, RoleCatalog.MERCENARIO, RoleCatalog.ESPIA -> "Traidor"
            RoleCatalog.DESERTOR -> "Neutral"
            else -> "Pueblo"
        }
        return "$availability · $minimumPlayers+ jugadores"
    }

    private fun roleCompositionColor(roleKey: String): Int {
        return when (roleKey) {
            RoleCatalog.ASESINO,
            RoleCatalog.MERCENARIO,
            RoleCatalog.ESPIA -> getColor(R.color.accent_red)
            RoleCatalog.DESERTOR,
            RoleCatalog.BUFON -> getColor(R.color.accent_purple)
            RoleCatalog.PAYADOR,
            RoleCatalog.ORACULO -> getColor(R.color.accent_blue)
            else -> getColor(R.color.text_primary)
        }
    }

    private fun updateRoleStepButton(button: Button?, enabled: Boolean) {
        button ?: return
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
        button.setTextColor(getColor(if (enabled) R.color.text_primary else R.color.text_muted))
    }

    private fun dialogColumn(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 20f
            setPadding(0, dp(2), 0, dp(10))
        }
    }

    private fun dialogSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.accent_gold))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(10), dp(4), dp(5))
        }
    }

    private fun compactDialogButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            isAllCaps = false
            maxLines = 1
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_btn_dark_ripple)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                14,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }
    }

    private fun updateTimingStepButton(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.setBackgroundResource(
            if (enabled) R.drawable.bg_btn_dark_ripple else R.drawable.bg_btn_gold_ripple
        )
        button.setTextColor(
            getColor(if (enabled) R.color.text_primary else R.color.bg_dark)
        )
        button.alpha = if (enabled) 1f else 0.45f
    }

    private fun showLandscapeDialog(dialog: AlertDialog, widthDp: Int) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val availableWidth = resources.displayMetrics.widthPixels - dp(32)
        dialog.window?.setLayout(
            dp(widthDp).coerceAtMost(availableWidth),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setDimAmount(0.55f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        listOf(
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
            AlertDialog.BUTTON_POSITIVE
        ).forEach { buttonId ->
            dialog.getButton(buttonId)?.apply {
                minHeight = dp(44)
                maxLines = 1
                isAllCaps = false
                setTextColor(getColor(R.color.accent_gold))
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    10,
                    14,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
            }
        }
        dialog.window?.decorView?.post {
            val availableHeight = resources.displayMetrics.heightPixels - dp(32)
            val measuredHeight = dialog.window?.decorView?.measuredHeight ?: return@post
            if (measuredHeight > availableHeight) {
                dialog.window?.setLayout(
                    dp(widthDp).coerceAtMost(availableWidth),
                    availableHeight
                )
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showPlayerProfile(player: GamePlayer) {
        session = PlayerProfileStore.withProfiles(this, session)
        val profile = PlayerProfileStore.profileFor(this, session, player)
        PlayerProfileDialog.showFull(
            activity = this,
            profile = profile,
            canEdit = player.isHuman
        )
    }

    private fun confirmPlayerRemoval(
        index: Int,
        player: GamePlayer,
        onlinePlayer: OnlineLobbyPlayer? = null
    ) {
        if (isFirestoreOnlineLobby()) {
            if (onlinePlayer == null || !canCurrentHostRemoveOnlinePlayer(onlinePlayer)) {
                Toast.makeText(this, "No se puede expulsar a ese jugador ahora.", Toast.LENGTH_SHORT).show()
                return
            }
            AlertDialog.Builder(this)
                .setTitle("Expulsar participante online")
                .setMessage(
                    "Quitar a ${onlinePlayer.name} de la sala? " +
                        "Su cupo quedara liberado y no podra iniciar esta partida."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Expulsar") { _, _ ->
                    removeOnlinePlayer(onlinePlayer)
                }
                .show()
            return
        }
        if (isOnlineGuest()) {
            Toast.makeText(this, "Solo el anfitrion puede expulsar jugadores.", Toast.LENGTH_SHORT).show()
            return
        }
        if (index == 0) {
            Toast.makeText(this, "El anfitrion no se puede expulsar.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Expulsar participante")
            .setMessage("Quitar a ${player.name} de la sala local?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Expulsar") { _, _ ->
                session = PlayerProfileStore.withProfiles(this, LocalGameFactory.removePlayer(session, index))
                renderLobby()
            }
            .show()
    }

    private fun currentMap(): GameMap {
        return LocalGameFactory.maps.firstOrNull { it.key == session.mapKey } ?: LocalGameFactory.maps.first()
    }

    private fun mapDescriptionFor(mapKey: String): String {
        return when (mapKey) {
            "grecia" ->
                "Intriga entre templos, plazas y discursos que esconden traiciones."
            "medieval" ->
                "Secretos entre murallas, castillos y un feudo que desconfía de todos."
            else ->
                "Sospechas en la pampa, el polvo del pueblo y la estación abandonada."
        }
    }

    private fun selectedMapRoleLabel(mapKey: String): String {
        return when (mapKey) {
            "grecia" -> "Rol exclusivo: Oráculo"
            "medieval" -> "Rol exclusivo: Bufón"
            else -> "Rol exclusivo: Payador"
        }
    }

    private fun isOnlineGuest(): Boolean {
        return lobbyMode == MODE_ONLINE_SEARCH
    }

    private fun isFirestoreOnlineLobby(): Boolean {
        return onlinePartidaId.isNotBlank() &&
            (lobbyMode == MODE_ONLINE_CREATE || lobbyMode == MODE_ONLINE_SEARCH)
    }

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(EXTRA_SESSION) as? GameSession
    }

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_LOBBY_MODE = "extra_lobby_mode"
        const val EXTRA_LOBBY_NAME = "extra_lobby_name"
        const val EXTRA_PARTIDA_ID = "extra_partida_id"
        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val EXTRA_RECOVERING_ONLINE = "extra_recovering_online"
        const val MODE_LOCAL = "local"
        const val MODE_ONLINE_CREATE = "online_create"
        const val MODE_ONLINE_SEARCH = "online_search"
        private const val ONLINE_ROOMS_COLLECTION = "partidas"
        private const val ONLINE_PLAYERS_COLLECTION = "jugadores"
        private const val ONLINE_ROOM_STATE_WAITING = "esperando"
        private const val ONLINE_ROOM_STATE_IN_GAME = "en_juego"
        private const val ONLINE_ROOM_STATE_ABANDONED = "abandonada"
        private const val RTDB_PUBLIC_CHAT_NODE = "chat"
        private const val RTDB_TRAITOR_CHAT_NODE = "chat_traidores"
        private const val RTDB_SPECTATOR_CHAT_NODE = "chat_espectadores"
        private const val FIELD_NAME = "nombre"
        private const val FIELD_STATE = "estado"
        private const val FIELD_TEST_MODE = "modoPrueba"
        private const val FIELD_MAX_PLAYERS = "maxJugadores"
        private const val FIELD_EXPECTED_PLAYERS = "jugadoresEsperados"
        private const val FIELD_HOST_ID = "hostId"
        private const val FIELD_HOST_NAME = "hostNombre"
        private const val FIELD_ACTIVE_HOST_ID = "hostActivoId"
        private const val FIELD_HOST_VERSION = "hostVersion"
        private const val FIELD_INITIAL_MATCH_CREATED = "partidaInicialCreada"
        private const val FIELD_CLEANUP_PENDING = "limpiezaPendiente"
        private const val FIELD_ROOM_CODE = "codigoSala"
        private const val FIELD_MAP_KEY = "mapa"
        private const val FIELD_MAP_VOTE = "votoMapa"
        private const val FIELD_INITIAL_MATCH = "partidaInicial"
        private const val FIELD_MATCH_STATE = "estadoPartida"
        private const val FIELD_CLIENT_STATES = "estadoClientes"
        private const val FIELD_ENTRY_RELEASED_MATCH_ID = "entradaLiberadaMatchId"
        private const val FIELD_LAST_RESULT = "ultimoResultado"
        private const val FIELD_IS_HOST = "esHost"
        private const val FIELD_PLAYER_STATE = "estado"
        private const val FIELD_PLAYER_READY = "listo"
        private const val FIELD_PLAYER_ORDER = "orden"
        private const val FIELD_ACTIVE_IN_MATCH = "activoEnPartida"
        private const val PLAYER_STATE_CONNECTED = "conectado"
        private const val PLAYER_STATE_DISCONNECTED = "desconectado"
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val PREF_ROLE_READING_SECONDS = "role_reading_seconds"
        private const val PREF_LOBBY_CHAT_PREVIEW_HIDDEN = "lobby_chat_preview_hidden"
        private const val DEFAULT_ROLE_READING_SECONDS = 0
        private const val MAX_LOCAL_LOBBY_NOTICES = 12
        private const val LOBBY_HOST_DISCONNECT_GRACE_MS = 60_000L
        private const val CLEANUP_BATCH_SIZE = 400L
        private const val CLEANUP_RETRY_DELAY_MS = 5_000L
        private const val LOBBY_CHAT_PREVIEW_LINES = 3
        private const val LOBBY_EMOTE_SOUND_COOLDOWN_MS = 900L
        private const val ONLINE_ENTRY_RELEASE_TIMEOUT_MS = 10_000L
        private const val ONLINE_ENTRY_RETRY_MS = 1_500L
    }

    private data class OnlineLobbyPlayer(
        val id: String,
        val name: String,
        val initial: String,
        val status: String,
        val ready: Boolean,
        val order: Int,
        val activeInMatch: Boolean,
        val mapVote: String?,
        val publicId: String,
        val profile: PlayerProfile,
        val lastSeenLocalMs: Long
    ) {
        fun statusLabel(activeHostId: String): String {
            val baseStatus = if (id == activeHostId) {
                "Anfitrion"
            } else if (status.equals(PLAYER_STATE_CONNECTED, ignoreCase = true)) {
                "Conectado"
            } else if (status.equals(PLAYER_STATE_DISCONNECTED, ignoreCase = true)) {
                "Desconectado"
            } else {
                status.replaceFirstChar { it.uppercase() }
            }
            return if (ready && status.equals(PLAYER_STATE_CONNECTED, ignoreCase = true)) {
                "$baseStatus - Listo"
            } else {
                baseStatus
            }
        }
    }

    private sealed interface OnlineStartTransactionResult {
        object AlreadyStarted : OnlineStartTransactionResult
        data class MapTieBreakRequired(val mapKeys: List<String>) : OnlineStartTransactionResult
        data class Started(val mapKey: String, val roleSummary: String) : OnlineStartTransactionResult
    }

    private data class MapVoteViews(
        val shade: View,
        val overlay: View,
        val count: TextView,
        val voters: TextView
    )

    private data class TimingEditor(
        val view: View,
        val currentConfig: () -> GameTimingConfig
    )

    private enum class TimingField(
        val label: String,
        val step: Int,
        val minimum: Int,
        val maximum: Int
    ) {
        TRANSITION(
            "CAMBIO DIA/NOCHE",
            GameTimingConfig.TRANSITION_STEP_SECONDS,
            GameTimingConfig.MIN_TRANSITION_SECONDS,
            GameTimingConfig.MAX_TRANSITION_SECONDS
        ),
        NIGHT(
            "ACCION NOCTURNA",
            GameTimingConfig.NIGHT_STEP_SECONDS,
            GameTimingConfig.MIN_NIGHT_SECONDS,
            GameTimingConfig.MAX_NIGHT_SECONDS
        ),
        DISCUSSION(
            "DISCUSION",
            GameTimingConfig.DISCUSSION_STEP_SECONDS,
            GameTimingConfig.MIN_DISCUSSION_SECONDS,
            GameTimingConfig.MAX_DISCUSSION_SECONDS
        ),
        VOTING(
            "VOTACION",
            GameTimingConfig.VOTING_STEP_SECONDS,
            GameTimingConfig.MIN_VOTING_SECONDS,
            GameTimingConfig.MAX_VOTING_SECONDS
        );

        fun value(config: GameTimingConfig): Int {
            return when (this) {
                TRANSITION -> config.transitionSeconds
                NIGHT -> config.nightSeconds
                DISCUSSION -> config.discussionSeconds
                VOTING -> config.votingSeconds
            }
        }

        fun update(config: GameTimingConfig, value: Int): GameTimingConfig {
            val updated = when (this) {
                TRANSITION -> config.copy(transitionSeconds = value)
                NIGHT -> config.copy(nightSeconds = value)
                DISCUSSION -> config.copy(discussionSeconds = value)
                VOTING -> config.copy(votingSeconds = value)
            }
            return updated.normalized()
        }
    }
}
