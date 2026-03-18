# Library Screens: CompactCard Migration

**Date:** 2026-03-17
**Status:** Approved

## Summary

Replace the manual compact-card implementation in `TvMediaCard`'s `compactMetadata = true` branch with the official `androidx.tv.material3.CompactCard` component. Update library grids to use 196dp fixed-width cards.

## Scope

- `ui/components/TvMediaCard.kt` — swap card shell in the `compactMetadata` branch
- `ui/screens/library/LibraryScreen.kt` — update grid min size and pass `cardWidth`
- `ui/screens/collections/CollectionsLibraryScreen.kt` — same grid update

All other `TvMediaCard` call sites (home screen rows, detail screens) are unaffected.

## Component Changes

### `TvMediaCard.kt` — compact path

Replace `Card { Box { ... } }` shell with `CompactCard`:

```kotlin
CompactCard(
    onClick = onClick,
    image = {
        // AsyncImage + progress bar overlay + badge overlays (unchanged)
    },
    title = {
        Text(title, style = ..., fontSize = 16.sp, maxLines = 1, overflow = Ellipsis)
    },
    subtitle = {
        if (!subtitle.isNullOrBlank()) Text(subtitle, ...)
    },
    modifier = (if (cardWidth != null) modifier.width(cardWidth) else modifier.fillMaxWidth())
        .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
    scale = CardDefaults.scale(focusedScale = 1.05f),
    border = CardDefaults.border(
        focusedBorder = Border(BorderStroke(3.dp, expressiveColors.focusRing))
    ),
    shape = CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
)
```

The `image` slot is a `Box` containing:
1. `AsyncImage` (full fill, ContentScale.Crop)
2. Vertical gradient scrim overlay (unchanged)
3. `WatchStatusOverlay` or `UnwatchedCountOverlay` at `TopEnd` (unchanged)
4. Playback progress bar at `BottomStart` (unchanged)

The `metaContainerColor` animated color and `compactOverlayScrim` animated color remain, used inside the `image` slot scrim.

The non-compact (`compactMetadata = false`) branch is untouched.

### `LibraryScreen.kt`

```kotlin
// Before
columns = GridCells.Adaptive(minSize = 240.dp)
// ...
TvMediaCard(..., cardWidth = null, compactMetadata = true, aspectRatio = 16f / 9f)

// After
columns = GridCells.Adaptive(minSize = 196.dp)
// ...
TvMediaCard(..., cardWidth = 196.dp, compactMetadata = true, aspectRatio = 16f / 9f)
```

### `CollectionsLibraryScreen.kt`

Same grid and card call site changes as `LibraryScreen.kt`.

## Overlays (preserved, no changes)

| Overlay | Position | Condition |
|---|---|---|
| Watch status (`✓`) | TopEnd | `watchStatus == WATCHED` |
| Resume pill | TopEnd | `watchStatus == IN_PROGRESS` |
| Unwatched count badge | TopEnd | `unwatchedCount > 0` (takes priority over watch status) |
| Playback progress bar | Bottom (3dp) | `watchStatus == IN_PROGRESS && playbackProgress > 0` |

## What Does Not Change

- Non-compact `TvMediaCard` path (home screen rows, season cards, detail screens)
- All badge/overlay components (`WatchStatusOverlay`, `UnwatchedCountOverlay`)
- Focus system (`focusRequester`, `focusProperties`, `RegisterPrimaryScreenFocus`)
- `LibraryViewModel`, `CollectionsLibraryViewModel` — no data changes
- Card aspect ratio stays `16f / 9f`
