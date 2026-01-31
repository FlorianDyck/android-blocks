package com.flo.blocks.game

data class AchievementEvent(
        val brick: ColoredBrick,
        val cleared: Int,
        val isNewRecord: Boolean,
        val blockRemoved: Boolean,
        val isMinimalist: Boolean,
        val aroundTheCorner: Boolean,
        val largeCorner: Boolean,
        val hugeCorner: Boolean,
        val wideCorner: Boolean,
        val notEvenAround: Boolean,
        val largeWideCorner: Boolean,
        val isBestMove: Boolean = false
)
