# Detail Screens — Plex-Style Redesign

**Date:** 2026-04-19  
**Scope:** Movie Detail, TV Show Detail, Season/Episode List

## Goals

Redesign the three detail screens to closely match the Plex Android TV app aesthetic:
- Full-bleed cinematic hero with poster floating inside (no separate overview section below)
- Metadata collapsed to a single dot-separated line
- Circular cast headshots (64dp)
- Plex-style episode list rows with synopsis and progress bar

## Screens in Scope

- `MovieDetailScreen` / `MovieDetailLayout`
- `TvShowDetailScreen` / `TvShowDetailLayout`
- `SeasonScreen`

`StuffDetailScreen` is explicitly out of scope.

---

## 1. Cinematic Hero (shared across all three screens)

### Layout
- **Backdrop:** full-bleed, fills the hero container (16:9 aspect ratio on TV)
- **Scrims:** left-to-right gradient (`rgba(10,13,20,0.95)` → transparent, covering ~60% from left); bottom-to-top gradient (`rgba(10,13,20,0.98)` → transparent, covering ~55% from bottom)
- **Poster:** floats bottom-left, `~64dp` wide, `2:3` aspect ratio, `8dp` corner radius, drop shadow. Positioned `18dp` from left and bottom edges.
- **Content block:** sits to the right of the poster, anchored to the bottom of the hero, `18dp` from bottom, `84dp` from left (clears the poster), `20dp` right margin

### Content block (top to bottom)
1. **Title** — `28sp`, `FontWeight.ExtraBold`, white, `lineHeight = 1.0`, `letterSpacing = -0.5sp`
2. **Metadata line** — single row, dot-separated: `Year • Duration • Rating • [quality badges]`
   - Text items: `14sp`, `#BBBBBB`
   - Separator: `•`, `#555555`
   - Quality badges (`4K`, `HDR`, `DV`, etc.): small pill, `12sp`, `FontWeight.Bold`; 4K = white tint; HDR = amber tint; DV = purple tint. Only shown when the stream has the relevant flag.
3. **Genre line** — genres joined by ` · ` separators, `13sp`, `#888888`
4. **Overview** — max 3 lines, `13sp`, `#999999`, `lineHeight = 1.5`. Ellipsized at end.
5. **Action buttons** — horizontal row, `8dp` gap:
   - **Play** (or **Resume**): `background = CinefinRed (#E50914)`, white text, `16sp FontWeight.Bold`, `8×24dp` padding, `8dp` radius. Label is "Resume" when `canResume()` is true.
   - **Watchlist** (`+ Watchlist` or `✓ In Watchlist`): subtle container (`rgba(255,255,255,0.10)`, 1dp border `rgba(255,255,255,0.15)`), `14sp`, `8×16dp` padding, `8dp` radius.
   - **More** (`···`): same subtle container, square, `8×12dp` padding. Opens the existing `MediaActionDialog`.

### What is removed
- `DetailOverviewSection` composable is **no longer rendered** on Movie or TV Show detail screens. All info (title, metadata, overview, buttons) now lives inside the hero.
- The separate poster+metadata card that previously appeared below the hero is deleted.

---

## 2. Movie Detail Screen

### Structure (top to bottom, inside a `LazyColumn`)
1. `CinematicHero` (as above)
2. **Cast** — section header "Cast", horizontal `LazyRow` of `PersonCircleCard` (see §4)
3. **More Like This** — existing `DetailShelfPanel` for similar movies, starts immediately after cast with no extra padding

### No changes to
- Navigation, ViewModel, data layer
- `MediaActionDialog` (more menu)
- `DetailShelfPanel` component internals

---

## 3. TV Show Detail Screen

### Structure (top to bottom)
1. `CinematicHero` (as above, using show backdrop/poster)
2. **Seasons selector** — horizontal row of season chips (existing behavior, keep as-is)
3. **Next Up** panel — if applicable (existing behavior, keep as-is)
4. **Cast** — `PersonCircleCard` row
5. **More Like This** — `DetailShelfPanel`

---

## 4. Season / Episode List Screen

### Hero
- Same `CinematicHero` component, using the season backdrop and season poster
- Title = season name (e.g., "Season 1"), metadata = year + episode count
- Action buttons: **Play** (plays from first unwatched episode) + **More**

### Episode List
Replaces the current episode row component with a new `EpisodeListRow` composable.

**Layout per row:**
- `Row`, full width, `10dp` vertical + horizontal padding, `8dp` corner radius
- Left: `160×90dp` thumbnail image (`8dp` radius), loaded via Coil
  - **Progress bar overlay:** if `playbackProgress > 0 && !isWatched` — bottom of thumbnail, `4dp` from edges, `3dp` tall, background `rgba(255,255,255,0.15)`, filled portion `CinefinRed`, corner radius `2dp`
  - **Duration badge:** bottom-right of thumbnail, `rgba(0,0,0,0.75)` pill, `12sp`
  - **Watched checkmark:** top-right, `16dp` green circle with `✓`, only when `isWatched`
- Right: `Column`, `flex:1`
  - Episode code: `"S{season} · E{episode}"`, `14sp`, `#888888`
  - Title: `18sp`, `FontWeight.Bold`, white (or `#BBBBBB` when watched)
  - Air date + duration: `14sp`, `#666666`
  - Overview: max 2 lines, `14sp`, `#999999`, ellipsized
- Right edge: **RESUME badge** (`CinefinRed` tint, 1dp border) — shown only when `playbackProgress > 0 && !isWatched`

**Row states:**
- **In-progress:** red-tinted background (`rgba(229,9,20,0.06)`) + red border (`rgba(229,9,20,0.20)`)
- **Watched:** entire row at `alpha = 0.5`
- **Unwatched:** default, no decoration

**D-pad focus:** `EpisodeListRow` must be focusable. On focus: standard `CinefinExpressiveColors.focusRing` border. On click: open `MediaActionDialog` with Play / Mark watched / Delete actions (matching current behavior).

---

## 5. Cast Card — `PersonCircleCard`

Replaces the existing rectangular `TvPersonCard`.

- **Image:** `64dp × 64dp` circle (`clip(CircleShape)`), loaded via Coil, fallback = generic person icon
- **Name:** `14sp`, `FontWeight.SemiBold`, `#DDDDDD`, center-aligned, max 2 lines
- **Role:** `12sp`, `#666666`, center-aligned, max 1 line
- **Total width:** `72dp` (image + centered text)
- **Click:** navigates to person detail screen (existing `onOpenPerson` callback)

---

## 6. Component Map

| Old component | New behavior |
|---|---|
| `CinematicHero` | Updated: poster inside hero, inline metadata, buttons inside hero |
| `DetailOverviewSection` | **Deleted** from Movie + TV Show layouts |
| `TvPersonCard` | **Replaced** by `PersonCircleCard` |
| `EpisodeListRow` (new) | New component for Season screen |
| `MovieDetailLayout` | Remove `DetailOverviewSection`, adjust spacing |
| `TvShowDetailLayout` | Remove `DetailOverviewSection`, adjust spacing |
| `SeasonScreen` | Replace episode rows with `EpisodeListRow` |
| `DetailShelfPanel` | No change |
| `MediaActionDialog` | No change |

---

## 7. Out of Scope

- Player screen
- Home screen
- Library screen
- Audio player
- `StuffDetailScreen`
- Any ViewModel or repository changes
- Any new network calls or data model changes

---

## 8. TV / Accessibility Constraints

- Minimum text: `12sp` for badges, `13sp` for secondary text, `18sp` for episode titles — all meet 10-foot legibility
- No `TvLazyRow` / `TvLazyColumn` (deprecated) — use standard `LazyRow`, `LazyColumn`
- All interactive elements must be focusable and reachable via D-pad
- `@file:OptIn(ExperimentalTvMaterial3Api::class)` on any file using tv-material3 components
