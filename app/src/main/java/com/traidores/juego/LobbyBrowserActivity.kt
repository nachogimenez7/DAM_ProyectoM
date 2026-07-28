package com.traidores.juego

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

class LobbyBrowserActivity : BaseActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private var lobbyListener: ListenerRegistration? = null
    private var lobbies = emptyList<OnlineLobby>()

    private lateinit var lobbyList: LinearLayout
    private lateinit var emptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby_browser)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        lobbyList = findViewById(R.id.lobbyList)
        emptyState = findViewById(R.id.lobbyEmptyState)
        showBrowserMessage("Buscando partidas online...")
        renderLobbyList()
    }

    override fun onStart() {
        super.onStart()
        listenForOnlineRooms()
    }

    override fun onStop() {
        lobbyListener?.remove()
        lobbyListener = null
        super.onStop()
    }

    private fun listenForOnlineRooms() {
        lobbyListener?.remove()
        OnlineDebugLog.i("lobby_browser_listen_start")
        lobbyListener = firestore.collection(ONLINE_ROOMS_COLLECTION)
            .whereEqualTo(FIELD_STATE, ONLINE_ROOM_STATE_WAITING)
            .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
            .limit(BROWSER_ROOM_LIMIT)
            .addSnapshotListener { snapshot, error ->
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

                lobbies = snapshot?.documents
                    ?.mapNotNull(::parseLobby)
                    ?.sortedWith(compareByDescending<OnlineLobby> { it.players }.thenBy { it.name })
                    .orEmpty()
                OnlineDebugLog.i("lobby_browser_snapshot rooms=${lobbies.size}")
                showBrowserMessage("Todavia no hay partidas online. Crea una sala o intenta mas tarde.")
                renderLobbyList()
            }
    }

    private fun parseLobby(document: DocumentSnapshot): OnlineLobby? {
        val status = document.getString(FIELD_STATE) ?: return null
        if (status != ONLINE_ROOM_STATE_WAITING) return null
        val updatedAtMs = document.getTimestamp(FIELD_UPDATED_AT)?.toDate()?.time ?: 0L
        if (!OnlineLobbyRules.isRoomFresh(updatedAtMs, System.currentTimeMillis(), ROOM_VISIBILITY_MS)) {
            return null
        }
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
        return OnlineLobby(
            id = document.id,
            code = document.getString(FIELD_ROOM_CODE).orEmpty(),
            name = document.getString(FIELD_NAME)?.takeIf { it.isNotBlank() } ?: "Sala online",
            players = players.coerceAtLeast(0),
            limit = limit,
            mapName = "Mapa $mapName",
            status = if (players >= limit) "Llena" else "Esperando",
            mapKey = mapKey,
            canJoin = true,
            accountsOnly = document.getBoolean(OnlineRoomFirestore.FIELD_ACCOUNTS_ONLY) == true
        )
    }

    private fun renderLobbyList() {
        lobbyList.removeAllViews()
        emptyState.visibility = if (lobbies.isEmpty()) View.VISIBLE else View.GONE
        lobbies.forEach { lobby ->
            lobbyList.addView(createLobbyRow(lobby))
        }
    }

    private fun createLobbyRow(lobby: OnlineLobby): View {
        val row = layoutInflater.inflate(R.layout.item_online_lobby, lobbyList, false)
        // El candado va en el nombre y no en una vista nueva: la fila ya esta ajustada y
        // sumarle un icono obligaria a rehacer el layout de una pantalla que hoy entra bien.
        row.findViewById<TextView>(R.id.lobbyName).text = if (lobby.accountsOnly) {
            "🔒 ${lobby.name}"
        } else {
            lobby.name
        }
        row.findViewById<TextView>(R.id.lobbyMap).text = if (lobby.accountsOnly) {
            "${lobby.mapName} - Solo cuentas"
        } else {
            "${lobby.mapName} - Argentina"
        }
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
                lobby.accountsOnly && GuestIdentity.isGuest() ->
                    "Sala ${lobby.name}, solo para cuentas registradas"
                lobby.players >= lobby.limit -> "Intentar reingresar a la sala ${lobby.name}"
                !lobby.canJoin -> "Sala ${lobby.name} en partida"
                else -> "Entrar a la sala ${lobby.name}"
            }
            setOnClickListener {
                if (lobby.accountsOnly && GuestIdentity.isGuest()) {
                    GameDialog.confirm(
                        activity = this@LobbyBrowserActivity,
                        title = "Solo cuentas",
                        message = getString(R.string.online_room_accounts_only_blocked),
                        positiveLabel = "IR AL PERFIL",
                        negativeLabel = "AHORA NO"
                    ) {
                        startActivity(Intent(this@LobbyBrowserActivity, ProfileActivity::class.java))
                    }
                    return@setOnClickListener
                }
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
            if (roomSnapshot.getString(FIELD_STATE) != ONLINE_ROOM_STATE_WAITING) {
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
            val connectedData = PlayerPublicIdentity.publicProfileFields(
                this,
                existingPublicId,
                playerName
            ) + mapOf(
                OnlineRoomFirestore.FIELD_NAME to playerName,
                OnlineRoomFirestore.FIELD_PLAYER_STATE to "conectado",
                "listo" to false,
                "uidTemporal" to uidTemporal,
                OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH to true,
                OnlineRoomFirestore.FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
                OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
            )
            if (alreadyJoined) {
                val reactivatedData = if (wasActive) {
                    connectedData
                } else {
                    connectedData + mapOf(
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
                    connectedData + mapOf(
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
        val limit: Int,
        val mapName: String,
        val status: String,
        val mapKey: String,
        val canJoin: Boolean = true,
        /** Sala cerrada a cuentas registradas por decision del anfitrion. */
        val accountsOnly: Boolean = false
    )

    companion object {
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
        private const val BROWSER_ROOM_LIMIT = 30L
        private const val ROOM_VISIBILITY_MS = 30L * 60L * 1000L
    }
}
