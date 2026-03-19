package com.rpeters.cinefintv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MotionPhotosPaused
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
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
import com.rpeters.cinefintv.data.preferences.SubtitleFont
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.components.CinefinOptionDialog
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

private enum class SettingsChoiceDialog {
    THEME_MODE,
    ACCENT_COLOR,
    CONTRAST_LEVEL,
    RESUME_PLAYBACK,
    STREAMING_QUALITY,
    AUDIO_CHANNELS,
    SUBTITLE_TEXT_SIZE,
    SUBTITLE_FONT,
    SUBTITLE_BACKGROUND,
}

private enum class SettingsCategory(
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    APPEARANCE(
        label = "Appearance",
        description = "Theme and visual behavior",
        icon = Icons.Default.Palette,
    ),
    PLAYBACK(
        label = "Playback",
        description = "Video and audio controls",
        icon = Icons.Default.PlayArrow,
    ),
    SUBTITLES(
        label = "Subtitles",
        description = "Readability and style",
        icon = Icons.Default.Subtitles,
    ),
    LIBRARY(
        label = "Library",
        description = "Collection management",
        icon = Icons.AutoMirrored.Filled.LibraryBooks,
    ),
    CASTING(
        label = "Casting",
        description = "Device handoff behavior",
        icon = Icons.Default.Cast,
    ),
    SECURITY(
        label = "Security",
        description = "Credential protections",
        icon = Icons.Default.Security,
    ),
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val expressiveColors = LocalCinefinExpressiveColors.current
    val listState = rememberLazyListState()
    val primaryContentRequester = remember { FocusRequester() }
    var activeDialog by remember { mutableStateOf<SettingsChoiceDialog?>(null) }
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }

    when (activeDialog) {
        SettingsChoiceDialog.THEME_MODE -> CinefinOptionDialog(
            title = "Theme mode",
            supportingText = "Select how Cinefin TV should look.",
            options = ThemeMode.entries,
            selected = uiState.appearance.themeMode,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setThemeMode,
        )
        SettingsChoiceDialog.ACCENT_COLOR -> CinefinOptionDialog(
            title = "Accent color",
            supportingText = "Used when dynamic colors are disabled.",
            options = AccentColor.entries,
            selected = uiState.appearance.accentColor,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setAccentColor,
        )
        SettingsChoiceDialog.CONTRAST_LEVEL -> CinefinOptionDialog(
            title = "Contrast level",
            supportingText = "Increase contrast for readability.",
            options = ContrastLevel.entries,
            selected = uiState.appearance.contrastLevel,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setContrastLevel,
        )
        SettingsChoiceDialog.RESUME_PLAYBACK -> CinefinOptionDialog(
            title = "Resume playback",
            supportingText = "Choose how playback resumes with saved progress.",
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
            supportingText = "Limit maximum channel count used for playback.",
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
        SettingsChoiceDialog.SUBTITLE_FONT -> CinefinOptionDialog(
            title = "Subtitle font",
            supportingText = "Choose subtitle font style.",
            options = SubtitleFont.entries,
            selected = uiState.subtitles.font,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setSubtitleFont,
        )
        SettingsChoiceDialog.SUBTITLE_BACKGROUND -> CinefinOptionDialog(
            title = "Subtitle background",
            supportingText = "Background treatment for subtitle readability.",
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
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                SettingsCategorySelector(
                    selected = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                )
            }

            if (uiState.isLoading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = expressiveColors.surfaceContainerLow.copy(alpha = 0.92f),
                        ),
                    ) {
                        Text(
                            text = "Loading settings...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                item {
                    when (selectedCategory) {
                        SettingsCategory.APPEARANCE -> SettingsSectionCard(
                            title = "Appearance",
                            description = "Material expressive controls and system-level visual behavior.",
                            icon = Icons.Default.Palette,
                        ) {
                            SettingsChoiceRow(
                                icon = Icons.Default.Palette,
                                title = "Theme mode",
                                description = "System, light, dark, or AMOLED black.",
                                selectedLabel = uiState.appearance.themeMode.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.THEME_MODE },
                                
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Palette,
                                title = "Use dynamic colors",
                                description = "Extract accent colors from media artwork.",
                                checked = uiState.appearance.useDynamicColors,
                                onCheckedChange = viewModel::setUseDynamicColors,
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.Palette,
                                title = "Accent color",
                                description = "Fallback accent when dynamic colors are off.",
                                selectedLabel = uiState.appearance.accentColor.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.ACCENT_COLOR },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.Contrast,
                                title = "Contrast level",
                                description = "Adjust readability with stronger contrast.",
                                selectedLabel = uiState.appearance.contrastLevel.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.CONTRAST_LEVEL },
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Palette,
                                title = "Use themed app icon",
                                description = "Apply dynamic icon tint where supported.",
                                checked = uiState.appearance.useThemedIcon,
                                onCheckedChange = viewModel::setUseThemedIcon,
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Devices,
                                title = "Enable edge-to-edge",
                                description = "Use edge-to-edge layout where available.",
                                checked = uiState.appearance.enableEdgeToEdge,
                                onCheckedChange = viewModel::setEnableEdgeToEdge,
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.MotionPhotosPaused,
                                title = "Reduce motion",
                                description = "Respect reduced-motion preference where possible.",
                                checked = uiState.appearance.respectReduceMotion,
                                onCheckedChange = viewModel::setRespectReduceMotion,
                            )
                        }
                        SettingsCategory.PLAYBACK -> SettingsSectionCard(
                            title = "Playback",
                            description = "Defaults for streaming behavior and episode progression.",
                            icon = Icons.Default.PlayArrow,
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Default.PlayArrow,
                                title = "Auto-play next episode",
                                description = "Start the next episode near the end of current playback.",
                                checked = uiState.playback.autoPlayNextEpisode,
                                onCheckedChange = viewModel::setAutoPlayNextEpisode,
                                
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.PlayArrow,
                                title = "Resume playback",
                                description = "Control how saved progress is handled.",
                                selectedLabel = uiState.playback.resumePlaybackMode.label,
                                onClick = { activeDialog = SettingsChoiceDialog.RESUME_PLAYBACK },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.HighQuality,
                                title = "Streaming quality",
                                description = "Cap transcoding quality for stable playback.",
                                selectedLabel = uiState.playback.transcodingQuality.label,
                                onClick = { activeDialog = SettingsChoiceDialog.STREAMING_QUALITY },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.MusicNote,
                                title = "Audio channels",
                                description = "Limit maximum playback channel count.",
                                selectedLabel = uiState.playback.audioChannels.label,
                                onClick = { activeDialog = SettingsChoiceDialog.AUDIO_CHANNELS },
                            )
                        }
                        SettingsCategory.SUBTITLES -> SettingsSectionCard(
                            title = "Subtitles",
                            description = "Tune subtitle readability for distance viewing.",
                            icon = Icons.Default.Subtitles,
                        ) {
                            SettingsChoiceRow(
                                icon = Icons.Default.Subtitles,
                                title = "Text size",
                                description = "Preferred subtitle text size.",
                                selectedLabel = uiState.subtitles.textSize.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_SIZE },
                                
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.ClosedCaption,
                                title = "Font",
                                description = "Choose subtitle font style.",
                                selectedLabel = uiState.subtitles.font.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_FONT },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.ClosedCaption,
                                title = "Background",
                                description = "Improve subtitle contrast over bright scenes.",
                                selectedLabel = uiState.subtitles.background.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_BACKGROUND },
                            )
                        }
                        SettingsCategory.LIBRARY -> SettingsSectionCard(
                            title = "Library",
                            description = "Sensitive management actions for media libraries.",
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        ) {
                            SettingsToggleRow(
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                title = "Enable management actions",
                                description = "Allow delete/refresh actions when available.",
                                checked = uiState.libraryActions.enableManagementActions,
                                onCheckedChange = viewModel::setLibraryManagementActions,
                                
                            )
                        }
                        SettingsCategory.CASTING -> SettingsSectionCard(
                            title = "Casting",
                            description = "Connection behavior for external playback devices.",
                            icon = Icons.Default.Cast,
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Default.Cast,
                                title = "Auto-reconnect",
                                description = "Reconnect to the previous cast session when possible.",
                                checked = uiState.cast.autoReconnect,
                                onCheckedChange = viewModel::setCastAutoReconnect,
                                
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Devices,
                                title = "Remember last device",
                                description = uiState.cast.lastDeviceName?.let {
                                    "Store the last cast target. Last: $it"
                                } ?: "Store the last cast target for faster reconnects.",
                                checked = uiState.cast.rememberLastDevice,
                                onCheckedChange = viewModel::setRememberLastCastDevice,
                            )
                        }
                        SettingsCategory.SECURITY -> SettingsSectionCard(
                            title = "Security",
                            description = "Credential access protection and auth hardening.",
                            icon = Icons.Default.Security,
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Default.Security,
                                title = "Require strong auth for credentials",
                                description = "Require stronger device auth before credential access.",
                                checked = uiState.credentialSecurity.requireStrongAuthForCredentials,
                                onCheckedChange = viewModel::setRequireStrongAuthForCredentials,
                                
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHero(
    title: String,
    subtitle: String,
    primaryValue: String,
    secondaryValue: String,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            expressiveColors.surfaceContainerHigh.copy(alpha = 0.96f),
                            expressiveColors.accentSurface.copy(alpha = 0.86f),
                            expressiveColors.surfaceContainerLow.copy(alpha = 0.94f),
                        ),
                    ),
                    shape = RoundedCornerShape(30.dp),
                )
                .padding(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = expressiveColors.titleAccent,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(text = primaryValue)
                    StatusPill(text = secondaryValue)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Surface(
        shape = RoundedCornerShape(50),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.pillStrong,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SettingsCategorySelector(
    selected: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    initialModifier: Modifier = Modifier,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(SettingsCategory.entries) { index, category ->
            // Use the initialModifier (which has the primary requester) for the SELECTED item
            // so that navigating from the TabBar hits the active category.
            val modifier = if (category == selected) initialModifier else Modifier
            if (category == selected) {
                Button(
                    onClick = { onCategorySelected(category) },
                    modifier = modifier,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = category.icon, contentDescription = null)
                        Text(text = category.label)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onCategorySelected(category) },
                    modifier = modifier,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = category.icon, contentDescription = null)
                        Text(text = category.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.surfaceContainerLow.copy(alpha = 0.93f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = expressiveColors.accentSurface.copy(alpha = 0.66f),
                    ),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = expressiveColors.titleAccent,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.surfaceContainer.copy(alpha = 0.92f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
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
private fun SettingsChoiceRow(
    icon: ImageVector,
    title: String,
    description: String,
    selectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Surface(
                shape = RoundedCornerShape(50),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        },
    )
}
