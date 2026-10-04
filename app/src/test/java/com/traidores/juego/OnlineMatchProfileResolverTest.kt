package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnlineMatchProfileResolverTest {
    @Test
    fun attachesRemoteAppearanceByUidDespiteDifferentLobbyProfileName() {
        val match = GameSession(
            code = "SALA",
            mapKey = "pampa",
            mapName = "Pampa",
            players = listOf(GamePlayer("Mamá", "M", isHuman = true), GamePlayer("Nacho", "N")),
            onlinePlayerUids = listOf("uid_mama", "uid_nacho")
        )
        val remote = PlayerProfile(
            name = "Nacho #42",
            publicId = "42",
            bio = "",
            avatarKey = "aldeana",
            bannerKey = "pampa",
            favoriteRoleKey = "detective",
            featuredAchievementIds = emptyList(),
            emoteIds = listOf("premium_mate"),
            stats = PlayerStats(0, 0, false),
            profilePhotoUrl = "https://firebasestorage.googleapis.com/v0/b/traidores/o/avatar.jpg?v=2",
            cosmeticThemeId = CosmeticPilot.THEME_FIRE
        )

        val resolved = OnlineMatchProfileResolver.attach(match, mapOf("uid_nacho" to remote))

        assertEquals("Nacho", resolved.playerProfiles.getValue("Nacho").name)
        assertEquals(CosmeticPilot.THEME_FIRE, resolved.playerProfiles.getValue("Nacho").cosmeticThemeId)
        assertEquals(listOf("premium_mate"), resolved.playerProfiles.getValue("Nacho").emoteIds)
        assertEquals(remote.profilePhotoUrl, resolved.playerProfiles.getValue("Nacho").publicAvatarUri)
        assertFalse(resolved.playerProfiles.containsKey("Mamá"))
    }

    @Test
    fun carriesAuthorityCompatibilityByUidAndPreservesItWithoutNewRoster() {
        val match = GameSession(
            code = "SALA", mapKey = "pampa", mapName = "Pampa",
            players = listOf(GamePlayer("Mamá", "M", isHuman = true), GamePlayer("Nacho", "N")),
            onlinePlayerUids = listOf("uid_mama", "uid_nacho")
        )
        val resolved = OnlineMatchProfileResolver.attach(
            match, emptyMap(), mapOf("uid_mama" to false, "uid_nacho" to true, "other" to false)
        )
        assertEquals(listOf("uid_mama"), resolved.onlineNonAuthorityPlayerUids)
        assertEquals(listOf("uid_mama"),
            OnlineMatchProfileResolver.attach(resolved, emptyMap()).onlineNonAuthorityPlayerUids)
        assertEquals(emptyList<String>(), OnlineMatchProfileResolver.attach(
            resolved, emptyMap(), mapOf("uid_mama" to true)
        ).onlineNonAuthorityPlayerUids)
    }
}
