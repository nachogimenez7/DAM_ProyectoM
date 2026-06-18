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
    private lateinit var mapName: TextView
    private lateinit var mapCards: List<ImageView>
    private lateinit var debugRoleButton: Button
    private lateinit var timingOptionsButton: Button
    private lateinit var lobbyTitle: TextView
    private lateinit var lobbyModeHint: TextView
    private lateinit var onlineCodePanel: LinearLayout
    private lateinit var onlineRoomCodeText: TextView
    private lateinit var btnCopyRoomCode: Button
    private lateinit var btnShareRoomCode: Button
    private lateinit var mapDescription: TextView
    private var lobbyMode = MODE_LOCAL
    private var onlineLobbyName = ""
    private var onlinePartidaId = ""
    private var onlineRoomCode = ""
    private var onlineRoomState = ONLINE_ROOM_STATE_WAITING
    private var onlineRoomModePrueba = false
    private var onlineRoomMaxPlayers = LocalGameFactory.MAX_PLAYERS
    private var onlineHostId = ""
    private var onlineInitialMatch: Map<String, Any?>? = null
    private var onlineStartedNoticeShown = false
    private var onlineRoomDeletedHandled = false
    private var leavingOnlineLobby = false
    private var enteringOnlineMatch = false
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

        session = readSession() ?: LocalGameFactory.createSession()
        lobbyMode = intent.getStringExtra(EXTRA_LOBBY_MODE) ?: MODE_LOCAL
        onlineLobbyName = intent.getStringExtra(EXTRA_LOBBY_NAME).orEmpty()
        onlinePartidaId = intent.getStringExtra(EXTRA_PARTIDA_ID).orEmpty()
        onlineRoomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
        if (onlinePartidaId.isNotBlank()) {
            onlineTempUid = OnlineTempIdentity.getOrCreate(this)
            onlinePlayerName = OnlineRoomFirestore.normalizedPlayerName(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
                    .orEmpty()
            )
        }

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val headerLabel: TextView = findViewById(R.id.headerLabel)
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
        mapDescription = findViewById(R.id.mapDescription)
        lobbyMapBackground = findViewById(R.id.lobbyMapBackground)
        mapName = findViewById(R.id.mapName)
        startButton = findViewById(R.id.btnStartGame)
        playersContainer = findViewById(R.id.playersContainer)
        playerCount = findViewById(R.id.playerCount)
        mapCards = listOf(
            findViewById(R.id.mapPampa),
            findViewById(R.id.mapGrecia),
            findViewById(R.id.mapMedieval)
        )

        btnBack.setOnClickListener { requestLobbyExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestLobbyExit()
            }
        })
        headerLabel.text = "MAPA"
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

        updateOnlineControlState()

        btnAddPlayer.setOnClickListener {
            val updated = LocalGameFactory.addMockPlayer(session)
            if (updated.players.size == session.players.size) {
                Toast.makeText(this, "Maximo ${LocalGameFactory.MAX_PLAYERS} jugadores en esta demo.", Toast.LENGTH_SHORT).show()
            }
            session = updated
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
            session = updated
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
        playerCount.text = "${session.players.size}/${currentMaxPlayers()} JUGADORES"
        lobbyTitle.text = when (lobbyMode) {
            MODE_ONLINE_CREATE -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "LOBBY ONLINE - ${it.uppercase()}" }
                ?: "LOBBY ONLINE - TU SALA"
            MODE_ONLINE_SEARCH -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "LOBBY ONLINE - ${it.uppercase()}" }
                ?: "LOBBY ONLINE - SALA ENCONTRADA"
            MODE_ONLINE_QUICK -> "LOBBY ONLINE - PARTIDA RAPIDA"
            else -> "LOBBY LOCAL"
        }
        lobbyModeHint.text = when (lobbyMode) {
            MODE_ONLINE_CREATE, MODE_ONLINE_SEARCH -> onlineLobbyHint()
            MODE_ONLINE_QUICK ->
                "Partida completa. Comenzara cuando el anfitrion confirme."
            else ->
                "Elegi mapa, tiempos y participantes antes de iniciar."
        }
        renderOnlineCodePanel()
        mapDescription.text = mapDescriptionFor(session.mapKey)
        renderStartButtonState()
        mapName.text = session.mapName.uppercase()
        lobbyMapBackground.setImageResource(currentMap().imageRes)
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

        session.players.forEachIndexed { index, player ->
            val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
            val onlinePlayer = onlinePlayers.getOrNull(index)
            row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
            row.findViewById<TextView>(R.id.playerName).text = player.name
            row.findViewById<TextView>(R.id.playerStatus).text =
                onlinePlayer?.statusLabel() ?: if (index == 0) "Anfitrion" else "Listo"
            row.findViewById<ImageButton>(R.id.btnPlayerProfile).setOnClickListener {
                showPlayerProfile(index, player)
            }
            row.findViewById<ImageButton>(R.id.btnPlayerProfile).contentDescription =
                "Ver perfil de ${player.name}"
            row.findViewById<ImageButton>(R.id.btnKickPlayer).apply {
                isEnabled = index != 0 && !isOnlineGuest() && !isFirestoreOnlineLobby()
                alpha = if (isEnabled) 1f else 0.28f
                contentDescription = when {
                    index == 0 -> "El anfitrion no se puede expulsar"
                    isOnlineGuest() || isFirestoreOnlineLobby() ->
                        "La expulsion online todavia esta en desarrollo"
                    else -> "Expulsar a ${player.name}"
                }
                setOnClickListener { confirmPlayerRemoval(index, player) }
            }
            playersContainer.addView(row)
        }
    }

    private fun listenToOnlineRoom() {
        roomListener?.remove()
        roomListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error cargando sala online: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    handleDeletedOnlineRoom()
                    return@addSnapshotListener
                }
                applyOnlineRoomSnapshot(snapshot)
            }
    }

    private fun markOnlinePresence(state: String) {
        if (onlinePartidaId.isBlank() || onlineTempUid.isBlank()) return
        val firestore = FirebaseFirestore.getInstance()
        val playerData = hashMapOf<String, Any>(
            FIELD_NAME to onlinePlayerName,
            FIELD_PLAYER_STATE to state,
            "uidTemporal" to onlineTempUid,
            OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
        )
        firestore
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(playerData, SetOptions.merge())

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
        val title = if (isHost) "Salir de la sala online" else "Salir del lobby"
        val message = if (isHost) {
            "Si salis, la sala quedara abandonada y ya no aparecera en buscar partida."
        } else {
            "Vas a salir de la sala. El resto de jugadores seguira en el lobby."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("SALIR") { _, _ ->
                leavingOnlineLobby = true
                finish()
            }
            .show()
    }

    private fun listenToOnlinePlayers() {
        playersListener?.remove()
        playersListener = FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error cargando jugadores: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                onlinePlayers = snapshot?.documents
                    ?.mapNotNull(::parseOnlinePlayer)
                    ?.sortedWith(
                        compareByDescending<OnlineLobbyPlayer> { it.isHost }
                            .thenBy { it.name.lowercase() }
                    )
                    .orEmpty()
                session = session.copy(
                    players = onlinePlayers.map { player ->
                        GamePlayer(
                            name = player.name,
                            initial = player.initial,
                            isHuman = player.id == onlineTempUid
                        )
                    }
                )
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
        onlineHostId = snapshot.getString(FIELD_HOST_ID).orEmpty()
        onlineRoomCode = snapshot.getString(FIELD_ROOM_CODE).orEmpty()
        onlineInitialMatch = snapshot.get(FIELD_INITIAL_MATCH).asStringAnyMap()

        val requestedMapKey = snapshot.getString(FIELD_MAP_KEY).orEmpty()
        val selectedMap = LocalGameFactory.maps.firstOrNull { it.key == requestedMapKey }
            ?: LocalGameFactory.maps.first()
        session = LocalGameFactory.selectMap(session, selectedMap.key)

        if (onlineRoomState == ONLINE_ROOM_STATE_IN_GAME && !onlineStartedNoticeShown) {
            onlineStartedNoticeShown = true
            startOnlineMatch()
        }
        renderLobby()
    }

    private fun parseOnlinePlayer(document: DocumentSnapshot): OnlineLobbyPlayer? {
        val name = document.getString(FIELD_NAME)
            ?.trim()
            ?.take(18)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val isHost = document.getBoolean(FIELD_IS_HOST) == true ||
            document.id == onlineHostId
        return OnlineLobbyPlayer(
            id = document.id,
            name = name,
            initial = name.firstOrNull()?.uppercase() ?: "?",
            isHost = isHost,
            status = document.getString(FIELD_PLAYER_STATE) ?: PLAYER_STATE_CONNECTED,
            ready = document.getBoolean(FIELD_PLAYER_READY) == true
        )
    }

    private fun handleDeletedOnlineRoom() {
        if (onlineRoomDeletedHandled) return
        onlineRoomDeletedHandled = true
        Toast.makeText(this, "La sala fue eliminada. Volviendo a buscar partida.", Toast.LENGTH_LONG).show()
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
            ONLINE_ROOM_STATE_IN_GAME -> "Partida iniciada proximamente.$codeText"
            else -> "Lobby online conectado a Firebase.$codeText"
        }
    }

    private fun currentMaxPlayers(): Int {
        return if (isFirestoreOnlineLobby()) {
            onlineRoomMaxPlayers
        } else {
            LocalGameFactory.MAX_PLAYERS
        }
    }

    private fun currentUserIsOnlineHost(): Boolean {
        return onlineHostId == onlineTempUid ||
            (onlineHostId.isBlank() && lobbyMode == MODE_ONLINE_CREATE)
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
        startButton.isEnabled = onlineRoomState == ONLINE_ROOM_STATE_WAITING &&
            onlinePlayers.isNotEmpty() &&
            (canStart || currentPlayer != null)
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        startButton.text = when {
            canStart -> "INICIAR ONLINE"
            currentReady -> "NO LISTO"
            currentUserIsOnlineHost() -> "LISTO HOST"
            else -> "LISTO"
        }
        startButton.contentDescription = when {
            canStart -> "Iniciar partida online para todos los jugadores"
            currentUserIsOnlineHost() && onlinePlayers.size < minimumOnlinePlayersToStart() ->
                "Faltan jugadores para iniciar la partida online"
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
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .collection(ONLINE_PLAYERS_COLLECTION)
            .document(onlineTempUid)
            .set(
                mapOf(
                    FIELD_NAME to onlinePlayerName,
                    FIELD_PLAYER_STATE to PLAYER_STATE_CONNECTED,
                    FIELD_PLAYER_READY to nextReady,
                    "uidTemporal" to onlineTempUid,
                    OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                Toast.makeText(this, "No se pudo actualizar listo: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun startOnlineRoomForEveryone() {
        if (!currentUserIsOnlineHost()) {
            Toast.makeText(this, "Solo el anfitrion puede iniciar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!onlineRoomCanStart()) {
            val message = if (onlinePlayers.size < minimumOnlinePlayersToStart()) {
                "Faltan jugadores para iniciar."
            } else {
                "Todavia faltan jugadores listos."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        startButton.isEnabled = false
        startButton.text = "INICIANDO..."
        val initialSession = buildOnlineBaseSession()
        val assignedSession = LocalGameFactory.assignRoles(initialSession)
        val initialMatch = initialMatchPayload(assignedSession)
        val matchState = matchStatePayload(assignedSession)
        FirebaseFirestore.getInstance()
            .collection(ONLINE_ROOMS_COLLECTION)
            .document(onlinePartidaId)
            .set(
                mapOf(
                    FIELD_STATE to ONLINE_ROOM_STATE_IN_GAME,
                    FIELD_INITIAL_MATCH to initialMatch,
                    FIELD_MATCH_STATE to matchState,
                    OnlineRoomFirestore.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                startButton.isEnabled = true
                renderStartButtonState()
                Toast.makeText(this, "No se pudo iniciar online: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun startOnlineMatch() {
        val sharedSession = onlineInitialMatch?.let(::sessionFromInitialMatch)
        if (sharedSession == null) {
            if (currentUserIsOnlineHost()) {
                Toast.makeText(this, "Preparando partida online...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Esperando datos de partida online...", Toast.LENGTH_LONG).show()
            }
            onlineStartedNoticeShown = false
            return
        }
        enteringOnlineMatch = true
        Toast.makeText(this, "Partida online iniciada.", Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, AssigningRolesActivity::class.java)
                .putExtra(EXTRA_SESSION, sharedSession)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_PARTIDA_ID, onlinePartidaId)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_PLAYER_ID, onlineTempUid)
                .putExtra(AssigningRolesActivity.EXTRA_ONLINE_IS_HOST, currentUserIsOnlineHost())
        )
    }

    private fun buildOnlineBaseSession(): GameSession {
        val realPlayers = onlinePlayers.map { player ->
            GamePlayer(
                name = player.name,
                initial = player.initial,
                isHuman = player.id == onlineTempUid
            )
        }
        val map = currentMap()
        var baseSession = GameSession(
            code = onlineRoomCode.ifBlank { onlinePartidaId.take(6) },
            mapKey = map.key,
            mapName = map.name,
            players = realPlayers,
            revealRolesOnDeath = session.revealRolesOnDeath,
            showIndividualVotes = session.showIndividualVotes
        )
        if (onlineRoomModePrueba) {
            while (baseSession.players.size < LocalGameFactory.MIN_PLAYERS) {
                val next = LocalGameFactory.addMockPlayer(baseSession)
                if (next.players.size == baseSession.players.size) break
                baseSession = next
            }
        }
        return GameSession(
            code = onlineRoomCode.ifBlank { onlinePartidaId.take(6) },
            mapKey = map.key,
            mapName = map.name,
            players = baseSession.players,
            revealRolesOnDeath = session.revealRolesOnDeath,
            showIndividualVotes = session.showIndividualVotes,
            roleComposition = LocalGameFactory.normalizedRoleComposition(
                session.copy(players = baseSession.players, mapKey = map.key, mapName = map.name)
            )
        )
    }

    private fun initialMatchPayload(assignedSession: GameSession): Map<String, Any?> {
        val uidByPlayerName = onlinePlayers.associateBy { it.name }
        return mapOf(
            "codigoSala" to assignedSession.code,
            "mapa" to assignedSession.mapKey,
            "mapaNombre" to assignedSession.mapName,
            "fase" to assignedSession.phase.name,
            "ronda" to assignedSession.round,
            "creadaEnLocal" to System.currentTimeMillis(),
            "jugadores" to assignedSession.players.mapIndexed { index, player ->
                val onlinePlayer = uidByPlayerName[player.name]
                mapOf(
                    "orden" to index,
                    "uidTemporal" to onlinePlayer?.id.orEmpty(),
                    "simulado" to (onlinePlayer == null),
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
        val playerPayloads = payload["jugadores"] as? List<*> ?: return null
        val players = playerPayloads
            .mapNotNull { it as? Map<*, *> }
            .sortedBy { (it["orden"] as? Number)?.toInt() ?: Int.MAX_VALUE }
            .mapNotNull { playerMap ->
                val name = (playerMap["nombre"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val roleKey = (playerMap["rolKey"] as? String).orEmpty()
                val roleName = (playerMap["rolNombre"] as? String).orEmpty()
                val roleTeam = (playerMap["rolEquipo"] as? String).orEmpty()
                val roleImage = (playerMap["rolImagen"] as? String).orEmpty()
                GamePlayer(
                    name = name,
                    initial = (playerMap["inicial"] as? String)?.takeIf { it.isNotBlank() }
                        ?: name.firstOrNull()?.uppercase()
                        ?: "?",
                    role = if (roleKey.isNotBlank()) {
                        GameRole(
                            key = roleKey,
                            name = roleName.ifBlank { roleKey },
                            team = roleTeam,
                            imageResName = roleImage
                        )
                    } else {
                        null
                    },
                    alive = true,
                    muted = false,
                    isHuman = (playerMap["uidTemporal"] as? String) == onlineTempUid
                )
            }
        if (players.isEmpty()) return null
        val mapKey = (payload["mapa"] as? String).orEmpty()
        val selectedMap = LocalGameFactory.maps.firstOrNull { it.key == mapKey } ?: currentMap()
        val base = GameSession(
            code = (payload["codigoSala"] as? String)?.takeIf { it.isNotBlank() }
                ?: onlineRoomCode.ifBlank { onlinePartidaId.take(6) },
            mapKey = selectedMap.key,
            mapName = (payload["mapaNombre"] as? String)?.takeIf { it.isNotBlank() } ?: selectedMap.name,
            players = players,
            phase = GamePhase.REPARTO,
            round = (payload["ronda"] as? Number)?.toInt() ?: 1,
            roleComposition = LocalGameFactory.normalizedRoleComposition(
                session.copy(players = players, mapKey = selectedMap.key, mapName = selectedMap.name)
            ),
            revealRolesOnDeath = session.revealRolesOnDeath,
            showIndividualVotes = session.showIndividualVotes,
            initialPlayerCount = players.size,
            startedAtEpochMs = System.currentTimeMillis()
        )
        val human = players.firstOrNull { it.isHuman } ?: players.first()
        val publicStart = "Dios preparo una partida online con roles ocultos."
        val privateStart = "Tu rol: ${human.role?.name ?: "desconocido"}."
        return base.copy(
            publicAnnouncement = publicStart,
            privateHint = privateStart,
            publicHistory = listOf(publicStart),
            godHistory = listOf(publicStart),
            desertorTeam = initialOnlineDesertorTeam(players, base.code)
        )
    }

    private fun initialOnlineDesertorTeam(players: List<GamePlayer>, sessionCode: String): String {
        val desertor = players.firstOrNull { it.role?.key == RoleCatalog.DESERTOR } ?: return ""
        if (desertor.isHuman) return ""
        return if (sessionCode.hashCode() and 1 == 0) GameRules.TOWN_WINNER else GameRules.TRAITOR_WINNER
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
        return onlinePlayers.firstOrNull { it.id == onlineTempUid }
    }

    private fun allOnlinePlayersReady(): Boolean {
        return onlinePlayers.isNotEmpty() &&
            onlinePlayers.all { it.status == PLAYER_STATE_CONNECTED && it.ready }
    }

    private fun onlineRoomCanStart(): Boolean {
        return onlinePlayers.size >= minimumOnlinePlayersToStart() && allOnlinePlayersReady()
    }

    private fun minimumOnlinePlayersToStart(): Int {
        return if (onlineRoomModePrueba) 1 else LocalGameFactory.MIN_PLAYERS
    }

    private fun updateOnlineControlState() {
        val disabled = isOnlineGuest() || isFirestoreOnlineLobby()
        listOf(btnAddPlayer, btnRemovePlayer, timingOptionsButton, btnAdvancedOptions).forEach { button ->
            button.isEnabled = !disabled
            button.alpha = if (disabled) 0.55f else 1f
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
                session = LocalGameFactory.selectMap(session, map.key).let {
                    it.copy(
                        roleComposition = LocalGameFactory.defaultRoleComposition(
                            it.players.size,
                            it.mapKey
                        )
                    )
                }
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
                "Configuracion personalizada. Podes ajustar cada tiempo manualmente."
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
        var revealRolesOnDeath = session.revealRolesOnDeath
        var showIndividualVotes = session.showIndividualVotes
        var quickTestMode = session.quickTestMode
        var debugBotsObeyVoteCommands = session.debugBotsObeyVoteCommands
        var debugForceVoteTies = session.debugForceVoteTies
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
            text = "Activado: permite saltear fases sin accion humana y acelera votaciones. " +
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
                text = "Fuerza un empate en la votacion inicial y vuelve a empatar el desempate."
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
                    debugForceVoteTies = debugForceVoteTies
                )
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 640)
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

    private fun showPlayerProfile(index: Int, player: GamePlayer) {
        val onlinePlayer = onlinePlayers.getOrNull(index)
        val status = when {
            onlinePlayer != null -> onlinePlayer.statusLabel()
            index == 0 -> "Anfitrion de la sala"
            else -> "Participante listo"
        }
        val type = when {
            onlinePlayer != null -> "Jugador online"
            player.isHuman -> "Tu perfil"
            lobbyMode != MODE_LOCAL -> "Participante simulado"
            else -> "Bot local"
        }
        AlertDialog.Builder(this)
            .setTitle(player.name)
            .setMessage("$status\n$type\nMapa: ${session.mapName}")
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun confirmPlayerRemoval(index: Int, player: GamePlayer) {
        if (isOnlineGuest() || isFirestoreOnlineLobby()) {
            Toast.makeText(this, "La expulsion online todavia esta en desarrollo.", Toast.LENGTH_SHORT).show()
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
                session = LocalGameFactory.removePlayer(session, index)
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
                "Intriga entre templos y plazas. Rol exclusivo: Oraculo."
            "medieval" ->
                "Secretos entre murallas y castillos. Rol exclusivo: Bufon."
            else ->
                "Sospechas en la pampa y el pueblo. Rol exclusivo: Payador."
        }
    }

    private fun isOnlineGuest(): Boolean {
        return lobbyMode == MODE_ONLINE_SEARCH || lobbyMode == MODE_ONLINE_QUICK
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
        const val MODE_LOCAL = "local"
        const val MODE_ONLINE_CREATE = "online_create"
        const val MODE_ONLINE_SEARCH = "online_search"
        const val MODE_ONLINE_QUICK = "online_quick"
        private const val ONLINE_ROOMS_COLLECTION = "partidas"
        private const val ONLINE_PLAYERS_COLLECTION = "jugadores"
        private const val ONLINE_ROOM_STATE_WAITING = "esperando"
        private const val ONLINE_ROOM_STATE_IN_GAME = "en_juego"
        private const val ONLINE_ROOM_STATE_ABANDONED = "abandonada"
        private const val FIELD_NAME = "nombre"
        private const val FIELD_STATE = "estado"
        private const val FIELD_TEST_MODE = "modoPrueba"
        private const val FIELD_MAX_PLAYERS = "maxJugadores"
        private const val FIELD_HOST_ID = "hostId"
        private const val FIELD_ROOM_CODE = "codigoSala"
        private const val FIELD_MAP_KEY = "mapa"
        private const val FIELD_INITIAL_MATCH = "partidaInicial"
        private const val FIELD_MATCH_STATE = "estadoPartida"
        private const val FIELD_IS_HOST = "esHost"
        private const val FIELD_PLAYER_STATE = "estado"
        private const val FIELD_PLAYER_READY = "listo"
        private const val PLAYER_STATE_CONNECTED = "conectado"
        private const val PLAYER_STATE_DISCONNECTED = "desconectado"
        private const val PREFS_NAME = "TraidoresPrefs"
        private const val PREF_ROLE_READING_SECONDS = "role_reading_seconds"
        private const val DEFAULT_ROLE_READING_SECONDS = 6
    }

    private data class OnlineLobbyPlayer(
        val id: String,
        val name: String,
        val initial: String,
        val isHost: Boolean,
        val status: String,
        val ready: Boolean
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
