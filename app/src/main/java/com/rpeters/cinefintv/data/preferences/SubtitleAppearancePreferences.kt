package com.rpeters.cinefintv.data.preferences

/**
 * Subtitle text sizing options.
 */
enum class SubtitleTextSize(val sizeSp: Float) {
    SMALL(14f),
    MEDIUM(18f),
    LARGE(22f),
    EXTRA_LARGE(26f),
}

/**
 * Subtitle font options.
 */
enum class SubtitleFont {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    ROBOTO,
    ROBOTO_FLEX,
    ROBOTO_SERIF,
    ROBOTO_MONO,
}

/**
 * Subtitle background styling options.
 */
enum class SubtitleBackground {
    NONE,
    BLACK,
    SEMI_TRANSPARENT,
}

/**
 * Data class representing subtitle appearance preferences.
 */
data class SubtitleAppearancePreferences(
    val textSize: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val font: SubtitleFont = SubtitleFont.DEFAULT,
    val background: SubtitleBackground = SubtitleBackground.SEMI_TRANSPARENT,
) {
    companion object {
        val DEFAULT = SubtitleAppearancePreferences()
    }
}
