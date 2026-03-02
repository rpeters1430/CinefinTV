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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialSecurityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "credential_security_preferences",
)

data class CredentialSecurityPreferences(
    val requireStrongAuthForCredentials: Boolean = false,
) {
    companion object {
        val DEFAULT = CredentialSecurityPreferences()
    }
}

@Singleton
class CredentialSecurityPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.credentialSecurityDataStore

    val preferences: Flow<CredentialSecurityPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading credential security preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading credential security preferences", exception)
                throw exception
            }
        }
        .map { prefs ->
            CredentialSecurityPreferences(
                requireStrongAuthForCredentials = prefs[PreferencesKeys.REQUIRE_STRONG_AUTH]
                    ?: CredentialSecurityPreferences.DEFAULT.requireStrongAuthForCredentials,
            )
        }

    suspend fun setRequireStrongAuthForCredentials(requireStrongAuth: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.REQUIRE_STRONG_AUTH] = requireStrongAuth
        }
    }

    suspend fun currentPreferences(): CredentialSecurityPreferences {
        return preferences.first()
    }

    private object PreferencesKeys {
        val REQUIRE_STRONG_AUTH = booleanPreferencesKey("require_strong_auth_for_credentials")
    }

    companion object {
        private const val TAG = "CredentialSecurityPrefs"
    }
}
