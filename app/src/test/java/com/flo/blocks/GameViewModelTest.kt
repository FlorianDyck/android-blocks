package com.flo.blocks

import com.flo.blocks.GameViewModel.ComputeEnabled
import com.flo.blocks.GameViewModel.UndoEnabled
import com.flo.blocks.data.AchievementFilter
import com.flo.blocks.data.GameRepository
import com.flo.blocks.data.SettingsRepository
import com.flo.blocks.game.canonical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var gameRepository: GameRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mock()
        gameRepository = FakeGameRepository()

        // Default mocks
        whenever(settingsRepository.computeEnabledFlow).thenReturn(flowOf(ComputeEnabled.Hidden))
        whenever(settingsRepository.undoEnabledFlow).thenReturn(flowOf(UndoEnabled.Always))
        whenever(settingsRepository.showUndoIfEnabledFlow).thenReturn(flowOf(true))
        whenever(settingsRepository.showNewGameButtonFlow).thenReturn(flowOf(false))
        whenever(settingsRepository.boardWidthFlow).thenReturn(flowOf(8))
        whenever(settingsRepository.boardHeightFlow).thenReturn(flowOf(8))
        whenever(settingsRepository.highscoreFlow).thenReturn(flowOf(0))
        whenever(settingsRepository.achievementShowMinimalistFlow)
            .thenReturn(flowOf(AchievementFilter.Always))
        whenever(settingsRepository.achievementShowComeAndGoneFlow)
            .thenReturn(flowOf(AchievementFilter.Always))
        whenever(settingsRepository.achievementShowNewRecordFlow).thenReturn(flowOf(true))
        whenever(settingsRepository.achievementShowClearedLinesFlow)
            .thenReturn(flowOf(true))
        whenever(settingsRepository.achievementAlphaFlow).thenReturn(flowOf(0.9f))
    }

    class FakeGameRepository : GameRepository(mock(), mock()) {
        private val records = mutableMapOf<com.flo.blocks.game.Brick, Int>()
        private val comeAndGones = mutableSetOf<com.flo.blocks.game.Brick>()
        private val minimalists = mutableSetOf<com.flo.blocks.game.Brick>()

        override suspend fun initialize() {}
        override suspend fun getLatestState(): Pair<com.flo.blocks.game.GameState, Int>? = null
        override suspend fun getHistory(): List<com.flo.blocks.game.GameState> = emptyList()
        override suspend fun saveGameState(state: com.flo.blocks.game.GameState, index: Int) {}
        override suspend fun newGame() {}
        override suspend fun getBlockAchievement(brick: com.flo.blocks.game.Brick): com.flo.blocks.data.BlockAchievement? {
            val canonical = brick.canonical
            return com.flo.blocks.data.BlockAchievement(
                canonical,
                records[canonical] ?: 0,
                comeAndGones.contains(canonical),
                minimalists.contains(canonical)
            )
        }
        override suspend fun updateBlockAchievement(brick: com.flo.blocks.game.Brick, lines: Int) {
            records[brick.canonical] = lines
        }
        override suspend fun markComeAndGone(brick: com.flo.blocks.game.Brick) {
            comeAndGones.add(brick.canonical)
        }
        override suspend fun markMinimalist(brick: com.flo.blocks.game.Brick) {
            minimalists.add(brick.canonical)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads board dimensions from repository`() = runTest(testDispatcher) {
        whenever(settingsRepository.boardWidthFlow).thenReturn(flowOf(10))
        whenever(settingsRepository.boardHeightFlow).thenReturn(flowOf(15))

        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        assertEquals(10, viewModel.game.value.board.width)
        assertEquals(15, viewModel.game.value.board.height)
    }

    @Test
    fun `saveBoardSize saves dimensions to repository`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        viewModel.saveBoardSize(12, 12)
        advanceUntilIdle()

        verify(settingsRepository).saveBoardSize(12, 12)
    }

    @Test
    fun `placeBrick triggers achievement message on multi-line clear`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // Setup a state where placing a brick clears 2 lines but NOT the whole brick
        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        // Fill first two lines except for (0,0) and (0,1)
        for (y in 0..1) {
            for (x in 1..7) {
                board[x, y] = com.flo.blocks.game.BlockColor.BLUE
            }
        }
        // Add a block that prevents (0,1) from being cleared by a row clear
        // Wait, rows clear 0..7. If (0,1) is part of the brick, it's cleared if line 1 is full.
        // To NOT remove the whole brick, we need the brick to have a tile in a line/row that is NOT cleared.
        // Let's use a 2x2 brick and clear only two lines.
        // Or just let it be Come and Gone and update expectation.

        val brick = com.flo.blocks.game.rect(0, 0, 0, 1) // 1x2 vertical brick
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)

        // Place the brick at (0,0) to clear 2 lines
        // This triggers Come and Gone because both tiles (0,0) and (0,1) are in cleared lines 0 and 1
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(true, events[0].blockRemoved)
        assertEquals(true, events[0].isNewRecord)
        assertEquals(2, events[0].cleared)
        assertEquals(brick, events[0].brick.brick)

        // Place again (setup same state)
        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        // Should be just Come and Gone (not a new record)
        assertEquals(2, events.size)
        assertEquals(true, events[1].blockRemoved)
        assertEquals(false, events[1].isNewRecord)
        assertEquals(2, events[1].cleared)

        job.cancel()
    }

    @Test
    fun `placeBrick triggers come and gone and merges message`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // 1x1 brick
        val brick = com.flo.blocks.game.field(0, 0)
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        // Board with one empty spot at (0,0) which completes both row 0 and line 0
        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        for (i in 1..7) board[i, 0] = com.flo.blocks.game.BlockColor.BLUE
        for (i in 1..7) board[0, i] = com.flo.blocks.game.BlockColor.BLUE

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)

        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(1, events.size)
        val ach = events[0]
        assertEquals(true, ach.blockRemoved)
        assertEquals(true, ach.isNewRecord)
        assertEquals(2, ach.cleared)

        // Verify repository updated
        val achievement = gameRepository.getBlockAchievement(brick)
        assertEquals(2, achievement?.maxLinesCleared)
        assertEquals(true, achievement?.comeAndGone)

        job.cancel()
    }

    @Test
    fun `achievements are independent of rotation`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // Setup a state where placing a brick clears 2 lines
        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        for (y in 0..1) {
            for (x in 1..7) {
                board[x, y] = com.flo.blocks.game.BlockColor.BLUE
            }
        }

        val brick = com.flo.blocks.game.rect(0, 0, 0, 1) // 1x2 vertical brick
        val rotatedBrick = brick.rotate() // 2x1 horizontal brick
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)
        val rotatedColoredBrick = com.flo.blocks.game.ColoredBrick(rotatedBrick, com.flo.blocks.game.BlockColor.GREEN)

        // 1. Place vertical brick to clear 2 lines
        // Also triggers Come and Gone
        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        val ach1 = events.last()
        assertEquals(true, ach1.blockRemoved)
        assertEquals(true, ach1.isNewRecord)
        assertEquals(2, ach1.cleared)

        // 2. Place horizontal brick (rotated) to clear 2 lines
        // Setup state for horizontal clear
        val board2 = com.flo.blocks.game.ColoredBoard(8, 8)
        for (x in 0..1) {
            for (y in 1..7) {
                board2[x, y] = com.flo.blocks.game.BlockColor.BLUE
            }
        }
        viewModel.game.value = com.flo.blocks.game.GameState(board2, arrayOf(rotatedColoredBrick, null, null), 0)
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        // Should be Come and Gone (not new record)
        val ach2 = events.last()
        assertEquals(true, ach2.blockRemoved)
        assertEquals(false, ach2.isNewRecord)
        assertEquals(2, ach2.cleared)

        job.cancel()
    }

    @Test
    fun `placeBrick triggers random congrats when no achievements`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // Setup a 3x3 brick and clear 2 lines that DON'T cover the whole brick
        val brick = com.flo.blocks.game.rect(0, 0, 2, 2)
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        // Fill lines 0 and 1 except for (0,0), (1,0), (2,0) and (0,1), (1,1), (2,1)
        for (y in 0..1) {
            for (x in 3..7) {
                board[x, y] = com.flo.blocks.game.BlockColor.BLUE
            }
        }

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)

        // First time: New Record
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()
        val ach1 = events.last()
        assertEquals(false, ach1.blockRemoved)
        assertEquals(true, ach1.isNewRecord)
        assertEquals(2, ach1.cleared)

        // Second time: Random congrats (represented by no recording + no blockRemoved)
        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        val ach2 = events.last()
        assertEquals(false, ach2.blockRemoved)
        assertEquals(false, ach2.isNewRecord)
        assertEquals(2, ach2.cleared)

        job.cancel()
    }

    @Test
    fun `placeBrick triggers minimalist achievement for L-shape`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // 3x3 L-shape brick (user example): 0,0, 0,1, 0,2, 1,2, 2,2
        // Min cells to clear: 1 row + 1 col = 1*8 + 1*8 - 1 = 15 cells.
        val brick = com.flo.blocks.game.rect(0, 0, 0, 2) + com.flo.blocks.game.rect(0, 2, 2, 2)
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        // Setup state to clear col 0 and row 2
        for (y in 0..7) if (y != 2) board[0, y] = com.flo.blocks.game.BlockColor.BLUE
        for (x in 0..7) if (x != 0) board[x, 2] = com.flo.blocks.game.BlockColor.BLUE

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)

        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(1, events.size)
        val ach = events[0]
        assertEquals(true, ach.blockRemoved)
        assertEquals(true, ach.isMinimalist)

        job.cancel()
    }

    @Test
    fun `placeBrick triggers minimalist achievement for 2x2 block`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        val events = mutableListOf<GameViewModel.Achievement>()
        val job = launch {
            viewModel.achievementEvents.collect { events.add(it) }
        }

        // 2x2 block: 0,0, 1,0, 0,1, 1,1
        // Min cells to clear: 2 rows = 16 cells OR 2 cols = 16 cells.
        val brick = com.flo.blocks.game.rect(0, 0, 1, 1)
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        // Setup state to clear row 0 and row 1
        for (x in 0..7) board[x, 0] = com.flo.blocks.game.BlockColor.BLUE
        for (x in 0..7) board[x, 1] = com.flo.blocks.game.BlockColor.BLUE

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)

        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(1, events.size)
        val ach = events[0]
        assertEquals(true, ach.blockRemoved)
        assertEquals(true, ach.isMinimalist)

        job.cancel()
    }

    @Test
    fun `highscore is detected on loss and saved on new game`() = runTest(testDispatcher) {
        whenever(settingsRepository.highscoreFlow).thenReturn(flowOf(10))
        val viewModel = GameViewModel(settingsRepository, gameRepository)
        advanceUntilIdle()

        assertEquals(10, viewModel.highscore.value)

        // 1. Achieve highscore but not lost
        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        for (x in 1..7) board[x, 0] = com.flo.blocks.game.BlockColor.BLUE
        val brick = com.flo.blocks.game.rect(0, 0, 0, 0) // 1x1
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)

        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 10)

        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(10, viewModel.highscore.value)

        // 2. Force a loss state and verify detection
        // We'll create a new GameState that IS lost and has a high score
        val lostBoard = com.flo.blocks.game.ColoredBoard(1, 1).apply { set(0, 0, com.flo.blocks.game.BlockColor.BLUE) }
        // 2x2 brick cannot be placed on 1x1 board
        val unplaceableBrick = com.flo.blocks.game.ColoredBrick(com.flo.blocks.game.rect(0, 0, 1, 1), com.flo.blocks.game.BlockColor.RED)
        val lostState = com.flo.blocks.game.GameState(lostBoard, arrayOf(unplaceableBrick, null, null), 12)

        // We can't easily trigger this via placeBrick because it refills randomly.
        // But we can test that checkHighscore sets the flag and newGame saves it.
        // Actually, let's just test the public behavior of newGame which includes checkHighscore.

        viewModel.game.value = lostState
        viewModel.newGame(8, 8)
        advanceUntilIdle()

        assertEquals(12, viewModel.highscore.value)
        // highscoreReached is false after newGame starts
        verify(settingsRepository).saveHighscore(12)
    }
}
