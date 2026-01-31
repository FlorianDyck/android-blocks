package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

data class MoveSequence(
        val moves: List<OffsetBrick>,
        val evaluations: List<Float>,
        val finalEval: Float,
        val maxIntermediateEval: Float,
        val sumEval: Float
) : Comparable<MoveSequence> {
    override fun compareTo(other: MoveSequence): Int {
        if (this.finalEval != other.finalEval) return this.finalEval.compareTo(other.finalEval)
        if (this.maxIntermediateEval != other.maxIntermediateEval)
                return this.maxIntermediateEval.compareTo(other.maxIntermediateEval)
        return this.sumEval.compareTo(other.sumEval)
    }
}

private fun findBestPermutationBit(
        initialBoard: BitContext.BitBoard,
        moveBoards: List<BitContext.BitBoard>
): MoveSequence? {
    var best: MoveSequence? = null
    for (perm in moveBoards.permutations()) {
        var currentBoard = initialBoard
        val intermediateEvals = mutableListOf<Float>()
        var isValid = true
        for (move in perm) {
            if (!currentBoard.canPlace(move)) {
                isValid = false
                break
            }
            val (nextBoard, _) = currentBoard.place(move)
            currentBoard = nextBoard
            intermediateEvals.add(currentBoard.evaluate())
        }
        if (isValid) {
            val finalEval = intermediateEvals.last()
            val maxIntermediate = intermediateEvals.maxOrNull() ?: Float.NEGATIVE_INFINITY
            val sumEval = intermediateEvals.sum()
            val seq =
                    MoveSequence(
                            perm.map { it.toOffsetBrick() },
                            intermediateEvals,
                            finalEval,
                            maxIntermediate,
                            sumEval
                    )
            if (best == null || seq > best) {
                best = seq
            }
        }
    }
    return best
}

private fun findBestPermutation(initialBoard: Board, moves: List<OffsetBrick>): MoveSequence? {
    var best: MoveSequence? = null
    for (perm in moves.permutations()) {
        var currentBoard = initialBoard
        val intermediateEvals = mutableListOf<Float>()
        var isValid = true
        for (move in perm) {
            if (!currentBoard.canPlace(move)) {
                isValid = false
                break
            }
            val (nextBoard, _) = currentBoard.place(move)
            currentBoard = nextBoard
            intermediateEvals.add(currentBoard.evaluate())
        }
        if (isValid) {
            val finalEval = intermediateEvals.last()
            val maxIntermediate = intermediateEvals.maxOrNull() ?: Float.NEGATIVE_INFINITY
            val sumEval = intermediateEvals.sum()
            val seq = MoveSequence(perm, intermediateEvals, finalEval, maxIntermediate, sumEval)
            if (best == null || seq > best) {
                best = seq
            }
        }
    }
    return best
}

class MoveCalculator {
    // State needed for synchronization
    private val mutex = Mutex()
    private var movesScore: MoveSequence? = null

    suspend fun compute(
            initialBoard: Board,
            bricks: List<Brick>,
            onProgress: suspend (Float) -> Unit,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        mutex.withLock { movesScore = null }
        if (initialBoard.width * initialBoard.height <= 64) {
            val context = BitContext(IntOffset(initialBoard.width, initialBoard.height))
            val bitBricks = bricks.map { context.BitBrick(it) }
            val initialBitBoard = context.BitBoard(initialBoard)

            computeSyncBit(initialBitBoard, initialBitBoard, bitBricks, listOf(), onNewBest)
        } else {
            computeParallel(initialBoard, initialBoard, bricks, listOf(), onProgress, onNewBest)
        }
    }

    /**
     * forceClearBeforeLast: when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first. Only
     * exception: A cross can be cleared by placing the first block later. We are willing to accept
     * this for a ~3 times speed increase.
     */
    private suspend fun computeParallel(
            initialBoard: Board,
            board: Board,
            bricks: List<Brick>,
            previousMoves: List<OffsetBrick>,
            onProgress: suspend (Float) -> Unit,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        val totalSteps = bricks.sumOf { it.offsetsWithin(board.width, board.height).size }
        val steps = AtomicInteger(0)
        // We reset state for new computation
        mutex.withLock { movesScore = null }

        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            coroutineScope {
                for (offsetBrick in bricks[i].offsetsWithin(board.width, board.height)) {
                    launch {
                        onProgress(steps.incrementAndGet().toFloat() / totalSteps)

                        computeSync(
                                initialBoard,
                                board,
                                offsetBrick,
                                remainingBricks,
                                previousMoves,
                                i > 0,
                                onNewBest
                        )
                    }
                }
            }
        }
        onProgress(1f)
    }

    private suspend fun computeSync(
            initialBoard: Board,
            board: Board,
            bricks: List<Brick>,
            previousMoves: List<OffsetBrick>,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            for (offsetBrick in bricks[i].offsetsWithin(board.width, board.height)) {
                computeSync(
                        initialBoard,
                        board,
                        offsetBrick,
                        remainingBricks,
                        previousMoves,
                        i > 0,
                        onNewBest
                )
            }
        }
    }

    private suspend fun computeSync(
            initialBoard: Board,
            board: Board,
            offsetBrick: OffsetBrick,
            remainingBricks: List<Brick>,
            previousMoves: List<OffsetBrick>,
            forceClearBeforeLast: Boolean,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        if (!board.canPlace(offsetBrick)) return

        val (newBoard, cleared) = board.place(offsetBrick)

        val myMoves = previousMoves + listOf(offsetBrick)
        if (remainingBricks.isEmpty()) {
            val myScore = newBoard.evaluate()
            // Optimization: check against current best before doing permutations
            if (movesScore?.let { myScore > it.finalEval } ?: true) {
                val bestSeq = findBestPermutation(initialBoard, myMoves)
                if (bestSeq != null) {
                    mutex.withLock {
                        if (movesScore == null || bestSeq > movesScore!!) {
                            movesScore = bestSeq
                            onNewBest(bestSeq)
                        }
                    }
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            computeSync(initialBoard, newBoard, remainingBricks, myMoves, onNewBest)
        }
    }

    /**
     * forceClearBeforeLast: when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first. Only
     * exception: A cross can be cleared by placing the first block later. We are willing to accept
     * this for a ~3 times speed increase.
     */
    private suspend fun computeParallelBit(
        initialBoard: BitContext.BitBoard,
        board: BitContext.BitBoard,
        bricks: List<BitContext.BitBrick>,
        previousMoves: List<BitContext.BitBoard>,
        onProgress: suspend (Float) -> Unit,
        onNewBest: suspend (MoveSequence) -> Unit
    ) {
        val totalSteps = bricks.sumOf { it.offsetsWithin().size }
        val steps = AtomicInteger(0)
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            coroutineScope {
                for (offsetBrick in bricks[i].offsetsWithin()) {
                    launch {
                        onProgress(steps.incrementAndGet().toFloat() / totalSteps)
                        // Log.d("progress", "$i, ${offsetBrick.offset}, ${progress.value}")
                        computeSyncBit(
                            initialBoard, board, offsetBrick, remainingBricks, previousMoves, i > 0, onNewBest
                        )
                    }
                }
            }
        }
        onProgress(1f)
    }

    private suspend fun computeSyncBit(
            initialBoard: BitContext.BitBoard,
            board: BitContext.BitBoard,
            bricks: List<BitContext.BitBrick>,
            previousMoves: List<BitContext.BitBoard>,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            for (offsetBrick in bricks[i].offsetsWithin()) {
                computeSyncBit(
                        initialBoard,
                        board,
                        offsetBrick,
                        remainingBricks,
                        previousMoves,
                        i > 0,
                        onNewBest
                )
            }
        }
    }

    private suspend fun computeSyncBit(
            initialBoard: BitContext.BitBoard,
            board: BitContext.BitBoard,
            offsetBrick: BitContext.BitBoard,
            remainingBricks: List<BitContext.BitBrick>,
            previousMoves: List<BitContext.BitBoard>,
            forceClearBeforeLast: Boolean,
            onNewBest: suspend (MoveSequence) -> Unit
    ) {
        if (!board.canPlace(offsetBrick)) return

        val (newBoard, cleared) = board.place(offsetBrick)

        val myMoves = previousMoves + listOf(offsetBrick)
        if (remainingBricks.isEmpty()) {
            // all blocks set, test all permutations
            val bestSeq = findBestPermutationBit(initialBoard, myMoves)
            if (bestSeq != null) {
                mutex.withLock {
                    if (movesScore == null || bestSeq > movesScore!!) {
                        movesScore = bestSeq
                        onNewBest(bestSeq)
                    }
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            computeSyncBit(initialBoard, newBoard, remainingBricks, myMoves, onNewBest)
        }
    }
}
