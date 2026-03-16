# CinefinTV

CinefinTV is a **native Android TV client for Jellyfin**, built with modern Android tooling (Kotlin, Jetpack Compose for TV, Media3, Hilt, Retrofit/OkHttp).

It targets a 10-foot experience (D-pad navigation, focused controls, large UI primitives) and currently delivers an MVP end-to-end flow: connect to a Jellyfin server, sign in, browse media, and play content.

---

## Download

[![Download APK](https://img.shields.io/github/v/release/rpeters1430/CinefinTV?label=Download%20APK&logo=android)](https://github.com/rpeters1430/CinefinTV/releases/latest/download/app-release.apk)

**[⬇ Download latest signed APK](https://github.com/rpeters1430/CinefinTV/releases/latest/download/app-release.apk)**

The APK is signed and published automatically whenever a new version is released via `./publish.sh`. Each release also pushes an updated `updates/version.json` so the in-app updater can notify existing users.

You can also browse all releases on the [Releases page](https://github.com/rpeters1430/CinefinTV/releases).

### Installation
1. On your Android TV device, enable **Unknown sources** (Settings → Device Preferences → Security & Restrictions).
2. Download the APK to your device (via a browser, USB, or `adb sideload`).
3. Open the APK file to install.

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
See `docs/plans/2026-03-09-app-upgrade-roadmap.md` for the prioritized upgrade plan.

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

- Execute a full TV QA regression sweep (emulator + real device) and prioritize defects
- Fix focus/navigation consistency issues across home rows, tabs, and detail actions
- Harden playback edge cases (stream errors, saved position recovery, autoplay boundaries)
- Improve visual polish (contrast/readability, spacing, metadata density)
- Expand tests for playback transitions and focus-critical UI behavior

For full sequencing and success metrics, follow `docs/plans/2026-03-09-app-upgrade-roadmap.md`.

---

## License

This project is licensed under the terms of the [MIT License](LICENSE).
