# Detail Screen D-Pad Navigation Tests — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 19 instrumented UI tests that verify D-pad focus routing through the Movie and TV Show detail screens, covering every explicit `focusProperties` branch in both layouts.

**Architecture:** Production code first (tag moves + new tags), then shared test helpers extracted from the existing test file, then the new navigation test class. All tests are instrumented (on-device) using the Compose testing framework with D-pad key simulation and `assertIsFocused()` assertions.

**Tech Stack:** Kotlin, Compose UI Testing (`createAndroidComposeRule`), JUnit4, AndroidJUnit4 runner, `androidx.compose.ui.test.performKeyInput`, `androidx.compose.ui.input.key.Key`, MockK (not needed here)

---

## Files Changed / Created

| Action | File |
|--------|------|
| Modify | `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailOverviewSection.kt` |
| Modify | `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailTestTags.kt` |
| Modify | `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/MovieDetailLayout.kt` |
| Modify | `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/TvShowDetailLayout.kt` |
| Create | `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailTestHelpers.kt` |
| Modify | `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailFocusUiTest.kt` |
| Create | `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailDpadNavigationUiTest.kt` |

---

## Task 1: Move `Overview` tag to the focusable outer Box

`DetailTestTags.Overview` currently sits on an inner `Column` (line 118). `assertIsFocused()` requires the tag on the node that holds focus — which is the outer `Box` with `.focusable()` (line 64). Moving it makes all future Overview focus assertions reliable.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailOverviewSection.kt:64-95`

- [ ] **Step 1: Move the testTag modifier**

In `DetailOverviewSection.kt`, find the outer `Box` modifier chain starting at line 64. Add `.testTag(DetailTestTags.Overview)` as the **first** modifier after `modifier` (before `.padding`, `.focusRequester`, `.focusable`). Remove the `.testTag(DetailTestTags.Overview)` from the inner `Column` at line 118.

Before (line 64–95):
```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.gutter)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusProperties { ... }
        .onFocusChanged { ... }
        .focusable()
        .background(...)
        .border(...)
        .padding(spacing.gutter),
) {
    Row(...) {
        Column(
            modifier = Modifier.weight(1f),
            ...
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DetailTestTags.Overview)   // ← remove from here
```

After — outer Box:
```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .testTag(DetailTestTags.Overview)               // ← add here
        .padding(horizontal = spacing.gutter)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusProperties { ... }
        .onFocusChanged { ... }
        .focusable()
        .background(...)
        .border(...)
        .padding(spacing.gutter),
```

Inner Column — remove the tag:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        // .testTag(DetailTestTags.Overview)  ← deleted
        .background(...)
```

- [ ] **Step 2: Verify the existing DetailFocusUiTest still compiles and the Overview assertions still pass**

Run:
```bash
./gradlew :app:compileDebugAndroidTestKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailOverviewSection.kt
git commit -m "fix(test): move Overview test tag to focusable outer Box in DetailOverviewSection"
```

---

## Task 2: Add first-row test tags

Three new tags and their application sites in both layouts. Tags are placed **inside** the first-item conditional blocks only — other items in the same row must not carry the tag.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailTestTags.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/MovieDetailLayout.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/TvShowDetailLayout.kt`

- [ ] **Step 1: Add the three tag constants to `DetailTestTags.kt`**

```kotlin
object DetailTestTags {
    const val PrimaryAction = "detail_primary_action"
    const val Overview = "detail_overview"
    const val HeroTitle = "detail_hero_title"
    const val HeroLogo = "detail_hero_logo"
    const val MovieCastSection = "movie_detail_cast_section"
    const val MovieSimilarSection = "movie_detail_similar_section"
    const val TvEpisodesPanel = "tv_detail_episodes_panel"
    const val TvCastPanel = "tv_detail_cast_panel"
    const val TvSimilarPanel = "tv_detail_similar_panel"
    const val TvDetailsPanel = "tv_detail_details_panel"
    // First-item tags for D-pad navigation tests
    const val FirstCastItem = "detail_first_cast_item"
    const val FirstSimilarItem = "detail_first_similar_item"
    const val FirstSeasonItem = "detail_first_season_item"
}
```

- [ ] **Step 2: Apply `FirstCastItem` and `FirstSimilarItem` tags in `MovieDetailLayout.kt`**

Find the cast items block (around line 133). Add `.testTag(DetailTestTags.FirstCastItem)` to the first-item modifier branch only:

```kotlin
modifier = if (person.id == castItems.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstCastFocusRequester)
        .focusProperties { up = overviewFocusRequester }
        .testTag(DetailTestTags.FirstCastItem)      // add this line
} else {
    Modifier
},
```

Find the similar items block (around line 171). Add `.testTag(DetailTestTags.FirstSimilarItem)` to the first-item modifier branch only:

```kotlin
modifier = if (mediaItem.id == similarItems.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstSimilarFocusRequester)
        .focusProperties {
            up = if (castItems.isNotEmpty()) {
                firstCastFocusRequester
            } else {
                overviewFocusRequester
            }
        }
        .testTag(DetailTestTags.FirstSimilarItem)   // add this line
} else {
    Modifier
},
```

- [ ] **Step 3: Apply `FirstSeasonItem`, `FirstCastItem`, and `FirstSimilarItem` tags in `TvShowDetailLayout.kt`**

Seasons block (around line 140):
```kotlin
modifier = if (season.id == seasons.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstSeasonFocusRequester)
        .focusProperties { up = overviewFocusRequester }
        .testTag(DetailTestTags.FirstSeasonItem)    // add this line
} else {
    Modifier
},
```

Cast block (around line 178):
```kotlin
modifier = if (person.id == castItems.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstCastFocusRequester)
        .focusProperties {
            up = if (seasons.isNotEmpty()) {
                firstSeasonFocusRequester
            } else {
                overviewFocusRequester
            }
        }
        .testTag(DetailTestTags.FirstCastItem)      // add this line
} else {
    Modifier
},
```

Similar block (around line 222):
```kotlin
modifier = if (item.id == similarItems.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstSimilarFocusRequester)
        .focusProperties {
            up = when {
                castItems.isNotEmpty() -> firstCastFocusRequester
                seasons.isNotEmpty() -> firstSeasonFocusRequester
                else -> overviewFocusRequester
            }
        }
        .testTag(DetailTestTags.FirstSimilarItem)   // add this line
} else {
    Modifier
},
```

- [ ] **Step 4: Compile to verify no errors**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/DetailTestTags.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/MovieDetailLayout.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/TvShowDetailLayout.kt
git commit -m "feat(test): add FirstCastItem, FirstSimilarItem, FirstSeasonItem test tags to detail layouts"
```

---

## Task 3: Extract shared test helpers to `DetailTestHelpers.kt`

`DetailTestHost` and `requestFocus()` are `private` in `DetailFocusUiTest.kt`. Both the existing file and the new navigation test file need them. Extract to a shared `internal` helper file in the same package.

**Files:**
- Create: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailTestHelpers.kt`
- Modify: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailFocusUiTest.kt`

- [ ] **Step 1: Create `DetailTestHelpers.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController

internal fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
internal fun DetailTestHost(content: @Composable () -> Unit) {
    CinefinTvTheme(useDynamicColors = false) {
        CompositionLocalProvider(
            LocalCinefinThemeController provides object : ThemeColorController {
                override fun updateSeedColor(color: Color?) = Unit
            }
        ) {
            content()
        }
    }
}
```

- [ ] **Step 2: Remove the private copies from `DetailFocusUiTest.kt`**

Delete lines 243–257 (the private `requestFocus()` extension and private `DetailTestHost` composable). The file will now use the `internal` versions from `DetailTestHelpers.kt` automatically since they are in the same package.

- [ ] **Step 3: Compile the test sources**

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```
Expected: `BUILD SUCCESSFUL` with no unresolved reference errors.

- [ ] **Step 4: Commit**
```bash
git add app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailTestHelpers.kt \
        app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailFocusUiTest.kt
git commit -m "refactor(test): extract DetailTestHost and requestFocus to shared DetailTestHelpers"
```

---

## Task 4: Write Movie D-pad navigation tests

All 9 Movie tests in the new file. Note: `MovieDetailLayout` takes `factSummary: String` — `TvShowDetailLayout` does not. Create the file and write the Movie section.

**Files:**
- Create: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailDpadNavigationUiTest.kt`

- [ ] **Step 1: Create the file with Movie tests**

```kotlin
package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailDpadNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // MovieDetailLayout — focus routing tests
    // -------------------------------------------------------------------------

    @Test
    fun movieDetail_onLoad_playButtonHasFocus() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
    }

    @Test
    fun movieDetail_playButton_downNavigatesToOverview() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_overview_downNavigatesToFirstCastItem() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstCastItem_upNavigatesToOverview() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    // Also covers spec row 9: overview DOWN → similar when no cast but similar present
    fun movieDetail_overview_downNavigatesToFirstSimilar_whenNoCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSimilarItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstSimilarItem_upNavigatesToFirstCast_whenBothPresent() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        // Navigate to similar: play → overview → cast → similar
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstSimilarItem_upNavigatesToOverview_whenNoCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no target
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    // -------------------------------------------------------------------------
    // Layout setup helpers
    // -------------------------------------------------------------------------

    private fun setMovieContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        castItems: List<CastModel> = emptyList(),
        similarItems: List<SimilarMovieModel> = emptyList(),
    ) {
        composeRule.setContent {
            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Test Movie",
                    eyebrow = "2026 · 2h",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "Test overview.",
                    factItems = emptyList(),
                    factSummary = "",
                    castItems = castItems,
                    similarItems = similarItems,
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile to verify no errors**

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**
```bash
git add app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailDpadNavigationUiTest.kt
git commit -m "test(ui): add Movie detail D-pad navigation tests"
```

---

## Task 5: Add TV Show D-pad navigation tests

Add the 10 TV Show tests and the `setTvShowContent` helper to `DetailDpadNavigationUiTest.kt`.

**Files:**
- Modify: `app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailDpadNavigationUiTest.kt`

- [ ] **Step 1: Add the TV Show tests and helper**

Insert the following block between the `setMovieContent` helper and the closing `}` of the class:

```kotlin
    // -------------------------------------------------------------------------
    // TvShowDetailLayout — focus routing tests
    // -------------------------------------------------------------------------

    @Test
    fun tvShowDetail_onLoad_playButtonHasFocus() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
    }

    @Test
    fun tvShowDetail_playButton_downNavigatesToOverview() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_downNavigatesToFirstSeason() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            seasons = listOf(SeasonModel(id = "s1", title = "Season 1", imageUrl = null, episodeCount = 5, unwatchedCount = 2)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSeasonItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSeason_upNavigatesToOverview() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            seasons = listOf(SeasonModel(id = "s1", title = "Season 1", imageUrl = null, episodeCount = 5, unwatchedCount = 2)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_downNavigatesToFirstCast_whenNoSeasons() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstCast_upNavigatesToFirstSeason_whenBothPresent() {
        val focus = FocusRequester()
        // firstVisibleItemIndex=3: seasons row at top, cast row (index 4) in prefetch window
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            seasons = listOf(SeasonModel(id = "s1", title = "Season 1", imageUrl = null, episodeCount = 5, unwatchedCount = 2)),
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → season
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSeasonItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToFirstCast_whenCastPresent() {
        // Seasons + cast + similar all present; similar is at LazyColumn index 5
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 4),
            primaryActionFocusRequester = focus,
            seasons = listOf(SeasonModel(id = "s1", title = "Season 1", imageUrl = null, episodeCount = 5, unwatchedCount = 2)),
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(SimilarMovieModel(id = "sim1", title = "Similar Show", imageUrl = null)),
        )

        // Navigate: play → overview → season → cast → similar
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToFirstSeason_whenNoCast() {
        // Seasons + similar (no cast); similar is at LazyColumn index 4
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            seasons = listOf(SeasonModel(id = "s1", title = "Season 1", imageUrl = null, episodeCount = 5, unwatchedCount = 2)),
            similarItems = listOf(SimilarMovieModel(id = "sim1", title = "Similar Show", imageUrl = null)),
        )

        // Navigate: play → overview → season → similar
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → season
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSeasonItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToOverview_whenNoSeasonsNoCast() {
        // Similar only; similar is at LazyColumn index 3
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(SimilarMovieModel(id = "sim1", title = "Similar Show", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no target
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    // TV Show layout helper — note: no factSummary parameter
    private fun setTvShowContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        seasons: List<SeasonModel> = emptyList(),
        castItems: List<CastModel> = emptyList(),
        similarItems: List<SimilarMovieModel> = emptyList(),
    ) {
        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Test Show",
                    eyebrow = "TV SERIES",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    seasons = seasons,
                    onSeasonClick = {},
                    castItems = castItems,
                    similarItems = similarItems,
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Test overview.",
                    factItems = emptyList(),
                    listState = listState,
                )
            }
        }
    }
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**
```bash
git add app/src/androidTest/java/com/rpeters/cinefintv/ui/screens/detail/DetailDpadNavigationUiTest.kt
git commit -m "test(ui): add TV Show detail D-pad navigation tests"
```

---

## Task 6: Run all detail tests on the emulator

Run both `DetailFocusUiTest` and `DetailDpadNavigationUiTest` on the connected emulator and verify all pass.

**Files:** None (verification only)

- [ ] **Step 1: Build and install APKs**

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```
Expected: both installs succeed.

- [ ] **Step 2: Run all detail UI tests**

```bash
adb shell am instrument -w -r \
  -e class 'com.rpeters.cinefintv.ui.screens.detail.DetailFocusUiTest,com.rpeters.cinefintv.ui.screens.detail.DetailDpadNavigationUiTest' \
  com.rpeters.cinefintv.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: `OK (23 tests)` — 5 from `DetailFocusUiTest` + 18 from `DetailDpadNavigationUiTest` (9 Movie + 9 TV Show; spec row 9 consolidated into row 5).

- [ ] **Step 3: If any test fails, diagnose**

Check `adb logcat -s "TestRunner" -d` for the failure stack trace. Common failure modes and fixes:
- `assertIsFocused` fails → check if the node's tag is on the correct focusable element (see Tasks 1 and 2)
- `No node found with tag` → the LazyColumn hasn't composed that item yet; increase `firstVisibleItemIndex` by 1 for that test
- Navigation ends up at wrong node → re-read the `focusProperties` in the layout file for that route

- [ ] **Step 4: Commit if any fixes were needed**

```bash
git add -p
git commit -m "fix(test): correct navigation test setup for emulator"
```
