package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

internal enum class SettingsSection { AUDIO, SUBTITLES, SPEED, ALL }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val PanelHeight = 460.dp

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
    val expressiveColors = LocalCinefinExpressiveColors.current
    // We use a full-screen Popup to capture focus and allow expressive animations
    if (isVisible) {
        Popup(
            onDismissRequest = onClose,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PanelHeight),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = expressiveColors.elevatedSurface.copy(alpha = 0.98f),
                        ),
                        tonalElevation = 16.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 48.dp, vertical = 32.dp)
                        ) {
                            val panelTitle = when (section) {
                                SettingsSection.AUDIO -> "Audio Tracks"
                                SettingsSection.SUBTITLES -> "Subtitle Tracks"
                                SettingsSection.SPEED -> "Playback Speed"
                                SettingsSection.ALL -> "Playback Options"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = panelTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Button(
                                    onClick = onClose,
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(48.dp),
                                    shape = ButtonDefaults.shape(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            val listState = rememberLazyListState()
                            val initialFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(isVisible) {
                                if (isVisible) {
                                    initialFocusRequester.requestFocus()
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (section == SettingsSection.AUDIO || section == SettingsSection.ALL) {
                                            if (section == SettingsSection.ALL) {
                                                item {
                                                    SectionHeader("Audio")
                                                }
                                            }
                                            items(audioTracks.size) { index ->
                                                val track = audioTracks[index]
                                                TrackButton(
                                                    selected = uiState.selectedAudioTrack?.id == track.id,
                                                    label = track.label,
                                                    modifier = if (index == 0 && section == SettingsSection.AUDIO) 
                                                        Modifier.focusRequester(initialFocusRequester) else Modifier,
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
                                                    Spacer(Modifier.height(8.dp))
                                                    SectionHeader("Subtitles")
                                                }
                                            }
                                            item {
                                                TrackButton(
                                                    selected = uiState.selectedSubtitleTrack == null,
                                                    label = "None",
                                                    modifier = if (section == SettingsSection.SUBTITLES) 
                                                        Modifier.focusRequester(initialFocusRequester) else Modifier,
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
                                                    Spacer(Modifier.height(8.dp))
                                                    SectionHeader("Playback Speed")
                                                }
                                            }
                                            items(PLAYBACK_SPEEDS.size) { index ->
                                                val speed = PLAYBACK_SPEEDS[index]
                                                val label = if (speed == 1.0f) "Normal (1x)" else "${speed}x"
                                                TrackButton(
                                                    selected = uiState.playbackSpeed == speed,
                                                    label = label,
                                                    modifier = if (index == 2 && section == SettingsSection.SPEED) 
                                                        Modifier.focusRequester(initialFocusRequester) else Modifier,
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
                                                Spacer(Modifier.height(8.dp))
                                                SectionHeader("Playback")
                                            }
                                            item {
                                                PlaybackSwitch(
                                                    title = "Auto-play next episode",
                                                    subtitle = "Start the next episode automatically near the end.",
                                                    checked = uiState.autoPlayNextEpisode,
                                                    onCheckedChange = {
                                                        onInteract()
                                                        onAutoPlayChange(it)
                                                    }
                                                )
                                            }
                                        }
                                        
                                        // Padding at bottom for expressive feel
                                        item { Spacer(Modifier.height(16.dp)) }
                                    }
                                }

                                // Expressive Vertical Scrollbar
                                ExpressiveVerticalScrollbar(
                                    listState = listState,
                                    modifier = Modifier
                                        .width(6.dp)
                                        .fillMaxHeight()
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
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
    OutlinedButton(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackButton(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContainerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            },
            focusedContentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExpressiveVerticalScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (listState.isScrollInProgress) 0.8f else 0.4f,
        label = "ScrollbarAlpha"
    )

    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.1f))
            .drawWithContent {
                drawContent()
                
                val layoutInfo = listState.layoutInfo
                val totalItemsCount = layoutInfo.totalItemsCount
                if (totalItemsCount > 0) {
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isNotEmpty()) {
                        val firstVisibleItem = visibleItemsInfo.first()
                        val viewportHeight = size.height
                        
                        val thumbHeightPercent = (visibleItemsInfo.size.toFloat() / totalItemsCount).coerceIn(0.1f, 1f)
                        val thumbOffsetPercent = (firstVisibleItem.index.toFloat() / totalItemsCount).coerceIn(0f, 1f)
                        
                        val thumbHeight = viewportHeight * thumbHeightPercent
                        val thumbOffset = viewportHeight * thumbOffsetPercent
                        
                        drawRoundRect(
                            color = thumbColor.copy(alpha = scrollbarAlpha),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, thumbOffset),
                            size = androidx.compose.ui.geometry.Size(size.width, thumbHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2, size.width / 2)
                        )
                    }
                }
            }
    )
}
