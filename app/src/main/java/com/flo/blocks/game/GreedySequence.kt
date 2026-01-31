package com.flo.blocks.game

import androidx.compose.ui.unit.IntOffset


fun findBestGreedySequence(initialBoard: Board, bricks: List<Brick>): Float? {
    if (initialBoard.width * initialBoard.height <= 64) {
        val context = BitContext(
            IntOffset(
                initialBoard.width, initialBoard.height
            )
        )
        val bitBricks = bricks.map { context.BitBrick(it) }
        val initialBoard = context.BitBoard(initialBoard)
        val greedySeq = findBestGreedySequenceBit(
            initialBoard, bitBricks
        )
        return greedySeq?.finalEval
    } else {
        val greedySeq = findBestGreedySequenceLarge(
            initialBoard, bricks
        )
        return greedySeq?.finalEval
    }
}

fun findBestGreedySequenceBit(
    initialBoard: BitContext.BitBoard,
    bricks: List<BitContext.BitBrick>
): MoveSequence? {
    var overallBest: MoveSequence? = null
    for (perm in bricks.permutations()) {
        var currentBoard = initialBoard
        val moves = mutableListOf<BitContext.BitBoard>()
        val evaluations = mutableListOf<Float>()
        var isValid = true
        for (brick in perm) {
            var bestMove: BitContext.BitBoard? = null
            var bestEval = Float.NEGATIVE_INFINITY
            for (offsetBrick in brick.offsetsWithin()) {
                if (currentBoard.canPlace(offsetBrick)) {
                    val (nextBoard, _) = currentBoard.place(offsetBrick)
                    val eval = nextBoard.evaluate()
                    if (eval > bestEval) {
                        bestEval = eval
                        bestMove = offsetBrick
                    }
                }
            }
            if (bestMove != null) {
                moves.add(bestMove)
                val (nextBoard, _) = currentBoard.place(bestMove)
                currentBoard = nextBoard
                evaluations.add(bestEval)
            } else {
                isValid = false
                break
            }
        }
        if (isValid) {
            val finalEval = evaluations.last()
            val maxIntermediate = evaluations.maxOrNull() ?: Float.NEGATIVE_INFINITY
            val sumEval = evaluations.sum()
            val seq =
                MoveSequence(
                    moves.map { it.toOffsetBrick() },
                    evaluations,
                    finalEval,
                    maxIntermediate,
                    sumEval
                )
            if (overallBest == null || seq > overallBest) {
                overallBest = seq
            }
        }
    }
    return overallBest
}

fun findBestGreedySequenceLarge(initialBoard: Board, bricks: List<Brick>): MoveSequence? {
    var overallBest: MoveSequence? = null
    for (perm in bricks.permutations()) {
        var currentBoard = initialBoard
        val moves = mutableListOf<OffsetBrick>()
        val evaluations = mutableListOf<Float>()
        var isValid = true
        for (brick in perm) {
            var bestMove: OffsetBrick? = null
            var bestEval = Float.NEGATIVE_INFINITY
            for (offsetBrick in brick.offsetsWithin(initialBoard.width, initialBoard.height)) {
                if (currentBoard.canPlace(offsetBrick)) {
                    val (nextBoard, _) = currentBoard.place(offsetBrick)
                    val eval = nextBoard.evaluate()
                    if (eval > bestEval) {
                        bestEval = eval
                        bestMove = offsetBrick
                    }
                }
            }
            if (bestMove != null) {
                moves.add(bestMove)
                val (nextBoard, _) = currentBoard.place(bestMove)
                currentBoard = nextBoard
                evaluations.add(bestEval)
            } else {
                isValid = false
                break
            }
        }
        if (isValid) {
            val finalEval = evaluations.last()
            val maxIntermediate = evaluations.maxOrNull() ?: Float.NEGATIVE_INFINITY
            val sumEval = evaluations.sum()
            val seq = MoveSequence(moves, evaluations, finalEval, maxIntermediate, sumEval)
            if (overallBest == null || seq > overallBest) {
                overallBest = seq
            }
        }
    }
    return overallBest
}
