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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val summaryFacts = factItems.take(3)
    val detailFacts = factItems.drop(3)

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
                brush = Brush.verticalGradient(
                    0f to expressiveColors.detailPanel.copy(alpha = 0.8f),
                    1f to expressiveColors.detailPanelMuted.copy(alpha = 0.92f),
                ),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .border(
                width = if (isFocused) {
                    2.dp
                } else {
                    1.dp
                },
                color = if (isFocused) {
                    expressiveColors.focusRing
                } else {
                    expressiveColors.borderSubtle.copy(alpha = 0.32f)
                },
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(horizontal = spacing.gutter.div(1.5f), vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OverviewSectionLabel(
            text = "Editorial Overview",
            highlighted = isFocused,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.gutter.div(1.5f)),
            verticalAlignment = Alignment.Top,
        ) {
            if (posterUrl != null) {
                DetailPosterArt(
                    imageUrl = posterUrl,
                    title = posterTitle,
                    modifier = Modifier
                        .width(180.dp)
                        .height(270.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OverviewNarrativeCard(
                    title = title,
                    description = description,
                    isFocused = isFocused,
                )

                if (summaryFacts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.elementGap.div(1.5f))) {
                        OverviewSectionLabel(
                            text = "At a Glance",
                            highlighted = false,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                            verticalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            summaryFacts.forEach { item ->
                                MetaFactItem(
                                    icon = item.icon,
                                    label = item.label,
                                    value = item.value,
                                    style = MetaFactStyle.Inline,
                                )
                            }
                        }
                    }
                }

                if (detailFacts.isNotEmpty()) {
                    OverviewGroupedPanel(
                        title = "Details",
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                            verticalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            detailFacts.forEach { item ->
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
                    OverviewGroupedPanel(
                        title = "Categories",
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        DetailChipRow(labels = chips)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSectionLabel(
    text: String,
    highlighted: Boolean,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = if (highlighted) expressiveColors.focusRing else MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun OverviewNarrativeCard(
    title: String,
    description: String,
    isFocused: Boolean,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = expressiveColors.detailPanelFocused.copy(alpha = if (isFocused) 0.7f else 0.42f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .border(
                width = 1.dp,
                color = expressiveColors.borderSubtle.copy(alpha = 0.45f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.elementGap.div(1.5f)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (description.isNotBlank()) description else "No overview available.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewGroupedPanel(
    title: String,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = expressiveColors.detailPanelMuted.copy(alpha = 0.88f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .border(
                width = 1.dp,
                color = expressiveColors.borderSubtle.copy(alpha = 0.38f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.elementGap.div(1.5f)),
    ) {
        OverviewSectionLabel(
            text = title,
            highlighted = false,
        )
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
