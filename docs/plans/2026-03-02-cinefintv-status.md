# CinefinTV Status Snapshot

**Date:** 2026-03-02
**Status:** Buildable, core navigation partially implemented

## Current State

The project is now in a materially better state than the earlier bootstrap snapshot.

### Build and project health

- `./gradlew :app:compileDebugKotlin` succeeds
- `./gradlew :app:assembleDebug` succeeds
- Android SDK path is configured locally for this checkout
- Gradle memory is configured locally so dexing no longer fails with heap exhaustion

### Implemented app flow

These user-facing areas are now real and data-backed:

- Auth flow
  - `ServerConnectionScreen`
  - `LoginScreen`
  - `AuthViewModel` wired into `JellyfinAuthRepository`
- Home
  - `HomeScreen`
  - `HomeViewModel`
  - real sections backed by `JellyfinMediaRepository`
- Detail
  - `DetailScreen`
  - `DetailViewModel`
  - item metadata + similar items
- Library
  - Movies
  - TV Shows
  - Stuff (home videos)
  - all routed through a shared `LibraryScreen` / `LibraryViewModel`
- Search
  - `SearchScreen`
  - `SearchViewModel`
  - debounced search backed by `JellyfinSearchRepository`
- Playback
  - `player/{itemId}` now renders an in-app Media3 player screen
  - old `VideoPlayerActivity` path has been removed from the manifest

### Supporting infrastructure now in place

- TV theme and navigation shell
- Reusable `TvMediaCard`
- Simple in-app Media3 playback path using `OkHttpDataSource` so existing auth/network interceptors still apply
- Temporary `AudioService` stub remains only to satisfy the manifest

## What Is Still Missing

The app is no longer mostly placeholders, but several major areas are still unfinished:

- `Music` route is still a placeholder
- Audio playback UX is not implemented beyond the service stub
- Player UI is minimal
  - no custom TV controls
  - no track selection
  - no next-episode logic
- Detail screen is a lean implementation, not the full planned TV detail experience
  - no seasons/episodes browser
  - no cast row
  - no richer hero treatment
- Auth is functional but still basic
  - no quick connect
  - no saved-session restore on startup
- The copied data/DI layer still includes mobile-era and out-of-scope pieces that should be trimmed later

## Practical Progress Mapping

Against the 2026-03-01 implementation plan:

- Tasks 1-6: Implemented enough to be buildable and navigable
- Auth tasks: Implemented in a practical, lighter-weight form
- Home task: Implemented in a practical first pass
- Library task: Implemented for Movies / TV Shows / Stuff
- Detail task: Implemented in a lean first pass
- Search task: Implemented in a practical first pass
- Player task: Implemented as a minimal Compose/Media3 route
- Music task: Not started
- Audio player task: Not started beyond service stub
- Final DI cleanup / scope cleanup: Not started

## Recommended Next Work

The highest-value next task for tomorrow is:

1. Implement the `Music` route so the last major top-level navigation item is no longer a placeholder.

After that, the best follow-up sequence is:

2. Improve the player from "works" to "TV-usable" with custom controls and better playback state handling.
3. Add saved-session bootstrap on app launch so successful auth persists across restarts.
4. Trim copied mobile-only or out-of-scope modules from the data/DI layer.

## Suggested Starting Point For Tomorrow

If you want the cleanest continuation path tomorrow:

- build `MusicViewModel`
- build `MusicScreen`
- wire `library/music` in `NavGraph`
- reuse the existing card patterns from Home/Library
- keep it simple at first: album/artist browsing before richer playback controls

## Notes

- The repo still has uncommitted changes, including documentation and the current implementation work.
- `.claude/` is still untracked and unrelated to the app build.
