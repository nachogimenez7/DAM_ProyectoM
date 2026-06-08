package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTableLayoutTest {

    @Test
    fun companionSlotsKeepCenterAndChatAreaFreeForCorePlayerCounts() {
        val tableWidth = 2100
        val tableHeight = 580
        val centerStage = TableRect(left = 720, top = 100, width = 660, height = 360)
        val chatArea = TableRect(left = 1840, top = 426, width = 260, height = 154)

        listOf(5, 7, 8).forEach { totalPlayers ->
            val companionCount = totalPlayers - 1
            val cardWidth = when {
                totalPlayers <= 6 -> 70
                totalPlayers <= 8 -> 64
                else -> 58
            }
            val cardHeight = when {
                totalPlayers <= 6 -> 116
                totalPlayers <= 8 -> 106
                else -> 96
            }

            val slots = GameTableLayout.companionSlots(
                companionCount = companionCount,
                tableWidth = tableWidth,
                tableHeight = tableHeight,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                edgeInset = 10,
                verticalInset = 6,
                reservedRightBottomHeight = 154
            )

            assertEquals(companionCount, slots.size)
            slots.forEach { slot ->
                assertFalse("slot $slot overlaps center for $totalPlayers players", slot.bounds.intersects(centerStage))
                assertFalse("slot $slot overlaps chat for $totalPlayers players", slot.bounds.intersects(chatArea))
            }
        }
    }

    @Test
    fun companionSlotsSplitPlayersAcrossBothSides() {
        val slots = GameTableLayout.companionSlots(
            companionCount = 7,
            tableWidth = 2100,
            tableHeight = 580,
            cardWidth = 64,
            cardHeight = 106,
            edgeInset = 10,
            verticalInset = 6,
            reservedRightBottomHeight = 154
        )

        assertEquals(4, slots.count { it.side == TableSide.LEFT })
        assertEquals(3, slots.count { it.side == TableSide.RIGHT })
        assertTrue(slots.map { it.companionIndex }.containsAll((0..6).toList()))
    }
}
