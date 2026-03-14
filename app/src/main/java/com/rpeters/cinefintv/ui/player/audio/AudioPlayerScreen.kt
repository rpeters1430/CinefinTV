package com.rpeters.cinefintv.ui.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    
    val playPauseFocusRequester = remember { FocusRequester() }

    if (uiState.isConnecting || uiState.isLoadingQueue) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                text = if (uiState.isConnecting) "Connecting to playback..." else "Loading queue...",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Playback Error", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.error)
                Text(text = uiState.errorMessage!!, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::retry) { Text("Retry") }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        playPauseFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred Backdrop
        AsyncImage(
            model = uiState.currentTrackImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer(alpha = 0.45f),
            contentScale = ContentScale.Crop
        )
        
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.85f))
            )
        ))

        Row(modifier = Modifier.fillMaxSize().padding(spacing.gutter)) {
            // Main Player Area (Left)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Info & Back
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                // Focal Point: Album Art & Titles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(spacing.cornerCard),
                        modifier = Modifier.size(320.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color.DarkGray)
                    ) {
                        if (uiState.currentTrackImageUrl != null) {
                            AsyncImage(
                                model = uiState.currentTrackImageUrl,
                                contentDescription = uiState.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp), tint = Color.Gray)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.artistName ?: "Unknown Artist",
                            style = MaterialTheme.typography.headlineSmall,
                            color = expressiveColors.titleAccent
                        )
                        Text(
                            text = uiState.albumName ?: "Unknown Album",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        
                        if (uiState.year != null || uiState.genre != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                uiState.year?.let { 
                                    CinefinChip(label = it.toString())
                                }
                                uiState.genre?.let {
                                    CinefinChip(label = it)
                                }
                            }
                        }
                    }
                }

                // Floating Controller Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.cornerContainer))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(spacing.cornerContainer))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Seek Bar
                        AudioSeekBar(
                            position = uiState.positionMs,
                            duration = uiState.durationMs,
                            onSeek = { /* Future: add seeking */ }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                IconButton(
                                    onClick = viewModel::skipToPrevious,
                                    enabled = uiState.canSkipPrevious,
                                    colors = IconButtonDefaults.colors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
                                }

                                IconButton(
                                    onClick = viewModel::togglePlayPause,
                                    modifier = Modifier.size(80.dp).focusRequester(playPauseFocusRequester),
                                    scale = IconButtonDefaults.scale(focusedScale = 1.15f),
                                    colors = IconButtonDefaults.colors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                                        focusedContentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                IconButton(
                                    onClick = viewModel::skipToNext,
                                    enabled = uiState.canSkipNext,
                                    colors = IconButtonDefaults.colors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
                                }
                            }

                            Text(
                                text = "${formatMs(uiState.positionMs)} / ${formatMs(uiState.durationMs)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(spacing.gutter))

            // Next Up Sidebar (Right)
            Column(
                modifier = Modifier.width(320.dp).fillMaxHeight()
            ) {
                Text(
                    text = "Next Up",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Surface(
                    shape = RoundedCornerShape(spacing.cornerCard),
                    colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val queueListState = rememberLazyListState()
                    LazyColumn(
                        state = queueListState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(uiState.queueItems) { index, track ->
                            val isCurrent = index == uiState.currentIndex
                            QueueItem(
                                track = track,
                                isCurrent = isCurrent,
                                onClick = { viewModel.skipToItem(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueItem(
    track: AudioQueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
            focusedContentColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                track.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    
    Box(
        modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
