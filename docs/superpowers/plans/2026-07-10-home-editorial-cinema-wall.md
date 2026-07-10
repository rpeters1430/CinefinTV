# Home Editorial Cinema Wall Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the CinefinTV Home screen into a premium Editorial Cinema Wall while preserving the existing Android TV D-pad focus contract.

**Architecture:** Keep `HomeScreenContent` as the test-facing entry point and preserve the existing `HomeUiState.Content` data flow. Implement behavior changes test-first, then extract focused private composables from `HomeScreen.kt` only where the redesign touches the code. The discovery strip is visual context only and must not add focus targets.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX TV Material 3, Coil 3, JUnit 4, Android Compose UI tests, Gradle wrapper.

## Global Constraints

- No repository API changes.
- No new Home route types.
- No "Show all" shelf navigation in this pass.
- No focusable discovery strip in this pass.
- No player behavior changes.
- No broad theme overhaul.
- No performance-heavy artwork analysis or dynamic palette extraction.
- Preserve initial hero focus, hero-to-shelf D-pad down, shelf-to-hero D-pad up, chrome escape, player-return focus, and equivalent-refresh focus behavior.

---

## File Structure

- Modify `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt`: add behavior tests for progress-aware hero CTA and discovery strip rendering.
- Modify `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeTestTags.kt`: add stable test tags for the discovery strip.
- Modify `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`: implement hero CTA copy, add the discovery strip, and extract focused private composables.

No new production files are required for the first pass. If `HomeScreen.kt` becomes awkward during execution, extracting `HomeHero.kt` is acceptable, but the default plan keeps changes local.

---

### Task 1: Progress-Aware Hero CTA

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeCardModel.playbackProgress: Float?`
- Produces: Hero primary CTA text is `Resume` when `playbackProgress != null && playbackProgress > 0f`, otherwise `Play`.

- [ ] **Step 1: Write the failing CTA tests**

Add these tests inside `HomeScreenUiTest`:

```kotlin
@Test
fun featuredPrimaryAction_showsResume_whenPlaybackProgressExists() {
    composeRule.setContent {
        HomeTestHost {
            HomeScreenContent(
                uiState = sampleContentState(
                    featuredItems = listOf(
                        sampleCard(
                            id = "featured-progress",
                            title = "In Progress",
                            playbackProgress = 0.42f,
                        )
                    )
                ),
                onOpenItem = {},
                onPlayItem = {},
                onOpenSeries = {},
                onOpenSeason = {},
                onRetry = {},
                shouldRestoreFocusOnResume = false,
                onConsumedRestore = {},
            )
        }
    }

    composeRule.onNodeWithText("Resume").assertIsDisplayed()
}

@Test
fun featuredPrimaryAction_showsPlay_whenPlaybackProgressIsMissing() {
    composeRule.setContent {
        HomeTestHost {
            HomeScreenContent(
                uiState = sampleContentState(
                    featuredItems = listOf(
                        sampleCard(
                            id = "featured-new",
                            title = "New Feature",
                            playbackProgress = null,
                        )
                    )
                ),
                onOpenItem = {},
                onPlayItem = {},
                onOpenSeries = {},
                onOpenSeason = {},
                onRetry = {},
                shouldRestoreFocusOnResume = false,
                onConsumedRestore = {},
            )
        }
    }

    composeRule.onNodeWithText("Play").assertIsDisplayed()
}
```

Update the existing test helper signature at the bottom of `HomeScreenUiTest`:

```kotlin
private fun sampleCard(
    id: String,
    title: String,
    subtitle: String? = "2026",
    description: String? = "Sample description",
    mediaQuality: String? = "4K HDR",
    playbackProgress: Float? = null,
): HomeCardModel = HomeCardModel(
    id = id,
    title = title,
    subtitle = subtitle,
    imageUrl = null,
    backdropUrl = null,
    description = description,
    year = 2026,
    runtime = "1h 40m",
    rating = "8.2",
    officialRating = "PG-13",
    itemType = "Movie",
    collectionType = "movies",
    watchStatus = WatchStatus.NONE,
    playbackProgress = playbackProgress,
    unwatchedCount = null,
    mediaQuality = mediaQuality,
)
```

- [ ] **Step 2: Run the new tests and verify RED**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest
```

Expected: `featuredPrimaryAction_showsResume_whenPlaybackProgressExists` fails because the hero still renders `Play`.

- [ ] **Step 3: Implement minimal CTA behavior**

In `HeroItem` in `HomeScreen.kt`, add:

```kotlin
val playActionLabel = remember(item.playbackProgress) {
    if ((item.playbackProgress ?: 0f) > 0f) "Resume" else "Play"
}
```

Replace the primary button text:

```kotlin
Text(
    playActionLabel,
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
)
```

- [ ] **Step 4: Run the CTA tests and verify GREEN**

Run the same command from Step 2.

Expected: both CTA tests pass.

- [ ] **Step 5: Run related existing hero focus test**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest#featuredPlayDown_movesFocusToFirstSectionItem
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "feat: make home hero action progress aware"
```

---

### Task 2: Editorial Discovery Strip

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeTestTags.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeUiState.Content.sections: List<HomeSectionModel>`
- Produces: `HomeDiscoveryStrip(sections: List<HomeSectionModel>, modifier: Modifier = Modifier)` private composable.

- [ ] **Step 1: Write the failing discovery strip test**

Add this test inside `HomeScreenUiTest`:

```kotlin
@Test
fun contentState_discoveryStrip_rendersPrioritySections() {
    composeRule.setContent {
        HomeTestHost {
            HomeScreenContent(
                uiState = HomeUiState.Content(
                    featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One")),
                    sections = listOf(
                        HomeSectionModel(
                            id = HomeSectionId.CONTINUE_WATCHING,
                            title = "Continue Watching",
                            items = listOf(sampleCard(id = "resume-1", title = "Resume One")),
                        ),
                        HomeSectionModel(
                            id = HomeSectionId.NEXT_EPISODES,
                            title = "Next Episodes",
                            items = listOf(sampleCard(id = "episode-1", title = "Episode One")),
                        ),
                        HomeSectionModel(
                            id = HomeSectionId.RECENT_MOVIES,
                            title = "Recently Added Movies",
                            items = listOf(sampleCard(id = "movie-1", title = "Movie One")),
                        ),
                    ),
                ),
                onOpenItem = {},
                onPlayItem = {},
                onOpenSeries = {},
                onOpenSeason = {},
                onRetry = {},
                shouldRestoreFocusOnResume = false,
                onConsumedRestore = {},
            )
        }
    }

    composeRule.onNodeWithTag("home_discovery_strip").assertIsDisplayed()
    composeRule.onNodeWithText("Continue Watching").assertIsDisplayed()
    composeRule.onNodeWithText("Next Episodes").assertIsDisplayed()
    composeRule.onNodeWithText("Recently Added Movies").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest#contentState_discoveryStrip_rendersPrioritySections
```

Expected: FAIL because `home_discovery_strip` does not exist.

- [ ] **Step 3: Add stable test tags**

In `HomeTestTags.kt`, add:

```kotlin
const val DiscoveryStrip = "home_discovery_strip"
fun discoveryStripItem(index: Int): String = "home_discovery_strip_item_$index"
```

- [ ] **Step 4: Thread sections into the hero**

In `HomeLoadedContent`, update the `FeaturedCarousel` call to pass sections:

```kotlin
FeaturedCarousel(
    items = state.featuredItems,
    sections = state.sections,
    onMoreInfo = onOpenItem,
    onPlay = onPlayItem,
    onItemFocused = { focusedItem = it },
    destinationFocus = destinationFocus,
    primaryActionFocusRequester = featuredPrimaryActionRequester,
    downRequester = firstSectionRequester,
    onNavigateDown = firstSectionRequester?.let { requester ->
        requestFocusAtListIndex(requester = requester, listIndex = 1)
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 0.dp),
)
```

Update `FeaturedCarousel` signature:

```kotlin
private fun FeaturedCarousel(
    items: List<HomeCardModel>,
    sections: List<HomeSectionModel>,
    onMoreInfo: (HomeCardModel) -> Unit,
    onPlay: (String) -> Unit,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    onItemFocused: (HomeCardModel) -> Unit,
    downRequester: FocusRequester?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
)
```

Update the `HeroItem` call inside `FeaturedCarousel`:

```kotlin
HeroItem(
    item = item,
    sections = sections,
    onMoreInfo = { onMoreInfo(item) },
    onPlay = { onPlay(item.id) },
    destinationFocus = destinationFocus,
    primaryActionFocusRequester = if (index == carouselState.activeItemIndex) {
        primaryActionFocusRequester
    } else {
        fallbackRequester
    },
    downRequester = downRequester,
    modifier = Modifier.fillMaxSize(),
)
```

Update `HeroItem` signature:

```kotlin
private fun HeroItem(
    item: HomeCardModel,
    sections: List<HomeSectionModel>,
    onMoreInfo: () -> Unit,
    onPlay: () -> Unit,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    downRequester: FocusRequester?,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 5: Implement `HomeDiscoveryStrip`**

Add this private composable below `HeroItem`:

```kotlin
@Composable
private fun HomeDiscoveryStrip(
    sections: List<HomeSectionModel>,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val prioritySections = remember(sections) {
        val priority = listOf(
            HomeSectionId.CONTINUE_WATCHING,
            HomeSectionId.NEXT_EPISODES,
            HomeSectionId.RECENT_MOVIES,
            HomeSectionId.RECENT_EPISODES,
            HomeSectionId.LIBRARIES,
        )
        priority.mapNotNull { id -> sections.firstOrNull { it.id == id } }
            .take(3)
    }

    if (prioritySections.isEmpty()) return

    Row(
        modifier = modifier
            .testTag(HomeTestTags.DiscoveryStrip),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prioritySections.forEachIndexed { index, section ->
            Column(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(HomeTestTags.discoveryStripItem(index)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${section.items.size} ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = expressiveColors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

Add this inside the `Column` in `HeroItem`, after the button `Row`:

```kotlin
HomeDiscoveryStrip(
    sections = sections,
    modifier = Modifier.padding(top = 8.dp),
)
```

- [ ] **Step 6: Run the discovery strip test and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 7: Run hero navigation tests**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest#featuredPlayDown_movesFocusToFirstSectionItem
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeTestTags.kt app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "feat: add home editorial discovery strip"
```

---

### Task 3: Extract Hero Metadata And Shelf Components

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/home/HomeScreenUiTest.kt`

**Interfaces:**
- Produces: `HomeHeroMetadata(item: HomeCardModel, modifier: Modifier = Modifier)` private composable.
- Produces: `HomeShelf(...)` private composable replacing the current `HomeSection(...)` call and body.

- [ ] **Step 1: Run current Home UI tests as refactor baseline**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest
```

Expected: PASS before refactoring.

- [ ] **Step 2: Extract `HomeHeroMetadata`**

In `HeroItem`, move the existing metadata creation and metadata `Text` rendering into this private composable:

```kotlin
@Composable
private fun HomeHeroMetadata(
    item: HomeCardModel,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(item.year, item.runtime, item.rating, item.itemType) {
        listOfNotNull(
            item.year?.toString(),
            item.runtime,
            item.rating?.let { "★ $it" },
            item.itemType,
        ).joinToString("  •  ")
    }

    if (metadata.isNotBlank()) {
        Text(
            text = metadata,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

Replace the old metadata `Text` block in `HeroItem` with:

```kotlin
HomeHeroMetadata(item = item)
```

- [ ] **Step 3: Rename/extract `HomeSection` to `HomeShelf`**

Rename:

```kotlin
private fun HomeSection(
```

to:

```kotlin
private fun HomeShelf(
```

Update the call site in `HomeLoadedContent`:

```kotlin
HomeShelf(
    sectionIndex = index,
    title = section.title,
    items = section.items,
    cardWidth = cardWidth,
    rowSpacing = rowSpacing,
    onOpenItem = onOpenItem,
    restoredFocusedItemId = if (section.id == lastFocusedSectionId) lastFocusedItemId else null,
    onItemFocused = { item ->
        lastFocusedSectionId = section.id
        lastFocusedItemId = item.id
        focusedItem = item
        ensureSectionVisible(index)
    },
    onEpisodeMenuRequested = { selectedEpisodeMenuItem = it },
    itemFocusRequesters = sectionItemFocusRequesters.getOrNull(index).orEmpty(),
    upFocusRequester = upFocusRequester,
    downFocusRequester = downFocusRequester,
    onNavigateUp = onNavigateUp,
    onNavigateDown = onNavigateDown,
    destinationFocus = destinationFocus,
)
```

- [ ] **Step 4: Run Home UI tests after extraction**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "refactor: extract home hero and shelf pieces"
```

---

### Task 4: Full Verification

**Files:**
- Verify only.

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: verified debug build and test result.

- [ ] **Step 1: Run JVM unit tests**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Home UI tests**

Run:

```bash
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.screens.home.HomeScreenUiTest
```

Expected: BUILD SUCCESSFUL and all `HomeScreenUiTest` tests pass.

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Check git status**

Run:

```bash
git status --short
```

Expected: clean working tree after commits.

---

## Self-Review

Spec coverage:

- Premium Editorial Cinema Wall: Tasks 1 and 2 implement the progress-aware hero and editorial discovery strip.
- Current data flow preserved: Tasks use `HomeUiState.Content`, `HomeCardModel`, and `HomeSectionModel` only.
- Focus contract preserved: Tasks 1, 2, and 3 run existing Home focus/navigation UI tests.
- No focusable discovery strip: Task 2 renders only non-clickable `Row`/`Column`/`Text`.
- Component extraction: Task 3 extracts hero metadata and shelf naming without broad architecture changes.
- Verification: Task 4 runs unit tests, Home UI tests, and debug build.

Placeholder scan: no TBD/TODO placeholders remain.

Type consistency: `HomeDiscoveryStrip`, `HomeHeroMetadata`, `HomeShelf`, `HomeTestTags.DiscoveryStrip`, and `HomeTestTags.discoveryStripItem(index)` are defined before later references rely on them.
