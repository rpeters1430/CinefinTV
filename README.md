# CinefinTV

CinefinTV is a **native Android TV client for Jellyfin** built with modern Android tooling: Kotlin, Jetpack Compose for TV, Media3, Hilt, Retrofit, and OkHttp.

It is designed for a true 10-foot TV experience with D-pad-first navigation, focus-aware UI, and playback controls that feel native on remote devices.

---

## Download

[![Download APK](https://img.shields.io/github/v/release/rpeters1430/CinefinTV?label=Download%20APK&logo=android)](https://github.com/rpeters1430/CinefinTV/releases/latest/download/app-release.apk)

**[⬇ Download latest signed APK](https://github.com/rpeters1430/CinefinTV/releases/latest/download/app-release.apk)**

Releases are published through `./publish.sh` (Unix-like shells) or `./publish.ps1` (PowerShell). The release process also updates `updates/version.json`, which is used by the in-app updater.

You can browse all published versions on the [Releases page](https://github.com/rpeters1430/CinefinTV/releases).

### Installation

1. On your Android TV device, enable **Unknown sources**.
2. Download the APK (browser, USB, or `adb`).
3. Open the APK file and complete installation.

---

## Project status

**Last updated:** **April 10, 2026**  
**Current app version:** **1.7.6 (versionCode 77)**  
**Lifecycle stage:** **Beta**

CinefinTV is past MVP and currently supports the full end-to-end user journey:

- Server connection and authentication (including Quick Connect and session restore)
- Home feed with watch-state aware sections
- Library browsing with filtering/sorting
- Search across multiple content types
- Rich detail pages for movies, shows, seasons, people, and collections
- Video playback with resume, track selection, and quality controls
- Audio playback with MediaSession-backed queue behavior
- Settings persistence via DataStore

### Current focus areas

The project is stable and usable, with active work centered on:

- Reliability hardening (cache, network, and concurrency edge cases)
- Dependency stabilization (reducing alpha/pre-release usage)
- Navigation/focus refinements across TV surfaces
- Playback and update-flow quality improvements
- Expanded automated coverage for critical runtime behavior

For full implementation details and known issues, see:

- `docs/2026-04-03-project-status-report.md`
- `docs/plans/2026-03-09-app-upgrade-roadmap.md`

---

## Feature overview

### Authentication

- Server connection with validation/testing
- Username/password login
- Quick Connect polling flow
- Session persistence and secure restore

### Browsing and discovery

- Home sections (continue watching, recently added, libraries)
- Dedicated library browsing for movies, TV, and collections
- Debounced search with grid results
- Music browsing (albums/artists and album detail track lists)
- Detail screens with cast/similar metadata

### Playback

- Media3-powered fullscreen player
- Custom TV control overlay
- Seek, progress, and timestamp controls
- Resume, chapter-aware skip actions, and next-item flows
- Audio/subtitle track selection and quality switching

### Settings and security

- Persisted playback/subtitle/appearance preferences
- Android Keystore-backed encryption
- Sensitive logging/header redaction safeguards

---

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Compose for TV Material
- **DI:** Hilt (KSP)
- **Playback:** AndroidX Media3 + Jellyfin FFmpeg integration
- **Networking:** Retrofit + OkHttp
- **Persistence:** DataStore Preferences + Android Keystore-backed encryption
- **Architecture:** MVVM-style ViewModels with repository coordination

---

## Requirements

- Android Studio (latest stable recommended)
- Android SDK (`compileSdk 36`)
- JDK 21
- A reachable Jellyfin server account
- Linux users can bootstrap dependencies with `./setup_linux.sh`

---

## Getting started

```bash
git clone <your-fork-or-repo-url>
cd CinefinTV
./setup_linux.sh
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Development commands

From the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

To publish a release and refresh update metadata:

```bash
./publish.sh
```

---

## Testing

Run JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run Android TV instrumented tests (device/emulator required):

```bash
./gradlew :app:connectedDebugAndroidTest
```

---

## License

This project is licensed under the [MIT License](LICENSE).
