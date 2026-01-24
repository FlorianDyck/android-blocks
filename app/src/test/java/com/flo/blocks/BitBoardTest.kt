package com.flo.blocks

import androidx.compose.ui.unit.IntOffset
import com.flo.blocks.game.BRICKS
import com.flo.blocks.game.BitContext
import com.flo.blocks.game.ColoredBoard
import org.junit.Test

import org.junit.Assert.*
import kotlin.time.TimeSource

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class BitBoardTest {
    @Test
    fun addition_isCorrect() {
        val bitContext = BitContext(IntOffset(8, 8))
        val timesource = TimeSource.Monotonic
        for (block in BRICKS) {
            println(block)
            println()

            val board = ColoredBoard(8, 8).board()
            val (placedBoard, clearedBoard) = board.place(block.offsetsWithin(8, 8)[0])

            val beforeGrades = timesource.markNow()
            val grades = placedBoard.grades()
            val afterGrades = timesource.markNow()
            val timeGrades = afterGrades - beforeGrades

            assertEquals(0, clearedBoard)

            val bitBoard = bitContext.BitBoard(ColoredBoard(8, 8).board())
            val bitBrick = bitContext.BitBrick(block)
            val (placedBitBoard, clearedBitBoard) = bitBoard.place(bitBrick.offsetsWithin()[0])

            val beforeBitGrades = timesource.markNow()
            val bitGrades = placedBitBoard.grades()
            val afterBitGrades = timesource.markNow()
            val timeBitGrades = afterBitGrades - beforeBitGrades

            val beforeBitGradesSlow = timesource.markNow()
            val bitGradesSlow = placedBitBoard.gradesSlow()
            val afterBitGradesSlow = timesource.markNow()
            val timeBitGradesSlow = afterBitGradesSlow - beforeBitGradesSlow

            assertArrayEquals(bitGrades.used, bitGradesSlow.used)
            assertArrayEquals(bitGrades.free, bitGradesSlow.free)
            assertEquals(0, clearedBitBoard)

            assertArrayEquals(grades.first, bitGrades.used)
            assertArrayEquals(grades.second, bitGrades.free)
            assertEquals(placedBoard.evaluate(), placedBitBoard.evaluate())

            println("grades: ${timeGrades.inWholeNanoseconds} ns")
            println("bit grades slow: ${timeBitGradesSlow.inWholeNanoseconds} ns}")
            println("bit grades: ${timeBitGrades.inWholeNanoseconds} ns")
        }
        assertEquals(4, 2 + 2)
    }
}