package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAuthoritativeStateTransportTest {

    @Test
    fun realtimeCodecPreservesVotesWithoutUsingPlayerNamesAsKeys() {
        val original = mapOf<String, Any?>(
            "phaseIndex" to 7,
            "votos" to mapOf("Ana.1" to "Bruno", "Jugador#2" to "Carla")
        )

        val encoded = OnlineRealtimeStateCodec.toRealtime(original)
        val decoded = OnlineRealtimeStateCodec.fromRealtime(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun freshestPrefersPhaseAndThenCheckpointTimestamp() {
        val phaseThree = mapOf<String, Any?>("phaseIndex" to 3, "actualizadaEnLocal" to 900L)
        val phaseFourOldClock = mapOf<String, Any?>("phaseIndex" to 4, "actualizadaEnLocal" to 1L)
        val phaseFourNew = mapOf<String, Any?>("phaseIndex" to 4, "actualizadaEnLocal" to 20L)

        assertEquals(
            phaseFourOldClock,
            OnlineAuthoritativeStateStore.freshest(phaseThree, phaseFourOldClock)
        )
        assertEquals(
            phaseFourNew,
            OnlineAuthoritativeStateStore.freshest(phaseFourOldClock, phaseFourNew)
        )
    }

    @Test
    fun checkpointMustBelongToExpectedMatch() {
        val state = mapOf<String, Any?>("phaseIndex" to 9)
        val checkpoint = mapOf<String, Any?>(
            "matchId" to "match-actual",
            "estadoPartida" to state
        )

        assertEquals(
            state,
            OnlineAuthoritativeStateStore.checkpointState(checkpoint, "match-actual")
        )
        assertNull(OnlineAuthoritativeStateStore.checkpointState(checkpoint, "match-viejo"))
    }

    @Test
    fun recoveryPrefersCheckpointOnSamePhaseDespitePhoneClockSkew() {
        val legacyRoomState = mapOf<String, Any?>(
            "phaseIndex" to 6,
            "actualizadaEnLocal" to 99_000L,
            "anuncioPublico" to "viejo"
        )
        val serverCheckpoint = mapOf<String, Any?>(
            "phaseIndex" to 6,
            "actualizadaEnLocal" to 1L,
            "anuncioPublico" to "actual"
        )

        assertEquals(
            serverCheckpoint,
            OnlineAuthoritativeStateStore.freshestForRecovery(
                legacyRoomState,
                serverCheckpoint
            )
        )
    }
}
