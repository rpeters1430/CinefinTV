package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

internal enum class SettingsSection { AUDIO, SUBTITLES, QUALITY, SPEED, ALL }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val QUALITY_OPTIONS = listOf("Auto", "1080p", "720p", "480p")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerTrackPanel(
    isVisible: Boolean,
    section: SettingsSection,
    uiState: PlayerUiState,
    player: ExoPlayer,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onAudioTrackSelected: (TrackOption) -> Unit,
    onSubtitleTrackSelected: (TrackOption?) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onClose: () -> Unit,
    onInteract: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(380.dp),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            val panelTitle = when (section) {
                SettingsSection.AUDIO -> "Audio Track"
                SettingsSection.SUBTITLES -> "Subtitles"
                SettingsSection.QUALITY -> "Quality"
                SettingsSection.SPEED -> "Playback Speed"
                SettingsSection.ALL -> "Media Settings"
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(panelTitle, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Audio section
                    if (section == SettingsSection.AUDIO || section == SettingsSection.ALL) {
                        if (section == SettingsSection.ALL) {
                            item {
                                Text(
                                    "Audio Track",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        items(audioTracks.size) { index ->
                            val track = audioTracks[index]
                            val isSelected = uiState.selectedAudioTrack?.id == track.id
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        onInteract()
                                        onAudioTrackSelected(track)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setPreferredAudioLanguage(track.language)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(track.label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onInteract()
                                        onAudioTrackSelected(track)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setPreferredAudioLanguage(track.language)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(track.label) }
                            }
                        }
                    }

                    // Subtitles section
                    if (section == SettingsSection.SUBTITLES || section == SettingsSection.ALL) {
                        if (section == SettingsSection.ALL) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Subtitles",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        // None option
                        item {
                            val isNoneSelected = uiState.selectedSubtitleTrack == null
                            if (isNoneSelected) {
                                Button(
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(null)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("None") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(null)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("None") }
                            }
                        }
                        items(subtitleTracks.size) { index ->
                            val track = subtitleTracks[index]
                            val isSelected = uiState.selectedSubtitleTrack?.id == track.id
                            if (isSelected) {
                                Button(
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(track)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(track.language)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(track.label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(track)
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(track.language)
                                            .build()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(track.label) }
                            }
                        }
                    }

                    // Quality section
                    if (section == SettingsSection.QUALITY || section == SettingsSection.ALL) {
                        if (section == SettingsSection.ALL) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Quality",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        items(QUALITY_OPTIONS.size) { index ->
                            val quality = QUALITY_OPTIONS[index]
                            // Quality selection is currently purely visual, as noted in code review
                            OutlinedButton(
                                onClick = {
                                    onInteract()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(quality) }
                        }
                    }

                    // Speed section
                    if (section == SettingsSection.SPEED || section == SettingsSection.ALL) {
                        if (section == SettingsSection.ALL) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Playback Speed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        items(PLAYBACK_SPEEDS.size) { index ->
                            val speed = PLAYBACK_SPEEDS[index]
                            val label = if (speed == 1.0f) "Normal (1×)" else "${speed}×"
                            if (uiState.playbackSpeed == speed) {
                                Button(
                                    onClick = {
                                        onInteract()
                                        onPlaybackSpeedSelected(speed)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onInteract()
                                        onPlaybackSpeedSelected(speed)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(label) }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onInteract(); onClose() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
