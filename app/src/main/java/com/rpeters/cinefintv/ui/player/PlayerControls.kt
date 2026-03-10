package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onAutoPlayChange: (Boolean) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            // Top Bar: Logo + Title + Season/Episode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CF",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val episodeInfo = buildString {
                        uiState.seasonNumber?.let { append("Season $it") }
                        uiState.episodeNumber?.let {
                            if (isNotEmpty()) append("  •  ")
                            append("Episode $it")
                        }
                    }
                    if (episodeInfo.isNotEmpty()) {
                        Text(
                            text = episodeInfo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Progress Bar
                if (duration > 0) {
                    // Time label above progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatMs(position),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatMs(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Seek bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        )
                    }

                    // Playback controls row
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Centered: Rewind / Play-Pause / Forward
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    player.seekTo((position - 10_000).coerceAtLeast(0))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    onInteract()
                                    if (isPlaying) player.pause() else player.play()
                                },
                                modifier = Modifier.focusRequester(playPauseFocusRequester),
                                scale = ButtonDefaults.scale(focusedScale = 1.1f)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    player.seekTo((position + 10_000).coerceAtMost(duration))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Right side: settings buttons + back
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (uiState.isEpisodicContent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Auto-play",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Switch(
                                        checked = uiState.autoPlayNextEpisode,
                                        onCheckedChange = {
                                            onInteract()
                                            onAutoPlayChange(it)
                                        },
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.SUBTITLES)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("CC")
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.AUDIO)
                                }
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = "Audio",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Audio")
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.QUALITY)
                                }
                            ) {
                                Icon(
                                    Icons.Default.HighQuality,
                                    contentDescription = "Quality",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Auto")
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.SPEED)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (uiState.playbackSpeed == 1.0f) "1×"
                                    else "${uiState.playbackSpeed}×"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.ALL)
                                }
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = "More",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedButton(onClick = { onInteract(); onBack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit player",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeCountdown(
    countdownRemaining: Long,
    nextEpisodeTitle: String?,
    onPlayNext: () -> Unit
) {
    AnimatedVisibility(
        visible = countdownRemaining > 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        androidx.tv.material3.Card(
            onClick = onPlayNext,
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Next Episode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = nextEpisodeTitle ?: "Coming up next",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Starting in $countdownRemaining seconds...",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onPlayNext,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Play Now")
                }
            }
        }
    }
}
