package com.flo.blocks

import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flo.blocks.data.AchievementFilter
import com.flo.blocks.data.GameRepository
import com.flo.blocks.data.SettingsRepository
import com.flo.blocks.game.AchievementEvent
import com.flo.blocks.game.AchievementFlags
import com.flo.blocks.game.Board
import com.flo.blocks.game.Brick
import com.flo.blocks.game.ColoredBoard
import com.flo.blocks.game.ColoredBrick
import com.flo.blocks.game.GameState
import com.flo.blocks.game.MoveCalculator
import com.flo.blocks.game.MoveSequence
import com.flo.blocks.game.OffsetBrick
import com.flo.blocks.game.findBestGreedySequence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.math.pow

class GameViewModel(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
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
    var showGreedyGapInfo = false
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveShowGreedyGapInfo(value) }
            if (value) {
                startComputation(game.value.bricks.filterNotNull().map { it.brick })
            }
        }
    var congratulateBestMove = false
        set(value) {
            field = value
            viewModelScope.launch { settingsRepository.saveCongratulateBestMove(value) }
            if (value) {
                startComputation(game.value.bricks.filterNotNull().map { it.brick })
            }
        }
    val achievementAlpha = MutableStateFlow(0.9f)

    fun setAchievementAlpha(alpha: Float) {
        viewModelScope.launch { settingsRepository.saveAchievementAlpha(alpha) }
    }

    val moveCalculator: MoveCalculator = MoveCalculator()

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
            updateEvalBaselines(width, height)
            currentEval.value = latestState.board.board().evaluate().normalize()
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
            showGreedyGapInfo = settingsRepository.showGreedyGapInfoFlow.first()
            congratulateBestMove = settingsRepository.congratulateBestMoveFlow.first()
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
            viewModelScope.launch {
                settingsRepository.showGreedyGapInfoFlow.collect { showGreedyGapInfo = it }
            }
            viewModelScope.launch {
                settingsRepository.congratulateBestMoveFlow.collect { congratulateBestMove = it }
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

    val showUndo = canUndo.combine(showUndoIfEnabled) { a, b -> a && b }

    val highscore = MutableStateFlow(0)

    private val _achievementEvents = MutableSharedFlow<AchievementEvent>()
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
        currentEval.value = newState.board.board().evaluate().normalize()
        gameStateIndex++

        viewModelScope.launch { gameRepository.saveGameState(newState, gameStateIndex) }

        hintRequested.value = false
        if (computeEnabled == ComputeEnabled.Auto || showBestEval || showGreedyGapInfo || congratulateBestMove) {
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
        val oldBestEval = bestEval.value // capture best eval before move

        val (nextState, blockRemoved, cellsCleared, clearedRowIndices, clearedColIndices) = game.value.place(
            index, position
        )
        updateGameState(nextState)

        val cleared = game.value.score - oldScore

        viewModelScope.launch {
            computeAchievements(
                brick,
                coloredBrick,
                cleared,
                blockRemoved,
                cellsCleared,
                clearedRowIndices,
                clearedColIndices,
                position,
                oldBestEval
            )
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
        position: IntOffset,
        oldBestEval: Float?
    ) {
        val currentRecord = gameRepository.getBlockAchievement(brick)?.maxLinesCleared ?: 0
        val isNewRecord = cleared > currentRecord
        val isThin = min(brick.width, brick.height) == 1

        val flags = AchievementFlags.calculate(
            brick = brick,
            position = position,
            clearedRowIndices = clearedRowIndices,
            clearedColIndices = clearedColIndices,
            cellsCleared = cellsCleared,
            blockRemoved = blockRemoved,
            linesCleared = cleared,
            boardWidth = game.value.board.width,
            boardHeight = game.value.board.height
        )

        val newAchievementData = com.flo.blocks.data.BlockAchievement(
            brick,
            if (isNewRecord) cleared else 0,
            blockRemoved,
            flags.isMinimalist,
            flags.isAroundTheCorner,
            flags.isLargeCorner,
            flags.isHugeCorner,
            flags.isWideCorner,
            flags.isNotEvenAround,
            flags.isLargeWideCorner
        )
        gameRepository.updateAchievement(newAchievementData)

        viewModelScope.launch {
            val evalToCheck =
                if (game.value.bricks.all { it == null } || game.value.bricks.all { it != null }) {
                    currentEval.value
                } else {
                    job?.join()
                    bestEval.value
                }

            val isBestMove =
                oldBestEval != null && evalToCheck != null && evalToCheck >= oldBestEval - 0.001f

            if ((blockRemoved && achievementShowComeAndGone.shouldShow(isThin)) or (isNewRecord && achievementShowNewRecord) or (flags.isMinimalist && achievementShowMinimalist.shouldShow(
                    isThin
                )) or (cleared > 1 && achievementShowClearedLines) or (flags.isAroundTheCorner && achievementShowAroundTheCorner) or (congratulateBestMove && isBestMove)
            ) {
                _achievementEvents.emit(
                    AchievementEvent(
                        coloredBrick,
                        cleared,
                        isNewRecord,
                        blockRemoved,
                        flags,
                        isBestMove
                    )
                )
            }
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
        updateEvalBaselines(width, height)
        currentEval.value = newState.board.board().evaluate().normalize()
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
        currentEval.value = game.value.board.board().evaluate().normalize()
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
    private var movesScore: MoveSequence? = null
    val bestEval: MutableStateFlow<Float?> = MutableStateFlow(null)
    val greedyGap: MutableStateFlow<Float?> = MutableStateFlow(null)
    val currentEval: MutableStateFlow<Float?> = MutableStateFlow(null)
    private var minEval = 0f
    private var maxEval = 1f

    private fun updateEvalBaselines(width: Int, height: Int) {
        minEval = Board.calculateMinEval(width, height)
        maxEval = Board.calculateMaxEval(width, height)
    }

    private fun Float.normalize(): Float {
        if (maxEval == minEval) return 0f
        val ratio = (this - minEval) / (maxEval - minEval)
        return ratio.coerceIn(0f, 1f).pow(10f) * 100f
    }

    val nextMove: MutableStateFlow<OffsetBrick?> = MutableStateFlow(null)
    val hintRequested = MutableStateFlow(false)

    fun requestHint() {
        hintRequested.value = true
        startComputation(game.value.bricks.filterNotNull().map { it.brick })
        moves?.let {
            if (it.isNotEmpty()) {
                nextMove.value = it[0]
            }
        }
    }

    val progress = MutableStateFlow(1f)
    private var currentState: Pair<Board, List<Brick>>? = null
    private var job: Job? = null

    fun startComputation(bricks: List<Brick>) {

        val computationStartState = Pair(game.value.board.board(), bricks)

        viewModelScope.launch {
            mutex.withLock {
                if (computationStartState == currentState) {
                    // If we are in Auto mode or a hint was requested, ensure the best move (if
                    // computed) is shown.
                    // This handles cases where we switch to Auto mode after computation is already
                    // done.
                    if (computeEnabled == ComputeEnabled.Auto || hintRequested.value) {
                        moves?.let { if (it.isNotEmpty()) nextMove.value = it[0] }
                    }
                    return@launch
                }
                currentState = computationStartState
                moves = null
                nextMove.value = null
                bestEval.value = null
                greedyGap.value = null
                movesScore = null
                job?.join()
                job = viewModelScope.launch {
                    withContext(defaultDispatcher) {
                        var greedyScore: Float? = null
                        if (showGreedyGapInfo) {
                            greedyScore = findBestGreedySequence(computationStartState.first, bricks)
                        }

                        moveCalculator.compute(
                            computationStartState.first,
                            bricks,
                            onProgress = { progress.value = it },
                            onNewBest = { bestSeq ->
                                mutex.withLock {
                                    if (movesScore == null || bestSeq > movesScore!!) {
                                        moves = bestSeq.moves
                                        movesScore = bestSeq
                                        bestEval.value = bestSeq.finalEval.normalize()
                                        if (computeEnabled == ComputeEnabled.Auto || hintRequested.value) {
                                            nextMove.value = bestSeq.moves[0]
                                        }
                                        if (showGreedyGapInfo && greedyScore != null) {
                                            greedyGap.value =
                                                bestSeq.finalEval.normalize() - greedyScore.normalize()
                                        }
                                    }
                                }
                            })
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
