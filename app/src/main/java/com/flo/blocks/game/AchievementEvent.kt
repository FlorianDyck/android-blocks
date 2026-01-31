package com.flo.blocks.game

data class AchievementEvent(
        val brick: ColoredBrick,
        val cleared: Int,
        val isNewRecord: Boolean,
        val blockRemoved: Boolean,
        val achievementFlags: AchievementFlags,
        val isBestMove: Boolean = false
)
