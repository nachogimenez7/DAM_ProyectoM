package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayChatLayoutTest {
    @Test
    fun everyTableSizeUsesTheSameAvailableCentralColumn() {
        listOf(5, 9, 13, 15).forEach { playerCount ->
            assertEquals(680, GameplayChatLayout.ambientMaxHeightDp(playerCount))
            assertEquals(
                680,
                GameplayChatLayout.expandedMaxHeightDp(playerCount, keyboardVisible = false)
            )
        }
    }

    @Test
    fun keyboardCanUseTheAvailableConversationHeight() {
        assertEquals(680, GameplayChatLayout.expandedMaxHeightDp(15, keyboardVisible = true))
    }
}
