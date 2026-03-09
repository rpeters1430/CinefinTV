# CinefinTV Status Snapshot

**Date:** 2026-03-09 (updated)
**Status:** MVP feature-complete, build clean, audio playback, richer TV detail flow, person detail screen, and infrastructure restoration implemented

---

## Current State

The app is functional end to end on Android TV against a live Jellyfin server. Core navigation, playback, auth, music, search, and library browsing are all in place. The project currently compiles cleanly and the active unit test suite passes.

### Latest updates (2026-03-05)

- **Person Detail Screen**: Added a dedicated screen for cast and crew members, showing their biography and a "Known For" media row.
- **Infrastructure Restoration (Task 24)**: Restored `ErrorHandler` and `OfflineManager` integration in the legacy `JellyfinRepository`.
- **Navigation Enhancements**: Integrated Person Detail into the main navigation graph and enabled clicking on cast members in the media detail screen.
- **Quick Connect Refactor**: Replaced ad-hoc text with a proper switching panel in LoginScreen.
- **Detail Screen Upgrade**: Added Cast & Crew row and enabled dynamic overview updates when focusing seasons/episodes/related items.
- **Player Autoplay**: Implemented a "Next Episode" countdown overlay that appears 15 seconds before an episode ends, allowing users to play immediately or wait for auto-switch.
- **Track Selection Improvements**: Ensured audio/subtitle track selection UI is functional and integrated with Media3.
- Audio playback is now fully wired with Media3 background playback (`AudioService`, `AudioPlayerViewModel`, `AudioPlayerScreen`) and music routes into the dedicated audio player flow.
- Retry behavior in the repository layer now performs real retries with backoff and a lightweight circuit breaker.
- JVM tests were repaired and expanded; home, music, and audio coverage now pass under `:app:testDebugUnitTest`.
- TV show detail was upgraded from a lean placeholder to a real TV browser: richer metadata, season lists with episode counts, actual episode playback targeting, wide season art, and improved wrapping tag layout.

### Build and project health

- `./gradlew :app:compileDebugKotlin` - BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` - BUILD SUCCESSFUL
- No current compile blockers in the app module

---

## Implemented screens and features

### Auth

- `ServerConnectionScreen` - server URL input, connection test
- `LoginScreen` - username/password, Quick Connect (mode-switching panel), error handling
- Saved-session restore keeps users out of the auth flow when a valid session already exists

### Home

- `HomeScreen` + `HomeViewModel`
- Continue Watching and recent-media sections
- Featured carousel and horizontal media rows

### Library

- `LibraryScreen` + `LibraryViewModel`
- Categories: Movies, TV Shows, Stuff
- Grid layouts for browse screens with widened horizontal padding for TV focus scale

### Detail

- `DetailScreen` + `DetailViewModel`
- Full-bleed backdrop, richer metadata chips, centered wrapping info rows
- TV shows now list seasons with episode counts and resolve playback to a real episode target
- **Cast & Crew**: Horizontal row of people with roles/images; clicking now navigates to Person Detail
- **Deeper Episode Context**: Focus-driven overviews for seasons and episodes
- Series detail action row is simplified to playback-only

### Person

- `PersonScreen` + `PersonViewModel`
- Dedicated detail view for actors and directors
- Biography/Overview and "Known For" media horizontal list

### Search

- `SearchScreen` + `SearchViewModel`
- Debounced search with grid results

### Music

- `MusicScreen` + `MusicViewModel`
- Albums / Artists grid and album detail track list
- Album tracks launch the dedicated `audio-player/{itemId}` route

### Player

- `PlayerScreen` with custom TV playback controls
- Position polling, play/pause/seek controls, and saved playback handling
- **Next Episode Autoplay**: Countdown overlay with 15s threshold and "Play Now" shortcut
- **Track Selection**: Integrated audio and subtitle selection UI

### Audio

- `AudioService` backed by Media3 `MediaSessionService`
- `AudioPlayerViewModel` + `AudioPlayerScreen` for background audio playback
- Album queue playback, saved-position restore, transport controls

### Supporting infrastructure

- `CinefinTvTheme` dark TV theme with improved muted-text contrast
- `TvMediaCard` horizontal-card component for TV browse surfaces
- `TvPersonCard` circular-card component for cast/crew display
- Audio service is registered in the manifest and actively used by the app

---

## Current priorities / next work

Prioritized implementation and fix sequencing now lives in `docs/plans/2026-03-09-app-upgrade-roadmap.md`.

Immediate next items:
- Run a full regression QA pass on emulator + real Android TV hardware and triage defects
- Fix top focus/navigation defects first (tabs, carousel entry, detail action rows, cast rails)
- Harden playback/session failure paths (timeouts, retries, saved-position recovery)
- Ship TV visual polish updates (contrast, spacing consistency, metadata readability)
- Expand automated checks around autoplay/track-selection transitions and key Home mappings
