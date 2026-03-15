package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchStatusBadge(
    status: WatchStatus,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    val containerColor = when (status) {
        WatchStatus.WATCHED -> expressiveColors.pillMuted
        WatchStatus.IN_PROGRESS -> expressiveColors.pillStrong.copy(alpha = 0.9f)
        WatchStatus.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val contentColor = when (status) {
        WatchStatus.WATCHED -> MaterialTheme.colorScheme.onSurface
        WatchStatus.IN_PROGRESS -> Color(0xFF0F1115)
        WatchStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        colors = SurfaceDefaults.colors(
            containerColor = containerColor
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val (icon, text) = when (status) {
                WatchStatus.WATCHED -> Pair(Icons.Default.Check, "Watched")
                WatchStatus.IN_PROGRESS -> Triple(
                    Icons.Default.PlayArrow,
                    if (progress != null) "${(progress * 100).toInt()}%" else "In Progress",
                    Unit
                ).let { Pair(it.first, it.second) }
                else -> Pair(Icons.Default.VisibilityOff, "Unwatched")
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
