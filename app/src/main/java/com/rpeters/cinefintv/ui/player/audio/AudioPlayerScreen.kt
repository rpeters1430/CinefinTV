package com.rpeters.cinefintv.ui.player.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isConnecting || uiState.isLoadingQueue) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (uiState.isConnecting) "Connecting to playback..." else "Loading queue...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Audio playback is unavailable",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::retry) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            uiState.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Track ${uiState.currentIndex + 1} of ${uiState.queueSize}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${formatTime(uiState.positionMs)} / ${formatTime(uiState.durationMs)}",
                style = MaterialTheme.typography.headlineSmall,
            )
            val progress = if (uiState.durationMs > 0L) {
                ((uiState.positionMs.toFloat() / uiState.durationMs.toFloat()) * 100f)
                    .coerceIn(0f, 100f)
            } else {
                0f
            }
            Text(
                text = "Progress ${progress.toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = viewModel::skipToPrevious,
                enabled = uiState.canSkipPrevious,
            ) {
                Text("Previous")
            }
            OutlinedButton(onClick = viewModel::seekBackward) {
                Text("Back 30s")
            }
            Button(onClick = viewModel::togglePlayPause) {
                Text(if (uiState.isPlaying) "Pause" else "Play")
            }
            OutlinedButton(onClick = viewModel::seekForward) {
                Text("Forward 30s")
            }
            OutlinedButton(
                onClick = viewModel::skipToNext,
                enabled = uiState.canSkipNext,
            ) {
                Text("Next")
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
