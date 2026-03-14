package com.rpeters.cinefintv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CinefinExpressiveColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val elevatedSurface: Color,
    val accentSurface: Color,
    val chromeSurface: Color,
    val borderSubtle: Color,
    val focusRing: Color,
    val focusGlow: Color,
    val pillMuted: Color,
    val pillStrong: Color,
    val titleAccent: Color,
)

val LocalCinefinExpressiveColors = staticCompositionLocalOf {
    CinefinExpressiveColors(
        backgroundTop = BackgroundTop,
        backgroundBottom = BackgroundBottom,
        heroStart = BackgroundTop,
        heroEnd = BackgroundBottom,
        elevatedSurface = SurfaceElevated,
        accentSurface = SurfaceAccent,
        chromeSurface = SurfaceDark,
        borderSubtle = BorderSubtle,
        focusRing = CinefinCoral,
        focusGlow = CinefinRed.copy(alpha = 0.28f),
        pillMuted = ProgressGray,
        pillStrong = CinefinRed.copy(alpha = 0.7f),
        titleAccent = CinefinGold,
    )
}
