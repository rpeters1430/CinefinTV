package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subtitleAppearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "subtitle_appearance_preferences",
)

@Singleton
open class SubtitleAppearancePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.subtitleAppearanceDataStore)

    open val preferencesFlow: Flow<SubtitleAppearancePreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading subtitle appearance preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading subtitle appearance preferences", exception)
                throw exception
            }
        }
        .map { preferences ->
            val defaults = SubtitleAppearancePreferences.DEFAULT
            SubtitleAppearancePreferences(
                textSize = parseEnum(
                    value = preferences[PreferencesKeys.TEXT_SIZE],
                    default = defaults.textSize,
                ),
                font = parseEnum(
                    value = preferences[PreferencesKeys.FONT],
                    default = defaults.font,
                ),
                background = parseEnum(
                    value = preferences[PreferencesKeys.BACKGROUND],
                    default = defaults.background,
                ),
            )
        }

    open suspend fun setTextSize(textSize: SubtitleTextSize) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEXT_SIZE] = textSize.name
        }
    }

    open suspend fun setFont(font: SubtitleFont) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT] = font.name
        }
    }

    open suspend fun setBackground(background: SubtitleBackground) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND] = background.name
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(
        value: String?,
        default: T,
    ): T {
        if (value == null) return default

        return try {
            enumValueOf<T>(value)
        } catch (e: IllegalArgumentException) {
            SecureLogger.w(
                TAG,
                "Invalid ${T::class.simpleName ?: "Enum"} value '$value', using default: $default",
                e,
            )
            default
        }
    }

    private object PreferencesKeys {
        val TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        val FONT = stringPreferencesKey("subtitle_font")
        val BACKGROUND = stringPreferencesKey("subtitle_background")
    }

    companion object {
        private const val TAG = "SubtitleAppearancePrefs"
    }
}
