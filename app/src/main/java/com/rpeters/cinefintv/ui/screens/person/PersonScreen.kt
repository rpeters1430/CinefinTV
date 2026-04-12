package com.rpeters.cinefintv.ui.screens.person

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonScreen(
    personId: String,
    onOpenItem: (String, String?) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    LaunchedEffect(personId) {
        viewModel.init(personId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalCinefinSpacing.current
    var focusedDescription by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val expressiveColors = LocalCinefinExpressiveColors.current

    val primaryContentRequester = remember { FocusRequester() }
    val destinationFocus = rememberTopLevelDestinationFocus(primaryContentRequester)

    when (val state = uiState) {
        is PersonUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading person details...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is PersonUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Could not load person",
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

        is PersonUiState.Content -> {
            val person = state.person
            var focusedMedia by remember(state.media) { mutableStateOf(state.media.firstOrNull()) }
            val backButtonRequester = remember { FocusRequester() }
            val firstKnownForRequester = remember { FocusRequester() }

            BackHandler(onBack = onBack)

            val listState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
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
                }

                val primaryContentMod = destinationFocus.primaryContentModifier()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .then(primaryContentMod),
                    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.safeZoneVertical),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { Spacer(Modifier.fillParentMaxHeight(0.24f)) }

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = SurfaceDefaults.colors(
                                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.74f),
                            ),
                            tonalElevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                )

                                val displayOverview = focusedDescription ?: person.overview
                                if (!displayOverview.isNullOrBlank()) {
                                    Text(
                                        text = displayOverview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onBack,
                                        modifier = Modifier
                                            .focusRequester(backButtonRequester)
                                            .onFocusChanged { if (it.isFocused) focusedDescription = null }
                                            .focusProperties {
                                                if (state.media.isNotEmpty()) down = firstKnownForRequester
                                            }
                                    ) {
                                        Text("Back")
                                    }
                                }
                            }
                        }
                    }

                    if (state.media.isNotEmpty()) {
                        item {
                            PersonImmersiveSection(
                                personName = person.name,
                                items = state.media,
                                focusedItem = focusedMedia ?: state.media.first(),
                                onOpenItem = onOpenItem,
                                firstItemRequester = firstKnownForRequester,
                                upRequester = backButtonRequester,
                                onItemFocused = {
                                    focusedMedia = it
                                    focusedDescription = it.overview
                                },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonImmersiveSection(
    personName: String,
    items: List<PersonMediaModel>,
    focusedItem: PersonMediaModel,
    onOpenItem: (String, String?) -> Unit,
    firstItemRequester: FocusRequester,
    upRequester: FocusRequester,
    onItemFocused: (PersonMediaModel) -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val sectionHeight by animateDpAsState(
        targetValue = 420.dp,
        animationSpec = tween(durationMillis = 220),
        label = "PersonImmersiveHeight",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(focusedItem.imageUrl)
                .crossfade(true)
                .size(1280, 720)
                .build(),
            contentDescription = focusedItem.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight)
                .background(
                    Brush.horizontalGradient(
                        colorStops = listOf(
                            0f to expressiveColors.heroStart.copy(alpha = 0.96f),
                            0.32f to Color.Black.copy(alpha = 0.8f),
                            0.7f to expressiveColors.heroEnd.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                        ).toTypedArray(),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = listOf(
                            0f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.2f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ).toTypedArray(),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CinefinShelfTitle(
                title = "Known For",
                eyebrow = personName,
            )
            Column(
                modifier = Modifier.fillMaxWidth(0.52f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = focusedItem.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                focusedItem.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = expressiveColors.titleAccent,
                    )
                }
                focusedItem.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    items,
                    key = { it.id },
                    contentType = { "MediaCard" }
                ) { item ->
                    TvMediaCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        imageUrl = item.imageUrl,
                        onClick = { onOpenItem(item.id, item.itemType) },
                        watchStatus = item.watchStatus,
                        playbackProgress = item.playbackProgress,
                        unwatchedCount = item.unwatchedCount,
                        onFocus = { onItemFocused(item) },
                        aspectRatio = 16f / 9f,
                        cardWidth = 240.dp,
                        modifier = Modifier
                            .then(if (item == items.first()) Modifier.focusRequester(firstItemRequester) else Modifier),
                    )
                }
            }
        }
    }
}
