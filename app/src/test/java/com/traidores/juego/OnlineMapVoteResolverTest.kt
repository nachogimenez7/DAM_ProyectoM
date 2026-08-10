package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMapVoteResolverTest {
    @Test
    fun `unique live leader changes the map shown in the lobby`() {
        val votes = listOf(
            OnlineMapVote("one", "A", "medieval"),
            OnlineMapVote("two", "B", "medieval"),
            OnlineMapVote("three", "C", "pampa")
        )

        assertEquals("medieval", OnlineMapVoteResolver.liveLobbyMapKey(votes, "pampa"))
    }

    @Test
    fun `live tie keeps the current room map`() {
        val votes = listOf(
            OnlineMapVote("one", "A", "medieval"),
            OnlineMapVote("two", "B", "pampa")
        )

        assertEquals("grecia", OnlineMapVoteResolver.liveLobbyMapKey(votes, "grecia"))
    }

    @Test
    fun `no votes keeps current room map`() {
        val resolution = OnlineMapVoteResolver.resolveAtStart(emptyList(), "grecia")

        assertEquals(OnlineMapResolution.Selected("grecia"), resolution)
    }

    @Test
    fun `unique leader wins even when host voted another map`() {
        val votes = listOf(
            OnlineMapVote("host", "H", "pampa"),
            OnlineMapVote("two", "D", "medieval"),
            OnlineMapVote("three", "T", "medieval")
        )

        assertEquals(
            OnlineMapResolution.Selected("medieval"),
            OnlineMapVoteResolver.resolveAtStart(votes, "pampa")
        )
    }

    @Test
    fun `tie requires host choice among tied maps`() {
        val votes = listOf(
            OnlineMapVote("host", "H", "pampa"),
            OnlineMapVote("two", "D", "medieval")
        )

        val unresolved = OnlineMapVoteResolver.resolveAtStart(votes, "grecia")
        assertTrue(unresolved is OnlineMapResolution.HostTieBreakRequired)
        assertEquals(
            OnlineMapResolution.Selected("medieval"),
            OnlineMapVoteResolver.resolveAtStart(votes, "grecia", "medieval")
        )
    }
}
