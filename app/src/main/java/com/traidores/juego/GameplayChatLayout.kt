package com.traidores.juego

/**
 * El chat ocupa la columna central; las cartas crecen por los laterales. Por eso la cantidad
 * de jugadores no debe recortar su altura: el controlador ya limita el panel entre la
 * cabecera superior y la ficha inferior según el espacio real de la pantalla.
 */
internal object GameplayChatLayout {
    private const val AVAILABLE_COLUMN_MAX_HEIGHT_DP = 680

    fun ambientMaxHeightDp(@Suppress("UNUSED_PARAMETER") playerCount: Int): Int =
        AVAILABLE_COLUMN_MAX_HEIGHT_DP

    fun expandedMaxHeightDp(
        @Suppress("UNUSED_PARAMETER") playerCount: Int,
        @Suppress("UNUSED_PARAMETER") keyboardVisible: Boolean
    ): Int = AVAILABLE_COLUMN_MAX_HEIGHT_DP
}
