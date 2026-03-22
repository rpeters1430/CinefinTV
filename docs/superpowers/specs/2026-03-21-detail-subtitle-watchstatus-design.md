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
- Retain existing `padding(horizontal = 24.dp, vertical = 22.dp)` on the inner `Column`
- The caller's `modifier` (e.g. `weight(1f).fillMaxWidth()`) must be applied to the outer `Box`, not the `Column`, so layout constraints from call sites are respected

No backdrop blur — it is expensive on TV hardware and the semi-transparent fill provides sufficient contrast.

### Files Changed

- `ui/screens/detail/DetailScreenComponents.kt` — `DetailGlassPanel` composable

---

## 2. Detail Screen Title Text Color

### Problem

The title `Text` composable in each detail screen (movie, TV show, episode) has no explicit `color` parameter. In the TV Material3 theme context this inherits a dark/black color, making the title unreadable against the dark backdrop.

### Solution

Add `color = MaterialTheme.colorScheme.onSurface` to the title `Text` in each detail screen's content composable. Use the existing `androidx.tv.material3.MaterialTheme` import already present at the top of each file — do not add a new `androidx.compose.material3.MaterialTheme` import, as that would create an ambiguous reference conflict.

### Files Changed

- `ui/screens/detail/MovieDetailScreen.kt` — title `Text` in `MovieDetailContent`
- `ui/screens/detail/TvShowDetailScreen.kt` — title `Text` in `TvShowDetailContent`
- `ui/screens/detail/EpisodeDetailScreen.kt` — episode title `Text`

---

## 3. Subtitle Selection Fix

### Problem

`applyTrackSelection` in `PlayerViewModel` uses `setPreferredTextLanguage(subtitleTrack.language)` for direct-play subtitle selection. This fails when:
- `language` is `null` (no language tag in the container)
- A prior selection left a `TrackSelectionOverride` in `trackSelectionParameters` that conflicts with the new preference

### Solution

Update `applyTrackSelection` in `PlayerViewModel`:

1. **Always clear stale overrides first:** call `builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)` before any subtitle logic.

2. **Use `TrackSelectionOverride` for reliable selection:** scan `player.currentTracks.groups`, filter for `C.TRACK_TYPE_TEXT` groups. For each group, scan its tracks for one whose `format.language` matches `subtitleTrack.language`. Apply `builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))`.

3. **Same-language and null-language collision:** when multiple text track groups share the same language (including null), match by position — the N-th subtitle `TrackOption` produced by `PlayerMappers.toSubtitleTrackOptions` corresponds to the N-th text group in ExoPlayer's `currentTracks` because both enumerate streams in the same order from the media source. Use the `TrackOption`'s position in `uiState.subtitleTracks` (its list index) to select the correct group when there is a language collision. This position-based match is only valid inside `applyTrackSelection`, which is only called from the `DIRECT_PLAY` branch of `selectSubtitleTrack`; do not apply it in any future transcoding path.

4. **Empty `currentTracks` fallback:** if no text groups are found at all (e.g., tracks not yet demuxed at selection time), log a warning and fall back to `setPreferredTextLanguage(subtitleTrack.language)` + `setSelectUndeterminedTextLanguage(true)`. Do not silently no-op.

5. **Disabling subtitles:** the up-front `clearOverridesOfType` call from point 1 already covers this path. No second `clearOverridesOfType` call is needed. Just call `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` and `setPreferredTextLanguage(null)`.

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
- Does **not** set `Loading` state — the screen never flickers or resets scroll position

**`MovieDetailViewModel.refreshWatchStatus()`:** re-fetch the item via `repositories.media.getMovieDetails(movieId)`. On success, update only `movie.isWatched` and `movie.playbackProgress` within the existing `Content` state.

**`EpisodeDetailViewModel.refreshWatchStatus()`:** re-fetch the episode item details. On success, update only `episode.isWatched` and `episode.playbackProgress` within the existing `Content` state.

**`TvShowDetailViewModel.refreshWatchStatus()`:** re-fetch both the series item (for top-level watch state) and the seasons list via `repositories.media.getSeasonsForSeries(seriesId)`. Update `SeasonModel.unwatchedCount` for each season in addition to any top-level fields. Season unwatched counts are the primary watch-state indicator for this screen and must not be left stale.

**`HomeViewModel.refreshWatchStatus()`:** re-issue only the `getContinueWatching` repository call. On success, update only the continue-watching section within the existing `Content` state and recompute the derived "next episodes" items by calling the existing `buildNextEpisodeSectionItems` method (it uses a `coroutineScope` async fan-out — reuse it rather than inlining new logic). Do **not** re-fetch the libraries, recently-added-movies, recently-added-episodes, recently-added-music, or recently-added-videos calls — those sections are not affected by playback.

#### Screen Layer

In `MovieDetailScreen`, `TvShowDetailScreen`, `EpisodeDetailScreen`, and `HomeScreen`, add lifecycle observation. Use `remember { mutableStateOf(false) }` (not `rememberSaveable`) for the `hasBeenPaused` flag — it should reset on config change since a config change recomposes the full destination and the ViewModel's current state is already up-to-date.

```
val lifecycleOwner = LocalLifecycleOwner.current
var hasBeenPaused by remember { mutableStateOf(false) }

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE  -> hasBeenPaused = true
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
```

`LocalLifecycleOwner` in a Compose Navigation destination is the `NavBackStackEntry`'s lifecycle owner. This fires precisely when the backstack entry resumes — i.e., when pressing back from the player to a detail screen, or from detail back to home. No SharedViewModel, no event bus, no `savedStateHandle` results needed.

### Files Changed

- `ui/screens/detail/MovieDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/TvShowDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/EpisodeDetailScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/home/HomeScreen.kt` — add `DisposableEffect` lifecycle observer
- `ui/screens/detail/MovieDetailViewModel.kt` — add `refreshWatchStatus()`
- `ui/screens/detail/TvShowDetailViewModel.kt` — add `refreshWatchStatus()` (re-fetches seasons too)
- `ui/screens/detail/EpisodeDetailViewModel.kt` — add `refreshWatchStatus()`
- `ui/screens/home/HomeViewModel.kt` — add `refreshWatchStatus()` (continue-watching only)

---

## Out of Scope

- Library screens (movies, TV shows) — user did not request refresh there
- Audio player — watch status for music is not requested
- Subtitle appearance preferences — existing system unchanged
- Transcoding subtitle path — already works via `reloadStream`
