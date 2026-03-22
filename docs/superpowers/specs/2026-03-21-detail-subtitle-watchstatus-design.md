# Design: Detail Screen Polish, Subtitle Fix, Watch Status Propagation

**Date:** 2026-03-21
**Status:** Approved

---

## Overview

Four related improvements to CinefinTV:

1. Detail screen text panel — add dark glass background so text is readable over backdrop images
2. Detail screen title color — fix title rendering black due to missing explicit color
3. Subtitle selection — fix subtitles not showing when selected in the player
4. Watch status propagation — refresh watch state on detail screens and home after playback

---

## 1. Detail Screen Text Panel Background

### Problem

`DetailGlassPanel` (`DetailScreenComponents.kt`) is a bare `Column` with padding and no background. Text renders directly over the backdrop gradient. When the backdrop image is light on the right side, overview text, metadata lines, and the title are illegible.

### Solution

Wrap the `Column` in a `Box` with:
- `background(expressiveColors.chromeSurface.copy(alpha = 0.82f), shape = RoundedCornerShape(spacing.cornerContainer))`
- A 1dp border: `borderSubtle.copy(alpha = 0.14f)`
- Retain existing `padding(horizontal = 24.dp, vertical = 22.dp)`

No backdrop blur — it is expensive on TV hardware and the semi-transparent fill provides sufficient contrast.

### Files Changed

- `ui/screens/detail/DetailScreenComponents.kt` — `DetailGlassPanel` composable

---

## 2. Detail Screen Title Text Color

### Problem

The title `Text` composable in each detail screen (movie, TV show, episode) has no explicit `color` parameter. In the TV Material3 theme context this inherits a dark/black color, making the title unreadable against the dark backdrop.

### Solution

Add `color = MaterialTheme.colorScheme.onSurface` to the title `Text` call in each detail screen's content composable.

### Files Changed

- `ui/screens/detail/MovieDetailScreen.kt` — `MovieDetailContent`
- `ui/screens/detail/TvShowDetailScreen.kt` — `TvShowDetailContent`
- `ui/screens/detail/EpisodeDetailScreen.kt` — episode title `Text`

---

## 3. Subtitle Selection Fix

### Problem

`applyTrackSelection` in `PlayerViewModel` uses `setPreferredTextLanguage(subtitleTrack.language)` for direct-play subtitle selection. This fails when:
- `language` is `null` (no language tag in the container)
- A prior selection left a `TrackSelectionOverride` in `trackSelectionParameters` that conflicts with the new preference

### Solution

Update `applyTrackSelection` in `PlayerViewModel`:

1. **Always clear stale overrides first:** call `builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)` before applying any new subtitle selection.

2. **Use `TrackSelectionOverride` for reliable selection:** scan `player.currentTracks.groups`, filter for `C.TRACK_TYPE_TEXT` groups, find the track whose `format.language` matches `subtitleTrack.language`, and apply `builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))`.

3. **Fallback:** if no language match is found (null or unrecognized), fall back to `setPreferredTextLanguage(subtitleTrack.language)` + `setSelectUndeterminedTextLanguage(true)`.

4. **Disabling subtitles:** clear overrides with `clearOverridesOfType` before calling `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`.

This approach is reliable because `currentTracks` is populated by the time the user can open the track panel (player is in `STATE_READY`).

### Files Changed

- `ui/player/PlayerViewModel.kt` — `applyTrackSelection` private method

---

## 4. Watch Status Propagation

### Problem

Detail screen and home screen ViewModels load their data once in `init`. After playing a video (fully or partially) and pressing back, the screens still show the old watch status/progress because there is no refresh mechanism.

### Solution

#### ViewModel Layer

Add `refreshWatchStatus()` to:
- `MovieDetailViewModel`
- `TvShowDetailViewModel`
- `EpisodeDetailViewModel`
- `HomeViewModel`

Each method:
- Guards: only runs if current `_uiState.value` is `Content` (no-ops on `Loading`/`Error`)
- Re-fetches the minimal data needed for watch state:
  - Detail ViewModels: re-fetch the item details, update only `isWatched` and `playbackProgress` fields within the existing `Content` state (no full reload)
  - `HomeViewModel`: re-fetch continue-watching and recently-watched rows and update the corresponding sections in the existing content state
- Does **not** set `Loading` state — the screen never flickers or resets scroll position

#### Screen Layer

In `MovieDetailScreen`, `TvShowDetailScreen`, `EpisodeDetailScreen`, and `HomeScreen`, add a `DisposableEffect(lifecycleOwner)` using `LifecycleEventObserver`:

```
ON_PAUSE  → set hasBeenPaused = true
ON_RESUME → if hasBeenPaused: call viewModel.refreshWatchStatus(); hasBeenPaused = false
```

`LocalLifecycleOwner` in a Compose Navigation destination is the `NavBackStackEntry`'s lifecycle owner. This means the effect fires precisely when the backstack entry resumes — i.e., when you press back from the player to a detail screen, or from detail back to home. No SharedViewModel, no event bus, no `savedStateHandle` results needed.

### Files Changed

- `ui/screens/detail/MovieDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/TvShowDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/EpisodeDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/home/HomeScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/MovieDetailViewModel.kt` — add `refreshWatchStatus()`
- `ui/screens/detail/TvShowDetailViewModel.kt` — add `refreshWatchStatus()`
- `ui/screens/detail/EpisodeDetailViewModel.kt` — add `refreshWatchStatus()`
- `ui/screens/home/HomeViewModel.kt` — add `refreshWatchStatus()`

---

## Out of Scope

- Library screens (movies, TV shows) — user did not request refresh there
- Audio player — watch status for music is not requested
- Subtitle appearance preferences — existing system unchanged
- Transcoding subtitle path — already works via `reloadStream`
