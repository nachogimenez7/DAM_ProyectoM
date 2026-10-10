package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialAdPolicyTest {
    private fun after(matches: Int, from: InterstitialAdPolicy.State = InterstitialAdPolicy.State()) =
        (1..matches).fold(from) { state, _ -> InterstitialAdPolicy.afterMatch(state) }

    @Test fun firstTwoMatchesNeverShowAnAd() {
        assertFalse(InterstitialAdPolicy.isDue(after(2), nowMs = 10_000_000L, adFree = false))
        assertTrue(InterstitialAdPolicy.isDue(after(3), nowMs = 10_000_000L, adFree = false))
    }

    @Test fun oneAdEveryTwoMatches() {
        val shown = InterstitialAdPolicy.afterAd(after(4), nowMs = 0L)
        val later = 10 * 60_000L
        assertFalse(InterstitialAdPolicy.isDue(after(1, shown), later, adFree = false))
        assertTrue(InterstitialAdPolicy.isDue(after(2, shown), later, adFree = false))
    }

    @Test fun keepsFourMinutesBetweenAds() {
        val shown = InterstitialAdPolicy.afterAd(after(4), nowMs = 0L)
        val twoMore = after(2, shown)
        assertFalse(InterstitialAdPolicy.isDue(twoMore, nowMs = 3 * 60_000L, adFree = false))
        assertTrue(InterstitialAdPolicy.isDue(twoMore, nowMs = 4 * 60_000L, adFree = false))
    }

    @Test fun adFreeAccountsNeverSeeAds() {
        assertFalse(InterstitialAdPolicy.isDue(after(10), nowMs = 10_000_000L, adFree = true))
    }
}
