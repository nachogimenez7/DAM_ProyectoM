package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineLobbyConfigTest {
    @Test
    fun customCompositionRoundTripsThroughFirestore() {
        val source = OnlineLobbyConfig(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ALDEANO to 4,
                    RoleCatalog.POLICIA to 0,
                    RoleCatalog.MEDICO to 0,
                    RoleCatalog.ASESINO to 2,
                    RoleCatalog.ESPIA to 1,
                    RoleCatalog.MERCENARIO to 1,
                    RoleCatalog.ALCALDE to 1,
                    RoleCatalog.PAYADOR to 1
                ),
                customized = true
            ),
            rolePreset = null
        )

        val restored = OnlineLobbyConfig.fromFirestore(source.toFirestore(), OnlineLobbyConfig())

        assertNull(restored.rolePreset)
        assertEquals(2, restored.roleComposition.counts[RoleCatalog.ASESINO])
        assertEquals(0, restored.roleComposition.counts[RoleCatalog.POLICIA])
        assertEquals(0, restored.roleComposition.counts[RoleCatalog.MEDICO])
    }

    @Test
    fun legacyRoomWithoutRolesKeepsRecommendedPreset() {
        val restored = OnlineLobbyConfig.fromFirestore(
            mapOf(
                "transicionSeg" to 3,
                "nocheSeg" to 30,
                "discusionSeg" to 60,
                "votacionSeg" to 30,
                "revelarRolesAlMorir" to false,
                "votosIndividuales" to true
            ),
            OnlineLobbyConfig()
        )

        assertEquals(RoleCompositionPreset.RECOMMENDED, restored.rolePreset)
        val composition = restored.compositionFor(10, "pampa")
        assertEquals(1, composition.counts[RoleCatalog.ESPIA])
    }

    @Test
    fun customCompositionCanOmitDoctorAndDetectiveButKeepsAssassin() {
        val config = OnlineLobbyConfig(
            roleComposition = RoleCompositionConfig(
                counts = mapOf(
                    RoleCatalog.ALDEANO to 7,
                    RoleCatalog.POLICIA to 0,
                    RoleCatalog.MEDICO to 0,
                    RoleCatalog.ASESINO to 1
                ),
                customized = true
            ),
            rolePreset = null
        )

        val composition = config.compositionFor(8, "pampa")

        assertEquals(0, composition.counts[RoleCatalog.POLICIA])
        assertEquals(0, composition.counts[RoleCatalog.MEDICO])
        assertEquals(1, composition.counts[RoleCatalog.ASESINO])
        assertEquals(7, composition.counts[RoleCatalog.ALDEANO])
    }

    @Test
    fun balanceWarningsCoverFairAndDangerousTables() {
        assertEquals(
            RoleCompositionBalance.BALANCED,
            RoleCompositionBalance.evaluate(
                10,
                mapOf(RoleCatalog.ASESINO to 1, RoleCatalog.ESPIA to 1, RoleCatalog.MERCENARIO to 1)
            )
        )
        assertEquals(
            RoleCompositionBalance.TRAITORS_FAVORED,
            RoleCompositionBalance.evaluate(
                10,
                mapOf(RoleCatalog.ASESINO to 3, RoleCatalog.ESPIA to 1, RoleCatalog.MERCENARIO to 1)
            )
        )
    }

    @Test
    fun classicAndChaoticPresetsResolveToDifferentAutomaticCompositions() {
        val classic = OnlineLobbyConfig(rolePreset = RoleCompositionPreset.CLASSIC)
            .compositionFor(10, "pampa")
        val chaotic = OnlineLobbyConfig(rolePreset = RoleCompositionPreset.CHAOTIC)
            .compositionFor(10, "pampa")

        assertEquals(1, classic.counts[RoleCatalog.ASESINO])
        assertEquals(0, classic.counts[RoleCatalog.MERCENARIO])
        assertEquals(0, classic.counts[RoleCatalog.ESPIA])
        assertEquals(2, chaotic.counts[RoleCatalog.ASESINO])
        assertEquals(1, chaotic.counts[RoleCatalog.MERCENARIO])
        assertEquals(1, chaotic.counts[RoleCatalog.ESPIA])
    }
}
