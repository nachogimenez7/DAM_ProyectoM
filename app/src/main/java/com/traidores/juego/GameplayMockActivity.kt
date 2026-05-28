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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameplay_mock)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnRevealCard: Button = findViewById(R.id.btnRevealCard)
        val btnVote: Button = findViewById(R.id.btnVote)
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

        btnRevealCard.setOnClickListener {
            isCardRevealed = !isCardRevealed
            if (isCardRevealed) {
                roleImage.setBackgroundColor(Color.TRANSPARENT)
                roleImage.setImageResource(R.drawable.rol_detective_medieval)
                roleName.text = "DETECTIVE"
                btnRevealCard.text = "OCULTAR CARTA"
                currentPlayerHint.text = "Tu rol esta revelado para esta prueba. En gameplay real solo se muestra cuando una regla lo permite."
            } else {
                roleImage.setImageDrawable(null)
                roleImage.setBackgroundResource(R.drawable.bg_card_back)
                roleName.text = "ROL OCULTO"
                btnRevealCard.text = "REVELAR CARTA"
                currentPlayerHint.text = "Tu carta esta oculta. Revelala solo cuando una regla lo permita."
            }
        }

        btnVote.setOnClickListener {
            if (selectedTarget.isBlank()) {
                Toast.makeText(this, "Selecciona un jugador primero.", Toast.LENGTH_SHORT).show()
            } else {
                phaseTitle.text = "VOTACION"
                phaseSubtitle.text = "Voto registrado contra $selectedTarget. Este mock todavia no calcula resultados."
                Toast.makeText(this, "Voto contra $selectedTarget registrado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlayer(root: View, name: String, initial: String) {
        val avatar: TextView = root.findViewById(R.id.playerAvatar)
        val playerName: TextView = root.findViewById(R.id.playerName)
        val status: TextView = root.findViewById(R.id.playerStatus)

        avatar.text = initial
        playerName.text = name
        status.text = "En juego"

        root.setOnClickListener {
            selectedTarget = name
            findViewById<TextView>(R.id.targetName).text = name
        }
    }
}
