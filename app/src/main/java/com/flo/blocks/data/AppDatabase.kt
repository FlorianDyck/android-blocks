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

@Database(entities = [Game::class, GameStateEntity::class], version = 1, exportSchema = false)
@TypeConverters(GameStateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
