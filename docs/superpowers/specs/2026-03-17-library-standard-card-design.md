# Library Screens: Standard Cards with Text Below

**Date:** 2026-03-17
**Status:** Approved

## Summary

Replace the current `compactMetadata = true` (text overlaid on image) card style in the library and collections grids with the standard `TvMediaCard` non-compact layout (text below image). Fix horizontal card text alignment to be left-aligned (start) instead of centered.

## Scope

- `ui/components/TvMediaCard.kt` — remove conditional centering for horizontal cards in non-compact path
- `ui/screens/library/LibraryScreen.kt` — remove `compactMetadata = true`
- `ui/screens/collections/CollectionsLibraryScreen.kt` — remove `compactMetadata = true`

All other `TvMediaCard` call sites (home screen rows, detail screens, season cards) are unaffected.

## Component Changes

### `TvMediaCard.kt` — non-compact horizontal alignment

In the non-compact path, the `Column` and `Text` components conditionally center-align when `isHorizontal` (aspectRatio > 1). Remove this conditional so horizontal cards use start alignment, matching standard grid convention:

```kotlin
// Before
horizontalAlignment = if (isHorizontal) Alignment.CenterHorizontally else Alignment.Start
// title textAlign:
textAlign = if (isHorizontal) TextAlign.Center else TextAlign.Start
// subtitle textAlign:
textAlign = if (isHorizontal) TextAlign.Center else TextAlign.Start

// After
horizontalAlignment = Alignment.Start
// title textAlign:
textAlign = TextAlign.Start
// subtitle textAlign:
textAlign = TextAlign.Start
```

### `LibraryScreen.kt`

Remove `compactMetadata = true` from the `TvMediaCard` call in the paged items grid. The parameter defaults to `false`, so no replacement value is needed.

```kotlin
// Before
TvMediaCard(
    ...
    compactMetadata = true,
)

// After
TvMediaCard(
    ...
    // compactMetadata defaults to false
)
```

All other parameters (`aspectRatio = 16f / 9f`, `cardWidth = null`, grid `minSize = 240.dp`, focus modifiers) remain unchanged.

### `CollectionsLibraryScreen.kt`

Same change as `LibraryScreen.kt` — remove `compactMetadata = true` from the card call site.

## What Does Not Change

- Card aspect ratio stays `16f / 9f`
- Grid `columns = GridCells.Adaptive(minSize = 240.dp)` unchanged
- All overlay components: `WatchStatusOverlay`, `UnwatchedCountOverlay`, progress bar
- Focus system: `focusRequester`, `focusProperties`, `RegisterPrimaryScreenFocus`, `TvScreenTopFocusAnchor`
- `LibraryViewModel`, `CollectionsLibraryViewModel` — no data changes
- `compactMetadata = true` path in `TvMediaCard` remains (still used elsewhere if needed)
- Home screen rows, detail screens, season cards — unaffected
