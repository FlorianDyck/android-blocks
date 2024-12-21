package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset


data class Grades(val free: IntArray, val used: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Grades

        if (!free.contentEquals(other.free)) return false
        if (!used.contentEquals(other.used)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = free.contentHashCode()
        result = 31 * result + used.contentHashCode()
        return result
    }
}

class BitContext(val boardSize: IntOffset) {
    val line: ULong = (0 until boardSize.x).sumOf { 1UL.shl(it) }
    val column: ULong = (0 until boardSize.y).sumOf { 1UL.shl(it * boardSize.x) }

    private fun placeablePositionMask(width: Int, height: Int) =
        (line shr (width - 1)) * (column shr ((height - 1) * boardSize.x))

    val placeablePositionMask3x3: ULong = placeablePositionMask(3, 3)
    val placeablePositionMask1x5: ULong = placeablePositionMask(1, 5)
    val placeablePositionMask5x1: ULong = placeablePositionMask(5, 1)
    fun IntOffset.shift() = y * boardSize.x + x

    inner class BitBoard(private val board: ULong) {
        constructor(board: Board) : this({
            var result = 0UL
            for (y in 0 until boardSize.y) {
                for (x in 0 until boardSize.x) {
                    if (board[x, y]) {
                        result = result or (1UL shl (x + y * boardSize.x))
                    }
                }
            }
            result
        }())
        fun get(position: IntOffset) = (board.shr(position.shift()).and(1UL)) != 0UL
        private fun placeablePositions(brick: BitBrick): ULong {
            var result = 0UL
            for (y in 0 until brick.brickSize.y) {
                for (x in 0 until brick.brickSize.x) {
                    if (get(boardSize)) {
                        result = result.or(board.shr(boardSize.shift()))
                    }
                }
            }
            return result.inv().and(brick.placeablePositionsMask)
        }

        fun canPlace(brick: BitBrick): Boolean = placeablePositions(brick) != 0UL
        fun canPlace(offsetBrick: BitBoard): Boolean = board.and(offsetBrick.board) == 0UL
        fun place(offsetBrick: BitBoard): Pair<BitBoard, Int> {
            var combination = board.or(offsetBrick.board)
            var cleared = 0

            var lines = 0UL
            for (y in 0 until boardSize.y) {
                if (combination.and(line.shl(y * boardSize.x)) == line.shl(y * boardSize.x)) {
                    lines = lines.or(1UL.shl(y * boardSize.x))
                    cleared += 1
                }
            }

            var columns = 0UL
            for (x in 0 until boardSize.x) {
                if ((combination and (column shl x)) == (column shl x)) {
                    columns = columns or (1UL shl x)
                    cleared += 1
                }
            }

            combination = combination.and((lines * line).or(columns * column).inv())
            return Pair(BitBoard(combination), cleared)
        }

        private fun grades(): Grades {
            val result = Grades(intArrayOf(0, 0, 0, 0, 0), intArrayOf(0, 0, 0, 0, 0))
            val left: ULong = board xor ((board shl 1) or column)
            val right: ULong = board xor ((board shr 1) or (column shl (boardSize.x - 1)))
            val bottom: ULong = board xor ((board shl boardSize.x) or line)
            val top: ULong =
                board xor ((board shr boardSize.x) or (line shl (boardSize.x * (boardSize.y - 1))))
            for (y in 0 until boardSize.y) {
                for (x in 0 until boardSize.x) {
                    var differentBlocksAround = 0
                    val pos = 1UL shl boardSize.x * y + x
                    if ((left and pos) != 0UL) differentBlocksAround++
                    if ((right and pos) != 0UL) differentBlocksAround++
                    if ((top and pos) != 0UL) differentBlocksAround++
                    if ((bottom and pos) != 0UL) differentBlocksAround++

                    if ((board and pos) != 0UL) {
                        result.used[differentBlocksAround]++
                    } else {
                        result.free[differentBlocksAround]++
                    }
                }
            }
            return result
        }

        private fun placeablePositions3X3(): Boolean {
            var result = this.board
            result = result or (result shr boardSize.x) or (result shr (2 * boardSize.x))
            result = result or (result shr 1) or (result shr 2)
            return (result and placeablePositionMask3x3).inv() != 0UL
        }

        private fun placeablePositions1X5(): Boolean {
            var result = this.board
            result = result or (result shr boardSize.x) or (result shr (2 * boardSize.x))
            result = result or (result shr (2 * boardSize.x))
            return (result and placeablePositionMask1x5).inv() != 0UL
        }

        private fun placeablePositions5X1(): Boolean {
            var result = this.board
            result = result or (result shr 1) or (result shr 2)
            result = result or (result shr 2)
            return (result and placeablePositionMask5x1).inv() != 0UL
        }

        fun evaluate(): Float {
            var score = 0
            val grades = grades()
            score += grades.free[0] * 3
            score += grades.free[1] * 2
            score += grades.free[2] * 1
            score += grades.free[3] * -2
            score += grades.free[4] * -21
//            score += grades.used[0] * 0
//            score += grades.used[1] * 0
//            score += grades.used[2] * 0
            score += grades.used[3] * -1
            score += grades.used[4] * -5
            if (placeablePositions3X3()) score += 20
            if (placeablePositions5X1()) score += 10
            if (placeablePositions1X5()) score += 10
            return score.toFloat()
        }

        fun toOffsetBrick(): OffsetBrick {
            var startX = 0
            while ((board and (column shl startX)) == 0UL) startX++
            var startY = 0
            while ((board and (line shl (startY * boardSize.x))) == 0UL) startY++

            val unShiftedBits = board shr (startX + startY * boardSize.x)
            var width = 1
            while ((unShiftedBits and (column shl width)) != 0UL) width++
            var height = 1
            while ((unShiftedBits and (line shl (height * boardSize.x))) != 0UL) height++

            return OffsetBrick(IntOffset(startX, startY), Brick(width, height, BooleanArray(width * height) {
                val x = it % width
                val y = it / width
                ((unShiftedBits shr (x + y * boardSize.x)) and 1UL) != 0UL
            }))
        }
    }

    inner class BitBrick(brick: Brick) {
        val brickSize = IntOffset(brick.width, brick.height)
        private val board: ULong = run {
            var result = 0UL
            for (y in 0 until brickSize.y) {
                for (x in 0 until brickSize.x) {
                    if (brick.getPosition(x, y)) {
                        result = result.or(1UL.shl(y * boardSize.x + x))
                    }
                }
            }
            result
        }
        val placeablePositionsMask: ULong = run {
            var result = 0UL
            for (y in 0..boardSize.y - brickSize.y) {
                for (x in 0..boardSize.x - brickSize.x) {
                    result = result.or(1UL.shl(y * boardSize.x + x))
                }
            }
            result
        }

        fun offsetsWithin(): List<BitBoard> {
            val result = ArrayList<BitBoard>(brickSize.x * brickSize.y)
            for (y in 0..boardSize.y - brickSize.y) {
                for (x in 0..boardSize.x - brickSize.x) {
                    result.add(BitBoard(board.shl(x + y * boardSize.x)))
                }
            }
            return result
        }
    }
}