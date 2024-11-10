package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset


data class GameState(
    var board: ColoredBoard = ColoredBoard(8, 8),
    var bricks: Array<ColoredBrick?> = Array(3) { randomBrick() },
    var score: Int = 0
) {
    fun lost() = (0..2).all { bricks[it] == null || !board.canPlace(bricks[it]!!.brick) }

    fun place(index: Int, position: IntOffset): GameState {
        val (newBoard, cleared) = board.place(bricks[index]!!.offset(position))
        var newBricks = bricks.clone()
        newBricks[index] = null
        if (newBricks.all { it == null }) {
            newBricks = Array(3) { randomBrick() }
        }
        return GameState(
            newBoard,
            newBricks,
            score + cleared
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (board != other.board) return false
        if (!bricks.contentEquals(other.bricks)) return false
        if (score != other.score) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.hashCode()
        result = 31 * result + bricks.hashCode()
        result = 31 * result + score
        return result
    }
}