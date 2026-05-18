# CinefinTV — Bug & Improvement Tracking System

**Author:** Comprehensive audit pass
**Date:** 2026-05-18
**Codebase snapshot:** v2.0.2 (versionCode 103) · 178 Kotlin files · ~37,134 LOC · 27 test files
**Build target:** compileSdk 37 / targetSdk 35 / minSdk 26 · Kotlin 2.3.21 · Compose BOM 2026.05.00

This document defines the bug/improvement tracking system for CinefinTV and populates it with a fresh audit of the current codebase. It replaces the now-resolved [2026-03-16 review](./2026-03-16-review-bugs-and-improvements.md) (which had ~60/61 items closed) and supersedes the open items in the [2026-05-06 status report](./2026-05-06-project-status-report.md).

---

## Part 1 — The System

### How issues are tracked

Every issue gets an ID like `P-17`, `D-15`, `BUILD-1`. The letter prefix is the category, the number is the position within that category and is permanent — IDs are never reused even after an issue closes.

When an issue is fixed, it stays in the document but flips to `✅ Fixed` and the body is replaced with a one-line summary of what changed and where. This preserves history and makes it easy to confirm a regression hasn't reopened a known issue.

### Categories

| Prefix | Category | Scope |
|---|---|---|
| `C-` | Crash / Critical | Anything that causes ANR, NPE crash, data loss, or security breach |
| `P-` | Player | ExoPlayer wiring, controls, subtitles, audio service, SyncPlay |
| `D-` | Data / Repository | Jellyfin API, caching, paging, session, sync |
| `U-` | UI / Compose | Screens, components, theme, accessibility, focus traps |
| `N-` | Navigation | NavGraph, routes, back stack, focus coordination |
| `A-` | Architecture | DI, layering, god-classes, abstractions, refactors |
| `S-` | Security | Keystore, credentials, TLS, certificate pinning, log redaction |
| `L-` | Logging / Observability | SecureLogger usage, log spam, metrics |
| `PERF-` | Performance | Jank, recomposition, memory, battery, network efficiency |
| `BUILD-` | Build / Dependencies | Gradle, AGP, KSP, library versions, manifests, release config |
| `T-` | Testing / QA | Unit/instrumented test gaps, fixture quality, CI |
| `DOC-` | Documentation | README, CLAUDE.md, CHANGELOG, in-code docs |

### Severity

| Level | Meaning | Response time |
|---|---|---|
| **Critical** | Crash, data loss, security regression, or shipping debug content to users | Hotfix — block release |
| **High** | Visible UX breakage on TV, sub-stable playback, or auth flow failure | Next minor release |
| **Medium** | Rough edges, suboptimal perf, missing handling of edge cases | Next regular release |
| **Low** | Cosmetic, cleanup, naming, doc drift, dead code | Background / opportunistic |

### Status workflow

```
  🆕 New ──────► 📋 Triaged ──────► 🔧 In Progress ──────► ✅ Fixed ──────► 🔍 Verified
                                                              │
                                                              ▼
                                       ⚠️ Partial ◄──────┘
                                            │
                                            └──► back to 🔧 In Progress
                                                  
                                       ⏸️ Deferred / ❌ Won't Fix are terminal
```

- **🆕 New** — Identified but not triaged. Body may be a stub; severity may be a guess.
- **📋 Triaged** — Acknowledged, severity confirmed, body complete, ready to pick up.
- **🔧 In Progress** — Someone is actively working. Set when a branch is opened.
- **✅ Fixed** — Code is in `main`. Body collapses to a one-line "Fixed in <file>: <change>".
- **🔍 Verified** — Manually or auto-tested on real hardware. Closes the loop.
- **⚠️ Partial** — Some mitigation merged but full fix needs more work. Keep open with a "remaining" note.
- **⏸️ Deferred** — Valid issue, but intentionally not now (e.g., waiting on upstream library).
- **❌ Won't Fix** — Decided against fixing, with a written rationale in the body.

### When to add a new issue

Add an entry whenever you'd otherwise leave a stale TODO comment in code or in a side conversation. The rule of thumb: if it would surprise a new contributor reading the file, it belongs in this doc. New issues start as **🆕 New** with severity in brackets; the next triage pass fills the body.

### File location & lifecycle

This file lives at `docs/2026-05-18-bug-improvement-system.md`. When the doc exceeds ~80 open items or annual milestones land, snapshot it under a dated filename (matching the existing `docs/YYYY-MM-DD-*.md` convention) and start a fresh active doc. Closed items move with the snapshot; open items carry forward.

---

## Part 2 — Current Snapshot

### What's healthy as of 2026-05-18

- **Architecture migration complete**: The 1,410-line `JellyfinRepository` god-class (B-2 in the March review) has been deleted. All ViewModels now go through `JellyfinRepositoryCoordinator` and the five specialized repos.
- **Repository execution standardized**: `BaseJellyfinRepository.withServerClient` / `executeWithRetry` / `executeWithCache` is now the universal pattern. Per-instance `ConcurrentHashMap<String, CircuitBreakerState>` resolved the M2 scoping concern.
- **Focus coordination**: `FocusNavigationCoordinator` is wired into 12 screens (Home, all detail screens, Library, Music, Search, Settings, Person). The `withFrameNanos` hacks from the March review are gone.
- **Test coverage**: 27 test files cover 12 of 17 ViewModels plus core data and navigation paths. Up from 15 in March.
- **Cache user-state mitigation**: `CachePolicyInterceptor` now excludes `/UserItems`, `/UserData`, and `/Users/*/Items` from the 60-second blanket cache (M3 partially mitigated).
- **Player decomposition**: Phase 2 work split `PlayerPlaybackContent` into isolated sub-composables; position reads no longer trigger full-screen recomposition.

### Headline scoring

| Metric | Value | Trend vs March |
|---|---|---|
| Kotlin source files | 178 | ↑ from 154 |
| Test files | 27 | ↑ from 15 |
| Open Critical issues | 0 | ↔ |
| Open High issues | 5 | new audit |
| Open Medium issues | 11 | new audit |
| Open Low issues | 9 | new audit |
| TODO/FIXME comments | 0 | clean |
| Largest source file | PlayerControls.kt (1,323 LOC) | god-composable risk |
| Alpha/beta deps | 9 | release risk |

---

## Part 3 — Open Findings (this audit)

The findings below are all **🆕 New** unless marked otherwise. Numbering continues from the March review where applicable (e.g. new player findings start at `P-17`).

### Critical

_None identified in this pass._

---

### High severity

#### `U-17`: `SeekBarControl` applies `.focusable()` twice on the same modifier chain
**File:** `ui/player/PlayerControls.kt:1145, 1182`
**Severity:** High · **Status:** 🆕 New

The `SeekBarControl` composable adds `.focusable()` at two points in the same modifier chain — once before `onPreviewKeyEvent` (line 1145) and again as the final modifier on the outer `Box` (line 1182). Compose treats each `.focusable()` as a focus target, so this either creates two adjacent focus targets in the focus graph or one shadows the other depending on order. Symptoms could include: double D-pad press required to escape the seek bar, focus appearing to "stick" on the seek bar, or `up`/`down` focus properties applied to the wrong target.

**Fix:** Remove the duplicate. The intent appears to be the inner `.focusable()` (so `.focusProperties` and `.onFocusChanged` see focus events); the outer one is leftover. Verify with a focus traversal test that `up = up` and `down = down` still route correctly.

#### `BUILD-1`: Missing `POST_NOTIFICATIONS` permission for Android 13+
**File:** `app/src/main/AndroidManifest.xml`
**Severity:** High · **Status:** 🆕 New

The audio playback path uses `AudioService` (a `MediaSessionService` foreground service with `foregroundServiceType="mediaPlayback"`), which posts a media-style notification for transport controls. On Android 13+ (API 33+), apps must hold the runtime `POST_NOTIFICATIONS` permission to display *any* notification, including media. The manifest declares `INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `REQUEST_INSTALL_PACKAGES`, but not `POST_NOTIFICATIONS`. Given `targetSdk = 35`, this affects all users on Android 13+ — they will not see playback controls in the notification shade and the audio service may be limited by the system.

**Fix:** Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` to the manifest and request it at runtime before starting `AudioService` (or before the first audio playback). Android TV launchers typically auto-grant this since there's no notification UI on Leanback, but the permission must still be declared for the foreground service to publish notifications without restriction.

#### `BUILD-2`: Multiple production-critical dependencies on alpha/beta versions
**File:** `gradle/libs.versions.toml`
**Severity:** High · **Status:** 📋 Triaged (carried from May status report H2)

Nine dependencies are on alpha/beta tracks, several in the core stack:

| Dependency | Current version | Risk |
|---|---|---|
| `androidx.navigation` | `2.10.0-alpha04` | Critical path — entire app nav |
| `androidx.navigation3` | `1.2.0-alpha02` (entire library is alpha) | Used in app — runtime + ui + lifecycle integration |
| `androidx.datastore` | `1.3.0-alpha09` | Credential persistence depends on this |
| `androidx.lifecycle-runtime-compose` | `2.11.0-beta01` | Used in every screen |
| `androidx.lifecycle-viewmodel-compose` | `2.11.0-beta01` | Every screen |
| `androidx.hilt:hilt-navigation-compose` | `1.4.0-beta01` | Every ViewModel injection |
| `androidx.core:core-ktx` | `1.19.0-alpha02` | Pervasive |
| `io.coil-kt:coil` | `3.5.0-beta01` | All image loading |
| `androidx.benchmark` | `1.5.0-alpha06` | Benchmarks only — lower risk |

Alphas may ship binary-incompatible changes between dot releases, and crash reports against them are typically not actionable upstream. Particularly concerning: navigation3 is *entirely* an alpha library still, used alongside `navigation-compose`.

**Fix:** Audit each one. For `navigation`, `datastore`, `lifecycle-*`, `hilt-navigation-compose`, `coil`, `core-ktx`: pin to the latest stable until release; the alpha features in use should be enumerable. For `navigation3`: confirm we actually need it — `androidx.navigation` covers most of what's currently here. If kept, mark the dependency as experimental in CLAUDE.md so contributors understand the stability profile.

#### `D-15`: `isTokenExpired()` doesn't actually check expiry
**File:** `data/repository/JellyfinAuthRepository.kt:220-222`
**Severity:** High · **Status:** 🆕 New

```kotlin
fun isTokenExpired(): Boolean {
    return _currentServer.value?.accessToken.isNullOrBlank()
}
```

The method name strongly implies a time-based expiry check. It actually returns "token is missing." The Kdoc above `shouldRefreshToken()` admits this explicitly: *"We do not currently have server-issued expiry metadata for Jellyfin access tokens. Until that exists, only treat missing tokens as refreshable via the proactive path."* The misnomer matters because `BaseJellyfinRepository.validateTokenAndRefreshIfNeeded()` (line 70) calls `isTokenExpired()` and triggers a `forceReAuthenticate` if true. With a missing token, that path runs. With a *stale-but-present* token (the actual common failure case — server revoked, password changed elsewhere, etc.), this returns false and we proceed to the API call only to handle the 401 reactively.

**Fix:** Rename to `isTokenMissing()` to match behavior, and let the reactive 401 path remain the source of truth for revoked tokens. Or, if proactive refresh is the goal, persist `loginTimestamp` already on `JellyfinServer` and compute `currentTimeMillis() - loginTimestamp > Constants.TOKEN_VALIDITY_DURATION_MS` (45 min default) — but recognize Jellyfin's server-side TTL is the real authority and this is heuristic.

#### `L-2`: Heavy unguarded `android.util.Log` calls in HomeViewModel hot paths
**File:** `ui/screens/home/HomeViewModel.kt` (lines 101, 114, 116, 124, 130, 135, 139, 151, 159, 169, 177, 276, 309, 317, 322, 324, 359, 369, 373, 376, plus more)
**Severity:** High · **Status:** 🆕 New

`HomeViewModel` makes ~25 `android.util.Log.d/w/e` calls on the init path and inside `currentServer` / `isSessionRestored` collectors and the `refresh` flow. These fire on every `StateFlow` emission, every refresh, and every cache load — typically multiple times per second during a cold start. None are guarded by `BuildConfig.DEBUG` and none go through `SecureLogger`. On release builds this generates significant logcat noise, occasional measurable I/O contention on slow TV hardware, and increases the chance of leaking debug-only context (server URLs, user IDs via `debugSummary()`) into device logs that are visible to other apps with `READ_LOGS` (rare on TV but possible).

The same pattern appears at lower density in `PlayerViewModel` (`P-17` below) and `NetworkStateInterceptor` (`L-3` below).

**Fix:** Migrate to `SecureLogger`. For tracing-style debugSummary logs in the hot path, wrap in `if (BuildConfig.DEBUG)` or move behind a feature flag, since even routing through SecureLogger they're noise in production. The CLAUDE.md guidance ("Use SecureLogger ... instead of Log.*") is explicit — this is an existing convention being violated.

---

### Medium severity

#### `P-17`: PlayerViewModel uses raw `android.util.Log` for analytics and retry events
**File:** `ui/player/PlayerViewModel.kt:494, 499, 574, 804, 838, 843, 915, +more`
**Severity:** Medium · **Status:** 🆕 New

7+ direct `Log.e/w` calls in PlayerViewModel including the playback error analytics path, dropped-frame reporter, retry logic, and track selection fallback. Same root cause as `L-2`: inconsistency with the SecureLogger convention. Lower severity because frequency is event-driven rather than per-emission.

**Fix:** Replace with `SecureLogger.e(TAG, ...)` / `SecureLogger.w(TAG, ...)`. Verify the analytics format is still readable in production logcat.

#### `L-3`: `NetworkStateInterceptor` logs every network request without DEBUG guard
**File:** `network/NetworkStateInterceptor.kt:35`
**Severity:** Medium · **Status:** 🆕 New

```kotlin
Log.d(TAG, "Request to ${request.url.host} via $networkType")
```

This fires for *every* HTTP request through the OkHttp chain. A typical Home screen cold start fans out 5–10 requests in parallel, plus image requests handled by Coil's pipeline. On a 60-second auto-refresh cycle that's ~600 log lines/minute just from this one line. Not a correctness issue, but a measurable noise floor.

**Fix:** Wrap in `if (BuildConfig.DEBUG)` or remove. The `Log.w` for the offline case (line 30) and the `Log.e` for SocketException (line 52) are legitimate event-level logs and can stay.

#### `L-1`: Inconsistent use of `android.util.Log` vs `SecureLogger` across codebase
**File:** 29 files use raw `Log.*`; 25 use `SecureLogger`/`Logger`
**Severity:** Medium · **Status:** 🆕 New

The codebase has near-even split: 29 files import `android.util.Log` directly, 25 use the redaction-safe `SecureLogger`. The CLAUDE.md convention is clear about which should be used. While `L-2`/`L-3`/`P-17` cover the highest-volume offenders, the broader inconsistency makes the convention de-facto optional and increases the chance that a future log line accidentally includes a credential.

**Fix:** Sweep — replace `import android.util.Log` with the SecureLogger equivalent everywhere except the few files that legitimately need raw access (e.g., `SecureLogger.kt` itself, `MainActivity` boot logging before DI is ready). Add a CI lint rule that fails the build on new `import android.util.Log` outside an allowlist.

#### `A-7`: `DispatcherProvider` exists but only `HomeViewModel` uses it
**File:** `data/common/DispatcherProvider.kt` (interface), 64 direct `Dispatchers.IO/Default/Main` call sites
**Severity:** Medium · **Status:** 🆕 New

The DI module defines `DispatcherProvider` precisely so tests can substitute a deterministic dispatcher (and the test utility `DeterministicDispatcherProvider` exists in `testutil/`). Yet only `HomeViewModel` injects it. Everywhere else — including `BaseJellyfinRepository`, all the specialized repos, `JellyfinCache`, `SecureCredentialManager`, the player, and most other ViewModels — uses `withContext(Dispatchers.IO)` directly. This makes those classes harder to test reliably: the test must rely on `Dispatchers.setMain` (already in `MainDispatcherRule`) for the Main case, but has no override for IO-bound work, leading to occasional races in flaky tests.

**Fix:** This is a sweep but it can be done incrementally. Prioritize the repos and player (where IO is heavy) and leave low-frequency callers for later. Pattern:
```kotlin
class X(private val dispatchers: DispatcherProvider) {
    suspend fun foo() = withContext(dispatchers.io) { ... }
}
```

#### `A-8`: "Stuff" type/class/route names persist after UI rename to "Collections"
**Files:** `ui/screens/library/LibraryViewModel.kt:107` (`StuffLibraryViewModel`), `ui/screens/detail/StuffDetailViewModel.kt`, `ui/screens/detail/StuffDetailScreen.kt`, `ui/screens/library/StuffLibraryScreen.kt`, `ui/navigation/NavDestinations.kt:37,65` (`LibraryStuff`, `StuffDetail`), `ui/navigation/NavGraph.kt:41,45,217–220,294–295`
**Severity:** Medium · **Status:** ⚠️ Partial (rename from `B-8` in March review only covered UI labels)

The March-review `B-8` was marked Fixed — but only the user-visible label was changed from "Stuff" to "Collections" in `NavTabItems`. The internal types, routes, screen classes, and ViewModels are all still named `Stuff*`. So `StuffLibraryViewModel.markWatched()` is the function that runs when a user clicks "Mark Watched" on a card in the "Collections" tab. New contributors will hunt for a `CollectionsViewModel` that doesn't exist.

**Fix:** Rename `StuffLibraryViewModel` → `CollectionsLibraryViewModel`, `StuffLibraryScreen` → `CollectionsLibraryScreen`, `StuffDetailViewModel` → `CollectionsDetailViewModel`, `StuffDetailScreen` → `CollectionsDetailScreen`, `LibraryStuff` → `LibraryCollections`, `StuffDetail` → `CollectionsDetail`. Use Android Studio's rename refactor so all import sites, tests, and string keys flip together. Note: there is a `StuffDetailViewModelTest` — it gets renamed too.

#### `A-9`: `LibraryViewModel.refreshSignal` has no debouncing
**File:** `ui/screens/library/LibraryViewModel.kt:69–75`
**Severity:** Medium · **Status:** 🆕 New

```kotlin
init {
    viewModelScope.launch {
        updateBus.events.collect {
            refreshSignal.value += 1
        }
    }
}
```

Every `MediaUpdateBus.events` emission increments `refreshSignal`, which is the input to `flatMapLatest` that builds a new `Pager`. If a user marks 20 episodes watched in quick succession (a real flow — "mark season watched" iterates), this triggers 20 paging source restarts, each fetching the first page from scratch. The `cachedIn(viewModelScope)` operator helps after the first emission settles, but during a burst it doesn't.

**Fix:** Add `.debounce(200L)` or `.sample(200L)` to the `updateBus.events` collection before incrementing. Pick the value so server-load amortizes — 200ms catches most bursts without delaying the visible refresh past the user's perception threshold.

#### `A-10`: `LibraryViewModel` ignores `RefreshItem(itemId)` granularity
**File:** `ui/screens/library/LibraryViewModel.kt:71` (same `init` block)
**Severity:** Medium · **Status:** 🆕 New

Related to `A-9`. The `MediaUpdateBus` is designed to carry both bulk events (`RefreshAll`) and targeted events (`RefreshItem(itemId)`). The library implementation collapses both into a single `refreshSignal.value += 1` — restarting the entire paged source whether 1 item changed or all of them. For a Paging 3 source, fully invalidating to update a single item's watch indicator is wasteful, and on a 10,000-item library means scrolling back to the top and re-fetching.

**Fix:** Distinguish event types in the collector. For `RefreshAll`, restart paging. For `RefreshItem(itemId)`, expose a separate `updatedItems: StateFlow<Map<String, ItemUpdate>>` that the screen merges into the rendered card model without invalidating the paging source. This is a meatier refactor — defer until A-9 lands and measures show it's still a problem.

#### `D-16`: `ensureSessionReady()` returns `true` for 5xx server errors
**File:** `data/repository/JellyfinAuthRepository.kt:422-438`
**Severity:** Medium · **Status:** 🆕 New

```kotlin
} catch (e: InvalidStatusException) {
    if (isTokenRejectedStatus(e.status)) {
        ...forceReAuthenticate()
    } else {
        Log.w(TAG, "ensureSessionReady: non-auth status ${e.status}, proceeding")
        true  // ← lies
    }
}
```

The function is named `ensureSessionReady` and contract-by-name promises the session is usable when it returns true. Returning `true` for 500/502/503 means the caller proceeds to fan out requests against a broken server, each of which will fail individually. The intent seems to be "let the caller handle the error path on the real request" but a caller reading this code reasonably assumes `true` means *go*.

**Fix:** Either rename to `isAuthTokenAccepted` (which is what it actually checks), or change the 5xx branch to return false. Probably the former is less disruptive — the contract becomes "did the token get rejected" rather than "is everything fine."

#### `PERF-1`: HomeViewModel `debugSummary()` logging fires on every StateFlow emission
**File:** `ui/screens/home/HomeViewModel.kt` (multiple collectors)
**Severity:** Medium · **Status:** 🆕 New

Cross-cuts `L-2`. The `debugSummary()` extension is called inline in log strings on every emission of `currentServer`, `isSessionRestored`, every refresh start, every snapshot publish, and every cache load. Even if those calls are cheap individually, they execute on the Main dispatcher in `viewModelScope`. The frequency under a cold start (auth restore + cache load + network refresh racing) is the bigger concern than the per-call cost.

**Fix:** Same mitigation as `L-2` — guard with `BuildConfig.DEBUG`. The cheapest path is a `private inline fun trace(message: () -> String)` helper that wraps the conditional, so the lambda doesn't allocate in release builds.

#### `U-19`: 24+ hardcoded hex colors in detail/cinematic composables
**Files:** `ui/screens/detail/cinematic/PersonCircleCard.kt` (lines 73, 94, 102, 112), `ui/screens/detail/cinematic/FlatDetailHero.kt` (lines 116, 200, 210, 246, 253, 263, +more), plus others
**Severity:** Medium · **Status:** 🆕 New

24 instances of `Color(0xFF...)` in `ui/screens/` outside the `theme/` package. These bypass `CinefinExpressiveColors` and break dynamic theming / Material You / contrast levels — settings the user can change in Settings will visibly miss these areas.

This is the spiritual sibling of the March-review `B-5` (player hardcoded colors), but in the detail screens.

**Fix:** Define semantic tokens for the recurring greys in `CinefinExpressiveColors.kt` (`personPlaceholderBackground`, `personFallbackIcon`, `metadataLabel`, `metadataValue`, `metadataDivider`) and replace the hex literals. Verify against all three contrast levels and the seed-color picker.

#### `BUILD-3`: Kotlin and KSP versions mismatched
**File:** `gradle/libs.versions.toml:3-4`
**Severity:** Medium · **Status:** 🆕 New

```toml
kotlin = "2.3.21"
ksp = "2.3.8"
```

KSP version strings normally track the Kotlin version in their first component (e.g. Kotlin `2.3.21` → KSP `2.3.21-1.x.y`). `2.3.8` doesn't match. Either KSP is for an older Kotlin (will likely warn or fail to load Hilt symbols) or this is a typo for `2.3.21-1.0.x` style. The May status report flagged this as H3.

**Fix:** Confirm what KSP version pairs with Kotlin 2.3.21 and bump. If `2.3.21-2.x.y` is the right pairing, update the string.

---

### Low severity

#### `U-18`: Hardcoded `NetflixRed` duplicates the `CinefinRed` theme token
**File:** `ui/screens/home/HomeScreen.kt:105`
**Severity:** Low · **Status:** 🆕 New

```kotlin
private val NetflixRed = Color(0xFFE50914)
```

`ui/theme/Color.kt` already defines `CinefinRed = #E50914` (same color). The duplicate file-private constant suggests someone copy-pasted the hex rather than importing the theme token, plus the name is awkward in a non-Netflix product.

**Fix:** Replace usages with `MaterialTheme.colorScheme.primary` or `LocalCinefinExpressiveColors.current.<appropriate token>`, then delete the constant. Quick win.

#### `U-20`: Boxed-primitive `mutableStateOf(0L)` for timestamp
**File:** `ui/screens/home/HomeScreen.kt:120`
**Severity:** Low · **Status:** 🆕 New

```kotlin
var lastPausedAtMs by remember { mutableStateOf(0L) }
```

Compose provides `mutableLongStateOf` specifically for `Long` state — it avoids autoboxing on every write. With one write per pause/resume cycle this isn't a perf crisis, but the rest of the file uses `mutableIntStateOf` and `mutableLongStateOf` elsewhere, so this stands out as an oversight.

**Fix:** `var lastPausedAtMs by remember { mutableLongStateOf(0L) }`. One-line change.

#### `A-11`: `LibraryViewModel` PagingConfig uses magic numbers
**File:** `ui/screens/library/LibraryViewModel.kt:51-54`
**Severity:** Low · **Status:** 🆕 New

```kotlin
config = PagingConfig(
    pageSize = 30,
    enablePlaceholders = false,
    initialLoadSize = 60
),
```

CLAUDE.md says: *"`core/constants/Constants.kt` is the single source of truth for all magic numbers."* `30` matches `Constants.SEARCH_PAGE_SIZE`, which is semantically wrong (this isn't search). `60` (initial load) isn't named anywhere.

**Fix:** Add `LIBRARY_PAGE_SIZE = 30` and `LIBRARY_INITIAL_LOAD_SIZE = 60` to the `Pagination` group in `Constants.kt`, then reference. If a different page size is desired, this is also the natural place to tune it.

#### `M-1`: `JellyfinCache.memoryCache` uses LinkedHashMap with synchronized blocks
**File:** `data/cache/JellyfinCache.kt:53-61`
**Severity:** Low · **Status:** ⚠️ Partial (carried from May status report M1)

The May report flagged this for `ConcurrentHashMap`. Current code uses `LinkedHashMap` with access-order=true (for LRU eviction via `removeEldestEntry`) and `synchronized(memoryCache) { ... }` blocks around every access — functionally correct but `LinkedHashMap` with `accessOrder=true` mutates internal state on `get()`, which is why the synchronization is needed even on reads.

Switching to `ConcurrentHashMap` directly *loses* the LRU eviction. A drop-in `ConcurrentLinkedHashMap` (from Guava/Caffeine) or a custom solution is needed for a proper concurrent LRU.

**Fix:** Either (a) accept the synchronized-LinkedHashMap as the pragmatic answer and close this issue with a comment, or (b) pull in Caffeine, which has a `Caffeine.newBuilder().maximumSize(50).build()` API that gives concurrent + LRU with no synchronization on the hot path. Caffeine is ~600KB; reasonable trade for hot-cache performance on slow TV hardware.

#### `L-4` (was `L1`): `UpdateManager.installApk` uses deprecated `ACTION_INSTALL_PACKAGE`
**File:** `update/UpdateManager.kt:175-191`
**Severity:** Low · **Status:** 📋 Triaged (carried from May status report L1)

```kotlin
@Suppress("DEPRECATION")
val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply { ... }
```

`Intent.ACTION_INSTALL_PACKAGE` was deprecated in API 25. `PackageInstaller` is the supported API for sideloading on API 29+. The `@Suppress("DEPRECATION")` is acknowledging this. It still works on API 35, but each major Android release adds friction (Play Protect warnings, install-source prompts, etc.) — and may eventually be removed.

**Fix:** Migrate to `PackageInstaller.Session`: create a session, copy the APK bytes into it, commit, handle the result through a `BroadcastReceiver`. Notably more code, but it's the only forward-compatible path for self-update. Keep the existing path as a fallback for older devices.

#### `BUILD-4`: `compileSdk = 37` (preview) with stable `targetSdk = 35`
**File:** `app/build.gradle.kts:18, 47`
**Severity:** Low · **Status:** 🆕 New

`compileSdk = 37` is the Android 16 / Baklava preview SDK. Building against a preview SDK means each Android Studio update can subtly shift behavior (lint rules, deprecation warnings, available APIs in code completion). For a beta-track release that hits user devices, the conservative answer is `compileSdk = 35` matching `targetSdk = 35` until 36/37 are stable.

There's no immediate breakage — release builds work — but a contributor on a different Android Studio version may see different lint output.

**Fix:** Either drop `compileSdk` to 35 (most conservative), or bump `targetSdk` to 36 once API 36 is stable (next year). If staying on 37 is intentional to access a specific new API, leave a comment in `build.gradle.kts` documenting why.

#### `DOC-1`: CHANGELOG.md is 2 months stale
**File:** `CHANGELOG.md`
**Severity:** Low · **Status:** 🆕 New

Last entry: `[0.2.0-beta] - 2026-03-14`. Actual versionName: `2.0.2` (versionCode `103`). 11 releases unaccounted for, including the entire Phase 2 (Performance) and Phase 3 (Architecture) milestone work documented in the May status report.

**Fix:** Backfill from `git log` between v0.2.0 and v2.0.2, grouping by version. Going forward, treat the CHANGELOG as a release-checklist item — `publish.sh` already bumps versions, and a `prepare-changelog` step there would close this. The `RELEASE_CHECKLIST.md` mentions the changelog but doesn't enforce it.

#### `DOC-2`: `CinefinTV_status_upgrade_path.md` cites stale counts
**File:** `docs/CinefinTV_status_upgrade_path.md:21, 38-39`
**Severity:** Low · **Status:** 🆕 New

The status/upgrade doc dates from 2026-03-17 and cites: `versionCode 26`, `1.2.5`, `154 Kotlin source files`, `15 JVM test files`. Current state: versionCode 103, 2.0.2, 178 files, 27 tests. The architectural commentary about `JellyfinRepository` co-existing with the coordinator is also outdated — the god-class is gone.

**Fix:** Either refresh in place (preferred since it's a flagship doc) or supersede with a pointer to the May status report. If superseding, leave a header note: *"This doc reflects the 2026-03 state. See `2026-05-06-project-status-report.md` for current."*

#### `T-1`: 5 ViewModels lack unit tests
**Files:** `ui/screens/library/LibraryViewModel.kt` (covers `MovieLibraryViewModel`, `TvShowLibraryViewModel`, `StuffLibraryViewModel`), `ui/screens/profile/ProfilePickerViewModel.kt`, `ui/navigation/ServerDiscoveryViewModel.kt`, `ui/theme/ThemeViewModel.kt`, `ui/screens/search/VoiceSearchNavViewModel.kt`
**Severity:** Low · **Status:** 🆕 New

Tests exist for 12 of 17 ViewModels. The five without coverage are smaller and lower-traffic, but the library family is non-trivial — `BaseLibraryViewModel` wires PagingSource + MediaUpdateBus and is the natural place to verify `A-9`/`A-10` regressions don't happen.

**Priority order:**
1. `LibraryViewModel` (cover `Base` + each concrete subclass) — exercises paging + update-bus integration; gates the A-9/A-10 fixes.
2. `ThemeViewModel` — preference reads/writes, accent override behavior.
3. `ServerDiscoveryViewModel` — broadcast discovery, manual entry, validation.
4. `ProfilePickerViewModel` — profile listing + switch flow.
5. `VoiceSearchNavViewModel` — voice intent handling, lowest priority.

---

## Part 4 — Carried Over (status updates from prior reviews)

| ID | From | Original issue | Current status |
|---|---|---|---|
| `P-16` | March 2026-03-16 | Video playback smoothness | ⚠️ Partial. Transcoding/polling/profile fixes shipped. Still depends on device decoder; profile on hardware. |
| `B-2` | March 2026-03-16 | `JellyfinRepository` god-class | ✅ Fixed. File deleted. All ViewModels use Coordinator. |
| `M1` | May 2026-05-06 | `LinkedHashMap` thread safety | Reissued as `M-1` above (low severity, may close). |
| `M2` | May 2026-05-06 | Circuit Breaker scope | ✅ Fixed. `private val circuitBreakerStates = ConcurrentHashMap<...>()` is per-instance in `BaseJellyfinRepository:46`. |
| `M3` | May 2026-05-06 | User-state cache exclusions | ✅ Fixed (or close to). `CachePolicyInterceptor` excludes `/UserItems`, `/UserData`, `/Users/*/Items`. Spot-check `/Resume*` endpoints to confirm. |
| `L1` | May 2026-05-06 | UpdateManager deprecated APIs | Reissued as `L-4` above. |
| `H1` | May 2026-05-06 | First-focus contracts | Mostly delivered via `FocusNavigationCoordinator` rollout. Verify per the `docs/focus-contract.md` checklist on hardware. |
| `H2` | May 2026-05-06 | Alpha/beta dependencies | Reissued as `BUILD-2` above (escalated to High). |
| `H3` | May 2026-05-06 | KSP version standardization | Reissued as `BUILD-3` above. |

---

## Part 5 — Test Coverage Matrix

| Area | Has tests? | Notes |
|---|---|---|
| `JellyfinAuthRepository` | ✅ | `JellyfinAuthRepositoryTest` |
| `JellyfinMediaRepository` | ✅ | `JellyfinMediaRepositoryTest` |
| `LibraryLoadingManager` | ✅ | `LibraryLoadingManagerTest` |
| `LibraryItemPagingSource` | ✅ | `LibraryItemPagingSourceTest` |
| `JellyfinStreamRepository` | ❌ | No tests. Stream URL building + transcode decisions are non-trivial. |
| `JellyfinUserRepository` | ❌ | No tests. Watched/unwatched + favorites + delete. |
| `JellyfinSearchRepository` | ❌ | No tests. |
| `JellyfinSystemRepository` | ❌ | No tests. |
| `SecureCredentialManager` | ❌ | No tests. Keystore-backed; needs Robolectric or instrumented test. |
| `EnhancedPlaybackManager` | ❌ | No tests. ~928 LOC of network/device-aware logic. |
| `JellyfinCache` | ❌ | No tests. |
| `HomeViewModel` | ✅ | `HomeViewModelTest` |
| `DetailViewModel` family | ✅ | `MovieDetailViewModelTest`, `TvShowDetailViewModelTest`, `SeasonViewModelTest`, `StuffDetailViewModelTest` |
| `PersonViewModel` | ✅ | `PersonViewModelTest` |
| `SearchViewModel` | ✅ | `SearchViewModelTest` |
| `SettingsViewModel` | ✅ | `SettingsViewModelTest` |
| `MusicViewModel` | ✅ | `MusicViewModelTest` |
| `AuthViewModel` | ✅ | `AuthViewModelTest` |
| `PlayerViewModel` | ✅ | `PlayerViewModelTest` |
| `AudioPlayerViewModel` | ✅ | `AudioPlayerViewModelTest` |
| `LibraryViewModel` (all 3) | ❌ | Tracked as `T-1` |
| `ProfilePickerViewModel` | ❌ | Tracked as `T-1` |
| `ServerDiscoveryViewModel` | ❌ | Tracked as `T-1` |
| `ThemeViewModel` | ❌ | Tracked as `T-1` |
| `VoiceSearchNavViewModel` | ❌ | Tracked as `T-1` |
| `FocusNavigationCoordinator` | ✅ | `FocusNavigationCoordinatorTest` |
| `NavRouteResolvers` | ✅ | `NavRouteResolversTest` |
| `NavChrome` | ✅ | `NavChromeTest` |
| `UpdateManager` | ✅ | `UpdateManagerTest` |
| `PlayerLifecycle` / `PlayerUtils` | ✅ | Both tested |

**Repository coverage gap is the most material item** — 6 of 8 repositories lack unit tests, including the ~688-line `JellyfinStreamRepository`. Recommend pulling this out as its own follow-up alongside `T-1`.

---

## Part 6 — Quick-Reference Summary Table

| ID | Title | Severity | Status |
|---|---|---|---|
| `U-17` | Duplicate `.focusable()` in SeekBarControl | High | 🆕 New |
| `BUILD-1` | Missing POST_NOTIFICATIONS permission | High | 🆕 New |
| `BUILD-2` | Alpha/beta dependencies in core stack | High | 📋 Triaged |
| `D-15` | `isTokenExpired()` misnamed | High | 🆕 New |
| `L-2` | HomeViewModel log spam in production | High | 🆕 New |
| `P-17` | PlayerViewModel uses raw Log.* | Medium | 🆕 New |
| `L-3` | NetworkStateInterceptor per-request log spam | Medium | 🆕 New |
| `L-1` | Inconsistent Log vs SecureLogger codebase-wide | Medium | 🆕 New |
| `A-7` | DispatcherProvider underused | Medium | 🆕 New |
| `A-8` | "Stuff" naming persists in types/routes | Medium | ⚠️ Partial |
| `A-9` | LibraryViewModel refreshSignal not debounced | Medium | 🆕 New |
| `A-10` | LibraryViewModel ignores RefreshItem granularity | Medium | 🆕 New |
| `D-16` | `ensureSessionReady()` returns true for 5xx | Medium | 🆕 New |
| `PERF-1` | HomeViewModel debugSummary on every emission | Medium | 🆕 New |
| `U-19` | Hardcoded hex colors in detail/cinematic | Medium | 🆕 New |
| `BUILD-3` | Kotlin/KSP version mismatch | Medium | 🆕 New |
| `U-18` | `NetflixRed` duplicates `CinefinRed` token | Low | 🆕 New |
| `U-20` | Boxed `mutableStateOf(0L)` should be `mutableLongStateOf` | Low | 🆕 New |
| `A-11` | LibraryViewModel paging magic numbers | Low | 🆕 New |
| `M-1` | JellyfinCache LinkedHashMap synchronized | Low | ⚠️ Partial |
| `L-4` | UpdateManager deprecated ACTION_INSTALL_PACKAGE | Low | 📋 Triaged |
| `BUILD-4` | compileSdk preview while targetSdk is stable | Low | 🆕 New |
| `DOC-1` | CHANGELOG 2 months stale | Low | 🆕 New |
| `DOC-2` | Status/upgrade doc cites stale counts | Low | 🆕 New |
| `T-1` | 5 ViewModels lack unit tests | Low | 🆕 New |
| `P-16` | Video playback smoothness (carry-over) | High | ⚠️ Partial |

**Totals (new this audit):** 0 Critical · 5 High · 11 Medium · 9 Low

---

## Part 7 — Recommended sequencing

Based on the severity matrix and dependency between items, a reasonable order:

**Sprint 1 — release blockers and quick wins**
1. `BUILD-1` POST_NOTIFICATIONS (one-line manifest + runtime request)
2. `U-17` Double `.focusable()` (one-line fix in PlayerControls)
3. `D-15` Rename `isTokenExpired` → `isTokenMissing`
4. `U-18` Replace `NetflixRed` constant
5. `U-20` `mutableLongStateOf` swap
6. `BUILD-3` KSP version alignment

**Sprint 2 — logging hygiene as a single sweep**
1. `L-2`, `L-3`, `P-17`, `PERF-1`, `L-1` — single PR, one file at a time, with a CI lint check added at the end.

**Sprint 3 — library refresh path correctness**
1. `T-1` — add `LibraryViewModel` tests first (gates the next two fixes).
2. `A-9` — debounce `refreshSignal`.
3. `A-10` — handle `RefreshItem` granularity.
4. `A-11` — move PagingConfig magic numbers to Constants.

**Sprint 4 — naming and dependencies**
1. `A-8` — rename `Stuff*` → `Collections*` codebase-wide (one big PR).
2. `BUILD-2` — alpha/beta dependency audit and pin-down.
3. `D-16` — `ensureSessionReady` semantic clarification.

**Sprint 5 — documentation and theming**
1. `DOC-1`, `DOC-2` — backfill changelog, refresh status docs.
2. `U-19` — replace hardcoded hex colors in detail/cinematic with semantic tokens.

**Backlog**
- `A-7` DispatcherProvider sweep (incremental, prioritize repos)
- `M-1` decide on Caffeine vs. accepting current state
- `L-4` PackageInstaller migration
- `BUILD-4` SDK version decision
- `P-16` continue hardware profiling

---

*To add a new finding: pick the next number in the appropriate category (e.g., next architecture issue is `A-12`), mark it `🆕 New`, give it severity in brackets, write a one-paragraph body. The next triage pass fills in file paths and the proposed fix.*
