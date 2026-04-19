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
data class CinefinMotionSpec(
    val overshoot: Easing = CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.4f),
    val premiumOvershoot: Easing = CubicBezierEasing(0.18f, 0.89f, 0.32f, 1.28f),
    val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
    val standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    val durationExtraShort: Int = 100,
    val durationShort: Int = 150,
    val durationMedium: Int = 350,
    val durationLong: Int = 600,
)

/**
 * Static tokens for reference.
 */
object CinefinMotion {
    val Overshoot: Easing = CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.4f)
    val PremiumOvershoot: Easing = CubicBezierEasing(0.18f, 0.89f, 0.32f, 1.28f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val DurationExtraShort = 100
    const val DurationShort = 150
    const val DurationMedium = 350
    const val DurationLong = 600

    fun create(reduceMotion: Boolean = false): CinefinMotionSpec {
        return if (reduceMotion) {
            CinefinMotionSpec(
                durationExtraShort = 0,
                durationShort = 0,
                durationMedium = 0,
                durationLong = 0,
            )
        } else {
            CinefinMotionSpec()
        }
    }
}

/**
 * CompositionLocal for CinefinMotion tokens.
 */
val LocalCinefinMotion = staticCompositionLocalOf { CinefinMotionSpec() }
