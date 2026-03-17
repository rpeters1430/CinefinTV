# CinefinTV — Bug & Improvement Review
**Original audit:** 2026-03-15
**Last updated:** 2026-03-16
**Scope:** Full codebase review — player, data/repository layer, UI/Compose screens, navigation, architecture
**New findings added:** 2026-03-16 (B-1 through B-8)

---

## Implementation Status
**Status as of 2026-03-16**

### Fixed in code
`D-1`, `D-2`, `D-3`, `D-4`, `D-5`, `D-6`, `D-7`, `D-8`, `D-9`, `D-10`, `D-11`, `D-13`

`P-1`, `P-2`, `P-3`, `P-4`, `P-5`, `P-6`, `P-7`, `P-8`, `P-9`, `P-10`, `P-11`, `P-12`, `P-13`, `P-14`, `P-15`

`U-1`, `U-3`, `U-5`, `U-6`, `U-7`, `U-9`, `U-10`, `U-11`, `U-12`, `U-13`, `U-16`

`A-1`, `A-2`, `A-3`, `A-4`, `A-5`, `A-6`

`N-1`, `N-2`, `N-3`, `N-4`, `N-5`

`U-2`, `U-4`, `U-8`, `U-14`, `U-15`

`D-12`, `D-14`

### Fixed in code during follow-up pass (2026-03-16 - session 2)
`A-2` (Real viewport-aware logic implemented), `A-5` (Logging stripped), `B-2` (PlayerViewModel migrated), `B-3` (Main-thread safety check), `B-4` (updateInfo!! fixed), `B-5` (Player color tokens), `B-6` (Dead code removed), `B-7` (Missing VM tests added - *Note: existing AudioPlayer tests still failing*), `B-8` (Renamed "Stuff" to "Collections")

### Partially mitigated / still needs investigation
`P-16`: Playback smoothness improved through transcode/profile/polling fixes, but not fully closed. Depends on device decoder behavior, display refresh handling, and Jellyfin server playback decisions for specific files. Continue profiling on target hardware.

---

## Technical Debt Resolved (2026-03-16)

1. **`A-2` Viewport-Awareness**: `rememberViewportAwareLoader` now hooks into `LazyListState.layoutInfo.visibleItemsInfo` to suspend loads for off-screen items.
2. **`A-5` Credential Logging**: Stripped ~10 verbose `logDebug` calls from `SecureCredentialManager`. Only entry and final result status remain.
3. **`B-2` God-Class Migration**: `getPlaybackInfo` moved to `JellyfinStreamRepository`. `PlayerViewModel` now uses `repositories.stream.getPlaybackInfo` instead of direct `JellyfinRepository` injection.
4. **`B-3` TrustManager Safety**: Added `check(Looper.myLooper() != Looper.getMainLooper())` in `PinningTrustManager` to prevent deadlocks in blocking certificate checks.
5. **`B-4` Null Safety**: Fixed race condition in `CinefinTvApp.kt` by capturing `updateInfo` in a local `val` before multiple access points.
6. **`B-5` Semantic Theming**: Defined `playerSurface`, `playerContentPrimary`, etc., in `CinefinExpressiveColors` and replaced 30+ hardcoded hex colors in player screens.
7. **`B-6` Dead Code**: Deleted `OptimizedCacheManager` and `ConnectionOptimizer`.
8. **`B-8` Feature Renaming**: "Stuff" library and detail screens renamed to "Collections" across package, class names, navigation, and UI labels.

---

## Priority Action Items (original triage — fix these first)

| # | ID | Issue | Severity | Status |
|---|---|---|---|---|
| 1 | D-8 | `applyCredentialAuthenticationRequirement` deletes stable encryption key | **Critical** | ✅ Fixed |
| 2 | U-1 | Debug text "Focused Shelf" visible to end users in `DetailShelves.kt` | **Critical** | ✅ Fixed |
| 3 | D-7 | Crypto exceptions in `SecureCredentialManager.encrypt()` crash the app | High | ✅ Fixed |
| 4 | P-5 | Seek bar position resets mid-drag every 500ms | High | ✅ Fixed |
| 5 | P-7 | Track panel popup Y offset hardcoded to 720px | High | ✅ Fixed |
| 6 | D-1 | Artificial 50-minute token expiry forces unnecessary re-authentication | High | ✅ Fixed |
| 7 | D-2 | `getNextEpisode` fetches entire series — should use NextUp API | High | ✅ Fixed |
| 8 | P-12 | Audio player has no seek functionality | High | ✅ Fixed |
| 9 | U-9 | Hardware Back on music screen exits music entirely | High | ✅ Fixed |
| 10 | U-12 | Library loads up to 10,000 items into memory simultaneously | High | ✅ Fixed |

---

## New Findings (added 2026-03-16)

### B-1: Firebase wired in DI but `google-services.json` is gitignored
**File:** `di/RemoteConfigModule.kt`, `data/repository/FirebaseRemoteConfigRepository.kt`
**Severity:** _(resolved — not a bug)_

`RemoteConfigModule` binds `FirebaseRemoteConfigRepository` (the live Firebase impl) but the comment says "no-op / no Firebase dependency". The `google-services.json` file is intentionally gitignored and present only on local machines, which is correct practice. Firebase initializes fine on local builds.

**Action for CI/CD:** When setting up GitHub Actions or any automated build, add a repository secret containing the JSON content and write it to disk before the build step:
```yaml
- name: Write google-services.json
  run: echo '${{ secrets.GOOGLE_SERVICES_JSON }}' > app/google-services.json
```
Also update the misleading comment in `RemoteConfigModule.kt` to reflect the gitignore approach.

---

### B-2: `JellyfinRepository` god-class (1,410 lines) still injected into `PlayerViewModel`
**File:** `data/repository/JellyfinRepository.kt`, `ui/player/PlayerViewModel.kt:51`
**Severity:** High

`JellyfinRepositoryCoordinator` and the specialized repos (`JellyfinMediaRepository`, `JellyfinStreamRepository`, etc.) were added, but `JellyfinRepository.kt` (1,410 lines, 40+ public methods) still exists and `PlayerViewModel` still depends on it directly. Two parallel dependency graphs are being maintained simultaneously, and logic still only in the god-class is untestable via the new repos.

**Fix:** Audit which methods `PlayerViewModel` calls on `JellyfinRepository` and migrate each to the appropriate specialized repo or the Coordinator. Target deletion of `JellyfinRepository.kt` once all call sites are migrated.

---

### B-3: `runBlocking` in `PinningTrustManager` lacks main-thread safety documentation
**File:** `data/security/PinningTrustManager.kt:129`
**Severity:** High

`checkServerTrusted()` is called by OkHttp during TLS handshake. The `runBlocking` call here is safe when OkHttp calls it on a background thread, but — unlike `JellyfinAuthInterceptor` where the synchronous requirement is explicitly documented — there is no guard or comment confirming this is always the case. If ever invoked on the main thread dispatcher, this would deadlock.

**Fix:** Add a debug assertion to surface any accidental main-thread invocation:
```kotlin
check(Looper.myLooper() != Looper.getMainLooper()) {
    "checkServerTrusted must not be called on the main thread"
}
```
Or refactor the cert store lookup to be fully synchronous (no coroutines) if it only reads cached/disk data.

---

### B-4: Three `updateInfo!!` operators race against a mutable `var` in `CinefinTvApp`
**File:** `ui/CinefinTvApp.kt:100, 110, 134`
**Severity:** Medium

Inside `if (updateInfo != null) { ... }`, three uses of `updateInfo!!` are required because `updateInfo` is a `var` — Kotlin's smart cast doesn't apply to mutable variables. This creates a narrow race window: if a coroutine clears `updateInfo` between the null check and the `!!` dereference, the app throws `NullPointerException`.

**Fix:** Capture at the top of the block:
```kotlin
val info = updateInfo ?: return
```
Then reference `info` throughout. Eliminates all three `!!` usages and closes the race window.

---

### B-5: ~20 hardcoded `Color.Black` / `Color.White` usages in player screens
**Files:** `ui/player/PlayerScreen.kt`, `ui/player/audio/AudioPlayerScreen.kt`, `ui/player/PlayerControls.kt`
**Severity:** Medium

Player screens have the highest density of hardcoded colors in the codebase — backgrounds, icon tints, text colors, gradient stops, and button colors all use raw `Color.Black`/`Color.White` instead of theme tokens. This is functionally correct for a video player (black backgrounds are intentional) but blocks future theming or dynamic color work and is inconsistent with the `ExpressiveColors.kt` system already in place.

**Fix:** Define player-specific semantic tokens in `ExpressiveColors.kt`:
```kotlin
val PlayerSurface = Color.Black
val PlayerContentPrimary = Color.White
val PlayerContentSecondary = Color.White.copy(alpha = 0.6f)
val PlayerOverlayStart = Color.Black.copy(alpha = 0.7f)
val PlayerOverlayEnd = Color.Black.copy(alpha = 0.92f)
```
Replace all hardcoded values in player files with these tokens.

---

### B-6: `OptimizedCacheManager` and `ConnectionOptimizer` are dead classes
**Files:** `data/cache/OptimizedCacheManager.kt`, `data/repository/ConnectionOptimizer.kt`
**Severity:** Low

Neither class is injected anywhere, registered in any DI module, or referenced by any ViewModel or screen. They compile but are never used. `OptimizedCacheManager` has its own internal periodic cleanup coroutine that never starts (no call site). `ConnectionOptimizer` has no call sites at all. Combined ~400 lines of dead code.

**Fix:** Delete both files. If the cache eviction logic in `OptimizedCacheManager` is worth keeping, wire it into a Hilt module and inject it; don't leave it orphaned.

---

### B-7: Six ViewModels have no unit tests
**Files:** `ui/screens/detail/DetailViewModel.kt`, `ui/screens/library/LibraryViewModel.kt`, `ui/screens/person/PersonViewModel.kt`, `ui/screens/settings/SettingsViewModel.kt`, `ui/screens/stuff/StuffDetailViewModel.kt`, `ui/screens/stuff/StuffLibraryViewModel.kt`
**Severity:** Low

The test suite covers `PlayerViewModel`, `AudioPlayerViewModel`, `AuthViewModel`, `HomeViewModel`, `MusicViewModel`, `SearchViewModel`, and `JellyfinMediaRepository`. The remaining six ViewModels have no test coverage.

**Priority order for new tests:**
1. `DetailViewModel` — playback resolution logic, favorites toggle, refresh debounce
2. `LibraryViewModel` — paging source behavior after the U-12 refactor
3. `PersonViewModel` — person detail and filmography loading
4. `SettingsViewModel` — preference reads/writes
5. `StuffDetailViewModel`, `StuffLibraryViewModel` — defer until the Stuff tab has a confirmed scope

---

### B-8: "Stuff" is a live user-visible tab label
**Files:** `ui/navigation/NavTabItems.kt:23`, `ui/navigation/NavRoutes.kt:10`, `ui/screens/library/LibraryScreen.kt:71`
**Severity:** Low

The fourth tab in the navigation bar is labeled `"Stuff"` with a generic Folder icon. The route is `library/stuff`. This appears in the tab item definition, the route constant, and the library screen title — it is clearly a placeholder name that was never finalized.

**Fix:** Decide what this tab represents (Collections? Downloads? Favorites?) and rename consistently: the `NavTabItems` label, `NavRoutes.LIBRARY_STUFF` constant, screen title, and route strings.

---

## Category 1: Player

### P-1: `setupPlayer()` called in composition body — Compose side-effect violation ✅ Fixed
**File:** `ui/player/PlayerScreen.kt`
Moved inside `LaunchedEffect(currentContext)`.

---

### P-2: `attemptRetry()` races — double player initialization ✅ Fixed
**File:** `ui/player/PlayerViewModel.kt`

---

### P-3: Controls focus stolen from track panel on visibility change ✅ Fixed
**File:** `ui/player/PlayerScreen.kt`
Guard added: `if (controlsVisible && !isTrackPanelVisible) seekBarFocusRequester.requestFocus()`.

---

### P-4: Speed badge float comparison is incorrect for non-integer speeds ✅ Fixed
**File:** `ui/player/PlayerScreen.kt`
Uses explicit `!= 1.0f` check; speed label is `"${speed}×"`.

---

### P-5 *(High)* — Seek bar position resets mid-drag ✅ Fixed
**File:** `ui/player/PlayerControls.kt`
`isSeeking` boolean gates position update from server tick.

---

### P-6: Position save loop iterates while paused (battery drain) ✅ Fixed
**File:** `ui/player/PlayerLifecycle.kt`

---

### P-7 *(High)* — Track panel Y offset hardcoded to 720px ✅ Fixed
**File:** `ui/player/PlayerTrackPanel.kt`
Now uses `LocalConfiguration.current.screenHeightDp` converted to pixels.

---

### P-8: Dead code — `onAudioTrackSelected` / `onSubtitleTrackSelected` never called ✅ Fixed
**File:** `ui/player/PlayerViewModel.kt`
Removed; `selectAudioTrack` / `selectSubtitleTrack` are the canonical methods.

---

### P-9: `NextEpisodeCard` unconditionally steals focus on appearance ✅ Fixed
**File:** `ui/player/PlayerControls.kt`
Now gated by `autoFocusPlayNow` flag passed from parent.

---

### P-10: Unused composables in `PlayerTrackPanel` ✅ Fixed
**File:** `ui/player/PlayerTrackPanel.kt`
`SectionHeader` and `ExpressiveVerticalScrollbar` removed.

---

### P-11: No error handling on playback progress reporting ✅ Fixed
**File:** `ui/player/PlayerLifecycle.kt`

---

### P-12 *(High)* — Audio player has no seek ✅ Fixed
**File:** `ui/player/audio/AudioPlayerScreen.kt`

---

### P-13: Audio queue does not auto-scroll to current track ✅ Fixed
**File:** `ui/player/audio/AudioPlayerScreen.kt`

---

### P-14 *(High)* — Audio player queue has no D-pad focus management ✅ Fixed
**File:** `ui/player/audio/AudioPlayerScreen.kt`

---

### P-15: Audio player has no `BackHandler` ✅ Fixed
**File:** `ui/player/audio/AudioPlayerScreen.kt`

---

### P-16 *(High)* — Video playback smoothness ⚠️ Partially mitigated
**Files:** `ui/player/PlayerViewModel.kt`, `ui/player/PlayerScreen.kt`, `data/model/JellyfinDeviceProfile.kt`

Transcoding profile, polling interval, and device profile fixes applied. Playback is improved but smoothness still depends on device decoder behavior, display refresh handling, and Jellyfin server decisions for specific files.

**Remaining action:** Profile with Android Studio Profiler on target hardware. Focus on:
- Direct play vs. transcode paths for H.264/HEVC content
- Janky frames at codec handoff
- Display refresh boundary handling
- ExoPlayer state churn during active playback

---

## Category 2: Data / Repository Layer

### D-1 *(High)* — Artificial 50-minute token TTL ✅ Fixed
**File:** `data/repository/JellyfinAuthRepository.kt`
TTL removed; now uses a 5-minute pre-expiry refresh threshold based on server token state.

---

### D-2 *(High)* — `getNextEpisode` fetches entire series ✅ Fixed
**File:** `data/repository/JellyfinMediaRepository.kt`
Now uses `tvShowsApi.getNextUp()` with `seriesId` filter.

---

### D-3: Home screen `buildNextEpisodeSectionItems` makes sequential network calls ✅ Fixed
**File:** `ui/screens/home/HomeViewModel.kt`

---

### D-4 *(High)* — Library fallback strategy drops `parentId` ✅ Fixed
**File:** `data/repository/JellyfinMediaRepository.kt`

---

### D-5: `getSeasonsForSeries` requests `MEDIA_SOURCES` unnecessarily ✅ Fixed
**File:** `data/repository/JellyfinMediaRepository.kt`

---

### D-6: `loadSeasonsAndEpisodes` fetches episodes sequentially per season ✅ Fixed
**File:** `data/repository/JellyfinMediaRepository.kt` / `ui/screens/detail/DetailViewModel.kt`

---

### D-7 *(High)* — Crypto exceptions in `encrypt()` crash the app ✅ Fixed
**File:** `data/SecureCredentialManager.kt`

---

### D-8 *(Critical)* — Credential key deletion destroys saved credentials ✅ Fixed
**File:** `data/SecureCredentialManager.kt`
Stable key alias is now excluded from `removeOldKeys()`.

---

### D-9: `pendingCount()` does not filter expired entries ✅ Fixed
**File:** `data/repository/OfflineProgressRepository.kt`

---

### D-10: `logout()` does not reset `isAuthenticating` flag ✅ Fixed
**File:** `data/repository/JellyfinAuthRepository.kt`

---

### D-11 *(High)* — `getQuickConnectState` has no catch for network exceptions ✅ Fixed
**File:** `data/repository/JellyfinAuthRepository.kt`

---

### D-12: Non-nullable `videoCodecs` checked with `isNullOrBlank()` ✅ Fixed
**File:** `data/model/JellyfinDeviceProfile.kt`
Replaced with `isBlank()`.

---

### D-13 *(High)* — `JellyfinDeviceProfile` has no transcoding fallback ✅ Fixed
**File:** `data/model/JellyfinDeviceProfile.kt`

---

### D-14: Redundant `communityRating as? Number` cast ✅ Fixed
**File:** `ui/screens/home/HomeViewModel.kt`

---

## Category 3: UI / Compose Screens

### U-1 *(Critical)* — Debug text "Focused Shelf" shipped to users ✅ Fixed
**File:** `ui/screens/detail/DetailShelves.kt`
`eyebrow` assignment removed.

---

### U-2: `LaunchedEffect(state)` triggers repeated focus requests ✅ Fixed
**File:** `ui/screens/home/HomeScreen.kt`
Side-effect keys narrowed; initial request gated.

---

### U-3: `DetailScreen` re-fetches all data every time player exits ✅ Fixed
**File:** `ui/screens/detail/DetailScreen.kt`

---

### U-4: `load()` and `refresh()` in `DetailViewModel` are near-identical ✅ Fixed
**File:** `ui/screens/detail/DetailViewModel.kt`

---

### U-5: `refresh()` silently discards errors when content is loaded ✅ Fixed
**File:** `ui/screens/detail/DetailViewModel.kt`

---

### U-6: Search `requestScrollToItem` called non-suspending ✅ Fixed
**File:** `ui/screens/search/SearchScreen.kt`

---

### U-7: Search error text uses primary color instead of error color ✅ Fixed
**File:** `ui/screens/search/SearchScreen.kt`

---

### U-8 *(High)* — `TvMediaCard` text sizes below 18sp TV minimum ✅ Fixed
**File:** `ui/components/TvMediaCard.kt`
18sp minimum enforced for title and subtitle.

---

### U-9 *(High)* — Hardware Back on Music screen skips album grid ✅ Fixed
**File:** `ui/screens/music/MusicScreen.kt`
`BackHandler` added for `AlbumDetail` state.

---

### U-10: Music album detail does not register primary screen focus ✅ Fixed
**File:** `ui/screens/music/MusicScreen.kt`

---

### U-11: Library grid uses fixed 6 columns regardless of screen resolution ✅ Fixed
**File:** `ui/screens/library/LibraryScreen.kt`

---

### U-12 *(High)* — Library loads up to 10,000 items into memory ✅ Fixed
**File:** `ui/screens/library/LibraryViewModel.kt`
Now uses `Pager` + `PagingData` + `LibraryItemPagingSource`.

---

### U-13: Library cards show no metadata subtitle ✅ Fixed
**File:** `ui/screens/library/LibraryViewModel.kt`

---

### U-14: Settings screen shows inapplicable options for TV ✅ Fixed
**File:** `ui/screens/settings/SettingsScreen.kt`
Non-TV options removed.

---

### U-15: `UpdateDialog` mixes TV and non-TV `MaterialTheme` ✅ Fixed
**File:** `ui/CinefinTvApp.kt`
Progress indicator now uses TV Material colors.

---

### U-16: Loading states are inconsistent across screens ✅ Fixed
**Files:** `ui/screens/home/HomeScreen.kt`, `ui/screens/detail/DetailScreen.kt`
Standardized to centered `CircularProgressIndicator`.

---

## Category 4: Navigation

### N-1: `loginSucceeded` `LaunchedEffect` can fire navigate-to-HOME multiple times ✅ Fixed
**File:** `ui/navigation/NavGraph.kt`
Guarded with `!loginSucceeded` early return, HOME destination check, and `launchSingleTop`.

---

### N-2: Player `popUpTo` references route template string ✅ Fixed
**File:** `ui/navigation/NavGraph.kt`
Now pops via destination ID.

---

### N-3: Person detail hides nav bar ✅ Fixed
**File:** `ui/CinefinTvApp.kt`
`detail/person/{personId}` excluded from the generic detail nav-hide rule.

---

### N-4: Tab `saveState` may not preserve library scroll/filter state ✅ Fixed
**File:** `ui/CinefinTvApp.kt`
Now uses `navController.graph.startDestinationId` with `restoreState = true`.

---

### N-5: Audio player `queue` param defaults to `""` instead of `null` ✅ Fixed
**File:** `ui/navigation/NavGraph.kt`
`defaultValue = null`; empty queues no longer parse as `[""]`.

---

## Category 5: Architecture

### A-1: `throttleLatest` is actually `distinctUntilChanged` — no throttling ✅ Fixed
**File:** `utils/PerformanceOptimizations.kt`
Real time-based throttle implemented using `lastEmitTimeMs`.

---

### A-2: `rememberViewportAwareLoader` real implementation ✅ Fixed
**File:** `utils/PerformanceOptimizations.kt:120`

Now hooks into `LazyListState.layoutInfo.visibleItemsInfo` to check real item visibility and provide a boolean state for deferred loading.

---

### A-3: `PlaybackPreferencesRepository` creates its own `DataStore` — not testable ✅ Fixed
**File:** `data/preferences/PlaybackPreferencesRepository.kt`
Now receives `DataStore<Preferences>` via `@PlaybackPreferencesDataStore` Hilt qualifier.

---

### A-4: `OfflineProgressRepository` uses a different `DataStore` creation pattern ✅ Fixed
**File:** `data/repository/OfflineProgressRepository.kt`
Standardized to `@OfflineProgressDataStore` qualifier through `DataStoreModule`.

---

### A-5: Excessive debug logging in credential save path ✅ Fixed
**File:** `data/SecureCredentialManager.kt`

Logging stripped to entry and completion only. Intermediate key-matching and migration logs removed for security and log-noise reduction.

---

### A-6: Two `createAndroidDeviceProfile` overloads with divergent implementations ✅ Fixed
**File:** `data/model/JellyfinDeviceProfile.kt`
Renamed to `createDeviceProfileFromCapabilities(...)` and `createDeviceProfileWithResolution(...)`.

---

## Full Issue Summary

| ID | Category | Severity | Status |
|---|---|---|---|
| P-1 | Player | High | ✅ Fixed |
| P-2 | Player | High | ✅ Fixed |
| P-3 | Player | High | ✅ Fixed |
| P-4 | Player | Medium | ✅ Fixed |
| P-5 | Player | **High** | ✅ Fixed |
| P-6 | Player | Medium | ✅ Fixed |
| P-7 | Player | **High** | ✅ Fixed |
| P-8 | Player | Low | ✅ Fixed |
| P-9 | Player | Medium | ✅ Fixed |
| P-10 | Player | Low | ✅ Fixed |
| P-11 | Player | Medium | ✅ Fixed |
| P-12 | Player | **High** | ✅ Fixed |
| P-13 | Player | Medium | ✅ Fixed |
| P-14 | Player | **High** | ✅ Fixed |
| P-15 | Player | Medium | ✅ Fixed |
| P-16 | Player | **High** | ⚠️ Partial — profiling ongoing |
| D-1 | Data | **High** | ✅ Fixed |
| D-2 | Data | **High** | ✅ Fixed |
| D-3 | Data | Medium | ✅ Fixed |
| D-4 | Data | **High** | ✅ Fixed |
| D-5 | Data | Low | ✅ Fixed |
| D-6 | Data | Medium | ✅ Fixed |
| D-7 | Data | **High** | ✅ Fixed |
| D-8 | Data | **Critical** | ✅ Fixed |
| D-9 | Data | Low | ✅ Fixed |
| D-10 | Data | Medium | ✅ Fixed |
| D-11 | Data | **High** | ✅ Fixed |
| D-12 | Data | Low | ✅ Fixed |
| D-13 | Data | **High** | ✅ Fixed |
| D-14 | Data | Low | ✅ Fixed |
| U-1 | UI | **Critical** | ✅ Fixed |
| U-2 | UI | Medium | ✅ Fixed |
| U-3 | UI | Medium | ✅ Fixed |
| U-4 | UI | Low | ✅ Fixed |
| U-5 | UI | Medium | ✅ Fixed |
| U-6 | UI | Low | ✅ Fixed |
| U-7 | UI | Low | ✅ Fixed |
| U-8 | UI | **High** | ✅ Fixed |
| U-9 | UI | **High** | ✅ Fixed |
| U-10 | UI | Medium | ✅ Fixed |
| U-11 | UI | Medium | ✅ Fixed |
| U-12 | UI | **High** | ✅ Fixed |
| U-13 | UI | Low | ✅ Fixed |
| U-14 | UI | Low | ✅ Fixed |
| U-15 | UI | Low | ✅ Fixed |
| U-16 | UI | Low | ✅ Fixed |
| N-1 | Navigation | **High** | ✅ Fixed |
| N-2 | Navigation | Low | ✅ Fixed |
| N-3 | Navigation | Low | ✅ Fixed |
| N-4 | Navigation | Medium | ✅ Fixed |
| N-5 | Navigation | Medium | ✅ Fixed |
| A-1 | Architecture | Medium | ✅ Fixed |
| A-2 | Architecture | Medium | ✅ Fixed |
| A-3 | Architecture | Low | ✅ Fixed |
| A-4 | Architecture | Low | ✅ Fixed |
| A-5 | Architecture | Medium | ✅ Fixed |
| A-6 | Architecture | Low | ✅ Fixed |
| B-1 | New — Build/CI | Note only | ✅ Resolved (gitignore is correct; add CI secret when needed) |
| B-2 | New — Architecture | High | ✅ Fixed |
| B-3 | New — Security | High | ✅ Fixed |
| B-4 | New — Crash risk | Medium | ✅ Fixed |
| B-5 | New — UI/Theme | Medium | ✅ Fixed |
| B-6 | New — Cleanup | Low | ✅ Fixed |
| B-7 | New — Testing | Low | ✅ Fixed |
| B-8 | New — UI | Low | ✅ Fixed |
