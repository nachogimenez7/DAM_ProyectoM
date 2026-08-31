package com.traidores.juego

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import com.traidores.juego.GameToast as Toast
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.TextViewCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class LobbyActivity : BaseActivity() {

    private lateinit var session: GameSession
    private lateinit var btnAddPlayer: Button
    private lateinit var btnRemovePlayer: Button
    private lateinit var btnAdvancedOptions: Button
    private lateinit var practiceRoleSummary: TextView
    private lateinit var lobbyMapBackground: ImageView
    private lateinit var playersContainer: LinearLayout
    private lateinit var playerCount: TextView
    private lateinit var startButton: Button
    private lateinit var mapCards: List<ImageView>
    private lateinit var selectedMapImage: ImageView
    private lateinit var selectedMapName: TextView
    private lateinit var selectedMapRole: TextView
    private lateinit var timingOptionsButton: Button
    private lateinit var lobbyTitle: TextView
    private lateinit var lobbyModeHint: TextView
    private lateinit var onlineStartProgress: LinearLayout
    private lateinit var onlineStartProgressBar: ProgressBar
    private lateinit var onlineStartProgressText: TextView
    private lateinit var onlineInviteLabel: TextView
    private lateinit var onlinePlayersLabel: TextView
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
    private lateinit var onlineMapVoteTitle: TextView
    private lateinit var mapVoteCardsRow: LinearLayout
    private lateinit var mapVoteResultHint: TextView
    private lateinit var onlinePlayersScroll: HorizontalScrollView
    private lateinit var onlinePlayersContainer: LinearLayout
    private lateinit var onlineRoleCompositionPanel: LinearLayout
    private lateinit var onlineRoleSectionTitle: TextView
    private lateinit var onlineRoleBalance: TextView
    private lateinit var onlineRoleSummary: TextView
    private lateinit var onlineRolePresetRow: LinearLayout
    private lateinit var onlineRolePresetButtons: Map<RoleCompositionPreset, Button>
    private lateinit var btnConfigureOnlineRoles: Button
    private lateinit var onlineRulesSummary: TextView
    private lateinit var lobbyOptionsRow: LinearLayout
    private lateinit var localPlayerControlsRow: LinearLayout
    private lateinit var lobbyConfigurationLabel: TextView
    private lateinit var playersListPanel: LinearLayout
    private lateinit var lobbyPlayersLabel: TextView
    private lateinit var lobbyBodyScroll: ScrollView
    private lateinit var lobbyStartDock: LinearLayout
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
    private var onlineMatchStateMatchId = ""
    private var onlineCheckpointLoadInProgress = false
    private var onlineCheckpointLoadedMatchId = ""
    private var onlinePrivateRoleAssignments: List<Map<String, Any?>> = emptyList()
    private var onlinePrivateRolesMatchId = ""
    private var onlinePrivateRolesLoading = false
    private var onlinePrivateRoleLoadAttempt = 0
    private var onlinePrivateRoleLoadGeneration = 0
    private var onlinePrivateRoleAttemptMatchId = ""
    private var onlinePrivateRoleListener: ListenerRegistration? = null
    private var onlinePrivateRoleTimeoutRunnable: Runnable? = null
    private var onlinePrivateRoleRetryRunnable: Runnable? = null
    private var onlineStartedNoticeShown = false
    private var onlineClientStates: Map<String, Any?> = emptyMap()
    private var onlineEntryReleasedMatchId = ""
    private var onlineRoomSnapshotHasPendingWrites = false
    private var onlineEntryBarrierMatchId = ""
    private var onlineEntryBarrierStartedAtMs = 0L
    private var onlineEntryAckMatchId = ""
    private var onlineEntryAckInProgress = false
    private var onlineEntryAckRunnable: Runnable? = null
    private var onlineEntryReleaseInProgress = false
    private var onlineEntryReleaseTimeoutScheduled = false
    private var onlineRealtimeAccessReadyMatchId = ""
    private var onlineRealtimeAccessSyncInProgress = false
    private var onlineRealtimeAccessRetryRunnable: Runnable? = null
    private var onlineMatchEntryRetryCount = 0
    private var onlineMatchEntryRetryMatchId = ""
    private var onlineMatchEntryRetryRunnable: Runnable? = null
    private var lastOnlineMatchRebuildFailureReason = ""
    private var onlineIncompatibleNoticeShown = false
    private var recoveringOnlineMatch = false
    private var onlineRoomDeletedHandled = false
    private var onlineRemovalHandled = false
    private var onlineStartedMatchCancellationInProgress = false
    private var ownPlayerListener: ListenerRegistration? = null
    private var ownRoomBanListener: ListenerRegistration? = null
    private var leavingOnlineLobby = false
    private var enteringOnlineMatch = false
    private var returnedFromOnlineMatch = false
    private var onlineRematchReactivationInProgress = false
    private var onlineRematchReactivationCompleted = false
    private var onlineHostHandoffInProgress = false
    private var onlineHostHandoffCheckScheduled = false
    private var onlineRematchResetInProgress = false
    private var onlineCleanupInProgress = false
    private var onlineExpectedUpdateInProgress = false
    private var onlinePlayersServerRefreshInProgress = false
    private var onlineStartTransactionInProgress = false
    private var onlineExitPreflightInProgress = false
    private var onlineExitInProgress = false
    private var pendingOnlineRolePreset: RoleCompositionPreset? = null
    private var pendingOnlineRolePresetRunnable: Runnable? = null
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
    private var lobbyChatExpandedScroll: ScrollView? = null
    private var lobbyPlayersBaselineReady = false
    private var lastMapVoteLeaderKey: String? = null
    private var lastOnlineResultKey = ""
    private var lobbyRoomBaselineReady = false
    private var realtimePresence: RealtimeRoomPresence? = null
    private var realtimeGameplaySync: RealtimeGameplaySync? = null
    private var realtimeLobbySyncRestartRunnable: Runnable? = null
    private var lobbyReconnectGraceRefreshRunnable: Runnable? = null
    private var realtimePresenceStates = emptyMap<String, RealtimePresenceState>()
    private var realtimePresenceBaselineReady = false
    private var lobbyRealtimeAccessReady = false
    private var onlineTempUid = ""
    private var onlinePlayerName = ""
    private var practiceRoleIndex = 0
    private val firestoreUsage = OnlineFirestoreUsageCounter()

    private val onlineEntryReleaseTimeoutRunnable = Runnable {
        onlineEntryReleaseTimeoutScheduled = false
        maybeReleaseOnlineMatchEntry()
    }
    private val practiceRoles = listOf(
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

        lobbyMode = intent.getStringExtra(EXTRA_LOBBY_MODE) ?: MODE_LOCAL
        val incomingSession = readSession() ?: LocalGameFactory.createSession()
        session = PlayerProfileStore.withProfiles(
            this,
            if (lobbyMode == MODE_LOCAL) {
                LocalBotNameStore.apply(this, incomingSession)
            } else {
                incomingSession
            }
        )
        onlineLobbyConfig = OnlineLobbyConfig.fromSession(session)
        onlineLobbyName = intent.getStringExtra(EXTRA_LOBBY_NAME).orEmpty()
        onlinePartidaId = intent.getStringExtra(EXTRA_PARTIDA_ID).orEmpty()
        onlineRoomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        recoveringOnlineMatch = intent.getBooleanExtra(EXTRA_RECOVERING_ONLINE, false)
        if (onlinePartidaId.isNotBlank()) {
            onlineTempUid = OnlineTempIdentity.getOrCreate(this)
            // profileName devuelve el alias cuando no hay cuenta; leer la preferencia directo
            // dejaria entrar a la sala con un nombre libre que las reglas rechazan.
            onlinePlayerName = PlayerPublicIdentity.profileName(this)
            OnlineStabilityReport.beginRoom(
                context = this,
                roomCode = onlineRoomCode,
                matchId = "",
                isHost = lobbyMode == MODE_ONLINE_CREATE,
                expectedPlayers = onlineExpectedPlayers
            )
        }

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnLobbySettings: ImageButton = findViewById(R.id.btnLobbySettings)
        btnAddPlayer = findViewById(R.id.btnAddPlayer)
        btnRemovePlayer = findViewById(R.id.btnRemovePlayer)
        btnAdvancedOptions = findViewById(R.id.btnAdvancedOptions)
        practiceRoleSummary = findViewById(R.id.practiceRoleSummary)
        timingOptionsButton = findViewById(R.id.btnTimingOptions)
        lobbyTitle = findViewById(R.id.lobbyTitle)
        lobbyModeHint = findViewById(R.id.lobbyModeHint)
        onlineStartProgress = findViewById(R.id.onlineStartProgress)
        onlineStartProgressBar = findViewById(R.id.onlineStartProgressBar)
        onlineStartProgressText = findViewById(R.id.onlineStartProgressText)
        onlineInviteLabel = findViewById(R.id.onlineInviteLabel)
        onlinePlayersLabel = findViewById(R.id.onlinePlayersLabel)
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
        onlineMapVoteTitle = findViewById(R.id.onlineMapVoteTitle)
        mapVoteCardsRow = findViewById(R.id.mapVoteCardsRow)
        mapVoteResultHint = findViewById(R.id.mapVoteResultHint)
        onlinePlayersScroll = findViewById(R.id.onlinePlayersScroll)
        onlinePlayersContainer = findViewById(R.id.onlinePlayersContainer)
        onlineRoleCompositionPanel = findViewById(R.id.onlineRoleCompositionPanel)
        onlineRoleSectionTitle = findViewById(R.id.onlineRoleSectionTitle)
        onlineRoleBalance = findViewById(R.id.onlineRoleBalance)
        onlineRoleSummary = findViewById(R.id.onlineRoleSummary)
        onlineRolePresetRow = findViewById(R.id.onlineRolePresetRow)
        onlineRolePresetButtons = mapOf(
            RoleCompositionPreset.RECOMMENDED to findViewById(R.id.btnOnlineRoleRecommended),
            RoleCompositionPreset.CLASSIC to findViewById(R.id.btnOnlineRoleClassic),
            RoleCompositionPreset.CHAOTIC to findViewById(R.id.btnOnlineRoleChaotic)
        )
        btnConfigureOnlineRoles = findViewById(R.id.btnConfigureOnlineRoles)
        onlineRulesSummary = findViewById(R.id.onlineRulesSummary)
        lobbyOptionsRow = findViewById(R.id.lobbyOptionsRow)
        localPlayerControlsRow = findViewById(R.id.localPlayerControlsRow)
        lobbyConfigurationLabel = findViewById(R.id.lobbyConfigurationLabel)
        playersListPanel = findViewById(R.id.playersListPanel)
        lobbyPlayersLabel = findViewById(R.id.lobbyPlayersLabel)
        lobbyBodyScroll = findViewById(R.id.lobbyBodyScroll)
        lobbyStartDock = findViewById(R.id.lobbyStartDock)
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
                findViewById(R.id.mapPampaVoteVoters),
                findViewById(R.id.mapPampaDefaultBadge)
            ),
            "grecia" to MapVoteViews(
                findViewById(R.id.mapGreciaShade),
                findViewById(R.id.mapGreciaVoteOverlay),
                findViewById(R.id.mapGreciaVoteCount),
                findViewById(R.id.mapGreciaVoteVoters),
                findViewById(R.id.mapGreciaDefaultBadge)
            ),
            "medieval" to MapVoteViews(
                findViewById(R.id.mapMedievalShade),
                findViewById(R.id.mapMedievalVoteOverlay),
                findViewById(R.id.mapMedievalVoteCount),
                findViewById(R.id.mapMedievalVoteVoters),
                findViewById(R.id.mapMedievalDefaultBadge)
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
        timingOptionsButton.setOnClickListener { showTestOptionsDialog() }
        btnAdvancedOptions.setOnClickListener { showAdvancedOptionsDialog() }
        practiceRoleSummary.setOnClickListener {
            showPracticeRolePicker(practiceRoleIndex) { selectedIndex ->
                practiceRoleIndex = selectedIndex
                renderPracticeRoleSummary()
            }
        }
        btnCopyRoomCode.setOnClickListener { copyOnlineRoomCode() }
        btnShareRoomCode.setOnClickListener { shareOnlineRoomCode() }
        btnReleaseDisconnected.setOnClickListener { releaseDisconnectedOnlinePlayers() }
        btnDecreaseExpectedPlayers.setOnClickListener { updateOnlineExpectedPlayers(onlineExpectedPlayers - 1) }
        btnIncreaseExpectedPlayers.setOnClickListener { updateOnlineExpectedPlayers(onlineExpectedPlayers + 1) }
        btnPlayWithPresent.setOnClickListener { playOnlineWithPresentPlayers() }
        btnConfigureOnlineRoles.setOnClickListener { showOnlineRoleCompositionDialog() }
        onlineRolePresetButtons.forEach { (preset, button) ->
            button.setOnClickListener { applyOnlineRolePreset(preset) }
        }
        lobbyChatDock.setOnClickListener {
            showLobbyChatSheet()
        }
        btnToggleLobbyChat.setOnClickListener {
            setLobbyChatPreviewHidden(!isLobbyChatPreviewHidden())
        }

        updateOnlineControlState()

        btnAddPlayer.setOnClickListener {
            val previousPlayerCount = session.players.size
            val preferredBotName = LocalBotNameStore.nextAvailableName(this, session)
            val updated = LocalGameFactory.addMockPlayer(session, preferredBotName)
            if (updated.players.size == session.players.size) {
                Toast.makeText(this, "Maximo ${LocalGameFactory.MAX_PLAYERS} jugadores en esta demo.", Toast.LENGTH_SHORT).show()
            }
            session = PlayerProfileStore.withProfiles(
                this,
                LocalBotNameStore.apply(this, updated)
            )
            renderLobby()
            if (session.players.size > previousPlayerCount) {
                revealLastLocalPlayer()
            }
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
            val selectedRoleKey = practiceRoles[practiceRoleIndex].first
            val minimumPlayers = LocalGameFactory.minimumPlayersForRole(selectedRoleKey)
            val selectedRoleMap = RoleMap.fromSessionKey(session.mapKey)
            if (
                selectedRoleKey.isNotBlank() &&
                !RoleCatalog.isAvailableOnMap(selectedRoleKey, selectedRoleMap)
            ) {
                Toast.makeText(
                    this,
                    "${practiceRoles[practiceRoleIndex].second} no esta disponible en este mapa.",
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
        startButton.post { maybeShowFirstLobbyTutorial() }
    }

    private fun maybeShowFirstLobbyTutorial() {
        if (isFinishing || isDestroyed || TutorialDialog.hasBeenSeen(this)) return
        TutorialDialog.show(this, markAsSeen = true)
    }

    override fun onStart() {
        super.onStart()
        if (isFirestoreOnlineLobby()) {
            if (enteringOnlineMatch) {
                returnedFromOnlineMatch = true
                onlineRematchReactivationCompleted = false
            }
            enteringOnlineMatch = false
            lobbyRealtimeAccessReady = false
            startRealtimePresence()
            startRealtimeLobbySync()
            markOnlinePresence(PLAYER_STATE_CONNECTED)
            listenToOnlineRoom()
            listenToOnlinePlayers()
            listenToOwnOnlineMembership()
            listenToOwnRoomBan()
        }
    }

    override fun onStop() {
        if (isFirestoreOnlineLobby()) {
            lobbyRealtimeAccessReady = false
            lobbyChatController?.stop()
            val shouldStopRealtimeAsDisconnected = !enteringOnlineMatch &&
                (leavingOnlineLobby || onlineRemovalHandled || isFinishing)
            val shouldWriteLegacyDisconnected = shouldStopRealtimeAsDisconnected && !onlineRemovalHandled
            realtimePresence?.stop(markDisconnected = shouldStopRealtimeAsDisconnected)
            realtimePresence = null
            realtimeGameplaySync?.stop()
            realtimeGameplaySync = null
            if (shouldWriteLegacyDisconnected) {
                markOnlinePresence(PLAYER_STATE_DISCONNECTED)
            }
        }
        roomListener?.remove()
        playersListener?.remove()
        ownPlayerListener?.remove()
        ownRoomBanListener?.remove()
        lobbyChatController?.stop()
        if (::startButton.isInitialized) {
            startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
            onlineEntryAckRunnable?.let(startButton::removeCallbacks)
            onlineMatchEntryRetryRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleTimeoutRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleRetryRunnable?.let(startButton::removeCallbacks)
            onlineRealtimeAccessRetryRunnable?.let(startButton::removeCallbacks)
            realtimeLobbySyncRestartRunnable?.let(startButton::removeCallbacks)
            lobbyReconnectGraceRefreshRunnable?.let(startButton::removeCallbacks)
            pendingOnlineRolePresetRunnable?.let(startButton::removeCallbacks)
        }
        onlineEntryAckRunnable = null
        onlineEntryAckInProgress = false
        onlineMatchEntryRetryRunnable = null
        onlinePrivateRoleTimeoutRunnable = null
        onlinePrivateRoleRetryRunnable = null
        onlineRealtimeAccessRetryRunnable = null
        onlineRealtimeAccessSyncInProgress = false
        onlinePrivateRoleListener?.remove()
        onlinePrivateRoleListener = null
        onlinePrivateRolesLoading = false
        onlinePrivateRoleLoadGeneration += 1
        realtimeLobbySyncRestartRunnable = null
        lobbyReconnectGraceRefreshRunnable = null
        pendingOnlineRolePresetRunnable = null
        pendingOnlineRolePreset = null
        onlineEntryReleaseTimeoutScheduled = false
        roomListener = null
        playersListener = null
        ownPlayerListener = null
        ownRoomBanListener = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }

    override fun onDestroy() {
        if (isFirestoreOnlineLobby()) {
            OnlineDebugLog.i(
                "firestore_usage lobby roomId=$onlinePartidaId uid=$onlineTempUid " +
                    firestoreUsage.summary()
            )
        }
        super.onDestroy()
    }

    private fun renderLobby() {
        updateOnlineControlState()
        val onlineLobby = isFirestoreOnlineLobby()
        playerCount.text = if (onlineLobby) {
            val connected = activeOnlinePlayers().count(::isOnlinePlayerAvailableForLobby)
            "$connected/$onlineExpectedPlayers conectados"
        } else {
            "${currentVisiblePlayerCount()}/${currentMaxPlayers()} jugadores"
        }
        if (onlineLobby) {
            val occupied = activeOnlinePlayers().size
            val connected = activeOnlinePlayers().count(::isOnlinePlayerAvailableForLobby)
            playerCount.contentDescription =
                "$connected jugadores conectados de $onlineExpectedPlayers lugares; " +
                    "$occupied lugares ocupados"
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
        onlinePlayersLabel.text = if (onlineLobby) {
            "JUGADORES"
        } else {
            getString(R.string.lobby_section_players)
        }
        onlineMapVoteTitle.text = "VOTACIÓN DE MAPA"
        onlineRoleSectionTitle.text = "ROLES DE LA PARTIDA"
        lobbyConfigurationLabel.text = if (onlineLobby) {
            "REGLAS"
        } else {
            getString(R.string.lobby_section_configuration)
        }
        onlineInviteLabel.text = if (onlineLobby) {
            "INVITAR · CÓDIGO DE SALA"
        } else {
            getString(R.string.lobby_section_invite)
        }
        arrangeLobbySections(onlineLobby)
        renderOnlineCodePanel()
        renderReleaseDisconnectedButton()
        renderOnlinePlayerTargetControls()
        renderLobbyStructure(onlineLobby)
        renderStartButtonState()
        val currentMap = displayedLobbyMap()
        selectedMapName.text = currentMap.name.uppercase()
        selectedMapRole.text = selectedMapRoleLabel(currentMap.key)
        selectedMapImage.setImageResource(currentMap.imageRes)
        lobbyMapBackground.setImageResource(currentMap.imageRes)
        mapCards.forEachIndexed { index, imageView ->
            val selected = LocalGameFactory.maps[index].key == currentMap.key
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
        renderOnlineRoleComposition()
        timingOptionsButton.text = "Opciones de partida"
        btnAdvancedOptions.text = if (onlineLobby) "Ver y editar reglas" else "Opciones avanzadas"
        renderPracticeRoleSummary()

        val visibleOnlinePlayers = activeOnlinePlayers()
        val onlineSlotCount = if (onlineLobby) onlineExpectedPlayers else session.players.size
        val onlineChipWidth = if (onlineSlotCount <= 0) {
            dp(64)
        } else {
            val availableWidth = resources.displayMetrics.widthPixels - dp(64)
            val totalGaps = dp(5) * (onlineSlotCount - 1).coerceAtLeast(0)
            ((availableWidth - totalGaps) / onlineSlotCount).coerceIn(dp(62), dp(70))
        }
        // Keep the strip swipeable but never enable the framework scrollbar at runtime.
        // Samsung Android 16 crashes in View.onDrawScrollBars when a view inflated with
        // scrollbars="none" is enabled later because its ScrollBarDrawable remains null.
        onlinePlayersScroll.isHorizontalScrollBarEnabled =
            OnlineLobbyPresentation.shouldShowNativePlayerScrollBar(onlineSlotCount)
        val preserveOnlinePlayerStrip = onlineLobby &&
            onlineCleanupPending &&
            onlinePlayersContainer.childCount > 0
        if (!preserveOnlinePlayerStrip) {
            playersContainer.removeAllViews()
            onlinePlayersContainer.removeAllViews()
            val displayedPlayerIndices = if (onlineLobby) {
                session.players.indices.sortedWith(
                    compareBy<Int> { index ->
                        if (visibleOnlinePlayers.getOrNull(index)?.id == onlineTempUid) 0 else 1
                    }.thenBy { index -> visibleOnlinePlayers.getOrNull(index)?.order ?: index }
                )
            } else {
                session.players.indices.toList()
            }
            displayedPlayerIndices.forEachIndexed { displayIndex, sourceIndex ->
                val player = session.players[sourceIndex]
                if (onlineLobby) {
                    onlinePlayersContainer.addView(
                        createOnlinePlayerChip(player, visibleOnlinePlayers.getOrNull(sourceIndex)),
                        LinearLayout.LayoutParams(onlineChipWidth, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                            if (displayIndex > 0) marginStart = dp(5)
                        }
                    )
                    return@forEachIndexed
                }
                val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
                val onlinePlayer = visibleOnlinePlayers.getOrNull(sourceIndex)
                row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
                row.findViewById<TextView>(R.id.playerName).apply {
                    text = player.name
                    if (!player.isHuman) {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_edit_pencil,
                            0
                        )
                        compoundDrawablePadding = dp(6)
                        isClickable = true
                        isFocusable = true
                        contentDescription = "Cambiar nombre de ${player.name}"
                        setOnClickListener { showLocalBotNameEditor(sourceIndex) }
                    }
                }
                row.findViewById<TextView>(R.id.playerStatus).text =
                    onlinePlayer?.statusLabel(onlineHostId.ifBlank { onlineActiveHostId })
                        ?: if (sourceIndex == 0) "Anfitrion" else "Bot"
                row.findViewById<ImageButton>(R.id.btnPlayerProfile).setOnClickListener {
                    showPlayerProfile(player, onlinePlayer)
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
                        sourceIndex != 0 && !isOnlineGuest()
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
                        sourceIndex == 0 -> "El anfitrion no se puede expulsar"
                        else -> "Expulsar a ${player.name}"
                    }
                    setOnClickListener { confirmPlayerRemoval(sourceIndex, player, onlinePlayer) }
                }
                playersContainer.addView(row)
            }
            if (onlineLobby) {
                val occupiedSlots = visibleOnlinePlayers.size.coerceAtMost(onlineExpectedPlayers)
                repeat(OnlineLobbyPresentation.emptySlotCount(onlineExpectedPlayers, occupiedSlots)) { emptyIndex ->
                    val slotIndex = occupiedSlots + emptyIndex
                    onlinePlayersContainer.addView(
                        createEmptyLobbySlotChip(),
                        LinearLayout.LayoutParams(
                            onlineChipWidth,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            if (slotIndex > 0) marginStart = dp(5)
                        }
                    )
                }
            }
        }
        renderLobbyChatDock()
        scheduleLobbyReconnectGraceRefresh()
    }

    private fun renderLobbyStructure(onlineLobby: Boolean) {
        val presentation = OnlineLobbyPresentation.structure(onlineLobby)
        selectedMapCard.visibility = presentation.selectedMapVisible.toVisibility()
        onlineMapVoteHeader.visibility = presentation.onlineMapVoteVisible.toVisibility()
        onlineInviteLabel.visibility = presentation.onlineSectionLabelsVisible.toVisibility()
        onlinePlayersLabel.visibility = presentation.onlineSectionLabelsVisible.toVisibility()
        mapVoteResultHint.visibility = presentation.onlineMapVoteVisible.toVisibility()
        mapDescription.visibility = presentation.mapDescriptionVisible.toVisibility()
        mapDescription.text = mapDescriptionFor(session.mapKey)
        onlinePlayersScroll.visibility = presentation.onlinePlayersVisible.toVisibility()
        playersListPanel.visibility = presentation.localPlayersVisible.toVisibility()
        lobbyPlayersLabel.visibility = presentation.localPlayersVisible.toVisibility()
        // El selector de rol es una herramienta de prueba: se mantiene únicamente dentro
        // de Opciones avanzadas para no ocupar ni distraer en el lobby normal.
        practiceRoleSummary.visibility = View.GONE
        mapVoteCardsRow.layoutParams = mapVoteCardsRow.layoutParams.apply {
            height = dp(presentation.mapVoteCardsHeightDp)
        }
        val bodyParams = lobbyBodyScroll.layoutParams as RelativeLayout.LayoutParams
        if (onlineLobby) {
            bodyParams.addRule(RelativeLayout.ABOVE, R.id.lobbyStartDock)
        } else {
            bodyParams.removeRule(RelativeLayout.ABOVE)
        }
        lobbyBodyScroll.layoutParams = bodyParams
    }

    private fun arrangeLobbySections(onlineLobby: Boolean) {
        val panel = findViewById<LinearLayout>(R.id.lobbyConfigurationPanel)
        val orderedViews = if (onlineLobby) {
            listOf(
                lobbyModeHint,
                onlinePlayersLabel,
                onlinePlayerTargetPanel,
                onlinePlayersScroll,
                onlineMapVoteHeader,
                mapVoteCardsRow,
                mapVoteResultHint,
                onlineRoleCompositionPanel,
                lobbyConfigurationLabel,
                onlineRulesSummary,
                lobbyOptionsRow,
                btnReleaseDisconnected,
                onlineInviteLabel,
                onlineCodePanel
            )
        } else {
            listOf(
                startButton,
                onlineStartProgress,
                lobbyModeHint,
                btnReleaseDisconnected,
                onlineInviteLabel,
                onlineCodePanel,
                onlinePlayersLabel,
                onlinePlayersScroll,
                onlineRoleCompositionPanel,
                lobbyConfigurationLabel,
                onlinePlayerTargetPanel,
                selectedMapCard,
                onlineMapVoteHeader,
                mapVoteCardsRow,
                mapVoteResultHint,
                mapDescription,
                lobbyOptionsRow,
                lobbyPlayersLabel,
                localPlayerControlsRow
            )
        }
        orderedViews.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            panel.addView(view)
        }
        if (onlineLobby) {
            lobbyStartDock.visibility = View.VISIBLE
            listOf(onlineStartProgress, startButton).forEach { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                lobbyStartDock.addView(view)
            }
        } else {
            lobbyStartDock.visibility = View.GONE
        }
    }

    private fun Boolean.toVisibility(): Int = if (this) View.VISIBLE else View.GONE

    private fun renderOnlineMapVoting() {
        if (!isFirestoreOnlineLobby()) {
            mapVoteViews.values.forEach { views ->
                views.shade.visibility = View.GONE
                views.overlay.visibility = View.GONE
                views.defaultBadge.visibility = View.GONE
            }
            return
        }
        val summary = OnlineMapVoteResolver.summarize(currentOnlineMapVotes())
        val displayedMap = displayedLobbyMap()
        val currentVote = currentOnlinePlayer()?.mapVote
        LocalGameFactory.maps.forEachIndexed { index, map ->
            val views = mapVoteViews.getValue(map.key)
            val count = summary.counts[map.key] ?: 0
            val initials = summary.voterInitials[map.key].orEmpty()
            val selectedByCurrentPlayer = currentVote == map.key
            val leading = map.key in summary.leaders
            val cardPresentation = OnlineLobbyPresentation.mapVoteCard(
                count = count,
                totalVotes = summary.totalVotes,
                isCurrentMap = map.key == displayedMap.key
            )
            views.shade.visibility = View.VISIBLE
            views.overlay.visibility = View.VISIBLE
            views.count.text = if (cardPresentation.showVotePrompt) {
                getString(R.string.lobby_map_vote_empty)
            } else {
                resources.getQuantityString(
                    R.plurals.lobby_map_vote_count,
                    cardPresentation.count,
                    cardPresentation.count
                )
            }
            views.voters.text = compactVoterInitials(initials)
            views.defaultBadge.visibility =
                if (cardPresentation.showDefaultBadge) View.VISIBLE else View.GONE
            (mapCards[index].parent as? FrameLayout)?.setBackgroundResource(
                if (selectedByCurrentPlayer || leading) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark
            )
        }
        mapVoteResultHint.text = when {
            summary.totalVotes == 0 ->
                "Sin votos: al iniciar se mantiene ${currentMap().name}."
            summary.uniqueLeader != null ->
                "${mapName(summary.uniqueLeader)} lidera la votación. " +
                    "El lobby ya muestra este mapa."
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

    private fun renderOnlineRoleComposition() {
        val online = isFirestoreOnlineLobby()
        onlineRoleCompositionPanel.visibility = if (online) View.VISIBLE else View.GONE
        onlineRulesSummary.visibility = if (online) View.VISIBLE else View.GONE
        if (!online) return

        val visiblePreset = pendingOnlineRolePreset ?: onlineLobbyConfig.rolePreset
        val visibleConfig = pendingOnlineRolePreset?.let { preset ->
            onlineLobbyConfig.copy(
                rolePreset = preset,
                roleComposition = LocalGameFactory.roleCompositionPreset(
                    onlineExpectedPlayers,
                    displayedLobbyMap().key,
                    preset
                )
            )
        } ?: onlineLobbyConfig
        val composition = visibleConfig.compositionFor(
            onlineExpectedPlayers,
            displayedLobbyMap().key
        )
        val counts = composition.counts
        onlineRoleSummary.text = LocalGameFactory.editableRoleKeys()
            .filter { (counts[it] ?: 0) > 0 }
            .joinToString(" · ") { roleKey ->
                "${counts.getValue(roleKey)} ${publicRoleLabel(roleKey, counts.getValue(roleKey))}"
            }
        val balance = RoleCompositionBalance.evaluate(onlineExpectedPlayers, counts)
        onlineRoleBalance.text = balance.label
        onlineRoleBalance.setTextColor(
            getColor(
                when (balance) {
                    RoleCompositionBalance.BALANCED -> R.color.accent_green
                    RoleCompositionBalance.TOWN_FAVORED -> R.color.accent_blue
                    RoleCompositionBalance.TRAITORS_FAVORED -> R.color.accent_red
                    RoleCompositionBalance.RISKY -> R.color.accent_purple
                }
            )
        )
        btnConfigureOnlineRoles.text = if (currentUserIsOnlineHost()) {
            "PERSONALIZAR ROLES"
        } else {
            "VER ROLES"
        }
        btnConfigureOnlineRoles.contentDescription =
            "${btnConfigureOnlineRoles.text}. ${balance.label}. ${onlineRoleSummary.text}"
        val canEditPreset = currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineInitialMatchCreated &&
            !onlineCleanupPending
        onlineRolePresetRow.visibility = View.VISIBLE
        onlineRolePresetButtons.forEach { (preset, button) ->
            val selected = visiblePreset == preset
            button.setBackgroundResource(
                if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
            )
            button.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
            button.isEnabled = canEditPreset
            button.alpha = when {
                selected -> 1f
                canEditPreset -> 0.86f
                else -> 0.55f
            }
        }
        onlineRulesSummary.visibility = View.VISIBLE
        onlineRulesSummary.text = buildString {
            append("Roles al morir: ")
            append(if (onlineLobbyConfig.revealRolesOnDeath) "SÍ" else "NO")
            append("  ·  Votos: ")
            append(if (onlineLobbyConfig.showIndividualVotes) "INDIVIDUALES" else "TOTALES")
            append("  ·  Ritmo: ")
            append(onlineLobbyConfig.timing.preset()?.label ?: "PERSONALIZADO")
        }
    }

    private fun applyOnlineRolePreset(preset: RoleCompositionPreset) {
        val canEdit = currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineInitialMatchCreated &&
            !onlineCleanupPending
        if (!canEdit) {
            GameNotice.show(this, "Solo el anfitrión puede cambiar los roles antes de iniciar.")
            return
        }
        if (pendingOnlineRolePreset == null && onlineLobbyConfig.rolePreset == preset) return
        pendingOnlineRolePreset = preset
        pendingOnlineRolePresetRunnable?.let(startButton::removeCallbacks)
        renderOnlineRoleComposition()
        pendingOnlineRolePresetRunnable = Runnable {
            val selectedPreset = pendingOnlineRolePreset ?: return@Runnable
            pendingOnlineRolePresetRunnable = null
            val counts = LocalGameFactory.roleCompositionPreset(
                onlineExpectedPlayers,
                displayedLobbyMap().key,
                selectedPreset
            ).counts
            saveOnlineRoleComposition(selectedPreset, counts)
        }.also { runnable ->
            startButton.postDelayed(runnable, ROLE_PRESET_SAVE_DELAY_MS)
        }
    }

    private fun publicRoleLabel(roleKey: String, count: Int): String {
        val singular = roleLabel(roleKey).lowercase().replaceFirstChar { it.uppercase() }
        if (count == 1) return singular
        return when (roleKey) {
            RoleCatalog.ALDEANO -> "Aldeanos"
            RoleCatalog.POLICIA -> if (displayedLobbyMap().key == "pampa") "Comisarios" else "Detectives"
            RoleCatalog.MEDICO -> "Médicos"
            RoleCatalog.ALCALDE -> "Alcaldes"
            RoleCatalog.ASESINO -> "Asesinos"
            RoleCatalog.MERCENARIO -> "Mercenarios"
            RoleCatalog.ESPIA -> "Espías"
            RoleCatalog.DESERTOR -> "Desertores"
            RoleCatalog.PAYADOR -> "Payadores"
            RoleCatalog.ORACULO -> "Oráculos"
            RoleCatalog.BUFON -> "Bufones"
            else -> singular
        }
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
        val isCurrentPlayer = onlinePlayer?.id == onlineTempUid
        val cosmeticTheme = CosmeticPilot.normalizeTheme(onlinePlayer?.profile?.cosmeticThemeId)
            ?: CosmeticPilot.THEME_CLASSIC
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(
                if (isCurrentPlayer) R.drawable.bg_lobby_player_self else R.drawable.bg_btn_dark
            )
            if (isCurrentPlayer) elevation = dp(4).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
            contentDescription = "Ver perfil de ${player.name}"
            val avatarEntry = ProfileRoleCatalog.find(
                onlinePlayer?.profile?.avatarKey.orEmpty().ifBlank { "aldeana" }
            )
            addView(FrameLayout(this@LobbyActivity).apply {
                background = when {
                    onlinePlayer?.id == onlineActiveHostId ->
                        getDrawable(R.drawable.bg_profile_avatar_frame)
                    decorated -> CosmeticPilot.avatarFrame(this@LobbyActivity, cosmeticTheme)
                    else -> getDrawable(R.drawable.bg_player_avatar)
                }
                addView(CircleProfileImageView(this@LobbyActivity).apply {
                    val resId = resources.getIdentifier(
                        avatarEntry.role.imageResName,
                        "drawable",
                        packageName
                    ).takeIf { it != 0 } ?: R.drawable.placeholder_local
                    val showingPlayGamesPhoto = PlayGamesProfileAvatar.render(
                        context = this@LobbyActivity,
                        image = this,
                        uriValue = onlinePlayer?.profile?.playGamesAvatarUri.orEmpty(),
                        fallbackDrawableRes = resId
                    )
                    if (!showingPlayGamesPhoto) {
                        scaleType = ImageView.ScaleType.MATRIX
                        setImageResource(resId)
                        alignLobbyAvatarToFocus(this, avatarEntry.verticalFocus)
                    }
                    alpha = when {
                        onlinePlayer == null || isOnlinePlayerConnected(onlinePlayer) -> 1f
                        isOnlinePlayerAvailableForLobby(onlinePlayer) -> 0.72f
                        else -> 0.4f
                    }
                    contentDescription = if (showingPlayGamesPhoto) {
                        "Foto de Play Juegos de ${player.name}"
                    } else {
                        "Avatar ilustrado de ${player.name}"
                    }
                }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
                if (onlinePlayer?.ready == true) {
                    addView(TextView(this@LobbyActivity).apply {
                        text = "✓"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setBackgroundColor(getColor(R.color.accent_green))
                        textSize = 9f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }, FrameLayout.LayoutParams(dp(15), dp(15), Gravity.BOTTOM or Gravity.END))
                }
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(TextView(this@LobbyActivity).apply {
                text = if (isCurrentPlayer) "VOS" else player.name
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(
                    when {
                        decorated -> CosmeticPilot.accentColor(cosmeticTheme)
                        isCurrentPlayer -> getColor(R.color.accent_gold)
                        else -> getColor(R.color.text_secondary)
                    }
                )
                textSize = 10f
                if (isCurrentPlayer) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))
            setOnClickListener { showPlayerProfile(player, onlinePlayer) }
            if (onlinePlayer != null && canCurrentHostRemoveOnlinePlayer(onlinePlayer)) {
                setOnLongClickListener {
                    confirmPlayerRemoval(session.players.indexOf(player), player, onlinePlayer)
                    true
                }
            }
        }
    }

    private fun createEmptyLobbySlotChip(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_lobby_empty_slot)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = getString(R.string.lobby_empty_slot_description)
            addView(TextView(this@LobbyActivity).apply {
                text = "+"
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_muted))
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(this@LobbyActivity).apply {
                text = getString(R.string.lobby_empty_slot)
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_muted))
                textSize = 10f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))
        }
    }

    private fun startLobbyChat() {
        if (
            !isFirestoreOnlineLobby() ||
            onlineRoomState != ONLINE_ROOM_STATE_WAITING ||
            onlinePartidaId.isBlank() ||
            onlineTempUid.isBlank()
        ) {
            lobbyChatController?.stop()
            return
        }
        if (lobbyChatController == null) {
            lobbyChatController = LobbyChatController(
                database = FirebaseDatabase.getInstance(),
                roomId = onlinePartidaId,
                actorId = onlineTempUid,
                speaker = onlinePlayerName,
                onMessagesChanged = { messages ->
                    playLobbyEmoteSoundForNewMessages(
                        messages.filterNot { isLobbyActorLocallyMuted(it.actorId) }
                    )
                    lobbyChatMessages = messages
                    renderLobbyChatDock()
                    lobbyChatExpandedMessages?.let(::renderLobbyChatMessages)
                },
                onError = { error ->
                    OnlineDebugLog.e("lobby_chat_failure roomId=$onlinePartidaId uid=$onlineTempUid", error)
                },
                onRateLimited = { remainingMs ->
                    val seconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(1L)
                    Toast.makeText(
                        this,
                        "Espera ${seconds}s antes de volver a enviar.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onAccessCancelled = {
                    lobbyRealtimeAccessReady = false
                    realtimePresence?.refresh()
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
        lobbyChatActionText.text = "Escribí un mensaje..."
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
            .filterNot { message ->
                !message.isSystem && isLobbyActorLocallyMuted(message.actorId)
            }
            .sortedBy { it.createdAtLocal }
            .takeLast(LobbyChatController.MAX_MESSAGES)
    }

    private fun isLobbyActorLocallyMuted(actorId: String): Boolean {
        if (actorId.isBlank() || actorId == onlineTempUid) return false
        return LocalMuteStore.isMuted(this, publicId = "", uid = actorId)
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
        lobbyChatExpandedScroll = messageScroll
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
                if (!lobbyRealtimeAccessReady) {
                    Toast.makeText(
                        this@LobbyActivity,
                        "Reconectando el chat...",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
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
                setEmoteImageResource(emote.imageRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundResource(R.drawable.bg_btn_dark_ripple)
                contentDescription = "Enviar emote ${emote.tooltipText()}"
                androidx.appcompat.widget.TooltipCompat.setTooltipText(this, emote.tooltipText())
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener {
                    if (!lobbyRealtimeAccessReady) {
                        Toast.makeText(
                            this@LobbyActivity,
                            "Reconectando el chat...",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    lobbyChatController?.sendEmote(emote)
                }
            }, LinearLayout.LayoutParams(dp(62), dp(62)).apply {
                if (index > 0) marginStart = dp(9)
            })
        }
        content.addView(emoteRow)
        dialog.setContentView(content)
        dialog.setOnDismissListener {
            lobbyChatExpandedMessages = null
            lobbyChatExpandedScroll = null
        }
        dialog.show()
        renderLobbyChatMessages(messageContainer)
        input.post {
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
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
                    setEmoteImageResource(emote.imageRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = emote.tooltipText()
                    androidx.appcompat.widget.TooltipCompat.setTooltipText(this, emote.tooltipText())
                }, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(9) })
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
        if (container === lobbyChatExpandedMessages) {
            lobbyChatExpandedScroll?.post {
                lobbyChatExpandedScroll?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun listenToOnlineRoom() {
        roomListener?.remove()
        OnlineDebugLog.i("lobby_room_listen_start roomId=$onlinePartidaId uid=$onlineTempUid")
        firestoreUsage.listenerStarted("room")
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
                firestoreUsage.serverSnapshot(
                    name = "room",
                    fromCache = snapshot.metadata.isFromCache,
                    pendingWrites = snapshot.metadata.hasPendingWrites(),
                    changedDocuments = 1,
                    resultDocuments = 1,
                    dependentDocuments = if (
                        snapshot.getString(FIELD_STATE) == ONLINE_ROOM_STATE_WAITING
                    ) 0 else 1
                )
                applyOnlineRoomSnapshot(snapshot)
            }
    }

    private fun startRealtimePresence() {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank() || realtimePresence != null) return
        val presence = RealtimeRoomPresence(
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
            onOwnPresenceReady = {
                if (realtimePresence != null && !isFinishing) {
                    lobbyRealtimeAccessReady = true
                    startLobbyChat()
                }
            },
            onOwnPresenceUnavailable = {
                lobbyRealtimeAccessReady = false
                lobbyChatController?.stop()
            },
            onError = { error ->
                OnlineDebugLog.e(
                    "rtdb_lobby_presence_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                    error
                )
            }
        )
        realtimePresence = presence
        presence.start()
    }

    private fun ensureRealtimeLobbySync(): RealtimeGameplaySync? {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return null
        realtimeGameplaySync?.let { return it }
        val sync = RealtimeGameplaySync(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            uid = onlineTempUid,
            onClientStatesChanged = { states ->
                onlineClientStates = states
                if (::startButton.isInitialized && !isFinishing && !isDestroyed) {
                    coordinateOnlineMatchEntry()
                    maybeReleaseOnlineMatchEntry()
                    renderStartButtonState()
                }
            },
            onError = { error ->
                OnlineDebugLog.e(
                    "rtdb_lobby_sync_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                    error
                )
                realtimePresence?.refresh()
                scheduleRealtimeLobbySyncRestart()
            }
        )
        realtimeGameplaySync = sync
        return sync
    }

    private fun startRealtimeLobbySync() {
        ensureRealtimeLobbySync()?.start()
    }

    private fun scheduleRealtimeLobbySyncRestart() {
        if (!::startButton.isInitialized || realtimeLobbySyncRestartRunnable != null) return
        realtimeGameplaySync?.stop()
        realtimeGameplaySync = null
        onlineClientStates = emptyMap()
        val runnable = Runnable {
            realtimeLobbySyncRestartRunnable = null
            if (!isFinishing && !isDestroyed && isFirestoreOnlineLobby()) {
                startRealtimeLobbySync()
            }
        }
        realtimeLobbySyncRestartRunnable = runnable
        startButton.postDelayed(runnable, ONLINE_ENTRY_RETRY_MS)
    }

    private fun restartRealtimeLobbySyncNow() {
        if (!::startButton.isInitialized || isFinishing || isDestroyed) return
        realtimeLobbySyncRestartRunnable?.let(startButton::removeCallbacks)
        realtimeLobbySyncRestartRunnable = null
        realtimeGameplaySync?.stop()
        realtimeGameplaySync = null
        onlineClientStates = emptyMap()
        startRealtimeLobbySync()
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
            OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
            OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
        )
        playerData.putAll(
            PlayerPublicIdentity.publicProfileUpdateFields(this, publicId, onlinePlayerName)
        )
        if (currentlyReleased) {
            playerData[FIELD_PLAYER_READY] = false
        }
        firestoreUsage.write("presence")
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
            // El cierre intencional se resuelve antes de terminar la Activity. Nunca se debe
            // borrar la sala desde un callback de presencia: puede haber invitados conectados
            // que no sean candidatos estables a anfitrion y desaparecer sus documentos se
            // interpreta en esos clientes como una expulsion.
            OnlineDebugLog.w(
                "host_presence_disconnected_without_teardown roomId=$onlinePartidaId hostId=$onlineTempUid"
            )
        }
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
            if (startAfterSnapshot) renderStartButtonState()
            Toast.makeText(this, "Terminando de limpiar la partida anterior...", Toast.LENGTH_SHORT).show()
            return
        }
        onlineExpectedUpdateInProgress = true
        if (startAfterSnapshot) {
            pendingExpectedPlayersForStart = normalizedTarget
            startButton.isEnabled = false
            startButton.text = "AJUSTANDO..."
        }
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
            renderStartButtonState()
            OnlineDebugLog.e("lobby_expected_players_update_failure roomId=$onlinePartidaId", error)
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo cambiar la cantidad", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun playOnlineWithPresentPlayers() {
        if (!currentUserIsOnlineHost()) return
        startButton.isEnabled = false
        startButton.text = "VERIFICANDO..."
        refreshOnlinePlayersFromServer(
            reason = "play_with_present",
            onFailure = failure@{ error ->
                if (isFinishing || isDestroyed) return@failure
                startButton.isEnabled = true
                renderStartButtonState()
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo verificar a los jugadores", error),
                    Toast.LENGTH_LONG
                ).show()
            },
            onComplete = complete@{ serverPlayers ->
                if (!currentUserIsOnlineHost() || isFinishing || isDestroyed) return@complete
                val players = serverPlayers.filter { it.activeInMatch }
                val minimum = minimumOnlinePlayerLimit()
                if (players.size < minimum) {
                    startButton.isEnabled = true
                    renderStartButtonState()
                    Toast.makeText(
                        this,
                        "Faltan jugadores para el minimo de $minimum.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@complete
                }
                val unavailable = players.count { !isOnlinePlayerAvailableForLobby(it) || !it.ready }
                if (unavailable > 0) {
                    startButton.isEnabled = true
                    renderStartButtonState()
                    Toast.makeText(
                        this,
                        "Todavia faltan $unavailable jugador(es) listos.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@complete
                }
                if (players.size == onlineExpectedPlayers) {
                    startOnlineRoomTransaction(hostTieBreakChoice = null, serverPlayersAtStart = players)
                } else {
                    updateOnlineExpectedPlayers(players.size, startAfterSnapshot = true)
                }
            }
        )
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

    private fun canCurrentHostTransferTo(player: OnlineLobbyPlayer): Boolean {
        return canCurrentHostRemoveOnlinePlayer(player) &&
            isOnlinePlayerConnected(player) &&
            canBeLobbyHost(player) &&
            !onlineInitialMatchCreated &&
            !onlineHostHandoffInProgress
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
            Toast.makeText(
                this,
                "${player.name} fue expulsado de la sala.",
                Toast.LENGTH_SHORT
            ).show()
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
        if (
            onlineExitPreflightInProgress ||
            onlineExitInProgress ||
            onlineHostHandoffInProgress ||
            onlineRemovalHandled
        ) {
            return
        }
        onlineExitPreflightInProgress = true
        val roomReference = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
        roomReference.get(Source.SERVER)
            .addOnSuccessListener { roomSnapshot ->
                if (!roomSnapshot.exists()) {
                    onlineExitPreflightInProgress = false
                    handleDeletedOnlineRoom()
                    return@addOnSuccessListener
                }
                roomReference.collection(ONLINE_PLAYERS_COLLECTION)
                    .get(Source.SERVER)
                    .addOnSuccessListener { playersSnapshot ->
                        onlineExitPreflightInProgress = false
                        if (isFinishing || isDestroyed || onlineRemovalHandled) {
                            return@addOnSuccessListener
                        }
                        val verifiedPlayers = playersSnapshot.documents
                            .mapNotNull(::parseOnlinePlayer)
                            .sortedWith(
                                compareBy<OnlineLobbyPlayer> { it.order }
                                    .thenBy { it.name.lowercase() }
                                    .thenBy { it.id }
                            )
                        // La autoridad puede cambiar entre el último listener visible y el
                        // toque en Atrás. Decidir con datos de servidor evita que un anfitrión
                        // heredado intente salir por la rama de invitado y sea rechazado.
                        applyOnlineRoomSnapshot(roomSnapshot)
                        applyOnlinePlayersSnapshot(
                            updatedPlayers = verifiedPlayers,
                            source = "server_exit_preflight",
                            pendingWrites = false
                        )
                        if (!onlineRemovalHandled && !isFinishing && !isDestroyed) {
                            showVerifiedLobbyExitDialog()
                        }
                    }
                    .addOnFailureListener(::handleOnlineExitPreflightFailure)
            }
            .addOnFailureListener(::handleOnlineExitPreflightFailure)
    }

    private fun handleOnlineExitPreflightFailure(error: Exception) {
        onlineExitPreflightInProgress = false
        OnlineDebugLog.e(
            "lobby_exit_preflight_failure roomId=$onlinePartidaId uid=$onlineTempUid",
            error
        )
        if (isFinishing || isDestroyed || onlineRemovalHandled) return
        OnlineStabilityReport.recordEvent(this, "salida_verificacion_fallo", error.javaClass.simpleName)
        GameDialog.confirm(
            activity = this,
            title = "Firebase no respondió",
            message = "No pudimos confirmar el estado de la sala. Podés salir igualmente; " +
                "la app marcará tu presencia como desconectada y el resto podrá continuar.",
            positiveLabel = "SALIR IGUAL",
            negativeLabel = "QUEDARME"
        ) {
            forceLocalOnlineExit("preflight_failure")
        }
    }

    private fun showVerifiedLobbyExitDialog() {
        val liveOnlineMatch = onlineInitialMatchCreated &&
            onlineInitialMatch != null &&
            onlineMatchState != null &&
            (onlineMatchState?.get("ganador") as? String).orEmpty().isBlank()
        if (
            GameplayExitPolicy.shouldRecoverGameplayFromLobby(
                roomState = onlineRoomState,
                hasLiveMatch = liveOnlineMatch,
                returnedFromGameplay = returnedFromOnlineMatch
            )
        ) {
            showActiveMatchRecoveryChoice()
            return
        }
        val isHost = currentUserIsOnlineHost()
        if (
            GameplayExitPolicy.shouldOfferStartedMatchCancellation(
                roomState = onlineRoomState,
                hasLiveMatch = liveOnlineMatch,
                isHost = isHost,
                returnedFromGameplay = returnedFromOnlineMatch
            )
        ) {
            GameDialog.confirm(
                activity = this,
                title = "Cancelar inicio online",
                message = "La partida ya fue enviada, pero todavía no comenzó en todos los dispositivos. Si salís ahora, se cancelará para toda la sala.",
                positiveLabel = "CANCELAR PARTIDA"
            ) {
                cancelStartedOnlineMatchAndExit()
            }
            return
        }
        val handoffCandidate = if (isHost) onlineLobbyHostHandoffCandidate(excludeCurrent = true) else null
        val title = if (isHost) "Salir de la sala online" else "Salir del lobby"
        val message = if (isHost && handoffCandidate != null) {
            "Si salís, ${handoffCandidate.name} quedará como anfitrión activo y la sala continuará."
        } else if (isHost) {
            "No hay otro jugador con cuenta disponible para recibir el rol de anfitrión. " +
                "Si salís, la sala se cerrará para todos."
        } else {
            "¿Seguro que querés salir de esta sala? Si alguien ocupa tu lugar, vas a tener que esperar a que vuelva a quedar un cupo."
        }
        GameDialog.confirm(
            activity = this,
            title = title,
            message = message,
            positiveLabel = "SALIR"
        ) {
            if (isHost && handoffCandidate != null) {
                transferLobbyHost(handoffCandidate, exitAfterTransfer = true)
            } else if (isHost) {
                closeOnlineRoomAndExit()
            } else {
                releaseOwnOnlineSlotAndExit()
            }
        }
    }

    private fun showActiveMatchRecoveryChoice() {
        GameDialog.choose(
            activity = this,
            title = "PARTIDA EN CURSO",
            message = "La partida sigue activa. Podés recuperarla o salir de la sala de forma segura.",
            options = listOf("RECUPERAR PARTIDA", "SALIR DE LA SALA")
        ) { selected ->
            if (selected == 0) {
                recoveringOnlineMatch = true
                onlineStartedNoticeShown = false
                returnedFromOnlineMatch = false
                OnlineStabilityReport.recordEvent(this, "recuperacion_solicitada")
                GameNotice.show(this, "Recuperando partida…")
                ensurePrivateRolesLoaded()
            } else {
                forceLocalOnlineExit("user_exit_active_match")
            }
        }
    }

    /** Ultima salida de seguridad: no depende de una lectura previa de Firestore. */
    private fun forceLocalOnlineExit(reason: String) {
        if (leavingOnlineLobby || isFinishing || isDestroyed) return
        OnlineStabilityReport.recordEvent(this, "salida_local_segura", reason)
        onlineRemovalHandled = true
        leavingOnlineLobby = true
        realtimePresence?.setConnected(false)
        OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        startActivity(
            Intent(this, OnlineModeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    private fun closeOnlineRoomAndExit() {
        if (onlineExitInProgress || onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        onlineExitInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val hostReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlineTempUid)
        OnlineDebugLog.w(
            "lobby_host_close_requested roomId=$onlinePartidaId host=$onlineTempUid"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            val host = transaction.get(hostReference)
            if (!room.exists()) return@runTransaction false
            if (
                room.getString(FIELD_HOST_ID) != onlineTempUid &&
                room.getString(FIELD_ACTIVE_HOST_ID) != onlineTempUid
            ) {
                throw IllegalStateException("Ya no sos el anfitrión de esta sala.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no está esperando jugadores.")
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_ABANDONED,
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to 0,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            if (host.exists()) {
                transaction.update(
                    hostReference,
                    mapOf(
                        FIELD_ACTIVE_IN_MATCH to false,
                        FIELD_PLAYER_READY to false,
                        FIELD_PLAYER_STATE to PLAYER_STATE_DISCONNECTED,
                        OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp(),
                        OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis()
                    )
                )
            }
            true
        }.addOnSuccessListener {
            onlineExitInProgress = false
            onlineRemovalHandled = true
            leavingOnlineLobby = true
            realtimePresence?.setConnected(false)
            OnlineRoomRecovery.clearIf(this, onlinePartidaId)
            OnlineDebugLog.w(
                "lobby_host_close_success roomId=$onlinePartidaId host=$onlineTempUid"
            )
            finish()
        }.addOnFailureListener { error ->
            onlineExitInProgress = false
            OnlineDebugLog.e(
                "lobby_host_close_failure roomId=$onlinePartidaId host=$onlineTempUid",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo cerrar la sala", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun cancelStartedOnlineMatchAndExit() {
        if (
            onlineStartedMatchCancellationInProgress ||
            onlinePartidaId.isBlank() ||
            onlineTempUid.isBlank()
        ) {
            return
        }
        onlineStartedMatchCancellationInProgress = true
        val roomReference = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
        OnlineDebugLog.w(
            "online_started_match_cancel_requested roomId=$onlinePartidaId host=$onlineTempUid"
        )
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) throw IllegalStateException("La sala ya no existe.")
            if (room.getString(FIELD_ACTIVE_HOST_ID) != onlineTempUid) {
                throw IllegalStateException("Ya no sos el anfitrión activo.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_IN_GAME) {
                throw IllegalStateException("La partida ya cambió de estado.")
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_ABANDONED,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    "ultimaActividadOnline" to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener {
            onlineStartedMatchCancellationInProgress = false
            onlineRemovalHandled = true
            leavingOnlineLobby = true
            realtimePresence?.setConnected(false)
            OnlineRoomRecovery.clearIf(this, onlinePartidaId)
            OnlineDebugLog.w(
                "online_started_match_cancel_success roomId=$onlinePartidaId host=$onlineTempUid"
            )
            finish()
        }.addOnFailureListener { error ->
            onlineStartedMatchCancellationInProgress = false
            OnlineDebugLog.e(
                "online_started_match_cancel_failure roomId=$onlinePartidaId host=$onlineTempUid",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo cancelar la partida", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun alignLobbyAvatarToFocus(image: ImageView, verticalFocus: Float) {
        image.post {
            val drawable = image.drawable ?: return@post
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            if (drawableWidth <= 0f || drawableHeight <= 0f) return@post
            val scale = maxOf(
                image.width / drawableWidth,
                image.height / drawableHeight
            ) * 1.12f
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val horizontalOffset = (image.width - scaledWidth) / 2f
            val verticalOffset = (image.height / 2f - scaledHeight * verticalFocus.coerceIn(0f, 1f))
                .coerceIn(image.height - scaledHeight, 0f)
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(horizontalOffset, verticalOffset)
            }
        }
    }

    private fun releaseOwnOnlineSlotAndExit() {
        if (onlineExitInProgress || onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        onlineExitInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlineTempUid)
        OnlineDebugLog.i(
            "lobby_self_release_requested roomId=$onlinePartidaId uid=$onlineTempUid"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            val player = transaction.get(playerReference)
            if (!room.exists() || !player.exists()) return@runTransaction false
            if (player.getBoolean(FIELD_ACTIVE_IN_MATCH) == false) return@runTransaction false
            if (
                room.getString(FIELD_ACTIVE_HOST_ID) == onlineTempUid ||
                room.getString(FIELD_HOST_ID) == onlineTempUid
            ) {
                throw IllegalStateException(
                    "La sala cambió de anfitrión. Intentá salir nuevamente."
                )
            }

            transaction.update(
                playerReference,
                PlayerPublicIdentity.publicProfileUpdateFields(
                    this,
                    PlayerPublicIdentity.currentPublicId(this),
                    onlinePlayerName
                ) + mapOf(
                    FIELD_NAME to onlinePlayerName,
                    FIELD_ACTIVE_IN_MATCH to false,
                    FIELD_PLAYER_READY to false,
                    FIELD_PLAYER_STATE to PLAYER_STATE_DISCONNECTED,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                )
            )
            if (room.getString(FIELD_STATE) == ONLINE_ROOM_STATE_WAITING) {
                val currentPlayers = room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 1L
                transaction.update(
                    roomReference,
                    mapOf(
                        OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to
                            (currentPlayers - 1L).coerceAtLeast(0L),
                        OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                )
            }
            true
        }.addOnSuccessListener { released ->
            onlineExitInProgress = false
            onlineRemovalHandled = true
            leavingOnlineLobby = true
            realtimePresence?.setConnected(false)
            OnlineRoomRecovery.clearIf(this, onlinePartidaId)
            OnlineDebugLog.i(
                "lobby_self_release_success roomId=$onlinePartidaId uid=$onlineTempUid released=$released"
            )
            finish()
        }.addOnFailureListener { error ->
            onlineExitInProgress = false
            OnlineDebugLog.e(
                "lobby_self_release_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                error
            )
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo salir de la sala", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun listenToOnlinePlayers() {
        playersListener?.remove()
        OnlineDebugLog.i("lobby_players_listen_start roomId=$onlinePartidaId")
        firestoreUsage.listenerStarted("players")
        playersListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("lobby_players_listen_failure roomId=$onlinePartidaId", error)
                    verifyOwnMembershipAfterPlayersFailure(error)
                    return@addSnapshotListener
                }
                firestoreUsage.serverSnapshot(
                    name = "players",
                    fromCache = snapshot?.metadata?.isFromCache == true,
                    pendingWrites = snapshot?.metadata?.hasPendingWrites() == true,
                    changedDocuments = snapshot?.documentChanges?.size ?: 0,
                    resultDocuments = snapshot?.documents?.size ?: 0,
                    dependentDocuments = 1
                )
                val updatedPlayers = snapshot?.documents
                    ?.mapNotNull(::parseOnlinePlayer)
                    ?.sortedWith(
                        compareBy<OnlineLobbyPlayer> { it.order }
                            .thenBy { it.name.lowercase() }
                            .thenBy { it.id }
                    )
                    .orEmpty()
                applyOnlinePlayersSnapshot(
                    updatedPlayers = updatedPlayers,
                    source = if (snapshot?.metadata?.isFromCache == true) "cache" else "listener_server",
                    pendingWrites = snapshot?.metadata?.hasPendingWrites() == true
                )
            }
    }

    /**
     * La lista completa deja de estar autorizada apenas el anfitrión marca al jugador como
     * inactivo. Escuchar además el documento propio garantiza que el expulsado reciba el
     * cambio y no quede mirando un lobby obsoleto desde el que todas sus acciones fallan.
     */
    private fun listenToOwnOnlineMembership() {
        ownPlayerListener?.remove()
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        firestoreUsage.listenerStarted("own_membership")
        ownPlayerListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e(
                        "own_player_listener_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                        error
                    )
                    return@addSnapshotListener
                }
                // No decidir una expulsión con un miss de caché: al abrir el lobby todavía
                // puede no existir una copia local aunque el documento sí esté en el servidor.
                if (snapshot == null || snapshot.metadata.isFromCache) {
                    return@addSnapshotListener
                }
                firestoreUsage.serverSnapshot(
                    name = "own_membership",
                    fromCache = false,
                    pendingWrites = snapshot.metadata.hasPendingWrites(),
                    changedDocuments = if (snapshot.exists()) 1 else 0,
                    resultDocuments = if (snapshot.exists()) 1 else 0
                )
                applyOwnOnlineMembership(snapshot)
            }
    }

    private fun applyOwnOnlineMembership(snapshot: DocumentSnapshot?) {
        if (
            onlineRemovalHandled ||
            onlineExitInProgress ||
            onlineHostHandoffInProgress ||
            leavingOnlineLobby ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }
        val active = snapshot?.takeIf { it.exists() }
            ?.let { it.getBoolean(FIELD_ACTIVE_IN_MATCH) != false }
            ?: false
        if (active) {
            if (returnedFromOnlineMatch) {
                onlineRematchReactivationCompleted = true
                returnedFromOnlineMatch = false
            }
            return
        }
        val canRepairOwnSlot = returnedFromOnlineMatch ||
            onlineHostId == onlineTempUid ||
            (onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE)
        if (canRepairOwnSlot && !onlineRematchReactivationCompleted) {
            reactivateOwnOnlineSlot()
        } else {
            handleRemovedFromOnlineLobby()
        }
    }

    private fun verifyOwnMembershipAfterPlayersFailure(originalError: Exception) {
        if (onlineRemovalHandled || onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                firestoreUsage.forcedQuery("own_membership_recovery", resultDocuments = 1)
                if (!onlineRemovalHandled) {
                    val active = snapshot.takeIf { it.exists() }
                        ?.let { it.getBoolean(FIELD_ACTIVE_IN_MATCH) != false }
                        ?: false
                    if (!active) {
                        applyOwnOnlineMembership(snapshot)
                    } else {
                        showOnlinePlayersLoadError(originalError)
                    }
                }
            }
            .addOnFailureListener {
                if (!onlineRemovalHandled) showOnlinePlayersLoadError(originalError)
            }
    }

    private fun showOnlinePlayersLoadError(error: Exception) {
        if (onlineRemovalHandled || isFinishing || isDestroyed) return
        Toast.makeText(
            this,
            OnlineErrorMessages.forAction("Error cargando jugadores", error),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun applyOnlinePlayersSnapshot(
        updatedPlayers: List<OnlineLobbyPlayer>,
        source: String,
        pendingWrites: Boolean
    ) {
        if (!isFirestoreOnlineLobby() || isFinishing || isDestroyed) return
        trackLobbyPlayerNotices(onlinePlayers, updatedPlayers)
        onlinePlayers = updatedPlayers
        val ownPlayer = onlinePlayers.firstOrNull { it.id == onlineTempUid }
        if (ownPlayer?.activeInMatch == false) {
            if (onlineExitInProgress || leavingOnlineLobby) return
            val canRepairOwnSlot = returnedFromOnlineMatch ||
                onlineHostId == onlineTempUid ||
                (onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE)
            if (canRepairOwnSlot && !onlineRematchReactivationCompleted) {
                reactivateOwnOnlineSlot()
            } else {
                handleRemovedFromOnlineLobby()
            }
            return
        } else if (returnedFromOnlineMatch && ownPlayer?.activeInMatch == true) {
            onlineRematchReactivationCompleted = true
            returnedFromOnlineMatch = false
        }
        val visiblePlayers = activeOnlinePlayers()
        OnlineDebugLog.i(
            "lobby_players_snapshot roomId=$onlinePartidaId source=$source pending=$pendingWrites players=${onlinePlayers.size} active=${visiblePlayers.size} connected=${visiblePlayers.count(::isOnlinePlayerConnected)} ready=${visiblePlayers.count { it.ready }}"
        )
        session = PlayerProfileStore.withProfiles(this, session.copy(
            players = visiblePlayers.map { player ->
                GamePlayer(
                    name = player.name,
                    initial = player.initial,
                    isHuman = player.id == onlineTempUid,
                    control = if (player.id == onlineTempUid) {
                        PlayerControl.LOCAL
                    } else {
                        PlayerControl.REMOTE
                    }
                )
            },
            playerProfiles = visiblePlayers.associate { player -> player.name to player.profile }
        ))
        syncRealtimeLobbyAccess()
        coordinateOnlineMatchEntry()
        maybeClaimOnlineLobbyHostHandoff()
        maybeResetFinishedOnlineRoomForRematch()
        maybeContinuePendingOnlineCleanup()
        renderLobby()
    }

    private fun syncRealtimeLobbyAccess() {
        if (
            !isFirestoreOnlineLobby() ||
            onlineRoomState != ONLINE_ROOM_STATE_WAITING ||
            !currentUserIsOnlineHost()
        ) {
            return
        }
        val members = onlinePlayers
            .filter { it.activeInMatch }
            .associate { player ->
                player.id to RealtimeRoomMemberAccess(
                    name = player.name,
                    inLobby = true,
                    alive = true,
                    traitor = false
                )
            }
        if (members.isEmpty()) return
        RealtimeRoomAccess.syncMembers(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            hostUid = onlineTempUid,
            matchId = "",
            members = members,
            onFailure = { error ->
                OnlineDebugLog.e(
                    "rtdb_lobby_access_sync_failure roomId=$onlinePartidaId host=$onlineTempUid",
                    error
                )
            }
        )
    }

    private fun refreshOnlinePlayersFromServer(
        reason: String,
        onFailure: ((Exception) -> Unit)? = null,
        onComplete: ((List<OnlineLobbyPlayer>) -> Unit)? = null
    ) {
        if (onlinePlayersServerRefreshInProgress) {
            if (onComplete != null && ::startButton.isInitialized) {
                startButton.postDelayed(
                    { refreshOnlinePlayersFromServer(reason, onFailure, onComplete) },
                    PLAYERS_REFRESH_RETRY_MS
                )
            }
            return
        }
        if (onlinePartidaId.isBlank() || isFinishing || isDestroyed) {
            onFailure?.invoke(IllegalStateException("La sala ya no esta disponible."))
            return
        }
        onlinePlayersServerRefreshInProgress = true
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                onlinePlayersServerRefreshInProgress = false
                firestoreUsage.forcedQuery(
                    name = "players_$reason",
                    resultDocuments = snapshot.documents.size,
                    dependentDocuments = 1
                )
                val serverPlayers = snapshot.documents
                    .mapNotNull(::parseOnlinePlayer)
                    .sortedWith(
                        compareBy<OnlineLobbyPlayer> { it.order }
                            .thenBy { it.name.lowercase() }
                            .thenBy { it.id }
                    )
                applyOnlinePlayersSnapshot(
                    updatedPlayers = serverPlayers,
                    source = "server_$reason",
                    pendingWrites = false
                )
                onComplete?.invoke(serverPlayers)
            }
            .addOnFailureListener { error ->
                onlinePlayersServerRefreshInProgress = false
                OnlineDebugLog.e(
                    "lobby_players_server_refresh_failure roomId=$onlinePartidaId reason=$reason",
                    error
                )
                onFailure?.invoke(error)
            }
    }

    private fun reactivateOwnOnlineSlot() {
        if (onlineRematchReactivationInProgress || onlineRematchReactivationCompleted) return
        onlineRematchReactivationInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlineTempUid)
        OnlineDebugLog.w(
            "rematch_self_reactivation_requested roomId=$onlinePartidaId uid=$onlineTempUid"
        )
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            val player = transaction.get(playerReference)
            if (!room.exists() || !player.exists()) {
                throw IllegalStateException("La sala o el jugador ya no existen.")
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala todavía no está preparada para la revancha.")
            }
            if (player.getBoolean(FIELD_ACTIVE_IN_MATCH) != false) {
                return@runTransaction false
            }
            val currentPlayers = room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 0L
            val limit = room.getLong(FIELD_EXPECTED_PLAYERS)
                ?: room.getLong(FIELD_MAX_PLAYERS)
                ?: OnlineRoomFirestore.DEFAULT_MAX_PLAYERS.toLong()
            if (currentPlayers >= limit) {
                throw IllegalStateException("La sala volvió a llenarse.")
            }
            transaction.update(
                playerReference,
                PlayerPublicIdentity.publicProfileUpdateFields(
                    this,
                    PlayerPublicIdentity.currentPublicId(this),
                    onlinePlayerName
                ) + mapOf(
                    FIELD_NAME to onlinePlayerName,
                    FIELD_PLAYER_STATE to PLAYER_STATE_CONNECTED,
                    FIELD_PLAYER_READY to false,
                    OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to true,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.update(
                roomReference,
                mapOf(
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener { reactivated ->
            onlineRematchReactivationInProgress = false
            onlineRematchReactivationCompleted = true
            returnedFromOnlineMatch = false
            if (reactivated == true) {
                OnlineDebugLog.i(
                    "rematch_self_reactivation_success roomId=$onlinePartidaId uid=$onlineTempUid"
                )
            }
        }.addOnFailureListener { error ->
            onlineRematchReactivationInProgress = false
            OnlineDebugLog.e(
                "rematch_self_reactivation_failure roomId=$onlinePartidaId uid=$onlineTempUid",
                error
            )
            handleRemovedFromOnlineLobby()
        }
    }

    private fun listenToOwnRoomBan() {
        ownRoomBanListener?.remove()
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        firestoreUsage.listenerStarted("own_room_ban")
        ownRoomBanListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection("baneados")
            .document(onlineTempUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    OnlineDebugLog.e("own_room_ban_listener_failure roomId=$onlinePartidaId", error)
                    return@addSnapshotListener
                }
                firestoreUsage.serverSnapshot(
                    name = "own_room_ban",
                    fromCache = snapshot?.metadata?.isFromCache == true,
                    pendingWrites = snapshot?.metadata?.hasPendingWrites() == true,
                    changedDocuments = if (snapshot?.exists() == true) 1 else 0,
                    resultDocuments = if (snapshot?.exists() == true) 1 else 0
                )
                if (snapshot?.exists() != true || onlineRemovalHandled) return@addSnapshotListener
                showOnlineRemovalDialog(
                    snapshot.getString("motivo")
                        ?: "El anfitrión te expulsó de esta sala."
                )
            }
    }

    private fun applyOnlineRoomSnapshot(snapshot: DocumentSnapshot) {
        val previousActiveHostId = onlineActiveHostId
        val previousLobbyConfig = onlineLobbyConfig
        val previousRoomState = onlineRoomState
        onlineLobbyName = snapshot.getString(FIELD_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: onlineLobbyName
        onlineRoomState = snapshot.getString(FIELD_STATE) ?: ONLINE_ROOM_STATE_WAITING
        if (onlineRoomState == ONLINE_ROOM_STATE_WAITING) {
            if (
                OnlineLobbyEntryGate.shouldResetForWaitingLobby(
                    previousState = previousRoomState,
                    currentState = onlineRoomState
                )
            ) {
                resetOnlineMatchEntryForWaitingLobby()
            }
            // Recovering a waiting lobby is not recovering an active match. A new match started
            // from here must wait for every client to acknowledge its private role document.
            recoveringOnlineMatch = false
        } else {
            // Lobby-chat access intentionally closes when the match begins. Stop the listener
            // before RTDB cancels it so the presence callback cannot restart it in a loop.
            lobbyChatController?.stop()
        }
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
        val incomingMatchId = (onlineInitialMatch?.get("matchId") as? String).orEmpty()
        val roomMatchState = snapshot.get(FIELD_MATCH_STATE).asStringAnyMap()
        if (incomingMatchId != onlineMatchStateMatchId) {
            onlineMatchStateMatchId = incomingMatchId
            onlineMatchState = roomMatchState
            onlineCheckpointLoadInProgress = false
            onlineCheckpointLoadedMatchId = ""
        } else {
            onlineMatchState = OnlineAuthoritativeStateStore.freshest(
                onlineMatchState,
                roomMatchState
            )
        }
        onlineEntryReleasedMatchId = snapshot.getString(FIELD_ENTRY_RELEASED_MATCH_ID).orEmpty()
        onlineRoomSnapshotHasPendingWrites = snapshot.metadata.hasPendingWrites()
        OnlineStabilityReport.beginRoom(
            context = this,
            roomCode = onlineRoomCode,
            matchId = (onlineInitialMatch?.get("matchId") as? String).orEmpty(),
            isHost = currentUserIsOnlineHost(),
            expectedPlayers = onlineExpectedPlayers
        )
        session = session.copy(
            timingConfig = onlineLobbyConfig.timing,
            revealRolesOnDeath = onlineLobbyConfig.revealRolesOnDeath,
            showIndividualVotes = onlineLobbyConfig.showIndividualVotes,
            roleComposition = onlineLobbyConfig.compositionFor(
                onlineExpectedPlayers,
                snapshot.getString(FIELD_MAP_KEY).orEmpty().ifBlank { session.mapKey }
            )
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
        if (
            onlineRoomState == ONLINE_ROOM_STATE_ABANDONED &&
            !onlineStartedMatchCancellationInProgress
        ) {
            showAbandonedOnlineRoomNotice()
            return
        }

        val liveOnlineMatch = onlineInitialMatchCreated &&
            onlineInitialMatch != null &&
            onlineMatchState != null &&
            (onlineMatchState?.get("ganador") as? String).orEmpty().isBlank()
        if (
            GameplayExitPolicy.shouldRecoverGameplayFromLobby(
                roomState = onlineRoomState,
                hasLiveMatch = liveOnlineMatch,
                returnedFromGameplay = returnedFromOnlineMatch
            )
        ) {
            // El lobby queda debajo del gameplay para reutilizarlo al terminar. Si Android o
            // una salida accidental revelan esa pantalla mientras la partida sigue viva, no
            // debe comportarse como un lobby normal ni liberar el lugar del jugador.
            recoveringOnlineMatch = true
            onlineStartedNoticeShown = false
            returnedFromOnlineMatch = false
            OnlineDebugLog.w(
                "lobby_unexpected_gameplay_return roomId=$onlinePartidaId uid=$onlineTempUid"
            )
        }
        maybeResetFinishedOnlineRoomForRematch()
        maybeContinuePendingOnlineCleanup()

        if (
            onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
            liveOnlineMatch &&
            !onlineStartedNoticeShown
        ) {
            ensurePrivateRolesLoaded()
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
            playGamesAvatarUri = PlayGamesProfileAvatar.normalize(
                document.getString(PlayerPublicIdentity.FIELD_PROFILE_PLAY_GAMES_AVATAR).orEmpty()
            ),
            bannerKey = ProfileCustomizationCatalog.normalizeBannerKey(bannerKey),
            favoriteRoleKey = ProfileRoleCatalog.find(favoriteRoleKey).key,
            featuredAchievementIds = emptyList(),
            emoteIds = emptyList(),
            stats = PlayerStats(matches = 0, wins = 0, hasProgress = false),
            cosmeticThemeId = CosmeticPilot.normalizeTheme(
                document.getString(PlayerPublicIdentity.FIELD_PROFILE_COSMETIC_THEME)
            ) ?: CosmeticPilot.THEME_CLASSIC
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

    private fun isOnlinePlayerAvailableForLobby(
        player: OnlineLobbyPlayer,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return OnlineLobbyRules.countsAsPresentDuringReconnect(
            connected = isOnlinePlayerConnected(player),
            ready = player.ready,
            activeInMatch = player.activeInMatch,
            lastSeenMs = onlinePlayerLastSeenMs(player),
            nowMs = nowMs,
            graceMs = LOBBY_PLAYER_RECONNECT_GRACE_MS
        )
    }

    private fun onlineParticipantsForLobbyStart(
        nowMs: Long = System.currentTimeMillis()
    ): List<OnlineLobbyParticipant> {
        return onlineParticipants().map { participant ->
            participant.copy(
                connected = OnlineLobbyRules.countsAsPresentDuringReconnect(
                    connected = participant.connected,
                    ready = participant.ready,
                    activeInMatch = participant.activeInMatch,
                    lastSeenMs = participant.lastSeenLocalMs,
                    nowMs = nowMs,
                    graceMs = LOBBY_PLAYER_RECONNECT_GRACE_MS
                )
            )
        }
    }

    private fun scheduleLobbyReconnectGraceRefresh(nowMs: Long = System.currentTimeMillis()) {
        if (!::startButton.isInitialized) return
        lobbyReconnectGraceRefreshRunnable?.let(startButton::removeCallbacks)
        lobbyReconnectGraceRefreshRunnable = null
        if (!isFirestoreOnlineLobby()) return

        val nextExpiryMs = activeOnlinePlayers()
            .asSequence()
            .filter { !isOnlinePlayerConnected(it) && it.ready }
            .map { player ->
                LOBBY_PLAYER_RECONNECT_GRACE_MS -
                    (nowMs - onlinePlayerLastSeenMs(player)).coerceAtLeast(0L)
            }
            .filter { it > 0L }
            .minOrNull()
            ?: return
        val runnable = Runnable {
            lobbyReconnectGraceRefreshRunnable = null
            if (!isFinishing && !isDestroyed) renderLobby()
        }
        lobbyReconnectGraceRefreshRunnable = runnable
        startButton.postDelayed(runnable, nextExpiryMs + 100L)
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
        val reconnectingIds = activeOnlinePlayers()
            .filter { !isOnlinePlayerConnected(it) && isOnlinePlayerAvailableForLobby(it) }
            .map { it.id }
        val protectedIds = (setOf(onlineTempUid, onlineHostId, onlineActiveHostId) + reconnectingIds)
            .filter { it.isNotBlank() }
            .toSet()
        val releasableIds = OnlineLobbyRules.releasableDisconnectedPlayers(
            onlineParticipants(),
            protectedPlayerIds = protectedIds
        )
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
        showOnlineRemovalDialog("El anfitrión te expulsó de esta sala.")
    }

    private fun showOnlineRemovalDialog(message: String) {
        if (onlineRemovalHandled || isFinishing || isDestroyed) return
        onlineRemovalHandled = true
        leavingOnlineLobby = true
        OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        GameDialog.notice(
            activity = this,
            title = "Fuiste expulsado",
            message = message,
            positiveLabel = "VOLVER A JUGAR ONLINE",
            onPositive = { finish() }
        ).setCancelable(false)
    }

    private fun showAbandonedOnlineRoomNotice() {
        if (onlineRemovalHandled || isFinishing || isDestroyed) return
        onlineRemovalHandled = true
        leavingOnlineLobby = true
        realtimePresence?.setConnected(false)
        OnlineRoomRecovery.clearIf(this, onlinePartidaId)
        val cancelledStartedMatch = onlineInitialMatchCreated || onlineInitialMatch != null
        GameDialog.notice(
            activity = this,
            title = if (cancelledStartedMatch) "Partida cancelada" else "Sala cerrada",
            message = if (cancelledStartedMatch) {
                "El anfitrión canceló el inicio porque la sala no logró sincronizarse."
            } else {
                "El anfitrión salió y no había otra cuenta disponible para continuar la sala."
            },
            positiveLabel = "VOLVER A JUGAR ONLINE",
            onPositive = { finish() }
        ).setCancelable(false)
    }

    private fun onlineLobbyHint(): String {
        return when (onlineRoomState) {
            ONLINE_ROOM_STATE_IN_GAME -> getString(R.string.lobby_hint_online_in_game)
            else -> {
                val active = activeOnlinePlayers()
                val reconnecting = active.count {
                    !isOnlinePlayerConnected(it) && isOnlinePlayerAvailableForLobby(it)
                }
                val disconnected = active.count { !isOnlinePlayerAvailableForLobby(it) }
                val missing = (onlineExpectedPlayers - active.size).coerceAtLeast(0)
                when {
                    reconnecting == 1 ->
                        "1 jugador está reconectando. Conserva su lugar y no frena la sala."
                    reconnecting > 1 ->
                        "$reconnecting jugadores están reconectando. Conservan sus lugares."
                    disconnected == 1 ->
                        "1 jugador está desconectado. Su lugar queda reservado para que pueda volver."
                    disconnected > 1 ->
                        "$disconnected jugadores están desconectados. Sus lugares quedan reservados."
                    missing > 0 -> getString(R.string.lobby_hint_online_invite)
                    else -> getString(R.string.lobby_hint_online_ready)
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

    /**
     * El anfitrion de una sala tiene que tener cuenta: es la autoridad de la partida y quien
     * queda a cargo de moderar. El `publicId` solo lo tienen las cuentas registradas, asi que
     * alcanza con mirarlo. Las reglas de Firestore hacen valer lo mismo del lado del servidor
     * mirando los claims del token; esto es para no proponer un candidato que va a ser
     * rechazado.
     *
     * En el lobby el filtro es duro: si no queda ningun registrado conectado, no hay traspaso
     * y la sala se desarma, que es lo que ya pasaba cuando no quedaba nadie. En gameplay no se
     * puede ser tan estricto, porque dejaria la partida sin quien publique las fases.
     */
    private fun canBeLobbyHost(player: OnlineLobbyPlayer): Boolean {
        return player.publicId.isNotBlank()
    }

    private fun onlineLobbyHostFallbackId(): String {
        return activeOnlinePlayers()
            .asSequence()
            .filter { isOnlinePlayerConnected(it) && canBeLobbyHost(it) }
            .sortedWith(compareBy<OnlineLobbyPlayer> { it.order }.thenBy { it.id })
            .firstOrNull()
            ?.id
            .orEmpty()
    }

    private fun onlineLobbyHostHandoffCandidate(excludeCurrent: Boolean = false): OnlineLobbyPlayer? {
        if (excludeCurrent) {
            return activeOnlinePlayers()
                .filter { it.id != onlineTempUid && isOnlinePlayerConnected(it) && canBeLobbyHost(it) }
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
                syncRealtimeLobbyAccess()
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

    private fun transferLobbyHost(
        candidate: OnlineLobbyPlayer,
        exitAfterTransfer: Boolean
    ) {
        if (onlineHostHandoffInProgress) return
        onlineHostHandoffInProgress = true
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val candidateReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(candidate.id)
        val currentHostReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlineTempUid)
        OnlineDebugLog.w(
            "lobby_host_transfer_requested roomId=$onlinePartidaId previousHost=$onlineTempUid " +
                "candidate=${candidate.id} exit=$exitAfterTransfer"
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
            if (candidateSnapshot.getString(PlayerPublicIdentity.FIELD_PUBLIC_ID).isNullOrBlank()) {
                throw IllegalStateException("El nuevo anfitrión necesita una cuenta registrada.")
            }
            val currentPlayers = room.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 1L
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_HOST_ID to candidate.id,
                    FIELD_HOST_NAME to candidate.name,
                    FIELD_ACTIVE_HOST_ID to candidate.id,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to if (exitAfterTransfer) {
                        (currentPlayers - 1L).coerceAtLeast(1L)
                    } else {
                        currentPlayers
                    },
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            transaction.update(
                currentHostReference,
                if (exitAfterTransfer) {
                    mapOf(
                        FIELD_IS_HOST to false,
                        FIELD_ACTIVE_IN_MATCH to false,
                        FIELD_PLAYER_READY to false,
                        FIELD_PLAYER_STATE to PLAYER_STATE_DISCONNECTED,
                        OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp(),
                        OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis()
                    )
                } else {
                    mapOf(FIELD_IS_HOST to false)
                }
            )
            transaction.update(candidateReference, mapOf(FIELD_IS_HOST to true))
            true
        }.addOnSuccessListener {
            onlineHostHandoffInProgress = false
            onlineActiveHostId = candidate.id
            onlineHostId = candidate.id
            onlineHostVersion += 1
            if (exitAfterTransfer) {
                leavingOnlineLobby = true
                onlineRemovalHandled = true
                realtimePresence?.setConnected(false)
                OnlineRoomRecovery.clearIf(this, onlinePartidaId)
            }
            OnlineDebugLog.w(
                "lobby_host_transfer_success roomId=$onlinePartidaId previousHost=$onlineTempUid " +
                    "newHost=${candidate.id} exit=$exitAfterTransfer"
            )
            RealtimeRoomAccess.transferHost(
                database = FirebaseDatabase.getInstance(),
                roomId = onlinePartidaId,
                nextHostUid = candidate.id,
                onComplete = {
                    if (exitAfterTransfer) {
                        finish()
                    } else {
                        addLobbySystemNotice("${candidate.name} ahora es el anfitrión de la sala.")
                        GameNotice.show(
                            this,
                            "Le pasaste el rol de anfitrión a ${candidate.name}."
                        )
                        renderLobby()
                    }
                },
                onFailure = { error ->
                    OnlineDebugLog.e(
                        "rtdb_host_transfer_failure roomId=$onlinePartidaId newHost=${candidate.id}",
                        error
                    )
                    if (exitAfterTransfer) {
                        finish()
                    } else {
                        GameNotice.show(
                            this,
                            "El traspaso quedó guardado. Terminando de sincronizar la sala..."
                        )
                        renderLobby()
                    }
                }
            )
        }.addOnFailureListener { error ->
            onlineHostHandoffInProgress = false
            OnlineDebugLog.e(
                "lobby_host_transfer_failure roomId=$onlinePartidaId previousHost=$onlineTempUid " +
                    "candidate=${candidate.id} exit=$exitAfterTransfer",
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
        onlineStartProgress.visibility = View.GONE
        if (!isFirestoreOnlineLobby()) {
            startButton.setBackgroundResource(R.drawable.bg_btn_gold_ripple)
            startButton.setTextColor(getColor(R.color.bg_dark))
            startButton.isEnabled = !isOnlineGuest() && session.players.size >= LocalGameFactory.MIN_PLAYERS
            startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
            startButton.text = if (isOnlineGuest()) {
                getString(R.string.lobby_start_waiting_host)
            } else {
                getString(R.string.lobby_start_local)
            }
            startButton.contentDescription = when {
                isOnlineGuest() -> "Esperando al anfitrion para iniciar la partida"
                !startButton.isEnabled -> "Faltan jugadores para iniciar la partida"
                else -> "Iniciar partida"
            }
            return
        }

        startButton.setBackgroundResource(R.drawable.bg_btn_dark_ripple)
        startButton.setTextColor(getColor(R.color.text_primary))

        if (
            onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
            onlineInitialMatchCreated &&
            !onlineStartedNoticeShown
        ) {
            startButton.isEnabled = false
            startButton.alpha = 0.72f
            startButton.text = "PREPARANDO PARTIDA..."
            startButton.contentDescription = "Preparando la partida para todos los jugadores"
            return
        }

        val currentPlayer = currentOnlinePlayer()
        val currentReady = currentPlayer?.ready == true
        val canStart = currentUserIsOnlineHost() && onlineRoomCanStart()
        val activePlayers = activeOnlinePlayers()
        val missingPlayers = (onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)
        val disconnectedPlayers = activePlayers.count { !isOnlinePlayerAvailableForLobby(it) }
        val missingReady = activePlayers.count {
            isOnlinePlayerAvailableForLobby(it) && !it.ready
        }
        val canStartWithPresent = currentUserIsOnlineHost() &&
            activePlayers.size >= minimumOnlinePlayerLimit() &&
            missingPlayers > 0 &&
            disconnectedPlayers == 0 &&
            missingReady == 0 &&
            !onlineInitialMatchCreated
        val presentation = OnlineLobbyPresentation.startState(
            activePlayers = activePlayers.size,
            expectedPlayers = onlineExpectedPlayers,
            disconnectedPlayers = disconnectedPlayers,
            missingReady = missingReady,
            isHost = currentUserIsOnlineHost(),
            canStart = canStart,
            canStartWithPresent = canStartWithPresent,
            cleanupPending = onlineCleanupPending,
            initialMatchCreated = onlineInitialMatchCreated,
            currentReady = currentReady
        )
        presentation.progress?.let { progress ->
            onlineStartProgressBar.max = progress.total
            onlineStartProgressBar.progress = progress.current
            onlineStartProgressText.text = when (progress.kind) {
                OnlineLobbyProgressKind.PLAYERS -> getString(
                    R.string.lobby_start_progress_players,
                    progress.current,
                    progress.total
                )
                OnlineLobbyProgressKind.READY -> getString(
                    R.string.lobby_start_progress_ready,
                    progress.current,
                    progress.total
                )
            }
            onlineStartProgress.visibility = View.VISIBLE
        }
        if (presentation.isGold) {
            startButton.setBackgroundResource(R.drawable.bg_btn_gold_ripple)
            startButton.setTextColor(getColor(R.color.bg_dark))
        }
        startButton.isEnabled = onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineCleanupPending &&
            activePlayers.isNotEmpty() &&
            (canStart || currentPlayer != null)
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        startButton.text = when (presentation.buttonCopy) {
            OnlineLobbyStartCopy.CLEANING -> getString(R.string.lobby_start_cleaning)
            OnlineLobbyStartCopy.START_ONLINE -> getString(R.string.lobby_start_online)
            OnlineLobbyStartCopy.PLAY_WITH_PRESENT -> getString(
                R.string.lobby_start_with_present,
                activePlayers.size
            )
            OnlineLobbyStartCopy.WAITING -> getString(
                R.string.lobby_start_waiting,
                activePlayers.size,
                onlineExpectedPlayers
            )
            OnlineLobbyStartCopy.SYNCING -> getString(R.string.lobby_start_syncing)
            OnlineLobbyStartCopy.VERIFY_READY -> getString(R.string.lobby_start_verify_ready)
            OnlineLobbyStartCopy.NOT_READY -> getString(R.string.lobby_start_not_ready)
            OnlineLobbyStartCopy.HOST_READY -> getString(R.string.lobby_start_host_ready)
            OnlineLobbyStartCopy.READY -> getString(R.string.lobby_start_ready)
        }
        startButton.contentDescription = when {
            onlineCleanupPending -> "Preparando una nueva partida"
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
            activePlayers.all { isOnlinePlayerAvailableForLobby(it) && it.ready }
        val currentPlayerReady = currentOnlinePlayer()?.ready == true
        val hostShouldVerifyServerState = currentUserIsOnlineHost() &&
            currentPlayerReady &&
            activePlayers.size == onlineExpectedPlayers
        if (currentUserIsOnlineHost() && (onlineRoomCanStart() || hostShouldVerifyServerState)) {
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
        firestoreUsage.write("ready")
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(
                PlayerPublicIdentity.publicProfileUpdateFields(this, publicId, onlinePlayerName) + mapOf(
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
        if (onlineRoomState != ONLINE_ROOM_STATE_WAITING || onlineCleanupPending) {
            Toast.makeText(this, "La sala todavia no esta lista para iniciar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (onlineInitialMatchCreated || onlineInitialMatch != null) {
            OnlineDebugLog.w("online_start_skipped_existing_initial_match roomId=$onlinePartidaId hostId=$onlineTempUid")
            coordinateOnlineMatchEntry()
            return
        }
        startButton.isEnabled = false
        startButton.text = "VERIFICANDO..."
        refreshOnlinePlayersFromServer(
            reason = "start_preflight",
            onFailure = failure@{ error ->
                if (isFinishing || isDestroyed) return@failure
                startButton.isEnabled = true
                renderStartButtonState()
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo verificar a los jugadores", error),
                    Toast.LENGTH_LONG
                ).show()
            },
            onComplete = complete@{ serverPlayers ->
                if (!currentUserIsOnlineHost() || isFinishing || isDestroyed) {
                    return@complete
                }
                val activePlayers = serverPlayers.filter { it.activeInMatch }
                val problem = when {
                    activePlayers.size != onlineExpectedPlayers ->
                        "Faltan ${(onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)} jugadores para iniciar."
                    activePlayers.any { !isOnlinePlayerAvailableForLobby(it) } ->
                        "Hay jugadores desconectados o todavia sincronizando."
                    activePlayers.any { !it.ready } ->
                        "Todavia faltan ${activePlayers.count { !it.ready }} jugador(es) listos."
                    else -> null
                }
                if (problem != null) {
                    OnlineDebugLog.w(
                        "online_start_server_preflight_blocked roomId=$onlinePartidaId hostId=$onlineTempUid reason=$problem active=${activePlayers.size}/$onlineExpectedPlayers ready=${activePlayers.count { it.ready }}"
                    )
                    startButton.isEnabled = true
                    renderStartButtonState()
                    Toast.makeText(this, problem, Toast.LENGTH_SHORT).show()
                    return@complete
                }
                startOnlineRoomTransaction(hostTieBreakChoice, activePlayers)
            }
        )
    }

    private fun resetOnlineMatchEntryForWaitingLobby() {
        if (::startButton.isInitialized) {
            startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
            onlineEntryAckRunnable?.let(startButton::removeCallbacks)
            onlineMatchEntryRetryRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleTimeoutRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleRetryRunnable?.let(startButton::removeCallbacks)
            onlineRealtimeAccessRetryRunnable?.let(startButton::removeCallbacks)
            pendingOnlineRolePresetRunnable?.let(startButton::removeCallbacks)
        }
        onlineStartedNoticeShown = false
        onlinePrivateRoleAssignments = emptyList()
        onlinePrivateRolesMatchId = ""
        onlinePrivateRolesLoading = false
        onlinePrivateRoleLoadAttempt = 0
        onlinePrivateRoleLoadGeneration += 1
        onlinePrivateRoleAttemptMatchId = ""
        onlinePrivateRoleListener?.remove()
        onlinePrivateRoleListener = null
        onlinePrivateRoleTimeoutRunnable = null
        onlinePrivateRoleRetryRunnable = null
        onlineRealtimeAccessRetryRunnable = null
        onlineEntryBarrierMatchId = ""
        onlineEntryBarrierStartedAtMs = 0L
        onlineEntryAckMatchId = ""
        onlineEntryAckInProgress = false
        onlineEntryAckRunnable = null
        onlineEntryReleaseInProgress = false
        onlineEntryReleaseTimeoutScheduled = false
        onlineRealtimeAccessReadyMatchId = ""
        onlineRealtimeAccessSyncInProgress = false
        onlineRealtimeAccessRetryRunnable = null
        onlineMatchEntryRetryCount = 0
        onlineMatchEntryRetryMatchId = ""
        onlineMatchEntryRetryRunnable = null
        onlineInitialMatch = null
        onlineMatchState = null
        onlineMatchStateMatchId = ""
        onlineCheckpointLoadInProgress = false
        onlineCheckpointLoadedMatchId = ""
        pendingOnlineRolePresetRunnable = null
        pendingOnlineRolePreset = null
        lastOnlineMatchRebuildFailureReason = ""
        onlineStartTransactionInProgress = false
        OnlineDebugLog.i(
            "online_entry_reset_for_waiting_lobby roomId=$onlinePartidaId uid=$onlineTempUid"
        )
    }

    private fun startOnlineRoomTransaction(
        hostTieBreakChoice: String?,
        serverPlayersAtStart: List<OnlineLobbyPlayer>
    ) {
        if (onlineStartTransactionInProgress) {
            OnlineDebugLog.w(
                "online_start_duplicate_blocked roomId=$onlinePartidaId hostId=$onlineTempUid"
            )
            return
        }
        onlineStartTransactionInProgress = true
        startButton.text = "INICIANDO..."
        val onlineMatchId = UUID.randomUUID().toString()
        OnlineStabilityReport.updateMatch(this, onlineMatchId, true, onlineExpectedPlayers)
        OnlineStabilityReport.recordEvent(this, "inicio_solicitado")
        OnlineDebugLog.i(
            "online_start_requested roomId=$onlinePartidaId code=${onlineRoomCode.ifBlank { "-" }} hostId=$onlineTempUid expected=$onlineExpectedPlayers active=${serverPlayersAtStart.size} tieBreak=${hostTieBreakChoice ?: "-"}"
        )
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val playerReferences = serverPlayersAtStart
            .map { player ->
                roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id)
            }
        val transactionAttempts = AtomicInteger(0)
        firestore.runTransaction { transaction ->
            val attempt = transactionAttempts.incrementAndGet()
            OnlineDebugLog.i(
                "online_start_transaction_attempt roomId=$onlinePartidaId match=$onlineMatchId attempt=$attempt players=${serverPlayersAtStart.size}"
            )
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
            val activePlayersAtStart = playerReferences
                .map { reference -> transaction.get(reference) }
                .mapNotNull(::parseOnlinePlayer)
                .filter { it.activeInMatch }
                .sortedWith(
                    compareBy<OnlineLobbyPlayer> { it.order }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.id }
                )
            if (activePlayersAtStart.size != expectedPlayers) {
                throw IllegalStateException("Faltan jugadores para iniciar.")
            }
            if (activePlayersAtStart.any { !it.ready }) {
                throw IllegalStateException("Todavia faltan jugadores listos.")
            }
            val currentVotes = activePlayersAtStart.map { player ->
                OnlineMapVote(
                    playerId = player.id,
                    playerInitial = player.initial,
                    mapKey = player.mapVote
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
                buildOnlineBaseSession(selectedMapKey, roomConfig, activePlayersAtStart)
                    .copy(onlineMatchId = onlineMatchId)
            )
            val initialMatch = initialMatchPayload(assignedSession, activePlayersAtStart)
            val matchState = matchStatePayload(assignedSession)
            activePlayersAtStart.forEachIndexed { playerIndex, onlinePlayer ->
                // Una salida puede dejar huecos (0, 2, 3, 4) y el siguiente ingreso no
                // conoce todos los documentos para reservar ese hueco. Al iniciar, esta es
                // la lista autoritativa: normalizarla evita órdenes duplicados y mantiene
                // alineadas las acciones de cada rol con su UID.
                transaction.update(
                    roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(onlinePlayer.id),
                    FIELD_PLAYER_ORDER,
                    playerIndex
                )
                val ownRole = assignedSession.players[playerIndex].role
                    ?: throw IllegalStateException("El reparto quedó incompleto.")
                val visibleRoles = assignedSession.players.mapIndexedNotNull { index, candidate ->
                    val role = candidate.role ?: return@mapIndexedNotNull null
                    val visible = index == playerIndex ||
                        (
                            ownRole.team == GameRules.TRAITOR_WINNER &&
                                role.team == GameRules.TRAITOR_WINNER
                            )
                    if (!visible) return@mapIndexedNotNull null
                    roleAssignmentPayload(index, role)
                }
                transaction.set(
                    roomReference.collection("repartos").document(onlinePlayer.id),
                    mapOf(
                        "matchId" to onlineMatchId,
                        "uidTemporal" to onlinePlayer.id,
                        "rolesVisibles" to visibleRoles,
                        "creadaEn" to FieldValue.serverTimestamp()
                    )
                )
            }
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
            val realtimeAccess = activePlayersAtStart.mapIndexed { index, onlinePlayer ->
                val player = assignedSession.players[index]
                onlinePlayer.id to RealtimeRoomMemberAccess(
                    name = player.name,
                    inLobby = false,
                    alive = player.alive,
                    traitor = player.role?.team == GameRules.TRAITOR_WINNER
                )
            }.toMap()
            OnlineStartTransactionResult.Started(
                mapKey = selectedMap.key,
                roleSummary = onlineRoleSummary(assignedSession),
                matchId = onlineMatchId,
                realtimeAccess = realtimeAccess
            )
        }.addOnSuccessListener { result ->
            onlineStartTransactionInProgress = false
            when (result) {
                OnlineStartTransactionResult.AlreadyStarted -> {
                    OnlineStabilityReport.recordEvent(this, "inicio_ya_existente")
                    OnlineDebugLog.w(
                        "online_start_already_created roomId=$onlinePartidaId hostId=$onlineTempUid attempts=${transactionAttempts.get()}"
                    )
                    Toast.makeText(this, "La partida ya fue iniciada. Sincronizando...", Toast.LENGTH_SHORT).show()
                    coordinateOnlineMatchEntry()
                }
                is OnlineStartTransactionResult.MapTieBreakRequired -> {
                    startButton.isEnabled = true
                    renderStartButtonState()
                    showMapTieBreakDialog(result.mapKeys)
                }
                is OnlineStartTransactionResult.Started -> {
                    OnlineStabilityReport.recordEvent(this, "inicio_confirmado")
                    OnlineDebugLog.i(
                        "online_start_success roomId=$onlinePartidaId hostId=$onlineTempUid map=${result.mapKey} roles=${result.roleSummary} attempts=${transactionAttempts.get()}"
                    )
                    onlineRealtimeAccessReadyMatchId = ""
                    syncRealtimeMatchAccess(result.matchId, result.realtimeAccess)
                }
            }
        }.addOnFailureListener { error ->
            OnlineStabilityReport.recordEvent(this, "inicio_fallo", error.javaClass.simpleName)
            onlineStartTransactionInProgress = false
            OnlineDebugLog.e(
                "online_start_failure roomId=$onlinePartidaId hostId=$onlineTempUid attempts=${transactionAttempts.get()}",
                error
            )
            startButton.isEnabled = true
            renderStartButtonState()
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo iniciar la partida", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun realtimeAccessForSession(
        source: GameSession
    ): Map<String, RealtimeRoomMemberAccess> = source.players.mapIndexedNotNull { index, player ->
        val uid = source.onlinePlayerUids.getOrNull(index)?.takeIf(String::isNotBlank)
            ?: return@mapIndexedNotNull null
        uid to RealtimeRoomMemberAccess(
            name = player.name,
            inLobby = false,
            alive = player.alive,
            traitor = player.role?.team == GameRules.TRAITOR_WINNER
        )
    }.toMap()

    private fun syncRealtimeMatchAccess(
        matchId: String,
        members: Map<String, RealtimeRoomMemberAccess>
    ) {
        if (
            matchId.isBlank() ||
            members.isEmpty() ||
            onlineRealtimeAccessReadyMatchId == matchId ||
            onlineRealtimeAccessSyncInProgress ||
            !currentUserIsOnlineHost()
        ) {
            return
        }
        onlineRealtimeAccessRetryRunnable?.let(startButton::removeCallbacks)
        onlineRealtimeAccessRetryRunnable = null
        onlineRealtimeAccessSyncInProgress = true
        OnlineDebugLog.i(
            "rtdb_match_access_sync_requested roomId=$onlinePartidaId host=$onlineTempUid match=$matchId members=${members.size}"
        )
        RealtimeRoomAccess.syncMembers(
            database = FirebaseDatabase.getInstance(),
            roomId = onlinePartidaId,
            hostUid = onlineTempUid,
            matchId = matchId,
            members = members,
            onComplete = {
                onlineRealtimeAccessSyncInProgress = false
                onlineRealtimeAccessReadyMatchId = matchId
                OnlineDebugLog.i(
                    "rtdb_match_access_sync_success roomId=$onlinePartidaId host=$onlineTempUid match=$matchId"
                )
                if (::startButton.isInitialized && !isFinishing && !isDestroyed) {
                    startButton.post {
                        restartRealtimeLobbySyncNow()
                        onlineEntryAckRunnable?.let(startButton::removeCallbacks)
                        onlineEntryAckRunnable = null
                        onlineEntryAckInProgress = false
                        coordinateOnlineMatchEntry()
                    }
                }
            },
            onFailure = { error ->
                onlineRealtimeAccessSyncInProgress = false
                OnlineDebugLog.e(
                    "rtdb_match_access_sync_failure roomId=$onlinePartidaId host=$onlineTempUid match=$matchId",
                    error
                )
                if (
                    ::startButton.isInitialized &&
                    !isFinishing &&
                    !isDestroyed &&
                    onlineRoomState == ONLINE_ROOM_STATE_IN_GAME &&
                    onlineRealtimeAccessRetryRunnable == null
                ) {
                    val retry = Runnable {
                        onlineRealtimeAccessRetryRunnable = null
                        syncRealtimeMatchAccess(matchId, members)
                    }
                    onlineRealtimeAccessRetryRunnable = retry
                    startButton.postDelayed(retry, ONLINE_ENTRY_RETRY_MS)
                }
            }
        )
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
        val matchId = (onlineInitialMatch?.get("matchId") as? String).orEmpty()
        if (recoveringOnlineMatch && onlineCheckpointLoadedMatchId != matchId) {
            loadOnlineAuthoritativeCheckpoint(matchId)
            return
        }
        if (onlinePrivateRolesMatchId != matchId || onlinePrivateRoleAssignments.isEmpty()) {
            ensurePrivateRolesLoaded()
            return
        }
        val rebuiltSession = onlineInitialMatch?.let(::sessionFromInitialMatch)
        val entryProblem = rebuiltSession?.let(::onlineMatchEntryProblem)
        if (rebuiltSession == null || entryProblem != null) {
            if (lastOnlineMatchRebuildFailureReason == OnlineMatchSessionError.INCOMPATIBLE_STATE.name) {
                showIncompatibleOnlineRoomNotice()
                return
            }
            scheduleOnlineMatchEntryRetry(
                reason = entryProblem ?: lastOnlineMatchRebuildFailureReason.ifBlank {
                    "No se pudo reconstruir la partida compartida."
                }
            )
            return
        }
        val rebuiltMatchId = rebuiltSession.onlineMatchId
        if (rebuiltMatchId.isBlank()) {
            scheduleOnlineMatchEntryRetry("La partida compartida no incluye un identificador.")
            return
        }
        if (
            currentUserIsOnlineHost() &&
            onlineRealtimeAccessReadyMatchId != rebuiltMatchId
        ) {
            syncRealtimeMatchAccess(
                matchId = rebuiltMatchId,
                members = realtimeAccessForSession(rebuiltSession)
            )
            return
        }
        cancelOnlineMatchEntryRetry(resetAttempts = true)
        if (onlineEntryBarrierMatchId != rebuiltMatchId) {
            if (::startButton.isInitialized) {
                startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
                onlineEntryAckRunnable?.let(startButton::removeCallbacks)
            }
            onlineEntryAckRunnable = null
            onlineEntryReleaseTimeoutScheduled = false
            onlineEntryBarrierMatchId = rebuiltMatchId
            onlineEntryBarrierStartedAtMs = SystemClock.elapsedRealtime()
            onlineEntryAckMatchId = ""
            onlineEntryAckInProgress = false
            onlineEntryReleaseInProgress = false
        }
        if (
            OnlineLobbyEntryGate.isReleased(matchId, onlineEntryReleasedMatchId) &&
            !onlineRoomSnapshotHasPendingWrites
        ) {
            enterReleasedOnlineMatch(matchId)
            return
        }
        acknowledgeOnlineMatchEntry(matchId)
        maybeReleaseOnlineMatchEntry()
    }

    private fun loadOnlineAuthoritativeCheckpoint(matchId: String) {
        if (
            matchId.isBlank() ||
            onlineCheckpointLoadInProgress ||
            onlineCheckpointLoadedMatchId == matchId ||
            onlineRoomState != ONLINE_ROOM_STATE_IN_GAME
        ) {
            return
        }
        onlineCheckpointLoadInProgress = true
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(OnlineAuthoritativeStateStore.COLLECTION)
            .document(OnlineAuthoritativeStateStore.DOCUMENT)
            .get(Source.SERVER)
            .addOnSuccessListener { checkpoint ->
                onlineCheckpointLoadInProgress = false
                onlineCheckpointLoadedMatchId = matchId
                firestoreUsage.forcedQuery("authoritative_checkpoint_recovery", 1, 1)
                val checkpointState = OnlineAuthoritativeStateStore.checkpointState(
                    checkpoint = checkpoint.data,
                    expectedMatchId = matchId
                )
                onlineMatchState = OnlineAuthoritativeStateStore.freshestForRecovery(
                    onlineMatchState,
                    checkpointState
                )
                coordinateOnlineMatchEntry()
            }
            .addOnFailureListener { error ->
                onlineCheckpointLoadInProgress = false
                onlineCheckpointLoadedMatchId = matchId
                OnlineDebugLog.e(
                    "lobby_checkpoint_recovery_failure roomId=$onlinePartidaId match=$matchId",
                    error
                )
                coordinateOnlineMatchEntry()
            }
    }

    private fun scheduleOnlineMatchEntryRetry(reason: String) {
        if (onlineRoomSnapshotHasPendingWrites || !::startButton.isInitialized) return
        val matchId = (onlineInitialMatch?.get("matchId") as? String).orEmpty()
        if (onlineMatchEntryRetryMatchId != matchId) {
            cancelOnlineMatchEntryRetry(resetAttempts = true)
            onlineMatchEntryRetryMatchId = matchId
        }
        if (onlineMatchEntryRetryRunnable != null) return
        if (onlineMatchEntryRetryCount >= ONLINE_MATCH_ENTRY_MAX_RETRIES) {
            onlineStartedNoticeShown = true
            OnlineDebugLog.e(
                "online_match_corrupt_confirmed roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId attempts=$onlineMatchEntryRetryCount reason=$reason"
            )
            Toast.makeText(
                this,
                "La partida llego incompleta despues de varios intentos. Creen una sala nueva.",
                Toast.LENGTH_LONG
            ).show()
            OnlineRoomRecovery.clearIf(this, onlinePartidaId)
            finish()
            return
        }
        onlineMatchEntryRetryCount += 1
        OnlineStabilityReport.recordEvent(
            this,
            "entrada_reintentada",
            "intento_${onlineMatchEntryRetryCount}"
        )
        startButton.isEnabled = false
        startButton.text = "PREPARANDO PARTIDA..."
        if (onlineMatchEntryRetryCount == 1) {
            Toast.makeText(this, "Sincronizando datos de partida...", Toast.LENGTH_SHORT).show()
        }
        OnlineDebugLog.w(
            "online_match_entry_retry roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId attempt=$onlineMatchEntryRetryCount/$ONLINE_MATCH_ENTRY_MAX_RETRIES reason=$reason"
        )
        val runnable = Runnable {
            onlineMatchEntryRetryRunnable = null
            if (!isFinishing && !isDestroyed) {
                coordinateOnlineMatchEntry()
            }
        }
        onlineMatchEntryRetryRunnable = runnable
        startButton.postDelayed(runnable, ONLINE_ENTRY_RETRY_MS)
    }

    private fun cancelOnlineMatchEntryRetry(resetAttempts: Boolean) {
        if (::startButton.isInitialized) {
            onlineMatchEntryRetryRunnable?.let(startButton::removeCallbacks)
        }
        onlineMatchEntryRetryRunnable = null
        if (resetAttempts) {
            onlineMatchEntryRetryCount = 0
            onlineMatchEntryRetryMatchId = ""
        }
    }

    private fun acknowledgeOnlineMatchEntry(matchId: String) {
        if (activeOnlinePlayers().none { it.id == onlineTempUid }) return
        if (currentUserIsOnlineHost()) {
            if (onlineEntryAckMatchId == matchId) return
            onlineEntryAckRunnable?.let(startButton::removeCallbacks)
            onlineEntryAckRunnable = null
            onlineEntryAckInProgress = false
            onlineEntryAckMatchId = matchId
            OnlineDebugLog.i(
                "online_entry_ack_local_host roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
            )
            maybeReleaseOnlineMatchEntry()
            return
        }
        val acknowledgedIds = OnlineLobbyEntryGate.acknowledgedPlayerIds(matchId, onlineClientStates)
        if (onlineTempUid in acknowledgedIds) {
            onlineEntryAckRunnable?.let(startButton::removeCallbacks)
            onlineEntryAckRunnable = null
            onlineEntryAckInProgress = false
            onlineEntryAckMatchId = matchId
            return
        }
        if (
            !OnlineLobbyEntryGate.shouldPublishAcknowledgement(
                playerId = onlineTempUid,
                matchId = matchId,
                clientStates = onlineClientStates,
                publishInProgress = onlineEntryAckInProgress
            )
        ) {
            return
        }
        if (onlineEntryAckMatchId == matchId) {
            OnlineDebugLog.w(
                "online_entry_ack_missing_republish roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
            )
            onlineEntryAckMatchId = ""
        }
        onlineEntryAckInProgress = true
        val delayMs = Random.nextLong(ONLINE_ENTRY_ACK_JITTER_MAX_MS + 1L)
        val runnable = Runnable {
            onlineEntryAckRunnable = null
            if (
                onlineStartedNoticeShown ||
                onlineEntryBarrierMatchId != matchId ||
                isFinishing ||
                isDestroyed
            ) {
                onlineEntryAckInProgress = false
                return@Runnable
            }
            val currentAcknowledgedIds = OnlineLobbyEntryGate.acknowledgedPlayerIds(
                matchId,
                onlineClientStates
            )
            if (onlineTempUid in currentAcknowledgedIds) {
                onlineEntryAckInProgress = false
                onlineEntryAckMatchId = matchId
                return@Runnable
            }
            publishOnlineMatchEntryAck(matchId)
        }
        onlineEntryAckRunnable = runnable
        OnlineDebugLog.i(
            "online_entry_ack_scheduled roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId delayMs=$delayMs"
        )
        startButton.postDelayed(runnable, delayMs)
    }

    private fun publishOnlineMatchEntryAck(matchId: String) {
        val rosterSize = initialMatchPlayerIds().size
        OnlineDebugLog.i(
            "online_entry_ack_requested roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
        )
        val sync = ensureRealtimeLobbySync()
        if (sync == null) {
            onlineEntryAckInProgress = false
            scheduleOnlineMatchEntryRetry("No se pudo abrir la sincronización de entrada.")
            return
        }
        sync.publishClientState(
            mapOf(
                "fase" to GamePhase.REPARTO.name,
                "ronda" to 0,
                "phaseIndex" to 0,
                "enGameplay" to false,
                "jugadoresVistos" to rosterSize,
                "jugadoresEsperados" to rosterSize,
                "uidTemporal" to onlineTempUid,
                OnlineLobbyEntryGate.FIELD_MATCH_ID to matchId,
                OnlineLobbyEntryGate.FIELD_ENTRY_READY to true,
                "actualizadaEnLocal" to System.currentTimeMillis()
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
                    val retryRunnable = Runnable {
                        onlineEntryAckRunnable = null
                        coordinateOnlineMatchEntry()
                    }
                    onlineEntryAckRunnable = retryRunnable
                    startButton.postDelayed(retryRunnable, ONLINE_ENTRY_RETRY_MS)
                }
            }
    }

    private fun maybeReleaseOnlineMatchEntry() {
        if (
            onlineStartedNoticeShown ||
            onlineRoomState != ONLINE_ROOM_STATE_IN_GAME ||
            !currentUserIsOnlineHost() ||
            onlineRoomSnapshotHasPendingWrites ||
            onlineEntryReleaseInProgress
        ) {
            return
        }
        val matchId = onlineEntryBarrierMatchId
        if (matchId.isBlank() || onlineEntryReleasedMatchId == matchId) return
        val expectedPlayerIds = initialMatchPlayerIds()
        if (expectedPlayerIds.size != onlineExpectedPlayers) {
            scheduleOnlineEntryReleaseTimeout()
            return
        }
        val readyPlayerIds = OnlineLobbyEntryGate.readyPlayerIds(
            expectedPlayerIds = expectedPlayerIds,
            matchId = matchId,
            clientStates = onlineClientStates,
            localPlayerId = onlineTempUid,
            localPlayerReady = onlineEntryBarrierMatchId == matchId
        )
        val connectedPlayerIds = onlinePlayers
            .filter { player ->
                player.activeInMatch && isOnlinePlayerConnected(player)
            }
            .mapTo(linkedSetOf()) { it.id }
        val elapsedMs = SystemClock.elapsedRealtime() - onlineEntryBarrierStartedAtMs
        val allReady = expectedPlayerIds.all(readyPlayerIds::contains)
        val timeoutQuorumReady = OnlineLobbyEntryGate.canReleaseAfterTimeout(
            expectedPlayerIds = expectedPlayerIds,
            matchId = matchId,
            clientStates = onlineClientStates,
            localPlayerId = onlineTempUid,
            localPlayerReady = onlineEntryBarrierMatchId == matchId,
            connectedPlayerIds = connectedPlayerIds,
            elapsedMs = elapsedMs
        )
        if (!allReady && !timeoutQuorumReady) {
            scheduleOnlineEntryReleaseTimeout()
            return
        }
        val acknowledgedCount = readyPlayerIds.size
        val missingNames = onlinePlayers
            .filter { it.id in expectedPlayerIds && it.id !in readyPlayerIds }
            .joinToString(",") { it.name }
        onlineEntryReleaseInProgress = true
        OnlineDebugLog.i(
            "online_entry_release_requested roomId=$onlinePartidaId host=$onlineTempUid match=$matchId acknowledged=$acknowledgedCount/${expectedPlayerIds.size} connected=${connectedPlayerIds.size}/${expectedPlayerIds.size} mode=${if (allReady) "all_ready" else "timeout_quorum"} missing=${missingNames.ifBlank { "-" }}"
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
        val quorumRemainingMs = OnlineLobbyEntryGate.HARD_RELEASE_AFTER_MS - elapsedMs
        val connectedFallbackRemainingMs =
            OnlineLobbyEntryGate.FULLY_CONNECTED_RELEASE_AFTER_MS - elapsedMs
        val delayMs = when {
            quorumRemainingMs > 0L -> quorumRemainingMs
            connectedFallbackRemainingMs > 0L -> connectedFallbackRemainingMs
            else -> ONLINE_ENTRY_RETRY_MS
        }
        onlineEntryReleaseTimeoutScheduled = true
        startButton.postDelayed(onlineEntryReleaseTimeoutRunnable, delayMs)
    }

    private fun enterReleasedOnlineMatch(matchId: String) {
        if (onlineStartedNoticeShown) return
        onlineStartedNoticeShown = true
        if (::startButton.isInitialized) {
            startButton.removeCallbacks(onlineEntryReleaseTimeoutRunnable)
            onlineEntryAckRunnable?.let(startButton::removeCallbacks)
        }
        onlineEntryAckRunnable = null
        onlineEntryAckInProgress = false
        cancelOnlineMatchEntryRetry(resetAttempts = true)
        onlineEntryReleaseTimeoutScheduled = false
        OnlineDebugLog.i(
            "online_entry_released roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId"
        )
        startOnlineMatch()
    }

    private fun startOnlineMatch() {
        val sharedSession = onlineInitialMatch?.let(::sessionFromInitialMatch)
        if (sharedSession == null) {
            onlineStartedNoticeShown = false
            if (lastOnlineMatchRebuildFailureReason == OnlineMatchSessionError.INCOMPATIBLE_STATE.name) {
                showIncompatibleOnlineRoomNotice()
                return
            }
            scheduleOnlineMatchEntryRetry(
                lastOnlineMatchRebuildFailureReason.ifBlank {
                    "No se pudo reconstruir la partida compartida."
                }
            )
            return
        }
        val entryProblem = onlineMatchEntryProblem(sharedSession)
        if (entryProblem != null) {
            onlineStartedNoticeShown = false
            scheduleOnlineMatchEntryRetry(entryProblem)
            return
        }
        cancelOnlineMatchEntryRetry(resetAttempts = true)
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
        stopOnlineFirestoreListenersForMatchTransition()
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
        mapKey: String,
        config: OnlineLobbyConfig,
        playersAtStart: List<OnlineLobbyPlayer>
    ): GameSession {
        val realPlayers = playersAtStart.map { player ->
            GamePlayer(
                name = player.name,
                initial = player.initial,
                isHuman = player.id == onlineTempUid,
                control = if (player.id == onlineTempUid) {
                    PlayerControl.LOCAL
                } else {
                    PlayerControl.REMOTE
                }
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
            onlinePlayerUids = playersAtStart.map { it.id },
            onlineRegisteredPlayerUids = playersAtStart
                .filter { it.publicId.isNotBlank() }
                .map { it.id },
            roleComposition = config.compositionFor(realPlayers.size, map.key)
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
        val disconnected = activePlayers.count { !isOnlinePlayerAvailableForLobby(it) }
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
        if (GameEngine.humanPlayer(session).role == null) {
            return "El reparto online llego incompleto. Creen una sala nueva."
        }
        if (currentUserIsOnlineHost() && session.players.any { it.role == null }) {
            return "El anfitrion todavía está recibiendo el reparto completo."
        }
        return null
    }

    private fun initialMatchPayload(
        assignedSession: GameSession,
        playersAtStart: List<OnlineLobbyPlayer>
    ): Map<String, Any?> {
        require(assignedSession.players.size == playersAtStart.size) {
            "El reparto cambio la cantidad de jugadores capturados."
        }
        require(
            assignedSession.players.map { it.name } == playersAtStart.map { it.name }
        ) {
            "El reparto cambio el orden de jugadores capturados."
        }
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
                "votosIndividuales" to assignedSession.showIndividualVotes,
                "roles" to assignedSession.roleComposition.counts
            ),
            "jugadores" to assignedSession.players.mapIndexed { index, player ->
                val onlinePlayer = playersAtStart[index]
                mapOf(
                    "orden" to index,
                    "uidTemporal" to onlinePlayer.id,
                    "publicId" to onlinePlayer.publicId,
                    "simulado" to false,
                    "nombre" to player.name,
                    "inicial" to player.initial
                )
            }
        )
    }

    private fun stopOnlineFirestoreListenersForMatchTransition() {
        roomListener?.remove()
        roomListener = null
        playersListener?.remove()
        playersListener = null
        ownPlayerListener?.remove()
        ownPlayerListener = null
        ownRoomBanListener?.remove()
        ownRoomBanListener = null
        onlinePrivateRoleListener?.remove()
        onlinePrivateRoleListener = null
        onlinePrivateRolesLoading = false
        onlinePrivateRoleLoadGeneration += 1
        OnlineDebugLog.i(
            "firestore_usage lobby_transition roomId=$onlinePartidaId uid=$onlineTempUid " +
                firestoreUsage.summary()
        )
    }

    private fun ensurePrivateRolesLoaded() {
        val matchId = (onlineInitialMatch?.get("matchId") as? String).orEmpty()
        if (matchId.isBlank() || onlinePrivateRolesLoading) return
        if (onlinePrivateRolesMatchId == matchId && onlinePrivateRoleAssignments.isNotEmpty()) {
            coordinateOnlineMatchEntry()
            return
        }
        if (onlinePrivateRoleAttemptMatchId != matchId) {
            onlinePrivateRoleAttemptMatchId = matchId
            onlinePrivateRoleLoadAttempt = 0
            onlinePrivateRoleLoadGeneration += 1
            onlinePrivateRoleListener?.remove()
            onlinePrivateRoleListener = null
            onlinePrivateRoleTimeoutRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleRetryRunnable?.let(startButton::removeCallbacks)
            onlinePrivateRoleTimeoutRunnable = null
            onlinePrivateRoleRetryRunnable = null
        }
        onlinePrivateRoleRetryRunnable?.let(startButton::removeCallbacks)
        onlinePrivateRoleRetryRunnable = null
        onlinePrivateRolesLoading = true
        onlinePrivateRoleLoadAttempt += 1
        val generation = ++onlinePrivateRoleLoadGeneration
        val repartos = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection("repartos")
        if (currentUserIsOnlineHost()) {
            firestoreUsage.listenerStarted("private_roles_host")
            onlinePrivateRoleListener = repartos.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (generation != onlinePrivateRoleLoadGeneration) return@addSnapshotListener
                if (error != null) {
                    finishPrivateRoleLoadAttempt(generation)
                    handlePrivateRoleLoadFailure(matchId, error)
                    return@addSnapshotListener
                }
                firestoreUsage.serverSnapshot(
                    name = "private_roles_host",
                    fromCache = snapshot?.metadata?.isFromCache == true,
                    pendingWrites = snapshot?.metadata?.hasPendingWrites() == true,
                    changedDocuments = snapshot?.documentChanges?.size ?: 0,
                    resultDocuments = snapshot?.documents?.size ?: 0,
                    dependentDocuments = 1
                )
                val documents = snapshot?.documents.orEmpty()
                val expectedPlayerIds = initialMatchPlayerIds()
                val currentDocuments = documents.filter {
                    it.exists() &&
                        it.id in expectedPlayerIds &&
                        it.getString("matchId") == matchId
                }
                val completeRoles = OnlineHostRoleRecovery.completeRolesByOrder(
                    rawAssignments = privateRoleAssignmentsFromDocuments(matchId, currentDocuments),
                    playerCount = expectedPlayerIds.size
                )
                if (
                    expectedPlayerIds.isEmpty() ||
                    currentDocuments.mapTo(linkedSetOf()) { it.id } != expectedPlayerIds ||
                    completeRoles == null
                ) {
                    return@addSnapshotListener
                }
                if (!finishPrivateRoleLoadAttempt(generation)) return@addSnapshotListener
                applyPrivateRoleDocuments(matchId, currentDocuments)
            }
        } else {
            firestoreUsage.listenerStarted("private_role_self")
            onlinePrivateRoleListener = repartos.document(onlineTempUid)
                .addSnapshotListener(MetadataChanges.INCLUDE) { document, error ->
                    if (generation != onlinePrivateRoleLoadGeneration) return@addSnapshotListener
                    if (error != null) {
                        finishPrivateRoleLoadAttempt(generation)
                        handlePrivateRoleLoadFailure(matchId, error)
                        return@addSnapshotListener
                    }
                    firestoreUsage.serverSnapshot(
                        name = "private_role_self",
                        fromCache = document?.metadata?.isFromCache == true,
                        pendingWrites = document?.metadata?.hasPendingWrites() == true,
                        changedDocuments = if (document?.exists() == true) 1 else 0,
                        resultDocuments = if (document?.exists() == true) 1 else 0
                    )
                    if (document?.exists() != true || document.getString("matchId") != matchId) {
                        return@addSnapshotListener
                    }
                    if (!finishPrivateRoleLoadAttempt(generation)) return@addSnapshotListener
                    applyPrivateRoleDocuments(matchId, listOf(document))
                }
        }
        val timeoutRunnable = Runnable {
            if (
                generation != onlinePrivateRoleLoadGeneration ||
                !onlinePrivateRolesLoading ||
                onlinePrivateRolesMatchId == matchId
            ) {
                return@Runnable
            }
            finishPrivateRoleLoadAttempt(generation)
            OnlineDebugLog.w(
                "private_roles_load_timeout roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId attempt=$onlinePrivateRoleLoadAttempt"
            )
            schedulePrivateRoleLoadRetry(matchId)
        }
        onlinePrivateRoleTimeoutRunnable = timeoutRunnable
        startButton.postDelayed(timeoutRunnable, ONLINE_PRIVATE_ROLE_LOAD_TIMEOUT_MS)
    }

    private fun finishPrivateRoleLoadAttempt(generation: Int): Boolean {
        if (generation != onlinePrivateRoleLoadGeneration) return false
        onlinePrivateRoleLoadGeneration += 1
        onlinePrivateRoleListener?.remove()
        onlinePrivateRoleListener = null
        onlinePrivateRoleTimeoutRunnable?.let(startButton::removeCallbacks)
        onlinePrivateRoleTimeoutRunnable = null
        onlinePrivateRolesLoading = false
        return true
    }

    private fun schedulePrivateRoleLoadRetry(matchId: String) {
        if (
            matchId.isBlank() ||
            onlinePrivateRolesMatchId == matchId ||
            onlinePrivateRoleRetryRunnable != null ||
            onlineRoomState != ONLINE_ROOM_STATE_IN_GAME
        ) {
            return
        }
        val delayMs = (
            ONLINE_PRIVATE_ROLE_RETRY_BASE_MS * onlinePrivateRoleLoadAttempt.coerceAtMost(4)
            ) + Random.nextLong(ONLINE_PRIVATE_ROLE_RETRY_JITTER_MS + 1L)
        val runnable = Runnable {
            onlinePrivateRoleRetryRunnable = null
            if (!isFinishing && !isDestroyed && onlineRoomState == ONLINE_ROOM_STATE_IN_GAME) {
                ensurePrivateRolesLoaded()
            }
        }
        onlinePrivateRoleRetryRunnable = runnable
        startButton.postDelayed(runnable, delayMs)
    }

    private fun applyPrivateRoleDocuments(matchId: String, documents: List<DocumentSnapshot>) {
        onlinePrivateRolesLoading = false
        val assignments = privateRoleAssignmentsFromDocuments(matchId, documents)
            .distinctBy { (it["orden"] as? Number)?.toInt() }
        if (assignments.isEmpty()) {
            schedulePrivateRoleLoadRetry(matchId)
            return
        }
        if (
            currentUserIsOnlineHost() &&
            OnlineHostRoleRecovery.completeRolesByOrder(
                rawAssignments = assignments,
                playerCount = initialMatchPlayerIds().size
            ) == null
        ) {
            OnlineDebugLog.w(
                "private_roles_incomplete_for_host roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId roles=${assignments.size}/${initialMatchPlayerIds().size}"
            )
            schedulePrivateRoleLoadRetry(matchId)
            return
        }
        onlinePrivateRoleAssignments = assignments
        onlinePrivateRolesMatchId = matchId
        coordinateOnlineMatchEntry()
    }

    private fun privateRoleAssignmentsFromDocuments(
        matchId: String,
        documents: List<DocumentSnapshot>
    ): List<Map<String, Any?>> = documents
        .filter { it.exists() && it.getString("matchId") == matchId }
        .flatMap { document ->
            (document.get("rolesVisibles") as? List<*>)
                .orEmpty()
                .mapNotNull { it.asStringAnyMap() }
        }

    private fun handlePrivateRoleLoadFailure(matchId: String, error: Exception) {
        onlinePrivateRolesLoading = false
        OnlineDebugLog.e(
            "private_roles_load_failure roomId=$onlinePartidaId uid=$onlineTempUid match=$matchId attempt=$onlinePrivateRoleLoadAttempt",
            error
        )
        schedulePrivateRoleLoadRetry(matchId)
    }

    private fun roleAssignmentPayload(order: Int, role: GameRole): Map<String, Any?> =
        mapOf(
            "orden" to order,
            "rolKey" to role.key,
            "rolNombre" to role.name,
            "rolEquipo" to role.team,
            "rolImagen" to role.imageResName
        )

    private fun initialMatchPlayerIds(): LinkedHashSet<String> {
        val players = onlineInitialMatch?.get("jugadores") as? List<*> ?: return linkedSetOf()
        return players.mapNotNull { rawPlayer ->
            val player = rawPlayer as? Map<*, *> ?: return@mapNotNull null
            (player["uidTemporal"] as? String)?.takeIf(String::isNotBlank)
        }.toCollection(linkedSetOf())
    }

    private fun matchStatePayload(assignedSession: GameSession): Map<String, Any?> {
        return mapOf(
            "versionEstado" to OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
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
            showIndividualVotes = session.showIndividualVotes,
            privateRoleAssignments = onlinePrivateRoleAssignments,
            requireCompleteRoleAssignments = currentUserIsOnlineHost()
        )
        return when (result) {
            is OnlineMatchSessionResult.Success -> {
                lastOnlineMatchRebuildFailureReason = ""
                result.session
            }
            is OnlineMatchSessionResult.Failure -> {
                lastOnlineMatchRebuildFailureReason = result.reason.name
                OnlineDebugLog.e(
                    "online_match_rebuild_failure roomId=$onlinePartidaId uid=$onlineTempUid reason=${result.reason.name}"
                )
                null
            }
        }
    }

    private fun showIncompatibleOnlineRoomNotice() {
        cancelOnlineMatchEntryRetry(resetAttempts = true)
        if (onlineIncompatibleNoticeShown) return
        onlineIncompatibleNoticeShown = true
        Toast.makeText(
            this,
            OnlineMatchSessionError.INCOMPATIBLE_STATE.userMessage,
            Toast.LENGTH_LONG
        ).show()
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
        if (!OnlineLobbyRules.canPrepareRematch(
                roomState = onlineRoomState,
                hasAuthoritativeState = onlineMatchState != null,
                winner = (onlineMatchState?.get("ganador") as? String).orEmpty(),
                isHost = currentUserIsOnlineHost(),
                resetInProgress = onlineRematchResetInProgress,
                cleanupPending = onlineCleanupPending,
                playerCount = onlinePlayers.size
            )
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
            if (!OnlineLobbyRules.isRematchableRoom(
                    roomState = roomState,
                    hasAuthoritativeState = authoritativeState != null,
                    winner = (authoritativeState?.get("ganador") as? String).orEmpty()
                )
            ) {
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
                    OnlineRoomFirestore.FIELD_HOST_VERSION to FieldValue.increment(1),
                    FIELD_INITIAL_MATCH_CREATED to false,
                    FIELD_CLEANUP_PENDING to true,
                    FIELD_INITIAL_MATCH to FieldValue.delete(),
                    FIELD_MATCH_STATE to FieldValue.delete(),
                    FIELD_CLIENT_STATES to FieldValue.delete(),
                    FIELD_ENTRY_RELEASED_MATCH_ID to FieldValue.delete(),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to playersToReset.size,
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
                onlineMatchStateMatchId = ""
                onlineCheckpointLoadInProgress = false
                onlineCheckpointLoadedMatchId = ""
                onlineCleanupPending = true
                onlineStartedNoticeShown = false
                recoveringOnlineMatch = false
                OnlineRoomRecovery.clearIf(this, onlinePartidaId)
                OnlineDebugLog.i(
                    "rematch_reset_success roomId=$onlinePartidaId hostId=$onlineTempUid players=${playersToReset.size}"
                )
                Toast.makeText(
                    this,
                    "Sala preparada. Estamos borrando las huellas de la partida anterior...",
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
        GameDialog.choose(
            activity = this,
            title = "Empate en la votación",
            message = "Tu voto ya contó como uno. Ahora elegí entre los mapas empatados para iniciar.",
            options = validKeys.map(::mapName)
        ) { index ->
            startOnlineRoomForEveryone(validKeys[index])
        }
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
            collectionNames = listOf(
                "acciones",
                "repartos",
                OnlineAuthoritativeStateStore.COLLECTION
            ),
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
            RTDB_SPECTATOR_CHAT_NODE to null,
            "emotes" to null,
            "propuesta_silencio" to null,
            "votos_silencio" to null,
            "silenciados" to null,
            RealtimeAuthoritativeState.NODE to null
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
            activeOnlinePlayers().all { isOnlinePlayerAvailableForLobby(it) && it.ready }
    }

    private fun onlineRoomCanStart(): Boolean {
        return OnlineLobbyRules.canStart(
            players = onlineParticipantsForLobbyStart(),
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
        localPlayerControlsRow.visibility = localPlayerControlsVisibility
        btnAddPlayer.visibility = localPlayerControlsVisibility
        btnRemovePlayer.visibility = localPlayerControlsVisibility

        timingOptionsButton.visibility = if (firestoreLobby) View.GONE else View.VISIBLE
        timingOptionsButton.isEnabled = !firestoreLobby
        btnAdvancedOptions.visibility = View.VISIBLE
        btnAdvancedOptions.isEnabled = true
        btnAdvancedOptions.alpha = 1f
        btnAdvancedOptions.text = if (firestoreLobby) "VER Y EDITAR REGLAS" else "OPCIONES AVANZADAS"
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
        GameDialog.custom(
            activity = this,
            contentView = dialogContent,
            widthDp = 620,
            negativeLabel = "CANCELAR",
            neutralLabel = "RESTABLECER",
            positiveLabel = "APLICAR",
            onNeutral = {
                draft = GameTimingPreset.NORMAL.config
                customMode = false
                refreshValues()
            },
            onPositive = {
                session = session.copy(timingConfig = draft.normalized())
                renderLobby()
            }
        )
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
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES DE PARTIDA"))
        content.addView(dialogSectionTitle("RITMO"))

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

        addTestSwitch("Partida rápida", quickTestMode) { quickTestMode = it }
        content.addView(TextView(this).apply {
            text = "Saltea las fases en las que no te toca actuar y acelera las votaciones."
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
        } else {
            content.addView(TextView(this).apply {
                text = "Las herramientas debug solo aparecen en compilaciones de prueba."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
        }
        val scroll = ScrollView(this).apply { addView(content) }
        GameDialog.custom(
            activity = this,
            contentView = scroll,
            widthDp = 560,
            negativeLabel = "CANCELAR",
            positiveLabel = "APLICAR",
            onPositive = {
                session = session.copy(
                    quickTestMode = quickTestMode,
                    debugBotsObeyVoteCommands = botsObeyVotes,
                    debugForceVoteTies = forceTies,
                    debugBotsNeverKillHuman = botsNeverKill,
                    debugBotsNeverVoteHuman = botsNeverVote
                )
                renderLobby()
            }
        )
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
        var selectedPracticeRoleIndex = practiceRoleIndex
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES AVANZADAS"))
        content.addView(dialogSectionTitle("REGLAS DE LA PARTIDA"))
        addAdvancedRuleOption(
            content = content,
            title = "Mostrar roles al morir o al expulsar",
            description = "Si se desactiva, las cartas eliminadas permanecen ocultas.",
            checked = revealRolesOnDeath
        ) { checked ->
            revealRolesOnDeath = checked
        }
        addAdvancedRuleOption(
            content = content,
            title = "Mostrar votos individuales",
            description = "Muestra quién votó a cada jugador. Si se desactiva, solo se ve el total.",
            checked = showIndividualVotes
        ) { checked ->
            showIndividualVotes = checked
        }
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
        content.addView(dialogSectionTitle(getString(R.string.lobby_practice_section)))
        content.addView(TextView(this).apply {
            text = getString(R.string.lobby_practice_description)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        val practiceRoleButton = compactDialogButton("")
        val practiceRoleDetailButton = compactDialogButton(
            getString(R.string.lobby_practice_view_role)
        ).apply {
            textSize = 11f
        }
        fun refreshPracticeRole() {
            val (roleKey, label) = practiceRoles[selectedPracticeRoleIndex]
            val requirement = practiceRoleRequirement(roleKey)
            practiceRoleButton.text = getString(
                R.string.lobby_practice_role,
                label,
                requirement
            )
            practiceRoleDetailButton.visibility =
                if (roleKey.isBlank()) View.GONE else View.VISIBLE
            practiceRoleDetailButton.contentDescription = getString(
                R.string.lobby_practice_view_role_description,
                label
            )
        }
        practiceRoleButton.setOnClickListener {
            showPracticeRolePicker(selectedPracticeRoleIndex) { selectedIndex ->
                selectedPracticeRoleIndex = selectedIndex
                refreshPracticeRole()
            }
        }
        practiceRoleDetailButton.setOnClickListener {
            val roleKey = practiceRoles[selectedPracticeRoleIndex].first
            if (roleKey.isNotBlank()) {
                RoleDetailDialog.show(this, roleForPracticeDetails(roleKey))
            }
        }
        refreshPracticeRole()
        content.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    practiceRoleButton,
                    LinearLayout.LayoutParams(0, dp(42), 1f)
                )
                addView(
                    practiceRoleDetailButton,
                    LinearLayout.LayoutParams(dp(96), dp(42)).apply {
                        marginStart = dp(6)
                    }
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )
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
        val compositionSummary = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 10.5f
            maxLines = 2
            setPadding(dp(4), 0, dp(4), dp(5))
        }
        val resetRecommendedButton = compactDialogButton("RESTABLECER RECOMENDADO").apply {
            textSize = 10f
            isAllCaps = false
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
            compositionSummary.text = buildRoleCompositionSummary(
                playerTotal = playerTotal,
                roleKeys = roleKeys,
                counts = draftCounts
            )
            resetRecommendedButton.visibility = if (selectedCompositionPreset == null) {
                View.VISIBLE
            } else {
                View.GONE
            }
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
        resetRecommendedButton.setOnClickListener {
            applyCompositionPreset(RoleCompositionPreset.RECOMMENDED)
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
        content.addView(compositionSummary)
        content.addView(
            resetRecommendedButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            ).apply {
                bottomMargin = dp(5)
            }
        )
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
        content.addView(
            roles,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )

        val advancedDialogContent = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        GameDialog.custom(
            activity = this,
            contentView = advancedDialogContent,
            widthDp = 640,
            contentHeightDp = advancedOptionsContentHeightDp(),
            negativeLabel = "VOLVER",
            positiveLabel = "APLICAR",
            onPositive = {
                practiceRoleIndex = selectedPracticeRoleIndex
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
                Toast.makeText(this, "Opciones aplicadas.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** Regla con el interruptor separado para que el título nunca quede debajo del control. */
    private fun addAdvancedRuleOption(
        content: LinearLayout,
        title: String,
        description: String,
        checked: Boolean,
        enabled: Boolean = true,
        onChanged: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(2), dp(4))
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@LobbyActivity).apply {
                text = title
                setTextColor(getColor(R.color.text_primary))
                textSize = 13.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 2
                includeFontPadding = false
            })
            addView(TextView(this@LobbyActivity).apply {
                text = description
                setTextColor(getColor(R.color.text_secondary))
                textSize = 10.5f
                maxLines = 2
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            showText = false
            isChecked = checked
            isEnabled = enabled
            minWidth = dp(64)
            contentDescription = title
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(42)
        ).apply {
            marginStart = dp(8)
        })
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(4)
        })
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
        addAdvancedRuleOption(
            content = content,
            title = "Mostrar roles al morir o al expulsar",
            description = "Si se desactiva, las cartas eliminadas permanecen ocultas.",
            checked = revealRolesOnDeath,
            enabled = canEdit
        ) { checked -> revealRolesOnDeath = checked }
        addAdvancedRuleOption(
            content = content,
            title = "Mostrar votos individuales",
            description = "Muestra quién votó a cada jugador. Si se desactiva, solo se ve el total.",
            checked = showIndividualVotes,
            enabled = canEdit
        ) { checked -> showIndividualVotes = checked }
        content.addView(dialogSectionTitle("TIEMPOS"))
        val timingEditor = buildTimingEditor(session.timingConfig)
        content.addView(timingEditor.view)
        if (!canEdit) setViewTreeEnabled(timingEditor.view, false)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        GameDialog.custom(
            activity = this,
            contentView = scroll,
            widthDp = 560,
            contentHeightDp = advancedOptionsContentHeightDp(),
            negativeLabel = if (canEdit) "VOLVER" else "CERRAR",
            positiveLabel = if (canEdit) "APLICAR" else null,
            onPositive = if (canEdit) {
                {
                    saveOnlineLobbyConfig(
                        onlineLobbyConfig.copy(
                            timing = timingEditor.currentConfig(),
                            revealRolesOnDeath = revealRolesOnDeath,
                            showIndividualVotes = showIndividualVotes
                        )
                    )
                }
            } else {
                null
            }
        )
    }

    private fun showOnlineRoleCompositionDialog() {
        val canEdit = currentUserIsOnlineHost() &&
            onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            !onlineInitialMatchCreated &&
            !onlineCleanupPending
        val playerTotal = onlineExpectedPlayers
        val mapKey = displayedLobbyMap().key
        val roleKeys = LocalGameFactory.editableRoleKeys()
        val currentComposition = onlineLobbyConfig.compositionFor(playerTotal, mapKey)
        val draftCounts = linkedMapOf<String, Int>().apply {
            roleKeys.forEach { roleKey -> put(roleKey, currentComposition.counts[roleKey] ?: 0) }
        }
        var selectedPreset = onlineLobbyConfig.rolePreset

        val content = dialogColumn()
        content.addView(dialogTitle(if (canEdit) "CONFIGURAR ROLES" else "ROLES DE LA PARTIDA"))
        content.addView(TextView(this).apply {
            text = if (canEdit) {
                "La composición es pública. Al aplicar cambios, todos deberán marcar LISTO nuevamente."
            } else {
                "El anfitrión eligió esta composición. Las identidades continúan ocultas."
            }
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(8))
        })

        val balanceView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        val presetButtons = linkedMapOf<RoleCompositionPreset, Button>()
        val countViews = linkedMapOf<String, TextView>()
        val minusButtons = linkedMapOf<String, Button>()
        val plusButtons = linkedMapOf<String, Button>()

        fun nonVillagerTotal(): Int = draftCounts
            .filterKeys { it != RoleCatalog.ALDEANO }
            .values
            .sum()

        fun refreshEditor() {
            draftCounts[RoleCatalog.ALDEANO] =
                (playerTotal - nonVillagerTotal()).coerceAtLeast(0)
            roleKeys.forEach { roleKey ->
                val count = draftCounts[roleKey] ?: 0
                val maximum = LocalGameFactory.maxCountForRole(roleKey, playerTotal, mapKey)
                countViews[roleKey]?.text = count.toString()
                updateRoleStepButton(
                    minusButtons[roleKey],
                    canEdit && roleKey != RoleCatalog.ALDEANO &&
                        count > if (roleKey == RoleCatalog.ASESINO) 1 else 0
                )
                updateRoleStepButton(
                    plusButtons[roleKey],
                    canEdit && roleKey != RoleCatalog.ALDEANO && count < maximum &&
                        (draftCounts[RoleCatalog.ALDEANO] ?: 0) > 0
                )
            }
            presetButtons.forEach { (preset, button) ->
                val selected = selectedPreset == preset
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
            }
            val balance = RoleCompositionBalance.evaluate(playerTotal, draftCounts)
            balanceView.text = "${balance.label} · ${balance.explanation}"
            balanceView.setTextColor(
                getColor(
                    when (balance) {
                        RoleCompositionBalance.BALANCED -> R.color.accent_green
                        RoleCompositionBalance.TOWN_FAVORED -> R.color.accent_blue
                        RoleCompositionBalance.TRAITORS_FAVORED -> R.color.accent_red
                        RoleCompositionBalance.RISKY -> R.color.accent_purple
                    }
                )
            )
        }

        fun applyPreset(preset: RoleCompositionPreset) {
            val composition = LocalGameFactory.roleCompositionPreset(playerTotal, mapKey, preset)
            roleKeys.forEach { roleKey -> draftCounts[roleKey] = composition.counts[roleKey] ?: 0 }
            selectedPreset = preset
            refreshEditor()
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        RoleCompositionPreset.entries.forEachIndexed { index, preset ->
            val button = compactDialogButton(preset.label).apply {
                textSize = 10f
                isEnabled = canEdit
                alpha = if (canEdit) 1f else 0.72f
                setOnClickListener { applyPreset(preset) }
            }
            presetButtons[preset] = button
            presetRow.addView(button, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                if (index > 0) marginStart = dp(5)
            })
        }
        content.addView(presetRow)
        content.addView(balanceView)

        val roles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        roleKeys.forEach { roleKey ->
            val definition = RoleCatalog.definition(roleKey)
            val detailMap = definition.exclusiveMap ?: RoleMap.fromSessionKey(mapKey)
            val role = RoleCatalog.role(roleKey, detailMap)
            val maximum = LocalGameFactory.maxCountForRole(roleKey, playerTotal, mapKey)
            val locked = maximum == 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
                setImageResource(if (resId != 0) resId else R.drawable.placeholder_local)
                alpha = if (locked) 0.32f else 1f
                contentDescription = "Ver ${role.name}"
                setOnClickListener { RoleDetailDialog.show(this@LobbyActivity, role) }
            }, LinearLayout.LayoutParams(dp(38), dp(46)).apply { marginEnd = dp(8) })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@LobbyActivity).apply {
                    text = roleLabel(roleKey)
                    setTextColor(if (locked) getColor(R.color.text_muted) else roleCompositionColor(roleKey))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(this@LobbyActivity).apply {
                    text = if (locked) {
                        roleMeta(roleKey, definition.minimumPlayers)
                    } else {
                        when (definition.team) {
                            GameRules.TRAITOR_WINNER -> "Traidor"
                            GameRules.TOWN_WINNER -> "Pueblo"
                            else -> "Neutral"
                        }
                    }
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 9f
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            val minus = compactDialogButton("−").apply {
                setOnClickListener {
                    val current = draftCounts[roleKey] ?: 0
                    val minimum = if (roleKey == RoleCatalog.ASESINO) 1 else 0
                    if (current > minimum) {
                        draftCounts[roleKey] = current - 1
                        selectedPreset = null
                        refreshEditor()
                    }
                }
            }
            minusButtons[roleKey] = minus
            row.addView(minus, LinearLayout.LayoutParams(dp(34), dp(34)))
            val count = TextView(this).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_btn_dark)
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            countViews[roleKey] = count
            row.addView(count, LinearLayout.LayoutParams(dp(42), dp(34)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
            val plus = compactDialogButton("+").apply {
                setOnClickListener {
                    val current = draftCounts[roleKey] ?: 0
                    if (current < maximum && (draftCounts[RoleCatalog.ALDEANO] ?: 0) > 0) {
                        draftCounts[roleKey] = current + 1
                        selectedPreset = null
                        refreshEditor()
                    }
                }
            }
            plusButtons[roleKey] = plus
            row.addView(plus, LinearLayout.LayoutParams(dp(34), dp(34)))
            roles.addView(row)
        }
        refreshEditor()
        content.addView(roles)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        GameDialog.custom(
            activity = this,
            contentView = scroll,
            widthDp = 640,
            contentHeightDp = advancedOptionsContentHeightDp(),
            negativeLabel = if (canEdit) "CANCELAR" else "CERRAR",
            positiveLabel = if (canEdit) "APLICAR" else null,
            onPositive = if (canEdit) {
                {
                    saveOnlineRoleComposition(
                        preset = selectedPreset,
                        counts = draftCounts.toMap()
                    )
                }
            } else {
                null
            }
        )
    }

    private fun saveOnlineRoleComposition(
        preset: RoleCompositionPreset?,
        counts: Map<String, Int>
    ) {
        val updatedConfig = onlineLobbyConfig.copy(
            roleComposition = RoleCompositionConfig(counts = counts, customized = preset == null),
            rolePreset = preset
        ).normalized()
        if (updatedConfig == onlineLobbyConfig) {
            if (pendingOnlineRolePreset == preset) pendingOnlineRolePreset = null
            renderOnlineRoleComposition()
            GameNotice.show(this, "La composición ya estaba configurada de esa manera.")
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        val activePlayers = activeOnlinePlayers()
        firestore.runBatch { batch ->
            batch.update(
                roomReference,
                mapOf(
                    OnlineLobbyConfig.FIELD_ROOM_CONFIG to updatedConfig.toFirestore(),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            activePlayers.forEach { player ->
                batch.update(
                    roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id),
                    mapOf(FIELD_PLAYER_READY to false)
                )
            }
        }.addOnSuccessListener {
            if (pendingOnlineRolePreset == preset) pendingOnlineRolePreset = null
            onlineLobbyConfig = updatedConfig
            session = session.copy(
                roleComposition = updatedConfig.compositionFor(
                    onlineExpectedPlayers,
                    displayedLobbyMap().key
                )
            )
            addLobbySystemNotice("El anfitrión actualizó los roles. Todos deben confirmar LISTO nuevamente.")
            renderLobby()
            GameNotice.show(this, "Roles actualizados para toda la sala.")
        }.addOnFailureListener { error ->
            if (pendingOnlineRolePreset == preset) pendingOnlineRolePreset = null
            renderOnlineRoleComposition()
            OnlineDebugLog.e("lobby_roles_update_failure roomId=$onlinePartidaId", error)
            GameNotice.show(
                this,
                OnlineErrorMessages.forAction("No se pudieron actualizar los roles", error),
                GameNotice.Duration.LONG
            )
        }
    }

    private fun showBannedPlayersDialog() {
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection("baneados")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    GameNotice.show(this, "No hay jugadores bloqueados en esta sala.")
                    return@addOnSuccessListener
                }
                val documents = snapshot.documents
                GameDialog.choose(
                    activity = this,
                    title = "BLOQUEADOS DE ESTA SALA",
                    message = "Tocá un jugador para permitirle volver a entrar.",
                    options = documents.map {
                        "DESBLOQUEAR ${it.getString("nombre").orEmpty().uppercase()}"
                    }
                ) { selected ->
                    val document = documents[selected]
                    GameDialog.confirm(
                        activity = this,
                        title = "Permitir reingreso",
                        message = "¿Desbloquear a ${document.getString("nombre").orEmpty()}?",
                        positiveLabel = "DESBLOQUEAR"
                    ) {
                        document.reference.delete()
                            .addOnSuccessListener {
                                GameNotice.show(this, "El jugador puede volver a entrar.")
                            }
                            .addOnFailureListener { error ->
                                GameNotice.show(
                                    this,
                                    OnlineErrorMessages.forAction("No se pudo desbloquear", error),
                                    GameNotice.Duration.LONG
                                )
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                GameNotice.show(
                    this,
                    OnlineErrorMessages.forAction("No se pudo cargar la lista", error),
                    GameNotice.Duration.LONG
                )
            }
    }

    private fun advancedOptionsContentHeightDp(): Int =
        (resources.configuration.screenHeightDp - 190).coerceIn(280, 520)

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
                    showIndividualVotes = safe.showIndividualVotes,
                    roleComposition = safe.compositionFor(
                        onlineExpectedPlayers,
                        displayedLobbyMap().key
                    )
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
        AccessibilityOptionsDialog.show(
            activity = this,
            reportLabel = if (isFirestoreOnlineLobby()) "COPIAR REPORTE BETA" else null,
            onReportRequested = if (isFirestoreOnlineLobby()) {
                { OnlineStabilityReport.copyToClipboard(this) }
            } else {
                null
            }
        )
    }

    private fun roleLabel(roleKey: String): String {
        return when (roleKey) {
            RoleCatalog.ALDEANO -> "ALDEANO"
            RoleCatalog.POLICIA -> if (displayedLobbyMap().key == "pampa") "COMISARIO" else "DETECTIVE"
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

    private fun roleForPracticeDetails(roleKey: String): Role {
        val currentMap = RoleMap.fromSessionKey(session.mapKey)
        val detailMap = RoleCatalog.definition(roleKey).exclusiveMap ?: currentMap
        return RoleCatalog.role(roleKey, detailMap)
    }

    private fun showPracticeRolePicker(
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val content = dialogColumn()
        content.addView(dialogTitle(getString(R.string.lobby_practice_picker_title)))
        content.addView(TextView(this).apply {
            text = getString(R.string.lobby_practice_picker_description)
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        var dismissPicker: () -> Unit = {}
        fun roleChoiceButton(index: Int): Button {
            val (roleKey, label) = practiceRoles[index]
            val selected = index == selectedIndex
            val meta = if (roleKey.isBlank()) {
                getString(R.string.lobby_practice_random_description)
            } else {
                roleMeta(roleKey, LocalGameFactory.minimumPlayersForRole(roleKey))
            }
            return compactDialogButton("$label\n$meta").apply {
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                setTextColor(
                    getColor(
                        when {
                            selected -> R.color.bg_dark
                            roleKey.isBlank() -> R.color.text_secondary
                            else -> R.color.text_primary
                        }
                    )
                )
                contentDescription = if (selected) {
                    getString(R.string.lobby_practice_role_selected_description, label, meta)
                } else {
                    getString(R.string.lobby_practice_role_choice_description, label, meta)
                }
                setOnClickListener {
                    onSelected(index)
                    dismissPicker()
                }
            }
        }

        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        choices.addView(
            roleChoiceButton(0),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(PRACTICE_ROLE_PICKER_ROW_HEIGHT_DP)
            ).apply {
                bottomMargin = dp(6)
            }
        )
        practiceRoles.indices.drop(1).chunked(2).forEach { rowIndices ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowIndices.forEachIndexed { column, index ->
                val singleChoiceRow = rowIndices.size == 1
                row.addView(
                    roleChoiceButton(index),
                    LinearLayout.LayoutParams(
                        if (singleChoiceRow) LinearLayout.LayoutParams.MATCH_PARENT else 0,
                        dp(PRACTICE_ROLE_PICKER_ROW_HEIGHT_DP),
                        if (singleChoiceRow) 0f else 1f
                    ).apply {
                        if (column > 0) marginStart = dp(6)
                    }
                )
            }
            choices.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6)
                }
            )
        }
        content.addView(choices)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val dialog = GameDialog.custom(
            activity = this,
            contentView = scroll,
            widthDp = 620,
            contentHeightDp = advancedOptionsContentHeightDp(),
            negativeLabel = getString(R.string.action_cancel),
            positiveLabel = null
        )
        dismissPicker = { dialog.dismiss() }
    }

    private fun renderPracticeRoleSummary() {
        val (roleKey, label) = practiceRoles[practiceRoleIndex]
        val requirement = practiceRoleRequirement(roleKey)
        practiceRoleSummary.text = getString(
            R.string.lobby_practice_summary,
            label,
            requirement
        )
        practiceRoleSummary.setTextColor(
            getColor(if (roleKey.isBlank()) R.color.text_secondary else R.color.accent_gold)
        )
        practiceRoleSummary.contentDescription = getString(
            R.string.lobby_practice_summary_description,
            label,
            requirement
        )
    }

    private fun practiceRoleRequirement(roleKey: String): String {
        if (roleKey.isBlank()) return ""
        val minimumPlayers = LocalGameFactory.minimumPlayersForRole(roleKey)
        return if (minimumPlayers > session.players.size) {
            getString(R.string.lobby_practice_minimum, minimumPlayers)
        } else {
            ""
        }
    }

    private fun buildRoleCompositionSummary(
        playerTotal: Int,
        roleKeys: List<String>,
        counts: Map<String, Int>
    ): String {
        val selectedRoles = roleKeys.mapNotNull { key ->
            val count = counts[key] ?: 0
            if (count > 0) "$count ${roleLabel(key)}" else null
        }
        return "$playerTotal jugadores · ${selectedRoles.joinToString(" · ")}".trimEnd('·', ' ')
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun revealLastLocalPlayer() {
        lobbyBodyScroll.post {
            val lastPlayer = playersContainer.getChildAt(playersContainer.childCount - 1)
                ?: return@post
            lastPlayer.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, lastPlayer.width, lastPlayer.height),
                true
            )
        }
    }

    private fun showLocalBotNameEditor(playerIndex: Int) {
        val player = session.players.getOrNull(playerIndex)
            ?.takeUnless(GamePlayer::isHuman)
            ?: return
        val slot = LocalBotNameStore.slotForPlayer(this, session, playerIndex) ?: return
        GameDialog.input(
            activity = this,
            title = "Cambiar nombre del bot",
            currentValue = player.name,
            hint = "Nombre del bot",
            maxLength = LocalBotNameStore.MAX_NAME_LENGTH,
            positiveLabel = "GUARDAR"
        ) { rawName ->
            LocalBotNameStore.validationError(session, playerIndex, rawName)?.let {
                return@input it
            }
            val name = LocalBotNameStore.normalize(rawName)
            LocalBotNameStore.save(this, slot, name)
            session = PlayerProfileStore.withProfiles(
                this,
                LocalBotNameStore.apply(this, session)
            )
            renderLobby()
            Toast.makeText(this, "Ahora el bot se llama $name.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun showPlayerProfile(
        player: GamePlayer,
        onlinePlayer: OnlineLobbyPlayer? = null
    ) {
        session = PlayerProfileStore.withProfiles(this, session)
        val profile = PlayerProfileStore.profileFor(this, session, player)
        if (
            isFirestoreOnlineLobby() &&
            onlinePlayer != null &&
            onlinePlayer.id.isNotBlank() &&
            onlinePlayer.id != onlineTempUid
        ) {
            val actions = buildList {
                val muted = LocalMuteStore.isMuted(
                    this@LobbyActivity,
                    profile.publicId,
                    onlinePlayer.id
                )
                add(
                    PlayerProfileAction(
                        label = if (muted) "VOLVER A ESCUCHAR" else "SILENCIAR PARA MÍ",
                        description = "Solo cambia el chat y los emotes que ves vos."
                    ) {
                        val nowMuted = LocalMuteStore.toggle(
                            this@LobbyActivity,
                            profile.publicId,
                            onlinePlayer.id
                        )
                        GameNotice.show(
                            this@LobbyActivity,
                            if (nowMuted) {
                                "Dejaste de ver el chat y los emotes de ${player.name}. Solo cambia lo que ves vos."
                            } else {
                                "Volvés a ver el chat y los emotes de ${player.name}."
                            }
                        )
                        renderLobbyChatDock()
                        lobbyChatExpandedMessages?.let(::renderLobbyChatMessages)
                    }
                )
                add(
                    PlayerProfileAction(
                        label = "REPORTAR",
                        dangerous = true,
                        description = "Envía un reporte para que podamos revisarlo."
                    ) {
                        val matchId = (onlineInitialMatch?.get("matchId") as? String)
                            ?.takeIf { it.length in 8..80 }
                            ?: "lobby-$onlinePartidaId".take(80)
                        PlayerModeration.showReportDialog(
                            activity = this@LobbyActivity,
                            roomId = onlinePartidaId,
                            matchId = matchId,
                            reportedUid = onlinePlayer.id,
                            reportedName = player.name
                        )
                    }
                )
                if (canCurrentHostTransferTo(onlinePlayer)) {
                    add(
                        PlayerProfileAction(
                            label = "PASAR ANFITRIÓN",
                            description =
                                "Le permite configurar e iniciar la sala. Vos seguís jugando normalmente."
                        ) {
                            GameDialog.confirm(
                                activity = this@LobbyActivity,
                                title = "Pasar el rol de anfitrión",
                                message = "¿Querés que ${player.name} sea el nuevo anfitrión? " +
                                    "Podrá cambiar reglas, iniciar la partida y moderar la sala.",
                                positiveLabel = "PASAR ANFITRIÓN"
                            ) {
                                transferLobbyHost(onlinePlayer, exitAfterTransfer = false)
                            }
                        }
                    )
                }
                if (canCurrentHostRemoveOnlinePlayer(onlinePlayer)) {
                    add(
                        PlayerProfileAction(
                            label = "EXPULSAR DE LA SALA",
                            dangerous = true,
                            description = "Lo saca del lobby y libera su lugar. Puede volver a entrar si queda un cupo."
                        ) {
                            GameDialog.confirm(
                                activity = this@LobbyActivity,
                                title = "Expulsar de la sala",
                                message = "¿Seguro que querés expulsar a ${player.name}? Su lugar quedará libre.",
                                positiveLabel = "EXPULSAR"
                            ) {
                                removeOnlinePlayer(onlinePlayer)
                            }
                        }
                    )
                }
            }
            PlayerProfileDialog.showFull(
                activity = this,
                profile = profile,
                canEdit = false,
                actions = actions
            )
            return
        }
        val localBotIndex = if (lobbyMode == MODE_LOCAL && !player.isHuman) {
            session.players.indexOfFirst { it === player || it.name == player.name }
        } else {
            -1
        }
        PlayerProfileDialog.showFull(
            activity = this,
            profile = profile,
            canEdit = player.isHuman,
            actions = if (localBotIndex >= 0) {
                listOf(
                    PlayerProfileAction(
                        label = "CAMBIAR NOMBRE",
                        description = "Se guarda para tus próximas partidas contra la IA."
                    ) {
                        showLocalBotNameEditor(localBotIndex)
                    }
                )
            } else {
                emptyList()
            }
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
            GameDialog.confirm(
                activity = this,
                title = "Expulsar participante online",
                message = "¿Seguro que querés expulsar a ${onlinePlayer.name}? Su lugar quedará libre.",
                positiveLabel = "EXPULSAR"
            ) {
                removeOnlinePlayer(onlinePlayer)
            }
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

        GameDialog.confirm(
            activity = this,
            title = "Expulsar participante",
            message = "¿Quitar a ${player.name} de la sala local?",
            positiveLabel = "EXPULSAR"
        ) {
            session = PlayerProfileStore.withProfiles(this, LocalGameFactory.removePlayer(session, index))
            renderLobby()
        }
    }

    private fun currentMap(): GameMap {
        return LocalGameFactory.maps.firstOrNull { it.key == session.mapKey } ?: LocalGameFactory.maps.first()
    }

    private fun currentOnlineMapVotes(): List<OnlineMapVote> {
        return activeOnlinePlayers().map { player ->
            OnlineMapVote(player.id, player.initial, player.mapVote)
        }
    }

    private fun displayedLobbyMap(): GameMap {
        if (!isFirestoreOnlineLobby()) return currentMap()
        val key = OnlineMapVoteResolver.liveLobbyMapKey(
            votes = currentOnlineMapVotes(),
            currentMapKey = session.mapKey
        )
        return LocalGameFactory.maps.firstOrNull { it.key == key } ?: currentMap()
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
        private const val PRACTICE_ROLE_PICKER_ROW_HEIGHT_DP = 58
        private const val DEFAULT_ROLE_READING_SECONDS = 0
        private const val MAX_LOCAL_LOBBY_NOTICES = 12
        private const val LOBBY_HOST_DISCONNECT_GRACE_MS = 60_000L
        private const val LOBBY_PLAYER_RECONNECT_GRACE_MS = 5 * 60_000L
        private const val CLEANUP_BATCH_SIZE = 400L
        private const val CLEANUP_RETRY_DELAY_MS = 5_000L
        private const val LOBBY_CHAT_PREVIEW_LINES = 3
        private const val LOBBY_EMOTE_SOUND_COOLDOWN_MS = 900L
        private const val ONLINE_ENTRY_RETRY_MS = 1_500L
        private const val ONLINE_ENTRY_ACK_JITTER_MAX_MS = 250L
        private const val ONLINE_MATCH_ENTRY_MAX_RETRIES = 3
        private const val ONLINE_PRIVATE_ROLE_LOAD_TIMEOUT_MS = 7_000L
        private const val ONLINE_PRIVATE_ROLE_RETRY_BASE_MS = 750L
        private const val ONLINE_PRIVATE_ROLE_RETRY_JITTER_MS = 750L
        private const val PLAYERS_REFRESH_RETRY_MS = 250L
        private const val ROLE_PRESET_SAVE_DELAY_MS = 900L
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
        data class Started(
            val mapKey: String,
            val roleSummary: String,
            val matchId: String,
            val realtimeAccess: Map<String, RealtimeRoomMemberAccess>
        ) : OnlineStartTransactionResult
    }

    private data class MapVoteViews(
        val shade: View,
        val overlay: View,
        val count: TextView,
        val voters: TextView,
        val defaultBadge: TextView
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
