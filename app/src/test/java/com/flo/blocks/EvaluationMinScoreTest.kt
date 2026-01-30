package com.flo.blocks

import com.flo.blocks.game.Board
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationMinScoreTest {
    @Test
    fun `checkerboard evaluation should be zero after shift`() {
        val width = 8
        val height = 8

        // 1. Calculate min score using the helper
        val minScore = Board.calculateMinEval(width, height)

        // 2. Create the worse checkerboard manually
        val board1 =
                Board(
                        width,
                        height,
                        BooleanArray(width * height) { index ->
                            val x = index % width
                            val y = index / width
                            (x + y) % 2 == 0
                        }
                )
        val board2 =
                Board(
                        width,
                        height,
                        BooleanArray(width * height) { index ->
                            val x = index % width
                            val y = index / width
                            (x + y) % 2 == 1
                        }
                )

        val eval1 = board1.evaluate()
        val eval2 = board2.evaluate()

        // The helper should have chosen the minimum of these two
        assertEquals(minOf(eval1, eval2), minScore)

        // The shifted evaluation should be 0 for the worse one
        val shiftedEval1 = eval1 - minScore
        val shiftedEval2 = eval2 - minScore

        // At least one of them must be 0 (the worse one)
        // And the other one must be >= 0
        assert(shiftedEval1 >= 0)
        assert(shiftedEval2 >= 0)
        assert(shiftedEval1 == 0f || shiftedEval2 == 0f)

        println("Width: $width, Height: $height")
        println("Min Score: $minScore")
        println("Eval 1: $eval1 (Shifted: $shiftedEval1)")
        println("Eval 2: $eval2 (Shifted: $shiftedEval2)")
    }

    @Test
    fun `empty board should have positive shifted score`() {
        val width = 8
        val height = 8
        val minScore = Board.calculateMinEval(width, height)
        val emptyBoard = Board(width, height, BooleanArray(width * height) { false })
        val shiftedEval = emptyBoard.evaluate() - minScore

        println("Empty board shifted eval: $shiftedEval")
        assert(shiftedEval > 0)
    }

    @Test
    fun `checkerboard and empty board should normalize correctly with non-linear scaling`() {
        val width = 8
        val height = 8

        val minScore = Board.calculateMinEval(width, height)
        val maxScore = Board.calculateMaxEval(width, height)

        fun Float.normalize(): Float {
            val ratio = (this - minScore) / (maxScore - minScore)
            return ratio.coerceIn(0f, 1f).pow(10f) * 100f
        }

        // 1. Checkerboard should be 0
        val board1 =
                Board(
                        width,
                        height,
                        BooleanArray(width * height) { index ->
                            val x = index % width
                            val y = index / width
                            (x + y) % 2 == 0
                        }
                )
        val board2 =
                Board(
                        width,
                        height,
                        BooleanArray(width * height) { index ->
                            val x = index % width
                            val y = index / width
                            (x + y) % 2 == 1
                        }
                )

        val eval1 = board1.evaluate().normalize()
        val eval2 = board2.evaluate().normalize()

        println("Checkerboard 1 Normalized: $eval1")
        println("Checkerboard 2 Normalized: $eval2")

        assert(eval1 >= -0.001f)
        assert(eval2 >= -0.001f)
        assert(kotlin.math.abs(eval1) < 0.001f || kotlin.math.abs(eval2) < 0.001f)

        // 2. Empty board should be 100
        val emptyBoard = Board(width, height, BooleanArray(width * height) { false })
        val emptyEval = emptyBoard.evaluate().normalize()
        println("Empty Board Normalized: $emptyEval")
        assertEquals(100f, emptyEval, 0.001f)

        // 3. Verify non-linearity: 80% linear ratio -> ~10.7% normalized
        // We simulate a raw score that would give 0.8 ratio
        val ratio80Raw = minScore + 0.8f * (maxScore - minScore)
        val normalized80 = ratio80Raw.normalize()
        println("Linear 80% Ratio Normalized: $normalized80")
        // 0.8^10 * 100 = 10.737...
        assertEquals(10.737f, normalized80, 0.01f)
    }
}
