package com.rpeters.cinefintv.ui.screens.person

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import kotlinx.coroutines.launch
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonScreen(
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var focusedDescription by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    when (val state = uiState) {
        is PersonUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading person details...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            }
        }

        is PersonUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Could not load person",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::load) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }

        is PersonUiState.Content -> {
            val person = state.person
            
            BackHandler(onBack = onBack)

            val listState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
                // Background Image (if available)
                if (person.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(person.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.3f to Color.Black.copy(alpha = 0.4f),
                                    0.6f to Color.Black.copy(alpha = 0.8f),
                                    1.0f to Color.Black,
                                ),
                            ),
                        ),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Invisible focusable item to allow scrolling back to the very top
                    item {
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                }
                                .focusable()
                        )
                    }

                    item { Spacer(Modifier.fillParentMaxHeight(0.4f)) }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )

                            val displayOverview = focusedDescription ?: person.overview
                            if (!displayOverview.isNullOrBlank()) {
                                Text(
                                    text = displayOverview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                            }

                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                ) {
                                    Text("Back")
                                }
                            }
                        }
                    }

                    if (state.media.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Known For",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(
                                        state.media,
                                        key = { it.id },
                                        contentType = { "MediaCard" }
                                    ) { item ->
                                        TvMediaCard(
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            imageUrl = item.imageUrl,
                                            onClick = { onOpenItem(item.id) },
                                            onFocus = { focusedDescription = item.overview },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
