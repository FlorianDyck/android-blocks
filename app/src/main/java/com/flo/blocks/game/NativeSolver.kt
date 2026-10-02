package com.flo.blocks.game

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** JNI adapter for the portable C++ search. Run [solve] on a worker thread. */
object NativeSolver {
    enum class Mode(val nativeId: Int) {
        GREEDY(0), BEAM_128(1), BEAM_512(2), EXHAUSTIVE(3),
        PATTERN_8X8(4), SYMMETRIC_8X8(5)
    }

    data class IndexedMove(val block: Int, val x: Int, val y: Int)

    data class Solution(
        val moves: List<IndexedMove>,
        val clears: List<Int>,
        val cleared: Int,
        val score: Float,
        val complete: Boolean,
        val exact: Boolean,
        val interrupted: Boolean,
        val nodes: Long,
        val firstMs: Double,
        val elapsedMs: Double
    )

    /** Call from a different thread to stop a running search. One search per instance. */
    class Cancellation : AutoCloseable {
        private val handle = AtomicLong(nativeCreateCancellation())

        fun cancel() {
            handle.get().takeIf { it != 0L }?.let(::nativeCancel)
        }

        internal fun id(): Long = handle.get().also {
            check(it != 0L) { "Cancellation has already been closed" }
        }

        override fun close() {
            handle.getAndSet(0L).takeIf { it != 0L }?.let(::nativeReleaseCancellation)
        }
    }

    private data class NativeResult(
        val moves: IntArray,
        val clears: IntArray,
        val cleared: Int,
        val score: Float,
        val complete: Boolean,
        val exact: Boolean,
        val interrupted: Boolean,
        val nodes: Long,
        val firstMs: Double,
        val elapsedMs: Double
    ) {
        fun toSolution(): Solution = Solution(
            moves.toList().chunked(3).map { IndexedMove(it[0], it[1], it[2]) },
            clears.toList(), cleared, score, complete, exact, interrupted,
            nodes, firstMs, elapsedMs
        )
    }

    private fun interface ImprovementCallback {
        fun accept(result: NativeResult)
    }

    @Volatile private var appContext: Context? = null
    private val preparedModels = mutableMapOf<Mode, String>()

    init {
        System.loadLibrary("blocks_native")
    }

    /** Supply the application context before using a learned 8×8 model. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun solve(
        board: Board,
        bricks: List<Brick>,
        mode: Mode,
        onImprovement: (Solution) -> Unit = {},
        cancellation: Cancellation = Cancellation(),
        budgetMs: Long = 100L
    ): Solution {
        try {
            return solveInternal(board, bricks, mode, onImprovement, cancellation, budgetMs)
        } finally {
            cancellation.close()
        }
    }

    private fun solveInternal(
        board: Board,
        bricks: List<Brick>,
        mode: Mode,
        onImprovement: (Solution) -> Unit,
        cancellation: Cancellation,
        budgetMs: Long
    ): Solution {
        require(board.width in 5..15 && board.height in 5..15) { "Native solver supports board dimensions 5..15" }
        require(board.board.size == board.width * board.height) { "Board cell count is inconsistent" }
        require(bricks.size <= 3) { "Native solver accepts at most three bricks" }
        require(budgetMs in 0..(Long.MAX_VALUE / 1000)) { "Invalid search budget" }

        val modelPath = if (mode == Mode.PATTERN_8X8 || mode == Mode.SYMMETRIC_8X8) {
            require(board.width == 8 && board.height == 8) { "The selected trained model supports only 8×8 boards" }
            prepareModel(mode)
        } else null

        val boardRows = IntArray(board.height) { y ->
            (0 until board.width).fold(0) { bits, x ->
                if (board[x, y]) bits or (1 shl x) else bits
            }
        }
        val shapes = IntArray(bricks.size * 7)
        bricks.forEachIndexed { index, brick ->
            require(brick.width in 1..5 && brick.height in 1..5) { "Native solver supports brick dimensions 1..5" }
            require(brick.positions.size == brick.width * brick.height) { "Brick cell count is inconsistent" }
            shapes[index * 7] = brick.width
            shapes[index * 7 + 1] = brick.height
            for (y in 0 until brick.height) {
                for (x in 0 until brick.width) {
                    if (brick.getPosition(x, y)) {
                        shapes[index * 7 + 2 + y] = shapes[index * 7 + 2 + y] or (1 shl x)
                    }
                }
            }
        }

        return nativeSolve(board.width, board.height, boardRows, shapes, mode.nativeId,
            budgetMs * 1000, modelPath, cancellation.id(),
            ImprovementCallback { onImprovement(it.toSolution()) }).toSolution()
    }

    @Synchronized
    private fun prepareModel(mode: Mode): String {
        preparedModels[mode]?.let { return it }
        val context = checkNotNull(appContext) { "Call NativeSolver.initialize(context) before using a trained model" }
        val filename = when (mode) {
            Mode.PATTERN_8X8 -> "pattern-8x8.model"
            Mode.SYMMETRIC_8X8 -> "symmetric-8x8.model"
            else -> error("No model for $mode")
        }
        val directory = File(context.cacheDir, "native_models").apply { mkdirs() }
        val target = File(directory, filename)
        val temporary = File(directory, "$filename.tmp")
        context.assets.open("models/$filename").use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.renameTo(target)) { "Could not prepare native model $filename" }
        return target.absolutePath.also { preparedModels[mode] = it }
    }

    private external fun nativeCreateCancellation(): Long
    private external fun nativeCancel(id: Long)
    private external fun nativeReleaseCancellation(id: Long)
    private external fun nativeSolve(
        width: Int, height: Int, boardRows: IntArray, shapes: IntArray,
        mode: Int, budgetMicros: Long, modelPath: String?, cancellationId: Long,
        callback: ImprovementCallback
    ): NativeResult
}
