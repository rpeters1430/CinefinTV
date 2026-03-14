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
    val gutter: Dp = 56.dp,
    val rowGap: Dp = 32.dp,
    val cardGap: Dp = 20.dp,
    val elementGap: Dp = 12.dp,
    val chipGap: Dp = 10.dp,
    val cornerCard: Dp = 18.dp,
    val cornerContainer: Dp = 28.dp,
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
        heroStart = Color(0xFF2A1016),
        heroEnd = Color(0xFF101722),
        elevatedSurface = SurfaceElevated,
        accentSurface = SurfaceAccent,
        chromeSurface = Color(0xCC121A24),
        borderSubtle = BorderSubtle,
        focusRing = CinefinCoral,
        focusGlow = CinefinRed.copy(alpha = 0.28f),
        pillMuted = Color(0x66303A4B),
        pillStrong = Color(0x99E50914),
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
