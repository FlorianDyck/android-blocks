package com.flo.blocks


const val BOARD_SIZE = 8
val BOARD_INDICES = (0 until BOARD_SIZE)

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

    fun canPlace(brick: Brick): Boolean = brick.possiblyPlaceablePositions().any { canPlace(it) }
    fun canPlace(brick: OffsetBrick): Boolean {
        return brick.onBoard(width, height) && brick.positionList().all { !board[it.x + it.y * BOARD_SIZE] }
    }

    private fun differentBlocksAround(index: Int): Int {
        val isSet = board[index]
        fun isDifferent(delta: Int) = (board[index + delta] != isSet)

        var result = 0
        if (if (index >= BOARD_SIZE) isDifferent(-BOARD_SIZE) else !isSet) result += 1
        if (if ((index % BOARD_SIZE) != 0) isDifferent(-1) else !isSet) result += 1
        if (if ((index % BOARD_SIZE) != BOARD_SIZE - 1) isDifferent(1) else !isSet) result += 1
        if (if (index < board.size - BOARD_SIZE) isDifferent(BOARD_SIZE) else !isSet) result += 1
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

    fun place(hovering: OffsetBrick): Int {
        for (position in hovering.positionList()) board[position.x + position.y * BOARD_SIZE] = true

        val lines = hovering.lines().filter { line -> BOARD_INDICES.all { row -> board[row + BOARD_SIZE * line] } }
        val rows = hovering.rows().filter { row -> BOARD_INDICES.all { line -> board[row + BOARD_SIZE * line] } }

        for (line in lines) for (row in BOARD_INDICES) board[row + BOARD_SIZE * line] = false
        for (row in rows) for (line in BOARD_INDICES) board[row + BOARD_SIZE * line] = false

        return lines.size + rows.size
    }

    fun clone() = Board(width, height, board.clone())
}


