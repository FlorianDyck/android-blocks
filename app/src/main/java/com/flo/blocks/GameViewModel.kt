package com.flo.blocks

import android.util.Log
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flo.blocks.data.AchievementFilter
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
import kotlin.math.min

class GameViewModel(
    private val settingsRepository: SettingsRepository, private val gameRepository: GameRepository
) : ViewModel() {

    enum class ComputeEnabled {
        Auto, Button, Hidden
    }

    enum class UndoEnabled {
        Always, UnlessNewBlocks, Never
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

    var achievementShowMinimalist = AchievementFilter.Always
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveAchievementShowMinimalist(value) }
        }
    var achievementShowComeAndGone = AchievementFilter.Always
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveAchievementShowComeAndGone(value) }
        }
    var achievementShowNewRecord = true
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveAchievementShowNewRecord(value) }
        }
    var achievementShowClearedLines = true
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveAchievementShowClearedLines(value) }
        }
    var achievementShowAroundTheCorner = true
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveAchievementShowAroundTheCorner(value) }
        }
    var showBestEval = false
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveShowBestEval(value) }
            if (value) {
                startComputation(game.value.bricks.filterNotNull().map { it.brick })
            }
        }
    var showCurrentEval = false
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveShowCurrentEval(value) }
        }
    val achievementAlpha = MutableStateFlow(0.9f)

    fun setAchievementAlpha(alpha: Float) {
        viewModelScope.launch { settingsRepository.saveAchievementAlpha(alpha) }
    }

    init {
        viewModelScope.launch {
            val width = settingsRepository.boardWidthFlow.first()
            val height = settingsRepository.boardHeightFlow.first()
            gameRepository.initialize()
            val (latestState, index) = gameRepository.getLatestState() ?: Pair(
                GameState(
                    ColoredBoard(width, height)
                ), 0
            )
            game.value = latestState
            currentEval.value = latestState.board.board().evaluate()
            gameStateIndex = index

            val fullHistory = gameRepository.getHistory()
            if (fullHistory.isNotEmpty()) {
                history.addAll(fullHistory.dropLast(1))
            }

            computeEnabled = settingsRepository.computeEnabledFlow.first()
            undoEnabled = settingsRepository.undoEnabledFlow.first()
            showUndoIfEnabled.value = settingsRepository.showUndoIfEnabledFlow.first()
            showNewGameButton.value = settingsRepository.showNewGameButtonFlow.first()
            achievementShowMinimalist = settingsRepository.achievementShowMinimalistFlow.first()
            achievementShowComeAndGone = settingsRepository.achievementShowComeAndGoneFlow.first()
            achievementShowNewRecord = settingsRepository.achievementShowNewRecordFlow.first()
            achievementShowClearedLines = settingsRepository.achievementShowClearedLinesFlow.first()
            achievementShowAroundTheCorner =
                settingsRepository.achievementShowAroundTheCornerFlow.first()
            showBestEval = settingsRepository.showBestEvalFlow.first()
            showCurrentEval = settingsRepository.showCurrentEvalFlow.first()
            achievementAlpha.value = settingsRepository.achievementAlphaFlow.first()

            viewModelScope.launch {
                settingsRepository.highscoreFlow.collect { highscore.value = it }
            }
            viewModelScope.launch {
                settingsRepository.achievementShowMinimalistFlow.collect {
                    achievementShowMinimalist = it
                }
            }
            viewModelScope.launch {
                settingsRepository.achievementShowComeAndGoneFlow.collect {
                    achievementShowComeAndGone = it
                }
            }
            viewModelScope.launch {
                settingsRepository.achievementShowNewRecordFlow.collect {
                    achievementShowNewRecord = it
                }
            }
            viewModelScope.launch {
                settingsRepository.achievementShowClearedLinesFlow.collect {
                    achievementShowClearedLines = it
                }
            }
            viewModelScope.launch {
                settingsRepository.achievementShowAroundTheCornerFlow.collect {
                    achievementShowAroundTheCorner = it
                }
            }
            viewModelScope.launch {
                settingsRepository.achievementAlphaFlow.collect { achievementAlpha.value = it }
            }
            viewModelScope.launch {
                settingsRepository.showBestEvalFlow.collect { showBestEval = it }
            }
            viewModelScope.launch {
                settingsRepository.showCurrentEvalFlow.collect { showCurrentEval = it }
            }
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
        val isMinimalist: Boolean,
        val aroundTheCorner: Boolean,
        val largeCorner: Boolean,
        val hugeCorner: Boolean,
        val wideCorner: Boolean,
        val notEvenAround: Boolean,
        val largeWideCorner: Boolean
    )

    val showUndo = canUndo.combine(showUndoIfEnabled) { a, b -> a && b }

    val highscore = MutableStateFlow(0)

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
        currentEval.value = newState.board.board().evaluate()
        gameStateIndex++

        viewModelScope.launch { gameRepository.saveGameState(newState, gameStateIndex) }

        hintRequested.value = false
        if (computeEnabled == ComputeEnabled.Auto || showBestEval) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        canUndo.value = canUndo()
    }

    private fun updateHighscore() {
        val currentScore = game.value.score
        if (currentScore > highscore.value && currentScore > 0) {
            highscore.value = currentScore
            viewModelScope.launch { settingsRepository.saveHighscore(currentScore) }
        }
    }

    fun placeBrick(index: Int, position: IntOffset): Int {
        val coloredBrick = game.value.bricks[index] ?: return 0
        val brick = coloredBrick.brick
        val oldScore = game.value.score

        val (nextState, blockRemoved, cellsCleared, clearedRowIndices, clearedColIndices) = game.value.place(
            index, position
        )
        updateGameState(nextState)

        val cleared = game.value.score - oldScore

        if (cleared > 0 || blockRemoved) {
            viewModelScope.launch {
                computeAchievements(
                    brick,
                    coloredBrick,
                    cleared,
                    blockRemoved,
                    cellsCleared,
                    clearedRowIndices,
                    clearedColIndices,
                    position
                )
            }
        }
        return cleared
    }

    private suspend fun computeAchievements(
        brick: Brick,
        coloredBrick: ColoredBrick,
        cleared: Int,
        blockRemoved: Boolean,
        cellsCleared: Int,
        clearedRowIndices: List<Int>,
        clearedColIndices: List<Int>,
        position: IntOffset
    ) {
        val currentRecord = gameRepository.getBlockAchievement(brick)?.maxLinesCleared ?: 0
        val isNewRecord = cleared > currentRecord
        val minCells = brick.minCellsToClear(game.value.board.width, game.value.board.height)
        val isMinimalist = blockRemoved && cellsCleared == minCells
        val isThin = min(brick.width, brick.height) == 1

        // Corner Achievement Logic
        var isAroundTheCorner = false
        var isLargeCorner = false
        var isHugeCorner = false
        var isWideCorner = false
        var isNotEvenAround = false
        var isLargeWideCorner = false

        if (clearedRowIndices.isNotEmpty() && clearedColIndices.isNotEmpty()) {
            val intersections = clearedRowIndices.flatMap { y ->
                clearedColIndices.map { x -> IntOffset(x, y) }
            }
            val brickPositions = brick.positionList().map { it + position }.toSet()

            if (intersections.none { it in brickPositions }) {
                isAroundTheCorner = true

                val neighbors = intersections.flatMap { intersect ->
                    listOf(
                        IntOffset(intersect.x - 1, intersect.y),
                        IntOffset(intersect.x + 1, intersect.y),
                        IntOffset(intersect.x, intersect.y - 1),
                        IntOffset(intersect.x, intersect.y + 1)
                    )
                }.toSet()

                val neighborCount = neighbors.count { it in brickPositions }

                if (cleared >= 3) isLargeCorner = true
                if (cleared >= 4) isHugeCorner = true
                if (neighborCount == 1) isWideCorner = true
                if (neighborCount == 0) isNotEvenAround = true
                if (cleared >= 3 && neighborCount == 1) isLargeWideCorner = true
            }
        }

        val newAchievementData = com.flo.blocks.data.BlockAchievement(
            brick,
            if (isNewRecord) cleared else 0,
            blockRemoved,
            isMinimalist,
            isAroundTheCorner,
            isLargeCorner,
            isHugeCorner,
            isWideCorner,
            isNotEvenAround,
            isLargeWideCorner
        )
        gameRepository.updateAchievement(newAchievementData)

        if ((blockRemoved && achievementShowComeAndGone.shouldShow(isThin)) or (isNewRecord && achievementShowNewRecord) or (isMinimalist && achievementShowMinimalist.shouldShow(
                isThin
            )) or (cleared > 1 && achievementShowClearedLines) or (isAroundTheCorner && achievementShowAroundTheCorner)
        ) {
            _achievementEvents.emit(
                Achievement(
                    coloredBrick,
                    cleared,
                    isNewRecord,
                    blockRemoved,
                    isMinimalist,
                    isAroundTheCorner,
                    isLargeCorner,
                    isHugeCorner,
                    isWideCorner,
                    isNotEvenAround,
                    isLargeWideCorner
                )
            )
        }
    }

    fun newGame() {
        newGame(game.value.board.width, game.value.board.height)
    }

    fun newGame(width: Int, height: Int) {
        updateHighscore()
        val newState = GameState(ColoredBoard(width, height))
        stopComputation()
        history.clear()
        lastGameState.value = null
        game.value = newState
        currentEval.value = newState.board.board().evaluate()
        gameStateIndex = 0 // Reset index for new game
        viewModelScope.launch {
            gameRepository.newGame()
            gameRepository.saveGameState(newState, gameStateIndex)
        }

        hintRequested.value = false
        if (computeEnabled == ComputeEnabled.Auto || showBestEval) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        canUndo.value = canUndo()
    }

    fun canUndo(): Boolean {
        return history.isNotEmpty() && when (undoEnabled) {
            UndoEnabled.Always -> true
            UndoEnabled.UnlessNewBlocks -> game.value.bricks.any { it == null }
            UndoEnabled.Never -> false
        }
    }

    fun undo(): Boolean {
        if (!canUndo()) return false
        stopComputation()
        game.value = history.pop()
        currentEval.value = game.value.board.board().evaluate()
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
        hintRequested.value = false
        if (computeEnabled == ComputeEnabled.Auto || showBestEval) {
            startComputation(game.value.bricks.filterNotNull().map { it.brick })
        }
        return canStillUndo
    }

    fun saveBoardSize(width: Int, height: Int) {
        viewModelScope.launch { settingsRepository.saveBoardSize(width, height) }
    }

    /** locks: computationStartState, moves, nextMove, movesScore */
    private val mutex = Mutex()
    private var moves: List<OffsetBrick>? = null
    private var movesScore = Float.NEGATIVE_INFINITY
    val bestEval: MutableStateFlow<Float?> = MutableStateFlow(null)
    val currentEval: MutableStateFlow<Float?> = MutableStateFlow(null)
    val nextMove: MutableStateFlow<OffsetBrick?> = MutableStateFlow(null)
    val hintRequested = MutableStateFlow(false)

    fun requestHint() {
        hintRequested.value = true
        moves?.let {
            if (it.isNotEmpty()) {
                nextMove.value = it[0]
            }
        }
    }

    //    val currentMove = MutableStateFlow("")
    val progress = MutableStateFlow(1f)
    private var currentState: Pair<Board, List<Brick>>? = null
    private var job: Job? = null

    /**
     * forceClearBeforeLast: when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first. Only
     * exception: A cross can be cleared by placing the first block later. We are willing to accept
     * this for a ~3 times speed increase.
     */
    private suspend fun computeParallel(
        board: Board, bricks: List<Brick>, previousMoves: List<OffsetBrick>
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
        board: Board, bricks: List<Brick>, previousMoves: List<OffsetBrick>
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
                    bestEval.value = myScore
                    if (computeEnabled == ComputeEnabled.Auto || hintRequested.value) {
                        nextMove.value = myMoves[0]
                    }
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            // recursively set blocks
            computeSync(newBoard, remainingBricks, myMoves)
        }
    }

    /**
     * forceClearBeforeLast: when there is no block removed by beginning with the latter blocks,
     * this state could in most cases also be achieved by placing the first block first. Only
     * exception: A cross can be cleared by placing the first block later. We are willing to accept
     * this for a ~3 times speed increase.
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
                    bestEval.value = myScore
                    if (computeEnabled == ComputeEnabled.Auto || hintRequested.value) {
                        nextMove.value = myMoves[0].toOffsetBrick()
                    }
                }
            }
        } else if (!(forceClearBeforeLast && cleared == 0 && remainingBricks.size == 1)) {
            // recursively set blocks
            computeSyncBit(newBoard, remainingBricks, myMoves)
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
                bestEval.value = null
                movesScore = Float.NEGATIVE_INFINITY
                job?.join()
                job = viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        if (game.value.board.width * game.value.board.height <= 64) {
                            val context = BitContext(
                                IntOffset(
                                    game.value.board.width, game.value.board.height
                                )
                            )
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
