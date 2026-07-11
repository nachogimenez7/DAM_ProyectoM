package com.traidores.juego

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.google.firebase.firestore.SetOptions

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
    private lateinit var mapDescription: TextView
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
    private var onlineInitialMatch: Map<String, Any?>? = null
    private var onlineMatchState: Map<String, Any?>? = null
    private var onlineStartedNoticeShown = false
    private var recoveringOnlineMatch = false
    private var onlineRoomDeletedHandled = false
    private var onlineRemovalHandled = false
    private var leavingOnlineLobby = false
    private var enteringOnlineMatch = false
    private var onlineHostHandoffInProgress = false
    private var roomListener: ListenerRegistration? = null
    private var playersListener: ListenerRegistration? = null
    private var onlinePlayers = emptyList<OnlineLobbyPlayer>()
    private var onlineTempUid = ""
    private var onlinePlayerName = ""
    private var debugRoleIndex = 0

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
        mapDescription = findViewById(R.id.mapDescription)
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

        btnBack.setOnClickListener { requestLobbyExit() }
        btnLobbySettings.setOnClickListener { showLobbyOptionsDialog() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestLobbyExit()
            }
        })
        setupMapSelector()
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        debugRoleSection.visibility = if (isDebugBuild && lobbyMode == MODE_LOCAL) {
            View.VISIBLE
        } else {
            View.GONE
        }
        debugRoleButton.setOnClickListener {
            debugRoleIndex = (debugRoleIndex + 1) % debugRoles.size
            renderDebugRole()
        }
        timingOptionsButton.setOnClickListener { showTimingDialog() }
        btnAdvancedOptions.setOnClickListener { showAdvancedOptionsDialog() }
        btnCopyRoomCode.setOnClickListener { copyOnlineRoomCode() }
        btnShareRoomCode.setOnClickListener { shareOnlineRoomCode() }
        btnReleaseDisconnected.setOnClickListener { releaseDisconnectedOnlinePlayers() }

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
            markOnlinePresence(PLAYER_STATE_CONNECTED)
            listenToOnlineRoom()
            listenToOnlinePlayers()
        }
    }

    override fun onStop() {
        if (isFirestoreOnlineLobby()) {
            if (!enteringOnlineMatch) {
                markOnlinePresence(PLAYER_STATE_DISCONNECTED)
            }
        }
        roomListener?.remove()
        playersListener?.remove()
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
        playerCount.text = "${currentVisiblePlayerCount()}/${currentMaxPlayers()} JUGADORES"
        lobbyTitle.text = when (lobbyMode) {
            MODE_ONLINE_CREATE -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "LOBBY ONLINE - ${it.uppercase()}" }
                ?: "LOBBY ONLINE - TU SALA"
            MODE_ONLINE_SEARCH -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "LOBBY ONLINE - ${it.uppercase()}" }
                ?: "LOBBY ONLINE - SALA ENCONTRADA"
            else -> "JUGAR vs IA"
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
        mapDescription.text = mapDescriptionFor(session.mapKey)
        renderStartButtonState()
        val currentMap = currentMap()
        selectedMapName.text = currentMap.name.uppercase()
        selectedMapRole.text = selectedMapRoleLabel(session.mapKey)
        selectedMapImage.setImageResource(currentMap.imageRes)
        lobbyMapBackground.setImageResource(currentMap.imageRes)
        mapCards.forEachIndexed { index, imageView ->
            val selected = LocalGameFactory.maps[index].key == session.mapKey
            imageView.alpha = if (selected) 1f else 0.55f
            imageView.setBackgroundResource(if (selected) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark)
            val map = LocalGameFactory.maps[index]
            imageView.contentDescription = if (selected) {
                "${map.name}, mapa seleccionado"
            } else if (isOnlineGuest() || isFirestoreOnlineLobby()) {
                "${map.name}, el mapa lo administra la sala online"
            } else {
                "Elegir ${map.name}"
            }
        }
        playersContainer.removeAllViews()
        renderDebugRole()
        timingOptionsButton.text = "TIEMPOS"

        val visibleOnlinePlayers = activeOnlinePlayers()
        session.players.forEachIndexed { index, player ->
            val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
            val onlinePlayer = visibleOnlinePlayers.getOrNull(index)
            row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
            row.findViewById<TextView>(R.id.playerName).text = player.name
            row.findViewById<TextView>(R.id.playerStatus).text =
                onlinePlayer?.statusLabel() ?: if (index == 0) "Anfitrion" else "Listo"
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
    }

    private fun listenToOnlineRoom() {
        roomListener?.remove()
        OnlineDebugLog.i("lobby_room_listen_start roomId=$onlinePartidaId uid=$onlineTempUid")
        roomListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .addSnapshotListener { snapshot, error ->
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

    private fun markOnlinePresence(state: String) {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        OnlineDebugLog.i("presence_update roomId=$onlinePartidaId uid=$onlineTempUid state=$state")
        val firestore = FirebaseFirestore.getInstance()
        val currentlyReleased = onlinePlayers.firstOrNull { it.id == onlineTempUid }?.activeInMatch == false
        val playerData = hashMapOf<String, Any>(
                    FIELD_NAME to onlinePlayerName,
                    PlayerPublicIdentity.FIELD_PUBLIC_ID to PlayerPublicIdentity.currentPublicId(this),
                    PlayerPublicIdentity.FIELD_PROFILE_NAME to onlinePlayerName,
                    PlayerPublicIdentity.FIELD_ROOM_NAME to RoomDisplayNames.withPublicId(
                        onlinePlayerName,
                        PlayerPublicIdentity.currentPublicId(this)
                    ),
                    FIELD_PLAYER_STATE to state,
                    "uidTemporal" to onlineTempUid,
            OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to !currentlyReleased,
            OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
            OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
        )
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
            firestore
                .collection(ONLINE_ROOMS_COLLECTION)
                .document(onlinePartidaId)
                .set(
                    mapOf(
                        FIELD_STATE to ONLINE_ROOM_STATE_ABANDONED,
                        OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener {
                    OnlineDebugLog.i("room_marked_abandoned roomId=$onlinePartidaId hostId=$onlineTempUid")
                }
                .addOnFailureListener { error ->
                    OnlineDebugLog.e("room_mark_abandoned_failure roomId=$onlinePartidaId hostId=$onlineTempUid", error)
                }
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
                onlinePlayers = snapshot?.documents
                    ?.mapNotNull(::parseOnlinePlayer)
                    ?.sortedWith(
                        compareBy<OnlineLobbyPlayer> { it.order }
                            .thenBy { it.name.lowercase() }
                            .thenBy { it.id }
                    )
                    .orEmpty()
                if (onlinePlayers.firstOrNull { it.id == onlineTempUid }?.activeInMatch == false) {
                    handleRemovedFromOnlineLobby()
                    return@addSnapshotListener
                }
                OnlineDebugLog.i(
                    "lobby_players_snapshot roomId=$onlinePartidaId players=${onlinePlayers.size} active=${activeOnlinePlayers().size} connected=${activeOnlinePlayers().count { it.status == PLAYER_STATE_CONNECTED }}"
                )
                val visiblePlayers = activeOnlinePlayers()
                session = PlayerProfileStore.withProfiles(this, session.copy(
                    players = visiblePlayers.map { player ->
                        GamePlayer(
                            name = player.name,
                            initial = player.initial,
                            isHuman = player.id == onlineTempUid
                        )
                    }
                ))
                maybeClaimOnlineLobbyHostHandoff()
                renderLobby()
            }
    }

    private fun applyOnlineRoomSnapshot(snapshot: DocumentSnapshot) {
        onlineLobbyName = snapshot.getString(FIELD_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: onlineLobbyName
        onlineRoomState = snapshot.getString(FIELD_STATE) ?: ONLINE_ROOM_STATE_WAITING
        onlineRoomModePrueba = snapshot.getBoolean(FIELD_TEST_MODE) ?: false
        onlineRoomMaxPlayers = snapshot.getLong(FIELD_MAX_PLAYERS)
            ?.toInt()
            ?.coerceIn(1, LocalGameFactory.MAX_PLAYERS)
            ?: LocalGameFactory.MAX_PLAYERS
        onlineExpectedPlayers = snapshot.getLong(FIELD_EXPECTED_PLAYERS)
            ?.toInt()
            ?.coerceIn(LocalGameFactory.MIN_PLAYERS, LocalGameFactory.MAX_PLAYERS)
            ?: onlineRoomMaxPlayers.coerceIn(LocalGameFactory.MIN_PLAYERS, LocalGameFactory.MAX_PLAYERS)
        onlineHostId = snapshot.getString(FIELD_HOST_ID).orEmpty()
        onlineActiveHostId = snapshot.getString(FIELD_ACTIVE_HOST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: onlineHostId
        onlineHostVersion = snapshot.getLong(FIELD_HOST_VERSION)?.toInt() ?: 0
        onlineRoomCode = snapshot.getString(FIELD_ROOM_CODE).orEmpty()
        onlineInitialMatchCreated = snapshot.getBoolean(FIELD_INITIAL_MATCH_CREATED) == true
        onlineInitialMatch = snapshot.get(FIELD_INITIAL_MATCH).asStringAnyMap()
        onlineMatchState = snapshot.get(FIELD_MATCH_STATE).asStringAnyMap()
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

        if (onlineRoomState == ONLINE_ROOM_STATE_IN_GAME && !onlineStartedNoticeShown) {
            onlineStartedNoticeShown = true
            startOnlineMatch()
        }
        maybeClaimOnlineLobbyHostHandoff()
        renderLobby()
    }

    private fun parseOnlinePlayer(document: DocumentSnapshot): OnlineLobbyPlayer? {
        val name = (document.getString(PlayerPublicIdentity.FIELD_ROOM_NAME)
            ?: document.getString(FIELD_NAME))
            ?.trim()
            ?.take(18)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val isHost = document.id == onlineActiveHostId.ifBlank { onlineHostId }
        return OnlineLobbyPlayer(
            id = document.id,
            name = name,
            initial = name.firstOrNull()?.uppercase() ?: "?",
            isHost = isHost,
            status = document.getString(FIELD_PLAYER_STATE) ?: PLAYER_STATE_CONNECTED,
            ready = document.getBoolean(FIELD_PLAYER_READY) == true,
            order = document.getLong(FIELD_PLAYER_ORDER)?.toInt() ?: Int.MAX_VALUE,
            activeInMatch = document.getBoolean(FIELD_ACTIVE_IN_MATCH) != false,
            lastSeenLocalMs = document.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL) ?: 0L,
            publicId = document.getString(PlayerPublicIdentity.FIELD_PUBLIC_ID).orEmpty()
        )
    }

    private fun onlineParticipants(): List<OnlineLobbyParticipant> {
        return onlinePlayers.map { player ->
            OnlineLobbyParticipant(
                id = player.id,
                connected = player.status == PLAYER_STATE_CONNECTED,
                ready = player.ready,
                activeInMatch = player.activeInMatch,
                order = player.order,
                lastSeenLocalMs = player.lastSeenLocalMs
            )
        }
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
        return onlineActiveHostId == onlineTempUid ||
            (onlineActiveHostId.isBlank() && onlineHostId == onlineTempUid) ||
            (onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE)
    }

    private fun onlineLobbyHostHandoffCandidate(excludeCurrent: Boolean = false): OnlineLobbyPlayer? {
        if (excludeCurrent) {
            return activeOnlinePlayers()
                .filter { it.id != onlineTempUid && it.status == PLAYER_STATE_CONNECTED }
                .minWithOrNull(compareBy<OnlineLobbyPlayer> { it.order }.thenBy { it.id })
        }
        val candidateId = OnlineLobbyRules.hostHandoffCandidate(
            players = onlineParticipants(),
            activeHostId = onlineActiveHostId.ifBlank { onlineHostId },
            nowMs = System.currentTimeMillis()
        )?.id ?: return null
        return onlinePlayers.firstOrNull { it.id == candidateId }
    }

    private fun maybeClaimOnlineLobbyHostHandoff() {
        if (
            !isFirestoreOnlineLobby() ||
            onlineRoomState != ONLINE_ROOM_STATE_WAITING ||
            onlineHostHandoffInProgress ||
            onlineTempUid.isBlank()
        ) {
            return
        }
        val previousHostId = onlineActiveHostId.ifBlank { onlineHostId }
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
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                return@runTransaction false
            }
            val currentHostId = room.getString(FIELD_ACTIVE_HOST_ID)
                ?.takeIf { it.isNotBlank() }
                ?: previousHostId
            if (currentHostId != previousHostId) {
                return@runTransaction false
            }
            val previousHost = transaction.get(previousHostReference)
            val candidate = transaction.get(candidateReference)
            val previousHostParticipant = OnlineLobbyParticipant(
                id = previousHostId,
                connected = previousHost.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED,
                ready = previousHost.getBoolean(FIELD_PLAYER_READY) == true,
                activeInMatch = previousHost.getBoolean(FIELD_ACTIVE_IN_MATCH) != false,
                order = previousHost.getLong(FIELD_PLAYER_ORDER)?.toInt() ?: Int.MAX_VALUE,
                lastSeenLocalMs = previousHost.getLong(OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL) ?: 0L
            )
            if (OnlineLobbyRules.isRecentlyConnected(previousHostParticipant, System.currentTimeMillis())) {
                return@runTransaction false
            }
            if (candidate.getString(FIELD_PLAYER_STATE) != PLAYER_STATE_CONNECTED) {
                return@runTransaction false
            }
            if (candidate.getBoolean(FIELD_ACTIVE_IN_MATCH) == false) {
                return@runTransaction false
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_ACTIVE_HOST_ID to onlineTempUid,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener { claimed ->
            onlineHostHandoffInProgress = false
            if (claimed == true) {
                onlineActiveHostId = onlineTempUid
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
            if (candidateSnapshot.getString(FIELD_PLAYER_STATE) != PLAYER_STATE_CONNECTED) {
                throw IllegalStateException("El nuevo anfitrion ya no esta conectado.")
            }
            if (candidateSnapshot.getBoolean(FIELD_ACTIVE_IN_MATCH) == false) {
                throw IllegalStateException("El nuevo anfitrion ya no esta activo.")
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_ACTIVE_HOST_ID to candidate.id,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener {
            onlineHostHandoffInProgress = false
            onlineActiveHostId = candidate.id
            leavingOnlineLobby = false
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

        val currentPlayer = currentOnlinePlayer()
        val currentReady = currentPlayer?.ready == true
        val canStart = currentUserIsOnlineHost() && onlineRoomCanStart()
        val activePlayers = activeOnlinePlayers()
        val missingPlayers = (onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)
        val disconnectedPlayers = activePlayers.count { it.status != PLAYER_STATE_CONNECTED }
        val missingReady = activePlayers.count {
            it.status == PLAYER_STATE_CONNECTED && !it.ready
        }
        startButton.isEnabled = onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            activePlayers.isNotEmpty() &&
            (canStart || currentPlayer != null)
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        startButton.text = when {
            canStart -> "INICIAR ONLINE"
            currentUserIsOnlineHost() && missingPlayers > 0 -> "FALTAN $missingPlayers"
            currentUserIsOnlineHost() && disconnectedPlayers > 0 -> "SINCRONIZANDO"
            currentUserIsOnlineHost() && missingReady > 0 -> "FALTAN LISTOS"
            currentUserIsOnlineHost() && onlineInitialMatchCreated -> "SINCRONIZANDO"
            currentReady -> "NO LISTO"
            currentUserIsOnlineHost() -> "LISTO HOST"
            else -> "LISTO"
        }
        startButton.contentDescription = when {
            canStart -> "Iniciar partida online para todos los jugadores"
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
        if (currentUserIsOnlineHost() && onlineRoomCanStart()) {
            startOnlineRoomForEveryone()
        } else {
            toggleCurrentOnlineReady()
        }
    }

    private fun toggleCurrentOnlineReady() {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        val nextReady = !(currentOnlinePlayer()?.ready == true)
        OnlineDebugLog.i("ready_update_requested roomId=$onlinePartidaId uid=$onlineTempUid ready=$nextReady")
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(
                mapOf(
                    FIELD_NAME to onlinePlayerName,
                    PlayerPublicIdentity.FIELD_PUBLIC_ID to PlayerPublicIdentity.currentPublicId(this),
                    PlayerPublicIdentity.FIELD_PROFILE_NAME to onlinePlayerName,
                    PlayerPublicIdentity.FIELD_ROOM_NAME to RoomDisplayNames.withPublicId(
                        onlinePlayerName,
                        PlayerPublicIdentity.currentPublicId(this)
                    ),
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

    private fun startOnlineRoomForEveryone() {
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
            startOnlineMatch()
            return
        }
        startButton.isEnabled = false
        startButton.text = "INICIANDO..."
        val initialSession = buildOnlineBaseSession()
        val assignedSession = LocalGameFactory.assignRoles(initialSession)
        val activePlayersAtStart = activeOnlinePlayers()
        OnlineDebugLog.i(
            "online_start_requested roomId=$onlinePartidaId code=${onlineRoomCode.ifBlank { "-" }} hostId=$onlineTempUid expected=$onlineExpectedPlayers active=${activePlayersAtStart.size} players=${assignedSession.players.size} roles=${onlineRoleSummary(assignedSession)}"
        )
        val initialMatch = initialMatchPayload(assignedSession)
        val matchState = matchStatePayload(assignedSession)
        val firestore = FirebaseFirestore.getInstance()
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(onlinePartidaId)
        firestore.runTransaction { transaction ->
            val room = transaction.get(roomReference)
            if (!room.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (room.getBoolean(FIELD_INITIAL_MATCH_CREATED) == true || room.get(FIELD_INITIAL_MATCH) != null) {
                return@runTransaction OnlineStartTransactionResult.ALREADY_STARTED
            }
            if (room.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta esperando jugadores.")
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
            activePlayersAtStart.forEach { player ->
                val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION).document(player.id)
                val snapshot = transaction.get(playerReference)
                val stillActive = snapshot.getBoolean(FIELD_ACTIVE_IN_MATCH) != false
                val connected = snapshot.getString(FIELD_PLAYER_STATE) == PLAYER_STATE_CONNECTED
                val ready = snapshot.getBoolean(FIELD_PLAYER_READY) == true
                if (!snapshot.exists() || !stillActive || !connected || !ready) {
                    throw IllegalStateException("Todavia faltan jugadores listos.")
                }
            }
            transaction.update(
                roomReference,
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_IN_GAME,
                    FIELD_INITIAL_MATCH to initialMatch,
                    FIELD_MATCH_STATE to matchState,
                    FIELD_INITIAL_MATCH_CREATED to true,
                    FIELD_ACTIVE_HOST_ID to onlineTempUid,
                    FIELD_HOST_VERSION to FieldValue.increment(1),
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS to activePlayersAtStart.size,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            OnlineStartTransactionResult.STARTED
        }.addOnSuccessListener { result ->
            if (result == OnlineStartTransactionResult.ALREADY_STARTED) {
                OnlineDebugLog.w("online_start_already_created roomId=$onlinePartidaId hostId=$onlineTempUid")
                Toast.makeText(this, "La partida ya fue iniciada. Sincronizando...", Toast.LENGTH_SHORT).show()
                startOnlineMatch()
                return@addOnSuccessListener
            }
            OnlineDebugLog.i("online_start_success roomId=$onlinePartidaId hostId=$onlineTempUid")
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

    private fun buildOnlineBaseSession(): GameSession {
        val realPlayers = activeOnlinePlayers().map { player ->
            GamePlayer(
                name = player.name,
                initial = player.initial,
                isHuman = player.id == onlineTempUid
            )
        }
        val map = currentMap()
        return GameSession(
            code = onlineRoomCode.ifBlank { onlinePartidaId.take(6) },
            mapKey = map.key,
            mapName = map.name,
            players = realPlayers,
            timingConfig = session.timingConfig.normalized(),
            revealRolesOnDeath = session.revealRolesOnDeath,
            showIndividualVotes = session.showIndividualVotes,
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
        if (onlineInitialMatchCreated || onlineInitialMatch != null) {
            return null
        }
        val activePlayers = activeOnlinePlayers()
        val missing = (onlineExpectedPlayers - activePlayers.size).coerceAtLeast(0)
        if (missing > 0) return "Faltan $missing jugadores para iniciar."
        if (activePlayers.size != onlineExpectedPlayers) {
            return "La cantidad de jugadores no coincide con la sala."
        }
        val disconnected = activePlayers.count { it.status != PLAYER_STATE_CONNECTED }
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
        if (session.players.size < LocalGameFactory.MIN_PLAYERS) {
            return "La partida online quedo incompleta. Creen una sala nueva."
        }
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

    private fun allOnlinePlayersReady(): Boolean {
        return activeOnlinePlayers().isNotEmpty() &&
            activeOnlinePlayers().all { it.status == PLAYER_STATE_CONNECTED && it.ready }
    }

    private fun onlineRoomCanStart(): Boolean {
        return OnlineLobbyRules.canStart(
            players = onlineParticipants(),
            expectedPlayers = onlineExpectedPlayers,
            roomWaiting = onlineRoomState == ONLINE_ROOM_STATE_WAITING,
            initialMatchCreated = onlineInitialMatchCreated
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

        val hostOptionsVisible = !firestoreLobby || currentUserIsOnlineHost()
        listOf(timingOptionsButton, btnAdvancedOptions).forEach { button ->
            button.visibility = if (hostOptionsVisible) View.VISIBLE else View.GONE
            button.isEnabled = hostOptionsVisible
            button.alpha = if (hostOptionsVisible) 1f else 0.55f
        }
    }

    private fun setupMapSelector() {
        mapCards.forEachIndexed { index, imageView ->
            val map = LocalGameFactory.maps[index]
            imageView.setImageResource(map.imageRes)
            imageView.setOnClickListener {
                if (isOnlineGuest() || isFirestoreOnlineLobby()) {
                    Toast.makeText(
                        this,
                        "El mapa lo administra la sala online.",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun showAdvancedOptionsDialog() {
        if (isFirestoreOnlineLobby()) {
            showOnlineAdvancedOptionsDialog()
            return
        }
        var revealRolesOnDeath = session.revealRolesOnDeath
        var showIndividualVotes = session.showIndividualVotes
        var quickTestMode = session.quickTestMode
        var debugBotsObeyVoteCommands = session.debugBotsObeyVoteCommands
        var debugForceVoteTies = session.debugForceVoteTies
        var debugBotsNeverKillHuman = session.debugBotsNeverKillHuman
        var debugBotsNeverVoteHuman = session.debugBotsNeverVoteHuman
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
        val quickTestSwitch = SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "MODO TEST RAPIDO"
            isChecked = quickTestMode
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(2), dp(4), dp(4))
            setOnCheckedChangeListener { _, checked ->
                quickTestMode = checked
            }
        }
        content.addView(quickTestSwitch)
        content.addView(TextView(this).apply {
            text = "Activado: permite saltear fases sin acción humana y acelera votaciones. " +
                "Desactivado: respeta los tiempos del lobby para mostrar una partida real."
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
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebugBuild && lobbyMode == MODE_LOCAL) {
            content.addView(SwitchCompat(this).apply {
                applyTraidoresSwitchStyle()
                text = "IA DE PRUEBA: OBEDECER VOTOS"
                isChecked = debugBotsObeyVoteCommands
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setPadding(dp(4), dp(2), dp(4), dp(4))
                setOnCheckedChangeListener { _, checked ->
                    debugBotsObeyVoteCommands = checked
                }
            })
            content.addView(TextView(this).apply {
                text = "Reconoce ordenes del chat como \"votenme\" o \"voten a Nombre\"."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), 0, dp(4), dp(8))
            })
            content.addView(SwitchCompat(this).apply {
                applyTraidoresSwitchStyle()
                text = "PRUEBA: FORZAR EMPATES"
                isChecked = debugForceVoteTies
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setPadding(dp(4), dp(2), dp(4), dp(4))
                setOnCheckedChangeListener { _, checked ->
                    debugForceVoteTies = checked
                }
            })
            content.addView(TextView(this).apply {
                text = "Fuerza un empate en la votación inicial y vuelve a empatar el desempate."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), 0, dp(4), dp(8))
            })
            content.addView(SwitchCompat(this).apply {
                applyTraidoresSwitchStyle()
                text = "LOS BOTS NO ME MATAN"
                isChecked = debugBotsNeverKillHuman
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setPadding(dp(4), dp(2), dp(4), dp(4))
                setOnCheckedChangeListener { _, checked ->
                    debugBotsNeverKillHuman = checked
                }
            })
            content.addView(TextView(this).apply {
                text = "Debug local: los asesinos evitan matarte de noche si hay otro objetivo valido."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), 0, dp(4), dp(8))
            })
            content.addView(SwitchCompat(this).apply {
                applyTraidoresSwitchStyle()
                text = "LOS BOTS NO ME VOTAN"
                isChecked = debugBotsNeverVoteHuman
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setPadding(dp(4), dp(2), dp(4), dp(4))
                setOnCheckedChangeListener { _, checked ->
                    debugBotsNeverVoteHuman = checked
                }
            })
            content.addView(TextView(this).apply {
                text = "Debug local: los votos bot evitan al humano si queda otro candidato valido."
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                textSize = 11f
                setPadding(dp(4), 0, dp(4), dp(8))
            })
        }
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
                    quickTestMode = quickTestMode,
                    debugBotsObeyVoteCommands = debugBotsObeyVoteCommands,
                    debugForceVoteTies = debugForceVoteTies,
                    debugBotsNeverKillHuman = debugBotsNeverKillHuman,
                    debugBotsNeverVoteHuman = debugBotsNeverVoteHuman
                )
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 640)
    }

    private fun showOnlineAdvancedOptionsDialog() {
        var revealRolesOnDeath = session.revealRolesOnDeath
        var showIndividualVotes = session.showIndividualVotes
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES DE PARTIDA"))
        content.addView(SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "Mostrar roles al morir"
            isChecked = revealRolesOnDeath
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnCheckedChangeListener { _, checked -> revealRolesOnDeath = checked }
        })
        content.addView(SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            text = "Mostrar votos individuales"
            isChecked = showIndividualVotes
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnCheckedChangeListener { _, checked -> showIndividualVotes = checked }
        })
        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("APLICAR") { _, _ ->
                session = session.copy(
                    revealRolesOnDeath = revealRolesOnDeath,
                    showIndividualVotes = showIndividualVotes
                )
                renderLobby()
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 480)
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
            setPadding(dp(18), dp(10), dp(18), dp(4))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
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
        val availableWidth = resources.displayMetrics.widthPixels - dp(24)
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
        private const val FIELD_NAME = "nombre"
        private const val FIELD_STATE = "estado"
        private const val FIELD_TEST_MODE = "modoPrueba"
        private const val FIELD_MAX_PLAYERS = "maxJugadores"
        private const val FIELD_EXPECTED_PLAYERS = "jugadoresEsperados"
        private const val FIELD_HOST_ID = "hostId"
        private const val FIELD_ACTIVE_HOST_ID = "hostActivoId"
        private const val FIELD_HOST_VERSION = "hostVersion"
        private const val FIELD_INITIAL_MATCH_CREATED = "partidaInicialCreada"
        private const val FIELD_ROOM_CODE = "codigoSala"
        private const val FIELD_MAP_KEY = "mapa"
        private const val FIELD_INITIAL_MATCH = "partidaInicial"
        private const val FIELD_MATCH_STATE = "estadoPartida"
        private const val FIELD_IS_HOST = "esHost"
        private const val FIELD_PLAYER_STATE = "estado"
        private const val FIELD_PLAYER_READY = "listo"
        private const val FIELD_PLAYER_ORDER = "orden"
        private const val FIELD_ACTIVE_IN_MATCH = "activoEnPartida"
        private const val PLAYER_STATE_CONNECTED = "conectado"
        private const val PLAYER_STATE_DISCONNECTED = "desconectado"
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val PREF_ROLE_READING_SECONDS = "role_reading_seconds"
        private const val DEFAULT_ROLE_READING_SECONDS = 0
    }

    private data class OnlineLobbyPlayer(
        val id: String,
        val name: String,
        val initial: String,
        val isHost: Boolean,
        val status: String,
        val ready: Boolean,
        val order: Int,
        val activeInMatch: Boolean,
        val publicId: String,
        val lastSeenLocalMs: Long
    ) {
        fun statusLabel(): String {
            val baseStatus = if (isHost) {
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

    private enum class OnlineStartTransactionResult {
        STARTED,
        ALREADY_STARTED
    }

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
