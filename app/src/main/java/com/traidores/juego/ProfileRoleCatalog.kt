package com.traidores.juego

object ProfileRoleCatalog {

    data class Entry(
        val key: String,
        val role: Role
    )

    val entries = listOf(
        Entry(
            "aldeana",
            Role(
                "Aldeana",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Vive en un pueblucho donde el polvo entra por las ventanas y las noticias llegan tarde, si llegan. No espera justicia de nadie: ni del comisario, ni del patron, ni del cura. Aprendio que sobrevivir tambien es saber cuando cerrar la boca.",
                "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores.",
                "rol_aldeano_gaucho"
            )
        ),
        Entry(
            "detective",
            Role(
                "Detective",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "No tiene oficina limpia ni placa respetada. Investiga entre pulperias, amenazas y expedientes que desaparecen cuando incomodan a alguien con poder. En este pueblo, encontrar la verdad es facil; lo dificil es que alguien se anime a decirla.",
                "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso.",
                "rol_detective_gaucho"
            )
        ),
        Entry(
            "medica",
            Role(
                "Medica",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Antes curaba heridas de hombres buscados por la ley, sin preguntar demasiado. Logro alejarse de esa vida, pero todavia reconoce el sonido de ciertos pasos en la puerta. Ahora cura al pueblo, aunque sabe que el pasado siempre vuelve montado y con sed.",
                "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminacion se cancela.",
                "rol_medico_gaucho"
            )
        ),
        Entry(
            "alcalde",
            Role(
                "Alcalde",
                "Pueblo del Interior - 1915",
                "Pueblo",
                "Gobierna poco y firma mucho. Entre comisarios corruptos, favores politicos y patrones que hablan mas fuerte que la ley, intenta parecer autoridad en un pueblo donde todos saben quien manda de verdad.",
                "Puede revelar su identidad durante el debate. Desde ese momento su voto vale doble y decide entre los dos participantes mas votados si hay empate.",
                "rol_alcalde_gaucho"
            )
        ),
        Entry(
            "asesino",
            Role(
                "Asesino",
                "Pueblo del Interior - 1915",
                "Asesino",
                "No mata por gloria ni por fe: mata porque la banda lo ordena y porque el miedo funciona mejor que cualquier discurso. Se cree parte de una justicia nueva, pero cada cuerpo que deja atras lo acerca mas a los mismos corruptos que dice odiar.",
                "Los asesinos eligen en conjunto una victima durante la noche. Si queda uno solo, decide por su cuenta.",
                "rol_asesino_gaucho"
            )
        ),
        Entry(
            "espia",
            Role(
                "Espia",
                "Pueblo del Interior - 1915",
                "Asesino",
                "Escucha detras de mostradores, en patios de tierra y en mesas donde el vino afloja la lengua. Nadie le presta demasiada atencion, y por eso mismo sabe mas que todos. En un pueblo chico, el secreto siempre pasa por alguien.",
                "Forma parte del bando traidor, pero ante la investigacion del Detective aparece como inocente.",
                "rol_espia_gaucho"
            )
        ),
        Entry(
            "mercenario",
            Role(
                "Mercenario",
                "Pueblo del Interior - 1915",
                "Asesino",
                "No pregunta si el trabajo es justo, solo cuanto pagan y quien queda parado al final. Ha servido a comisarios, bandidos y patrones con la misma cara cansada. Su moral dura lo mismo que una bolsa de monedas.",
                "Forma parte del bando traidor. Puede impedir que una victima hable o vote durante el dia siguiente.",
                "rol_mercenario_gaucho"
            )
        ),
        Entry(
            "desertora",
            Role(
                "Desertora",
                "Pueblo del Interior - 1915",
                "Neutral",
                "Pertenecio a la banda el tiempo suficiente para entender que el codigo tenia letra chica. Se fue antes de terminar convertida en leyenda o cadaver. Desde entonces cambia de rumbo cada vez que escucha cascos acercarse.",
                "Elige un bando al comenzar. Puede reconsiderarlo una sola vez y debe sobrevivir para ganar con su bando final.",
                "rol_desertor_gaucho"
            )
        ),
        Entry(
            "payador",
            Role(
                "Payador",
                "Pueblo del Interior - 1915",
                "Rol de Mapa",
                "Es el hazmerreir del pueblo, pero todos lo quieren un poco. Canta mal cuando esta sobrio y peor cuando esta inspirado, aunque sus coplas siempre terminan juntando gente.",
                "Una vez por partida elige dos participantes para un Contrapunto. Solo ellos y el Payador pueden hablar; al terminar senala a uno, que recibe un voto adicional.",
                "rol_payador_gaucho"
            )
        ),
        Entry(
            "bufon",
            Role(
                "Bufon",
                "Feudo de Hierro",
                "Rol de Mapa",
                "Sobrevive haciendo reir a quienes podrian mandarlo a morir. Mientras el feudo se hunde en acusaciones, dice verdades que todos prefieren tomar como broma.",
                "Quiere ser eliminado por votacion del pueblo. Si lo consiguen votar, gana su propia condicion especial.",
                "rol_bufon_medieval"
            )
        ),
        Entry(
            "oraculo",
            Role(
                "Oraculo",
                "Antigua Grecia",
                "Rol de Mapa",
                "La respetan porque habla con lo que nadie puede controlar. Su don puede traer de vuelta una verdad salvadora o una voz que incendie la ciudad desde la tumba.",
                "Resucita a un jugador muerto para el debate del dia siguiente, sin voto ni accion nocturna.",
                "rol_oraculo_griego"
            )
        )
    )

    val pampaDetective: Role = entries.first { it.key == "detective" }.role

    fun find(key: String): Entry {
        return entries.firstOrNull { it.key == key } ?: entries.first()
    }
}
