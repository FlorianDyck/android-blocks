package com.flo.blocks

data class ColoredBoard(val width: Int, val height: Int, val board: Array<BlockColor>) {

    constructor() : this(BOARD_SIZE, BOARD_SIZE, Array(BOARD_SIZE * BOARD_SIZE) { BlockColor.BACKGROUND })

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

    fun canPlace(brick: Brick) =
        brick.possiblyPlaceablePositions().any { canPlace(it) }

    fun canPlace(brick: OffsetBrick): Boolean {
        return brick.onBoard(width, height) && brick.positionList().all { board[it.x + it.y * BOARD_SIZE].free() }
    }

    fun board(): Board {
        return Board(width, height, BooleanArray(width * height) { board[it].used() })
    }

    fun place(hovering: OffsetBrick, color: BlockColor): Int {
        for (position in hovering.positionList()) board[position.x + position.y * BOARD_SIZE] = color

        val lines = hovering.lines().filter { line -> BOARD_INDICES.all { row -> board[row + BOARD_SIZE * line].used() } }
        val rows = hovering.rows().filter { row -> BOARD_INDICES.all { line -> board[row + BOARD_SIZE * line].used() } }

        for (line in lines) for (row in BOARD_INDICES) board[row + BOARD_SIZE * line] = BlockColor.BACKGROUND
        for (row in rows) for (line in BOARD_INDICES) board[row + BOARD_SIZE * line] = BlockColor.BACKGROUND

        return lines.size + rows.size
    }

    fun clone() = ColoredBoard(width, height, board.clone())
}