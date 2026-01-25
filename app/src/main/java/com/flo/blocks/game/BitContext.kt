package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset


data class Grades(val free: IntArray, val used: IntArray) {
    override operator fun equals(other: Any?): Boolean {
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
            var combination = board or offsetBrick.board
            var cleared = 0

            var toClear = 0UL
            for (y in 0 until boardSize.y) {
                if (combination.and(line.shl(y * boardSize.x)) == line.shl(y * boardSize.x)) {
                    toClear = toClear.or(line.shl(y * boardSize.x))
                    cleared += 1
                }
            }
            for (x in 0 until boardSize.x) {
                if ((combination and (column shl x)) == (column shl x)) {
                    toClear = toClear or (column shl x)
                    cleared += 1
                }
            }

            combination = combination.and(toClear.inv())
            return Pair(BitBoard(combination), cleared)
        }

        internal fun grades(): Grades {
            // check for each direction if there the board changes in that direction
            // the shift is the board in that direction,
            // the additional column/ line treats blocks off the board as ones
            val i1 /* left   */: ULong = board xor ((board shl 1) or column)
            val i2 /* right  */: ULong = board xor ((board shr 1) or (column shl (boardSize.x - 1)))
            val i3 /* bottom */: ULong = board xor ((board shl boardSize.x) or line)
            val i4 /* top    */: ULong = board xor ((board shr boardSize.x) or (line shl (boardSize.x * (boardSize.y - 1))))

            // summing up how many directions are differing in b3,b2,b1 as 3-bit number
            val b3 = i1 and i2 and i3 and i4
            val b2 = b3.inv() and ((i1 and (i2 or i3 or i4)) or (i2 and (i3 or i4)) or (i3 and i4))
            val b1 = i1 xor i2 xor i3 xor i4

            // storing how many bits are set into different variables
            val diff4 = b3
            val diff3 = b2 and b1
            val diff2 = b2 and b1.inv()
            val diff1 = b2.inv() and b1
            val diff0 = b3.inv() and b2.inv() and b1.inv()

            return Grades(
                intArrayOf(
                    // free
                    (diff0 and board.inv()).countOneBits(),
                    (diff1 and board.inv()).countOneBits(),
                    (diff2 and board.inv()).countOneBits(),
                    (diff3 and board.inv()).countOneBits(),
                    (diff4 and board.inv()).countOneBits(),
                ),
                intArrayOf(
                    // used
                    (diff0 and board).countOneBits(),
                    (diff1 and board).countOneBits(),
                    (diff2 and board).countOneBits(),
                    (diff3 and board).countOneBits(),
                    (diff4 and board).countOneBits(),
                )
            )
        }

        internal fun gradesSlow(): Grades {
            var result = 0L
            val left: ULong = board xor ((board shl 1) or column)
            val right: ULong = board xor ((board shr 1) or (column shl (boardSize.x - 1)))
            val bottom: ULong = board xor ((board shl boardSize.x) or line)
            val top: ULong =
                board xor ((board shr boardSize.x) or (line shl (boardSize.x * (boardSize.y - 1))))

            val ALTERNATING_BITS = 0x55_55_55_55_55_55_55_55UL
            // number of set bits in 2-bit groups among left, right and bottom
            val sum3_0 = ((left shr 1) and ALTERNATING_BITS) + ((right shr 1) and ALTERNATING_BITS) + ((bottom shr 1) and ALTERNATING_BITS)
            val sum3_1 = ( left        and ALTERNATING_BITS) + ( right        and ALTERNATING_BITS) + ( bottom        and ALTERNATING_BITS)

            val ALTERNATING_BITS2 = 0x33_33_33_33_33_33_33_33UL
            val EVERY_FOURTH_BIT = 0x11_11_11_11_11_11_11_11UL
            // number of set bits in 4-bit groups among left, right, bottom and top,
            // additionally increased by 5 if the position is set (to switch between used and free)
            val numbersOfSetBits = arrayOf(
                (((sum3_0 shr 2) and ALTERNATING_BITS2) + ((top shr 3) and EVERY_FOURTH_BIT) + 5UL * ((board shr 3) and EVERY_FOURTH_BIT)).toLong(),
                (((sum3_1 shr 2) and ALTERNATING_BITS2) + ((top shr 2) and EVERY_FOURTH_BIT) + 5UL * ((board shr 2) and EVERY_FOURTH_BIT)).toLong(),
                (( sum3_0        and ALTERNATING_BITS2) + ((top shr 1) and EVERY_FOURTH_BIT) + 5UL * ((board shr 1) and EVERY_FOURTH_BIT)).toLong(),
                (( sum3_1        and ALTERNATING_BITS2) + ( top        and EVERY_FOURTH_BIT) + 5UL * ( board        and EVERY_FOURTH_BIT)).toLong(),
            )
            for (numbersOfSetBitsPart in numbersOfSetBits) {
                // numbersOfSetBitsPart contains in each 4-bit group a number for a pixel on the board,
                // of how many adjacent pixels are different
                // the right shift and binary and with 1111 (0xF as Long, 0xFL) gets this number
                // this is multiplied by six to index into result, which is built of 10 6-bit numbers
                //
                // unrolled for efficiency
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 60) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 56) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 52) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 48) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 44) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 40) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 36) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 32) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 28) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 24) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 20) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 16) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 12) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 8) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart shr 4) and 0xFL).toInt())
                result += 1L shl (6 * ((numbersOfSetBitsPart) and 0xFL).toInt())
            }
            return Grades(
                intArrayOf(
                    // free
                    ((result).toInt()) and 0x3F,
                    (((result) shr 6).toInt()) and 0x3F,
                    (((result) shr 12).toInt()) and 0x3F,
                    (((result) shr 18).toInt()) and 0x3F,
                    (((result) shr 24).toInt()) and 0x3F,
                ),
                intArrayOf(
                    // used
                    (((result) shr 30).toInt()) and 0x3F,
                    (((result) shr 36).toInt()) and 0x3F,
                    (((result) shr 42).toInt()) and 0x3F,
                    (((result) shr 48).toInt()) and 0x3F,
                    (((result) shr 54).toInt()) and 0x3F,
                )
            )
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

            return OffsetBrick(
                IntOffset(startX, startY),
                Brick(width, height, BooleanArray(width * height) {
                    val x = it % width
                    val y = it / width
                    ((unShiftedBits shr (x + y * boardSize.x)) and 1UL) != 0UL
                })
            )
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