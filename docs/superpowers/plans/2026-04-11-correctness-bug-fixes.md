# Correctness Bug Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 9 confirmed correctness bugs in the CinefinTV Android TV app, ordered by severity.

**Architecture:** All fixes are surgical — no new files except one extension function in `utils/Extensions.kt`. Each task is independently shippable. ViewModel bugs use TDD (write failing test → fix → verify pass). UI/navigation bugs are verified manually on device.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Hilt, MockK, Turbine, coroutines-test, Jellyfin SDK

---

## Files Modified

| File | Change |
|------|--------|
| `ui/screens/detail/TvShowDetailViewModel.kt` | Add RefreshItem ID guard, add pre-launch guard, remove `episodes` field |
| `ui/screens/detail/SeasonViewModel.kt` | Add RefreshItem ID guard, remove redundant `refreshWatchStatus()` call |
| `ui/screens/detail/TvShowDetailScreen.kt` | Add focus registration, fix `onPrimaryAction` after episodes removal |
| `ui/screens/detail/MovieDetailScreen.kt` | Add focus registration |
| `ui/screens/detail/SeasonScreen.kt` | Add focus registration |
| `ui/screens/detail/CollectionDetailScreen.kt` | Add focus registration |
| `ui/screens/person/PersonScreen.kt` | Add focus registration |
| `ui/screens/library/LibraryViewModel.kt` | Fix brace placement |
| `utils/Extensions.kt` | Add `MediaStream.getAudioLabel()` extension |
| `app/src/test/.../TvShowDetailViewModelTest.kt` | Add ID guard and pre-launch guard tests |
| `app/src/test/.../SeasonViewModelTest.kt` | Add ID guard test |

---

## Task 1: Add RefreshItem ID guard to TvShowDetailViewModel (Bug 1 — HIGH)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt:74-86`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt`

**Context:** `observeUpdateEvents()` (line 74) calls `refreshWatchStatus()` for every `RefreshItem` event regardless of which item changed. This causes 3 unnecessary network calls (series details, seasons, next-up) whenever any item in the app is updated. `MovieDetailViewModel:85` correctly guards with `if (event.itemId == movieId)`.

- [ ] **Step 1.1: Write failing test**

Open `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt` and add this test inside the `TvShowDetailViewModelTest` class:

```kotlin
@Test
fun observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork() = runTest {
    val fakeRepos = FakeTvShowDetailRepositories()
    val seriesId = UUID.randomUUID().toString()
    val foreignId = UUID.randomUUID().toString() // a DIFFERENT item's ID
    val seriesDto = makeSeriesDto(id = seriesId)
    val seasonDto = makeSeasonDto(unplayedCount = 1)

    coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
    coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(seasonDto))
    coEvery { fakeRepos.media.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())
    coEvery { fakeRepos.media.getNextUpForSeries(seriesId) } returns ApiResult.Error("none")
    every { fakeRepos.stream.getBackdropUrl(any()) } returns null
    every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
    every { fakeRepos.stream.getWideCardImageUrl(any()) } returns null
    every { fakeRepos.stream.getImageUrl(any(), any()) } returns null

    val vm = TvShowDetailViewModel(fakeRepos.coordinator, updateBus)
    vm.init(seriesId)
    advanceUntilIdle()

    // Clear invocation records so we can verify what happens AFTER init
    io.mockk.clearMocks(fakeRepos.media, answers = false)

    // Emit a RefreshItem for a DIFFERENT item (not this show)
    updateBus.refreshItem(foreignId)
    advanceUntilIdle()

    // No network calls should have been made for the show
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getSeriesDetails(any()) }
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getSeasonsForSeries(any()) }
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getNextUpForSeries(any()) }
}
```

- [ ] **Step 1.2: Run test to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest.observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `FAILED` — the test fails because `getSeriesDetails` IS called (no guard exists yet).

- [ ] **Step 1.3: Fix TvShowDetailViewModel — add ID guard**

In `TvShowDetailViewModel.kt`, find `observeUpdateEvents()` (around line 74). Change:

```kotlin
// BEFORE
private fun observeUpdateEvents() {
    viewModelScope.launch {
        updateBus.events.collect { event ->
            when (event) {
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                    refreshWatchStatus()
                }
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                    load(silent = true)
                }
            }
        }
    }
}
```

```kotlin
// AFTER
private fun observeUpdateEvents() {
    viewModelScope.launch {
        updateBus.events.collect { event ->
            when (event) {
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                    if (event.itemId == showId) {
                        refreshWatchStatus()
                    }
                }
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                    load(silent = true)
                }
            }
        }
    }
}
```

- [ ] **Step 1.4: Run test to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest.observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with 1 test passing.

- [ ] **Step 1.5: Run all TvShowDetailViewModel tests to check for regressions**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest" 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 1.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt
git commit -m "fix: only refresh show detail on RefreshItem for matching showId

Previously any RefreshItem event (from any item in the app) triggered
3 network calls on the show detail screen. Now correctly guards with
event.itemId == showId, matching the pattern in MovieDetailViewModel.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 2: Add RefreshItem ID guard to SeasonViewModel (Bug 1 — HIGH)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt:57-70`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModelTest.kt`

**Context:** Same bug as Task 1 but in `SeasonViewModel`. A `RefreshItem` for any item triggers `refreshWatchStatus()` which fetches all episodes for the season unnecessarily.

- [ ] **Step 2.1: Write failing test**

Open `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModelTest.kt` and add this test inside the `SeasonViewModelTest` class:

```kotlin
@Test
fun observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork() = runTest {
    val fakeRepos = FakeSeasonDetailRepositories()
    val seasonId = UUID.randomUUID().toString()
    val seriesId = UUID.randomUUID().toString()
    val foreignId = UUID.randomUUID().toString()
    val seasonDto = makeSeasonDto(id = seasonId, seriesId = seriesId)
    val seriesDto = makeSeriesDto(id = seriesId)
    val episodes = listOf(makeEpisodeDto())

    coEvery { fakeRepos.media.getItemDetails(seasonId) } returns ApiResult.Success(seasonDto)
    coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
    coEvery { fakeRepos.media.getEpisodesForSeason(seasonId) } returns ApiResult.Success(episodes)
    every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
    every { fakeRepos.stream.getBackdropUrlWithFallback(any(), any()) } returns null
    every { fakeRepos.stream.getBackdropUrl(any()) } returns null

    val vm = SeasonViewModel(fakeRepos.coordinator, updateBus)
    vm.init(seasonId)
    advanceUntilIdle()

    io.mockk.clearMocks(fakeRepos.media, answers = false)

    // Emit RefreshItem for a DIFFERENT item
    updateBus.refreshItem(foreignId)
    advanceUntilIdle()

    // No episode fetch should have happened
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getEpisodesForSeason(any()) }
}
```

- [ ] **Step 2.2: Run test to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.SeasonViewModelTest.observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `FAILED`.

- [ ] **Step 2.3: Fix SeasonViewModel — add ID guard**

In `SeasonViewModel.kt`, find `observeUpdateEvents()` (around line 57). Change:

```kotlin
// BEFORE
private fun observeUpdateEvents() {
    viewModelScope.launch {
        updateBus.events.collect { event ->
            when (event) {
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                    // Refresh watch status if any item was updated
                    refreshWatchStatus()
                }
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                    load(silent = true)
                }
            }
        }
    }
}
```

```kotlin
// AFTER
private fun observeUpdateEvents() {
    viewModelScope.launch {
        updateBus.events.collect { event ->
            when (event) {
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                    if (event.itemId == seasonId) {
                        refreshWatchStatus()
                    }
                }
                is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                    load(silent = true)
                }
            }
        }
    }
}
```

- [ ] **Step 2.4: Run test to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.SeasonViewModelTest.observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2.5: Run all SeasonViewModel tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.SeasonViewModelTest" 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 2.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModelTest.kt
git commit -m "fix: only refresh season detail on RefreshItem for matching seasonId

Same guard as the TvShowDetailViewModel fix — prevents unnecessary
episode fetches when unrelated items are updated elsewhere in the app.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 3: Add pre-launch guard to TvShowDetailViewModel.refreshWatchStatus (Bug 2 — MEDIUM)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt:89`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt`

**Context:** `refreshWatchStatus()` launches a coroutine and makes 3 network calls even when state is `Loading` or `Error`. `MovieDetailViewModel:204` and `SeasonViewModel:74` both return early with a Content guard before launching. This fix adds the same guard.

- [ ] **Step 3.1: Write failing test**

Add this test to `TvShowDetailViewModelTest`. The existing `refreshWatchStatus_whenNotContent_doesNothing` tests that state doesn't change, but doesn't verify that network calls are skipped. Add a new test that verifies no network call is made:

```kotlin
@Test
fun refreshWatchStatus_whenLoading_doesNotCallNetwork() = runTest {
    val fakeRepos = FakeTvShowDetailRepositories()
    val seriesId = UUID.randomUUID().toString()

    // Return error so state stays Loading after init attempt resolves to Error
    // Actually we want Loading state — call refreshWatchStatus before init
    val vm = TvShowDetailViewModel(fakeRepos.coordinator, updateBus)
    // State is Loading at construction, before init() is called
    assertTrue(vm.uiState.value is TvShowDetailUiState.Loading)

    vm.refreshWatchStatus()
    advanceUntilIdle()

    // State should still be Loading — no network calls should flip it
    assertTrue(vm.uiState.value is TvShowDetailUiState.Loading)
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getSeriesDetails(any()) }
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getSeasonsForSeries(any()) }
    io.mockk.coVerify(exactly = 0) { fakeRepos.media.getNextUpForSeries(any()) }
}
```

- [ ] **Step 3.2: Run test to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest.refreshWatchStatus_whenLoading_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `FAILED` — network calls ARE made because there is no guard.

- [ ] **Step 3.3: Fix TvShowDetailViewModel — add pre-launch guard**

In `TvShowDetailViewModel.kt`, find `refreshWatchStatus()` (around line 89). Add a guard as the very first line:

```kotlin
// BEFORE
fun refreshWatchStatus() {
    viewModelScope.launch {
        val showResult = repositories.media.getSeriesDetails(showId)
```

```kotlin
// AFTER
fun refreshWatchStatus() {
    _uiState.value as? TvShowDetailUiState.Content ?: return
    viewModelScope.launch {
        val showResult = repositories.media.getSeriesDetails(showId)
```

- [ ] **Step 3.4: Run test to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest.refreshWatchStatus_whenLoading_doesNotCallNetwork" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3.5: Run all TvShowDetailViewModel tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest" 2>&1 | tail -20
```

Expected: All tests pass (the existing `refreshWatchStatus_whenNotContent_doesNothing` should still pass).

- [ ] **Step 3.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModelTest.kt
git commit -m "fix: skip refreshWatchStatus network calls when not in Content state

Matches the guard pattern in MovieDetailViewModel and SeasonViewModel.
Previously TvShowDetailViewModel would always make 3 network calls even
when the UI state was Loading or Error.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 4: Extract audio codec logic to shared extension (Bug 3 — MEDIUM)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/utils/Extensions.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt:170-192`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt:177-200`

**Context:** Identical 20-line audio codec/channel formatting block is copy-pasted in both `MovieDetailViewModel.toDetailModel()` and `SeasonViewModel.toEpisodeModel()`. Extract to a single extension on `org.jellyfin.sdk.model.api.MediaStream`.

- [ ] **Step 4.1: Add the extension function to Extensions.kt**

Open `app/src/main/java/com/rpeters/cinefintv/utils/Extensions.kt`. At the **end of the file**, add:

```kotlin
/**
 * Formats the primary audio stream's codec and channel count into a display label.
 * Returns null if the stream has no meaningful codec or channel information.
 * Examples: "EAC3 5.1", "TrueHD 7.1", "AAC Stereo"
 */
fun org.jellyfin.sdk.model.api.MediaStream.getAudioLabel(): String? {
    val codec = when (codec?.uppercase()) {
        "EAC3", "E-AC3" -> "EAC3"
        "AC3" -> "AC3"
        "TRUEHD" -> "TrueHD"
        "DTS" -> "DTS"
        "AAC" -> "AAC"
        "FLAC" -> "FLAC"
        "OPUS" -> "Opus"
        else -> codec?.uppercase()
    }
    val channels = when (channels) {
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> channels?.let { "$it ch" }
    }
    return listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
}
```

- [ ] **Step 4.2: Replace the inline block in MovieDetailViewModel**

In `MovieDetailViewModel.kt`, find `toDetailModel()`. Replace the `audioLabel` assignment (lines 170-192):

```kotlin
// BEFORE
audioLabel = mediaSources
    ?.firstOrNull()
    ?.mediaStreams
    ?.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
    ?.firstOrNull()
    ?.let { stream ->
        val codec = when (stream.codec?.uppercase()) {
            "EAC3", "E-AC3" -> "EAC3"
            "AC3" -> "AC3"
            "TRUEHD" -> "TrueHD"
            "DTS" -> "DTS"
            "AAC" -> "AAC"
            "FLAC" -> "FLAC"
            "OPUS" -> "Opus"
            else -> stream.codec?.uppercase()
        }
        val channels = when (stream.channels) {
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> stream.channels?.let { "$it ch" }
        }
        listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
    },
```

```kotlin
// AFTER
audioLabel = mediaSources
    ?.firstOrNull()
    ?.mediaStreams
    ?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
    ?.getAudioLabel(),
```

- [ ] **Step 4.3: Replace the inline block in SeasonViewModel**

In `SeasonViewModel.kt`, find `toEpisodeModel()`. Replace the `audioLabel` assignment (lines 177-200):

```kotlin
// BEFORE
audioLabel = mediaSources
    ?.firstOrNull()
    ?.mediaStreams
    ?.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
    ?.firstOrNull()
    ?.let { stream ->
        val codec = when (stream.codec?.uppercase()) {
            "EAC3", "E-AC3" -> "EAC3"
            "AC3" -> "AC3"
            "TRUEHD" -> "TrueHD"
            "DTS" -> "DTS"
            "AAC" -> "AAC"
            "FLAC" -> "FLAC"
            "OPUS" -> "Opus"
            else -> stream.codec?.uppercase()
        }
        val channels = when (stream.channels) {
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> stream.channels?.let { "$it ch" }
        }
        listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
    },
```

```kotlin
// AFTER
audioLabel = mediaSources
    ?.firstOrNull()
    ?.mediaStreams
    ?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
    ?.getAudioLabel(),
```

- [ ] **Step 4.4: Verify build compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.5: Run all detail ViewModel tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.*" 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/utils/Extensions.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt
git commit -m "refactor: extract audio codec/channel formatting to MediaStream.getAudioLabel()

Eliminates 20-line copy-paste in MovieDetailViewModel and SeasonViewModel.
Single source of truth in utils/Extensions.kt.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 5: Register detail screens with AppChromeFocusController (Bug 4 — HIGH)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/CollectionDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/person/PersonScreen.kt`

**Context:** None of these screens call `rememberTopLevelDestinationFocus()`. Without it, the `AppChromeFocusController` doesn't know the primary content focus requester for these screens, so D-pad left from the content area does not navigate to the sidebar rail. The import is `com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus`.

The pattern used in `HomeScreen.kt` (the reference implementation):
```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryContentFocusRequester)
// Applied to the root scrollable container:
modifier = Modifier.then(destinationFocus.primaryContentModifier())
// Applied to left-edge items in horizontal rows:
modifier = Modifier.then(destinationFocus.drawerEscapeModifier(isLeftEdge = true))
```

`primaryContentModifier()` does two things: (1) registers the focus requester with the chrome controller so the scaffold can restore focus to it, and (2) sets `up`/`left` focus properties to go to the nav drawer. Apply it to the root `LazyColumn` / scrollable container.

**Sub-step 5a: MovieDetailScreen**

`MovieDetailScreen` already has `primaryActionFocus` (line 74) which is passed to `MovieDetailLayout` as `primaryActionFocusRequester`. Reuse it.

- [ ] **Step 5a.1: Add import to MovieDetailScreen.kt**

In `MovieDetailScreen.kt`, add this import after the existing imports:

```kotlin
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
```

- [ ] **Step 5a.2: Add destinationFocus declaration**

After the line `val primaryActionFocus = remember { FocusRequester() }` (line 74), add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocus)
```

- [ ] **Step 5a.3: Pass modifier to MovieDetailLayout**

In the `MovieDetailLayout(...)` call (around line 212), add a `modifier` parameter:

```kotlin
MovieDetailLayout(
    // ... all existing params ...
    listState = listState,
    modifier = Modifier.then(destinationFocus.primaryContentModifier()),
)
```

**Sub-step 5b: TvShowDetailScreen**

`TvShowDetailScreen` already has `primaryActionFocus` (line 76).

- [ ] **Step 5b.1: Add import to TvShowDetailScreen.kt**

```kotlin
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
```

- [ ] **Step 5b.2: Add destinationFocus declaration**

After `val primaryActionFocus = remember { FocusRequester() }` (line 76), add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocus)
```

- [ ] **Step 5b.3: Pass modifier to TvShowDetailLayout**

In the `TvShowDetailLayout(...)` call (around line 213), add:

```kotlin
TvShowDetailLayout(
    // ... all existing params ...
    listState = listState,
    modifier = Modifier.then(destinationFocus.primaryContentModifier()),
)
```

**Sub-step 5c: SeasonScreen**

The real content is in the private `SeasonContent` composable (line 101). It already has `primaryActionFocusRequester` at line 115. The `LazyColumn` is at line 214.

- [ ] **Step 5c.1: Add import to SeasonScreen.kt**

```kotlin
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
```

- [ ] **Step 5c.2: Add destinationFocus in SeasonContent**

In `SeasonContent` (line 101), after `val primaryActionFocusRequester = remember { FocusRequester() }` (line 115), add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocusRequester)
```

- [ ] **Step 5c.3: Apply modifier to SeasonContent's LazyColumn**

Find the `LazyColumn` in `SeasonContent` (line 214). Change:

```kotlin
// BEFORE
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = spacing.gutter * 2),
)
```

```kotlin
// AFTER
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .then(destinationFocus.primaryContentModifier()),
    contentPadding = PaddingValues(bottom = spacing.gutter * 2),
)
```

**Sub-step 5d: CollectionDetailScreen**

`CollectionDetailScreen` delegates to `CollectionFolderContent` and `CollectionVideoContent`. Both private composables already declare their own `primaryActionFocusRequester`. Wire focus registration inside each sub-composable independently (same as SeasonContent above — no need to pass from the parent).

- [ ] **Step 5d.1: Add import to CollectionDetailScreen.kt**

```kotlin
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
```

- [ ] **Step 5d.2: Wire focus in CollectionVideoContent**

`CollectionVideoContent` starts at line 111. After `val primaryActionFocusRequester = remember { FocusRequester() }` (line 117), add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocusRequester)
```

Then find the root scrollable container in `CollectionVideoContent` and apply `Modifier.then(destinationFocus.primaryContentModifier())` to it.

- [ ] **Step 5d.3: Wire focus in CollectionFolderContent**

Find `CollectionFolderContent` in the same file. Locate its `primaryActionFocusRequester` (or add one if absent). After it, add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocusRequester)
```

Apply `Modifier.then(destinationFocus.primaryContentModifier())` to its root scrollable container.

**Sub-step 5e: PersonScreen**

The `Content` branch starts at line 121. The existing `backButtonRequester` (line 124) is the top-left focusable item — use it as the primary content focus requester. The `LazyColumn` is at line 169.

- [ ] **Step 5e.1: Add import to PersonScreen.kt**

```kotlin
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
```

- [ ] **Step 5e.2: Add destinationFocus in the Content branch**

Inside the `is PersonUiState.Content -> {` branch (line 121), after `val backButtonRequester = remember { FocusRequester() }` (line 124), add:

```kotlin
val destinationFocus = rememberTopLevelDestinationFocus(backButtonRequester)
```

- [ ] **Step 5e.3: Apply modifier to PersonScreen's LazyColumn**

Find the `LazyColumn` in the Content branch (line 169). Change:

```kotlin
// BEFORE
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.safeZoneVertical),
    verticalArrangement = Arrangement.spacedBy(24.dp),
)
```

```kotlin
// AFTER
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .then(destinationFocus.primaryContentModifier()),
    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.safeZoneVertical),
    verticalArrangement = Arrangement.spacedBy(24.dp),
)
```

- [ ] **Step 5.4: Verify build compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.5: Manual test on device/emulator**

Install and verify D-pad left from each detail screen navigates focus to the sidebar rail.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Navigate to: Movie Detail → press D-pad left → sidebar should receive focus.
Repeat for: TV Show Detail, Season, Collection Detail, Person.

- [ ] **Step 5.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/CollectionDetailScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/person/PersonScreen.kt
git commit -m "fix: register all detail screens with AppChromeFocusController

D-pad left from Movie/Show/Season/Collection/Person detail screens now
correctly navigates focus to the sidebar nav rail. Applies
rememberTopLevelDestinationFocus() pattern that HomeScreen already uses.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 6: Verify BackHandler onBack lambda stability (Bug 5 — MEDIUM)

**Files:**
- Read only: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

**Context:** `BackHandler(onBack = onBack)` in detail screens is always-active. Confirming `onBack` is always a stable `backStack.pop()` call — if not, `enabled` parameter is required.

- [ ] **Step 6.1: Read NavGraph and check all detail route onBack lambdas**

Open `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`. Find all `onBack` arguments passed to detail screens:

- `MovieDetail` → `onBack = { backStack.pop() }` ✓
- `TvShowDetail` → `onBack = { backStack.pop() }` ✓
- `SeasonDetail` → `onBack = { backStack.pop() }` ✓
- `CollectionDetail` → `onBack = { backStack.pop() }` ✓
- `PersonDetail` → `onBack = { backStack.pop() }` ✓

`backStack.pop()` is defined as a local extension (line 309): `if (size > 1) removeAt(size - 1)`. This is safe — it never blocks and the navDisplay's own `onBack` at line 299 also calls `backStack.pop()`, so there is no conflict.

**Result: No code change needed.** All `onBack` lambdas are stable nav pop calls.

- [ ] **Step 6.2: No commit needed** — verification complete.

---

## Task 7: Remove dead episodes field from TvShowDetailUiState.Content (Bug 6 — MEDIUM)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt:226`

**Context:** `episodes: List<EpisodeModel>` in `TvShowDetailUiState.Content` is always `emptyList()`. It's a dead field. One caller exists in `TvShowDetailScreen:226`: `state.episodes.firstOrNull()?.let { onPlayEpisode(it.id) }` — since this always returns null, the expression always falls through to `state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }`. Removing the field simplifies the data model and eliminates the silent stub.

- [ ] **Step 7.1: Remove episodes field from TvShowDetailUiState.Content**

In `TvShowDetailViewModel.kt`, find `TvShowDetailUiState.Content`:

```kotlin
// BEFORE
data class Content(
    val show: TvShowDetailModel,
    val seasons: List<SeasonModel>,
    val episodes: List<EpisodeModel>,
    val cast: List<CastModel>,
    val similarShows: List<SimilarMovieModel>,
) : TvShowDetailUiState()
```

```kotlin
// AFTER
data class Content(
    val show: TvShowDetailModel,
    val seasons: List<SeasonModel>,
    val cast: List<CastModel>,
    val similarShows: List<SimilarMovieModel>,
) : TvShowDetailUiState()
```

- [ ] **Step 7.2: Remove the episodes local variable from load()**

In `TvShowDetailViewModel.load()`, remove the line:

```kotlin
val episodes = emptyList<EpisodeModel>()
```

And remove `episodes = episodes,` from the `_uiState.value = TvShowDetailUiState.Content(...)` call.

```kotlin
// BEFORE
_uiState.value = TvShowDetailUiState.Content(
    show = showDto.toDetailModel(nextUpDto),
    seasons = seasons,
    episodes = episodes,
    cast = cast,
    similarShows = similar
)
```

```kotlin
// AFTER
_uiState.value = TvShowDetailUiState.Content(
    show = showDto.toDetailModel(nextUpDto),
    seasons = seasons,
    cast = cast,
    similarShows = similar
)
```

- [ ] **Step 7.3: Fix TvShowDetailScreen — remove state.episodes reference**

In `TvShowDetailScreen.kt`, find `onPrimaryAction` around line 222. Change:

```kotlin
// BEFORE
onPrimaryAction = {
    if (show.nextUpEpisodeId != null) {
        onPlayEpisode(show.nextUpEpisodeId)
    } else {
        state.episodes.firstOrNull()?.let { onPlayEpisode(it.id) }
            ?: state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }
    }
},
```

```kotlin
// AFTER
onPrimaryAction = {
    if (show.nextUpEpisodeId != null) {
        onPlayEpisode(show.nextUpEpisodeId)
    } else {
        state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }
    }
},
```

- [ ] **Step 7.4: Verify build compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7.5: Run all TvShowDetailViewModel tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest" 2>&1 | tail -20
```

Expected: All tests pass. (Tests that construct `TvShowDetailUiState.Content(...)` directly in test code will need to have `episodes = ...` removed too — fix any compilation errors in test files.)

- [ ] **Step 7.6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt
git commit -m "fix: remove dead episodes field from TvShowDetailUiState.Content

The field was always emptyList() — a silent stub. Removed the field and
simplified onPrimaryAction in TvShowDetailScreen to fall through directly
to seasons when no nextUpEpisodeId is set.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 8: Fix CollectionLibraryViewModel brace placement (Bug 7 — LOW)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModel.kt:110-112`

**Context:** The class body `{` is on a line separated from the superclass call by a blank line (line 112). Valid Kotlin but misleading and inconsistent with the rest of the codebase.

- [ ] **Step 8.1: Move brace to end of superclass call line**

In `LibraryViewModel.kt`, find lines 107-113:

```kotlin
// BEFORE
@HiltViewModel
class CollectionLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
) : BaseLibraryViewModel(repositories, updateBus, listOf(BaseItemKind.VIDEO, BaseItemKind.COLLECTION_FOLDER))

{
    fun markWatched(itemId: String, onComplete: (() -> Unit)? = null) {
```

```kotlin
// AFTER
@HiltViewModel
class CollectionLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
) : BaseLibraryViewModel(repositories, updateBus, listOf(BaseItemKind.VIDEO, BaseItemKind.COLLECTION_FOLDER)) {
    fun markWatched(itemId: String, onComplete: (() -> Unit)? = null) {
```

- [ ] **Step 8.2: Verify build compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8.3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModel.kt
git commit -m "style: move CollectionLibraryViewModel class body brace to standard position

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 9: Remove redundant refreshWatchStatus from deleteEpisode (Bug 8 — LOW)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt:104-110`

**Context:** `deleteEpisode()` calls both `updateBus.refreshAll()` and `refreshWatchStatus()`. `updateBus.refreshAll()` already triggers `load(silent = true)` via `observeUpdateEvents()`. The extra `refreshWatchStatus()` call launches a competing coroutine that writes to `_uiState` concurrently with the full reload. After deletion the deleted episode no longer exists, making the episode fetch semantically wrong.

- [ ] **Step 9.1: Remove the redundant call**

In `SeasonViewModel.kt`, find `deleteEpisode()`:

```kotlin
// BEFORE
fun deleteEpisode(episodeId: String) {
    viewModelScope.launch {
        if (repositories.user.deleteItemAsAdmin(episodeId) is ApiResult.Success) {
            updateBus.refreshAll()
            refreshWatchStatus()
        }
    }
}
```

```kotlin
// AFTER
fun deleteEpisode(episodeId: String) {
    viewModelScope.launch {
        if (repositories.user.deleteItemAsAdmin(episodeId) is ApiResult.Success) {
            updateBus.refreshAll()
        }
    }
}
```

- [ ] **Step 9.2: Verify build and tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.SeasonViewModelTest" 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 9.3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt
git commit -m "fix: remove redundant refreshWatchStatus() call from deleteEpisode

updateBus.refreshAll() already triggers load(silent=true) via the event
observer. The extra refreshWatchStatus() was racy and semantically wrong
(it fetches the just-deleted episode).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Task 10: Remove unused SavedStateHandle imports (Bug 9 — LOW)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt:3`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt:3`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt:3`

**Context:** All three files import `androidx.lifecycle.SavedStateHandle` but never reference it.

- [ ] **Step 10.1: Remove unused import from MovieDetailViewModel.kt**

Delete the line:
```kotlin
import androidx.lifecycle.SavedStateHandle
```

- [ ] **Step 10.2: Remove unused import from TvShowDetailViewModel.kt**

Delete the line:
```kotlin
import androidx.lifecycle.SavedStateHandle
```

- [ ] **Step 10.3: Remove unused import from SeasonViewModel.kt**

Delete the line:
```kotlin
import androidx.lifecycle.SavedStateHandle
```

- [ ] **Step 10.4: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10.5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonViewModel.kt
git commit -m "chore: remove unused SavedStateHandle imports from detail ViewModels

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Final Verification

- [ ] **Run full unit test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -30
```

Expected: All tests pass with no failures.

- [ ] **Run lint**

```bash
./gradlew :app:lintDebug 2>&1 | grep -E "Error|Warning" | head -30
```

Review any new warnings introduced by these changes.

- [ ] **Build release-equivalent APK**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.
