package com.traidores.juego

import android.content.Context
import java.io.Serializable
import kotlin.math.roundToInt

data class PlayerProfile(
    val name: String,
    val publicId: String,
    val bio: String,
    val avatarKey: String,
    val bannerKey: String,
    val favoriteRoleKey: String,
    val featuredAchievementIds: List<String>,
    val emoteIds: List<String>,
    val stats: PlayerStats,
    val playGamesAvatarUri: String = "",
    val cosmeticThemeId: String = CosmeticPilot.THEME_CLASSIC
) : Serializable

data class PlayerStats(
    val matches: Int,
    val wins: Int,
    val hasProgress: Boolean
) : Serializable {
    val winRatePercent: Int
        get() = if (matches > 0) ((wins * 100.0) / matches).toInt() else 0
}

object PlayerProfileStore {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_NAME = "profile_name"
    private const val PREF_BIO = "profile_bio"
    private const val PREF_AVATAR = "profile_avatar"
    private const val PREF_BANNER = "profile_banner"
    private const val PREF_FAVORITE_ROLE = "profile_favorite_role"
    private const val PREF_ACHIEVEMENTS = "profile_achievements"
    private const val DEFAULT_BIO = "No fui yo. Esta vez."
    private const val DEFAULT_AVATAR_KEY = "aldeana"
    private const val DEFAULT_BANNER_KEY = "pampa"
    private const val DEFAULT_ROLE_KEY = "detective"
    private const val ACHIEVEMENT_SEPARATOR = "|"
    private const val MAX_FEATURED_ACHIEVEMENTS = 3

    fun loadHumanProfile(context: Context): PlayerProfile {
        AchievementTracker.ensureProfileOpened(context)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackName = preferences
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
            .ifBlank { "Jugador" }
        // Un invitado no tiene nombre propio: usa el alias que eligio de la lista cerrada.
        // Se resuelve aca y en PlayerPublicIdentity.profileName para que ninguna pantalla
        // tenga que acordarse de preguntar si hay cuenta.
        val guestName = if (GuestIdentity.isGuest()) GuestIdentity.displayName(context) else null
        val featuredAchievementIds = preferences
            .getString(PREF_ACHIEVEMENTS, null)
            ?.split(ACHIEVEMENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .mapNotNull(::achievementIdFromStoredValue)
            .distinct()
            .take(MAX_FEATURED_ACHIEVEMENTS)

        return PlayerProfile(
            name = guestName
                ?: preferences.getString(PREF_NAME, fallbackName).orEmpty().ifBlank { fallbackName },
            publicId = PlayerPublicIdentity.currentPublicId(context),
            bio = preferences.getString(PREF_BIO, DEFAULT_BIO).orEmpty(),
            avatarKey = preferences.getString(PREF_AVATAR, DEFAULT_AVATAR_KEY)
                .orEmpty()
                .ifBlank { DEFAULT_AVATAR_KEY },
            bannerKey = ProfileCustomizationCatalog.normalizeBannerKey(
                preferences.getString(PREF_BANNER, DEFAULT_BANNER_KEY)
                    .orEmpty()
                    .ifBlank { DEFAULT_BANNER_KEY }
            ),
            favoriteRoleKey = preferences.getString(PREF_FAVORITE_ROLE, DEFAULT_ROLE_KEY)
                .orEmpty()
                .ifBlank { DEFAULT_ROLE_KEY },
            featuredAchievementIds = featuredAchievementIds.ifEmpty {
                AchievementTracker.unlockedAchievements(context)
                    .map { it.id }
                    .take(MAX_FEATURED_ACHIEVEMENTS)
            },
            emoteIds = EmoteLoadout.selectedIds(context),
            stats = PlayerStats(matches = 0, wins = 0, hasProgress = false),
            playGamesAvatarUri = PlayGamesProfileAvatar.normalize(
                preferences.getString(ProfileActivity.PREF_PLAY_GAMES_AVATAR_URI, "").orEmpty()
            ),
            cosmeticThemeId = CosmeticPilot.selectedTheme(context)
        )
    }

    fun profileFor(context: Context, session: GameSession, player: GamePlayer): PlayerProfile {
        val profile = session.playerProfiles[player.name]
            ?: if (player.isHuman) loadHumanProfile(context).copy(name = player.name) else BotProfileFactory.profileFor(player.name)
        return if (player.control == PlayerControl.BOT) {
            profile.copy(cosmeticThemeId = CosmeticPilot.THEME_CLASSIC)
        } else {
            profile
        }
    }

    fun withProfiles(context: Context, session: GameSession): GameSession {
        val existing = session.playerProfiles
        val profiles = linkedMapOf<String, PlayerProfile>()
        val humanProfile = loadHumanProfile(context)
        session.players.forEach { player ->
            val profile = existing[player.name] ?: if (player.isHuman) {
                humanProfile.copy(name = player.name)
            } else {
                BotProfileFactory.profileFor(player.name)
            }
            profiles[player.name] = if (player.control == PlayerControl.BOT) {
                profile.copy(cosmeticThemeId = CosmeticPilot.THEME_CLASSIC)
            } else {
                profile
            }
        }
        return session.copy(playerProfiles = profiles)
    }

    private fun achievementIdFromStoredValue(value: String): String? {
        return ProfileCustomizationCatalog.achievementById(value)?.id
            ?: ProfileCustomizationCatalog.achievement(value)?.id
    }

    /**
     * Baja a este dispositivo el perfil visual guardado en `perfiles_publicos`. Se usa al
     * entrar con una cuenta que ya existia: sin esto se recuperaba el `#` pero el avatar, el
     * banner y la frase quedaban los del celular nuevo, que es justo lo contrario de lo que
     * promete la pantalla de cuenta.
     *
     * Cada campo se escribe solo si vino con algo: un documento incompleto no puede borrar lo
     * que el jugador ya tenia.
     */
    fun saveRecoveredProfile(
        context: Context,
        name: String,
        bio: String,
        avatarKey: String,
        bannerKey: String,
        favoriteRoleKey: String,
        playGamesAvatarUri: String? = null
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        name.takeIf { it.isNotBlank() }?.let {
            editor.putString(PREF_NAME, it)
            editor.putString(OpcionesActivity.PREF_PLAYER_NAME, it)
        }
        bio.takeIf { it.isNotBlank() }?.let { editor.putString(PREF_BIO, it) }
        avatarKey.takeIf { it.isNotBlank() }?.let { editor.putString(PREF_AVATAR, it) }
        playGamesAvatarUri?.let {
            editor.putString(
                ProfileActivity.PREF_PLAY_GAMES_AVATAR_URI,
                PlayGamesProfileAvatar.normalize(it)
            )
        }
        bannerKey.takeIf { it.isNotBlank() }?.let {
            editor.putString(PREF_BANNER, ProfileCustomizationCatalog.normalizeBannerKey(it))
        }
        favoriteRoleKey.takeIf { it.isNotBlank() }?.let { editor.putString(PREF_FAVORITE_ROLE, it) }
        editor.apply()
    }
}

object BotProfileFactory {
    private const val BOT_PUBLIC_ID_BASE = 70_000

    private val roster = mapOf(
        bot(
            "Thiago",
            "Siempre habla primero y despues revisa si tenia razon.",
            "pampa_policia",
            "pampa",
            "pampa_policia",
            listOf(ProfileCustomizationCatalog.ACH_PROFILE_CREATED, ProfileCustomizationCatalog.ACH_EXPEL_ALL_KILLERS),
            listOf("gaucho_sospechoso", "gaucho_enojado", "premium_mate", "griego_contento")
        ),
        bot(
            "Mora",
            "Escucha mas de lo que dice. Si te mira raro, algo vio.",
            "grecia_oraculo",
            "grecia",
            "grecia_oraculo",
            listOf(ProfileCustomizationCatalog.ACH_PROFILE_CREATED, ProfileCustomizationCatalog.ACH_TOTAL_WINS_50),
            listOf("griego_sospechoso", "griego_triste", "premium_dormida", "griego_enojado")
        ),
        bot(
            "Lautaro",
            "No acusa fuerte: deja frases cortas y espera que el pueblo se prenda fuego solo.",
            "pampa_asesino",
            "pampa",
            "pampa_asesino",
            listOf(ProfileCustomizationCatalog.ACH_ASSASSIN_KILLS_25, ProfileCustomizationCatalog.ACH_PROFILE_CREATED),
            listOf("gaucho_enojado", "gaucho_sospechoso", "premium_mate", "griego_contento")
        ),
        bot("Valen", "Suele votar tarde, pero pocas veces vota sin motivo.", "grecia_medico", "grecia", "grecia_medico"),
        bot("Rami", "Tiene cara de inocente y estadisticas que no ayudan a creerle.", "medieval_espia", "medieval", "medieval_espia"),
        bot("Juli", "Defiende al que nadie defiende y despues pregunta por que sospechan.", "grecia_alcalde", "grecia", "grecia_alcalde"),
        bot("Santi", "Cuando todos gritan, el cuenta votos.", "medieval_policia", "medieval", "medieval_policia"),
        bot("Mili", "Le gusta cambiar de opinion justo antes de votar.", "pampa_mercenario", "pampa", "pampa_mercenario"),
        bot("Toto", "Juega como si supiera algo. A veces es verdad.", "pampa_payador", "pampa", "pampa_payador"),
        bot("Agus", "Se rie en los momentos equivocados.", "medieval_bufon", "medieval", "medieval_bufon"),
        bot("Bruno", "Pide pruebas, recibe pruebas y pide pruebas mejores.", "medieval_aldeano", "medieval", "medieval_aldeano"),
        bot("Lola", "Nunca parece apurada, ni cuando la acusan tres a la vez.", "grecia_desertor", "grecia", "grecia_desertor"),
        bot("Fede", "Vota con seguridad incluso cuando no tiene ninguna.", "medieval_asesino", "medieval", "medieval_asesino"),
        bot("Cata", "Tiene memoria para cada contradiccion del pueblo.", "pampa_alcalde", "pampa", "pampa_alcalde")
    )

    private val bios = listOf(
        "Dice que vino a jugar tranquilo, pero anota todo.",
        "No levanta la voz: espera que otro se equivoque.",
        "Tiene una teoria distinta para cada ronda.",
        "Si sobrevive dos dias, empieza a dar miedo.",
        "Nunca admite estar perdido, solo estar observando."
    )
    private val avatarKeys = ProfileRoleCatalog.entries.map { it.key }
    private val bannerKeys = ProfileCustomizationCatalog.banners.map { it.key }
    private val favoriteRoles = ProfileRoleCatalog.entries.map { it.key }
    private val achievementIds = ProfileCustomizationCatalog.achievements.map { it.id }
    private val emoteIds = EmoteCatalog.all.map { it.id }

    fun profileFor(name: String): PlayerProfile {
        return roster[name] ?: generatedProfile(name)
    }

    private fun generatedProfile(name: String): PlayerProfile {
        val seed = stableSeed(name)
        return PlayerProfile(
            name = name,
            publicId = botPublicId(name),
            bio = bios.pick(seed),
            avatarKey = avatarKeys.pick(seed / 3),
            bannerKey = bannerKeys.pick(seed / 5),
            favoriteRoleKey = favoriteRoles.pick(seed / 7),
            featuredAchievementIds = stableSlice(achievementIds, seed, 3),
            emoteIds = stableSlice(emoteIds, seed / 11, EmoteCatalog.LOADOUT_SIZE),
            stats = botStatsFor(name),
            cosmeticThemeId = CosmeticPilot.THEME_CLASSIC
        )
    }

    private fun bot(
        name: String,
        bio: String,
        avatarKey: String,
        bannerKey: String,
        favoriteRoleKey: String,
        achievementIds: List<String> = stableSlice(ProfileCustomizationCatalog.achievements.map { it.id }, stableSeed(name), 2),
        emoteIds: List<String> = stableSlice(EmoteCatalog.defaultLoadoutIds + EmoteCatalog.all.map { it.id }, stableSeed(name), EmoteCatalog.LOADOUT_SIZE)
    ): Pair<String, PlayerProfile> {
        return name to PlayerProfile(
            name = name,
            publicId = botPublicId(name),
            bio = bio,
            avatarKey = avatarKey,
            bannerKey = bannerKey,
            favoriteRoleKey = favoriteRoleKey,
            featuredAchievementIds = achievementIds.distinct().take(3),
            emoteIds = EmoteLoadout.normalizeIds(emoteIds),
            stats = botStatsFor(name),
            cosmeticThemeId = CosmeticPilot.THEME_CLASSIC
        )
    }

    private fun botStatsFor(name: String): PlayerStats {
        val seed = stableSeed(name)
        val matches = 20 + seed % 381
        val rate = 35 + (seed / 13) % 31
        val wins = (matches * (rate / 100.0)).roundToInt().coerceIn(0, matches)
        return PlayerStats(matches = matches, wins = wins, hasProgress = true)
    }

    private fun botPublicId(name: String): String {
        return (BOT_PUBLIC_ID_BASE + stableSeed(name) % 30_000).toString()
    }

    private fun stableSeed(value: String): Int {
        var hash = 23
        value.lowercase().forEach { char -> hash = 31 * hash + char.code }
        return hash and Int.MAX_VALUE
    }

    private fun <T> List<T>.pick(seed: Int): T {
        return this[seed % size]
    }

    private fun <T> stableSlice(source: List<T>, seed: Int, count: Int): List<T> {
        if (source.isEmpty()) return emptyList()
        val selected = mutableListOf<T>()
        var cursor = seed
        while (selected.size < count && selected.size < source.size) {
            val item = source[cursor % source.size]
            if (item !in selected) selected += item
            cursor = cursor / 3 + 7
        }
        return selected
    }
}
