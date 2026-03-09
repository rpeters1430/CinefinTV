# CinefinTV — Code Review & Improvement Plan
**Reviewed:** 2026-03-09 | **Version:** 1.0.2 (versionCode 3)

---

## Executive Summary

The project is in a solid MVP state with real architectural discipline. MainActivity is lean, MVVM is consistent, Hilt DI is properly wired, and coroutine patterns are sound throughout. The roadmap document (`2026-03-09-app-upgrade-roadmap.md`) is well-structured and prioritized correctly — Reliability → UX → Features → Architecture is exactly the right order for a TV app at this stage.

That said, there are several concrete issues the roadmap doesn't call out: a dependency version mismatch that can break builds, a 984-line PlayerScreen that needs splitting, a significant amount of dead/aspirational code adding noise, and pervasive hardcoded colors that undercut the theme system.

---

## What You're Doing Well

**Architecture & DI**
- `MainActivity` is ~20 lines — a genuine win after the work you described doing on Cinefin.
- `JellyfinRepositoryCoordinator` is a clean coordinator pattern. Injecting one object instead of 5 repositories is exactly right.
- `ApiResult<T>` sealed class for every network call gives consistent error handling across the entire data layer.
- Version catalog (`libs.versions.toml`) is used correctly and centrally.
- Signing config reads from env vars, Gradle properties, or `local.properties` in that order — the right pattern for CI/CD.

**Compose & State**
- `sealed class HomeUiState { Loading / Error / Content }` is the correct pattern and it's consistent across ViewModels.
- `collectAsStateWithLifecycle` is used everywhere (not the older `collectAsState`). Good.
- `key = { it.id }` and `contentType = { "MediaCard" }` are set on LazyList `items()` calls in HomeScreen. This is exactly what prevents unnecessary recomposition.
- `HomeViewModel.refresh()` fires all five API calls with `async`/`awaitAll` in parallel. Good use of structured concurrency.

**Security**
- `SecureCredentialManager` uses Android Keystore + DataStore with key rotation. That's production-grade for a media client.
- Network security config is in place.
- `JellyfinAuthInterceptor` doubles as an `Authenticator` for automatic token refresh on 401s.

**Testing**
- JVM unit tests exist for HomeViewModel, SearchViewModel, MusicViewModel, AuthViewModel, PlayerViewModel, and AudioPlayerViewModel.
- `MainDispatcherRule` + `MockK` + `Turbine` is the correct testing stack for Kotlin coroutines and StateFlow.
- `FakeRepositories` pattern instead of mocking everything inline — good test infrastructure.

---

## Issues & Concerns

### 🔴 Critical

#### 1. KSP Version Mismatch — Will Likely Break Builds

In `libs.versions.toml`:
```toml
kotlin = "2.3.10"
ksp   = "2.3.6"
```

KSP 2.x versions must match the Kotlin version they compile against. The version `2.3.6` does not correspond to Kotlin `2.3.10`. This will cause Hilt annotation processing failures at build time. The KSP version for Kotlin `2.3.10` should be `2.3.10-1.0.x` (check the KSP releases page for the latest patch). Fix this before anything else.

**Fix:**
```toml
ksp = "2.3.10-1.0.24"  # or whatever the latest 2.3.10-compatible release is
```

---

### 🟠 High Priority

#### 2. PlayerScreen is 984 Lines — Needs Decomposition

The entire player UI, ExoPlayer lifecycle, track selection panel, seek logic, overlay controls, and position polling all live in one file. The roadmap's Phase D mentions "continue breaking up oversized screens" but this one should be Phase A work because it's the hardest screen to maintain and debug.

Suggested splits:
- `PlayerScreen.kt` — top-level composable, lifecycle orchestration (~100 lines)
- `PlayerControls.kt` — the overlay buttons + seek bar
- `PlayerTrackPanel.kt` — the audio/subtitle/quality/speed drawer (currently `SettingsSection` enum + the LazyColumn panel)
- `rememberExoPlayer` — move this to `PlayerViewModel` so the player lifecycle is in the ViewModel scope, not the Compose tree. Currently, if the composable is removed and re-added for any reason (e.g., navigation), the player is torn down and rebuilt even if the `streamUrl` hasn't changed.

#### 3. JellyfinRepository.kt is 1,414 Lines

The coordinator pattern (`JellyfinRepositoryCoordinator`) helps at the injection site but the main repository still does auth retries, session management, URL building, library queries, person queries, stream info, and favorites. It's a god class. The `JellyfinMediaRepository`, `JellyfinAuthRepository`, `JellyfinStreamRepository`, and `JellyfinUserRepository` exist as siblings — the main repo should be thinning out and delegating *all* domain logic to those.

Immediate action: Audit which methods in `JellyfinRepository` are still directly called by ViewModels vs. accessed through the Coordinator. Anything accessed through the Coordinator should be moved into the appropriate sub-repository and the method in the main repo should be removed or just delegate.

#### 4. Hardcoded `Color.White` Across 15+ Screens

`Color.White` appears directly in auth screens, PersonScreen, MusicScreen, StuffLibraryScreen, StuffDetailScreen, HomeScreen, and TvMediaCard. In a dark-only TV app this doesn't break anything visually today, but it means the theme system isn't actually controlling those colors. If you ever adjust `OnBackground` or `onSurface` in the theme, none of those screens will respond.

**Pattern to follow:**
```kotlin
// Wrong — hardcoded
color = Color.White

// Right — theme-driven
color = MaterialTheme.colorScheme.onBackground
// or for secondary text:
color = MaterialTheme.colorScheme.onSurfaceVariant
```

The `Color.Black` gradients in video overlays (PlayerScreen, StuffDetailScreen) are an exception — black gradients for scrim effects are intentional and fine.

---

### 🟡 Medium Priority

#### 5. Dead Code: FeatureFlags.AI and Phase4Module Leftovers

`FeatureFlags.AI` defines ~25 constants for AI summaries, mood analysis, smart recommendations, person bios, theme extraction, and "why you'll love this" features. None of these are implemented. `Phase4Module` has a `TODO Task 24` about `SharedAppStateManager` that was apparently never resolved. `GEMINI.md` sits alongside `CLAUDE.md` at the repo root suggesting a prior AI assistant switch that wasn't fully cleaned up.

This dead code increases cognitive load when reading the codebase and creates the false impression that AI features are in progress. Recommendation: either delete `FeatureFlags.AI` entirely and the commented-out `Phase4Module` provider, or move them to a `feature/ai-exploration` branch.

#### 6. Navigation Slide Transitions Are Not Ideal for TV

The NavGraph uses `slideInHorizontally + fadeIn` for all transitions. On TV with a D-pad, slide animations that move content 300px can be disorienting because focus needs to land in the right place immediately. TV UX guidelines (and the androidx.tv material3 library's own defaults) favor fade-only transitions. Consider replacing slide+fade with a simple fade at `tween(200)` for all four transition parameters.

#### 7. Settings Screen is a Placeholder in the Live Nav Graph

`NavRoutes.SETTINGS` navigates to `PlaceholderScreen("Settings")` — a composable that just renders the word "Settings" centered on screen. It's reachable from the nav graph today. If users can navigate there from Home, this will look like a bug. It should either be removed from the nav graph until implemented or show a clear "Coming Soon" state with a back prompt.

#### 8. `datastore` and `tvFoundation` Are on Unstable Alpha Channels

```toml
datastore     = "1.3.0-alpha06"
tvFoundation  = "1.0.0-alpha12"
```

DataStore has a stable `1.1.x` series. For a shipping app, `1.3.0-alpha06` brings unnecessary risk. `tv-foundation` doesn't have a stable release yet so that's unavoidable, but DataStore should be pinned to `1.1.1` stable unless you specifically need a `1.3.x` alpha feature.

#### 9. Coil `AsyncImage` Calls Are Missing Size Hints

In TvMediaCard and throughout the screens, `AsyncImage` is called without explicit `size` in the `ImageRequest`. Coil will infer size from the composable's layout dimensions, which is usually fine — but for a TV app loading many thumbnails simultaneously in LazyRows, explicitly setting size avoids loading images at screen resolution when a thumbnail resolution is sufficient:

```kotlin
ImageRequest.Builder(context)
    .data(imageUrl)
    .size(520, 293)  // 2× the 260dp card width at hdpi
    .crossfade(true)
    .build()
```

#### 10. `QUALITY_OPTIONS` List Is Decorative, Not Functional

In PlayerScreen:
```kotlin
private val QUALITY_OPTIONS = listOf("Auto", "1080p", "720p", "480p")
```

This list is displayed in the track panel UI but selecting a quality option does not change the transcoding bitrate sent to the Jellyfin server — there's no corresponding API call to update `maxStreamingBitrate`. This makes the quality selector look functional when it isn't. Either wire it up to the `EnhancedPlaybackManager`/`AdaptiveBitrateMonitor` that already exist, or remove the UI element until it's implemented.

---

### 🟢 Low Priority / Nice-to-Have

#### 11. `PerformanceOptimizer` Object May Be Unnecessary

`PerformanceOptimizer.executeOffMainThread()` checks `isMainThread()` and wraps in `withContext(Dispatchers.IO)` if true. But any `suspend fun` in a ViewModel is already off the main thread if the ViewModel uses `viewModelScope.launch` correctly. The explicit main-thread check is a guard for code that doesn't trust its call context, which suggests those call sites may not be structured correctly. Audit whether this utility is compensating for missing `withContext` in repositories, and if so, fix the repositories instead.

#### 12. `AuthViewModel` Scope in NavGraph

`hiltViewModel()` for `AuthViewModel` is called at the `CinefinTvNavGraph` composable level. In Compose Navigation, `hiltViewModel()` without a `navBackStackEntry` parameter is scoped to the composable's current `NavBackStackEntry`. At the NavGraph function level this is effectively the start destination's entry. This is workable but fragile — if the AuthViewModel outlives the auth screens or gets recreated unexpectedly, `LaunchedEffect` observers for `isSessionChecked` and `loginSucceeded` could fire stale values. Consider using a `NavGraph`-scoped ViewModel by getting a parent back stack entry reference.

---

## Assessment of the Roadmap Document

The `2026-03-09-app-upgrade-roadmap.md` is well-written and the phasing is correct. Specific notes:

**Phase A (Reliability) — Mostly valid, one gap:** The playback hardening items (A2) and session resilience (A3) are right. However, the KSP version mismatch (#1 above) should be listed as a P0 prerequisite — it can prevent the build from running at all. Add it as "A0: Resolve build blockers before QA sweep."

**Phase B (TV UX) — Accurate and actionable:** Focus system QA (B1) and visual hierarchy polish (B2) are the right targets. The hardcoded `Color.White` issue (#4) is the concrete implementation task behind B2.

**Phase C (Feature Depth) — Reasonable but watch scope:** C2 (subtitle/audio track UX) is blocked by the quality selector being non-functional (#10 above). Don't start C2 without also fixing the quality UI. The `QUALITY_OPTIONS` and track panel are in the same `SettingsSection` enum — a user can't tell which selections are real.

**Phase D (Architecture) — Correct priority placement:** Listing this last is right. The roadmap calls out "continue breaking up oversized screens" which maps to PlayerScreen (#2) and JellyfinRepository (#3). These should be referenced explicitly.

**Missing from the roadmap entirely:**
- KSP build blocker
- Dead AI feature flag code (noise in the codebase)
- Non-functional quality selector in PlayerScreen
- Navigation transition polish for TV UX

---

## Recommended Immediate Actions (ordered)

1. **Fix KSP version** in `libs.versions.toml` to match Kotlin `2.3.10` — verify the build compiles clean.
2. **Delete `FeatureFlags.AI`** constants and clean up `Phase4Module` TODO — reduce noise before Phase A QA work begins.
3. **Replace `Color.White` hardcodes** with `MaterialTheme.colorScheme.onBackground` across all screens — mechanical find/replace, low risk.
4. **Remove Settings from nav graph** or show a proper placeholder screen with a visible back action.
5. **Wire or remove quality selector** in PlayerScreen — mark it `// Not yet implemented` in UI if keeping the UI slot.
6. **Split PlayerScreen** into 3-4 focused files before adding any new playback features.
7. **Downgrade DataStore** to stable `1.1.1`.

The roadmap's Phase A QA sweep (#1 in the sprint backlog) should start only after items 1-4 above are complete, otherwise you'll be testing against a noisy baseline.
