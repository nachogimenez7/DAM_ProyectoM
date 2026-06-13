package com.traidores.juego

import androidx.annotation.DrawableRes

data class ProfileBannerOption(
    val key: String,
    val label: String,
    @DrawableRes val drawableRes: Int
)

data class ProfileAchievement(
    val name: String,
    val shortName: String,
    val description: String,
    val obtainedDate: String
)

object ProfileCustomizationCatalog {

    val banners = listOf(
        ProfileBannerOption("pampa", "Pampa", R.drawable.profile_banner_pampa),
        ProfileBannerOption("grecia", "Grecia", R.drawable.profile_banner_grecia),
        ProfileBannerOption("medieval", "Medieval", R.drawable.profile_banner_medieval)
    )

    val achievements = listOf(
        ProfileAchievement(
            name = "Primer engano",
            shortName = "Engano",
            description = "Ganaste como Traidor logrando que el pueblo expulsara a un jugador inocente.",
            obtainedDate = "12/06/2026"
        ),
        ProfileAchievement(
            name = "Sobreviviente",
            shortName = "Sobrevivir",
            description = "Llegaste con vida al final de una partida de al menos ocho jugadores.",
            obtainedDate = "08/06/2026"
        ),
        ProfileAchievement(
            name = "Ojo experto",
            shortName = "Ojo experto",
            description = "Acertaste una investigacion decisiva mientras jugabas como Detective.",
            obtainedDate = "03/06/2026"
        )
    )

    fun achievement(name: String): ProfileAchievement? {
        return achievements.firstOrNull { it.name == name }
    }

    fun banner(key: String): ProfileBannerOption {
        return banners.firstOrNull { it.key == normalizeBannerKey(key) } ?: banners.first()
    }

    fun normalizeBannerKey(key: String): String {
        return when (key) {
            "dorado" -> "pampa"
            "bordo" -> "grecia"
            "noche" -> "medieval"
            else -> key
        }
    }
}
