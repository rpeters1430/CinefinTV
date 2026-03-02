package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

// DataStore delegate for theme preferences
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences",
)

/**
 * Repository for managing theme preferences using DataStore.
 * Provides reactive access to theme settings across the application.
 */
@Singleton
open class ThemePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.themeDataStore)

    /**
     * Flow of current theme preferences.
     */
    open val themePreferencesFlow: Flow<ThemePreferences> = dataStore.data
        .catch { exception ->
            // If there's an error reading preferences, emit default values
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading theme preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading theme preferences", exception)
                throw exception
            }
        }
        .map { preferences ->
            val defaults = ThemePreferences.DEFAULT
            ThemePreferences(
                themeMode = parseEnum(
                    value = preferences[PreferencesKeys.THEME_MODE],
                    default = defaults.themeMode,
                ),
                useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS]
                    ?: defaults.useDynamicColors,
                accentColor = parseEnum(
                    value = preferences[PreferencesKeys.ACCENT_COLOR],
                    default = defaults.accentColor,
                ),
                contrastLevel = parseEnum(
                    value = preferences[PreferencesKeys.CONTRAST_LEVEL],
                    default = defaults.contrastLevel,
                ),
                useThemedIcon = preferences[PreferencesKeys.USE_THEMED_ICON]
                    ?: defaults.useThemedIcon,
                enableEdgeToEdge = preferences[PreferencesKeys.ENABLE_EDGE_TO_EDGE]
                    ?: defaults.enableEdgeToEdge,
                respectReduceMotion = preferences[PreferencesKeys.RESPECT_REDUCE_MOTION]
                    ?: defaults.respectReduceMotion,
            )
        }

    /**
     * Update theme mode (System, Light, Dark, AMOLED Black).
     */
    open suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    /**
     * Update dynamic colors setting.
     */
    open suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = useDynamicColors
        }
    }

    /**
     * Update custom accent color.
     */
    open suspend fun setAccentColor(accentColor: AccentColor) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = accentColor.name
        }
    }

    /**
     * Update contrast level.
     */
    open suspend fun setContrastLevel(contrastLevel: ContrastLevel) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTRAST_LEVEL] = contrastLevel.name
        }
    }

    /**
     * Update themed icon setting.
     */
    open suspend fun setUseThemedIcon(useThemedIcon: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_THEMED_ICON] = useThemedIcon
        }
    }

    /**
     * Update edge-to-edge layout setting.
     */
    open suspend fun setEnableEdgeToEdge(enableEdgeToEdge: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_EDGE_TO_EDGE] = enableEdgeToEdge
        }
    }

    /**
     * Update reduce motion setting.
     */
    open suspend fun setRespectReduceMotion(respectReduceMotion: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESPECT_REDUCE_MOTION] = respectReduceMotion
        }
    }

    /**
     * Reset all theme preferences to defaults.
     */
    open suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Parse enum value from string with robust error handling.
     * Logs warnings when invalid values are encountered and returns defaults.
     *
     * @param T The enum type to parse
     * @param value The string value to parse
     * @param default The default value to use if parsing fails
     * @return The parsed enum value or default if parsing fails
     */
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CONTRAST_LEVEL = stringPreferencesKey("contrast_level")
        val USE_THEMED_ICON = booleanPreferencesKey("use_themed_icon")
        val ENABLE_EDGE_TO_EDGE = booleanPreferencesKey("enable_edge_to_edge")
        val RESPECT_REDUCE_MOTION = booleanPreferencesKey("respect_reduce_motion")
    }

    companion object {
        private const val TAG = "ThemePreferencesRepository"
    }
}
