package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMapVoteResolverTest {
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
