package com.traidores.juego

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.traidores.juego.GameToast as Toast
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import java.util.Date

class LobbyBrowserActivity : BaseActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val firestoreUsage = OnlineFirestoreUsageMetrics.counter("buscador")
    private var lobbyListener: ListenerRegistration? = null
    private var lobbies = emptyList<OnlineLobby>()
    private var browserStarted = false
    private var queryGeneration = 0
    private var roomLimit = OnlineLobbySearchWindow.PAGE_SIZE
    private var hasMoreRooms = false
    private var refreshInProgress = false
    private var manualRefreshGeneration = 0
    private var lastManualRefreshMs: Long? = null
    private var serverSnapshotRevision = 0L
    private val serverClock = OnlineServerClock {
        if (browserStarted) {
            renderRefreshButton()
            listenForOnlineRooms()
        }
    }
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshBrowser = object : Runnable {
        override fun run() {
            val nowMs = serverClock.nowMs()
            lobbies = if (nowMs == null) emptyList() else lobbies.filter {
                OnlineRoomRetentionPolicy.isDiscoverable(
                    it.updatedAtMs,
                    nowMs,
                    it.players,
                    it.limit,
                    false,
                    allowFullForReturningMember = it.returningMember
                )
            }
            renderLobbyList()
            refreshHandler.postDelayed(this, 60_000L)
        }
    }

    private lateinit var lobbyList: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var refreshStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby_browser)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        lobbyList = findViewById(R.id.lobbyList)
        emptyState = findViewById(R.id.lobbyEmptyState)
        refreshButton = findViewById(R.id.btnRefreshLobbies)
        refreshStatus = findViewById(R.id.lobbyRefreshStatus)
        refreshButton.setOnClickListener { refreshRoomsFromServer() }
        renderRefreshButton()
        showBrowserMessage("Buscando partidas online...")
        renderLobbyList()
    }

    override fun onStart() {
        super.onStart()
        browserStarted = true
        serverClock.start()
        listenForOnlineRooms()
        refreshHandler.postDelayed(refreshBrowser, 60_000L)
    }

    override fun onStop() {
        browserStarted = false
        queryGeneration++
        manualRefreshGeneration++
        refreshInProgress = false
        refreshStatus.visibility = View.GONE
        serverClock.stop()
        lobbyListener?.remove()
        lobbyListener = null
        refreshHandler.removeCallbacks(refreshBrowser)
        super.onStop()
    }

    private fun listenForOnlineRooms() {
        lobbyListener?.remove()
        val serverNowMs = serverClock.nowMs()
        if (serverNowMs == null) {
            lobbies = emptyList()
            hasMoreRooms = false
            showBrowserMessage("Sincronizando salas disponibles...")
            renderLobbyList()
            return
        }
        val generation = ++queryGeneration
        OnlineDebugLog.i("lobby_browser_listen_start")
        firestoreUsage.listenerStarted("salas")
        lobbyListener = availableRoomsQuery(serverNowMs)
            .addSnapshotListener { snapshot, error ->
                if (!browserStarted || generation != queryGeneration) return@addSnapshotListener
                if (error != null) {
                    OnlineDebugLog.e("lobby_browser_listen_failure", error)
                    lobbies = emptyList()
                    showBrowserMessage(OnlineErrorMessages.forAction("No se pudieron cargar las salas", error))
                    renderLobbyList()
                    Toast.makeText(
                        this,
                        OnlineErrorMessages.forAction("Error cargando salas online", error),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                firestoreUsage.serverSnapshot(
                    "salas",
                    fromCache = snapshot.metadata.isFromCache,
                    pendingWrites = snapshot.metadata.hasPendingWrites(),
                    changedDocuments = snapshot.documentChanges.size,
                    resultDocuments = snapshot.size()
                )
                if (!snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                    serverSnapshotRevision++
                }
                applyRoomsSnapshot(snapshot)
                if (OnlineLobbySearchWindow.shouldExpand(roomLimit, snapshot.size(), lobbies.size) &&
                    !snapshot.metadata.isFromCache) {
                    roomLimit += OnlineLobbySearchWindow.PAGE_SIZE
                    refreshHandler.post { if (browserStarted && generation == queryGeneration) listenForOnlineRooms() }
                }
            }
    }

    private fun availableRoomsQuery(serverNowMs: Long): Query =
        firestore.collection(ONLINE_ROOMS_COLLECTION)
            .whereEqualTo(FIELD_STATE, ONLINE_ROOM_STATE_WAITING)
            .whereEqualTo(
                OnlineRoomFirestore.FIELD_VISIBILITY,
                OnlineRoomFirestore.VISIBILITY_PUBLIC
            )
            .whereGreaterThan(
                FIELD_UPDATED_AT,
                Date(serverNowMs - OnlineRoomRetentionPolicy.BROWSER_FRESH_FOR_MS)
            )
            .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
            .limit(roomLimit)

    private fun applyRoomsSnapshot(snapshot: QuerySnapshot) {
        lobbies = snapshot.documents.mapNotNull(::parseLobby)
            .sortedWith(compareByDescending<OnlineLobby> { it.players }.thenBy { it.name })
        hasMoreRooms = snapshot.size().toLong() >= roomLimit
        OnlineDebugLog.i("lobby_browser_snapshot rooms=${lobbies.size}")
        showBrowserMessage("Todavia no hay partidas online. Crea una sala o intenta mas tarde.")
        renderLobbyList()
    }

    private fun refreshRoomsFromServer() {
        if (!browserStarted || refreshInProgress) return
        val serverNowMs = serverClock.nowMs() ?: return
        val elapsedMs = SystemClock.elapsedRealtime()
        if (lastManualRefreshMs?.let { elapsedMs - it < MANUAL_REFRESH_INTERVAL_MS } == true) {
            Toast.makeText(this, R.string.lobby_refresh_wait, Toast.LENGTH_SHORT).show()
            return
        }
        lastManualRefreshMs = elapsedMs
        refreshInProgress = true
        val generation = ++manualRefreshGeneration
        val limitAtStart = roomLimit
        val revisionAtStart = serverSnapshotRevision
        refreshStatus.text = getString(R.string.lobby_refresh_loading)
        refreshStatus.visibility = View.VISIBLE
        renderRefreshButton()
        OnlineDebugLog.i("lobby_browser_manual_refresh_requested limit=$limitAtStart")
        availableRoomsQuery(serverNowMs).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                firestoreUsage.forcedQuery("salas_actualizar", resultDocuments = snapshot.size())
                if (!browserStarted || generation != manualRefreshGeneration) return@addOnSuccessListener
                // Una respuesta manual anterior no debe reemplazar cambios más recientes del listener.
                if (limitAtStart == roomLimit && revisionAtStart == serverSnapshotRevision) {
                    applyRoomsSnapshot(snapshot)
                }
                refreshInProgress = false
                refreshStatus.text = getString(R.string.lobby_refresh_done)
                renderRefreshButton()
                OnlineDebugLog.i("lobby_browser_manual_refresh_success documents=${snapshot.size()}")
            }
            .addOnFailureListener { error ->
                if (!browserStarted || generation != manualRefreshGeneration) return@addOnFailureListener
                refreshInProgress = false
                refreshStatus.text = getString(R.string.lobby_refresh_failed)
                renderRefreshButton()
                OnlineDebugLog.e("lobby_browser_manual_refresh_failure", error)
                // Mantener las filas existentes para poder reintentar sin perder la lista.
            }
    }

    private fun renderRefreshButton() {
        refreshButton.isEnabled = !refreshInProgress && serverClock.nowMs() != null
        refreshButton.alpha = if (refreshButton.isEnabled) 1f else 0.45f
        refreshButton.contentDescription = getString(
            if (refreshInProgress) R.string.lobby_refresh_loading else R.string.lobby_refresh_action
        )
    }

    private fun parseLobby(document: DocumentSnapshot): OnlineLobby? {
        val status = document.getString(FIELD_STATE) ?: return null
        if (status != ONLINE_ROOM_STATE_WAITING) return null
        if (
            OnlineRoomFirestore.normalizedVisibility(
                document.getString(OnlineRoomFirestore.FIELD_VISIBILITY).orEmpty()
            ) != OnlineRoomFirestore.VISIBILITY_PUBLIC
        ) return null
        val updatedAtMs = document.getTimestamp(FIELD_UPDATED_AT)?.toDate()?.time ?: 0L
        val mapKey = document.getString(FIELD_MAP_KEY) ?: DEFAULT_MAP_KEY
        val mapName = document.getString(FIELD_MAP_NAME)
            ?: LocalGameFactory.maps.firstOrNull { it.key == mapKey }?.name
            ?: "Pampa"
        val players = document.getLong(FIELD_CURRENT_PLAYERS)?.toInt() ?: 0
        val limit = OnlineLobbyRules.displayedPlayerLimit(
            expectedPlayers = document.getLong(FIELD_EXPECTED_PLAYERS)?.toInt(),
            maximumPlayers = document.getLong(FIELD_MAX_PLAYERS)?.toInt(),
            fallback = DEFAULT_MAX_PLAYERS
        )
        val serverNowMs = serverClock.nowMs() ?: return null
        val returningMember = OnlineRoomRecovery.load(this)?.roomId == document.id
        if (!OnlineRoomRetentionPolicy.isDiscoverable(
                updatedAtMs = updatedAtMs,
                nowMs = serverNowMs,
                currentPlayers = players,
                playerLimit = limit,
                deleting = document.getString("cleanupState") == "deleting",
                allowFullForReturningMember = returningMember
            )) return null
        return OnlineLobby(
            id = document.id,
            code = document.getString(FIELD_ROOM_CODE).orEmpty(),
            name = document.getString(FIELD_NAME)?.takeIf { it.isNotBlank() } ?: "Sala online",
            players = players.coerceAtLeast(0),
            updatedAtMs = updatedAtMs,
            limit = limit,
            mapName = "Mapa $mapName",
            status = when {
                returningMember -> "Tu sala"
                players >= limit -> "Llena"
                else -> "Esperando"
            },
            mapKey = mapKey,
            canJoin = players < limit || returningMember,
            returningMember = returningMember
        )
    }

    private fun renderLobbyList() {
        lobbyList.removeAllViews()
        emptyState.visibility = if (lobbies.isEmpty()) View.VISIBLE else View.GONE
        lobbies.forEach { lobby ->
            lobbyList.addView(createLobbyRow(lobby))
        }
        if (hasMoreRooms) lobbyList.addView(Button(this).apply {
            text = "BUSCAR MÁS SALAS"
            setOnClickListener {
                isEnabled = false
                roomLimit += OnlineLobbySearchWindow.PAGE_SIZE
                listenForOnlineRooms()
            }
        })
    }

    private fun createLobbyRow(lobby: OnlineLobby): View {
        val row = layoutInflater.inflate(R.layout.item_online_lobby, lobbyList, false)
        row.findViewById<TextView>(R.id.lobbyName).text = lobby.name
        row.findViewById<TextView>(R.id.lobbyMap).text = "${lobby.mapName} - Argentina"
        row.findViewById<TextView>(R.id.lobbyPlayers).text = "${lobby.players}/${lobby.limit}"
        row.findViewById<TextView>(R.id.lobbyStatus).apply {
            text = lobby.status
            setTextColor(Color.parseColor(lobbyStatusColor(lobby)))
        }
        row.findViewById<Button>(R.id.btnEnterLobby).apply {
            isEnabled = canJoinLobby(lobby)
            alpha = if (isEnabled) 1f else 0.42f
            text = lobbyActionLabel(lobby)
            contentDescription = when {
                lobby.players >= lobby.limit -> "Intentar reingresar a la sala ${lobby.name}"
                !lobby.canJoin -> "Sala ${lobby.name} en partida"
                else -> "Entrar a la sala ${lobby.name}"
            }
            setOnClickListener {
                enterLobby(lobby)
            }
        }
        return row
    }

    private fun showBrowserMessage(message: String) {
        emptyState.text = message
    }

    private fun canJoinLobby(lobby: OnlineLobby): Boolean {
        return lobby.canJoin
    }

    private fun lobbyActionLabel(lobby: OnlineLobby): String {
        return when {
            lobby.players >= lobby.limit -> "REINGRESAR"
            !lobby.canJoin -> "EN PARTIDA"
            else -> "ENTRAR"
        }
    }

    private fun lobbyStatusColor(lobby: OnlineLobby): String {
        return when (lobby.status) {
            "Casi llena" -> "#D4A24E"
            "En partida", "Llena" -> "#8F2633"
            else -> "#5A8A3C"
        }
    }

    private fun enterLobby(lobby: OnlineLobby) {
        OnlineTempIdentity.ensureAuthenticated(this)
            .addOnFailureListener { error ->
                OnlineDebugLog.e("auth_browser_join_failure roomId=${lobby.id}", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo preparar el ingreso online", error),
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnSuccessListener {
                enterAuthenticatedLobby(lobby)
            }
    }

    private fun enterAuthenticatedLobby(lobby: OnlineLobby) {
        val existingPublicId = PlayerPublicIdentity.currentPublicId(this)
        // Un invitado nunca reserva numero, asi que este atajo no puede pedirlo: `onReady`
        // vuelve a `enterLobby` y sin el chequeo de invitado quedaria dando vueltas para
        // siempre esperando un `#` que no va a llegar.
        if (existingPublicId.isBlank() && !GuestIdentity.isGuest()) {
            PlayerPublicIdentity.ensurePublicId(
                context = this,
                firestore = firestore,
                onReady = { enterLobby(lobby) },
                onFailure = { error ->
                    OnlineDebugLog.e("public_id_browser_join_fallback roomId=${lobby.id}", error)
                }
            )
            return
        }
        // profileName resuelve el alias del invitado; leer la preferencia directo devolveria
        // el nombre libre que un invitado no puede usar.
        val playerName = PlayerPublicIdentity.profileName(this)
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        val roomReference = firestore.collection(ONLINE_ROOMS_COLLECTION).document(lobby.id)
        val playerReference = roomReference.collection(ONLINE_PLAYERS_COLLECTION)
            .document(uidTemporal)

        OnlineDebugLog.i("browser_join_requested roomId=${lobby.id} uid=$uidTemporal players=${lobby.players}/${lobby.limit}")
        Toast.makeText(this, "Sala ${lobby.id}. Uniendote a ${lobby.name}.", Toast.LENGTH_SHORT).show()
        firestore.runTransaction { transaction ->
            val roomSnapshot = transaction.get(roomReference)
            if (!roomSnapshot.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (roomSnapshot.getString("cleanupState") == "deleting" ||
                roomSnapshot.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta disponible.")
            }
            val playerSnapshot = transaction.get(playerReference)
            val alreadyJoined = playerSnapshot.exists()
            val wasActive = playerSnapshot.getBoolean(OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH) != false
            val currentPlayers = roomSnapshot.getLong(FIELD_CURRENT_PLAYERS) ?: lobby.players.toLong()
            val limit = roomSnapshot.getLong(FIELD_EXPECTED_PLAYERS)
                ?: roomSnapshot.getLong(FIELD_MAX_PLAYERS)
                ?: lobby.limit.toLong()
            if ((!alreadyJoined || !wasActive) && currentPlayers >= limit) {
                throw IllegalStateException("La sala esta llena.")
            }

            // publicProfileFields omite el `publicId` cuando esta vacio (invitados) y de paso
            // publica avatar, banner y frase, que esta rama no mandaba y dejaban el
            // mini-perfil en blanco para quien entraba desde el navegador.
            val profileCreateData = PlayerPublicIdentity.publicProfileFields(
                this,
                existingPublicId,
                playerName
            )
            val protocolFields = if (BuildConfig.SERVER_ONLINE_V3 || roomSnapshot.getLong("protocolVersion") == 3L)
                mapOf("protocolVersion" to 3, OnlineRoomFirestore.FIELD_CAN_ARBITRATE to false) else emptyMap()
            val connectionData = protocolFields + mapOf(
                OnlineRoomFirestore.FIELD_NAME to playerName,
                OnlineRoomFirestore.FIELD_PLAYER_STATE to "conectado",
                "listo" to false,
                "uidTemporal" to uidTemporal,
                OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to true,
                OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
            )
            if (alreadyJoined) {
                val connectedUpdateData = PlayerPublicIdentity.publicProfileUpdateFields(
                    this,
                    existingPublicId,
                    playerName
                ) + connectionData
                val reactivatedData = if (wasActive) {
                    connectedUpdateData
                } else {
                    connectedUpdateData + mapOf(
                        OnlineRoomFirestore.FIELD_PLAYER_ORDER to currentPlayers.toInt(),
                        OnlineRoomFirestore.FIELD_JOINED_AT to FieldValue.serverTimestamp()
                    )
                }
                transaction.update(playerReference, reactivatedData)
                if (!wasActive) {
                    transaction.update(
                        roomReference,
                        mapOf(
                            FIELD_CURRENT_PLAYERS to FieldValue.increment(1),
                            "actualizadaEn" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                transaction.set(
                    playerReference,
                    profileCreateData + connectionData + mapOf(
                        OnlineRoomFirestore.FIELD_IS_HOST to false,
                        OnlineRoomFirestore.FIELD_PLAYER_ORDER to currentPlayers.toInt(),
                        OnlineRoomFirestore.FIELD_JOINED_AT to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    roomReference,
                    mapOf(
                        FIELD_CURRENT_PLAYERS to FieldValue.increment(1),
                        "actualizadaEn" to FieldValue.serverTimestamp()
                    )
                )
            }
            !alreadyJoined
        }.addOnSuccessListener {
            OnlineDebugLog.i("browser_join_success roomId=${lobby.id} uid=$uidTemporal")
            OnlineRoomRecovery.save(
                this,
                roomId = lobby.id,
                roomCode = lobby.code,
                roomName = lobby.name,
                mapKey = lobby.mapKey,
                isHost = false
            )
            val session = LocalGameFactory.createOnlineLobby(
                humanName = playerName,
                playerCount = 1,
                humanIsHost = false
            ).let { LocalGameFactory.selectMap(it, lobby.mapKey) }
            Toast.makeText(this, "Entrando a ${lobby.name}.", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, LobbyActivity::class.java)
                    .putExtra(LobbyActivity.EXTRA_SESSION, session)
                    .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_SEARCH)
                    .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, lobby.name)
                    .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, lobby.id)
                    .putExtra(LobbyActivity.EXTRA_ROOM_CODE, lobby.code)
                    .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, false)
            )
        }.addOnFailureListener { error ->
            OnlineDebugLog.e("browser_join_failure roomId=${lobby.id} uid=$uidTemporal", error)
            Toast.makeText(
                this,
                "No se pudo entrar a la sala: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private data class OnlineLobby(
        val id: String,
        val code: String,
        val name: String,
        val players: Int,
        val updatedAtMs: Long,
        val limit: Int,
        val mapName: String,
        val status: String,
        val mapKey: String,
        val canJoin: Boolean = true,
        val returningMember: Boolean = false
    )

    companion object {
        private const val MANUAL_REFRESH_INTERVAL_MS = 5_000L
        private const val ONLINE_ROOMS_COLLECTION = "partidas"
        private const val ONLINE_PLAYERS_COLLECTION = "jugadores"
        private const val ONLINE_ROOM_STATE_WAITING = "esperando"
        private const val FIELD_NAME = "nombre"
        private const val FIELD_STATE = "estado"
        private const val FIELD_ROOM_CODE = "codigoSala"
        private const val FIELD_MAP_KEY = "mapa"
        private const val FIELD_MAP_NAME = "mapaNombre"
        private const val FIELD_CURRENT_PLAYERS = "jugadoresActuales"
        private const val FIELD_UPDATED_AT = "actualizadaEn"
        private const val FIELD_EXPECTED_PLAYERS = "jugadoresEsperados"
        private const val FIELD_MAX_PLAYERS = "maxJugadores"
        private const val DEFAULT_MAP_KEY = "pampa"
        private const val DEFAULT_MAX_PLAYERS = 10
    }
}
