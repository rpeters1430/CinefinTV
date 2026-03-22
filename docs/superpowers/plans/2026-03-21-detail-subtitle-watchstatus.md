# Detail Polish, Subtitle Fix, Watch Status Propagation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix illegible detail screen text, broken subtitle selection in the player, and stale watch status after returning from playback.

**Architecture:** Four independent changes: (1) add a dark glass background to `DetailGlassPanel` and fix title text color; (2) replace fragile language-preference subtitle selection with `TrackSelectionOverride` in `PlayerViewModel`; (3) add silent `refreshWatchStatus()` methods to four ViewModels; (4) add lifecycle observers to four screens that call those methods when the screen resumes.

**Tech Stack:** Kotlin, Jetpack Compose, androidx.tv.material3, Media3 ExoPlayer 1.10.0-beta01, Hilt, MockK, Turbine, coroutines-test.

**Spec:** `docs/superpowers/specs/2026-03-21-detail-subtitle-watchstatus-design.md`

---

## File Map

| File | Change |
|---|---|
| `ui/screens/detail/DetailScreenComponents.kt` | Wrap `DetailGlassPanel` Column in a Box with dark glass background |
| `ui/screens/detail/MovieDetailScreen.kt` | Add `color` to title Text; add lifecycle observer |
| `ui/screens/detail/TvShowDetailScreen.kt` | Add `color` to title Text; add lifecycle observer |
| `ui/screens/detail/EpisodeDetailScreen.kt` | Add `color` to title Text; add lifecycle observer |
| `ui/screens/home/HomeScreen.kt` | Add lifecycle observer |
| `ui/screens/detail/MovieDetailViewModel.kt` | Add `refreshWatchStatus()` |
| `ui/screens/detail/TvShowDetailViewModel.kt` | Add `refreshWatchStatus()` |
| `ui/screens/detail/EpisodeDetailViewModel.kt` | Add `refreshWatchStatus()` |
| `ui/screens/home/HomeViewModel.kt` | Add `refreshWatchStatus()` |
| `ui/player/PlayerViewModel.kt` | Replace `applyTrackSelection` subtitle logic with `TrackSelectionOverride` |
| `testutil/FakeRepositories.kt` | Add `FakeMovieDetailRepositories`, `FakeTvShowDetailRepositories` |
| `ui/screens/detail/MovieDetailViewModelTest.kt` | New — test `refreshWatchStatus()` |
| `ui/screens/detail/TvShowDetailViewModelTest.kt` | New — test `refreshWatchStatus()` |
| `ui/screens/home/HomeViewModelTest.kt` | Add `refreshWatchStatus()` tests |
| `ui/player/PlayerViewModelTest.kt` | Add subtitle track state tests |

---

## Task 1: Detail Screen — Glass Panel Background + Title Color

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt:181-190`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt:139-144`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt:146-151`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt:136-141`

- [ ] **Step 1: Update `DetailGlassPanel` to add dark glass background**

  In `DetailScreenComponents.kt`, replace the current bare `Column`:

  ```kotlin
  @Composable
  fun DetailGlassPanel(
      modifier: Modifier = Modifier,
      content: @Composable ColumnScope.() -> Unit,
  ) {
      val expressiveColors = LocalCinefinExpressiveColors.current
      val spacing = LocalCinefinSpacing.current
      Box(
          modifier = modifier
              .background(
                  color = expressiveColors.chromeSurface.copy(alpha = 0.82f),
                  shape = RoundedCornerShape(spacing.cornerContainer),
              )
              .border(
                  width = 1.dp,
                  color = expressiveColors.borderSubtle.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(spacing.cornerContainer),
              )
      ) {
          Column(
              modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              content = content,
          )
      }
  }
  ```

  Note: `LocalCinefinSpacing` is already imported at the top of the file. Add `androidx.compose.foundation.border` import if not present.

- [ ] **Step 2: Add title color to `MovieDetailScreen`**

  At line 139, add `color = MaterialTheme.colorScheme.onSurface` to the title Text. Use the existing `androidx.tv.material3.MaterialTheme` import — do NOT add a compose.material3 import.

  ```kotlin
  Text(
      text = movie.title,
      style = MaterialTheme.typography.displayMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
  )
  ```

- [ ] **Step 3: Add title color to `TvShowDetailScreen`**

  At line 146, same pattern:

  ```kotlin
  Text(
      text = show.title,
      style = MaterialTheme.typography.displayMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
  )
  ```

- [ ] **Step 4: Add title color to `EpisodeDetailScreen`**

  At line 136, same pattern:

  ```kotlin
  Text(
      text = episode.title,
      style = MaterialTheme.typography.displayMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
  )
  ```

- [ ] **Step 5: Build and visually verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL. Install and open any movie/show/episode detail screen. The text panel to the right of the poster should have a visible dark background. Title text should be white/light, not black.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt
  git commit -m "fix: add dark glass background to detail panel and fix title text color"
  ```

---

## Task 2: Subtitle Selection — Use TrackSelectionOverride for Direct Play

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt:373-394`
- Modify: `app/src/test/java/com/rpeters/cinefintv/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Write failing tests for subtitle state management**

  In `PlayerViewModelTest.kt`, add these two tests. The `applyTrackSelection` call is safe in tests because `_player` is null and the method early-returns — only the state update is testable at unit level.

  ```kotlin
  @Test
  fun selectSubtitleTrack_updatesSelectedSubtitleTrackInState() = runTest {
      // mockkObject(PlaybackPositionStore) is already called in the class init block
      coEvery { PlaybackPositionStore.getPlaybackPosition(any(), any()) } returns 0L

      val fakeRepositories = FakePlayerRepositories()
      coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Error("not needed")

      val viewModel = PlayerViewModel(
          savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
          repositories = fakeRepositories.coordinator,
          enhancedPlaybackManager = enhancedPlaybackManager,
          adaptiveBitrateMonitor = adaptiveBitrateMonitor,
          playbackPreferencesRepository = playbackPreferencesRepository,
          subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
          appContext = appContext,
          okHttpClient = OkHttpClient(),
      )
      advanceUntilIdle()

      val track = TrackOption(id = "sub-0", label = "English", language = "eng", streamIndex = 2)
      viewModel.selectSubtitleTrack(track, positionMs = 5000L, playWhenReady = true)
      advanceUntilIdle()

      assertEquals(track, viewModel.uiState.value.selectedSubtitleTrack)
  }

  @Test
  fun selectSubtitleTrack_withNull_clearsSelectedSubtitleTrack() = runTest {
      // mockkObject(PlaybackPositionStore) is already called in the class init block
      coEvery { PlaybackPositionStore.getPlaybackPosition(any(), any()) } returns 0L

      val fakeRepositories = FakePlayerRepositories()
      coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Error("not needed")

      val track = TrackOption(id = "sub-0", label = "English", language = "eng", streamIndex = 2)
      val viewModel = PlayerViewModel(
          savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
          repositories = fakeRepositories.coordinator,
          enhancedPlaybackManager = enhancedPlaybackManager,
          adaptiveBitrateMonitor = adaptiveBitrateMonitor,
          playbackPreferencesRepository = playbackPreferencesRepository,
          subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
          appContext = appContext,
          okHttpClient = OkHttpClient(),
      )
      advanceUntilIdle()

      viewModel.selectSubtitleTrack(track, positionMs = 0L, playWhenReady = false)
      viewModel.selectSubtitleTrack(null, positionMs = 0L, playWhenReady = false)
      advanceUntilIdle()

      assertNull(viewModel.uiState.value.selectedSubtitleTrack)
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail (or pass trivially due to missing implementation)**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.player.PlayerViewModelTest.selectSubtitleTrack_updatesSelectedSubtitleTrackInState" --tests "com.rpeters.cinefintv.ui.player.PlayerViewModelTest.selectSubtitleTrack_withNull_clearsSelectedSubtitleTrack"
  ```

  Expected: tests run (the state update part already works). They confirm the behavior is preserved after the fix.

- [ ] **Step 3: Replace `applyTrackSelection` in `PlayerViewModel`**

  Replace the entire `applyTrackSelection` private method (lines 373-394) with:

  ```kotlin
  private fun applyTrackSelection(audioTrack: TrackOption?, subtitleTrack: TrackOption?) {
      val player = _player ?: return

      val builder = player.trackSelectionParameters.buildUpon()

      // Audio selection
      if (audioTrack != null) {
          builder.setPreferredAudioLanguage(audioTrack.language)
      }

      // Always clear stale text overrides before applying any subtitle logic.
      // This single call covers both the enable and disable paths below.
      builder.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)

      if (subtitleTrack != null) {
          builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)

          // Prefer TrackSelectionOverride for reliable selection.
          // Position-based tie-breaking: the N-th TrackOption in uiState.subtitleTracks
          // corresponds to the N-th TRACK_TYPE_TEXT group in ExoPlayer's currentTracks
          // (both enumerate streams in container order). Only applies for DIRECT_PLAY.
          val subtitleIndex = uiState.value.subtitleTracks.indexOf(subtitleTrack)
          val textGroups = player.currentTracks.groups
              .filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }

          val override: androidx.media3.common.TrackSelectionOverride? = when {
              textGroups.isEmpty() -> {
                  // Tracks not yet demuxed — fall back to language preference
                  android.util.Log.w(
                      "PlayerViewModel",
                      "applyTrackSelection: no text track groups available, falling back to language preference"
                  )
                  null
              }
              subtitleIndex >= 0 && subtitleIndex < textGroups.size -> {
                  // Reliable position-based match
                  val group = textGroups[subtitleIndex]
                  androidx.media3.common.TrackSelectionOverride(
                      group.mediaTrackGroup,
                      0, // select track at index 0 — text groups contain one track each
                  )
              }
              else -> {
                  // Fall back: find by language
                  val lang = subtitleTrack.language
                  textGroups.firstNotNullOfOrNull { group ->
                      (0 until group.length)
                          .firstOrNull { i -> group.getTrackFormat(i).language == lang }
                          ?.let { trackIdx ->
                              androidx.media3.common.TrackSelectionOverride(
                                  group.mediaTrackGroup,
                                  listOf(trackIdx),
                              )
                          }
                  }
              }
          }

          if (override != null) {
              builder.addOverride(override)
          } else {
              // Language preference fallback when no override was found
              builder.setPreferredTextLanguage(subtitleTrack.language)
              builder.setSelectUndeterminedTextLanguage(true)
          }
      } else {
          // Disabling subtitles — overrides already cleared above by clearOverridesOfType
          builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
          builder.setPreferredTextLanguage(null)
      }

      player.trackSelectionParameters = builder.build()
  }
  ```

  Add the required import at the top of `PlayerViewModel.kt` if not already present:
  ```kotlin
  import androidx.media3.common.C
  import androidx.media3.common.TrackSelectionOverride
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.player.PlayerViewModelTest.selectSubtitleTrack_updatesSelectedSubtitleTrackInState" --tests "com.rpeters.cinefintv.ui.player.PlayerViewModelTest.selectSubtitleTrack_withNull_clearsSelectedSubtitleTrack"
  ```

  Expected: both PASS.

- [ ] **Step 5: Run the full test suite to check for regressions**

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: all tests PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt \
          app/src/test/java/com/rpeters/cinefintv/ui/player/PlayerViewModelTest.kt
  git commit -m "fix: use TrackSelectionOverride for reliable subtitle selection in direct play"
  ```

---

## Task 3: Watch Status — Add `refreshWatchStatus()` to ViewModels

**Files:**
- Modify: `app/src/test/java/com/rpeters/cinefintv/testutil/FakeRepositories.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt`
- Create: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModelTest.kt`
- Create: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt`
- Modify: `app/src/test/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModelTest.kt`

### 3a: Test infrastructure

- [ ] **Step 1: Add missing fake repository classes to `FakeRepositories.kt`**

  Append to the end of `FakeRepositories.kt`:

  ```kotlin
  class FakeMovieDetailRepositories(
      val media: JellyfinMediaRepository = mockk(relaxed = true),
      val stream: JellyfinStreamRepository = mockk(relaxed = true),
  ) {
      val coordinator: JellyfinRepositoryCoordinator = mockk {
          every { this@mockk.media } returns this@FakeMovieDetailRepositories.media
          every { this@mockk.stream } returns this@FakeMovieDetailRepositories.stream
          every { this@mockk.user } returns mockk(relaxed = true)
          every { this@mockk.search } returns mockk(relaxed = true)
          every { this@mockk.auth } returns mockk(relaxed = true)
      }
  }

  class FakeTvShowDetailRepositories(
      val media: JellyfinMediaRepository = mockk(relaxed = true),
      val stream: JellyfinStreamRepository = mockk(relaxed = true),
  ) {
      val coordinator: JellyfinRepositoryCoordinator = mockk {
          every { this@mockk.media } returns this@FakeTvShowDetailRepositories.media
          every { this@mockk.stream } returns this@FakeTvShowDetailRepositories.stream
          every { this@mockk.user } returns mockk(relaxed = true)
          every { this@mockk.search } returns mockk(relaxed = true)
          every { this@mockk.auth } returns mockk(relaxed = true)
      }
  }
  ```

### 3b: `MovieDetailViewModel.refreshWatchStatus()`

- [ ] **Step 2: Write the failing test for `MovieDetailViewModel.refreshWatchStatus()`**

  Create `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModelTest.kt`:

  ```kotlin
  package com.rpeters.cinefintv.ui.screens.detail

  import androidx.lifecycle.SavedStateHandle
  import com.rpeters.cinefintv.data.repository.common.ApiResult
  import com.rpeters.cinefintv.testutil.FakeMovieDetailRepositories
  import com.rpeters.cinefintv.testutil.MainDispatcherRule
  import io.mockk.coEvery
  import io.mockk.every
  import io.mockk.mockk
  import java.util.UUID
  import kotlinx.coroutines.ExperimentalCoroutinesApi
  import kotlinx.coroutines.test.advanceUntilIdle
  import kotlinx.coroutines.test.runTest
  import org.jellyfin.sdk.model.api.BaseItemDto
  import org.jellyfin.sdk.model.api.UserItemDataDto
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test

  @OptIn(ExperimentalCoroutinesApi::class)
  class MovieDetailViewModelTest {

      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      private fun makeMovieDto(
          id: String = UUID.randomUUID().toString(),
          isPlayed: Boolean = false,
          playbackPositionTicks: Long = 0L,
          runTimeTicks: Long = 6_000_000_000L, // ~100 min
      ): BaseItemDto = mockk(relaxed = true) {
          every { this@mockk.id } returns java.util.UUID.fromString(id)
          every { userData } returns mockk<UserItemDataDto>(relaxed = true) {
              every { played } returns isPlayed
              every { this@mockk.playbackPositionTicks } returns playbackPositionTicks
          }
          every { runTimeTicks } returns runTimeTicks
          every { people } returns emptyList()
      }

      @Test
      fun refreshWatchStatus_updatesIsWatchedWithoutLoadingState() = runTest {
          val movieId = UUID.randomUUID().toString()
          val fakeRepositories = FakeMovieDetailRepositories()

          val unwatchedDto = makeMovieDto(id = movieId, isPlayed = false)
          val watchedDto = makeMovieDto(id = movieId, isPlayed = true)

          coEvery { fakeRepositories.media.getMovieDetails(movieId) } returns ApiResult.Success(unwatchedDto)
          coEvery { fakeRepositories.media.getSimilarMovies(movieId) } returns ApiResult.Success(emptyList())
          every { fakeRepositories.stream.getBackdropUrl(any()) } returns null
          every { fakeRepositories.stream.getPosterCardImageUrl(any()) } returns null
          every { fakeRepositories.stream.getImageUrl(any(), any()) } returns null

          val viewModel = MovieDetailViewModel(
              repositories = fakeRepositories.coordinator,
              savedStateHandle = SavedStateHandle(mapOf("itemId" to movieId)),
          )
          advanceUntilIdle()

          val initialState = viewModel.uiState.value as MovieDetailUiState.Content
          assertFalse(initialState.movie.isWatched)

          // Now simulate returning from player with watched status
          coEvery { fakeRepositories.media.getMovieDetails(movieId) } returns ApiResult.Success(watchedDto)
          viewModel.refreshWatchStatus()
          advanceUntilIdle()

          // State must still be Content (no Loading flicker)
          val refreshedState = viewModel.uiState.value as MovieDetailUiState.Content
          assertTrue(refreshedState.movie.isWatched)
      }

      @Test
      fun refreshWatchStatus_whenStateIsLoading_doesNothing() = runTest {
          val movieId = UUID.randomUUID().toString()
          val fakeRepositories = FakeMovieDetailRepositories()

          // Never resolve so state stays Loading
          coEvery { fakeRepositories.media.getMovieDetails(movieId) } returns ApiResult.Error("never")
          coEvery { fakeRepositories.media.getSimilarMovies(movieId) } returns ApiResult.Error("never")

          val viewModel = MovieDetailViewModel(
              repositories = fakeRepositories.coordinator,
              savedStateHandle = SavedStateHandle(mapOf("itemId" to movieId)),
          )
          advanceUntilIdle() // load() completes with Error because getMovieDetails returns ApiResult.Error

          viewModel.refreshWatchStatus() // should no-op — state is Error, not Content
          advanceUntilIdle()

          // Still in error state (not a crash or state change)
          assertTrue(viewModel.uiState.value is MovieDetailUiState.Error)
      }
  }
  ```

- [ ] **Step 3: Run test to confirm it fails**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.MovieDetailViewModelTest"
  ```

  Expected: FAIL — `refreshWatchStatus` method does not exist yet.

- [ ] **Step 4: Add `refreshWatchStatus()` to `MovieDetailViewModel`**

  Add after the existing `load()` function:

  ```kotlin
  fun refreshWatchStatus() {
      val currentState = _uiState.value as? MovieDetailUiState.Content ?: return
      viewModelScope.launch {
          val result = repositories.media.getMovieDetails(movieId)
          if (result is ApiResult.Success) {
              val dto = result.data
              _uiState.value = currentState.copy(
                  movie = currentState.movie.copy(
                      isWatched = dto.isWatched(),
                      playbackProgress = if (dto.canResume()) (dto.getWatchedPercentage() / 100.0).toFloat() else null,
                  )
              )
          }
      }
  }
  ```

- [ ] **Step 5: Run test to confirm it passes**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.MovieDetailViewModelTest"
  ```

  Expected: all tests PASS.

### 3c: `EpisodeDetailViewModel.refreshWatchStatus()`

- [ ] **Step 6: Add `refreshWatchStatus()` to `EpisodeDetailViewModel`**

  Pattern identical to movie. Add after `load()`:

  ```kotlin
  fun refreshWatchStatus() {
      val currentState = _uiState.value as? EpisodeDetailUiState.Content ?: return
      viewModelScope.launch {
          val result = repositories.media.getEpisodeDetails(episodeId)
          if (result is ApiResult.Success) {
              val dto = result.data
              _uiState.value = currentState.copy(
                  episode = currentState.episode.copy(
                      isWatched = dto.isWatched(),
                      playbackProgress = if (dto.canResume()) (dto.getWatchedPercentage() / 100.0).toFloat() else null,
                  )
              )
          }
      }
  }
  ```

- [ ] **Step 7: Verify existing episode tests still pass**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.EpisodeDetailViewModelTest"
  ```

  Expected: PASS.

### 3d: `TvShowDetailViewModel.refreshWatchStatus()`

- [ ] **Step 8: Write the failing test for `TvShowDetailViewModel.refreshWatchStatus()`**

  Create `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt`:

  ```kotlin
  package com.rpeters.cinefintv.ui.screens.detail

  import androidx.lifecycle.SavedStateHandle
  import com.rpeters.cinefintv.data.repository.common.ApiResult
  import com.rpeters.cinefintv.testutil.FakeTvShowDetailRepositories
  import com.rpeters.cinefintv.testutil.MainDispatcherRule
  import io.mockk.coEvery
  import io.mockk.every
  import io.mockk.mockk
  import java.util.UUID
  import kotlinx.coroutines.ExperimentalCoroutinesApi
  import kotlinx.coroutines.test.advanceUntilIdle
  import kotlinx.coroutines.test.runTest
  import org.jellyfin.sdk.model.api.BaseItemDto
  import org.jellyfin.sdk.model.api.UserItemDataDto
  import org.junit.Assert.assertEquals
  import org.junit.Rule
  import org.junit.Test

  @OptIn(ExperimentalCoroutinesApi::class)
  class TvShowDetailViewModelTest {

      @get:Rule
      val mainDispatcherRule = MainDispatcherRule()

      private fun makeSeriesDto(id: String = UUID.randomUUID().toString()): BaseItemDto =
          mockk(relaxed = true) {
              every { this@mockk.id } returns java.util.UUID.fromString(id)
              every { people } returns emptyList()
              every { genres } returns emptyList()
              every { studios } returns emptyList()
              every { mediaSources } returns emptyList()
          }

      private fun makeSeasonDto(
          id: String = UUID.randomUUID().toString(),
          unwatchedCount: Int = 0,
      ): BaseItemDto = mockk(relaxed = true) {
          every { this@mockk.id } returns java.util.UUID.fromString(id)
          every { userData } returns mockk<UserItemDataDto>(relaxed = true) {
              every { unplayedItemCount } returns unwatchedCount
          }
      }

      @Test
      fun refreshWatchStatus_updatesSeasonUnwatchedCountsWithoutLoadingState() = runTest {
          val seriesId = UUID.randomUUID().toString()
          val seasonId = UUID.randomUUID().toString()
          val fakeRepositories = FakeTvShowDetailRepositories()

          val seriesDto = makeSeriesDto(id = seriesId)
          val seasonInitial = makeSeasonDto(id = seasonId, unwatchedCount = 3)
          val seasonRefreshed = makeSeasonDto(id = seasonId, unwatchedCount = 2)

          coEvery { fakeRepositories.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
          coEvery { fakeRepositories.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(seasonInitial))
          coEvery { fakeRepositories.media.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())
          coEvery { fakeRepositories.media.getNextUpForSeries(seriesId) } returns ApiResult.Success(null)
          every { fakeRepositories.stream.getBackdropUrl(any()) } returns null
          every { fakeRepositories.stream.getPosterCardImageUrl(any()) } returns null
          every { fakeRepositories.stream.getWideCardImageUrl(any()) } returns null
          every { fakeRepositories.stream.getImageUrl(any(), any()) } returns null

          val viewModel = TvShowDetailViewModel(
              repositories = fakeRepositories.coordinator,
              savedStateHandle = SavedStateHandle(mapOf("itemId" to seriesId)),
          )
          advanceUntilIdle()

          val initial = (viewModel.uiState.value as TvShowDetailUiState.Content)
          assertEquals(3, initial.seasons.first().unwatchedCount)

          // After watching an episode
          coEvery { fakeRepositories.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(seasonRefreshed))
          viewModel.refreshWatchStatus()
          advanceUntilIdle()

          val refreshed = viewModel.uiState.value as TvShowDetailUiState.Content
          assertEquals(2, refreshed.seasons.first().unwatchedCount)
      }
  }
  ```

- [ ] **Step 9: Run test to confirm it fails**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest"
  ```

  Expected: FAIL — `refreshWatchStatus` does not exist.

- [ ] **Step 10: Add `refreshWatchStatus()` to `TvShowDetailViewModel`**

  Add after the existing `load()` function:

  ```kotlin
  fun refreshWatchStatus() {
      val currentState = _uiState.value as? TvShowDetailUiState.Content ?: return
      viewModelScope.launch {
          val seasonsResult = repositories.media.getSeasonsForSeries(seriesId)
          if (seasonsResult is ApiResult.Success) {
              val updatedSeasons = seasonsResult.data.map { it.toSeasonModel() }
              _uiState.value = currentState.copy(seasons = updatedSeasons)
          }
      }
  }
  ```

- [ ] **Step 11: Run test to confirm it passes**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest"
  ```

  Expected: PASS.

### 3e: `HomeViewModel.refreshWatchStatus()`

- [ ] **Step 12: Write the failing test for `HomeViewModel.refreshWatchStatus()`**

  Add to the existing `HomeViewModelTest.kt`:

  ```kotlin
  @Test
  fun refreshWatchStatus_updatesContinueWatchingSectionWithoutLoadingState() = runTest {
      val fakeRepositories = FakeHomeRepositories()
      val movie1 = mockBaseItemDto("Movie 1")
      val movie2 = mockBaseItemDto("Movie 2 (new)")

      coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
      coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie1))
      coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), limit = any()) } returns ApiResult.Success(emptyList())
      every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "url"
      every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

      val viewModel = HomeViewModel(fakeRepositories.coordinator)
      advanceUntilIdle()

      val initialState = viewModel.uiState.value as HomeUiState.Content
      assertEquals("Movie 1", initialState.sections.first { it.title == "Continue Watching" }.items.first().title)

      // Simulate returning from player — continue watching updated
      coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie2))
      viewModel.refreshWatchStatus()
      advanceUntilIdle()

      // Must still be Content, not Loading
      val refreshedState = viewModel.uiState.value as HomeUiState.Content
      assertEquals("Movie 2 (new)", refreshedState.sections.first { it.title == "Continue Watching" }.items.first().title)
  }

  @Test
  fun refreshWatchStatus_whenContinueWatchingEmpty_removesContinueWatchingSection() = runTest {
      val fakeRepositories = FakeHomeRepositories()
      val movie = mockBaseItemDto("Movie 1")

      coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
      coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie))
      coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), limit = any()) } returns ApiResult.Success(emptyList())
      every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "url"
      every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

      val viewModel = HomeViewModel(fakeRepositories.coordinator)
      advanceUntilIdle()

      // Simulate finishing the last continue-watching item
      coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(emptyList())
      viewModel.refreshWatchStatus()
      advanceUntilIdle()

      val state = viewModel.uiState.value as HomeUiState.Content
      assertTrue(state.sections.none { it.title == "Continue Watching" })
  }
  ```

  **Before adding the new tests**, update the existing `mockBaseItemDto` helper at the bottom of `HomeViewModelTest.kt` to stub `mediaSources` and `seriesId`, which `toCardModel` accesses via `getMediaQualityLabel()`. Add these two lines to the helper:

  ```kotlin
  private fun mockBaseItemDto(name: String): BaseItemDto {
      val item: BaseItemDto = mockk()
      every { item.id } returns UUID.randomUUID()
      every { item.name } returns name
      every { item.type } returns BaseItemKind.MOVIE
      every { item.userData } returns null
      every { item.productionYear } returns null
      every { item.runTimeTicks } returns null
      every { item.overview } returns null
      every { item.communityRating } returns null
      every { item.officialRating } returns null
      every { item.collectionType } returns null
      every { item.mediaSources } returns null   // needed by getMediaQualityLabel()
      every { item.seriesId } returns null       // needed by toCardModel subtitle branch
      return item
  }
  ```

- [ ] **Step 13: Run tests to confirm they fail**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.home.HomeViewModelTest.refreshWatchStatus_updatesContinueWatchingSectionWithoutLoadingState" --tests "com.rpeters.cinefintv.ui.screens.home.HomeViewModelTest.refreshWatchStatus_whenContinueWatchingEmpty_removesContinueWatchingSection"
  ```

  Expected: FAIL — `refreshWatchStatus` does not exist.

- [ ] **Step 14: Add `refreshWatchStatus()` to `HomeViewModel`**

  Add after the existing `refresh()` function:

  ```kotlin
  fun refreshWatchStatus() {
      val currentState = _uiState.value as? HomeUiState.Content ?: return
      viewModelScope.launch {
          val continueResult = repositories.media.getContinueWatching(limit = 12)
          val nextEpisodeItems = buildNextEpisodeSectionItems(continueResult)

          val updatedSections = currentState.sections.toMutableList()

          // Update "Continue Watching" section
          val continueIdx = updatedSections.indexOfFirst { it.title == "Continue Watching" }
          if (continueResult is ApiResult.Success && continueResult.data.isNotEmpty()) {
              val newSection = HomeSectionModel(
                  title = "Continue Watching",
                  items = continueResult.data.take(12).map(::toCardModel),
              )
              if (continueIdx >= 0) updatedSections[continueIdx] = newSection
              else updatedSections.add(0, newSection)
          } else if (continueIdx >= 0) {
              updatedSections.removeAt(continueIdx)
          }

          // Update "Next Episodes" section
          val nextIdx = updatedSections.indexOfFirst { it.title == "Next Episodes" }
          if (nextEpisodeItems.isNotEmpty()) {
              val newSection = HomeSectionModel(title = "Next Episodes", items = nextEpisodeItems)
              if (nextIdx >= 0) updatedSections[nextIdx] = newSection
              else {
                  val insertAfter = updatedSections.indexOfFirst { it.title == "Continue Watching" }
                  updatedSections.add((insertAfter + 1).coerceAtLeast(0), newSection)
              }
          } else if (nextIdx >= 0) {
              updatedSections.removeAt(nextIdx)
          }

          _uiState.value = currentState.copy(sections = updatedSections)
      }
  }
  ```

- [ ] **Step 15: Run all new tests to confirm they pass**

  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.home.HomeViewModelTest"
  ```

  Expected: all PASS.

- [ ] **Step 16: Run full test suite**

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: all PASS.

- [ ] **Step 17: Commit**

  ```bash
  git add app/src/test/java/com/rpeters/cinefintv/testutil/FakeRepositories.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailViewModel.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt \
          app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModelTest.kt \
          app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt \
          app/src/test/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModelTest.kt
  git commit -m "feat: add refreshWatchStatus to detail and home ViewModels"
  ```

---

## Task 4: Watch Status — Lifecycle Observers in Screens

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

These are composable UI changes — no unit tests. Behavior is verified by running the app and watching status updating after playback.

The pattern to add to each screen's top-level composable (the one with `BackHandler` and the `viewModel` parameter):

```kotlin
val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
var hasBeenPaused by remember { mutableStateOf(false) }
androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
        when (event) {
            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                hasBeenPaused = false
                viewModel.refreshWatchStatus()
            }
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

Required new imports for screens that don't already have them:
- `androidx.compose.runtime.DisposableEffect`
- `androidx.compose.runtime.remember`
- `androidx.compose.runtime.mutableStateOf`
- `androidx.compose.runtime.getValue`
- `androidx.compose.runtime.setValue`
- `androidx.lifecycle.Lifecycle`
- `androidx.lifecycle.LifecycleEventObserver`
- `androidx.lifecycle.compose.LocalLifecycleOwner`

Check each file's existing imports and only add what's missing.

- [ ] **Step 1: Add lifecycle observer to `MovieDetailScreen`**

  In `MovieDetailScreen()` composable, add the observer block immediately after the `BackHandler` line (line 52):

  ```kotlin
  @Composable
  fun MovieDetailScreen(
      onPlayMovie: (String) -> Unit,
      onOpenMovie: (String) -> Unit,
      onBack: () -> Unit,
      viewModel: MovieDetailViewModel = hiltViewModel(),
  ) {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      BackHandler(onBack = onBack)

      val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
      var hasBeenPaused by remember { mutableStateOf(false) }
      DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
              when (event) {
                  Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
                  Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                      hasBeenPaused = false
                      viewModel.refreshWatchStatus()
                  }
                  else -> {}
              }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      Box(modifier = Modifier.fillMaxSize()) { ... }
  }
  ```

  Add these imports to the file (check what's missing):
  ```kotlin
  import androidx.compose.runtime.DisposableEffect
  import androidx.lifecycle.Lifecycle
  import androidx.lifecycle.LifecycleEventObserver
  import androidx.lifecycle.compose.LocalLifecycleOwner
  ```
  `remember`, `mutableStateOf`, `getValue`, `setValue` are all already imported in this file.

- [ ] **Step 2: Add lifecycle observer to `TvShowDetailScreen`**

  Same pattern — add after `BackHandler` in `TvShowDetailScreen()`. Add missing imports as needed.

- [ ] **Step 3: Add lifecycle observer to `EpisodeDetailScreen`**

  Same pattern — add after `BackHandler` in `EpisodeDetailScreen()`. `remember` and `mutableStateOf` are already imported.

- [ ] **Step 4: Add lifecycle observer to `HomeScreen`**

  The top-level composable is `HomeScreen()` at line 71 of `HomeScreen.kt`. It takes a `viewModel: HomeViewModel = hiltViewModel()` parameter. Add the same observer block immediately after the `val uiState by viewModel.uiState...` line, before the main content composable. There is no `BackHandler` in `HomeScreen`.

- [ ] **Step 5: Build**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL with no warnings about unresolved imports.

- [ ] **Step 6: Run full test suite**

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: all tests PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt \
          app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
  git commit -m "feat: refresh watch status on resume from player via lifecycle observers"
  ```

---

## Manual Verification Checklist

After all tasks are done, verify on device:

- [ ] Open a movie detail screen — text panel has dark glass background; title is white/light
- [ ] Open a TV show detail screen — same
- [ ] Open an episode detail screen — same
- [ ] Open the player, select a subtitle track — subtitles appear during playback
- [ ] Select "None" subtitles — subtitles disappear
- [ ] Watch a movie partially, press back — movie detail screen shows updated progress bar
- [ ] Watch a movie to completion (~95%+), press back — detail shows "Watched", home screen "Continue Watching" updates
- [ ] Watch a TV episode, press back through detail to home — home "Continue Watching" reflects new state
