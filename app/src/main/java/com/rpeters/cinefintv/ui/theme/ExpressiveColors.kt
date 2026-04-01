package com.rpeters.cinefintv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
    val detailHeroScrimStart: Color,
    val detailHeroScrimEnd: Color,
    val detailHeroPanel: Color,
    val detailPanel: Color,
    val detailPanelMuted: Color,
    val detailPanelFocused: Color,
    val detailBadge: Color,
    // Semantic Status Tokens
    val watchedGreen: Color,
    
    // M3 Expressive Tokens
    val surfaceContainerLowest: Color = SurfaceContainerLowest,
    val surfaceContainerLow: Color = SurfaceContainerLow,
    val surfaceContainer: Color = SurfaceContainer,
    val surfaceContainerHigh: Color = SurfaceContainerHigh,
    val surfaceContainerHighest: Color = SurfaceContainerHighest,
    
    // Player tokens
    val playerSurface: Color = PlayerSurface,
    val playerContentPrimary: Color = PlayerContentPrimary,
    val playerContentSecondary: Color = PlayerContentSecondary,
    val playerOverlayStart: Color = PlayerOverlayStart,
    val playerOverlayEnd: Color = PlayerOverlayEnd,
)

/**
 * Material Expressive Motion Tokens
 */
@Immutable
object CinefinMotion {
    /**
     * Standard M3 Easing with a slight overshoot for expressive feedback on TV.
     */
    val Overshoot: Easing = CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.4f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    
    const val DurationShort = 150
    const val DurationMedium = 350
    const val DurationLong = 600
}

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
        focusRing = CinefinRed,
        focusGlow = CinefinRed.copy(alpha = 0.28f),
        pillMuted = ProgressGray,
        pillStrong = CinefinRed.copy(alpha = 0.7f),
        titleAccent = CinefinGold,
        detailHeroScrimStart = BackgroundTop.copy(alpha = 0.96f),
        detailHeroScrimEnd = BackgroundBottom.copy(alpha = 0.88f),
        detailHeroPanel = SurfaceDark.copy(alpha = 0.84f),
        detailPanel = SurfaceContainerHigh.copy(alpha = 0.94f),
        detailPanelMuted = SurfaceContainer.copy(alpha = 0.9f),
        detailPanelFocused = SurfaceContainerHighest.copy(alpha = 0.98f),
        detailBadge = SurfaceContainerHighest.copy(alpha = 0.96f),
        watchedGreen = WatchedIndicator,
    )
}

val LocalCinefinMotion = staticCompositionLocalOf { CinefinMotion }
