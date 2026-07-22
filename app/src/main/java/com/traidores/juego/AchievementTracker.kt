package com.traidores.juego

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AchievementTracker {

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PENDING_DATE = "Pendiente"
    private const val UNLOCKED_PREFIX = "achievement_unlocked_"
    private const val DATE_PREFIX = "achievement_date_"
    private const val PROCESSED_EVENTS = "achievement_processed_events"
    private const val ASSASSIN_KILLS = "achievement_assassin_kills"
    private const val JESTER_WINS = "achievement_jester_wins"
    private const val DESERTER_WINS = "achievement_deserter_wins"
    private const val MAYOR_POWER_WINS = "achievement_mayor_power_wins"
    private const val TOTAL_WINS = "achievement_total_wins"
    private const val MAX_PROCESSED_EVENTS = 120

    fun ensureProfileOpened(context: Context): List<ProfileAchievement> {
        return unlock(context, ProfileCustomizationCatalog.ACH_PROFILE_CREATED)
            ?.let(::listOf)
            .orEmpty()
    }

    fun achievementsWithProgress(context: Context): List<ProfileAchievement> {
        return ProfileCustomizationCatalog.achievements.map { achievementWithProgress(context, it) }
    }

    fun achievementWithProgress(
        context: Context,
        achievement: ProfileAchievement
    ): ProfileAchievement {
        val prefs = prefs(context)
        val unlocked = prefs.getBoolean(unlockedKey(achievement.id), false)
        val date = prefs.getString(dateKey(achievement.id), null)
            .orEmpty()
            .ifBlank { "Sin fecha" }
        return achievement.copy(obtainedDate = if (unlocked) date else PENDING_DATE)
    }

    fun unlockedAchievements(context: Context): List<ProfileAchievement> {
        return achievementsWithProgress(context)
            .filter { it.obtainedDate != PENDING_DATE }
    }

    fun recordMatchIfNeeded(context: Context, session: GameSession): List<ProfileAchievement> {
        val human = session.players.firstOrNull { it.isHuman } ?: return emptyList()
        val prefs = prefs(context)
        val unlocked = mutableListOf<ProfileAchievement>()

        unlocked += recordJesterVictoryIfNeeded(prefs, context, session, human)

        if (session.winner.isBlank()) {
            unlocked += unlockSupremeIfReady(context)
            return unlocked
        }

        val finalEventKey = "final:${MatchOutcome.matchKey(session)}"
        if (finalEventKey in processedEvents(prefs)) {
            unlocked += unlockSupremeIfReady(context)
            return unlocked
        }

        val humanWon = MatchOutcome.didHumanWin(session, human)
        val roleKey = human.role?.key.orEmpty()
        if (humanWon) {
            if (incrementCounter(prefs, TOTAL_WINS, 1) >= 50) {
                unlocked += unlock(context, ProfileCustomizationCatalog.ACH_TOTAL_WINS_50)
                    .orEmptyList()
            }
        }

        if (roleKey == RoleCatalog.ASESINO) {
            val kills = session.actionHistory.count {
                it.actor == human.name && it.type == GameActionType.KILL
            }
            if (kills > 0 && incrementCounter(prefs, ASSASSIN_KILLS, kills) >= 25) {
                unlocked += unlock(context, ProfileCustomizationCatalog.ACH_ASSASSIN_KILLS_25)
                    .orEmptyList()
            }
        }

        if (roleKey == RoleCatalog.DESERTOR && humanWon) {
            if (incrementCounter(prefs, DESERTER_WINS, 1) >= 10) {
                unlocked += unlock(context, ProfileCustomizationCatalog.ACH_DESERTER_WINS_10)
                    .orEmptyList()
            }
        }

        if (roleKey == RoleCatalog.MERCENARIO && silencedSameTargetThreeTimes(session, human)) {
            unlocked += unlock(context, ProfileCustomizationCatalog.ACH_MERCENARY_SAME_TARGET_3)
                .orEmptyList()
        }

        if (
            roleKey == RoleCatalog.ALDEANO &&
            humanWon &&
            human.alive &&
            session.initialPlayerCount > 12
        ) {
            unlocked += unlock(context, ProfileCustomizationCatalog.ACH_VILLAGER_SURVIVES_12)
                .orEmptyList()
        }

        if (roleKey == RoleCatalog.ALCALDE && humanWon && mayorDecidedExpulsion(session)) {
            if (incrementCounter(prefs, MAYOR_POWER_WINS, 1) >= 15) {
                unlocked += unlock(context, ProfileCustomizationCatalog.ACH_MAYOR_POWER_WINS_15)
                    .orEmptyList()
            }
        }

        if (expelledAllKillersByVote(session)) {
            unlocked += unlock(context, ProfileCustomizationCatalog.ACH_EXPEL_ALL_KILLERS)
                .orEmptyList()
        }

        rememberEvent(prefs, finalEventKey)
        unlocked += unlockSupremeIfReady(context)
        return unlocked
    }

    private fun recordJesterVictoryIfNeeded(
        prefs: SharedPreferences,
        context: Context,
        session: GameSession,
        human: GamePlayer
    ): List<ProfileAchievement> {
        val wonAsJester = session.specialVictories.any {
            it.playerName == human.name && it.roleKey == RoleCatalog.BUFON
        }
        if (!wonAsJester) return emptyList()

        val eventKey = "special:jester:${MatchOutcome.matchKey(session)}"
        if (eventKey in processedEvents(prefs)) return emptyList()

        val unlocked = if (incrementCounter(prefs, JESTER_WINS, 1) >= 5) {
            unlock(context, ProfileCustomizationCatalog.ACH_JESTER_WINS_5)
                .orEmptyList()
        } else {
            emptyList()
        }
        rememberEvent(prefs, eventKey)
        return unlocked
    }

    private fun silencedSameTargetThreeTimes(session: GameSession, human: GamePlayer): Boolean {
        return session.actionHistory
            .filter { it.actor == human.name && it.type == GameActionType.SILENCE }
            .groupingBy { it.target }
            .eachCount()
            .values
            .any { it >= 3 }
    }

    private fun mayorDecidedExpulsion(session: GameSession): Boolean {
        if (!session.alcaldeRevealed) return false
        if (session.voteRound >= 3 && session.dayEliminationTarget.isNotBlank()) return true
        val history = session.publicHistory.joinToString(" ").lowercase()
        return listOf(
            "alcalde decidio",
            "alcalde impuso",
            "alcalde se protegio"
        ).any(history::contains)
    }

    private fun expelledAllKillersByVote(session: GameSession): Boolean {
        if (session.winner != GameRules.TOWN_WINNER) return false
        val killerNames = session.players
            .filter { it.role?.key in GameRules.killerRoleKeys }
            .map { it.name }
        if (killerNames.isEmpty()) return false

        val expelledNames = expulsionTargets(session)
        if (!killerNames.all { it in expelledNames }) return false

        val killerNameSet = killerNames.toSet()
        val lastRelevantExpulsions = expelledNames.takeLast(killerNames.size)
        return lastRelevantExpulsions.size == killerNames.size &&
            lastRelevantExpulsions.all { it in killerNameSet }
    }

    private fun expulsionTargets(session: GameSession): List<String> {
        return session.publicHistory.mapNotNull { message ->
            val lower = message.lowercase()
            session.players.firstOrNull { player ->
                lower.contains("${player.name.lowercase()} fue expulsado")
            }?.name
        }
    }

    private fun unlockSupremeIfReady(context: Context): List<ProfileAchievement> {
        val prefs = prefs(context)
        val requiredIds = ProfileCustomizationCatalog.achievements
            .map { it.id }
            .filterNot { it == ProfileCustomizationCatalog.ACH_TRAIDORES_SUPREMO }
        val hasAllRequired = requiredIds.all { prefs.getBoolean(unlockedKey(it), false) }
        if (!hasAllRequired) return emptyList()
        return unlock(context, ProfileCustomizationCatalog.ACH_TRAIDORES_SUPREMO).orEmptyList()
    }

    private fun unlock(context: Context, achievementId: String): ProfileAchievement? {
        val achievement = ProfileCustomizationCatalog.achievementById(achievementId) ?: return null
        val prefs = prefs(context)
        if (prefs.getBoolean(unlockedKey(achievementId), false)) return null

        prefs.edit()
            .putBoolean(unlockedKey(achievementId), true)
            .putString(dateKey(achievementId), today())
            .apply()
        return achievementWithProgress(context, achievement)
    }

    private fun incrementCounter(
        prefs: SharedPreferences,
        key: String,
        amount: Int
    ): Int {
        val updated = (prefs.getInt(key, 0) + amount).coerceAtLeast(0)
        prefs.edit().putInt(key, updated).apply()
        return updated
    }

    private fun processedEvents(prefs: SharedPreferences): List<String> {
        return prefs.getString(PROCESSED_EVENTS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun rememberEvent(prefs: SharedPreferences, eventKey: String) {
        val updated = (processedEvents(prefs) + eventKey)
            .distinct()
            .takeLast(MAX_PROCESSED_EVENTS)
        prefs.edit()
            .putString(PROCESSED_EVENTS, updated.joinToString("\n"))
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun unlockedKey(id: String): String = "$UNLOCKED_PREFIX$id"

    private fun dateKey(id: String): String = "$DATE_PREFIX$id"

    private fun today(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale("es", "AR")).format(Date())
    }

    private fun ProfileAchievement?.orEmptyList(): List<ProfileAchievement> {
        return this?.let(::listOf).orEmpty()
    }
}
