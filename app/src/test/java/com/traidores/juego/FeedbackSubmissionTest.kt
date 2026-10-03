package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSubmissionTest {
    @Test fun longContextFitsExistingServerLimitWithoutLosingDescription() {
        val description = "a".repeat(600)
        val message = FeedbackSubmission.message(description, "b".repeat(2000))
        assertEquals(1200, message.length)
        assertTrue(message.startsWith(description + "\n\nResumen de la partida:\n"))
    }

    @Test fun generalFeedbackDoesNotAttachAnOldMatch() {
        assertEquals("Mi sugerencia", FeedbackSubmission.message("Mi sugerencia", ""))
    }
}
