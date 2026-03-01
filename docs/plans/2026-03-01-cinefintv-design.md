# CinefinTV — Android TV App Design

**Date:** 2026-03-01
**Status:** Approved

## Overview

CinefinTV is a standalone Android TV Jellyfin client built with Jetpack Compose and
`androidx.tv:tv-material`. It is a separate app from the original Cinefin mobile app,
targeting Android TV / Google TV devices exclusively. The data layer is copied from the
original Cinefin app and the TV UI is built fresh using Material TV components.

---

## 1. Project Structure

```
CinefinTV/
├── app/
│   ├── src/main/
│   │   ├── data/              ← copied from original, minimal changes
│   │   │   ├── model/
│   │   │   ├── network/
│   │   │   ├── repository/
│   │   │   ├── preferences/
│   │   │   └── session/
│   │   ├── di/                ← copied from original (Hilt modules)
│   │   ├── core/              ← copied from original (Logger, ErrorHandler, etc.)
│   │   └── ui/
│   │       ├── theme/         ← NEW: TvMaterialTheme, TV color scheme
│   │       ├── navigation/    ← NEW: TV nav graph (NavigationDrawer)
│   │       ├── components/    ← NEW: TvMediaCard, focus helpers, shared components
│   │       └── screens/       ← NEW: all TV screens
│   │           ├── auth/
│   │           ├── home/
│   │           ├── library/
│   │           ├── detail/
│   │           ├── search/
│   │           ├── music/
│   │           └── player/
│   └── build.gradle.kts
├── docs/plans/
├── build.gradle.kts
└── settings.gradle.kts
```

### What is copied from the original (no/minor changes)
- `data/model/`, `data/network/`, `data/repository/`, `data/preferences/`, `data/session/`
- `di/` — all Hilt modules
- `core/` — Logger, ErrorHandler, FeatureFlags, Constants

### What is stripped out (not needed for MVP)
- `OfflineDownloadManager`, `OfflinePlaybackManager`, background workers
- `GenerativeAiRepository` and all AI modules
- `CastManager` and all Cast modules
- `BiometricAuthManager` (TV has no biometrics)
- `OfflineScreen`, `DownloadsScreen`, `AiAssistantScreen`

### What is built fresh
- Everything under `ui/` — theme, navigation, all screens, all components

---

## 2. Navigation & TV UX

### Pattern
Persistent side `NavigationDrawer` (androidx.tv). Collapses to a narrow icon rail when
not focused, expands when d-pad left is pressed from the content area.

```
┌──────┬────────────────────────────────────┐
│  🏠  │                                    │
│  🔍  │         Main Content Area          │
│  🎬  │         (focus-managed)            │
│  📺  │                                    │
│  🎞  │                                    │
│  🎵  │                                    │
└──────┴────────────────────────────────────┘
  Rail         Scaffold content
```

### Nav Destinations

| Destination | Icon            | Route              | Nav item |
|-------------|-----------------|--------------------|----------|
| Home        | Home            | `home`             | yes      |
| Search      | Search          | `search`           | yes      |
| Movies      | Movie           | `library/movies`   | yes      |
| TV Shows    | Tv              | `library/tvshows`  | yes      |
| Stuff       | VideoLibrary    | `library/stuff`    | yes      |
| Music       | MusicNote       | `library/music`    | yes (if library exists) |
| Detail      | —               | `detail/{itemId}`  | no       |
| Player      | —               | `player/{itemId}`  | no       |
| Login       | —               | auth nav graph     | no       |

### D-pad Focus Rules
- `FocusRequester` on first content item of each screen — auto-focus on navigation
- `FocusGroup` wraps card rows so left/right d-pad stays within the row
- Standard Compose `LazyRow`, `LazyColumn`, `LazyVerticalGrid` with `rememberLazyListState`
  handle focus routing (TvLazyRow/TvLazyColumn are deprecated)
- `androidx.tv` components used: `Carousel`, `ImmersiveList`, `NavigationDrawer`, `Card`, `Surface`
- Back button navigates up; from Home it shows an exit confirmation dialog

### TV Activity Setup
- Full-screen, no action bar, forced landscape
- `android:launchMode="singleTask"`
- `LEANBACK_LAUNCHER` intent filter so it appears in the TV launcher grid

---

## 3. Theme & Visual Design

### Library
`androidx.tv:tv-material` — `TvMaterialTheme` wraps the entire app.

### Color Scheme (always dark — no light theme on TV)
```kotlin
TvMaterialTheme(
    colorScheme = darkColorScheme(
        primary         = Color(0xFFE50914),   // Cinefin red accent
        onPrimary       = Color.White,
        background      = Color(0xFF0D1117),   // deep navy-black (not flat black)
        onBackground    = Color.White,
        surface         = Color(0xFF161B22),   // card/surface background
        onSurface       = Color(0xFFE6EDF3),
        surfaceVariant  = Color(0xFF21262D),   // container backgrounds
        onSurfaceVariant = Color(0xFF8B949E),
    )
)
```

### Typography
- Uses `TvTypography` from `tv-material` — already TV-scaled
- Minimum body text: 18sp for readability at 10-foot viewing distance
- Display text for hero titles: 48–57sp

### Card Focus States
`androidx.tv` `Card` handles focus indication natively — glow/border appears on focused
card via `CardDefaults`. No custom focus indication needed.

---

## 4. Screens & Components

### Home Screen
Two zones:
1. **Hero Carousel** (`androidx.tv` `Carousel`) — cycles through recently added / continue
   watching items. Full-width with backdrop image + title/metadata overlay and gradient scrim.
2. **Content rows** (`LazyColumn` of `LazyRow`s):
   - Continue Watching
   - Recently Added — Movies
   - Recently Added — TV Shows
   - Recently Added — Stuff (Home Videos)
   - Recently Added — Music *(only if music library present)*
   - Libraries

### Library Screens (Movies / TV Shows / Stuff)
- `LazyVerticalGrid` of `TvMediaCard`s
- Poster image, title below
- Stuff screen: same grid, filtered to home video library type

### Music Screen
- Top level: `LazyVerticalGrid` of Artist or Album cards (toggle between views)
- Album detail: tracklist as `LazyColumn`
- Playback: audio player bottom overlay — artwork, track title, artist, play/pause, seek,
  previous/next. Uses existing `TvAudioPlayerControls` logic adapted for Compose TV.

### Detail Screen (Movie / TV Show / Home Video)
- Full-bleed backdrop behind content overlay
- Title, year, rating, genre badges
- Action buttons: Play, Shuffle (TV shows only), Trailer
- Overview text
- Cast row (`LazyRow`)
- TV shows: seasons tabs + episode list (`LazyColumn`)

### Search Screen
- System TV keyboard via `SearchView` pattern
- Results in `LazyVerticalGrid`
- Filters: All / Movies / TV Shows / Stuff / Music / People

### Player Screen
- Full-screen ExoPlayer surface
- Auto-hide overlay controls (5s timeout):
  - Play/Pause, seek bar with elapsed/remaining time
  - Skip back 10s / forward 30s
  - Audio track selection, Subtitle track selection
  - Title + episode info
- D-pad: center = play/pause, left/right = seek, up = show controls

### Shared Component: `TvMediaCard`
Single composable used on every screen:
- Poster image via Coil
- Progress bar overlay (if item is in-progress)
- Watch status badge (watched checkmark)
- Focus scale animation (built into `androidx.tv` `Card`)
- `onClick` navigates to detail or plays directly (for episodes)

---

## 5. Data Flow & Architecture

### Pattern
MVVM with StateFlow, same as the original app.

```
Screen (Composable)
  └── ViewModel (StateFlow<UiState>)
        └── Repository (copied from original)
              └── Jellyfin SDK / Retrofit / DataStore
```

### ViewModels
| ViewModel          | Feeds                            | Key repositories used           |
|--------------------|----------------------------------|---------------------------------|
| `HomeViewModel`    | Home screen rows + carousel      | `JellyfinRepository`            |
| `LibraryViewModel` | Movie/TV/Stuff grid (paged)      | `JellyfinRepository`, `Paging3` |
| `DetailViewModel`  | Item detail, cast, seasons/eps   | `JellyfinRepository`            |
| `SearchViewModel`  | Search results (debounced)       | `JellyfinSearchRepository`      |
| `PlayerViewModel`  | Video playback state             | `JellyfinStreamRepository`      |
| `MusicViewModel`   | Artists, albums, tracks, audio   | `JellyfinRepository`            |

### Auth Flow
Unchanged from original:
1. Server URL entry screen
2. Token login or Quick Connect
3. Token stored in `SecureCredentialManager`
4. `JellyfinSessionManager` manages session lifecycle
5. On token expiry → navigate back to login

### Libraries and Min SDK
```kotlin
// build.gradle.kts
minSdk = 26    // Android 8.0 — same as original
targetSdk = 35
compileSdk = 35

implementation("androidx.tv:tv-material:1.0.0")
implementation("androidx.tv:tv-foundation:1.0.0")
// All other deps mirror the original app (Media3, Coil, Hilt, Jellyfin SDK, etc.)
// Removed: Cast framework, Leanback, WorkManager (download workers)
```

---

## 6. Out of Scope (MVP)

- Offline downloads
- Chromecast
- AI assistant
- Biometric authentication
- Trailer playback (detail screen shows button, wired up post-MVP)
- Settings screen (uses app defaults; post-MVP)
- Firebase / Analytics (added post-MVP)
