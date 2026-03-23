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
  DetailScreenComponents.kt        ← shared stateless primitives only (trimmed)
  cinematic/
    CinematicHero.kt               ← full-bleed hero (shared by both screens)
    MovieDetailLayout.kt           ← movie continuous scroll layout
    TvShowDetailLayout.kt          ← TV show split-panel layout
    ExpandableFactsSection.kt      ← progressive disclosure metadata block
```

`DetailScreenComponents.kt` is trimmed: `DetailHeroBox`, `DetailGlassPanel`, and `DetailTitleLogo` are removed once `CinematicHero` replaces them. `DetailFactCard` and `DetailLabeledMetaItemView` are unified into a single `MetaFactItem` composable (see Design System Cleanup).

---

## Component Specs

### CinematicHero

Shared by both `MovieDetailLayout` and `TvShowDetailLayout`.

**Backdrop:**
- `AsyncImage` full-bleed, `ContentScale.Crop`
- Bottom edge fades to `BackgroundDark` via `Brush.verticalGradient` (transparent at 40% → `BackgroundDark` at 100%)
- Dynamic color tint: after the backdrop loads, extract a dominant color using the Palette API (same pattern as `HomeScreen.kt` lines 260–289). Call `LocalCinefinThemeController.current.updateSeedColor(extractedColor)` — `LocalCinefinThemeController` is defined in `CinefinTvApp.kt:73` and provides a `ThemeViewModel` with `updateSeedColor(Color?)`. The extracted color bleeds into the Compose color scheme via `rememberDynamicColorScheme` in `CinefinTvTheme`. Clear seed on disposal: `DisposableEffect(itemId) { onDispose { themeController.updateSeedColor(null) } }`

**Size:**
- `Modifier.fillMaxWidth().heightIn(min = (LocalConfiguration.current.screenHeightDp * 0.52f).dp)`

**Logo / Title** (centered, bottom-anchored above the action bar):
- If logo image available → `AsyncImage` with `Modifier.heightIn(max = 120.dp).wrapContentWidth()`
- Fallback → `Text` with `MaterialTheme.typography.displaySmall`, `fontWeight = FontWeight.ExtraBold`, white, centered
- Both wrapped in `AnimatedVisibility(visible = isLoaded, enter = fadeIn(tween(300)))`

**Eyebrow line** (above logo):
- e.g. `TV SERIES · 4 SEASONS` or `2024 · 2h 18m`
- `labelMedium` (TV Material3), `onSurface.copy(alpha = 0.5f)`, `letterSpacing = 1.5.sp`, uppercase

**Meta line** (below logo):
- Rating in `titleAccent` (gold from `LocalCinefinExpressiveColors`), year, genres as `CinefinChip`s (default/muted weight)

**Action bar** (pinned to bottom of hero, `Box` alignment `BottomCenter`):
- Background: `LocalCinefinExpressiveColors.current.chromeSurface` with `alpha = 0.85f`. No blur modifier — use semi-transparent color only, consistent with the existing `DetailGlassPanel` approach. On Android 12+ devices where `RenderEffect` is available this can be enhanced in a future pass.
- Top border: `Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.border, shape = RectangleShape)` — uses `androidx.tv.material3.MaterialTheme` (same import as `DetailScreenComponents.kt`)
- Padding: `horizontal = LocalCinefinSpacing.current.gutter`, `vertical = 16.dp`
- Buttons:
  - Primary: Play / Resume (red fill, `CinefinRed`)
  - Secondary: context-dependent — TV shows get **Episodes**, movies get **Watchlist**. Both screens may show **Trailer** if trailer URL is present
  - Button spacing: `elementGap` token

**Scroll anchor fix** (applied in both `MovieDetailScreen` and `TvShowDetailScreen`, not inside `CinematicHero`):
```kotlin
val listState = rememberLazyListState()
val primaryActionFocus = remember { FocusRequester() }

LaunchedEffect(itemId) {
    listState.scrollToItem(0)
    snapshotFlow { listState.isScrollInProgress }
        .first { !it }
    try { primaryActionFocus.requestFocus() } catch (_: Exception) {}
}
```
`primaryActionFocus` is passed into `CinematicHero` and attached to the primary Play/Resume button.

---

### MovieDetailLayout

`LazyColumn(state = listState)` below `CinematicHero`. `listState` is owned by `MovieDetailScreen` and used for the scroll anchor fix above.

Sections (top to bottom), all with `horizontalPadding = LocalCinefinSpacing.current.gutter`:

1. **Description** — `bodyLarge` (18sp), max 4 lines with "Show more" expand toggle. `AnimatedVisibility` on the overflow text
2. **ExpandableFactsSection** — see spec below
3. **Genre chips** — `LazyRow` of `CinefinChip`s (default weight), always visible
4. **Cast** — `CinefinShelfTitle` (in `CinefinTvPrimitives.kt:286`) + `LazyRow` of `TvPersonCard`s
5. **Similar Movies** — `CinefinShelfTitle` + `LazyRow` of `TvMediaCard`s (landscape `aspectRatio = 16f/9f`)

---

### TvShowDetailLayout

`CinematicHero` at top. Below: `Row(modifier = Modifier.fillMaxSize())`.

> **Scroll anchor fix for TV show layout:** `TvShowDetailLayout` does not wrap its content in a single `LazyColumn`, so the `listState.scrollToItem(0)` pattern is not directly applicable to the outer container. Instead, the screen owns scroll states for each right-panel tab:
> ```kotlin
> val episodeListState = rememberLazyListState()
> val castGridState = rememberLazyGridState()
> val similarGridState = rememberLazyGridState()
> LaunchedEffect(itemId) {
>     selectedTab = TvShowTab.Episodes
>     episodeListState.scrollToItem(0)
>     castGridState.scrollToItem(0)
>     similarGridState.scrollToItem(0)
>     try { primaryActionFocus.requestFocus() } catch (_: Exception) {}
> }
> ```

**Tab state type:**
```kotlin
enum class TvShowTab { Episodes, Cast, Similar, Details }
```
`selectedTab` is a `rememberSaveable { mutableStateOf(TvShowTab.Episodes) }` in `TvShowDetailScreen`.

**Left Rail** (`Modifier.width(200.dp).fillMaxHeight()`):

- `LazyColumn` of 4 items: Episodes, Cast, Similar, Details
- Each item: `Row` with `Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp)`
- Selected: left-edge accent drawn via `Modifier.drawBehind { drawRect(color = CinefinRed, size = Size(4.dp.toPx(), size.height)) }` + `onSurface` color text + `fontWeight = Bold`
- Unselected: `onSurface.copy(alpha = 0.45f)` text
- Right edge: `Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = onSurface.copy(alpha = 0.08f))`
- D-pad: `focusProperties { right = rightPanelFirstItemFocusRequester }` on each rail item

**Right Panel** (`Modifier.weight(1f).fillMaxHeight()`):

```kotlin
AnimatedContent(
    targetState = selectedTab,
    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }
) { tab ->
    when (tab) { ... }
}
```

- **`TvShowTab.Episodes`**:
  - Season selector: `LazyRow` of `CinefinChip`s (one per season). Currently active season uses `strong = true`. Tapping a chip updates `selectedSeasonIndex`
  - Episode list: `LazyColumn(state = episodeListState)`. Auto-scrolls to resume episode on initial load: `LaunchedEffect(selectedSeasonIndex) { episodeListState.animateScrollToItem(resumeEpisodeIndex) }`
  - Episode row: `Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp))` containing:
    - Thumbnail: `AsyncImage`, `Modifier.width(128.dp).height(72.dp)`, `ContentScale.Crop`, rounded `8.dp`
    - Progress bar at bottom of thumbnail if `watchedPercentage > 0`
    - `NEXT` badge: `CinefinChip(label = "NEXT", strong = true)` overlaid `TopEnd` of thumbnail — shown on the first unwatched episode after the last watched episode (or first episode if none watched)
    - Episode number + title: `labelLarge`, white
    - Duration: `labelMedium`, `onSurface.copy(alpha = 0.5f)`

- **`TvShowTab.Cast`**: `LazyVerticalGrid(GridCells.Fixed(4), state = castGridState)` of `TvPersonCard`s (defined in `TvPersonCard.kt`: 172dp circular card, name + role below)

- **`TvShowTab.Similar`**: `LazyVerticalGrid(GridCells.Fixed(4), state = similarGridState)` of `TvMediaCard`s (portrait aspect)

- **`TvShowTab.Details`**: `ExpandableFactsSection` (see below) + full description + genre chips

---

### ExpandableFactsSection

Used in both `MovieDetailLayout` (section 2) and `TvShowDetailLayout` (Details tab). File: `cinematic/ExpandableFactsSection.kt`.

- **Collapsed state**: `Row` showing `Director · Studio · Language` summary + trailing chevron icon. `Modifier.focusable()` with `onFocusChanged` for visual feedback. D-pad select or click triggers expand
- **Expanded state**: `FlowRow` of `MetaFactItem(style = MetaFactStyle.Card, ...)` composables (Director, Studio, Country, Language, Rating, Runtime, Genre)
- **Transition**: `AnimatedVisibility(visible = isExpanded, enter = expandVertically(tween(250, easing = CinefinMotion.Emphasized)) + fadeIn(), exit = shrinkVertically(...) + fadeOut())`
- Uses `CinefinMotion.Emphasized` — the `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` token from `ExpressiveColors.kt:49`.

**Icon mapping for `MetaFactItem` fields** (use `Icons.Default.*` consistent with existing screens):
- Director → `Icons.Default.Movie`
- Studio / Production → `Icons.Default.Apartment`
- Runtime → `Icons.Default.Schedule`
- Year / Released → `Icons.Default.CalendarToday`
- Rating → `Icons.Default.Star`
- Country → `Icons.Default.Language`
- Language → `Icons.Default.Subtitles`
- Genre → `Icons.Default.Category`
- Status (TV) → `Icons.Default.Tv`

---

## Design System Cleanup

### `CinefinExpressiveColors` + `Color.kt`

Add token:
```kotlin
val watchedGreen: Color = Color(0xFF2E7D32)
```
Add to `CinefinExpressiveColors` data class and to `LocalCinefinExpressiveColors` default instance.

### `CinefinSpacing` + `Theme.kt`

Add token:
```kotlin
val gridContentPadding: Dp = 56.dp
```
This is intentionally larger than `gutter` (48.dp) to give edge cards room to scale 1.05–1.1x on focus without clipping. Replace the hardcoded `56.dp` in `MovieLibraryScreen`, `TvShowLibraryScreen`, and `StuffLibraryScreen` with `LocalCinefinSpacing.current.gridContentPadding`.

The `48.dp` `Modifier.padding(48.dp)` on the title/empty-state in those screens is correct as the `gutter` token and stays unchanged.

### `TvMediaCard.kt`

- `Color(0xFF2E7D32)` → `LocalCinefinExpressiveColors.current.watchedGreen`
- `Color.White.copy(alpha = 0.12f)` → `LocalCinefinExpressiveColors.current.focusGlow.copy(alpha = 0.12f)`

### `DetailScreenComponents.kt` — `EpisodeListRow`

- `Color(0xFF2E7D32)` at line 838 → `LocalCinefinExpressiveColors.current.watchedGreen`

### `DetailScreenComponents.kt` — Unify `DetailFactCard` + `DetailLabeledMetaItemView`

Create a single composable replacing both:

```kotlin
enum class MetaFactStyle { Card, Inline }

@Composable
fun MetaFactItem(
    icon: ImageVector,
    label: String,
    value: String,
    style: MetaFactStyle = MetaFactStyle.Card,
    modifier: Modifier = Modifier,
)
```

- `MetaFactStyle.Card` — bordered card tile (current `DetailFactCard` visual): `fillMaxWidth`, `cornerRadius = 18.dp`, border `onSurface.copy(alpha = 0.12f)`, padding `12.dp`
- `MetaFactStyle.Inline` — pill row (current `DetailLabeledMetaItemView` visual): background `surface.copy(alpha = 0.18f)`, `RoundedCornerShape(20.dp)`, padding `horizontal = 14.dp, vertical = 12.dp`, icon 18dp tinted `primary`, label `labelMedium` in `primary`, value `bodyLarge` in `onSurfaceVariant`

Remove `DetailFactCard` and `DetailLabeledMetaItemView`. Update all call sites to `MetaFactItem`.

`ExpandableFactsSection` uses `MetaFactItem(style = MetaFactStyle.Card, ...)`.

### `DetailScreenComponents.kt` — Remove replaced components

After `CinematicHero` is wired up, remove:
- `DetailHeroBox`
- `DetailGlassPanel`
- `DetailTitleLogo`

---

## Scroll Anchor Fix (summary)

**Root cause:** `FocusRequester.requestFocus()` is called on an off-screen item, causing the `LazyColumn` to scroll to it before the screen has settled at position 0.

**Movie screens:** `LazyListState` owned by `MovieDetailScreen`, `scrollToItem(0)` in `LaunchedEffect(itemId)`, focus requested after `isScrollInProgress` settles.

**TV show screens:** No single outer `LazyColumn`. On entry, reset `selectedTab = TvShowTab.Episodes` and call `episodeListState.scrollToItem(0)`. Focus lands on the primary action button in the hero via `primaryActionFocus.requestFocus()`.

---

## D-pad Navigation Notes

- `CinematicHero` action bar: buttons are focusable TV Material3 components; `focusProperties { down = railFirstItemFocusRequester }` on the last button
- Left rail → right panel: `focusProperties { right = rightPanelFirstItemFocusRequester }` on each rail item
- Right panel → left rail: `focusProperties { left = railFocusRequester }` on the first focusable item in each panel
- All new interactive composables: `Modifier.focusable()` + `onFocusChanged` for visual feedback
- Minimum focus target height: `Modifier.defaultMinSize(minHeight = 48.dp)` on all interactive rows
- `CinematicHero` uses `androidx.tv.material3.MaterialTheme` for `colorScheme.border` (same import as `DetailScreenComponents.kt`)

---

## Out of Scope

- Home screen carousel / shelves
- Library grid screens (5-column change already shipped; `gridContentPadding` token is additive only)
- Auth screens
- Player / audio player
- Search screen
- Settings screen
- New API calls or ViewModel changes (layout only — existing `uiState` data is sufficient)
