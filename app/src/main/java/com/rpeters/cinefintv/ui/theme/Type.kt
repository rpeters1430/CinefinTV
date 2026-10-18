package com.rpeters.cinefintv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import androidx.compose.material3.Typography as ComposeTypography

@OptIn(ExperimentalTvMaterial3Api::class)
val CinefinTvTypography = Typography(
    displayLarge  = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    headlineMedium= TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium   = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    labelLarge    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium   = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
)

val CinefinComposeTypography = ComposeTypography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
)
