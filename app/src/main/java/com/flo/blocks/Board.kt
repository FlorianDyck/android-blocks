package com.flo.blocks


const val BOARD_SIZE = 8
val BOARD_INDICES = (0 until BOARD_SIZE)

fun canPlace(board: BooleanArray, brick: Brick): Boolean =
    brick.possiblyPlaceablePositions().any { it.onBoard(board) }

fun canPlace(board: Array<BlockColor>, brick: Brick) =
    brick.possiblyPlaceablePositions().any { it.onBoard(board) }

fun place(board: BooleanArray, hovering: OffsetBrick): Int {
    for (position in hovering.positionList()) board[position.x + position.y * BOARD_SIZE] = true

    val lines = hovering.lines().filter { line -> BOARD_INDICES.all { row -> board[row + BOARD_SIZE * line] } }
    val rows = hovering.rows().filter { row -> BOARD_INDICES.all { line -> board[row + BOARD_SIZE * line] } }

    for (line in lines) for (row in BOARD_INDICES) board[row + BOARD_SIZE * line] = false
    for (row in rows) for (line in BOARD_INDICES) board[row + BOARD_SIZE * line] = false

    return lines.size + rows.size
}

fun place(board: Array<BlockColor>, hovering: OffsetBrick, color: BlockColor): Int {
    for (position in hovering.positionList()) board[position.x + position.y * BOARD_SIZE] = color

    val lines = hovering.lines().filter { line -> BOARD_INDICES.all { row -> board[row + BOARD_SIZE * line].used() } }
    val rows = hovering.rows().filter { row -> BOARD_INDICES.all { line -> board[row + BOARD_SIZE * line].used() } }

    for (line in lines) for (row in BOARD_INDICES) board[row + BOARD_SIZE * line] = BlockColor.BACKGROUND
    for (row in rows) for (line in BOARD_INDICES) board[row + BOARD_SIZE * line] = BlockColor.BACKGROUND

    return lines.size + rows.size
}


