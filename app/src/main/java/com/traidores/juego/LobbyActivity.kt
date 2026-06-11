package com.traidores.juego

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
    private var debugRoleIndex = 0

    private val debugRoles = listOf(
        "" to "AZAR",
        "asesino" to "ASESINO",
        "mercenario" to "MERCENARIO",
        "policia" to "COMISARIO",
        "medico" to "MEDICO",
        "alcalde" to "ALCALDE",
        "payador" to "PAYADOR",
        "desertor" to "DESERTOR",
        "espia" to "ESPIA",
        "aldeano" to "ALDEANO"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)

        session = readSession() ?: LocalGameFactory.createSession()

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val headerLabel: TextView = findViewById(R.id.headerLabel)
        val btnAddPlayer: Button = findViewById(R.id.btnAddPlayer)
        val btnRemovePlayer: Button = findViewById(R.id.btnRemovePlayer)
        val btnAdvancedOptions: Button = findViewById(R.id.btnAdvancedOptions)
        val debugRoleSection: LinearLayout = findViewById(R.id.debugRoleSection)
        debugRoleButton = findViewById(R.id.btnDebugRole)
        timingOptionsButton = findViewById(R.id.btnTimingOptions)
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
        debugRoleSection.visibility = if (isDebugBuild) View.VISIBLE else View.GONE
        debugRoleButton.setOnClickListener {
            debugRoleIndex = (debugRoleIndex + 1) % debugRoles.size
            renderDebugRole()
        }
        timingOptionsButton.setOnClickListener { showTimingDialog() }
        btnAdvancedOptions.setOnClickListener { showAdvancedOptionsDialog() }

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
                Toast.makeText(this, "Minimo ${LocalGameFactory.MIN_PLAYERS} jugadores para iniciar.", Toast.LENGTH_SHORT).show()
            }
            session = updated
            renderLobby()
        }

        startButton.setOnClickListener {
            val selectedRoleKey = debugRoles[debugRoleIndex].first
            val minimumPlayers = LocalGameFactory.minimumPlayersForRole(selectedRoleKey)
            if (selectedRoleKey == "payador" && session.mapKey != "pampa") {
                Toast.makeText(
                    this,
                    "El Payador solo aparece en el mapa Pampa.",
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
                Toast.makeText(this, "Iniciando partida local.", Toast.LENGTH_SHORT).show()
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
        startButton.isEnabled = session.players.size >= LocalGameFactory.MIN_PLAYERS
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        mapName.text = session.mapName.uppercase()
        lobbyMapBackground.setImageResource(currentMap().imageRes)
        mapCards.forEachIndexed { index, imageView ->
            val selected = LocalGameFactory.maps[index].key == session.mapKey
            imageView.alpha = if (selected) 1f else 0.55f
            imageView.setBackgroundResource(if (selected) R.drawable.bg_btn_gold else R.drawable.bg_btn_dark)
        }
        playersContainer.removeAllViews()
        renderDebugRole()
        timingOptionsButton.text = "TIEMPOS  ${session.timingConfig.normalized().summary()}"

        session.players.forEachIndexed { index, player ->
            val row = layoutInflater.inflate(R.layout.item_lobby_player, playersContainer, false)
            row.findViewById<TextView>(R.id.playerAvatar).text = player.initial
            row.findViewById<TextView>(R.id.playerName).text = player.name
            row.findViewById<TextView>(R.id.playerStatus).text = if (index == 0) "Anfitrion" else "Listo"
            row.setOnClickListener { onPlayerClicked(index, player) }
            playersContainer.addView(row)
        }
    }

    private fun setupMapSelector() {
        mapCards.forEachIndexed { index, imageView ->
            val map = LocalGameFactory.maps[index]
            imageView.setImageResource(map.imageRes)
            imageView.setOnClickListener {
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
        fun refreshValues() {
            valueViews.forEach { (field, view) ->
                view.text = "${field.value(draft)} s"
            }
        }

        TimingField.entries.forEach { field ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val label = TextView(this).apply {
                text = field.label
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
            }
            row.addView(label, LinearLayout.LayoutParams(0, dp(34), 1f))

            val minus = compactDialogButton("-")
            val value = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
            }
            val plus = compactDialogButton("+")
            valueViews[field] = value
            minus.setOnClickListener {
                draft = field.update(draft, field.value(draft) - field.step)
                refreshValues()
            }
            plus.setOnClickListener {
                draft = field.update(draft, field.value(draft) + field.step)
                refreshValues()
            }
            row.addView(minus, LinearLayout.LayoutParams(dp(36), dp(32)))
            row.addView(value, LinearLayout.LayoutParams(dp(64), dp(32)))
            row.addView(plus, LinearLayout.LayoutParams(dp(36), dp(32)))
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
                draft = GameTimingConfig()
                refreshValues()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                session = session.copy(timingConfig = draft.normalized())
                renderLobby()
                dialog.dismiss()
            }
        }
        showLandscapeDialog(dialog, widthDp = 520)
    }

    private fun showAdvancedOptionsDialog() {
        val content = dialogColumn()
        content.addView(dialogTitle("OPCIONES AVANZADAS"))
        content.addView(TextView(this).apply {
            text = "COMPOSICION AUTOMATICA - PROXIMAMENTE"
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
                textSize = 12f
            }, LinearLayout.LayoutParams(0, dp(30), 1f))
            row.addView(compactDialogButton("-").apply {
                isEnabled = false
                alpha = 0.35f
            }, LinearLayout.LayoutParams(dp(34), dp(28)))
            row.addView(TextView(this).apply {
                text = entry.count.toString()
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent_gold))
                textSize = 13f
            }, LinearLayout.LayoutParams(dp(48), dp(28)))
            row.addView(compactDialogButton("+").apply {
                isEnabled = false
                alpha = 0.35f
            }, LinearLayout.LayoutParams(dp(34), dp(28)))
            roles.addView(row)
        }
        scroll.addView(roles)
        content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(230)))

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton("CERRAR", null)
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
            "grecia" -> entries += RoleCountPreview("ORACULO", 0)
            "medieval" -> entries += RoleCountPreview("BUFON", 0)
        }
        val specialRoles = entries.drop(1).sumOf { it.count }
        entries[0] = entries[0].copy(count = (count - specialRoles).coerceAtLeast(0))
        return entries
    }

    private fun dialogColumn(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
            setBackgroundResource(R.drawable.bg_translucent_game_panel)
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent_gold))
            textSize = 18f
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
            setBackgroundResource(R.drawable.bg_btn_dark)
        }
    }

    private fun showLandscapeDialog(dialog: AlertDialog, widthDp: Int) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(widthDp), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setDimAmount(0.55f)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun onPlayerClicked(index: Int, player: GamePlayer) {
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

    @Suppress("DEPRECATION")
    private fun readSession(): GameSession? {
        return intent.getSerializableExtra(EXTRA_SESSION) as? GameSession
    }

    companion object {
        const val EXTRA_SESSION = "extra_session"
    }

    private data class RoleCountPreview(val label: String, val count: Int)

    private enum class TimingField(
        val label: String,
        val step: Int
    ) {
        TRANSITION("ENTRE FASES", GameTimingConfig.TRANSITION_STEP_SECONDS),
        NIGHT("ACCION NOCTURNA", GameTimingConfig.NIGHT_STEP_SECONDS),
        DISCUSSION("DISCUSION", GameTimingConfig.DISCUSSION_STEP_SECONDS),
        VOTING("VOTACION", GameTimingConfig.VOTING_STEP_SECONDS);

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
