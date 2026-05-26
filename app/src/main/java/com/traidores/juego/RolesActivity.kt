package com.traidores.juego

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RolesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roles)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val rolesList = listOf(
            Role(
                "Aldeano",
                "Pueblo",
                "Rol base sin habilidades. Su fuerza reside en el debate y la votación diurna para descubrir a los traidores.",
                "rol_aldeano"
            ),
            Role(
                "Detective",
                "Pueblo",
                "Investiga a un jugador por noche para descubrir si pertenece al bando de los Asesinos.",
                "rol_detective"
            ),
            Role(
                "Médico",
                "Pueblo",
                "Protege a un jugador cada noche de ser eliminado por los Asesinos. No puede protegerse a sí mismo dos noches seguidas.",
                "rol_medico"
            ),
            Role(
                "Alcalde",
                "Pueblo",
                "Puede revelar su identidad públicamente. A partir de ese momento, su voto en las decisiones del día vale por dos.",
                "rol_alcalde"
            ),
            Role(
                "Asesino",
                "Asesino",
                "Elige a una víctima cada noche en secreto junto a sus aliados. Debe mentir y actuar de inocente durante el día.",
                "rol_asesino"
            ),
            Role(
                "Espía",
                "Asesino",
                "Miembro del bando asesino que posee la habilidad pasiva de aparecer como inocente (Pueblo) ante las investigaciones del Detective.",
                "rol_espia"
            ),
            Role(
                "Mercenario",
                "Asesino",
                "Puede elegir a un jugador durante la noche para silenciarlo, impidiéndole hablar o votar durante el día siguiente.",
                "rol_mercenario"
            ),
            Role(
                "Oráculo",
                "Rol de Mapa",
                "Habilidad especial de mapa. Permite comunicarse con el más allá y resucitar temporalmente a un jugador eliminado para interrogarlo.",
                "rol_oraculo"
            ),
            Role(
                "Payador",
                "Rol de Mapa",
                "Habilidad especial de mapa. Controla el ritmo del debate y posee el voto de gracia decisivo en caso de empates en la votación.",
                "rol_payador"
            )
        )

        val recyclerView: RecyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RoleAdapter(this, rolesList)
    }
}
