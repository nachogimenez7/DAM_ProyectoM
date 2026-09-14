package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class OnlineActionIdentityTest {
    @Test fun voteIdentityIsUniquePerActorMatchRoundAndPhaseAndStableOnRetry() {
        val ids = (0..4).flatMap { actor -> (1..3).flatMap { round -> (4..5).map { phase ->
            OnlineActionIdentity.voteDocumentId(2, "match-123", "uid-$actor", round, phase)
        } } }
        assertEquals(30, ids.toSet().size)
        assertEquals("match-123_uid-0_r1_p4_votar_s1", ids.first())
        assertEquals(ids.first(), OnlineActionIdentity.voteDocumentId(2, "match-123", "uid-0", 1, 4))
        assertNotEquals(ids.first(), OnlineActionIdentity.voteDocumentId(2, "match-456", "uid-0", 1, 4))
    }
    @Test fun legacyHostKeepsExistingVoteDocumentOnUpdatedGuest() {
        assertEquals(OnlineActionIdentity.documentId("match-123", "uid-0", 1, 4, "votar"),
            OnlineActionIdentity.voteDocumentId(1, "match-123", "uid-0", 1, 4))
    }
}
