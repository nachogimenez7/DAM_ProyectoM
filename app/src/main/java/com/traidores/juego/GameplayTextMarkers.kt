package com.traidores.juego

import java.text.Normalizer

object GameplayTextMarkers {
    const val DAWN = "Amanecer"
    const val DAY = "Día"
    const val NIGHT = "Noche"
    const val DEATH = "murió"
    const val NO_DEATH = "no murió nadie"
    const val SILENCED = "Silenciados"
    const val CANNOT_SPEAK_OR_VOTE = "no puede hablar ni votar hoy"
    const val EXPELLED = "fue expulsado"
    const val EXPELLED_BY = "expulsar a"
    const val VOTING = "votación"
    const val ORACLE_RETURN = "regresa para hablar"

    fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
    }

    fun contains(text: String, marker: String): Boolean {
        return normalize(text).contains(normalize(marker))
    }
}
