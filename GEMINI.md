# CinefinTV: Gemini Context & Guidelines

CinefinTV is a native Android TV client for Jellyfin, built with modern Android tooling and targeting a 10-foot user experience.

## Project Overview
- **Purpose**: A high-performance, native Jellyfin client for Android TV devices.
- **Key Features**: Authentication, Media Browsing (Movies, TV, Music, Home Videos), Search, Video/Audio Playback (Media3), and Session Persistence.
- **Main Technologies**:
  - **Language**: Kotlin (2.3.10)
  - **UI**: Jetpack Compose with Compose for TV (Material 3)
  - **DI**: Hilt (2.59.2) with KSP
  - **Playback**: AndroidX Media3 (ExoPlayer, MediaSession) + Jellyfin FFmpeg
  - **Networking**: Retrofit (3.0.0) + OkHttp (5.3.2)
  - **Persistence**: DataStore Preferences + Android Keystore encryption
  - **Architecture**: MVVM with Repository Coordination

## Project Structure
- `app/src/main/java/com/rpeters/cinefintv/`
  - `core/`: Cross-cutting concerns (Error handling, Offline management, Logger).
  - `data/`: Repositories, API models, and data persistence logic.
  - `di/`: Hilt modules for dependency injection.
  - `network/`: OkHttp interceptors and connectivity logic.
  - `ui/`: Compose-based UI layer.
    - `screens/`: Feature-specific screens (auth, home, detail, library, etc.).
    - `components/`: Shared TV-optimized UI components (e.g., `TvMediaCard`).
    - `player/`: Video and Audio player implementations using Media3.
    - `navigation/`: NavGraph and route definitions.
    - `theme/`: TV-specific Material 3 theme.
  - `update/`: Self-update logic for the application.

## Key Development Workflows

### Building and Running
- **Build Debug APK**: `./gradlew :app:assembleDebug`
- **Install on Device**: `adb install app/build/outputs/apk/debug/app-debug.apk`
- **Run Unit Tests**: `./gradlew :app:testDebugUnitTest`
- **Clean Build**: `./gradlew clean :app:assembleDebug`

### Architecture & Patterns
- **Repository Layer**: All data access is mediated by `JellyfinRepositoryCoordinator`.
- **UI State**: Screens use a `UiState` sealed class pattern within ViewModels.
- **Error Handling**: Repository methods return `ApiResult<T>` (Success, Error, Loading).
- **Navigation**: D-pad focused navigation using `androidx.navigation.compose`.

## Engineering Standards & Conventions

### TV-Specific Rules
- **Opt-In**: Use `@OptIn(ExperimentalTvMaterial3Api::class)` for TV material components.
- **Focus**: Always ensure proper D-pad focus traversal for new UI elements.
- **Text Size**: Minimum body text size is **18sp** for legibility at a distance.
- **Layouts**: Use standard `LazyRow`, `LazyColumn`, and `LazyVerticalGrid`. **Do not use** the deprecated `TvLazyRow`/`TvLazyColumn`.
- **Theme**: Dark-only theme (`BackgroundDark = #0D1117`).

### Coding Style
- Follow standard Kotlin coding conventions.
- Use Hilt for all dependency injection.
- Prefer `StateFlow` for observing state in Compose.
- Ensure all new repository methods use `ApiResult` for consistent error handling.

### Testing Guidelines
- **Location**: `app/src/test/java/com/rpeters/cinefintv/`
- **Frameworks**: MockK for mocking, Turbine for Flow assertions, and `MainDispatcherRule` for coroutines.
- **Strategy**: Focus on ViewModel testing with fake repositories/dependencies. Avoid Hilt in unit tests.

## Key Files
- `CLAUDE.md`: Quick reference for commands and architecture.
- `app/build.gradle.kts`: Application dependencies and configuration.
- `gradle/libs.versions.toml`: Centralized version management.
- `docs/plans/`: Historical and current implementation plans.
