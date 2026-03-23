package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import com.rpeters.cinefintv.utils.coerceAlpha

enum class WatchStatus { NONE, WATCHED, IN_PROGRESS }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
    watchStatus: WatchStatus = WatchStatus.NONE,
    playbackProgress: Float? = null,
    unwatchedCount: Int? = null,
    aspectRatio: Float = 2f / 3f,
    cardWidth: androidx.compose.ui.unit.Dp? = null,
    compactMetadata: Boolean = false,
) {
    val performanceProfile = LocalPerformanceProfile.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    var isFocused by remember { mutableStateOf(false) }

    val titleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "MediaCardTitleColor",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "MediaCardSubtitleColor",
    )

    val focusedScale = remember(aspectRatio, cardWidth, compactMetadata) {
        if (compactMetadata) 1.0f 
        else if (aspectRatio > 1f || (cardWidth != null && cardWidth > 200.dp)) 1.05f 
        else 1.1f
    }

    StandardCardContainer(
        modifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth(),
        imageCard = {
            Card(
                onClick = onClick,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .onFocusChanged {
                        val focused = it.isFocused || it.hasFocus
                        if (focused != isFocused) {
                            isFocused = focused
                            if (focused) onFocus()
                        }
                    },
                scale = CardDefaults.scale(focusedScale = focusedScale),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 3.dp,
                            color = Color.White,
                        ),
                    ),
                ),
                shape = CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(expressiveColors.accentSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                                    .size(if (aspectRatio > 1f) 640 else 336, if (aspectRatio > 1f) 360 else 504)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = title.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Black.copy(alpha = 0.28f),
                                    ),
                                ),
                            ),
                    )

                    // White brightness overlay when focused — makes card visually "pop"
                    if (isFocused && !compactMetadata) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(expressiveColors.focusGlow.copy(alpha = 0.12f))
                        )
                    }

                    if (watchStatus == WatchStatus.IN_PROGRESS && playbackProgress != null && playbackProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(playbackProgress.coerceIn(0f, 1f))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    if (unwatchedCount != null && unwatchedCount > 0) {
                        UnwatchedCountOverlay(
                            count = unwatchedCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(1f)
                        )
                    } else if (watchStatus == WatchStatus.WATCHED) {
                        WatchStatusOverlay(
                            status = watchStatus,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(1f)
                        )
                    }
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp),
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                maxLines = if (subtitle.isNullOrBlank()) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        },
        subtitle = {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.sp),
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchStatusOverlay(status: WatchStatus, modifier: Modifier = Modifier) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    Box(modifier = modifier.padding(spacing.chipGap.coerceAtLeast(0.dp).div(1.5f))) {
        when (status) {
            WatchStatus.WATCHED -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = expressiveColors.watchedGreen.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            WatchStatus.IN_PROGRESS -> {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            WatchStatus.NONE -> Unit
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UnwatchedCountOverlay(count: Int, modifier: Modifier = Modifier) {
    val spacing = LocalCinefinSpacing.current
    Box(modifier = modifier.padding(spacing.chipGap.coerceAtLeast(0.dp).div(1.5f))) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = expressiveBadgeColor(),
                    shape = RoundedCornerShape(999.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "99+" else "$count",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (count > 9) 10.sp else 12.sp
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun expressiveBadgeColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
