package com.rpeters.cinefintv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Shapes as ComposeShapes
import androidx.compose.material3.darkColorScheme as ComposeDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as TvDarkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(content: @Composable () -> Unit) {
    val tvColorScheme = TvDarkColorScheme(
            primary          = CinefinRed,
            onPrimary        = OnBackground,
            background       = BackgroundDark,
            onBackground     = OnBackground,
            surface          = SurfaceDark,
            onSurface        = OnBackground,
            surfaceVariant   = SurfaceVariant,
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
        background = BackgroundDark,
        onBackground = OnBackground,
        surface = SurfaceDark,
        onSurface = OnBackground,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceMuted,
        surfaceContainer = Color(0xFF1B2129),
        surfaceContainerHigh = Color(0xFF222A33),
        outline = Color(0xFF5B6573),
        error = Color(0xFFFFB4AB),
        onError = BackgroundDark,
    )
    val composeShapes = ComposeShapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

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
