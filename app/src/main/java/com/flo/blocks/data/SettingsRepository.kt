package com.flo.blocks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flo.blocks.GameViewModel.ComputeEnabled
import com.flo.blocks.GameViewModel.UndoEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface SettingsRepository {
    val computeEnabledFlow: Flow<ComputeEnabled>
    val undoEnabledFlow: Flow<UndoEnabled>
    val showUndoIfEnabledFlow: Flow<Boolean>
    val showNewGameButtonFlow: Flow<Boolean>
    val boardWidthFlow: Flow<Int>
    val boardHeightFlow: Flow<Int>
    val highscoreFlow: Flow<Int>
    val achievementShowMinimalistFlow: Flow<AchievementFilter>
    val achievementShowComeAndGoneFlow: Flow<AchievementFilter>
    val achievementShowNewRecordFlow: Flow<Boolean>
    val achievementShowClearedLinesFlow: Flow<Boolean>
    val achievementShowAroundTheCornerFlow: Flow<Boolean>
    val achievementAlphaFlow: Flow<Float>
    val showBestEvalFlow: Flow<Boolean>
    val showCurrentEvalFlow: Flow<Boolean>
    val showGreedyGapInfoFlow: Flow<Boolean>
    val congratulateBestMoveFlow: Flow<Boolean>

    suspend fun saveComputeEnabled(computeEnabled: ComputeEnabled)
    suspend fun saveUndoEnabled(undoEnabled: UndoEnabled)
    suspend fun saveShowUndoIfEnabled(showUndo: Boolean)
    suspend fun saveShowNewGameButton(show: Boolean)
    suspend fun saveBoardSize(width: Int, height: Int)
    suspend fun saveHighscore(score: Int)
    suspend fun saveAchievementShowMinimalist(filter: AchievementFilter)
    suspend fun saveAchievementShowComeAndGone(filter: AchievementFilter)
    suspend fun saveAchievementShowNewRecord(show: Boolean)
    suspend fun saveAchievementShowClearedLines(show: Boolean)
    suspend fun saveAchievementShowAroundTheCorner(show: Boolean)
    suspend fun saveAchievementAlpha(alpha: Float)
    suspend fun saveShowBestEval(show: Boolean)
    suspend fun saveShowCurrentEval(show: Boolean)
    suspend fun saveShowGreedyGapInfo(show: Boolean)
    suspend fun saveCongratulateBestMove(show: Boolean)
}

enum class AchievementFilter {
    Always, ExceptThin, Never;

    fun shouldShow(isThin: Boolean): Boolean {
        return when (this) {
            Always -> true
            ExceptThin -> !isThin
            Never -> false
        }
    }
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val COMPUTE_ENABLED = stringPreferencesKey("compute_enabled")
        val UNDO_ENABLED = stringPreferencesKey("undo_enabled")
        val SHOW_UNDO_IF_ENABLED = booleanPreferencesKey("show_undo_if_enabled")
        val SHOW_NEW_GAME_BUTTON = booleanPreferencesKey("show_new_game_button")
        val BOARD_WIDTH = intPreferencesKey("board_width")
        val BOARD_HEIGHT = intPreferencesKey("board_height")
        val HIGHSCORE = intPreferencesKey("highscore")
        val ACHIEVEMENT_SHOW_MINIMALIST = stringPreferencesKey("achievement_show_minimalist")
        val ACHIEVEMENT_SHOW_COME_AND_GONE = stringPreferencesKey("achievement_show_come_and_gone")
        val ACHIEVEMENT_SHOW_NEW_RECORD = booleanPreferencesKey("achievement_show_new_record")
        val ACHIEVEMENT_SHOW_CLEARED_LINES = booleanPreferencesKey("achievement_show_cleared_lines")
        val ACHIEVEMENT_SHOW_AROUND_THE_CORNER =
            booleanPreferencesKey("achievement_show_around_the_corner")
        val ACHIEVEMENT_ALPHA =
            androidx.datastore.preferences.core.floatPreferencesKey("achievement_alpha")
        val SHOW_BEST_EVAL = booleanPreferencesKey("show_best_eval")
        val SHOW_CURRENT_EVAL = booleanPreferencesKey("show_current_eval")
        val SHOW_GREEDY_GAP_INFO = booleanPreferencesKey("show_greedy_gap_info")
        val CONGRATULATE_BEST_MOVE = booleanPreferencesKey("congratulate_best_move")
    }

    override val computeEnabledFlow: Flow<ComputeEnabled> =
        context.dataStore.data.map { preferences ->
            val computeEnabledString =
                preferences[PreferencesKeys.COMPUTE_ENABLED] ?: ComputeEnabled.Hidden.name
            ComputeEnabled.valueOf(computeEnabledString)
        }

    override val undoEnabledFlow: Flow<UndoEnabled> = context.dataStore.data.map { preferences ->
        val undoEnabledString = preferences[PreferencesKeys.UNDO_ENABLED] ?: UndoEnabled.Always.name
        UndoEnabled.valueOf(undoEnabledString)
    }

    override val showUndoIfEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_UNDO_IF_ENABLED] ?: true // Default to true
    }

    override val showNewGameButtonFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_NEW_GAME_BUTTON] ?: false // Default to false
    }

    override val boardWidthFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BOARD_WIDTH] ?: 8 // Default to 8
    }

    override val boardHeightFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BOARD_HEIGHT] ?: 8 // Default to 8
    }

    override val highscoreFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        val width = preferences[PreferencesKeys.BOARD_WIDTH] ?: 8
        val height = preferences[PreferencesKeys.BOARD_HEIGHT] ?: 8
        preferences[intPreferencesKey("highscore_${width}_${height}")] ?: 0
    }

    override val achievementShowMinimalistFlow: Flow<AchievementFilter> =
        context.dataStore.data.map { preferences ->
            val value = preferences[PreferencesKeys.ACHIEVEMENT_SHOW_MINIMALIST]
                ?: AchievementFilter.Always.name
            AchievementFilter.valueOf(value)
        }

    override val achievementShowComeAndGoneFlow: Flow<AchievementFilter> =
        context.dataStore.data.map { preferences ->
            val value = preferences[PreferencesKeys.ACHIEVEMENT_SHOW_COME_AND_GONE]
                ?: AchievementFilter.Always.name
            AchievementFilter.valueOf(value)
        }

    override val achievementShowNewRecordFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ACHIEVEMENT_SHOW_NEW_RECORD] ?: true
        }

    override val achievementShowClearedLinesFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ACHIEVEMENT_SHOW_CLEARED_LINES] ?: true
        }

    override val achievementShowAroundTheCornerFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ACHIEVEMENT_SHOW_AROUND_THE_CORNER] ?: true
        }

    override val achievementAlphaFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ACHIEVEMENT_ALPHA] ?: 0.8f
    }

    override val showBestEvalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_BEST_EVAL] ?: false
    }

    override val showCurrentEvalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_CURRENT_EVAL] ?: false
    }

    override val showGreedyGapInfoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_GREEDY_GAP_INFO] ?: false
    }

    override val congratulateBestMoveFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.CONGRATULATE_BEST_MOVE] ?: false
        }

    override suspend fun saveComputeEnabled(computeEnabled: ComputeEnabled) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.COMPUTE_ENABLED] = computeEnabled.name
        }
    }

    override suspend fun saveUndoEnabled(undoEnabled: UndoEnabled) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.UNDO_ENABLED] = undoEnabled.name
        }
    }

    override suspend fun saveShowUndoIfEnabled(showUndo: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SHOW_UNDO_IF_ENABLED] = showUndo
        }
    }

    override suspend fun saveShowNewGameButton(show: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.SHOW_NEW_GAME_BUTTON] = show }
    }

    override suspend fun saveBoardSize(width: Int, height: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.BOARD_WIDTH] = width
            settings[PreferencesKeys.BOARD_HEIGHT] = height
        }
    }

    override suspend fun saveHighscore(score: Int) {
        context.dataStore.edit { settings ->
            val width = settings[PreferencesKeys.BOARD_WIDTH] ?: 8
            val height = settings[PreferencesKeys.BOARD_HEIGHT] ?: 8
            settings[intPreferencesKey("highscore_${width}_${height}")] = score
        }
    }

    override suspend fun saveAchievementShowMinimalist(filter: AchievementFilter) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.ACHIEVEMENT_SHOW_MINIMALIST] = filter.name
        }
    }

    override suspend fun saveAchievementShowComeAndGone(filter: AchievementFilter) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.ACHIEVEMENT_SHOW_COME_AND_GONE] = filter.name
        }
    }

    override suspend fun saveAchievementShowNewRecord(show: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.ACHIEVEMENT_SHOW_NEW_RECORD] = show
        }
    }

    override suspend fun saveAchievementShowClearedLines(show: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.ACHIEVEMENT_SHOW_CLEARED_LINES] = show
        }
    }

    override suspend fun saveAchievementShowAroundTheCorner(show: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.ACHIEVEMENT_SHOW_AROUND_THE_CORNER] = show
        }
    }

    override suspend fun saveAchievementAlpha(alpha: Float) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.ACHIEVEMENT_ALPHA] = alpha }
    }

    override suspend fun saveShowBestEval(show: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.SHOW_BEST_EVAL] = show }
    }

    override suspend fun saveShowCurrentEval(show: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.SHOW_CURRENT_EVAL] = show }
    }

    override suspend fun saveShowGreedyGapInfo(show: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.SHOW_GREEDY_GAP_INFO] = show }
    }

    override suspend fun saveCongratulateBestMove(show: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.CONGRATULATE_BEST_MOVE] = show
        }
    }
}
