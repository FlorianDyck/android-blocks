package com.flo.blocks

import com.flo.blocks.data.SettingsRepository
import com.flo.blocks.data.GameRepository
import com.flo.blocks.GameViewModel.ComputeEnabled
import com.flo.blocks.GameViewModel.UndoEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
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
    }

    class FakeGameRepository : GameRepository(mock(), mock()) {
        private val achievements = mutableMapOf<com.flo.blocks.game.Brick, Int>()
        override suspend fun initialize() {}
        override suspend fun getLatestState(): Pair<com.flo.blocks.game.GameState, Int>? = null
        override suspend fun getHistory(): List<com.flo.blocks.game.GameState> = emptyList()
        override suspend fun saveGameState(state: com.flo.blocks.game.GameState, index: Int) {}
        override suspend fun newGame() {}
        override suspend fun getBlockAchievement(brick: com.flo.blocks.game.Brick): com.flo.blocks.data.BlockAchievement? {
            return achievements[brick]?.let { com.flo.blocks.data.BlockAchievement(brick, it) }
        }
        override suspend fun updateBlockAchievement(brick: com.flo.blocks.game.Brick, lines: Int) {
            achievements[brick] = lines
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

        // Setup a state where placing a brick clears 2 lines
        val board = com.flo.blocks.game.ColoredBoard(8, 8)
        // Fill first two lines except for (0,0) and (0,1)
        for (y in 0..1) {
            for (x in 1..7) {
                board[x, y] = com.flo.blocks.game.BlockColor.BLUE
            }
        }
        
        val brick = com.flo.blocks.game.rect(0, 0, 0, 1) // 1x2 vertical brick
        val coloredBrick = com.flo.blocks.game.ColoredBrick(brick, com.flo.blocks.game.BlockColor.RED)
        
        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)
        
        // Place the brick at (0,0) to clear 2 lines
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals("New Record! 2 lines cleared!", events[0].message)
        assertEquals(brick, events[0].brick.brick)
        assertEquals(com.flo.blocks.game.BlockColor.RED, events[0].brick.color)
        
        // Place again (setup same state)
        viewModel.game.value = com.flo.blocks.game.GameState(board, arrayOf(coloredBrick, null, null), 0)
        viewModel.placeBrick(0, androidx.compose.ui.unit.IntOffset(0, 0))
        advanceUntilIdle()
        
        // Should be "Well done" now as 2 is not > 2
        assertEquals(2, events.size)
        assertEquals("Well done! 2 lines cleared!", events[1].message)
        
        job.cancel()
    }
}
