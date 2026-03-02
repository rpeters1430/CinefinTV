package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryActionsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library_actions_preferences",
)

/**
 * Persists preferences that gate sensitive library actions (delete, metadata refresh, etc.).
 */
@Singleton
class LibraryActionsPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.libraryActionsDataStore)

    val preferences: Flow<LibraryActionsPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading library action preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading library action preferences", exception)
                throw exception
            }
        }
        .map { prefs ->
            LibraryActionsPreferences(
                enableManagementActions = prefs[PreferencesKeys.ENABLE_MANAGEMENT_ACTIONS]
                    ?: LibraryActionsPreferences.DEFAULT.enableManagementActions,
            )
        }

    suspend fun setEnableManagementActions(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.ENABLE_MANAGEMENT_ACTIONS] = enabled
            }
        } catch (exception: IOException) {
            SecureLogger.e(
                TAG,
                "IOException saving library action preferences, keeping previous value",
                exception,
            )
        }
    }

    private object PreferencesKeys {
        val ENABLE_MANAGEMENT_ACTIONS = booleanPreferencesKey("enable_management_actions")
    }

    companion object {
        private const val TAG = "LibraryActionsPrefsRepo"
    }
}
