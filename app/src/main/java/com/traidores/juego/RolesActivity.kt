package com.traidores.juego

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
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
        recyclerView.adapter = RoleAdapter(this, buildRoleItems(mapKey)) { role ->
            showRoleDetail(role)
        }
    }

    private fun showRoleDetail(role: Role) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_role_detail)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val roleImage: ImageView = dialog.findViewById(R.id.detailRoleImage)
        val roleName: TextView = dialog.findViewById(R.id.detailRoleName)
        val roleTeam: TextView = dialog.findViewById(R.id.detailRoleTeam)
        val roleFantasy: TextView = dialog.findViewById(R.id.detailRoleFantasy)
        val roleGameplay: TextView = dialog.findViewById(R.id.detailRoleGameplay)
        val closeButton: ImageButton = dialog.findViewById(R.id.btnCloseRoleDetail)

        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        roleImage.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
        roleName.text = role.name.uppercase()
        roleTeam.text = role.team.uppercase()
        roleFantasy.text = role.fantasyDescription
        roleGameplay.text = role.gameplayDescription

        val teamColor = when (role.team.lowercase()) {
            "pueblo" -> Color.parseColor("#4a7fb5")
            "asesino" -> Color.parseColor("#a83232")
            "neutral" -> Color.parseColor("#d4a24e")
            "feudo de hierro", "antigua grecia", "pampa argentina 1915" -> Color.parseColor("#5a8a3c")
            else -> Color.parseColor("#c4b69c")
        }
        roleTeam.setTextColor(teamColor)
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
                "rol_aldeano_$suffix",
                "Conoce cada esquina del pueblo y sabe que la verdad suele esconderse en los silencios.",
                "No tiene accion nocturna. Participa en el debate y vota para descubrir a los traidores."
            ),
            Role(
                "Detective",
                "Pueblo",
                "Cada noche investiga a un jugador y recibe Inocente o Sospechoso. Debe administrar su informacion sin exponerse demasiado pronto.",
                "rol_detective_$suffix",
                "Observa gestos, rutas y coartadas con la paciencia de quien ya vio demasiadas mentiras.",
                "Cada noche elige un jugador para investigar y obtiene una pista sobre su bando."
            ),
            Role(
                "Medico",
                "Pueblo",
                "Protege a un jugador cada noche. Si los Asesinos eligen al protegido, la eliminacion se cancela.",
                "rol_medico_$suffix",
                "Carga remedios, vendas y secretos suficientes para mantener vivo a quien no deberia caer.",
                "Cada noche protege a un jugador. Si ese jugador era la victima, evita su eliminacion."
            ),
            Role(
                "Alcalde",
                "Pueblo",
                "Puede revelar su identidad durante el debate para activar un voto doble permanente. La revelacion es irreversible.",
                "rol_alcalde_$suffix",
                "Su voz pesa mas que la de otros, pero anunciarse demasiado pronto puede convertirlo en blanco.",
                "Puede revelar su rol durante el dia. Desde entonces su voto cuenta doble."
            ),
            Role(
                "Asesino",
                "Asesino",
                "Conoce a sus companeros desde el inicio. De noche elige una victima y de dia intenta pasar por inocente.",
                "rol_asesino_$suffix",
                "Camina entre todos con una calma calculada, esperando que la sospecha caiga sobre otro.",
                "Conoce a los demas asesinos. Durante la noche el bando asesino elige una victima."
            ),
            Role(
                "Espia",
                "Asesino",
                "Pertenece al bando asesino, pero aparece como Inocente ante las investigaciones del Detective.",
                "rol_espia_$suffix",
                "Sonrie cuando conviene, escucha donde nadie mira y convierte cada rumor en una herramienta.",
                "Juega con los asesinos, pero las investigaciones lo muestran como inocente."
            ),
            Role(
                "Mercenario",
                "Asesino",
                "Puede silenciar a una victima para impedirle hablar o votar durante la siguiente fase de dia.",
                "rol_mercenario_$suffix",
                "No pregunta por causas ni juramentos: solo por el precio y por quien debe quedar atado.",
                "Puede dejar a un jugador sin hablar ni votar durante la siguiente fase de dia."
            ),
            Role(
                "Desertor",
                "Neutral",
                "Solo quiere sobrevivir hasta el final, sin importar que bando gane. Puede alinearse con quien le convenga.",
                "rol_desertor_$suffix",
                "Ya abandono una bandera y podria abandonar otra. Su lealtad dura lo que dura su ventaja.",
                "Gana si sobrevive hasta el final. Puede colaborar con cualquier bando segun le convenga."
            )
        )
    }

    private fun exclusiveRole(mapKey: MapKey): Role {
        return when (mapKey) {
            MapKey.MEDIEVAL -> Role(
                "Bufon",
                "Feudo de Hierro",
                "Su objetivo es ser ejecutado por votacion popular. Si lo votan, gana de inmediato; si muere de noche, pierde.",
                "rol_bufon_medieval",
                "Molesta, provoca y exagera cada gesto hasta que todos desean verlo fuera de la plaza.",
                "Gana si el pueblo lo ejecuta por votacion. Pierde si muere durante la noche."
            )
            MapKey.GREECE -> Role(
                "Oraculo",
                "Antigua Grecia",
                "Resucita temporalmente a un jugador muerto para el debate del dia siguiente, sin voto ni accion nocturna.",
                "rol_oraculo_griego",
                "Dice escuchar voces que llegan desde mas alla del velo, aunque nadie sabe cuanto creerle.",
                "Puede traer temporalmente a un eliminado para aportar informacion durante el debate."
            )
            MapKey.PAMPA -> Role(
                "Payador",
                "Pampa Argentina 1915",
                "Abre el debate, decide cuando inicia la votacion y en caso de empate usa el Voto de Gracia.",
                "rol_payador_gaucho",
                "Marca el ritmo de la ronda con palabra filosa, copla rapida y mirada atenta.",
                "Ordena el debate, habilita la votacion y puede desempatar con su Voto de Gracia."
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

