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

    val mutex = Mutex()
    var moves: List<OffsetBrick>? = null
    var movesScore = Float.NEGATIVE_INFINITY
    val nextMove: MutableStateFlow<OffsetBrick?> = MutableStateFlow(null)
//    val currentMove = MutableStateFlow("")
    val progress = MutableStateFlow(0f)
    var currentState: Pair<IntArray, List<Brick>>? = null

    private fun differentBlocksAround(board: IntArray, index: Int): Int {
        val isSet = board[index] == 0
        fun isDifferent(delta: Int) = ((board[index + delta] == 0) != isSet)

        var result = 0
        if (index >= BOARD_SIZE && isDifferent(-BOARD_SIZE)) result += 1
        if (((index % BOARD_SIZE) != 0) && isDifferent(-1)) result += 1
        if (((index % BOARD_SIZE) != BOARD_SIZE - 1) && isDifferent(1)) result += 1
        if (index < board.size - BOARD_SIZE && isDifferent(BOARD_SIZE)) result += 1
        return result
    }

    private fun evaluate(board: IntArray): Float {
        val t: Byte = 0
        val freeBlocks = board.count { it == 0 }
        val blockGrades = IntArray(5)
        val freedomGrades = IntArray(5)
        val borderLength = board.mapIndexed{ index, color ->
            if (color > 0) {
                blockGrades[differentBlocksAround(board, index)] += 1
                0
            } else {
                val result = differentBlocksAround(board, index)
                freedomGrades[result] += 1
                result
            }
        }.sum()
        var score = 1f * (freeBlocks - 2 * borderLength - freedomGrades[4] * 20 - freedomGrades[3] * 3 - blockGrades[4] * 2 - blockGrades[3])
//        if (score > movesScore) {
//            Log.i("evaluate", "${currentMove.value}: $score: free: $freeBlocks, border: $borderLength, freedoms: ${freedomGrades.map { "$it" }.reduce {a, b -> "$a$b"}}")
//        }
        if (canPlace(board, rect(0, 0, 2, 2))) score += 10
        if (canPlace(board, rect(0, 0, 0, 4))) score += 5
        if (canPlace(board, rect(0, 0, 0, 4))) score += 5
        return score
    }

    private suspend fun computeSync(
        computationStartState: Pair<IntArray, List<Brick>>,
        board: IntArray,
        bricks: List<Brick>,
        previousMoves: List<OffsetBrick>,
        parentName: String = ""
    ) {
        for (i in bricks.indices) {
            val subList = bricks.subList(0, i) + bricks.subList(i + 1, bricks.size)
            val brick = bricks[i]

            for (y in 0..BOARD_SIZE - brick.height) {
                for (x in 0..BOARD_SIZE - brick.width) {
                    if (parentName == "") {
                        progress.value = ((((x + 1f) / (BOARD_SIZE - brick.width + 1)) + y) / (BOARD_SIZE - brick.height + 1) + i)/ bricks.size
                    }

                    val myName = "$parentName, $i/${bricks.size}, ($x,$y)"
//                    currentMove.update { myName }

                    val offsetBrick = OffsetBrick(IntOffset(x, y), brick)
                    if (!offsetBrick.onBoard(board)) {
                        continue
                    }

                    val newBoard = board.clone()
                    place(newBoard, offsetBrick, 1)

                    val myScore = evaluate(newBoard)

                    if (computationStartState != currentState) {
                        return
                    }

                    val myMoves = previousMoves + listOf(offsetBrick)
                    if (myScore > movesScore) {
                        Log.i("compute evaluation", "${myName}: $myScore")
                        mutex.withLock {
                            if (computationStartState != currentState) {
                                return
                            }
                            moves = myMoves
                            movesScore = myScore
                            nextMove.update { myMoves[0] }
                        }
                    }

                    if (subList.isNotEmpty()) {
                        computeSync(computationStartState, newBoard, subList, myMoves, myName)
                    }
                }
            }
        }
//        if (computationStartState == currentState) {
//            currentMove.update { parentName }
//        }
    }

    fun compute(board: IntArray, bricks: List<Brick>) {
        val computationStartState = Pair(board, bricks)
        Log.i("compute", "called")
        if (computationStartState == currentState) {
            Log.i("compute", "return")
            return
        }
        viewModelScope.launch {
            mutex.withLock {
                if (computationStartState == currentState) {
                    Log.i("compute", "return")
                    return@launch
                }
                currentState = computationStartState
                moves = null
                nextMove.update { null }
                movesScore = Float.NEGATIVE_INFINITY
//                currentMove.update { "" }
            }
            Log.i("compute", "go")
            withContext(Dispatchers.Default) {
                computeSync(computationStartState, board, bricks, listOf())
            }
        }
    }
}