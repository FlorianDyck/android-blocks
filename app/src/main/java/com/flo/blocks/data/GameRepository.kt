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
                canonicalAchievements[canonicalBrick] =
                        maxOf(currentMax, achievement.maxLinesCleared)
            }

            if (needsMigration) {
                blockAchievementDao.clearAllAchievements()
                for ((brick, maxLines) in canonicalAchievements) {
                    blockAchievementDao.upsertAchievement(
                            BlockAchievement(
                                    brick,
                                    maxLines,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false,
                                    false
                            )
                    )
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
        return withContext(Dispatchers.IO) { gameDao.getHistory(gameId).map { it.data } }
    }

    open suspend fun newGame() {
        withContext(Dispatchers.IO) { currentGameId = gameDao.insertGame(Game()).toInt() }
    }

    open suspend fun getBlockAchievement(brick: Brick): BlockAchievement? {
        return withContext(Dispatchers.IO) { blockAchievementDao.getAchievement(brick.canonical) }
    }

    open suspend fun updateAchievement(newAchievementData: BlockAchievement) {
        withContext(Dispatchers.IO) {
            val canonicalBrick = newAchievementData.brick.canonical
            val current = blockAchievementDao.getAchievement(canonicalBrick)

            val newMaxLines =
                    maxOf(current?.maxLinesCleared ?: 0, newAchievementData.maxLinesCleared)
            val newComeAndGone = (current?.comeAndGone ?: false) || newAchievementData.comeAndGone
            val newMinimalist = (current?.minimalist ?: false) || newAchievementData.minimalist
            val newAroundTheCorner =
                    (current?.aroundTheCorner ?: false) || newAchievementData.aroundTheCorner
            val newLargeCorner = (current?.largeCorner ?: false) || newAchievementData.largeCorner
            val newHugeCorner = (current?.hugeCorner ?: false) || newAchievementData.hugeCorner
            val newWideCorner = (current?.wideCorner ?: false) || newAchievementData.wideCorner
            val newNotEvenAround =
                    (current?.notEvenAround ?: false) || newAchievementData.notEvenAround
            val newLargeWideCorner =
                    (current?.largeWideCorner ?: false) || newAchievementData.largeWideCorner

            val mergedAchievement =
                    BlockAchievement(
                            canonicalBrick,
                            newMaxLines,
                            newComeAndGone,
                            newMinimalist,
                            newAroundTheCorner,
                            newLargeCorner,
                            newHugeCorner,
                            newWideCorner,
                            newNotEvenAround,
                            newLargeWideCorner
                    )

            if (current != mergedAchievement) {
                blockAchievementDao.upsertAchievement(mergedAchievement)
            }
        }
    }

    open suspend fun getAllAchievements(): List<BlockAchievement> {
        return withContext(Dispatchers.IO) { blockAchievementDao.getAllAchievements() }
    }
}
