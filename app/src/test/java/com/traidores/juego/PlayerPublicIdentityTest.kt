package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPublicIdentityTest {

    @Test
    fun legacyGuestPublicIdIsIgnoredUntilAccountIsRegistered() {
        assertEquals("", PlayerPublicIdentity.publicIdForSession("27", isGuest = true))
        assertEquals("27", PlayerPublicIdentity.publicIdForSession("27", isGuest = false))
        assertEquals("", PlayerPublicIdentity.publicIdForSession("#27", isGuest = false))
    }

    @Test
    fun publicProfileFieldsExposeBioAvatarAndFavoriteRole() {
        val profile = PlayerProfile(
            name = "Federico",
            publicId = "7",
            bio = "Juego callado hasta que hace falta hablar.",
            avatarKey = "grecia_oraculo",
            bannerKey = "medieval",
            favoriteRoleKey = "pampa_payador",
            featuredAchievementIds = emptyList(),
            emoteIds = emptyList(),
            stats = PlayerStats(matches = 0, wins = 0, hasProgress = false)
        )

        val fields = PlayerPublicIdentity.publicProfileFields(
            profile = profile,
            publicId = "7",
            visibleName = "Fede"
        )

        assertEquals("7", fields[PlayerPublicIdentity.FIELD_PUBLIC_ID])
        assertEquals("Fede", fields[PlayerPublicIdentity.FIELD_PROFILE_NAME])
        assertEquals("Fede", fields[PlayerPublicIdentity.FIELD_ROOM_NAME])
        assertEquals("Juego callado hasta que hace falta habla", fields[PlayerPublicIdentity.FIELD_PROFILE_BIO])
        assertEquals("grecia_oraculo", fields[PlayerPublicIdentity.FIELD_PROFILE_AVATAR])
        assertEquals("medieval", fields[PlayerPublicIdentity.FIELD_PROFILE_BANNER])
        assertEquals("pampa_payador", fields[PlayerPublicIdentity.FIELD_PROFILE_FAVORITE_ROLE])
    }
}
