package com.traidores.juego

import org.junit.Assert.*
import org.junit.Test

class ServerReactionCursorTest {
    private fun reaction(ts: Long, actor: String = "a", slot: Int = 0, match: String = "m") =
        ServerReaction(actor, match, 5, slot, "griego_contento", ts)
    @Test fun reconnectDoesNotReplayTheRingOrAnotherMatch() {
        val cursor = ServerReactionCursor("m", 1000)
        assertFalse(cursor.accept(reaction(999)))
        assertFalse(cursor.accept(reaction(1000)))
        assertFalse(cursor.accept(reaction(1001, match = "previous")))
        assertTrue(cursor.accept(reaction(1001)))
        assertFalse(cursor.accept(reaction(1001)))
        assertFalse(cursor.accept(reaction(1004))) // optimistic server-timestamp correction
        assertTrue(cursor.accept(reaction(1001, actor = "b")))
        assertTrue(cursor.accept(reaction(11001))) // overwritten same slot is a new emote
        assertFalse(ServerReactionCursor("m", 12000).accept(reaction(11001)))
    }
}
