package com.flo.blocks.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flo.blocks.game.Brick
import com.flo.blocks.game.GameState
import com.google.gson.Gson

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val gameId: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "game_states",
    primaryKeys = ["gameId", "stateIndex"],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GameStateEntity(
    val gameId: Int,
    val stateIndex: Int,
    val data: GameState // Will be converted to JSON
)

@Entity(tableName = "block_achievements")
data class BlockAchievement(
    @PrimaryKey val brick: Brick,
    val maxLinesCleared: Int = 0,
    val comeAndGone: Boolean = false,
    val minimalist: Boolean = false
)

class BrickConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromBrick(brick: Brick): String {
        return gson.toJson(brick)
    }

    @TypeConverter
    fun toBrick(json: String): Brick {
        return gson.fromJson(json, Brick::class.java)
    }
}

class GameStateConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromGameState(gameState: GameState): String {
        return gson.toJson(gameState)
    }

    @TypeConverter
    fun toGameState(json: String): GameState {
        return gson.fromJson(json, GameState::class.java)
    }
}

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameState(gameState: GameStateEntity)

    @Query("SELECT MAX(gameId) FROM games")
    suspend fun getCurrentGameId(): Int?

    @Query("SELECT * FROM game_states WHERE gameId = :gameId ORDER BY stateIndex DESC LIMIT 1")
    suspend fun getLatestState(gameId: Int): GameStateEntity?

    @Query("SELECT * FROM game_states WHERE gameId = :gameId ORDER BY stateIndex ASC")
    suspend fun getHistory(gameId: Int): List<GameStateEntity>

    @Query("DELETE FROM game_states WHERE gameId = :gameId AND stateIndex > :maxIndex")
    suspend fun deleteStatesAfter(gameId: Int, maxIndex: Int)

    @Query("DELETE FROM games")
    suspend fun clearAllGames()

    @androidx.room.Transaction
    suspend fun setGameState(gameId: Int, index: Int, state: GameStateEntity) {
        deleteStatesAfter(gameId, index)
        insertGameState(state)
    }
}

@Dao
interface BlockAchievementDao {
    @Query("SELECT * FROM block_achievements WHERE brick = :brick")
    suspend fun getAchievement(brick: Brick): BlockAchievement?

    @Query("SELECT * FROM block_achievements")
    suspend fun getAllAchievements(): List<BlockAchievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAchievement(achievement: BlockAchievement)

    @Query("DELETE FROM block_achievements")
    suspend fun clearAllAchievements()
}

@Database(
    entities = [Game::class, GameStateEntity::class, BlockAchievement::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(GameStateConverter::class, BrickConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun blockAchievementDao(): BlockAchievementDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `block_achievements` (`brick` TEXT NOT NULL, `maxLinesCleared` INTEGER NOT NULL, PRIMARY KEY(`brick`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `block_achievements` ADD COLUMN `comeAndGone` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `block_achievements` ADD COLUMN `minimalist` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
