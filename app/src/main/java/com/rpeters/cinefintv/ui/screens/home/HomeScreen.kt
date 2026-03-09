package com.rpeters.cinefintv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (String) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var backgroundImageUrl by remember { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Loading home...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        is HomeUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Home could not load",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::refresh) {
                    Text("Retry")
                }
            }
        }

        is HomeUiState.Content -> {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = backgroundImageUrl,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(600, easing = FastOutSlowInEasing)) + 
                         scaleIn(initialScale = 1.05f, animationSpec = tween(600, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(600, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 1.05f, animationSpec = tween(600, easing = FastOutSlowInEasing))
                            )
                    },
                    label = "BackgroundContent"
                ) { url ->
                    if (url != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Black.copy(alpha = 0.8f),
                                                Color.Black
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    if (state.featuredItems.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp)
                                    .padding(top = 16.dp)
                                    .onFocusChanged {
                                        if (it.hasFocus) {
                                            backgroundImageUrl = null
                                        }
                                    },
                            )
                        }
                    }

                    items(
                        state.sections,
                        key = { it.title },
                        contentType = { "Section" }
                    ) { section ->
                        var isSectionFocused by remember { mutableStateOf(false) }
                        
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 48.dp)
                                .onFocusChanged { isSectionFocused = it.hasFocus },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isSectionFocused) Color.White else Color.White.copy(alpha = 0.5f),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(
                                    section.items,
                                    key = { it.id },
                                    contentType = { "MediaCard" }
                                ) { item ->
                                    TvMediaCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        imageUrl = item.imageUrl,
                                        onClick = { onOpenItem(item.id) },
                                        onFocus = { backgroundImageUrl = item.backdropUrl ?: item.imageUrl }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    items: List<HomeCardModel>,
    onMoreInfo: (String) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Carousel(
        itemCount = items.size,
        modifier = modifier
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp)),
        autoScrollDurationMillis = 6000L,
    ) { index ->
        val item = items[index]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdropUrl ?: item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.85f),
                                0.55f to Color.Black.copy(alpha = 0.4f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.5f)
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val carouselMeta = listOfNotNull(
                    item.year?.toString(),
                    item.runtime,
                    item.officialRating,
                    item.rating?.let { "★ $it" },
                ).joinToString("  ·  ")
                if (carouselMeta.isNotBlank()) {
                    Text(
                        text = carouselMeta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onPlay(item.id) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = { onMoreInfo(item.id) }) {
                        Text("More Info")
                    }
                }
            }
        }
    }
}
