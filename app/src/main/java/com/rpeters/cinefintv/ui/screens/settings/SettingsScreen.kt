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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.AccentColor
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.ContrastLevel
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.components.CinefinOptionDialog
import com.rpeters.cinefintv.ui.components.CinefinSettingListItem
import com.rpeters.cinefintv.ui.components.CinefinSwitchListItem
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

private enum class SettingsChoiceDialog {
    THEME_MODE,
    ACCENT_COLOR,
    CONTRAST,
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

    when (activeDialog) {
        SettingsChoiceDialog.THEME_MODE -> CinefinOptionDialog(
            title = "Theme mode",
            supportingText = "Choose the overall app theme behavior.",
            options = ThemeMode.entries,
            selected = uiState.appearance.themeMode,
            labelFor = {
                when (it) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                    ThemeMode.AMOLED_BLACK -> "AMOLED Black"
                }
            },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setThemeMode,
        )

        SettingsChoiceDialog.ACCENT_COLOR -> CinefinOptionDialog(
            title = "Accent color",
            supportingText = "Pick a fallback accent when dynamic colors are off.",
            options = AccentColor.entries,
            selected = uiState.appearance.accentColor,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setAccentColor,
        )

        SettingsChoiceDialog.CONTRAST -> CinefinOptionDialog(
            title = "Contrast",
            supportingText = "Increase contrast for stronger readability and focus states.",
            options = ContrastLevel.entries,
            selected = uiState.appearance.contrastLevel,
            labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setContrastLevel,
        )

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
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    expressiveColors.chromeSurface,
                                    expressiveColors.accentSurface.copy(alpha = 0.95f),
                                ),
                            ),
                            shape = RoundedCornerShape(28.dp),
                        )
                        .padding(28.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = expressiveColors.titleAccent,
                        )
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Playback, subtitles, library actions, casting, and account security.",
                            style = MaterialTheme.typography.bodyLarge,
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
                        SettingsChoiceListItem(
                            title = "Theme mode",
                            description = "Choose the overall app theme behavior.",
                            selectedLabel = when (uiState.appearance.themeMode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.AMOLED_BLACK -> "AMOLED Black"
                            },
                            onClick = { activeDialog = SettingsChoiceDialog.THEME_MODE },
                        )
                        SettingsToggleListItem(
                            title = "Dynamic colors",
                            description = "Use wallpaper-derived Material colors when supported.",
                            checked = uiState.appearance.useDynamicColors,
                            onCheckedChange = viewModel::setUseDynamicColors,
                        )
                        SettingsChoiceListItem(
                            title = "Accent color",
                            description = "Pick a fallback accent when dynamic colors are off.",
                            selectedLabel = uiState.appearance.accentColor.name.replace('_', ' '),
                            onClick = { activeDialog = SettingsChoiceDialog.ACCENT_COLOR },
                        )
                        SettingsChoiceListItem(
                            title = "Contrast",
                            description = "Increase contrast for stronger readability and focus states.",
                            selectedLabel = uiState.appearance.contrastLevel.name
                                .lowercase()
                                .replaceFirstChar(Char::uppercase),
                            onClick = { activeDialog = SettingsChoiceDialog.CONTRAST },
                        )
                        SettingsToggleListItem(
                            title = "Themed app icon",
                            description = "Use a wallpaper-adaptive icon when the launcher supports it.",
                            checked = uiState.appearance.useThemedIcon,
                            onCheckedChange = viewModel::setUseThemedIcon,
                        )
                        SettingsToggleListItem(
                            title = "Edge-to-edge layout",
                            description = "Allow content and system surfaces to blend more tightly.",
                            checked = uiState.appearance.enableEdgeToEdge,
                            onCheckedChange = viewModel::setEnableEdgeToEdge,
                        )
                        SettingsToggleListItem(
                            title = "Reduce motion",
                            description = "Respect reduced-motion preferences where possible.",
                            checked = uiState.appearance.respectReduceMotion,
                            onCheckedChange = viewModel::setRespectReduceMotion,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Playback") {
                        SettingsToggleListItem(
                            title = "Auto-play next episode",
                            description = "Start the next episode automatically near the end.",
                            checked = uiState.playback.autoPlayNextEpisode,
                            onCheckedChange = viewModel::setAutoPlayNextEpisode,
                        )
                        SettingsChoiceListItem(
                            title = "Resume playback",
                            description = "Choose how playback resumes when an item has saved progress.",
                            selectedLabel = uiState.playback.resumePlaybackMode.label,
                            onClick = { activeDialog = SettingsChoiceDialog.RESUME_PLAYBACK },
                        )
                        SettingsChoiceListItem(
                            title = "Streaming quality",
                            description = "Cap transcoding quality for compatible streams.",
                            selectedLabel = uiState.playback.transcodingQuality.label,
                            onClick = { activeDialog = SettingsChoiceDialog.STREAMING_QUALITY },
                        )
                        SettingsChoiceListItem(
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
                            title = "Text size",
                            description = "Preferred subtitle text size in the player.",
                            selectedLabel = uiState.subtitles.textSize.name.replace('_', ' '),
                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_SIZE },
                        )
                        SettingsChoiceListItem(
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
                            title = "Auto-reconnect",
                            description = "Reconnect to the last cast session automatically when possible.",
                            checked = uiState.cast.autoReconnect,
                            onCheckedChange = viewModel::setCastAutoReconnect,
                        )
                        SettingsToggleListItem(
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
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsToggleListItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CinefinSwitchListItem(
        headline = title,
        supporting = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingsChoiceListItem(
    title: String,
    description: String,
    selectedLabel: String,
    onClick: () -> Unit,
) {
    CinefinSettingListItem(
        headline = title,
        supporting = description,
        trailingText = selectedLabel,
        onClick = onClick,
    )
}
