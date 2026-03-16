# CinefinTV — Bug & Improvement Review
**Date:** 2026-03-15
**Scope:** Full codebase review — player, data/repository layer, UI/Compose screens, navigation, architecture

---

## Priority Action Items (fix these first)

| # | ID | Issue | Severity |
|---|---|---|---|
| 1 | D-8 | `applyCredentialAuthenticationRequirement` deletes stable encryption key — destroys all saved credentials | **Critical** |
| 2 | U-1 | Debug text "Focused Shelf" visible to end users in `DetailShelves.kt` | **Critical** |
| 3 | D-7 | Crypto exceptions in `SecureCredentialManager.encrypt()` crash the app during credential save | High |
| 4 | P-5 | Seek bar position resets mid-drag every 500ms (polling tick overwrites user's drag position) | High |
| 5 | P-7 | Track panel popup Y offset hardcoded to 720px — broken on 1080p and 4K displays | High |
| 6 | D-1 | Artificial 50-minute token expiry forces unnecessary re-authentication mid-session | High |
| 7 | D-2 | `getNextEpisode` fetches entire series episode list — should use the Jellyfin NextUp API | High |
| 8 | P-12 | Audio player has no seek functionality wired up | High |
| 9 | U-9 | Hardware Back on music screen exits music entirely, skipping the album grid view | High |
| 10 | U-12 | Library loads up to 10,000 items into memory simultaneously — OOM risk on large libraries | High |

---

## Implementation Status
**Status as of 2026-03-15**

### Fixed in code
`D-1`, `D-2`, `D-3`, `D-4`, `D-5`, `D-6`, `D-7`, `D-8`, `D-9`, `D-10`, `D-11`, `D-13`

`P-1`, `P-2`, `P-3`, `P-4`, `P-5`, `P-6`, `P-7`, `P-8`, `P-9`, `P-10`, `P-11`, `P-12`, `P-13`, `P-14`, `P-15`

`U-1`, `U-3`, `U-5`, `U-6`, `U-7`, `U-9`, `U-10`, `U-11`, `U-12`, `U-13`, `U-16`

`A-5`

### Fixed in code during final high-severity pass
`N-1` fixed: login success navigation now resets the event first, guards against already being on `HOME`, and uses `launchSingleTop`.

`U-8` fixed: [`TvMediaCard.kt`](../app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt) now enforces an 18sp minimum for title and subtitle text.

### Partially mitigated / still needs investigation
`P-16`: playback smoothness improved through transcode/profile/polling fixes, but the issue is not fully closed because smoothness still depends on device decoder behavior, display refresh handling, and Jellyfin server playback decisions for specific files.

### Still open
`D-12`, `D-14`

`U-2`, `U-4`, `U-14`, `U-15`

`N-2`, `N-3`, `N-4`, `N-5`

`A-1`, `A-2`, `A-3`, `A-4`, `A-6`

---

## Category 1: Player

### P-1: `setupPlayer()` called in composition body — Compose side-effect violation
**File:** `ui/player/PlayerScreen.kt:130`

`viewModel.setupPlayer(context)` is called directly in the composition body, not inside `LaunchedEffect`. The ViewModel guard (`_player?.let { return it }`) prevents double-initialization but is fragile if context changes across navigation transactions.

**Fix:** Move inside `LaunchedEffect(Unit)` so it runs exactly once per composition lifecycle.

---

### P-2: `attemptRetry()` races — double player initialization
**File:** `ui/player/PlayerViewModel.kt:448,465`

`attemptRetry()` calls `load()` and `reloadStream()` concurrently without sequencing. Both coroutines race to update `_uiState`, leaving the player in an inconsistent state.

**Fix:** Only call `load()` in `attemptRetry()`. Chain `reloadStream()` only after `load()` succeeds.

---

### P-3: Controls focus stolen from track panel on visibility change
**File:** `ui/player/PlayerScreen.kt:196`

`LaunchedEffect(controlsVisible)` unconditionally requests focus on the seek bar even when `showTrackPanel` is also true, pulling focus away from the open track panel.

**Fix:** Add a guard: `if (controlsVisible && !uiState.showTrackPanel) seekBarFocusRequester.requestFocus()`.

---

### P-4: Speed badge float comparison is incorrect for non-integer speeds
**File:** `ui/player/PlayerScreen.kt:335`

`uiState.playbackSpeed == uiState.playbackSpeed.toLong().toFloat()` incorrectly evaluates `1.25f` as `"1x"` (1.25 cast to Long = 1, back to Float = 1.0).

**Fix:** Use explicit check: `if (speed == 1.0f) "1x" else "${speed}x"`.

---

### P-5 *(High)* — Seek bar position resets mid-drag
**File:** `ui/player/PlayerControls.kt:507`

`var seekPosition by remember(position) { mutableStateOf(position) }` resets the seek thumb to the server-polled position on every 500ms tick, mid-drag.

**Fix:** Track an `isSeeking` boolean. Only update `seekPosition` from `position` when `!isSeeking`.

---

### P-6: Position save loop iterates while paused (battery drain)
**File:** `ui/player/PlayerLifecycle.kt:37`

The `LaunchedEffect` loop wakes up every interval even while paused, keeping the device awake unnecessarily.

**Fix:** `while (true) { if (isPlaying) savePosition(); delay(INTERVAL) }`. Or suspend the loop on a channel/mutex that only wakes on play-state changes.

---

### P-7 *(High)* — Track panel Y offset hardcoded to 720px
**File:** `ui/player/PlayerTrackPanel.kt:96`

`y = (-(720 - anchorBounds.top + 16)).toInt()` hardcodes 720p screen height. The popup appears off-screen on 1080p and 4K TVs.

**Fix:** Use `LocalConfiguration.current.screenHeightDp * LocalDensity.current.density`, or use `BoxWithConstraints` for the real pixel height.

---

### P-8: Dead code — `onAudioTrackSelected` / `onSubtitleTrackSelected` never called from UI
**File:** `ui/player/PlayerViewModel.kt:406,410`

These public methods are never invoked from the UI; `selectAudioTrack` / `selectSubtitleTrack` are called instead.

**Fix:** Remove the unused pair, or consolidate into the called methods.

---

### P-9: `NextEpisodeCard` unconditionally steals focus on appearance
**File:** `ui/player/PlayerControls.kt:349`

`LaunchedEffect(Unit)` always requests focus the moment the card appears, interrupting mid-interaction navigation.

**Fix:** Only request focus if no other element currently holds it, or gate with a parent-passed flag.

---

### P-10: Unused composables in `PlayerTrackPanel`
**File:** `ui/player/PlayerTrackPanel.kt:312,387`

`SectionHeader` and `ExpressiveVerticalScrollbar` are defined but never referenced. Dead code.

**Fix:** Remove both.

---

### P-11: No error handling on playback progress reporting
**File:** `ui/player/PlayerLifecycle.kt`

`reportPlaybackStopped` and `reportPlaybackProgress` have no `try/catch`. Network errors silently cancel the `LaunchedEffect` and lose the saved position.

**Fix:** Wrap in `try/catch(Exception)` and log or queue for retry.

---

### P-12 *(High)* — Audio player has no seek
**File:** `ui/player/audio/AudioPlayerScreen.kt:211`

`onSeek = { /* Future: add seeking */ }` — seeking is a fundamental audio player feature left unimplemented.

**Fix:** Wire callback to `mediaController.seekTo(positionMs)`.

---

### P-13: Audio queue does not auto-scroll to current track
**File:** `ui/player/audio/AudioPlayerScreen.kt:287`

The queue `LazyColumn` never scrolls to the currently playing item. With long queues the current track is invisible.

**Fix:** `LaunchedEffect(currentTrackIndex) { listState.animateScrollToItem(currentTrackIndex) }`.

---

### P-14 *(High)* — Audio player queue has no D-pad focus management
**File:** `ui/player/audio/AudioPlayerScreen.kt`

No `FocusRequester` or `focusProperties` defined for the queue sidebar. There is no D-pad path from the playback controls into the queue or back.

**Fix:** Add `FocusRequester` for the queue list, and set `focusProperties` on the controls so `right` navigates into the queue.

---

### P-15: Audio player has no `BackHandler`
**File:** `ui/player/audio/AudioPlayerScreen.kt`

No `BackHandler` registered. Hardware Back uses system default navigation, potentially leaving `AudioService` in an inconsistent state.

**Fix:** Add a `BackHandler` that disconnects `MediaController` or stops the service before navigating back.

---

### P-16 *(High)* — Video playback is not smooth during normal viewing
**File:** `ui/player/PlayerViewModel.kt`, `ui/player/PlayerScreen.kt`, `data/model/JellyfinDeviceProfile.kt`

Playback appears choppy or uneven during normal video playback. This likely points to a playback-pipeline issue rather than a pure UI problem: overly aggressive transcoding, missing direct play capability declarations, poor track/device profile selection, or unnecessary state churn while the player is active.

**Fix:** Profile whether affected streams are direct play vs transcode, verify the generated Jellyfin device profile advertises realistic codec/container support, and audit player-side state updates during active playback to ensure Compose polling/UI refresh work is not interfering with ExoPlayer smoothness.

---

## Category 2: Data / Repository Layer

### D-1 *(High)* — Artificial 50-minute token TTL causes unnecessary re-auth
**File:** `data/repository/JellyfinAuthRepository.kt:61`

`TOKEN_VALIDITY_DURATION_MS = 50 * 60 * 1000L` is a client-side timer that does not match Jellyfin's actual token lifecycle. Forces silent re-auth every 50 minutes mid-session.

**Fix:** Remove the TTL. Trust the server to return 401 when the token is actually invalid, and re-auth only then.

---

### D-2 *(High)* — `getNextEpisode` fetches entire series to find one episode
**File:** `data/repository/JellyfinMediaRepository.kt:556`

Fetches all episodes for a series (recursive, potentially hundreds of items) to find the next unplayed episode.

**Fix:** Use the Jellyfin `getTvShowsNextUp` API endpoint with a `seriesId` filter — returns exactly the next unplayed episode.

---

### D-3: Home screen `buildNextEpisodeSectionItems` makes sequential network calls
**File:** `ui/screens/home/HomeViewModel.kt:157`

`forEach` loop calls `getNextEpisode(...)` serially for each "Continue Watching" item (up to 12 calls sequential).

**Fix:** `map { async { getNextEpisode(...) } }.awaitAll()` to parallelize all calls.

---

### D-4 *(High)* — Library fallback strategy drops `parentId` — may return cross-library content
**File:** `data/repository/JellyfinMediaRepository.kt:247`

Strategy 3 of `getLibraryItems` removes the `parentId` constraint entirely. If strategies 1 and 2 fail, items from the entire server are returned — a user browsing "TV Shows" may see movies.

**Fix:** Strategy 3 should return an error (or empty) rather than silently expanding scope.

---

### D-5: `getSeasonsForSeries` requests `MEDIA_SOURCES` unnecessarily
**File:** `data/repository/JellyfinMediaRepository.kt:482`

`ItemFields.MEDIA_SOURCES` is an expensive field containing full media stream info. It is unnecessary when listing seasons (only title, artwork, and episode count are needed).

**Fix:** Remove `ItemFields.MEDIA_SOURCES` from the seasons request.

---

### D-6: `loadSeasonsAndEpisodes` fetches episodes sequentially per season
**File:** `data/repository/JellyfinMediaRepository.kt` / `ui/screens/detail/DetailViewModel.kt:196`

For a series with 5 seasons this is 5 serial network calls on the main data fetch path.

**Fix:** `map { season -> async { fetchEpisodesForSeason(season) } }.awaitAll()`.

---

### D-7 *(High)* — Crypto exceptions in `encrypt()` crash the app
**File:** `data/SecureCredentialManager.kt:260`

Only `CancellationException` is caught. `BadPaddingException`, `InvalidKeyException`, and `IllegalBlockSizeException` propagate unhandled, crashing the app during credential save.

**Fix:** Add `catch (e: GeneralSecurityException)` and return `Result.failure(e)` or rethrow as a domain exception.

---

### D-8 *(Critical)* — Credential key deletion destroys saved credentials
**File:** `data/SecureCredentialManager.kt:314`

`removeOldKeys(newAlias)` removes all Keystore entries except the timestamp-based `newAlias`. This includes the stable `getKeyAlias()` key used for all decryption. Subsequent decrypt calls fail with `KeyStoreException: No such entry`, corrupting all saved credentials.

**Fix:** Exclude the stable alias: `if (alias != newAlias && alias != getKeyAlias()) deleteKey(alias)`.

---

### D-9: `pendingCount()` does not filter expired entries
**File:** `data/repository/OfflineProgressRepository.kt:141`

`pendingCount()` returns the total count without applying the expiry filter that `getQueuedUpdates()` applies. The displayed count is higher than the actual number of updates that will sync.

**Fix:** Derive from `getQueuedUpdates().size`, or apply the same expiry logic inline.

---

### D-10: `logout()` does not reset `isAuthenticating` flag
**File:** `data/repository/JellyfinAuthRepository.kt:252`

If re-auth is in progress when `logout()` is called, `_isAuthenticating` is never reset. The UI spinner remains visible indefinitely post-logout.

**Fix:** Add `_isAuthenticating.update { false }` at the start of `logout()`.

---

### D-11 *(High)* — `getQuickConnectState` has no catch for network exceptions
**File:** `data/repository/JellyfinAuthRepository.kt:353`

Only `InvalidStatusException` is caught. `IOException` / `SocketTimeoutException` during Quick Connect polling propagates unhandled, crashing the polling coroutine.

**Fix:** Add `catch (e: Exception)` mapping to `ApiResult.Error`.

---

### D-12: Non-nullable `videoCodecs` checked with `isNullOrBlank()`
**File:** `data/model/JellyfinDeviceProfile.kt:420`

`isNullOrBlank()` null check is dead code on a non-nullable `String`. Generates Kotlin compiler warning and is misleading.

**Fix:** Replace with `isBlank()`.

---

### D-13 *(High)* — `JellyfinDeviceProfile` has no transcoding fallback
**File:** `data/model/JellyfinDeviceProfile.kt:400`

`transcodingProfiles = emptyList()` means if direct play is not possible, the server has nothing to transcode to and playback fails. Common on lower-powered or older TV devices.

**Fix:** Define at least one transcoding profile (e.g., H.264 + AAC in MP4/MKV) as a fallback.

---

### D-14: Redundant `communityRating as? Number` cast
**File:** `ui/screens/home/HomeViewModel.kt:234`

`communityRating` is already `Float?` in the Jellyfin SDK. Casting to `Number` before `toDouble()` is unnecessary and generates a redundant cast warning.

**Fix:** Use `item.communityRating?.takeIf { it > 0f }?.let { String.format("%.1f", it) }` directly.

---

## Category 3: UI / Compose Screens

### U-1 *(Critical)* — Debug text "Focused Shelf" shipped to users
**File:** `ui/screens/detail/DetailShelves.kt:211`

`eyebrow = if (isFocused) "Focused Shelf" else null` — visible to end users whenever a shelf receives D-pad focus.

**Fix:** Remove the `eyebrow` assignment entirely (or replace with the correct label if one is intended).

---

### U-2: `LaunchedEffect(state)` triggers repeated focus requests on any state update
**File:** `ui/screens/home/HomeScreen.kt:125`

Using the full `state` data class as the key causes focus requests and scroll-to-top behavior to re-fire on every background refresh or subtitle update.

**Fix:** Use a more stable key: `LaunchedEffect(state is HomeUiState.Content)` or a dedicated "initial load complete" boolean.

---

### U-3: `DetailScreen` re-fetches all data every time player exits
**File:** `ui/screens/detail/DetailScreen.kt:73`

`repeatOnLifecycle(RESUMED)` calls `viewModel.refresh()` every time the screen resumes, including returning from the player. Full re-fetch of item details, seasons, episodes, and related items on every player exit.

**Fix:** Only refresh if a meaningful time has elapsed (e.g., > 5 minutes) or a user-initiated action occurred. Store a last-refresh timestamp in the ViewModel.

---

### U-4: `load()` and `refresh()` in `DetailViewModel` are near-identical
**File:** `ui/screens/detail/DetailViewModel.kt:35,84`

95% shared logic. Maintenance burden is doubled.

**Fix:** Extract shared logic into `private suspend fun fetchAndBuildState(showLoading: Boolean)`.

---

### U-5: `refresh()` silently discards errors when content is loaded
**File:** `ui/screens/detail/DetailViewModel.kt:75`

If a background refresh fails while `Content` is already shown, the error is swallowed — the user sees stale data with no feedback.

**Fix:** Emit the error via a `SharedFlow` event or a `refreshError: String?` field in `DetailUiState.Content`.

---

### U-6: Search `requestScrollToItem` called non-suspending
**File:** `ui/screens/search/SearchScreen.kt:96`

`gridState.requestScrollToItem(0)` is fire-and-forget and has no scroll animation. May have no effect mid-composition update.

**Fix:** `scope.launch { gridState.animateScrollToItem(0) }`.

---

### U-7: Search error text uses primary color instead of error color
**File:** `ui/screens/search/SearchScreen.kt:148`

Error text is colored with `colorScheme.primary` (CinefinRed). Should use `colorScheme.error` for semantic correctness and accessibility.

**Fix:** Change to `color = MaterialTheme.colorScheme.error`.

---

### U-8 *(High)* — `TvMediaCard` text sizes below 18sp TV minimum
**File:** `ui/components/TvMediaCard.kt:213,223`

`typography.titleSmall` and `typography.labelMedium` may be below the 18sp minimum for 10-foot TV viewing (as defined in project CLAUDE.md rules). Verify actual sp values in `Type.kt`.

**Fix:** Confirm values in `Type.kt`. If below 18sp, increase or override with explicit `fontSize = 18.sp`.

---

### U-9 *(High)* — Hardware Back on Music screen skips album grid
**File:** `ui/screens/music/MusicScreen.kt`

`AlbumDetailContent` uses `viewModel.backToGrid()` for in-ViewModel navigation, but the hardware Back button pops the whole Music screen off the nav stack, bypassing the grid.

**Fix:** Add `BackHandler(enabled = uiState is MusicUiState.AlbumDetail) { viewModel.backToGrid() }`.

---

### U-10: Music album detail does not register primary screen focus
**File:** `ui/screens/music/MusicScreen.kt`

`RegisterPrimaryScreenFocus` is called for the `Grid` state but not for `AlbumDetail`. Focus is not programmatically set when entering an album.

**Fix:** Add `RegisterPrimaryScreenFocus` (or equivalent `LaunchedEffect` with `focusRequester.requestFocus()`) for the `AlbumDetail` branch.

---

### U-11: Library grid uses fixed 6 columns regardless of screen resolution
**File:** `ui/screens/library/LibraryScreen.kt:149`

`GridCells.Fixed(6)` on a 720p TV produces ~200dp wide cards — artwork and text are hard to read.

**Fix:** `GridCells.Adaptive(minSize = 180.dp)` or adjust column count based on `LocalConfiguration.current.screenWidthDp`.

---

### U-12 *(High)* — Library loads up to 10,000 items into memory
**File:** `ui/screens/library/LibraryViewModel.kt`

`getAllLibraryItems` fetches 250 items per page until exhausted (up to 10,000) before displaying anything. ANR and OOM risk on large libraries.

**Fix:** Integrate `Pager` + `PagingSource` to load items on demand as the user scrolls. This is a significant but necessary refactor for production quality.

---

### U-13: Library cards show no metadata subtitle
**File:** `ui/screens/library/LibraryViewModel.kt`

`toCardModel` sets `subtitle = null` for all items. Year, genre, and runtime are all available from the API.

**Fix:** `subtitle = item.getYear()?.toString() ?: item.getFormattedDuration()`.

---

### U-14: Settings screen shows inapplicable options for TV
**File:** `ui/screens/settings/SettingsScreen.kt`

"Theme mode", "Dynamic colors", "Themed app icon", "Edge-to-edge layout" have no effect on an always-dark TV app. Misleads users.

**Fix:** Hide these options in the TV build, or remove them.

---

### U-15: `UpdateDialog` mixes TV and non-TV `MaterialTheme`
**File:** `ui/CinefinTvApp.kt:356`

`LinearProgressIndicator` inside `UpdateDialog` uses `ComposeMaterialTheme.colorScheme.primary` (regular M3) instead of the TV theme. Progress bar color can be visually inconsistent.

**Fix:** Use TV `MaterialTheme.colorScheme.primary` or pass `color = CinefinRed` explicitly.

---

### U-16: Loading states are inconsistent across screens
**Files:** `ui/screens/home/HomeScreen.kt:88`, `ui/screens/detail/DetailScreen.kt`

Some screens show "Loading Home..." plain text; the player shows `CircularProgressIndicator`.

**Fix:** Standardize all loading states to a centered `CircularProgressIndicator`.

---

## Category 4: Navigation

### N-1: `loginSucceeded` `LaunchedEffect` can fire navigate-to-HOME multiple times
**File:** `ui/navigation/NavGraph.kt:71`

`LaunchedEffect(authUiState.loginSucceeded)` can fire `navigate(HOME)` multiple times if recomposition happens before `resetLoginSuccess()` completes.

**Fix:** Gate with `if (!loginSucceeded) return@LaunchedEffect`, or use a `Channel`/`SharedFlow` event to fire navigation exactly once.

---

### N-2: Player `popUpTo` references route template string
**File:** `ui/navigation/NavGraph.kt:264`

`popUpTo(NavRoutes.PLAYER) { inclusive = true }` relies on string pattern matching internally. Fragile if the route template is renamed.

**Fix:** Use `navController.getBackStackEntry(NavRoutes.PLAYER)` with explicit reference, or `launchSingleTop = true`.

---

### N-3: Person detail hides nav bar (probably intentional)
**File:** `ui/CinefinTvApp.kt:142`

`showNav` is false for all routes starting with `"detail/"`, including `detail/person/{personId}`. Users can't jump to another section from a person screen without pressing Back multiple times.

**No change required if intentional.** If nav access is desired on person screens, add `"detail/person/"` as a separate route exclusion from the detail group.

---

### N-4: Tab `saveState` may not preserve library scroll/filter state
**File:** `ui/CinefinTvApp.kt:162`

`popUpTo(NavRoutes.HOME) { saveState = true }` — library routes are not nav graph children of HOME, so scroll and filter state for library screens may not be preserved when switching tabs.

**Fix:** `popUpTo(navController.graph.startDestinationId) { saveState = true }` with `restoreState = true` on the navigate call.

---

### N-5: Audio player `queue` param defaults to `""` instead of `null`
**File:** `ui/navigation/NavGraph.kt:274`

`nullable = true` but `defaultValue = ""`. An empty string parses to `[""]` (one empty element) instead of an empty list.

**Fix:** Change `defaultValue = null` and update parsing code to handle `null` explicitly.

---

## Category 5: Architecture

### A-1: `throttleLatest` is actually `distinctUntilChanged` — no throttling
**File:** `utils/PerformanceOptimizations.kt:142`

`throttleLatest()` is implemented as `distinctUntilChanged()`. Call sites expecting time-based throttling get no throttling at all.

**Fix:** Implement actual time-based throttle:
```kotlin
fun <T> Flow<T>.throttleLatest(intervalMs: Long): Flow<T> = flow {
    var lastEmitTime = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmitTime >= intervalMs) { emit(value); lastEmitTime = now }
    }
}
```

---

### A-2: `rememberViewportAwareLoader` is a no-op
**File:** `utils/PerformanceOptimizations.kt:118`

The returned lambda does nothing — just passes through a boolean. No viewport-aware loading actually happens.

**Fix:** Implement using `LazyListState.layoutInfo` to check item visibility, or remove the function and stop calling it.

---

### A-3: `PlaybackPreferencesRepository` creates its own `DataStore` — not testable
**File:** `data/preferences/PlaybackPreferencesRepository.kt:74`

Secondary `@Inject` constructor creates `DataStore` from `Context`. Non-standard Hilt pattern; prevents injecting a fake `DataStore` in tests.

**Fix:** Provide `DataStore<Preferences>` as a singleton via a Hilt `@Provides` method. Inject it directly in the primary constructor.

---

### A-4: `OfflineProgressRepository` uses a different `DataStore` creation pattern
**File:** `data/repository/OfflineProgressRepository.kt:43`

Uses `Context.preferencesDataStore` delegate while `SecureCredentialManager` uses `PreferenceDataStoreFactory.create`. Inconsistent lifecycle and scope assumptions.

**Fix:** Standardize all `DataStore` creation through a single Hilt module.

---

### A-5: Excessive debug logging in credential save path — security concern
**File:** `data/SecureCredentialManager.kt:343`

Over 20 `logDebug` calls inside `savePassword`, including inside `withContext(NonCancellable)`. Excessive logging on a security-sensitive path is a risk even if content is not directly sensitive.

**Fix:** Reduce to at most 2–3 logs at entry and exit. Remove all intermediate step logs from the `NonCancellable` block.

---

### A-6: Two `createAndroidDeviceProfile` overloads with divergent implementations
**File:** `data/model/JellyfinDeviceProfile.kt:29,365`

One overload takes `DirectPlayCapabilities`, the other takes `maxWidth`/`maxHeight`. Different internal logic with the same name creates ambiguity about which to call.

**Fix:** Rename clearly: `createDeviceProfileFromCapabilities(...)` and `createDeviceProfileWithResolution(...)`.

---

## Full Issue Summary

| ID | Category | Severity | File |
|---|---|---|---|
| P-1 | Player | High | `PlayerScreen.kt` |
| P-2 | Player | High | `PlayerViewModel.kt` |
| P-3 | Player | High | `PlayerScreen.kt` |
| P-4 | Player | Medium | `PlayerScreen.kt` |
| P-5 | Player | **High** | `PlayerControls.kt` |
| P-6 | Player | Medium | `PlayerLifecycle.kt` |
| P-7 | Player | **High** | `PlayerTrackPanel.kt` |
| P-8 | Player | Low | `PlayerViewModel.kt` |
| P-9 | Player | Medium | `PlayerControls.kt` |
| P-10 | Player | Low | `PlayerTrackPanel.kt` |
| P-11 | Player | Medium | `PlayerLifecycle.kt` |
| P-12 | Player | **High** | `AudioPlayerScreen.kt` |
| P-13 | Player | Medium | `AudioPlayerScreen.kt` |
| P-14 | Player | **High** | `AudioPlayerScreen.kt` |
| P-15 | Player | Medium | `AudioPlayerScreen.kt` |
| P-16 | Player | **High** | `PlayerViewModel.kt`, `PlayerScreen.kt`, `JellyfinDeviceProfile.kt` |
| D-1 | Data | **High** | `JellyfinAuthRepository.kt` |
| D-2 | Data | **High** | `JellyfinMediaRepository.kt` |
| D-3 | Data | Medium | `HomeViewModel.kt` |
| D-4 | Data | **High** | `JellyfinMediaRepository.kt` |
| D-5 | Data | Low | `JellyfinMediaRepository.kt` |
| D-6 | Data | Medium | `DetailViewModel.kt` |
| D-7 | Data | **High** | `SecureCredentialManager.kt` |
| D-8 | Data | **Critical** | `SecureCredentialManager.kt` |
| D-9 | Data | Low | `OfflineProgressRepository.kt` |
| D-10 | Data | Medium | `JellyfinAuthRepository.kt` |
| D-11 | Data | **High** | `JellyfinAuthRepository.kt` |
| D-12 | Data | Low | `JellyfinDeviceProfile.kt` |
| D-13 | Data | **High** | `JellyfinDeviceProfile.kt` |
| D-14 | Data | Low | `HomeViewModel.kt` |
| U-1 | UI | **Critical** | `DetailShelves.kt` |
| U-2 | UI | Medium | `HomeScreen.kt` |
| U-3 | UI | Medium | `DetailScreen.kt` |
| U-4 | UI | Low | `DetailViewModel.kt` |
| U-5 | UI | Medium | `DetailViewModel.kt` |
| U-6 | UI | Low | `SearchScreen.kt` |
| U-7 | UI | Low | `SearchScreen.kt` |
| U-8 | UI | **High** | `TvMediaCard.kt` |
| U-9 | UI | **High** | `MusicScreen.kt` |
| U-10 | UI | Medium | `MusicScreen.kt` |
| U-11 | UI | Medium | `LibraryScreen.kt` |
| U-12 | UI | **High** | `LibraryViewModel.kt` |
| U-13 | UI | Low | `LibraryViewModel.kt` |
| U-14 | UI | Low | `SettingsScreen.kt` |
| U-15 | UI | Low | `CinefinTvApp.kt` |
| U-16 | UI | Low | `HomeScreen.kt`, `DetailScreen.kt` |
| N-1 | Navigation | **High** | `NavGraph.kt` |
| N-2 | Navigation | Low | `NavGraph.kt` |
| N-3 | Navigation | Low | `CinefinTvApp.kt` |
| N-4 | Navigation | Medium | `CinefinTvApp.kt` |
| N-5 | Navigation | Medium | `NavGraph.kt` |
| A-1 | Architecture | Medium | `PerformanceOptimizations.kt` |
| A-2 | Architecture | Medium | `PerformanceOptimizations.kt` |
| A-3 | Architecture | Low | `PlaybackPreferencesRepository.kt` |
| A-4 | Architecture | Low | `OfflineProgressRepository.kt` |
| A-5 | Architecture | Medium | `SecureCredentialManager.kt` |
| A-6 | Architecture | Low | `JellyfinDeviceProfile.kt` |
