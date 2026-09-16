package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRoomRetentionPolicyTest {
    private val now = 10L * OnlineRoomRetentionPolicy.STALE_AFTER_MS

    @Test
    fun roomBecomesStaleAtTwentyFourHours() {
        assertFalse(
            OnlineRoomRetentionPolicy.isStale(
                now - OnlineRoomRetentionPolicy.STALE_AFTER_MS + 1L,
                now
            )
        )
        assertTrue(
            OnlineRoomRetentionPolicy.isStale(
                now - OnlineRoomRetentionPolicy.STALE_AFTER_MS,
                now
            )
        )
    }

    @Test
    fun invalidOrFutureTimestampsAreNeverDeleted() {
        assertFalse(OnlineRoomRetentionPolicy.isStale(0L, now))
        assertFalse(OnlineRoomRetentionPolicy.isStale(now + 1L, now))
    }

    @Test
    fun discoveryHidesFullStaleAndDeletingRooms() {
        fun visible(players: Int = 2, updatedAt: Long = now, deleting: Boolean = false) =
            OnlineRoomRetentionPolicy.isDiscoverable(updatedAt, now, players, 4, deleting)
        assertTrue(visible())
        assertFalse(visible(players = 4))
        assertFalse(visible(players = 5))
        assertFalse(visible(players = -1))
        assertFalse(visible(deleting = true))
        assertFalse(visible(updatedAt = 0L))
        assertTrue(visible(updatedAt = now + 1L))
        assertTrue(visible(updatedAt = now + 60_000L))
        assertTrue(visible(updatedAt = now - OnlineRoomRetentionPolicy.BROWSER_FRESH_FOR_MS))
        assertFalse(visible(updatedAt = now - OnlineRoomRetentionPolicy.BROWSER_FRESH_FOR_MS - 1L))
    }

    @Test
    fun anotherPlayerJoiningDoesNotHideRoomWhenServerClockIsAhead() {
        for (players in 1..4) {
            assertTrue(OnlineRoomRetentionPolicy.isDiscoverable(now + 2_000L, now, players, 5, false))
        }
        assertFalse(OnlineRoomRetentionPolicy.isDiscoverable(now + 2_000L, now, 5, 5, false))
    }

    @Test
    fun fullRoomRemainsVisibleOnlyToItsReturningMember() {
        assertTrue(
            OnlineRoomRetentionPolicy.isDiscoverable(
                updatedAtMs = now,
                nowMs = now,
                currentPlayers = 5,
                playerLimit = 5,
                deleting = false,
                allowFullForReturningMember = true
            )
        )
        assertFalse(
            OnlineRoomRetentionPolicy.isDiscoverable(
                updatedAtMs = now,
                nowMs = now,
                currentPlayers = 6,
                playerLimit = 5,
                deleting = false,
                allowFullForReturningMember = true
            )
        )
    }

    @Test
    fun migratedHostSupersedesCreatorAndCachedPreference() {
        assertFalse(OnlineRoomRecovery.isCurrentHost("creator", "new-host", "creator"))
        assertTrue(OnlineRoomRecovery.isCurrentHost("new-host", "new-host", "creator"))
        assertTrue(OnlineRoomRecovery.isCurrentHost("creator", null, "creator"))
        assertTrue(OnlineRoomRecovery.isCurrentHost("creator", "", "creator"))
        assertFalse(OnlineRoomRecovery.isCurrentHost("", null, ""))
        assertFalse(OnlineRoomRecovery.isCurrentHost("intruder", "new-host", "creator"))
    }

    @Test
    fun recoveryHidesOldWaitingRoomButKeepsAnActiveMatch() {
        val old = now - OnlineRoomRetentionPolicy.BROWSER_FRESH_FOR_MS - 1L
        assertFalse(
            OnlineRoomRetentionPolicy.isRecoveryAvailable(
                OnlineRoomFirestore.STATE_WAITING, old, now
            )
        )
        assertTrue(
            OnlineRoomRetentionPolicy.isRecoveryAvailable(
                OnlineRoomFirestore.STATE_IN_GAME, old, now
            )
        )
        assertTrue(
            OnlineRoomRetentionPolicy.isRecoveryAvailable(
                OnlineRoomFirestore.STATE_WAITING, now + 2_000L, now
            )
        )
    }
}
