package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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

    val focusedScaleValue = remember(aspectRatio, cardWidth, compactMetadata, performanceProfile.tier) {
        if (compactMetadata) 1.0f
        else if (performanceProfile.tier == DevicePerformanceProfile.Tier.LOW) 1.03f
        else if (aspectRatio > 1f || (cardWidth != null && cardWidth > 200.dp)) 1.035f
        else 1.06f
    }

    // Card-to-card D-pad scrolling relies on the LazyRow's own focus-follow scroll animation.
    // Running a long overshoot scale animation on the newly focused card at the same time
    // makes that scroll read as choppy, since two independently-timed motions (translate +
    // scale) are visible at once. Only HIGH-tier devices — which have the frame budget to
    // spare — get the slower "premium" overshoot; everything else settles quickly with a
    // plain ease so the scroll itself reads as the dominant motion.
    val isHighTier = performanceProfile.tier == DevicePerformanceProfile.Tier.HIGH
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScaleValue else 1f,
        animationSpec = if (isHighTier) {
            tween(durationMillis = CinefinMotion.DurationMedium, easing = CinefinMotion.PremiumOvershoot)
        } else {
            tween(durationMillis = CinefinMotion.DurationShort, easing = CinefinMotion.Standard)
        },
        label = "MediaCardScale"
    )

    val useAnimations = isHighTier

    val titleColorValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    val titleColor by if (useAnimations) {
        animateColorAsState(
            targetValue = titleColorValue,
            animationSpec = tween(
                durationMillis = CinefinMotion.DurationShort,
                easing = CinefinMotion.Standard
            ),
            label = "MediaCardTitleColor",
        )
    } else {
        remember(titleColorValue) { mutableStateOf(titleColorValue) }
    }

    val subtitleColorValue = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val subtitleColor by if (useAnimations) {
        animateColorAsState(
            targetValue = subtitleColorValue,
            animationSpec = tween(
                durationMillis = CinefinMotion.DurationShort,
                easing = CinefinMotion.Standard
            ),
            label = "MediaCardSubtitleColor",
        )
    } else {
        remember(subtitleColorValue) { mutableStateOf(subtitleColorValue) }
    }

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
        modifier = (if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth())
            .zIndex(if (isFocused) 8f else 1f),
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
                            color = expressiveColors.focusRing.copy(alpha = 0.88f),
                        ),
                    ),
                ),
                glow = CardDefaults.glow(
                    focusedGlow = if (performanceProfile.tier != DevicePerformanceProfile.Tier.LOW) {
                        androidx.tv.material3.Glow(
                            elevationColor = expressiveColors.focusGlow.copy(alpha = 0.28f),
                            elevation = 10.dp
                        )
                    } else {
                        androidx.tv.material3.Glow.None
                    }
                ),
                shape = CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(spacing.cornerCard))
                ) {
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
                                colorStops = if (performanceProfile.tier != DevicePerformanceProfile.Tier.LOW) {
                                    arrayOf(
                                        0.0f to Color.Transparent,
                                        0.55f to Color.Black.copy(alpha = 0.10f),
                                        0.80f to Color.Black.copy(alpha = 0.35f),
                                        1.0f to Color.Black.copy(alpha = 0.65f),
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to Color.Transparent,
                                        1.0f to Color.Black.copy(alpha = 0.5f),
                                    )
                                }
                            ),
                        ),
                    )

                    if (isFocused && !compactMetadata) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(expressiveColors.focusGlow.copy(alpha = 0.06f))
                        )
                    }

                    if (watchStatus == WatchStatus.IN_PROGRESS && playbackProgress != null && playbackProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(playbackProgress.coerceIn(0f, 1f))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            ),
                                        ),
                                        shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
                                    )
                            )
                        }
                    }

                    if (unwatchedCount != null && unwatchedCount > 0) {
                        UnwatchedCountOverlay(
                            count = unwatchedCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(2f)
                        )
                    } else if (watchStatus == WatchStatus.WATCHED) {
                        WatchStatusOverlay(
                            status = watchStatus,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(2f)
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
                        fontSize = 16.sp
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchStatusOverlay(status: WatchStatus, modifier: Modifier = Modifier) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val padding = remember(spacing.chipGap) {
        (spacing.chipGap.value / 1.5f).coerceAtLeast(0f).dp
    }
    Box(modifier = modifier.padding(padding)) {
        when (status) {
            WatchStatus.WATCHED -> {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            color = Color(0xDD0D1117),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .border(
                            width = 1.5.dp,
                            color = expressiveColors.watchedGreen.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = expressiveColors.watchedGreen,
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
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
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
                .height(28.dp)
                .widthIn(min = 28.dp)
                .background(
                    color = Color(0xEE161B22),
                    shape = RoundedCornerShape(999.dp),
                )
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "99+" else "$count",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
