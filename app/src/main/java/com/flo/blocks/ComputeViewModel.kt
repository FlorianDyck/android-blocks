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

class ComputeViewModel : ViewModel() {

    /**
     * locks: computationStartState, moves, nextMove, movesScore
     */
    private val mutex = Mutex()
    private var moves: List<OffsetBrick>? = null
    private var movesScore = Float.NEGATIVE_INFINITY
    val nextMove: MutableStateFlow<OffsetBrick?> = MutableStateFlow(null)

    //    val currentMove = MutableStateFlow("")
    val progress = MutableStateFlow(0f)
    private var currentState: Pair<BooleanArray, List<Brick>>? = null

    private fun differentBlocksAround(board: BooleanArray, index: Int): Int {
        val isSet = board[index]
        fun isDifferent(delta: Int) = (board[index + delta] != isSet)

        var result = 0
        if (index >= BOARD_SIZE && isDifferent(-BOARD_SIZE)) result += 1
        if (((index % BOARD_SIZE) != 0) && isDifferent(-1)) result += 1
        if (((index % BOARD_SIZE) != BOARD_SIZE - 1) && isDifferent(1)) result += 1
        if (index < board.size - BOARD_SIZE && isDifferent(BOARD_SIZE)) result += 1
        return result
    }

    private fun evaluate(board: BooleanArray): Float {
        val freeBlocks = board.count { !it }
        val blockGrades = IntArray(5)
        val freedomGrades = IntArray(5)
        val borderLength = board.mapIndexed { index, color ->
            if (color) {
                blockGrades[differentBlocksAround(board, index)] += 1
                0
            } else {
                val result = differentBlocksAround(board, index)
                freedomGrades[result] += 1
                result
            }
        }.sum()
        var score =
            1f * (freeBlocks - 2 * borderLength - freedomGrades[4] * 20 - freedomGrades[3] * 3 - blockGrades[4] * 2 - blockGrades[3])
//        if (score > movesScore) {
//            Log.i("evaluate", "${currentMove.value}: $score: free: $freeBlocks, border: $borderLength, freedoms: ${freedomGrades.map { "$it" }.reduce {a, b -> "$a$b"}}")
//        }
        if (canPlace(board, rect(0, 0, 2, 2))) score += 10
        if (canPlace(board, rect(0, 0, 4, 0))) score += 5
        if (canPlace(board, rect(0, 0, 0, 4))) score += 5
        return score
    }

    /**
     * forceClearBeforeLast:
     * when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first.
     * Only exception: A cross can be cleared by placing the first block later.
     * We are willing to accept this for a ~3 times speed increase.
     */
    private suspend fun computeSync(
        computationStartState: Pair<BooleanArray, List<Brick>>,
        board: BooleanArray,
        bricks: List<Brick>,
        previousMoves: List<OffsetBrick>,
        parentName: String = "",
        forceClearBeforeLast: Boolean = false
    ) {
        for (i in bricks.indices) {
            val subList = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            val brick = bricks[i]

            for (y in 0..BOARD_SIZE - brick.height) {
                for (x in 0..BOARD_SIZE - brick.width) {
                    if (parentName == "") {
                        val lineProgress = (x + 1f) / (BOARD_SIZE - brick.width + 1)
                        val boardProgress = (lineProgress + y) / (BOARD_SIZE - brick.height + 1)
                        progress.value =
                            if (bricks.size == 1) boardProgress
                            else if (i == 0) boardProgress * .95f
                            else .95f + .05f * (boardProgress + i - 1) / (bricks.size - 1)
                    }

                    val myName = "$parentName, $i/${bricks.size}, ($x,$y)"
//                    currentMove.update { myName }

                    val offsetBrick = OffsetBrick(IntOffset(x, y), brick)
                    if (!offsetBrick.onBoard(board)) continue

                    val newBoard = board.clone()
                    val anyCleared = place(newBoard, offsetBrick) > 0


                    if (computationStartState != currentState) return

                    val myMoves = previousMoves + listOf(offsetBrick)
                    if (subList.isEmpty()) {
                        val myScore = evaluate(newBoard)
                        if (myScore > movesScore) {
                            Log.i("compute evaluation", "${myName}: $myScore")
                            mutex.withLock {
                                if (computationStartState != currentState) return
                                moves = myMoves
                                movesScore = myScore
                                nextMove.update { myMoves[0] }
                            }
                        }
                    }

                    if (subList.isNotEmpty() && !(forceClearBeforeLast && !anyCleared && subList.size == 1)) {
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
        }
//        if (computationStartState == currentState) {
//            currentMove.update { parentName }
//        }
    }

    fun compute(board: Array<BlockColor>, bricks: List<Brick>) {
        Log.i("compute", "called")
        val computationStartState = Pair(BooleanArray(board.size) { board[it].used() }, bricks)
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
}