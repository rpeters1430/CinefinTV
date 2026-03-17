package com.rpeters.cinefintv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MotionPhotosPaused
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.components.CinefinOptionDialog
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.components.rememberTvScreenFocusState
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

private enum class SettingsChoiceDialog {
    RESUME_PLAYBACK,
    STREAMING_QUALITY,
    AUDIO_CHANNELS,
    SUBTITLE_TEXT_SIZE,
    SUBTITLE_BACKGROUND,
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val expressiveColors = LocalCinefinExpressiveColors.current
    var activeDialog by remember { mutableStateOf<SettingsChoiceDialog?>(null) }
    val listState = rememberLazyListState()
    val screenFocus = rememberTvScreenFocusState()
    RegisterPrimaryScreenFocus(
        route = NavRoutes.SETTINGS,
        requester = screenFocus.primaryContentRequester,
    )

    RequestScreenFocus(
        key = uiState.isLoading,
        requester = screenFocus.topAnchorRequester,
        enabled = !uiState.isLoading,
    )

    when (activeDialog) {
        SettingsChoiceDialog.RESUME_PLAYBACK -> CinefinOptionDialog(
            title = "Resume playback",
            supportingText = "Choose how playback resumes when an item has saved progress.",
            options = ResumePlaybackMode.entries,
            selected = uiState.playback.resumePlaybackMode,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setResumePlaybackMode,
        )

        SettingsChoiceDialog.STREAMING_QUALITY -> CinefinOptionDialog(
            title = "Streaming quality",
            supportingText = "Cap transcoding quality for compatible streams.",
            options = TranscodingQuality.entries,
            selected = uiState.playback.transcodingQuality,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setTranscodingQuality,
        )

        SettingsChoiceDialog.AUDIO_CHANNELS -> CinefinOptionDialog(
            title = "Audio channels",
            supportingText = "Limit the maximum audio channel count used for playback.",
            options = AudioChannelPreference.entries,
            selected = uiState.playback.audioChannels,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setAudioChannels,
        )

        SettingsChoiceDialog.SUBTITLE_TEXT_SIZE -> CinefinOptionDialog(
            title = "Subtitle text size",
            supportingText = "Preferred subtitle text size in the player.",
            options = SubtitleTextSize.entries,
            selected = uiState.subtitles.textSize,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setSubtitleTextSize,
        )

        SettingsChoiceDialog.SUBTITLE_BACKGROUND -> CinefinOptionDialog(
            title = "Subtitle background",
            supportingText = "Subtitle background treatment for readability.",
            options = SubtitleBackground.entries,
            selected = uiState.subtitles.background,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setSubtitleBackground,
        )

        null -> Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        expressiveColors.backgroundTop,
                        expressiveColors.backgroundBottom,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                TvScreenTopFocusAnchor(
                    state = screenFocus,
                    onFocused = {
                        listState.requestScrollToItem(0)
                    },
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = expressiveColors.chromeSurface.copy(alpha = 0.74f),
                    ),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = expressiveColors.titleAccent,
                        )
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Playback, subtitles, library actions, casting, and account security.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Text(
                        text = "Loading settings...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    SettingsSection(title = "Appearance") {
                        SettingsToggleListItem(
                            icon = Icons.Default.MotionPhotosPaused,
                            title = "Reduce motion",
                            description = "Respect reduced-motion preferences where possible.",
                            checked = uiState.appearance.respectReduceMotion,
                            modifier = Modifier
                                .focusRequester(screenFocus.primaryContentRequester)
                                .focusProperties {
                                    up = screenFocus.topAnchorRequester
                                },
                            onCheckedChange = viewModel::setRespectReduceMotion,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Playback") {
                        SettingsToggleListItem(
                            icon = Icons.Default.PlayArrow,
                            title = "Auto-play next episode",
                            description = "Start the next episode automatically near the end.",
                            checked = uiState.playback.autoPlayNextEpisode,
                            onCheckedChange = viewModel::setAutoPlayNextEpisode,
                        )
                        SettingsChoiceListItem(
                            icon = Icons.Default.PlayArrow,
                            title = "Resume playback",
                            description = "Choose how playback resumes when an item has saved progress.",
                            selectedLabel = uiState.playback.resumePlaybackMode.label,
                            onClick = { activeDialog = SettingsChoiceDialog.RESUME_PLAYBACK },
                        )
                        SettingsChoiceListItem(
                            icon = Icons.Default.HighQuality,
                            title = "Streaming quality",
                            description = "Cap transcoding quality for compatible streams.",
                            selectedLabel = uiState.playback.transcodingQuality.label,
                            onClick = { activeDialog = SettingsChoiceDialog.STREAMING_QUALITY },
                        )
                        SettingsChoiceListItem(
                            icon = Icons.Default.MusicNote,
                            title = "Audio channels",
                            description = "Limit the maximum audio channel count used for playback.",
                            selectedLabel = uiState.playback.audioChannels.label,
                            onClick = { activeDialog = SettingsChoiceDialog.AUDIO_CHANNELS },
                        )
                    }
                }

                item {
                    SettingsSection(title = "Subtitles") {
                        SettingsChoiceListItem(
                            icon = Icons.Default.Subtitles,
                            title = "Text size",
                            description = "Preferred subtitle text size in the player.",
                            selectedLabel = uiState.subtitles.textSize.name.replace('_', ' '),
                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_SIZE },
                        )
                        SettingsChoiceListItem(
                            icon = Icons.Default.ClosedCaption,
                            title = "Background",
                            description = "Subtitle background treatment for readability.",
                            selectedLabel = uiState.subtitles.background.name.replace('_', ' '),
                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_BACKGROUND },
                        )
                    }
                }

                item {
                    SettingsSection(title = "Library") {
                        SettingsToggleListItem(
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                            title = "Enable management actions",
                            description = "Allow sensitive actions like delete or metadata refresh when available.",
                            checked = uiState.libraryActions.enableManagementActions,
                            onCheckedChange = viewModel::setLibraryManagementActions,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Casting") {
                        SettingsToggleListItem(
                            icon = Icons.Default.Cast,
                            title = "Auto-reconnect",
                            description = "Reconnect to the last cast session automatically when possible.",
                            checked = uiState.cast.autoReconnect,
                            onCheckedChange = viewModel::setCastAutoReconnect,
                        )
                        SettingsToggleListItem(
                            icon = Icons.Default.Devices,
                            title = "Remember last device",
                            description = uiState.cast.lastDeviceName?.let {
                                "Keep the last used cast device available for faster reconnects. Last device: $it"
                            } ?: "Keep the last used cast device available for faster reconnects.",
                            checked = uiState.cast.rememberLastDevice,
                            onCheckedChange = viewModel::setRememberLastCastDevice,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Security") {
                        SettingsToggleListItem(
                            icon = Icons.Default.Security,
                            title = "Require strong auth for credentials",
                            description = "Protect credential access behind stronger device authentication when available.",
                            checked = uiState.credentialSecurity.requireStrongAuthForCredentials,
                            onCheckedChange = viewModel::setRequireStrongAuthForCredentials,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsToggleListItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun SettingsChoiceListItem(
    icon: ImageVector,
    title: String,
    description: String,
    selectedLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
