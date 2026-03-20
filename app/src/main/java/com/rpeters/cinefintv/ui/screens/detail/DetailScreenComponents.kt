@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

/**
 * Full-width hero box with backdrop image and gradient overlays.
 * Content is placed via [content] slot; anchor to [Alignment.BottomStart] for standard detail layout.
 */
@Composable
fun DetailHeroBox(
    backdropUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                .size(1280, 720)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = 0.08f),
                            0.35f to background.copy(alpha = 0.42f),
                            1f to background,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = 0.96f),
                            0.28f to background.copy(alpha = 0.82f),
                            0.56f to background.copy(alpha = 0.3f),
                            1f to Color.Transparent,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            expressiveColors.chromeSurface.copy(alpha = 0.14f),
                            Color.Transparent,
                            expressiveColors.focusGlow.copy(alpha = 0.18f),
                        ),
                    )
                )
        )
        content()
    }
}

@Composable
fun DetailPosterArt(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(spacing.cornerContainer),
        colors = SurfaceDefaults.colors(containerColor = expressiveColors.accentSurface.copy(alpha = 0.92f)),
        tonalElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = expressiveColors.borderSubtle.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(spacing.cornerContainer),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .size(420, 630)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DetailGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(spacing.cornerContainer),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.chromeSurface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        tonalElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = expressiveColors.borderSubtle.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(spacing.cornerContainer),
                )
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun DetailChipRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val filtered = labels.filter { it.isNotBlank() }
    if (filtered.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filtered.forEachIndexed { index, label ->
            CinefinChip(
                label = label,
                strong = index == 0,
            )
        }
    }
}

@Composable
fun DetailProgressLabel(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    if (pct <= 0 || pct >= 100) return

    Text(
        text = "$pct% watched",
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun DetailActionRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryFocusRequester: FocusRequester? = null,
    primaryDownFocusRequester: FocusRequester? = null,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = if (primaryFocusRequester != null) {
                Modifier
                    .focusRequester(primaryFocusRequester)
                    .then(
                        if (primaryDownFocusRequester != null) {
                            Modifier.focusProperties { down = primaryDownFocusRequester }
                        } else {
                            Modifier
                        }
                    )
            } else {
                if (primaryDownFocusRequester != null) {
                    Modifier.focusProperties { down = primaryDownFocusRequester }
                } else {
                    Modifier
                }
            },
            scale = ButtonDefaults.scale(focusedScale = 1.06f),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                focusedContainerColor = Color.White,
                focusedContentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(primaryLabel)
        }
        if (!secondaryLabel.isNullOrBlank() && onSecondaryClick != null) {
            OutlinedButton(
                onClick = onSecondaryClick,
                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                colors = ButtonDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = expressiveColors.focusGlow.copy(alpha = 0.24f),
                    focusedContentColor = Color.White,
                ),
                border = ButtonDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = expressiveColors.borderSubtle.copy(alpha = 0.65f),
                        ),
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = expressiveColors.focusRing,
                        ),
                    ),
                ),
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

/**
 * Section with a heading label and arbitrary [content] below it.
 * Used for Cast, Similar, Seasons, Chapters rows.
 */
@Composable
fun DetailContentSection(
    title: String,
    eyebrow: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isHeaderFocused by remember(title) { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .onFocusChanged { state ->
                    isHeaderFocused = state.hasFocus || state.isFocused
                }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                eyebrow?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isHeaderFocused) Color.White else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .height(3.dp)
                        .width((this@BoxWithConstraints.maxWidth * 0.18f).coerceAtLeast(72.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    if (isHeaderFocused) expressiveColors.focusRing else expressiveColors.titleAccent,
                                    Color.Transparent,
                                )
                            ),
                            shape = RoundedCornerShape(999.dp),
                        )
                )
            }
        }
        content()
    }
}

/**
 * Shared error state for all detail screens.
 */
@Composable
fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** Centered spinner used during loading. */
@Composable
fun DetailLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
