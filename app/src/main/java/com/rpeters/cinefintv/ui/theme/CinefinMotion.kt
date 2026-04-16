package com.rpeters.cinefintv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CinefinTV Motion System
 * Defines standard easing and duration tokens for a premium TV experience.
 */
@Immutable
object CinefinMotion {
    /**
     * Standard M3 Easing with a slight overshoot for expressive feedback on TV.
     */
    val Overshoot: Easing = CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.4f)
    
    /**
     * Premium overshoot effect for focus transitions.
     * Offers a snappy entry with a soft, high-quality rebound.
     */
    val PremiumOvershoot: Easing = CubicBezierEasing(0.18f, 0.89f, 0.32f, 1.28f)
    
    /**
     * Emphasized easing for significant UI transitions (e.g., screen changes, large panels).
     */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * Standard easing for subtle property changes (e.g., color, alpha).
     */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Standard Durations
    const val DurationExtraShort = 100
    const val DurationShort = 150
    const val DurationMedium = 350
    const val DurationLong = 600
}

/**
 * CompositionLocal for CinefinMotion tokens.
 */
val LocalCinefinMotion = staticCompositionLocalOf { CinefinMotion }
