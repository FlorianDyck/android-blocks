package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset

data class ColoredBoard(val width: Int, val height: Int, val board: Array<BlockColor>) {

    constructor(width: Int, height: Int) : this(width, height, Array(width * height) { BlockColor.BACKGROUND })

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
        return brick.onBoard(width, height) && brick.positionList().all { board[it.x + it.y * width].free() }
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

    fun place(hovering: OffsetBrick, color: BlockColor): Pair<ColoredBoard, Int> {
        val result = ColoredBoard(width, height, board.clone())
        for (position in hovering.positionList()) result[position] = color

        val lines = hovering.lines().filter { line -> lineIndices.all { row -> result[row, line].used() } }
        val rows = hovering.rows().filter { row -> rowIndices.all { line -> result[row, line].used() } }

        for (line in lines) for (row in rowIndices) result[row, line] = BlockColor.BACKGROUND
        for (row in rows) for (line in lineIndices) result[row, line] = BlockColor.BACKGROUND

        return Pair(result, lines.size + rows.size)
    }
}