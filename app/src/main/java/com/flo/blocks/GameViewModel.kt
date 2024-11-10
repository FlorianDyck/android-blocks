package com.flo.blocks

import android.util.Log
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Stack


class GameViewModel : ViewModel() {

    val game: MutableStateFlow<GameState> = MutableStateFlow(GameState())
    val lastGameState: MutableStateFlow<GameState?> = MutableStateFlow(null)
    val history: Stack<GameState> = Stack()

    private fun updateGameState(newState: GameState) {
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

    /**
     * forceClearBeforeLast:
     * when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first.
     * Only exception: A cross can be cleared by placing the first block later.
     * We are willing to accept this for a ~3 times speed increase.
     */
    private suspend fun computeSync(
        computationStartState: Pair<Board, List<Brick>>,
        board: Board,
        bricks: List<Brick>,
        previousMoves: List<OffsetBrick>,
        parentName: String = "",
        forceClearBeforeLast: Boolean = false
    ) {
        for (i in bricks.indices) {
            val subList = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            val brick = bricks[i]

            for (offsetBrick in brick.possiblyPlaceablePositions()) {
                if (parentName == "") {
                    val lineProgress = (offsetBrick.offset.x + 1f) / (BOARD_SIZE - brick.width + 1)
                    val boardProgress = (lineProgress + offsetBrick.offset.y) / (BOARD_SIZE - brick.height + 1)
                    progress.value =
                        if (bricks.size == 1) boardProgress
                        else if (i == 0) boardProgress * .95f
                        else .95f + .05f * (boardProgress + i - 1) / (bricks.size - 1)
                }
                if (!board.canPlace(offsetBrick)) continue

                val myName = "$parentName, $i/${bricks.size}, ${offsetBrick.offset}"
//                    currentMove.update { myName }

                val newBoard = board.clone()
                val anyCleared = newBoard.place(offsetBrick) > 0

                if (computationStartState != currentState) return

                val myMoves = previousMoves + listOf(offsetBrick)
                if (subList.isEmpty()) {
                    // all blocks set, evaluate position
                    val myScore = newBoard.evaluate()
                    if (myScore > movesScore) {
                        Log.i("compute evaluation", "${myName}: $myScore")
                        mutex.withLock {
                            if (computationStartState != currentState) return
                            moves = myMoves
                            movesScore = myScore
                            nextMove.update { myMoves[0] }
                        }
                    }
                } else if (!(forceClearBeforeLast && !anyCleared && subList.size == 1)) {
                    // recursively set blocks
                    computeSync(
                        computationStartState,
                        newBoard,
                        subList,
                        myMoves,
                        myName,
                        !anyCleared && i > 0
                    )
                }
            }
        }
//        if (computationStartState == currentState) {
//            currentMove.update { parentName }
//        }
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
            }
            withContext(Dispatchers.Default) {
                computeSync(computationStartState, computationStartState.first, bricks, listOf())
            }
        }
    }

    fun stopComputation() {
        viewModelScope.launch {
            mutex.withLock {
                currentState = null
                nextMove.value = null
                progress.value = 1f
            }
        }
    }
}