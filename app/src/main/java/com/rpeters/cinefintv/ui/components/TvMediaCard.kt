package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

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
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "CardScale"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "TitleColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "CardElevation"
    )

    Column(
        modifier = modifier.width(260.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.toPx()
                }
        ) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (it.isFocused || it.hasFocus) onFocus()
                    },
                scale = CardDefaults.scale(focusedScale = 1.0f),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.border.copy(alpha = 0.8f)
                        )
                    )
                ),
                shape = CardDefaults.shape(MaterialTheme.shapes.extraSmall)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = title.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    if (watchStatus == WatchStatus.IN_PROGRESS && playbackProgress != null && playbackProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp)
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

                    if (watchStatus == WatchStatus.WATCHED) {
                        WatchStatusOverlay(
                            status = watchStatus,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(1f)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchStatusOverlay(status: WatchStatus, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(6.dp)) {
        when (status) {
            WatchStatus.WATCHED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            WatchStatus.IN_PROGRESS -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            WatchStatus.NONE -> {}
        }
    }
}
