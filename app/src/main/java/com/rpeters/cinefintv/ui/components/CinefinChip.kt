package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
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
    val backgroundBrush = if (strong) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                expressiveColors.titleAccent.copy(alpha = 0.74f),
            ),
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                expressiveColors.detailBadge.copy(alpha = 0.92f),
                expressiveColors.detailPanelMuted.copy(alpha = 0.86f),
            ),
        )
    }
    val contentColor = if (strong) {
        MaterialTheme.colorScheme.onPrimary 
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundBrush)
            .border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (strong) {
                        Color.White.copy(alpha = 0.2f)
                    } else {
                        expressiveColors.borderSubtle.copy(alpha = 0.3f)
                    }
                ),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.25.sp,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
