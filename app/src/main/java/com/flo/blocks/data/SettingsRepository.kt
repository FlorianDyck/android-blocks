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

    suspend fun saveComputeEnabled(computeEnabled: ComputeEnabled)
    suspend fun saveUndoEnabled(undoEnabled: UndoEnabled)
    suspend fun saveShowUndoIfEnabled(showUndo: Boolean)
    suspend fun saveShowNewGameButton(show: Boolean)
    suspend fun saveBoardSize(width: Int, height: Int)
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val COMPUTE_ENABLED = stringPreferencesKey("compute_enabled")
        val UNDO_ENABLED = stringPreferencesKey("undo_enabled")
        val SHOW_UNDO_IF_ENABLED = booleanPreferencesKey("show_undo_if_enabled")
        val SHOW_NEW_GAME_BUTTON = booleanPreferencesKey("show_new_game_button")
        val BOARD_WIDTH = intPreferencesKey("board_width")
        val BOARD_HEIGHT = intPreferencesKey("board_height")
    }

    override val computeEnabledFlow: Flow<ComputeEnabled> = context.dataStore.data
        .map { preferences ->
            val computeEnabledString =
                preferences[PreferencesKeys.COMPUTE_ENABLED] ?: ComputeEnabled.Hidden.name
            ComputeEnabled.valueOf(computeEnabledString)
        }

    override val undoEnabledFlow: Flow<UndoEnabled> = context.dataStore.data
        .map { preferences ->
            val undoEnabledString =
                preferences[PreferencesKeys.UNDO_ENABLED] ?: UndoEnabled.Always.name
            UndoEnabled.valueOf(undoEnabledString)
        }

    override val showUndoIfEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_UNDO_IF_ENABLED] ?: true // Default to true
        }

    override val showNewGameButtonFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_NEW_GAME_BUTTON] ?: false // Default to false
        }

    override val boardWidthFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BOARD_WIDTH] ?: 8 // Default to 8
        }

    override val boardHeightFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BOARD_HEIGHT] ?: 8 // Default to 8
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
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SHOW_NEW_GAME_BUTTON] = show
        }
    }

    override suspend fun saveBoardSize(width: Int, height: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.BOARD_WIDTH] = width
            settings[PreferencesKeys.BOARD_HEIGHT] = height
        }
    }

}