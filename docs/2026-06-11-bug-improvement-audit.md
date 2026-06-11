# CinefinTV — Bug & Improvement Audit (Updated Task List)

**Date:** 2026-06-11
**Codebase snapshot:** v2.1.6 (versionCode 117) · 229 Kotlin files · ~47,300 LOC · 48 test files
**Build target:** compileSdk 37 · targetSdk 35 · minSdk 26 · AGP 9.2.1 · Kotlin 2.4.0
**Prior baseline:** v2.0.2 audit (`docs/2026-05-18-bug-improvement-system.md`)

---

## What changed since the last audit (v2.0.2 → v2.1.6)

The codebase grew from 178 → 229 files and ~37K → ~47K LOC, and the test suite nearly doubled (27 → 48 files). Three items from the May audit are now resolved:

| ID | Item | Old status | Now |
|---|---|---|---|
| `T-1` | ViewModels lacked unit tests | 🆕 New (5 missing) | ✅ **Resolved** — all 17 ViewModels have test files |
| `M-1` | `JellyfinCache` used `LinkedHashMap` + `synchronized` | ⚠️ Partial | ✅ **Resolved** — now `ConcurrentHashMap` |
| `U-19` | 24+ hardcoded hex colors in UI | 🆕 New | ✅ **Mostly resolved** — only 2 strays remain (see `COLOR-1`) |

Two notes on things that look like problems but aren't: the `runBlocking` calls in `JellyfinAuthInterceptor` / `PinningTrustManager` are required by OkHttp's synchronous API and are documented; the empty `catch (_: Exception) {}` in `ServerDiscoveryRepository:85` wraps NSD discovery teardown, which legitimately throws if already stopped. Leave both alone.

---

## Open task list

Totals: **0 Critical · 2 High · 3 Medium · 4 Low · 1 Debt**

### High

**`BUILD-2` — Production-critical dependencies still on alpha** *(carried, partial progress)*
`gradle/libs.versions.toml`. Coil is now stable (3.5.0) and lifecycle / hilt-navigation moved to `rc01`, but three load-bearing libraries are still on alpha in the release branch:
- `navigation = 2.10.0-alpha05`
- `navigation3 = 1.2.0-alpha04`
- `datastore = 1.3.0-alpha09` (backs all encrypted prefs / settings)

Test-only alphas (`benchmarkMacro`, `uiautomator-beta02`) are lower risk and can stay. **Fix:** pin navigation + datastore to the newest stable that compiles; if an alpha is genuinely required for a feature, add a one-line `// pinned: <reason>` comment so it stops resurfacing every audit.

**`BUILD-4` — `compileSdk = 37` (preview) regressed back in** *(was fixed in May, now reverted)*
`app/build.gradle.kts:18`. The May audit downgraded this to a stable SDK; it is back to **37**, a preview level, while `targetSdk = 35`. Compiling production against a preview SDK risks unstable/removed APIs.
Note for ranking: CinefinTV is a **TV app**, and Google Play's Aug 31 2026 "target API 36" rule **explicitly exempts Android TV** (TV only needs targetSdk ≥ 35). So `targetSdk = 35` is fully compliant and does **not** need to move. The only action here is **aligning `compileSdk` to a stable level (35 or 36)** — don't ship compiled against preview 37.

### Medium

**`FOCUS-1` — D-pad focus-retry timing hack duplicated inline** *(new)*
`ui/screens/detail/DetailScreenComponents.kt:113` and `ui/screens/detail/cinematic/TvShowDetailLayout.kt:211` both hand-roll the same loop: `withFrameNanos {}` + `runCatching { requestFocus() }` + `delay(if (attempt == 0) 64L else 32L)`. This is the exact "fragile timing hack" the 2026-05-06 report claimed `FocusNavigationCoordinator` had eliminated — and the coordinator (`FocusNavigationCoordinator.kt:89-98`) already implements this retry logic centrally. **Fix:** route both detail screens through the coordinator's focus-retry helper so the back-off lives in one place.

**`L-1` — Raw `android.util.Log` still in ~20 files** *(carried, still partial)*
The SecureLogger sweep stalled. The remaining offenders are concentrated in the **security and data layers** — exactly where PII redaction matters most: `PinningTrustManager`, `CertificatePinner`, `PinningHostnameVerifier`, `EncryptedPreferences`, `JellyfinMediaRepository`, `JellyfinStreamRepository`, `RetryStrategy`, `LibraryItemPagingSource`, `OptimizedClientFactory`, plus several `utils/*` files. **Fix:** finish migrating these to `SecureLogger`; prioritize the `data/security/*` and repository files.

**`KEYS-1` — Main library grid has no stable item keys** *(new)*
`ui/screens/library/LibraryScreenShared.kt:194` uses `items(uiState.items.size) { index -> ... }` with index-based access and **no `key=` / `contentType=`**. This is your most-used browsing surface; without stable keys, focus position and recomposition get unstable during paging/refresh. `PersonScreen` already does this correctly (`key = { it.id }, contentType = { "MediaCard" }`) — mirror that pattern here.

### Low

**`STATE-1` — Boxed numeric `mutableStateOf`** *(new, same class as the fixed `U-20`)*
`CinefinTvApp.kt:149` (`0f`), `CinefinTvApp.kt:151` (`0L`), `PerformanceMonitor.kt:476` (`0L`). Swap to `mutableFloatStateOf` / `mutableLongStateOf` to avoid autoboxing on frequent updates (download progress, timestamps).

**`COLOR-1` — 2 stray hardcoded colors** *(remainder of `U-19`)*
`DetailScreenComponents.kt:863` (`Color(0xBF000000)` scrim) and `PlayerScreen.kt:836` (`Color(0xFFFFD700)` gold quality badge). Move to theme tokens (a `scrim` token and a themed badge color) so dark/light and future re-theming stay consistent.

**`NULL-1` — 2 non-null assertions on error strings** *(new)*
`AudioPlayerScreen.kt:133` (`uiState.errorMessage!!`) and `ProfilePickerScreen.kt:90` (`uiState.error!!`). Both are reachable error paths; a race between the null-check and the read can crash. Use a safe-call + elvis (`?: return` / `?: ""`).

**`MAGIC-1` — Unnamed polling delays in the player** *(new)*
`PlayerScreen.kt:787` (`delay(1000L)`), `:913` and `:973` (`delay(500L)`) drive the Next-Up and Skip-Intro/Credits overlays via fixed polling. The file already defines named interval constants (e.g. `PROGRESS_UPDATE_INTERVAL_ACTIVE_MS`), so these raw literals are inconsistent. **Fix:** extract named constants; optionally make the overlays event-driven off player-position state instead of polling.

### Debt (ongoing, not blocking)

**`REFACTOR-1` — Player/Home god-files keep growing**
`PlayerControls.kt` (1,343), `PlayerViewModel.kt` (1,120), `PlayerScreen.kt` (1,096), `HomeScreen.kt` (1,058), `DetailScreenComponents.kt` (995). The player trio alone is ~3,500 lines. Continue the decomposition pattern already used on the detail screen (extract focused sub-composables / delegate playback concerns out of the ViewModel) when you next touch these files.

---

## Suggested sequencing

**Sprint 1 — quick, high-value (½ day)**
1. `BUILD-4` align `compileSdk` to stable
2. `KEYS-1` add keys to library grid
3. `STATE-1` + `COLOR-1` + `NULL-1` (mechanical, low-risk)

**Sprint 2 — focus & logging hygiene**
4. `FOCUS-1` centralize focus-retry through the coordinator
5. `L-1` finish SecureLogger migration in `data/security/*` and repositories

**Sprint 3 — dependency hardening**
6. `BUILD-2` move navigation + datastore off alpha (or document the pins)
7. `MAGIC-1` named constants in the player

**Backlog**
8. `REFACTOR-1` opportunistic decomposition of the player/home files
