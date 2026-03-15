package com.rpeters.cinefintv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Shapes as ComposeShapes
import androidx.compose.material3.darkColorScheme as ComposeDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as TvDarkColorScheme

import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(
    performanceProfile: DevicePerformanceProfile? = null,
    content: @Composable () -> Unit
) {
    val spacing = CinefinSpacing()
    val tvColorScheme = TvDarkColorScheme(
            primary          = CinefinRed,
            onPrimary        = OnBackground,
            background       = BackgroundBottom,
            onBackground     = OnBackground,
            surface          = SurfaceElevated,
            onSurface        = OnBackground,
            surfaceVariant   = SurfaceAccent,
            onSurfaceVariant = OnSurfaceMuted,
    )
    val expressiveColorScheme = ComposeDarkColorScheme(
        primary = CinefinRed,
        onPrimary = OnBackground,
        primaryContainer = Color(0xFF5C1017),
        onPrimaryContainer = OnBackground,
        secondary = Color(0xFFFFB4AB),
        onSecondary = BackgroundDark,
        secondaryContainer = Color(0xFF442226),
        onSecondaryContainer = Color(0xFFFFDAD5),
        tertiary = Color(0xFF9AD0FF),
        onTertiary = BackgroundDark,
        tertiaryContainer = Color(0xFF12324C),
        onTertiaryContainer = Color(0xFFD1E4FF),
        background = BackgroundBottom,
        onBackground = OnBackground,
        surface = SurfaceElevated,
        onSurface = OnBackground,
        surfaceVariant = SurfaceAccent,
        onSurfaceVariant = OnSurfaceMuted,
        surfaceContainer = SurfaceDark,
        surfaceContainerHigh = SurfaceAccent,
        outline = BorderSubtle,
        error = Color(0xFFFFB4AB),
        onError = BackgroundDark,
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
        focusRing = Color(0xFFF2F4F8),
        focusGlow = Color(0x00FFFFFF),
        pillMuted = Color(0x66323A47),
        pillStrong = Color(0xFF8AB4F8),
        titleAccent = Color(0xFFD7E3FF),
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
