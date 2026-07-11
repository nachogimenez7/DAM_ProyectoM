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
            "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminación se cancela.",
            5
        ),
        RoleDefinition(
            ALCALDE,
            GameRules.TOWN_WINNER,
            "Puede revelar su identidad durante el debate. Desde ese momento su voto vale doble y decide entre los dos participantes más votados si hay empate.",
            8
        ),
        RoleDefinition(
            ASESINO,
            GameRules.TRAITOR_WINNER,
            "Los asesinos eligen en conjunto una víctima durante la noche. Si queda uno solo, decide por su cuenta.",
            5
        ),
        RoleDefinition(
            ESPIA,
            GameRules.TRAITOR_WINNER,
            "Elige la víctima cada noche junto a los asesinos, pero ante la investigación aparece como inocente.",
            10
        ),
        RoleDefinition(
            MERCENARIO,
            GameRules.TRAITOR_WINNER,
            "Forma parte del bando traidor. Puede impedir que una víctima hable o vote durante el día siguiente.",
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
            "Una vez por partida elige dos participantes para un Contrapunto. Solo esos dos pueden hablar (el Payador escucha); al terminar señala a uno, que recibe un voto adicional.",
            8,
            RoleMap.PAMPA,
            "Rol de Mapa"
        ),
        RoleDefinition(
            BUFON,
            "Neutral",
            "Molesta, interrumpe y busca hacerse odiar para que el pueblo lo expulse durante la votación. Esa es su única condición de victoria: no gana si muere de noche ni por otra causa.",
            8,
            RoleMap.MEDIEVAL,
            "Rol de Mapa"
        ),
        RoleDefinition(
            ORACULO,
            GameRules.TOWN_WINNER,
            "Una vez por partida puede invocar a cualquier jugador muerto para el debate del día siguiente. Su rol permanece oculto: puede hablar, pero no votar ni usar habilidades.",
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

    private val guideRoleKeys = listOf(
        ALDEANO,
        POLICIA,
        MEDICO,
        ALCALDE,
        ASESINO,
        ESPIA,
        MERCENARIO,
        DESERTOR,
        PAYADOR,
        BUFON,
        ORACULO
    )

    private val adviceByKey = mapOf(
        ALDEANO to
            "No tienes acción nocturna. Usa el debate: pregunta, compara versiones y recuerda quién acusó, defendió o cambió su historia.",
        POLICIA to
            "Cada noche investiga a un jugador. Guarda tus pistas hasta que puedas usarlas sin exponerte demasiado pronto.",
        MEDICO to
            "Cada noche protege a un jugador. Puedes protegerte si necesitas sobrevivir, o cuidar a quien tenga información clave.",
        ALCALDE to
            "Puedes revelarte durante el debate. Desde entonces tu voto vale doble y puedes decidir empates.",
        ASESINO to
            "Cada noche elige una víctima con los Traidores. De día, desvía sospechas sin defender siempre a tus aliados.",
        ESPIA to
            "El investigador te verá como inocente. Participa en la elección de víctima y usa esa apariencia para proteger a los Traidores.",
        MERCENARIO to
            "Cada noche silencia a un jugador. Elige a quien pueda convencer al pueblo o compartir información importante.",
        DESERTOR to
            "Elige un bando y ayuda a que gane. Sobrevivir importa: no te comprometas sin mirar quién tiene ventaja.",
        PAYADOR to
            "No actúas de noche. De día puedes iniciar Contrapunto entre dos jugadores cuyas versiones se contradigan.",
        BUFON to
            "No actúas de noche. Tu objetivo es que el pueblo te expulse durante la votación, no morir de noche.",
        ORACULO to
            "Actúas de noche. Una vez por partida puedes invocar a un muerto para que hable en el próximo debate."
    )

    fun definition(key: String): RoleDefinition {
        return definitions[key] ?: definitions.getValue(ALDEANO)
    }

    fun guideKeys(): List<String> = guideRoleKeys

    fun advice(key: String): String =
        adviceByKey[key] ?: adviceByKey.getValue(ALDEANO)

    fun guideName(key: String): String {
        return when (key) {
            ALDEANO -> "Aldeano"
            POLICIA -> "Detective / Comisario"
            MEDICO -> "Médico"
            ALCALDE -> "Alcalde"
            ASESINO -> "Asesino"
            ESPIA -> "Espía"
            MERCENARIO -> "Mercenario"
            DESERTOR -> "Desertor"
            PAYADOR -> "Payador"
            BUFON -> "Bufón"
            ORACULO -> "Oráculo"
            else -> "Aldeano"
        }
    }

    fun guideAvailability(key: String): String {
        val definition = definition(key)
        val map = when (definition.exclusiveMap) {
            RoleMap.PAMPA -> "Solo mapa Pampa"
            RoleMap.MEDIEVAL -> "Solo mapa Medieval"
            RoleMap.GREECE -> "Solo mapa Grecia"
            null -> "Todos los mapas"
        }
        return "$map - Desde ${definition.minimumPlayers} jugadores"
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
                "Una polis pequeña obsesionada con parecer grande. En la plaza, los discursos de honor esconden hambre, deudas y un culto popular que mata con máscaras doradas en nombre de Ares.",
                displayName(ORACULO, map),
                "mapa_grecia"
            )
            RoleMap.PAMPA -> MapInfo(
                "Pueblo del Interior - 1915",
                "Argentina, 1915",
                "Un pueblucho seco donde el comisario, los patrones y los favores políticos pesan más que la ley. Una banda peligrosa dice pelear contra la corrupción, aunque su justicia llega manchada de sangre.",
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
        return when (map) {
            RoleMap.MEDIEVAL -> when (key) {
                ALDEANO -> "Aldeana"
                POLICIA -> "Detective"
                MEDICO -> "Médico"
                ALCALDE -> "Alcalde"
                ASESINO -> "Asesino"
                ESPIA -> "Espía"
                MERCENARIO -> "Mercenario"
                DESERTOR -> "Desertora"
                BUFON -> "Bufón"
                else -> "Aldeana"
            }
            RoleMap.GREECE -> when (key) {
                ALDEANO -> "Aldeano"
                POLICIA -> "Detective"
                MEDICO -> "Médico"
                ALCALDE -> "Alcalde"
                ASESINO -> "Asesina"
                ESPIA -> "Espía"
                MERCENARIO -> "Mercenario"
                DESERTOR -> "Desertor"
                ORACULO -> "Oráculo"
                else -> "Aldeano"
            }
            RoleMap.PAMPA -> when (key) {
                ALDEANO -> "Aldeano"
                POLICIA -> "Comisario"
                MEDICO -> "Médica"
                ALCALDE -> "Alcaldesa"
                ASESINO -> "Asesino"
                ESPIA -> "Espía"
                MERCENARIO -> "Mercenario"
                DESERTOR -> "Desertor"
                PAYADOR -> "Payador"
                else -> "Aldeano"
            }
        }
    }

    private fun gameName(key: String, map: RoleMap): String {
        return when {
            key == POLICIA && map == RoleMap.PAMPA -> "Comisario"
            else -> displayName(key, map)
        }
    }

    private val stories = mapOf(
        RoleMap.MEDIEVAL to mapOf(
            ALDEANO to "Vive donde el barro llega antes que las noticias. No tiene poder, título ni espada, pero conoce cada puerta, cada deuda y cada silencio raro del pueblo. Cuando el feudo empieza a pudrirse, ella lo nota antes que nadie.",
            POLICIA to "Antes resolvía robos menores y mentiras de taberna. Ahora sigue rastros de sangre disfrazados de accidentes, sabiendo que descubrir la verdad no sirve de nada si no vive lo suficiente para contarla.",
            MEDICO to "Todos en el feudo le deben algo: una fiebre curada, una herida cerrada o una vida salvada a tiempo. No pregunta de qué lado viene el paciente, pero recuerda cada corte, cada veneno y cada mentira.",
            ALCALDE to "Gobierna desde una silla demasiado grande y un salón demasiado lleno. Está perdiendo la cordura entre rumores, copas servidas y órdenes que apenas recuerda, pero todavía conserva el poder de salvar o condenar al feudo.",
            ASESINO to "Una familia misteriosa lo compró con oro, tierras y promesas de un apellido respetado. No mata por fe ni por justicia: mata porque vio una escalera hacia el poder y decidió subirla con las manos manchadas.",
            ESPIA to "Aconseja al alcalde desde muy cerca, eligiendo qué verdades llegan completas y cuáles llegan torcidas. Su arma no es una daga, sino una voz tranquila que alimenta la paranoia del poder.",
            MERCENARIO to "No sirve a ningún linaje, solo al contrato correcto. Ha peleado por señores que olvidaron su nombre y ahora cobra para que ciertas conversaciones terminen antes de tiempo.",
            DESERTOR to "Una vez juró proteger el feudo, hasta que entendió a quién estaba protegiendo realmente. Desde entonces vive entre caminos y nombres falsos, cargando con la duda de si huyó por cobardía o por lucidez.",
            BUFON to "Molesta, interrumpe y se esfuerza por caer mal. Mientras el feudo se hunde en acusaciones, exagera, provoca y dice lo necesario para que todos quieran expulsarlo. Solo gana si el pueblo lo elimina durante la votación."
        ),
        RoleMap.GREECE to mapOf(
            ALDEANO to "Vende aceite, pan o telas bajo estatuas demasiado grandes para una ciudad tan pequeña. Escucha a los hombres hablar de gloria en la plaza mientras ella cuenta monedas para llegar al invierno. No cree en discursos: cree en sobrevivir al próximo decreto.",
            POLICIA to "No busca huellas en callejones, sino contradicciones en discursos públicos. Sabe que en la polis nadie miente en silencio: todos mienten frente a testigos, con palabras hermosas y las manos limpias.",
            MEDICO to "Aprendió a curar cuerpos entrenados para la guerra y estómagos vacíos por culpa de la política. Ante sus manos no importa si alguien invoca a Ares, a Atenea o a nadie; la sangre se seca igual en todos.",
            ALCALDE to "No gobierna desde un trono, sino desde la palabra. Cada decisión debe sonar justa, fuerte y digna ante una polis que exige grandeza aunque apenas pueda sostenerse. Su miedo no es morir: es quedar como el líder que hizo pequeña a su ciudad.",
            ASESINO to "Se puso la máscara dorada para dejar de ser una voz ignorada entre la multitud. El culto le prometió que Ares no escucha plegarias, escucha actos. Desde entonces mata convencida de que cada cuerpo caído obliga a la polis a despertar.",
            ESPIA to "No necesita ocultarse en sombras: se sienta en la plaza, aplaude discursos y repite frases en el oído correcto. En una ciudad gobernada por reputaciones, una palabra bien colocada puede arruinar más que una daga.",
            MERCENARIO to "Vino de guerras ajenas, donde los dioses cambiaban de nombre pero los muertos pesaban igual. La polis lo mira como extranjero útil: indigno para votar, perfecto para hacer lo que ningún ciudadano quiere admitir.",
            DESERTOR to "Abandonó la defensa de la polis cuando entendió que sus murallas protegían más orgullo que vidas. No traicionó una bandera; se negó a morir por discursos escritos por hombres que nunca pisan el campo de batalla.",
            ORACULO to "La respetan porque habla con lo que nadie puede controlar. Pero su don no obedece a la política ni al deseo del pueblo: puede traer de vuelta una verdad salvadora o una voz que incendie la ciudad desde la tumba."
        ),
        RoleMap.PAMPA to mapOf(
            ALDEANO to "Vive en un pueblucho donde el polvo entra por las ventanas y las noticias llegan tarde, si llegan. No espera justicia de nadie: ni del comisario, ni del patrón, ni del cura. Aprendió que sobrevivir también es saber cuándo cerrar la boca.",
            POLICIA to "No tiene oficina limpia ni placa respetada. Investiga entre pulperías, amenazas y expedientes que desaparecen cuando incomodan a alguien con poder. En este pueblo, encontrar la verdad es fácil; lo difícil es que alguien se anime a decirla.",
            MEDICO to "Antes curaba heridas de hombres buscados por la ley, sin preguntar demasiado. Logró alejarse de esa vida, pero todavía reconoce el sonido de ciertos pasos en la puerta. Ahora cura al pueblo, aunque sabe que el pasado siempre vuelve montado y con sed.",
            ALCALDE to "Gobierna poco y firma mucho. Entre comisarios corruptos, favores políticos y patrones que hablan más fuerte que la ley, intenta parecer autoridad en un pueblo donde todos saben quién manda de verdad.",
            ASESINO to "No mata por gloria ni por fe: mata porque la banda lo ordena y porque el miedo funciona mejor que cualquier discurso. Se cree parte de una justicia nueva, pero cada cuerpo que deja atrás lo acerca más a los mismos corruptos que dice odiar.",
            ESPIA to "Escucha detrás de mostradores, en patios de tierra y en mesas donde el vino afloja la lengua. Nadie le presta demasiada atención, y por eso mismo sabe más que todos. En un pueblo chico, el secreto siempre pasa por alguien.",
            MERCENARIO to "No pregunta si el trabajo es justo, solo cuánto pagan y quién queda parado al final. Ha servido a comisarios, bandidos y patrones con la misma cara cansada. Su moral dura lo mismo que una bolsa de monedas.",
            DESERTOR to "Perteneció a la banda el tiempo suficiente para entender que el código tenía letra chica. Se fue antes de terminar convertida en leyenda o cadáver. Desde entonces cambia de rumbo cada vez que escucha cascos acercarse.",
            PAYADOR to "Es el hazmerreír del pueblo, pero todos lo quieren un poco. Canta mal cuando está sobrio y peor cuando está inspirado, aunque sus coplas siempre terminan juntando gente."
        )
    )
}
