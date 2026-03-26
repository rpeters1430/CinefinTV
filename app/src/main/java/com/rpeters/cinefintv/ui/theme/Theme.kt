package com.rpeters.cinefintv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.data.preferences.AccentColor
import com.rpeters.cinefintv.data.preferences.ContrastLevel
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import androidx.compose.material3.ColorScheme as ComposeColorScheme
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Shapes as ComposeShapes
import androidx.compose.material3.darkColorScheme as ComposeDarkColorScheme
import androidx.compose.material3.lightColorScheme as ComposeLightColorScheme
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as TvDarkColorScheme
import androidx.tv.material3.lightColorScheme as TvLightColorScheme

@Immutable
data class CinefinSpacing(
    val gutter: Dp = 48.dp, // Horizontal overscan safe zone
    val safeZoneVertical: Dp = 27.dp, // Vertical overscan safe zone
    val rowGap: Dp = 24.dp,
    val cardGap: Dp = 16.dp,
    val elementGap: Dp = 12.dp,
    val chipGap: Dp = 8.dp,
    val labelGap: Dp = 4.dp,
    val gridContentPadding: Dp = 56.dp, // Grid edge padding — larger than gutter to give focus-scaled cards room at edges
    val cornerCard: Dp = 16.dp,
    val cornerContainer: Dp = 24.dp,
    val cornerPill: Dp = 999.dp,
)

val LocalCinefinSpacing = staticCompositionLocalOf { CinefinSpacing() }

/**
 * Generates a Material 3 ColorScheme based on a seed color (Material You for TV).
 * Since system wallpaper is not available on TV, we use content-based seeds.
 */
@Composable
fun rememberDynamicColorScheme(seedColor: Color?): ComposeColorScheme {
    val baseRed = CinefinRed
    return remember(seedColor) {
        val primary = seedColor ?: baseRed
        ComposeDarkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFFD1E4FF), // Muted blue as fallback secondary
            onSecondary = Color(0xFF003258),
            surface = SurfaceElevated,
            onSurface = OnBackground,
            surfaceVariant = SurfaceAccent,
            onSurfaceVariant = OnSurfaceMuted,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceContainerHigh,
            outline = BorderSubtle,
        )
    }
}

private data class ThemePalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val elevatedSurface: Color,
    val accentSurface: Color,
    val chromeSurface: Color,
    val borderSubtle: Color,
    val pillMuted: Color,
    val titleAccent: Color,
    val onBackground: Color,
)

private fun accentSeedFor(accentColor: AccentColor): Color = when (accentColor) {
    AccentColor.JELLYFIN_PURPLE -> Color(0xFF7E57C2)
    AccentColor.JELLYFIN_BLUE -> CinefinBlue
    AccentColor.JELLYFIN_TEAL -> Color(0xFF00ACC1)
    AccentColor.MATERIAL_PURPLE -> Color(0xFF9C27B0)
    AccentColor.MATERIAL_BLUE -> Color(0xFF2196F3)
    AccentColor.MATERIAL_GREEN -> Color(0xFF43A047)
    AccentColor.MATERIAL_RED -> CinefinRed
    AccentColor.MATERIAL_ORANGE -> Color(0xFFFB8C00)
}

private fun paletteFor(darkTheme: Boolean, amoledBlack: Boolean): ThemePalette = if (darkTheme) {
    ThemePalette(
        backgroundTop = if (amoledBlack) Color.Black else BackgroundTop,
        backgroundBottom = if (amoledBlack) Color.Black else BackgroundBottom,
        heroStart = if (amoledBlack) Color(0xFF050505) else Color(0xFF12161D),
        heroEnd = if (amoledBlack) Color.Black else Color(0xFF1A2029),
        elevatedSurface = if (amoledBlack) Color(0xFF050505) else SurfaceElevated,
        accentSurface = if (amoledBlack) Color(0xFF121212) else SurfaceAccent,
        chromeSurface = if (amoledBlack) Color(0xF20A0A0A) else Color(0xE61B1F26),
        borderSubtle = if (amoledBlack) Color(0xFF222222) else BorderSubtle,
        pillMuted = if (amoledBlack) Color(0xFF1E1E1E) else ProgressGray,
        titleAccent = CinefinGold,
        onBackground = OnBackground,
    )
} else {
    ThemePalette(
        backgroundTop = Color(0xFFF8FAFD),
        backgroundBottom = Color(0xFFEFF3F8),
        heroStart = Color(0xFFFFFFFF),
        heroEnd = Color(0xFFF3F6FB),
        elevatedSurface = Color(0xFFFFFFFF),
        accentSurface = Color(0xFFE4EAF3),
        chromeSurface = Color(0xF7FFFFFF),
        borderSubtle = Color(0xFFD0D7E2),
        pillMuted = Color(0xFFD7DEE8),
        titleAccent = Color(0xFF8A5A00),
        onBackground = Color(0xFF10151C),
    )
}

private fun createColorScheme(
    primary: Color,
    darkTheme: Boolean,
    palette: ThemePalette,
    contrastLevel: ContrastLevel,
): ComposeColorScheme {
    val outline = when (contrastLevel) {
        ContrastLevel.STANDARD -> palette.borderSubtle
        ContrastLevel.MEDIUM -> palette.borderSubtle.copy(alpha = 0.9f)
        ContrastLevel.HIGH -> primary.copy(alpha = 0.95f)
    }
    val surfaceContainer = if (darkTheme) {
        palette.accentSurface
    } else {
        Color(0xFFF1F5FA)
    }
    val surfaceContainerHigh = if (darkTheme) {
        when (contrastLevel) {
            ContrastLevel.STANDARD -> SurfaceContainerHigh
            ContrastLevel.MEDIUM -> Color(0xFF3A424C)
            ContrastLevel.HIGH -> Color(0xFF48515C)
        }
    } else {
        when (contrastLevel) {
            ContrastLevel.STANDARD -> Color(0xFFE4EAF3)
            ContrastLevel.MEDIUM -> Color(0xFFDCE3EE)
            ContrastLevel.HIGH -> Color(0xFFD2DBE9)
        }
    }

    return if (darkTheme) {
        ComposeDarkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.28f),
            onPrimaryContainer = palette.onBackground,
            secondary = primary.copy(alpha = 0.84f),
            onSecondary = Color.White,
            secondaryContainer = primary.copy(alpha = 0.18f),
            onSecondaryContainer = palette.onBackground,
            background = palette.backgroundBottom,
            onBackground = palette.onBackground,
            surface = palette.elevatedSurface,
            onSurface = palette.onBackground,
            surfaceVariant = palette.accentSurface,
            onSurfaceVariant = if (contrastLevel == ContrastLevel.HIGH) palette.onBackground else OnSurfaceMuted,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = outline,
        )
    } else {
        ComposeLightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = Color(0xFF0D1320),
            secondary = primary.copy(alpha = 0.85f),
            onSecondary = Color.White,
            secondaryContainer = primary.copy(alpha = 0.14f),
            onSecondaryContainer = Color(0xFF162132),
            background = palette.backgroundBottom,
            onBackground = palette.onBackground,
            surface = palette.elevatedSurface,
            onSurface = palette.onBackground,
            surfaceVariant = palette.accentSurface,
            onSurfaceVariant = Color(0xFF43505F),
            outline = outline,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(
    seedColor: Color? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColors: Boolean = true,
    accentColor: AccentColor = AccentColor.JELLYFIN_PURPLE,
    contrastLevel: ContrastLevel = ContrastLevel.STANDARD,
    performanceProfile: DevicePerformanceProfile? = null,
    content: @Composable () -> Unit
) {
    val spacing = CinefinSpacing()
    val motion = CinefinMotion
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED_BLACK -> true
    }
    val isAmoledBlack = themeMode == ThemeMode.AMOLED_BLACK
    val palette = paletteFor(darkTheme = isDarkTheme, amoledBlack = isAmoledBlack)
    val dynamicSeed = if (useDynamicColors) seedColor else null
    val primarySeed = dynamicSeed ?: accentSeedFor(accentColor)

    val expressiveColorScheme = remember(primarySeed, isDarkTheme, isAmoledBlack, contrastLevel) {
        createColorScheme(
            primary = primarySeed,
            darkTheme = isDarkTheme,
            palette = palette,
            contrastLevel = contrastLevel,
        )
    }

    val tvColorScheme: TvColorScheme = if (isDarkTheme) TvDarkColorScheme(
        primary = expressiveColorScheme.primary,
        onPrimary = expressiveColorScheme.onPrimary,
        background = expressiveColorScheme.background,
        onBackground = expressiveColorScheme.onBackground,
        surface = expressiveColorScheme.surface,
        onSurface = expressiveColorScheme.onSurface,
        surfaceVariant = expressiveColorScheme.surfaceVariant,
        onSurfaceVariant = expressiveColorScheme.onSurfaceVariant,
    ) else TvLightColorScheme(
        primary = expressiveColorScheme.primary,
        onPrimary = expressiveColorScheme.onPrimary,
        background = expressiveColorScheme.background,
        onBackground = expressiveColorScheme.onBackground,
        surface = expressiveColorScheme.surface,
        onSurface = expressiveColorScheme.onSurface,
        surfaceVariant = expressiveColorScheme.surfaceVariant,
        onSurfaceVariant = expressiveColorScheme.onSurfaceVariant,
    )

    val expressiveColors = CinefinExpressiveColors(
        backgroundTop = palette.backgroundTop,
        backgroundBottom = palette.backgroundBottom,
        heroStart = palette.heroStart,
        heroEnd = palette.heroEnd,
        elevatedSurface = palette.elevatedSurface,
        accentSurface = palette.accentSurface,
        chromeSurface = palette.chromeSurface,
        borderSubtle = palette.borderSubtle,
        focusRing = expressiveColorScheme.primary,
        focusGlow = expressiveColorScheme.primary.copy(
            alpha = when (contrastLevel) {
                ContrastLevel.STANDARD -> 0.28f
                ContrastLevel.MEDIUM -> 0.34f
                ContrastLevel.HIGH -> 0.42f
            }
        ),
        pillMuted = palette.pillMuted,
        pillStrong = expressiveColorScheme.primary.copy(alpha = 0.7f),
        titleAccent = palette.titleAccent,
        watchedGreen = WatchedIndicator,
    )

    val composeShapes = ComposeShapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(spacing.elementGap),
        medium = RoundedCornerShape(spacing.cornerCard),
        large = RoundedCornerShape(spacing.cornerContainer),
        extraLarge = RoundedCornerShape(32.dp),
    )

    val profile = performanceProfile ?: DevicePerformanceProfile.detect(androidx.compose.ui.platform.LocalContext.current)

    CompositionLocalProvider(
        LocalPerformanceProfile provides profile,
        LocalCinefinExpressiveColors provides expressiveColors,
        LocalCinefinSpacing provides spacing,
        LocalCinefinMotion provides motion,
    ) {
        TvMaterialTheme(
            colorScheme = tvColorScheme,
            typography = CinefinTvTypography,
        ) {
            ComposeMaterialTheme(
                colorScheme = expressiveColorScheme,
                shapes = composeShapes,
                typography = CinefinComposeTypography,
                content = content,
            )
        }
    }
}
