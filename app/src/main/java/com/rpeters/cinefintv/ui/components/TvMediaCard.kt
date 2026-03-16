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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
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
    val metaContainerColor by animateColorAsState(
        targetValue = if (isFocused) {
            expressiveColors.elevatedSurface.copy(alpha = 0.98f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "MediaCardMetaSurface",
    )

    Column(
        modifier = if (cardWidth != null) modifier.width(cardWidth) else modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onFocusChanged {
                    val focused = it.isFocused || it.hasFocus
                    isFocused = focused
                    if (focused) onFocus()
                },
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 3.dp,
                        color = expressiveColors.focusRing,
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

        val isHorizontal = aspectRatio > 1f
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = SurfaceDefaults.colors(
                containerColor = if (isHorizontal) Color.Transparent else metaContainerColor
            ),
            tonalElevation = if (isFocused && !isHorizontal) 4.dp else 0.dp,
        ) {
            val hasSubtitle = !subtitle.isNullOrBlank()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = if (hasSubtitle) 8.dp else 6.dp)
                    .defaultMinSize(minHeight = if (hasSubtitle) 54.dp else 44.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = if (isHorizontal) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    color = titleColor,
                    maxLines = if (hasSubtitle) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isHorizontal) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasSubtitle) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (isHorizontal) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchStatusOverlay(status: WatchStatus, modifier: Modifier = Modifier) {
    val spacing = LocalCinefinSpacing.current
    Box(modifier = modifier.padding(spacing.chipGap.coerceAtLeast(0.dp).div(1.5f))) {
        when (status) {
            WatchStatus.WATCHED -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = Color(0xFF2E7D32).copy(alpha = 0.95f),
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
