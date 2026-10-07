package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class ProfileAvatarCatalogTest {
    @Test fun fourteenRosterMembersHaveUniqueAnimalsIndependentOfRoles() {
        val profiles = LocalGameFactory.botSlots().map { BotProfileFactory.profileFor(LocalGameFactory.defaultBotName(it)!!) }
        assertEquals(14, profiles.size)
        assertEquals(ProfileAvatarCatalog.keys, profiles.map { it.avatarKey })
        assertEquals(14, profiles.map { it.avatarKey }.toSet().size)
        assertEquals("avatar_hornero", BotProfileFactory.profileFor("Agus").avatarKey)
    }

    @Test fun legacyAvatarsMigrateOnceAndAnimalKeysRoundTripUnchanged() {
        for (key in listOf("aldeana", "pampa_policia", "grecia_oraculo", "medieval_bufon", "")) {
            val migrated = ProfileAvatarCatalog.normalize(key)
            assertTrue(migrated in ProfileAvatarCatalog.keys)
            assertEquals(migrated, ProfileAvatarCatalog.normalize(migrated))
            assertEquals(migrated, ProfileAvatarCatalog.find(key).key)
        }
        ProfileAvatarCatalog.keys.forEach { assertEquals(it, ProfileAvatarCatalog.normalize(it)) }
    }
}
