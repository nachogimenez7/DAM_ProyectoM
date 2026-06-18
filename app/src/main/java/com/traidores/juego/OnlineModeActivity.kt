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
import com.google.firebase.firestore.FirebaseFirestore

class OnlineModeActivity : BaseActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnCreate: Button
    private lateinit var btnJoinCode: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_mode)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnQuick: Button = findViewById(R.id.btnQuick)
        val btnSearch: Button = findViewById(R.id.btnSearch)
        btnJoinCode = findViewById(R.id.btnJoinCode)
        btnCreate = findViewById(R.id.btnCreate)

        btnBack.setOnClickListener { finish() }

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
            createOnlineRoom()
        }
    }

    private fun createOnlineRoom() {
        btnCreate.isEnabled = false
        btnCreate.text = "CREANDO..."

        val preferences = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
        val playerName = preferences
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val selectedMap = OnlineRoomFirestore.selectedMapFromKey(
            preferences.getString(OpcionesActivity.PREF_LAST_SELECTED_MAP, null).orEmpty()
        )
        val creation = OnlineRoomFirestore.createRoom(
            firestore = firestore,
            playerName = playerName,
            uidTemporal = OnlineTempIdentity.getOrCreate(this),
            map = selectedMap,
            origin = "android-online-create"
        )

        creation.commitTask
            .addOnSuccessListener {
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
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
                )
            }
            .addOnFailureListener { error ->
                btnCreate.isEnabled = true
                btnCreate.text = "CREAR PARTIDA"
                Toast.makeText(
                    this,
                    "No se pudo crear la sala: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
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
            text = "Ingresa el codigo de 5 caracteres de la sala."
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
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(5))
            hint = "ABCDE"
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
            if (code.length != 5) {
                Toast.makeText(this, "El codigo debe tener 5 caracteres.", Toast.LENGTH_SHORT).show()
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

        firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION)
            .whereEqualTo(OnlineRoomFirestore.FIELD_ROOM_CODE, code)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val roomSnapshot = snapshot.documents.firstOrNull()
                if (roomSnapshot == null) {
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
                joinButton.isEnabled = true
                joinButton.text = "UNIRSE"
                Toast.makeText(
                    this,
                    "No se pudo buscar la sala: ${error.message}",
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
        joinButton: Button
    ) {
        val playerName = OnlineRoomFirestore.normalizedPlayerName(
            getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
                .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
                .orEmpty()
        )
        val uidTemporal = OnlineTempIdentity.getOrCreate(this)
        val roomReference = firestore.collection(OnlineRoomFirestore.ROOMS_COLLECTION).document(roomId)
        val playerReference = roomReference.collection(OnlineRoomFirestore.PLAYERS_COLLECTION)
            .document(uidTemporal)

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
            val currentPlayers = freshRoom.getLong(OnlineRoomFirestore.FIELD_CURRENT_PLAYERS) ?: 0L
            val limit = freshRoom.getLong(OnlineRoomFirestore.FIELD_MAX_PLAYERS)
                ?: OnlineRoomFirestore.DEFAULT_MAX_PLAYERS.toLong()

            if (!alreadyJoined && currentPlayers >= limit) {
                throw IllegalStateException("La sala esta llena.")
            }

            val connectedData = mapOf(
                OnlineRoomFirestore.FIELD_NAME to playerName,
                OnlineRoomFirestore.FIELD_PLAYER_STATE to "conectado",
                "uidTemporal" to uidTemporal,
                OnlineRoomFirestore.FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp()
            )
            if (alreadyJoined) {
                transaction.update(playerReference, connectedData)
            } else {
                transaction.set(
                    playerReference,
                    connectedData + mapOf(
                        OnlineRoomFirestore.FIELD_IS_HOST to false,
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
            dialog.dismiss()
            val finalMapKey = resolvedMapKey.ifBlank { mapKey }
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
            )
        }.addOnFailureListener { error ->
            joinButton.isEnabled = true
            joinButton.text = "UNIRSE"
            Toast.makeText(
                this,
                "No se pudo entrar a la sala: ${error.message}",
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
}

