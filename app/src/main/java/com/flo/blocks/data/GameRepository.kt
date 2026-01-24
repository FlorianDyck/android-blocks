package com.flo.blocks.data

import com.flo.blocks.game.Brick
import com.flo.blocks.game.GameState
import com.flo.blocks.game.canonical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class GameRepository(
    private val gameDao: GameDao,
    private val blockAchievementDao: BlockAchievementDao
) {

    private var currentGameId: Int? = null

    open suspend fun initialize() {
        withContext(Dispatchers.IO) {
            currentGameId = gameDao.getCurrentGameId()
            if (currentGameId == null) {
                currentGameId = gameDao.insertGame(Game()).toInt()
            }
            
            // Migrate achievements to canonical form
            val allAchievements = blockAchievementDao.getAllAchievements()
            val canonicalAchievements = mutableMapOf<Brick, Int>()
            var needsMigration = false
            
            for (achievement in allAchievements) {
                val canonicalBrick = achievement.brick.canonical
                if (achievement.brick != canonicalBrick) {
                    needsMigration = true
                }
                val currentMax = canonicalAchievements[canonicalBrick] ?: 0
                canonicalAchievements[canonicalBrick] = maxOf(currentMax, achievement.maxLinesCleared)
            }
            
            if (needsMigration) {
                blockAchievementDao.clearAllAchievements()
                for ((brick, maxLines) in canonicalAchievements) {
                    blockAchievementDao.upsertAchievement(BlockAchievement(brick, maxLines))
                }
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

    open suspend fun getBlockAchievement(brick: Brick): BlockAchievement? {
        return withContext(Dispatchers.IO) {
            blockAchievementDao.getAchievement(brick.canonical)
        }
    }

    open suspend fun updateBlockAchievement(brick: Brick, lines: Int) {
        withContext(Dispatchers.IO) {
            val canonicalBrick = brick.canonical
            val currentRecord = blockAchievementDao.getAchievement(canonicalBrick)?.maxLinesCleared ?: 0
            if (lines > currentRecord) {
                blockAchievementDao.upsertAchievement(BlockAchievement(canonicalBrick, lines))
            }
        }
    }

    open suspend fun getAllAchievements(): List<BlockAchievement> {
        return withContext(Dispatchers.IO) {
            blockAchievementDao.getAllAchievements()
        }
    }
}
