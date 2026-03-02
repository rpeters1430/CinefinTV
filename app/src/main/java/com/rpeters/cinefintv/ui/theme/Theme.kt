package com.rpeters.cinefintv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = CinefinRed,
            onPrimary        = OnBackground,
            background       = BackgroundDark,
            onBackground     = OnBackground,
            surface          = SurfaceDark,
            onSurface        = OnBackground,
            surfaceVariant   = SurfaceVariant,
            onSurfaceVariant = OnSurfaceMuted,
        ),
        typography = CinefinTvTypography,
        content = content
    )
}
