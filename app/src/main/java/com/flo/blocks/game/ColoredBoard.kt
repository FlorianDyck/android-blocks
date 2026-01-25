package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset

data class PlacementResult(
    val board: ColoredBoard,
    val cleared: Int,
    val cellsCleared: Int,
    val blockRemoved: Boolean,
    val clearedRowIndices: List<Int>,
    val clearedColIndices: List<Int>
)

data class ColoredBoard(val width: Int, val height: Int, val board: Array<BlockColor>) {

    constructor(
        width: Int, height: Int
    ) : this(width, height, Array(width * height) { BlockColor.BACKGROUND })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColoredBoard

        if (width != other.width) return false
        if (height != other.height) return false
        if (!board.contentEquals(other.board)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + board.contentHashCode()
        return result
    }

    fun canPlace(brick: Brick) = brick.offsetsWithin(width, height).any { canPlace(it) }

    fun canPlace(brick: OffsetBrick): Boolean {
        return brick.onBoard(width, height) && brick.positionList()
            .all { board[it.x + it.y * width].free() }
    }

    private val rowIndices = 0 until width
    private val lineIndices = 0 until height

    operator fun get(x: Int, y: Int) = board[x + y * width]
    operator fun get(offset: IntOffset) = get(offset.x, offset.y)

    operator fun set(x: Int, y: Int, value: BlockColor) = board.set(x + y * width, value)
    operator fun set(offset: IntOffset, value: BlockColor) = set(offset.x, offset.y, value)

    fun board(): Board {
        return Board(width, height, BooleanArray(width * height) { board[it].used() })
    }

    fun place(hovering: ColoredOffsetBrick): PlacementResult {
        val result = ColoredBoard(width, height, board.clone())
        val positions = hovering.brick.positionList()
        for (position in positions) result[position] = hovering.color

        val lines = hovering.brick.lines().filter { line ->
            rowIndices.all { row -> result[row, line].used() }
        }
        val rows = hovering.brick.rows().filter { row ->
            lineIndices.all { line -> result[row, line].used() }
        }

        for (line in lines) for (row in rowIndices) result[row, line] = BlockColor.BACKGROUND
        for (row in rows) for (line in lineIndices) result[row, line] = BlockColor.BACKGROUND

        val blockRemoved = positions.all { it.y in lines || it.x in rows }
        val r = lines.size
        val c = rows.size
        val cellsCleared = r * width + c * height - r * c
        return PlacementResult(result, r + c, cellsCleared, blockRemoved, lines, rows)
    }
}
