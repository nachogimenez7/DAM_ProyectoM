package com.traidores.juego

data class TableRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height

    fun intersects(other: TableRect): Boolean {
        return left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
    }
}

data class PlayerCardSlot(
    val companionIndex: Int,
    val side: TableSide,
    val bounds: TableRect
)

enum class TableSide {
    LEFT,
    RIGHT
}

object GameTableLayout {

    fun companionSlots(
        companionCount: Int,
        tableWidth: Int,
        tableHeight: Int,
        cardWidth: Int,
        cardHeight: Int,
        edgeInset: Int,
        verticalInset: Int,
        reservedRightBottomHeight: Int
    ): List<PlayerCardSlot> {
        if (companionCount <= 0 || tableWidth <= 0 || tableHeight <= 0) return emptyList()

        val leftCount = (companionCount + 1) / 2
        val rightCount = companionCount - leftCount
        val leftX = edgeInset
        val rightX = (tableWidth - edgeInset - cardWidth).coerceAtLeast(edgeInset)

        return columnSlots(
            count = leftCount,
            startIndex = 0,
            side = TableSide.LEFT,
            x = leftX,
            tableHeight = tableHeight,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            verticalInset = verticalInset,
            reservedBottomHeight = 0
        ) + columnSlots(
            count = rightCount,
            startIndex = leftCount,
            side = TableSide.RIGHT,
            x = rightX,
            tableHeight = tableHeight,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            verticalInset = verticalInset,
            reservedBottomHeight = reservedRightBottomHeight
        )
    }

    private fun columnSlots(
        count: Int,
        startIndex: Int,
        side: TableSide,
        x: Int,
        tableHeight: Int,
        cardWidth: Int,
        cardHeight: Int,
        verticalInset: Int,
        reservedBottomHeight: Int
    ): List<PlayerCardSlot> {
        if (count <= 0) return emptyList()

        val usableHeight = (tableHeight - reservedBottomHeight - verticalInset * 2).coerceAtLeast(cardHeight)
        val gap = if (count == 1) {
            0
        } else {
            ((usableHeight - cardHeight) / (count - 1)).coerceAtLeast(0)
        }
        val singleTop = verticalInset + ((usableHeight - cardHeight) / 2).coerceAtLeast(0)

        return List(count) { index ->
            val top = if (count == 1) singleTop else verticalInset + gap * index
            PlayerCardSlot(
                companionIndex = startIndex + index,
                side = side,
                bounds = TableRect(
                    left = x,
                    top = top.coerceAtLeast(verticalInset),
                    width = cardWidth,
                    height = cardHeight
                )
            )
        }
    }
}
