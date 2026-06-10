package com.traidores.juego

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.ImageView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
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
        val debugRoleSection: LinearLayout = findViewById(R.id.debugRoleSection)
        debugRoleButton = findViewById(R.id.btnDebugRole)
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
}
