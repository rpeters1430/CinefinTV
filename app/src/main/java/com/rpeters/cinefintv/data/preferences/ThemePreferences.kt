package com.rpeters.cinefintv.data.preferences

/**
 * Theme mode options for the application.
 */
enum class ThemeMode {
    /** Follow system dark mode setting */
    SYSTEM,

    /** Always use light theme */
    LIGHT,

    /** Always use dark theme */
    DARK,

    /** Always use pure black theme (AMOLED-optimized) */
    AMOLED_BLACK,
}

/**
 * Contrast level for theme colors (Material 3 accessibility).
 */
enum class ContrastLevel {
    /** Standard contrast (default) */
    STANDARD,

    /** Medium contrast for better readability */
    MEDIUM,

    /** High contrast for maximum accessibility */
    HIGH,
}

/**
 * Custom accent color options when dynamic color is disabled.
 */
enum class AccentColor {
    /** Default Jellyfin purple */
    JELLYFIN_PURPLE,

    /** Jellyfin blue */
    JELLYFIN_BLUE,

    /** Jellyfin teal */
    JELLYFIN_TEAL,

    /** Material purple */
    MATERIAL_PURPLE,

    /** Material blue */
    MATERIAL_BLUE,

    /** Material green */
    MATERIAL_GREEN,

    /** Material red */
    MATERIAL_RED,

    /** Material orange */
    MATERIAL_ORANGE,
}

/**
 * Data class representing user theme preferences.
 */
data class ThemePreferences(
    /**
     * Theme mode selection (System, Light, Dark, AMOLED Black).
     */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /**
     * Whether to use Material You dynamic colors on Android 12+.
     * When enabled, colors are extracted from the system wallpaper.
     */
    val useDynamicColors: Boolean = true,

    /**
     * Custom accent color to use when dynamic colors are disabled.
     */
    val accentColor: AccentColor = AccentColor.JELLYFIN_PURPLE,

    /**
     * Contrast level for theme colors.
     */
    val contrastLevel: ContrastLevel = ContrastLevel.STANDARD,

    /**
     * Whether to use themed app icon on Android 13+.
     * Themed icons adapt to wallpaper colors.
     */
    val useThemedIcon: Boolean = true,

    /**
     * Whether to enable edge-to-edge layout.
     */
    val enableEdgeToEdge: Boolean = true,

    /**
     * Whether to respect system animation settings (reduce motion).
     */
    val respectReduceMotion: Boolean = true,
) {
    companion object {
        /**
         * Default theme preferences.
         */
        val DEFAULT = ThemePreferences()
    }
}
