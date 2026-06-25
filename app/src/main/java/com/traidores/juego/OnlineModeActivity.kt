package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class OnlineModeActivity : BaseActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnCreate: Button
    private lateinit var btnJoinCode: Button
    private lateinit var btnRecoverRoom: Button
    private var pendingRecoveredRoom: OnlineRecoveredRoom? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnQuick: Button = findViewById(R.id.btnQuick)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        btnRecoverRoom = findViewById(R.id.btnRecoverRoom)
        btnJoinCode = findViewById(R.id.btnJoinCode)
        btnCreate = findViewById(R.id.btnCreate)

        btnBack.setOnClickListener { finish() }

        btnRecoverRoom.setOnClickListener {
            val recovered = pendingRecoveredRoom ?: return@setOnClickListener
            openRecoveredRoom(recovered)
        }

        btnQuick.setOnClickListener {
            openOnlineLobby(
                mode = LobbyActivity.MODE_ONLINE_QUICK,
                playerCount = LocalGameFactory.MAX_PLAYERS,
                humanIsHost = false
            )
        }

        btnSearch.setOnClickListener {
            startActivity(Intent(this, LobbyBrowserActivity::class.java))
        }

        btnJoinCode.setOnClickListener {
            showJoinByCodeDialog()
        }

        btnCreate.setOnClickListener {
            showCreateRoomDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        refreshRecoveredRoomButton()
    }

    private fun showCreateRoomDialog() {
        var expectedPlayers = OnlineRoomFirestore.DEFAULT_EXPECTED_PLAYERS
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(18))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }

        content.addView(dialogTitle("CREAR SALA ONLINE"))

        val subtitle = TextView(this).apply {
            text = "Elegi cuantos jugadores reales van a entrar. La sala no inicia hasta que esten todos listos."
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        content.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val countLabel = TextView(this).apply {
            text = "$expectedPlayers JUGADORES"
            setTextColor(resources.getColor(R.color.accent_gold, theme))
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        val selectorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val minus = dialogButton("-", gold = false)
        val plus = dialogButton("+", gold = false)
        selectorRow.addView(minus, LinearLayout.LayoutParams(dp(56), dp(44)))
        selectorRow.addView(
            countLabel,
            LinearLayout.LayoutParams(dp(176), dp(44)).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
            }
        )
        selectorRow.addView(plus, LinearLayout.LayoutParams(dp(56), dp(44)))
        content.addView(selectorRow)

        fun refreshCount() {
            countLabel.text = "$expectedPlayers JUGADORES"
            minus.isEnabled = expectedPlayers > LocalGameFactory.MIN_PLAYERS
            minus.alpha = if (minus.isEnabled) 1f else 0.45f
            plus.isEnabled = expectedPlayers < LocalGameFactory.MAX_PLAYERS
            plus.alpha = if (plus.isEnabled) 1f else 0.45f
        }
        minus.setOnClickListener {
            expectedPlayers = (expectedPlayers - 1).coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
            refreshCount()
        }
        plus.setOnClickListener {
            expectedPlayers = (expectedPlayers + 1).coerceAtMost(LocalGameFactory.MAX_PLAYERS)
            refreshCount()
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        val cancelButton = dialogButton("CANCELAR", gold = false)
        val createButton = dialogButton("CREAR", gold = true)
        buttonRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(dp(138), dp(44)).apply { rightMargin = dp(10) }
        )
        buttonRow.addView(
            createButton,
            LinearLayout.LayoutParams(dp(138), dp(44)).apply { leftMargin = dp(10) }
        )
        content.addView(buttonRow)

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        cancelButton.setOnClickListener { dialog.dismiss() }
        createButton.setOnClickListener {
            dialog.dismiss()
            createOnlineRoom(expectedPlayers)
        }
        refreshCount()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                minOf(resources.displayMetrics.widthPixels - dp(32), dp(430)),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun createOnlineRoom(expectedPlayers: Int) {
        btnCreate.isEnabled = false
        btnCreate.text = "PREPARANDO..."
        PlayerPublicIdentity.ensurePublicId(
            context = this,
            firestore = firestore,
            onReady = { publicId ->
                createOnlineRoomWithPublicId(expectedPlayers, publicId)
            },
            onFailure = { error ->
                OnlineDebugLog.e("public_id_create_room_fallback", error)
            }
        )
    }

    private fun createOnlineRoomWithPublicId(expectedPlayers: Int, publicId: String) {
        createOnlineRoomWithPublicId(expectedPlayers, publicId, remainingCodeAttempts = ROOM_CODE_CREATE_ATTEMPTS)
    }

    private fun createOnlineRoomWithPublicId(
        expectedPlayers: Int,
        publicId: String,
        remainingCodeAttempts: Int
    ) {
        btnCreate.isEnabled = false
        btnCreate.text = "CREANDO..."

        val preferences = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        val playerName = preferences
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val selectedMap = OnlineRoomFirestore.selectedMapFromKey(
            preferences.getString(OpcionesActivity.PREF_LAST_SELECTED_MAP, null).orEmpty()
        )
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        OnlineDebugLog.i(
            "create_room_requested hostId=$uidTemporal map=${selectedMap.key} expected=$expectedPlayers player=${OnlineRoomFirestore.normalizedPlayerName(playerName)}"
        )
        val candidateCode = OnlineRoomFirestore.generateRoomCode()
        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .whereEqualTo(OnlineRoomFirestore.FIELD_ROOM_CODE, candidateCode)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    OnlineDebugLog.w(
                        "create_room_code_collision code=$candidateCode remaining=$remainingCodeAttempts"
                    )
                    if (remainingCodeAttempts > 1) {
                        createOnlineRoomWithPublicId(
                            expectedPlayers = expectedPlayers,
                            publicId = publicId,
                            remainingCodeAttempts = remainingCodeAttempts - 1
                        )
                    } else {
                        btnCreate.isEnabled = true
                        btnCreate.text = "CREAR PARTIDA"
                        Toast.makeText(
                            this,
                            "No se pudo generar un codigo libre. Intenta de nuevo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@addOnSuccessListener
                }
                commitOnlineRoomCreation(
                    expectedPlayers = expectedPlayers,
                    publicId = publicId,
                    playerName = playerName,
                    uidTemporal = uidTemporal,
                    selectedMap = selectedMap,
                    roomCode = candidateCode
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("create_room_code_check_failure hostId=$uidTemporal", error)
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo verificar el codigo de sala", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun commitOnlineRoomCreation(
        expectedPlayers: Int,
        publicId: String,
        playerName: String,
        uidTemporal: String,
        selectedMap: GameMap,
        roomCode: String
    ) {
        val creation = OnlineRoomFirestore.createRoom(
            firestore = firestore,
            playerName = playerName,
            uidTemporal = uidTemporal,
            publicId = publicId,
            map = selectedMap,
            origin = "android-online-create",
            expectedPlayers = expectedPlayers,
            roomCode = roomCode
        )

        creation.commitTask
            .addOnSuccessListener {
                OnlineDebugLog.i(
                    "create_room_success roomId=${creation.roomReference.id} code=${creation.roomCode} hostId=$uidTemporal map=${creation.map.key} expected=${creation.expectedPlayers}"
                )
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                OnlineRoomRecovery.save(
                    this,
                    roomId = creation.roomReference.id,
                    roomCode = creation.roomCode,
                    roomName = creation.roomName,
                    mapKey = creation.map.key,
                    isHost = true
                )
                val session = LocalGameFactory.createOnlineLobby(
                    humanName = creation.playerName,
                    playerCount = 1,
                    humanIsHost = true
                ).let { LocalGameFactory.selectMap(it, creation.map.key) }
                Toast.makeText(this, "Sala online creada.", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, LobbyActivity::class.java)
                        .putExtra(LobbyActivity.EXTRA_SESSION, session)
                        .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_CREATE)
                        .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, creation.roomName)
                        .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, creation.roomReference.id)
                        .putExtra(LobbyActivity.EXTRA_ROOM_CODE, creation.roomCode)
                        .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, false)
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("create_room_failure hostId=$uidTemporal map=${selectedMap.key}", error)
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo crear la sala", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun refreshRecoveredRoomButton() {
        val recovered = OnlineRoomRecovery.load(this)
        if (recovered == null) {
            showRecoveredRoom(null)
            return
        }
        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(recovered.roomId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    OnlineRoomRecovery.clear(this)
                    showRecoveredRoom(null)
                    return@addOnSuccessListener
                }
                val state = snapshot.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty()
                if (OnlineRecoveryGate.targetForRoomState(state) == OnlineRecoveryTarget.CLEAR) {
                    OnlineRoomRecovery.clear(this)
                    showRecoveredRoom(null)
                    return@addOnSuccessListener
                }
                val resolved = recovered.copy(
                    roomCode = snapshot.getString(OnlineRoomFirestore.FIELD_ROOM_CODE)
                        ?.takeIf { it.isNotBlank() }
                        ?: recovered.roomCode,
                    roomName = snapshot.getString(OnlineRoomFirestore.FIELD_NAME)
                        ?.takeIf { it.isNotBlank() }
                        ?: recovered.roomName,
                    mapKey = snapshot.getString(OnlineRoomFirestore.FIELD_MAP_KEY)
                        ?.takeIf { it.isNotBlank() }
                        ?: recovered.mapKey
                )
                showRecoveredRoom(resolved)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("recover_room_check_failure roomId=${recovered.roomId}", error)
                showRecoveredRoom(recovered)
            }
    }

    private fun showRecoveredRoom(room: OnlineRecoveredRoom?) {
        pendingRecoveredRoom = room
        btnRecoverRoom.visibility = if (room == null) android.view.View.GONE else android.view.View.VISIBLE
        if (room != null) {
            btnRecoverRoom.text = "REINGRESAR ${room.roomCode.ifBlank { room.roomId.take(6) }}"
        }
    }

    private fun openRecoveredRoom(room: OnlineRecoveredRoom) {
        btnRecoverRoom.isEnabled = false
        btnRecoverRoom.text = "RECUPERANDO..."
        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(room.roomId)
            .get()
            .addOnSuccessListener { snapshot ->
                btnRecoverRoom.isEnabled = true
                if (!snapshot.exists()) {
                    OnlineRoomRecovery.clear(this)
                    showRecoveredRoom(null)
                    Toast.makeText(this, "La sala ya no existe.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val state = snapshot.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty()
                when (OnlineRecoveryGate.targetForRoomState(state)) {
                    OnlineRecoveryTarget.LOBBY -> openRecoveredLobby(room, snapshot)
                    OnlineRecoveryTarget.GAMEPLAY -> openRecoveredGameplay(room, snapshot)
                    OnlineRecoveryTarget.CLEAR -> {
                        OnlineRoomRecovery.clear(this)
                        showRecoveredRoom(null)
                        Toast.makeText(this, "La sala ya termino o fue abandonada.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { error ->
                btnRecoverRoom.isEnabled = true
                showRecoveredRoom(room)
                OnlineDebugLog.e("recover_room_open_failure roomId=${room.roomId}", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo reingresar", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openRecoveredLobby(room: OnlineRecoveredRoom, snapshot: DocumentSnapshot) {
        val playerName = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val resolvedMapKey = snapshot.getString(OnlineRoomFirestore.FIELD_MAP_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: room.mapKey
        val resolvedRoomCode = snapshot.getString(OnlineRoomFirestore.FIELD_ROOM_CODE)
            ?.takeIf { it.isNotBlank() }
            ?: room.roomCode
        val resolvedRoomName = snapshot.getString(OnlineRoomFirestore.FIELD_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: room.roomName
        val session = LocalGameFactory.createOnlineLobby(
            humanName = playerName,
            playerCount = 1,
            humanIsHost = room.isHost
        ).let { LocalGameFactory.selectMap(it, resolvedMapKey) }
        OnlineDebugLog.i("recover_room_lobby_open roomId=${room.roomId} code=$resolvedRoomCode isHost=${room.isHost}")
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(
                    LobbyActivity.EXTRA_LOBBY_MODE,
                    if (room.isHost) LobbyActivity.MODE_ONLINE_CREATE else LobbyActivity.MODE_ONLINE_SEARCH
                )
                .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, resolvedRoomName)
                .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, room.roomId)
                .putExtra(LobbyActivity.EXTRA_ROOM_CODE, resolvedRoomCode)
                .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, true)
        )
    }

    private fun openRecoveredGameplay(room: OnlineRecoveredRoom, snapshot: DocumentSnapshot) {
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        val mapKey = snapshot.getString(OnlineRoomFirestore.FIELD_MAP_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: room.mapKey
        val selectedMap = OnlineRoomFirestore.selectedMapFromKey(mapKey)
        val roomCode = snapshot.getString(OnlineRoomFirestore.FIELD_ROOM_CODE)
            ?.takeIf { it.isNotBlank() }
            ?: room.roomCode
        val roomName = snapshot.getString(OnlineRoomFirestore.FIELD_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: room.roomName
        val expectedPlayers = snapshot.getLong(OnlineRoomFirestore.FIELD_EXPECTED_PLAYERS)
            ?.toInt()
            ?: OnlineRoomFirestore.DEFAULT_EXPECTED_PLAYERS
        val defaults = LocalGameFactory.createSession()
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = snapshot.get("partidaInicial"),
            matchStateRaw = snapshot.get("estadoPartida"),
            uidTemporal = uidTemporal,
            expectedPlayers = expectedPlayers,
            fallbackRoomId = room.roomId,
            fallbackRoomCode = roomCode,
            fallbackMapKey = selectedMap.key,
            fallbackMapName = selectedMap.name,
            revealRolesOnDeath = defaults.revealRolesOnDeath,
            showIndividualVotes = defaults.showIndividualVotes
        )
        when (result) {
            is OnlineMatchSessionResult.Success -> {
                val isHost = snapshot.getString(OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID) == uidTemporal ||
                    snapshot.getString(OnlineRoomFirestore.FIELD_HOST_ID) == uidTemporal ||
                    room.isHost
                OnlineRoomRecovery.save(
                    this,
                    roomId = room.roomId,
                    roomCode = roomCode,
                    roomName = roomName,
                    mapKey = result.session.mapKey,
                    isHost = isHost
                )
                OnlineDebugLog.i(
                    "recover_room_gameplay_open roomId=${room.roomId} uid=$uidTemporal isHost=$isHost phase=${result.session.phase.name} phaseIndex=${result.session.phaseIndex}"
                )
                Toast.makeText(this, "Reingresando a la partida.", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this, GameplayMockActivity::class.java)
                        .putExtra(LobbyActivity.EXTRA_SESSION, result.session)
                        .putExtra(GameplayMockActivity.EXTRA_TEMA, GameplayTableUi.themeForMapKey(result.session.mapKey))
                        .putExtra(GameplayMockActivity.EXTRA_ES_NOCHE, false)
                        .putExtra(GameplayMockActivity.EXTRA_ONLINE_PARTIDA_ID, room.roomId)
                        .putExtra(GameplayMockActivity.EXTRA_ONLINE_PLAYER_ID, uidTemporal)
                        .putExtra(GameplayMockActivity.EXTRA_ONLINE_IS_HOST, isHost)
                )
            }
            is OnlineMatchSessionResult.Failure -> {
                OnlineDebugLog.e(
                    "recover_room_gameplay_corrupt roomId=${room.roomId} uid=$uidTemporal reason=${result.reason.name}"
                )
                OnlineRoomRecovery.clearIf(this, room.roomId)
                showRecoveredRoom(null)
                Toast.makeText(this, result.reason.userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showJoinByCodeDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(18))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }

        val title = TextView(this).apply {
            text = "UNIRSE POR CODIGO"
            setTextColor(resources.getColor(R.color.accent_gold, theme))
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        content.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "Ingresa el codigo de ${OnlineRoomFirestore.ROOM_CODE_LENGTH} caracteres de la sala."
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(14))
        }
        content.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val codeInput = EditText(this).apply {
            setSingleLine(true)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(
                InputFilter.AllCaps(),
                InputFilter.LengthFilter(OnlineRoomFirestore.ROOM_CODE_LENGTH)
            )
            hint = "ABC123"
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setHintTextColor(resources.getColor(R.color.text_muted, theme))
            textSize = 24f
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundResource(R.drawable.bg_btn_dark)
        }
        content.addView(
            codeInput,
            LinearLayout.LayoutParams(dp(210), dp(54)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            }
        )

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelButton = dialogButton("CANCELAR", gold = false)
        val joinButton = dialogButton("UNIRSE", gold = true)
        buttonRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(dp(138), dp(44)).apply { rightMargin = dp(10) }
        )
        buttonRow.addView(
            joinButton,
            LinearLayout.LayoutParams(dp(138), dp(44)).apply { leftMargin = dp(10) }
        )
        content.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        joinButton.setOnClickListener {
            val code = codeInput.text.toString().trim().uppercase()
            if (code.length != OnlineRoomFirestore.ROOM_CODE_LENGTH) {
                Toast.makeText(
                    this,
                    "El codigo debe tener ${OnlineRoomFirestore.ROOM_CODE_LENGTH} caracteres.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            joinRoomByCode(code, dialog, joinButton)
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                minOf(resources.displayMetrics.widthPixels - dp(32), dp(430)),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun joinRoomByCode(code: String, dialog: AlertDialog, joinButton: Button) {
        joinButton.isEnabled = false
        joinButton.text = "BUSCANDO..."
        OnlineDebugLog.i("join_code_search code=$code")

        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .whereEqualTo(OnlineRoomFirestore.FIELD_ROOM_CODE, code)
            .get()
            .addOnSuccessListener { snapshot ->
                val waitingRooms = snapshot.documents.filter {
                    it.getString(OnlineRoomFirestore.FIELD_STATE) == OnlineRoomFirestore.STATE_WAITING
                }
                val roomSnapshot = waitingRooms.singleOrNull()
                if (roomSnapshot == null) {
                    if (waitingRooms.size > 1) {
                        OnlineDebugLog.e("join_code_collision code=$code rooms=${waitingRooms.size}")
                        joinButton.isEnabled = true
                        joinButton.text = "UNIRSE"
                        Toast.makeText(
                            this,
                            "Hay mas de una sala con ese codigo. Creen una sala nueva.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnSuccessListener
                    }
                    OnlineDebugLog.w("join_code_not_found code=$code")
                    joinButton.isEnabled = true
                    joinButton.text = "UNIRSE"
                    Toast.makeText(this, "No existe una sala con ese codigo.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                joinOnlineRoom(
                    roomId = roomSnapshot.id,
                    roomName = roomSnapshot.getString(OnlineRoomFirestore.FIELD_NAME)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Sala online",
                    roomCode = code,
                    mapKey = roomSnapshot.getString(OnlineRoomFirestore.FIELD_MAP_KEY).orEmpty(),
                    dialog = dialog,
                    joinButton = joinButton
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("join_code_search_failure code=$code", error)
                joinButton.isEnabled = true
                joinButton.text = "UNIRSE"
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo buscar la sala", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun joinOnlineRoom(
        roomId: String,
        roomName: String,
        roomCode: String,
        mapKey: String,
        dialog: AlertDialog,
        joinButton: Button,
        publicId: String? = null
    ) {
        if (publicId == null) {
            PlayerPublicIdentity.ensurePublicId(
                context = this,
                firestore = firestore,
                onReady = { resolvedPublicId ->
                    joinOnlineRoom(roomId, roomName, roomCode, mapKey, dialog, joinButton, resolvedPublicId)
                },
                onFailure = { error ->
                    OnlineDebugLog.e("public_id_join_room_fallback roomId=$roomId", error)
                }
            )
            return
        }
        val playerName = OnlineRoomFirestore.normalizedPlayerName(
            getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
                .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
                .orEmpty()
        )
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        val roomReference = firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION).document(roomId)
        val playerReference = roomReference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(uidTemporal)
        OnlineDebugLog.i("join_room_requested roomId=$roomId uid=$uidTemporal code=$roomCode")

        firestore.runTransaction { transaction ->
            val freshRoom = transaction.get(roomReference)
            if (!freshRoom.exists()) {
                throw IllegalStateException("La sala ya no existe.")
            }
            if (freshRoom.getString(OnlineRoomFirestore.FIELD_STATE) != OnlineRoomFirestore.STATE_WAITING) {
                throw IllegalStateException("La sala ya no esta disponible.")
            }

            val playerSnapshot = transaction.get(playerReference)
            val alreadyJoined = playerSnapshot.exists()
            val wasActive = playerSnapshot.getBoolean(OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH) != false
            val currentPlayers = freshRoom.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 0L
            val limit = freshRoom.getLong(OnlineRoomFirestore.FIELD_EXPECTED_PLAYERS)
                ?: freshRoom.getLong(OnlineRoomFirestore.FIELD_MAX_PLAYERS)
                ?: OnlineRoomFirestore.DEFAULT_MAX_PLAYERS.toLong()

            if ((!alreadyJoined || !wasActive) && currentPlayers >= limit) {
                throw IllegalStateException("La sala esta llena.")
            }

            val connectedData = mapOf(
                OnlineRoomFirestore.FIELD_NAME to playerName,
                PlayerPublicIdentity.FIELD_PUBLIC_ID to publicId,
                PlayerPublicIdentity.FIELD_PROFILE_NAME to playerName,
                PlayerPublicIdentity.FIELD_ROOM_NAME to RoomDisplayNames.withPublicId(playerName, publicId),
                OnlineRoomFirestore.FIELD_PLAYER_STATE to "conectado",
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
                        OnlineRoomFirestore.FIELD_CURRENT_PLAYERS,
                        FieldValue.increment(1)
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
                    OnlineRoomFirestore.FIELD_CURRENT_PLAYERS,
                    FieldValue.increment(1)
                )
            }
            transaction.update(
                roomReference,
                OnlineRoomFirestore.FIELD_UPDATED_AT,
                FieldValue.serverTimestamp()
            )
            freshRoom.getString(OnlineRoomFirestore.FIELD_MAP_KEY).orEmpty()
        }.addOnSuccessListener { resolvedMapKey ->
            OnlineDebugLog.i("join_room_success roomId=$roomId uid=$uidTemporal map=${resolvedMapKey.ifBlank { mapKey }}")
            dialog.dismiss()
            val finalMapKey = resolvedMapKey.ifBlank { mapKey }
            OnlineRoomRecovery.save(
                this,
                roomId = roomId,
                roomCode = roomCode,
                roomName = roomName,
                mapKey = finalMapKey,
                isHost = false
            )
            val session = LocalGameFactory.createOnlineLobby(
                humanName = playerName,
                playerCount = 1,
                humanIsHost = false
            ).let { LocalGameFactory.selectMap(it, finalMapKey) }
            Toast.makeText(this, "Entrando a $roomName.", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, LobbyActivity::class.java)
                    .putExtra(LobbyActivity.EXTRA_SESSION, session)
                    .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_SEARCH)
                    .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, roomName)
                    .putExtra(LobbyActivity.EXTRA_PARTIDA_ID, roomId)
                    .putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode)
                    .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, false)
            )
        }.addOnFailureListener { error ->
            OnlineDebugLog.e("join_room_failure roomId=$roomId uid=$uidTemporal", error)
            joinButton.isEnabled = true
            joinButton.text = "UNIRSE"
            Toast.makeText(
                this,
                OnlineErrorMessages.forAction("No se pudo entrar a la sala", error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dialogButton(text: String, gold: Boolean): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(
                resources.getColor(
                    if (gold) R.color.bg_dark else R.color.text_primary,
                    theme
                )
            )
            setBackgroundResource(if (gold) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 0
            minWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.accent_gold, theme))
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }.also { title ->
            title.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun openOnlineLobby(mode: String, playerCount: Int, humanIsHost: Boolean) {
        val playerName = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val session = LocalGameFactory.createOnlineLobby(
            humanName = playerName,
            playerCount = playerCount,
            humanIsHost = humanIsHost
        )
        Toast.makeText(this, "Entrando al lobby online.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, mode)
            )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ROOM_CODE_CREATE_ATTEMPTS = 5
    }
}

