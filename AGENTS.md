# Repository Guidelines

## Project Structure & Module Organization
`app/` contains the Android TV application. Main Kotlin sources live under `app/src/main/java/com/rpeters/cinefintv`, organized by feature areas such as `ui/screens`, `ui/player`, `data/repository`, `data/preferences`, `network`, `di`, and `utils`. Android resources are in `app/src/main/res`. JVM unit tests live in `app/src/test/java`, and instrumented Compose/UI tests live in `app/src/androidTest/java`. Planning notes, release checklists, and design docs are kept in `docs/`. Version catalog and shared Gradle configuration live in `gradle/` and the root `build.gradle.kts`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

- `./gradlew :app:assembleDebug` builds a debug APK.
- `./gradlew :app:testDebugUnitTest` runs JVM unit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs device/emulator UI tests.
- `./gradlew :app:lintDebug` runs Android lint checks.
- `./publish.sh` publishes a release and updates `updates/version.json`.

The project targets JDK 21 and Android SDK 36. For device testing, install with `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Coding Style & Naming Conventions
Follow the existing Kotlin style: 4-space indentation, trailing commas where the codebase already uses them, and one top-level type per concern when practical. Use `UpperCamelCase` for classes, `lowerCamelCase` for functions and properties, and descriptive screen/view-model pairs such as `HomeScreen.kt` and `HomeViewModel.kt`. Keep package names under `com.rpeters.cinefintv.*`. Prefer feature-focused organization over generic utility dumping, and keep Compose test tags near the owning screen (`HomeTestTags`, `PlayerTestTags`).

## Testing Guidelines
Unit tests use JUnit 4, MockK, Turbine, and coroutine test utilities. Name tests after the subject and expected behavior, for example `loadingState_rendersLoadingSurface`. Put JVM tests beside the corresponding feature package in `app/src/test/java`; reserve `androidTest` for Compose focus, navigation, and playback interactions. Add or update tests for view-model logic and TV navigation regressions whenever behavior changes.

## Commit & Pull Request Guidelines
Recent history favors short Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:`. Keep subjects imperative and scoped to one change. Pull requests should include a concise description, linked issue or design doc when relevant, test coverage notes, and screenshots or short recordings for UI changes, especially focus/navigation updates on TV surfaces.
