package com.rpeters.cinefintv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.components.CinefinSettingListItem
import com.rpeters.cinefintv.ui.components.CinefinSwitchListItem
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

internal enum class SettingsSection { AUDIO, SUBTITLES, QUALITY, SPEED, ALL }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val STREAMING_QUALITIES = TranscodingQuality.entries

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
    onSectionSelected: (SettingsSection) -> Unit,
    onQualitySelected: (TranscodingQuality) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onInteract: () -> Unit
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val dismissOrReturnToMenu = {
        if (section == SettingsSection.ALL) {
            onClose()
        } else {
            onSectionSelected(SettingsSection.ALL)
        }
    }
    if (isVisible && anchorBounds != null) {
        val popupWidth = 320.dp
        val popupMaxHeight = 400.dp
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val popupOffsetY = (-(screenHeightPx - anchorBounds.top + 16f)).toInt()
        
        Popup(
            onDismissRequest = onClose,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            alignment = Alignment.BottomCenter,
            offset = androidx.compose.ui.unit.IntOffset(
                x = 0, 
                y = popupOffsetY
            )
        ) {
            Surface(
                modifier = Modifier
                    .width(popupWidth)
                    .heightIn(max = popupMaxHeight),
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.98f),
                ),
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ),
                tonalElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val panelTitle = when (section) {
                        SettingsSection.AUDIO -> "Audio"
                        SettingsSection.SUBTITLES -> "Subtitles"
                        SettingsSection.QUALITY -> "Quality"
                        SettingsSection.SPEED -> "Speed"
                        SettingsSection.ALL -> "Playback Options"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = panelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        if (section != SettingsSection.ALL) {
                            Button(
                                onClick = { onSectionSelected(SettingsSection.ALL) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(32.dp),
                                scale = ButtonDefaults.scale(focusedScale = 1.1f)
                            ) {
                                Text("Back", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    val listState = rememberLazyListState()
                    val initialFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(isVisible, section) {
                        if (isVisible) {
                            initialFocusRequester.requestFocus()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (section == SettingsSection.ALL) {
                            item {
                                SettingsMenuItem(
                                    title = "Streaming Quality",
                                    description = uiState.transcodingQuality.label,
                                    selectedValue = "",
                                    modifier = Modifier.focusRequester(initialFocusRequester),
                                    onClick = {
                                        onInteract()
                                        onSectionSelected(SettingsSection.QUALITY)
                                    },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = "Audio Track",
                                    description = uiState.selectedAudioTrack?.label ?: audioTracks.firstOrNull()?.label ?: "Default",
                                    selectedValue = "",
                                    onClick = {
                                        onInteract()
                                        onSectionSelected(SettingsSection.AUDIO)
                                    },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = "Subtitles",
                                    description = uiState.selectedSubtitleTrack?.label ?: "Off",
                                    selectedValue = "",
                                    onClick = {
                                        onInteract()
                                        onSectionSelected(SettingsSection.SUBTITLES)
                                    },
                                )
                            }
                            item {
                                SettingsMenuItem(
                                    title = "Playback Speed",
                                    description = if (uiState.playbackSpeed == 1.0f) "Normal" else "${uiState.playbackSpeed}x",
                                    selectedValue = "",
                                    onClick = {
                                        onInteract()
                                        onSectionSelected(SettingsSection.SPEED)
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.AUDIO) {
                            items(audioTracks.size) { index ->
                                val track = audioTracks[index]
                                TrackButton(
                                    selected = uiState.selectedAudioTrack?.id == track.id,
                                    label = track.label,
                                    modifier = if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                                    onClick = {
                                        onInteract()
                                        onAudioTrackSelected(track)
                                        onClose()
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.SUBTITLES) {
                            item {
                                TrackButton(
                                    selected = uiState.selectedSubtitleTrack == null,
                                    label = "None",
                                    modifier = Modifier.focusRequester(initialFocusRequester),
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

                        if (section == SettingsSection.QUALITY) {
                            items(STREAMING_QUALITIES.size) { index ->
                                val quality = STREAMING_QUALITIES[index]
                                TrackButton(
                                    selected = uiState.transcodingQuality == quality,
                                    label = quality.label,
                                    modifier = if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                                    onClick = {
                                        onInteract()
                                        onQualitySelected(quality)
                                        onClose()
                                    },
                                )
                            }
                        }

                        if (section == SettingsSection.SPEED) {
                            items(PLAYBACK_SPEEDS.size) { index ->
                                val speed = PLAYBACK_SPEEDS[index]
                                val label = if (speed == 1.0f) "Normal (1x)" else "${speed}x"
                                TrackButton(
                                    selected = uiState.playbackSpeed == speed,
                                    label = label,
                                    modifier = if (index == 2) Modifier.focusRequester(initialFocusRequester) else Modifier,
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
                                PlaybackSwitch(
                                    title = "Auto-play next",
                                    subtitle = "Start next episode automatically",
                                    checked = uiState.autoPlayNextEpisode,
                                    onCheckedChange = {
                                        onInteract()
                                        onAutoPlayChange(it)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    description: String,
    selectedValue: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CinefinSettingListItem(
        headline = title,
        supporting = description,
        trailingText = selectedValue,
        modifier = modifier,
        onClick = onClick,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    CinefinSwitchListItem(
        headline = title,
        supporting = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackButton(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DenseListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
        },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
    )
}
