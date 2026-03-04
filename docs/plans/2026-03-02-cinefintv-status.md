# CinefinTV Status Snapshot

**Date:** 2026-03-04 (updated)
**Status:** MVP feature-complete, build clean, audio playback and richer TV detail flow implemented

---

## Current State

The app is functional end to end on Android TV against a live Jellyfin server. Core navigation, playback, auth, music, search, and library browsing are all in place. The project currently compiles cleanly and the active unit test suite passes.

### Latest updates (2026-03-04)

- Audio playback is now fully wired with Media3 background playback (`AudioService`, `AudioPlayerViewModel`, `AudioPlayerScreen`) and music routes into the dedicated audio player flow.
- Retry behavior in the repository layer now performs real retries with backoff and a lightweight circuit breaker, and `RetryStrategy` no longer behaves like a placeholder.
- JVM tests were repaired and expanded; home, music, and audio coverage now pass under `:app:testDebugUnitTest`.
- TV show detail was upgraded from a lean placeholder to a real TV browser: richer metadata, season lists with episode counts, actual episode playback targeting, wide season art, and improved wrapping tag layout.
- TV layout polish was applied across the app: secondary text contrast is brighter, edge padding was increased on browse screens, and horizontal rows now leave room for focused cards.

### Build and project health

- `./gradlew :app:compileDebugKotlin` - BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` - BUILD SUCCESSFUL
- No current compile blockers in the app module
- Existing worktree still contains broad in-progress refactors across playback, repository, and UI layers

---

## Implemented screens and features

### Auth

- `ServerConnectionScreen` - server URL input, connection test
- `LoginScreen` - username/password, Quick Connect, error handling
- Saved-session restore keeps users out of the auth flow when a valid session already exists

### Home

- `HomeScreen` + `HomeViewModel`
- Continue Watching and recent-media sections
- Featured carousel and horizontal media rows
- Added row padding so focused cards do not clip on the left edge

### Library

- `LibraryScreen` + `LibraryViewModel`
- Categories: Movies, TV Shows, Stuff
- Grid layouts for browse screens with widened horizontal padding for TV focus scale

### Detail

- `DetailScreen` + `DetailViewModel`
- Full-bleed backdrop, richer metadata chips, centered wrapping info rows
- TV shows now list seasons with episode counts and resolve playback to a real episode target
- Series detail action row is simplified to playback-only
- Season cards prefer true wide art instead of portrait poster fallback

### Search

- `SearchScreen` + `SearchViewModel`
- Debounced search with grid results
- Grid padding updated for safer TV focus spacing

### Music

- `MusicScreen` + `MusicViewModel`
- Albums / Artists grid and album detail track list
- Album tracks launch the dedicated `audio-player/{itemId}` route
- Grid and detail spacing updated for TV focus behavior

### Player

- `PlayerScreen` with custom TV playback controls
- Position polling, play/pause/seek controls, and saved playback handling

### Audio

- `AudioService` backed by Media3 `MediaSessionService`
- `AudioPlayerViewModel` + `AudioPlayerScreen` for background audio playback
- Album queue playback, saved-position restore, transport controls
- Test seams added so audio behavior is covered by JVM tests

### Supporting infrastructure

- `CinefinTvTheme` dark TV theme with improved muted-text contrast
- `TvMediaCard` horizontal-card component for TV browse surfaces
- `JellyfinStreamRepository.getLandscapeImageUrl()` for general landscape imagery
- `JellyfinStreamRepository.getWideCardImageUrl()` for strict wide-only season art
- Audio service is registered in the manifest and actively used by the app

---

## Current priorities / next work

- Run a live visual QA pass on actual TV hardware or emulator for focus, chip wrapping, and edge spacing
- Add cast/crew and deeper episode context to the detail screen
- Improve video playback with track selection and next-episode autoplay driven by detail/playback context
- Continue cleanup of older encoding-corrupted Kotlin files
- Review remaining broad refactor files for cleanup and split large changes into more maintainable units
