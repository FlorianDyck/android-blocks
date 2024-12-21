package com.flo.blocks

import android.util.Log
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flo.blocks.game.BitContext
import com.flo.blocks.game.Board
import com.flo.blocks.game.Brick
import com.flo.blocks.game.GameState
import com.flo.blocks.game.OffsetBrick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Stack


class GameViewModel : ViewModel() {

    val game: MutableStateFlow<GameState> = MutableStateFlow(GameState())
    val lastGameState: MutableStateFlow<GameState?> = MutableStateFlow(null)
    val history: Stack<GameState> = Stack()

    fun updateGameState(newState: GameState) {
        stopComputation()

        history.push(game.value)
        lastGameState.value = game.value
        game.value = newState
    }

    fun placeBrick(index: Int, position: IntOffset): Int {
        updateGameState(game.value.place(index, position))
        return game.value.score - history.peek().score
    }

    fun newGame() {
        updateGameState(GameState())
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun undo(): Boolean {
        if (history.isNotEmpty()) {
            this.game.value = history.pop()
        }
        lastGameState.value = if (history.isNotEmpty()) history.peek() else null
        stopComputation()
        return canUndo()
    }

    /**
     * locks: computationStartState, moves, nextMove, movesScore
     */
    private val mutex = Mutex()
    private var moves: List<OffsetBrick>? = null
    private var movesScore = Float.NEGATIVE_INFINITY
    val nextMove: MutableStateFlow<OffsetBrick?> = MutableStateFlow(null)

    //    val currentMove = MutableStateFlow("")
    val progress = MutableStateFlow(1f)
    private var currentState: Pair<Board, List<Brick>>? = null
    private var job: Job? = null

    /**
     * forceClearBeforeLast:
     * when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first.
     * Only exception: A cross can be cleared by placing the first block later.
     * We are willing to accept this for a ~3 times speed increase.
     */
    private suspend fun computeParallel(
        board: Board,
        bricks: List<Brick>,
        previousMoves: List<OffsetBrick>
    ) {
        val totalSteps = bricks.map { it.offsetsWithin(board.width, board.height).size }.sum()
        var steps = 0
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            coroutineScope {
                for (offsetBrick in bricks[i].offsetsWithin(board.width, board.height)) {
                    launch {
                        steps += 1
                        progress.value = steps.toFloat() / totalSteps
                        // Log.d("progress", "$i, ${offsetBrick.offset}, ${progress.value}")
                        computeSync(board, offsetBrick, remainingBricks, previousMoves, i > 0)
                    }
                }
            }
        }
        progress.value = 1f
    }

    private suspend fun computeSync(
        board: Board,
        bricks: List<Brick>,
        previousMoves: List<OffsetBrick>
    ) {
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            for (offsetBrick in bricks[i].offsetsWithin(board.width, board.height)) {
                computeSync(board, offsetBrick, remainingBricks, previousMoves, i > 0)
            }
        }
    }

    suspend fun computeSync(
        board: Board,
        offsetBrick: OffsetBrick,
        remainingBricks: List<Brick>,
        previousMoves: List<OffsetBrick>,
        forceClearBeforeLast: Boolean
    ) {
        if (!board.canPlace(offsetBrick)) return

        val (newBoard, cleared) = board.place(offsetBrick)

        val myMoves = previousMoves + listOf(offsetBrick)
        if (remainingBricks.isEmpty()) {
            // all blocks set, evaluate position
            val myScore = newBoard.evaluate()
            if (myScore > movesScore) {
                mutex.withLock {
                    if (myScore <= movesScore) return
                    moves = myMoves
                    movesScore = myScore
                    nextMove.value = myMoves[0]
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            // recursively set blocks
            computeSync(
                newBoard,
                remainingBricks,
                myMoves
            )
        }
    }

    /**
     * forceClearBeforeLast:
     * when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first.
     * Only exception: A cross can be cleared by placing the first block later.
     * We are willing to accept this for a ~3 times speed increase.
     */
    private suspend fun computeParallelBit(
        board: BitContext.BitBoard,
        bricks: List<BitContext.BitBrick>,
        previousMoves: List<BitContext.BitBoard>
    ) {
        val totalSteps = bricks.map { it.offsetsWithin().size }.sum()
        var steps = 0
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            coroutineScope {
                for (offsetBrick in bricks[i].offsetsWithin()) {
                    launch {
                        steps += 1
                        progress.value = steps.toFloat() / totalSteps
                        // Log.d("progress", "$i, ${offsetBrick.offset}, ${progress.value}")
                        computeSyncBit(board, offsetBrick, remainingBricks, previousMoves, i > 0)
                    }
                }
            }
        }
        progress.value = 1f
    }

    private suspend fun computeSyncBit(
        board: BitContext.BitBoard,
        bricks: List<BitContext.BitBrick>,
        previousMoves: List<BitContext.BitBoard>,
    ) {
        for (i in bricks.indices) {
            val remainingBricks = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            for (offsetBrick in bricks[i].offsetsWithin()) {
                computeSyncBit(board, offsetBrick, remainingBricks, previousMoves, i > 0)
            }
        }
    }

    suspend fun computeSyncBit(
        board: BitContext.BitBoard,
        offsetBrick: BitContext.BitBoard,
        remainingBricks: List<BitContext.BitBrick>,
        previousMoves: List<BitContext.BitBoard>,
        forceClearBeforeLast: Boolean
    ) {
        if (!board.canPlace(offsetBrick)) return

        val (newBoard, cleared) = board.place(offsetBrick)

        val myMoves = previousMoves + listOf(offsetBrick)
        if (remainingBricks.isEmpty()) {
            // all blocks set, evaluate position
            val myScore = newBoard.evaluate()
            if (myScore > movesScore) {
                mutex.withLock {
                    if (myScore <= movesScore) return
                    moves = myMoves.map { it.toOffsetBrick() }
                    movesScore = myScore
                    nextMove.value = myMoves[0].toOffsetBrick()
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            // recursively set blocks
            computeSyncBit(
                newBoard,
                remainingBricks,
                myMoves
            )
        }
    }

    fun startComputation(bricks: List<Brick>) {
        Log.i("compute", "called")
        val computationStartState = Pair(game.value.board.board(), bricks)
        if (computationStartState == currentState) return

        viewModelScope.launch {
            mutex.withLock {
                if (computationStartState == currentState) return@launch
                currentState = computationStartState
                moves = null
                nextMove.value = null
                movesScore = Float.NEGATIVE_INFINITY
                job?.join()
                job = viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        if(game.value.board.width * game.value.board.height <= 64) {
                            val context = BitContext(IntOffset(game.value.board.width, game.value.board.height))
                            computeParallelBit(
                                context.BitBoard(computationStartState.first),
                                bricks.map { context.BitBrick(it) },
                                listOf()
                            )
                        } else {
                            computeParallel(computationStartState.first, bricks, listOf())
                        }
                    }
                }
            }
        }
    }

    fun stopComputation() {
        viewModelScope.launch {
            mutex.withLock {
                job?.cancel()
                currentState = null
                nextMove.value = null
                progress.value = 1f
            }
        }
    }
}