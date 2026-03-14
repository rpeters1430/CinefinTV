package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailActionRow(
    state: DetailUiState.Content,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissActionError: () -> Unit,
    playButtonRequester: FocusRequester,
    firstShelfRequester: FocusRequester?,
    onFocusedDescriptionChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val item = state.item
    val isSeriesDetail = state.seasons.isNotEmpty()
    val hideSecondaryActions = isSeriesDetail || state.episodesBySeasonId.isNotEmpty()
    
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var selectedSubtitle by remember(item.subtitleOptions) {
        mutableStateOf(item.subtitleOptions.firstOrNull())
    }

    if (showSubtitleDialog) {
        val firstOptionRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstOptionRequester.requestFocus() }
        Popup(
            onDismissRequest = { showSubtitleDialog = false },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(spacing.cornerCard),
                    modifier = Modifier.width(400.dp),
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Select Subtitle",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(item.subtitleOptions) { option ->
                                val isSelected = selectedSubtitle == option
                                val isFirst = item.subtitleOptions.firstOrNull() == option
                                val optionModifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isFirst) Modifier.focusRequester(firstOptionRequester) else Modifier)
                                if (isSelected) {
                                    Button(
                                        onClick = { selectedSubtitle = option; showSubtitleDialog = false },
                                        modifier = optionModifier
                                    ) { Text(option) }
                                } else {
                                    OutlinedButton(
                                        onClick = { selectedSubtitle = option; showSubtitleDialog = false },
                                        modifier = optionModifier
                                    ) { Text(option) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap)
    ) {
        if (item.subtitleOptions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { showSubtitleDialog = true },
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                        .focusProperties {
                            firstShelfRequester?.let { down = it }
                        }
                ) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedSubtitle ?: "Choose subtitle")
                }
            }
        }

        if (!state.actionErrorMessage.isNullOrBlank()) {
            Text(
                text = state.actionErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(spacing.cornerCard))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            expressiveColors.chromeSurface,
                            expressiveColors.accentSurface.copy(alpha = 0.88f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = expressiveColors.borderSubtle.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(spacing.cornerCard),
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.playableItemId?.let { playableItemId ->
                Button(
                    onClick = { onPlay(playableItemId) },
                    modifier = Modifier
                        .focusRequester(playButtonRequester)
                        .onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                        .focusProperties {
                            firstShelfRequester?.let { down = it }
                        }
                ) {
                    Text(state.playButtonLabel)
                }
            }

            if (!hideSecondaryActions && state.playableItemId != null) {
                if (state.isDeleting) {
                    Surface(shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "Deleting...",
                            modifier = Modifier.padding(
                                horizontal = 18.dp,
                                vertical = 12.dp,
                            ),
                        )
                    }
                } else if (state.isDeleteConfirmationVisible) {
                    Button(
                        onClick = onConfirmDelete,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                    ) {
                        Text("Confirm Delete")
                    }
                    OutlinedButton(
                        onClick = onCancelDelete,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                    ) {
                        Text("Cancel")
                    }
                } else {
                    OutlinedButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                            .focusProperties {
                                firstShelfRequester?.let { down = it }
                            }
                    ) {
                        Text("Delete")
                    }
                }
            }

            if (!hideSecondaryActions && state.playableItemId != null) {
                OutlinedButton(
                    onClick = {
                        onDismissActionError()
                        onBack()
                    },
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) onFocusedDescriptionChange(null) }
                        .focusProperties {
                            firstShelfRequester?.let { down = it }
                        }
                ) {
                    Text("Back")
                }
            }
        }
    }
}
