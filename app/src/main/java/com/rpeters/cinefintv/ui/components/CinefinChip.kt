package com.rpeters.cinefintv.ui.components

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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon

/**
 * A unified, TV-optimized chip component for metadata and status badges.
 * Replaces HeroChip, DetailChip, and DetailInfoChip.
 *
 * @param label The text to display in the chip.
 * @param strong Whether to use a more prominent "strong" background (e.g., for 'Featured' or 'HD').
 * @param icon An optional Material icon to display before the label.
 * @param modifier The modifier to be applied to the chip.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinChip(
    label: String,
    strong: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    
    val backgroundColor = if (strong) expressiveColors.pillStrong else expressiveColors.pillMuted
    val contentColor = if (strong) Color(0xFF0F1115) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(spacing.cornerPill))
            .background(backgroundColor)
            .border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp, 
                    color = if (strong) {
                        expressiveColors.pillStrong.copy(alpha = 0.92f)
                    } else {
                        expressiveColors.borderSubtle.copy(alpha = 0.45f)
                    }
                ),
                shape = RoundedCornerShape(spacing.cornerPill),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
