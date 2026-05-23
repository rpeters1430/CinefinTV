@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.components.CinefinChip

data class HeroSecondaryAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun DetailStripTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun FlatDetailHero(
    backdropUrl: String?,
    posterUrl: String?,
    title: String,
    metadataItems: List<String>,
    qualityBadges: List<String>,
    genres: List<String>,
    summary: String?,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActions: List<HeroSecondaryAction>,
    primaryActionFocusRequester: FocusRequester,
    primaryActionDownFocusRequester: FocusRequester? = null,
    onDownNavigation: (() -> Unit)? = null,
    drawerFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onFocusChanged { state ->
                if (state.hasFocus || state.isFocused) {
                    onFocus?.invoke()
                }
            },
    ) {
        // Scrims are now handled by ImmersiveBackground or local overlays if needed.
        // We'll keep a local horizontal scrim for text readability on this specific layout.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.72f),
                            0.35f to Color.Black.copy(alpha = 0.32f),
                            0.65f to Color.Transparent,
                        )
                    )
                ),
        )


        posterUrl?.let {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(it)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 24.dp)
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .background(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 136.dp, end = 24.dp, bottom = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        blurRadius = 16f,
                    ),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(DetailTestTags.HeroTitle),
            )

            HeroMetadataLine(
                metadataItems = metadataItems,
                qualityBadges = qualityBadges,
                genres = genres,
            )

            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HeroActionStrip(
                primaryLabel = primaryActionLabel,
                onPrimaryClick = onPrimaryAction,
                secondaryActions = secondaryActions,
                primaryFocusRequester = primaryActionFocusRequester,
                primaryDownFocusRequester = primaryActionDownFocusRequester,
                onDownNavigation = onDownNavigation,
                drawerFocusRequester = drawerFocusRequester,
            )
        }
    }
}

@Composable
private fun HeroMetadataLine(
    metadataItems: List<String>,
    qualityBadges: List<String>,
    genres: List<String>,
) {
    val filteredMetadata = metadataItems.filter { it.isNotBlank() }
    val filteredBadges = qualityBadges.filter { it.isNotBlank() }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filteredMetadata.forEach { value ->
            CinefinChip(label = value)
        }
        filteredBadges.forEach { badge ->
            HeroQualityBadge(label = badge)
        }
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" · "),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroQualityBadge(label: String) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val normalized = label.trim().uppercase()
    val tint = when {
        normalized.contains("DV") || normalized.contains("DOLBY VISION") -> expressiveColors.badgeDolbyVision
        normalized.contains("HDR") -> expressiveColors.badgeHDR
        normalized.contains("4K") -> expressiveColors.badge4K
        else -> expressiveColors.badgeGeneric
    }
    Text(
        text = normalized,
        color = tint,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        modifier = Modifier
            .background(
                color = Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.4f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun HeroActionStrip(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryActions: List<HeroSecondaryAction>,
    primaryFocusRequester: FocusRequester,
    primaryDownFocusRequester: FocusRequester?,
    onDownNavigation: (() -> Unit)?,
    drawerFocusRequester: FocusRequester? = null,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val secondaryFocusRequesters = remember(secondaryActions.size) {
        List(secondaryActions.size) { FocusRequester() }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .testTag(DetailTestTags.PrimaryAction)
                .focusRequester(primaryFocusRequester)
                .blockBringIntoView()
                .focusProperties {
                    if (primaryDownFocusRequester != null && onDownNavigation == null) {
                        down = primaryDownFocusRequester
                    }
                    if (secondaryActions.isNotEmpty()) {
                        right = secondaryFocusRequesters.first()
                    }
                    drawerFocusRequester?.let {
                        left = it
                        up = it
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    if (
                        onDownNavigation != null &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onDownNavigation()
                        true
                    } else {
                        false
                    }
                }
                .defaultMinSize(minHeight = 44.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            scale = ButtonDefaults.scale(focusedScale = 1.03f),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                ),
            ),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }

        secondaryActions.forEachIndexed { index, action ->
            val isMoreAction = action.label == "···"
            OutlinedButton(
                onClick = action.onClick,
                modifier = Modifier
                    .then(
                        if (isMoreAction) {
                            Modifier
                                .requiredWidth(52.dp)
                                .height(44.dp)
                        } else {
                            Modifier.defaultMinSize(minHeight = 44.dp)
                        },
                    )
                    .focusRequester(secondaryFocusRequesters[index])
                    .blockBringIntoView()
                    .focusProperties {
                        left = if (index == 0) primaryFocusRequester else secondaryFocusRequesters[index - 1]
                        if (index < secondaryFocusRequesters.lastIndex) {
                            right = secondaryFocusRequesters[index + 1]
                        }
                        if (primaryDownFocusRequester != null && onDownNavigation == null) {
                            down = primaryDownFocusRequester
                        }
                        drawerFocusRequester?.let {
                            up = it
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val nativeEvent = keyEvent.nativeKeyEvent
                        if (
                            onDownNavigation != null &&
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            onDownNavigation()
                            true
                        } else {
                            false
                        }
                    },
                contentPadding = if (isMoreAction) {
                    PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                },
                scale = ButtonDefaults.scale(focusedScale = 1.03f),
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                    focusedContentColor = Color.White,
                ),
                border = ButtonDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                        ),
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, expressiveColors.focusRing),
                    ),
                ),
            ) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = if (isMoreAction) 18.sp else 14.sp,
                        fontWeight = if (isMoreAction) FontWeight.Black else FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
