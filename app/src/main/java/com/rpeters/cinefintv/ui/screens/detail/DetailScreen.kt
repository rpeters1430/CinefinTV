package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.components.WatchStatus

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenPerson: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var focusedDescription by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "DetailContent"
    ) { state ->
        when (state) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading details...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            is DetailUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Detail could not load",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
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

            is DetailUiState.Content -> {
                val item = state.item
                val episodes = state.episodesBySeasonId.values.flatten()
                val isSeriesDetail = state.seasons.isNotEmpty()
                var subtitlesExpanded by remember { mutableStateOf(false) }
                var selectedSubtitle by remember(item.subtitleOptions) {
                    mutableStateOf(item.subtitleOptions.firstOrNull())
                }

                LaunchedEffect(state.isDeleted) {
                    if (state.isDeleted) {
                        onBack()
                    }
                }

                BackHandler(onBack = onBack)

                val listState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.backdropUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(item.backdropUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.45f to Color.Black.copy(alpha = 0.55f),
                                        0.75f to Color.Black.copy(alpha = 0.88f),
                                        1.0f to Color.Black,
                                    ),
                                ),
                            ),
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
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

                        item { Spacer(Modifier.fillParentMaxHeight(0.35f)) }

                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                )

                                if (!item.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                val displayOverview = focusedDescription ?: item.overview
                                if (!displayOverview.isNullOrBlank()) {
                                    Text(
                                        text = displayOverview,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    )
                                }

                                if (item.metaBadges.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        item.metaBadges.forEach { badge ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (item.infoRows.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        item.infoRows.forEach { infoRow ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = Color.Black.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.border.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (infoRow.icon != null) {
                                                        Icon(
                                                            imageVector = infoRow.icon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            text = infoRow.label.uppercase(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = infoRow.value,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.onBackground,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item.technicalDetails?.let { details ->
                                    val technicalRows = listOfNotNull(
                                        details.videoQuality?.let {
                                            DetailInfoRowModel("Video Quality", it, Icons.Default.HighQuality)
                                        },
                                        details.audioCodec?.let {
                                            DetailInfoRowModel("Audio Codec", it, Icons.Default.GraphicEq)
                                        },
                                        details.audioType?.let {
                                            DetailInfoRowModel("Audio Type", it, Icons.Default.Speaker)
                                        },
                                        details.language?.let {
                                            DetailInfoRowModel("Language", it, Icons.Default.Language)
                                        },
                                    )

                                    if (technicalRows.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            technicalRows.forEach { infoRow ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = SurfaceDefaults.colors(
                                                        containerColor = Color.Black.copy(alpha = 0.4f)
                                                    ),
                                                    modifier = Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.border.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        infoRow.icon?.let {
                                                            Icon(
                                                                imageVector = it,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(20.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text(
                                                                text = infoRow.label.uppercase(),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = infoRow.value,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                color = MaterialTheme.colorScheme.onBackground,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (item.subtitleOptions.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Subtitles",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Button(
                                            onClick = { subtitlesExpanded = !subtitlesExpanded },
                                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                        ) {
                                            Icon(Icons.Default.ClosedCaption, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(selectedSubtitle ?: "Choose subtitle")
                                        }

                                        if (subtitlesExpanded) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = SurfaceDefaults.colors(
                                                    containerColor = Color.Black.copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier.fillMaxWidth(0.45f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    item.subtitleOptions.forEach { option ->
                                                        val isSelected = selectedSubtitle == option
                                                        if (isSelected) {
                                                            Button(onClick = {
                                                                selectedSubtitle = option
                                                                subtitlesExpanded = false
                                                            }) {
                                                                Text(option)
                                                            }
                                                        } else {
                                                            OutlinedButton(onClick = {
                                                                selectedSubtitle = option
                                                                subtitlesExpanded = false
                                                            }) {
                                                                Text(option)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!state.actionErrorMessage.isNullOrBlank()) {
                                    Text(
                                        text = state.actionErrorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    state.playableItemId?.let { playableItemId ->
                                        Button(
                                            onClick = { onPlay(playableItemId) },
                                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                        ) {
                                            Text(state.playButtonLabel)
                                        }
                                    }

                                    if (!isSeriesDetail) {
                                        if (state.isDeleting) {
                                            Surface(shape = RoundedCornerShape(12.dp)) {
                                                Text(
                                                    text = "Deleting...",
                                                    modifier = Modifier.padding(
                                                        horizontal = 18.dp,
                                                        vertical = 12.dp,
                                                    ),
                                                )
                                            }
                                        } else if (state.isDeleteConfirmationVisible) {
                                            Button(
                                                onClick = viewModel::confirmDelete,
                                                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                            ) {
                                                Text("Confirm Delete")
                                            }
                                            OutlinedButton(
                                                onClick = viewModel::cancelDelete,
                                                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                            ) {
                                                Text("Cancel")
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = viewModel::requestDelete,
                                                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                            ) {
                                                Text("Delete")
                                            }
                                        }
                                    }

                                    if (!isSeriesDetail) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.dismissActionError()
                                                onBack()
                                            },
                                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedDescription = null }
                                        ) {
                                            Text("Back")
                                        }
                                    }
                                }
                            }
                        }

                        if (state.seasons.isNotEmpty()) {
                            item {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier.onFocusChanged { isFocused = it.hasFocus },
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Seasons",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    ) {
                                        items(
                                            state.seasons,
                                            key = { it.id },
                                            contentType = { "MediaCard" }
                                        ) { season ->
                                            TvMediaCard(
                                                title = season.title,
                                                subtitle = season.subtitle,
                                                imageUrl = season.imageUrl,
                                                onClick = { onOpenItem(season.id) },
                                                onFocus = { focusedDescription = season.overview },
                                                watchStatus = season.watchStatus,
                                                playbackProgress = season.playbackProgress,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.seasons.isEmpty() && episodes.isNotEmpty()) {
                            item {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier.onFocusChanged { isFocused = it.hasFocus },
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Episodes",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    ) {
                                        items(
                                            episodes,
                                            key = { it.id },
                                            contentType = { "MediaCard" }
                                        ) { episode ->
                                            TvMediaCard(
                                                title = episode.title,
                                                subtitle = episode.subtitle,
                                                imageUrl = episode.imageUrl,
                                                onClick = { onOpenItem(episode.id) },
                                                onFocus = { focusedDescription = episode.overview },
                                                watchStatus = episode.watchStatus,
                                                playbackProgress = episode.playbackProgress,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.cast.isNotEmpty()) {
                            item {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier.onFocusChanged { isFocused = it.hasFocus },
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Cast & Crew",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    ) {
                                        items(
                                            state.cast,
                                            key = { it.id + it.role },
                                            contentType = { "PersonCard" }
                                        ) { person ->
                                            TvPersonCard(
                                                name = person.name,
                                                role = person.role,
                                                imageUrl = person.imageUrl,
                                                onClick = { onOpenPerson(person.id) },
                                                onFocus = { focusedDescription = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.related.isNotEmpty()) {
                            item {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier.onFocusChanged { isFocused = it.hasFocus },
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "More Like This",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    ) {
                                        items(
                                            state.related,
                                            key = { it.id },
                                            contentType = { "MediaCard" }
                                        ) { related ->
                                            TvMediaCard(
                                                title = related.title,
                                                subtitle = related.subtitle,
                                                imageUrl = related.imageUrl,
                                                onClick = { onOpenItem(related.id) },
                                                onFocus = { focusedDescription = related.overview },
                                                watchStatus = related.watchStatus,
                                                playbackProgress = related.playbackProgress,
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
}
