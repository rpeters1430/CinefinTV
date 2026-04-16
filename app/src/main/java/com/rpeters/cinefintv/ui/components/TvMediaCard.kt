package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
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
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.theme.CinefinMotion
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinMotion
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
    onMenuAction: (() -> Unit)? = null,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
    watchStatus: WatchStatus = WatchStatus.NONE,
    playbackProgress: Float? = null,
    unwatchedCount: Int? = null,
    aspectRatio: Float = 2f / 3f,
    cardWidth: androidx.compose.ui.unit.Dp? = null,
    compactMetadata: Boolean = false,
) {
    val context = LocalContext.current
    val performanceProfile = LocalPerformanceProfile.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    val motion = LocalCinefinMotion.current
    var isFocused by remember { mutableStateOf(false) }
    var menuHandledForCurrentPress by remember { mutableStateOf(false) }

    val focusedScaleValue = remember(aspectRatio, cardWidth, compactMetadata) {
        if (compactMetadata) 1.0f
        else if (aspectRatio > 1f || (cardWidth != null && cardWidth > 200.dp)) 1.035f
        else 1.06f
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScaleValue else 1f,
        animationSpec = tween(
            durationMillis = CinefinMotion.DurationMedium,
            easing = CinefinMotion.PremiumOvershoot
        ),
        label = "MediaCardScale"
    )

    val titleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(
            durationMillis = CinefinMotion.DurationShort,
            easing = CinefinMotion.Standard
        ),
        label = "MediaCardTitleColor",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(
            durationMillis = CinefinMotion.DurationShort,
            easing = CinefinMotion.Standard
        ),
        label = "MediaCardSubtitleColor",
    )

    val imageRequest = remember(imageUrl, performanceProfile.tier, aspectRatio, context) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                .size(
                    if (aspectRatio > 1f) 640 else 336,
                    if (aspectRatio > 1f) 360 else 504,
                )
                .build()
        }
    }
    val metadataTextAlign = TextAlign.Start

    StandardCardContainer(
        modifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth(),
        imageCard = {
            Card(
                onClick = onClick,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val nativeEvent = keyEvent.nativeKeyEvent
                        when {
                            onMenuAction == null -> false
                            nativeEvent.action == android.view.KeyEvent.ACTION_UP -> {
                                menuHandledForCurrentPress = false
                                false
                            }
                            shouldOpenCardMenu(nativeEvent) && !menuHandledForCurrentPress -> {
                                menuHandledForCurrentPress = true
                                onMenuAction()
                                true
                            }
                            shouldOpenCardMenu(nativeEvent) -> true
                            else -> false
                        }
                    }
                    .onFocusChanged {
                        val focused = it.isFocused || it.hasFocus
                        if (focused != isFocused) {
                            isFocused = focused
                            if (focused) onFocus()
                        }
                    },
                scale = CardDefaults.scale(focusedScale = 1f), // Handled by graphicsLayer
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = expressiveColors.focusRing.copy(alpha = 0.82f),
                        ),
                    ),
                ),
                glow = CardDefaults.glow(
                    focusedGlow = androidx.tv.material3.Glow(
                        elevationColor = expressiveColors.focusGlow.copy(alpha = 0.22f),
                        elevation = 8.dp
                    )
                ),
                shape = CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(expressiveColors.accentSurface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
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

                    // Bottom-heavy scrim for card title/badge legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.6f to Color.Black.copy(alpha = 0.08f),
                                        0.85f to Color.Black.copy(alpha = 0.28f),
                                        1.0f to Color.Black.copy(alpha = 0.55f),
                                    ),
                                ),
                            ),
                    )

                    if (isFocused && !compactMetadata) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(expressiveColors.focusGlow.copy(alpha = 0.05f))
                        )
                    }

                    if (watchStatus == WatchStatus.IN_PROGRESS && playbackProgress != null && playbackProgress > 0f) {
                        val progressTrackShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(7.dp)
                                .background(
                                    color = expressiveColors.playerSurface.copy(alpha = 0.8f),
                                    shape = progressTrackShape,
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(playbackProgress.coerceIn(0f, 1f))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                                expressiveColors.playerContentPrimary,
                                            ),
                                        ),
                                        shape = progressTrackShape,
                                    )
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
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (compactMetadata) 16.sp else 18.sp
                ),
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
                maxLines = if (subtitle.isNullOrBlank()) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = metadataTextAlign,
                modifier = Modifier.fillMaxWidth().padding(top = if (compactMetadata) 4.dp else 6.dp)
            )
        },
        subtitle = {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = if (compactMetadata) 14.sp else 16.sp
                    ),
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = metadataTextAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private fun shouldOpenCardMenu(event: android.view.KeyEvent): Boolean {
    return event.keyCode == android.view.KeyEvent.KEYCODE_MENU || 
           event.keyCode == android.view.KeyEvent.KEYCODE_INFO ||
           (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.isLongPress) ||
           (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress)
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
                        color = Color.White,
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
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
