package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset


data class OffsetBrick(val offset: IntOffset, val brick: Brick) {
    private fun getXMin() = offset.x
    private fun getXMax() = offset.x + brick.width - 1
    private fun getYMin() = offset.y
    private fun getYMax() = offset.y + brick.height - 1
    fun onBoard(width: Int, height: Int): Boolean {
        return 0 <= offset.x && offset.x + brick.width <= width &&
                0 <= offset.y && offset.y + brick.height <= height
    }

    fun positionList(): List<IntOffset> {
        return brick.positionList().map { it + offset }
    }

    fun getPosition(x: Int, y: Int): Boolean {
        return brick.getPosition(x - offset.x, y - offset.y)
    }

    fun rows(): IntRange = getXMin()..getXMax()
    fun lines(): IntRange = getYMin()..getYMax()
}