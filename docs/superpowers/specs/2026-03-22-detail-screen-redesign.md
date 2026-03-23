# Detail Screen Redesign — Cinematic Direction

**Date:** 2026-03-22
**Scope:** `MovieDetailScreen`, `TvShowDetailScreen`, and supporting components
**Out of scope:** Home screen, library screens (grid column count already done), auth, player, audio player

---

## Goals

1. Redesign the movie and TV show detail screens with a cinematic, immersive aesthetic inspired by Apple TV+ / Disney+
2. Fix the scroll anchor bug (screens opening mid-scroll rather than at the top)
3. Clean up hardcoded color values in `TvMediaCard` and library screens as part of the same pass

---

## Visual Direction

**Cinematic / Immersive** — full-bleed backdrop dominates, content IS the UI. Dark, atmospheric. The backdrop image and content-extracted palette color set the mood for each title. No poster in the hero — the backdrop is the poster.

---

## New File Structure

```
ui/screens/detail/
  MovieDetailScreen.kt             ← thin wrapper, unchanged structure
  TvShowDetailScreen.kt            ← thin wrapper, unchanged structure
  DetailScreenComponents.kt        ← shared stateless primitives only
  cinematic/
    CinematicHero.kt               ← full-bleed hero (shared by both screens)
    MovieDetailLayout.kt           ← movie continuous scroll layout
    TvShowDetailLayout.kt          ← TV show split-panel layout
    ExpandableFactsSection.kt      ← progressive disclosure metadata block
```

`DetailScreenComponents.kt` is trimmed: poster/glass-panel components are removed once `CinematicHero` replaces them. `DetailFactCard` and `DetailLabeledMetaItemView` are unified into one composable.

---

## Component Specs

### CinematicHero

Shared by both `MovieDetailLayout` and `TvShowDetailLayout`.

- **Backdrop**: `AsyncImage` full-bleed, `ContentScale.Crop`. Bottom edge fades to `BackgroundDark` via `Brush.verticalGradient`. Palette API color bleeds into gradient tint via existing `LocalCinefinThemeController`
- **Minimum height**: `0.52 * screenHeight` (fills more than half the screen)
- **Logo / Title** (centered, bottom-anchored):
  - If logo image available → `AsyncImage` with `maxHeight = 120.dp`
  - Fallback → large bold text (`headlineLarge`, white, `fontWeight = ExtraBold`)
  - Both fade in with `AnimatedVisibility` + 300ms fade
- **Eyebrow line**: above logo — e.g. `TV SERIES · 4 SEASONS` or `2024 · 2h 18m`, small uppercase, muted (`onSurface.copy(alpha = 0.5f)`)
- **Meta line**: below logo — rating in gold (`titleAccent`), year, genres as `CinefinChip`s
- **Action bar**: frosted glass strip pinned to bottom of hero
  - Background: `chromeSurface` expressive color token with `blur` modifier
  - Border top: `MaterialTheme.colorScheme.border` at `1.dp`
  - Buttons: Play/Resume (red `CinefinRed` fill), then secondary actions (Episodes for TV shows, Watchlist, Trailer)
- **Scroll anchor fix**: `LazyListState.scrollToItem(0)` in `LaunchedEffect(itemId)`. `FocusRequester.requestFocus()` for the primary action button fires only after scroll settles (use `snapshotFlow { listState.isScrollInProgress }.first { !it }`)

---

### MovieDetailLayout

`LazyColumn` below `CinematicHero`. `LazyListState` passed in from screen for scroll anchor fix.

Sections (top to bottom):

1. **Description** — `bodyLarge` (18sp), max 4 lines, "Show more" expand toggle. Full width, horizontal padding `LocalCinefinSpacing.current.gutter`
2. **ExpandableFactsSection** — collapsed by default (shows `Director · Studio · Language` summary line). D-pad select or click expands to `FlowRow` of `DetailFactCard`s. `AnimatedVisibility` with 250ms vertical expand
3. **Genre chips** — `LazyRow` of `CinefinChip`s, always visible
4. **Cast** — `CinefinShelfTitle` + `LazyRow` of `TvPersonCard`s
5. **Similar Movies** — `CinefinShelfTitle` + `LazyRow` of `TvMediaCard`s (landscape aspect)

---

### TvShowDetailLayout

`CinematicHero` at top. Below: fixed `Row` filling remaining screen height.

**Left Rail** (`width = 200.dp`):
- Items: Episodes, Cast, Similar, Details
- Selected item: red left border (`2.dp`, `CinefinRed`) + full-brightness text
- Unselected: muted text (`onSurface.copy(alpha = 0.45f)`)
- D-pad: up/down within rail, right moves focus to right panel
- `focusProperties { right = firstItemInPanelFocusRequester }`

**Right Panel** (fills remaining width):

- **Episodes tab**:
  - Season selector: `LazyRow` of pill `CinefinChip`s (one per season). Current season pre-selected
  - Episode list: `LazyColumn` of episode rows
  - Episode row: thumbnail (16:9, `72.dp` height) + episode number + title + duration + progress bar (if partially watched) + `NEXT` badge on resume target
  - List auto-scrolls to resume episode on entry

- **Cast tab**: `LazyVerticalGrid(GridCells.Fixed(4))` of `TvPersonCard`s

- **Similar tab**: `LazyVerticalGrid(GridCells.Fixed(4))` of `TvMediaCard`s (portrait aspect)

- **Details tab**: `ExpandableFactsSection` (same component as movies) + full description + genre chips

- **Panel transitions**: `AnimatedContent` with 200ms `fadeIn`/`fadeOut` when switching tabs

**Scroll anchor fix**: On entry (`LaunchedEffect(itemId)`), rail resets to Episodes tab, right panel scrolls to top, focus lands on primary action button in hero.

---

### ExpandableFactsSection

Used in both `MovieDetailLayout` (section 2) and `TvShowDetailLayout` (Details tab).

- **Collapsed state**: single `Row` summary — `Director · Studio · Language` — with a chevron icon. Focusable
- **Expanded state**: `FlowRow` of `DetailFactCard`s (Director, Studio, Country, Language, Rating, Runtime, Genre)
- **Transition**: `AnimatedVisibility` with `expandVertically` + `fadeIn` (250ms, `FastOutSlowIn` easing from `LocalCinefinMotion`)
- Reuses existing `DetailFactCard` composable from `DetailScreenComponents.kt`

---

## Design System Cleanup

### `CinefinExpressiveColors` + `Color.kt`
- Add `watchedGreen: Color = Color(0xFF2E7D32)` token

### `TvMediaCard.kt`
- Replace `Color(0xFF2E7D32)` → `LocalCinefinExpressiveColors.current.watchedGreen`
- Replace `Color.White.copy(alpha = 0.12f)` → `LocalCinefinExpressiveColors.current.focusGlow.copy(alpha = 0.12f)`

### Library screens
- Replace hardcoded `56.dp` horizontal padding → `LocalCinefinSpacing.current.gutter`
  (confirm correct value — gutter is currently `48.dp`)

### `DetailScreenComponents.kt`
- Unify `DetailFactCard` and `DetailLabeledMetaItemView` into one composable with a `style: FactDisplayStyle` parameter (`FactDisplayStyle.Card` vs `FactDisplayStyle.Inline`)
- Remove `DetailHeroBox`, `DetailGlassPanel`, `DetailTitleLogo` once `CinematicHero` replaces them

---

## Scroll Anchor Fix (detail)

**Root cause**: `FocusRequester.requestFocus()` is called on an item that is off-screen, causing the `LazyColumn` to scroll to it. This happens because focus is requested before the list has a chance to settle at position 0.

**Fix pattern** (applied to both screens):

```kotlin
val listState = rememberLazyListState()
val focusRequester = remember { FocusRequester() }

LaunchedEffect(itemId) {
    listState.scrollToItem(0)
    snapshotFlow { listState.isScrollInProgress }
        .first { !it }
    try { focusRequester.requestFocus() } catch (_: Exception) {}
}
```

Both `MovieDetailScreen` and `TvShowDetailScreen` own their `LazyListState` and pass it into their respective layout composables.

---

## D-pad Navigation Notes

- TV Material3 components handle most focus traversal automatically
- Explicit `focusProperties` needed at the hero → content boundary and rail → panel boundary in `TvShowDetailLayout`
- All new interactive composables must be `focusable()` with `onFocusChanged` for visual feedback
- Minimum touch/focus target: `48.dp` (TV safe)

---

## Out of Scope

- Home screen carousel / shelves
- Library grid screens (5-column change already shipped)
- Auth screens
- Player / audio player
- Search screen
- Settings screen
- New API calls or ViewModel changes (layout only — existing `uiState` data is sufficient)
