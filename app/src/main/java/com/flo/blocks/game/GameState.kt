package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset
import java.util.Random


inline fun <reified T> randArray(size: Int, values: List<T>) =
    Array(size) { values[Random().nextInt(values.size)] }

data class GameState(
    var board: ColoredBoard = ColoredBoard(8, 8),
    var bricks: Array<Brick> = randArray(3, BRICKS),
    var colors: Array<BlockColor> = randArray(3, BLOCK_COLORS),
    var score: Int = 0
) {
    fun lost() = (0..2).all { colors[it].free() || !board.canPlace(bricks[it]) }

    fun place(index: Int, position: IntOffset): GameState {
        val (newBoard, cleared) = board.place(OffsetBrick(position, bricks[index]), colors[index])
        var newColors = colors.clone()
        newColors[index] = BlockColor.INVISIBLE
        val newBricks = if (newColors.all { it.free() }) {
            newColors = randArray(3, BLOCK_COLORS)
            randArray(3, BRICKS)
        } else {
            bricks
        }
        return GameState(
            newBoard,
            newBricks,
            newColors,
            score + cleared
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (board != other.board) return false
        if (!bricks.contentEquals(other.bricks)) return false
        if (!colors.contentEquals(other.colors)) return false
        if (score != other.score) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.hashCode()
        result = 31 * result + bricks.hashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + score
        return result
    }
}