package com.rpeters.cinefintv.ui.screens.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScreensaverOverlay(
    viewModel: ScreensaverViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.isIdle) return

    val focusRequester = remember { FocusRequester() }

    // Intercept D-pad keys to dismiss screensaver
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                viewModel.onUserActivity()
                true // Consume the keypress so it doesn't navigate screens behind it
            }
    ) {
        // 1. Ken Burns slideshow backdrops
        ScreensaverSlideshow(backdropUrls = uiState.backdropUrls)

        // 2. Clock and Date (Shifts corners every 2 minutes to prevent OLED burn-in)
        ScreensaverClock()

        // 3. Now Playing Card (displays active background music if present)
        uiState.nowPlaying?.let { track ->
            ScreensaverNowPlayingCard(track = track)
        }
    }

    // Request focus on start to capture key presses
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun ScreensaverSlideshow(
    backdropUrls: List<String>,
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(backdropUrls) {
        if (backdropUrls.isNotEmpty()) {
            while (true) {
                delay(12000L) // Wait 12 seconds per backdrop
                activeIndex = (activeIndex + 1) % backdropUrls.size
            }
        }
    }

    val currentUrl = if (backdropUrls.isNotEmpty()) backdropUrls[activeIndex % backdropUrls.size] else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Crossfade(
            targetState = currentUrl,
            animationSpec = tween(2500), // Slow crossfade
            label = "screensaverSlideshow"
        ) { url ->
            if (url != null) {
                // Ken Burns Zoom/Pan state
                var scale by remember { mutableFloatStateOf(1.0f) }
                var translationX by remember { mutableFloatStateOf(0f) }
                var translationY by remember { mutableFloatStateOf(0f) }

                val panDirection = remember(url) { (0..3).random() }

                LaunchedEffect(url) {
                    animate(
                        initialValue = 1.0f,
                        targetValue = 1.15f,
                        animationSpec = tween(durationMillis = 14500, easing = LinearEasing)
                    ) { valX, _ ->
                        scale = valX
                    }
                }

                LaunchedEffect(url) {
                    val targetX = when (panDirection) {
                        0 -> 40f
                        1 -> -40f
                        else -> 0f
                    }
                    val targetY = when (panDirection) {
                        2 -> 30f
                        3 -> -30f
                        else -> 0f
                    }
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 14500, easing = LinearEasing)
                    ) { progress, _ ->
                        translationX = targetX * progress
                        translationY = targetY * progress
                    }
                }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = translationX,
                            translationY = translationY
                        ),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Default atmospheric background when library contains no backdrops
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                            )
                        )
                )
            }
        }

        // Heavy dark vignette scrim to make clock/cards legible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
        )
    }
}

@Composable
private fun ScreensaverClock() {
    var clockAlignment by remember { mutableStateOf<Alignment>(Alignment.TopEnd) }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance().time
            currentTime = timeFormatter.format(now)
            currentDate = dateFormatter.format(now)
            delay(1000L)
        }
    }

    // Shift clock alignment every 2 minutes to prevent OLED screen burn-in
    LaunchedEffect(Unit) {
        val alignments = listOf(
            Alignment.TopEnd,
            Alignment.TopStart,
            Alignment.BottomEnd,
            Alignment.TopEnd // Shift back and forth in upper corners mainly
        )
        var idx = 0
        while (true) {
            delay(120000L)
            idx = (idx + 1) % alignments.size
            clockAlignment = alignments[idx]
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Box(
            modifier = Modifier
                .align(clockAlignment)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Box(
    track: NowPlayingTrack,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (track.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    track.artist?.let { artist ->
                        Text(
                            text = artist,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (track.isPlaying) "Playing Music" else "Music Paused",
                        color = if (track.isPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Rename layout wrapper to match parameter name
@Composable
private fun ScreensaverNowPlayingCard(
    track: NowPlayingTrack,
) {
    Box(track = track)
}
