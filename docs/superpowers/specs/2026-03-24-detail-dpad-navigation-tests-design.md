# Detail Screen D-Pad Navigation Tests — Design

**Date:** 2026-03-24
**Scope:** Movie and TV Show detail screens
**Type:** Instrumented UI test coverage

---

## Problem

`DetailFocusUiTest` verifies that sections render correctly but does not verify D-pad focus routing. After fixing the scrolling root cause (anchor was bypassing the hero and jumping straight to the overview), there is no automated coverage confirming the focus chain works as intended. Any future regression in `focusProperties` wiring would go undetected.

---

## File Structure

### New files
```
app/src/androidTest/.../ui/screens/detail/DetailDpadNavigationUiTest.kt
app/src/androidTest/.../ui/screens/detail/DetailTestHelpers.kt
```

### Refactor: extract shared helpers to `DetailTestHelpers.kt`
Two items currently `private` in `DetailFocusUiTest.kt` need to become `internal` and shared:

1. `DetailTestHost` composable — wraps content in `CinefinTvTheme` + `CompositionLocalProvider` for `LocalCinefinThemeController`
2. `SemanticsNodeInteraction.requestFocus()` extension — calls `performSemanticsAction(SemanticsActions.RequestFocus)`

Both test files import from `DetailTestHelpers.kt`. `DetailFocusUiTest.kt` removes its own private copies.

---

## Production Code Change: Move Overview Test Tag

In `DetailOverviewSection.kt`, `testTag(DetailTestTags.Overview)` currently lives on an inner `Column` (line 118). The focusable node is the **outer `Box`** that carries `.focusRequester()` and `.focusable()`. Since `assertIsFocused()` targets the focused node directly, the tag must move to the outer `Box`.

**Before:**
```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .focusRequester(...)
        .focusable()
        ...
) {
    Row(...) {
        Column(
            modifier = Modifier
                ...
                .testTag(DetailTestTags.Overview)  // ← wrong node
```

**After:**
```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .testTag(DetailTestTags.Overview)           // ← moved here
        .focusRequester(...)
        .focusable()
        ...
```

---

## New Test Tags

Add to `DetailTestTags.kt`:
- `FirstCastItem`
- `FirstSimilarItem`
- `FirstSeasonItem`

Applied **inside** the first-item conditional in the layouts, alongside the existing `focusRequester`:

```kotlin
// MovieDetailLayout — first cast item only
modifier = if (person.id == castItems.firstOrNull()?.id) {
    Modifier
        .focusRequester(firstCastFocusRequester)
        .focusProperties { up = overviewFocusRequester }
        .testTag(DetailTestTags.FirstCastItem)      // inside the conditional
} else {
    Modifier
}
```

Same pattern for `FirstSimilarItem` (both layouts) and `FirstSeasonItem` (TV Show layout only).

---

## Technical Approach

**Establishing initial focus:**
```kotlin
composeRule.runOnIdle { primaryActionFocusRequester.requestFocus() }
```
Mirrors what `focusDetailScreenAtTop` does in production.

**Scroll state per test:**
`LazyColumn` only composes items within the viewport. Tests that assert focus on lower rows must initialize `LazyListState` so both the source and target items are composed before the key press:

| Test targets | `firstVisibleItemIndex` |
|---|---|
| Play button or overview only | `0` (default `rememberLazyListState()`) |
| Cast / season row | `2` (overview at top, row 3 in prefetch window) |
| Movie similar row (index 4) | `3` (cast at top, similar in prefetch window) |
| TV Show similar row (index 5, seasons+cast+similar) | `4` (cast at top, similar at index 5 in prefetch window) |
| TV Show similar row (index 4, seasons+similar, no cast) | `3` (seasons at top, similar at index 4 in prefetch window) |
| TV Show similar only (index 3, no seasons/cast) | `2` (overview at top, similar in prefetch window) |

**D-pad simulation:**
```kotlin
composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
composeRule.waitForIdle()   // always wait after a key press before asserting
```

**Focus assertion:**
```kotlin
composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
// etc.
```

**Note on `factSummary`:** `MovieDetailLayout` has a `factSummary: String` parameter that `TvShowDetailLayout` does not. TV Show test setups must not pass this argument.

---

## Test Coverage

### MovieDetailLayout (9 tests)

| Test | `firstVisibleItemIndex` | Initial focus | Action | Assert |
|------|------------------------|---------------|--------|--------|
| `movieDetail_onLoad_playButtonHasFocus` | 0 | requestFocus(play) | — | PrimaryAction is focused |
| `movieDetail_playButton_downNavigatesToOverview` | 0 | requestFocus(play) | DOWN | Overview is focused |
| `movieDetail_overview_downNavigatesToFirstCastItem` | 2 | requestFocus(play) → DOWN (overview) | DOWN | FirstCastItem is focused |
| `movieDetail_firstCastItem_upNavigatesToOverview` | 2 | requestFocus(play) → DOWN → DOWN (cast) | UP | Overview is focused |
| `movieDetail_overview_downNavigatesToFirstSimilar_whenNoCast` | 2 | no cast; requestFocus(play) → DOWN (overview) | DOWN | FirstSimilarItem is focused |
| `movieDetail_firstSimilarItem_upNavigatesToFirstCast_whenBothPresent` | 3 | cast+similar; focus play → DOWN → DOWN → DOWN (similar) | UP | FirstCastItem is focused |
| `movieDetail_firstSimilarItem_upNavigatesToOverview_whenNoCast` | 2 | no cast; focus play → DOWN → DOWN (similar) | UP | Overview is focused |
| `movieDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows` | 0 | no cast/similar; requestFocus(play) → DOWN (overview) | DOWN | Overview is still focused |
| `movieDetail_overview_downNavigatesToFirstSimilar_whenNoCastButSimilarPresent` | 2 | no cast, similar present; DOWN to overview | DOWN | FirstSimilarItem is focused |

### TvShowDetailLayout (10 tests)

| Test | `firstVisibleItemIndex` | Initial focus | Action | Assert |
|------|------------------------|---------------|--------|--------|
| `tvShowDetail_onLoad_playButtonHasFocus` | 0 | requestFocus(play) | — | PrimaryAction is focused |
| `tvShowDetail_playButton_downNavigatesToOverview` | 0 | requestFocus(play) | DOWN | Overview is focused |
| `tvShowDetail_overview_downNavigatesToFirstSeason` | 2 | seasons present; play → DOWN (overview) | DOWN | FirstSeasonItem is focused |
| `tvShowDetail_firstSeason_upNavigatesToOverview` | 2 | seasons present; play → DOWN → DOWN (season) | UP | Overview is focused |
| `tvShowDetail_overview_downNavigatesToFirstCast_whenNoSeasons` | 2 | cast, no seasons; play → DOWN (overview) | DOWN | FirstCastItem is focused |
| `tvShowDetail_firstCast_upNavigatesToFirstSeason_whenBothPresent` | 3 | seasons+cast; play → DOWN → DOWN → DOWN (cast) | UP | FirstSeasonItem is focused |
| `tvShowDetail_firstSimilar_upNavigatesToFirstCast_whenCastPresent` | 4 | seasons+cast+similar; play → navigate to similar | UP | FirstCastItem is focused |
| `tvShowDetail_firstSimilar_upNavigatesToFirstSeason_whenNoCast` | 3 | seasons+similar, no cast; navigate to similar | UP | FirstSeasonItem is focused |
| `tvShowDetail_firstSimilar_upNavigatesToOverview_whenNoSeasonsNoCast` | 2 | similar only; play → DOWN → DOWN (similar) | UP | Overview is focused |
| `tvShowDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows` | 0 | no seasons/cast/similar; play → DOWN (overview) | DOWN | Overview is still focused |

---

## Implementation Order

Prerequisites must land before writing any test code:

1. **Move `DetailTestTags.Overview` tag** to the outer focusable `Box` in `DetailOverviewSection.kt` (see production code change section above)
2. **Add new tag constants** (`FirstCastItem`, `FirstSimilarItem`, `FirstSeasonItem`) to `DetailTestTags.kt`
3. **Apply new tags** inside the first-item conditionals in `MovieDetailLayout.kt` and `TvShowDetailLayout.kt`
4. **Extract shared helpers** — move `DetailTestHost` and `requestFocus()` extension from `DetailFocusUiTest.kt` to `DetailTestHelpers.kt` as `internal`; remove private copies from `DetailFocusUiTest.kt`

Then write the tests:

5. **Write `DetailDpadNavigationUiTest.kt`** — 9 Movie tests, then 10 TV Show tests
6. **Run on emulator** to confirm all pass

---

## What Is Not Covered

- The anchor (item 0) DOWN route — covered implicitly by the initial focus tests (play button gets focus, confirming the anchor is not an obstacle)
- RIGHT/LEFT navigation within cast/season/similar rows — handled by Compose default traversal; no explicit `focusProperties` wiring to regress
- Stuff detail screen — separate follow-on work
- Season episode list screen — separate follow-on work
