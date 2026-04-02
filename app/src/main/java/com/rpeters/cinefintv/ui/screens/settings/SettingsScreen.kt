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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.rpeters.cinefintv.data.preferences.SubtitleTextColor
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.preferences.VideoSeekIncrement
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.components.CinefinOptionDialog
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

private enum class SettingsChoiceDialog {
    THEME_MODE,
    ACCENT_COLOR,
    CONTRAST_LEVEL,
    RESUME_PLAYBACK,
    VIDEO_SEEK_INCREMENT,
    STREAMING_QUALITY,
    AUDIO_CHANNELS,
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
}

@Composable
fun SettingsScreen(
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
    val firstSectionItemModifier = Modifier
        .focusRequester(firstSectionItemRequester)
        .then(
            destinationFocus.drawerEscapeModifier(
                isLeftEdge = true,
                up = categoryFocusRequesters.getValue(selectedCategory),
            )
        )

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
                    categoryFocusRequesters = categoryFocusRequesters,
                    sectionFocusRequester = firstSectionItemRequester,
                    destinationFocus = destinationFocus,
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
                            description = "Global TV theme controls applied across the app.",
                            icon = Icons.Default.Palette,
                        ) {
                            SettingsChoiceRow(
                                icon = Icons.Default.Palette,
                                title = "Theme mode",
                                description = "System, light, dark, or AMOLED black.",
                                selectedLabel = uiState.appearance.themeMode.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.THEME_MODE },
                                modifier = firstSectionItemModifier,
                            )
                            SettingsToggleRow(
                                icon = Icons.Default.Palette,
                                title = "Use dynamic colors",
                                description = "Use artwork-derived accent colors across the TV UI.",
                                checked = uiState.appearance.useDynamicColors,
                                onCheckedChange = viewModel::setUseDynamicColors,
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.Palette,
                                title = "Accent color",
                                description = "Fallback accent color when dynamic colors are disabled.",
                                selectedLabel = uiState.appearance.accentColor.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.ACCENT_COLOR },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.Contrast,
                                title = "Contrast level",
                                description = "Strengthen focus and surface separation.",
                                selectedLabel = uiState.appearance.contrastLevel.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.CONTRAST_LEVEL },
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
                                modifier = firstSectionItemModifier,
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.PlayArrow,
                                title = "Resume playback",
                                description = "Control how saved progress is handled.",
                                selectedLabel = uiState.playback.resumePlaybackMode.label,
                                onClick = { activeDialog = SettingsChoiceDialog.RESUME_PLAYBACK },
                            )
                            SettingsChoiceRow(
                                icon = Icons.Default.Tune,
                                title = "Seek step",
                                description = "Choose how far forward and back playback jumps.",
                                selectedLabel = uiState.playback.videoSeekIncrement.label,
                                onClick = { activeDialog = SettingsChoiceDialog.VIDEO_SEEK_INCREMENT },
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
                            description = "Player subtitle styling that applies during video playback.",
                            icon = Icons.Default.Subtitles,
                        ) {
                            SettingsChoiceRow(
                                icon = Icons.Default.Subtitles,
                                title = "Text size",
                                description = "Preferred subtitle text size.",
                                selectedLabel = uiState.subtitles.textSize.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_SIZE },
                                modifier = firstSectionItemModifier,
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
                            SettingsChoiceRow(
                                icon = Icons.Default.ClosedCaption,
                                title = "Text color",
                                description = "Set the subtitle text color used in the player.",
                                selectedLabel = uiState.subtitles.textColor.name.replace('_', ' '),
                                onClick = { activeDialog = SettingsChoiceDialog.SUBTITLE_TEXT_COLOR },
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
    categoryFocusRequesters: Map<SettingsCategory, FocusRequester>,
    sectionFocusRequester: FocusRequester,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(SettingsCategory.entries) { index, category ->
            val isSelected = category == selected
            OutlinedButton(
                onClick = { onCategorySelected(category) },
                modifier = Modifier
                    .focusRequester(categoryFocusRequesters.getValue(category))
                    .focusProperties {
                        destinationFocus.drawerFocusRequester?.let { up = it }
                        down = sectionFocusRequester
                        left = categoryFocusRequesters[SettingsCategory.entries.getOrNull(index - 1)]
                            ?: destinationFocus.drawerFocusRequester
                            ?: FocusRequester.Cancel
                        right = categoryFocusRequesters[SettingsCategory.entries.getOrNull(index + 1)]
                            ?: FocusRequester.Cancel
                    },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    } else {
                        expressiveColors.focusGlow.copy(alpha = 0.24f)
                    },
                    focusedContentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                border = androidx.tv.material3.ButtonDefaults.border(
                    border = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                expressiveColors.borderSubtle.copy(alpha = 0.7f)
                            },
                        ),
                    ),
                    focusedBorder = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = expressiveColors.focusRing,
                        ),
                    ),
                ),
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
