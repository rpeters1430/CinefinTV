# Detail & Library UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign library screens to 3-column 16:9 card grids, and redesign all detail screens to a cinematic hero + LazyColumn content row layout with proper D-pad focus management.

**Architecture:** Each library screen gets `GridCells.Fixed(3)` with `aspectRatio = 16f/9f` cards. Detail screens replace `verticalScroll(Column)` + nested `LazyRow` with a `LazyColumn` whose first item is a `DetailHeroBox` (backdrop + gradient overlays + bottom-left info), followed by content section items. Shared composables (`DetailHeroBox`, `DetailContentSection`, `DetailErrorState`) live in a new `DetailScreenComponents.kt` file. No ViewModel, model, or navigation changes.

**Tech Stack:** Kotlin, Jetpack Compose, androidx.tv.material3, Coil3, Hilt. Build: `./gradlew :app:assembleDebug`. Tests: `./gradlew :app:testDebugUnitTest`.

---

## File Map

| Action | File |
|--------|------|
| Modify | `ui/screens/library/MovieLibraryScreen.kt` |
| Modify | `ui/screens/library/TvShowLibraryScreen.kt` |
| Modify | `ui/screens/library/StuffLibraryScreen.kt` |
| Create | `ui/screens/detail/DetailScreenComponents.kt` |
| Modify | `ui/screens/detail/MovieDetailScreen.kt` |
| Modify | `ui/screens/detail/TvShowDetailScreen.kt` |
| Modify | `ui/screens/detail/EpisodeDetailScreen.kt` |
| Modify | `ui/screens/detail/StuffDetailScreen.kt` |
| Modify | `ui/screens/detail/SeasonScreen.kt` |

---

## Task 1: Library Screens — 3-Column 16:9 Grid

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/MovieLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/TvShowLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/StuffLibraryScreen.kt`

- [ ] **Step 1: Verify baseline tests pass**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Update MovieLibraryScreen grid config**

In `MovieLibraryScreen.kt`, find the `LazyVerticalGrid` call and make two changes:

Change `GridCells.Adaptive(minSize = 160.dp)` → `GridCells.Fixed(3)`

Add `aspectRatio = 16f / 9f` to the `TvMediaCard` call inside `items { }`:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(24.dp),
    verticalArrangement = Arrangement.spacedBy(32.dp),
    modifier = Modifier.fillMaxSize()
) {
    items(pagedItems.itemCount) { index ->
        val item = pagedItems[index]
        if (item != null) {
            TvMediaCard(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                aspectRatio = 16f / 9f,
                watchStatus = item.watchStatus,
                playbackProgress = item.playbackProgress,
                unwatchedCount = item.unwatchedCount,
                onClick = { onOpenItem(item) }
            )
        }
    }
    // ... append loading item unchanged
}
```

- [ ] **Step 3: Apply same change to TvShowLibraryScreen.kt**

Same two changes: `GridCells.Fixed(3)` and `aspectRatio = 16f / 9f` on `TvMediaCard`.

- [ ] **Step 4: Apply same change to StuffLibraryScreen.kt**

Same two changes: `GridCells.Fixed(3)` and `aspectRatio = 16f / 9f` on `TvMediaCard`.

- [ ] **Step 5: Build and run tests**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/
git commit -m "feat(library): switch to 3-column 16:9 card grid"
```

---

## Task 2: Shared Detail Screen Components

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt`

These three composables are used by every detail screen. Creating them first means subsequent tasks can reference them immediately.

- [ ] **Step 1: Create `DetailScreenComponents.kt`**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Full-width hero box with backdrop image and gradient overlays.
 * Content is placed via [content] slot; anchor to [Alignment.BottomStart] for standard detail layout.
 */
@Composable
fun DetailHeroBox(
    backdropUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Vertical gradient: transparent top → opaque background bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to background.copy(alpha = 0.5f),
                            1f to background,
                        )
                    )
                )
        )
        // Horizontal gradient: opaque background left → transparent right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to background,
                            0.35f to background.copy(alpha = 0.7f),
                            1f to Color.Transparent,
                        )
                    )
                )
        )
        content()
    }
}

/**
 * Section with a heading label and arbitrary [content] below it.
 * Used for Cast, Similar, Seasons, Chapters rows.
 */
@Composable
fun DetailContentSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        content()
    }
}

/**
 * Shared error state for all detail screens.
 */
@Composable
fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** Centered spinner used during loading. */
@Composable
fun DetailLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

- [ ] **Step 2: Build to verify file compiles**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt
git commit -m "feat(detail): add shared DetailHeroBox and section composables"
```

---

## Task 3: MovieDetailScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt`

Replace the entire file. The public `MovieDetailScreen` signature is unchanged. `MovieDetailViewModel` is unchanged.

- [ ] **Step 1: Replace `MovieDetailScreen.kt` with new implementation**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun MovieDetailScreen(
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is MovieDetailUiState.Loading -> DetailLoadingState()
            is MovieDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is MovieDetailUiState.Content -> MovieDetailContent(
                movie = state.movie,
                cast = state.cast,
                similarMovies = state.similarMovies,
                onPlayMovie = onPlayMovie,
                onOpenMovie = onOpenMovie,
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: MovieDetailModel,
    cast: List<CastModel>,
    similarMovies: List<SimilarMovieModel>,
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = movie.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        movie.year?.let {
                            Text(text = "$it", style = MaterialTheme.typography.bodyLarge)
                        }
                        movie.officialRating?.let {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        movie.duration?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        movie.rating?.let {
                            Text(
                                text = "★ $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    movie.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { onPlayMovie(movie.id) },
                            modifier = Modifier.focusRequester(playFocusRequester),
                        ) {
                            Text("Play")
                        }
                        OutlinedButton(onClick = {}) {
                            Text("Trailer")
                        }
                    }
                }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                DetailContentSection(title = "Cast & Crew") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(cast) { person ->
                            TvMediaCard(
                                title = person.name,
                                subtitle = person.role,
                                imageUrl = person.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 120.dp,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        if (similarMovies.isNotEmpty()) {
            item {
                DetailContentSection(title = "Similar Movies") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similarMovies) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                onClick = { onOpenMovie(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt
git commit -m "feat(detail): rewrite MovieDetailScreen to hero + LazyColumn layout"
```

---

## Task 4: TvShowDetailScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt`

Replace the entire file. Public signature unchanged. `TvShowDetailViewModel` unchanged.

- [ ] **Step 1: Replace `TvShowDetailScreen.kt`**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun TvShowDetailScreen(
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TvShowDetailUiState.Loading -> DetailLoadingState()
            is TvShowDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is TvShowDetailUiState.Content -> TvShowDetailContent(
                show = state.show,
                seasons = state.seasons,
                cast = state.cast,
                similarShows = state.similarShows,
                onPlayEpisode = onPlayEpisode,
                onOpenSeason = onOpenSeason,
                onOpenShow = onOpenShow,
            )
        }
    }
}

@Composable
private fun TvShowDetailContent(
    show: TvShowDetailModel,
    seasons: List<SeasonModel>,
    cast: List<CastModel>,
    similarShows: List<SimilarMovieModel>,
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = show.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        show.yearRange?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        show.officialRating?.let {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        show.status?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        show.rating?.let {
                            Text(
                                text = "★ $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    show.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (show.nextUpEpisodeId != null) {
                            Button(
                                onClick = { onPlayEpisode(show.nextUpEpisodeId) },
                                modifier = Modifier.focusRequester(playFocusRequester),
                            ) {
                                Text("Play Next Up: ${show.nextUpTitle}")
                            }
                        } else {
                            Button(
                                onClick = { seasons.firstOrNull()?.let { onOpenSeason(it.id) } },
                                modifier = Modifier.focusRequester(playFocusRequester),
                                enabled = seasons.isNotEmpty(),
                            ) {
                                Text("Browse Seasons")
                            }
                        }
                    }
                }
            }
        }

        if (seasons.isNotEmpty()) {
            item {
                DetailContentSection(title = "Seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(seasons) { season ->
                            TvMediaCard(
                                title = season.title,
                                subtitle = season.episodeCount?.let { "$it Episodes" },
                                imageUrl = season.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                unwatchedCount = season.unwatchedCount,
                                onClick = { onOpenSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                DetailContentSection(title = "Cast & Crew") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(cast) { person ->
                            TvMediaCard(
                                title = person.name,
                                subtitle = person.role,
                                imageUrl = person.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 120.dp,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                DetailContentSection(title = "More Like This") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similarShows) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                onClick = { onOpenShow(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt
git commit -m "feat(detail): rewrite TvShowDetailScreen to hero + LazyColumn layout"
```

---

## Task 5: EpisodeDetailScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt`

Replace the entire file. Public signature unchanged. `EpisodeDetailViewModel` unchanged.

- [ ] **Step 1: Replace `EpisodeDetailScreen.kt`**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.utils.formatMs

@Composable
fun EpisodeDetailScreen(
    onPlayEpisode: (String, Long?) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EpisodeDetailUiState.Loading -> DetailLoadingState()
            is EpisodeDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is EpisodeDetailUiState.Content -> EpisodeDetailContent(
                episode = state.episode,
                chapters = state.chapters,
                onPlayEpisode = onPlayEpisode,
            )
        }
    }
}

@Composable
private fun EpisodeDetailContent(
    episode: EpisodeDetailModel,
    chapters: List<ChapterModel>,
    onPlayEpisode: (String, Long?) -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = episode.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    episode.seriesName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        episode.episodeCode?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        episode.year?.let {
                            Text(text = "$it", style = MaterialTheme.typography.bodyLarge)
                        }
                        episode.duration?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (episode.isWatched) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text(
                                    text = "Watched",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    episode.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Button(
                        onClick = { onPlayEpisode(episode.id, null) },
                        modifier = Modifier.focusRequester(playFocusRequester),
                    ) {
                        Text("Play")
                    }
                }
            }
        }

        if (chapters.isNotEmpty()) {
            item {
                DetailContentSection(title = "Chapters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(chapters, key = { it.id }) { chapter ->
                            TvMediaCard(
                                title = chapter.name,
                                subtitle = formatMs(chapter.positionMs),
                                imageUrl = chapter.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 240.dp,
                                onClick = { onPlayEpisode(episode.id, chapter.positionMs) },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt
git commit -m "feat(detail): rewrite EpisodeDetailScreen to hero + LazyColumn layout"
```

---

## Task 6: StuffDetailScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/StuffDetailScreen.kt`

Replace the entire file. Public signature unchanged. `StuffDetailViewModel` unchanged.

- [ ] **Step 1: Replace `StuffDetailScreen.kt`**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun StuffDetailScreen(
    onOpenItem: (String, String?) -> Unit,
    onPlayItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StuffDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is StuffDetailUiState.Loading -> DetailLoadingState()
            is StuffDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is StuffDetailUiState.Content -> {
                if (state.stuff.isCollection) {
                    StuffCollectionContent(
                        stuff = state.stuff,
                        items = state.items,
                        onOpenItem = onOpenItem,
                    )
                } else {
                    StuffVideoContent(
                        stuff = state.stuff,
                        onPlayItem = onPlayItem,
                    )
                }
            }
        }
    }
}

/** Single playable video — full B1 hero with Play button. */
@Composable
private fun StuffVideoContent(
    stuff: StuffDetailModel,
    onPlayItem: (String) -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    DetailHeroBox(backdropUrl = stuff.backdropUrl, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.55f)
                .padding(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stuff.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            stuff.overview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Button(
                onClick = { onPlayItem(stuff.id) },
                modifier = Modifier.focusRequester(playFocusRequester),
            ) {
                Text("Play")
            }
        }
    }
}

/** Collection — compact title hero + 3-column 16:9 item grid. */
@Composable
private fun StuffCollectionContent(
    stuff: StuffDetailModel,
    items: List<StuffItemModel>,
    onOpenItem: (String, String?) -> Unit,
) {
    // Column root required: LazyVerticalGrid needs bounded height via weight(1f)
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeroBox(backdropUrl = stuff.backdropUrl) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.55f)
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stuff.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                stuff.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }

        Text(
            text = "Items",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 16.dp),
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No items found in this collection",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 56.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    TvMediaCard(
                        title = item.title,
                        imageUrl = item.imageUrl,
                        aspectRatio = 16f / 9f,
                        watchStatus = item.watchStatus,
                        playbackProgress = item.playbackProgress,
                        onClick = { onOpenItem(item.id, item.itemType) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/StuffDetailScreen.kt
git commit -m "feat(detail): rewrite StuffDetailScreen to hero layout"
```

---

## Task 7: SeasonScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt`

Replace the entire file. Public signature unchanged (`onOpenEpisode`, `onBack`). "Play Next" reuses `onOpenEpisode` — no new callback needed. `SeasonViewModel` unchanged.

- [ ] **Step 1: Replace `SeasonScreen.kt`**

```kotlin
@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.WatchStatus

@Composable
fun SeasonScreen(
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SeasonUiState.Loading -> DetailLoadingState()
            is SeasonUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is SeasonUiState.Content -> SeasonContent(
                season = state.season,
                episodes = state.episodes,
                onOpenEpisode = onOpenEpisode,
            )
        }
    }
}

@Composable
private fun SeasonContent(
    season: SeasonDetailModel,
    episodes: List<EpisodeModel>,
    onOpenEpisode: (String) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background

    // "Play Next" = first unwatched episode, or first episode if all watched
    val nextEpisodeId = remember(episodes) {
        episodes.firstOrNull { !it.isWatched }?.id ?: episodes.firstOrNull()?.id
    }

    // Column root is required — LazyVerticalGrid needs weight(1f) to get bounded height
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Mini hero strip ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(season.backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Vertical gradient: transparent → background (fraction-based)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.5f to background.copy(alpha = 0.7f),
                                1f to background,
                            )
                        )
                    )
            )
            // Horizontal gradient: background left → transparent right
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to background,
                                0.4f to background.copy(alpha = 0.6f),
                                1f to Color.Transparent,
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 56.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                season.seriesName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = season.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${episodes.size} Episodes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                season.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (nextEpisodeId != null) {
                    Button(
                        onClick = { onOpenEpisode(nextEpisodeId) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Play Next")
                    }
                }
            }
        }

        // ── Episodes label ───────────────────────────────────────────────────
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 12.dp),
        )

        // ── Episode grid — weight(1f) fills remaining column space ───────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(episodes) { episode ->
                TvMediaCard(
                    title = episode.title,
                    subtitle = episode.episodeCode ?: episode.number?.let { "Episode $it" },
                    imageUrl = episode.imageUrl,
                    aspectRatio = 16f / 9f,
                    watchStatus = when {
                        episode.isWatched -> WatchStatus.WATCHED
                        (episode.playbackProgress ?: 0f) > 0f -> WatchStatus.IN_PROGRESS
                        else -> WatchStatus.NONE
                    },
                    playbackProgress = episode.playbackProgress,
                    onClick = { onOpenEpisode(episode.id) },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build and test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: Both BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt
git commit -m "feat(detail): rewrite SeasonScreen to mini hero + 4-col episode grid"
```

---

## Done

All 8 files modified, 1 file created, 7 commits. The detail and library screens match the approved design spec.

Verify final state:
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
