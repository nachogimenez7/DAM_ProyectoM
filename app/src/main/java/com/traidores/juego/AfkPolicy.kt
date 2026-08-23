package com.traidores.juego

object AfkPolicy {
    const val CONSECUTIVE_MISSES_BEFORE_EXPULSION = 2

    fun warning(opportunity: AfkOpportunity, expulsionEnabled: Boolean): String {
        val action = if (opportunity == AfkOpportunity.NIGHT) "acción" else "voto"
        if (!expulsionEnabled) return "Perdiste tu $action de esta ronda."

        val nextOpportunity = if (opportunity == AfkOpportunity.NIGHT) {
            "próxima noche"
        } else {
            "próxima votación"
        }
        return "Perdiste tu $action. Si vuelves a ausentarte en tu $nextOpportunity, " +
            "serás expulsado por AFK."
    }

    fun selfExpelledMessage(): String =
        "Fuiste expulsado por permanecer inactivo durante dos oportunidades consecutivas."
}
