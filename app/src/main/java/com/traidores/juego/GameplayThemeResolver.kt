package com.traidores.juego

object GameplayThemeResolver {

    fun themeFromIntentOrSession(requestedTheme: String?, mapKey: String): String {
        return when (requestedTheme) {
            "gaucho", "medieval", "griego" -> requestedTheme
            else -> GameplayTableUi.themeForMapKey(mapKey)
        }
    }

    fun backgroundDrawableFor(theme: String, isNight: Boolean, isVertical: Boolean = true): Int {
        if (isVertical) {
            return when (theme) {
                "medieval" -> if (isNight) {
                    R.drawable.mapa_medieval_vertical_noche
                } else {
                    R.drawable.mapa_medieval_vertical_dia
                }
                "griego" -> if (isNight) {
                    R.drawable.mapa_grecia_vertical_noche
                } else {
                    R.drawable.mapa_grecia_vertical_dia
                }
                else -> if (isNight) {
                    R.drawable.mapa_pampa_vertical_noche
                } else {
                    R.drawable.mapa_pampa_vertical_dia
                }
            }
        }
        return when (theme) {
            "medieval" -> if (isNight) R.drawable.fondo_medieval_noche else R.drawable.fondo_medieval_dia
            "griego" -> if (isNight) R.drawable.fondo_griego_noche else R.drawable.fondo_griego_dia
            else -> if (isNight) R.drawable.fondo_gaucho_noche else R.drawable.fondo_gaucho_dia
        }
    }

    fun logDrawableFor(theme: String): Int {
        return when (theme) {
            "medieval" -> R.drawable.log_medieval
            "griego" -> R.drawable.log_griego
            else -> R.drawable.log_gaucho
        }
    }
}
