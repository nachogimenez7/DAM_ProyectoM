package com.traidores.juego

internal enum class RoleCompositionBalance(
    val label: String,
    val explanation: String
) {
    BALANCED(
        "EQUILIBRADA",
        "La presión de los bandos es razonable para esta cantidad de jugadores."
    ),
    TOWN_FAVORED(
        "PUEBLO FAVORECIDO",
        "Hay pocos Traidores para el tamaño de la mesa."
    ),
    TRAITORS_FAVORED(
        "TRAIDORES FAVORECIDOS",
        "La cantidad de Traidores puede cerrar la partida muy rápido."
    ),
    RISKY(
        "COMPOSICIÓN MUY ARRIESGADA",
        "Hay muchos roles especiales o neutrales; la partida será difícil de predecir."
    );

    companion object {
        fun evaluate(playerCount: Int, counts: Map<String, Int>): RoleCompositionBalance {
            val total = playerCount.coerceAtLeast(1)
            val assassins = counts[RoleCatalog.ASESINO] ?: 0
            val traitors = assassins +
                (counts[RoleCatalog.ESPIA] ?: 0) +
                (counts[RoleCatalog.MERCENARIO] ?: 0)
            val neutrals = (counts[RoleCatalog.DESERTOR] ?: 0) +
                (counts[RoleCatalog.BUFON] ?: 0)
            val specialRoles = counts
                .filterKeys { it != RoleCatalog.ALDEANO }
                .values
                .sum()
            return when {
                traitors >= (total + 2) / 3 || assassins >= 3 -> TRAITORS_FAVORED
                total >= 8 && traitors <= 1 -> TOWN_FAVORED
                neutrals >= 2 || specialRoles >= total - 1 -> RISKY
                else -> BALANCED
            }
        }
    }
}
