package com.traidores.juego

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LobbyBrowserActivity : BaseActivity() {

    private val lobbies = listOf(
        MockOnlineLobby("Peruvians", 8, 9, "Mapa pampeano", "Casi llena", "pampa"),
        MockOnlineLobby("Cayetanos", 10, 15, "Mapa griego", "Esperando", "grecia"),
        MockOnlineLobby("Los Cojenunca", 5, 5, "Mapa medieval", "En partida", "medieval", false),
        MockOnlineLobby("San Miguel 2020", 4, 6, "Mapa pampeano", "Esperando", "pampa"),
        MockOnlineLobby("Hombres Diviertiendose", 1, 7, "Mapa griego", "Esperando", "grecia")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby_browser)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        val lobbyList: LinearLayout = findViewById(R.id.lobbyList)
        lobbies.forEach { lobby ->
            lobbyList.addView(createLobbyRow(lobby))
        }
    }

    private fun createLobbyRow(lobby: MockOnlineLobby): View {
        val row = layoutInflater.inflate(R.layout.item_online_lobby, null, false)
        row.findViewById<TextView>(R.id.lobbyName).text = lobby.name
        row.findViewById<TextView>(R.id.lobbyMap).text = "${lobby.mapName} - Argentina"
        row.findViewById<TextView>(R.id.lobbyPlayers).text = "${lobby.players}/${lobby.limit}"
        row.findViewById<TextView>(R.id.lobbyStatus).apply {
            text = lobby.status
            setTextColor(
                Color.parseColor(
                    when (lobby.status) {
                        "Casi llena" -> "#D4A24E"
                        "En partida" -> "#8F2633"
                        else -> "#5A8A3C"
                    }
                )
            )
        }
        row.findViewById<Button>(R.id.btnEnterLobby).apply {
            val isFull = lobby.players >= lobby.limit
            isEnabled = lobby.canJoin && !isFull
            alpha = if (isEnabled) 1f else 0.42f
            text = when {
                isFull -> "LLENA"
                isEnabled -> "ENTRAR"
                else -> "NO DISPONIBLE"
            }
            setOnClickListener { enterLobby(lobby) }
        }
        return row
    }

    private fun enterLobby(lobby: MockOnlineLobby) {
        val playerName = getSharedPreferences("TraidoresPrefs", Context.MODE_PRIVATE)
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
        val session = LocalGameFactory.createOnlineLobby(
            humanName = playerName,
            playerCount = (lobby.players + 1).coerceAtMost(lobby.limit),
            humanIsHost = false
        ).let { LocalGameFactory.selectMap(it, lobby.mapKey) }
        Toast.makeText(this, "Entrando a ${lobby.name}.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, LobbyActivity::class.java)
                .putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra(LobbyActivity.EXTRA_LOBBY_MODE, LobbyActivity.MODE_ONLINE_SEARCH)
                .putExtra(LobbyActivity.EXTRA_LOBBY_NAME, lobby.name)
        )
    }

    private data class MockOnlineLobby(
        val name: String,
        val players: Int,
        val limit: Int,
        val mapName: String,
        val status: String,
        val mapKey: String,
        val canJoin: Boolean = true
    )
}
