# CinefinTV — Copilot Instructions

Native Android TV client for Jellyfin. Kotlin + Jetpack Compose for TV, MVVM, Hilt DI, Media3.

## Build & Test

```bash
# Debug build
./gradlew :app:assembleDebug

# Install on connected device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# All unit tests
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.home.HomeViewModelTest"

# Clean build
./gradlew clean :app:assembleDebug
```

Tests are JVM-only (no instrumented tests). Located in `app/src/test/`.

## Architecture

### Repository Layer

All data access flows through `JellyfinRepositoryCoordinator` — the single dependency injected into most ViewModels:

```kotlin
@Singleton
class JellyfinRepositoryCoordinator @Inject constructor(
    val media: JellyfinMediaRepository,   // browse/search content
    val user: JellyfinUserRepository,     // user profile
    val search: JellyfinSearchRepository, // search
    val stream: JellyfinStreamRepository, // stream & image URLs
    val auth: JellyfinAuthRepository,     // authentication state
)
```

All repository methods return `ApiResult<T>` (sealed: `Success`, `Error`, `Loading`) with an `ErrorType` enum for structured error categories.

All concrete repositories extend `BaseJellyfinRepository`, which provides:

```kotlin
// Standard: gets validated server + authenticated client, wraps in ApiResult
withServerClient("operationName") { server, client -> ... }

// With automatic retry (exponential backoff, 3 attempts by default)
executeWithRetry("operationName") { ... }

// Cache-first: checks JellyfinCache, falls back to network, caches result (30 min TTL default)
executeWithCache("operationName", cacheKey = "key") { ... }
```

`withServerClient` is the standard pattern for new repository methods — it handles token validation, 401 retry, and circuit-breaking automatically. `JellyfinSystemRepository` (not in the coordinator) handles server-info/system API calls and is injected independently.

### ViewModel Pattern

Each screen has a co-located sealed `UiState` and a `@HiltViewModel`:

```kotlin
sealed class XxxUiState {
    data object Loading : XxxUiState()
    data class Error(val message: String) : XxxUiState()
    data class Content(...) : XxxUiState()
}

@HiltViewModel
class XxxViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<XxxUiState>(XxxUiState.Loading)
    val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()
}
```

### Auth & Session

- `JellyfinAuthRepository` owns `_currentServer: StateFlow<JellyfinServer?>` and implements `TokenProvider`.
- On login, `JellyfinServer` (serializable) is encrypted and persisted via `SecureCredentialManager` → Android Keystore + DataStore.
- On cold start, `AuthViewModel.checkSavedSession()` calls `tryRestoreSession()` which seeds `_currentServer` without a network call.
- `NavGraph` uses `LaunchedEffect(isSessionChecked)` to skip auth screens if session is active.
- Logout clears both in-memory state and DataStore under `NonCancellable` to guarantee persistence.

### Navigation

Routes are defined in `NavRoutes` / `AuthRoutes` objects. Tab bar (defined in `navTabItems`) is hidden for `auth/*`, `player/*`, and `audio-player/*` routes. Routes not in `navTabItems` cause `selectedTabIndex` to fall back to 0.

Key routes: `detail/{itemId}`, `detail/person/{personId}`, `collections/detail/{itemId}`, `player/{itemId}?start={start}`, `audio-player/{itemId}?queue={queue}`, `library/{movies|tvshows|collections|music}`.

### Player

**Video (`PlayerScreen`):** ExoPlayer created via `rememberExoPlayer()` composable (not in a ViewModel). Uses `OkHttpDataSource` so auth headers are included. Custom overlay handles back/play-pause/seek; controls auto-hide after 3 s. The outer `Box` is focusable via `playerFocusRequester`; the inner `PlayerView` has `isFocusable=false` to prevent focus theft.

**Audio (`AudioPlayerScreen`):** Bound `MediaController` + `AudioService` (MediaSessionService). `AudioPlayerViewModel` connects via `AudioControllerConnector` rather than holding ExoPlayer directly.

## Key Conventions

### TV-Specific Rules

- All `androidx.tv.material3` composables require `@OptIn(ExperimentalTvMaterial3Api::class)`. Use per-composable `@OptIn` annotations (not file-level).
- **Never use** `TvLazyRow` / `TvLazyColumn` (deprecated) — use standard `LazyRow`, `LazyColumn`, `LazyVerticalGrid`.
- Minimum body text size is **18sp** for 10-foot viewing.
- Navigation is D-pad driven; always verify focus traversal when adding interactive elements.

### `BaseItemDto` Extensions

`utils/Extensions.kt` provides rich extension functions on `BaseItemDto` — always prefer these over raw field access:

- Type checks: `isMovie()`, `isSeries()`, `isSeason()`, `isEpisode()`, `isMusic()`
- Display: `getDisplayTitle()`, `getYear()`, `getFormattedDuration()`, `getEpisodeCode()`
- Watch state: `isWatched()`, `isPartiallyWatched()`, `canResume()`, `getWatchedPercentage()`
- Series helpers: `getUnwatchedEpisodeCount()`, `hasUnwatchedEpisodes()`

### Theme & Colors

Dark-only. Named colors in `ui/theme/Color.kt`:

| Token | Value | Use |
|---|---|---|
| `BackgroundDark` | `#0D1117` | Main background |
| `SurfaceDark` / `SurfaceVariant` | `#0D1117` / `#21262D` | Surfaces |
| `CinefinRed` | `#E50914` | Primary accent |
| `OnBackground` | `#F0F6FC` | Primary text |
| `OnSurfaceMuted` | `#8B949E` | Secondary text |

### Dynamic Colors on Carousel

Carousel/featured items extract dominant color from the poster image using Coil's palette extraction. This requires `allowHardware(false)` on the image request so the bitmap is CPU-accessible. Don't set `allowHardware(true)` on requests that feed the palette pipeline.

### Feature Flags

`FirebaseRemoteConfigRepository` / `RemoteConfigRepository` pulls feature flags at runtime. Features can be disabled server-side without an app update. Check `FeatureFlags` before adding hard-coded feature gates.

### API Limits

`ApiParameterValidator.MAX_LIMIT` is 10,000. `LibraryViewModel` uses `limit=10_000` to avoid library truncation. Respect this ceiling when passing limit parameters.

## Testing

- **MockK** for mocking, **Turbine** for Flow assertions, **coroutines-test** with `MainDispatcherRule`.
- Pre-built fake helpers in `testutil/`: `FakeHomeRepositories`, `FakePlayerRepositories`, `FakeMusicRepositories`, `MainDispatcherRule`, `DeterministicDispatcherProvider`.
- Construct ViewModels directly with fake dependencies — no Hilt in tests.
- Use `mockkObject` / `unmockkObject` for Kotlin singleton objects (e.g., `PlaybackPositionStore`).
- `advanceUntilIdle()` is required after ViewModel construction to let coroutines settle before asserting state.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun someTest() = runTest {
        val fakes = FakeHomeRepositories()
        coEvery { fakes.media.someCall() } returns ApiResult.Success(...)
        val vm = FooViewModel(fakes.coordinator)
        advanceUntilIdle()
        // assert vm.uiState.value
    }
}
```

## Key Versions

| | |
|---|---|
| Kotlin | 2.3.20 |
| Compose BOM | 2026.03.00 |
| `androidx.tv:tv-material` | 1.1.0-beta01 |
| Hilt | 2.59.2 (KSP) |
| Media3 | 1.10.0-rc02 |
| Jellyfin SDK | 1.8.6 |
| Coil | 3.4.0 |
| OkHttp | 5.3.2 |
