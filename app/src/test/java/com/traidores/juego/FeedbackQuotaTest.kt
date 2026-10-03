package com.traidores.juego

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FeedbackQuotaTest {
    private val now = 200_000_000L

    @Test fun newAccountCanSend() {
        assertNull(FeedbackQuota.rejection(now, null, null, 0))
    }

    @Test fun fiveMinuteBoundaryAllowsNextReport() {
        assertNotNull(FeedbackQuota.rejection(now, now - 1000, now - FeedbackQuota.INTERVAL_MS + 1, 1))
        assertNull(FeedbackQuota.rejection(now, now - 1000, now - FeedbackQuota.INTERVAL_MS, 1))
    }

    @Test fun thirdReportExhaustsWindowUntilTwentyFourHours() {
        assertNotNull(FeedbackQuota.rejection(now, now - FeedbackQuota.WINDOW_MS + 1, now - FeedbackQuota.INTERVAL_MS, 3))
        assertNull(FeedbackQuota.rejection(now, now - FeedbackQuota.WINDOW_MS, now - FeedbackQuota.INTERVAL_MS, 3))
    }

    @Test fun changingClockBackwardsDoesNotAllowAnImmediateRetry() {
        assertNotNull(FeedbackQuota.rejection(now, now, now + 1000, 1))
    }
}
