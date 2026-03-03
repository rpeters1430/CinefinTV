# CinefinTV Status Snapshot

**Date:** 2026-03-02 (updated)
**Status:** MVP feature-complete, running on device, build clean

---

## Current State

The app is fully functional end-to-end on a real Android TV device connected to a live Jellyfin server. All major navigation destinations are implemented and data-backed. The build is clean.

### Build and project health

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- Android SDK path and Gradle memory configured locally
- No compile errors; pre-existing warnings only (Hilt annotation target, redundant Json builder)

### Verified on device (screenshots 2026-03-01)

- **ServerConnectionScreen** — URL input + Continue, dark theme, TV keyboard
- **LoginScreen** — Username/password fields, Sign In / Back, TV keyboard
- **HomeScreen** — "Continue Watching" + "Recently Added Movies" rows with real poster images from server
- **DetailScreen** — Backdrop image, title, Play / Back buttons (lean but functional)
- **Auth flow** — Full login round-trip working against a live Jellyfin server

---

## Implemented screens and features

### Auth
- `ServerConnectionScreen` — server URL input, connection test
- `LoginScreen` — username/password, error handling
- `AuthViewModel` wired to `JellyfinAuthRepository`
- **Saved-session restore** — `JellyfinServer` serialized to DataStore on login; `tryRestoreSession()` seeds in-memory state on cold start; app skips auth if session is already active

### Home
- `HomeScreen` + `HomeViewModel`
- Sections: Continue Watching, Recently Added Movies, Recently Added TV, Recently Added Videos, Libraries
- Parallel async fetches; images via `getSeriesImageUrl`

### Library
- `LibraryScreen` + `LibraryViewModel`
- Categories: Movies, TV Shows, Stuff (home videos)
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
- `TvMaterialTheme` — dark color scheme, min 18sp body text
- `NavigationDrawer` sidebar with 6 destinations
- `TvMediaCard` reusable component
- `JellyfinRepositoryCoordinator` — media, stream, search, auth, user repos
- `AudioService` stub in manifest (no audio player UX yet)

---

## Known issues observed in device testing

| Issue | Severity | Notes |
|---|---|---|
| Library cards on Home navigate to DetailScreen showing "Movies" / "Shows" as title | Medium | Home adds a "Libraries" section; tapping a library card routes to `detail/{libraryId}` instead of the library list. Fix: route library cards to `LIBRARY_MOVIES` / `LIBRARY_TVSHOWS` etc., or remove the Libraries section from Home (accessible via nav drawer already). |
| Raw filenames shown as card titles on Home | Low | Server metadata issue — `getDisplayTitle()` is correct; the Jellyfin server items don't have clean titles set. Not a code bug. |
| Detail screen is minimal | Low | No cast row, no seasons/episodes browser, no synopsis. Functional but sparse. |

---

## What is still missing / future work

- Fix library card routing from Home (navigate to library list, not detail)
- Detail screen improvements: synopsis, cast row, seasons browser for TV shows
- Audio player UX (AudioService is a stub; no playback UI)
- Player improvements: track/subtitle selection, next-episode auto-play
- Quick Connect auth option ✅ complete
- DI/scope cleanup — copied data layer still includes mobile-era modules
- Smoke test: Music route, Search, saved-session restore on cold relaunch

---

## Commit history (this session)

```
d9c8b8f fix: add NonCancellable guard to clearServerState
dfb6830 fix: persist server state to DataStore so saved-session restore works on cold start
814d6ac feat: skip auth on launch if saved session is active
1d39e3b feat: add TV player controls overlay with auto-hide and seek
7d364f9 fix: remove shadow state cast and sync artist view type on back navigation
6067a98 fix: correct music image URL method and error retry view type
aa044ac feat: implement MusicViewModel and MusicScreen with album/artist browsing
392544e feat: implement core navigation screens and Media3 playback
```
