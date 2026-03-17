package com.rpeters.cinefintv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import androidx.compose.material3.ColorScheme as ComposeColorScheme
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Shapes as ComposeShapes
import androidx.compose.material3.darkColorScheme as ComposeDarkColorScheme
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as TvDarkColorScheme

@Immutable
data class CinefinSpacing(
    val gutter: Dp = 48.dp,
    val rowGap: Dp = 24.dp,
    val cardGap: Dp = 16.dp,
    val elementGap: Dp = 12.dp,
    val chipGap: Dp = 8.dp,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(
    seedColor: Color? = null,
    useDynamicColors: Boolean = true,
    performanceProfile: DevicePerformanceProfile? = null,
    content: @Composable () -> Unit
) {
    val spacing = CinefinSpacing()
    val motion = CinefinMotion
    
    val expressiveColorScheme = if (useDynamicColors && seedColor != null) {
        rememberDynamicColorScheme(seedColor)
    } else {
        ComposeDarkColorScheme(
            primary = CinefinRed,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF5C1017),
            onPrimaryContainer = OnBackground,
            secondary = Color(0xFFFFB4AB),
            onSecondary = BackgroundDark,
            secondaryContainer = Color(0xFF442226),
            onSecondaryContainer = Color(0xFFFFDAD5),
            background = BackgroundBottom,
            onBackground = OnBackground,
            surface = SurfaceElevated,
            onSurface = OnBackground,
            surfaceVariant = SurfaceAccent,
            onSurfaceVariant = OnSurfaceMuted,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceContainerHigh,
            outline = BorderSubtle,
        )
    }

    val tvColorScheme = TvDarkColorScheme(
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
        backgroundTop = BackgroundTop,
        backgroundBottom = BackgroundBottom,
        heroStart = Color(0xFF12161D),
        heroEnd = Color(0xFF1A2029),
        elevatedSurface = SurfaceElevated,
        accentSurface = SurfaceAccent,
        chromeSurface = Color(0xE61B1F26),
        borderSubtle = BorderSubtle,
        focusRing = expressiveColorScheme.primary,
        focusGlow = expressiveColorScheme.primary.copy(alpha = 0.28f),
        pillMuted = ProgressGray,
        pillStrong = expressiveColorScheme.primary.copy(alpha = 0.7f),
        titleAccent = CinefinGold,
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
