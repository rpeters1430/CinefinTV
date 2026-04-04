# CinefinTV Project Status Report

**Date:** 2026-04-03
**Version:** 1.6.4 (versionCode 65)
**Build Target:** compileSdk 36 / targetSdk 35 / minSdk 26

---

## Executive Summary

CinefinTV has moved past MVP into a solid beta. The full user journey works end-to-end: auth, session restore, home feed, library browsing, search, detail pages, video playback, audio playback, and settings are all implemented and functional. The architecture is clean and the coordinator/repository pattern is well-established.

That said, there is **one high-severity operational issue** (missing update version file), a cluster of **alpha/pre-release production dependencies**, a handful of **medium-severity bugs** (thread safety, interceptor ordering, exception swallowing), and a meaningful backlog of **scaffolded-but-not-wired features**.

---

## What Is Working

- Full authentication: server discovery, Quick Connect polling, username/password login, TOFU pinning, encrypted session restore on cold start
- Home screen: featured carousel, all section rows, episode menu dialog, watch-status sync, silent background refresh, focus restoration, 60-second periodic refresh
- Library screens: Paging3 integration, filters, sort options — movies, TV shows, collections
- Search: 350ms debounce, grid results, navigation from all result types
- Detail screens: movie, TV show, season/episodes, collection, person — fully implemented including cast, similar items, cinematic hero layout
- Video player: HLS/DASH via ExoPlayer, HDR detection, skip intro/credits via chapter markers, next-episode card, resume dialog, audio/subtitle track selection, quality switching, adaptive bitrate monitor, position save/restore (local + Jellyfin server)
- Audio player: MediaSession + bound service, queue sidebar, glassmorphism design
- Settings: appearance, playback, subtitle preferences all persisted via DataStore
- Security: Android Keystore encryption, `SecureLogger` redacting tokens, sensitive OkHttp header scrubbing
- Navigation: all major routes wired, back-stack correct, nav chrome correctly hidden for fullscreen destinations
- Focus system: `FocusNavigationCoordinator` throttle, `rememberTopLevelDestinationFocus` contract, D-pad left/up escape to nav rail
- Tests: JVM ViewModel tests + instrumented Compose/focus tests with `MainDispatcherRule`, `FakeRepositories`, Turbine, MockK

---

## Severity: High

### ~~H1 — Episode detail route~~ (resolved)

The episode detail screen was intentionally removed because the implementation was unstable and navigation was unreliable. All route definitions and resolver references have already been cleaned up. Episode navigation currently resolves to the TV show detail/season screen flow. No action needed — this is a known feature gap, not a bug.

### H2 — `updates/version.json` does not exist

`UpdateManager` hardcodes `https://raw.githubusercontent.com/rpeters1430/CinefinTV/main/updates/version.json`. The `updates/` directory at the repository root is empty. Every update check returns HTTP 404 and surfaces an error to the user. Until this file is created and kept current (via `publish.sh`), the in-app updater is broken.

**Fix:** Create `updates/version.json` with the correct schema and verify `publish.sh` keeps it updated on each release.

### H3 — Multiple alpha/pre-release dependencies in production

| Dependency | Version | Usage |
|---|---|---|
| `navigation-compose` | `2.10.0-alpha02` | Core routing layer |
| `paging` | `3.5.0-alpha01` | All library screens |
| `datastore-preferences` | `1.3.0-alpha07` | User preferences persistence |
| `lifecycle-runtime-compose` | `2.11.0-alpha03` | State collection in composables |
| `hilt-navigation-compose` | `1.4.0-alpha01` | ViewModel injection in nav graph |
| `tv-material` | `1.1.0-beta01` | Core UI components |

DataStore alpha is the most concerning — it stores settings, session credentials pointers, and playback positions. All of these should be tracked toward stable releases.

---

## Severity: Medium

### M1 — `LinkedHashMap` memory cache is not thread-safe

`JellyfinCache.kt:50–58` uses a `LinkedHashMap` for the in-memory LRU cache. Cache reads and writes occur from multiple coroutines on `Dispatchers.IO`. Concurrent access produces undefined behavior. Replace with `ConcurrentHashMap` or add `@GuardedBy` synchronization.

### M2 — `circuitBreakerStates` is a static companion object singleton

`BaseJellyfinRepository.kt:41`: `circuitBreakerStates` is in `companion object`, making it a process-wide singleton shared across all repository subclasses. If two repositories happen to use the same operation name string, their circuit-breaker state will interfere. Move to an instance field.

### M3 — `NetworkStateInterceptor` blocks before the OkHttp cache is checked

`NetworkModule.kt:74–77` adds `NetworkStateInterceptor` before `CachePolicyInterceptor` in the interceptor chain. When the device is offline, `NetworkStateInterceptor` throws `IOException` before OkHttp can serve a cached response. This means `CachePolicyInterceptor`'s `only-if-cached` offline mode never activates. Swap the order, or move the connectivity check after cache lookup.

### M4 — 60-second cache applied to watch-progress responses

`CachePolicyInterceptor.kt:42–47` injects `Cache-Control: public, max-age=60` on any API response that lacks its own header. This includes Continue Watching and recently-watched progress responses — meaning home screen data can be stale for up to 60 seconds after a user finishes an episode. The `isCacheable` check should exclude endpoints that return user-state data (e.g., paths containing `/Users/{userId}/Items`).

### M5 — `getPlaybackInfo` exception silently swallowed

`PlayerViewModel.kt:198–203` (approximately): `runCatching { repositories.stream.getPlaybackInfo(...) }.getOrNull()` discards any exception without logging the failure reason. Transcoding failures silently fall back to direct play with no diagnostic information. Log the throwable at minimum before returning `null`.

### M6 — `refreshItemMetadata` always returns an error

`JellyfinUserRepository.kt:206–210`: `refreshItemMetadata()` is stubbed to always return `ApiResult.Error("Metadata refresh not yet implemented")`. Any feature that calls this will silently report failure. Either implement it or remove the call sites.

### M7 — `ksp` version format does not match Kotlin convention

`libs.versions.toml:4`: `ksp = "2.3.6"`. KSP versions must match the Kotlin version prefix (e.g., `2.3.20-1.0.31`). The current value may silently produce incorrect annotation processing or build warnings.

### M8 — `withFrameNanos` double-frame hack for focus timing

`HomeScreen.kt` (multiple locations ~309–404): Focus restoration after navigation uses `withFrameNanos {}` called twice in sequence as a frame-timing workaround. This is fragile on variable-frame-rate or low-end hardware. Replace with `snapshotFlow { isCompositionComplete }` or a measure-pass callback.

### M9 — Incomplete migration from monolithic `JellyfinRepository`

`JellyfinRepository.kt` is the pre-coordinator facade class. It is still alive as a Hilt singleton because `EnhancedPlaybackManager` is injected with it rather than the `JellyfinRepositoryCoordinator`. This keeps two parallel dependency graphs active. Migrate `EnhancedPlaybackManager` to accept only `JellyfinStreamRepository` (the only sub-repo it actually needs) and delete `JellyfinRepository`.

### M10 — Home sections silently capped at 12 items

`HomeScreen.kt:645`: `val visibleItems = items.take(12)` with no "Show more" affordance. Users with large Continue Watching queues lose items with no indication. Add a "Show all" action or increase/remove the cap.

---

## Severity: Low

### L1 — `mockk-android` version hardcoded outside version catalog

`app/build.gradle.kts:170`: `"io.mockk:mockk-android:1.14.9"` is a string literal while the rest of MockK (`libs.mockk`) is in the version catalog. These will silently diverge. Move to `libs.versions.toml`.

### L2 — Smoke test declares `start` argument as `StringType`, real NavGraph uses `LongType`

`AppNavigationSmokeUiTest.kt:435`: The smoke test registers `start` as `NavType.StringType` but `NavGraph.kt:278` uses `NavType.LongType`. The test passes but does not exercise the real type contract, masking potential URL encoding mismatches in `NavRoutes.player(itemId, startPositionMs)`.

### L3 — `SecureLogger` UUID scrubbing too aggressive in debug builds

`SecureLogger.kt:157`: The regex replaces all UUID-shaped strings with `"UUID-***"` including Jellyfin item IDs in debug logs, hindering debugging. Consider gating UUID scrubbing to release builds only.

### L4 — `RatingCategory.color` is dead code

`Extensions.kt:44`: `RatingCategory.color` is a hex string property with no usages in the codebase. Remove it.

### L5 — `SettingsHero` composable is defined but never called

`SettingsScreen.kt:400`: `SettingsHero` is a composable defined at the bottom of the file but not included in the settings screen layout. Remove or integrate it.

### L6 — `PlaybackPositionStore` is a Kotlin `object`, not Hilt-injected

`PlaybackPositionStore.kt`: Uses the `object` singleton pattern and takes `Context` on every call. This is tested via `mockkObject()` which is fragile. Convert to a `@Singleton` Hilt binding.

### L7 — `Intent.ACTION_INSTALL_PACKAGE` deprecated since API 29

`UpdateManager.kt:183`: Suppress annotation hides a deprecation. On API 29+, `PackageInstaller` APIs are preferred. This still works but is a known long-term maintenance debt.

---

## Scaffolded But Not Yet Wired

| Feature | Where It Exists | Gap |
|---|---|---|
| Episode detail screen | Intentionally removed | Unstable nav/UI; episodes route through season screen for now |
| Metadata refresh | `JellyfinUserRepository.refreshItemMetadata()` | Always returns error (also M6) |
| In-app update UI | `UpdateManager`, `UpdateStatus` states | No composable surfaces update availability |
| Reduce motion preference | `ThemePreferences.respectReduceMotion`, `SettingsViewModel` | Stored and writable, but no `LocalCinefinMotionSpec` or animation gating |
| Dynamic motion tokens | Performance audit (`docs/performance-optimization-audit-2026-04-02.md`) | Not implemented |
| Player UI state slicing | Performance audit | Not implemented |
| Card visual complexity tiers | Performance audit | Not implemented |
| `useThemedIcon`, `enableEdgeToEdge` settings | `SettingsViewModel` lines 96–108 | Stored, no UI surface |
| Music "all albums/all artists" browsing | `MusicViewModel.loadGrid()` | Currently uses `getRecentlyAddedByType`; older content hidden |
| Baseline Profile / Macrobenchmark module | Performance audit | Not implemented |
| `updates/version.json` | `UpdateManager`, publish pipeline | File absent from repo (also H2) |

---

## Test Coverage Gaps

These areas have no unit tests despite meaningful logic:

- `BaseJellyfinRepository` circuit-breaker trip/recovery behavior
- `JellyfinSessionManager` single-flight re-auth mutex under concurrent requests
- `EnhancedPlaybackManager` direct-play vs transcode decision logic
- `AdaptiveBitrateMonitor` quality degradation/upgrade transitions
- `JellyfinCache` concurrent read/write behavior

---

## Recommended Priority Order

1. **H2** — Create `updates/version.json`, verify publish pipeline keeps it current
2. **M1** — Fix `JellyfinCache` thread safety (`LinkedHashMap` → synchronized or `ConcurrentHashMap`)
4. **M3** — Fix interceptor order: move `NetworkStateInterceptor` after cache check
5. **M4** — Exclude user-state endpoints from the 60-second blanket cache
6. **H3** — Audit and upgrade alpha dependencies toward stable (start with DataStore, then Navigation, Paging)
7. **M9** — Complete `EnhancedPlaybackManager` migration and delete `JellyfinRepository`
8. **M2** — Move `circuitBreakerStates` to an instance field
9. **M10** — Remove or increase the 12-item home section cap; add "Show all" if capping is desired
10. **M7** — Fix KSP version string format
11. Music grid — switch `MusicViewModel.loadGrid()` to a proper "all albums" query
12. Implement in-app update UI surface (`UpdateStatus` → dialog or banner)
13. Wire `respectReduceMotion` preference into animation durations
