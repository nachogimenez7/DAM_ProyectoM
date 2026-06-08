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
        btnBack.setOnClickListener { finish() }

        selectedMapTitle = findViewById(R.id.selectedMapTitle)
        selectedMapContext = findViewById(R.id.selectedMapContext)
        recyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<LinearLayout>(R.id.mapMedieval).setOnClickListener { showMapRoles(MapKey.MEDIEVAL) }
        findViewById<LinearLayout>(R.id.mapGreece).setOnClickListener { showMapRoles(MapKey.GREECE) }
        findViewById<LinearLayout>(R.id.mapPampa).setOnClickListener { showMapRoles(MapKey.PAMPA) }

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
        roleFantasy.text = role.story
        roleGameplay.text = role.function
        roleTeam.setTextColor(teamColor(role.team))

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
                "Un feudo que aparenta paz mientras una familia misteriosa compra voluntades y siembra muertes discretas. El alcalde se aferra al poder entre banquetes, paranoia y ordenes que apenas recuerda.",
                "Bufon",
                "mapa_medieval"
            )
            MapKey.GREECE -> MapInfo(
                "Antigua Grecia",
                "Siglo V a.C.",
                "Una polis pequena obsesionada con parecer grande. En la plaza, los discursos de honor esconden hambre, deudas y un culto popular que mata con mascaras doradas en nombre de Ares.",
                "Oraculo",
                "mapa_grecia"
            )
            MapKey.PAMPA -> MapInfo(
                "Pueblo del Interior - 1915",
                "Argentina, 1915",
                "Un pueblucho seco donde el comisario, los patrones y los favores politicos pesan mas que la ley. Una banda peligrosa dice pelear contra la corrupcion, aunque su justicia llega manchada de sangre.",
                "Payador",
                "mapa_pampa"
            )
        }
    }

    private fun baseRoles(mapKey: MapKey): List<Role> {
        val map = mapInfo(mapKey)
        val suffix = roleImageSuffix(mapKey)
        return listOf(
            Role(
                roleName(mapKey, "aldeanos"),
                map.name,
                "Pueblo",
                story(mapKey, "aldeanos"),
                "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
                "rol_aldeano_$suffix"
            ),
            Role(
                "Detective",
                map.name,
                "Pueblo",
                story(mapKey, "detective"),
                "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
                "rol_detective_$suffix"
            ),
            Role(
                roleName(mapKey, "medico"),
                map.name,
                "Pueblo",
                story(mapKey, "medico"),
                "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
                "rol_medico_$suffix"
            ),
            Role(
                "Alcalde",
                map.name,
                "Pueblo",
                story(mapKey, "alcalde"),
                "Puede revelar su identidad durante el debate. Desde ese momento, su voto vale doble.",
                "rol_alcalde_$suffix"
            ),
            Role(
                roleName(mapKey, "asesino"),
                map.name,
                "Asesino",
                story(mapKey, "asesino"),
                "Forma parte del bando traidor. Cada noche participa en la eleccion de la victima.",
                "rol_asesino_$suffix"
            ),
            Role(
                "Espia",
                map.name,
                "Asesino",
                story(mapKey, "espia"),
                "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
                "rol_espia_$suffix"
            ),
            Role(
                "Mercenario",
                map.name,
                "Asesino",
                story(mapKey, "mercenario"),
                "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
                "rol_mercenario_$suffix"
            ),
            Role(
                "Desertora",
                map.name,
                "Neutral",
                story(mapKey, "desertor"),
                "No pertenece al pueblo ni a los traidores. Su objetivo es sobrevivir hasta el final de la partida.",
                "rol_desertor_$suffix"
            )
        )
    }

    private fun exclusiveRole(mapKey: MapKey): Role {
        val map = mapInfo(mapKey)
        return when (mapKey) {
            MapKey.MEDIEVAL -> Role(
                "Bufon",
                map.name,
                "Rol de Mapa",
                story(mapKey, "rolMapa"),
                "Quiere ser eliminado por votacion del pueblo. Si lo consiguen votar, gana su propia condicion especial.",
                "rol_bufon_medieval"
            )
            MapKey.GREECE -> Role(
                "Oraculo",
                map.name,
                "Rol de Mapa",
                story(mapKey, "rolMapa"),
                "Resucita a un jugador muerto para el debate del dia siguiente, sin voto ni accion nocturna.",
                "rol_oraculo_griego"
            )
            MapKey.PAMPA -> Role(
                "Payador",
                map.name,
                "Rol de Mapa",
                story(mapKey, "rolMapa"),
                "Abre el debate de forma exclusiva, decide cuando inicia la votacion y en caso de empate usa el Voto de Gracia.",
                "rol_payador_gaucho"
            )
        }
    }

    private fun roleName(mapKey: MapKey, key: String): String {
        return when {
            key == "aldeanos" -> "Aldeana"
            key == "medico" && mapKey == MapKey.MEDIEVAL -> "Medico"
            key == "medico" -> "Medica"
            key == "asesino" && mapKey == MapKey.GREECE -> "Asesina"
            key == "asesino" -> "Asesino"
            else -> key
        }
    }

    private fun story(mapKey: MapKey, key: String): String {
        return when (mapKey) {
            MapKey.MEDIEVAL -> medievalStories.getValue(key)
            MapKey.GREECE -> greekStories.getValue(key)
            MapKey.PAMPA -> pampaStories.getValue(key)
        }
    }

    private fun roleImageSuffix(mapKey: MapKey): String {
        return when (mapKey) {
            MapKey.MEDIEVAL -> "medieval"
            MapKey.GREECE -> "griego"
            MapKey.PAMPA -> "gaucho"
        }
    }

    private fun teamColor(team: String): Int {
        return when (team.lowercase()) {
            "pueblo" -> Color.parseColor("#4a7fb5")
            "asesino" -> Color.parseColor("#a83232")
            "neutral" -> Color.parseColor("#d7dee8")
            "rol de mapa" -> Color.parseColor("#5a8a3c")
            else -> Color.parseColor("#c4b69c")
        }
    }

    private val medievalStories = mapOf(
        "aldeanos" to "Vive donde el barro llega antes que las noticias. No tiene poder, titulo ni espada, pero conoce cada puerta, cada deuda y cada silencio raro del pueblo. Cuando el feudo empieza a pudrirse, ella lo nota antes que nadie.",
        "detective" to "Antes resolvia robos menores y mentiras de taberna. Ahora sigue rastros de sangre disfrazados de accidentes, sabiendo que descubrir la verdad no sirve de nada si no vive lo suficiente para contarla.",
        "medico" to "Todos en el feudo le deben algo: una fiebre curada, una herida cerrada o una vida salvada a tiempo. No pregunta de que lado viene el paciente, pero recuerda cada corte, cada veneno y cada mentira.",
        "alcalde" to "Gobierna desde una silla demasiado grande y una mesa demasiado llena. Esta perdiendo la cordura entre rumores, copas servidas y ordenes que apenas recuerda, pero todavia conserva el poder de salvar o condenar al feudo.",
        "asesino" to "Una familia misteriosa lo compro con oro, tierras y promesas de un apellido respetado. No mata por fe ni por justicia: mata porque vio una escalera hacia el poder y decidio subirla con las manos manchadas.",
        "espia" to "Aconseja al alcalde desde muy cerca, eligiendo que verdades llegan completas y cuales llegan torcidas. Su arma no es una daga, sino una voz tranquila que alimenta la paranoia del poder.",
        "mercenario" to "No sirve a ningun linaje, solo al contrato correcto. Ha peleado por senores que olvidaron su nombre y ahora cobra para que ciertas conversaciones terminen antes de tiempo.",
        "desertor" to "Una vez juro proteger el feudo, hasta que entendio a quien estaba protegiendo realmente. Desde entonces vive entre caminos y nombres falsos, cargando con la duda de si huyo por cobardia o por lucidez.",
        "rolMapa" to "Sobrevive haciendo reir a quienes podrian mandarlo a morir. Mientras el feudo se hunde en acusaciones, el tropieza, exagera y dice verdades que todos prefieren tomar como broma. Si lo votan, al menos tendra publico completo."
    )

    private val greekStories = mapOf(
        "aldeanos" to "Vende aceite, pan o telas bajo estatuas demasiado grandes para una ciudad tan pequena. Escucha a los hombres hablar de gloria en la plaza mientras ella cuenta monedas para llegar al invierno. No cree en discursos: cree en sobrevivir al proximo decreto.",
        "detective" to "No busca huellas en callejones, sino contradicciones en discursos publicos. Sabe que en la polis nadie miente en silencio: todos mienten frente a testigos, con palabras hermosas y las manos limpias.",
        "medico" to "Aprendio a curar cuerpos entrenados para la guerra y estomagos vacios por culpa de la politica. En su mesa no importa si alguien invoca a Ares, a Atenea o a nadie; la sangre se seca igual en todos.",
        "alcalde" to "No gobierna desde un trono, sino desde la palabra. Cada decision debe sonar justa, fuerte y digna ante una polis que exige grandeza aunque apenas pueda sostenerse. Su miedo no es morir: es quedar como el lider que hizo pequena a su ciudad.",
        "asesino" to "Se puso la mascara dorada para dejar de ser una voz ignorada entre la multitud. El culto le prometio que Ares no escucha plegarias, escucha actos. Desde entonces mata convencida de que cada cuerpo caido obliga a la polis a despertar.",
        "espia" to "No necesita ocultarse en sombras: se sienta en la plaza, aplaude discursos y repite frases en el oido correcto. En una ciudad gobernada por reputaciones, una palabra bien colocada puede arruinar mas que una daga.",
        "mercenario" to "Vino de guerras ajenas, donde los dioses cambiaban de nombre pero los muertos pesaban igual. La polis lo mira como extranjero util: indigno para votar, perfecto para hacer lo que ningun ciudadano quiere admitir.",
        "desertor" to "Abandono la defensa de la polis cuando entendio que sus murallas protegian mas orgullo que vidas. No traiciono una bandera; se nego a morir por discursos escritos por hombres que nunca pisan el campo de batalla.",
        "rolMapa" to "La respetan porque habla con lo que nadie puede controlar. Pero su don no obedece a la politica ni al deseo del pueblo: puede traer de vuelta una verdad salvadora o una voz que incendie la ciudad desde la tumba."
    )

    private val pampaStories = mapOf(
        "aldeanos" to "Vive en un pueblucho donde el polvo entra por las ventanas y las noticias llegan tarde, si llegan. No espera justicia de nadie: ni del comisario, ni del patron, ni del cura. Aprendio que sobrevivir tambien es saber cuando cerrar la boca.",
        "detective" to "No tiene oficina limpia ni placa respetada. Investiga entre pulperias, amenazas y expedientes que desaparecen cuando incomodan a alguien con poder. En este pueblo, encontrar la verdad es facil; lo dificil es que alguien se anime a decirla.",
        "medico" to "Antes curaba heridas de hombres buscados por la ley, sin preguntar demasiado. Logro alejarse de esa vida, pero todavia reconoce el sonido de ciertos pasos en la puerta. Ahora cura al pueblo, aunque sabe que el pasado siempre vuelve montado y con sed.",
        "alcalde" to "Gobierna poco y firma mucho. Entre comisarios corruptos, favores politicos y patrones que hablan mas fuerte que la ley, intenta parecer autoridad en un pueblo donde todos saben quien manda de verdad.",
        "asesino" to "No mata por gloria ni por fe: mata porque la banda lo ordena y porque el miedo funciona mejor que cualquier discurso. Se cree parte de una justicia nueva, pero cada cuerpo que deja atras lo acerca mas a los mismos corruptos que dice odiar.",
        "espia" to "Escucha detras de mostradores, en patios de tierra y en mesas donde el vino afloja la lengua. Nadie le presta demasiada atencion, y por eso mismo sabe mas que todos. En un pueblo chico, el secreto siempre pasa por alguien.",
        "mercenario" to "No pregunta si el trabajo es justo, solo cuanto pagan y quien queda parado al final. Ha servido a comisarios, bandidos y patrones con la misma cara cansada. Su moral dura lo mismo que una bolsa de monedas.",
        "desertor" to "Pertenecio a la banda el tiempo suficiente para entender que el codigo tenia letra chica. Se fue antes de terminar convertida en leyenda o cadaver. Desde entonces cambia de rumbo cada vez que escucha cascos acercarse.",
        "rolMapa" to "Es el hazmerreir del pueblo, pero todos lo quieren un poco. Canta mal cuando esta sobrio y peor cuando esta inspirado, aunque sus coplas siempre terminan juntando gente. En casi todas repite la historia de aquel comisario viejo que una vez le pidio que le diera placer a su esposa mientras el miraba, y nadie sabe si creerle o reirse."
    )

    private enum class MapKey {
        MEDIEVAL,
        GREECE,
        PAMPA
    }
}
