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
import android.widget.ScrollView
import android.widget.TextView
import com.traidores.juego.GameToast as Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class OnlineModeActivity : BaseActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnCreate: Button
    private lateinit var btnJoinCode: Button
    private lateinit var btnRecoverRoom: Button
    private var pendingRecoveredRoom: OnlineRecoveredRoom? = null
    private var accessCheckInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        btnRecoverRoom = findViewById(R.id.btnRecoverRoom)
        btnJoinCode = findViewById(R.id.btnJoinCode)
        btnCreate = findViewById(R.id.btnCreate)

        btnBack.setOnClickListener { finish() }

        btnRecoverRoom.setOnClickListener {
            val recovered = pendingRecoveredRoom ?: return@setOnClickListener
            openRecoveredRoom(recovered)
        }

        btnSearch.setOnClickListener {
            OnlineTempIdentity.ensureAuthenticated(this)
                .addOnSuccessListener {
                    startActivity(Intent(this, LobbyBrowserActivity::class.java))
                }
                .addOnFailureListener { error ->
                    OnlineDebugLog.e("auth_lobby_browser_failure", error)
                    Toast.makeText(
                        this,
                        OnlineErrorMessages.forAction("No se pudo preparar el online", error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        btnJoinCode.setOnClickListener {
            showJoinByCodeDialog()
        }

        btnCreate.setOnClickListener {
            // Crear sala es de cuentas registradas: el creador arranca siendo anfitrion y el
            // anfitrion es la autoridad de la partida. Las reglas lo rechazan igual; esto
            // existe para explicar por que, en vez de mostrar un error de permisos.
            if (GuestIdentity.isGuest()) {
                GameDialog.confirm(
                    activity = this,
                    title = "Solo con cuenta",
                    message = getString(R.string.online_guest_cannot_create) + "\n\n" +
                        getString(R.string.online_guest_create_cta),
                    positiveLabel = "IR AL PERFIL",
                    negativeLabel = "AHORA NO"
                ) {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
                return@setOnClickListener
            }
            showCreateRoomDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        verifyOnlineAccess()
    }

    private fun verifyOnlineAccess() {
        if (accessCheckInProgress) return
        accessCheckInProgress = true
        setOnlineActionsEnabled(false)
        OnlineAccessGate.verify(
            context = this,
            onAllowed = {
                accessCheckInProgress = false
                setOnlineActionsEnabled(true)
                refreshRecoveredRoomButton()
                OnlineRoomJanitor.sweepOwnedStaleRooms(this)
            },
            onBlocked = { ban ->
                accessCheckInProgress = false
                GameDialog.confirm(
                    activity = this,
                    title = "Acceso online suspendido",
                    message = ban.reason,
                    positiveLabel = "VOLVER",
                    negativeLabel = ""
                ) { finish() }.setCancelable(false)
            },
            onFailure = { error ->
                accessCheckInProgress = false
                OnlineDebugLog.e("online_access_gate_failure", error)
                GameNotice.show(
                    this,
                    OnlineErrorMessages.forAction(
                        "No pudimos verificar tu acceso online. Reintentando",
                        error
                    ),
                    GameNotice.Duration.LONG
                )
                setOnlineActionsEnabled(false)
                if (::btnCreate.isInitialized) {
                    btnCreate.postDelayed(
                        { if (!isFinishing && !isDestroyed) verifyOnlineAccess() },
                        ONLINE_ACCESS_RETRY_MS
                    )
                }
            }
        )
    }

    private fun setOnlineActionsEnabled(enabled: Boolean) {
        if (::btnCreate.isInitialized) btnCreate.isEnabled = enabled
        if (::btnJoinCode.isInitialized) btnJoinCode.isEnabled = enabled
        if (::btnRecoverRoom.isInitialized) btnRecoverRoom.isEnabled = enabled
        findViewById<Button>(R.id.btnSearch)?.isEnabled = enabled
    }

    private fun showCreateRoomDialog() {
        var expectedPlayers = OnlineRoomFirestore.DEFAULT_EXPECTED_PLAYERS
        var modePrueba = false
        var roomVisibility = OnlineRoomFirestore.VISIBILITY_PUBLIC
        val preferences = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        var selectedMap = OnlineRoomFirestore.selectedMapFromKey(
            preferences.getString(OpcionesActivity.PREF_LAST_SELECTED_MAP, null).orEmpty()
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(18))
            setBackgroundResource(R.drawable.bg_dialog_game_panel)
        }

        content.addView(dialogTitle("CREAR SALA ONLINE"))

        val subtitle = TextView(this).apply {
            text = "Elige cuantos jugadores reales van a entrar. La sala no inicia hasta que esten todos listos."
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
            textSize = 22f
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
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
            }
        )
        selectorRow.addView(plus, LinearLayout.LayoutParams(dp(56), dp(44)))
        content.addView(
            selectorRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(TextView(this).apply {
            text = "NOMBRE DE LA SALA"
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(5))
        })
        val roomNameInput = EditText(this).apply {
            setSingleLine(true)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(OnlineRoomFirestore.MAX_ROOM_NAME_LENGTH))
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 16f
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(R.drawable.bg_btn_dark)
        }
        content.addView(
            roomNameInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { bottomMargin = dp(12) }
        )

        val testModeSwitch = SwitchCompat(this).apply {
            applyTraidoresSwitchStyle()
            isChecked = modePrueba
            text = ""
            contentDescription = "Activar sala de prueba"
        }
        val testModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), 0)
            addView(TextView(this@OnlineModeActivity).apply {
                text = "SALA DE PRUEBA"
                setTextColor(resources.getColor(R.color.text_primary, theme))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(testModeSwitch)
        }
        content.addView(
            testModeRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(TextView(this).apply {
            text = "Permite iniciar con 3 o 4 jugadores para hacer pruebas. Una partida normal requiere al menos 5."
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(4))
        })

        content.addView(TextView(this).apply {
            text = "VISIBILIDAD"
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        })
        val visibilityButtons = linkedMapOf<String, Button>()
        val visibilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun refreshVisibilityButtons() {
            visibilityButtons.forEach { (visibility, button) ->
                val selected = visibility == roomVisibility
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(
                    resources.getColor(if (selected) R.color.bg_dark else R.color.text_primary, theme)
                )
            }
        }
        listOf(
            OnlineRoomFirestore.VISIBILITY_PUBLIC to "PÚBLICA",
            OnlineRoomFirestore.VISIBILITY_PRIVATE to "PRIVADA"
        ).forEachIndexed { index, (visibility, label) ->
            val button = dialogButton(label, gold = visibility == roomVisibility)
            visibilityButtons[visibility] = button
            button.setOnClickListener {
                roomVisibility = visibility
                refreshVisibilityButtons()
            }
            visibilityRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                }
            )
        }
        refreshVisibilityButtons()
        content.addView(
            visibilityRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(TextView(this).apply {
            text = "Pública: aparece en Buscar partida. Privada: no aparece en la lista y se entra con el código."
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        })

        content.addView(TextView(this).apply {
            text = "MAPA"
            setTextColor(resources.getColor(R.color.text_primary, theme))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(6))
        })
        val mapButtons = linkedMapOf<GameMap, Button>()
        val mapRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun refreshMapButtons() {
            mapButtons.forEach { (map, button) ->
                val isSelected = map.key == selectedMap.key
                button.setBackgroundResource(
                    if (isSelected) R.drawable.bg_btn_gold_ripple else R.drawable.bg_btn_dark_ripple
                )
                button.setTextColor(
                    resources.getColor(if (isSelected) R.color.bg_dark else R.color.text_primary, theme)
                )
                button.alpha = if (isSelected) 1f else 0.82f
            }
        }
        LocalGameFactory.maps.forEachIndexed { index, map ->
            val button = dialogButton(map.name.uppercase(), gold = map.key == selectedMap.key)
            mapButtons[map] = button
            button.setOnClickListener {
                selectedMap = map
                refreshMapButtons()
            }
            mapRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (index > 0) leftMargin = dp(6)
                }
            )
        }
        refreshMapButtons()
        content.addView(
            mapRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun refreshCount() {
            countLabel.text = "$expectedPlayers JUGADORES"
            val minimum = if (modePrueba) {
                LocalGameFactory.TEST_MIN_PLAYERS
            } else {
                LocalGameFactory.MIN_PLAYERS
            }
            minus.isEnabled = expectedPlayers > minimum
            minus.alpha = if (minus.isEnabled) 1f else 0.45f
            plus.isEnabled = expectedPlayers < LocalGameFactory.MAX_PLAYERS
            plus.alpha = if (plus.isEnabled) 1f else 0.45f
        }
        minus.setOnClickListener {
            val minimum = if (modePrueba) {
                LocalGameFactory.TEST_MIN_PLAYERS
            } else {
                LocalGameFactory.MIN_PLAYERS
            }
            expectedPlayers = (expectedPlayers - 1).coerceAtLeast(minimum)
            refreshCount()
        }
        plus.setOnClickListener {
            expectedPlayers = (expectedPlayers + 1).coerceAtMost(LocalGameFactory.MAX_PLAYERS)
            refreshCount()
        }
        testModeSwitch.setOnCheckedChangeListener { _, checked ->
            modePrueba = checked
            expectedPlayers = OnlineRoomFirestore.normalizedExpectedPlayers(expectedPlayers, modePrueba)
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
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) }
        )
        buttonRow.addView(
            createButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(5) }
        )
        content.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val scrollContent = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(scrollContent)
            .create()
        cancelButton.setOnClickListener { dialog.dismiss() }
        createButton.setOnClickListener {
            dialog.dismiss()
            preferences.edit()
                .putString(OpcionesActivity.PREF_LAST_SELECTED_MAP, selectedMap.key)
                .apply()
            createOnlineRoom(
                expectedPlayers,
                selectedMap,
                modePrueba,
                roomNameInput.text.toString(),
                roomVisibility
            )
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

    private fun createOnlineRoom(
        expectedPlayers: Int,
        selectedMap: GameMap,
        modePrueba: Boolean,
        requestedRoomName: String,
        roomVisibility: String
    ) {
        btnCreate.isEnabled = false
        btnCreate.text = "PREPARANDO..."
        OnlineTempIdentity.ensureAuthenticated(this)
            .addOnSuccessListener {
                PlayerPublicIdentity.ensurePublicId(
                    context = this,
                    firestore = firestore,
                    onReady = { publicId ->
                        createOnlineRoomWithPublicId(
                            expectedPlayers,
                            selectedMap,
                            modePrueba,
                            requestedRoomName,
                            roomVisibility,
                            publicId
                        )
                    },
                    onFailure = { error ->
                        OnlineDebugLog.e("public_id_create_room_fallback", error)
                    }
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("auth_create_room_failure", error)
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo preparar la sala online", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun createOnlineRoomWithPublicId(
        expectedPlayers: Int,
        selectedMap: GameMap,
        modePrueba: Boolean,
        requestedRoomName: String,
        roomVisibility: String,
        publicId: String
    ) {
        createOnlineRoomWithPublicId(
            expectedPlayers,
            selectedMap,
            modePrueba,
            requestedRoomName,
            roomVisibility,
            publicId,
            remainingCodeAttempts = ROOM_CODE_CREATE_ATTEMPTS
        )
    }

    private fun createOnlineRoomWithPublicId(
        expectedPlayers: Int,
        selectedMap: GameMap,
        modePrueba: Boolean,
        requestedRoomName: String,
        roomVisibility: String,
        publicId: String,
        remainingCodeAttempts: Int
    ) {
        btnCreate.isEnabled = false
        btnCreate.text = "CREANDO..."

        val playerName = PlayerPublicIdentity.profileName(this)
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        OnlineDebugLog.i(
            "create_room_requested hostId=$uidTemporal map=${selectedMap.key} expected=$expectedPlayers testMode=$modePrueba player=${OnlineRoomFirestore.normalizedPlayerName(playerName)}"
        )
        val candidateCode = OnlineRoomFirestore.generateRoomCode()
        firestore.collection(OnlineRoomFirestore.ROOM_CODES_COLLECTION)
            .document(candidateCode)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    OnlineDebugLog.w(
                        "create_room_code_collision code=$candidateCode remaining=$remainingCodeAttempts"
                    )
                    if (remainingCodeAttempts > 1) {
                        createOnlineRoomWithPublicId(
                            expectedPlayers = expectedPlayers,
                            selectedMap = selectedMap,
                            modePrueba = modePrueba,
                            requestedRoomName = requestedRoomName,
                            roomVisibility = roomVisibility,
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
                    modePrueba = modePrueba,
                    requestedRoomName = requestedRoomName,
                    roomVisibility = roomVisibility,
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
        modePrueba: Boolean,
        requestedRoomName: String,
        roomVisibility: String,
        publicId: String,
        playerName: String,
        uidTemporal: String,
        selectedMap: GameMap,
        roomCode: String
    ) {
        val database = FirebaseDatabase.getInstance()
        val roomReference = firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION).document()
        val safePlayerName = OnlineRoomFirestore.normalizedPlayerName(playerName)
        RealtimeRoomAccess.initializeHost(
            database = database,
            roomId = roomReference.id,
            hostUid = uidTemporal,
            hostName = safePlayerName,
            onReady = {
                val creation = OnlineRoomFirestore.createRoom(
                    firestore = firestore,
                    playerName = safePlayerName,
                    uidTemporal = uidTemporal,
                    publicId = publicId,
                    profileFields = PlayerPublicIdentity.publicProfileFields(
                        this,
                        publicId,
                        safePlayerName
                    ),
                    map = selectedMap,
                    origin = "android-online-create",
                    expectedPlayers = expectedPlayers,
                    modePrueba = modePrueba,
                    requestedRoomName = requestedRoomName,
                    visibility = roomVisibility,
                    roomCode = roomCode,
                    roomReference = roomReference
                )
                creation.commitTask
                    .addOnSuccessListener {
                        OnlineDebugLog.i(
                            "create_room_success roomId=${creation.roomReference.id} code=${creation.roomCode} hostId=$uidTemporal map=${creation.map.key} expected=${creation.expectedPlayers} testMode=$modePrueba"
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
                        database.getReference("salas/${roomReference.id}")
                            .removeValue()
                            .addOnFailureListener { cleanupError ->
                                OnlineDebugLog.e(
                                    "rtdb_failed_room_rollback_failure roomId=${roomReference.id}",
                                    cleanupError
                                )
                            }
                        btnCreate.isEnabled = true
                        btnCreate.text = "CREAR PARTIDA"
                        Toast.makeText(
                            this,
                            OnlineErrorMessages.forAction("No se pudo crear la sala", error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            },
            onFailure = { error ->
                OnlineDebugLog.e(
                    "rtdb_access_initialize_failure roomId=${roomReference.id}",
                    error
                )
                database.getReference("salas/${roomReference.id}")
                    .removeValue()
                    .addOnFailureListener { cleanupError ->
                        OnlineDebugLog.e(
                            "rtdb_failed_access_rollback_failure roomId=${roomReference.id}",
                            cleanupError
                        )
                    }
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo proteger la sala", error),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun refreshRecoveredRoomButton() {
        val recovered = OnlineRoomRecovery.load(this)
        if (recovered == null) {
            showRecoveredRoom(null)
            return
        }
        // Nunca ofrecemos un reingreso basandonos solamente en el recuerdo local: puede
        // pertenecer a una sala que ya termino, fue eliminada o de la que salio el jugador.
        showRecoveredRoom(null)
        val uid = OnlineTempIdentity.getOrCreate(this)
        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(recovered.roomId)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    OnlineRoomRecovery.clear(this)
                    return@addOnSuccessListener
                }
                val state = snapshot.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty()
                if (OnlineRecoveryGate.targetForRoomState(state) == OnlineRecoveryTarget.CLEAR) {
                    OnlineRoomRecovery.clear(this)
                    return@addOnSuccessListener
                }
                snapshot.reference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
                    .document(uid)
                    .get(Source.SERVER)
                    .addOnSuccessListener { player ->
                        val target = OnlineRecoveryGate.targetForRecovery(
                            state = state,
                            playerExists = player.exists(),
                            activeInMatch = player.getBoolean(
                                OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH
                            ) != false
                        )
                        if (target == OnlineRecoveryTarget.CLEAR) {
                            OnlineRoomRecovery.clear(this)
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
                        OnlineDebugLog.e(
                            "recover_player_check_failure roomId=${recovered.roomId} uid=$uid",
                            error
                        )
                    }
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("recover_room_check_failure roomId=${recovered.roomId}", error)
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
        OnlineTempIdentity.ensureAuthenticated(this)
            .addOnFailureListener { error ->
                btnRecoverRoom.isEnabled = true
                showRecoveredRoom(room)
                OnlineDebugLog.e("auth_recover_room_failure roomId=${room.roomId}", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo preparar el reingreso", error),
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnSuccessListener {
                firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
                    .document(room.roomId)
                    .get(Source.SERVER)
                    .addOnSuccessListener roomSnapshot@{ snapshot ->
                        btnRecoverRoom.isEnabled = true
                        if (!snapshot.exists()) {
                            OnlineRoomRecovery.clear(this)
                            showRecoveredRoom(null)
                            Toast.makeText(this, "La sala ya no existe.", Toast.LENGTH_LONG).show()
                            return@roomSnapshot
                        }
                        val state = snapshot.getString(OnlineRoomFirestore.FIELD_STATE).orEmpty()
                        if (OnlineRecoveryGate.targetForRoomState(state) == OnlineRecoveryTarget.CLEAR) {
                            clearUnavailableRecoveredRoom("La sala ya termino o fue abandonada.")
                            return@roomSnapshot
                        }
                        verifyRecoveredMembershipAndOpen(room, snapshot, state)
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
    }

    private fun verifyRecoveredMembershipAndOpen(
        room: OnlineRecoveredRoom,
        snapshot: DocumentSnapshot,
        state: String
    ) {
        val uid = OnlineTempIdentity.getOrCreate(this)
        snapshot.reference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { player ->
                when (
                    OnlineRecoveryGate.targetForRecovery(
                        state = state,
                        playerExists = player.exists(),
                        activeInMatch = player.getBoolean(
                            OnlineRoomFirestore.FIELD_ACTIVE_IN_MATCH
                        ) != false
                    )
                ) {
                    OnlineRecoveryTarget.LOBBY -> openRecoveredLobby(room, snapshot)
                    OnlineRecoveryTarget.GAMEPLAY -> openRecoveredGameplay(room, snapshot)
                    OnlineRecoveryTarget.CLEAR -> clearUnavailableRecoveredRoom(
                        "Ya no formas parte de esa sala."
                    )
                }
            }
            .addOnFailureListener { error ->
                showRecoveredRoom(null)
                OnlineDebugLog.e(
                    "recover_player_open_failure roomId=${room.roomId} uid=$uid",
                    error
                )
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo confirmar el reingreso", error),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun clearUnavailableRecoveredRoom(message: String) {
        OnlineRoomRecovery.clear(this)
        showRecoveredRoom(null)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openRecoveredLobby(room: OnlineRecoveredRoom, snapshot: DocumentSnapshot) {
        val playerName = PlayerPublicIdentity.profileName(this)
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
                // This path only recovers a room that is still waiting. If it starts a new
                // match afterwards it must participate in the normal entry barrier; treating
                // it as an in-game recovery lets the host advance alone and strands guests.
                .putExtra(LobbyActivity.EXTRA_RECOVERING_ONLINE, false)
        )
    }

    private fun openRecoveredGameplay(room: OnlineRecoveredRoom, snapshot: DocumentSnapshot) {
        val uid = OnlineTempIdentity.getOrCreate(this)
        val repartos = firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .document(room.roomId)
            .collection("repartos")
        val isHost = snapshot.getString(OnlineRoomFirestore.FIELD_ACTIVE_HOST_ID) == uid ||
            snapshot.getString(OnlineRoomFirestore.FIELD_HOST_ID) == uid ||
            room.isHost
        if (isHost) {
            repartos.get()
                .addOnSuccessListener { query ->
                    val assignments = query.documents.flatMap(::visibleRolesFromReparto)
                        .distinctBy { (it["orden"] as? Number)?.toInt() }
                    openRecoveredGameplayWithRoles(room, snapshot, assignments)
                }
                .addOnFailureListener { error -> showPrivateRoleRecoveryError(room, error) }
        } else {
            repartos.document(uid).get()
                .addOnSuccessListener { reparto ->
                    openRecoveredGameplayWithRoles(
                        room,
                        snapshot,
                        visibleRolesFromReparto(reparto)
                    )
                }
                .addOnFailureListener { error -> showPrivateRoleRecoveryError(room, error) }
        }
    }

    private fun visibleRolesFromReparto(reparto: DocumentSnapshot): List<Map<String, Any?>> =
        (reparto.get("rolesVisibles") as? List<*>)
            .orEmpty()
            .mapNotNull { raw ->
                (raw as? Map<*, *>)?.entries
                    ?.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        key to entry.value
                    }
                    ?.toMap()
            }

    private fun showPrivateRoleRecoveryError(room: OnlineRecoveredRoom, error: Exception) {
                OnlineDebugLog.e("recover_private_role_failure roomId=${room.roomId}", error)
                Toast.makeText(
                    this,
                    OnlineErrorMessages.forAction("No se pudo recuperar tu rol privado", error),
                    Toast.LENGTH_LONG
                ).show()
    }

    private fun openRecoveredGameplayWithRoles(
        room: OnlineRecoveredRoom,
        snapshot: DocumentSnapshot,
        privateRoles: List<Map<String, Any?>>
    ) {
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
            showIndividualVotes = defaults.showIndividualVotes,
            privateRoleAssignments = privateRoles
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

        firestore.collection(OnlineRoomFirestore.ROOM_CODES_COLLECTION)
            .document(code)
            .get()
            .addOnSuccessListener codeLookup@{ codeSnapshot ->
                val roomId = codeSnapshot.getString("partidaId").orEmpty()
                if (!codeSnapshot.exists() || roomId.isBlank()) {
                    OnlineDebugLog.w("join_code_not_found code=$code")
                    joinButton.isEnabled = true
                    joinButton.text = "UNIRSE"
                    Toast.makeText(this, "No existe una sala con ese codigo.", Toast.LENGTH_LONG).show()
                    return@codeLookup
                }
                firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
                    .document(roomId)
                    .get()
                    .addOnSuccessListener roomLookup@{ roomSnapshot ->
                        val available = roomSnapshot.exists() &&
                            roomSnapshot.getString(OnlineRoomFirestore.FIELD_STATE) ==
                            OnlineRoomFirestore.STATE_WAITING &&
                            roomSnapshot.getString(OnlineRoomFirestore.FIELD_ROOM_CODE) == code
                        if (!available) {
                            OnlineDebugLog.w("join_code_room_unavailable code=$code roomId=$roomId")
                            joinButton.isEnabled = true
                            joinButton.text = "UNIRSE"
                            Toast.makeText(
                                this,
                                "La sala ya no esta disponible.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@roomLookup
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
                        OnlineDebugLog.e("join_code_room_load_failure code=$code roomId=$roomId", error)
                        joinButton.isEnabled = true
                        joinButton.text = "UNIRSE"
                        Toast.makeText(
                            this,
                            OnlineErrorMessages.forAction("No se pudo abrir la sala", error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
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
            OnlineTempIdentity.ensureAuthenticated(this)
                .addOnSuccessListener {
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
                }
                .addOnFailureListener { error ->
                    OnlineDebugLog.e("auth_join_room_failure roomId=$roomId", error)
                    joinButton.isEnabled = true
                    joinButton.text = "UNIRSE"
                    Toast.makeText(
                        this,
                        OnlineErrorMessages.forAction("No se pudo preparar el ingreso online", error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            return
        }
        val playerName = PlayerPublicIdentity.profileName(this)
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

            val profileCreateData = PlayerPublicIdentity.publicProfileFields(this, publicId, playerName)
            val connectionData = mapOf(
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
                    publicId,
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
                        OnlineRoomFirestore.FIELD_CURRENT_PLAYERS,
                        FieldValue.increment(1)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ONLINE_ACCESS_RETRY_MS = 5_000L
        private const val ROOM_CODE_CREATE_ATTEMPTS = 5
    }
}

