# Season & Episode Detail Redesign

**Date:** 2026-03-20
**Scope:** SeasonScreen episode list, EpisodeDetailScreen media info, focus scroll-to-top fix, play button fix

---

## 1. Season Screen — Episode List Layout

### Problem
The current episode grid in `SeasonScreen.kt` renders episodes in a manual 5-column row grid using `items(rows)` with a `Row` of `TvMediaCard`s per row. The cards are too large and the grid pattern doesn't suit an episode list.

### Solution
Replace the manual row-grid with a flat `items(episodes)` list, each episode rendered as a new `EpisodeListRow` composable (horizontal wide card).

### `EpisodeListRow` composable
- Location: `DetailScreenComponents.kt`
- A full-width TV `Card` (clickable, focusable)
- Layout: `Row`, `padding(horizontal = 48.dp, vertical = 6.dp)`, height ~96dp
- **Left:** 16:9 thumbnail, ~160dp wide, rounded corners matching `LocalCinefinSpacing.cornerCard`
  - Watch-status overlay (watched checkmark, "Resume" pill)
  - Playback progress bar at bottom edge only if `episode.playbackProgress > 0f` (field is `Float?` in model but always set; guard with `?: 0f` for safety)
- **Right column:** `padding(start = 16.dp)`, vertically centered
  - Row: episode code label (e.g. "E1") + separator + duration — `labelMedium`, `onSurfaceVariant`
  - Title — `titleMedium` bold, 2-line max
  - Overview — `bodyMedium`, `onSurfaceVariant`, 2-line max
- Focus: TV `Card` border ring (white, 2dp) + `focusedScale = 1.02f` (subtle for a wide card)
- `onFocus` callback fires when card receives focus
- Signature:
  ```kotlin
  fun EpisodeListRow(
      episode: EpisodeModel,
      modifier: Modifier = Modifier,
      onFocus: () -> Unit = {},
      onClick: () -> Unit,
  )
  ```

### SeasonScreen changes
- Remove the `val columns`, `val rows`, and `items(rows) { rowIndex -> Row { ... } }` block
- Replace with `items(episodes, key = { it.id }) { episode -> EpisodeListRow(...) }`
- `focusRequester` and `focusProperties` applied via `modifier` on `EpisodeListRow`:
  - Episode matching `lastFocusedEpisodeId` gets `.focusRequester(episodeGridEntryRequester)`
  - First episode gets `.focusProperties { up = primaryActionFocusRequester }`
- **Focus requester always attached:** `primaryActionFocusRequester` must always be attached to a rendered node. Currently it is only passed when `focusedEpisode == null`. Fix: pass `primaryFocusRequester = primaryActionFocusRequester` to **both** `DetailActionRow` branches (the `focusedEpisode == null` and `focusedEpisode != null` cases). Without this, pressing UP from the first episode while `focusedEpisode != null` has no valid target and silently fails.
- `onFocus` updates both `lastFocusedEpisodeId` and `focusedEpisode`
- `onClick` calls `onOpenEpisode(episode.id)` (navigates to `EpisodeDetailScreen`)
- Keep the `DetailContentSection(title = "Episodes", eyebrow = ...) {}` as a separate `item` immediately above the episode list items, with an empty content lambda (it renders the title + underline bar only). Remove the `modifier = Modifier.padding(top = 0.dp)` caller override — it has no real effect (internal padding is always 32dp), but removing it keeps the call site clean.

---

## 2. Episode Detail Screen — Media Details Section

### Problem
`EpisodeDetailScreen` shows only basic metadata (episode code, year, duration, overview). Users want video/audio technical details: resolution, codec, HDR type, audio format, channels, language.

### Data availability
`getEpisodeDetails` already requests `ItemFields.MEDIA_SOURCES` and `ItemFields.MEDIA_STREAMS`, so `BaseItemDto.mediaSources` is populated on the returned DTO.

### New model
Add `MediaDetailModel` data class (co-located in `EpisodeDetailViewModel.kt`):
```kotlin
data class VideoStreamInfo(
    val resolution: String?,   // "4K", "1080p", "720p"
    val codec: String?,        // "HEVC", "AVC", "AV1", "VP9" — matches MediaQualityExtensions output
    val hdr: String?,          // "Dolby Vision", "HDR10+", "HDR10", "HDR"
    val bitrateKbps: Int?,
)

data class AudioStreamInfo(
    val codec: String,         // "TrueHD Atmos", "EAC3", "DTS", "AAC", "AC3"
    val channels: String?,     // "7.1", "5.1", "Stereo"
    val language: String?,     // ISO 639 code uppercased, e.g. "EN"
    val isDefault: Boolean,  // map from audioStream.isDefault == true (SDK field is Boolean?)
)

data class MediaDetailModel(
    val container: String?,    // "mkv", "mp4"
    val video: VideoStreamInfo?,
    val audioStreams: List<AudioStreamInfo>,
)
```

### ViewModel changes
- Add `toMediaDetailModel()` private extension on `BaseItemDto`
- Extract from `mediaSources.firstOrNull()`:
  - `container` from `source.container`
  - Video stream: first stream where `type == MediaStreamType.VIDEO`
    - Resolution: same logic as `MediaQualityExtensions.getMediaQualityLabel()`
    - Codec: map `videoStream.codec?.uppercase()` using the same mapping as `MediaQualityExtensions` — `"HEVC"/"H265"` → `"HEVC"`, `"AVC"/"H264"` → `"AVC"`, `"VP9"` → `"VP9"`, `"AV1"` → `"AV1"`, else null
    - HDR: same logic as `MediaQualityExtensions`
    - Bitrate: `videoStream.bitRate?.div(1000)?.toInt()`
  - Audio streams: all streams where `type == MediaStreamType.AUDIO`, mapped to `AudioStreamInfo`
    - Codec: map to display string ("TrueHD Atmos" if `audioStream.profile` contains "atmos", else codec name)
    - Channels: derive from `audioStream.channels` (2→"Stereo", 6→"5.1", 8→"7.1", else "$channels ch")
    - Language: `audioStream.language?.uppercase()?.take(3)`
    - isDefault: `audioStream.isDefault == true` (SDK field is `Boolean?`; use `== true` for null-safety)
- Add `mediaDetail: MediaDetailModel?` to `EpisodeDetailUiState.Content`
- Populate during `load()` from the same `episodeDto`

### Screen changes
- Add `mediaDetail: MediaDetailModel?` parameter to the `EpisodeDetailContent` private composable; update its call site in `EpisodeDetailScreen` to pass `state.mediaDetail`
- Add a new `item` in `EpisodeDetailContent`'s `LazyColumn` after the chapters item
- Render only if `mediaDetail != null` and has at least video or audio data
- `DetailContentSection(title = "Media Details")` containing:
  - If video != null: a `DetailChipRow` with non-null entries from: resolution, codec, HDR, bitrate formatted as "$bitrateKbps kbps"
  - If audioStreams non-empty: one `DetailChipRow` per audio stream (default stream first), each chip shows e.g. "EAC3  5.1  EN"
- The section does not need focusable elements — info only
- Update focus chain: `primaryDownFocusRequester` stays pointed at chapters (or null if no chapters) — media details section is non-interactive so no focus target needed

---

## 3. Focus Scroll-to-Top Fix

### Problem
In `MovieDetailScreen`, `TvShowDetailScreen`, and `SeasonScreen`, `DetailHeroBox` has:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus && listState.firstVisibleItemIndex == 0) {
        scope.launch { listState.animateScrollToItem(0) }
    }
}
```
The `&& listState.firstVisibleItemIndex == 0` guard means the scroll-to-top never fires when the user has scrolled down. When D-pad UP from a content row moves focus to the play button, the hero is off-screen.

### Fix
Remove the `listState.firstVisibleItemIndex == 0` condition in all three screens:
```kotlin
modifier = Modifier.onFocusChanged {
    if (it.hasFocus) {
        scope.launch { listState.animateScrollToItem(0) }
    }
}
```

`EpisodeDetailScreen` already does this correctly and needs no change.

---

## 4. Play Button Fix

### Problem
`EpisodeDetailScreen` uses `LaunchedEffect(episode.id) { anchorFocusRequester.requestFocus() }` to set initial focus. If the `LaunchedEffect` fires before the anchor node completes its layout pass, `requestFocus()` is a no-op and the screen has no focused element. The play button never receives focus, so the user cannot activate it.

### Fix
In `EpisodeDetailContent`, add a single-frame delay before requesting focus:
```kotlin
LaunchedEffect(episode.id) {
    withFrameMillis {}   // wait one frame for layout
    anchorFocusRequester.requestFocus()
}
```
`withFrameMillis` is from `androidx.compose.runtime` — it suspends until the next frame callback, ensuring the Spacer node is attached before focus is requested.

---

## Files Changed

| File | Change |
|---|---|
| `ui/screens/detail/DetailScreenComponents.kt` | Add `EpisodeListRow` composable |
| `ui/screens/detail/SeasonScreen.kt` | Replace grid with `items(episodes)` + `EpisodeListRow` |
| `ui/screens/detail/EpisodeDetailViewModel.kt` | Add `MediaDetailModel`, `VideoStreamInfo`, `AudioStreamInfo`; populate in `load()` |
| `ui/screens/detail/EpisodeDetailScreen.kt` | Add media details section; fix `LaunchedEffect` focus timing |
| `ui/screens/detail/MovieDetailScreen.kt` | Remove `firstVisibleItemIndex == 0` condition |
| `ui/screens/detail/TvShowDetailScreen.kt` | Remove `firstVisibleItemIndex == 0` condition |
| `ui/screens/detail/SeasonScreen.kt` | Remove `firstVisibleItemIndex == 0` condition |

No new files. No new API calls. No new dependencies.
