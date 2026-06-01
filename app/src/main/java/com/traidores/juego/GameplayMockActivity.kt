package com.traidores.juego

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class GameplayMockActivity : BaseActivity() {

    private var isCardRevealed = false
    private var selectedTarget = ""
    private var phaseIndex = 0
    private val playerRows = mutableListOf<View>()
    private val phases = listOf(
        Phase("NOCHE 1", "Los roles nocturnos eligen objetivo. Para esta demo, selecciona a alguien e investiga."),
        Phase("DEBATE", "Todos discuten lo ocurrido. Observa acusaciones, coartadas y silencios."),
        Phase("VOTACION", "Elige un jugador y registra tu voto. Luego se resolveria la eliminacion."),
        Phase("RESULTADO", "Demo local: aca se mostraria quien fue eliminado y pasaria la siguiente ronda.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnRevealCard: Button = findViewById(R.id.btnRevealCard)
        val btnVote: Button = findViewById(R.id.btnVote)
        val btnNextPhase: Button = findViewById(R.id.btnNextPhase)
        val roleImage: ImageView = findViewById(R.id.roleImage)
        val roleName: TextView = findViewById(R.id.roleName)
        val phaseTitle: TextView = findViewById(R.id.phaseTitle)
        val phaseSubtitle: TextView = findViewById(R.id.phaseSubtitle)
        val currentPlayerHint: TextView = findViewById(R.id.currentPlayerHint)

        btnBack.setOnClickListener { finish() }

        setupPlayer(findViewById(R.id.playerL1), "Martina", "M")
        setupPlayer(findViewById(R.id.playerL2), "Tomas", "T")
        setupPlayer(findViewById(R.id.playerL3), "Sofia", "S")
        setupPlayer(findViewById(R.id.playerR1), "Camila", "C")
        setupPlayer(findViewById(R.id.playerR2), "Juan", "J")
        setupPlayer(findViewById(R.id.playerR3), "Albert", "A")
        updatePhase(phaseTitle, phaseSubtitle)

        btnRevealCard.setOnClickListener {
            isCardRevealed = !isCardRevealed
            if (isCardRevealed) {
                roleImage.setBackgroundColor(Color.TRANSPARENT)
                roleImage.setImageResource(R.drawable.rol_detective_gaucho)
                roleName.text = "COMISARIO"
                btnRevealCard.text = "OCULTAR CARTA"
                currentPlayerHint.text = "Rol revelado para esta prueba. En partida real se mostraria solo cuando la regla lo permita."
            } else {
                roleImage.setImageDrawable(null)
                roleImage.setBackgroundResource(R.drawable.bg_card_back)
                roleName.text = "OCULTO"
                btnRevealCard.text = "REVELAR"
                currentPlayerHint.text = "Tu carta esta oculta. Revelala solo cuando una regla lo permita."
            }
        }

        btnVote.setOnClickListener {
            if (selectedTarget.isBlank()) {
                Toast.makeText(this, "Selecciona un jugador primero.", Toast.LENGTH_SHORT).show()
            } else {
                phaseIndex = 2
                updatePhase(phaseTitle, phaseSubtitle)
                Toast.makeText(this, "Voto contra $selectedTarget registrado.", Toast.LENGTH_SHORT).show()
            }
        }

        btnNextPhase.setOnClickListener {
            phaseIndex = (phaseIndex + 1) % phases.size
            updatePhase(phaseTitle, phaseSubtitle)
        }
    }

    private fun setupPlayer(root: View, name: String, initial: String) {
        playerRows += root
        val avatar: TextView = root.findViewById(R.id.playerAvatar)
        val playerName: TextView = root.findViewById(R.id.playerName)
        val status: TextView = root.findViewById(R.id.playerStatus)

        avatar.text = initial
        playerName.text = name
        status.text = "En juego"

        root.setOnClickListener {
            clearPlayerTargets()
            selectedTarget = name
            findViewById<TextView>(R.id.targetName).text = name
            status.text = "Objetivo"
            Toast.makeText(this, "$name seleccionado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePhase(title: TextView, subtitle: TextView) {
        val phase = phases[phaseIndex]
        title.text = phase.title
        subtitle.text = phase.subtitle
    }

    private fun clearPlayerTargets() {
        playerRows.forEach { row ->
            row.findViewById<TextView>(R.id.playerStatus).text = "En juego"
        }
    }

    private data class Phase(
        val title: String,
        val subtitle: String
    )
}
