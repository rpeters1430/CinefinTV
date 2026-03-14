package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

/**
 * A unified, TV-optimized chip component for metadata and status badges.
 * Replaces HeroChip, DetailChip, and DetailInfoChip.
 *
 * @param label The text to display in the chip.
 * @param strong Whether to use a more prominent "strong" background (e.g., for 'Featured' or 'HD').
 * @param modifier The modifier to be applied to the chip.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinChip(
    label: String,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    
    val backgroundColor = if (strong) expressiveColors.pillStrong else expressiveColors.pillMuted
    val contentColor = if (strong) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(spacing.cornerPill))
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.dp, 
                    color = expressiveColors.borderSubtle.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(spacing.cornerPill),
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
