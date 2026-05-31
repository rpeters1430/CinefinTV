package com.rpeters.cinefintv.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.preferences.AccentColor
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.AudioLanguagePreference
import com.rpeters.cinefintv.data.preferences.SubtitleLanguagePreference
import com.rpeters.cinefintv.data.preferences.ContrastLevel
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleFont
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.SubtitleTextColor
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.preferences.VideoSeekIncrement
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.components.CinefinOptionDialog
import com.rpeters.cinefintv.ui.components.CinefinSettingListItem
import com.rpeters.cinefintv.ui.components.CinefinSwitchListItem
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

private enum class SettingsChoiceDialog {
    THEME_MODE,
    ACCENT_COLOR,
    CONTRAST_LEVEL,
    RESUME_PLAYBACK,
    VIDEO_SEEK_INCREMENT,
    STREAMING_QUALITY,
    AUDIO_CHANNELS,
    AUDIO_LANGUAGE,
    SUBTITLE_LANGUAGE,
    SUBTITLE_TEXT_SIZE,
    SUBTITLE_FONT,
    SUBTITLE_BACKGROUND,
    SUBTITLE_TEXT_COLOR,
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
    ACCOUNT(
        label = "Account",
        description = "Session and sign-out controls",
        icon = Icons.Default.Tune,
    ),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToProfilePicker: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val expressiveColors = LocalCinefinExpressiveColors.current
    val listState = rememberLazyListState()
    val categoryFocusRequesters = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val firstSectionItemRequester = remember { FocusRequester() }
    var activeDialog by remember { mutableStateOf<SettingsChoiceDialog?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.PLAYBACK) }
    val destinationFocus = rememberTopLevelDestinationFocus(
        categoryFocusRequesters.getValue(selectedCategory)
    )

    // D-pad navigation helper: Left goes back to category list
    val firstSectionItemModifier = Modifier
        .focusRequester(firstSectionItemRequester)
        .focusProperties {
            left = categoryFocusRequesters.getValue(selectedCategory)
        }

    when (activeDialog) {
        SettingsChoiceDialog.THEME_MODE -> CinefinOptionDialog(
            title = "Theme mode",
            supportingText = "Switch between system, light, dark, and AMOLED black.",
            options = ThemeMode.entries,
            selected = uiState.appearance.themeMode,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setThemeMode,
        )
        SettingsChoiceDialog.ACCENT_COLOR -> CinefinOptionDialog(
            title = "Accent color",
            supportingText = "Fallback accent when dynamic colors are off.",
            options = AccentColor.entries,
            selected = uiState.appearance.accentColor,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setAccentColor,
        )
        SettingsChoiceDialog.CONTRAST_LEVEL -> CinefinOptionDialog(
            title = "Contrast level",
            supportingText = "Increase focus-ring and surface separation for readability.",
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
        SettingsChoiceDialog.VIDEO_SEEK_INCREMENT -> CinefinOptionDialog(
            title = "Seek step",
            supportingText = "How far left and right skip during video playback.",
            options = VideoSeekIncrement.entries,
            selected = uiState.playback.videoSeekIncrement,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setVideoSeekIncrement,
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
        SettingsChoiceDialog.AUDIO_LANGUAGE -> CinefinOptionDialog(
            title = "Default audio language",
            supportingText = "Choose which audio language playback should prefer first.",
            options = AudioLanguagePreference.entries,
            selected = uiState.playback.audioLanguage,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setAudioLanguage,
        )
        SettingsChoiceDialog.SUBTITLE_LANGUAGE -> CinefinOptionDialog(
            title = "Default subtitle language",
            supportingText = "Choose which subtitle track to load automatically. \"None\" disables subtitles unless you switch them on manually.",
            options = SubtitleLanguagePreference.entries,
            selected = uiState.playback.subtitleLanguage,
            labelFor = { it.label },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setSubtitleLanguage,
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
        SettingsChoiceDialog.SUBTITLE_TEXT_COLOR -> CinefinOptionDialog(
            title = "Subtitle text color",
            supportingText = "Change subtitle text color in the player.",
            options = SubtitleTextColor.entries,
            selected = uiState.subtitles.textColor,
            labelFor = { it.name.replace('_', ' ') },
            onDismissRequest = { activeDialog = null },
            onOptionSelected = viewModel::setSubtitleTextColor,
        )
        null -> Unit
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        expressiveColors.backgroundTop,
                        expressiveColors.backgroundBottom,
                    ),
                ),
            )
            .padding(top = 32.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Left Pane: Settings Categories
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(start = 56.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rebuilt beautiful title section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = expressiveColors.titleAccent,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            // List of Categories using TV ListItem
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(SettingsCategory.entries) { index, category ->
                    val isSelected = category == selectedCategory
                    val focusRequester = categoryFocusRequesters.getValue(category)

                    ListItem(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    selectedCategory = category
                                }
                            }
                            .focusProperties {
                                destinationFocus.drawerFocusRequester?.let { left = it }
                                right = firstSectionItemRequester
                                up = categoryFocusRequesters[SettingsCategory.entries.getOrNull(index - 1)]
                                    ?: FocusRequester.Cancel
                                down = categoryFocusRequesters[SettingsCategory.entries.getOrNull(index + 1)]
                                    ?: FocusRequester.Cancel
                            },
                        leadingContent = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        headlineContent = {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = if (isSelected) expressiveColors.accentSurface.copy(alpha = 0.25f) else Color.Transparent,
                            focusedContainerColor = expressiveColors.focusGlow.copy(alpha = 0.16f),
                            selectedContainerColor = expressiveColors.accentSurface.copy(alpha = 0.4f),
                            focusedSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Right Pane: Active Category Detail List
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 56.dp, start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = selectedCategory.label,
                style = MaterialTheme.typography.headlineSmall,
                color = expressiveColors.titleAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = SurfaceDefaults.colors(
                                containerColor = expressiveColors.surfaceContainerLow.copy(alpha = 0.6f)
                            )
                        ) {
                            Text(
                                text = "Loading details...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = SurfaceDefaults.colors(
                                containerColor = expressiveColors.surfaceContainerLow.copy(alpha = 0.8f)
                            ),
                            border = androidx.tv.material3.Border(
                                border = BorderStroke(
                                    1.dp,
                                    expressiveColors.borderSubtle.copy(alpha = 0.4f)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (selectedCategory) {
                                    SettingsCategory.APPEARANCE -> {
                                        CinefinSettingListItem(
                                            headline = "Theme mode",
                                            supporting = "Switch between system, light, dark, and AMOLED black.",
                                            trailingText = uiState.appearance.themeMode.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.THEME_MODE },
                                            modifier = firstSectionItemModifier
                                        )
                                        CinefinSwitchListItem(
                                            headline = "Use dynamic colors",
                                            supporting = "Use artwork-derived accent colors across the TV UI.",
                                            checked = uiState.appearance.useDynamicColors,
                                            onCheckedChange = viewModel::setUseDynamicColors
                                        )
                                        CinefinSettingListItem(
                                            headline = "Accent color",
                                            supporting = "Fallback accent color when dynamic colors are disabled.",
                                            trailingText = uiState.appearance.accentColor.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.ACCENT_COLOR }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Contrast level",
                                            supporting = "Strengthen focus-ring and surface separation for readability.",
                                            trailingText = uiState.appearance.contrastLevel.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.CONTRAST_LEVEL }
                                        )
                                    }
                                    SettingsCategory.PLAYBACK -> {
                                        CinefinSwitchListItem(
                                            headline = "Auto-play next episode",
                                            supporting = "Start the next episode near the end of current playback.",
                                            checked = uiState.playback.autoPlayNextEpisode,
                                            onCheckedChange = viewModel::setAutoPlayNextEpisode,
                                            modifier = firstSectionItemModifier
                                        )
                                        CinefinSettingListItem(
                                            headline = "Resume playback",
                                            supporting = "Control how saved progress is handled.",
                                            trailingText = uiState.playback.resumePlaybackMode.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.RESUME_PLAYBACK }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Seek step",
                                            supporting = "Choose how far forward and back playback jumps.",
                                            trailingText = uiState.playback.videoSeekIncrement.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.VIDEO_SEEK_INCREMENT }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Streaming quality",
                                            supporting = "Cap transcoding quality for stable playback.",
                                            trailingText = uiState.playback.transcodingQuality.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.STREAMING_QUALITY }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Audio channels",
                                            supporting = "Limit maximum playback channel count.",
                                            trailingText = uiState.playback.audioChannels.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.AUDIO_CHANNELS }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Default audio language",
                                            supporting = "Preferred language when playback starts.",
                                            trailingText = uiState.playback.audioLanguage.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.AUDIO_LANGUAGE }
                                        )
                                    }
                                    SettingsCategory.SUBTITLES -> {
                                        CinefinSettingListItem(
                                            headline = "Default subtitle language",
                                            supporting = "Subtitle track to load automatically at the start of playback.",
                                            trailingText = uiState.playback.subtitleLanguage.label,
                                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_LANGUAGE },
                                            modifier = firstSectionItemModifier
                                        )
                                        CinefinSettingListItem(
                                            headline = "Text size",
                                            supporting = "Preferred subtitle text size in the player.",
                                            trailingText = uiState.subtitles.textSize.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_SIZE }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Font",
                                            supporting = "Choose subtitle font style.",
                                            trailingText = uiState.subtitles.font.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_FONT }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Background",
                                            supporting = "Improve subtitle contrast over bright scenes.",
                                            trailingText = uiState.subtitles.background.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_BACKGROUND }
                                        )
                                        CinefinSettingListItem(
                                            headline = "Text color",
                                            supporting = "Set the subtitle text color used in the player.",
                                            trailingText = uiState.subtitles.textColor.name.replace('_', ' '),
                                            onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_COLOR }
                                        )
                                    }
                                    SettingsCategory.ACCOUNT -> {
                                        CinefinSettingListItem(
                                            headline = "Switch Profile",
                                            supporting = "Switch between saved Jellyfin accounts or add a new one.",
                                            trailingText = "Profiles",
                                            onClick = onNavigateToProfilePicker,
                                            modifier = firstSectionItemModifier
                                        )
                                        CinefinSettingListItem(
                                            headline = "Sign out",
                                            supporting = uiState.signOutError ?: "Remove this device session and return to server sign-in.",
                                            trailingText = if (uiState.isSigningOut) "Signing out..." else "Sign out",
                                            onClick = viewModel::logout
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
