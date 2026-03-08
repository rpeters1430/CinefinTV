# CinefinTV

CinefinTV is a **native Android TV client for Jellyfin**, built with modern Android tooling (Kotlin, Jetpack Compose for TV, Media3, Hilt, Retrofit/OkHttp).

It targets a 10-foot experience (D-pad navigation, focused controls, large UI primitives) and currently delivers an MVP end-to-end flow: connect to a Jellyfin server, sign in, browse media, and play content.

---

## Project status (current)

**Status:** MVP feature-complete and running on real Android TV hardware.

What is currently working:
- Full authentication flow (server URL + user login)
- Saved session restore on cold start
- Home feed with server-backed rows (continue watching + recently added)
- Library browsing (movies / TV / home videos)
- Search with debounced query and grid results
- Music browsing (albums/artists, album tracks)
- Video playback with custom TV controls overlay

See `docs/plans/2026-03-02-cinefintv-status.md` for a detailed status snapshot and known issues.

---

## Features

### Auth
- Server connection screen with URL validation/testing
- Username/password login
- Session persistence and restore

### Browsing
- Home sections:
  - Continue Watching
  - Recently Added Movies
  - Recently Added TV
  - Recently Added Videos
  - Libraries
- Library categories via shared library screen
- Search with lazy grid UI
- Music mode (Albums/Artists + album detail track listing)

### Playback
- Media3-powered fullscreen player
- Custom TV overlay controls:
  - Back
  - Play/Pause
  - Seek ±10 seconds
  - Progress + timestamps
- Auto-hide controls and periodic playback state polling
- OkHttp-backed data source so authenticated streams work consistently

---

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Compose for TV Material
- **Dependency Injection:** Hilt (KSP)
- **Playback:** AndroidX Media3 + Jellyfin FFmpeg integration
- **Networking:** Retrofit + OkHttp
- **Persistence:** DataStore Preferences + Android Keystore–backed encryption
- **Architecture:** MVVM-style ViewModels with repository coordination

---

## Requirements

- Android Studio (latest stable recommended)
- Android SDK installed (project compiles with `compileSdk 36`)
- JDK 21
- A reachable Jellyfin server account for end-to-end usage

---

## Getting started

git clone <your-fork-or-repo-url>
cd CinefinTV
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

---

## Testing

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Current tests are focused on ViewModels and supporting test utilities.

---

## Known gaps / next priorities

- Fix Home "Libraries" card navigation behavior
- Expand detail screens (synopsis/cast/seasons)
- Add richer audio playback UX (current audio service is stub-level)
- Add player niceties (subtitle/audio track selection, next episode autoplay)
- Continue DI cleanup and module scope tightening

---

## License

This project is licensed under the terms of the [MIT License](LICENSE).
