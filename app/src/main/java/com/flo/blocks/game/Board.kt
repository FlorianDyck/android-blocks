package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset

data class Board(val width: Int, val height: Int, val board: BooleanArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Board

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

    fun canPlace(brick: Brick): Boolean = brick.offsetsWithin(width, height).any { canPlace(it) }
    fun canPlace(brick: OffsetBrick): Boolean {
        return brick.onBoard(width, height) && brick.positionList().all { !board[it.x + it.y * width] }
    }

    private fun differentBlocksAround(index: Int): Int {
        val isSet = board[index]
        fun isDifferent(delta: Int) = (board[index + delta] != isSet)

        var result = 0
        if (if (index >= width) isDifferent(-width) else !isSet) result += 1
        if (if ((index % width) != 0) isDifferent(-1) else !isSet) result += 1
        if (if ((index % width) != width - 1) isDifferent(1) else !isSet) result += 1
        if (if (index < board.size - width) isDifferent(width) else !isSet) result += 1
        return result
    }

    fun evaluate(): Float {
        val freeBlocks = board.count { !it }
        val blockGrades = IntArray(5)
        val freedomGrades = IntArray(5)
        val borderLength = board.mapIndexed { index, color ->
            if (color) {
                blockGrades[differentBlocksAround(index)] += 1
                0
            } else {
                val result = differentBlocksAround(index)
                freedomGrades[result] += 1
                result
            }
        }.sum()
        var score =
            1f * (3 * freeBlocks - 2 * borderLength - freedomGrades[4] * 20 - freedomGrades[3] * 3 - blockGrades[4] * 2 - blockGrades[3])
//        if (score > movesScore) {
//            Log.i("evaluate", "${currentMove.value}: $score: free: $freeBlocks, border: $borderLength, freedoms: ${freedomGrades.map { "$it" }.reduce {a, b -> "$a$b"}}")
//        }
        if (canPlace(rect(0, 0, 2, 2))) score += 10
        if (canPlace(rect(0, 0, 4, 0))) score += 5
        if (canPlace(rect(0, 0, 0, 4))) score += 5
        return score
    }

    private val rowIndices = 0 until width
    private val lineIndices = 0 until height

    operator fun get(x: Int, y: Int) = board[x + y * width]
    operator fun get(offset: IntOffset) = get(offset.x, offset.y)

    operator fun set(x: Int, y: Int, value: Boolean) = board.set(x + y * width, value)
    operator fun set(offset: IntOffset, value: Boolean) = set(offset.x, offset.y, value)

    fun place(hovering: OffsetBrick): Pair<Board, Int> {
        val result = Board(width, height, board.clone())
        for (position in hovering.positionList()) result[position] = true

        val lines = hovering.lines().filter { line -> lineIndices.all { row -> result[row, line] } }
        val rows = hovering.rows().filter { row -> rowIndices.all { line -> result[row, line] } }

        for (line in lines) for (row in rowIndices) result[row, line] = false
        for (row in rows) for (line in lineIndices) result[row, line] = false

        return Pair(result, lines.size + rows.size)
    }
}


