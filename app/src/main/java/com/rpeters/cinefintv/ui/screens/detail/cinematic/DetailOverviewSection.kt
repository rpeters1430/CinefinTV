@file:OptIn(ExperimentalLayoutApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.screens.detail.DetailChipRow
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.DetailPosterArt
import com.rpeters.cinefintv.ui.screens.detail.MetaFactItem
import com.rpeters.cinefintv.ui.screens.detail.MetaFactStyle
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailOverviewSection(
    title: String,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    chips: List<String>,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    posterUrl: String? = null,
    posterTitle: String = title,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isFocused by remember(title) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .testTag(DetailTestTags.Overview)
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .blockBringIntoView()
            .focusProperties {
                if (upFocusRequester != null) {
                    up = upFocusRequester
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                when {
                    onNavigateUp != null &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        onNavigateUp()
                        true
                    }
                    onNavigateDown != null &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onNavigateDown()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { state ->
                isFocused = state.isFocused || state.hasFocus
            }
            .focusable()
            .graphicsLayer {
                shadowElevation = if (isFocused) 10.dp.toPx() else 0.dp.toPx()
            }
            .background(
                color = expressiveColors.detailPanel.copy(alpha = 0.68f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) {
                    expressiveColors.focusRing
                } else {
                    expressiveColors.borderSubtle.copy(alpha = 0.32f)
                },
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(horizontal = spacing.gutter, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.gutter),
            verticalAlignment = Alignment.Top,
        ) {
            if (posterUrl != null) {
                DetailPosterArt(
                    imageUrl = posterUrl,
                    title = posterTitle,
                    modifier = Modifier
                        .width(220.dp)
                        .height(330.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
                ) {
                    Text(
                        text = "ABOUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFocused) expressiveColors.focusRing else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (description.isNotBlank()) description else "No overview available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (factItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.elementGap)) {
                        Text(
                            text = "DETAILS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                        ) {
                            factItems.forEach { item ->
                                MetaFactItem(
                                    icon = item.icon,
                                    label = item.label,
                                    value = item.value,
                                    style = MetaFactStyle.Card,
                                )
                            }
                        }
                    }
                }

                if (chips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.elementGap)) {
                        Text(
                            text = "CATEGORIES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        DetailChipRow(labels = chips)
                    }
                }
            }
        }
    }
}
