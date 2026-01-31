package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset

data class AchievementFlags(
        val isMinimalist: Boolean,
        val isAroundTheCorner: Boolean,
        val isLargeCorner: Boolean,
        val isHugeCorner: Boolean,
        val isWideCorner: Boolean,
        val isNotEvenAround: Boolean,
        val isLargeWideCorner: Boolean
) {
    companion object {
        fun calculate(
            brick: Brick,
            position: IntOffset,
            clearedRowIndices: List<Int>,
            clearedColIndices: List<Int>,
            cellsCleared: Int,
            blockRemoved: Boolean,
            linesCleared: Int,
            boardWidth: Int,
            boardHeight: Int
        ): AchievementFlags {
            val minCells = brick.minCellsToClear(boardWidth, boardHeight)
            val isMinimalist = blockRemoved && cellsCleared == minCells

            var isAroundTheCorner = false
            var isLargeCorner = false
            var isHugeCorner = false
            var isWideCorner = false
            var isNotEvenAround = false
            var isLargeWideCorner = false

            if (clearedRowIndices.isNotEmpty() && clearedColIndices.isNotEmpty()) {
                val intersections =
                    clearedRowIndices.flatMap { y ->
                        clearedColIndices.map { x -> IntOffset(x, y) }
                    }
                val brickPositions = brick.positionList().map { it + position }.toSet()

                if (intersections.none { it in brickPositions }) {
                    isAroundTheCorner = true

                    val neighbors =
                        intersections
                            .flatMap { intersect ->
                                listOf(
                                    IntOffset(intersect.x - 1, intersect.y),
                                    IntOffset(intersect.x + 1, intersect.y),
                                    IntOffset(intersect.x, intersect.y - 1),
                                    IntOffset(intersect.x, intersect.y + 1)
                                )
                            }
                            .toSet()

                    val neighborCount = neighbors.count { it in brickPositions }

                    if (linesCleared >= 3) isLargeCorner = true
                    if (linesCleared >= 4) isHugeCorner = true
                    if (neighborCount == 1) isWideCorner = true
                    if (neighborCount == 0) isNotEvenAround = true
                    if (linesCleared >= 3 && neighborCount == 1) isLargeWideCorner = true
                }
            }

            return AchievementFlags(
                isMinimalist,
                isAroundTheCorner,
                isLargeCorner,
                isHugeCorner,
                isWideCorner,
                isNotEvenAround,
                isLargeWideCorner
            )
        }
    }
}
