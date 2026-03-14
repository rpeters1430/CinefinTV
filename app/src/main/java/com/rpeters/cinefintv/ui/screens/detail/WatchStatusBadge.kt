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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchStatusBadge(
    status: WatchStatus,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = when (status) {
                WatchStatus.WATCHED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                WatchStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (icon, text, color) = when (status) {
                WatchStatus.WATCHED -> Triple(Icons.Default.Check, "Watched", Color(0xFF4CAF50))
                WatchStatus.IN_PROGRESS -> Triple(
                    Icons.Default.PlayArrow,
                    if (progress != null) "${(progress * 100).toInt()}%" else "In Progress",
                    MaterialTheme.colorScheme.primary
                )
                else -> Triple(
                    Icons.Default.VisibilityOff,
                    "Unwatched",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
