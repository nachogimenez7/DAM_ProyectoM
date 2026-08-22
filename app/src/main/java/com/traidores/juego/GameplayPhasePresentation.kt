package com.traidores.juego

data class GameplayPhaseText(
    val title: String,
    val subtitle: String,
    val actionLabel: String
)

/**
 * Copy y decisiones de presentación sin dependencias de Android ni Firebase.
 * Esta frontera puede moverse a un futuro modulo KMP `commonMain`.
 */
object GameplayPhasePresentation {
    fun phaseText(
        phase: GamePhase,
        round: Int,
        winnerPresent: Boolean,
        nightSubtitle: String,
        humanRoleTurn: Boolean
    ): GameplayPhaseText = when (phase) {
        GamePhase.REPARTO -> GameplayPhaseText(
            "TU ROL",
            "Revisa tu carta. La primera noche empieza enseguida.",
            "NOCHE"
        )
        GamePhase.NOCHE_ASESINO -> GameplayPhaseText(
            "NOCHE $round",
            nightSubtitle,
            if (humanRoleTurn) "MATAR" else "ESPERAR"
        )
        GamePhase.NOCHE_MERCENARIO -> GameplayPhaseText(
            "NOCHE $round",
            nightSubtitle,
            if (humanRoleTurn) "SILENCIAR" else "ESPERAR"
        )
        GamePhase.NOCHE_POLICIA -> GameplayPhaseText(
            "NOCHE $round",
            nightSubtitle,
            if (humanRoleTurn) "INVESTIGAR" else "ESPERAR"
        )
        GamePhase.NOCHE_MEDICO -> GameplayPhaseText(
            "NOCHE $round",
            nightSubtitle,
            if (humanRoleTurn) "PROTEGER" else "ESPERAR"
        )
        GamePhase.NOCHE_ORACULO -> GameplayPhaseText(
            "NOCHE $round",
            nightSubtitle,
            if (humanRoleTurn) "GUARDAR PODER" else "ESPERAR"
        )
        GamePhase.AMANECER -> GameplayPhaseText(
            "AMANECER",
            "El pueblo despierta y escucha lo ocurrido.",
            "AMANECER"
        )
        GamePhase.DIA_DEBATE -> GameplayPhaseText(
            "DÍA $round",
            "El pueblo debate antes de votar.",
            "VOTAR"
        )
        GamePhase.CONTRAPUNTO -> GameplayPhaseText(
            "CONTRAPUNTO",
            "Selecciona un participante y confirma el contrapunto.",
            "SEÑALAR"
        )
        GamePhase.VOTACION -> GameplayPhaseText(
            "VOTACIÓN",
            "Tocá una carta para votar. Tocá otra para cambiar antes del cierre.",
            "VOTO"
        )
        GamePhase.RECUENTO_VOTOS -> GameplayPhaseText(
            "RECUENTO",
            "El pueblo cuenta los votos.",
            "CONTINUAR"
        )
        GamePhase.DESEMPATE_VOTACION -> GameplayPhaseText(
            "DESEMPATE",
            "Tocá una carta empatada para votar; podés cambiar antes del cierre.",
            "VOTO"
        )
        GamePhase.ALCALDE_DESEMPATE -> GameplayPhaseText(
            "DESEMPATE",
            "El Alcalde decide entre los jugadores empatados.",
            "DECIDIR"
        )
        GamePhase.RESULTADO -> GameplayPhaseText(
            "RESULTADO",
            "El pueblo conoce el resultado.",
            if (winnerPresent) "FINAL" else "CONTINUAR"
        )
    }

    fun roleFunction(roleKey: String): String = when (roleKey) {
        RoleCatalog.ASESINO ->
            "Cada noche eliges una víctima para eliminar. Ganas cuando los Traidores logran controlar el pueblo."
        RoleCatalog.MERCENARIO ->
            "Cada noche silencias a un jugador. Esa persona no podrá hablar ni votar durante el día siguiente."
        RoleCatalog.POLICIA ->
            "Cada noche investigas a un jugador y recibes en privado una pista sobre su bando."
        RoleCatalog.MEDICO ->
            "Cada noche proteges a un jugador. Si los Traidores lo atacan, evitas su eliminación."
        RoleCatalog.ALCALDE ->
            "Puedes revelar tu identidad durante el debate. Desde entonces tu voto vale doble y decides ciertos empates."
        RoleCatalog.PAYADOR ->
            "Una vez por partida inicias un Contrapunto entre dos jugadores y agregas un voto al más sospechoso."
        RoleCatalog.DESERTOR ->
            "Eliges un bando al comenzar y ganas con ese equipo si sobrevives. Más adelante puedes cambiarlo una sola vez."
        RoleCatalog.ESPIA ->
            "Eliges la víctima cada noche junto a los Traidores, pero cuando te investiga el investigador apareces como inocente."
        RoleCatalog.BUFON ->
            "Tu objetivo es molestar, interrumpir y hacerte odiar para que el pueblo te expulse durante la votación. Esa es tu única condición de victoria."
        RoleCatalog.ORACULO ->
            "Durante la noche, una vez por partida, puedes invocar a un jugador muerto para el debate del día siguiente. Su rol permanece oculto: puede hablar, pero no votar ni usar habilidades."
        else ->
            "No tienes una habilidad especial. Debes debatir, detectar contradicciones y votar para eliminar a los Traidores."
    }

    fun passiveNightMessage(
        mapKey: String,
        round: Int,
        phaseIndex: Int,
        phase: GamePhase
    ): String {
        val messages = passiveNightMessages(mapKey)
        val index = (round * 31 + phaseIndex * 7 + phase.ordinal)
            .let { kotlin.math.abs(it) % messages.size }
        return messages[index]
    }

    internal fun passiveNightMessages(mapKey: String): List<String> = when (mapKey) {
        "grecia" -> listOf(
            "La polis guarda silencio. En el agora, hasta las estatuas parecen escuchar.",
            "El aceite de las lamparas tiembla. Alguien cruza el patio sin mirar al cielo.",
            "Los dioses callan. Una sandalia roza la piedra y nadie pregunta de quien fue.",
            "La noche cae sobre las columnas. Sobrevives contando sombras entre los olivos.",
            "Un rumor sube desde el puerto. Nadie lo confirma, pero todos lo sienten."
        )
        "medieval" -> listOf(
            "El castillo duerme. Una antorcha chispea donde nadie deberia estar despierto.",
            "Se apagan voces en la taberna. Una puerta cruje y el patio queda inmovil.",
            "La guardia mira hacia otro lado. En las murallas, una sombra cambia de rumbo.",
            "La noche hace su trabajo. Sobrevives oyendo pasos detras de la piedra.",
            "Un juglar calla a mitad de verso. Nadie rie, nadie pregunta."
        )
        else -> listOf(
            "Cerras los ojos. Alguien pisa una rama y todos fingen no haber escuchado.",
            "El pueblo duerme. Una sombra parece saber demasiado, pero no declara.",
            "Se escuchan susurros, pasos y una puerta que nadie va a admitir haber abierto.",
            "La noche hace su trabajo. Sobrevives mirando el techo.",
            "Alguien se mueve en secreto. El mate queda frio y las sospechas calientes."
        )
    }
}
