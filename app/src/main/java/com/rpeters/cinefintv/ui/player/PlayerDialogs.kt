package com.rpeters.cinefintv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    CinefinDialogSurface(
        onDismissRequest = onStartOver,
        modifier = Modifier
            .width(560.dp)
            .testTag(PlayerTestTags.ResumeDialog),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Resume Playback?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            val totalSeconds = positionMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val timeStr = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
            
            Text(
                text = "Would you like to continue from $timeStr or start from the beginning?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            CinefinDialogActions(
                dismissLabel = "Start from beginning",
                confirmLabel = "Resume",
                onDismiss = onStartOver,
                onConfirm = onResume,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SecurityAlertDialog(
    hostname: String,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    CinefinDialogSurface(
        onDismissRequest = onCancel
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Security Alert: Certificate Changed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = "The security certificate for your server ($hostname) has changed. This can happen if you recently renewed it, but could also indicate a security risk.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Do you want to trust the new certificate? This will update your saved security settings.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            CinefinDialogActions(
                dismissLabel = "Cancel",
                confirmLabel = "Trust and Continue",
                onDismiss = onCancel,
                onConfirm = onTrust,
            )
        }
    }
}
