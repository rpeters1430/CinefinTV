# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (requires signing config in local.properties or env vars)
./gradlew :app:assembleRelease

# Install on connected Android TV device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Run all unit tests
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.home.HomeViewModelTest"

# Clean build
./gradlew clean :app:assembleDebug
```

## Architecture

### Layer Overview
- **`ui/`** — Compose screens, ViewModels, navigation, components, theme
- **`data/`** — Repository implementations, models, session/credential storage
- **`di/`** — Hilt modules (`NetworkModule`, `SecurityModule`, `AudioModule`, `DispatcherModule`, etc.)
- **`network/`** — OkHttp interceptors (auth, connectivity, cache policy)

### Repository Layer
All data access goes through `JellyfinRepositoryCoordinator`, which is the single dependency injected into most ViewModels:

```kotlin
class JellyfinRepositoryCoordinator @Inject constructor(
    val media: JellyfinMediaRepository,     // browse/search content
    val user: JellyfinUserRepository,       // user profile
    val search: JellyfinSearchRepository,   // search
    val stream: JellyfinStreamRepository,   // stream & image URLs
    val auth: JellyfinAuthRepository,       // authentication state
)
```

All repository methods return `ApiResult<T>` (`data/repository/common/ApiResult.kt` — sealed class: `Success`, `Error`, `Loading`). `ErrorType` enum gives callers structured error categories (e.g. `DNS_RESOLUTION`, `UNAUTHORIZED`).

All concrete repositories extend `BaseJellyfinRepository`, which provides standard execution helpers:

```kotlin
// Preferred: gets validated server + authenticated client, wraps result in ApiResult
withServerClient("operationName") { server, client -> ... }

// With automatic retry (3 attempts, exponential backoff)
executeWithRetry("operationName") { ... }

// Cache-first: checks JellyfinCache, falls back to network, caches result
executeWithCache("operationName", cacheKey = "key", cacheTtlMs = 30 * 60 * 1000L) { ... }
```

`withServerClient` is the standard pattern for new repository methods — it handles token validation, 401 retry, and circuit-breaking automatically.

### Auth & Session Persistence
- `JellyfinAuthRepository` owns `_currentServer: StateFlow<JellyfinServer?>` and implements `TokenProvider`
- On login, `JellyfinServer` (serializable) is encrypted and persisted via `SecureCredentialManager` → Android Keystore + DataStore
- On cold start, `AuthViewModel.checkSavedSession()` calls `tryRestoreSession()` which seeds `_currentServer` without a network call
- `NavGraph` uses `LaunchedEffect(isSessionChecked)` to skip auth screens if session is active
- Logout clears both in-memory state and DataStore under `NonCancellable` to guarantee persistence

### Navigation
- `CinefinTvApp` — root composable; wraps content in `CinefinTvTheme` with a `TabRow` nav bar (hidden for `auth/*`, `player/*`, and `audio-player/*` routes)
- `CinefinTvNavGraph` — `NavHost` with all route definitions; holds the single `AuthViewModel`
- Routes defined in `NavRoutes` / `AuthRoutes` objects. Key routes: `detail/{itemId}`, `detail/person/{personId}`, `stuff/detail/{itemId}`, `player/{itemId}`, `audio-player/{itemId}?queue={queue}`, `library/{movies|tvshows|stuff|music}`

### ViewModel Pattern
Each screen has a co-located sealed `UiState` class plus a `@HiltViewModel`. Standard structure:

```kotlin
sealed class XxxUiState { Loading; Error(message); Content(...) }

@HiltViewModel
class XxxViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<XxxUiState>(XxxUiState.Loading)
    val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()
}
```

### Player
**Video (`PlayerScreen`):** Creates ExoPlayer directly via `rememberExoPlayer()` composable (not in a ViewModel).
- `PlayerView` with `useController = false`; custom Box-based overlay handles back/play-pause/seek
- Playback state (position, duration, isPlaying) polled every 500 ms via `LaunchedEffect`
- Controls auto-hide after 3 s using `LaunchedEffect(lastInteraction)`
- `OkHttpDataSource` is used so auth headers are included in media requests

**Audio (`AudioPlayerScreen`):** Uses a bound `MediaController` + `AudioService` (MediaSessionService). `AudioPlayerViewModel` connects to the service via `AudioControllerConnector` rather than holding ExoPlayer directly.

### Other Repositories
`JellyfinSystemRepository` (not in the coordinator) handles server info / system-level API calls and is injected independently where needed.

Preference repositories (`PlaybackPreferencesRepository`, `ThemePreferencesRepository`, `SubtitleAppearancePreferencesRepository`, `LibraryActionsPreferencesRepository`) are also injected independently and backed by DataStore.

### Cross-Screen State Updates
`MediaUpdateBus` (`data/common/MediaUpdateBus.kt`) is a `SharedFlow`-based event bus for propagating media state changes (e.g. watched status) across screens without reloading. ViewModels subscribe in `init {}`:

```kotlin
mediaUpdateBus.events.collect { event ->
    when (event) {
        is MediaUpdateEvent.RefreshItem -> { /* update single item */ }
        is MediaUpdateEvent.RefreshAll -> loadData()
    }
}
```

### Session Management
`JellyfinSessionManager` (`data/session/`) is the central provider of authenticated Jellyfin API clients. It uses a single-flight mutex to prevent concurrent re-auth races. `OptimizedClientFactory` creates token-aware clients per server. `BaseJellyfinRepository.withServerClient` delegates to this — don't bypass it.

### Playback Decisions
`EnhancedPlaybackManager` (`data/playback/`) determines whether to direct-play or transcode based on network quality (`ConnectivityChecker` bandwidth estimation), device capabilities, and HDR/bitrate limits. It emits structured reason codes. `ConnectivityChecker` (`network/`) exposes a Flow of `NetworkQuality` (EXCELLENT/GOOD/FAIR/POOR) via `ConnectivityManager` callbacks.

### Library Paging
`LibraryItemPagingSource` implements Paging 3 for library browsing with server-side filtering. Use this (not manual pagination) for any screen that lists large library collections.

### Logging
Use `SecureLogger` (`utils/SecureLogger.kt`) instead of `Log.*` — it redacts tokens/passwords via regex and handles chunking for long messages. Never log credentials directly.

### BaseItemDto Extensions
`utils/Extensions.kt` provides rich extension functions on `BaseItemDto` — prefer these over direct field access:
- Type checks: `isMovie()`, `isSeries()`, `isSeason()`, `isEpisode()`, `isMusic()`
- Display: `getDisplayTitle()`, `getYear()`, `getYearRange()`, `getFormattedDuration()`, `getEpisodeCode()`
- Watch state: `isWatched()`, `isPartiallyWatched()`, `canResume()`, `getWatchedPercentage()`
- Series helpers: `getUnwatchedEpisodeCount()`, `hasUnwatchedEpisodes()`, `getNextUpInfo()`

## TV-Specific Rules

- All `androidx.tv.material3` composables require `@OptIn(ExperimentalTvMaterial3Api::class)`. A file-level `@file:OptIn(ExperimentalTvMaterial3Api::class)` annotation is preferred over per-call opt-ins.
- **Do not use** `TvLazyRow` / `TvLazyColumn` (deprecated) — use standard `LazyRow`, `LazyColumn`, `LazyVerticalGrid`
- Minimum body text size is **18sp** for 10-foot viewing
- Navigation is D-pad driven; test focus traversal when adding interactive elements

## Theme & Colors

Dark-only. Key named colors in `ui/theme/Color.kt`:
- `BackgroundDark` = `#0D1117`
- `SurfaceDark` = `#161B22`
- `CinefinRed` = `#E50914` (primary accent)

## Testing

Tests live in `app/src/test/` (JVM unit tests only — no instrumented tests).

- **MockK** for mocking, **Turbine** for Flow assertions, **coroutines-test** with `MainDispatcherRule`
- Fake helpers live in `testutil/` (e.g., `FakeHomeRepositories`, `MainDispatcherRule`)
- ViewModels are tested by constructing them directly with fake dependencies — no Hilt in tests

## Key Dependency Versions

| Dependency | Version |
|---|---|
| Kotlin | 2.3.10 |
| Compose BOM | 2026.02.01 |
| androidx.tv:tv-material | 1.0.0 |
| Hilt | 2.59.2 (KSP) |
| Media3 | 1.10.0-beta01 |
| Jellyfin SDK | 1.8.6 |
| jellyfin-media3-ffmpeg | 1.9.0+1 |
| Coil | 3.4.0 |
| OkHttp | 5.3.2 |
| Gradle / AGP | 9.1.0 / 9.0.1 |
