package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineFirestoreUsageCounterTest {

    @Test
    fun ignoresCacheAndPendingSnapshotsAndCountsEmptyInitialQueryOnce() {
        val counter = OnlineFirestoreUsageCounter()
        counter.listenerStarted("actions")
        counter.serverSnapshot("actions", true, false, 0, 0, 1)
        counter.serverSnapshot("actions", false, true, 1, 1, 1)
        counter.serverSnapshot("actions", false, false, 0, 0, 1)
        counter.serverSnapshot("actions", false, false, 0, 0, 1)

        assertEquals(
            "actions[start=1,snap=2,docs=1,rules=1,forced=0,writes=0]",
            counter.summary()
        )
    }

    @Test
    fun separatesVisibleRuleAndForcedReads() {
        val counter = OnlineFirestoreUsageCounter()
        counter.listenerStarted("players")
        counter.serverSnapshot("players", false, false, 14, 14, dependentDocuments = 1)
        counter.serverSnapshot("players", false, false, 1, 14, dependentDocuments = 1)
        counter.forcedQuery("start_preflight", 14, dependentDocuments = 1)
        counter.write("presence")

        assertEquals(
            "players[start=1,snap=2,docs=15,rules=2,forced=0,writes=0];" +
                "start_preflight[start=0,snap=0,docs=0,rules=1,forced=14,writes=0];" +
                "presence[start=0,snap=0,docs=0,rules=0,forced=0,writes=1]",
            counter.summary()
        )
    }
}
