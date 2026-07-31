package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlayGamesIntegrationTest {

    @Test
    fun everyLocalAchievementHasOneRemoteResourceSlot() {
        val resources = ProfileCustomizationCatalog.achievements.map { achievement ->
            assertNotNull(
                "Falta mapear ${achievement.id}",
                PlayGamesProgressSync.achievementResource(achievement.id)
            )
            PlayGamesProgressSync.achievementResource(achievement.id)
        }

        assertEquals(10, resources.size)
        assertEquals(resources.size, resources.distinct().size)
    }

    @Test
    fun incrementalAchievementsMatchThePlayConsoleDraft() {
        val expected = mapOf(
            ProfileCustomizationCatalog.ACH_ASSASSIN_KILLS_25 to 25,
            ProfileCustomizationCatalog.ACH_JESTER_WINS_5 to 5,
            ProfileCustomizationCatalog.ACH_DESERTER_WINS_10 to 10,
            ProfileCustomizationCatalog.ACH_MAYOR_POWER_WINS_15 to 15,
            ProfileCustomizationCatalog.ACH_TOTAL_WINS_50 to 50
        )

        val configured = ProfileCustomizationCatalog.achievements
            .mapNotNull { achievement ->
                PlayGamesProgressSync.incrementalMaxSteps(achievement.id)
                    ?.let { steps -> achievement.id to steps }
            }
            .toMap()

        assertEquals(expected, configured)
    }

    @Test
    fun cloudConflictPrefersMoreMatchesThenNewestVersion() {
        val fewer = payload(matches = 3, updatedAt = 500)
        val more = payload(matches = 4, updatedAt = 100)
        assertSame(more, PlayGamesCloudPayload.preferred(fewer, more))

        val older = payload(matches = 4, updatedAt = 200)
        val newer = payload(matches = 4, updatedAt = 300)
        assertSame(newer, PlayGamesCloudPayload.preferred(older, newer))
    }

    @Test
    fun cloudPayloadRoundTripKeepsProgressButRejectsDeviceIdentity() {
        val original = PlayGamesCloudPayload(
            matchCount = 12,
            updatedAtMs = 1234,
            values = mapOf(
                "profile_name" to "Nacho",
                "achievement_unlocked_profile_created" to true,
                "local_match_history_keys" to setOf("a", "b"),
                "profile_public_id" to "999",
                "online_temp_uid" to "device-only"
            )
        )

        val decoded = PlayGamesCloudPayload.decode(original.encode())

        assertNotNull(decoded)
        assertEquals(12, decoded?.matchCount)
        assertEquals("Nacho", decoded?.values?.get("profile_name"))
        assertEquals(true, decoded?.values?.get("achievement_unlocked_profile_created"))
        assertEquals(setOf("a", "b"), decoded?.values?.get("local_match_history_keys"))
        assertFalse(decoded?.values?.containsKey("profile_public_id") == true)
        assertFalse(decoded?.values?.containsKey("online_temp_uid") == true)
    }

    private fun payload(matches: Int, updatedAt: Long): PlayGamesCloudPayload {
        return PlayGamesCloudPayload(
            matchCount = matches,
            updatedAtMs = updatedAt,
            values = emptyMap()
        )
    }
}
