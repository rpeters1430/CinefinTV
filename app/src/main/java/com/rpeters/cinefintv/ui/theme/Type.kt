package com.rpeters.cinefintv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import androidx.compose.material3.Typography as ComposeTypography

@OptIn(ExperimentalTvMaterial3Api::class)
val CinefinTvTypography = Typography(
    displayLarge  = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium= TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium   = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

val CinefinComposeTypography = ComposeTypography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
)
