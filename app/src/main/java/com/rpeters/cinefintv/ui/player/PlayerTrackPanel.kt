package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlin.math.roundToInt

internal enum class SettingsSection { AUDIO, SUBTITLES, SPEED, ALL }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val PopupWidth = 420.dp
private val PopupMaxHeight = 420.dp
private val PopupVerticalGap = 16.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerTrackPanel(
    isVisible: Boolean,
    section: SettingsSection,
    anchorBounds: Rect?,
    uiState: PlayerUiState,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onAudioTrackSelected: (TrackOption) -> Unit,
    onSubtitleTrackSelected: (TrackOption?) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onInteract: () -> Unit
) {
    if (!isVisible || anchorBounds == null) return

    val density = LocalDensity.current
    val popupWidthPx = with(density) { PopupWidth.roundToPx() }
    val popupMaxHeightPx = with(density) { PopupMaxHeight.roundToPx() }
    val popupGapPx = with(density) { PopupVerticalGap.roundToPx() }
    val popupOffset = IntOffset(
        x = (anchorBounds.center.x - (popupWidthPx / 2f)).roundToInt(),
        y = (anchorBounds.top - popupGapPx - popupMaxHeightPx).roundToInt().coerceAtLeast(0),
    )

    Popup(
        offset = popupOffset,
        onDismissRequest = onClose,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            Surface(
                modifier = Modifier
                    .width(PopupWidth)
                    .heightIn(max = PopupMaxHeight),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ),
                tonalElevation = 12.dp,
            ) {
                val panelTitle = when (section) {
                    SettingsSection.AUDIO -> "Audio"
                    SettingsSection.SUBTITLES -> "Subtitles"
                    SettingsSection.SPEED -> "Playback Speed"
                    SettingsSection.ALL -> "Playback Options"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = panelTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (section == SettingsSection.AUDIO || section == SettingsSection.ALL) {
                            if (section == SettingsSection.ALL) {
                                item {
                                    Text(
                                        text = "Audio",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(audioTracks.size) { index ->
                                val track = audioTracks[index]
                                TrackButton(
                                    selected = uiState.selectedAudioTrack?.id == track.id,
                                    label = track.label,
                                    onClick = {
                                        onInteract()
                                        onAudioTrackSelected(track)
                                        onClose()
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.SUBTITLES || section == SettingsSection.ALL) {
                            if (section == SettingsSection.ALL) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Subtitles",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            item {
                                TrackButton(
                                    selected = uiState.selectedSubtitleTrack == null,
                                    label = "None",
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(null)
                                        onClose()
                                    },
                                )
                            }
                            items(subtitleTracks.size) { index ->
                                val track = subtitleTracks[index]
                                TrackButton(
                                    selected = uiState.selectedSubtitleTrack?.id == track.id,
                                    label = track.label,
                                    onClick = {
                                        onInteract()
                                        onSubtitleTrackSelected(track)
                                        onClose()
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.SPEED || section == SettingsSection.ALL) {
                            if (section == SettingsSection.ALL) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Playback Speed",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(PLAYBACK_SPEEDS.size) { index ->
                                val speed = PLAYBACK_SPEEDS[index]
                                val label = if (speed == 1.0f) "Normal (1x)" else "${speed}x"
                                TrackButton(
                                    selected = uiState.playbackSpeed == speed,
                                    label = label,
                                    onClick = {
                                        onInteract()
                                        onPlaybackSpeedSelected(speed)
                                        onClose()
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.ALL && uiState.isEpisodicContent) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Playback",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = SurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Auto-play next episode",
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                text = "Start the next episode automatically near the end.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = uiState.autoPlayNextEpisode,
                                            onCheckedChange = {
                                                onInteract()
                                                onAutoPlayChange(it)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onInteract()
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label)
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label)
                Spacer(Modifier.width(24.dp))
            }
        }
    }
}
