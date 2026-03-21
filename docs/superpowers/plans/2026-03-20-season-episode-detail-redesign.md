# Season & Episode Detail Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix focus scroll-to-top on all detail screens, fix episode detail play button, replace the Season screen's oversized grid with a horizontal episode list, and add video/audio technical details to the Episode detail screen.

**Architecture:** All changes are in `ui/screens/detail/`. No new files, no new API calls, no new dependencies. Tasks are ordered cheapest-first so each commit leaves the app in a shippable state.

**Tech Stack:** Kotlin, Jetpack Compose TV (`androidx.tv.material3`), Hilt, Jellyfin SDK 1.8.6, MockK + Turbine for tests.

---

## File Map

| File | What changes |
|---|---|
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt` | Remove broken `firstVisibleItemIndex == 0` guard |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt` | Remove broken `firstVisibleItemIndex == 0` guard |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt` | Same guard fix + replace 5-col grid + fix `primaryActionFocusRequester` attachment |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt` | `withFrameMillis` delay + add media details section |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModel.kt` | Add `VideoStreamInfo`, `AudioStreamInfo`, `MediaDetailModel` + mapping |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt` | Add `EpisodeListRow` composable |
| `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModelTest.kt` | New — unit tests for `toMediaDetailModel()` mapping |
| `app/src/test/java/com/rpeters/cinefintv/testutil/FakeRepositories.kt` | Add `FakeEpisodeDetailRepositories` |

---

## Task 1: Fix focus scroll-to-top in Movie and TvShow detail screens

**Files:**
- Modify: `ui/screens/detail/MovieDetailScreen.kt:113-118`
- Modify: `ui/screens/detail/TvShowDetailScreen.kt:120-125`

Both files contain `DetailHeroBox` with this broken modifier:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus && listState.firstVisibleItemIndex == 0) {  // <-- BUG
        scope.launch { listState.animateScrollToItem(0) }
    }
},
```
The guard `&& listState.firstVisibleItemIndex == 0` prevents the scroll from firing when the user has scrolled down. When D-pad UP from a content row focuses the play button, the hero stays off-screen.

- [ ] **Step 1: Fix MovieDetailScreen**

In `MovieDetailScreen.kt`, find the `DetailHeroBox` modifier (around line 113) and change:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus && listState.firstVisibleItemIndex == 0) {
        // Keep top visible when buttons in hero are focused
        scope.launch { listState.animateScrollToItem(0) }
    }
},
```
to:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus) {
        scope.launch { listState.animateScrollToItem(0) }
    }
},
```

- [ ] **Step 2: Fix TvShowDetailScreen**

In `TvShowDetailScreen.kt`, find the same pattern (around line 120) and apply the identical fix — remove `&& listState.firstVisibleItemIndex == 0`.

- [ ] **Step 3: Build to verify no compile errors**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt
git commit -m "fix: scroll hero into view on focus regardless of scroll position"
```

---

## Task 2: Fix episode detail play button (focus timing)

**Files:**
- Modify: `ui/screens/detail/EpisodeDetailScreen.kt:79-82`

The `LaunchedEffect` fires immediately on composition, before the `Spacer` anchor node has completed its layout pass. `requestFocus()` on an unattached node is a silent no-op, leaving the screen with no focused element and making the play button unreachable.

- [ ] **Step 1: Add `withFrameMillis` delay**

In `EpisodeDetailScreen.kt`, find `EpisodeDetailContent` (around line 79):
```kotlin
LaunchedEffect(episode.id) {
    // Request initial focus on the top anchor
    anchorFocusRequester.requestFocus()
}
```
Change to:
```kotlin
LaunchedEffect(episode.id) {
    withFrameMillis {}   // wait one frame for layout to attach the anchor node
    anchorFocusRequester.requestFocus()
}
```

Add the import at the top of the file (it belongs with the other `androidx.compose.runtime` imports):
```kotlin
import androidx.compose.runtime.withFrameMillis
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt
git commit -m "fix: wait one frame before requesting initial focus on episode detail"
```

---

## Task 3: Add `EpisodeListRow` composable

**Files:**
- Modify: `ui/screens/detail/DetailScreenComponents.kt` (append at end of file)

`EpisodeListRow` is a full-width horizontal TV card: 16:9 thumbnail on the left, episode metadata on the right. It replaces the oversized grid cards in `SeasonScreen`.

- [ ] **Step 1: Add the composable to `DetailScreenComponents.kt`**

Append the following to the end of `DetailScreenComponents.kt`. Add any missing imports alongside the existing ones at the top of the file.

```kotlin
/**
 * Full-width horizontal episode row for season episode lists.
 * Left: 16:9 thumbnail with watch status overlay and progress bar.
 * Right: episode code + duration, title, overview.
 */
@Composable
fun EpisodeListRow(
    episode: com.rpeters.cinefintv.ui.screens.detail.EpisodeModel,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp)
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                if (focused != isFocused) {
                    isFocused = focused
                    if (focused) onFocus()
                }
            },
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.02f),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            ),
        ),
        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = if (isFocused) expressiveColors.accentSurface else expressiveColors.chromeSurface.copy(alpha = 0.45f),
            focusedContainerColor = expressiveColors.accentSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
            ) {
                if (episode.imageUrl != null) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(episode.imageUrl)
                            .crossfade(true)
                            .size(320, 180)
                            .build(),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(expressiveColors.accentSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = episode.number?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Watch status overlay
                val watchStatus = when {
                    episode.isWatched -> com.rpeters.cinefintv.ui.components.WatchStatus.WATCHED
                    (episode.playbackProgress ?: 0f) > 0f -> com.rpeters.cinefintv.ui.components.WatchStatus.IN_PROGRESS
                    else -> com.rpeters.cinefintv.ui.components.WatchStatus.NONE
                }
                if (watchStatus == com.rpeters.cinefintv.ui.components.WatchStatus.WATCHED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.95f), RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                // Progress bar
                val progress = episode.playbackProgress ?: 0f
                if (progress > 0f && !episode.isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                // Episode code + duration row
                val metaLine = listOfNotNull(episode.episodeCode, episode.duration).joinToString("  •  ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                episode.overview?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
```

You will need these imports if not already present in `DetailScreenComponents.kt`:
```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
```

Most of these are already present — check before adding duplicates.

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. Fix any import errors; do not add duplicate imports.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt
git commit -m "feat: add EpisodeListRow horizontal card composable"
```

---

## Task 4: Update SeasonScreen to use episode list

**Files:**
- Modify: `ui/screens/detail/SeasonScreen.kt`

Three changes in one commit:
1. Remove the 5-column grid, replace with `items(episodes)` + `EpisodeListRow`
2. Fix `primaryActionFocusRequester` always-attached bug
3. Remove `firstVisibleItemIndex == 0` guard from `DetailHeroBox`

- [ ] **Step 1: Replace the episode grid**

In `SeasonScreen.kt`, find and delete this entire block (approximately lines 221–269):
```kotlin
// 5-column grid for smaller cards
val columns = 5
val rows = (episodes.size + columns - 1) / columns
items(rows) { rowIndex ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (columnIndex in 0 until columns) {
            val episodeIndex = rowIndex * columns + columnIndex
            if (episodeIndex < episodes.size) {
                val episode = episodes[episodeIndex]
                TvMediaCard(
                    ...
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
```

Replace it with:
```kotlin
items(episodes, key = { it.id }) { episode ->
    EpisodeListRow(
        episode = episode,
        modifier = Modifier
            .then(
                if (episode.id == lastFocusedEpisodeId) Modifier.focusRequester(episodeGridEntryRequester) else Modifier
            )
            .then(
                if (episode.id == episodes.firstOrNull()?.id) {
                    Modifier.focusProperties { up = primaryActionFocusRequester }
                } else {
                    Modifier
                }
            ),
        onFocus = {
            lastFocusedEpisodeId = episode.id
            focusedEpisode = episode
        },
        onClick = { onOpenEpisode(episode.id) },
    )
}
```

Also update the `DetailContentSection` item for the "Episodes" heading. Find:
```kotlin
item {
    DetailContentSection(
        title = "Episodes",
        eyebrow = "${episodes.count { !it.isWatched }} unwatched",
        modifier = Modifier.padding(top = 0.dp),
    ) {}
}
```
Change to (remove the unnecessary modifier override):
```kotlin
item {
    DetailContentSection(
        title = "Episodes",
        eyebrow = "${episodes.count { !it.isWatched }} unwatched",
    ) {}
}
```

- [ ] **Step 2: Fix `primaryActionFocusRequester` always attached**

Find the `DetailActionRow` that fires when `focusedEpisode == null` (around line 188). It already has `primaryFocusRequester = primaryActionFocusRequester`.

Find the `DetailActionRow` that fires when `focusedEpisode != null` (around line 202). It currently has `primaryFocusRequester = null`. Change it to:
```kotlin
DetailActionRow(
    primaryLabel = if ((focusedEpisode?.playbackProgress ?: 0f) > 0f) "Resume Episode" else "Play Episode",
    onPrimaryClick = { onOpenEpisode(focusedEpisode!!.id) },
    primaryFocusRequester = primaryActionFocusRequester,  // was null — now always attached
)
```

- [ ] **Step 3: Fix `firstVisibleItemIndex == 0` guard**

Find `DetailHeroBox` modifier in `SeasonContent` (around line 126):
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus && listState.firstVisibleItemIndex == 0) {
        scope.launch { listState.animateScrollToItem(0) }
    }
},
```
Change to:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus) {
        scope.launch { listState.animateScrollToItem(0) }
    }
},
```

- [ ] **Step 4: Remove unused `TvMediaCard` import if no longer used**

Check if `TvMediaCard` is still referenced in `SeasonScreen.kt`. If not, remove the import:
```kotlin
import com.rpeters.cinefintv.ui.components.TvMediaCard
```

- [ ] **Step 5: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt
git commit -m "feat: replace season episode grid with horizontal list rows"
```

---

## Task 5: Add `MediaDetailModel` to `EpisodeDetailViewModel`

**Files:**
- Modify: `ui/screens/detail/EpisodeDetailViewModel.kt`
- Create: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModelTest.kt`
- Modify: `app/src/test/java/com/rpeters/cinefintv/testutil/FakeRepositories.kt`

- [ ] **Step 1: Add `FakeEpisodeDetailRepositories` to `FakeRepositories.kt`**

Append to `FakeRepositories.kt`:
```kotlin
class FakeEpisodeDetailRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeEpisodeDetailRepositories.media
        every { this@mockk.stream } returns this@FakeEpisodeDetailRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModelTest.kt`:

```kotlin
package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeEpisodeDetailRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSource
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeSavedStateHandle(episodeId: String) =
        SavedStateHandle(mapOf("itemId" to episodeId))

    private fun makeBaseItem(
        id: String = UUID.randomUUID().toString(),
        videoCodec: String? = "hevc",
        videoWidth: Int? = 1920,
        videoHeight: Int? = 1080,
        videoBitRate: Int? = 8000000,
        audioCodec: String? = "eac3",
        audioChannels: Int? = 6,
        audioLanguage: String? = "eng",
        audioIsDefault: Boolean? = true,
        container: String? = "mkv",
    ): BaseItemDto {
        val videoStream = mockk<MediaStream>(relaxed = true) {
            every { type } returns MediaStreamType.VIDEO
            every { codec } returns videoCodec
            every { width } returns videoWidth
            every { height } returns videoHeight
            every { bitRate } returns videoBitRate
            every { videoRange } returns null
            every { videoRangeType } returns null
        }
        val audioStream = mockk<MediaStream>(relaxed = true) {
            every { type } returns MediaStreamType.AUDIO
            every { codec } returns audioCodec
            every { channels } returns audioChannels
            every { language } returns audioLanguage
            every { isDefault } returns audioIsDefault
            every { profile } returns null
        }
        val source = mockk<MediaSource>(relaxed = true) {
            every { this@mockk.container } returns container
            every { mediaStreams } returns listOf(videoStream, audioStream)
        }
        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { mediaSources } returns listOf(source)
            every { chapters } returns null
        }
    }

    @Test
    fun load_parsesVideoResolutionCorrectly() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, videoWidth = 1920, videoHeight = 1080))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertEquals("1080p", state.mediaDetail?.video?.resolution)
    }

    @Test
    fun load_parsesVideoCodecHEVC() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, videoCodec = "hevc"))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertEquals("HEVC", state.mediaDetail?.video?.codec)
    }

    @Test
    fun load_parsesAudioStreamChannels() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, audioChannels = 6, audioCodec = "eac3"))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        val audio = state.mediaDetail?.audioStreams?.firstOrNull()
        assertNotNull(audio)
        assertEquals("5.1", audio?.channels)
        assertEquals("EAC3", audio?.codec)
    }

    @Test
    fun load_whenNoMediaSources_mediaDetailIsNull() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        val itemWithNoSources = mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(episodeId)
            every { mediaSources } returns null
            every { chapters } returns null
        }
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(itemWithNoSources)
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertNull(state.mediaDetail)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.EpisodeDetailViewModelTest"
```
Expected: FAIL — `EpisodeDetailUiState.Content` does not yet have a `mediaDetail` field.

- [ ] **Step 4: Add data models to `EpisodeDetailViewModel.kt`**

After the existing `data class ChapterModel(...)` (around line 43), add:

```kotlin
data class VideoStreamInfo(
    val resolution: String?,
    val codec: String?,
    val hdr: String?,
    val bitrateKbps: Int?,
)

data class AudioStreamInfo(
    val codec: String,
    val channels: String?,
    val language: String?,
    val isDefault: Boolean,
)

data class MediaDetailModel(
    val container: String?,
    val video: VideoStreamInfo?,
    val audioStreams: List<AudioStreamInfo>,
)
```

Add `mediaDetail: MediaDetailModel?` to `EpisodeDetailUiState.Content`:
```kotlin
data class Content(
    val episode: EpisodeDetailModel,
    val chapters: List<ChapterModel>,
    val mediaDetail: MediaDetailModel?,
) : EpisodeDetailUiState()
```

- [ ] **Step 5: Add `toMediaDetailModel()` to the ViewModel**

Add this private extension function after `toEpisodeDetailModel()` in `EpisodeDetailViewModel`:

```kotlin
private fun BaseItemDto.toMediaDetailModel(): MediaDetailModel? {
    val source = mediaSources?.firstOrNull() ?: return null
    val streams = source.mediaStreams ?: return null

    val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStreams = streams.filter { it.type == MediaStreamType.AUDIO }

    val video = videoStream?.let { vs ->
        val width = vs.width ?: 0
        val height = vs.height ?: 0
        val resolution = when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            width > 0 -> "${height}p"
            else -> null
        }
        val codecRaw = vs.codec?.uppercase()
        val codec = when (codecRaw) {
            "HEVC", "H265" -> "HEVC"
            "AVC", "H264" -> "AVC"
            "VP9" -> "VP9"
            "AV1" -> "AV1"
            else -> null
        }
        val videoRange = vs.videoRange?.toString()?.lowercase()
        val videoRangeType = vs.videoRangeType?.toString()?.lowercase()
        val hdr = when {
            videoRange?.contains("dolby vision") == true || videoRangeType?.contains("dv") == true -> "Dolby Vision"
            videoRange?.contains("hdr10+") == true -> "HDR10+"
            videoRange?.contains("hdr10") == true -> "HDR10"
            videoRange?.contains("hdr") == true || videoRangeType?.contains("hdr") == true -> "HDR"
            else -> null
        }
        VideoStreamInfo(
            resolution = resolution,
            codec = codec,
            hdr = hdr,
            bitrateKbps = vs.bitRate?.div(1000),
        )
    }

    val audios = audioStreams.map { ast ->
        val codecRaw = ast.codec?.uppercase() ?: "AUDIO"
        val codecDisplay = when {
            ast.profile?.lowercase()?.contains("atmos") == true -> "TrueHD Atmos"
            else -> when (codecRaw) {
                "EAC3", "E-AC3" -> "EAC3"
                "AC3" -> "AC3"
                "TRUEHD" -> "TrueHD"
                "DTS" -> "DTS"
                "AAC" -> "AAC"
                "FLAC" -> "FLAC"
                "MP3" -> "MP3"
                "OPUS" -> "Opus"
                "VORBIS" -> "Vorbis"
                else -> codecRaw
            }
        }
        val channels = when (ast.channels) {
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> ast.channels?.let { "$it ch" }
        }
        AudioStreamInfo(
            codec = codecDisplay,
            channels = channels,
            language = ast.language?.uppercase()?.take(3),
            isDefault = ast.isDefault == true,
        )
    }.sortedByDescending { it.isDefault }

    if (video == null && audios.isEmpty()) return null
    return MediaDetailModel(
        container = source.container?.uppercase(),
        video = video,
        audioStreams = audios,
    )
}
```

Add the `MediaStreamType` import if not already present:
```kotlin
import org.jellyfin.sdk.model.api.MediaStreamType
```

- [ ] **Step 6: Wire `mediaDetail` in `load()`**

In the `load()` function, find the `_uiState.value = EpisodeDetailUiState.Content(...)` call and add `mediaDetail`:
```kotlin
_uiState.value = EpisodeDetailUiState.Content(
    episode = episodeDto.toEpisodeDetailModel(),
    chapters = chapters,
    mediaDetail = episodeDto.toMediaDetailModel(),
)
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.EpisodeDetailViewModelTest"
```
Expected: 4 tests PASS.

- [ ] **Step 8: Run full test suite to check for regressions**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: All tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModelTest.kt \
        app/src/test/java/com/rpeters/cinefintv/testutil/FakeRepositories.kt
git commit -m "feat: add MediaDetailModel and media stream parsing to EpisodeDetailViewModel"
```

---

## Task 6: Show media details section in EpisodeDetailScreen

**Files:**
- Modify: `ui/screens/detail/EpisodeDetailScreen.kt`

- [ ] **Step 1: Update `EpisodeDetailContent` signature**

Find the private composable `EpisodeDetailContent` (around line 67). Add `mediaDetail: MediaDetailModel?` parameter:
```kotlin
@Composable
private fun EpisodeDetailContent(
    episode: EpisodeDetailModel,
    chapters: List<ChapterModel>,
    mediaDetail: MediaDetailModel?,
    onPlayEpisode: (String, Long?) -> Unit,
)
```

Update the call site in `EpisodeDetailScreen` (around line 57):
```kotlin
is EpisodeDetailUiState.Content -> EpisodeDetailContent(
    episode = state.episode,
    chapters = state.chapters,
    mediaDetail = state.mediaDetail,
    onPlayEpisode = onPlayEpisode,
)
```

- [ ] **Step 2: Add media details `item` to the `LazyColumn`**

In `EpisodeDetailContent`, after the chapters `item` block, add:

```kotlin
if (mediaDetail != null && (mediaDetail.video != null || mediaDetail.audioStreams.isNotEmpty())) {
    item {
        DetailContentSection(title = "Media Details") {
            // Video row
            mediaDetail.video?.let { video ->
                val videoChips = listOfNotNull(
                    video.resolution,
                    video.codec,
                    video.hdr,
                    video.bitrateKbps?.let { "${it} kbps" },
                    mediaDetail.container,
                )
                if (videoChips.isNotEmpty()) {
                    DetailChipRow(
                        labels = videoChips,
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
            }
            // Audio rows (one per stream)
            mediaDetail.audioStreams.forEach { audio ->
                val audioLabel = listOfNotNull(
                    audio.codec,
                    audio.channels,
                    audio.language,
                ).joinToString("  ")
                if (audioLabel.isNotBlank()) {
                    DetailChipRow(
                        labels = listOf(audioLabel),
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: All pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt
git commit -m "feat: show video/audio media details on episode detail screen"
```

---

## Final Verification

- [ ] Build and install on device/emulator:
  ```bash
  ./gradlew :app:assembleDebug
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] Verify on device:
  - **Movie detail:** D-pad down to cast row, D-pad up → hero scrolls back into view, play button gets focus
  - **TvShow detail:** same scroll-to-top behaviour
  - **Season screen:** episodes appear as horizontal rows (thumbnail + title + overview), not a grid
  - **Season screen:** press up from first episode → play button gets focus (works regardless of which episode card is currently focused)
  - **Episode detail:** opening an episode auto-focuses the play button; pressing select navigates to player
  - **Episode detail:** scroll down — "Media Details" section shows resolution, codec, audio format chips
