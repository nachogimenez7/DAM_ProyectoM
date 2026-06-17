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
    val obtainedDate: String,
    val rarity: AchievementRarity
)

enum class AchievementRarity(
    val label: String,
    val borderColorHex: String,
    @DrawableRes val medalRes: Int
) {
    BRONZE("Bronce", "#B87333", R.drawable.ic_achievement_medal_bronze),
    SILVER("Plata", "#C9D0D8", R.drawable.ic_achievement_medal_silver),
    GOLD("Oro", "#E8B84B", R.drawable.ic_achievement_medal)
}

object ProfileCustomizationCatalog {

    val banners = listOf(
        ProfileBannerOption("pampa", "Pampa", R.drawable.profile_banner_pampa),
        ProfileBannerOption("grecia", "Grecia", R.drawable.profile_banner_grecia),
        ProfileBannerOption("medieval", "Medieval", R.drawable.profile_banner_medieval)
    )

    val achievements = listOf(
        ProfileAchievement(
            name = "Te agradezco infinitamente",
            shortName = "Gracias",
            description = "Te registraste en Traidores y dejaste tu nombre grabado en el pueblo.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.BRONZE
        ),
        ProfileAchievement(
            name = "Ser primero no es lo importante, es lo unico",
            shortName = "Primero",
            description = "Mataste a 25 jugadores siendo Asesino. No era una carrera, pero igual llegaste primero.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.BRONZE
        ),
        ProfileAchievement(
            name = "Y me gusta el rol, el maldito rol!",
            shortName = "Maldito rol",
            description = "Ganaste 5 partidas como Bufon. Te votaron, te festejaron y encima te salio bien.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.BRONZE
        ),
        ProfileAchievement(
            name = "Hoy dormis afuera",
            shortName = "Afuera",
            description = "Expulsaste a todos los Asesinos de forma seguida durante el dia.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.BRONZE
        ),
        ProfileAchievement(
            name = "Lo que no te mata, te infecta",
            shortName = "Infecta",
            description = "Ganaste 10 partidas como Desertor. Cambiaste de bando, pero nunca de objetivo.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.BRONZE
        ),
        ProfileAchievement(
            name = "Ya nadie va a escuchar tu voto",
            shortName = "Sin voto",
            description = "Silenciaste a un mismo jugador 3 veces en una misma partida como Mercenario.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.SILVER
        ),
        ProfileAchievement(
            name = "Sobreviviendo dije, sobreviviendo",
            shortName = "Sobreviviendo",
            description = "Ganaste como Aldeano, sin morir, en una partida de mas de 12 jugadores.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.SILVER
        ),
        ProfileAchievement(
            name = "El alcalde que fue prometido",
            shortName = "Prometido",
            description = "Ganaste 15 partidas como Alcalde despues de usar tu poder para decidir una expulsion.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.SILVER
        ),
        ProfileAchievement(
            name = "Quien te ha visto y quien te ve",
            shortName = "50 victorias",
            description = "Ganaste 50 partidas. El pueblo ya no sabe si confiarte el mate o revisarte los bolsillos.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.GOLD
        ),
        ProfileAchievement(
            name = "Traidores Supremo",
            shortName = "Supremo",
            description = "Obtuviste todos los demas logros. A esta altura, el pueblo te saluda con respeto y miedo.",
            obtainedDate = "Pendiente",
            rarity = AchievementRarity.GOLD
        )
    )

    fun achievement(name: String): ProfileAchievement? {
        return achievements.firstOrNull { it.name == name }
    }

    fun unlockedAchievements(): List<ProfileAchievement> {
        return achievements
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
