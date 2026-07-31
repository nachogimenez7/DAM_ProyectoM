package com.traidores.juego

import android.app.Activity
import android.content.Context
import com.google.android.gms.games.PlayGames
import com.google.firebase.firestore.FirebaseFirestore

object PlayGamesProgressSync {
    fun onAuthenticated(activity: Activity) {
        PlayGamesCloudSave.restoreOrUpload(activity) {
            syncAllAchievements(activity)
            submitLeaderboards(activity)
            PlayerPublicIdentity.ensurePublicId(
                context = activity,
                firestore = FirebaseFirestore.getInstance(),
                onReady = {}
            )
        }
    }

    fun onMatchRecorded(context: Context) {
        PlayGamesCloudSave.markLocalChanged(context)
        val activity = context as? Activity ?: return
        if (!PlayGamesIdentity.isReady(activity)) return
        submitLeaderboards(activity)
        PlayGamesCloudSave.save(activity)
    }

    fun onProfileSaved(context: Context) {
        PlayGamesCloudSave.markLocalChanged(context)
        val activity = context as? Activity ?: return
        if (PlayGamesIdentity.isReady(activity)) {
            PlayGamesCloudSave.save(activity)
        }
    }

    fun unlockAchievement(context: Context, localAchievementId: String) {
        val incrementalSteps = incrementalMaxSteps(localAchievementId)
        if (incrementalSteps != null) {
            setAchievementSteps(context, localAchievementId, incrementalSteps)
            return
        }
        val activity = context as? Activity ?: return
        if (!PlayGamesIdentity.isReady(activity)) return
        val remoteId = remoteAchievementId(activity, localAchievementId) ?: return
        PlayGames.getAchievementsClient(activity).unlock(remoteId)
    }

    fun setAchievementSteps(
        context: Context,
        localAchievementId: String,
        completedSteps: Int
    ) {
        val maxSteps = incrementalMaxSteps(localAchievementId) ?: return
        val activity = context as? Activity ?: return
        if (!PlayGamesIdentity.isReady(activity)) return
        val remoteId = remoteAchievementId(activity, localAchievementId) ?: return
        val safeSteps = completedSteps.coerceIn(0, maxSteps)
        if (safeSteps == 0) return
        PlayGames.getAchievementsClient(activity).setSteps(remoteId, safeSteps)
    }

    fun syncAllAchievements(activity: Activity) {
        if (!PlayGamesIdentity.isReady(activity)) return
        val client = PlayGames.getAchievementsClient(activity)
        val unlockedIds = AchievementTracker.unlockedAchievements(activity)
            .mapTo(mutableSetOf()) { it.id }
        ProfileCustomizationCatalog.achievements.forEach { achievement ->
            val remoteId = remoteAchievementId(activity, achievement.id) ?: return@forEach
            val maxSteps = incrementalMaxSteps(achievement.id)
            if (maxSteps == null) {
                if (achievement.id in unlockedIds) client.unlock(remoteId)
                return@forEach
            }
            val localSteps = if (achievement.id in unlockedIds) {
                maxSteps
            } else {
                AchievementTracker.incrementalProgress(activity, achievement.id)
                    .coerceAtMost(maxSteps)
            }
            if (localSteps > 0) client.setSteps(remoteId, localSteps)
        }
    }

    fun submitLeaderboards(activity: Activity) {
        if (!PlayGamesIdentity.isReady(activity)) return
        val stats = MatchHistoryStore.stats(activity)
        val client = PlayGames.getLeaderboardsClient(activity)
        PlayGamesConfig.configuredRemoteId(
            activity,
            R.string.play_games_leaderboard_total_wins
        )?.let { client.submitScore(it, stats.wins.toLong()) }
        PlayGamesConfig.configuredRemoteId(
            activity,
            R.string.play_games_leaderboard_total_matches
        )?.let { client.submitScore(it, stats.matches.toLong()) }
    }

    internal fun achievementResource(localAchievementId: String): Int? {
        return when (localAchievementId) {
            ProfileCustomizationCatalog.ACH_PROFILE_CREATED ->
                R.string.play_games_achievement_profile_created
            ProfileCustomizationCatalog.ACH_ASSASSIN_KILLS_25 ->
                R.string.play_games_achievement_assassin_kills_25
            ProfileCustomizationCatalog.ACH_JESTER_WINS_5 ->
                R.string.play_games_achievement_jester_wins_5
            ProfileCustomizationCatalog.ACH_EXPEL_ALL_KILLERS ->
                R.string.play_games_achievement_expel_all_killers
            ProfileCustomizationCatalog.ACH_DESERTER_WINS_10 ->
                R.string.play_games_achievement_deserter_wins_10
            ProfileCustomizationCatalog.ACH_MERCENARY_SAME_TARGET_3 ->
                R.string.play_games_achievement_mercenary_same_target_3
            ProfileCustomizationCatalog.ACH_VILLAGER_SURVIVES_12 ->
                R.string.play_games_achievement_villager_survives_12
            ProfileCustomizationCatalog.ACH_MAYOR_POWER_WINS_15 ->
                R.string.play_games_achievement_mayor_power_wins_15
            ProfileCustomizationCatalog.ACH_TOTAL_WINS_50 ->
                R.string.play_games_achievement_total_wins_50
            ProfileCustomizationCatalog.ACH_TRAIDORES_SUPREMO ->
                R.string.play_games_achievement_traidores_supremo
            else -> null
        }
    }

    internal fun incrementalMaxSteps(localAchievementId: String): Int? {
        return when (localAchievementId) {
            ProfileCustomizationCatalog.ACH_ASSASSIN_KILLS_25 -> 25
            ProfileCustomizationCatalog.ACH_JESTER_WINS_5 -> 5
            ProfileCustomizationCatalog.ACH_DESERTER_WINS_10 -> 10
            ProfileCustomizationCatalog.ACH_MAYOR_POWER_WINS_15 -> 15
            ProfileCustomizationCatalog.ACH_TOTAL_WINS_50 -> 50
            else -> null
        }
    }

    private fun remoteAchievementId(
        context: Context,
        localAchievementId: String
    ): String? {
        val resource = achievementResource(localAchievementId) ?: return null
        return PlayGamesConfig.configuredRemoteId(context, resource)
    }
}
