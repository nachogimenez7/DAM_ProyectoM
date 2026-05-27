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

        val items = buildRoleItems()
        val recyclerView: RecyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RoleAdapter(this, items)
    }

    private fun buildRoleItems(): List<RoleListItem> {
        val maps = listOf(
            MapInfo(
                "Feudo de Hierro",
                "Época Medieval",
                "Un feudo entre montañas donde el castillo del señor domina el pueblo. Callejones oscuros, tabernas con secretos y murallas que guardan traiciones.",
                "Bufón",
                "mapa_medieval"
            ),
            MapInfo(
                "Antigua Grecia",
                "Siglo V a.C.",
                "Una polis bañada por el sol mediterráneo, donde los dioses observan y el ágora es el escenario de los debates. La verdad y el engaño se mezclan entre columnas de mármol.",
                "Oráculo",
                "mapa_grecia"
            ),
            MapInfo(
                "La Pampa - 1915",
                "Argentina, 1915",
                "Un pueblo de la llanura pampeana donde la vida transcurre entre pulperías, fogones y guitarreadas. Bajo la calma del campo, alguien trama en secreto.",
                "Payador",
                "mapa_pampa"
            )
        )

        val baseRoles = listOf(
            Role(
                "Aldeano",
                "Pueblo",
                "Rol base sin habilidades especiales. Su fortaleza reside en el debate diurno y en leer las contradicciones de los demás.",
                "rol_aldeano"
            ),
            Role(
                "Detective",
                "Pueblo",
                "Cada noche investiga a un jugador y recibe Inocente o Sospechoso. Debe administrar su información sin exponerse demasiado pronto.",
                "rol_detective"
            ),
            Role(
                "Médico",
                "Pueblo",
                "Protege a un jugador cada noche. Si los Asesinos eligen al protegido, la eliminación se cancela.",
                "rol_medico"
            ),
            Role(
                "Alcalde",
                "Pueblo",
                "Puede revelar su identidad durante el debate para activar un voto doble permanente. La revelación es irreversible.",
                "rol_alcalde"
            ),
            Role(
                "Asesino",
                "Asesino",
                "Conoce a sus compañeros desde el inicio. De noche elige una víctima y de día intenta pasar por inocente.",
                "rol_asesino"
            ),
            Role(
                "Espía",
                "Asesino",
                "Pertenece al bando asesino, pero aparece como Inocente ante las investigaciones del Detective.",
                "rol_espia"
            ),
            Role(
                "Mercenario",
                "Asesino",
                "Puede silenciar a una víctima para impedirle hablar o votar durante la siguiente fase de día.",
                "rol_mercenario"
            ),
            Role(
                "Desertor",
                "Neutral",
                "Solo quiere sobrevivir hasta el final, sin importar qué bando gane. Puede alinearse con quien le convenga.",
                "rol_desertor"
            )
        )

        val mapRoles = mapOf(
            "Feudo de Hierro" to Role(
                "Bufón",
                "Feudo de Hierro",
                "Su objetivo es ser ejecutado por votación popular. Si lo votan, gana de inmediato; si muere de noche, pierde.",
                "rol_bufon"
            ),
            "Antigua Grecia" to Role(
                "Oráculo",
                "Antigua Grecia",
                "Resucita temporalmente a un jugador muerto para el debate del día siguiente, sin voto ni acción nocturna.",
                "rol_oraculo"
            ),
            "Pampa Argentina 1915" to Role(
                "Payador",
                "Pampa Argentina 1915",
                "Abre el debate, decide cuándo inicia la votación y en caso de empate usa el Voto de Gracia.",
                "rol_payador"
            )
        )

        val items = mutableListOf<RoleListItem>()
        items += RoleListItem.SectionHeader("MAPAS")
        maps.forEach { items += RoleListItem.MapCard(it) }

        items += RoleListItem.SectionHeader("ROLES - FEUDO DE HIERRO")
        items += baseRoles.map { RoleListItem.RoleCard(it) }
        items += RoleListItem.RoleCard(mapRoles.getValue("Feudo de Hierro"))

        items += RoleListItem.SectionHeader("ROLES - ANTIGUA GRECIA")
        items += baseRoles.map { RoleListItem.RoleCard(it) }
        items += RoleListItem.RoleCard(mapRoles.getValue("Antigua Grecia"))

        items += RoleListItem.SectionHeader("ROLES - PAMPA ARGENTINA 1915")
        items += baseRoles.map { RoleListItem.RoleCard(it) }
        items += RoleListItem.RoleCard(mapRoles.getValue("Pampa Argentina 1915"))

        return items
    }
}
