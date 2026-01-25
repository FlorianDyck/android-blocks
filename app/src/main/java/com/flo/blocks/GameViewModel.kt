package com.flo.blocks


import android.util.Log
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flo.blocks.data.GameRepository
import com.flo.blocks.data.SettingsRepository
import com.flo.blocks.game.BitContext
import com.flo.blocks.game.Board
import com.flo.blocks.game.Brick
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.ColoredBrick
import com.flo.blocks.game.GameState
import com.flo.blocks.game.OffsetBrick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Stack

class GameViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    enum class ComputeEnabled {
        Auto,
        Button,
        Hidden
    }
    enum class UndoEnabled {
        Always,
        UnlessNewBlocks,
        Never
    }

    var computeEnabled = ComputeEnabled.Hidden
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveComputeEnabled(value) }
            if (value == ComputeEnabled.Auto) {
                startComputation(game.value.bricks.filterNotNull().map { it.brick })
            }
            showCompute.value = value == ComputeEnabled.Button
        }
    var undoEnabled = UndoEnabled.Always
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveUndoEnabled(value) }
            canUndo.value = canUndo()
        }

    init {
        viewModelScope.launch {
            val width = settingsRepository.boardWidthFlow.first()
            val height = settingsRepository.boardHeightFlow.first()
            gameRepository.initialize()
            val (latestState, index) = gameRepository.getLatestState() ?: Pair(GameState(ColoredBoard(width, height)), 0)
            game.value = latestState
            gameStateIndex = index
            
            val fullHistory = gameRepository.getHistory()
            if (fullHistory.isNotEmpty()) {
                history.addAll(fullHistory.dropLast(1))
            }
            
            computeEnabled = settingsRepository.computeEnabledFlow.first()
            undoEnabled = settingsRepository.undoEnabledFlow.first()
            showUndoIfEnabled.value = settingsRepository.showUndoIfEnabledFlow.first()
            showNewGameButton.value = settingsRepository.showNewGameButtonFlow.first()
        }
    }

    val showCompute = MutableStateFlow(false)
    val canUndo = MutableStateFlow(false)
    val showUndoIfEnabled = MutableStateFlow(true).also { flow ->
        // Save the value whenever it changes (skip initial emission)
        viewModelScope.launch {
            flow.drop(1).collect { value ->
                settingsRepository.saveShowUndoIfEnabled(value)
            }
        }
    }
    val showNewGameButton = MutableStateFlow(false).also { flow ->
        // Save the value whenever it changes (skip initial emission)
        viewModelScope.launch {
            flow.drop(1).collect { value ->
                settingsRepository.saveShowNewGameButton(value)
            }
        }
    }

    data class Achievement(
        val brick: ColoredBrick,
        val cleared: Int,
        val isNewRecord: Boolean,
        val blockRemoved: Boolean,
        val isMinimalist: Boolean
    )

    val showUndo = canUndo.combine(showUndoIfEnabled) { a, b -> a && b }

    private val _achievementEvents = MutableSharedFlow<Achievement>()
    val achievementEvents = _achievementEvents.asSharedFlow()

    val game: MutableStateFlow<GameState> = MutableStateFlow(GameState(ColoredBoard(8, 8)))
    val lastGameState: MutableStateFlow<GameState?> = MutableStateFlow(null)
    val history: Stack<GameState> = Stack()

    private var gameStateIndex = 0

    fun updateGameState(newState: GameState) {
        stopComputation()

        val oldState = game.value
        history.push(oldState)
        lastGameState.value = oldState
        game.value = newState
        gameStateIndex++
        
        viewModelScope.launch {
            gameRepository.saveGameState(newState, gameStateIndex)
        }

        if(computeEnabled == ComputeEnabled.Auto) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        canUndo.value = canUndo()
    }

    fun placeBrick(index: Int, position: IntOffset): Int {
        val coloredBrick = game.value.bricks[index] ?: return 0
        val brick = coloredBrick.brick
        val oldScore = game.value.score
        
        val (nextState, blockRemoved, cellsCleared) = game.value.place(index, position)
        updateGameState(nextState)
        
        val cleared = game.value.score - oldScore

        if (cleared > 0 || blockRemoved) {
            viewModelScope.launch {
                val currentRecord = gameRepository.getBlockAchievement(brick)?.maxLinesCleared ?: 0
                val isNewRecord = cleared > currentRecord
                val minCells = brick.minCellsToClear(game.value.board.width, game.value.board.height)
                val isMinimalist = blockRemoved && cellsCleared == minCells

                if (blockRemoved) gameRepository.markComeAndGone(brick)
                if (isNewRecord) gameRepository.updateBlockAchievement(brick, cleared)
                if (isMinimalist) gameRepository.markMinimalist(brick)

                if (blockRemoved || isNewRecord || isMinimalist || cleared > 1) {
                    _achievementEvents.emit(Achievement(coloredBrick, cleared, isNewRecord, blockRemoved, isMinimalist))
                }
            }
        }
        return cleared
    }

    fun newGame() {
        newGame(game.value.board.width, game.value.board.height)
    }

    fun newGame(width: Int, height: Int) {
        val newState = GameState(ColoredBoard(width, height))
        stopComputation()
        history.clear()
        lastGameState.value = null
        game.value = newState
        gameStateIndex = 0 // Reset index for new game
        viewModelScope.launch {
            gameRepository.newGame()
            gameRepository.saveGameState(newState, gameStateIndex)
        }

        if(computeEnabled == ComputeEnabled.Auto) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        canUndo.value = canUndo()
    }

    fun canUndo(): Boolean {
        return history.isNotEmpty() && when(undoEnabled) {
            UndoEnabled.Always -> true
            UndoEnabled.UnlessNewBlocks -> game.value.bricks.any{ it == null }
            UndoEnabled.Never -> false
        }
    }

    fun undo(): Boolean {
        if (!canUndo()) return false
        stopComputation()
        game.value = history.pop()
        lastGameState.value = history.lastOrNull()
        gameStateIndex--
        
        viewModelScope.launch {
            // We just need to ensure the DB knows current state is now previous index 
            // In our append-only logic, we might not need to do anything if we rely on index, 
            // but effectively we are "restoring" an old state.
            // If we want to persist the 'undo' action, we should probably delete the 'future' state 
            // which the repository saveGameState does (deleteStatesAfter).
            // So re-saving the popped state at the new index ensures consistency.
            gameRepository.saveGameState(game.value, gameStateIndex)
        }
        
        val canStillUndo = canUndo()
        canUndo.value = canStillUndo
        if(computeEnabled == ComputeEnabled.Auto) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        return canStillUndo
    }

    fun saveBoardSize(width: Int, height: Int) {
        viewModelScope.launch {
            settingsRepository.saveBoardSize(width, height)
        }
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
        val totalSteps = bricks.sumOf { it.offsetsWithin(board.width, board.height).size }
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
        val totalSteps = bricks.sumOf { it.offsetsWithin().size }
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
                            computeSyncBit(
                                context.BitBoard(computationStartState.first),
                                bricks.map { context.BitBrick(it) },
                                listOf()
                            )
                        } else {
                            computeParallel(computationStartState.first, bricks, listOf())
                        }
                        Log.i("compute", "finished")
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

    suspend fun getAllAchievements() = gameRepository.getAllAchievements()
}