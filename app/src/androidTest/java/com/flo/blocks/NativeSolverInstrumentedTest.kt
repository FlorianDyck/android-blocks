package com.flo.blocks

import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flo.blocks.game.Board
import com.flo.blocks.game.Brick
import com.flo.blocks.game.NativeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSolverInstrumentedTest {
    @Test
    fun beamPublishesReplayableMoves() {
        val board = Board(8, 8, BooleanArray(64))
        val bricks = listOf(
            Brick(1, 1, booleanArrayOf(true)),
            Brick(2, 1, booleanArrayOf(true, true)),
            Brick(1, 2, booleanArrayOf(true, true))
        )
        val improvements = mutableListOf<NativeSolver.Solution>()
        val solution = NativeSolver.solve(board, bricks, NativeSolver.Mode.BEAM_128,
            onImprovement = { improvements.add(it) }, budgetMs = 1_000)

        assertTrue(improvements.isNotEmpty())
        assertTrue(solution.complete)
        assertEquals(bricks.size, solution.moves.size)
        assertEquals(bricks.size, solution.clears.size)
        var replay = board
        for (move in solution.moves) {
            val brick = bricks[move.block].offset(IntOffset(move.x, move.y))
            assertTrue(replay.canPlace(brick))
            replay = replay.place(brick).first
        }
    }

    @Test
    fun cancellationStopsSearch() {
        val board = Board(8, 8, BooleanArray(64))
        val brick = Brick(1, 1, booleanArrayOf(true))
        val cancellation = NativeSolver.Cancellation()
        cancellation.cancel()

        val result = NativeSolver.solve(board, listOf(brick), NativeSolver.Mode.EXHAUSTIVE,
            cancellation = cancellation, budgetMs = 1_000)

        assertTrue(result.interrupted)
        assertFalse(result.complete)
    }

    @Test
    fun bundledModelsLoadAndRejectOtherBoardSizes() {
        NativeSolver.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val brick = Brick(1, 1, booleanArrayOf(true))
        for (mode in listOf(NativeSolver.Mode.PATTERN_8X8, NativeSolver.Mode.SYMMETRIC_8X8)) {
            val result = NativeSolver.solve(Board(8, 8, BooleanArray(64)), listOf(brick), mode)
            assertTrue(result.complete)
            try {
                NativeSolver.solve(Board(9, 9, BooleanArray(81)), listOf(brick), mode)
                throw AssertionError("Expected trained model to reject 9×9 board")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("8×8"))
            }
        }
    }
}
