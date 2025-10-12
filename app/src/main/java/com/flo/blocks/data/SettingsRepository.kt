import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flo.blocks.GameViewModel.ComputeEnabled
import com.flo.blocks.GameViewModel.UndoEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val COMPUTE_ENABLED = stringPreferencesKey("compute_enabled")
        val UNDO_ENABLED = stringPreferencesKey("undo_enabled")
        val SHOW_UNDO_IF_ENABLED = booleanPreferencesKey("show_undo_if_enabled")
    }

    val computeEnabledFlow: Flow<ComputeEnabled> = context.dataStore.data
        .map { preferences ->
            val computeEnabledString =
                preferences[PreferencesKeys.COMPUTE_ENABLED] ?: ComputeEnabled.Hidden.name
            ComputeEnabled.valueOf(computeEnabledString)
        }

    val undoEnabledFlow: Flow<UndoEnabled> = context.dataStore.data
        .map { preferences ->
            val undoEnabledString =
                preferences[PreferencesKeys.UNDO_ENABLED] ?: UndoEnabled.Always.name
            UndoEnabled.valueOf(undoEnabledString)
        }

    val showUndoIfEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_UNDO_IF_ENABLED] ?: true // Default to true
        }

    suspend fun saveComputeEnabled(computeEnabled: ComputeEnabled) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.COMPUTE_ENABLED] = computeEnabled.name
        }
    }

    suspend fun saveUndoEnabled(undoEnabled: UndoEnabled) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.UNDO_ENABLED] = undoEnabled.name
        }
    }

    suspend fun saveShowUndoIfEnabled(showUndo: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SHOW_UNDO_IF_ENABLED] = showUndo
        }
    }

}