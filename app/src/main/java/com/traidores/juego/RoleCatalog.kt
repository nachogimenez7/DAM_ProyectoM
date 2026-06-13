package com.traidores.juego

enum class RoleMap(
    val sessionKey: String,
    val imageSuffix: String
) {
    MEDIEVAL("medieval", "medieval"),
    GREECE("grecia", "griego"),
    PAMPA("pampa", "gaucho");

    companion object {
        fun fromSessionKey(key: String): RoleMap {
            return entries.firstOrNull { it.sessionKey == key } ?: PAMPA
        }
    }
}

data class RoleDefinition(
    val key: String,
    val team: String,
    val function: String,
    val minimumPlayers: Int,
    val exclusiveMap: RoleMap? = null,
    val displayCategory: String = team
)

object RoleCatalog {

    const val ALDEANO = "aldeano"
    const val POLICIA = "policia"
    const val MEDICO = "medico"
    const val ALCALDE = "alcalde"
    const val ASESINO = "asesino"
    const val ESPIA = "espia"
    const val MERCENARIO = "mercenario"
    const val DESERTOR = "desertor"
    const val PAYADOR = "payador"
    const val BUFON = "bufon"
    const val ORACULO = "oraculo"

    private val definitions = listOf(
        RoleDefinition(
            ALDEANO,
            GameRules.TOWN_WINNER,
            "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
            5
        ),
        RoleDefinition(
            POLICIA,
            GameRules.TOWN_WINNER,
            "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
            5
        ),
        RoleDefinition(
            MEDICO,
            GameRules.TOWN_WINNER,
            "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
            5
        ),
        RoleDefinition(
            ALCALDE,
            GameRules.TOWN_WINNER,
            "Puede revelar su identidad durante el debate. Desde ese momento su voto vale doble y decide entre los dos participantes mas votados si hay empate.",
            8
        ),
        RoleDefinition(
            ASESINO,
            GameRules.TRAITOR_WINNER,
            "Los asesinos eligen en conjunto una victima durante la noche. Si queda uno solo, decide por su cuenta.",
            5
        ),
        RoleDefinition(
            ESPIA,
            GameRules.TRAITOR_WINNER,
            "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
            10
        ),
        RoleDefinition(
            MERCENARIO,
            GameRules.TRAITOR_WINNER,
            "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
            7
        ),
        RoleDefinition(
            DESERTOR,
            "Neutral",
            "Elige un bando al comenzar. Puede reconsiderarlo una sola vez cuando quedan aproximadamente dos tercios de los jugadores iniciales y debe sobrevivir para ganar con su bando final.",
            9
        ),
        RoleDefinition(
            PAYADOR,
            GameRules.TOWN_WINNER,
            "Una vez por partida elige dos participantes para un Contrapunto. Solo ellos y el Payador pueden hablar; al terminar senala a uno, que recibe un voto adicional.",
            8,
            RoleMap.PAMPA,
            "Rol de Mapa"
        ),
        RoleDefinition(
            BUFON,
            "Neutral",
            "Quiere ser eliminado por votacion del pueblo. Si lo consiguen votar, gana su propia condicion especial.",
            8,
            RoleMap.MEDIEVAL,
            "Rol de Mapa"
        ),
        RoleDefinition(
            ORACULO,
            GameRules.TOWN_WINNER,
            "Una vez por partida trae a un jugador muerto al debate del dia siguiente. Puede hablar, pero no votar ni usar habilidades.",
            8,
            RoleMap.GREECE,
            "Rol de Mapa"
        )
    ).associateBy { it.key }

    private val baseRoleKeys = listOf(
        ALDEANO,
        POLICIA,
        MEDICO,
        ALCALDE,
        ASESINO,
        ESPIA,
        MERCENARIO,
        DESERTOR
    )

    fun definition(key: String): RoleDefinition {
        return definitions[key] ?: definitions.getValue(ALDEANO)
    }

    fun minimumPlayers(key: String): Int = definition(key).minimumPlayers

    fun isAvailableOnMap(key: String, map: RoleMap): Boolean {
        return definition(key).exclusiveMap?.let { it == map } ?: true
    }

    fun mapInfo(map: RoleMap): MapInfo {
        return when (map) {
            RoleMap.MEDIEVAL -> MapInfo(
                "Feudo de Hierro",
                "Epoca Medieval",
                "Un feudo que aparenta paz mientras una familia misteriosa compra voluntades y siembra muertes discretas. El alcalde se aferra al poder entre banquetes, paranoia y ordenes que apenas recuerda.",
                displayName(BUFON, map),
                "mapa_medieval"
            )
            RoleMap.GREECE -> MapInfo(
                "Antigua Grecia",
                "Siglo V a.C.",
                "Una polis pequena obsesionada con parecer grande. En la plaza, los discursos de honor esconden hambre, deudas y un culto popular que mata con mascaras doradas en nombre de Ares.",
                displayName(ORACULO, map),
                "mapa_grecia"
            )
            RoleMap.PAMPA -> MapInfo(
                "Pueblo del Interior - 1915",
                "Argentina, 1915",
                "Un pueblucho seco donde el comisario, los patrones y los favores politicos pesan mas que la ley. Una banda peligrosa dice pelear contra la corrupcion, aunque su justicia llega manchada de sangre.",
                displayName(PAYADOR, map),
                "mapa_pampa"
            )
        }
    }

    fun rolesForMap(map: RoleMap): List<Role> {
        return (baseRoleKeys + exclusiveRoleKey(map)).map { role(it, map) }
    }

    fun role(key: String, map: RoleMap): Role {
        val definition = definition(key)
        return Role(
            name = displayName(definition.key, map),
            mapName = mapInfo(map).name,
            team = definition.displayCategory,
            story = stories.getValue(map).getValue(definition.key),
            function = definition.function,
            imageResName = "rol_${imageKey(definition.key)}_${map.imageSuffix}"
        )
    }

    fun gameRole(key: String, map: RoleMap): GameRole {
        val definition = definition(key)
        return GameRole(
            key = definition.key,
            name = gameName(definition.key, map),
            team = definition.team,
            imageResName = "rol_${imageKey(definition.key)}_${map.imageSuffix}"
        )
    }

    private fun exclusiveRoleKey(map: RoleMap): String {
        return when (map) {
            RoleMap.MEDIEVAL -> BUFON
            RoleMap.GREECE -> ORACULO
            RoleMap.PAMPA -> PAYADOR
        }
    }

    private fun imageKey(key: String): String {
        return if (key == POLICIA) "detective" else key
    }

    private fun displayName(key: String, map: RoleMap): String {
        return when (key) {
            ALDEANO -> "Aldeana"
            POLICIA -> "Detective"
            MEDICO -> if (map == RoleMap.MEDIEVAL) "Medico" else "Medica"
            ALCALDE -> "Alcalde"
            ASESINO -> if (map == RoleMap.GREECE) "Asesina" else "Asesino"
            ESPIA -> "Espia"
            MERCENARIO -> "Mercenario"
            DESERTOR -> if (map == RoleMap.PAMPA) "Desertora" else "Desertor"
            PAYADOR -> "Payador"
            BUFON -> "Bufon"
            ORACULO -> "Oraculo"
            else -> "Aldeana"
        }
    }

    private fun gameName(key: String, map: RoleMap): String {
        return when {
            key == POLICIA && map == RoleMap.PAMPA -> "Comisario"
            key == MEDICO -> "Medico"
            key == ASESINO -> "Asesino"
            else -> displayName(key, map)
        }
    }

    private val stories = mapOf(
        RoleMap.MEDIEVAL to mapOf(
            ALDEANO to "Vive donde el barro llega antes que las noticias. No tiene poder, titulo ni espada, pero conoce cada puerta, cada deuda y cada silencio raro del pueblo. Cuando el feudo empieza a pudrirse, ella lo nota antes que nadie.",
            POLICIA to "Antes resolvia robos menores y mentiras de taberna. Ahora sigue rastros de sangre disfrazados de accidentes, sabiendo que descubrir la verdad no sirve de nada si no vive lo suficiente para contarla.",
            MEDICO to "Todos en el feudo le deben algo: una fiebre curada, una herida cerrada o una vida salvada a tiempo. No pregunta de que lado viene el paciente, pero recuerda cada corte, cada veneno y cada mentira.",
            ALCALDE to "Gobierna desde una silla demasiado grande y una mesa demasiado llena. Esta perdiendo la cordura entre rumores, copas servidas y ordenes que apenas recuerda, pero todavia conserva el poder de salvar o condenar al feudo.",
            ASESINO to "Una familia misteriosa lo compro con oro, tierras y promesas de un apellido respetado. No mata por fe ni por justicia: mata porque vio una escalera hacia el poder y decidio subirla con las manos manchadas.",
            ESPIA to "Aconseja al alcalde desde muy cerca, eligiendo que verdades llegan completas y cuales llegan torcidas. Su arma no es una daga, sino una voz tranquila que alimenta la paranoia del poder.",
            MERCENARIO to "No sirve a ningun linaje, solo al contrato correcto. Ha peleado por senores que olvidaron su nombre y ahora cobra para que ciertas conversaciones terminen antes de tiempo.",
            DESERTOR to "Una vez juro proteger el feudo, hasta que entendio a quien estaba protegiendo realmente. Desde entonces vive entre caminos y nombres falsos, cargando con la duda de si huyo por cobardia o por lucidez.",
            BUFON to "Sobrevive haciendo reir a quienes podrian mandarlo a morir. Mientras el feudo se hunde en acusaciones, el tropieza, exagera y dice verdades que todos prefieren tomar como broma. Si lo votan, al menos tendra publico completo."
        ),
        RoleMap.GREECE to mapOf(
            ALDEANO to "Vende aceite, pan o telas bajo estatuas demasiado grandes para una ciudad tan pequena. Escucha a los hombres hablar de gloria en la plaza mientras ella cuenta monedas para llegar al invierno. No cree en discursos: cree en sobrevivir al proximo decreto.",
            POLICIA to "No busca huellas en callejones, sino contradicciones en discursos publicos. Sabe que en la polis nadie miente en silencio: todos mienten frente a testigos, con palabras hermosas y las manos limpias.",
            MEDICO to "Aprendio a curar cuerpos entrenados para la guerra y estomagos vacios por culpa de la politica. En su mesa no importa si alguien invoca a Ares, a Atenea o a nadie; la sangre se seca igual en todos.",
            ALCALDE to "No gobierna desde un trono, sino desde la palabra. Cada decision debe sonar justa, fuerte y digna ante una polis que exige grandeza aunque apenas pueda sostenerse. Su miedo no es morir: es quedar como el lider que hizo pequena a su ciudad.",
            ASESINO to "Se puso la mascara dorada para dejar de ser una voz ignorada entre la multitud. El culto le prometio que Ares no escucha plegarias, escucha actos. Desde entonces mata convencida de que cada cuerpo caido obliga a la polis a despertar.",
            ESPIA to "No necesita ocultarse en sombras: se sienta en la plaza, aplaude discursos y repite frases en el oido correcto. En una ciudad gobernada por reputaciones, una palabra bien colocada puede arruinar mas que una daga.",
            MERCENARIO to "Vino de guerras ajenas, donde los dioses cambiaban de nombre pero los muertos pesaban igual. La polis lo mira como extranjero util: indigno para votar, perfecto para hacer lo que ningun ciudadano quiere admitir.",
            DESERTOR to "Abandono la defensa de la polis cuando entendio que sus murallas protegian mas orgullo que vidas. No traiciono una bandera; se nego a morir por discursos escritos por hombres que nunca pisan el campo de batalla.",
            ORACULO to "La respetan porque habla con lo que nadie puede controlar. Pero su don no obedece a la politica ni al deseo del pueblo: puede traer de vuelta una verdad salvadora o una voz que incendie la ciudad desde la tumba."
        ),
        RoleMap.PAMPA to mapOf(
            ALDEANO to "Vive en un pueblucho donde el polvo entra por las ventanas y las noticias llegan tarde, si llegan. No espera justicia de nadie: ni del comisario, ni del patron, ni del cura. Aprendio que sobrevivir tambien es saber cuando cerrar la boca.",
            POLICIA to "No tiene oficina limpia ni placa respetada. Investiga entre pulperias, amenazas y expedientes que desaparecen cuando incomodan a alguien con poder. En este pueblo, encontrar la verdad es facil; lo dificil es que alguien se anime a decirla.",
            MEDICO to "Antes curaba heridas de hombres buscados por la ley, sin preguntar demasiado. Logro alejarse de esa vida, pero todavia reconoce el sonido de ciertos pasos en la puerta. Ahora cura al pueblo, aunque sabe que el pasado siempre vuelve montado y con sed.",
            ALCALDE to "Gobierna poco y firma mucho. Entre comisarios corruptos, favores politicos y patrones que hablan mas fuerte que la ley, intenta parecer autoridad en un pueblo donde todos saben quien manda de verdad.",
            ASESINO to "No mata por gloria ni por fe: mata porque la banda lo ordena y porque el miedo funciona mejor que cualquier discurso. Se cree parte de una justicia nueva, pero cada cuerpo que deja atras lo acerca mas a los mismos corruptos que dice odiar.",
            ESPIA to "Escucha detras de mostradores, en patios de tierra y en mesas donde el vino afloja la lengua. Nadie le presta demasiada atencion, y por eso mismo sabe mas que todos. En un pueblo chico, el secreto siempre pasa por alguien.",
            MERCENARIO to "No pregunta si el trabajo es justo, solo cuanto pagan y quien queda parado al final. Ha servido a comisarios, bandidos y patrones con la misma cara cansada. Su moral dura lo mismo que una bolsa de monedas.",
            DESERTOR to "Pertenecio a la banda el tiempo suficiente para entender que el codigo tenia letra chica. Se fue antes de terminar convertida en leyenda o cadaver. Desde entonces cambia de rumbo cada vez que escucha cascos acercarse.",
            PAYADOR to "Es el hazmerreir del pueblo, pero todos lo quieren un poco. Canta mal cuando esta sobrio y peor cuando esta inspirado, aunque sus coplas siempre terminan juntando gente."
        )
    )
}
