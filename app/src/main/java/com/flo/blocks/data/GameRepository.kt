package com.flo.blocks.data

import com.flo.blocks.game.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class GameRepository(private val gameDao: GameDao) {

    private var currentGameId: Int? = null

    open suspend fun initialize() {
        withContext(Dispatchers.IO) {
            currentGameId = gameDao.getCurrentGameId()
            if (currentGameId == null) {
                currentGameId = gameDao.insertGame(Game()).toInt()
            }
        }
    }

    open suspend fun saveGameState(state: GameState, index: Int) {
        val gameId = currentGameId ?: return
        withContext(Dispatchers.IO) {
            gameDao.setGameState(gameId, index, GameStateEntity(gameId, index, state))
        }
    }

    open suspend fun getLatestState(): Pair<GameState, Int>? {
        val gameId = currentGameId ?: return null
        return withContext(Dispatchers.IO) {
            val entity = gameDao.getLatestState(gameId) ?: return@withContext null
            Pair(entity.data, entity.stateIndex)
        }
    }

    open suspend fun getHistory(): List<GameState> {
        val gameId = currentGameId ?: return emptyList()
        return withContext(Dispatchers.IO) {
            gameDao.getHistory(gameId).map { it.data }
        }
    }

    open suspend fun newGame() {
        withContext(Dispatchers.IO) {
            currentGameId = gameDao.insertGame(Game()).toInt()
        }
    }
}
