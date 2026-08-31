package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineFirestorePolicyTest {

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
}
