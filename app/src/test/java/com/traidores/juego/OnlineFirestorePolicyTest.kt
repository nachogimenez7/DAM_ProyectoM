package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineFirestorePolicyTest {

    @Test
    fun emptyCacheOnUnlockDoesNotCloseTheLobby() {
        assertFalse(OnlineFirestorePolicy.isConfirmedMissingRoom(false, true, false))
        assertFalse(OnlineFirestorePolicy.isConfirmedMissingRoom(false, true, true))
    }

    @Test
    fun localPendingDeletionDoesNotCloseTheLobbyBeforeServerConfirmation() {
        assertFalse(OnlineFirestorePolicy.isConfirmedMissingRoom(false, false, true))
    }

    @Test
    fun confirmedServerDeletionClosesTheLobbyButAnExistingRoomDoesNot() {
        assertTrue(OnlineFirestorePolicy.isConfirmedMissingRoom(false, false, false))
        assertFalse(OnlineFirestorePolicy.isConfirmedMissingRoom(true, false, false))
        assertFalse(OnlineFirestorePolicy.isConfirmedMissingRoom(true, true, false))
    }

    @Test
    fun onlyPoliceListensForPrivateInvestigationClue() {
        assertTrue(OnlineFirestorePolicy.shouldListenForPrivateClue(RoleCatalog.POLICIA))
        assertFalse(OnlineFirestorePolicy.shouldListenForPrivateClue(RoleCatalog.MEDICO))
        assertFalse(OnlineFirestorePolicy.shouldListenForPrivateClue(null))
    }

    @Test
    fun onlyCurrentHostMirrorsLegacyPresenceToFirestore() {
        assertTrue(OnlineFirestorePolicy.shouldMirrorLegacyPresence(isHost = true))
        assertFalse(OnlineFirestorePolicy.shouldMirrorLegacyPresence(isHost = false))
    }

    @Test
    fun hostRefreshesConnectedLeaseEveryThirtySeconds() {
        assertFalse(
            OnlineFirestorePolicy.shouldWriteLegacyHostPresence(
                isHost = true,
                state = RealtimeRoomPresence.STATE_CONNECTED,
                lastState = RealtimeRoomPresence.STATE_CONNECTED,
                nowElapsedMs = 29_999L,
                lastWriteElapsedMs = 0L
            )
        )
        assertTrue(
            OnlineFirestorePolicy.shouldWriteLegacyHostPresence(
                isHost = true,
                state = RealtimeRoomPresence.STATE_CONNECTED,
                lastState = RealtimeRoomPresence.STATE_CONNECTED,
                nowElapsedMs = 30_000L,
                lastWriteElapsedMs = 0L
            )
        )
    }

    @Test
    fun stateChangeWritesImmediatelyAndGuestsNeverMirrorLease() {
        assertTrue(
            OnlineFirestorePolicy.shouldWriteLegacyHostPresence(
                isHost = true,
                state = RealtimeRoomPresence.STATE_DISCONNECTED,
                lastState = RealtimeRoomPresence.STATE_CONNECTED,
                nowElapsedMs = 1L,
                lastWriteElapsedMs = 0L
            )
        )
        assertFalse(
            OnlineFirestorePolicy.shouldWriteLegacyHostPresence(
                isHost = false,
                state = RealtimeRoomPresence.STATE_CONNECTED,
                lastState = "",
                nowElapsedMs = 60_000L,
                lastWriteElapsedMs = 0L
            )
        )
    }
}
