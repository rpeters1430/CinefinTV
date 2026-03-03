# CinefinTV Status Snapshot

**Date:** 2026-03-02 (updated)
**Status:** MVP feature-complete, running on device, build clean

---

## Current State

The app is fully functional end-to-end on a real Android TV device connected to a live Jellyfin server. All major navigation destinations are implemented and data-backed. Multiple device-test bug rounds have been completed. The build is clean.

### Build and project health

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Android SDK path and Gradle memory configured locally
- No compile errors; pre-existing warnings only (Hilt deprecation on `hiltViewModel` import, unnecessary safe call in stream repo)

### Verified on device (screenshots 2026-03-02)

- **ServerConnectionScreen** — URL input + Continue, dark theme, TV keyboard
- **LoginScreen** — Username/password fields, Sign In / Back, Quick Connect panel
- **HomeScreen** — Carousel (title/year/description/Play/More Info), Continue Watching + Recently Added rows, landscape thumbnails, centered card text
- **LibraryScreen** — 4-column grid with landscape backdrop images for Movies and TV Shows
- **DetailScreen** — Backdrop image, title, Play / Back buttons (lean but functional)
- **Auth flow** — Full login round-trip working; saved-session restore on cold start

---

## Implemented screens and features

### Auth
- `ServerConnectionScreen` — server URL input, connection test
- `LoginScreen` — username/password, error handling
- **Quick Connect** ✅ — mode-switch panel; large spaced code display (`8    3    7    4`); `New Code` / `Cancel`; polls ViewModel every 3s; handles Pending / Approved / Denied / Expired states; error shown first in `when` block (ViewModel does not clear code on denial)
- `AuthViewModel` wired to `JellyfinAuthRepository`
- **Saved-session restore** — `JellyfinServer` serialized to DataStore on login; `tryRestoreSession()` seeds in-memory state on cold start; app skips auth if session is already active

### Home
- `HomeScreen` + `HomeViewModel`
- Sections: Continue Watching, Recently Added TV Episodes, Recently Added Movies, Recently Added Videos, Recently Added Music
- Parallel async fetches; images via `getLandscapeImageUrl` (landscape episode stills / backdrops)
- Featured carousel auto-scrolls every 6s; explicit `color = onBackground` on title text

### Library
- `LibraryScreen` + `LibraryViewModel`
- Categories: Movies, TV Shows, Stuff (home videos)
- 4-column `LazyVerticalGrid`; landscape backdrop images via `getLandscapeImageUrl`
- Shared screen routed by `LibraryCategory` enum

### Detail
- `DetailScreen` + `DetailViewModel`
- Full-bleed backdrop, item title, Play and Back actions
- Lean implementation — no cast row, no seasons browser yet

### Search
- `SearchScreen` + `SearchViewModel`
- Debounced query, `LazyVerticalGrid` of `TvMediaCard` results

### Music
- `MusicScreen` + `MusicViewModel`
- Albums / Artists tab toggle (TV Button row)
- Grid → AlbumDetail (track list) → play track via `player/{itemId}`
- `MusicViewType` enum; `MusicUiState`: Loading | Grid | AlbumDetail | Error(message, viewType)
- Error retry preserves the tab that was active when the error occurred

### Player
- `PlayerScreen` with fullscreen `PlayerView` (`useController = false`)
- Custom TV controls overlay: Back + title (top), −10s / play-pause / +10s (center), progress bar + timestamp (bottom)
- Controls auto-hide after 3 seconds; any button press resets the timer
- Playback state (isPlaying, position, duration) polled every 500ms locally
- `OkHttpDataSource` so auth interceptors apply to all streams

### Supporting infrastructure
- `CinefinTvTheme` — always-dark color scheme (`darkColorScheme`), min 18sp body text
- `TvMediaCard` — `Surface`-based (not `Button`), `RoundedCornerShape(12.dp)`, centered title/subtitle text, landscape image slot
- `JellyfinStreamRepository.getLandscapeImageUrl()` — episodes use episode `Primary` still; movies/series prefer `Backdrop`, fall back to `Primary`
- `CinefinTvApp` TabRow — `onFocus = {}` (navigation only on D-pad OK press); TabRow hidden on `auth/`, `player/`, `detail/` routes
- `JellyfinRepositoryCoordinator` — media, stream, search, auth, user repos
- `AudioService` stub in manifest (no audio player UX yet)

---

## Bug fixes completed (device testing 2026-03-02)

| Bug | Fix |
|---|---|
| App navigated to Search screen on login | `Tab.onFocus` was firing on initial focus; replaced with `onFocus = {}` — navigation only on `onClick` |
| Clicking a home card navigated to Search instead of Detail | `detail/` routes not excluded from `showNav`; TabRow received focus and routed to Search tab; fixed by adding `!currentRoute.startsWith("detail/")` to `showNav` |
| Media cards clipped to circles | `TvMediaCard` used `Button` (defaults to `RoundedCornerShape(50%)`); replaced with `Surface` + explicit `RoundedCornerShape(12.dp)` |
| Card text black / unreadable | `Button` container overrode `LocalContentColor`; `Surface` inherits `colorScheme.onSurface` (light in dark theme) |
| Carousel title text black | TV Material3 `Carousel` sets `LocalContentColor` from a transparent container (resolves to black); added `color = MaterialTheme.colorScheme.onBackground` explicitly |
| Card images were portrait posters in landscape slots | `getSeriesImageUrl` returned portrait primary images; replaced with `getLandscapeImageUrl` |
| Card text left-aligned | Added `textAlign = TextAlign.Center` + `Modifier.fillMaxWidth()` to title and subtitle `Text`; `horizontalAlignment = CenterHorizontally` on text `Column` |

---

## Known issues / future work

- Detail screen improvements: synopsis, cast row, seasons browser for TV shows
- Audio player UX (AudioService is a stub; no playback UI)
- Player improvements: track/subtitle selection, next-episode auto-play
- DI/scope cleanup — copied data layer still includes mobile-era modules
- Raw filenames shown as card titles for home-video items — server metadata issue, not a code bug
