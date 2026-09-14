package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePresencePublisherTest {
    private class Harness {
        val registrations = mutableListOf<(Exception?) -> Unit>()
        val writes = mutableListOf<Pair<Boolean, (Exception?) -> Unit>>()
        val errors = mutableListOf<Exception>()
        var ready = 0
        var unavailable = 0
        val publisher = OnlinePresencePublisher(
            registerDisconnect = { registrations += it },
            writePresence = { connected, callback -> writes += connected to callback },
            onReady = { ready += 1 },
            onUnavailable = { unavailable += 1 },
            onError = { errors += it }
        )
    }

    @Test
    fun onlineIsPublishedOnlyAfterServerArmsDisconnect() {
        val h = Harness()
        h.publisher.publishOnline()
        assertTrue(h.writes.isEmpty())
        h.registrations.single()(null)
        assertEquals(listOf(true), h.writes.map { it.first })
        assertEquals(0, h.ready)
        h.writes.single().second(null)
        assertEquals(1, h.ready)
    }

    @Test
    fun backgroundDuringRegistrationNeverPublishesOnlineFromOldCallback() {
        val h = Harness()
        h.publisher.publishOnline()
        h.publisher.invalidate()
        h.publisher.publishOffline(reportFailure = true)
        h.registrations.single()(null)
        assertEquals(listOf(false), h.writes.map { it.first })
        assertEquals(0, h.ready)
    }

    @Test
    fun immediateReconnectCannotBeOverwrittenByLateOfflineCompletion() {
        val h = Harness()
        h.publisher.publishOffline(reportFailure = true)
        h.publisher.publishOnline()
        h.registrations.single()(null)
        h.writes[1].second(null)
        h.writes[0].second(IllegalStateException("old offline failure"))
        assertEquals(listOf(false, true), h.writes.map { it.first })
        assertEquals(1, h.ready)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun lostSocketThenReconnectIgnoresObsoleteRegistrationAndErrors() {
        val h = Harness()
        h.publisher.publishOnline()
        h.publisher.invalidate()
        h.publisher.publishOnline()
        h.registrations[1](null)
        h.registrations[0](IllegalStateException("old socket"))
        h.writes.single().second(null)
        assertEquals(1, h.ready)
        assertEquals(1, h.unavailable)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun revokedMembershipSuppressesAlreadyPendingOnlineAcknowledgement() {
        val h = Harness()
        h.publisher.publishOnline()
        h.registrations.single()(null)
        h.publisher.invalidate()
        h.writes.single().second(null)
        assertEquals(0, h.ready)
        assertEquals(1, h.unavailable)
    }

    @Test
    fun currentRegistrationFailureKeepsAccessUnavailableAndDoesNotWriteOnline() {
        val h = Harness()
        val failure = IllegalStateException("permission denied")
        h.publisher.publishOnline()
        h.registrations.single()(failure)
        assertTrue(h.writes.isEmpty())
        assertEquals(listOf(failure), h.errors)
        assertEquals(1, h.unavailable)
    }

    @Test
    fun activityStopDoesNotDeliverOfflineFailureToDestroyedUi() {
        val h = Harness()
        h.publisher.invalidate()
        h.publisher.publishOffline(reportFailure = false)
        h.writes.single().second(IllegalStateException("connection gone"))
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun repeatedReconnectsAcceptOnlyLatestConnectionAcknowledgement() {
        val h = Harness()
        repeat(100) {
            h.publisher.invalidate()
            h.publisher.publishOnline()
        }
        h.registrations.reversed().forEach { it(null) }
        assertEquals(1, h.writes.size)
        h.writes.single().second(null)
        assertEquals(1, h.ready)
        assertTrue(h.errors.isEmpty())
    }
}
