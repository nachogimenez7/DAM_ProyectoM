package com.traidores.juego

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.TextViewCompat

class LobbyActivity : BaseActivity() {

    private lateinit var session: GameSession
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
    private lateinit var mapDescription: TextView
    private var lobbyMode = MODE_LOCAL
    private var onlineLobbyName = ""
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

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val headerLabel: TextView = findViewById(R.id.headerLabel)
        val btnAddPlayer: Button = findViewById(R.id.btnAddPlayer)
        val btnRemovePlayer: Button = findViewById(R.id.btnRemovePlayer)
        val btnAdvancedOptions: Button = findViewById(R.id.btnAdvancedOptions)
        val debugRoleSection: LinearLayout = findViewById(R.id.debugRoleSection)
        debugRoleButton = findViewById(R.id.btnDebugRole)
        timingOptionsButton = findViewById(R.id.btnTimingOptions)
        lobbyTitle = findViewById(R.id.lobbyTitle)
        lobbyModeHint = findViewById(R.id.lobbyModeHint)
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

        btnBack.setOnClickListener { finish() }
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

        if (isOnlineGuest()) {
            btnAddPlayer.isEnabled = false
            btnAddPlayer.alpha = 0.35f
            btnRemovePlayer.isEnabled = false
            btnRemovePlayer.alpha = 0.35f
            timingOptionsButton.isEnabled = false
            timingOptionsButton.alpha = 0.55f
            btnAdvancedOptions.isEnabled = false
            btnAdvancedOptions.alpha = 0.55f
        }

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

    override fun onResume() {
        super.onResume()
        MusicManager.playMenuMusic(this)
    }

    private fun renderLobby() {
        playerCount.text = "${session.players.size}/${LocalGameFactory.MAX_PLAYERS} JUGADORES"
        lobbyTitle.text = when (lobbyMode) {
            MODE_ONLINE_CREATE -> "LOBBY ONLINE - TU SALA"
            MODE_ONLINE_SEARCH -> onlineLobbyName
                .takeIf { it.isNotBlank() }
                ?.let { "LOBBY ONLINE - ${it.uppercase()}" }
                ?: "LOBBY ONLINE - SALA ENCONTRADA"
            MODE_ONLINE_QUICK -> "LOBBY ONLINE - PARTIDA RAPIDA"
            else -> "LOBBY LOCAL"
        }
        lobbyModeHint.text = when (lobbyMode) {
            MODE_ONLINE_CREATE ->
                "Configura la sala y espera a que se sumen jugadores."
            MODE_ONLINE_SEARCH ->
                "Sala encontrada. El anfitrion controla la configuracion."
            MODE_ONLINE_QUICK ->
                "Partida completa. Comenzara cuando el anfitrion confirme."
            else ->
                "Elegi mapa, tiempos y participantes antes de iniciar."
        }
        mapDescription.text = mapDescriptionFor(session.mapKey)
        startButton.isEnabled = !isOnlineGuest() &&
            session.players.size >= LocalGameFactory.MIN_PLAYERS
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        startButton.text = if (isOnlineGuest()) "ESPERANDO AL ANFITRION" else "INICIAR PARTIDA"
        mapName.text = session.mapName.uppercase()
        lobbyMapBackground.setImageResource(currentMap().imageRes)
        mapCards.forEachIndexed { index, imageView ->
            val selected = LocalGameFactory.maps[index].key == session.mapKey
            imageView.alpha = if (selected) 1f else 0.55f
            imageView.setBackgroundResource(if (selected) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark)
        }
        playersContainer.removeAllViews()
        renderDebugRole()
        timingOptionsButton.text = "TIEMPOS"

        session.players.forEachIndexed { index, player ->
            val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
            row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
            row.findViewById<TextView>(R.id.playerName).text = player.name
            row.findViewById<TextView>(R.id.playerStatus).text = if (index == 0) "Anfitrion" else "Listo"
            row.findViewById<ImageButton>(R.id.btnPlayerProfile).setOnClickListener {
                showPlayerProfile(index, player)
            }
            row.findViewById<ImageButton>(R.id.btnKickPlayer).apply {
                isEnabled = index != 0 && !isOnlineGuest()
                alpha = if (isEnabled) 1f else 0.28f
                setOnClickListener { confirmPlayerRemoval(index, player) }
            }
            playersContainer.addView(row)
        }
    }

    private fun setupMapSelector() {
        mapCards.forEachIndexed { index, imageView ->
            val map = LocalGameFactory.maps[index]
            imageView.setImageResource(map.imageRes)
            imageView.setOnClickListener {
                if (isOnlineGuest()) {
                    Toast.makeText(
                        this,
                        "El mapa lo administra el anfitrion.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                session = LocalGameFactory.selectMap(session, map.key)
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

        val dialog = AlertDialog.Builder(this)
            .setView(content)
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
        var debugBotsObeyVoteCommands = session.debugBotsObeyVoteCommands
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
        }
        content.addView(TextView(this).apply {
            text = "COMPOSICION ACTUAL - EDICION PROXIMAMENTE"
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_muted))
            textSize = 11f
            setPadding(0, 0, 0, dp(6))
        })

        val scroll = ScrollView(this)
        val roles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        advancedRoleCatalog().forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.addView(TextView(this).apply {
                text = entry.label
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            row.addView(TextView(this).apply {
                text = entry.count.toString()
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_btn_dark)
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(54), dp(32)))
            roles.addView(row)
        }
        scroll.addView(roles)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(142)
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("APLICAR") { _, _ ->
                session = session.copy(
                    revealRolesOnDeath = revealRolesOnDeath,
                    debugBotsObeyVoteCommands = debugBotsObeyVoteCommands
                )
            }
            .create()
        showLandscapeDialog(dialog, widthDp = 500)
    }

    private fun advancedRoleCatalog(): List<RoleCountPreview> {
        val count = session.players.size
        val entries = mutableListOf(
            RoleCountPreview("ALDEANO", 0),
            RoleCountPreview(if (session.mapKey == "pampa") "COMISARIO" else "DETECTIVE", 1),
            RoleCountPreview("MEDICO", 1),
            RoleCountPreview("ASESINO", 1),
            RoleCountPreview("MERCENARIO", if (count >= 7) 1 else 0),
            RoleCountPreview("ALCALDE", if (count >= 8) 1 else 0),
            RoleCountPreview("DESERTOR", if (count >= 9) 1 else 0),
            RoleCountPreview("ESPIA", if (count >= 10) 1 else 0)
        )
        when (session.mapKey) {
            "pampa" -> entries += RoleCountPreview("PAYADOR", if (count >= 8) 1 else 0)
            "grecia" -> entries += RoleCountPreview("ORACULO", if (count >= 8) 1 else 0)
            "medieval" -> entries += RoleCountPreview("BUFON", if (count >= 8) 1 else 0)
        }
        val specialRoles = entries.drop(1).sumOf { it.count }
        entries[0] = entries[0].copy(count = (count - specialRoles).coerceAtLeast(0))
        return entries
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
            textSize = 15f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_btn_dark_ripple)
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
        dialog.window?.setLayout(dp(widthDp), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setDimAmount(0.55f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        listOf(
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
            AlertDialog.BUTTON_POSITIVE
        ).forEach { buttonId ->
            dialog.getButton(buttonId)?.apply {
                minHeight = dp(44)
                setTextColor(getColor(R.color.accent_gold))
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showPlayerProfile(index: Int, player: GamePlayer) {
        val status = if (index == 0) "Anfitrion de la sala" else "Participante listo"
        val type = when {
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
        if (isOnlineGuest()) {
            Toast.makeText(this, "Solo el anfitrion puede expulsar.", Toast.LENGTH_SHORT).show()
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

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(EXTRA_SESSION) as? GameSession
    }

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_LOBBY_MODE = "extra_lobby_mode"
        const val EXTRA_LOBBY_NAME = "extra_lobby_name"
        const val MODE_LOCAL = "local"
        const val MODE_ONLINE_CREATE = "online_create"
        const val MODE_ONLINE_SEARCH = "online_search"
        const val MODE_ONLINE_QUICK = "online_quick"
    }

    private data class RoleCountPreview(val label: String, val count: Int)

    private enum class TimingField(
        val label: String,
        val step: Int,
        val minimum: Int,
        val maximum: Int
    ) {
        TRANSITION(
            "ENTRE FASES",
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
