# Detail & Library UI Redesign

**Date:** 2026-03-19
**Scope:** Library screens (Movie, TV Show, Stuff), Detail screens (Movie, TV Show, Season, Episode, Stuff)

---

## Goals

1. Library screens: switch to 3-column 16:9 horizontal card grid
2. Detail screens: replace current vertical-scroll layout with a cinematic B1 hero + LazyColumn content row pattern
3. Season screen: replace the no-op poster header with a mini B1 hero strip + 4-column episode grid
4. Fix all D-pad navigation issues: replace `verticalScroll(Column)` + nested `LazyRow` anti-pattern, add `FocusRequester` wiring, seed initial focus on Play button

---

## Library Screens

**Affected files:**
- `ui/screens/library/MovieLibraryScreen.kt`
- `ui/screens/library/TvShowLibraryScreen.kt`
- `ui/screens/library/StuffLibraryScreen.kt`

**Changes:**
- `GridCells.Adaptive(minSize = 160.dp)` → `GridCells.Fixed(3)`
- Pass `aspectRatio = 16f / 9f` to every `TvMediaCard` in the grid
- All other card properties (title, subtitle, watchStatus, playbackProgress, unwatchedCount) unchanged

---

## Detail Screen Architecture — Hero + LazyColumn

All detail screens (Movie, TV Show, Episode, Stuff) follow the same structural pattern:

### Hero Section (first LazyColumn item)

The hero is a `Box` that fills the screen height (`Modifier.fillMaxWidth().aspectRatio(16f / 9f)` or a fixed height of ~360.dp on a 1080p screen). It is the **first item** in the `LazyColumn`, not a fixed block outside it.

```
Box(fillMaxWidth, height ≈ 360.dp) {
  AsyncImage(backdrop, ContentScale.Crop, fillMaxSize)

  // Fraction-based gradients (NOT absolute pixel values):
  // Vertical: transparent (0%) → bg×0.5 (60%) → bg (100%)
  // Horizontal: bg (0%) → bg×0.8 (30%) → transparent (100%)
  Box(fillMaxSize, verticalGradient(fractions))
  Box(fillMaxSize, horizontalGradient(fractions))

  // Content anchored bottom-left, width capped at 55%:
  Column(Alignment.BottomStart, padding = 48dp horiz, 32dp bottom) {
    title (displayMedium, bold, 2-line max)
    metadata row (year · rating badge · duration · ★ score)  // spaced 16dp
    overview (bodyLarge, 3 lines max, color = onSurfaceVariant)
    action buttons Row (16dp gap)
  }
}
```

**Gradient implementation note:** Use fraction-based stops (`0f to Color, 0.6f to Color, 1f to Color`) rather than absolute pixel `endY`/`endX` values so gradients adapt correctly across screen resolutions.

### Content Section (remaining LazyColumn items)

- Each content section (Seasons, Cast, Similar, Chapters, Items) is a **separate lazy item**: a `Column(verticalArrangement = spacedBy(16.dp))` with a section label + `LazyRow` of cards
- This replaces the current `verticalScroll(rememberScrollState())` + nested `LazyRow` pattern
- Focus-aware scrolling is automatic with `LazyColumn`

### D-Pad Focus — Initial Seed Only

Use `FocusRequester` only to seed initial focus on the Play button when the screen first loads. Do not attempt to manually wire every card's `focusProperties { down = ... }` — Compose's default D-pad traversal handles inter-section navigation correctly when content is inside a `LazyColumn`.

```kotlin
val playButtonFocusRequester = remember { FocusRequester() }

// On content load:
LaunchedEffect(Unit) { playButtonFocusRequester.requestFocus() }

// Play button modifier:
Modifier.focusRequester(playButtonFocusRequester)
```

### BackHandler

Each screen includes an unconditional `BackHandler(onBack = onBack)`. This replaces the "Back" button in the `ErrorState` composable (which can be removed or kept as a fallback).

### Portrait Cards in LazyRow

`TvMediaCard` in a `LazyRow` must have an explicit `cardWidth` when using portrait aspect ratio (2:3), otherwise `fillMaxWidth()` resolves to unbounded width and crashes. Use `cardWidth = 120.dp` for cast/person portrait cards. Wide (16:9) cards in a `LazyRow` similarly need `cardWidth = 200.dp`.

---

## Screen-by-Screen Specs

### MovieDetailScreen

**Hero buttons:** `Play` (primary, seeds initial focus) · `Trailer` (outline, placeholder TODO)

**Content rows:**
1. Cast & Crew — portrait `TvMediaCard`s (`aspectRatio = 2f/3f`, `cardWidth = 120.dp`)
2. Similar Movies — wide `TvMediaCard`s (`aspectRatio = 16f/9f`, `cardWidth = 200.dp`)

Both rows hidden if empty.

**No changes to:** `MovieDetailViewModel`, `MovieDetailUiState`, `MovieDetailModel`

---

### TvShowDetailScreen

**Hero buttons:**
- If `show.nextUpEpisodeId != null`: `"Play Next Up: ${show.nextUpTitle}"` → `onPlayEpisode(show.nextUpEpisodeId)` (primary, seeds focus)
- Else: `"Browse Seasons"` → `onOpenSeason(seasons.first().id)` (primary, seeds focus, disabled if seasons empty)

**Content rows:**
1. Seasons — wide `TvMediaCard`s (`aspectRatio = 16f/9f`, `cardWidth = 200.dp`), `unwatchedCount` badge shown, `onClick = onOpenSeason(season.id)`
2. Cast & Crew — portrait `TvMediaCard`s (`aspectRatio = 2f/3f`, `cardWidth = 120.dp`)
3. More Like This — wide `TvMediaCard`s (`aspectRatio = 16f/9f`, `cardWidth = 200.dp`)

All rows hidden if empty.

**No changes to:** `TvShowDetailViewModel`, `TvShowDetailUiState`, `TvShowDetailModel`

---

### SeasonScreen — S2 Mini Hero

**Architectural note:** The current `SeasonScreen` uses a `Box(fillMaxSize)` root with a full-screen `AsyncImage` backdrop behind all content. The redesign replaces this with a `Column(fillMaxSize)` root where the backdrop image is scoped only to the mini hero strip `Box`. The episode grid below the hero has a plain background, not a backdrop.

**Structure:**
```
Column(fillMaxSize) {
  // Mini hero strip (~35% screen height, fixed at ~220.dp on 1080p)
  Box(fillMaxWidth, height = 220.dp) {
    AsyncImage(backdrop, fillMaxSize, ContentScale.Crop)
    // Fraction-based gradients (replace current hardcoded endY = 800f):
    // Vertical: Brush.verticalGradient(0f to Transparent, 0.5f to bg×0.7f, 1f to bg)
    // Horizontal: Brush.horizontalGradient(0f to bg×0.8f, 0.6f to Transparent)
    Column(Alignment.BottomStart, padding = 48dp horiz, 24dp bottom) {
      Text(seriesName, titleMedium, secondary color)
      Text(seasonTitle, displaySmall, bold)
      Text("${episodes.size} Episodes", bodyLarge, muted)  // from episodes list size
      Text(season.overview, bodyLarge, muted, maxLines=2)  // if available
      Spacer(12.dp)
      Button("Play Next", onClick = { onOpenEpisode(nextEpisodeId!!) })  // reuses existing callback
    }
  }

  Text("Episodes", headlineSmall, semibold, padding = 16dp horiz + 12dp top)

  // Episode grid fills all remaining space
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = Modifier.weight(1f).fillMaxWidth(),  // REQUIRED: weight(1f) not fillMaxSize
    contentPadding = PaddingValues(horizontal=48.dp, bottom=48.dp)
  ) {
    items(episodes) { episode ->
      TvMediaCard(
        aspectRatio = 16f / 9f,
        watchStatus = ...,
        playbackProgress = ...,
        onClick = { onOpenEpisode(episode.id) }
      )
    }
  }
}
```

**"Play Next" logic (computed in screen, no ViewModel or NavGraph change):**
```kotlin
val nextEpisodeId = remember(episodes) {
  episodes.firstOrNull { !it.isWatched }?.id ?: episodes.firstOrNull()?.id
}
// Button disabled if nextEpisodeId == null (empty season)
// Uses existing onOpenEpisode callback — navigates to EpisodeDetailScreen,
// not directly to the player. No new callback or NavGraph wiring needed.
```

**Remove:** The current no-op `Card` wrapping the season poster (lines 114–130 of current SeasonScreen.kt) — it was an accidental D-pad focus trap. If the poster image is wanted decoratively, use a plain `AsyncImage` with no click handler.

**No changes to:** `SeasonViewModel`, `SeasonUiState`, `SeasonDetailModel`, `EpisodeModel`

---

### EpisodeDetailScreen

**Hero buttons:** Single `"Play"` button → `onPlayEpisode(episode.id, null)` (seeds initial focus).

No Resume/Play-from-beginning split — `EpisodeDetailModel` does not expose a `playbackProgress` field, and adding one is out of scope for this redesign. The `isWatched` badge in the metadata row is sufficient to indicate watched state.

**Content rows:**
1. Chapters — wide `TvMediaCard`s (`aspectRatio = 16f/9f`, `cardWidth = 240.dp`), subtitle = formatted timestamp, `onClick = onPlayEpisode(episode.id, chapter.positionMs)`

Only shown if `chapters.isNotEmpty()`.

**No changes to:** `EpisodeDetailViewModel`, `EpisodeDetailUiState`, `EpisodeDetailModel`, `ChapterModel`

---

### StuffDetailScreen

**Two cases based on `stuff.isCollection`:**

**Single video (`!stuff.isCollection`):**
- Full B1 hero layout (hero fills ~360.dp)
- Hero button: `"Play"` → `onPlayItem(stuff.id)` (seeds initial focus)
- No content rows below

**Collection (`stuff.isCollection`):**
- Compact hero (title + overview in hero, **no play button**)
- Below hero: `"Items"` section label + `LazyVerticalGrid`:
  ```kotlin
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    modifier = Modifier.weight(1f).fillMaxWidth(),
  ) {
    items(items) { item ->
      TvMediaCard(
        aspectRatio = 16f / 9f,  // wide cards
        watchStatus = item.watchStatus,
        playbackProgress = item.playbackProgress,
        onClick = { onOpenItem(item.id, item.itemType) }
      )
    }
  }
  ```
- Empty state: centered `Text("No items in this collection")` if `items.isEmpty()`

**Note:** `StuffDetailScreen` uses a `Column(fillMaxSize)` outer container (not `LazyColumn`) for the collection case because the grid itself is lazy. The `weight(1f)` modifier on the grid is required.

**No changes to:** `StuffDetailViewModel`, `StuffDetailUiState`, `StuffDetailModel`, `StuffItemModel`

---

## Component Changes

### TvMediaCard

No API changes required. Callers pass explicit `aspectRatio` and `cardWidth` parameters as needed (both already exist on the component).

### TvPersonCard

The existing `TvPersonCard` component is removed from detail screens and replaced with `TvMediaCard(aspectRatio = 2f/3f, cardWidth = 120.dp)` for consistency. Person navigation remains a TODO placeholder.

---

## Files To Modify

| File | Change |
|---|---|
| `ui/screens/library/MovieLibraryScreen.kt` | Fixed(3) grid, 16:9 cards |
| `ui/screens/library/TvShowLibraryScreen.kt` | Fixed(3) grid, 16:9 cards |
| `ui/screens/library/StuffLibraryScreen.kt` | Fixed(3) grid, 16:9 cards |
| `ui/screens/detail/MovieDetailScreen.kt` | Full rewrite to B1 hero + LazyColumn |
| `ui/screens/detail/TvShowDetailScreen.kt` | Full rewrite to B1 hero + LazyColumn |
| `ui/screens/detail/SeasonScreen.kt` | Mini hero strip, weight(1f) grid, fix focus trap |
| `ui/screens/detail/EpisodeDetailScreen.kt` | Full rewrite to B1 hero + LazyColumn |
| `ui/screens/detail/StuffDetailScreen.kt` | Full rewrite to B1 hero + LazyColumn |

**No ViewModel, repository, model, or navigation changes required.**

---

## TV-Specific Constraints

- All `@OptIn(ExperimentalTvMaterial3Api::class)` annotations required (file-level preferred)
- Do not use `TvLazyRow` / `TvLazyColumn` — use standard `LazyRow`, `LazyColumn`, `LazyVerticalGrid`
- Minimum 18sp body text for 10-foot viewing (existing typography styles already comply)
- D-pad Back via `BackHandler`
- `LazyVerticalGrid` inside a `Column` requires `Modifier.weight(1f)` on the grid — never `fillMaxSize()` in this context
- Cards in `LazyRow` must have explicit `cardWidth` to avoid unbounded width measurement crash
- Gradients use fraction-based stops (0f–1f), never absolute pixel `endY`/`endX` values
