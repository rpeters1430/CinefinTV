# CinefinTV Correctness Bug Fixes — Design Spec

**Date:** 2026-04-11  
**Scope:** Correctness and data integrity bugs only. Performance/optimization out of scope for this pass.  
**Approach:** Severity-first — fix highest-impact bugs first, each fix independently shippable.

---

## Background

A full codebase review identified 9 confirmed correctness bugs across ViewModels, event handling, and TV navigation. These are ordered by impact: broken D-pad navigation and incorrect network event routing are the highest priority; formatting/cleanup is lowest.

---

## Bug Inventory (Severity Order)

### HIGH

#### Bug 1 — `RefreshItem` not filtered by item ID in `TvShowDetailViewModel` & `SeasonViewModel`

- **Files:** `ui/screens/detail/TvShowDetailViewModel.kt:77-86`, `ui/screens/detail/SeasonViewModel.kt:59-70`
- **Problem:** `observeUpdateEvents()` calls `refreshWatchStatus()` on every `RefreshItem` event, regardless of which item changed. Any watched-status change anywhere in the app triggers 3 network calls for the currently-viewed show, or 1 network call for the currently-viewed season. `MovieDetailViewModel:85` and `CollectionDetailViewModel:91` correctly guard with `if (event.itemId == movieId)`.
- **Fix:** Add ID guard before calling `refreshWatchStatus()`:
  - `TvShowDetailViewModel`: `if (event.itemId == showId)`
  - `SeasonViewModel`: `if (event.itemId == seasonId)`
- **Test:** Unit test — emit `RefreshItem` with a foreign ID, verify `refreshWatchStatus()` is not called.

#### Bug 4 — Detail screens not registered with `AppChromeFocusController`

- **Files:** `MovieDetailScreen.kt`, `TvShowDetailScreen.kt`, `SeasonScreen.kt`, `PersonScreen.kt`, `CollectionDetailScreen.kt`
- **Problem:** None of these screens call `rememberTopLevelDestinationFocus()`. Per CLAUDE.md, every top-level screen must register so D-pad left/up from content reaches the sidebar rail. These screens use `LocalAppChromeFocusController` only for the delete dialog but never register their primary content focus requester. Pressing D-pad left from a detail screen does not navigate to the sidebar.
- **Fix:** In each screen:
  1. Pass the existing `primaryActionFocus` (already declared) to `rememberTopLevelDestinationFocus(primaryActionFocus)`
  2. Apply `Modifier.then(destinationFocus.primaryContentModifier())` to the main scrollable content root
  3. Apply `destinationFocus.drawerEscapeModifier(isLeftEdge = true)` to left-edge interactive items
- **Note:** `primaryActionFocus` already exists in `MovieDetailScreen:74`. Check each screen for its equivalent before adding a new one.

---

### MEDIUM

#### Bug 2 — Missing pre-launch guard in `TvShowDetailViewModel.refreshWatchStatus()`

- **File:** `ui/screens/detail/TvShowDetailViewModel.kt:89`
- **Problem:** `refreshWatchStatus()` immediately launches a coroutine and makes 3 network calls even when `_uiState` is `Loading` or `Error`. `MovieDetailViewModel:204` and `SeasonViewModel:74` both return early with `_uiState.value as? Content ?: return` before launching. `TvShowDetailViewModel` reads `currentState` only after all 3 calls complete, wasting network resources if the state is not `Content`.
- **Fix:** Add `_uiState.value as? TvShowDetailUiState.Content ?: return` as the first line of `refreshWatchStatus()`, before `viewModelScope.launch`.

#### Bug 3 — Duplicate audio codec/channel formatting logic

- **Files:** `MovieDetailViewModel.kt:176-192`, `SeasonViewModel.kt:183-199`
- **Problem:** Identical 20-line codec/channel block duplicated in two places. Adding a new codec or fixing a mapping requires updating both; they will inevitably drift.
- **Fix:** Extract to an extension function in `utils/Extensions.kt`:
  ```kotlin
  fun MediaStreamInfo.getAudioLabel(): String?
  ```
  Both ViewModels call it instead of inlining the logic.

#### Bug 5 — `BackHandler` stability — verify `onBack` lambda

- **Files:** `MovieDetailScreen.kt:52`, `TvShowDetailScreen.kt`, `SeasonScreen.kt`
- **Problem:** `BackHandler(onBack = onBack)` with no `enabled` condition is always active. If `onBack` is a stale or conditional lambda, the user can get stuck with no system fallback. Needs verification that `onBack` is always a stable nav pop call at every detail route in `CinefinTvNavGraph`.
- **Fix:** Read `CinefinTvNavGraph` and check each detail route's `onBack` argument.
  - **Pass (no code change):** Every route passes `{ navController.popBackStack() }` or `navController::popBackStack` directly.
  - **Fail (code change required):** Any route wraps `onBack` in a conditional or captures extra state — add `enabled` parameter to `BackHandler` so the system back gesture still works when the handler is logically disabled.

#### Bug 6 — `episodes` always `emptyList()` in `TvShowDetailUiState.Content`

- **File:** `ui/screens/detail/TvShowDetailViewModel.kt:135`
- **Problem:** `val episodes = emptyList<EpisodeModel>()` is hardcoded on every load. The `Content` state carries an `episodes` field that is always empty. Any consumer of `state.episodes` gets nothing silently.
- **Decision:** Episodes are shown on `SeasonScreen`, not `TvShowDetailScreen`. The field in `TvShowDetailUiState.Content` is dead weight.
- **Fix:** Remove the `episodes: List<EpisodeModel>` field from `TvShowDetailUiState.Content` and remove `val episodes = emptyList<EpisodeModel>()` from `load()`. Update any call sites that reference `state.episodes`.

---

### LOW

#### Bug 7 — `CollectionLibraryViewModel` body on separate line from constructor

- **File:** `ui/screens/library/LibraryViewModel.kt:110-112`
- **Problem:** Class body `{` is on a line separated from the superclass call by a blank line. Valid Kotlin but non-standard and misleading.
- **Fix:** Move `{` to end of line 110.

#### Bug 8 — `deleteEpisode` calls `refreshWatchStatus()` after `refreshAll`

- **File:** `ui/screens/detail/SeasonViewModel.kt:104-110`
- **Problem:** `updateBus.refreshAll()` already triggers `load(silent = true)` via `observeUpdateEvents()`. Calling `refreshWatchStatus()` immediately after means two concurrent coroutines writing to `_uiState`. After deletion the episode no longer exists, so `refreshWatchStatus()` fetching it is semantically wrong.
- **Fix:** Remove the `refreshWatchStatus()` call from `deleteEpisode`. `updateBus.refreshAll()` is sufficient.

#### Bug 9 — Unused `SavedStateHandle` imports

- **Files:** `MovieDetailViewModel.kt:3`, `TvShowDetailViewModel.kt:3`, `SeasonViewModel.kt:3`
- **Problem:** `SavedStateHandle` is imported in the constructor annotation chain but never referenced in any of the three files.
- **Fix:** Remove the unused import from all three files.

---

## Out of Scope

- Performance/optimization (recomposition, caching, LazyList keys) — next pass
- UI/UX improvements (text sizes, hardcoded colors, theme tokens) — next pass
- Test coverage gaps — follow-on after correctness fixes

---

## Implementation Order

Steps are ordered by severity. Each step is independently shippable.

| Step | Fix | Severity |
|------|-----|----------|
| 1 | Add `event.itemId` ID guard in `TvShowDetailViewModel` & `SeasonViewModel` `observeUpdateEvents()` | HIGH |
| 2 | Register all 5 detail screens with `AppChromeFocusController` via `rememberTopLevelDestinationFocus()` | HIGH |
| 3 | Add pre-launch guard to `TvShowDetailViewModel.refreshWatchStatus()` | MEDIUM |
| 4 | Extract audio codec/channel formatting to `MediaStreamInfo.getAudioLabel()` in `Extensions.kt` | MEDIUM |
| 5 | Verify `BackHandler` `onBack` lambda stability in `CinefinTvNavGraph`; fix if conditional | MEDIUM |
| 6 | Remove dead `episodes: List<EpisodeModel>` field from `TvShowDetailUiState.Content` | MEDIUM |
| 7 | Fix `CollectionLibraryViewModel` brace placement | LOW |
| 8 | Remove redundant `refreshWatchStatus()` call from `SeasonViewModel.deleteEpisode()` | LOW |
| 9 | Remove unused `SavedStateHandle` imports from 3 ViewModel files | LOW |
