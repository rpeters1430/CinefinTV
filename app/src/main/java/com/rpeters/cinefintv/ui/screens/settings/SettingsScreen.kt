package com.rpeters.cinefintv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.AccentColor
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.ContrastLevel
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val expressiveColors = LocalCinefinExpressiveColors.current

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
                        SettingsChoiceRow(
                            title = "Theme mode",
                            description = "Choose the overall app theme behavior.",
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
                            onSelected = viewModel::setThemeMode,
                        )
                        SettingsToggleRow(
                            title = "Dynamic colors",
                            description = "Use wallpaper-derived Material colors when supported.",
                            checked = uiState.appearance.useDynamicColors,
                            onCheckedChange = viewModel::setUseDynamicColors,
                        )
                        SettingsChoiceRow(
                            title = "Accent color",
                            description = "Pick a fallback accent when dynamic colors are off.",
                            options = AccentColor.entries,
                            selected = uiState.appearance.accentColor,
                            labelFor = { it.name.replace('_', ' ') },
                            onSelected = viewModel::setAccentColor,
                        )
                        SettingsChoiceRow(
                            title = "Contrast",
                            description = "Increase contrast for stronger readability and focus states.",
                            options = ContrastLevel.entries,
                            selected = uiState.appearance.contrastLevel,
                            labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                            onSelected = viewModel::setContrastLevel,
                        )
                        SettingsToggleRow(
                            title = "Themed app icon",
                            description = "Use a wallpaper-adaptive icon when the launcher supports it.",
                            checked = uiState.appearance.useThemedIcon,
                            onCheckedChange = viewModel::setUseThemedIcon,
                        )
                        SettingsToggleRow(
                            title = "Edge-to-edge layout",
                            description = "Allow content and system surfaces to blend more tightly.",
                            checked = uiState.appearance.enableEdgeToEdge,
                            onCheckedChange = viewModel::setEnableEdgeToEdge,
                        )
                        SettingsToggleRow(
                            title = "Reduce motion",
                            description = "Respect reduced-motion preferences where possible.",
                            checked = uiState.appearance.respectReduceMotion,
                            onCheckedChange = viewModel::setRespectReduceMotion,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Playback") {
                        SettingsToggleRow(
                            title = "Auto-play next episode",
                            description = "Start the next episode automatically near the end.",
                            checked = uiState.playback.autoPlayNextEpisode,
                            onCheckedChange = viewModel::setAutoPlayNextEpisode,
                        )
                        SettingsChoiceRow(
                            title = "Resume playback",
                            description = "Choose how playback resumes when an item has saved progress.",
                            options = ResumePlaybackMode.entries,
                            selected = uiState.playback.resumePlaybackMode,
                            labelFor = { it.label },
                            onSelected = viewModel::setResumePlaybackMode,
                        )
                        SettingsChoiceRow(
                            title = "Streaming quality",
                            description = "Cap transcoding quality for compatible streams.",
                            options = TranscodingQuality.entries,
                            selected = uiState.playback.transcodingQuality,
                            labelFor = { it.label },
                            onSelected = viewModel::setTranscodingQuality,
                        )
                        SettingsChoiceRow(
                            title = "Audio channels",
                            description = "Limit the maximum audio channel count used for playback.",
                            options = AudioChannelPreference.entries,
                            selected = uiState.playback.audioChannels,
                            labelFor = { it.label },
                            onSelected = viewModel::setAudioChannels,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Subtitles") {
                        SettingsChoiceRow(
                            title = "Text size",
                            description = "Preferred subtitle text size in the player.",
                            options = SubtitleTextSize.entries,
                            selected = uiState.subtitles.textSize,
                            labelFor = { it.name.replace('_', ' ') },
                            onSelected = viewModel::setSubtitleTextSize,
                        )
                        SettingsChoiceRow(
                            title = "Background",
                            description = "Subtitle background treatment for readability.",
                            options = SubtitleBackground.entries,
                            selected = uiState.subtitles.background,
                            labelFor = { it.name.replace('_', ' ') },
                            onSelected = viewModel::setSubtitleBackground,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Library") {
                        SettingsToggleRow(
                            title = "Enable management actions",
                            description = "Allow sensitive actions like delete or metadata refresh when available.",
                            checked = uiState.libraryActions.enableManagementActions,
                            onCheckedChange = viewModel::setLibraryManagementActions,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Casting") {
                        SettingsToggleRow(
                            title = "Auto-reconnect",
                            description = "Reconnect to the last cast session automatically when possible.",
                            checked = uiState.cast.autoReconnect,
                            onCheckedChange = viewModel::setCastAutoReconnect,
                        )
                        SettingsToggleRow(
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
                        SettingsToggleRow(
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
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                if (option == selected) {
                    Button(onClick = { onSelected(option) }) {
                        Text(labelFor(option))
                    }
                } else {
                    OutlinedButton(onClick = { onSelected(option) }) {
                        Text(labelFor(option))
                    }
                }
            }
        }
    }
}
