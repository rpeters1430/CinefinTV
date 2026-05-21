package com.rpeters.cinefintv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.theme.CinefinGold
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
                    .heightIn(max = popupMaxHeight)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape || keyEvent.key == Key.DirectionLeft) {
                                if (section != SettingsSection.ALL) {
                                    onInteract()
                                    onSectionSelected(SettingsSection.ALL)
                                    true
                                } else {
                                    false
                                }
                            } else false
                        } else false
                    },
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.98f),
                ),
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.15f))
                ),
                tonalElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val panelTitle = when (section) {
                        SettingsSection.AUDIO -> "Audio"
                        SettingsSection.SUBTITLES -> "Subtitles"
                        SettingsSection.QUALITY -> "Quality"
                        SettingsSection.SPEED -> "Speed"
                        SettingsSection.ALL -> "Playback Options"
                    }

                    Text(
                        text = panelTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val listState = rememberLazyListState()
                    val initialFocusRequester = remember { FocusRequester() }
                    val selectedAudioIndex = audioTracks.indexOfFirst {
                        it.id == uiState.selectedAudioTrack?.id
                    }.takeIf { it >= 0 } ?: 0
                    val selectedSubtitleIndex = when {
                        uiState.selectedSubtitleTrack == null -> 0
                        else -> subtitleTracks.indexOfFirst {
                            it.id == uiState.selectedSubtitleTrack.id
                        }.takeIf { it >= 0 }?.plus(1) ?: 0
                    }
                    val selectedQualityIndex = STREAMING_QUALITIES.indexOf(uiState.transcodingQuality)
                        .takeIf { it >= 0 } ?: 0
                    val selectedSpeedIndex = PLAYBACK_SPEEDS.indexOf(uiState.playbackSpeed)
                        .takeIf { it >= 0 } ?: PLAYBACK_SPEEDS.indexOf(1.0f)
                    val initialListIndex = when (section) {
                        SettingsSection.ALL -> 0
                        SettingsSection.AUDIO -> selectedAudioIndex
                        SettingsSection.SUBTITLES -> selectedSubtitleIndex
                        SettingsSection.QUALITY -> selectedQualityIndex
                        SettingsSection.SPEED -> selectedSpeedIndex
                    }

                    LaunchedEffect(isVisible, section, initialListIndex) {
                        if (isVisible) {
                            listState.scrollToItem(initialListIndex)
                            initialFocusRequester.requestFocus()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (section == SettingsSection.ALL) {
                            item {
                                SettingsMenuItem(
                                    icon = Icons.Default.HighQuality,
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
                                    icon = Icons.Default.MusicNote,
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
                                    icon = Icons.Default.ClosedCaption,
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
                                    icon = Icons.Default.PlayArrow,
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
                                    modifier = if (index == selectedAudioIndex) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
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
                                    modifier = if (selectedSubtitleIndex == 0) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
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
                                    modifier = if (selectedSubtitleIndex == index + 1) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
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
                                    modifier = if (index == selectedQualityIndex) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
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
                                    modifier = if (index == selectedSpeedIndex) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    selectedValue: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
            focusedContainerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.15f),
            focusedContentColor = CinefinGold,
            selectedContainerColor = CinefinGold.copy(alpha = 0.1f),
            selectedContentColor = CinefinGold,
            focusedSelectedContainerColor = CinefinGold.copy(alpha = 0.25f),
            focusedSelectedContentColor = CinefinGold
        ),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        trailingContent = {
            if (selectedValue.isNotBlank()) {
                Text(
                    text = selectedValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.tv.material3.LocalContentColor.current,
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationListItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
            focusedContainerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.15f),
            focusedContentColor = CinefinGold,
            selectedContainerColor = CinefinGold.copy(alpha = 0.1f),
            selectedContentColor = CinefinGold,
            focusedSelectedContainerColor = CinefinGold.copy(alpha = 0.25f),
            focusedSelectedContentColor = CinefinGold
        ),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
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
    val expressiveColors = LocalCinefinExpressiveColors.current
    ListItem(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
            focusedContainerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.15f),
            focusedContentColor = CinefinGold,
            selectedContainerColor = CinefinGold.copy(alpha = 0.1f),
            selectedContentColor = CinefinGold,
            focusedSelectedContainerColor = CinefinGold.copy(alpha = 0.25f),
            focusedSelectedContentColor = CinefinGold
        ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        trailingContent = {
            androidx.tv.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
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
    val expressiveColors = LocalCinefinExpressiveColors.current
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
            focusedContainerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.15f),
            focusedContentColor = CinefinGold,
            selectedContainerColor = CinefinGold.copy(alpha = 0.1f),
            selectedContentColor = CinefinGold,
            focusedSelectedContainerColor = CinefinGold.copy(alpha = 0.25f),
            focusedSelectedContentColor = CinefinGold
        ),
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
