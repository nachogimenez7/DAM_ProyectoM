package com.traidores.juego

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RolesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var selectedMapTitle: TextView
    private lateinit var selectedMapContext: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roles)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        selectedMapTitle = findViewById(R.id.selectedMapTitle)
        selectedMapContext = findViewById(R.id.selectedMapContext)
        recyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val mapMedieval: LinearLayout = findViewById(R.id.mapMedieval)
        val mapGreece: LinearLayout = findViewById(R.id.mapGreece)
        val mapPampa: LinearLayout = findViewById(R.id.mapPampa)

        mapMedieval.setOnClickListener { showMapRoles(MapKey.MEDIEVAL) }
        mapGreece.setOnClickListener { showMapRoles(MapKey.GREECE) }
        mapPampa.setOnClickListener { showMapRoles(MapKey.PAMPA) }

        showMapRoles(MapKey.MEDIEVAL)
    }

    private fun showMapRoles(mapKey: MapKey) {
        val map = mapInfo(mapKey)
        selectedMapTitle.text = map.name
        selectedMapContext.text = "${map.description} Rol exclusivo: ${map.exclusiveRole}."
        recyclerView.adapter = RoleAdapter(this, buildRoleItems(mapKey))
    }

    private fun buildRoleItems(mapKey: MapKey): List<RoleListItem> {
        val items = mutableListOf<RoleListItem>()
        items += RoleListItem.SectionHeader("ROLES DEL MAPA")
        items += baseRoles(mapKey).map { RoleListItem.RoleCard(it) }
        items += RoleListItem.RoleCard(exclusiveRole(mapKey))
        return items
    }

    private fun mapInfo(mapKey: MapKey): MapInfo {
        return when (mapKey) {
            MapKey.MEDIEVAL -> MapInfo(
                "Feudo de Hierro",
                "Epoca Medieval",
                "Un feudo entre montanas donde el castillo domina el pueblo. Callejones oscuros, tabernas con secretos y murallas que guardan traiciones.",
                "Bufon",
                "mapa_medieval"
            )
            MapKey.GREECE -> MapInfo(
                "Antigua Grecia",
                "Siglo V a.C.",
                "Una polis mediterranea donde los dioses observan y el agora concentra debates, sospechas y pactos.",
                "Oraculo",
                "mapa_grecia"
            )
            MapKey.PAMPA -> MapInfo(
                "Pampa Argentina 1915",
                "Argentina, 1915",
                "Un pueblo de llanura entre pulperias, fogones y guitarreadas. Bajo la calma del campo, alguien trama en secreto.",
                "Payador",
                "mapa_pampa"
            )
        }
    }

    private fun baseRoles(mapKey: MapKey): List<Role> {
        val suffix = roleImageSuffix(mapKey)
        return listOf(
            Role(
                "Aldeano",
                "Pueblo",
                "Rol base sin habilidades especiales. Su fortaleza reside en el debate diurno y en leer las contradicciones de los demas.",
                "rol_aldeano_$suffix"
            ),
            Role(
                "Detective",
                "Pueblo",
                "Cada noche investiga a un jugador y recibe Inocente o Sospechoso. Debe administrar su informacion sin exponerse demasiado pronto.",
                "rol_detective_$suffix"
            ),
            Role(
                "Medico",
                "Pueblo",
                "Protege a un jugador cada noche. Si los Asesinos eligen al protegido, la eliminacion se cancela.",
                "rol_medico_$suffix"
            ),
            Role(
                "Alcalde",
                "Pueblo",
                "Puede revelar su identidad durante el debate para activar un voto doble permanente. La revelacion es irreversible.",
                "rol_alcalde_$suffix"
            ),
            Role(
                "Asesino",
                "Asesino",
                "Conoce a sus companeros desde el inicio. De noche elige una victima y de dia intenta pasar por inocente.",
                "rol_asesino_$suffix"
            ),
            Role(
                "Espia",
                "Asesino",
                "Pertenece al bando asesino, pero aparece como Inocente ante las investigaciones del Detective.",
                "rol_espia_$suffix"
            ),
            Role(
                "Mercenario",
                "Asesino",
                "Puede silenciar a una victima para impedirle hablar o votar durante la siguiente fase de dia.",
                "rol_mercenario_$suffix"
            ),
            Role(
                "Desertor",
                "Neutral",
                "Solo quiere sobrevivir hasta el final, sin importar que bando gane. Puede alinearse con quien le convenga.",
                "rol_desertor_$suffix"
            )
        )
    }

    private fun exclusiveRole(mapKey: MapKey): Role {
        return when (mapKey) {
            MapKey.MEDIEVAL -> Role(
                "Bufon",
                "Feudo de Hierro",
                "Su objetivo es ser ejecutado por votacion popular. Si lo votan, gana de inmediato; si muere de noche, pierde.",
                "rol_bufon_medieval"
            )
            MapKey.GREECE -> Role(
                "Oraculo",
                "Antigua Grecia",
                "Resucita temporalmente a un jugador muerto para el debate del dia siguiente, sin voto ni accion nocturna.",
                "rol_oraculo_griego"
            )
            MapKey.PAMPA -> Role(
                "Payador",
                "Pampa Argentina 1915",
                "Abre el debate, decide cuando inicia la votacion y en caso de empate usa el Voto de Gracia.",
                "rol_payador_gaucho"
            )
        }
    }

    private fun roleImageSuffix(mapKey: MapKey): String {
        return when (mapKey) {
            MapKey.MEDIEVAL -> "medieval"
            MapKey.GREECE -> "griego"
            MapKey.PAMPA -> "gaucho"
        }
    }

    private enum class MapKey {
        MEDIEVAL,
        GREECE,
        PAMPA
    }
}

