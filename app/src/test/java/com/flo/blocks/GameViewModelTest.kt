package com.flo.blocks

import com.flo.blocks.data.SettingsRepository
import com.flo.blocks.data.GameRepository
import com.flo.blocks.data.GameDao
import com.flo.blocks.GameViewModel.ComputeEnabled
import com.flo.blocks.GameViewModel.UndoEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.doReturn

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
        whenever(settingsRepository.boardWidthFlow).thenReturn(flowOf(8))
        whenever(settingsRepository.boardHeightFlow).thenReturn(flowOf(8))
    }

    class FakeGameRepository : GameRepository(mock()) {
        override suspend fun initialize() {}
        override suspend fun getLatestState(): Pair<com.flo.blocks.game.GameState, Int>? = null
        override suspend fun getHistory(): List<com.flo.blocks.game.GameState> = emptyList()
        override suspend fun saveGameState(state: com.flo.blocks.game.GameState, index: Int) {}
        override suspend fun newGame() {}
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
}
